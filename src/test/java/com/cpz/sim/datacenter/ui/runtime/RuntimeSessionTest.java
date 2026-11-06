package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeSessionTest {
    @TempDir Path temp;
    private Properties properties() throws Exception {
        var p = new Properties();
        try (var in = Files.newInputStream(Path.of("data/config.properties"))) { p.load(in); }
        return p;
    }
    @Test void timerPausesResumesAndChangesSpeedWithoutCatchup() throws Exception {
        long[] now = {0};
        var c = new RuntimeClock(properties(), () -> now[0]);
        assertFalse(c.running()); assertFalse(c.poll());
        c.toggle(); now[0] += 999_000_000; assertFalse(c.poll());
        now[0] += 1_000_000; assertTrue(c.poll()); assertFalse(c.poll());
        c.toggle(); now[0] += 100_000_000_000L; assertFalse(c.poll());
        c.change(1); assertEquals(2, c.factor()); assertFalse(c.running());
        c.toggle(); now[0] += 499_000_000; assertFalse(c.poll());
        now[0] += 1_000_000; assertTrue(c.poll());
        c.change(1); now[0] += 250_000_000; assertTrue(c.poll()); assertFalse(c.poll());
        for (int i = 0; i < 20; i++) c.change(1);
        assertFalse(c.canIncrease());
        for (int i = 0; i < 20; i++) c.change(-1);
        assertFalse(c.canDecrease());
    }
    @Test void rejectsInvalidTimerConfiguration() throws Exception {
        var p = properties(); p.setProperty("simulation.timer.speed-factors", "1,NaN");
        assertThrows(IllegalArgumentException.class, () -> new RuntimeClock(p, () -> 0));
        var missing = properties(); missing.setProperty("simulation.timer.initial-speed-factor", "3");
        assertThrows(IllegalArgumentException.class, () -> new RuntimeClock(missing, () -> 0));
    }
    @Test void backendModesProduceConsistentSnapshots() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String mode : new String[]{"UTILIZATION_DRIVEN", "POWER_DRIVEN", "TEMPERATURE_DRIVEN"}) {
            var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(Path.of("data/config/datacenter.json").toFile());
            root.put("dataInputMode", mode);
            if (mode.equals("TEMPERATURE_DRIVEN")) { root.remove("rackCooling"); root.remove("roomAirTemperature"); }
            Path file = temp.resolve(mode + ".json"); mapper.writeValue(file.toFile(), root);
            var session = new RuntimeSession(new JsonDatacenterConfigLoader().load(file), properties());
            var initial = session.snapshot();
            assertEquals(96, initial.operationalSnapshot().rackCount());
            assertEquals(initial.tickIndex(), initial.operationalSnapshot().tickIndex());
            session.step();
            assertEquals(initial.tickIndex() + 1, session.snapshot().tickIndex());
            assertEquals(session.snapshot().tickIndex(), session.snapshot().operationalSnapshot().tickIndex());
            assertTrue(session.snapshot().operationalSnapshot().racks().containsKey(new com.cpz.sim.datacenter.model.RackLocation("C02", "R01")));
        }
    }
    @Test void rackProjectionDistinguishesIdentityAndMissingStates() {
        var a = new com.cpz.sim.datacenter.model.RackLocation("C01", "R01");
        var b = new com.cpz.sim.datacenter.model.RackLocation("C02", "R01");
        var range = new com.cpz.sim.datacenter.ui.simulation.TemperatureRange(20, 80);
        var cold = new com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot(a, 1, 1, 0, 0, 0, 25, 25, 0);
        var hot = new com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot(b, 1, 1, 0, 0, 0, 70, 70, 0);
        var empty = new com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot(a, 0, 0, 0, 0, 0, Double.NaN, 20, Double.NaN);
        var offline = new com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot(a, 1, 0, 0, 0, 0, Double.NaN, 20, Double.NaN);
        assertNotEquals(a, b); assertNotEquals(RackDisplay.color(cold, range), RackDisplay.color(hot, range));
        assertNotEquals(RackDisplay.color(empty, range), RackDisplay.color(offline, range));
        assertNotEquals(RackDisplay.color(offline, range), RackDisplay.color(null, range));
        assertTrue(Double.isNaN(RackDisplay.temperature(offline)));
        assertEquals(25, RackDisplay.temperature(cold));
    }
}
