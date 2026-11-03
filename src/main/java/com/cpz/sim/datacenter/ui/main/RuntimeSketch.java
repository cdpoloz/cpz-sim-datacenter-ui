package com.cpz.sim.datacenter.ui.main;

import com.cpz.sim.datacenter.ui.runtime.RuntimeLayout;
import com.cpz.sim.datacenter.ui.runtime.RuntimeView;
import processing.core.PApplet;
import java.awt.GraphicsEnvironment;
import java.awt.Frame;

import static com.cpz.sim.datacenter.ui.main.Launcher.PROPS;

/** Independent composition root; no classic control JSON, asset map or simulation is loaded. */
public final class RuntimeSketch extends PApplet {
    private RuntimeView view;

    @Override public void settings() {
        int requestedWidth = Integer.parseInt(PROPS.getProperty("sketch.width"));
        int requestedHeight = Integer.parseInt(PROPS.getProperty("sketch.height"));
        var usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Frame probe = new Frame();
        probe.addNotify();
        var insets = probe.getInsets();
        probe.dispose();
        int maxWidth = Math.max(1, usable.width - insets.left - insets.right);
        int maxHeight = Math.max(1, usable.height - insets.top - insets.bottom);
        float scale = Math.min(1f, Math.min((float) maxWidth / requestedWidth, (float) maxHeight / requestedHeight));
        size(Math.max(1, (int) (requestedWidth * scale)), Math.max(1, (int) (requestedHeight * scale)), P2D);
        smooth(Integer.parseInt(PROPS.getProperty("sketch.smoothing")));
    }

    @Override public void setup() {
        surface.setTitle(PROPS.getProperty("window.title") + " | Runtime");
        frameRate(Integer.parseInt(PROPS.getProperty("sketch.fps")));
        var font = createFont("data/font/JetBrainsMono.ttf", height * .021f, true);
        textFont(font);
        view = new RuntimeView(this, new RuntimeLayout(width, height), font);
    }

    @Override public void draw() { view.draw(); }

}
