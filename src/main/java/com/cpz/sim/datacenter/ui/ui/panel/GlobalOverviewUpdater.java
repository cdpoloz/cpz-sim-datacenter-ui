package com.cpz.sim.datacenter.ui.ui.panel;

import com.cpz.sim.datacenter.history.DatacenterSimulationStepSnapshot;
import com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;

import java.util.Optional;

/**
 * Projects simulation summary values into the Global Overview panel labels.
 *
 * @author CPZ
 */
public class GlobalOverviewUpdater {

    private final SimulationContainer simulationContainer;
    private final UiComponentContainer uiComponentContainer;

    /**
     * Creates an updater for Global Overview labels.
     *
     * @param simulationContainer simulation state and recorded history
     * @param uiComponentContainer configured UI labels
     */
    public GlobalOverviewUpdater(
            SimulationContainer simulationContainer,
            UiComponentContainer uiComponentContainer
    ) {
        this.simulationContainer = simulationContainer;
        this.uiComponentContainer = uiComponentContainer;
    }

    /** Updates all currently available Global Overview labels. */
    public void update() {
        if (simulationContainer == null || simulationContainer.operationalSnapshot() == null) return;
        if (uiComponentContainer == null) return;
        updateDisplayedRoomTemperature();
        updateDisplayedRoomMaximumRackTemperature();
    }

    private void updateDisplayedRoomTemperature() {
        if (!uiComponentContainer.labels().containsKey("lblRoomTemperatureValue")) return;
        latestDisplayedRoomAverageTemperatureCelsius()
                .ifPresent(averageRoomTemperatureCelsius ->
                        uiComponentContainer
                                .labels()
                                .get("lblRoomTemperatureValue")
                                .setText(String.format("%.1f°C", averageRoomTemperatureCelsius))
                );
    }

    private void updateDisplayedRoomMaximumRackTemperature() {
        if (!uiComponentContainer.labels().containsKey("lblRoomMaximumRackTemperatureValue")) return;
        if (!uiComponentContainer.labels().containsKey("lblRoomMaximumRackTemperatureLocation")) return;
        simulationContainer
                .operationalSnapshot()
                .hottestRackLocation()
                .ifPresent(location -> {
                    double maximumRackTemperature = simulationContainer
                            .operationalSnapshot()
                            .hottestRackAverageTemperatureCelsius();
                    String columnCode = location.column();
                    String rackCode = location.rackCode().value();
                    uiComponentContainer
                            .labels()
                            .get("lblRoomMaximumRackTemperatureValue")
                            .setText(String.format("%.1f°C", maximumRackTemperature));
                    uiComponentContainer
                            .labels()
                            .get("lblRoomMaximumRackTemperatureLocation")
                            .setText(columnCode + "-" + rackCode);
                });
    }

    private Optional<Double> latestDisplayedRoomAverageTemperatureCelsius() {
        if (simulationContainer.simulationHistory() == null) return Optional.empty();
        return simulationContainer
                .simulationHistory()
                .latest()
                .map(this::displayedRoomAverageTemperatureCelsius);
    }

    private double displayedRoomAverageTemperatureCelsius(DatacenterSimulationStepSnapshot snapshot) {
        int onlineServerCount = snapshot
                .operationalSnapshot()
                .racks()
                .values()
                .stream()
                .mapToInt(RackOperationalSnapshot::onlineServerCount)
                .sum();
        double temperatureSumCelsius = snapshot
                .operationalSnapshot()
                .racks()
                .values()
                .stream()
                .filter(RackOperationalSnapshot::hasOnlineServers)
                .mapToDouble(rackSnapshot ->
                        rackSnapshot.averageOnlineTemperatureCelsius()
                                * rackSnapshot.onlineServerCount()
                )
                .sum();
        if (onlineServerCount == 0) return snapshot.operationalSnapshot().roomTemperatureCelsius();
        return temperatureSumCelsius / onlineServerCount;
    }
}
