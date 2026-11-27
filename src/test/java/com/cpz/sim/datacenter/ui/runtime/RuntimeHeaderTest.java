package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.processing.controls.controls.label.Label;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import processing.data.JSONObject;
import processing.event.MouseEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeHeaderTest {
    @TempDir Path temporary;

    @Test void viewKeepsConfiguredHeaderAnchorsAndRestoresSizesAfterResize() throws Exception {
        var fields = Map.of("headerTitle", "lblTitle", "dataInputModeLabel", "lblDataInputMode",
                "dateLabel", "lblDate", "timeLabel", "lblTime", "simulationTimeLabel", "lblSimulationTime");
        var properties = new Properties();
        try (var input = Files.newInputStream(Path.of("data/config.properties"))) { properties.load(input); }
        var definition = RoomMap.loadDefinition();
        for (int[] size : new int[][]{{1600, 828}, {1920, 1014}}) {
            var sketch = new RuntimeTestApplet(size[0], size[1]);
            var configured = SharedLabels.load(sketch, "data/config/labels.json", Set.copyOf(fields.values()));
            try (var view = new RuntimeView(sketch, new RuntimeLayout(sketch.width, sketch.height), null,
                    new RoomMap(definition.layout()), new RuntimeSession(definition, properties),
                    new RuntimeClock(properties, () -> 0))) {
                for (int[] resized : new int[][]{size, {1200, 828}, size}) {
                    sketch.width = resized[0]; sketch.height = resized[1];
                    view.mouseEvent(new MouseEvent(this, 0, MouseEvent.MOVE, 0, 0, 0, 0, 0));
                    float scale = Math.min(1f, ((float) sketch.width / sketch.height) / (1920f / 1014f));
                    for (var entry : fields.entrySet()) {
                        var field = RuntimeView.class.getDeclaredField(entry.getKey()); field.setAccessible(true);
                        var actual = (Label) field.get(view);
                        var expected = configured.get(entry.getValue());
                        assertEquals(expected.getTooltipBounds(), actual.getTooltipBounds(), entry.getValue());
                        assertEquals(expected.getStyleConfig().textSize * scale,
                                actual.getStyleConfig().textSize, .001f, entry.getValue());
                    }
                }
            }
        }
    }

    @Test void runningIndicatorUsesClassicConfiguration() throws Exception {
        var root = JSONObject.parse(Files.readString(Path.of("data/config/indicators.json")));
        var controls = root.getJSONArray("controls");
        for (int i = 0; i < controls.size(); i++) {
            var entry = controls.getJSONObject(i);
            if (!"indSimulationRunning".equals(entry.getString("code"))) continue;
            entry.getJSONObject("bounds").setFloat("x", .75f);
            entry.setString("onColor", "#ffcc66");
            entry.setString("offColor", "#112233");
        }
        var path = temporary.resolve("indicators.json"); Files.writeString(path, root.toString());
        var sketch = new RuntimeTestApplet(1920, 1014);
        Indicator indicator = RuntimeView.loadRunningIndicator(sketch, path.toString());
        assertEquals(.75f * sketch.width, indicator.getTooltipBounds().x(), .001f);
        assertEquals(0xffffcc66, indicator.getOnColor());
        assertEquals(0xff112233, indicator.getOffColor());
        assertEquals("svg", indicator.getRendererType());
        assertEquals("data/img/ind/indCircle.svg", indicator.getRendererPath());
        assertFalse(indicator.isOn());
    }
}
