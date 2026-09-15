package com.cpz.sim.datacenter.ui.ui;

import java.util.List;

/**
 * Holds mutable UI state.
 *
 * @author CPZ
 */
public class UiStateContainer {

    private boolean updateSnapshots;
    private boolean updateUI;
    private float minServerTemperatureCelsius;
    private float maxServerTemperatureCelsius;
    private List<Float> selectedHotAisleTemperatures;

    public UiStateContainer() {
    }

    public boolean updateSnapshots() {
        return updateSnapshots;
    }

    public void setUpdateSnapshots(boolean updateSnapshots) {
        this.updateSnapshots = updateSnapshots;
    }

    public boolean updateUI() {
        return updateUI;
    }

    public void setUpdateUI(boolean updateUI) {
        this.updateUI = updateUI;
    }

    public float minServerTemperatureCelsius() {
        return minServerTemperatureCelsius;
    }

    public void setMinServerTemperatureCelsius(float minServerTemperatureCelsius) {
        this.minServerTemperatureCelsius = minServerTemperatureCelsius;
    }

    public float maxServerTemperatureCelsius() {
        return maxServerTemperatureCelsius;
    }

    public void setMaxServerTemperatureCelsius(float maxServerTemperatureCelsius) {
        this.maxServerTemperatureCelsius = maxServerTemperatureCelsius;
    }

    public List<Float> selectedHotAisleTemperatures() {
        return selectedHotAisleTemperatures;
    }

    public void setSelectedHotAisleTemperatures(List<Float> selectedHotAisleTemperatures) {
        this.selectedHotAisleTemperatures = selectedHotAisleTemperatures;
    }
}