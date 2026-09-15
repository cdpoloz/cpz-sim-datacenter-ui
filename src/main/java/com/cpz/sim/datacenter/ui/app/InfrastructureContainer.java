package com.cpz.sim.datacenter.ui.app;

import com.cpz.processing.controls.core.input.InputManager;
import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.processing.controls.input.ProcessingKeyboardAdapter;
import com.cpz.sim.datacenter.ui.input.MainInputLayer;
import com.cpz.sim.datacenter.ui.input.MouseInputDispatcher;

/**
 * Holds input and overlay infrastructure shared by the Processing callbacks and controls.
 *
 * <p>The {@link ProcessingKeyboardAdapter} slot is available for controls-library integration;
 * the active application currently handles keyboard releases directly in the sketch.</p>
 *
 * @author CPZ
 */
public class InfrastructureContainer {

    private InputManager inputManager;
    private MainInputLayer mainInputLayer;
    private OverlayManager overlayManager;
    private ProcessingKeyboardAdapter processingKeyboardAdapter;
    private MouseInputDispatcher mouseInputDispatcher;

    /** Creates an empty container populated by {@link InfrastructureInitializer}. */
    public InfrastructureContainer() {
    }

    public InputManager inputManager() {
        return inputManager;
    }

    public void setInputManager(InputManager inputManager) {
        this.inputManager = inputManager;
    }

    public MainInputLayer mainInputLayer() {
        return mainInputLayer;
    }

    public void setMainInputLayer(MainInputLayer mainInputLayer) {
        this.mainInputLayer = mainInputLayer;
    }

    public OverlayManager overlayManager() {
        return overlayManager;
    }

    public void setOverlayManager(OverlayManager overlayManager) {
        this.overlayManager = overlayManager;
    }

    public ProcessingKeyboardAdapter processingKeyboardAdapter() {
        return processingKeyboardAdapter;
    }

    public void setProcessingKeyboardAdapter(ProcessingKeyboardAdapter processingKeyboardAdapter) {
        this.processingKeyboardAdapter = processingKeyboardAdapter;
    }

    public MouseInputDispatcher mouseInputDispatcher() {
        return mouseInputDispatcher;
    }

    public void setMouseInputDispatcher(MouseInputDispatcher mouseInputDispatcher) {
        this.mouseInputDispatcher = mouseInputDispatcher;
    }
}
