package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.sim.datacenter.config.definition.HotAisleDefinition;
import com.cpz.sim.datacenter.cooling.CoolingZoneDefinition;
import com.cpz.sim.datacenter.snapshot.*;
import java.util.*;

/** Selects backend groups and formats values; no physical aggregation occurs here. */
final class RuntimeMetrics {
    record AisleCooling(List<String> zones, boolean shared, Optional<CoolingZoneGroupSnapshot> group) { }
    static AisleCooling aisle(HotAisleDefinition aisle, List<CoolingZoneDefinition> zones, Optional<CoolingSnapshot> snapshot) {
        if (aisle == null) return new AisleCooling(List.of(), false, Optional.empty());
        var columns = Set.copyOf(aisle.columns());
        var matching = zones.stream().filter(z -> z.serverLocations().stream().anyMatch(s -> columns.contains(s.column()))).toList();
        var codes = matching.stream().map(CoolingZoneDefinition::code).distinct().sorted().toList();
        boolean shared = matching.stream().anyMatch(z -> z.serverLocations().stream().anyMatch(s -> !columns.contains(s.column())));
        return new AisleCooling(codes, shared, codes.isEmpty() ? Optional.empty() : snapshot.map(s -> s.aggregateZones(aisle.code(), codes)));
    }
    static String aisleText(AisleCooling data) {
        if (data.group().isEmpty()) return "Sin zonas o snapshot\nSUPPLY/EXHAUST: -- m³/s\nCaudal agrupado no disponible";
        var group = data.group().orElseThrow();
        return "Cobertura " + percent(group.thermalCoverage()) + "  ΔT " + number(group.airTemperatureRiseCelsius(), "%.1f°C") + " (tick)"
                + "\nRecirculación " + percent(group.averageRecirculationFraction()) + (data.shared() ? " · zona compartida" : "")
                + "\nSUPPLY/EXHAUST: -- m³/s (sin API)";
    }
    record Summary(String left, String right) { }
    static Summary summary(DatacenterOperationalSnapshot s) {
        if (s == null) return new Summary("Último tick\nDatos no disponibles", "");
        boolean online = s.hasOnlineServers();
        String hottest = online && s.hottestRackLocation().isPresent()
                ? s.hottestRackLocation().map(r -> r.column()+"/"+r.rackCode().value()).orElse("--") + "  " + number(s.hottestRackAverageTemperatureCelsius(), "%.1f°C") : "--";
        String left = "Último tick\nAire sala: " + number(s.roomAirTemperatureCelsius(), "%.1f°C")
                + "\nServidores online: " + (online ? number(s.averageOnlineServerTemperatureCelsius(), "%.1f°C") : "--")
                + "\nRack más caliente: " + hottest;
        String right = "IT online: " + (online ? percent(s.totalItUtilization()) : "--")
                + "\nIT: " + kw(s.currentItPowerWatts())
                + "\nRefrigeración: " + (s.hasCoolingData() ? kw(s.coolingPowerWatts()) : "--")
                + "\nInstalación: " + (s.hasFacilityPowerData() ? kw(s.totalFacilityPowerWatts()) : "--")
                + "\nPUE: " + (s.hasPue() ? number(s.pue(), "%.2f") : "--");
        return new Summary(left, right);
    }
    private static String kw(double watts) { return number(watts/1000, "%.2f kW"); }
    private static String percent(double value) { return number(value*100, "%.0f%%"); }
    private static String number(double value, String format) { return Double.isFinite(value) ? String.format(Locale.ROOT, format, value) : "--"; }
}
