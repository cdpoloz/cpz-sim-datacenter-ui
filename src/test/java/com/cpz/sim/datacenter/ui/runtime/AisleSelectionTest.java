package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.sim.datacenter.config.definition.*;
import com.cpz.sim.datacenter.model.RackLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AisleSelectionTest {
    private static final RackLocation A = new RackLocation("NORTH", "R01");
    private static final RackLocation B = new RackLocation("SOUTH", "R01");
    private static final RackLocation C = new RackLocation("WEST", "R02");
    private RoomMap room(List<HotAisleDefinition> aisles) {
        return new RoomMap(new DatacenterLayoutDefinition(null, aisles, List.of(
                new RackDefinition("R01", "NORTH", "01", List.of("S1")),
                new RackDefinition("R03", "NORTH", "03", List.of("S1")),
                new RackDefinition("R01", "SOUTH", "02", List.of("S1")),
                new RackDefinition("R02", "WEST", "02", List.of("S1")))));
    }
    @Test void selectionSynchronizesAndClearsUnrelatedRacks() {
        var room = room(List.of(new HotAisleDefinition("single", List.of("WEST")), new HotAisleDefinition("shared", List.of("SOUTH", "NORTH"))));
        var aisle = new AisleSelection(room);
        assertNull(aisle.selected()); assertTrue(aisle.canNext());
        aisle.selectRack(A); assertEquals("shared", aisle.selected().code()); assertEquals(A, room.selected());
        aisle.selectRack(B); assertEquals(B, room.selected());
        aisle.change(-1); assertEquals("single", aisle.selected().code()); assertNull(room.selected());
        aisle.selectRack(C); assertEquals(C, room.selected());
        aisle.change(1); assertNull(room.selected());
        aisle.selectRack(A); aisle.change(0); assertEquals(A, room.selected());
    }
    @Test void unassociatedAndAbsentAislesKeepRackSelection() {
        var room = room(List.of(new HotAisleDefinition("one", List.of("WEST"))));
        var aisle = new AisleSelection(room);
        aisle.selectRack(C); aisle.selectRack(A);
        assertNull(aisle.selected()); assertEquals(A, room.selected());
        var without = room(List.of()); var noAisles = new AisleSelection(without);
        noAisles.selectRack(B); assertNull(noAisles.selected()); assertFalse(noAisles.canNext()); assertEquals(B, without.selected());
    }
    @Test void geometryPreservesDeclaredColumnsAndGapsAfterResize() {
        var room = room(List.of(new HotAisleDefinition("shared", List.of("SOUTH", "NORTH"))));
        var aisle = new AisleSelection(room); aisle.change(1);
        for (int[] size : new int[][]{{1920, 1014}, {800, 800}}) {
            var layout = new RuntimeLayout(size[0], size[1]);
            var g = aisle.geometry(layout, size[0], size[1]);
            assertEquals(List.of("SOUTH", "NORTH"), new ArrayList<>(g.columns().keySet()));
            assertEquals(3, g.racks().size());
            var a = g.racks().get(A).resolve(size[0], size[1]);
            var b = g.racks().get(B).resolve(size[0], size[1]);
            assertTrue(b.x() < a.x()); assertTrue(a.y() < b.y());
            var container = layout.contentBounds(RuntimeLayout.Area.SELECTED_AISLE).resolve(size[0], size[1]);
            for (var bounds : g.racks().values()) {
                var r = bounds.resolve(size[0], size[1]);
                assertTrue(r.x()-r.width()/2 >= container.x() && r.x()+r.width()/2 <= container.x()+container.width());
                assertTrue(r.y()-r.height()/2 >= container.y() && r.y()+r.height()/2 <= container.y()+container.height());
            }
        }
    }
    @Test void metricsRetainOfflineMaximumAndClearMissingValues() {
        var location = new com.cpz.sim.datacenter.model.ServerLocation("NORTH", "R01", "S1");
        var offline = new com.cpz.sim.datacenter.snapshot.ServerGroupOperationalSnapshot("one", 1, 0, 0, 0, 0,
                Double.NaN, Double.NaN, 42, Optional.of(location));
        String text = AisleMetrics.text(offline, v -> v + "C");
        assertTrue(text.contains("Sin servidores online"));
        assertTrue(text.contains("Media online: --  IT: --"));
        assertTrue(text.contains("42.0C")); assertTrue(text.contains("NORTH/R01/S1"));
        assertEquals("Datos no disponibles", AisleMetrics.text(null, v -> "unexpected"));
    }
}
