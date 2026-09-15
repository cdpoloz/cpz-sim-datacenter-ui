package com.cpz.sim.datacenter.ui.app;

/**
 * Defines a component with an explicit, order-sensitive startup phase.
 *
 * @author CPZ
 */
public interface Initializable {

    /**
     * Initializes resources or registrations after constructor dependencies are available.
     */
    void initialize();

}
