package com.cpz.sim.datacenter.ui.config;

import java.util.List;
import java.util.Objects;
/**
 * Root configuration containing all hot aisle definitions.
 *
 * @author CPZ
 */
public record HotAisleConfiguration(
        List<HotAisleDefinition> hotAisles
) {

    public HotAisleConfiguration {
        Objects.requireNonNull(hotAisles, "Hot aisle definitions must not be null");
        if (hotAisles.isEmpty()) throw new IllegalArgumentException("At least one hot aisle must be configured");
        hotAisles = List.copyOf(hotAisles);
    }
}
