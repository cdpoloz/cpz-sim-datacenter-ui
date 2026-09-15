package com.cpz.sim.datacenter.ui.controls;

import com.cpz.processing.controls.controls.config.ControlConfigLoader;
import com.cpz.processing.controls.controls.toggle.Toggle;
import com.cpz.processing.controls.core.input.InputManager;
import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.app.Initializable;
import com.cpz.sim.datacenter.ui.input.MainInputLayer;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Loads active UI controls, stores them by code, and wires interactive controls to input.
 *
 * <p>Labels and Indicators are render/update targets. Buttons and Toggles additionally register
 * with {@link MainInputLayer} and forward listener callbacks to the sketch boundary.</p>
 *
 * @author CPZ
 */
public class ControlManager extends ApplicationComponent implements Initializable {

    private static final String CONFIG_PATH = "data/config/";
    private final UiComponentContainer container;
    private final ControlLoader loader;
    private final OverlayManager overlayManager;
    private final InputManager inputManager;
    private final MainInputLayer mainInputLayer;
    private final Consumer<String> buttonClickHandler;
    private final BiConsumer<Toggle, Integer> toggleChangeHandler;

    /**
     * Creates the startup manager for control loading and listener registration.
     *
     * @param context Processing access used by the JSON loader
     * @param container destination for code-indexed control groups
     * @param overlayManager overlay service supplied to interactive controls
     * @param inputManager layered input manager supplied to interactive controls
     * @param mainInputLayer layer on which Buttons and Toggles are registered
     * @param buttonClickHandler callback receiving stable Button codes
     * @param toggleChangeHandler callback receiving a Toggle and its new integer state
     */
    public ControlManager(
            ApplicationContext context,
            UiComponentContainer container,
            OverlayManager overlayManager,
            InputManager inputManager,
            MainInputLayer mainInputLayer,
            Consumer<String> buttonClickHandler,
            BiConsumer<Toggle, Integer> toggleChangeHandler
    ) {
        super(context);
        this.container = container;
        this.overlayManager = overlayManager;
        this.inputManager = inputManager;
        this.mainInputLayer = mainInputLayer;
        this.buttonClickHandler = buttonClickHandler;
        this.toggleChangeHandler = toggleChangeHandler;
        loader = new ControlLoader(context);
    }

    /** Loads every active control group and registers interactive controls. */
    @Override
    public void initialize() {
        loadLabels();
        loadIndicators();
        loadButtons();
        loadToggles();
    }

    private void loadLabels() {
        ControlConfigLoader controlConfigLoader = new ControlConfigLoader(sketch());
        container.setLabels(loader.loadLabels(controlConfigLoader, CONFIG_PATH + "labels.json"));
    }

    private void loadIndicators() {
        ControlConfigLoader controlConfigLoader = new ControlConfigLoader(sketch());
        container.setIndicators(loader.loadIndicators(controlConfigLoader, CONFIG_PATH + "indicators.json"));
        container.setIndicatorsAiSlot(loader.loadIndicators(controlConfigLoader, CONFIG_PATH + "indicatorsAiSlot.json"));
        container.setIndicatorsAlert(loader.loadIndicators(controlConfigLoader, CONFIG_PATH + "indicatorsAlert.json"));
        container.setIndicatorsNullAisle(loader.loadIndicators(controlConfigLoader, CONFIG_PATH + "indicatorsNullAisle.json"));
        container.setIndicatorsSelectedRack(loader.loadIndicators(controlConfigLoader, CONFIG_PATH + "indicatorsSelectedRack.json"));
        container.setIndicatorsSelectedAisle(loader.loadIndicators(controlConfigLoader, CONFIG_PATH + "indicatorsSelectedAisle.json"));
        container.setIndicatorsRack(loader.loadIndicators(controlConfigLoader, CONFIG_PATH + "indicatorsRack.json"));
        container.setIndicatorsRackCondition(loader.loadIndicators(controlConfigLoader, CONFIG_PATH + "indicatorsRackCondition.json"));
        container.setIndicatorsSelectedAisleMaximumTemperatureServer(loader.loadIndicators(controlConfigLoader, CONFIG_PATH + "indicatorsSelectedAisleMaximumTemperatureServer.json"));
    }

    private void loadButtons() {
        ControlConfigLoader controlConfigLoader = new ControlConfigLoader(sketch(), overlayManager, inputManager);
        container.setButtonsSelectedAisleRack(loader.loadButtons(controlConfigLoader, CONFIG_PATH + "buttonsSelectedAisleRacks.json"));
        container.buttonsSelectedAisleRack().values().forEach(button -> {
            mainInputLayer.addPointerTarget(button::handlePointerEvent);
            button.setClickListener(() -> buttonClickHandler.accept((button.getCode())));
        });
        container.setButtonsColumn(loader.loadButtons(controlConfigLoader, CONFIG_PATH + "buttonsSelectedColumn.json"));
        container.buttonsColumn().values().forEach(button -> {
            mainInputLayer.addPointerTarget(button::handlePointerEvent);
            button.setClickListener(() -> buttonClickHandler.accept((button.getCode())));
        });
        container.setButtonsPlay(loader.loadButtons(controlConfigLoader, CONFIG_PATH + "buttonsPlay.json"));
        container.buttonsPlay().values().forEach(button -> {
            mainInputLayer.addPointerTarget(button::handlePointerEvent);
            button.setClickListener(() -> buttonClickHandler.accept((button.getCode())));
        });
    }

    private void loadToggles() {
        ControlConfigLoader controlConfigLoader = new ControlConfigLoader(sketch(), overlayManager, inputManager);
        container.setToggles(loader.loadToggles(controlConfigLoader, CONFIG_PATH + "toggles.json"));
        container.toggles().values().forEach(toggle -> {
            mainInputLayer.addPointerTarget(toggle::handlePointerEvent);
            toggle.setChangeListener(state -> toggleChangeHandler.accept(toggle, state));
        });
    }

}
