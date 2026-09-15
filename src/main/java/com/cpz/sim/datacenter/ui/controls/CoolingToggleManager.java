package com.cpz.sim.datacenter.ui.controls;

import com.cpz.processing.controls.controls.toggle.Toggle;
import com.cpz.sim.datacenter.cooling.CoolingUnitType;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.app.Initializable;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * Synchronizes cooling controls with enabled state in the backend {@code CoolingSystem}.
 *
 * <p>Individual SUPPLY/EXHAUST controls map to backend unit codes. Master controls update all
 * children of one type and reflect whether any child remains enabled.</p>
 *
 * @author CPZ
 */
public class CoolingToggleManager extends ApplicationComponent implements Initializable {

    private final SimulationContainer simulationContainer;
    private final UiComponentContainer uiComponentContainer;
    private boolean syncingCoolingToggles;

    /**
     * Creates a manager for cooling-unit and master toggle state.
     *
     * @param context Processing application context
     * @param simulationContainer backend cooling state and derived toggle groups
     * @param uiComponentContainer code-indexed Toggle controls
     */
    public CoolingToggleManager(ApplicationContext context, SimulationContainer simulationContainer, UiComponentContainer uiComponentContainer) {
        super(context);
        this.simulationContainer = simulationContainer;
        this.uiComponentContainer = uiComponentContainer;
    }

    /** Derives SUPPLY and EXHAUST child control codes from the initial cooling snapshot. */
    @Override
    public void initialize() {
        initializeCoolingToggleGroups();
    }

    private void initializeCoolingToggleGroups() {
        List<String> supplyToggleCodes = new ArrayList<>();
        simulationContainer
                .coolingSnapshot()
                .units()
                .stream()
                .filter(unit -> unit.type() == CoolingUnitType.SUPPLY)
                .forEach(unit -> {
                    // Backend SUPPLY-C01-C02 and UI tglSupplyC01-C02 share the location suffix.
                    String toggleCode = unit.unitCode().replace("SUPPLY-", "tglSupply");
                    supplyToggleCodes.add(toggleCode);
                });
        simulationContainer.setSupplyToggleCodes(supplyToggleCodes);
        List<String> exhaustToggleCodes = new ArrayList<>();
        simulationContainer
                .coolingSnapshot()
                .units()
                .stream()
                .filter(unit -> unit.type() == CoolingUnitType.EXHAUST)
                .forEach(unit -> {
                    // Backend EXHAUST-C01 and UI tglExhaustC01 share the location suffix.
                    String toggleCode = unit.unitCode().replace("EXHAUST-", "tglExhaust");
                    exhaustToggleCodes.add(toggleCode);
                });
        simulationContainer.setExhaustToggleCodes(exhaustToggleCodes);
    }

    /**
     * Applies a user-originated toggle change to a master group or one backend cooling unit.
     *
     * @param toggle changed control
     * @param state controls-library state ({@code 1} means enabled)
     */
    public void toggleCoolingUnit(Toggle toggle, int state) {
        // Programmatic master/child synchronization also fires listeners; ignore that re-entry.
        if (syncingCoolingToggles) return;
        String toggleCode = toggle.getCode();
        boolean enabled = state == 1;
        if (toggleCode.equals("tglSupply")) {
            updateChildCoolingToggles(simulationContainer.supplyToggleCodes(), enabled);
            return;
        }
        if (toggleCode.equals("tglExhaust")) {
            updateChildCoolingToggles(simulationContainer.exhaustToggleCodes(), enabled);
            return;
        }
        if (simulationContainer.supplyToggleCodes().contains(toggleCode)) {
            updateCoolingUnit(toggle, enabled);
            updateMasterToggle("tglSupply", simulationContainer.supplyToggleCodes());
            return;
        }
        if (simulationContainer.exhaustToggleCodes().contains(toggleCode)) {
            updateCoolingUnit(toggle, enabled);
            updateMasterToggle("tglExhaust", simulationContainer.exhaustToggleCodes());
        }
    }

    private void updateChildCoolingToggles(List<String> toggleCodes, boolean enabled) {
        // Hold the guard across both setState and backend updates for the complete group change.
        syncingCoolingToggles = true;
        try {
            for (String toggleCode : toggleCodes) {
                Toggle toggle = uiComponentContainer.toggles().get(toggleCode);
                if (toggle == null) throw new IllegalStateException("Missing toggle: " + toggleCode);
                toggle.setState(enabled ? 1 : 0);
                updateCoolingUnit(toggle, enabled);
            }
        } finally {
            syncingCoolingToggles = false;
        }
    }

    private void updateMasterToggle(String masterToggleCode, List<String> childToggleCodes) {
        // A master means "at least one enabled", not "all enabled".
        boolean anyEnabled = childToggleCodes
                .stream()
                .map(uiComponentContainer.toggles()::get)
                .anyMatch(toggle -> toggle != null && toggle.getState() == 1);
        syncingCoolingToggles = true;
        try {
            Toggle masterToggle = uiComponentContainer.toggles().get(masterToggleCode);
            if (masterToggle == null) throw new IllegalStateException("Missing master toggle: " + masterToggleCode);
            masterToggle.setState(anyEnabled ? 1 : 0);
        } finally {
            syncingCoolingToggles = false;
        }
    }

    private void updateCoolingUnit(Toggle toggle, boolean enabled) {
        String unitType = "";
        String toggleCode = toggle.getCode();
        if (toggleCode.startsWith("tglSupply")) unitType = "SUPPLY";
        else if (toggleCode.startsWith("tglExhaust")) unitType = "EXHAUST";
        if (unitType.isEmpty()) return;
        boolean individualToggle = toggleCode.startsWith("tglSupplyC") || toggleCode.startsWith("tglExhaustC");
        if (!individualToggle) return;
        // Restore the backend prefix while preserving the configured column/zone suffix.
        String unitCode = unitType
                + "-"
                + toggleCode.replace("tglSupply", "").replace("tglExhaust", "");
        simulationContainer.coolingSystem().setEnabled(unitCode, enabled);
    }
}
