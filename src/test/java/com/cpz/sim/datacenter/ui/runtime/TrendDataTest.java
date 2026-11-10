package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class TrendDataTest {
    @TempDir Path tmp;
    private RuntimeSession session() throws Exception {
        var props = new Properties();
        try (var input = Files.newInputStream(Path.of("data/config.properties"))) { props.load(input); }
        return new RuntimeSession(new JsonDatacenterConfigLoader().load(Path.of("data/config/datacenter.json")), props);
    }

    @Test void historyUsesRecordedTicksAndIgnoresPausedControlChanges() throws Exception {
        var s = session();
        var initial = s.recentHistory(120);
        assertEquals(1, initial.size());
        var unit = s.coolingUnits().getFirst();
        s.setCoolingEnabled(unit.code(), !s.coolingEnabled(unit.code()));
        assertEquals(initial, s.recentHistory(120));
        var original = TrendData.project(initial, TrendData.View.TEMPERATURE, null);
        assertEquals(s.snapshot().operationalSnapshot().averageOnlineServerTemperatureCelsius(), original.getFirst().first());
        s.step();
        var next = s.recentHistory(120);
        assertEquals(2, next.size());
        assertEquals(initial.getFirst().tickIndex() + 1, next.getLast().tickIndex());
        assertEquals(original.getFirst(), TrendData.project(next, TrendData.View.TEMPERATURE, null).getFirst());
        assertEquals(1, s.recentHistory(1).size());
    }

    @Test void aisleSelectionUsesEachSnapshotsGroupAndClearsMissingSelection() throws Exception {
        var s = session(); s.step();
        var history = s.recentHistory(120);
        var aisles = s.definition().layout().hotAisles();
        var first = aisles.getFirst().code(); var last = aisles.getLast().code();
        var a = TrendData.project(history, TrendData.View.AISLE, first);
        var b = TrendData.project(history, TrendData.View.AISLE, last);
        for (int i = 0; i < history.size(); i++) {
            assertEquals(history.get(i).operationalSnapshot().getServerGroup(first).averageOnlineTemperatureCelsius(), a.get(i).first());
            assertEquals(history.get(i).operationalSnapshot().getServerGroup(last).averageOnlineTemperatureCelsius(), b.get(i).first());
        }
        assertTrue(TrendData.project(history, TrendData.View.AISLE, null).stream().allMatch(v -> Double.isNaN(v.first())));
        assertTrue(TrendData.project(history, TrendData.View.AISLE, "missing").stream().allMatch(v -> Double.isNaN(v.first())));
        assertEquals(a, TrendData.project(history, TrendData.View.AISLE, first));
    }

    @Test void axesAndMissingValues() {
        assertEquals(new TrendData.Range(0, 1), TrendData.range(List.of()));
        var samples = List.of(new TrendData.Sample(3, 5, Double.NaN, Double.NaN));
        var range = TrendData.range(samples);
        assertEquals(.5, range.fraction(5), 1e-9);
        assertEquals(0, range.fraction(range.min()), 1e-9);
        assertEquals(1, range.fraction(range.max()), 1e-9);
        assertEquals(.5, TrendData.xFraction(3, 3, 3));
        assertEquals(.25, TrendData.xFraction(4, 3, 7));
        assertEquals(new TrendData.Range(0, 1), TrendData.range(List.of(new TrendData.Sample(1, Double.NaN, Double.POSITIVE_INFINITY, Double.NaN))));
        assertTrue(TrendData.project(List.of(), TrendData.View.POWER, null).isEmpty());
    }

    @Test void unavailableSnapshotFieldsRemainGaps() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(Path.of("data/config/datacenter.json").toFile());
        root.remove("roomAirTemperature"); root.remove("rackCooling");
        for (var server : root.withArray("servers")) ((com.fasterxml.jackson.databind.node.ObjectNode) server).put("status", "OFFLINE");
        var path = tmp.resolve("offline.json"); mapper.writeValue(path.toFile(), root);
        var props = new Properties();
        try (var in = Files.newInputStream(Path.of("data/config.properties"))) { props.load(in); }
        var s = new RuntimeSession(new JsonDatacenterConfigLoader().load(path), props);
        var temp = TrendData.project(s.recentHistory(120), TrendData.View.TEMPERATURE, null).getFirst();
        assertTrue(Double.isNaN(temp.first())); assertTrue(Double.isNaN(temp.second())); assertTrue(Double.isNaN(temp.third()));
        assertTrue(Double.isNaN(TrendData.project(s.recentHistory(120), TrendData.View.UTILIZATION, null).getFirst().first()));
    }

    @Test void trendButtonChangesViewWithoutRecordingOrChangingSelection() throws Exception {
        var s = session(); var before = s.snapshot();
        var sketch = new processing.core.PApplet(); sketch.width = 1920; sketch.height = 1011;
        var layout = new RuntimeLayout(sketch.width, sketch.height);
        var panel = new TrendsPanel(sketch, null, layout, s);
        String aisle = s.definition().layout().hotAisles().getFirst().code();
        panel.refresh(aisle);
        var field = TrendsPanel.class.getDeclaredField("mode"); field.setAccessible(true);
        var button = (com.cpz.processing.controls.controls.button.Button) field.get(panel);
        var b = button.getTooltipBounds();
        for (var type : List.of(com.cpz.processing.controls.core.input.PointerEvent.Type.PRESS,
                com.cpz.processing.controls.core.input.PointerEvent.Type.RELEASE))
            panel.pointer(new com.cpz.processing.controls.core.input.PointerEvent(type, b.centerX(), b.centerY()));
        var view = TrendsPanel.class.getDeclaredField("view"); view.setAccessible(true);
        assertEquals(TrendData.View.AISLE, view.get(panel));
        var selection = TrendsPanel.class.getDeclaredField("aisle"); selection.setAccessible(true);
        assertEquals(aisle, selection.get(panel));
        assertSame(before, s.snapshot()); assertEquals(1, s.recentHistory(120).size());
    }
}
