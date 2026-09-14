package com.cpz.sim.datacenter.ui.resources;

import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.app.Initializable;
import processing.core.PImage;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads and provides access to the graphical resources used by the UI.
 *
 * <p>This manager is responsible for initializing fonts, images and other
 * static assets required by the application.</p>
 *
 * @author CPZ
 */
public class ResourceManager extends ApplicationComponent implements Initializable {

    private final ResourceContainer container;

    public ResourceManager(ApplicationContext context, ResourceContainer container) {
        super(context);
        this.container = container;
    }

    @Override
    public void initialize() {
        loadFont();
        loadBackgroundImages();
        loadStaticOverlay();
    }

    private void loadFont() {
        container.setDefaultFont(
                sketch().createFont(
                        "data/font/JetBrainsMono.ttf",
                        96,
                        true
                )
        );
        sketch().textFont(container.defaultFont());
    }

    private void loadBackgroundImages() {
        List<PImage> images = new ArrayList<>();
        images.add(sketch().loadImage("data/img/ui_background.png"));
        images.add(sketch().loadImage("data/img/ui_backgroundSelectedAisle.png"));
        images.add(sketch().loadImage("data/img/ui_backgroundSelectedRack.png"));
        images.add(sketch().loadImage("data/img/ui_backgroundRoom.png"));
        images.add(sketch().loadImage("data/img/ui_backgroundHeader.png"));
        images.add(sketch().loadImage("data/img/ui_backgroundFooter.png"));
        container.setBackgroundImages(images);
    }

    private void loadStaticOverlay() {
        container.setStaticOverlay(sketch().loadImage("data/img/ui_overlay.png"));
    }
}
