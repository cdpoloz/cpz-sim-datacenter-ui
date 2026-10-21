package com.cpz.sim.datacenter.ui.simulation;

import com.cpz.processing.controls.controls.label.Label;
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
                           SimulationThermalPipeline pipeline, DatacenterSimulationHistoryRecorder recorder) {
        DatacenterSimulationStepSnapshot step() {
            return recorder.record(engine.step());
        }
    }

    private DatacenterDefinition definition(String mode, boolean coupled, String granularity) throws Exception {
        var mapper = JsonMapper.builder().build();
        ObjectNode root = (ObjectNode) mapper.readTree(Files.readString(Path.of("data/config/datacenter.json")));
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
        var definition = definition(mode, coupled, granularity);
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
        return new Fixture(definition, dc, temperatures, engine, pipeline, recorder);
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
}
