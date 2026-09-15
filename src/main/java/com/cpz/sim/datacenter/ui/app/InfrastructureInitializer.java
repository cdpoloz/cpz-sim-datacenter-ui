package com.cpz.sim.datacenter.ui.app;

import com.cpz.processing.controls.core.input.InputManager;
import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.sim.datacenter.ui.input.MainInputLayer;
import com.cpz.sim.datacenter.ui.input.MouseInputDispatcher;

/**
 * Creates the input pipeline and overlay manager used by configured controls.
 *
 * @author CPZ
 */
public class InfrastructureInitializer implements Initializable {

    private final InfrastructureContainer infrastructureContainer;

    /**
     * Creates an initializer for the container populated during startup.
     *
     * @param infrastructureContainer destination for initialized infrastructure
     */
    public InfrastructureInitializer(InfrastructureContainer infrastructureContainer) {
        this.infrastructureContainer = infrastructureContainer;
    }

    /**
     * Creates and registers the main input layer, mouse dispatcher, and overlay manager.
     */
    @Override
    public void initialize() {
        initializeInput();
        initializeOverlay();
    }

    private void initializeInput() {
        infrastructureContainer.setInputManager(new InputManager());
        infrastructureContainer.setMainInputLayer(new MainInputLayer(0));
        infrastructureContainer.setMouseInputDispatcher(new MouseInputDispatcher(infrastructureContainer.inputManager()));
        infrastructureContainer.inputManager().registerLayer(infrastructureContainer.mainInputLayer());
    }

    private void initializeOverlay() {
        infrastructureContainer.setOverlayManager(new OverlayManager());
    }
}
