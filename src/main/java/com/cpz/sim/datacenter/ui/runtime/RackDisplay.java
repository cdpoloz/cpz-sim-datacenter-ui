package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot;
import com.cpz.sim.datacenter.ui.simulation.TemperatureRange;
import com.cpz.utils.color.Colors;
import static com.cpz.sim.datacenter.ui.util.Constants.*;

/** Display-only projection; temperature aggregation is performed by the backend. */
final class RackDisplay {
    static final int OFFLINE = 0xff254577;
    static final int UNAVAILABLE = 0xff6d6380;
    static int color(RackOperationalSnapshot rack, TemperatureRange range) {
        if (rack == null) return UNAVAILABLE;
        if (!rack.hasInstalledServers()) return COLOR_EMPTY_RACK;
        if (!rack.hasOnlineServers()) return OFFLINE;
        double t = rack.averageOnlineTemperatureCelsius();
        if (!Double.isFinite(t)) return UNAVAILABLE;
        float min = range.minServerTemperatureCelsius(), max = range.maxServerTemperatureCelsius();
        if (t > max) return COLOR_HOTSPOT_RACK;
        return Colors.lerpColor(COLOR_MIN_TEMPERATURE, COLOR_MAX_TEMPERATURE,
                max > min ? Math.clamp((float) ((t - min) / (max - min)), 0, 1) : 0);
    }
    static double temperature(RackOperationalSnapshot rack) {
        return rack != null && rack.hasOnlineServers() ? rack.averageOnlineTemperatureCelsius() : Double.NaN;
    }
}
