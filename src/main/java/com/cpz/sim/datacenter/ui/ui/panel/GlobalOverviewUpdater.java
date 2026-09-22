package com.cpz.sim.datacenter.ui.ui.panel;

import com.cpz.processing.controls.controls.label.Label;
import com.cpz.sim.datacenter.history.DatacenterSimulationStepSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingZoneSnapshot;
import com.cpz.sim.datacenter.snapshot.DatacenterOperationalSnapshot;
import com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;
import com.cpz.sim.datacenter.ui.ui.UiFormatContainer;
import com.cpz.utils.color.Colors;
import processing.core.PApplet;

import java.util.Optional;

import static com.cpz.sim.datacenter.ui.util.Constants.*;

/**
 * Projects simulation summary values into the Global Overview panel labels.
 *
 * @author CPZ
 */
public class GlobalOverviewUpdater extends ApplicationComponent {

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
            ApplicationContext context,
            SimulationContainer simulationContainer,
            UiComponentContainer uiComponentContainer,
            UiFormatContainer uiFormatContainer
    ) {
        super(context);
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
        updateDisplayedRoomPue();
        updateDisplayedRoomSystemStatus();
    }

    private void updateDisplayedRoomTemperature() {
        if (!uiComponentContainer.labels().containsKey("lblAverageServerTemperatureValue")) return;
        latestDisplayedRoomAverageTemperatureCelsius()
                .ifPresent(averageRoomTemperatureCelsius ->
                        uiComponentContainer
                                .labels()
                                .get("lblAverageServerTemperatureValue")
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
        if (!uiComponentContainer.labels().containsKey("lblRoomTotalElectricalLoadValue")) return;
        double totalLoadMegawatts = displayedRoomTotalElectricalLoadMegawatts();
        uiComponentContainer
                .labels()
                .get("lblRoomTotalElectricalLoadValue")
                .setText(String.format(uiFormatContainer.powerMw(), totalLoadMegawatts));
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

    private void updateDisplayedRoomPue() {
        if (!uiComponentContainer.labels().containsKey("lblRoomPueValue")) return;
        double pue = displayedRoomPue();
        String text = Double.isFinite(pue) ? String.format("%.2f", pue) : "--";
        uiComponentContainer.labels().get("lblRoomPueValue").setText(text);
        String key = "indRoomPueBar";
        int iMax = (int) PApplet.map((float) pue, 1.0f, 2.2f, 1, 6);
        for (int i = 0; i < 6; i++)
            uiComponentContainer.indicators().get(key + (i + 1)).setOn(i < iMax);
    }

    private double displayedRoomPue() {
        double itPowerWatts = displayedRoomItPowerWatts();
        if (itPowerWatts == 0.0) return Double.NaN;
        double estimatedTotalElectricalPowerWatts = displayedRoomEstimatedTotalElectricalPowerWatts();
        return estimatedTotalElectricalPowerWatts / itPowerWatts;
    }

    private double displayedRoomItPowerWatts() {
        return simulationContainer
                .operationalSnapshot()
                .racks()
                .values()
                .stream()
                .mapToDouble(RackOperationalSnapshot::currentPowerWatts)
                .sum();
    }

    private double displayedRoomEstimatedTotalElectricalPowerWatts() {
        double TEMPORARY_HVAC_COP = 3.0;
        double itPowerWatts = displayedRoomItPowerWatts();
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
        return itPowerWatts + estimatedHvacElectricalPowerWatts;
    }

    private void updateDisplayedRoomSystemStatus() {
        if (!uiComponentContainer.labels().containsKey("lblSystemStatusValue")) return;
        Label lblSystemStatusValue = uiComponentContainer.labels().get("lblSystemStatusValue");
        String status = displayedRoomSystemStatus();
        lblSystemStatusValue.setText(status);
        int c = switch (status) {
            case "NORMAL" -> COLOR_GREEN_LABEL;
            case "WATCH" -> COLOR_BLUE_LABEL;
            case "WARNING" -> COLOR_YELLOW_LABEL;
            case "CRITICAL" -> COLOR_MAGENTA_LABEL;
            default -> Colors.argb(0, 0, 0, 0);
        };
        lblSystemStatusValue.setTextColor(c);
        uiComponentContainer.indicators().get("indSystemStatusNormal").setOn(status.equals("NORMAL"));
        uiComponentContainer.indicators().get("indSystemStatusWatch").setOn(status.equals("WATCH"));
        uiComponentContainer.indicators().get("indSystemStatusWarning").setOn(status.equals("WARNING"));
        uiComponentContainer.indicators().get("indSystemStatusCritical").setOn(status.equals("CRITICAL"));
    }

    private String displayedRoomSystemStatus() {
        if (simulationContainer == null || simulationContainer.operationalSnapshot() == null) return "NO DATA";
        int onlineServerCount = simulationContainer
                .operationalSnapshot()
                .racks()
                .values()
                .stream()
                .mapToInt(RackOperationalSnapshot::onlineServerCount)
                .sum();
        if (onlineServerCount == 0) return "IDLE";
        double averageTemperature = displayedRoomAverageTemperatureCelsius();
        double maximumRackTemperature = displayedRoomMaximumRackTemperatureCelsius();
        double itLoad = displayedRoomAverageItLoadPercentage();
        double hvacLoad = displayedRoomHvacLoadPercentage();
        double pue = displayedRoomPue();
        if (averageTemperature > 62.0 || maximumRackTemperature > 72.0 || itLoad > 97.0 || hvacLoad > 98.0 || pue > 2.20)
            return "CRITICAL";
        if (averageTemperature > 55.0 || maximumRackTemperature > 66.0 || itLoad > 90.0 || hvacLoad > 90.0 || pue > 1.80)
            return "WARNING";
        if (averageTemperature > 48.0 || maximumRackTemperature > 60.0 || itLoad > 80.0 || hvacLoad > 75.0 || pue > 1.50)
            return "WATCH";
        return "NORMAL";
    }

    private double displayedRoomAverageTemperatureCelsius() {
        if (simulationContainer == null || simulationContainer.operationalSnapshot() == null) return Double.NaN;
        return displayedRoomAverageTemperatureCelsius(simulationContainer.operationalSnapshot());
    }

    private double displayedRoomAverageTemperatureCelsius(DatacenterOperationalSnapshot operationalSnapshot) {
        int onlineServerCount = operationalSnapshot
                .racks()
                .values()
                .stream()
                .mapToInt(RackOperationalSnapshot::onlineServerCount)
                .sum();
        double temperatureSumCelsius = operationalSnapshot
                .racks()
                .values()
                .stream()
                .filter(RackOperationalSnapshot::hasOnlineServers)
                .mapToDouble(rackSnapshot -> rackSnapshot.averageOnlineTemperatureCelsius() * rackSnapshot.onlineServerCount()
                )
                .sum();
        if (onlineServerCount == 0) return operationalSnapshot.roomTemperatureCelsius();
        return temperatureSumCelsius / onlineServerCount;
    }

    private double displayedRoomMaximumRackTemperatureCelsius(DatacenterOperationalSnapshot operationalSnapshot) {
        if (operationalSnapshot.hottestRackLocation().isEmpty()) return Double.NaN;
        return operationalSnapshot.hottestRackAverageTemperatureCelsius();
    }

    private double displayedRoomMaximumRackTemperatureCelsius() {
        if (simulationContainer == null || simulationContainer.operationalSnapshot() == null) return Double.NaN;
        return displayedRoomMaximumRackTemperatureCelsius(simulationContainer.operationalSnapshot());
    }
}
