package com.cpz.sim.datacenter.ui.ui.render;

import com.cpz.processing.controls.controls.button.Button;
import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.controls.toggle.Toggle;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;

/**
 * Draws configured control groups in their visual stacking order.
 *
 * @author CPZ
 */
public class ControlRenderer {

    private final UiComponentContainer uiComponentContainer;

    /**
     * Creates a renderer for controls already loaded into the shared container.
     *
     * @param uiComponentContainer controls to draw
     */
    public ControlRenderer(UiComponentContainer uiComponentContainer) {
        this.uiComponentContainer = uiComponentContainer;
    }

    public void updateIndicatorsPlay(boolean playMinus, boolean playPlus) {
        uiComponentContainer.indicatorsPlay().get("indPlayPlus").setOn(playPlus);
        uiComponentContainer.indicatorsPlay().get("indPlayMinus").setOn(playMinus);
    }

    /** Draws all control groups; later groups can appear above earlier groups. */
    public void draw() {
        uiComponentContainer.indicatorsAlert().values().forEach(Indicator::draw);
        uiComponentContainer.labels().values().forEach(Label::draw);
        uiComponentContainer.indicators().values().forEach(Indicator::draw);
        uiComponentContainer.indicatorsSelectedAisle().values().forEach(Indicator::draw);
        uiComponentContainer.indicatorsSelectedAisleMaximumTemperatureServer().values().forEach(Indicator::draw);
        uiComponentContainer.indicatorsSelectedRack().values().forEach(Indicator::draw);
        uiComponentContainer.indicatorsRack().values().forEach(Indicator::draw);
        uiComponentContainer.indicatorsNullAisle().values().forEach(Indicator::draw);
        uiComponentContainer.indicatorsAiSlot().values().forEach(Indicator::draw);
        uiComponentContainer.indicatorsRackCondition().values().forEach(Indicator::draw);
        uiComponentContainer.buttonsSelectedAisleRack().values().forEach(Button::draw);
        uiComponentContainer.buttonsColumn().values().forEach(Button::draw);
        uiComponentContainer.buttonsPlay().values().forEach(Button::draw);
        uiComponentContainer.toggles().values().forEach(Toggle::draw);
        uiComponentContainer.indicatorsPlay().values().forEach(Indicator::draw);
    }
}
