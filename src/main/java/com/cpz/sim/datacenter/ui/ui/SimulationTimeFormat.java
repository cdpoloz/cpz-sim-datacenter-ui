package com.cpz.sim.datacenter.ui.ui;

/** Shared simulated elapsed-time presentation for the header and snapshot events. */
public final class SimulationTimeFormat {
    private SimulationTimeFormat() {
    }

    public static String daysHoursMinutes(double elapsedSeconds) {
        long totalMinutes = (long) (elapsedSeconds / 60.0);
        return "%02dd %02d:%02d".formatted(totalMinutes / 1440,
                (totalMinutes % 1440) / 60, totalMinutes % 60);
    }
}
