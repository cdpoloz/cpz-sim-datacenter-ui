package com.cpz.sim.datacenter.ui.ui.render;

import com.cpz.sim.datacenter.history.DatacenterSimulationStepSnapshot;
import com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.ui.UiStateContainer;

import java.util.List;

import static com.cpz.sim.datacenter.ui.main.Launcher.PROPS;
import static com.cpz.sim.datacenter.ui.util.Constants.COLOR_BLUE_LABEL;

/**
 * Draws Global Overview panel charts from the recorded simulation history.
 *
 * @author CPZ
 */
public class GlobalOverviewRenderer extends ApplicationComponent {

    private final SimulationContainer simulationContainer;
    private final UiStateContainer uiStateContainer;

    /**
     * Creates a renderer for Global Overview history charts.
     *
     * @param context Processing drawing and coordinate-mapping access
     * @param simulationContainer recorded simulation history
     * @param uiStateContainer shared UI temperature scale
     */
    public GlobalOverviewRenderer(
            ApplicationContext context,
            SimulationContainer simulationContainer,
            UiStateContainer uiStateContainer
    ) {
        super(context);
        this.simulationContainer = simulationContainer;
        this.uiStateContainer = uiStateContainer;
    }

    /** Draws all currently implemented Global Overview charts. */
    public void draw() {
        if (simulationContainer == null || simulationContainer.simulationHistory() == null) return;
        sketch().pushStyle();
        drawDisplayedRoomAverageTemperatureGraph();
        drawDisplayedRoomMaximumRackTemperatureGraph();
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
                maxY
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
                maxY
        );
    }

    private void drawTemperatureSeries(
            List<Double> temperatures,
            float minX,
            float minY,
            float maxY
    ) {
        sketch().strokeWeight(Float.parseFloat(PROPS.getProperty("ui.global.overview.graph.stroke.weigth")) * sketch().height);
        sketch().stroke(COLOR_BLUE_LABEL);
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
        float y = sketch().map(
                (float) temperature,
                uiStateContainer.minServerTemperatureCelsius(),
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
}
