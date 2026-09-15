package com.cpz.sim.datacenter.ui.ui.render;

import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.resources.ResourceContainer;
import com.cpz.processing.controls.core.overlay.OverlayManager;

import static com.cpz.sim.datacenter.ui.util.Constants.COLOR_BACKGROUND;
import static processing.core.PConstants.CORNER;

/**
 * Renders static UI layers.
 *
 * @author CPZ
 */
public class StaticUiRenderer extends ApplicationComponent {

    private final ResourceContainer resourceContainer;
    private final OverlayManager overlayManager;

    public StaticUiRenderer(ApplicationContext context, ResourceContainer resourceContainer, OverlayManager overlayManager) {
        super(context);
        this.resourceContainer = resourceContainer;
        this.overlayManager = overlayManager;
    }

    public void drawBackground() {
        sketch().background(COLOR_BACKGROUND);
        sketch().pushStyle();
        sketch().imageMode(CORNER);
        resourceContainer
                .backgroundImages()
                .forEach(image -> sketch().image(image, 0, 0, sketch().width, sketch().height));
        sketch().popStyle();
    }

    public void drawOverlay() {
        overlayManager.getActiveOverlays().forEach(entry -> entry.getRender().run());
        sketch().pushStyle();
        sketch().imageMode(CORNER);
        sketch().image(resourceContainer.staticOverlay(), 0, 0, sketch().width, sketch().height);
        sketch().popStyle();
    }
}