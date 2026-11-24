package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.sim.datacenter.snapshot.ServerGroupOperationalSnapshot;
import java.util.Locale;
import java.util.function.DoubleFunction;

/** Formats backend values without changing their population or aggregation. */
final class AisleMetrics {
    static String text(ServerGroupOperationalSnapshot group, DoubleFunction<String> temperature) {
        if (group == null) return "No data";
        String state = !group.hasInstalledServers() ? "No servers installed\n" : !group.hasOnlineServers() ? "No online servers\n" : "";
        String avg = value(group.averageOnlineTemperatureCelsius(), temperature);
        String load = Double.isFinite(group.averageOnlineUtilization()) ? String.format(Locale.ROOT, "%.0f%%", 100 * group.averageOnlineUtilization()) : "--";
        String maximum = value(group.maximumTemperatureCelsius(), temperature);
        String location = group.maximumTemperatureLocation().map(p -> p.column()+"/"+p.rackCode().value()+"/"+p.slot()).orElse("--");
        return state + "Online avg temp: " + avg + "  IT load: " + load + "\nInstalled max temp: " + maximum + "\n" + location + "  (* max)";
    }
    private static String value(double value, DoubleFunction<String> format) { return Double.isFinite(value) ? format.apply(value) : "--"; }
}
