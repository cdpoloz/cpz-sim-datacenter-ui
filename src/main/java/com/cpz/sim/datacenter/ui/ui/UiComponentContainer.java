package com.cpz.sim.datacenter.ui.ui;

import com.cpz.processing.controls.controls.button.Button;
import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.controls.toggle.Toggle;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;

import java.util.Map;

/**
 * Stores all UI components created during application startup.
 *
 * <p>This container acts as the central owner of the user interface
 * controls and their lookup maps.</p>
 *
 * @author CPZ
 */
public class UiComponentContainer {

    private Map<String, Label> labels;
    private Map<String, Indicator> indicators;
    private Map<String, Indicator> indicatorsAiSlot;
    private Map<String, Indicator> indicatorsAlert;
    private Map<String, Indicator> indicatorsNullAisle;
    private Map<String, Indicator> indicatorsSelectedRack;
    private Map<String, Indicator> indicatorsSelectedAisle;
    private Map<String, Indicator> indicatorsRack;
    private Map<String, Indicator> indicatorsRackCondition;
    private Map<String, Indicator> indicatorsSelectedAisleMaximumTemperatureServer;
    private Map<String, Button> buttonsSelectedAisleRack;
    private Map<String, Button> buttonsColumn;
    private Map<String, Button> buttonsPlay;
    private Map<String, Toggle> toggles;

    public UiComponentContainer() {
    }

    public void setLabels(Map<String, Label> labels) {
        this.labels = labels;
    }

    public void setIndicators(Map<String, Indicator> indicators) {
        this.indicators = indicators;
    }

    public void setIndicatorsAiSlot(Map<String, Indicator> indicatorsAiSlot) {
        this.indicatorsAiSlot = indicatorsAiSlot;
    }

    public void setIndicatorsAlert(Map<String, Indicator> indicatorsAlert) {
        this.indicatorsAlert = indicatorsAlert;
    }

    public void setIndicatorsNullAisle(Map<String, Indicator> indicatorsNullAisle) {
        this.indicatorsNullAisle = indicatorsNullAisle;
    }

    public void setIndicatorsSelectedRack(Map<String, Indicator> indicatorsSelectedRack) {
        this.indicatorsSelectedRack = indicatorsSelectedRack;
    }

    public void setIndicatorsSelectedAisle(Map<String, Indicator> indicatorsSelectedAisle) {
        this.indicatorsSelectedAisle = indicatorsSelectedAisle;
    }

    public void setIndicatorsRack(Map<String, Indicator> indicatorsRack) {
        this.indicatorsRack = indicatorsRack;
    }

    public void setIndicatorsRackCondition(Map<String, Indicator> indicatorsRackCondition) {
        this.indicatorsRackCondition = indicatorsRackCondition;
    }

    public void setIndicatorsSelectedAisleMaximumTemperatureServer(Map<String, Indicator> indicatorsSelectedAisleMaximumTemperatureServer) {
        this.indicatorsSelectedAisleMaximumTemperatureServer = indicatorsSelectedAisleMaximumTemperatureServer;
    }

    public void setButtonsSelectedAisleRack(Map<String, Button> buttonsSelectedAisleRack) {
        this.buttonsSelectedAisleRack = buttonsSelectedAisleRack;
    }

    public void setButtonsColumn(Map<String, Button> buttonsColumn) {
        this.buttonsColumn = buttonsColumn;
    }

    public void setButtonsPlay(Map<String, Button> buttonsPlay) {
        this.buttonsPlay = buttonsPlay;
    }

    public void setToggles(Map<String, Toggle> toggles) {
        this.toggles = toggles;
    }

    public Map<String, Button> buttonsSelectedAisleRack() {
        return buttonsSelectedAisleRack;
    }

    public Map<String, Button> buttonsColumn() {
        return buttonsColumn;
    }

    public Map<String, Button> buttonsPlay() {
        return buttonsPlay;
    }
}
