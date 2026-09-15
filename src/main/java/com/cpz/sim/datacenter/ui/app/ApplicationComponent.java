package com.cpz.sim.datacenter.ui.app;

import com.cpz.sim.datacenter.ui.main.Sketch;

/**
 * Base for active components that need access to Processing through the application context.
 *
 * <p>Domain services and passive containers are injected separately and should not use this
 * type merely for dependency lookup.</p>
 *
 * @author CPZ
 */
public abstract class ApplicationComponent {

    private final ApplicationContext context;

    /**
     * Creates a Processing-aware component.
     *
     * @param context context containing the active sketch
     */
    protected ApplicationComponent(ApplicationContext context) {
        this.context = context;
    }

    /**
     * Returns the shared Processing boundary.
     *
     * @return application context
     */
    protected final ApplicationContext context() {
        return context;
    }

    /**
     * Returns the active sketch for Processing API calls on the sketch thread.
     *
     * @return active sketch
     */
    protected final Sketch sketch() {
        return context.sketch();
    }

}
