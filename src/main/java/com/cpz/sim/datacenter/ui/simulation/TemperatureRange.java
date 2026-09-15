package com.cpz.sim.datacenter.ui.simulation;

/**
 * Temperature scale shared by server labels, rack colors, aisle colors, and the gradient.
 *
 * @param minServerTemperatureCelsius ambient lower bound in degrees Celsius
 * @param maxServerTemperatureCelsius rounded upper equilibrium bound in degrees Celsius
 *
 * @author CPZ
 */
public record TemperatureRange(
        float minServerTemperatureCelsius,
        float maxServerTemperatureCelsius
) {
}
