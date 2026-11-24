package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.processing.controls.controls.button.Button;
import com.cpz.processing.controls.core.input.PointerEvent;
import com.cpz.sim.datacenter.config.definition.*;
import com.cpz.sim.datacenter.model.RackLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import processing.core.PApplet;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoomMapTest {
    @TempDir Path tmp;
    private RoomMap small() {
        return new RoomMap(new DatacenterLayoutDefinition(List.of(
                new RackDefinition("R01", "C02", "02", List.of("S1")),
                new RackDefinition("R01", "C01", "01", List.of("S1", "S2")),
                new RackDefinition("R03", "C01", "03", List.of("S3")))));
    }
    @Test void loadsDefaultAndAlternativePath() throws Exception {
        String old = System.getProperty("simulation.datacenter.config");
        try {
            System.clearProperty("simulation.datacenter.config");
            var map = RoomMap.load();
            assertEquals(8, map.columns().size()); assertEquals(96, map.racks().size());
            assertTrue(map.racks().stream().allMatch(r -> r.slots().size() == 12));
            assertEquals(5, map.hotAisles().size());
            Path alternative = tmp.resolve("alternative.json");
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var json = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(Path.of("data/config/datacenter.json").toFile());
            var layout = (com.fasterxml.jackson.databind.node.ObjectNode) json.get("layout");
            var original = (com.fasterxml.jackson.databind.node.ArrayNode) layout.get("racks");
            var reduced = mapper.createArrayNode();
            reduced.add(original.get(0)); reduced.add(original.get(1)); reduced.add(original.get(12));
            layout.set("racks", reduced);
            layout.remove("hotAisles");
            json.set("servers", mapper.createArrayNode());
            json.remove("rackCooling");
            mapper.writeValue(alternative.toFile(), json);
            System.setProperty("simulation.datacenter.config", alternative.toString());
            var smaller = RoomMap.load();
            assertEquals(3, smaller.racks().size());
            assertEquals(2, smaller.columns().size());
            System.setProperty("simulation.datacenter.config", tmp.resolve("missing.json").toString());
            assertTrue(assertThrows(IllegalStateException.class, RoomMap::load).getMessage().contains("missing.json"));
        } finally {
            if (old == null) System.clearProperty("simulation.datacenter.config"); else System.setProperty("simulation.datacenter.config", old);
        }
    }
    @Test void rowsAlignAndButtonsSelectDistinctLocationsAfterResize() {
        var map = small();
        assertEquals(List.of("C01", "C02"), map.columns());
        assertEquals(2, map.racks().getFirst().slots().size());
        var sketch = new PApplet(); sketch.width = 1920; sketch.height = 1014;
        var buttons = new LinkedHashMap<RackLocation, Button>();
        var first = map.geometry(new RuntimeLayout(sketch.width, sketch.height), sketch.width, sketch.height);
        for (var p : first.racks()) {
            var button = new Button(sketch, p.rack().location().code(), p.bounds());
            button.setClickListener(() -> map.select(p.rack().location()));
            buttons.put(p.rack().location(), button);
        }
        click(buttons, first.racks().getFirst().bounds().resolve(sketch.width, sketch.height));
        assertEquals(new RackLocation("C01", "R01"), map.selected());
        sketch.width = 800; sketch.height = 800;
        var resized = map.geometry(new RuntimeLayout(800, 800), 800, 800);
        resized.racks().forEach(p -> buttons.get(p.rack().location()).setBounds(p.bounds()));
        assertEquals(new RackLocation("C01", "R01"), map.selected());
        var target = resized.racks().stream().filter(p -> p.rack().location().equals(new RackLocation("C02", "R01"))).findFirst().orElseThrow();
        click(buttons, target.bounds().resolve(800, 800));
        assertEquals(new RackLocation("C02", "R01"), map.selected());
        for (var b : buttons.values()) { b.handlePointerEvent(new PointerEvent(PointerEvent.Type.PRESS, 0, 0)); b.handlePointerEvent(new PointerEvent(PointerEvent.Type.RELEASE, 0, 0)); }
        assertEquals(new RackLocation("C02", "R01"), map.selected());
    }
    private void click(Map<RackLocation, Button> buttons, com.cpz.processing.controls.controls.geometry.ResolvedBounds b) {
        for (var button : buttons.values()) button.handlePointerEvent(new PointerEvent(PointerEvent.Type.PRESS, b.x(), b.y()));
        for (var button : buttons.values()) button.handlePointerEvent(new PointerEvent(PointerEvent.Type.RELEASE, b.x(), b.y()));
    }
    @Test void geometryFitsWithoutOverlap() {
        var map = RoomMap.load();
        for (int[] size : new int[][]{{1920, 1014}, {1280, 720}, {800, 800}}) {
            var layout = new RuntimeLayout(size[0], size[1]);
            var content = layout.contentBounds(RuntimeLayout.Area.ROOM).resolve(size[0], size[1]);
            var geometry = map.geometry(layout, size[0], size[1]);
            var rectangles = new ArrayList<float[]>();
            geometry.columns().values().forEach(b -> { var v = b.resolve(size[0], size[1]); rectangles.add(new float[]{v.x(), v.y(), v.width(), v.height()}); });
            geometry.racks().forEach(p -> { var v = p.bounds().resolve(size[0], size[1]); rectangles.add(new float[]{v.x()-v.width()/2, v.y()-v.height()/2, v.width(), v.height()}); });
            for (int i = 0; i < rectangles.size(); i++) {
                var a = rectangles.get(i);
                assertTrue(a[0] >= content.x() - .01f && a[1] >= content.y() - .01f && a[0]+a[2] <= content.x()+content.width()+.01f && a[1]+a[3] <= content.y()+content.height()+.01f);
                for (int j = i+1; j < rectangles.size(); j++) { var b = rectangles.get(j); assertTrue(a[0]+a[2] <= b[0]+.01f || b[0]+b[2] <= a[0]+.01f || a[1]+a[3] <= b[1]+.01f || b[1]+b[3] <= a[1]+.01f); }
            }
        }
    }
}
