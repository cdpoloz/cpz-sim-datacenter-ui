package com.cpz.sim.datacenter.ui.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeLayoutTest {
    @Test void areasAndControlsFitWithoutOverlappingAcrossAspectRatios() {
        for (int[] size : new int[][]{{1920, 993}, {1280, 720}, {800, 800}}) {
            var layout = new RuntimeLayout(size[0], size[1]);
            var areas = RuntimeLayout.Area.values();
            for (int i = 0; i < areas.length; i++) {
                var b = layout.resolve(areas[i], size[0], size[1]);
                assertTrue(b.x() > 0 && b.y() > 0);
                assertTrue(b.x() + b.width() < size[0] && b.y() + b.height() < size[1]);
                for (var control : new com.cpz.processing.controls.controls.geometry.ControlBounds[]{layout.titleBounds(areas[i]), layout.contentBounds(areas[i])}) {
                    var c = control.resolve(size[0], size[1]);
                    assertTrue(c.x() >= b.x() && c.y() >= b.y());
                    assertTrue(c.x() + c.width() <= b.x() + b.width() + .01f);
                    assertTrue(c.y() + c.height() <= b.y() + b.height() + .01f);
                }
                for (int j = i + 1; j < areas.length; j++) {
                    var c = layout.resolve(areas[j], size[0], size[1]);
                    assertTrue(b.x() + b.width() <= c.x() + .01f || c.x() + c.width() <= b.x() + .01f
                            || b.y() + b.height() <= c.y() + .01f || c.y() + c.height() <= b.y() + .01f);
                }
            }
        }
    }
    @Test void relativeDimensionsResolveAgainstHeight() {
        var bounds = new RuntimeLayout(1920, 993).bounds(RuntimeLayout.Area.ROOM);
        var a = bounds.resolve(1920, 993);
        var b = bounds.resolve(3000, 993);
        assertEquals(a.width(), b.width(), .001f);
        assertEquals(a.height(), b.height(), .001f);
        assertEquals(a.x() / 1920, b.x() / 3000, .00001f);
    }
}
