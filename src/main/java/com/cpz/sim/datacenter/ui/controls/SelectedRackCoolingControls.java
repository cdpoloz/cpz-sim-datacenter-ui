package com.cpz.sim.datacenter.ui.controls;

import com.cpz.processing.controls.core.overlay.tooltip.Tooltip;
import com.cpz.processing.controls.core.overlay.tooltip.TooltipArea;
import com.cpz.processing.controls.core.overlay.tooltip.TooltipBounds;
import com.cpz.processing.controls.core.overlay.tooltip.util.TooltipOverlayController;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.system.RackCoolingSystem;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;
import processing.core.PApplet;

import java.util.Locale;
import java.util.Optional;

/** Projects rack cooling without calculating physics or changing recorded snapshots. */
public final class SelectedRackCoolingControls {
    private final SimulationContainer simulation;
    private final UiComponentContainer controls;
    private RackLocation displayedRack;
    private boolean syncing;
    private Runnable resizeTooltip = () -> { };

    public SelectedRackCoolingControls(SimulationContainer simulation, UiComponentContainer controls) {
        this.simulation = simulation;
        this.controls = controls;
    }

    /** Hover over the selected-rack illustration or its fan; both share one tooltip. */
    public void registerTooltips(PApplet sketch, TooltipOverlayController tooltips, float relativeTextSize) {
        if (!Float.isFinite(relativeTextSize) || relativeTextSize <= 0)
            throw new IllegalArgumentException("relative tooltip text size must be finite and positive");
        var toggle = controls.toggles().get("tglSelectedRackCooling");
        if (toggle.getTooltip() == null) toggle.setTooltip("");
        var tooltip = toggle.getTooltip();
        resizeTooltip = () -> resizeTooltip(tooltip, sketch.height, relativeTextSize);
        refreshTooltipLayout();
        var area = new TooltipArea(() -> new TooltipBounds(
                sketch.width * 0.707f, sketch.height * 0.149f,
                sketch.width * 0.073f, sketch.height * 0.399f)).setTooltip(tooltip);
        tooltips.registerTarget(area);
        tooltips.registerTarget(toggle);
    }

    /** Resolve relative text sizing before the overlay refresh, including while paused. */
    public void refreshTooltipLayout() {
        resizeTooltip.run();
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
            if (installed) {
                var unit = definition.orElseThrow();
                toggle.setTooltipText("INTERNAL RACK COOLING: " + (enabled ? "ON" : "OFF")
                        + "\nHEAT REJECTION: " + unit.heatRejectionDestination()
                        + "\n" + snapshot.map(value -> format("RACK AIR TEMP: %.1f°C", value.airTemperatureCelsius()))
                                .orElse("RACK AIR TEMP: --")
                        + "\n" + snapshot.map(value -> format("HEAT REMOVAL: %.2f kW", value.actualCoolingPowerWatts() / 1000))
                                .orElse("HEAT REMOVAL: --")
                        + "\n" + format("COOLING CAPACITY: %.2f kW", unit.ratedCoolingCapacityWatts() / 1000)
                        + "\n" + format("ELECTRICAL INPUT: %.2f kW", system.orElseThrow().currentElectricalPowerWatts(unit.code()) / 1000));
            } else toggle.setTooltipText("");
            toggle.getTooltip().setEnabled(installed);
            if (installed) {
                // The library tooltip uses a fixed minimum height; reserve space for all text lines.
                var style = toggle.getTooltip().getStyleConfig();
                style.setMinHeight(style.textPadding * 2
                        + style.textSize * 1.5f * toggle.getTooltip().getText().lines().count());
            }
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

    private static void resizeTooltip(Tooltip tooltip, int canvasHeight, float relativeTextSize) {
        if (canvasHeight <= 0) return;
        var style = tooltip.getStyleConfig();
        style.setTextSize(relativeTextSize * canvasHeight);
        style.setMinHeight(style.textPadding * 2 + style.textSize * 1.5f * tooltip.getText().lines().count());
    }

    private static String format(String format, double value) {
        return String.format(Locale.ROOT, format, value);
    }
}
