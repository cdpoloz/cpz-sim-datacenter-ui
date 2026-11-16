package com.cpz.sim.datacenter.ui.ui;

import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.core.overlay.notification.NotificationSeverity;
import com.cpz.sim.datacenter.health.*;
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.snapshot.*;
import com.cpz.sim.datacenter.system.*;
import com.cpz.sim.datacenter.temperature.*;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.main.Sketch;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.ui.panel.*;
import com.cpz.sim.datacenter.ui.ui.selection.SelectionManager;
import com.cpz.sim.foundation.time.SimulationTick;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static com.cpz.sim.datacenter.ui.util.Constants.*;

class ThermalShutdownPresentationTest {
    @Test
    void backendShutdownUpdatesCountsMissingMetricsCriticalMapAndPersistentAlertThenRecovers() {
        var f = new Fixture();
        var active = new HashMap<String, String>();
        var severities = new HashMap<String, NotificationSeverity>();
        var sequence = new AtomicInteger();
        var notifier = new SimulationAlertNotifier((severity, message) -> {
            String id = "alert-" + sequence.incrementAndGet();
            active.put(id, message); severities.put(id, severity); return id;
        }, id -> { active.remove(id); severities.remove(id); });
        f.protectedServer.evaluateThermalProtection(110, 0);
        f.publish(1);
        f.update();
        notifier.update(f.simulation.healthSnapshot());
        assertEquals("2", f.text("lblRoomTotalServersValue"));
        assertEquals("0", f.text("lblRoomOnlineServersValue"));
        assertEquals("--", f.text("lblAverageServerTemperatureValue"));
        assertEquals("--", f.text("lblRoomMaximumRackTemperatureValue"));
        assertEquals("--", f.text("lblRoomMaximumRackTemperatureLocation"));
        assertEquals("AVG SERVER:--", f.text("lblTrendsRoomTemperature"));
        assertEquals("IT LOAD:5W", f.text("lblTrendsItLoad"));
        assertTrue(Double.isNaN(MetricPresentation.onlineAverageTemperatureCelsius(f.simulation.operationalSnapshot())));
        assertEquals("CRITICAL", f.text("lblSystemStatusValue"));
        assertTrue(f.controls.indicators().get("indSystemStatusCritical").isOn());
        assertTrue(f.controls.indicatorsRackCondition().get("indRackOfflineC01R01").isOn());
        assertFalse(f.controls.indicatorsRackCondition().get("indRackHotspotC01R01").isOn());
        assertEquals(COLOR_HOTSPOT_RACK, f.controls.indicatorsRack().get("indRackC01R01").getOnColor());
        assertEquals(1, active.size());
        assertTrue(active.values().iterator().next().contains("Thermal protection shutdown"));
        assertEquals(NotificationSeverity.ERROR, severities.values().iterator().next());
        String originalId = active.keySet().iterator().next();

        // Cold enough, but MANUAL restart has not been requested: the shutdown remains an emergency.
        f.temperatures.setTemperatureCelsius(f.protectedServer.getCode(), 60);
        f.protectedServer.evaluateThermalProtection(60, 120);
        f.publish(2); f.update(); notifier.update(f.simulation.healthSnapshot());
        notifier.update(f.simulation.healthSnapshot());
        assertEquals(Set.of(originalId), active.keySet());
        assertEquals("CRITICAL", f.text("lblSystemStatusValue"));
        assertEquals("0", f.text("lblRoomOnlineServersValue"));

        f.protectedServer.requestThermalRestart();
        f.protectedServer.evaluateThermalProtection(60, 180);
        f.protectedServer.setUtilization(.5);
        f.publish(3); f.update(); notifier.update(f.simulation.healthSnapshot());
        assertTrue(active.isEmpty());
        assertEquals("1", f.text("lblRoomOnlineServersValue"));
        assertEquals("60.0°C", f.text("lblAverageServerTemperatureValue"));
        assertEquals("AVG SERVER:60.0°C", f.text("lblTrendsRoomTemperature"));
        assertEquals("C01-R01", f.text("lblRoomMaximumRackTemperatureLocation"));
        assertEquals("NORMAL", f.text("lblSystemStatusValue"));
        assertFalse(f.controls.indicatorsRackCondition().get("indRackHotspotC01R01").isOn());
        assertEquals(HardwareStatus.OFFLINE, f.operatorOfflineServer.getStatus());
    }

