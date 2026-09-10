package com.cpz.sim.datacenter.ui.controls;

import com.cpz.processing.controls.controls.Control;
import com.cpz.processing.controls.controls.config.ControlConfigLoader;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.app.Initializable;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author CPZ
 */
public class ControlManager extends ApplicationComponent implements Initializable {

    private static final String CONFIG_PATH = "data/config/";
    private final UiComponentContainer container;
    private final ControlLoader loader;

    public ControlManager(ApplicationContext context, UiComponentContainer container) {
        super(context);
        this.container = container;
        loader = new ControlLoader(context);
    }

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
        
    }

    private void loadButtons() {

    }

    private void loadToggles() {

    }

}
