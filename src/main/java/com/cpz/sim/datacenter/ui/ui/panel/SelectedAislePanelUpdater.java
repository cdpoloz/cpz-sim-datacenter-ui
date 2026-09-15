package com.cpz.sim.datacenter.ui.ui.panel;

import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.sim.datacenter.cooling.CoolingZoneDefinition;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.snapshot.CoolingZoneGroupSnapshot;
import com.cpz.sim.datacenter.snapshot.CoolingZoneSnapshot;
import com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot;
import com.cpz.sim.datacenter.snapshot.ServerGroupOperationalSnapshot;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.config.HotAisleDefinition;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;
import com.cpz.sim.datacenter.ui.ui.selection.SelectionManager;
import com.cpz.utils.color.Colors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.cpz.sim.datacenter.ui.main.Launcher.PROPS;
import static com.cpz.sim.datacenter.ui.util.Constants.COLOR_MAX_TEMPERATURE;
import static com.cpz.sim.datacenter.ui.util.Constants.COLOR_MIN_TEMPERATURE;
import static com.cpz.sim.datacenter.ui.util.Constants.COLOR_WHITE_LABEL;

/**
 * Projects operational and cooling snapshots into the selected hot-aisle panel.
 *
 * <p>The returned rack-row temperatures are also the data source for the dynamic gradient.</p>
 *
 * @author CPZ
 */
public class SelectedAislePanelUpdater extends ApplicationComponent {

    private final SimulationContainer simulationContainer;
    private final UiComponentContainer uiComponentContainer;
    private final SelectionManager selectionManager;

    /**
     * Creates an updater that resolves the active aisle through {@link SelectionManager}.
     *
     * @param context Processing color-mapping access
     * @param simulationContainer operational/cooling snapshots and configuration
     * @param uiComponentContainer selected-aisle controls
     * @param selectionManager source of the selected column and hot aisle
     */
    public SelectedAislePanelUpdater(
            ApplicationContext context,
            SimulationContainer simulationContainer,
            UiComponentContainer uiComponentContainer,
            SelectionManager selectionManager
    ) {
        super(context);
        this.simulationContainer = simulationContainer;
        this.uiComponentContainer = uiComponentContainer;
        this.selectionManager = selectionManager;
    }

    /**
     * Updates edge state, cooling metrics, installed-server metrics, and gradient data.
     *
     * @param minServerTemperatureCelsius lower color-scale bound
     * @param maxServerTemperatureCelsius upper color-scale bound
     * @param temperatureFormat temperature label format
     * @param percentageFormat percentage label format
     * @param airflowFormat paired supply/exhaust airflow format
     * @return representative temperatures ordered by rack code
     */
    public List<Float> update(
            float minServerTemperatureCelsius,
            float maxServerTemperatureCelsius,
            String temperatureFormat,
            String percentageFormat,
            String airflowFormat
    ) {
        updateAisleEdgeIndicators();
        updateSelectedAisleLabel();
        List<String> aisleZoneCodes = resolveAisleCoolingZoneCodes(selectedHotAisle());
        CoolingZoneGroupSnapshot coolingGroupSnapshot = simulationContainer.coolingSnapshot().aggregateZones(selectedHotAisle().code(), aisleZoneCodes);
        updateCoolingMetrics(
                aisleZoneCodes,
                coolingGroupSnapshot,
                temperatureFormat,
                percentageFormat,
                airflowFormat
        );
        ServerGroupOperationalSnapshot aisleSnapshot =
                simulationContainer
                        .operationalSnapshot()
                        .findServerGroup(selectedHotAisle().code())
                        .orElseThrow(() -> new IllegalStateException("Missing operational snapshot for aisle: " + selectedHotAisle().code()));
        updateInstalledServerMetrics(
                aisleSnapshot,
                minServerTemperatureCelsius,
                maxServerTemperatureCelsius,
                temperatureFormat,
                percentageFormat
        );
        return calculateSelectedHotAisleTemperatures();
    }

    private void updateAisleEdgeIndicators() {
        boolean leftEdgeAisleSelected = selectedColumn().equals(PROPS.getProperty("datacenter.first.column"));
        boolean rightEdgeAisleSelected = selectedColumn().equals(PROPS.getProperty("datacenter.last.column"));
        uiComponentContainer.indicatorsNullAisle().get("indNullAisleLeft").setOn(leftEdgeAisleSelected);
        uiComponentContainer.indicators().get("indColdAirArrowsLeft").setOn(!leftEdgeAisleSelected);
        uiComponentContainer.indicatorsNullAisle().get("indNullAisleRight").setOn(rightEdgeAisleSelected);
        uiComponentContainer.indicators().get("indColdAirArrowsRight").setOn(!rightEdgeAisleSelected);
    }