    @Test
    void partiallyOnlineCriticalRackUsesHotspotInsteadOfOfflineIcon() {
        var f = new Fixture();
        f.protectedServer.evaluateThermalProtection(110, 0);
        f.operatorOfflineServer.setStatus(HardwareStatus.OK);
        f.operatorOfflineServer.setUtilization(.5);
        f.temperatures.setTemperatureCelsius(f.operatorOfflineServer.getCode(), 60);
        f.publish(1); f.update();
        assertTrue(f.controls.indicatorsRackCondition().get("indRackHotspotC01R01").isOn());
        assertFalse(f.controls.indicatorsRackCondition().get("indRackOfflineC01R01").isOn());
        assertEquals(COLOR_HOTSPOT_RACK, f.controls.indicatorsRack().get("indRackC01R01").getOnColor());
        assertEquals("CRITICAL", f.text("lblSystemStatusValue"));
    }

    @Test
    void operatorOfflineIsIdleAndReadableWithoutFalseCriticality() {
        var f = new Fixture();
        f.protectedServer.setStatus(HardwareStatus.OFFLINE);
        f.publish(1); f.update();
        assertEquals("0", f.text("lblRoomOnlineServersValue"));
        assertEquals("IDLE", f.text("lblSystemStatusValue"));
        assertTrue(f.controls.indicatorsRackCondition().get("indRackOfflineC01R01").isOn());
        assertFalse(f.controls.indicatorsRackCondition().get("indRackHotspotC01R01").isOn());
        assertEquals(COLOR_MIN_TEMPERATURE, f.controls.indicatorsRack().get("indRackC01R01").getOnColor());
        assertEquals(COLOR_WHITE_LABEL, f.controls.labels().get("lblSystemStatusValue").getTextColor());
        assertTrue(HealthPresentation.criticalRacks(f.simulation.healthSnapshot()).isEmpty());
    }

    @Test
    void throttlingIsVisibleEvenWhenNoOrdinaryHealthThresholdWasCrossed() {
        var item = new ServerHealthSnapshot("test", "C01", new RackCode("R01"), "S01",
                HardwareStatus.OK, Set.of(), .4, 50, ThermalAlertLevel.NORMAL, ThermalProtectionState.THROTTLED);
        var snapshot = new HealthSnapshot(1, 60, List.of(item));
        assertEquals("WARNING", HealthPresentation.systemStatus(snapshot));
        assertEquals(NotificationSeverity.WARNING, HealthPresentation.notificationSeverity(item));
        var messages = new ArrayList<String>();
        var notifier = new SimulationAlertNotifier((severity, message) -> { messages.add(message); return "test"; }, id -> {});
        notifier.update(snapshot);
        assertTrue(messages.getFirst().contains("Thermal throttling"));
    }

    @Test
    void standbyPowerUsesReadableUnitsAndMissingTemperaturesAreNotInvented() {
        assertEquals("5W", MetricPresentation.power(5, "%.2fkW", "%.2fMW"));
        assertEquals("50W", MetricPresentation.power(50, "%.2fkW", "%.2fMW"));
        assertEquals("4.22kW", MetricPresentation.power(4215, "%.2fkW", "%.2fMW"));
        assertEquals("1.25MW", MetricPresentation.power(1250000, "%.2fkW", "%.2fMW"));
        assertEquals("--", MetricPresentation.power(Double.NaN, "%.2fkW"));
        assertEquals("--", MetricPresentation.temperature(Double.NaN, "%.1f°C"));
        assertEquals("--", MetricPresentation.temperature(Double.POSITIVE_INFINITY, "%.1f°C"));
    }

