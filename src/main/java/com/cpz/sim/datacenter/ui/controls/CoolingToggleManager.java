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
 * Handles cooling toggle interactions.
 *
 * @author CPZ
 */
public class CoolingToggleManager extends ApplicationComponent implements Initializable {

    private final SimulationContainer simulationContainer;
    private final UiComponentContainer uiComponentContainer;
    private boolean syncingCoolingToggles;

    public CoolingToggleManager(ApplicationContext context, SimulationContainer simulationContainer, UiComponentContainer uiComponentContainer) {
        super(context);
        this.simulationContainer = simulationContainer;
        this.uiComponentContainer = uiComponentContainer;
    }

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
                    String toggleCode = unit.unitCode().replace("EXHAUST-", "tglExhaust");
                    exhaustToggleCodes.add(toggleCode);
                });
        simulationContainer.setExhaustToggleCodes(exhaustToggleCodes);
    }

    public void toggleCoolingUnit(Toggle toggle, int state) {
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
        String unitCode = unitType
                + "-"
                + toggleCode.replace("tglSupply", "").replace("tglExhaust", "");
        simulationContainer.coolingSystem().setEnabled(unitCode, enabled);
    }
}
