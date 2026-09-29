package com.cpz.sim.datacenter.ui.ui.render;

import com.cpz.sim.datacenter.history.DatacenterSimulationStepSnapshot;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.snapshot.*;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.ui.UiStateContainer;
import com.cpz.sim.datacenter.ui.ui.selection.SelectionManager;
import processing.core.PApplet;

import java.util.List;

import static com.cpz.sim.datacenter.ui.main.Launcher.PROPS;
import static com.cpz.sim.datacenter.ui.util.Constants.*;

/**
 * Draws Global Overview panel charts from the recorded simulation history.
 *
 * @author CPZ
 */
public class GlobalOverviewRenderer extends ApplicationComponent {

    private final SimulationContainer simulationContainer;
    private final UiStateContainer uiStateContainer;
    private final SelectionManager selectionManager;

    /**
     * Creates a renderer for Global Overview history charts.
     *
     * @param context             Processing drawing and coordinate-mapping access
     * @param simulationContainer recorded simulation history
     * @param uiStateContainer    shared UI temperature scale
     */
    public GlobalOverviewRenderer(
            ApplicationContext context,
            SimulationContainer simulationContainer,
            UiStateContainer uiStateContainer,
            SelectionManager selectionManager
    ) {
        super(context);
        this.simulationContainer = simulationContainer;
        this.uiStateContainer = uiStateContainer;
        this.selectionManager = selectionManager;
    }

    /**
     * Draws all currently implemented Global Overview charts.
     */
    public void draw() {
        if (simulationContainer == null || simulationContainer.simulationHistory() == null) return;
        sketch().pushStyle();
        drawDisplayedRoomAverageTemperatureGraph();
        drawDisplayedRoomMaximumRackTemperatureGraph();
        drawDisplayedRoomAverageItLoadGraph();
        drawDisplayedRoomHvacLoadGraph();
        drawDisplayedRoomEstimatedPowerLoadGraph();
        drawDisplayedRoomHottestServerTemperatureGraph();
        drawDisplayedRoomItAndPowerLoadGraph();
        sketch().popStyle();
    }

    private void drawDisplayedRoomAverageTemperatureGraph() {
        float minX = Float.parseFloat(PROPS.getProperty("ui.average.room.temperature.min.x"));
        float maxX = Float.parseFloat(PROPS.getProperty("ui.average.room.temperature.max.x"));
        float minY = Float.parseFloat(PROPS.getProperty("ui.average.room.temperature.min.y"));
        float maxY = Float.parseFloat(PROPS.getProperty("ui.average.room.temperature.max.y"));
        int sampleCount = (int) ((maxX - minX) * sketch().width);
        drawTemperatureSeries(
                latestDisplayedRoomAverageTemperatures(sampleCount),
                minX,
                minY,
                maxY,
                COLOR_BLUE_LABEL
        );
    }

    private void drawDisplayedRoomMaximumRackTemperatureGraph() {
        float minX = Float.parseFloat(PROPS.getProperty("ui.maximum.rack.temperature.min.x"));
        float maxX = Float.parseFloat(PROPS.getProperty("ui.maximum.rack.temperature.max.x"));
        float minY = Float.parseFloat(PROPS.getProperty("ui.maximum.rack.temperature.min.y"));
        float maxY = Float.parseFloat(PROPS.getProperty("ui.maximum.rack.temperature.max.y"));
        int sampleCount = (int) ((maxX - minX) * sketch().width);
        drawTemperatureSeries(
                latestDisplayedRoomMaximumRackTemperatures(sampleCount),
                minX,
                minY,
                maxY,
                COLOR_BLUE_LABEL
        );
    }

    private void drawTemperatureSeries(List<Double> temperatures, float minX, float minY, float maxY, int strokeColor) {
        sketch().strokeWeight(Float.parseFloat(PROPS.getProperty("ui.global.overview.graph.stroke.weigth")) * sketch().height);
        sketch().stroke(strokeColor);
        for (int i = 1; i < temperatures.size(); i++) {
            double previousTemperature = temperatures.get(i - 1);
            double temperature = temperatures.get(i);
            if (!Double.isFinite(previousTemperature) || !Double.isFinite(temperature)) continue;
            float x = minX * sketch().width + i;
            float y = yForTemperature(temperature, minY, maxY);
            float previousX = minX * sketch().width + i - 1;
            float previousY = yForTemperature(previousTemperature, minY, maxY);
            sketch().line(x, y, previousX, previousY);
        }
    }

    private float yForTemperature(double temperature, float minY, float maxY) {
        float y = PApplet.map(
                (float) temperature,
                0,
                uiStateContainer.maxServerTemperatureCelsius(),
                maxY * sketch().height,
                minY * sketch().height
        );
        return Math.clamp(y, minY * sketch().height, maxY * sketch().height);
    }