    @Test
    void roomAirTemperatureUsesTheSharedGradientAndShowsMissingDataExplicitly() {
        var f = new Fixture();
        f.publish(1);
        f.room.update(20, 110);
        var label = f.controls.labels().get("lblRoomAirTemperatureValue");
        assertEquals("--", label.getText());
        assertEquals(COLOR_WHITE_LABEL, label.getTextColor());
        var snapshot = f.simulation.operationalSnapshot();
        for (double air : new double[] {20, 65, 110, 160}) {
            f.simulation.setOperationalSnapshot(new DatacenterOperationalSnapshot(
                    snapshot.tickIndex(), snapshot.elapsedSeconds(), snapshot.racks(), snapshot.columns(), snapshot.serverGroups(),
                    snapshot.averageOnlineServerTemperatureCelsius(), snapshot.hottestRackLocation(),
                    snapshot.hottestRackAverageTemperatureCelsius(), snapshot.totalItUtilization(),
                    snapshot.idleItPowerWatts(), snapshot.maxItPowerWatts(), snapshot.currentItPowerWatts(),
                    snapshot.coolingPowerWatts(), snapshot.totalFacilityPowerWatts(), snapshot.pue(),
                    Optional.of(new RoomAirTemperatureSnapshot(snapshot.tickIndex(), snapshot.elapsedSeconds(), air)),
                    snapshot.rackCoolingSnapshot(), snapshot.facilityEnergySnapshot()));
            f.room.update(20, 110);
            assertEquals(String.format(Locale.ROOT, "%.1f°C", air), label.getText());
            if (air == 20) assertEquals(COLOR_MIN_TEMPERATURE, label.getTextColor());
            else if (air >= 110) assertEquals(COLOR_MAX_TEMPERATURE, label.getTextColor());
            else {
                assertNotEquals(COLOR_MIN_TEMPERATURE, label.getTextColor());
                assertNotEquals(COLOR_MAX_TEMPERATURE, label.getTextColor());
            }
        }
    }

    @Test
    void globalStatusUsesThePolicyFromTheLoadedDatacenterJson() {
        var f = new Fixture();
        var definition = new com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader()
                .load(java.nio.file.Path.of("data/config/datacenter.json"));
        f.simulation.setDatacenterDefinition(definition);
        f.temperatures.setTemperatureCelsius(f.protectedServer.getCode(), 24);
        f.protectedServer.setUtilization(1);
        f.publish(1); f.update();
        assertTrue(f.simulation.healthSnapshot().servers().stream()
                .anyMatch(server -> server.hasAlertReason(ServerAlertReason.HIGH_UTILIZATION)));
        assertEquals("NORMAL", f.text("lblSystemStatusValue"));
        assertFalse(f.controls.indicators().get("indSystemStatusWarning").isOn());
        assertEquals("1", f.text("lblRoomOnlineServersValue"));
    }

    private static final class Fixture {
        final Sketch sketch = new Sketch();
        final UiComponentContainer controls = new UiComponentContainer();
        final SimulationContainer simulation = new SimulationContainer();
        final Server protectedServer;
        final Server operatorOfflineServer;
        final Datacenter dc;
        final TemperatureSystem temperatures;
        final TemperatureSystemOptions options = new TemperatureSystemOptions(24, 110, 5000, 8);
        final ServerHealthSystem health;
        final EnergyConsumptionSystem energy;
        final DatacenterOperationalSnapshotProvider operational;
        final HeaderUpdater header;
        final GlobalOverviewUpdater global;
        final RoomPanelUpdater room;

