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
    @Test void bodyPanelsLeaveTheOuterFrameAndFooterClear() {
        for (int[] size : new int[][]{{1920, 1014}, {1600, 828}, {1200, 828}, {3000, 993}}) {
            int width = size[0], height = size[1];
            var layout = new RuntimeLayout(width, height);
            var footer = layout.resolve(RuntimeLayout.Area.FOOTER, width, height);
            for (var area : java.util.List.of(RuntimeLayout.Area.SELECTED_AISLE, RuntimeLayout.Area.SELECTED_RACK,
                    RuntimeLayout.Area.SUMMARY, RuntimeLayout.Area.TRENDS, RuntimeLayout.Area.ALERTS)) {
                var bounds = layout.resolve(area, width, height);
                // The 3 px artwork stroke is centred on the outer path in the design canvas.
                assertTrue(bounds.x() > width * 89f / 3840, area.name());
                assertTrue(bounds.x() + bounds.width() < width * 3752f / 3840, area.name());
                assertTrue(bounds.y() + bounds.height() < footer.y() - height * .01f, area.name());
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
    @Test void footerPlaybackTracksClassicAnchorsAndFitsArtwork() throws Exception {
        var toggles = processing.data.JSONObject.parse(java.nio.file.Files.readString(
                java.nio.file.Path.of("data/config/toggles.json"))).getJSONArray("controls");
        processing.data.JSONObject play = null;
        for (int i = 0; i < toggles.size(); i++) {
            if ("tglPlay".equals(toggles.getJSONObject(i).getString("code")))
                play = toggles.getJSONObject(i).getJSONObject("bounds");
        }
        assertNotNull(play);
        var speeds = processing.data.JSONObject.parse(java.nio.file.Files.readString(
                java.nio.file.Path.of("data/config/buttonsPlay.json"))).getJSONArray("controls");
        for (int[] size : new int[][]{{1600,828}, {1920,993}, {1920,1014}, {800,800}}) {
            var layout = new RuntimeLayout(size[0], size[1]);
            for (int i = 0; i < 3; i++) {
                var expected = i == 0 ? play : speeds.getJSONObject(i-1).getJSONObject("bounds");
                var b = layout.playbackBounds(i,size[0],size[1]).resolve(size[0],size[1]);
                assertEquals(expected.getFloat("x") * size[0], b.x(), .001f);
                assertEquals(expected.getFloat("y") * size[1], b.y(), .001f);
                assertEquals(b.width(), b.height(), .001f);
                assertTrue(b.x()-b.width()/2 > .853f*size[0]);
                assertTrue(b.x()+b.width()/2 < .964f*size[0]);
                assertTrue(b.y()-b.height()/2 > .920f*size[1]);
                assertTrue(b.y()+b.height()/2 < .974f*size[1]);
            }
        }
    }
}
