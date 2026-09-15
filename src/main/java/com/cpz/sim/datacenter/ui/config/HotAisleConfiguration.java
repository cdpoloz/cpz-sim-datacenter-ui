package com.cpz.sim.datacenter.ui.config;

import java.util.List;
import java.util.Objects;

/**
 * Validated root of the UI-specific hot-aisle mapping file.
 *
 * @param hotAisles non-empty immutable list of aisle definitions
 *
 * @author CPZ
 */
public record HotAisleConfiguration(
        List<HotAisleDefinition> hotAisles
) {

    /** Validates and defensively copies the configured aisle list. */
    public HotAisleConfiguration {
        Objects.requireNonNull(hotAisles, "Hot aisle definitions must not be null");
        if (hotAisles.isEmpty()) throw new IllegalArgumentException("At least one hot aisle must be configured");
        hotAisles = List.copyOf(hotAisles);
    }
}
