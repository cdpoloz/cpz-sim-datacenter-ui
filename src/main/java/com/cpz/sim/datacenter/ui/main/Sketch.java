package com.cpz.sim.datacenter.ui.main;

import com.cpz.processing.controls.controls.toggle.Toggle;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.app.InfrastructureContainer;
import com.cpz.sim.datacenter.ui.app.InfrastructureInitializer;
import com.cpz.sim.datacenter.ui.controls.ControlManager;
import com.cpz.sim.datacenter.ui.controls.CoolingToggleManager;
import com.cpz.sim.datacenter.ui.resources.ResourceContainer;
import com.cpz.sim.datacenter.ui.resources.ResourceManager;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.simulation.SimulationManager;
import com.cpz.sim.datacenter.ui.simulation.TemperatureRange;
import com.cpz.sim.datacenter.ui.simulation.TemperatureRangeCalculator;
import com.cpz.sim.datacenter.ui.ui.*;
import com.cpz.sim.datacenter.ui.ui.panel.GlobalOverviewUpdater;
import com.cpz.sim.datacenter.ui.ui.panel.RoomPanelUpdater;
import com.cpz.sim.datacenter.ui.ui.panel.SelectedAislePanelUpdater;
import com.cpz.sim.datacenter.ui.ui.panel.SelectedRackPanelUpdater;
import com.cpz.sim.datacenter.ui.ui.render.ControlRenderer;
import com.cpz.sim.datacenter.ui.ui.render.GlobalOverviewRenderer;
import com.cpz.sim.datacenter.ui.ui.render.SelectedHotAisleTemperatureGradientRenderer;
import com.cpz.sim.datacenter.ui.ui.render.StaticUiRenderer;
import com.cpz.sim.datacenter.ui.ui.selection.SelectionManager;
import processing.core.PApplet;
import processing.event.MouseEvent;
import processing.opengl.PJOGL;

import java.io.File;

import static com.cpz.sim.datacenter.ui.main.Launcher.LOG;
import static com.cpz.sim.datacenter.ui.main.Launcher.PROPS;
import static com.cpz.sim.datacenter.ui.util.Constants.COLOR_BACKGROUND;

/**
 * Active Processing sketch and composition root for the datacenter UI.
 *
 * <p>Processing invokes the lifecycle and input callback methods on this class. Startup-only
 * collaborators remain local to {@link #setup()}; fields are reserved for objects used by
 * {@link #draw()} or by a later Processing callback.</p>
 *
 * @author CPZ
 */
public class Sketch extends PApplet {

    private InfrastructureContainer infrastructureContainer;
    private UiStateContainer uiStateContainer;
    private HeaderUpdater headerUpdater;
    private UiUpdateCoordinator uiUpdateCoordinator;
    private UiInteractionController uiInteractionController;
    private SelectedHotAisleTemperatureGradientRenderer selectedHotAisleTemperatureGradientRenderer;
    private StaticUiRenderer staticUiRenderer;
    private ControlRenderer controlRenderer;
    private GlobalOverviewUpdater globalOverviewUpdater;
    private GlobalOverviewRenderer globalOverviewRenderer;

    /**
     * Configures the Processing surface before it is created.
     */
    @Override
    public void settings() {
        LOG.info("Starting settings");
        PJOGL.setIcon("data" + File.separator + "img" + File.separator + PROPS.getProperty("window.icon"));
        size(Integer.parseInt(PROPS.getProperty("sketch.width")), Integer.parseInt(PROPS.getProperty("sketch.height")), P2D);
        smooth(Integer.parseInt(PROPS.getProperty("sketch.smoothing")));
        LOG.info("Finished settings");
    }

