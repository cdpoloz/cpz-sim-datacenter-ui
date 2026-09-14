package com.cpz.sim.datacenter.ui.app;

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

    private final ApplicationContext context;

    public ApplicationBootstrap(ApplicationContext context) {
        this.context = context;
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
