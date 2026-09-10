package com.cpz.sim.datacenter.ui.app;

import com.cpz.sim.datacenter.ui.main.Sketch;

/**
 * @author CPZ
 */
public abstract class ApplicationComponent {

    private final ApplicationContext context;

    protected ApplicationComponent(ApplicationContext context) {
        this.context = context;
    }

    protected final ApplicationContext context() {
        return context;
    }

    protected final Sketch sketch() {
        return context.sketch();
    }

}
