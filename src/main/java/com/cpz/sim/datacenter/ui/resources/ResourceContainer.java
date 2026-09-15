package com.cpz.sim.datacenter.ui.resources;

import processing.core.PFont;
import processing.core.PImage;

import java.util.List;

/**
 * Stores Processing resources loaded once during setup and reused by renderers.
 *
 * @author CPZ
 */
public class ResourceContainer {

    private PFont defaultFont;
    private List<PImage> backgroundImages;
    private PImage staticOverlay;

    /** Creates an empty resource container populated during setup. */
    public ResourceContainer() {
    }

    public PFont defaultFont() {
        return defaultFont;
    }

    public void setDefaultFont(PFont defaultFont) {
        this.defaultFont = defaultFont;
    }

    public List<PImage> backgroundImages() {
        return backgroundImages;
    }

    public void setBackgroundImages(List<PImage> backgroundImages) {
        this.backgroundImages = backgroundImages;
    }

    public PImage staticOverlay() {
        return staticOverlay;
    }

    public void setStaticOverlay(PImage staticOverlay) {
        this.staticOverlay = staticOverlay;
    }
}
