package com.cpz.sim.datacenter.ui.ui;

import processing.core.PApplet;
import processing.core.PImage;

/** The header artwork over the full-canvas background. */
public final class HeaderBackground {
    private final PApplet sketch;
    private final PImage artwork;

    public HeaderBackground(PApplet sketch) {
        this.sketch = sketch;
        artwork = sketch.loadImage("data/img/ui_backgroundHeader.png");
    }

    public void draw() {
        sketch.pushStyle();
        sketch.imageMode(PApplet.CORNER);
        sketch.noTint();
        sketch.image(artwork, 0, 0, sketch.width, sketch.height);
        sketch.popStyle();
    }
}
