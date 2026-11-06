package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.sim.datacenter.snapshot.ServerGroupOperationalSnapshot;
import java.util.Locale;
import java.util.function.DoubleFunction;

/** Formats backend values without changing their population or aggregation. */
final class AisleMetrics {
    static String text(ServerGroupOperationalSnapshot group, DoubleFunction<String> temperature) {
        if (group == null) return "Datos no disponibles";
        String state = !group.hasInstalledServers() ? "Sin servidores\n" : !group.hasOnlineServers() ? "Sin servidores online\n" : "";
        String avg = value(group.averageOnlineTemperatureCelsius(), temperature);
        String load = Double.isFinite(group.averageOnlineUtilization()) ? String.format(Locale.ROOT, "%.0f%%", 100 * group.averageOnlineUtilization()) : "--";
        String maximum = value(group.maximumTemperatureCelsius(), temperature);
        String location = group.maximumTemperatureLocation().map(p -> p.column()+"/"+p.rackCode().value()+"/"+p.slot()).orElse("--");
        return state + "Media online: " + avg + "  IT: " + load + "\nMáx instalado: " + maximum + "\n" + location + "  (* máximo)";
    }
    private static String value(double value, DoubleFunction<String> format) { return Double.isFinite(value) ? format.apply(value) : "--"; }
}
