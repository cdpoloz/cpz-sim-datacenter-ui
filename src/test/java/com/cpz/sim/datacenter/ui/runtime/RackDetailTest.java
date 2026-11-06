package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.history.DatacenterSimulationStepSnapshot;
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.snapshot.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RackDetailTest {
    @TempDir Path tempDir;
    private RuntimeSession session() throws Exception {
        var props = new Properties(); try (var in = Files.newInputStream(Path.of("data/config.properties"))) { props.load(in); }
        return new RuntimeSession(RoomMap.loadDefinition(), props);
    }
    @Test void joinsFullLocationsRegardlessOfSnapshotOrderAndKeepsRealSlots() throws Exception {
        var session = session(); var room = RoomMap.load(); var snap = session.snapshot();
        var a = room.racks().stream().filter(r -> r.location().equals(new RackLocation("C01", "R01"))).findFirst().orElseThrow();
        var b = room.racks().stream().filter(r -> r.location().equals(new RackLocation("C02", "R01"))).findFirst().orElseThrow();
        var energies = new ArrayList<>(snap.energySnapshot().servers()); Collections.reverse(energies);
        var temperatures = new ArrayList<>(snap.temperatureSnapshot().servers()); Collections.rotate(temperatures, 4);
        var health = new ArrayList<>(snap.healthSnapshot().servers()); Collections.reverse(health);
        var reordered = copy(snap, energies, temperatures, health);
        var first = RackDetail.project(a, session.serverRoles(), reordered);
        var second = RackDetail.project(b, session.serverRoles(), reordered);
        assertEquals(a.slots(), first.rows().stream().map(r -> r.location().slot()).toList());
        assertEquals(snap.operationalSnapshot().getRack(a.location()), first.summary());
        for (var row : first.rows()) if (row.installed()) {
            assertEquals(row.location(), row.energy().location()); assertEquals(row.location(), row.temperature().location()); assertEquals(row.location(), row.health().location());
            assertEquals(session.serverRoles().get(row.location()), row.role());
        }
        assertTrue(first.rows().stream().allMatch(r -> r.location().column().equals("C01")));
        assertTrue(second.rows().stream().allMatch(r -> r.location().column().equals("C02")));
        assertTrue(first.rows().stream().anyMatch(r -> !r.installed()));
        session.step(); assertEquals(session.snapshot().operationalSnapshot().getRack(a.location()), RackDetail.project(a, session.serverRoles(), session.snapshot()).summary());
    }
    private DatacenterSimulationStepSnapshot copy(DatacenterSimulationStepSnapshot snap, List<ServerEnergySnapshot> e, List<ServerTemperatureSnapshot> t, List<ServerHealthSnapshot> h) {
        var original = snap.energySnapshot();
        return new DatacenterSimulationStepSnapshot(new EnergyConsumptionSnapshot(original.tickIndex(), original.elapsedSeconds(), original.totalItPowerWatts(), original.consumedEnergyWh(), e),
                new TemperatureSnapshot(snap.tickIndex(), snap.elapsedSeconds(), snap.temperatureSnapshot().ambientTemperatureCelsius(), t),
                new HealthSnapshot(snap.tickIndex(), snap.elapsedSeconds(), h), snap.coolingSnapshot(), snap.operationalSnapshot(), snap.coupledThermalSnapshot());
    }
    @Test void distinguishesMissingHealthAlertAndOfflineFromEmpty() throws Exception {
        var session = session(); var snap = session.snapshot(); var location = new ServerLocation("C01", "R01", "S01");
        var rack = RoomMap.load().racks().stream().filter(r -> r.location().equals(location.rackLocation())).findFirst().orElseThrow();
        var h = new ArrayList<>(snap.healthSnapshot().servers());
        var old = h.stream().filter(s -> s.location().equals(location)).findFirst().orElseThrow(); h.remove(old);
        var missing = RackDetail.project(rack, session.serverRoles(), copy(snap, snap.energySnapshot().servers(), snap.temperatureSnapshot().servers(), h)).rows().getFirst();
        assertTrue(missing.installed()); assertTrue(missing.incomplete()); assertNull(missing.health());
        h.add(new ServerHealthSnapshot(old.serverCode(), old.column(), old.rackCode(), old.slot(), HardwareStatus.ALERT,
                Set.of(ServerAlertReason.HIGH_UTILIZATION, ServerAlertReason.HIGH_TEMPERATURE), old.utilization(), old.temperatureCelsius()));
        var alert = RackDetail.project(rack, session.serverRoles(), copy(snap, snap.energySnapshot().servers(), snap.temperatureSnapshot().servers(), h)).rows().getFirst();
        assertTrue(alert.health().hasAlertReason(ServerAlertReason.HIGH_TEMPERATURE)); assertTrue(alert.health().hasAlertReason(ServerAlertReason.HIGH_UTILIZATION));
        h.remove(h.size()-1); h.add(new ServerHealthSnapshot(old.serverCode(), old.column(), old.rackCode(), old.slot(), HardwareStatus.OFFLINE, Set.of(), old.utilization(), old.temperatureCelsius()));
        var offline = RackDetail.project(rack, session.serverRoles(), copy(snap, snap.energySnapshot().servers(), snap.temperatureSnapshot().servers(), h)).rows().getFirst();
        assertEquals(HardwareStatus.OFFLINE, offline.health().status()); assertNotNull(offline.energy()); assertNotNull(offline.temperature());
        assertEquals(RackDisplay.OFFLINE, RackPanel.indicatorColor(offline, session.range()));
        var empty = new RackDetail.Row(new ServerLocation("C01", "R01", "S07"), null, null, null, null);
        assertEquals(com.cpz.sim.datacenter.ui.util.Constants.COLOR_EMPTY_RACK, RackPanel.indicatorColor(empty, session.range()));
        var noTemperature = new RackDetail.Row(location, ServerRole.GENERAL_PURPOSE, null, missing.energy(), missing.health());
        assertEquals(RackDisplay.UNAVAILABLE, RackPanel.indicatorColor(noTemperature, session.range()));
        var t = alert.temperature();
        var cool = new ServerTemperatureSnapshot(t.serverCode(), t.column(), t.rackCode(), t.slot(), t.status(), t.utilization(), t.currentPowerWatts(), 20);
        var hot = new ServerTemperatureSnapshot(t.serverCode(), t.column(), t.rackCode(), t.slot(), t.status(), t.utilization(), t.currentPowerWatts(), 80);
        var range = new com.cpz.sim.datacenter.ui.simulation.TemperatureRange(20, 80);
        assertNotEquals(RackPanel.indicatorColor(new RackDetail.Row(location, alert.role(), cool, alert.energy(), alert.health()), range),
                RackPanel.indicatorColor(new RackDetail.Row(location, alert.role(), hot, alert.energy(), alert.health()), range));
    }
    @Test void panelGeometryFitsForDifferentSlotCountsAndSurfaces() {
        for (int[] size : new int[][]{{1920,1011},{800,800}}) for (int count : new int[]{0,3,12}) {
            var layout = new RuntimeLayout(size[0], size[1]); var g = RackPanel.geometry(layout, size[0], size[1], count);
            var content = layout.contentBounds(RuntimeLayout.Area.SELECTED_RACK).resolve(size[0], size[1]);
            var all = new ArrayList<com.cpz.processing.controls.controls.geometry.ControlBounds>(List.of(g.title(),g.summary(),g.columns(),g.legend())); all.addAll(g.rows());
            for (var bounds : all) { var b = bounds.resolve(size[0], size[1]); assertTrue(b.x() >= content.x()-.01 && b.y() >= content.y()-.01 && b.x()+b.width() <= content.x()+content.width()+.01 && b.y()+b.height() <= content.y()+content.height()+.01); }
            assertEquals(count, g.rows().size());
            assertEquals(count, g.indicators().size());
            for (int i = 0; i < count; i++) {
                var row = g.rows().get(i).resolve(size[0], size[1]); var indicator = g.indicators().get(i).resolve(size[0], size[1]);
                assertTrue(indicator.x() >= content.x()-.01 && indicator.y() >= row.y()-.01);
                assertTrue(indicator.x()+indicator.width() <= row.x()+.01 && indicator.y()+indicator.height() <= row.y()+row.height()+.01);
                assertTrue(row.x()+row.width() <= content.x()+content.width()+.01);
            }
            var ordered = new ArrayList<com.cpz.processing.controls.controls.geometry.ControlBounds>(List.of(g.title(),g.summary(),g.columns())); ordered.addAll(g.rows()); ordered.add(g.legend());
            for (int i = 1; i < ordered.size(); i++) {
                var previous = ordered.get(i-1).resolve(size[0], size[1]); var next = ordered.get(i).resolve(size[0], size[1]);
                assertTrue(previous.y()+previous.height() <= next.y()+.01);
            }
        }
    }
    @Test void provenanceReportsSimulatedInputsAndGranularity() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String mode : List.of("UTILIZATION_DRIVEN", "POWER_DRIVEN", "TEMPERATURE_DRIVEN")) {
            var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(Path.of("data/config/datacenter.json").toFile());
            root.put("dataInputMode", mode);
            if (mode.equals("POWER_DRIVEN")) root.put("powerInputGranularity", "RACK");
            if (mode.equals("TEMPERATURE_DRIVEN")) { root.remove("rackCooling"); root.remove("roomAirTemperature"); root.put("temperatureInputGranularity", "AISLE"); }
            var path = tempDir.resolve(mode + ".json"); mapper.writeValue(path.toFile(), root);
            var definition = new com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader().load(path);
            String legend = RackDetail.provenance(definition);
            assertTrue(legend.contains("simulada"));
            if (mode.equals("POWER_DRIVEN")) assertTrue(legend.contains("W/servidor inferida"));
            if (mode.equals("TEMPERATURE_DRIVEN")) assertTrue(legend.contains("AISLE") && legend.contains("asignada"));
        }
    }
    @Test void backendAcceptsDifferentSlotCountOrderAndNonConsecutiveCodes() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(Path.of("data/config/datacenter.json").toFile());
        var layout = (com.fasterxml.jackson.databind.node.ObjectNode) root.get("layout");
        for (var node : layout.withArray("racks")) {
            if (node.path("column").asText().equals("C01") && node.path("code").asText().equals("R01")) {
                var slots = mapper.createArrayNode(); slots.add("S02"); slots.add("S01"); slots.add("EMPTY-X");
                ((com.fasterxml.jackson.databind.node.ObjectNode) node).set("slots", slots);
            }
        }
        var retained = mapper.createArrayNode();
        for (var server : root.withArray("servers")) {
            boolean rack = server.path("column").asText().equals("C01") && server.path("rackCode").asText().equals("R01");
            if (server.path("column").asText().equals("C02") && server.path("rackCode").asText().equals("R01") && server.path("slot").asText().equals("S01")) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) server).put("role", "AI").put("workloadFactor", .1);
            }
            if (!rack || Set.of("S01", "S02").contains(server.path("slot").asText())) retained.add(server);
        }
        root.set("servers", retained);
        var path = tempDir.resolve("variant.json"); mapper.writeValue(path.toFile(), root);
        var definition = new com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader().load(path);
        var props = new Properties(); try (var in = Files.newInputStream(Path.of("data/config.properties"))) { props.load(in); }
        var session = new RuntimeSession(definition, props); var room = new RoomMap(definition.layout());
        var a = room.racks().stream().filter(r -> r.location().equals(new RackLocation("C01", "R01"))).findFirst().orElseThrow();
        var b = room.racks().stream().filter(r -> r.location().equals(new RackLocation("C02", "R01"))).findFirst().orElseThrow();
        var first = RackDetail.project(a, session.serverRoles(), session.snapshot());
        assertEquals(List.of("S02", "S01", "EMPTY-X"), first.rows().stream().map(r -> r.location().slot()).toList());
        assertFalse(first.rows().getLast().installed());
        var second = RackDetail.project(b, session.serverRoles(), session.snapshot());
        assertEquals(12, second.rows().size());
        var c01 = first.rows().stream().filter(row -> row.location().slot().equals("S01")).findFirst().orElseThrow();
        var c02 = second.rows().stream().filter(row -> row.location().slot().equals("S01")).findFirst().orElseThrow();
        assertEquals(ServerRole.GENERAL_PURPOSE, c01.role()); assertEquals(ServerRole.AI, c02.role());
        assertNotEquals(c01.energy().utilization(), c02.energy().utilization());
        var sketch = new processing.core.PApplet(); sketch.width = 1920; sketch.height = 1011;
        long tick = session.snapshot().tickIndex();
        var panel = new RackPanel(sketch, null, room, session, new RuntimeLayout(sketch.width, sketch.height));
        var rowsField = RackPanel.class.getDeclaredField("rows"); rowsField.setAccessible(true);
        var indicatorsField = RackPanel.class.getDeclaredField("indicators"); indicatorsField.setAccessible(true);
        room.select(b.location()); panel.refresh(); assertEquals(12, ((List<?>) rowsField.get(panel)).size());
        var labels = (List<?>) rowsField.get(panel); var indicators = (List<?>) indicatorsField.get(panel);
        assertEquals(12, indicators.size());
        var indicator = (com.cpz.processing.controls.controls.indicator.Indicator) indicators.get(0);
        var label = (com.cpz.processing.controls.controls.label.Label) labels.get(0);
        assertEquals(com.cpz.sim.datacenter.ui.util.Constants.COLOR_WHITE_LABEL, label.getTextColor());
        session.step(); panel.refresh();
        assertSame(indicator, ((List<?>) indicatorsField.get(panel)).get(0));
        assertEquals(RackPanel.indicatorColor(RackDetail.project(b, session.serverRoles(), session.snapshot()).rows().getFirst(), session.range()), indicator.getOnColor());
        for (Object item : (List<?>) rowsField.get(panel)) assertEquals(com.cpz.sim.datacenter.ui.util.Constants.COLOR_WHITE_LABEL, ((com.cpz.processing.controls.controls.label.Label)item).getTextColor());
        sketch.width = 800; sketch.height = 800;
        var resizedLayout = new RuntimeLayout(800, 800); panel.resize(resizedLayout, 800, 800);
        var actual = indicator.getTooltipBounds(); var expected = RackPanel.geometry(resizedLayout, 800, 800, 12).indicators().getFirst().resolve(800, 800);
        assertEquals(expected.x(), actual.x(), .001f); assertEquals(expected.y(), actual.y(), .001f);
        tick = session.snapshot().tickIndex();
        room.select(a.location()); panel.refresh(); assertEquals(3, ((List<?>) rowsField.get(panel)).size());
        assertEquals(3, ((List<?>) indicatorsField.get(panel)).size());
        room.clearSelection(); panel.refresh(); assertEquals(0, ((List<?>) rowsField.get(panel)).size());
        assertEquals(0, ((List<?>) indicatorsField.get(panel)).size());
        assertEquals(tick, session.snapshot().tickIndex());
    }
}
