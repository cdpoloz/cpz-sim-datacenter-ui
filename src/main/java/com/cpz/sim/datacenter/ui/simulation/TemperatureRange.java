package com.cpz.sim.datacenter.ui.simulation;

/**
 * Temperature scale shared by server labels, rack colors, aisle colors, and the gradient.
 *
 * @param minServerTemperatureCelsius lower display bound in degrees Celsius
 * @param maxServerTemperatureCelsius upper display bound in degrees Celsius
 *
 * @author CPZ
 */
public record TemperatureRange(
        float minServerTemperatureCelsius,
        float maxServerTemperatureCelsius
) {
}
