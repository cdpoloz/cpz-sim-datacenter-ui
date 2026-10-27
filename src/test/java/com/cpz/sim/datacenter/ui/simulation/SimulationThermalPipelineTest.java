package com.cpz.sim.datacenter.ui.simulation;

import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.controls.toggle.Toggle;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.ui.controls.SelectedRackCoolingControls;
import com.cpz.sim.datacenter.ui.ui.UiInteractionController;
import com.cpz.sim.datacenter.ui.ui.UiStateContainer;
import java.util.HashMap;
import java.util.Locale;
import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.factory.*;
import com.cpz.sim.datacenter.health.ServerHealthOptions;
import com.cpz.sim.datacenter.history.*;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.snapshot.*;
import com.cpz.sim.datacenter.system.*;
import com.cpz.sim.datacenter.temperature.*;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;
import com.cpz.sim.datacenter.ui.ui.UiFormatContainer;
import com.cpz.sim.datacenter.ui.ui.panel.GlobalOverviewUpdater;
import com.cpz.sim.datacenter.workload.ConstantWorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import processing.core.PApplet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SimulationThermalPipelineTest {

    @TempDir
    Path directory;

    private record Fixture(DatacenterDefinition definition, Datacenter datacenter,
                           TemperatureSystem temperatures, SimulationEngine engine,
                           SimulationThermalPipeline pipeline, DatacenterSimulationHistoryRecorder recorder, CoolingSystem cooling) {
        DatacenterSimulationStepSnapshot step() {
            return recorder.record(engine.step());
        }
    }

    private DatacenterDefinition definition(String mode, boolean coupled, String granularity) throws Exception {
        return definition(mode, coupled, granularity, null);
    }

    private DatacenterDefinition definition(String mode, boolean coupled, String granularity, String destination) throws Exception {
        var mapper = JsonMapper.builder().build();
        ObjectNode root = (ObjectNode) mapper.readTree(Files.readString(Path.of("data/config/datacenter.json")));
        if (destination == null) root.remove("rackCooling");
        else ((ObjectNode) root.path("rackCooling").path("units").get(0)).put("heatRejectionDestination", destination);
        root.put("dataInputMode", mode);
        root.put("temperatureInputGranularity", granularity);
        // Test parameters only, not calibrated production defaults.
        if (coupled) root.set("roomAirTemperature", mapper.readTree("""
                {"initialTemperatureCelsius":24.0,"effectiveThermalCapacityJoulesPerCelsius":2000000.0,
                 "environmentTemperatureCelsius":24.0,"environmentHeatTransferWattsPerCelsius":0.0}
                """));
        else root.remove("roomAirTemperature");
        Path file = directory.resolve("datacenter.json");
        Files.writeString(file, root.toString());
        return new JsonDatacenterConfigLoader().load(file);
    }

    private Fixture fixture(String mode, boolean coupled, String granularity) throws Exception {
        return fixture(definition(mode, coupled, granularity));
    }

    private Fixture fixture(DatacenterDefinition definition) {
        var dc = new DatacenterFactory().create(definition);
        var options = new TemperatureSystemOptionsFactory().create(definition);
        var cooling = new CoolingSystem(new CoolingConfigurationFactory().create(definition, dc).orElseThrow());
        var reference = new CoolingSnapshotTemperatureReferenceProvider(cooling.configuration());
        var temperatures = new TemperatureSystem(dc, options, new SimpleServerTemperatureModel(), reference);
        var engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(1)));
        var pipeline = new SimulationThermalPipeline(engine, definition, dc, temperatures, options, cooling,
                reference, new ConstantWorkloadSource(0.5),
                (server, tick) -> (server.getConfig().idlePowerWatts() + server.getConfig().maxPowerWatts()) / 2.0);
        var health = new ServerHealthSystem(dc, temperatures, ServerHealthOptions.defaults());
        var energy = new EnergyConsumptionSystem(dc);
        engine.register(health);
        engine.register(energy);
        var recorder = new DatacenterSimulationHistoryRecorder(new EnergyConsumptionSnapshotProvider(dc, energy),
                new TemperatureSnapshotProvider(dc, temperatures, options), new HealthSnapshotProvider(dc, health, temperatures),
                new DatacenterOperationalSnapshotProvider(dc), pipeline::coolingSnapshot,
                new DatacenterSimulationHistory(), Optional.empty(), pipeline::coupledThermalSnapshot);
        return new Fixture(definition, dc, temperatures, engine, pipeline, recorder, cooling);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTILIZATION_DRIVEN", "POWER_DRIVEN"})
    void coupledUiPipelineCapturesAirCoolingHistoryAndResetsWithoutDuplicateThermalOwners(String mode) throws Exception {
        var f = fixture(mode, true, "RACK");
        assertEquals(mode.equals("UTILIZATION_DRIVEN") ? 5 : 4, f.engine.systemCount());
        assertTrue(f.pipeline.coolingSnapshot().isEmpty());
        assertTrue(f.pipeline.coupledThermalSnapshot().isEmpty());
        var first = f.step();
        for (int i = 0; i < 2; i++) f.step();
        var latest = f.recorder.history().latest().orElseThrow();
        assertEquals(3, f.recorder.history().size());
        assertTrue(latest.operationalSnapshot().roomAirSnapshot().isPresent());
        assertNotEquals(latest.operationalSnapshot().averageOnlineServerTemperatureCelsius(),
                latest.operationalSnapshot().roomAirTemperatureCelsius());
        double expectedElectricalCooling = latest.coolingSnapshot().orElseThrow().units().stream()
                .mapToDouble(CoolingUnitSnapshot::currentElectricalPowerWatts).sum();
        assertEquals(expectedElectricalCooling, latest.operationalSnapshot().coolingPowerWatts());
        assertEquals(0.0, latest.coupledThermalSnapshot().orElseThrow().energyBalanceResidualJoules(), 0.002);
        f.engine.reset();
        f.recorder.clear();
        assertTrue(f.pipeline.coolingSnapshot().isEmpty());
        assertTrue(f.pipeline.coupledThermalSnapshot().isEmpty());
        assertTrue(f.recorder.history().isEmpty());
        assertEquals(first.operationalSnapshot().roomAirTemperatureCelsius(),
                f.step().operationalSnapshot().roomAirTemperatureCelsius(), 1e-10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RACK", "AISLE"})
    void observedTemperatureRemainsAuthoritativeAndAirUnavailable(String granularity) throws Exception {
        var f = fixture("TEMPERATURE_DRIVEN", false, granularity);
        assertEquals(4, f.engine.systemCount());
        var snapshot = f.step();
        var source = new TemperatureInputSourceFactory().create(f.definition);
        for (var server : f.datacenter.getServers()) {
            if (server.getStatus() == HardwareStatus.OFFLINE) continue;
            double expected = source.temperatureCelsius(server.getLocation().rackLocation(), f.engine.currentTick());
            assertEquals(expected, f.temperatures.getThermalState(server.getCode()).getTemperatureCelsius(), 1e-12);
        }
        assertTrue(snapshot.operationalSnapshot().roomAirSnapshot().isEmpty());
        assertTrue(Double.isNaN(snapshot.operationalSnapshot().roomAirTemperatureCelsius()));
        assertTrue(snapshot.coupledThermalSnapshot().isEmpty());
    }

    @Test
    void unconfiguredUiRetainsLegacyThermalPipeline() throws Exception {
        var f = fixture("UTILIZATION_DRIVEN", false, "RACK");
        assertEquals(6, f.engine.systemCount());
        var first = f.step();
        assertTrue(Double.isFinite(first.operationalSnapshot().averageOnlineServerTemperatureCelsius()));
        assertTrue(Double.isNaN(first.operationalSnapshot().roomAirTemperatureCelsius()));
        assertTrue(first.coolingSnapshot().isPresent());
        f.engine.reset();
        f.recorder.clear();
        assertTrue(f.pipeline.coolingSnapshot().isEmpty());
        assertEquals(first.operationalSnapshot().averageOnlineServerTemperatureCelsius(),
                f.step().operationalSnapshot().averageOnlineServerTemperatureCelsius(), 1e-10);
    }

    @Test
    void uiKeepsServerAverageAndAirLabelsDistinctAndShowsUnavailableWithoutAirModel() throws Exception {
        var coupled = fixture("UTILIZATION_DRIVEN", true, "RACK");
        var legacy = fixture("UTILIZATION_DRIVEN", false, "RACK");
        var applet = new PApplet();
        var air = new Label(applet, "lblTrendsAirTemperature", "", 0.0f, 0.0f);
        var average = new Label(applet, "lblAverageServerTemperatureValue", "", 0.0f, 0.0f);
        var controls = new UiComponentContainer();
        controls.setLabels(Map.of(air.getCode(), air, average.getCode(), average));
        var formats = new UiFormatContainer();
        formats.setTemperature("%.1f°C");
        var container = new SimulationContainer();
        var updater = new GlobalOverviewUpdater(null, container, controls, formats, null);
        for (var fixture : new Fixture[]{coupled, legacy}) {
            var step = fixture.step();
            container.setOperationalSnapshot(step.operationalSnapshot());
            container.setSimulationHistory(fixture.recorder.history());
            updater.update();
            assertEquals(String.format(java.util.Locale.ROOT, "%.1f°C",
                    step.operationalSnapshot().averageOnlineServerTemperatureCelsius()), average.getText());
            assertEquals(fixture == legacy ? "AIR:--" : "AIR:" + String.format(java.util.Locale.ROOT, "%.1f°C",
                    step.operationalSnapshot().roomAirTemperatureCelsius()), air.getText());
        }
    }

    private UiComponentContainer rackCoolingControls() {
        var applet = new PApplet();
        var controls = new UiComponentContainer();
        Map<String, Label> labels = new HashMap<>();
        var hvac = new Label(applet, "lblRoomHvacLoadValue", "", 0.0f, 0.0f);
        labels.put(hvac.getCode(), hvac);
        controls.setLabels(labels);
        var toggle = new Toggle(applet, "tglSelectedRackCooling", 0.0f, 0.0f, 20.0f);
        controls.setToggles(Map.of(toggle.getCode(), toggle));
        return controls;
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROOM", "EXTERNAL"})
    void rackControlsUseSolverStateAndRecordedThermalMetricsWithoutReintegratingWhilePaused(String destination) throws Exception {
        var f = fixture(definition("UTILIZATION_DRIVEN", true, "RACK", destination));
        var simulation = new SimulationContainer();
        simulation.setThermalPipeline(f.pipeline);
        var controls = rackCoolingControls();
        var panel = new SelectedRackCoolingControls(simulation, controls);
        var state = new UiStateContainer();
        var interaction = new UiInteractionController(state, null, null, null, panel);
        var toggle = controls.toggles().get("tglSelectedRackCooling");
        toggle.setChangeListener(value -> interaction.handleToggleChange(toggle, value));
        var applet = new PApplet();
        applet.width = 1920;
        applet.height = 993;
        var overlays = new com.cpz.processing.controls.core.overlay.OverlayManager();
        var tooltips = new com.cpz.processing.controls.core.overlay.tooltip.util.TooltipOverlayController(applet, overlays);
        panel.registerTooltips(applet, tooltips);
        var tooltipIdentity = toggle.getTooltip();
        var tooltipInput = new com.cpz.processing.controls.core.overlay.tooltip.input.TooltipInputLayer(1000, tooltips);
        assertFalse(tooltipInput.handlePointerEvent(new com.cpz.processing.controls.core.input.PointerEvent(
                com.cpz.processing.controls.core.input.PointerEvent.Type.MOVE, 1400, 300)));
        assertTrue(overlays.getActiveOverlays().isEmpty());
        var rack = new RackLocation("C01", new RackCode("R01"));
        var system = f.pipeline.rackCoolingSystem().orElseThrow();
        String code = system.findUnitForRack(rack).orElseThrow().code();
        panel.update(rack);
        assertTrue(system.isEnabled(code), "programmatic projection must not turn equipment off");
        assertTrue(toggle.getTooltip().getText().contains("Air (last step): --"));
        assertEquals(1, toggle.getState());
        tooltips.refresh();
        assertEquals(1, overlays.getActiveOverlays().size());
        var first = f.step();
        simulation.setOperationalSnapshot(first.operationalSnapshot());
        panel.update(rack);
        var thermal = first.rackCoolingSnapshot().orElseThrow().findRack(rack).orElseThrow();
        assertTrue(toggle.getTooltip().getText().contains("State: ON"));
        assertTrue(toggle.getTooltip().getText().contains("Heat rejection: " + destination));
        assertTrue(toggle.getTooltip().getText().contains("Rated capacity: 4.0 kW"));
        assertTrue(toggle.getTooltip().getStyleConfig().minHeight >= 8 * toggle.getTooltip().getStyleConfig().textSize);
        assertTrue(toggle.getTooltip().getText().contains("Unit: " + code));
        assertTrue(toggle.getTooltip().getText().contains(String.format(Locale.ROOT,
                "Air (last step): %.1f°C", thermal.airTemperatureCelsius())));
        assertTrue(toggle.getTooltip().getText().contains(String.format(Locale.ROOT,
                "Cooling (last step): %.2f kW", thermal.actualCoolingPowerWatts() / 1000)));
        assertEquals(1000.0, first.operationalSnapshot().coolingPowerWatts(), 1e-9);
        assertEquals(first.operationalSnapshot().currentItPowerWatts() + 1000,
                first.operationalSnapshot().totalFacilityPowerWatts(), 1e-9);
        assertTrue(thermal.actualCoolingPowerWatts() > 0);
        simulation.setCoolingSnapshot(first.coolingSnapshot().orElseThrow());
        var formats = new UiFormatContainer();
        formats.setPercentage("%.1f%%");
        var overview = new GlobalOverviewUpdater(null, simulation, controls, formats, null);
        overview.update();
        assertEquals(String.format(Locale.ROOT, "%.1f%%", thermal.actualCoolingPowerWatts() / 4000 * 100),
                controls.labels().get("lblRoomHvacLoadValue").getText());
        assertNotEquals(first.operationalSnapshot().averageOnlineServerTemperatureCelsius(), thermal.airTemperatureCelsius());
        toggle.setState(0);
        assertFalse(system.isEnabled(code));
        tooltips.refresh();
        assertSame(tooltipIdentity, toggle.getTooltip());
        assertEquals(1, overlays.getActiveOverlays().size());
        assertTrue(state.updateUI());
        assertTrue(toggle.getTooltip().getText().contains("Electrical power: 0.00 kW"));
        assertTrue(toggle.getTooltip().getText().contains("State: OFF"));
        assertTrue(toggle.getTooltip().getText().contains(String.format(Locale.ROOT,
                "Cooling (last step): %.2f kW", thermal.actualCoolingPowerWatts() / 1000)));
        assertTrue(thermal.equipment().enabled(), "history remains immutable after an operator command");
        assertEquals(first.facilityEnergySnapshot(), f.pipeline.coupledThermalSnapshot().orElseThrow().facilityEnergySnapshot());
        assertEquals(1, f.recorder.history().size());
        var off = f.step();
        simulation.setOperationalSnapshot(off.operationalSnapshot());
        panel.update(rack);
        assertEquals(0, off.rackCoolingSnapshot().orElseThrow().totalActualCoolingPowerWatts());
        assertTrue(toggle.getTooltip().getText().contains("Cooling (last step): 0.00 kW"));
        assertEquals(0, off.operationalSnapshot().coolingPowerWatts());
        simulation.setCoolingSnapshot(off.coolingSnapshot().orElseThrow());
        overview.update();
        assertEquals("0.0%", controls.labels().get("lblRoomHvacLoadValue").getText());
        panel.update(new RackLocation("C01", new RackCode("R02")));
        assertFalse(toggle.isVisible());
        assertFalse(toggle.isEnabled());
        assertFalse(toggle.getTooltip().isEnabled());
        assertEquals("", toggle.getTooltip().getText());
        tooltips.refresh();
        assertTrue(overlays.getActiveOverlays().isEmpty());
        panel.changeState(1);
        assertFalse(system.isEnabled(code), "an uncooled rack must not control the previous selection");
        f.engine.reset();
        f.recorder.clear();
        simulation.setOperationalSnapshot(null);
        panel.update(rack);
        assertTrue(system.isEnabled(code));
        assertEquals(1, toggle.getState());
        assertTrue(toggle.getTooltip().getText().contains("Cooling (last step): --"));
        tooltips.refresh();
        assertEquals(1, overlays.getActiveOverlays().size());
        applet.width = 960;
        applet.height = 496;
        tooltips.refresh();
        assertTrue(overlays.getActiveOverlays().isEmpty(), "old pointer position is outside the resized rack");
        tooltips.showIfMouseOver(700, 150);
        assertEquals(1, overlays.getActiveOverlays().size());
        tooltips.showIfMouseOver(5, 5); // The same tooltip is available on the fan control.
        assertEquals(1, overlays.getActiveOverlays().size());
        tooltips.dispose();
        assertTrue(overlays.getActiveOverlays().isEmpty());
        assertEquals(thermal.airTemperatureCelsius(), f.step().rackCoolingSnapshot().orElseThrow()
                .findRack(rack).orElseThrow().airTemperatureCelsius(), 1e-10);
    }

    @Test
    void rackControlsSupportLegacyConfigurationWithoutInventingLocalAirTemperature() throws Exception {
        var f = fixture("UTILIZATION_DRIVEN", false, "RACK");
        var simulation = new SimulationContainer();
        simulation.setThermalPipeline(f.pipeline);
        simulation.setOperationalSnapshot(f.step().operationalSnapshot());
        var controls = rackCoolingControls();
        var panel = new SelectedRackCoolingControls(simulation, controls);
        panel.update(new RackLocation("C01", new RackCode("R01")));
        panel.changeState(1);
        assertTrue(f.pipeline.rackCoolingSystem().isEmpty());
        assertFalse(controls.toggles().get("tglSelectedRackCooling").isVisible());
        assertFalse(controls.toggles().get("tglSelectedRackCooling").getTooltip().isEnabled());
        assertEquals("", controls.toggles().get("tglSelectedRackCooling").getTooltip().getText());
    }

    @Test
    void selectedFanTargetsIndependentConfiguredUnitsWithoutCodeNamingConventions() throws Exception {
        var mapper = JsonMapper.builder().build();
        ObjectNode root = (ObjectNode) mapper.readTree(Files.readString(Path.of("data/config/datacenter.json")));
        var units = (com.fasterxml.jackson.databind.node.ArrayNode) root.path("rackCooling").path("units");
        var second = ((ObjectNode) units.get(0)).deepCopy();
        second.put("code", "independent-cooler").put("rackCode", "R02").put("initiallyEnabled", false)
                .put("heatRejectionDestination", "EXTERNAL");
        units.add(second);
        Path file = directory.resolve("two-racks.json");
        Files.writeString(file, root.toString());
        var f = fixture(new JsonDatacenterConfigLoader().load(file));
        var simulation = new SimulationContainer();
        simulation.setThermalPipeline(f.pipeline);
        var controls = rackCoolingControls();
        var panel = new SelectedRackCoolingControls(simulation, controls);
        var toggle = controls.toggles().get("tglSelectedRackCooling");
        toggle.setChangeListener(panel::changeState);
        var system = f.pipeline.rackCoolingSystem().orElseThrow();
        assertSame(system, f.pipeline.rackCoolingSystem().orElseThrow());
        var rack1 = new RackLocation("C01", new RackCode("R01"));
        var rack2 = new RackLocation("C01", new RackCode("R02"));
        var firstCode = system.findUnitForRack(rack1).orElseThrow().code();
        panel.update(rack1);
        panel.update(rack2);
        assertTrue(system.isEnabled(firstCode));
        assertFalse(system.isEnabled("independent-cooler"));
        toggle.setState(1);
        assertTrue(system.isEnabled(firstCode));
        assertTrue(system.isEnabled("independent-cooler"));
        panel.update(rack1);
        toggle.setState(0);
        assertFalse(system.isEnabled(firstCode));
        assertTrue(system.isEnabled("independent-cooler"));
        var step = f.step();
        assertEquals(2, step.rackCoolingSnapshot().orElseThrow().units().size());
        assertEquals(1000, step.operationalSnapshot().coolingPowerWatts());
    }

    @Test
    void roomReferenceBenchmarkExcludesRackNodesToPreserveItsOriginalComparison() throws Exception {
        var withRack = definition("UTILIZATION_DRIVEN", true, "RACK", "ROOM");
        var withoutRack = definition("UTILIZATION_DRIVEN", true, "RACK");
        var scenario = com.cpz.sim.datacenter.ui.example.RoomAirReferenceScenarios.Scenario.NONE;
        assertEquals(com.cpz.sim.datacenter.ui.example.RoomAirReferenceScenarios.run(withoutRack, scenario, 2),
                com.cpz.sim.datacenter.ui.example.RoomAirReferenceScenarios.run(withRack, scenario, 2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NO_UNIT", "DISABLED", "ROOM", "EXTERNAL", "ROOM_WITH_HVAC", "EXTERNAL_WITH_HVAC"})
    void generatedUiExamplesLoadAndDriveTheRealPipeline(String variant) throws Exception {
        var source = Path.of("data/config/datacenter.json");
        String original = Files.readString(source);
        var files = com.cpz.sim.datacenter.ui.example.RackCoolingExampleConfigurations.generate(source, directory);
        assertEquals(original, Files.readString(source));
        assertEquals(6, files.size());
        var definition = new JsonDatacenterConfigLoader().load(files.get(variant));
        var mapper = JsonMapper.builder().build();
        var originalJson = mapper.readTree(original);
        var generatedJson = mapper.readTree(Files.readString(files.get(variant)));
        for (String field : new String[]{"layout", "servers", "serverModels", "temperature", "roomAirTemperature"})
            assertEquals(originalJson.path(field), generatedJson.path(field), field);
        var f = fixture(definition);
        var step = f.step();
        var room = step.coolingSnapshot().orElseThrow();
        assertTrue(room.units().stream().allMatch(unit -> unit.enabled() == variant.endsWith("_WITH_HVAC")));
        var racks = step.rackCoolingSnapshot();
        assertEquals(!variant.equals("NO_UNIT"), racks.isPresent());
        double rackPower = 0;
        if (racks.isPresent()) {
            var unit = racks.orElseThrow().units().getFirst();
            assertEquals(!variant.equals("DISABLED"), unit.equipment().enabled());
            assertEquals(variant.startsWith("EXTERNAL") ? "EXTERNAL" : "ROOM", unit.heatRejectionDestination().name());
            rackPower = racks.orElseThrow().totalElectricalPowerWatts();
            assertEquals(variant.equals("DISABLED") ? 0.0 : 1000.0, rackPower);
        }
        assertEquals(room.totalElectricalPowerWatts() + rackPower, step.operationalSnapshot().coolingPowerWatts(), 1e-8);
        assertEquals(0, step.coupledThermalSnapshot().orElseThrow().energyBalanceResidualJoules(), 0.005);
        var controls = new UiComponentContainer();
        var toggles = new HashMap<String, Toggle>();
        var applet = new PApplet();
        for (var unit : room.units()) {
            String code = unit.unitCode().replace("SUPPLY-", "tglSupply").replace("EXHAUST-", "tglExhaust");
            toggles.put(code, new Toggle(applet, code, 0.0f, 0.0f, 20.0f));
        }
        for (String code : new String[]{"tglSupply", "tglExhaust"})
            toggles.put(code, new Toggle(applet, code, 0.0f, 0.0f, 20.0f));
        controls.setToggles(toggles);
        var simulation = new SimulationContainer();
        simulation.setCoolingSnapshot(room);
        simulation.setCoolingSystem(f.cooling);
        var manager = new com.cpz.sim.datacenter.ui.controls.CoolingToggleManager(null, simulation, controls);
        toggles.values().forEach(toggle -> toggle.setChangeListener(value -> manager.toggleCoolingUnit(toggle, value)));
        manager.initialize();
        int expected = variant.endsWith("_WITH_HVAC") ? 1 : 0;
        assertTrue(toggles.values().stream().allMatch(toggle -> toggle.getState() == expected));
        assertTrue(f.cooling.currentUnitSnapshots().stream().allMatch(unit -> unit.enabled() == (expected == 1)));
    }

    @Test
    void exampleGeneratorRejectsReplacingItsSource() throws Exception {
        var source = directory.resolve("room.json");
        Files.copy(Path.of("data/config/datacenter.json"), source);
        String original = Files.readString(source);
        assertThrows(IllegalArgumentException.class, () ->
                com.cpz.sim.datacenter.ui.example.RackCoolingExampleConfigurations.generate(source, directory));
        assertEquals(original, Files.readString(source));
        assertFalse(Files.exists(directory.resolve("no_unit.json")));
    }

    @Test
    void initialRoomToggleProjectionPreservesMixedStatesAndComputesAnyEnabledMasters() throws Exception {
        var f = fixture("UTILIZATION_DRIVEN", true, "RACK");
        String enabledCode = f.cooling.configuration().units().stream()
                .filter(unit -> unit.type() == com.cpz.sim.datacenter.cooling.CoolingUnitType.SUPPLY)
                .findFirst().orElseThrow().code();
        f.cooling.enable(enabledCode);
        var room = f.step().coolingSnapshot().orElseThrow();
        var controls = new UiComponentContainer();
        var toggles = new HashMap<String, Toggle>();
        var applet = new PApplet();
        for (var unit : room.units()) {
            String code = unit.unitCode().replace("SUPPLY-", "tglSupply").replace("EXHAUST-", "tglExhaust");
            toggles.put(code, new Toggle(applet, code, 0.0f, 0.0f, 20.0f));
        }
        for (String code : new String[]{"tglSupply", "tglExhaust"})
            toggles.put(code, new Toggle(applet, code, 0.0f, 0.0f, 20.0f));
        toggles.values().forEach(toggle -> toggle.setState(1));
        controls.setToggles(toggles);
        var simulation = new SimulationContainer();
        simulation.setCoolingSnapshot(room);
        simulation.setCoolingSystem(f.cooling);
        var manager = new com.cpz.sim.datacenter.ui.controls.CoolingToggleManager(null, simulation, controls);
        toggles.values().forEach(toggle -> toggle.setChangeListener(value -> manager.toggleCoolingUnit(toggle, value)));
        manager.initialize();
        for (var unit : room.units()) {
            String code = unit.unitCode().replace("SUPPLY-", "tglSupply").replace("EXHAUST-", "tglExhaust");
            assertEquals(unit.unitCode().equals(enabledCode) ? 1 : 0, toggles.get(code).getState());
        }
        assertEquals(1, toggles.get("tglSupply").getState());
        assertEquals(0, toggles.get("tglExhaust").getState());
        assertEquals(1, f.cooling.currentUnitSnapshots().stream().filter(CoolingUnitSnapshot::enabled).count());
    }
}
