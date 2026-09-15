package com.cpz.sim.datacenter.ui.ui;

import java.util.List;

/**
 * Holds mutable state shared by frame updates and renderers.
 *
 * <p>{@code updateSnapshots} invalidates cached backend snapshots after an engine step;
 * {@code updateUI} invalidates control values after snapshots or selection changes.</p>
 *
 * @author CPZ
 */
public class UiStateContainer {

    private boolean updateSnapshots;
    private boolean updateUI;
    private float minServerTemperatureCelsius;
    private float maxServerTemperatureCelsius;
    private List<Float> selectedHotAisleTemperatures;

    /** Creates state with Java default values before {@link UiContainersInitializer} runs. */
    public UiStateContainer() {
    }

    /**
     * Returns whether backend snapshots must be captured before controls are updated.
     *
     * @return snapshot invalidation flag
     */
    public boolean updateSnapshots() {
        return updateSnapshots;
    }

    /** Marks backend snapshots stale or current. */
    public void setUpdateSnapshots(boolean updateSnapshots) {
        this.updateSnapshots = updateSnapshots;
    }

    /**
     * Returns whether controls must be projected from the latest state.
     *
     * @return control invalidation flag
     */
    public boolean updateUI() {
        return updateUI;
    }

    /** Marks control state stale or current. */
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

    /**
     * Returns rack-row temperatures prepared by the selected-aisle panel updater.
     *
     * @return temperatures in rack-code order, or {@code null} before initial projection
     */
    public List<Float> selectedHotAisleTemperatures() {
        return selectedHotAisleTemperatures;
    }

    public void setSelectedHotAisleTemperatures(List<Float> selectedHotAisleTemperatures) {
        this.selectedHotAisleTemperatures = selectedHotAisleTemperatures;
    }
}