    private void updateSelectedAisleLabel() {
        uiComponentContainer.labels().get("lblSelectedAisleValue").setText(selectedHotAisle().displayName());
    }

    private void updateCoolingMetrics(
            List<String> aisleZoneCodes,
            CoolingZoneGroupSnapshot coolingGroupSnapshot,
            String temperatureFormat,
            String percentageFormat,
            String airflowFormat
    ) {
        double thermalCoverage = coolingGroupSnapshot.thermalCoverage();
        uiComponentContainer.labels().get("lblSelectedAisleThermalCoverageValue").setText(String.format(percentageFormat, thermalCoverage * 100.0));
        double deltaTinOut = coolingGroupSnapshot.airTemperatureRiseCelsius();
        uiComponentContainer.labels().get("lblSelectedAisleTemperatureDeltaValue").setText(String.format(temperatureFormat, deltaTinOut));
        double recirculation = coolingGroupSnapshot.averageRecirculationFraction();
        uiComponentContainer.labels().get("lblSelectedAisleRecirculationValue").setText(String.format(percentageFormat, recirculation * 100.0));
        double supplyAirflow =
                aisleZoneCodes
                        .stream()
                        .map(simulationContainer.coolingSnapshot()::findZone)
                        .flatMap(Optional::stream)
                        .mapToDouble(CoolingZoneSnapshot::supplyAirflowCubicMetersPerSecond)
                        .sum();
        double exhaustAirflow =
                aisleZoneCodes
                        .stream()
                        .map(simulationContainer.coolingSnapshot()::findZone)
                        .flatMap(Optional::stream)
                        .mapToDouble(CoolingZoneSnapshot::exhaustAirflowCubicMetersPerSecond)
                        .sum();
        uiComponentContainer.labels().get("lblSelectedAisleAirflowValue").setText(String.format(airflowFormat, supplyAirflow, exhaustAirflow));
    }

    private void updateInstalledServerMetrics(
            ServerGroupOperationalSnapshot aisleSnapshot,
            float minServerTemperatureCelsius,
            float maxServerTemperatureCelsius,
            String temperatureFormat,
            String percentageFormat
    ) {
        Label averageTemperatureLabel = uiComponentContainer.labels().get("lblSelectedAisleAverageTemperatureValue");
        Label maxTemperatureLabel = uiComponentContainer.labels().get("lblSelectedAisleMaximumTemperatureValue");
        Label averageLoadLabel = uiComponentContainer.labels().get("lblSelectedAisleITLoadValue");
        uiComponentContainer
                .indicatorsSelectedAisleMaximumTemperatureServer()
                .values()
                .forEach(indicator -> indicator.setOn(false));
        if (!aisleSnapshot.hasInstalledServers()) {
            averageTemperatureLabel.setTextColor(COLOR_WHITE_LABEL);
            maxTemperatureLabel.setTextColor(COLOR_WHITE_LABEL);
            averageTemperatureLabel.setText("--");
            maxTemperatureLabel.setText("--");
            averageLoadLabel.setText("--");
            return;
        }
        updateMaximumTemperatureServer(
                aisleSnapshot,
                minServerTemperatureCelsius,
                maxServerTemperatureCelsius,
                temperatureFormat,
                maxTemperatureLabel
        );

        updateAverageAisleMetrics(
                aisleSnapshot,
                minServerTemperatureCelsius,
                maxServerTemperatureCelsius,
                temperatureFormat,
                percentageFormat,
                averageTemperatureLabel,
                averageLoadLabel
        );
    }

