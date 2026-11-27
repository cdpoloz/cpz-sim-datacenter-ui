package com.cpz.sim.datacenter.ui.ui;

import processing.core.PApplet;
import processing.core.PImage;

/** CLASSIC's general background restricted to the header, followed by its artwork. */
public final class HeaderBackground {
    // ui.svg: separator at y=127.27922 with a 3px stroke on the 1986px canvas.
    // Include the complete antialiased separator, without painting the body panels.
    private static final int HEADER_BOTTOM = 130;
    private final PApplet sketch;
    private final PImage background;
    private final PImage artwork;

    public HeaderBackground(PApplet sketch) {
        this.sketch = sketch;
        background = sketch.loadImage("data/img/ui_background.png");
        artwork = sketch.loadImage("data/img/ui_backgroundHeader.png");
    }

    public void draw() {
        sketch.pushStyle();
        sketch.imageMode(PApplet.CORNER);
        sketch.noTint();
        sketch.image(background, 0, 0, sketch.width,
                sketch.height * (float) HEADER_BOTTOM / background.height,
                0, 0, background.width, HEADER_BOTTOM);
        sketch.image(artwork, 0, 0, sketch.width, sketch.height);
        sketch.popStyle();
    }
}
