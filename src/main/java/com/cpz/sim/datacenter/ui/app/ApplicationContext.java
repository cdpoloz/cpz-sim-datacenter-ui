package com.cpz.sim.datacenter.ui.app;

import com.cpz.sim.datacenter.ui.main.Sketch;

/**
 * Shared application context used by the UI components.
 *
 * <p>The context progressively becomes the central access point for
 * application-wide services, managers and runtime state as the project
 * evolves.</p>
 *
 * @author CPZ
 */
public class ApplicationContext {

    private final Sketch sketch;

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
