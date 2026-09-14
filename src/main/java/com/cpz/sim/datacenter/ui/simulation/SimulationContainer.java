package com.cpz.sim.datacenter.ui.simulation;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.cooling.CoolingSnapshotCoordinator;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.snapshot.*;
import com.cpz.sim.datacenter.system.CoolingSystem;
import com.cpz.sim.datacenter.system.EnergyConsumptionSystem;
import com.cpz.sim.datacenter.system.ServerHealthSystem;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.temperature.CoolingSnapshotTemperatureReferenceProvider;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.datacenter.ui.config.HotAisleConfiguration;
import com.cpz.sim.datacenter.ui.config.HotAisleDefinition;
import com.cpz.sim.datacenter.workload.WorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.utils.time.Timer;

import java.util.List;
import java.util.Map;

/**
 * @author CPZ
 */
public class SimulationContainer {

    private Timer simulationTimer;
    private int simulationBasePeriodMillis;
    private List<Double> simulationSpeedFactors;
    private int simulationSpeedFactorIndex;
    private Datacenter datacenter;
    private DatacenterDefinition datacenterDefinition;
    private String roomName;
    private Map<String, HotAisleDefinition> hotAisleByColumn;
    private CoolingConfiguration coolingConfiguration;
    private Map<String, Rack> racks;
    private WorkloadSource workloadSource;
    private SimulationClock clock;
    private SimulationEngine engine;
    private EnergyConsumptionSystem energySystem;
    private CoolingSystem coolingSystem;
    private CoolingSnapshotTemperatureReferenceProvider coolingTemperatureReferenceProvider;
    private CoolingSnapshotCoordinator coolingSnapshotCoordinator;
    private TemperatureSystemOptions temperatureOptions;
    private TemperatureSystem temperatureSystem;
    private ServerHealthSystem healthSystem;
    private CoolingSnapshot coolingSnapshot;
    private EnergyConsumptionSnapshotProvider energySnapshotProvider;
    private TemperatureSnapshotProvider temperatureSnapshotProvider;
    private HealthSnapshotProvider healthSnapshotProvider;
    private HotAisleConfiguration hotAisleConfiguration;
    private DatacenterOperationalSnapshotProvider operationalSnapshotProvider;
    private List<String> supplyToggleCodes;
    private List<String> exhaustToggleCodes;

    public Timer simulationTimer() {
        return simulationTimer;
    }

    public void setSimulationTimer(Timer simulationTimer) {
        this.simulationTimer = simulationTimer;
    }

    public int simulationSpeedFactorIndex() {
        return simulationSpeedFactorIndex;
    }

    public void setSimulationSpeedFactorIndex(int simulationSpeedFactorIndex) {
        this.simulationSpeedFactorIndex = simulationSpeedFactorIndex;
    }

    public int simulationBasePeriodMillis() {
        return simulationBasePeriodMillis;
    }

    public void setSimulationBasePeriodMillis(int simulationBasePeriodMillis) {
        this.simulationBasePeriodMillis = simulationBasePeriodMillis;
    }

    public List<Double> simulationSpeedFactors() {
        return simulationSpeedFactors;
    }

    public void setSimulationSpeedFactors(List<Double> simulationSpeedFactors) {
        this.simulationSpeedFactors = simulationSpeedFactors;
    }

    public Datacenter datacenter() {
        return datacenter;
    }

    public void setDatacenter(Datacenter datacenter) {
        this.datacenter = datacenter;
    }

    public DatacenterDefinition datacenterDefinition() {
        return datacenterDefinition;
    }

    public void setDatacenterDefinition(DatacenterDefinition datacenterDefinition) {
        this.datacenterDefinition = datacenterDefinition;
    }

    public String roomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public Map<String, HotAisleDefinition> hotAisleByColumn() {
        return hotAisleByColumn;
    }

    public void setHotAisleByColumn(Map<String, HotAisleDefinition> hotAisleByColumn) {
        this.hotAisleByColumn = hotAisleByColumn;
    }

    public CoolingConfiguration coolingConfiguration() {
        return coolingConfiguration;
    }

    public void setCoolingConfiguration(CoolingConfiguration coolingConfiguration) {
        this.coolingConfiguration = coolingConfiguration;
    }

    public Map<String, Rack> racks() {
        return racks;
    }

    public void setRacks(Map<String, Rack> racks) {
        this.racks = racks;
    }

    public WorkloadSource workloadSource() {
        return workloadSource;
    }

