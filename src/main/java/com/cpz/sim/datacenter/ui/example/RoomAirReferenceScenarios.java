package com.cpz.sim.datacenter.ui.example;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.definition.RoomAirTemperatureOptionsDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.cooling.CoolingUnitType;
import com.cpz.sim.datacenter.factory.CoolingConfigurationFactory;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.factory.TemperatureSystemOptionsFactory;
import com.cpz.sim.datacenter.health.ServerHealthOptions;
import com.cpz.sim.datacenter.history.DatacenterSimulationHistory;
import com.cpz.sim.datacenter.history.DatacenterSimulationHistoryRecorder;
import com.cpz.sim.datacenter.history.DatacenterSimulationStepSnapshot;
import com.cpz.sim.datacenter.snapshot.*;
import com.cpz.sim.datacenter.system.*;
import com.cpz.sim.datacenter.temperature.CoolingSnapshotTemperatureReferenceProvider;
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.datacenter.ui.simulation.SimulationThermalPipeline;
import com.cpz.sim.datacenter.workload.ConstantWorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/** Reproducible headless cooling comparison; fixed 67% utilization, not UI noise. */
public final class RoomAirReferenceScenarios {

    public enum Scenario {
        NONE, SUPPLY_ONLY, EXHAUST_ONLY, ALL
    }

    public record Result(
            Scenario scenario, double capacityJoulesPerCelsius, int minutes,
            double airCelsius, double averageServerCelsius, double maximumServerCelsius,
            double itKilowatts, double coolingElectricalKilowatts,
            double lastMinuteRemovedHeatKilowatts, double pue, double maximumEnergyResidualJoules
    ) {
    }

    private RoomAirReferenceScenarios() {
    }

    public static Result run(DatacenterDefinition definition, Scenario scenario, int minutes) {
        if (minutes <= 0) throw new IllegalArgumentException("minutes must be positive");
        if (definition.roomAirTemperature() == null)
            throw new IllegalArgumentException("reference scenarios require explicit roomAirTemperature parameters");
        java.util.Objects.requireNonNull(scenario, "scenario must not be null");
        // Preserve the room-only benchmark even when the interactive dataset includes a local rack node.
        definition = withoutRackCooling(definition);
        var dc = new DatacenterFactory().create(definition);
        var options = new TemperatureSystemOptionsFactory().create(definition);
        var cooling = new CoolingSystem(new CoolingConfigurationFactory().create(definition, dc).orElseThrow());
        for (var unit : cooling.configuration().units()) {
            boolean enabled = scenario == Scenario.ALL
                    || scenario == Scenario.SUPPLY_ONLY && unit.type() == CoolingUnitType.SUPPLY
                    || scenario == Scenario.EXHAUST_ONLY && unit.type() == CoolingUnitType.EXHAUST;
            cooling.setEnabled(unit.code(), enabled);
        }
        var reference = new CoolingSnapshotTemperatureReferenceProvider(cooling.configuration());
        var temperatures = new TemperatureSystem(dc, options, new SimpleServerTemperatureModel(), reference);
        var engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(1)));
        var pipeline = new SimulationThermalPipeline(engine, definition, dc, temperatures, options, cooling,
                reference, new ConstantWorkloadSource(0.67),
                (server, tick) -> server.getConfig().idlePowerWatts()
                        + 0.67 * (server.getConfig().maxPowerWatts() - server.getConfig().idlePowerWatts()));
        var health = new ServerHealthSystem(dc, temperatures, ServerHealthOptions.defaults());
        var energy = new EnergyConsumptionSystem(dc);
        engine.register(health);
        engine.register(energy);
        var recorder = new DatacenterSimulationHistoryRecorder(new EnergyConsumptionSnapshotProvider(dc, energy),
                new TemperatureSnapshotProvider(dc, temperatures, options), new HealthSnapshotProvider(dc, health, temperatures),
                new DatacenterOperationalSnapshotProvider(dc), pipeline::coolingSnapshot,
                new DatacenterSimulationHistory(), Optional.empty(), pipeline::coupledThermalSnapshot);
        DatacenterSimulationStepSnapshot last = null;
        double maximumResidual = 0.0;
        for (int i = 0; i < minutes; i++) {
            last = recorder.record(engine.step());
            maximumResidual = Math.max(maximumResidual,
                    Math.abs(last.coupledThermalSnapshot().orElseThrow().energyBalanceResidualJoules()));
        }
        var operational = last.operationalSnapshot();
        return new Result(scenario, definition.roomAirTemperature().effectiveThermalCapacityJoulesPerCelsius(), minutes,
                operational.roomAirTemperatureCelsius(), operational.averageOnlineServerTemperatureCelsius(),
                last.temperatureSnapshot().maxTemperatureCelsius(), operational.currentItPowerWatts() / 1000.0,
                operational.coolingPowerWatts() / 1000.0,
                last.coupledThermalSnapshot().orElseThrow().removedHeatJoules() / 60.0 / 1000.0,
                operational.pue(), maximumResidual);
    }

    private static DatacenterDefinition withoutRackCooling(DatacenterDefinition definition) {
        return new DatacenterDefinition(definition.name(), definition.layout(), definition.serverModels(), definition.servers(),
                definition.temperature(), definition.health(), definition.cooling(), definition.dataInputMode(),
                definition.powerInputGranularity(), definition.temperatureInputGranularity(), definition.roomAirTemperature(), null);
    }

    public static DatacenterDefinition withCapacity(DatacenterDefinition definition, double capacity) {
        var room = java.util.Objects.requireNonNull(definition.roomAirTemperature(), "room parameters must be configured");
        return new DatacenterDefinition(definition.name(), definition.layout(), definition.serverModels(), definition.servers(),
                definition.temperature(), definition.health(), definition.cooling(), definition.dataInputMode(),
                definition.powerInputGranularity(), definition.temperatureInputGranularity(),
                new RoomAirTemperatureOptionsDefinition(room.initialTemperatureCelsius(), capacity,
                        room.environmentTemperatureCelsius(), room.environmentHeatTransferWattsPerCelsius()),
                definition.rackCooling());
    }

    /** Arguments: optional config path, optional simulated minutes (default 21). */
    public static void main(String[] args) {
        var definition = new JsonDatacenterConfigLoader().load(Path.of(
                args.length > 0 ? args[0] : "data/config/datacenter.json"));
        int minutes = args.length > 1 ? Integer.parseInt(args[1]) : 21;
        System.out.println("capacity_MJ_per_C,scenario,minutes,air_C,server_avg_C,server_max_C,it_kW,hvac_electrical_kW,last_minute_removed_heat_kW,pue,max_energy_residual_J");
        for (double capacity : new double[]{5_000_000.0, 20_000_000.0, 50_000_000.0}) {
            for (Scenario scenario : Scenario.values()) {
                var r = run(withCapacity(definition, capacity), scenario, minutes);
                System.out.printf(Locale.ROOT, "%.1f,%s,%d,%.4f,%.4f,%.4f,%.3f,%.3f,%.3f,%.4f,%.9f%n",
                        capacity / 1_000_000.0, r.scenario(), minutes, r.airCelsius(), r.averageServerCelsius(),
                        r.maximumServerCelsius(), r.itKilowatts(), r.coolingElectricalKilowatts(),
                        r.lastMinuteRemovedHeatKilowatts(), r.pue(), r.maximumEnergyResidualJoules());
            }
        }
    }
}
