package com.cpz.sim.datacenter.ui.input;

import com.cpz.processing.controls.core.input.InputManager;
import com.cpz.processing.controls.core.input.PointerEvent;

/**
 * Dispatches mouse input events.
 *
 * @author CPZ
 */
public class MouseInputDispatcher {

    private final InputManager inputManager;

    public MouseInputDispatcher(InputManager inputManager) {
        this.inputManager = inputManager;
    }

    public void mouseMoved(float mouseX, float mouseY, int mouseButton) {
        dispatch(PointerEvent.Type.MOVE, mouseX, mouseY, mouseButton);
    }

    public void mouseDragged(float mouseX, float mouseY, int mouseButton) {
        dispatch(PointerEvent.Type.DRAG, mouseX, mouseY, mouseButton);
    }

    public void mousePressed(float mouseX, float mouseY, int mouseButton) {
        dispatch(PointerEvent.Type.PRESS, mouseX, mouseY, mouseButton);
    }

    public void mouseReleased(float mouseX, float mouseY, int mouseButton) {
        dispatch(PointerEvent.Type.RELEASE, mouseX, mouseY, mouseButton);
    }

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