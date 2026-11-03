package com.cpz.sim.datacenter.ui.dataset;

import com.cpz.sim.datacenter.dataset.DatasetReplayPlan;
import com.cpz.sim.datacenter.input.DatacenterDataInputMode;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

/** Immutable startup settings. Plan semantics and limits belong to the backend. */
public record DatasetReplayOptions(Path configuration, Path workbook, String configurationId,
                                   String configurationVersion, DatasetReplayPlan plan) {
    public enum Source { DEMO, DATASET, RUNTIME }

    public static Source source(Properties properties) {
        String value = properties.getProperty("simulation.source");
        if (value == null) return Source.DEMO;
        try { return Source.valueOf(value); }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("simulation.source must be DEMO, DATASET or RUNTIME: " + value, exception);
        }
    }

    public static DatasetReplayOptions parse(Properties properties) {
        String start = required(properties, "simulation.dataset.startUtc");
        if (!start.endsWith("Z")) throw new IllegalArgumentException("simulation.dataset.startUtc must end in Z (UTC)");
        String points = required(properties, "simulation.dataset.steps");
        if (!points.matches("[0-9]+")) throw new IllegalArgumentException("simulation.dataset.steps must be an integer");
        try {
            return new DatasetReplayOptions(Path.of(required(properties, "simulation.datacenter.config")),
                    Path.of(required(properties, "simulation.dataset.path")),
                    required(properties, "simulation.dataset.configurationId"),
                    required(properties, "simulation.dataset.configurationVersion"),
                    new DatasetReplayPlan(DatacenterDataInputMode.valueOf(required(properties, "simulation.dataset.mode")),
                            Instant.parse(start), Integer.parseInt(points),
                            Duration.parse(required(properties, "simulation.dataset.step")),
                            Duration.parse(required(properties, "simulation.dataset.maximumHold"))));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid DATASET properties: " + exception.getMessage(), exception);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank() || !value.equals(value.strip()))
            throw new IllegalArgumentException("Required non-blank JVM property: " + key);
        return value;
    }
}
