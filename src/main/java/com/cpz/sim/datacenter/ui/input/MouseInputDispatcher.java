package com.cpz.sim.datacenter.ui.input;

import com.cpz.processing.controls.core.input.InputManager;
import com.cpz.processing.controls.core.input.PointerEvent;

/**
 * Converts Processing mouse callback data into controls-library pointer events.
 *
 * @author CPZ
 */
public class MouseInputDispatcher {

    private final InputManager inputManager;

    /**
     * Creates a dispatcher for the application's layered input manager.
     *
     * @param inputManager destination for translated pointer events
     */
    public MouseInputDispatcher(InputManager inputManager) {
        this.inputManager = inputManager;
    }

    /** Dispatches a Processing mouse-move callback. */
    public void mouseMoved(float mouseX, float mouseY, int mouseButton) {
        dispatch(PointerEvent.Type.MOVE, mouseX, mouseY, mouseButton);
    }

    /** Dispatches a Processing mouse-drag callback. */
    public void mouseDragged(float mouseX, float mouseY, int mouseButton) {
        dispatch(PointerEvent.Type.DRAG, mouseX, mouseY, mouseButton);
    }

    /** Dispatches a Processing mouse-press callback. */
    public void mousePressed(float mouseX, float mouseY, int mouseButton) {
        dispatch(PointerEvent.Type.PRESS, mouseX, mouseY, mouseButton);
    }

    /** Dispatches a Processing mouse-release callback. */
    public void mouseReleased(float mouseX, float mouseY, int mouseButton) {
        dispatch(PointerEvent.Type.RELEASE, mouseX, mouseY, mouseButton);
    }

    /** Dispatches a mouse-wheel callback including modifier state. */
    public void mouseWheel(float mouseX, float mouseY, int mouseButton, float wheelCount, boolean shiftDown, boolean controlDown) {
        inputManager.dispatchPointer(
                new PointerEvent(
                        PointerEvent.Type.WHEEL,
                        mouseX,
                        mouseY,
                        mouseButton,
                        wheelCount,
                        shiftDown,
                        controlDown
                )
        );
    }

    private void dispatch(PointerEvent.Type type, float mouseX, float mouseY, int mouseButton) {
        inputManager.dispatchPointer(new PointerEvent(type, mouseX, mouseY, mouseButton));
    }
}
