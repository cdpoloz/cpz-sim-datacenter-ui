package com.cpz.processing.template.input;

import com.cpz.processing.controls.core.input.DefaultInputLayer;
import com.cpz.processing.controls.core.input.KeyboardEvent;
import com.cpz.processing.controls.core.input.PointerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Application input layer that routes events to controls selected by the sketch.
 * <p>
 * This template layer uses simple consumption semantics: an event is consumed when
 * at least one registered target receives it.
 * </p>
 *
 * @author CPZ
 */
public final class MainInputLayer extends DefaultInputLayer {

    private final List<PointerTarget> pointerTargets = new ArrayList<>();
    private final List<KeyboardTarget> keyboardTargets = new ArrayList<>();

    public MainInputLayer(int priority) {
        super(priority);
    }

    public MainInputLayer addPointerTarget(PointerTarget target) {
        pointerTargets.add(Objects.requireNonNull(target, "target"));
        return this;
    }

    public MainInputLayer addKeyboardTarget(KeyboardTarget target) {
        keyboardTargets.add(Objects.requireNonNull(target, "target"));
        return this;
    }

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

    @FunctionalInterface
    public interface PointerTarget {

        void handlePointerEvent(PointerEvent event);
    }

    @FunctionalInterface
    public interface KeyboardTarget {

        void handleKeyboardEvent(KeyboardEvent event);
    }
}
