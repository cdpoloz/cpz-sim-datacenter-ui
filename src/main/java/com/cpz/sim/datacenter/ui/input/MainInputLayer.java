package com.cpz.sim.datacenter.ui.input;

import com.cpz.processing.controls.core.input.DefaultInputLayer;
import com.cpz.processing.controls.core.input.KeyboardEvent;
import com.cpz.processing.controls.core.input.PointerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Application input layer that fans library input events out to registered control targets.
 * <p>
 * An event is considered consumed when at least one registered target receives it. Every target
 * still sees the event so each control can perform its own hit testing and state transition.
 * </p>
 *
 * @author CPZ
 */
public final class MainInputLayer extends DefaultInputLayer {

    private final List<PointerTarget> pointerTargets = new ArrayList<>();
    private final List<KeyboardTarget> keyboardTargets = new ArrayList<>();

    /**
     * Creates an input layer with the ordering priority used by {@code InputManager}.
     *
     * @param priority dispatch priority
     */
    public MainInputLayer(int priority) {
        super(priority);
    }

    /**
     * Registers a recipient for pointer events.
     *
     * @param target target to register
     * @return this layer for fluent registration
     */
    public MainInputLayer addPointerTarget(PointerTarget target) {
        pointerTargets.add(Objects.requireNonNull(target, "target"));
        return this;
    }

    /**
     * Registers a recipient for keyboard events.
     *
     * @param target target to register
     * @return this layer for fluent registration
     */
    public MainInputLayer addKeyboardTarget(KeyboardTarget target) {
        keyboardTargets.add(Objects.requireNonNull(target, "target"));
        return this;
    }

    /**
     * Sends a pointer event to every pointer target.
     *
     * @param event translated pointer event
     * @return {@code true} when at least one target was registered
     */
    @Override
    public boolean handlePointerEvent(PointerEvent event) {
        if (event == null || pointerTargets.isEmpty()) {
            return false;
        }
        for (PointerTarget target : pointerTargets) {
            target.handlePointerEvent(event);
        }
        return true;
    }

    /**
     * Sends a keyboard event to every keyboard target.
     *
     * @param event translated keyboard event
     * @return {@code true} when at least one target was registered
     */
    @Override
    public boolean handleKeyboardEvent(KeyboardEvent event) {
        if (event == null || keyboardTargets.isEmpty()) {
            return false;
        }
        for (KeyboardTarget target : keyboardTargets) {
            target.handleKeyboardEvent(event);
        }
        return true;
    }

    /** Receives pointer events dispatched through this layer. */
    @FunctionalInterface
    public interface PointerTarget {

        /**
         * Handles one translated pointer event.
         *
         * @param event event to handle
         */
        void handlePointerEvent(PointerEvent event);
    }

    /** Receives keyboard events dispatched through this layer. */
    @FunctionalInterface
    public interface KeyboardTarget {

        /**
         * Handles one translated keyboard event.
         *
         * @param event event to handle
         */
        void handleKeyboardEvent(KeyboardEvent event);
    }
}
