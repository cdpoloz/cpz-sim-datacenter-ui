package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.factory.*;
import com.cpz.sim.datacenter.health.*;
import com.cpz.sim.datacenter.history.*;
import com.cpz.sim.datacenter.input.*;
import com.cpz.sim.datacenter.snapshot.*;
import com.cpz.sim.datacenter.system.*;
import com.cpz.sim.datacenter.temperature.*;
import com.cpz.sim.datacenter.ui.simulation.*;
import com.cpz.sim.datacenter.workload.*;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.utils.noise.*;
import java.time.Duration;
import java.util.*;

/** Runtime-only composition of the same backend systems used by the classic mode. */
public final class RuntimeSession {
    private final SimulationEngine engine;
    private final DatacenterSimulationHistoryRecorder recorder;
    private DatacenterSimulationStepSnapshot snapshot;
    private final TemperatureRange range;
    private final String temperatureFormat;

    public RuntimeSession(DatacenterDefinition definition, Properties props) {
        temperatureFormat = Objects.requireNonNull(props.getProperty("number.format.temperature"), "number.format.temperature");
        var dc = new DatacenterFactory().create(definition);
        var options = new TemperatureSystemOptionsFactory().create(definition);
        range = new TemperatureRangeCalculator().calculate(dc, options);
        var cooling = new CoolingSystem(new CoolingConfigurationFactory().create(definition, dc)
                .orElseThrow(() -> new IllegalStateException("The datacenter configuration does not contain a cooling block.")));
        var reference = new CoolingSnapshotTemperatureReferenceProvider(cooling.configuration());
        var temperature = new TemperatureSystem(dc, options, new SimpleServerTemperatureModel(), reference);
        var noise = new FractalNoise(new PerlinNoise(1234L), 5, 1.0f, 2.0f, 0.5f);
        WorkloadSource workload = null;
        ServerPowerInputSource power = null;
        switch (definition.dataInputMode()) {
            case UTILIZATION_DRIVEN -> workload = new ScaledWorkloadSource(new NoiseWorkloadSource(noise, .001, .2f, .9f), new WorkloadFactorProviderFactory().create(definition));
            case POWER_DRIVEN -> power = new NoiseServerPowerInputSource(noise, .001, .20, .85);
            case TEMPERATURE_DRIVEN -> { }
        }
        engine = new SimulationEngine(new SimulationClock(Duration.ofMinutes(1)));
        var pipeline = new SimulationThermalPipeline(engine, definition, dc, temperature, options, cooling, reference, workload, power);
        var health = new ServerHealthSystem(dc, temperature, new ServerHealthOptions(
                new HealthThreshold(Double.parseDouble(props.getProperty("simulation.health.utilization.alert-threshold")), Double.parseDouble(props.getProperty("simulation.health.utilization.recovery-threshold"))),
                new HealthThreshold(Double.parseDouble(props.getProperty("simulation.health.temperature.alert-threshold-celsius")), Double.parseDouble(props.getProperty("simulation.health.temperature.recovery-threshold-celsius")))));
        var energy = new EnergyConsumptionSystem(dc);
        engine.register(health); engine.register(energy);
        recorder = new DatacenterSimulationHistoryRecorder(new EnergyConsumptionSnapshotProvider(dc, energy),
                new TemperatureSnapshotProvider(dc, temperature, options), new HealthSnapshotProvider(dc, health, temperature),
                new DatacenterOperationalSnapshotProvider(dc), pipeline::coolingSnapshot,
                new DatacenterSimulationHistory(), Optional.empty(), pipeline::coupledThermalSnapshot);
        step(); // As in the classic mode, establish thermal state before the first frame.
    }
    public void step() { engine.step(); snapshot = recorder.record(engine.currentTick()); }
    public DatacenterSimulationStepSnapshot snapshot() { return snapshot; }
    public TemperatureRange range() { return range; }
    public String formatTemperature(double value) { return String.format(Locale.ROOT, temperatureFormat, value); }
}
