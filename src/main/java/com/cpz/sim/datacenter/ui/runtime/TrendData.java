package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.sim.datacenter.history.DatacenterSimulationStepSnapshot;
import java.util.List;

/** A bounded display projection of the recorder's snapshots; NaN denotes a gap. */
final class TrendData {
    enum View { TEMPERATURE, AISLE, UTILIZATION, POWER }
    record Sample(long tick, double first, double second, double third) { }
    record Range(double min, double max) {
        double fraction(double value) { return (value - min) / (max - min); }
    }

    static List<Sample> project(List<DatacenterSimulationStepSnapshot> history, View view, String aisle) {
        return history.stream().map(step -> {
            var s = step.operationalSnapshot();
            double missing = Double.NaN;
            return switch (view) {
                case TEMPERATURE -> new Sample(step.tickIndex(),
                        s.hasOnlineServers() ? s.averageOnlineServerTemperatureCelsius() : missing,
                        s.hottestRackLocation().isPresent() ? s.hottestRackAverageTemperatureCelsius() : missing,
                        s.roomAirTemperatureCelsius());
                case AISLE -> new Sample(step.tickIndex(), aisle == null ? missing : s.findServerGroup(aisle)
                        .filter(g -> g.hasOnlineServers()).map(g -> g.averageOnlineTemperatureCelsius()).orElse(missing), missing, missing);
                case UTILIZATION -> new Sample(step.tickIndex(), s.hasOnlineServers() ? s.totalItUtilization() * 100 : missing, missing, missing);
                case POWER -> new Sample(step.tickIndex(), s.currentItPowerWatts() / 1000,
                        s.hasFacilityPowerData() ? s.totalFacilityPowerWatts() / 1000 : missing, missing);
            };
        }).toList();
    }

    static Range range(List<Sample> samples) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (var sample : samples) for (double value : new double[]{sample.first(), sample.second(), sample.third()}) {
            if (Double.isFinite(value)) { min = Math.min(min, value); max = Math.max(max, value); }
        }
        if (!Double.isFinite(min)) return new Range(0, 1);
        double padding = min == max ? Math.max(1, Math.abs(min) * .05) : (max - min) * .08;
        // Axis text and coordinate mapping use the same rounded limits.
        return new Range(Math.floor((min - padding) * 100) / 100, Math.ceil((max + padding) * 100) / 100);
    }

    static double xFraction(long tick, long first, long last) {
        return first == last ? .5 : (double)(tick - first) / (last - first);
    }
}
