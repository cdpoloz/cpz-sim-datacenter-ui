package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.utils.time.Timer;
import com.cpz.utils.time.TimeSource;
import java.util.*;

/** Single-pulse scheduling: pause and speed changes restart the current period. */
public final class RuntimeClock {
    private final Timer timer;
    private final int base;
    private final List<Double> factors;
    private int index;
    public RuntimeClock(Properties properties, TimeSource source) {
        timer = new Timer(source);
        base = Integer.parseInt(properties.getProperty("simulation.timer.base-period-ms"));
        if (base <= 0) throw new IllegalArgumentException("simulation.timer.base-period-ms must be positive");
        String raw = properties.getProperty("simulation.timer.speed-factors");
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("simulation.timer.speed-factors is empty");
        factors = Arrays.stream(raw.split(",", -1)).map(String::trim).map(Double::parseDouble).toList();
        if (factors.stream().anyMatch(f -> !Double.isFinite(f) || f <= 0 || base / f > Integer.MAX_VALUE))
            throw new IllegalArgumentException("simulation.timer.speed-factors must be finite and positive with representable periods");
        double initial = Double.parseDouble(properties.getProperty("simulation.timer.initial-speed-factor"));
        index = factors.indexOf(initial);
        if (index < 0) throw new IllegalArgumentException("simulation.timer.initial-speed-factor must exist in speed-factors");
        configure();
    }
    private void configure() { timer.setPeriodMillis(Math.max(1, (int) Math.round(base / factor()))); }
    public double factor() { return factors.get(index); }
    public boolean running() { return timer.isRunning(); }
    public boolean canDecrease() { return index > 0; }
    public boolean canIncrease() { return index + 1 < factors.size(); }
    public void toggle() { timer.toggle(); }
    public boolean poll() { return timer.pollPeriodPulse(); }
    public void change(int direction) {
        int next = Math.clamp(index + direction, 0, factors.size() - 1);
        if (next == index) return;
        index = next; configure();
        if (running()) timer.start();
    }
}
