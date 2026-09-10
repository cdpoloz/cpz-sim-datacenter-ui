package com.cpz.sim.datacenter.ui.app;

import com.cpz.sim.datacenter.ui.main.Sketch;
import com.cpz.sim.datacenter.ui.resources.ResourceManager;

import java.util.List;

/**
 * Initializes the application.
 *
 * <p>This class progressively absorbs the initialization logic currently
 * located in the Processing sketch, keeping the sketch focused on the
 * application lifecycle.</p>
 *
 * @author CPZ
 */
public class ApplicationBootstrap {

    private final ResourceManager resourceManager;

    public ApplicationBootstrap(ApplicationContext context) {
        resourceManager = new ResourceManager(context);
    }

    public void initialize() {
        bootstrapProcessing();
        bootstrapInfrastructure();
        bootstrapControls();
        bootstrapResources();
        bootstrapDatacenter();
        bootstrapSimulation();
        bootstrapInitialState();
        bootstrapInitialUi();
    }

    private void bootstrapProcessing() {
    }

    private void bootstrapInfrastructure() {
    }

    private void bootstrapControls() {
    }

    private void bootstrapResources() {
        resourceManager.initialize();
    }

    private void bootstrapDatacenter() {
    }

    private void bootstrapSimulation() {
    }

    private void bootstrapInitialState() {
    }

    private void bootstrapInitialUi() {
    }
}