    /**
     * Builds the application graph and prepares the first complete UI snapshot.
     *
     * <p>Construction order is intentional: controls and resources must exist before managers
     * update them, and the simulation must take an initial step before snapshot-backed panels
     * can be populated.</p>
     */
    @Override
    public void setup() {
        LOG.info("Starting initial setup");
        background(COLOR_BACKGROUND);
        frameRate(Integer.parseInt(PROPS.getProperty("sketch.fps")));
        getSurface().setTitle(PROPS.getProperty("window.title"));
        LOG.info("Finished initial setup");
        // The context is the narrow bridge used by Processing-dependent components.
        ApplicationContext context = new ApplicationContext(this);
        infrastructureContainer = new InfrastructureContainer();
        InfrastructureInitializer infrastructureInitializer = new InfrastructureInitializer(infrastructureContainer);
        infrastructureInitializer.initialize();
        UiComponentContainer uiComponentContainer = new UiComponentContainer();
        UiFormatContainer uiFormatContainer = new UiFormatContainer();
        uiStateContainer = new UiStateContainer();
        UiFormatLoader uiFormatLoader = new UiFormatLoader();
        UiContainersInitializer uiContainersInitializer = new UiContainersInitializer(uiComponentContainer, uiFormatContainer, uiStateContainer, uiFormatLoader);
        uiContainersInitializer.initialize();
        ControlManager controlManager =
                new ControlManager(
                        context,
                        uiComponentContainer,
                        infrastructureContainer.overlayManager(),
                        infrastructureContainer.inputManager(),
                        infrastructureContainer.mainInputLayer(),
                        this::btnClicked,
                        this::tglChanged
                );
        controlManager.initialize();
        ResourceContainer resourceContainer = new ResourceContainer();
        ResourceManager resourceManager = new ResourceManager(context, resourceContainer);
        resourceManager.initialize();
        // These setup locals remain reachable through the runtime coordinators that own them.
        SimulationContainer simulationContainer = new SimulationContainer();
        SimulationManager simulationManager = new SimulationManager(context, simulationContainer, uiComponentContainer);
        CoolingToggleManager coolingToggleManager = new CoolingToggleManager(context, simulationContainer, uiComponentContainer);
        SelectionManager selectionManager = new SelectionManager(context, simulationContainer, uiComponentContainer);
        uiInteractionController = new UiInteractionController(uiStateContainer, simulationManager, coolingToggleManager, selectionManager);
        headerUpdater = new HeaderUpdater(context, simulationContainer, uiComponentContainer);
        TemperatureRangeCalculator temperatureRangeCalculator = new TemperatureRangeCalculator();
        SelectedRackPanelUpdater selectedRackPanelUpdater = new SelectedRackPanelUpdater(context, simulationContainer, uiComponentContainer, selectionManager);
        SelectedAislePanelUpdater selectedAislePanelUpdater = new SelectedAislePanelUpdater(context, simulationContainer, uiComponentContainer, selectionManager);
        RoomPanelUpdater roomPanelUpdater = new RoomPanelUpdater(context, simulationContainer, uiComponentContainer);
        uiUpdateCoordinator = new UiUpdateCoordinator(uiStateContainer, uiFormatContainer, simulationManager, selectedRackPanelUpdater, selectedAislePanelUpdater, roomPanelUpdater);
        UiStateInitializer uiStateInitializer = new UiStateInitializer(context, simulationContainer, uiComponentContainer);
        selectedHotAisleTemperatureGradientRenderer = new SelectedHotAisleTemperatureGradientRenderer(context);
        staticUiRenderer = new StaticUiRenderer(context, resourceContainer, infrastructureContainer.overlayManager());
        controlRenderer = new ControlRenderer(uiComponentContainer);
        globalOverviewUpdater = new GlobalOverviewUpdater(simulationContainer, uiComponentContainer);
        globalOverviewRenderer = new GlobalOverviewRenderer(context, simulationContainer, uiStateContainer);
        simulationManager.initialize();
        selectionManager.initialize();
        // Use one backend-derived temperature scale for panels, room colors, and the gradient.
        TemperatureRange temperatureRange = temperatureRangeCalculator.calculate(simulationContainer.datacenter(), simulationContainer.temperatureOptions());
        uiStateContainer.setMinServerTemperatureCelsius(temperatureRange.minServerTemperatureCelsius());
        uiStateContainer.setMaxServerTemperatureCelsius(temperatureRange.maxServerTemperatureCelsius());
        simulationManager.initializeInitialSimulationState();
        // Reuse the normal invalidation path to produce the first internally consistent frame.
        uiStateContainer.setUpdateUI(true);
        uiStateContainer.setUpdateSnapshots(true);
        uiUpdateCoordinator.updateSnapshotsIfNeeded();
        coolingToggleManager.initialize();
        uiStateInitializer.initialize(uiStateContainer.minServerTemperatureCelsius(), uiStateContainer.maxServerTemperatureCelsius(), uiFormatContainer.simpleTemperature());
    }

    /**
     * Updates invalidated state and renders one frame in the required layer order.
     */
    @Override
    public void draw() {
        // Update order keeps controls aligned with the engine state advanced in this frame.
        headerUpdater.update();
        uiUpdateCoordinator.updateClock();
        uiUpdateCoordinator.updateSnapshotsIfNeeded();
        uiUpdateCoordinator.updateControlsIfNeeded();
        globalOverviewUpdater.update();
        // The gradient must sit above controls but below active/static foreground overlays.
        staticUiRenderer.drawBackground();
        controlRenderer.draw();
        selectedHotAisleTemperatureGradientRenderer.draw(
                uiStateContainer.selectedHotAisleTemperatures(),
                uiStateContainer.minServerTemperatureCelsius(),
                uiStateContainer.maxServerTemperatureCelsius()
        );
        globalOverviewRenderer.draw();
        staticUiRenderer.drawOverlay();
    }

    private void btnClicked(String buttonCode) {
        uiInteractionController.handleButtonClick(buttonCode);
    }

    private void tglChanged(Toggle toggle, int state) {
        uiInteractionController.handleToggleChange(toggle, state);
    }

    // Processing discovers these input callbacks on the PApplet subclass.
    @Override
    public void mouseMoved() {
        infrastructureContainer.mouseInputDispatcher().mouseMoved(mouseX, mouseY, mouseButton);
    }

    @Override
    public void mouseDragged() {
        infrastructureContainer.mouseInputDispatcher().mouseDragged(mouseX, mouseY, mouseButton);
    }

    @Override
    public void mousePressed() {
        infrastructureContainer.mouseInputDispatcher().mousePressed(mouseX, mouseY, mouseButton);
    }

    @Override
    public void mouseReleased() {
        infrastructureContainer.mouseInputDispatcher().mouseReleased(mouseX, mouseY, mouseButton);
    }

    @Override
    public void mouseWheel(MouseEvent event) {
        if (event == null) return;
        infrastructureContainer.mouseInputDispatcher().mouseWheel(mouseX, mouseY, mouseButton, event.getCount(), event.isShiftDown(), event.isControlDown());
    }

    @Override
    public void keyReleased() {
        uiInteractionController.handleKeyReleased(keyCode);
    }
}
