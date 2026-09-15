package com.cpz.sim.datacenter.ui.simulation;

/**
 * Represents the UI temperature range.
 *
 * @author CPZ
 */
public record TemperatureRange(
        float minServerTemperatureCelsius,
        float maxServerTemperatureCelsius
) {
}