package com.cpz.sim.datacenter.ui.ui;

import com.cpz.sim.datacenter.ui.simulation.SimulationManager;
import com.cpz.sim.datacenter.ui.ui.panel.RoomPanelUpdater;
import com.cpz.sim.datacenter.ui.ui.panel.SelectedAislePanelUpdater;
import com.cpz.sim.datacenter.ui.ui.panel.SelectedRackPanelUpdater;

/**
 * Coordinates UI updates.
 *
 * @author CPZ
 */
public class UiUpdateCoordinator {

    private final UiStateContainer uiStateContainer;
    private final UiFormatContainer uiFormatContainer;
    private final SimulationManager simulationManager;
    private final SelectedRackPanelUpdater selectedRackPanelUpdater;
    private final SelectedAislePanelUpdater selectedAislePanelUpdater;
    private final RoomPanelUpdater roomPanelUpdater;

    public UiUpdateCoordinator(
            UiStateContainer uiStateContainer,
            UiFormatContainer uiFormatContainer,
            SimulationManager simulationManager,
            SelectedRackPanelUpdater selectedRackPanelUpdater,
            SelectedAislePanelUpdater selectedAislePanelUpdater,
            RoomPanelUpdater roomPanelUpdater
    ) {
        this.uiStateContainer = uiStateContainer;
        this.uiFormatContainer = uiFormatContainer;
        this.simulationManager = simulationManager;
        this.selectedRackPanelUpdater = selectedRackPanelUpdater;
        this.selectedAislePanelUpdater = selectedAislePanelUpdater;
        this.roomPanelUpdater = roomPanelUpdater;
    }

    public void updateSnapshotsIfNeeded() {
        if (!uiStateContainer.updateSnapshots()) return;
        simulationManager.updateSnapshots();
        uiStateContainer.setUpdateSnapshots(false);
        uiStateContainer.setUpdateUI(true);
    }

    public void updateControlsIfNeeded() {
        if (!uiStateContainer.updateUI()) return;
        selectedRackPanelUpdater.update(
                uiStateContainer.minServerTemperatureCelsius(),
                uiStateContainer.maxServerTemperatureCelsius(),
                uiFormatContainer.temperature(),
                uiFormatContainer.percentage(),
                uiFormatContainer.powerKw()
        );

        uiStateContainer.setSelectedHotAisleTemperatures(
                selectedAislePanelUpdater.update(
                        uiStateContainer.minServerTemperatureCelsius(),
                        uiStateContainer.maxServerTemperatureCelsius(),
                        uiFormatContainer.temperature(),
                        uiFormatContainer.percentage(),
                        uiFormatContainer.airflow()
                )
        );
        roomPanelUpdater.update(
                uiStateContainer.minServerTemperatureCelsius(),
                uiStateContainer.maxServerTemperatureCelsius()
        );
        uiStateContainer.setUpdateUI(false);
    }
}