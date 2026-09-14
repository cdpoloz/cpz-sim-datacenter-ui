package com.cpz.sim.datacenter.ui.simulation;

import com.cpz.utils.time.Timer;

import java.util.List;

/**
 * @author CPZ
 */
public class SimulationContext {

    private Timer simulationTimer;
    private int simulationBasePeriodMillis;
    private List<Double> simulationSpeedFactors;
    private int simulationSpeedFactorIndex;

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
}
