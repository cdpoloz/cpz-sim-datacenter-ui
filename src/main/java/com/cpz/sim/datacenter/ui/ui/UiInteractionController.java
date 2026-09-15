package com.cpz.sim.datacenter.ui.ui;

import com.cpz.processing.controls.controls.toggle.Toggle;
import com.cpz.sim.datacenter.ui.controls.CoolingToggleManager;
import com.cpz.sim.datacenter.ui.simulation.SimulationManager;
import com.cpz.sim.datacenter.ui.ui.selection.SelectionManager;

import static com.cpz.sim.datacenter.ui.util.Constants.*;

/**
 * Routes stable control codes and keyboard codes to selection, simulation, or cooling behavior.
 *
 * @author CPZ
 */
public class UiInteractionController {

    private final UiStateContainer uiStateContainer;
    private final SimulationManager simulationManager;
    private final CoolingToggleManager coolingToggleManager;
    private final SelectionManager selectionManager;

    /**
     * Creates the interaction boundary used by Processing and control callbacks.
     *
     * @param uiStateContainer control invalidation state
     * @param simulationManager play and speed behavior
     * @param coolingToggleManager cooling control behavior
     * @param selectionManager rack, column, and aisle selection behavior
     */
    public UiInteractionController(
            UiStateContainer uiStateContainer,
            SimulationManager simulationManager,
            CoolingToggleManager coolingToggleManager,
            SelectionManager selectionManager
    ) {
        this.uiStateContainer = uiStateContainer;
        this.simulationManager = simulationManager;
        this.coolingToggleManager = coolingToggleManager;
        this.selectionManager = selectionManager;
    }

    /**
     * Routes a Button code and invalidates panel controls after selection changes.
     *
     * @param buttonCode stable code from control JSON
     */
    public void handleButtonClick(String buttonCode) {
        if (buttonCode.startsWith("btnSelectedRack"))
            selectionManager.updateSelectedRack(buttonCode.replace("btnSelectedRack", ""));
        else if (buttonCode.startsWith("btnSelectedColumn"))
            selectionManager.updateSelectedColumn(buttonCode.replace("btnSelectedColumn", ""));
        else if (buttonCode.startsWith("btnPlay")) {
            if (buttonCode.equals("btnPlayPlus")) {
                simulationManager.increaseSimulationSpeed();
                return;
            }
            if (buttonCode.equals("btnPlayMinus")) {
                simulationManager.decreaseSimulationSpeed();
                return;
            }
        }
        uiStateContainer.setUpdateUI(true);
    }

    /**
     * Routes play and cooling Toggle changes.
     *
     * @param toggle changed Toggle
     * @param state controls-library state
     */
    public void handleToggleChange(Toggle toggle, int state) {
        String toggleCode = toggle.getCode();
        if (toggleCode.startsWith("tglSupply") || toggleCode.startsWith("tglExhaust"))
            coolingToggleManager.toggleCoolingUnit(toggle, state);
        else if (toggleCode.equals("tglPlay")) {
            if (simulationManager.isSyncingPlayToggle()) return;
            simulationManager.toggleSimulation();
        }
    }

    /**
     * Handles the active Space, Plus, and Minus keyboard shortcuts.
     *
     * @param keyCode Processing key code
     */
    public void handleKeyReleased(int keyCode) {
        if (keyCode == SPACE_BAR) {
            if (simulationManager.isSyncingPlayToggle()) return;
            simulationManager.toggleSimulation();
        } else if (keyCode == PLUS) simulationManager.increaseSimulationSpeed();
        else if (keyCode == MINUS) simulationManager.decreaseSimulationSpeed();
    }
}
