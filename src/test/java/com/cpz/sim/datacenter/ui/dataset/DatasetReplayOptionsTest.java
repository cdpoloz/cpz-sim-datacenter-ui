package com.cpz.sim.datacenter.ui.dataset;

import com.cpz.sim.datacenter.ui.main.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DatasetReplayOptionsTest {
    static Properties valid() {
        var p = new Properties();
        p.setProperty("simulation.source", "DATASET");
        p.setProperty("simulation.datacenter.config", "pilot.json");
        p.setProperty("simulation.dataset.path", "new.xlsx");
        p.setProperty("simulation.dataset.configurationId", "synthetic-dataset-pilot");
        p.setProperty("simulation.dataset.configurationVersion", "1");
        p.setProperty("simulation.dataset.mode", "POWER_DRIVEN");
        p.setProperty("simulation.dataset.startUtc", "2026-01-01T00:00:00Z");
        p.setProperty("simulation.dataset.steps", "3");
        p.setProperty("simulation.dataset.step", "PT15M");
        p.setProperty("simulation.dataset.maximumHold", "PT15M");
        return p;
    }
    @Test void selectionHappensWithoutConstructingSketchesAndNeverFallsBack() {
        var p = new Properties();
        assertEquals(Sketch.class, Launcher.selectSketch(p));
        p.setProperty("simulation.source", "DEMO");
        assertEquals(Sketch.class, Launcher.selectSketch(p));
        p.setProperty("simulation.source", "DATASET");
        assertEquals(DatasetReplaySketch.class, Launcher.selectSketch(p));
        var invalid = DatasetReplayController.load(p);
        assertEquals(DatasetReplayController.State.INVALID, invalid.state());
        invalid.play(0); invalid.pulse(Long.MAX_VALUE);
        assertEquals(DatasetReplayController.State.INVALID, invalid.state());
        assertTrue(invalid.errorLines().stream().anyMatch(s -> s.contains("startUtc")));
        for (String value : List.of("", "dataset", " DEMO", "OTHER")) {
            p.setProperty("simulation.source", value);
            assertThrows(IllegalArgumentException.class, () -> Launcher.selectSketch(p));
        }
    }
    @Test void parsesBothModesAndDelegatesPlanSemantics() {
        for (String mode : List.of("POWER_DRIVEN", "UTILIZATION_DRIVEN")) {
            var p = valid(); p.setProperty("simulation.dataset.mode", mode);
            var options = DatasetReplayOptions.parse(p);
            assertEquals(mode, options.plan().mode().name());
            assertEquals(3, options.plan().points());
            assertEquals(900, options.plan().step().getSeconds());
        }
    }
    @Test void everyDatasetPropertyIsRequired() {
        for (String key : valid().stringPropertyNames()) {
            if (key.equals("simulation.source")) continue;
            for (String value : List.of("", " ", " padded ")) {
                var p = valid(); p.setProperty(key, value);
                assertThrows(IllegalArgumentException.class, () -> DatasetReplayOptions.parse(p), key);
            }
            var p = valid(); p.remove(key);
            assertThrows(IllegalArgumentException.class, () -> DatasetReplayOptions.parse(p), key);
        }
    }
    @Test void rejectsNonUtcMalformedDurationsAndBackendPlanLimits() {
        Map<String, List<String>> invalid = Map.of(
                "startUtc", List.of("2026-01-01T01:00:00+01:00", "2026-01-01T00:00:00", "garbageZ"),
                "steps", List.of("0", "1001", "-1", "1.0", "9999999999999"),
                "mode", List.of("TEMPERATURE_DRIVEN", "power_driven", "UNKNOWN"),
                "step", List.of("15", "PT0S", "-PT1S", "P1M"),
                "maximumHold", List.of("15m", "PT0S", "-PT1S"));
        invalid.forEach((key, values) -> values.forEach(value -> {
            var p = valid(); p.setProperty("simulation.dataset." + key, value);
            assertThrows(IllegalArgumentException.class, () -> DatasetReplayOptions.parse(p), key + "=" + value);
        }));
    }
}
