package com.cpz.sim.datacenter.ui.main;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class LauncherTest {
    @Test void selectsRuntimeWithoutDatasetOrClassicMapConfiguration() {
        var properties = new Properties();
        properties.setProperty("simulation.source", "RUNTIME");
        properties.setProperty("simulation.dataset.path", "missing.xlsx");
        properties.setProperty("simulation.datacenter.config", "missing.json");
        assertEquals(RuntimeSketch.class, Launcher.selectSketch(properties));
    }

    @Test void preservesDefaultAndExistingModesAndRejectsUnknownSources() {
        var properties = new Properties();
        assertEquals(Sketch.class, Launcher.selectSketch(properties));
        properties.setProperty("simulation.source", "DEMO");
        assertEquals(Sketch.class, Launcher.selectSketch(properties));
        properties.setProperty("simulation.source", "DATASET");
        assertEquals(DatasetReplaySketch.class, Launcher.selectSketch(properties));
        for (String source : new String[]{"runtime", " RUNTIME", "", "UNKNOWN"}) {
            properties.setProperty("simulation.source", source);
            assertThrows(IllegalArgumentException.class, () -> Launcher.selectSketch(properties));
        }
    }
}
