package com.cpz.sim.datacenter.ui.ui.render;

import com.cpz.sim.datacenter.history.HotAisleTemperatureSample;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.ui.UiStateContainer;
import com.cpz.utils.color.Colors;
import processing.core.PApplet;

import java.util.List;

import static com.cpz.sim.datacenter.ui.main.Launcher.PROPS;
import static com.cpz.sim.datacenter.ui.util.Constants.*;

/**
 * Draws the historical average temperature chart for the selected hot aisle.
 *
 * @author CPZ
 */
public class SelectedHotAisleTemperatureHistoryRenderer extends ApplicationComponent {

    private final SimulationContainer simulationContainer;
    private final UiStateContainer uiStateContainer;

    /**
     * Creates a renderer for the selected hot aisle temperature history.
     *
     * @param context Processing drawing and coordinate-mapping access
     * @param simulationContainer shared simulation state
     * @param uiStateContainer shared UI state and temperature scale
     */
    public SelectedHotAisleTemperatureHistoryRenderer(
            ApplicationContext context,
            SimulationContainer simulationContainer,
            UiStateContainer uiStateContainer
    ) {
        super(context);
        this.simulationContainer = simulationContainer;
        this.uiStateContainer = uiStateContainer;
    }

    /**
     * Draws the historical average temperature chart for one hot aisle.
     *
     * @param selectedHotAisleCode selected hot-aisle identifier
     */
    public void draw(String selectedHotAisleCode) {
        if (simulationContainer == null
                || simulationContainer.simulationHistoryRecorder() == null
                || selectedHotAisleCode == null
                || selectedHotAisleCode.isBlank()) {
            return;
        }
        List<HotAisleTemperatureSample> samples =
                simulationContainer
                        .simulationHistoryRecorder()
                        .hotAisleTemperatureHistory()
                        .map(history -> history.samples(selectedHotAisleCode))
                        .orElse(List.of());
        if (samples.size() < 2) return;
        float minX = Float.parseFloat(PROPS.getProperty("ui.hot.aisle.average.temperature.min.x"));
        float maxX = Float.parseFloat(PROPS.getProperty("ui.hot.aisle.average.temperature.max.x"));
        float minY = Float.parseFloat(PROPS.getProperty("ui.hot.aisle.average.temperature.min.y"));
        float maxY = Float.parseFloat(PROPS.getProperty("ui.hot.aisle.average.temperature.max.y"));
        int sampleCount = (int) ((maxX - minX) * sketch().width);
        if (sampleCount < 2) return;
        int fromIndex = Math.max(0, samples.size() - sampleCount);
        List<HotAisleTemperatureSample> displayedSamples = samples.subList(fromIndex, samples.size());
        sketch().pushStyle();
        sketch().strokeWeight(Float.parseFloat(PROPS.getProperty("ui.global.overview.graph.stroke.weigth")) * sketch().height);
        for (int i = 1; i < displayedSamples.size(); i++) {
            HotAisleTemperatureSample previousSample = displayedSamples.get(i - 1);
            HotAisleTemperatureSample currentSample = displayedSamples.get(i);
            double previousTemperature = previousSample.averageTemperatureCelsius();
            double currentTemperature = currentSample.averageTemperatureCelsius();
            if (!Double.isFinite(previousTemperature) || !Double.isFinite(currentTemperature)) continue;
            int temperatureColor = resolveTemperatureRangeColor(
                    (float) currentTemperature,
                    uiStateContainer.minServerTemperatureCelsius(),
                    uiStateContainer.maxServerTemperatureCelsius()
            );
            sketch().stroke(temperatureColor);
            float x = minX * sketch().width + i;
            float previousX = minX * sketch().width + i - 1;
            float y = yForTemperature(currentTemperature, minY, maxY);
            float previousY = yForTemperature(previousTemperature, minY, maxY);
            sketch().line(x, y, previousX, previousY);
        }
        sketch().popStyle();
    }

    private int resolveTemperatureRangeColor(
            float temperatureCelsius,
            float minimumTemperatureCelsius,
            float maximumTemperatureCelsius
    ) {
        float normalizedTemperature = PApplet.map(
                temperatureCelsius,
                minimumTemperatureCelsius,
                maximumTemperatureCelsius,
                0.0f,
                1.0f
        );
        normalizedTemperature = Math.clamp(normalizedTemperature, 0.0f, 1.0f);
        return Colors.lerpColor(COLOR_MIN_TEMPERATURE, COLOR_MAX_TEMPERATURE, normalizedTemperature);
    }

    private float yForTemperature(double temperatureCelsius, float minY, float maxY) {
        float y = PApplet.map(
                (float) temperatureCelsius,
                uiStateContainer.minServerTemperatureCelsius(),
                uiStateContainer.maxServerTemperatureCelsius(),
                maxY * sketch().height,
                minY * sketch().height
        );
        return Math.clamp(y, minY * sketch().height, maxY * sketch().height);
    }
}