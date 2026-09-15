package com.cpz.sim.datacenter.ui.simulation;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerConfig;
import com.cpz.sim.datacenter.model.ServerThermalProperties;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;

/**
 * Derives a stable UI temperature scale from backend thermal configuration.
 *
 * @author CPZ
 */
public class TemperatureRangeCalculator {

    /** Creates a stateless temperature-range calculator. */
    public TemperatureRangeCalculator() {
    }

    /**
     * Uses ambient temperature as the lower bound and the highest theoretical server
     * equilibrium temperature as the upper bound.
     *
     * @param datacenter backend model containing server power and thermal configuration
     * @param temperatureOptions global ambient and fallback heat-dissipation configuration
     * @return temperature range suitable for UI color mapping
     */
    public TemperatureRange calculate(Datacenter datacenter, TemperatureSystemOptions temperatureOptions) {
        double ambientTemperature = temperatureOptions.ambientTemperatureCelsius();
        double globalHeatDissipation = temperatureOptions.heatDissipationWattsPerCelsius();
        double highestEquilibriumTemperature = ambientTemperature;
        for (Server server : datacenter.getServers()) {
            ServerConfig config = server.getConfig();
            ServerThermalProperties thermalProperties = config.thermalProperties();
            // A server override takes precedence over the global dissipation coefficient.
            double heatDissipation = thermalProperties != null ? thermalProperties.heatDissipationWattsPerCelsius() : globalHeatDissipation;
            double equilibriumTemperature = ambientTemperature + config.maxPowerWatts() / heatDissipation;
            highestEquilibriumTemperature = Math.max(highestEquilibriumTemperature, equilibriumTemperature);
        }
        return new TemperatureRange((float) ambientTemperature, (float) Math.ceil(highestEquilibriumTemperature));
    }
}
