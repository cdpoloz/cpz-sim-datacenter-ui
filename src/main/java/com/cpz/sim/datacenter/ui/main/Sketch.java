package com.cpz.sim.datacenter.ui.main;

import com.cpz.processing.controls.controls.toggle.Toggle;
import com.cpz.sim.datacenter.ui.app.ApplicationBootstrap;
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
import com.cpz.sim.datacenter.ui.ui.panel.RoomPanelUpdater;
import com.cpz.sim.datacenter.ui.ui.panel.SelectedAislePanelUpdater;
import com.cpz.sim.datacenter.ui.ui.panel.SelectedRackPanelUpdater;
import com.cpz.sim.datacenter.ui.ui.render.ControlRenderer;
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
 * @author CPZ
 */
public class Sketch extends PApplet {

    private InfrastructureContainer infrastructureContainer;
    private UiComponentContainer uiComponentContainer;
    private ResourceContainer resourceContainer;
    private SimulationContainer simulationContainer;
    private UiStateContainer uiStateContainer;
    private UiStateInitializer uiStateInitializer;
    private HeaderUpdater headerUpdater;
    private UiUpdateCoordinator uiUpdateCoordinator;
    private UiInteractionController uiInteractionController;
    private UiFormatContainer uiFormatContainer;
    private UiFormatLoader uiFormatLoader;
    private TemperatureRangeCalculator temperatureRangeCalculator;
    private SimulationManager simulationManager;
    private CoolingToggleManager coolingToggleManager;
    private SelectionManager selectionManager;
    private SelectedRackPanelUpdater selectedRackPanelUpdater;
    private SelectedAislePanelUpdater selectedAislePanelUpdater;
    private RoomPanelUpdater roomPanelUpdater;
    private SelectedHotAisleTemperatureGradientRenderer selectedHotAisleTemperatureGradientRenderer;
    private StaticUiRenderer staticUiRenderer;
    private ControlRenderer controlRenderer;

    public void settings() {
        LOG.info("Starting settings");
        PJOGL.setIcon("data" + File.separator + "img" + File.separator + PROPS.getProperty("window.icon"));
        // window size
        size(Integer.parseInt(PROPS.getProperty("sketch.width")), Integer.parseInt(PROPS.getProperty("sketch.height")), P2D);
        // smoothing
        smooth(Integer.parseInt(PROPS.getProperty("sketch.smoothing")));
        LOG.info("Finished settings");
    }

    public void setup() {
        LOG.info("Starting initial setup");
        background(COLOR_BACKGROUND);
        frameRate(Integer.parseInt(PROPS.getProperty("sketch.fps")));
        getSurface().setTitle(PROPS.getProperty("window.title"));
        LOG.info("Finished initial setup");
        // app context
        ApplicationContext context = new ApplicationContext(this);
        // infrastructure
        infrastructureContainer = new InfrastructureContainer();
        InfrastructureInitializer infrastructureInitializer = new InfrastructureInitializer(infrastructureContainer);
        infrastructureInitializer.initialize();
        // controls
        uiComponentContainer = new UiComponentContainer();
        ControlManager controlManager = new ControlManager(context, uiComponentContainer, infrastructureContainer.overlayManager(), infrastructureContainer.inputManager(), infrastructureContainer.mainInputLayer(), this::btnClicked, this::tglChanged);
        controlManager.initialize();
        uiFormatContainer = new UiFormatContainer();
        uiFormatLoader = new UiFormatLoader();
        uiStateContainer = new UiStateContainer();
        // input layer registration
        infrastructureContainer.inputManager().registerLayer(infrastructureContainer.mainInputLayer());
        // resources
        resourceContainer = new ResourceContainer();
        ResourceManager resourceManager = new ResourceManager(context, resourceContainer);
        resourceManager.initialize();
        // containers, managers, updaters, initializers & renderers
        simulationContainer = new SimulationContainer();
        simulationManager = new SimulationManager(context, simulationContainer, uiComponentContainer);
        coolingToggleManager = new CoolingToggleManager(context, simulationContainer, uiComponentContainer);
        selectionManager = new SelectionManager(context, simulationContainer, uiComponentContainer);
        uiInteractionController = new UiInteractionController(uiStateContainer, simulationManager, coolingToggleManager, selectionManager);
        headerUpdater = new HeaderUpdater(context, simulationContainer, uiComponentContainer);
        temperatureRangeCalculator = new TemperatureRangeCalculator();
        selectedRackPanelUpdater = new SelectedRackPanelUpdater(context, simulationContainer, uiComponentContainer, selectionManager);
        selectedAislePanelUpdater = new SelectedAislePanelUpdater(context, simulationContainer, uiComponentContainer, selectionManager);
        roomPanelUpdater = new RoomPanelUpdater(context, simulationContainer, uiComponentContainer);
        uiUpdateCoordinator = new UiUpdateCoordinator(uiStateContainer, uiFormatContainer, simulationManager, selectedRackPanelUpdater, selectedAislePanelUpdater, roomPanelUpdater);
        uiStateInitializer = new UiStateInitializer(context, simulationContainer, uiComponentContainer);
        selectedHotAisleTemperatureGradientRenderer = new SelectedHotAisleTemperatureGradientRenderer(context);
        staticUiRenderer = new StaticUiRenderer(context, resourceContainer, infrastructureContainer.overlayManager());
        controlRenderer = new ControlRenderer(uiComponentContainer);
        simulationManager.initialize();
        selectionManager.initialize();
        // app bootstrap
        ApplicationBootstrap bootstrap = new ApplicationBootstrap(context);
        bootstrap.initialize();
        // number formats
        uiFormatLoader.loadInto(uiFormatContainer);
        TemperatureRange temperatureRange = temperatureRangeCalculator.calculate(simulationContainer.datacenter(), simulationContainer.temperatureOptions());
        uiStateContainer.setMinServerTemperatureCelsius(temperatureRange.minServerTemperatureCelsius());
        uiStateContainer.setMaxServerTemperatureCelsius(temperatureRange.maxServerTemperatureCelsius());
        simulationManager.initializeInitialSimulationState();
        // initial update
        uiStateContainer.setUpdateUI(true);
        uiStateContainer.setUpdateSnapshots(true);
        uiUpdateCoordinator.updateSnapshotsIfNeeded();
        coolingToggleManager.initialize();
        // initial values
        uiStateInitializer.initialize(uiStateContainer.minServerTemperatureCelsius(), uiStateContainer.maxServerTemperatureCelsius(), uiFormatContainer.simpleTemperature());
    }

    public void draw() {
        // update
        headerUpdater.update();
        uiUpdateCoordinator.updateClock();
        uiUpdateCoordinator.updateSnapshotsIfNeeded();
        uiUpdateCoordinator.updateControlsIfNeeded();
        // draw
        staticUiRenderer.drawBackground();
        controlRenderer.draw();
        selectedHotAisleTemperatureGradientRenderer.draw(uiStateContainer.selectedHotAisleTemperatures(), uiStateContainer.minServerTemperatureCelsius(), uiStateContainer.maxServerTemperatureCelsius());
        staticUiRenderer.drawOverlay();
    }

    private void btnClicked(String buttonCode) {
        uiInteractionController.handleButtonClick(buttonCode);
    }

    private void tglChanged(Toggle toggle, int state) {
        uiInteractionController.handleToggleChange(toggle, state);
    }

    // <editor-fold defaultstate="collapsed" desc="*** Processing mouse events ***">
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

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="*** Processing keyboard events ***">
    @Override
    public void keyReleased() {
        uiInteractionController.handleKeyReleased(keyCode);
    }
    // </editor-fold>
}
