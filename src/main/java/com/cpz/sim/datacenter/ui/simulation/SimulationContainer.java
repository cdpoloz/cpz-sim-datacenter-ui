package com.cpz.sim.datacenter.ui.simulation;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.ui.config.HotAisleDefinition;
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
}
