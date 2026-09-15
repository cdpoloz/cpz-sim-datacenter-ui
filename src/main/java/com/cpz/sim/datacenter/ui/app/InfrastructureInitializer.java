package com.cpz.sim.datacenter.ui.app;

import com.cpz.processing.controls.core.input.InputManager;
import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.sim.datacenter.ui.input.MainInputLayer;
import com.cpz.sim.datacenter.ui.input.MouseInputDispatcher;

/**
 * Initializes application infrastructure components.
 *
 * @author CPZ
 */
public class InfrastructureInitializer implements Initializable {

    private final InfrastructureContainer infrastructureContainer;

    public InfrastructureInitializer(InfrastructureContainer infrastructureContainer) {
        this.infrastructureContainer = infrastructureContainer;
    }

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