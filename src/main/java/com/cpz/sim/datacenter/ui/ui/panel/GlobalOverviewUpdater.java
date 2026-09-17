package com.cpz.sim.datacenter.ui.ui.panel;

import com.cpz.sim.datacenter.history.DatacenterSimulationStepSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingZoneSnapshot;
import com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;
import com.cpz.sim.datacenter.ui.ui.UiFormatContainer;

import java.util.Optional;

/**
 * Projects simulation summary values into the Global Overview panel labels.
 *
 * @author CPZ
 */
public class GlobalOverviewUpdater {

    private final SimulationContainer simulationContainer;
    private final UiComponentContainer uiComponentContainer;
    private final UiFormatContainer uiFormatContainer;

    /**
     * Creates an updater for Global Overview labels.
     *
     * @param simulationContainer simulation state and recorded history
     * @param uiComponentContainer configured UI labels
     */
    public GlobalOverviewUpdater(
            SimulationContainer simulationContainer,
            UiComponentContainer uiComponentContainer,
            UiFormatContainer uiFormatContainer
    ) {
        this.simulationContainer = simulationContainer;
        this.uiComponentContainer = uiComponentContainer;
        this.uiFormatContainer = uiFormatContainer;
    }

    /** Updates all currently available Global Overview labels. */
    public void update() {
        if (simulationContainer == null || simulationContainer.operationalSnapshot() == null) return;
        if (uiComponentContainer == null) return;
        updateDisplayedRoomTemperature();
        updateDisplayedRoomMaximumRackTemperature();
        updateDisplayedRoomAverageItLoad();
        updateDisplayedRoomHvacLoad();
        updateDisplayedRoomTotalElectricalLoad();
    }

    private void updateDisplayedRoomTemperature() {
        if (!uiComponentContainer.labels().containsKey("lblRoomTemperatureValue")) return;
        latestDisplayedRoomAverageTemperatureCelsius()
                .ifPresent(averageRoomTemperatureCelsius ->
                        uiComponentContainer
                                .labels()
                                .get("lblRoomTemperatureValue")
                                .setText(String.format(uiFormatContainer.temperature(), averageRoomTemperatureCelsius))
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
                            .setText(String.format(uiFormatContainer.temperature(), maximumRackTemperature));
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

    private void updateDisplayedRoomAverageItLoad() {
        if (!uiComponentContainer.labels().containsKey("lblRoomItLoadValue")) return;
        double averageItLoadPercentage = displayedRoomAverageItLoadPercentage();
        uiComponentContainer
                .labels()
                .get("lblRoomItLoadValue")
                .setText(String.format(uiFormatContainer.percentage(), averageItLoadPercentage));
    }

    private double displayedRoomAverageItLoadPercentage() {
        int onlineServerCount = simulationContainer
                .operationalSnapshot()
                .racks()
                .values()
                .stream()
                .mapToInt(RackOperationalSnapshot::onlineServerCount)
                .sum();
        if (onlineServerCount == 0) return 0.0;
        return simulationContainer
                .operationalSnapshot()
                .racks()
                .values()
                .stream()
                .filter(RackOperationalSnapshot::hasOnlineServers)
                .mapToDouble(rackSnapshot ->
                        rackSnapshot.averageOnlineUtilization()
                                * rackSnapshot.onlineServerCount()
                )
                .sum() / onlineServerCount * 100.0;
    }

    private void updateDisplayedRoomHvacLoad() {
        if (!uiComponentContainer.labels().containsKey("lblRoomHvacLoadValue")) return;
        double hvacLoadPercentage = displayedRoomHvacLoadPercentage();
        uiComponentContainer
                .labels()
                .get("lblRoomHvacLoadValue")
                .setText(String.format(uiFormatContainer.percentage(), hvacLoadPercentage));
    }

    private double displayedRoomHvacLoadPercentage() {
        if (simulationContainer.coolingSnapshot() == null) return 0.0;
        double availableCoolingCapacityWatts = simulationContainer
                .coolingSnapshot()
                .zones()
                .stream()
                .mapToDouble(CoolingZoneSnapshot::availableCoolingCapacityWatts)
                .sum();
        if (availableCoolingCapacityWatts == 0.0) return 0.0;
        double usedCoolingCapacityWatts = simulationContainer
                .coolingSnapshot()
                .zones()
                .stream()
                .mapToDouble(CoolingZoneSnapshot::usedCoolingCapacityWatts)
                .sum();
        return usedCoolingCapacityWatts / availableCoolingCapacityWatts * 100.0;
    }

    private void updateDisplayedRoomTotalElectricalLoad() {
        // TODO Backend should provide total electrical load, including IT, SUPPLY and EXHAUST electrical power.
        if (!uiComponentContainer.labels().containsKey("lblRoomTotalElectricalLoadValue")) return;
        double totalLoadMegawatts = displayedRoomTotalElectricalLoadMegawatts();
        uiComponentContainer
                .labels()
                .get("lblRoomTotalElectricalLoadValue")
                .setText(String.format("%.2f MW", totalLoadMegawatts));
    }

    private double displayedRoomTotalElectricalLoadMegawatts() {
        double TEMPORARY_HVAC_COP = 3.0;
        double itPowerWatts = simulationContainer
                .operationalSnapshot()
                .racks()
                .values()
                .stream()
                .mapToDouble(RackOperationalSnapshot::currentPowerWatts)
                .sum();

        double usedCoolingCapacityWatts = 0.0;
        if (simulationContainer.coolingSnapshot() != null) {
            usedCoolingCapacityWatts = simulationContainer
                    .coolingSnapshot()
                    .zones()
                    .stream()
                    .mapToDouble(CoolingZoneSnapshot::usedCoolingCapacityWatts)
                    .sum();
        }
        double estimatedHvacElectricalPowerWatts = usedCoolingCapacityWatts / TEMPORARY_HVAC_COP;
        return (itPowerWatts + estimatedHvacElectricalPowerWatts) / 1_000_000.0;
    }
}
