package com.cpz.sim.datacenter.ui.ui;

import com.cpz.sim.datacenter.input.DatacenterDataInputMode;
import com.cpz.sim.datacenter.input.PowerInputGranularity;
import com.cpz.sim.datacenter.input.TemperatureInputGranularity;
import com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import processing.core.PApplet;

/**
 * Initializes labels that are derived once after the first backend snapshot is available.
 *
 * @author CPZ
 */
public class UiStateInitializer extends ApplicationComponent {

    private final SimulationContainer simulationContainer;
    private final UiComponentContainer uiComponentContainer;

    /**
     * Creates the one-time UI state initializer.
     *
     * @param context              Processing time and mapping access
     * @param simulationContainer  source of initial operational totals
     * @param uiComponentContainer controls to populate
     */
    public UiStateInitializer(ApplicationContext context, SimulationContainer simulationContainer, UiComponentContainer uiComponentContainer) {
        super(context);
        this.simulationContainer = simulationContainer;
        this.uiComponentContainer = uiComponentContainer;
    }

    /**
     * Populates the temperature legend, server totals, and wall-clock labels.
     *
     * @param minServerTemperatureCelsius scale lower bound
     * @param maxServerTemperatureCelsius scale upper bound
     * @param simpleTemperatureFormat     format used by integer-like legend labels
     */
    public void initialize(float minServerTemperatureCelsius, float maxServerTemperatureCelsius, String simpleTemperatureFormat) {
        initializeTemperatureScale(minServerTemperatureCelsius, maxServerTemperatureCelsius, simpleTemperatureFormat);
        initializeTemperatureTrendLabels(maxServerTemperatureCelsius, simpleTemperatureFormat);
        initializeRoomServerTotals();
        initializeDataInputMode();
        new WallClockLabels(uiComponentContainer.labels().get("lblDate"),
                uiComponentContainer.labels().get("lblTime")).update();
    }

    private void initializeDataInputMode() {
        String selectedRackTemperature = "TEMP";
        String selectedRackLoad = "LOAD";
        String selectedRackPower = "POWER";
        String inferred = " (i)";
        var definition = simulationContainer.datacenterDefinition();
        DatacenterDataInputMode datacenterDataInputMode = definition.dataInputMode();
        PowerInputGranularity powerGranularity = definition.powerInputGranularity();
        TemperatureInputGranularity temperatureGranularity = definition.temperatureInputGranularity();
        switch (datacenterDataInputMode) {
            case UTILIZATION_DRIVEN -> selectedRackPower += inferred;
            case POWER_DRIVEN -> {
                selectedRackLoad += inferred;
                if (powerGranularity == PowerInputGranularity.RACK) selectedRackPower += inferred;
            }
            case TEMPERATURE_DRIVEN -> {
                selectedRackPower += inferred;
                selectedRackLoad += inferred;
                if (temperatureGranularity == TemperatureInputGranularity.RACK) selectedRackTemperature += inferred;
            }
        }
        uiComponentContainer.labels().get("lblDataInputMode").setText(HeaderText.dataInputMode(definition));
        uiComponentContainer.labels().get("lblSelectedRackTemperature").setText(selectedRackTemperature);
        uiComponentContainer.labels().get("lblSelectedRackLoad").setText(selectedRackLoad);
        uiComponentContainer.labels().get("lblSelectedRackPower").setText(selectedRackPower);
    }

    private void initializeTemperatureScale(float minServerTemperatureCelsius, float maxServerTemperatureCelsius, String simpleTemperatureFormat) {
        uiComponentContainer.labels().get("lblRoomTemperatureScale01").setText(String.format(simpleTemperatureFormat, minServerTemperatureCelsius));
        for (int i = 0; i < 5; i++) {
            float temperature = PApplet.map(i, 0, 5, minServerTemperatureCelsius, maxServerTemperatureCelsius);
            String temperatureScaleLabelCode = "lblRoomTemperatureScale0" + (i + 1);
            uiComponentContainer.labels().get(temperatureScaleLabelCode).setText(String.format(simpleTemperatureFormat, temperature));
        }
        uiComponentContainer.labels().get("lblRoomTemperatureScale06").setText(String.format(simpleTemperatureFormat, maxServerTemperatureCelsius));
    }

    private void initializeTemperatureTrendLabels(float maxServerTemperatureCelsius, String simpleTemperatureFormat) {
        for (int i = 1; i < 5; i++) {
            float temperature = maxServerTemperatureCelsius * i / 4.0f;
            String temperatureScaleLabelCode = "lblTrendsRoomTemperature0" + i;
            uiComponentContainer.labels().get(temperatureScaleLabelCode).setText(String.format(simpleTemperatureFormat, temperature));
        }
    }

    private void initializeRoomServerTotals() {
        int totalInstalledServers =
                simulationContainer
                        .operationalSnapshot()
                        .racks()
                        .values()
                        .stream()
                        .mapToInt(RackOperationalSnapshot::installedServerCount)
                        .sum();
        int totalOnlineServers =
                simulationContainer
                        .operationalSnapshot()
                        .racks()
                        .values()
                        .stream()
                        .mapToInt(RackOperationalSnapshot::onlineServerCount)
                        .sum();
        uiComponentContainer.labels().get("lblRoomTotalServersValue").setText(String.valueOf(totalInstalledServers));
        uiComponentContainer.labels().get("lblRoomOnlineServersValue").setText(String.valueOf(totalOnlineServers));
    }
}
