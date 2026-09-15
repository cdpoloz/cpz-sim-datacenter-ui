package com.cpz.sim.datacenter.ui.ui.render;

import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.resources.ResourceContainer;
import com.cpz.processing.controls.core.overlay.OverlayManager;

import static com.cpz.sim.datacenter.ui.util.Constants.COLOR_BACKGROUND;
import static processing.core.PConstants.CORNER;

/**
 * Draws full-canvas background layers and the foreground overlay stack.
 *
 * @author CPZ
 */
public class StaticUiRenderer extends ApplicationComponent {

    private final ResourceContainer resourceContainer;
    private final OverlayManager overlayManager;

    /**
     * Creates the static layer renderer.
     *
     * @param context Processing drawing access
     * @param resourceContainer background and static overlay images
     * @param overlayManager source of active control overlays
     */
    public StaticUiRenderer(ApplicationContext context, ResourceContainer resourceContainer, OverlayManager overlayManager) {
        super(context);
        this.resourceContainer = resourceContainer;
        this.overlayManager = overlayManager;
    }

    /** Clears the frame and draws background images in configured list order. */
    public void drawBackground() {
        sketch().background(COLOR_BACKGROUND);
        sketch().pushStyle();
        sketch().imageMode(CORNER);
        resourceContainer
                .backgroundImages()
                .forEach(image -> sketch().image(image, 0, 0, sketch().width, sketch().height));
        sketch().popStyle();
    }

    /** Draws active library overlays followed by the static full-canvas foreground. */
    public void drawOverlay() {
        overlayManager.getActiveOverlays().forEach(entry -> entry.getRender().run());
        sketch().pushStyle();
        sketch().imageMode(CORNER);
        sketch().image(resourceContainer.staticOverlay(), 0, 0, sketch().width, sketch().height);
        sketch().popStyle();
    }
}
