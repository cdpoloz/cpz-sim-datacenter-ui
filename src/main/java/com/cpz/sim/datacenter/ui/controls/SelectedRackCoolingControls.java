package com.cpz.sim.datacenter.ui.controls;

import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.system.RackCoolingSystem;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;

import java.util.Locale;
import java.util.Optional;

/** Projects rack cooling without calculating physics or changing recorded snapshots. */
public final class SelectedRackCoolingControls {
    private final SimulationContainer simulation;
    private final UiComponentContainer controls;
    private RackLocation displayedRack;
    private boolean syncing;

    public SelectedRackCoolingControls(SimulationContainer simulation, UiComponentContainer controls) {
        this.simulation = simulation;
        this.controls = controls;
    }

    private Optional<RackCoolingSystem> system() {
        return simulation.thermalPipeline().rackCoolingSystem();
    }

    /** Air and actual heat removal describe the last completed tick; status/power are live. */
    public void update(RackLocation rack) {
        displayedRack = rack;
        var system = system();
        var definition = system.flatMap(value -> value.findUnitForRack(rack));
        var snapshot = Optional.ofNullable(simulation.operationalSnapshot())
                .flatMap(value -> value.rackCoolingSnapshot()).flatMap(value -> value.findRack(rack));
        var toggle = controls.toggles().get("tglSelectedRackCooling");
        syncing = true;
        try {
            boolean installed = definition.isPresent();
            boolean enabled = installed && system.orElseThrow().isEnabled(definition.orElseThrow().code());
            toggle.setEnabled(installed);
            toggle.setVisible(installed);
            toggle.setState(enabled ? 1 : 0);
            text("lblRackCoolingState", installed
                    ? (enabled ? "ON" : "OFF") + " > " + definition.orElseThrow().heatRejectionDestination() : "NO UNIT");
            text("lblRackCoolingAir", snapshot.map(value -> format("AIR %.1f°C", value.airTemperatureCelsius())).orElse("AIR --"));
            text("lblRackCoolingActual", snapshot.map(value -> format("LAST Q %.2f kW", value.actualCoolingPowerWatts() / 1000)).orElse("LAST Q --"));
            text("lblRackCoolingPower", installed ? format("ELEC %.2f kW",
                    system.orElseThrow().currentElectricalPowerWatts(definition.orElseThrow().code()) / 1000) : "ELEC --");
            text("lblRackCoolingCapacity", definition.map(value -> format("CAP %.1f kW", value.ratedCoolingCapacityWatts() / 1000)).orElse("CAP --"));
        } finally {
            syncing = false;
        }
    }

    /** The displayed rack is the target even if selection invalidation is still pending. */
    public void changeState(int state) {
        if (syncing || displayedRack == null) return;
        system().ifPresent(system -> system.findUnitForRack(displayedRack)
                .ifPresent(unit -> system.setEnabled(unit.code(), state == 1)));
        update(displayedRack);
    }

    private void text(String code, String value) {
        controls.labels().get(code).setText(value);
    }

    private static String format(String format, double value) {
        return String.format(Locale.ROOT, format, value);
    }
}
