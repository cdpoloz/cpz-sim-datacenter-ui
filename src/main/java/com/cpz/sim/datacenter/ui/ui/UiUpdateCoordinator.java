package com.cpz.sim.datacenter.ui.ui;

import com.cpz.sim.datacenter.ui.simulation.SimulationManager;
import com.cpz.sim.datacenter.ui.ui.panel.RoomPanelUpdater;
import com.cpz.sim.datacenter.ui.ui.panel.SelectedAislePanelUpdater;
import com.cpz.sim.datacenter.ui.ui.panel.SelectedRackPanelUpdater;

/**
 * Coordinates timer advancement, snapshot capture, and snapshot-to-control projection.
 *
 * <p>The explicit invalidation flags keep the Processing render loop responsive without
 * recomputing backend snapshots and every control on all 60 frames per second.</p>
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

    /**
     * Creates the coordinator for the frame's ordered update phase.
     *
     * @param uiStateContainer invalidation flags and gradient data
     * @param uiFormatContainer display formats passed to panel updaters
     * @param simulationManager backend step and snapshot behavior
     * @param selectedRackPanelUpdater selected-rack projection
     * @param selectedAislePanelUpdater selected-aisle projection
     * @param roomPanelUpdater room-map projection
     */
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

    /** Captures snapshots when invalidated and then invalidates dependent controls. */
    public void updateSnapshotsIfNeeded() {
        if (!uiStateContainer.updateSnapshots()) return;
        simulationManager.updateSnapshots();
        // One snapshot generation satisfies this invalidation; controls now depend on it.
        uiStateContainer.setUpdateSnapshots(false);
        uiStateContainer.setUpdateUI(true);
    }

    /** Projects the latest snapshots and selection into controls when invalidated. */
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
                // Gradient data is produced with the selected-aisle control projection.
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

    /** Polls the simulation timer and invalidates snapshots after an engine step. */
    public void updateClock() {
        if (!simulationManager.updateClock()) return;
        uiStateContainer.setUpdateSnapshots(true);
    }
}
