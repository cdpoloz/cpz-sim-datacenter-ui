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
        if (data.group().isEmpty()) return "No cooling zones or snapshot\nSUPPLY/EXHAUST: -- m³/s\nGrouped airflow unavailable";
        var group = data.group().orElseThrow();
        return "Coverage " + percent(group.thermalCoverage()) + "  ΔT " + number(group.airTemperatureRiseCelsius(), "%.1f°C") + " (tick)"
                + "\nRecirculation " + percent(group.averageRecirculationFraction()) + (data.shared() ? " · shared zone" : "")
                + "\nSUPPLY " + number(group.supplyAirflowCubicMetersPerSecond(), "%.2f")
                + " / EXHAUST " + number(group.exhaustAirflowCubicMetersPerSecond(), "%.2f") + " m³/s";
    }
    record Summary(String left, String right) { }
    static Summary summary(DatacenterOperationalSnapshot s) {
        if (s == null) return new Summary("Last tick\nNo data", "");
        boolean online = s.hasOnlineServers();
        String hottest = online && s.hottestRackLocation().isPresent()
                ? s.hottestRackLocation().map(r -> r.column()+"/"+r.rackCode().value()).orElse("--") + "  " + number(s.hottestRackAverageTemperatureCelsius(), "%.1f°C") : "--";
        String left = "Last tick\nRoom air temp: " + number(s.roomAirTemperatureCelsius(), "%.1f°C")
                + "\nOnline avg temp: " + (online ? number(s.averageOnlineServerTemperatureCelsius(), "%.1f°C") : "--")
                + "\nHottest rack (avg):\n" + hottest;
        String right = "Online IT load: " + (online ? percent(s.totalItUtilization()) : "--")
                + "\nIT power: " + kw(s.currentItPowerWatts())
                + "\nCooling power: " + (s.hasCoolingData() ? kw(s.coolingPowerWatts()) : "--")
                + "\nTotal power: " + (s.hasFacilityPowerData() ? kw(s.totalFacilityPowerWatts()) : "--")
                + "\nPUE: " + (s.hasPue() ? number(s.pue(), "%.2f") : "--");
        return new Summary(left, right);
    }
    private static String kw(double watts) { return number(watts/1000, "%.2f kW"); }
    private static String percent(double value) { return number(value*100, "%.0f%%"); }
    private static String number(double value, String format) { return Double.isFinite(value) ? String.format(Locale.ROOT, format, value) : "--"; }
}
