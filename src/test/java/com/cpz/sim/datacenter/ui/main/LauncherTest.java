package com.cpz.sim.datacenter.ui.main;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class LauncherTest {
    @Test void fileModeSelectsClassicOrRuntimeWhenJvmSourceIsAbsent() {
        var file = new Properties();
        var jvm = new Properties();
        assertEquals(Sketch.class, Launcher.selectSketch(file, jvm));
        file.setProperty("ui.mode", "CLASSIC");
        assertEquals(Sketch.class, Launcher.selectSketch(file, jvm));
        file.setProperty("ui.mode", "RUNTIME");
        assertEquals(RuntimeSketch.class, Launcher.selectSketch(file, jvm));
    }

    @Test void explicitJvmSourcesOverrideEveryFileModeIncludingInvalidValues() {
        var file = new Properties();
        var jvm = new Properties();
        for (String mode : new String[]{"CLASSIC", "RUNTIME", "", "UNKNOWN", "runtime"}) {
            file.setProperty("ui.mode", mode);
            assertJvmSources(file, jvm);
        }
        file.remove("ui.mode");
        assertJvmSources(file, jvm);
    }

    private void assertJvmSources(Properties file, Properties jvm) {
        jvm.setProperty("simulation.source", "DEMO");
        assertEquals(Sketch.class, Launcher.selectSketch(file, jvm));
        jvm.setProperty("simulation.source", "RUNTIME");
        assertEquals(RuntimeSketch.class, Launcher.selectSketch(file, jvm));
        jvm.setProperty("simulation.source", "DATASET");
        assertEquals(DatasetReplaySketch.class, Launcher.selectSketch(file, jvm));
    }

    @Test void rejectsInvalidFileModesAndJvmSourcesWithTheirKeysAndValidValues() {
        var file = new Properties();
        var jvm = new Properties();
        for (String mode : new String[]{"", " ", "UNKNOWN", "runtime", " CLASSIC", "CLASSIC "}) {
            file.setProperty("ui.mode", mode);
            var error = assertThrows(IllegalArgumentException.class, () -> Launcher.selectSketch(file, jvm));
            assertTrue(error.getMessage().contains("ui.mode"));
            assertTrue(error.getMessage().contains("CLASSIC"));
            assertTrue(error.getMessage().contains("RUNTIME"));
        }
        for (String mode : new String[]{"CLASSIC", "RUNTIME"}) {
            file.setProperty("ui.mode", mode);
            for (String source : new String[]{"", " ", "UNKNOWN", "runtime", " RUNTIME"}) {
                jvm.setProperty("simulation.source", source);
                var error = assertThrows(IllegalArgumentException.class, () -> Launcher.selectSketch(file, jvm));
                assertTrue(error.getMessage().contains("simulation.source"));
                assertTrue(error.getMessage().contains("DEMO, DATASET or RUNTIME"));
            }
        }
    }

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
