package com.cpz.sim.datacenter.ui.ui.render;

import java.util.List;

/** Display bounds shared by temperature curves and their four axis labels. */
record TemperatureGraphRange(double minimum, double maximum) {
    static TemperatureGraphRange from(List<Double> values, double configuredMaximum) {
        double minimum = 0.0;
        double maximum = Double.isFinite(configuredMaximum) ? Math.max(20.0, configuredMaximum) : 80.0;
        for (double value : values) {
            if (!Double.isFinite(value)) continue;
            minimum = Math.min(minimum, value - 2.0);
            maximum = Math.max(maximum, value + 2.0);
        }
        // Round outward to whole 20-degree spans: four legible, integral tick intervals.
        return new TemperatureGraphRange(Math.floor(minimum / 20.0) * 20.0,
                Math.ceil(maximum / 20.0) * 20.0);
    }

    double tick(int index) {
        return minimum + (maximum - minimum) * index / 4.0;
    }
}
