package com.cpz.sim.datacenter.ui.ui;

import com.cpz.processing.controls.core.overlay.notification.NotificationSeverity;
import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.sim.datacenter.health.*;
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.snapshot.*;
import com.cpz.sim.datacenter.system.ServerHealthSystem;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.temperature.*;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HealthPresentationTest {
    private static final RackLocation RACK = new RackLocation("C01", "R01");

    @Test
    void actualRoomAndSystemIndicatorsUseBackendLevelInsteadOfGradientOrAverageThresholds() {
        Fixture f = new Fixture();
        var sketch = new com.cpz.sim.datacenter.ui.main.Sketch();
        var controls = new UiComponentContainer();
        var indicators = new HashMap<String, Indicator>();
        for (String code : List.of("indRoomHotAisleC01", "indRoomHotAisleC02-C03", "indRoomHotAisleC04-C05",
                "indRoomHotAisleC06-C07", "indRoomHotAisleC08", "indSystemStatusNormal", "indSystemStatusWatch",
                "indSystemStatusWarning", "indSystemStatusCritical")) {
            indicators.put(code, new Indicator(sketch, code, 0, 0, 10, 10));
        }
        controls.setIndicators(indicators);
        var rackIndicator = new Indicator(sketch, "indRackC01R01", 0, 0, 10, 10);
        controls.setIndicatorsRack(java.util.Map.of("indRackC01R01", rackIndicator));
        var conditions = new HashMap<String, Indicator>();
        for (String type : List.of("Offline", "Empty", "Hotspot", "AI")) {
            String code = "indRack" + type + "C01R01";
            conditions.put(code, new Indicator(sketch, code, 0, 0, 10, 10));
        }
        controls.setIndicatorsRackCondition(conditions);
        var status = new Label(sketch, "lblSystemStatusValue", "", 0, 0, 100, 20);
        controls.setLabels(java.util.Map.of("lblSystemStatusValue", status));
        var simulation = new com.cpz.sim.datacenter.ui.simulation.SimulationContainer();
        simulation.setDatacenter(f.dc);
        var context = new com.cpz.sim.datacenter.ui.app.ApplicationContext(sketch);
        var format = new UiFormatContainer();
        var room = new com.cpz.sim.datacenter.ui.ui.panel.RoomPanelUpdater(context, simulation, controls, format);
        var global = new com.cpz.sim.datacenter.ui.ui.panel.GlobalOverviewUpdater(context, simulation, controls, format, null);
        var energy = new EnergyConsumptionSnapshotProvider(f.dc, new com.cpz.sim.datacenter.system.EnergyConsumptionSystem(f.dc));
        var temperature = new TemperatureSnapshotProvider(f.dc, f.temperatures, f.options);
        var groups = java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(i -> new ServerGroupDefinition("HA%02d".formatted(i), Set.of(f.server.getLocation()))).toList();
        var operational = new DatacenterOperationalSnapshotProvider(f.dc, groups);
        double[] values = {85, 100, 98, 95};
        for (int i = 0; i < values.length; i++) {
            HealthSnapshot health = f.at(i + 1, values[i]);
            var tick = new SimulationTick(i + 1, Duration.ofMinutes(i + 1), Duration.ofMinutes(1));
            simulation.setHealthSnapshot(health);
            simulation.setOperationalSnapshot(operational.snapshot(energy.snapshot(tick), temperature.snapshot(tick), health));
            room.update(24, 74);
            global.update();
            boolean critical = i == 1 || i == 2;
            assertEquals(critical, conditions.get("indRackHotspotC01R01").isOn());
            assertEquals(critical, indicators.get("indSystemStatusCritical").isOn());
            assertEquals(critical ? "CRITICAL" : "WARNING", status.getText());
            assertEquals(critical ? com.cpz.sim.datacenter.ui.util.Constants.COLOR_HOTSPOT_RACK
                    : com.cpz.sim.datacenter.ui.util.Constants.COLOR_MAX_TEMPERATURE, rackIndicator.getOnColor());
        }
        f.server.setStatus(HardwareStatus.OFFLINE);
        HealthSnapshot health = f.at(5, 150);
        var tick = new SimulationTick(5, Duration.ofMinutes(5), Duration.ofMinutes(1));
        simulation.setHealthSnapshot(health);
        simulation.setOperationalSnapshot(operational.snapshot(energy.snapshot(tick), temperature.snapshot(tick), health));
        room.update(24, 74);
        global.update();
        assertFalse(conditions.get("indRackHotspotC01R01").isOn());
        assertTrue(conditions.get("indRackOfflineC01R01").isOn());
        assertEquals("IDLE", status.getText());
    }

    @Test
    void mapSystemAndNotificationsFollowTheSameRealBackendHysteresis() {
        Fixture f = new Fixture();
        List<Event> events = new ArrayList<>();
        var notifier = new SimulationAlertNotifier((severity, message) -> events.add(new Event(severity, message)));
        var baseline = f.at(1, 25);
        notifier.update(baseline);
        assertEquals("NORMAL", HealthPresentation.systemStatus(baseline));
        assertTrue(events.isEmpty());

        var elevated = f.at(2, 80);
        notifier.update(elevated);
        assertEquals("WARNING", HealthPresentation.systemStatus(elevated));
        assertTrue(HealthPresentation.criticalRacks(elevated).isEmpty());
        assertEquals(NotificationSeverity.WARNING, events.getLast().severity());

        var critical = f.at(3, 100);
        notifier.update(critical);
        assertEquals(elevated.servers().getFirst().alertReasons(), critical.servers().getFirst().alertReasons());
        assertEquals(Set.of(RACK), HealthPresentation.criticalRacks(critical));
        assertEquals("CRITICAL", HealthPresentation.systemStatus(critical));
        assertEquals(NotificationSeverity.ERROR, events.getLast().severity());
        assertTrue(events.getLast().message().contains("Critical temperature"));
        assertTrue(events.getLast().message().contains("00d 00:03"));
        assertEquals(2, events.size());

        // Paused frames and changed raw temperature cannot bypass the evaluated snapshot.
        f.temperatures.getThermalState(f.server.getCode()).setTemperatureCelsius(70);
        notifier.update(critical);
        notifier.update(null);
        assertEquals(2, events.size());
        assertEquals(Set.of(RACK), HealthPresentation.criticalRacks(critical));

        var retained = f.at(4, 98);
        notifier.update(retained);
        assertEquals(Set.of(RACK), HealthPresentation.criticalRacks(retained));
        assertEquals("CRITICAL", HealthPresentation.systemStatus(retained));
        assertEquals(2, events.size());

        var downgrade = f.at(5, 95);
        notifier.update(downgrade);
        assertEquals(critical.servers().getFirst().alertReasons(), downgrade.servers().getFirst().alertReasons());
        assertTrue(HealthPresentation.criticalRacks(downgrade).isEmpty());
        assertEquals("WARNING", HealthPresentation.systemStatus(downgrade));
        assertEquals(NotificationSeverity.WARNING, events.getLast().severity());
        assertEquals(3, events.size());

        var recovery = f.at(6, 75);
        notifier.update(recovery);
        assertEquals("NORMAL", HealthPresentation.systemStatus(recovery));
        assertTrue(HealthPresentation.criticalRacks(recovery).isEmpty());
        assertEquals(NotificationSeverity.SUCCESS, events.getLast().severity());
        assertEquals(4, events.size());
    }

    @Test
    void rackProjectionUsesCompleteLocationAndAnyCriticalServerNotTheAverage() {
        var snapshot = snapshot(1,
                server("C01", "R01", "S01", HardwareStatus.ALERT, ThermalAlertLevel.CRITICAL, 101),
                server("C01", "R01", "S02", HardwareStatus.OK, ThermalAlertLevel.NORMAL, 30),
                server("C02", "R01", "S01", HardwareStatus.OK, ThermalAlertLevel.NORMAL, 30));
        assertEquals(Set.of(RACK), HealthPresentation.criticalRacks(snapshot));
        assertEquals("CRITICAL", HealthPresentation.systemStatus(snapshot));
    }

    @Test
    void rawTemperaturesAndUtilizationDoNotCreateNewUiThresholds() {
        var normal = server("C01", "R01", "S01", HardwareStatus.OK, ThermalAlertLevel.NORMAL, 150);
        var snapshot = snapshot(1, normal);
        assertEquals("NORMAL", HealthPresentation.systemStatus(snapshot));
        assertTrue(HealthPresentation.criticalRacks(snapshot).isEmpty());
        var utilization = new ServerHealthSnapshot("C01-R01-S01", "C01", new RackCode("R01"), "S01",
                HardwareStatus.ALERT, Set.of(ServerAlertReason.HIGH_UTILIZATION), 1, 60, ThermalAlertLevel.NORMAL);
        assertEquals("WARNING", HealthPresentation.systemStatus(snapshot(2, utilization)));
        assertEquals(NotificationSeverity.WARNING, HealthPresentation.notificationSeverity(utilization));
        assertTrue(HealthPresentation.criticalRacks(snapshot(2, utilization)).isEmpty());
    }

    @Test
    void offlineAndUnavailableHealthCannotReportCriticalOrSuccessfulRecovery() {
        assertEquals("NO DATA", HealthPresentation.systemStatus(null));
        assertEquals("IDLE", HealthPresentation.systemStatus(snapshot(1)));
        var offline = server("C01", "R01", "S01", HardwareStatus.OFFLINE, ThermalAlertLevel.CRITICAL, 150);
        assertEquals("IDLE", HealthPresentation.systemStatus(snapshot(1, offline)));
        assertTrue(HealthPresentation.criticalRacks(snapshot(1, offline)).isEmpty());
        List<Event> events = new ArrayList<>();
        var notifier = new SimulationAlertNotifier((severity, message) -> events.add(new Event(severity, message)));
        notifier.update(snapshot(1, server("C01", "R01", "S01", HardwareStatus.ALERT, ThermalAlertLevel.CRITICAL, 150)));
        notifier.update(snapshot(2, offline));
        assertTrue(events.isEmpty());
    }

    @Test
    void resetTickReestablishesBaselineWithoutSpuriousNotifications() {
        List<Event> events = new ArrayList<>();
        var notifier = new SimulationAlertNotifier((severity, message) -> events.add(new Event(severity, message)));
        notifier.update(snapshot(5, server("C01", "R01", "S01", HardwareStatus.ALERT, ThermalAlertLevel.CRITICAL, 150)));
        notifier.update(snapshot(1, server("C01", "R01", "S01", HardwareStatus.OK, ThermalAlertLevel.NORMAL, 25)));
        assertTrue(events.isEmpty());
        notifier.update(snapshot(2, server("C01", "R01", "S01", HardwareStatus.ALERT, ThermalAlertLevel.CRITICAL, 150)));
        assertEquals(NotificationSeverity.ERROR, events.getLast().severity());
    }

    private static HealthSnapshot snapshot(long tick, ServerHealthSnapshot... servers) {
        return new HealthSnapshot(tick, tick * 60, List.of(servers));
    }

    private static ServerHealthSnapshot server(String column, String rack, String slot, HardwareStatus status,
            ThermalAlertLevel level, double temperature) {
        return new ServerHealthSnapshot(column + "-" + rack + "-" + slot, column, new RackCode(rack), slot, status,
                level == ThermalAlertLevel.NORMAL ? Set.of() : Set.of(ServerAlertReason.HIGH_TEMPERATURE), .5, temperature, level);
    }

    private record Event(NotificationSeverity severity, String message) {
    }

    private static final class Fixture {
        private final Server server;
        private final Datacenter dc;
        private final TemperatureSystemOptions options = new TemperatureSystemOptions(24, 25, 5000, 8);
        private final TemperatureSystem temperatures;
        private final ServerHealthSystem health;
        private final HealthSnapshotProvider provider;

        private Fixture() {
            var rack = new Rack(new RackCode("R01"), RACK, List.of("S01"));
            server = new Server(new ServerLocation("C01", "R01", "S01"),
                    new ServerConfig("test", "CPZ", "Test", 100, 300), HardwareStatus.OK, ServerRole.GENERAL_PURPOSE);
            dc = new Datacenter(List.of(rack), List.of(server));
            temperatures = new TemperatureSystem(dc, options, new SimpleServerTemperatureModel());
            health = new ServerHealthSystem(dc, temperatures,
                    new ServerHealthOptions(new HealthThreshold(.9, .85), new HealthThreshold(80, 75), new HealthThreshold(100, 95)));
            provider = new HealthSnapshotProvider(dc, health, temperatures);
        }

        private HealthSnapshot at(long index, double temperature) {
            temperatures.getThermalState(server.getCode()).setTemperatureCelsius(temperature);
            var tick = new SimulationTick(index, Duration.ofMinutes(index), Duration.ofMinutes(1));
            health.update(tick);
            return provider.snapshot(tick);
        }
    }
}
