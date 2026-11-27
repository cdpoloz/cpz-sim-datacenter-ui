package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.processing.controls.controls.geometry.ControlBounds;
import processing.core.PApplet;
import processing.core.PImage;

import java.util.Arrays;

/** The CLASSIC room layer, with room-specific geometry supplied by RoomMap. */
final class RoomMapArtwork {
    private static final int LEFT = 108, TOP = 154, RIGHT = 1706, BOTTOM = 1249;
    private static final float CANVAS_WIDTH = 3840, CANVAS_HEIGHT = 1986;
    private final PImage artwork;
    private final PImage coolingArtwork;

    RoomMapArtwork(PApplet sketch) {
        var source = sketch.loadImage("data/img/ui_backgroundRoom.png");
        artwork = source.get(LEFT, TOP, RIGHT - LEFT, BOTTOM - TOP);
        artwork.loadPixels();
        int fill = artwork.pixels[(400 - TOP) * artwork.width + 50];
        // The exported salaFondo layer includes the demo room's eight column outlines.
        // Clear that interior region while retaining the frame, separators and legend.
        clear(235, 424, 1640, 1090, fill);
        artwork.updatePixels();
        coolingArtwork = artwork.get();
        coolingArtwork.loadPixels();
        for (int y = 243 - TOP; y < 1224 - TOP; y++) {
            int row = y * artwork.width;
            Arrays.fill(coolingArtwork.pixels, row + 25, row + artwork.width - 25, fill);
        }
        coolingArtwork.updatePixels();
    }

    private void clear(int x0, int y0, int x1, int y1, int fill) {
        for (int y = y0 - TOP; y < y1 - TOP; y++) {
            int row = y * artwork.width;
            Arrays.fill(artwork.pixels, row + x0 - LEFT, row + x1 - LEFT, fill);
        }
    }

    static ControlBounds bounds(float aspect, int left, int top, int right, int bottom) {
        return ControlBounds.relative(left / CANVAS_WIDTH, top / CANVAS_HEIGHT,
                (right - left) / CANVAS_WIDTH * aspect, (bottom - top) / CANVAS_HEIGHT);
    }

    static ControlBounds panelBounds(float aspect) {
        return bounds(aspect, LEFT, TOP, RIGHT, BOTTOM);
    }

    void draw(PApplet sketch, RuntimeLayout layout, boolean cooling) {
        var b = layout.resolve(RuntimeLayout.Area.ROOM, sketch.width, sketch.height);
        sketch.pushStyle();
        sketch.imageMode(PApplet.CORNER);
        sketch.noTint();
        sketch.image(cooling ? coolingArtwork : artwork, b.x(), b.y(), b.width(), b.height());
        sketch.popStyle();
    }
}
