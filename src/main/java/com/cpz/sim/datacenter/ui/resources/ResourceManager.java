package com.cpz.sim.datacenter.ui.resources;

import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.app.Initializable;
import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PImage;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads fonts and full-canvas image layers through the active Processing sketch.
 *
 * @author CPZ
 */
public class ResourceManager extends ApplicationComponent implements Initializable {

    private final ResourceContainer container;

    /**
     * Creates a resource manager that populates the supplied container.
     *
     * @param context Processing resource-loading access
     * @param container destination for loaded resources
     */
    public ResourceManager(ApplicationContext context, ResourceContainer container) {
        super(context);
        this.container = container;
    }

    /** Loads and installs the font, then loads background and overlay images. */
    @Override
    public void initialize() {
        loadFont();
        loadBackgroundImages();
        loadStaticOverlay();
    }

    private void loadFont() {
        container.setDefaultFont(createDefaultFont(sketch()));
        sketch().textFont(container.defaultFont());
    }

    /** Creates the font that CLASSIC labels inherit when no font is configured. */
    public static PFont createDefaultFont(PApplet sketch) {
        return sketch.createFont("data/font/JetBrainsMono.ttf", 96, true);
    }

    private void loadBackgroundImages() {
        List<PImage> images = new ArrayList<>();
        images.add(sketch().loadImage("data/img/ui_background.png"));
        images.add(sketch().loadImage("data/img/ui_backgroundSelectedAisle.png"));
        images.add(sketch().loadImage("data/img/ui_backgroundSelectedRack.png"));
        images.add(sketch().loadImage("data/img/ui_backgroundRoom.png"));
        images.add(sketch().loadImage("data/img/ui_backgroundHeader.png"));
        images.add(sketch().loadImage("data/img/ui_backgroundFooter.png"));
        images.add(sketch().loadImage("data/img/ui_backgroundOverview.png"));
        container.setBackgroundImages(images);
    }

    private void loadStaticOverlay() {
        container.setStaticOverlay(sketch().loadImage("data/img/ui_overlay.png"));
    }
}
