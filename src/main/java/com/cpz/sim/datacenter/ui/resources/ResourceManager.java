package com.cpz.sim.datacenter.ui.resources;

import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.app.Initializable;

/**
 * Loads and provides access to the graphical resources used by the UI.
 *
 * <p>This manager is responsible for initializing fonts, images and other
 * static assets required by the application.</p>
 *
 * @author CPZ
 */
public class ResourceManager extends ApplicationComponent implements Initializable {


    public ResourceManager(ApplicationContext context) {
        super(context);
    }

    @Override
    public void initialize() {

    }

}