        Fixture() {
            var location = new RackLocation("C01", "R01");
            var config = new ServerConfig("M", "CPZ", "Test", 100, 300, null,
                    new ServerThermalProtection(true, 90, 85, .4, 105, 70, 120, ThermalRestartPolicy.MANUAL, 5));
            protectedServer = new Server(new ServerLocation("C01", "R01", "S01"), config,
                    HardwareStatus.OK, ServerRole.GENERAL_PURPOSE);
            operatorOfflineServer = new Server(new ServerLocation("C01", "R01", "S02"), config,
                    HardwareStatus.OFFLINE, ServerRole.GENERAL_PURPOSE);
            dc = new Datacenter(List.of(new Rack(new RackCode("R01"), location, List.of("S01", "S02"))),
                    List.of(protectedServer, operatorOfflineServer));
            temperatures = new TemperatureSystem(dc, options, new SimpleServerTemperatureModel());
            health = new ServerHealthSystem(dc, temperatures, ServerHealthOptions.defaults());
            energy = new EnergyConsumptionSystem(dc);
            var groups = IntStream.rangeClosed(1, 5).mapToObj(i -> new ServerGroupDefinition("HA%02d".formatted(i),
                    Set.of(protectedServer.getLocation(), operatorOfflineServer.getLocation()))).toList();
            operational = new DatacenterOperationalSnapshotProvider(dc, groups);
            simulation.setDatacenter(dc);

            var labels = new HashMap<String, Label>();
            for (String code : List.of("lblRoom", "lblTime", "lblDate", "lblSimulationTime",
                    "lblRoomTotalServersValue", "lblRoomOnlineServersValue", "lblAverageServerTemperatureValue", "lblRoomAirTemperatureValue",
                    "lblRoomMaximumRackTemperatureValue", "lblRoomMaximumRackTemperatureLocation", "lblSystemStatusValue",
                    "lblTrendsMaximumTemperatureServer", "lblTrendsRoomTemperature", "lblTrendsItLoad", "lblTrendsTotalLoad"))
                labels.put(code, new Label(sketch, code, "STALE", 0, 0, 100, 20));
            controls.setLabels(labels);
            var indicators = new HashMap<String, Indicator>();
            for (String code : List.of("indSimulationRunning", "indSystemStatusNormal", "indSystemStatusWatch",
                    "indSystemStatusWarning", "indSystemStatusCritical", "indRoomHotAisleC01", "indRoomHotAisleC02-C03",
                    "indRoomHotAisleC04-C05", "indRoomHotAisleC06-C07", "indRoomHotAisleC08"))
                indicators.put(code, new Indicator(sketch, code, 0, 0, 10, 10));
            controls.setIndicators(indicators);
            controls.setIndicatorsRack(Map.of("indRackC01R01", new Indicator(sketch, "indRackC01R01", 0, 0, 10, 10)));
            var conditions = new HashMap<String, Indicator>();
            for (String type : List.of("Offline", "Empty", "Hotspot", "AI")) {
                String code = "indRack" + type + "C01R01";
                conditions.put(code, new Indicator(sketch, code, 0, 0, 10, 10));
            }
            controls.setIndicatorsRackCondition(conditions);
            var context = new ApplicationContext(sketch);
            var format = new UiFormatContainer();
            format.setTemperature("%.1f°C"); format.setPowerKw("%.2fkW"); format.setPowerMw("%.2fMW");
            var selection = new SelectionManager(context, simulation, controls) {
                @Override public String selectedColumn() { return "C01"; }
                @Override public String selectedRack() { return "R01"; }
            };
            header = new HeaderUpdater(context, simulation, controls);
            global = new GlobalOverviewUpdater(context, simulation, controls, format, selection);
            room = new RoomPanelUpdater(context, simulation, controls, format);
        }

        void publish(int index) {
            var tick = new SimulationTick(index, Duration.ofMinutes(index), Duration.ofMinutes(1));
            new PowerConsumptionSystem(dc).update(tick);
            health.update(tick); energy.update(tick);
            var e = new EnergyConsumptionSnapshotProvider(dc, energy).snapshot(tick);
            var t = new TemperatureSnapshotProvider(dc, temperatures, options).snapshot(tick);
            var h = new HealthSnapshotProvider(dc, health, temperatures).snapshot(tick);
            simulation.setEnergySnapshot(e); simulation.setTemperatureSnapshot(t); simulation.setHealthSnapshot(h);
            simulation.setOperationalSnapshot(operational.snapshot(e, t, h));
        }

        void update() { header.update(); global.update(); room.update(20, 110); }
        String text(String code) { return controls.labels().get(code).getText(); }
    }
}
