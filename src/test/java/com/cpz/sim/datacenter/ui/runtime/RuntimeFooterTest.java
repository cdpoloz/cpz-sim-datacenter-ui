package com.cpz.sim.datacenter.ui.runtime;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeFooterTest {
    @Test void formatsEveryConfiguredFactorLikeClassic() throws Exception {
        var properties = new Properties();
        try (var input = Files.newInputStream(Path.of("data/config.properties"))) {
            properties.load(input);
        }
        for (String raw : properties.getProperty("simulation.timer.speed-factors").split(",")) {
            double factor = Double.parseDouble(raw.trim());
            String expected = factor == Math.rint(factor) ? "x" + (int)factor : "x" + factor;
            assertEquals(expected, RuntimeView.formatFooterSpeed(factor));
        }
        assertEquals("x1", RuntimeView.formatFooterSpeed(1));
        assertEquals("x0.5", RuntimeView.formatFooterSpeed(.5));
    }
    @org.junit.jupiter.api.io.TempDir Path temporary;

    @Test void sharedLoaderResolvesEditedHeaderAndFooterConfiguration() throws Exception {
        var codes = java.util.Set.of("lblTitle", "lblSimulation", "lblSimulationValue");
        var root = processing.data.JSONObject.parse(Files.readString(Path.of("data/config/labels.json")));
        var entries = root.getJSONArray("controls");
        for (int i=0; i<entries.size(); i++) {
            var entry=entries.getJSONObject(i);
            if (codes.contains(entry.getString("code"))) {
                entry.getJSONObject("bounds").setFloat("x", .875f);
                entry.getJSONObject("style").setString("textColor", "#ffcc66");
            }
        }
        Path copy=temporary.resolve("labels.json"); Files.writeString(copy,root.toString());
        for (int[] size : new int[][]{{1600,828},{1920,1014}}) {
            var sketch=new RuntimeTestApplet(size[0],size[1]);
            var classic=new com.cpz.processing.controls.controls.config.ControlConfigLoader(sketch).load(copy.toString());
            var runtime=SharedLabels.load(sketch,copy.toString(),codes);
            assertEquals(codes,runtime.keySet());
            for (String code:runtime.keySet()) {
                var expected=(com.cpz.processing.controls.controls.label.Label)classic.get(code);
                var actual=runtime.get(code);
                assertEquals(expected.getText(),actual.getText());
                assertEquals(expected.getTooltipBounds(),actual.getTooltipBounds());
                assertEquals(.875f*size[0],actual.getTooltipBounds().x(),.001f);
                assertEquals(0xffffcc66,actual.getTextColor());
                assertEquals(expected.getStyleConfig().textSize,actual.getStyleConfig().textSize);
                assertEquals(expected.getStyleConfig().alignX,actual.getStyleConfig().alignX);
                assertEquals(expected.getStyleConfig().alignY,actual.getStyleConfig().alignY);
                assertEquals(expected.getStyleConfig().lineSpacingMultiplier,actual.getStyleConfig().lineSpacingMultiplier);
            }
        }
    }
}
