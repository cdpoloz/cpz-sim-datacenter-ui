package com.cpz.sim.datacenter.ui.ui;

import com.cpz.sim.datacenter.input.DatacenterDataInputMode;
import com.cpz.sim.datacenter.input.PowerInputGranularity;
import com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;

import static com.cpz.sim.datacenter.input.PowerInputGranularity.RACK;

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
     * @param context Processing time and mapping access
     * @param simulationContainer source of initial operational totals
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
     * @param simpleTemperatureFormat format used by integer-like legend labels
     */
    public void initialize(float minServerTemperatureCelsius, float maxServerTemperatureCelsius, String simpleTemperatureFormat) {
        initializeTemperatureScale(minServerTemperatureCelsius, maxServerTemperatureCelsius, simpleTemperatureFormat);
        initializeRoomServerTotals();
        initializeDataInputMode();
        initializeDateTime();
    }

    private void initializeDataInputMode() {
        String dataInputMode = "DATA INPUT MODE: ";
        String selectedRackLoad = "LOAD";
        String selectedRackPower = "POWER";
        String inferred = " (i)";
        DatacenterDataInputMode datacenterDataInputMode = simulationContainer.datacenterDefinition().dataInputMode();
        PowerInputGranularity granularity = simulationContainer.datacenterDefinition().powerInputGranularity();
        switch (datacenterDataInputMode) {
            case UTILIZATION_DRIVEN -> {
                dataInputMode += "UTILIZATION DRIVEN";
                selectedRackPower += inferred;
            }
            case POWER_DRIVEN -> {
                dataInputMode += "POWER DRIVEN - " + granularity;
                selectedRackLoad += inferred;
                if (granularity == RACK) selectedRackPower += inferred;
            }
            case TEMPERATURE_DRIVEN -> dataInputMode += "TEMPERATURE DRIVEN";
        }
        uiComponentContainer.labels().get("lblDataInputMode").setText(dataInputMode);
        uiComponentContainer.labels().get("lblSelectedRackLoad").setText(selectedRackLoad);
        uiComponentContainer.labels().get("lblSelectedRackPower").setText(selectedRackPower);
    }

    private void initializeTemperatureScale(float minServerTemperatureCelsius, float maxServerTemperatureCelsius, String simpleTemperatureFormat) {
        uiComponentContainer.labels().get("lblRoomTemperatureScale01").setText(String.format(simpleTemperatureFormat, minServerTemperatureCelsius));
        for (int i = 0; i < 5; i++) {
            float temperature = sketch().map(i, 0, 5, minServerTemperatureCelsius, maxServerTemperatureCelsius);
            String temperatureScaleLabelCode = "lblRoomTemperatureScale0" + (i + 1);
            uiComponentContainer.labels().get(temperatureScaleLabelCode).setText(String.format(simpleTemperatureFormat, temperature));
        }
        uiComponentContainer.labels().get("lblRoomTemperatureScale06").setText(String.format(simpleTemperatureFormat, maxServerTemperatureCelsius));
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

    private void initializeDateTime() {
        uiComponentContainer
                .labels()
                .get("lblDate")
                .setText(
                        String.format("%02d", sketch().day())
                                + "/"
                                + String.format("%02d", sketch().month())
                                + "/"
                                + sketch().year()
                );
        uiComponentContainer
                .labels()
                .get("lblTime")
                .setText(
                        String.format("%02d", sketch().hour())
                                + ":"
                                + String.format("%02d", sketch().minute())
                                + ":"
                                + String.format("%02d", sketch().second())
                );
    }
}
