package com.cpz.sim.datacenter.ui.simulation;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.cooling.CoolingSnapshotCoordinator;
import com.cpz.sim.datacenter.cooling.DatacenterCoolingTickInputProvider;
import com.cpz.sim.datacenter.factory.CoupledRoomTemperatureSystemFactory;
import com.cpz.sim.datacenter.factory.DatacenterInputPipelineFactory;
import com.cpz.sim.datacenter.factory.TemperatureInputSourceFactory;
import com.cpz.sim.datacenter.input.DatacenterDataInputMode;
import com.cpz.sim.datacenter.input.ServerPowerInputSource;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.snapshot.CoolingSnapshot;
import com.cpz.sim.datacenter.snapshot.CoupledRoomThermalSnapshot;
import com.cpz.sim.datacenter.system.*;
import com.cpz.sim.datacenter.temperature.CoolingSnapshotTemperatureReferenceProvider;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.datacenter.workload.WorkloadSource;
import com.cpz.sim.foundation.engine.Simulatable;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationTick;

import java.util.Optional;

/** Installs exactly one thermal owner; independent of Processing and UI controls. */
public final class SimulationThermalPipeline {

    private final Optional<CoupledRoomTemperatureSystem> coupled;
    private CoolingSnapshot legacyCoolingSnapshot;

    public SimulationThermalPipeline(
            SimulationEngine engine, DatacenterDefinition definition, Datacenter datacenter,
            TemperatureSystem temperatures, TemperatureSystemOptions options, CoolingSystem cooling,
            CoolingSnapshotTemperatureReferenceProvider reference, WorkloadSource workload,
            ServerPowerInputSource powerSource
    ) {
        coupled = new CoupledRoomTemperatureSystemFactory().create(
                definition, datacenter, temperatures, Optional.of(cooling));
        if (coupled.isPresent()) {
            new DatacenterInputPipelineFactory().registerInputSystems(engine, definition, datacenter,
                    temperatures, options, workload, powerSource, coupled.orElseThrow());
            return;
        }
        switch (definition.dataInputMode()) {
            case UTILIZATION_DRIVEN -> {
                engine.register(new WorkloadSystem(datacenter, workload));
                engine.register(new PowerConsumptionSystem(datacenter));
            }
            case POWER_DRIVEN -> engine.register(new PowerInputSystem(datacenter, powerSource));
            case TEMPERATURE_DRIVEN -> engine.register(new RackTemperatureInputSystem(datacenter, temperatures,
                    new TemperatureInputSourceFactory().create(definition), options));
        }
        var coordinator = new CoolingSnapshotCoordinator(
                new DatacenterCoolingTickInputProvider(datacenter), cooling, reference);
        engine.register(new Simulatable() {
            @Override
            public void update(SimulationTick tick) {
                legacyCoolingSnapshot = coordinator.update(tick);
            }

            @Override
            public void reset() {
                cooling.reset();
                legacyCoolingSnapshot = null;
            }
        });
        // Observed temperatures must never be overwritten by a power-based update.
        if (definition.dataInputMode() != DatacenterDataInputMode.TEMPERATURE_DRIVEN)
            engine.register(temperatures);
    }

    /** Returns the authoritative controls owned by the coupled thermal solver. */
    public Optional<RackCoolingSystem> rackCoolingSystem() {
        return coupled.flatMap(CoupledRoomTemperatureSystem::rackCoolingSystem);
    }

    public Optional<CoolingSnapshot> coolingSnapshot() {
        return coupled.isPresent() ? coupled.orElseThrow().lastCoolingSnapshot() : Optional.ofNullable(legacyCoolingSnapshot);
    }

    public Optional<CoupledRoomThermalSnapshot> coupledThermalSnapshot() {
        return coupled.flatMap(CoupledRoomTemperatureSystem::lastSnapshot);
    }
}
