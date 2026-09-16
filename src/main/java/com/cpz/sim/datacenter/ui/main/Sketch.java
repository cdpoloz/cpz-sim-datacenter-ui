package com.cpz.sim.datacenter.ui.main;

import com.cpz.processing.controls.controls.toggle.Toggle;
import com.cpz.sim.datacenter.history.DatacenterSimulationStepSnapshot;
import com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot;
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
import java.util.List;
import java.util.Optional;

import static com.cpz.sim.datacenter.ui.main.Launcher.LOG;
import static com.cpz.sim.datacenter.ui.main.Launcher.PROPS;
import static com.cpz.sim.datacenter.ui.util.Constants.COLOR_BACKGROUND;
import static com.cpz.sim.datacenter.ui.util.Constants.COLOR_BLUE_LABEL;

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

    private UiComponentContainer uiComponentContainer;
    private SimulationContainer simulationContainer;

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
        uiComponentContainer = new UiComponentContainer();
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
        simulationContainer = new SimulationContainer();
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
        //***
        updateGlobalOverview();
        //***
        // The gradient must sit above controls but below active/static foreground overlays.
        staticUiRenderer.drawBackground();
        controlRenderer.draw();
        selectedHotAisleTemperatureGradientRenderer.draw(
                uiStateContainer.selectedHotAisleTemperatures(),
                uiStateContainer.minServerTemperatureCelsius(),
                uiStateContainer.maxServerTemperatureCelsius()
        );
        //***
        // room average temperature graph
        float minX = Float.parseFloat(PROPS.getProperty("ui.average.room.temperature.min.x"));
        float maxX = Float.parseFloat(PROPS.getProperty("ui.average.room.temperature.max.x"));
        float minY = Float.parseFloat(PROPS.getProperty("ui.average.room.temperature.min.y"));
        float maxY = Float.parseFloat(PROPS.getProperty("ui.average.room.temperature.max.y"));
        int n = (int) ((maxX - minX) * width);
        List<Double> latestAverageRoomTemperatures = latestAverageRoomTemperatures(n);
        float minServerTemperatureCelsius = uiStateContainer.minServerTemperatureCelsius();
        float maxServerTemperatureCelsius = uiStateContainer.maxServerTemperatureCelsius();
        pushStyle();
        strokeWeight(Float.parseFloat(PROPS.getProperty("ui.average.room.temperature.stroke.weigth")) * height);
        stroke(COLOR_BLUE_LABEL);
        for (int i = 1; i < latestAverageRoomTemperatures.size(); i++) {
            double previousAverageTemperature = latestAverageRoomTemperatures.get(i - 1);
            double averageTemperature = latestAverageRoomTemperatures.get(i);
            float x = minX * width + i;
            float y = map((float) averageTemperature, minServerTemperatureCelsius, maxServerTemperatureCelsius, maxY * height, minY * height);
            y = Math.max(y, minY * height);
            float previousX = minX * width + i - 1;
            float previousY = map((float) previousAverageTemperature, minServerTemperatureCelsius, maxServerTemperatureCelsius, maxY * height, minY * height);
            previousY = Math.max(previousY, minY * height);
            line(x, y, previousX, previousY);
        }
        popStyle();
        //***
        staticUiRenderer.drawOverlay();
    }

    private void updateGlobalOverview() {
        // room average temperature
        if (simulationContainer == null || simulationContainer.operationalSnapshot() == null) return;
        if (uiComponentContainer == null) return;
        if (!uiComponentContainer.labels().containsKey("lblRoomTemperatureValue")) return;
        double averageRoomTemperatureCelsius = allAverageRoomTemperatures().getLast();
        uiComponentContainer.labels().get("lblRoomTemperatureValue").setText(String.format("%.1f°C", averageRoomTemperatureCelsius));
    }

    private List<Double> allAverageRoomTemperatures() {
        if (simulationContainer == null) return List.of();
        if (simulationContainer.simulationHistory() == null) return List.of();
        return simulationContainer
                .simulationHistory()
                .snapshots()
                .stream()
                .map(this::averageRoomTemperatureCelsius)
                .toList();
    }

    private List<Double> latestAverageRoomTemperatures(int n) {
        return simulationContainer
                .simulationHistory()
                .latest(n)
                .stream()
                .map(this::averageRoomTemperatureCelsius)
                .toList();
    }

    private double averageRoomTemperatureCelsius(DatacenterSimulationStepSnapshot snapshot) {
        int onlineServerCount = snapshot
                .operationalSnapshot()
                .racks()
                .values()
                .stream()
                .mapToInt(RackOperationalSnapshot::onlineServerCount)
                .sum();
        double temperatureSumCelsius = snapshot
                .operationalSnapshot()
                .racks()
                .values()
                .stream()
                .filter(RackOperationalSnapshot::hasOnlineServers)
                .mapToDouble(rackSnapshot ->
                        rackSnapshot.averageOnlineTemperatureCelsius()
                                * rackSnapshot.onlineServerCount()
                )
                .sum();
        if (onlineServerCount == 0) return snapshot.operationalSnapshot().roomTemperatureCelsius();
        return temperatureSumCelsius / onlineServerCount;
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