    private void updateMaximumTemperatureServer(
            ServerGroupOperationalSnapshot aisleSnapshot,
            float minServerTemperatureCelsius,
            float maxServerTemperatureCelsius,
            String temperatureFormat,
            Label maxTemperatureLabel
    ) {
        double maxTemperature = aisleSnapshot.maximumTemperatureCelsius();
        int maxTemperatureColor = resolveTemperatureRangeColor((float) maxTemperature, minServerTemperatureCelsius, maxServerTemperatureCelsius);
        maxTemperatureLabel.setTextColor(maxTemperatureColor);
        maxTemperatureLabel.setText(String.format(temperatureFormat, maxTemperature));
        ServerLocation maxTemperatureLocation =
                aisleSnapshot
                        .maximumTemperatureLocation()
                        .orElseThrow(() -> new IllegalStateException(
                                "The aisle has installed servers, "
                                        + "but does not report the location "
                                        + "of the maximum temperature: "
                                        + selectedHotAisle().code()
                        ));
        String maxTemperatureColumn = maxTemperatureLocation.column();
        String maxTemperatureSide;

        // Wall aisles have one inward-facing side; shared aisles follow column parity.
        if (maxTemperatureColumn.equals(PROPS.getProperty("datacenter.first.column"))) maxTemperatureSide = "Right";
        else if (maxTemperatureColumn.equals(PROPS.getProperty("datacenter.last.column"))) maxTemperatureSide = "Left";
        else {
            int columnNumber = Integer.parseInt(maxTemperatureColumn.replace("C", ""));
            maxTemperatureSide = columnNumber % 2 == 0 ? "Left" : "Right";
        }
        String rackNumber = maxTemperatureLocation.rackCode().value().replace("R", "");
        String indicatorCode = "indSelectedAisleMaximumTemperatureServer" + maxTemperatureSide + rackNumber;
        Indicator maxTemperatureIndicator = uiComponentContainer.indicatorsSelectedAisleMaximumTemperatureServer().get(indicatorCode);
        if (maxTemperatureIndicator == null)
            throw new IllegalStateException("Missing maximum temperature indicator: " + indicatorCode);
        maxTemperatureIndicator.setOnColor(maxTemperatureColor);
        maxTemperatureIndicator.setOn(true);
    }

    private void updateAverageAisleMetrics(
            ServerGroupOperationalSnapshot aisleSnapshot,
            float minServerTemperatureCelsius,
            float maxServerTemperatureCelsius,
            String temperatureFormat,
            String percentageFormat,
            Label averageTemperatureLabel,
            Label averageLoadLabel
    ) {
        if (aisleSnapshot.hasOnlineServers()) {
            double averageTemperature = aisleSnapshot.averageOnlineTemperatureCelsius();
            double averageLoad = aisleSnapshot.averageOnlineUtilization();
            int averageTemperatureColor = resolveTemperatureRangeColor((float) averageTemperature, minServerTemperatureCelsius, maxServerTemperatureCelsius);
            averageTemperatureLabel.setTextColor(averageTemperatureColor);
            averageTemperatureLabel.setText(String.format(temperatureFormat, averageTemperature));
            averageLoadLabel.setText(String.format(percentageFormat, averageLoad * 100));
        } else {
            averageTemperatureLabel.setTextColor(COLOR_WHITE_LABEL);
            averageTemperatureLabel.setText("--");
            averageLoadLabel.setText("--");
        }
    }

    private List<Float> calculateSelectedHotAisleTemperatures() {
        List<Float> temperatures = new ArrayList<>();
        List<String> rackCodes = resolveAisleRackCodes(selectedHotAisle());
        for (String rackCode : rackCodes) {
            float averageRackTemperature = 0;
            for (String column : selectedHotAisle().columns()) {
                RackLocation rackLocation = new RackLocation(column, new RackCode(rackCode));
                RackOperationalSnapshot rackSnapshot = simulationContainer.operationalSnapshot().findRack(rackLocation).orElseThrow();
                averageRackTemperature += (float) rackSnapshot.representativeTemperatureCelsius();
            }
            // Shared aisles contribute one row value by averaging corresponding column racks.
            averageRackTemperature /= selectedHotAisle().columns().size();
            temperatures.add(averageRackTemperature);
        }
        return temperatures;
    }

    private List<String> resolveAisleRackCodes(HotAisleDefinition aisle) {
        String referenceColumn = aisle.columns().getFirst();
        return simulationContainer
                .datacenter()
                .getRacks()
                .stream()
                .filter(rack -> rack.getLocation().column().equals(referenceColumn))
                .map(Rack::getCode)
                .map(RackCode::value)
                .sorted()
                .toList();
    }

    private List<String> resolveAisleCoolingZoneCodes(HotAisleDefinition aisle) {
        Set<String> aisleColumns = new HashSet<>(aisle.columns());
        return simulationContainer
                .coolingConfiguration()
                .zones()
                .stream()
                .filter(zone ->
                        // A zone belongs to the view when it serves any server in an aisle column.
                        zone.serverLocations()
                                .stream()
                                .anyMatch(location -> aisleColumns.contains(location.column()))
                )
                .map(CoolingZoneDefinition::code)
                .sorted()
                .toList();
    }

    private int resolveTemperatureRangeColor(
            float temperature,
            float minServerTemperatureCelsius,
            float maxServerTemperatureCelsius
    ) {
        float factor = sketch().map(temperature, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 1);
        factor = Math.clamp(factor, 0, 1);
        return Colors.lerpColor(COLOR_MIN_TEMPERATURE, COLOR_MAX_TEMPERATURE, factor);
    }

    private String selectedColumn() {
        return selectionManager.selectedColumn();
    }

    private HotAisleDefinition selectedHotAisle() {
        return selectionManager.selectedHotAisle();
    }
}