    private List<Double> latestDisplayedRoomAverageTemperatures(int n) {
        return simulationContainer
                .simulationHistory()
                .latest(n)
                .stream()
                .map(this::displayedRoomAverageTemperatureCelsius)
                .toList();
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

    private List<Double> latestDisplayedRoomMaximumRackTemperatures(int n) {
        return simulationContainer
                .simulationHistory()
                .latest(n)
                .stream()
                .map(this::displayedRoomMaximumRackTemperatureCelsius)
                .toList();
    }

    private double displayedRoomMaximumRackTemperatureCelsius(DatacenterSimulationStepSnapshot snapshot) {
        return snapshot.operationalSnapshot().hottestRackAverageTemperatureCelsius();
    }

    private void drawDisplayedRoomAverageItLoadGraph() {
        float minX = Float.parseFloat(PROPS.getProperty("ui.average.it.load.min.x"));
        float maxX = Float.parseFloat(PROPS.getProperty("ui.average.it.load.max.x"));
        float minY = Float.parseFloat(PROPS.getProperty("ui.average.it.load.min.y"));
        float maxY = Float.parseFloat(PROPS.getProperty("ui.average.it.load.max.y"));
        int sampleCount = (int) ((maxX - minX) * sketch().width);
        drawPercentageSeries(latestDisplayedRoomAverageItLoads(sampleCount), minX, minY, maxY, COLOR_BLUE_LABEL);
    }

    private void drawPercentageSeries(List<Double> percentages, float minX, float minY, float maxY, int strokeColor) {
        sketch().strokeWeight(Float.parseFloat(PROPS.getProperty("ui.global.overview.graph.stroke.weigth")) * sketch().height);
        sketch().stroke(strokeColor);
        for (int i = 1; i < percentages.size(); i++) {
            double previousPercentage = percentages.get(i - 1);
            double percentage = percentages.get(i);
            if (!Double.isFinite(previousPercentage) || !Double.isFinite(percentage)) continue;
            float x = minX * sketch().width + i;
            float y = yForPercentage(percentage, minY, maxY);
            float previousX = minX * sketch().width + i - 1;
            float previousY = yForPercentage(previousPercentage, minY, maxY);
            sketch().line(x, y, previousX, previousY);
        }
    }

    private float yForPercentage(double percentage, float minY, float maxY) {
        float y = PApplet.map((float) percentage, 0, 100, maxY * sketch().height, minY * sketch().height);
        return Math.clamp(y, minY * sketch().height, maxY * sketch().height);
    }

    private List<Double> latestDisplayedRoomAverageItLoads(int n) {
        return simulationContainer
                .simulationHistory()
                .latest(n)
                .stream()
                .map(this::displayedRoomAverageItLoadPercentage)
                .toList();
    }

    private double displayedRoomAverageItLoadPercentage(DatacenterSimulationStepSnapshot snapshot) {
        int onlineServerCount = snapshot
                .operationalSnapshot()
                .racks()
                .values()
                .stream()
                .mapToInt(RackOperationalSnapshot::onlineServerCount)
                .sum();
        if (onlineServerCount == 0) return 0.0;
        return snapshot
                .operationalSnapshot()
                .racks()
                .values()
                .stream()
                .filter(RackOperationalSnapshot::hasOnlineServers)
                .mapToDouble(rackSnapshot -> rackSnapshot.averageOnlineUtilization() * rackSnapshot.onlineServerCount())
                .sum() / onlineServerCount * 100.0;
    }

    private void drawDisplayedRoomHvacLoadGraph() {
        float minX = Float.parseFloat(PROPS.getProperty("ui.hvac.load.min.x"));
        float maxX = Float.parseFloat(PROPS.getProperty("ui.hvac.load.max.x"));
        float minY = Float.parseFloat(PROPS.getProperty("ui.hvac.load.min.y"));
        float maxY = Float.parseFloat(PROPS.getProperty("ui.hvac.load.max.y"));
        int sampleCount = (int) ((maxX - minX) * sketch().width);
        drawPercentageSeries(latestDisplayedRoomHvacLoads(sampleCount), minX, minY, maxY, COLOR_BLUE_LABEL);
    }

    private List<Double> latestDisplayedRoomHvacLoads(int n) {
        return simulationContainer
                .simulationHistory()
                .latest(n)
                .stream()
                .map(this::displayedRoomHvacLoadPercentage)
                .toList();
    }

    private double displayedRoomHvacLoadPercentage(DatacenterSimulationStepSnapshot snapshot) {
        return snapshot
                .coolingSnapshot()
                .map(this::displayedRoomHvacLoadPercentage)
                .orElse(0.0);
    }

    private double displayedRoomHvacLoadPercentage(CoolingSnapshot coolingSnapshot) {
        double availableCoolingCapacityWatts = coolingSnapshot
                .zones()
                .stream()
                .mapToDouble(CoolingZoneSnapshot::availableCoolingCapacityWatts)
                .sum();
        if (availableCoolingCapacityWatts == 0.0) return 0.0;
        double usedCoolingCapacityWatts = coolingSnapshot
                .zones()
                .stream()
                .mapToDouble(CoolingZoneSnapshot::usedCoolingCapacityWatts)
                .sum();
        return usedCoolingCapacityWatts / availableCoolingCapacityWatts * 100.0;
    }

    private void drawDisplayedRoomEstimatedPowerLoadGraph() {
        float minX = Float.parseFloat(PROPS.getProperty("ui.total.electrical.load.min.x"));
        float maxX = Float.parseFloat(PROPS.getProperty("ui.total.electrical.load.max.x"));
        float minY = Float.parseFloat(PROPS.getProperty("ui.total.electrical.load.min.y"));
        float maxY = Float.parseFloat(PROPS.getProperty("ui.total.electrical.load.max.y"));
        int sampleCount = (int) ((maxX - minX) * sketch().width);
        drawMegawattSeries(latestDisplayedRoomEstimatedPowerLoads(sampleCount), minX, minY, maxY);
    }

    private List<Double> latestDisplayedRoomEstimatedPowerLoads(int n) {
        return simulationContainer
                .simulationHistory()
                .latest(n)
                .stream()
                .map(this::displayedRoomEstimatedPowerLoadMegawatts)
                .toList();
    }

    private double displayedRoomEstimatedPowerLoadMegawatts(DatacenterSimulationStepSnapshot snapshot) {
        double TEMPORARY_HVAC_COP = 3.0;
        double itPowerWatts = snapshot
                .operationalSnapshot()
                .racks()
                .values()
                .stream()
                .mapToDouble(RackOperationalSnapshot::currentPowerWatts)
                .sum();
        double usedCoolingCapacityWatts = snapshot
                .coolingSnapshot()
                .map(coolingSnapshot -> coolingSnapshot
                        .zones()
                        .stream()
                        .mapToDouble(CoolingZoneSnapshot::usedCoolingCapacityWatts)
                        .sum())
                .orElse(0.0);
        double estimatedHvacElectricalPowerWatts = usedCoolingCapacityWatts / TEMPORARY_HVAC_COP;
        return (itPowerWatts + estimatedHvacElectricalPowerWatts) / 1_000_000.0;
    }

    private void drawMegawattSeries(List<Double> megawatts, float minX, float minY, float maxY) {
        sketch().strokeWeight(Float.parseFloat(PROPS.getProperty("ui.global.overview.graph.stroke.weigth")) * sketch().height);
        sketch().stroke(COLOR_BLUE_LABEL);
        for (int i = 1; i < megawatts.size(); i++) {
            double previousMegawatts = megawatts.get(i - 1);
            double currentMegawatts = megawatts.get(i);
            if (!Double.isFinite(previousMegawatts) || !Double.isFinite(currentMegawatts)) continue;
            float x = minX * sketch().width + i;
            float y = yForMegawatts(currentMegawatts, minY, maxY);
            float previousX = minX * sketch().width + i - 1;
            float previousY = yForMegawatts(previousMegawatts, minY, maxY);
            sketch().line(x, y, previousX, previousY);
        }
    }

    private float yForMegawatts(double megawatts, float minY, float maxY) {
        float y = PApplet.map((float) megawatts, 0.0f, 0.35f, maxY * sketch().height, minY * sketch().height);
        return Math.clamp(y, minY * sketch().height, maxY * sketch().height);
    }

    private void drawDisplayedRoomHottestServerTemperatureGraph() {
        float minX = Float.parseFloat(PROPS.getProperty("ui.hottest.server.temperature.min.x"));
        float maxX = Float.parseFloat(PROPS.getProperty("ui.hottest.server.temperature.max.x"));
        float minY = Float.parseFloat(PROPS.getProperty("ui.hottest.server.temperature.min.y"));
        float maxY = Float.parseFloat(PROPS.getProperty("ui.hottest.server.temperature.max.y"));
        int sampleCount = (int) ((maxX - minX) * sketch().width);
        RackLocation selectedRackLocation = new RackLocation(selectionManager.selectedColumn(), selectionManager.selectedRack());
        drawTemperatureSeries(
                latestDisplayedSelectedRackHottestServerTemperatures(selectedRackLocation, sampleCount),
                minX,
                minY,
                maxY,
                COLOR_MAX_TEMPERATURE
        );
        drawTemperatureSeries(
                latestDisplayedRoomAverageTemperatures(sampleCount),
                minX,
                minY,
                maxY,
                COLOR_MIN_TEMPERATURE
        );
    }

    private List<Double> latestDisplayedSelectedRackHottestServerTemperatures(RackLocation selectedRackLocation, int sampleCount) {
        if (selectedRackLocation == null || sampleCount <= 0) return List.of();
        List<DatacenterSimulationStepSnapshot> history = simulationContainer.simulationHistory().snapshots();
        int fromIndex = Math.max(0, history.size() - sampleCount);
        return history
                .subList(fromIndex, history.size())
                .stream()
                .map(DatacenterSimulationStepSnapshot::temperatureSnapshot)
                .map(temperatureSnapshot ->
                        temperatureSnapshot
                                .servers()
                                .stream()
                                .filter(server ->
                                        server.location()
                                                .column()
                                                .equals(selectedRackLocation.column()))
                                .filter(server ->
                                        server.location()
                                                .rackCode()
                                                .equals(selectedRackLocation.rackCode()))
                                .mapToDouble(ServerTemperatureSnapshot::temperatureCelsius)
                                .max()
                                .orElse(Double.NaN)
                )
                .toList();
    }

    private void drawDisplayedRoomItAndPowerLoadGraph() {
        float minX = Float.parseFloat(PROPS.getProperty("ui.it.total.load.min.x"));
        float maxX = Float.parseFloat(PROPS.getProperty("ui.it.total.load.max.x"));
        float minY = Float.parseFloat(PROPS.getProperty("ui.it.total.load.min.y"));
        float maxY = Float.parseFloat(PROPS.getProperty("ui.it.total.load.max.y"));
        int sampleCount = (int) ((maxX - minX) * sketch().width);
        drawMegawattSeries(
                latestDisplayedRoomItLoadsMegawatts(sampleCount),
                minX,
                minY,
                maxY,
                COLOR_BLUE_LABEL
        );
        drawMegawattSeries(
                latestDisplayedRoomTotalLoadsMegawatts(sampleCount),
                minX,
                minY,
                maxY,
                COLOR_YELLOW_LABEL
        );
    }

    private List<Double> latestDisplayedRoomItLoadsMegawatts(int n) {
        return simulationContainer
                .simulationHistory()
                .latest(n)
                .stream()
                .map(snapshot -> snapshot.operationalSnapshot()
                        .currentItPowerWatts() / 1_000_000.0)
                .toList();
    }

    private List<Double> latestDisplayedRoomTotalLoadsMegawatts(int n) {
        return simulationContainer
                .simulationHistory()
                .latest(n)
                .stream()
                .map(snapshot -> {
                    double totalPowerWatts = snapshot.operationalSnapshot().totalFacilityPowerWatts();
                    return Double.isFinite(totalPowerWatts)
                            ? totalPowerWatts / 1_000_000.0
                            : Double.NaN;
                })
                .toList();
    }

    private void drawMegawattSeries(List<Double> percentages, float minX, float minY, float maxY, int strokeColor) {
        sketch().strokeWeight(Float.parseFloat(PROPS.getProperty("ui.global.overview.graph.stroke.weigth")) * sketch().height);
        sketch().stroke(strokeColor);
        float displayedRoomMaximumPowerMegawatts = (float) displayedRoomMaximumPowerMegawatts();
        for (int i = 1; i < percentages.size(); i++) {
            double previousPercentage = percentages.get(i - 1);
            double percentage = percentages.get(i);
            if (!Double.isFinite(previousPercentage) || !Double.isFinite(percentage)) continue;
            float x = minX * sketch().width + i;
            float y = yForPower(percentage, minY, maxY, displayedRoomMaximumPowerMegawatts);
            float previousX = minX * sketch().width + i - 1;
            float previousY = yForPower(previousPercentage, minY, maxY, displayedRoomMaximumPowerMegawatts);
            sketch().line(x, y, previousX, previousY);
        }
    }

    private double displayedRoomMaximumPowerMegawatts() {
        DatacenterOperationalSnapshot operationalSnapshot = simulationContainer.operationalSnapshot();
        double maxItPowerWatts = operationalSnapshot.maxItPowerWatts();
        double maxCoolingPowerWatts = simulationContainer.coolingSnapshot() == null
                ? 0.0
                : simulationContainer.coolingSnapshot().ratedElectricalPowerWatts();
        return (maxItPowerWatts + maxCoolingPowerWatts) / 1_000_000.0;
    }

    private float yForPower(double power, float minY, float maxY, float displayedRoomMaximumPowerMegawatts) {



        float y = PApplet.map((float) power, 0, displayedRoomMaximumPowerMegawatts, maxY * sketch().height, minY * sketch().height);
        return Math.clamp(y, minY * sketch().height, maxY * sketch().height);
    }

}
