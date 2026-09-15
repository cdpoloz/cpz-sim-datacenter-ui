package com.cpz.sim.datacenter.ui.simulation;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerConfig;
import com.cpz.sim.datacenter.model.ServerThermalProperties;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;

/**
 * Calculates UI temperature ranges.
 *
 * @author CPZ
 */
public class TemperatureRangeCalculator {

    public TemperatureRangeCalculator() {
    }

    public TemperatureRange calculate(Datacenter datacenter, TemperatureSystemOptions temperatureOptions) {
        double ambientTemperature = temperatureOptions.ambientTemperatureCelsius();
        double globalHeatDissipation = temperatureOptions.heatDissipationWattsPerCelsius();
        double highestEquilibriumTemperature = ambientTemperature;
        for (Server server : datacenter.getServers()) {
            ServerConfig config = server.getConfig();
            ServerThermalProperties thermalProperties = config.thermalProperties();
            double heatDissipation = thermalProperties != null ? thermalProperties.heatDissipationWattsPerCelsius() : globalHeatDissipation;
            double equilibriumTemperature = ambientTemperature + config.maxPowerWatts() / heatDissipation;
            highestEquilibriumTemperature = Math.max(highestEquilibriumTemperature, equilibriumTemperature);
        }
        return new TemperatureRange((float) ambientTemperature, (float) Math.ceil(highestEquilibriumTemperature));
    }
}