    public void setWorkloadSource(WorkloadSource workloadSource) {
        this.workloadSource = workloadSource;
    }

    public SimulationClock clock() {
        return clock;
    }

    public void setClock(SimulationClock clock) {
        this.clock = clock;
    }

    public SimulationEngine engine() {
        return engine;
    }

    public void setEngine(SimulationEngine engine) {
        this.engine = engine;
    }

    public EnergyConsumptionSystem energySystem() {
        return energySystem;
    }

    public void setEnergySystem(EnergyConsumptionSystem energySystem) {
        this.energySystem = energySystem;
    }

    public CoolingSystem coolingSystem() {
        return coolingSystem;
    }

    public void setCoolingSystem(CoolingSystem coolingSystem) {
        this.coolingSystem = coolingSystem;
    }

    public CoolingSnapshotTemperatureReferenceProvider coolingTemperatureReferenceProvider() {
        return coolingTemperatureReferenceProvider;
    }

    public void setCoolingTemperatureReferenceProvider(CoolingSnapshotTemperatureReferenceProvider coolingTemperatureReferenceProvider) {
        this.coolingTemperatureReferenceProvider = coolingTemperatureReferenceProvider;
    }

    public CoolingSnapshotCoordinator coolingSnapshotCoordinator() {
        return coolingSnapshotCoordinator;
    }

    public void setCoolingSnapshotCoordinator(CoolingSnapshotCoordinator coolingSnapshotCoordinator) {
        this.coolingSnapshotCoordinator = coolingSnapshotCoordinator;
    }

    public TemperatureSystemOptions temperatureOptions() {
        return temperatureOptions;
    }

    public void setTemperatureOptions(TemperatureSystemOptions temperatureOptions) {
        this.temperatureOptions = temperatureOptions;
    }

    public TemperatureSystem temperatureSystem() {
        return temperatureSystem;
    }

    public void setTemperatureSystem(TemperatureSystem temperatureSystem) {
        this.temperatureSystem = temperatureSystem;
    }

    public ServerHealthSystem healthSystem() {
        return healthSystem;
    }

    public void setHealthSystem(ServerHealthSystem healthSystem) {
        this.healthSystem = healthSystem;
    }

    public CoolingSnapshot coolingSnapshot() {
        return coolingSnapshot;
    }

    public void setCoolingSnapshot(CoolingSnapshot coolingSnapshot) {
        this.coolingSnapshot = coolingSnapshot;
    }

    public EnergyConsumptionSnapshotProvider energySnapshotProvider() {
        return energySnapshotProvider;
    }

    public void setEnergySnapshotProvider(EnergyConsumptionSnapshotProvider energySnapshotProvider) {
        this.energySnapshotProvider = energySnapshotProvider;
    }

    public TemperatureSnapshotProvider temperatureSnapshotProvider() {
        return temperatureSnapshotProvider;
    }

    public void setTemperatureSnapshotProvider(TemperatureSnapshotProvider temperatureSnapshotProvider) {
        this.temperatureSnapshotProvider = temperatureSnapshotProvider;
    }

    public HealthSnapshotProvider healthSnapshotProvider() {
        return healthSnapshotProvider;
    }

    public void setHealthSnapshotProvider(HealthSnapshotProvider healthSnapshotProvider) {
        this.healthSnapshotProvider = healthSnapshotProvider;
    }

    public HotAisleConfiguration hotAisleConfiguration() {
        return hotAisleConfiguration;
    }

    public void setHotAisleConfiguration(HotAisleConfiguration hotAisleConfiguration) {
        this.hotAisleConfiguration = hotAisleConfiguration;
    }

    public DatacenterOperationalSnapshotProvider operationalSnapshotProvider() {
        return operationalSnapshotProvider;
    }

    public void setOperationalSnapshotProvider(DatacenterOperationalSnapshotProvider operationalSnapshotProvider) {
        this.operationalSnapshotProvider = operationalSnapshotProvider;
    }

    public List<String> supplyToggleCodes() {
        return supplyToggleCodes;
    }

    public void setSupplyToggleCodes(List<String> supplyToggleCodes) {
        this.supplyToggleCodes = supplyToggleCodes;
    }

    public List<String> exhaustToggleCodes() {
        return exhaustToggleCodes;
    }

    public void setExhaustToggleCodes(List<String> exhaustToggleCodes) {
        this.exhaustToggleCodes = exhaustToggleCodes;
    }
}
