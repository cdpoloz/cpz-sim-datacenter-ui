package com.cpz.sim.datacenter.ui.ui.render;

import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.utils.color.Colors;

import java.util.List;

import static com.cpz.sim.datacenter.ui.main.Launcher.PROPS;
import static com.cpz.sim.datacenter.ui.util.Constants.*;

/**
 * Renders the selected hot aisle temperature gradient.
 *
 * @author CPZ
 */
public class SelectedHotAisleTemperatureGradientRenderer extends ApplicationComponent {

    public SelectedHotAisleTemperatureGradientRenderer(ApplicationContext context) {
        super(context);
    }

    public void draw(List<Float> selectedHotAisleTemperatures, float minServerTemperatureCelsius, float maxServerTemperatureCelsius) {
        if (selectedHotAisleTemperatures == null || selectedHotAisleTemperatures.isEmpty()) return;
        sketch().pushStyle();
        sketch().noFill();
        sketch().strokeWeight(1);
        float y = Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.y")) * sketch().height;
        float height = Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.height")) * sketch().height;
        float totalHeight = y + selectedHotAisleTemperatures.size() * height;
        float x = Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.x")) * sketch().width;
        float minWidth = Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.min.width")) * sketch().height;
        float maxWidth = Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.max.width")) * sketch().height;
        drawFirstSegment(
                selectedHotAisleTemperatures,
                minServerTemperatureCelsius,
                maxServerTemperatureCelsius,
                x,
                y,
                height,
                totalHeight,
                minWidth,
                maxWidth
        );
        drawTemperatureSegments(
                selectedHotAisleTemperatures,
                minServerTemperatureCelsius,
                maxServerTemperatureCelsius,
                x,
                y,
                height,
                totalHeight,
                minWidth,
                maxWidth
        );
        drawBorder();
        sketch().popStyle();
    }

    private void drawFirstSegment(
            List<Float> selectedHotAisleTemperatures,
            float minServerTemperatureCelsius,
            float maxServerTemperatureCelsius,
            float x,
            float y,
            float segmentHeight,
            float totalHeight,
            float minWidth,
            float maxWidth
    ) {
        float minY = y;
        float maxY = y + segmentHeight;
        float factor = sketch().map(selectedHotAisleTemperatures.getFirst(), minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 1);
        factor = Math.clamp(factor, 0, 1);
        int color = Colors.lerpColor(COLOR_MIN_TEMPERATURE, COLOR_MAX_TEMPERATURE, factor);
        for (int currentY = (int) minY; currentY < (int) maxY; currentY++) {
            float width = sketch().map(currentY, y, totalHeight, minWidth, maxWidth);
            sketch().stroke(color);
            sketch().line(x - width * 0.5f, currentY, x + width * 0.5f, currentY);
        }
    }

    private void drawTemperatureSegments(
            List<Float> selectedHotAisleTemperatures,
            float minServerTemperatureCelsius,
            float maxServerTemperatureCelsius,
            float x,
            float y,
            float segmentHeight,
            float totalHeight,
            float minWidth,
            float maxWidth
    ) {
        for (int i = 0; i < selectedHotAisleTemperatures.size() - 1; i++) {
            float temperature = selectedHotAisleTemperatures.get(i);
            float nextTemperature = selectedHotAisleTemperatures.get(i + 1);
            float minY = y + (i + 1) * segmentHeight;
            float maxY = y + (i + 2) * segmentHeight;
            int color = Colors.lerpColor(COLOR_MIN_TEMPERATURE, COLOR_MAX_TEMPERATURE, temperatureFactor(temperature, minServerTemperatureCelsius, maxServerTemperatureCelsius));
            int nextColor = Colors.lerpColor(COLOR_MIN_TEMPERATURE, COLOR_MAX_TEMPERATURE, temperatureFactor(nextTemperature, minServerTemperatureCelsius, maxServerTemperatureCelsius));
            for (int currentY = (int) minY; currentY < (int) maxY; currentY++) {
                float width = sketch().map(currentY, y, totalHeight, minWidth, maxWidth);
                float factor = sketch().map(currentY, minY, maxY, 0, 1);
                int lineColor = Colors.lerpColor(color, nextColor, factor);
                sketch().stroke(lineColor);
                sketch().line(x - width * 0.5f, currentY, x + width * 0.5f, currentY);
            }
        }
    }

    private float temperatureFactor(float temperature, float minServerTemperatureCelsius, float maxServerTemperatureCelsius) {
        float factor = sketch().map(temperature, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 1);
        return Math.clamp(factor, 0, 1);
    }

    private void drawBorder() {
        sketch().strokeWeight(Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.stroke.weigth")) * sketch().height);
        sketch().stroke(COLOR_TEMPERATURE_GRADIENT_BORDER);
        sketch().line(
                Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.x.00")) * sketch().width,
                Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.y.00")) * sketch().height,
                Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.x.03")) * sketch().width,
                Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.y.03")) * sketch().height
        );
        sketch().line(
                Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.x.01")) * sketch().width,
                Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.y.01")) * sketch().height,
                Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.x.02")) * sketch().width,
                Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.y.02")) * sketch().height
        );
    }
}