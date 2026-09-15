package com.cpz.sim.datacenter.ui.ui;

import com.cpz.sim.datacenter.ui.app.Initializable;

/**
 * Initializes UI containers.
 *
 * @author CPZ
 */
public class UiContainersInitializer implements Initializable {

    private final UiComponentContainer uiComponentContainer;
    private final UiFormatContainer uiFormatContainer;
    private final UiStateContainer uiStateContainer;
    private final UiFormatLoader uiFormatLoader;

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