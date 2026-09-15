package com.cpz.sim.datacenter.ui.ui;

import com.cpz.sim.datacenter.ui.app.Initializable;

/**
 * Loads presentation formats and establishes a known pre-snapshot UI state.
 *
 * @author CPZ
 */
public class UiContainersInitializer implements Initializable {

    private final UiComponentContainer uiComponentContainer;
    private final UiFormatContainer uiFormatContainer;
    private final UiStateContainer uiStateContainer;
    private final UiFormatLoader uiFormatLoader;

    /**
     * Creates the initializer for passive UI containers.
     *
     * @param uiComponentContainer control container initialized later by {@code ControlManager}
     * @param uiFormatContainer destination for number formats
     * @param uiStateContainer destination for render-loop state
     * @param uiFormatLoader property-backed format loader
     */
    public UiContainersInitializer(
            UiComponentContainer uiComponentContainer,
            UiFormatContainer uiFormatContainer,
            UiStateContainer uiStateContainer,
            UiFormatLoader uiFormatLoader
    ) {
        this.uiComponentContainer = uiComponentContainer;
        this.uiFormatContainer = uiFormatContainer;
        this.uiStateContainer = uiStateContainer;
        this.uiFormatLoader = uiFormatLoader;
    }

    /** Loads formats and resets invalidation/gradient state. */
    @Override
    public void initialize() {
        initializeFormats();
        initializeState();
    }

    private void initializeFormats() {
        uiFormatLoader.loadInto(uiFormatContainer);
    }

    private void initializeState() {
        uiStateContainer.setUpdateUI(false);
        uiStateContainer.setUpdateSnapshots(false);
        uiStateContainer.setSelectedHotAisleTemperatures(null);
    }
}
