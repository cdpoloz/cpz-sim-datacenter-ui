package com.cpz.sim.datacenter.ui.app;

import com.cpz.sim.datacenter.ui.main.Sketch;

/**
 * Shared application context used by the UI components.
 *
 * <p>The context is deliberately narrow: it exposes the active Processing sketch while other
 * dependencies continue to be passed explicitly to component constructors.</p>
 *
 * @author CPZ
 */
public class ApplicationContext {

    private final Sketch sketch;

    /**
     * Creates a context for the active Processing sketch.
     *
     * @param sketch sketch that owns the application lifecycle
     */
    public ApplicationContext(Sketch sketch) {
        this.sketch = sketch;
    }

    /**
     * Returns the Processing sketch instance.
     *
     * @return application sketch
     */
    public Sketch sketch() {
        return sketch;
    }
}
