package com.cpz.sim.datacenter.ui.main;

import com.cpz.processing.controls.controls.toggle.Toggle;
import com.cpz.processing.controls.core.input.InputManager;
import com.cpz.processing.controls.core.input.PointerEvent;
import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.processing.controls.input.ProcessingKeyboardAdapter;
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.datacenter.ui.app.ApplicationBootstrap;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.controls.ControlManager;
import com.cpz.sim.datacenter.ui.controls.CoolingToggleManager;
import com.cpz.sim.datacenter.ui.input.MainInputLayer;
import com.cpz.sim.datacenter.ui.resources.ResourceContainer;
import com.cpz.sim.datacenter.ui.resources.ResourceManager;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.simulation.SimulationManager;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;
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
import java.util.*;

import static com.cpz.sim.datacenter.ui.main.Launcher.LOG;
import static com.cpz.sim.datacenter.ui.main.Launcher.PROPS;
import static com.cpz.sim.datacenter.ui.util.Constants.*;

/**
 * @author CPZ
 */
public class Sketch extends PApplet {

    private InputManager inputManager;
    private OverlayManager overlayManager;
    private ProcessingKeyboardAdapter processingKeyboardAdapter;
    private UiComponentContainer uiComponentContainer;
    private ResourceContainer resourceContainer;
    private SimulationContainer simulationContainer;
    private SimulationManager simulationManager;
    private CoolingToggleManager coolingToggleManager;
    private SelectionManager selectionManager;
    private SelectedRackPanelUpdater selectedRackPanelUpdater;
    private SelectedAislePanelUpdater selectedAislePanelUpdater;
    private RoomPanelUpdater roomPanelUpdater;
    private SelectedHotAisleTemperatureGradientRenderer selectedHotAisleTemperatureGradientRenderer;
    private StaticUiRenderer staticUiRenderer;
    private ControlRenderer controlRenderer;
    private boolean updateSnapshots, updateUI;
    private int previousSecond, previousDay;
    private float minServerTemperatureCelsius, maxServerTemperatureCelsius; //*******
    private List<Float> selectedHotAisleTemperatures;
    private String temperatureFormat, simpleTemperatureFormat, percentageFormat, powerKwFormat, powerMwFormat, speedFormat, pressureFormat, airflowFormat;

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
        // input manager
        inputManager = new InputManager();
        MainInputLayer mainInputLayer = new MainInputLayer(0);
        // overlay manager
        overlayManager = new OverlayManager();
        // controls
        uiComponentContainer = new UiComponentContainer();
        ControlManager controlManager = new ControlManager(
                context,
                uiComponentContainer,
                overlayManager,
                inputManager,
                mainInputLayer,
                this::btnClicked,
                this::tglChanged
        );
        controlManager.initialize();
        // input layer registration
        inputManager.registerLayer(mainInputLayer);
        //inputManager.registerLayer(new TooltipInputLayer(1000, tooltips));
        // resources
        resourceContainer = new ResourceContainer();
        ResourceManager resourceManager = new ResourceManager(context, resourceContainer);
        resourceManager.initialize();
        // managers, updaters & renderers
        simulationContainer = new SimulationContainer();
        simulationManager = new SimulationManager(context, simulationContainer, uiComponentContainer);
        coolingToggleManager = new CoolingToggleManager(context, simulationContainer, uiComponentContainer);
        selectionManager = new SelectionManager(context, simulationContainer, uiComponentContainer);
        selectedRackPanelUpdater = new SelectedRackPanelUpdater(context, simulationContainer, uiComponentContainer, selectionManager);
        selectedAislePanelUpdater = new SelectedAislePanelUpdater(context, simulationContainer, uiComponentContainer, selectionManager);
        roomPanelUpdater = new RoomPanelUpdater(context, simulationContainer, uiComponentContainer);
        selectedHotAisleTemperatureGradientRenderer = new SelectedHotAisleTemperatureGradientRenderer(context);
        staticUiRenderer = new StaticUiRenderer(context, resourceContainer, overlayManager);
        controlRenderer = new ControlRenderer(uiComponentContainer);
        simulationManager.initialize();
        selectionManager.initialize();
        // app bootstrap
        ApplicationBootstrap bootstrap = new ApplicationBootstrap(context);
        bootstrap.initialize();
        // number formats
        percentageFormat = PROPS.getProperty("number.format.percentage");
        temperatureFormat = PROPS.getProperty("number.format.temperature");
        simpleTemperatureFormat = PROPS.getProperty("number.format.temperature.simple");
        powerKwFormat = PROPS.getProperty("number.format.power.kw");
        powerMwFormat = PROPS.getProperty("number.format.power.mw");
        speedFormat = PROPS.getProperty("number.format.speed");
        pressureFormat = PROPS.getProperty("number.format.pressure");
        airflowFormat = PROPS.getProperty("number.format.airflow");
        calculateTemperatureRange(simulationContainer.datacenter(), simulationContainer.temperatureOptions());
        simulationManager.initializeInitialSimulationState();
        // initial update
        updateUI = true;
        updateSnapshots = true;
        updateSnapshots();
        coolingToggleManager.initialize();
        // initial values
        uiComponentContainer.labels().get("lblRoomTemperatureScale01").setText(String.format(simpleTemperatureFormat, minServerTemperatureCelsius));
        for (int i = 0; i < 5; i++) {
            float temperature = map(i, 0, 5, minServerTemperatureCelsius, maxServerTemperatureCelsius);
            String temperatureScaleLabelCode = "lblRoomTemperatureScale0" + (i + 1);
            uiComponentContainer.labels().get(temperatureScaleLabelCode).setText(String.format(simpleTemperatureFormat, temperature));
        }
        uiComponentContainer.labels().get("lblRoomTemperatureScale06").setText(String.format(simpleTemperatureFormat, maxServerTemperatureCelsius));
        int totalInstalledServers = simulationContainer.operationalSnapshot().racks()
                .values()
                .stream()
                .mapToInt(RackOperationalSnapshot::installedServerCount)
                .sum();
        int totalOnlineServers = simulationContainer.operationalSnapshot().racks()
                .values()
                .stream()
                .mapToInt(RackOperationalSnapshot::onlineServerCount)
                .sum();
        uiComponentContainer.labels().get("lblRoomTotalServersValue").setText(String.valueOf(totalInstalledServers));
        uiComponentContainer.labels().get("lblRoomOnlineServersValue").setText(String.valueOf(totalOnlineServers));
        uiComponentContainer.labels().get("lblDate").setText(String.format("%02d", day()) + "/" + String.format("%02d", month()) + "/" + year());
        uiComponentContainer.labels().get("lblTime").setText(String.format("%02d", hour()) + ":" + String.format("%02d", minute()) + ":" + String.format("%02d", second()));
    }

    private void updateHeader() {
        // single-room layout for now; refresh here when room switching is added
        String s = "DATACENTER MAP";
        if (simulationContainer.roomName() != null && !simulationContainer.roomName().isEmpty())
            s += (" - " + simulationContainer.roomName());
        uiComponentContainer.labels().get("lblRoom").setText(s);
        updateDateTime();
    }

    private void updateDateTime() {
        if (second() == previousSecond) return;
        previousSecond = second();
        uiComponentContainer.labels().get("lblTime").setText(String.format("%02d", hour()) + ":" + String.format("%02d", minute()) + ":" + String.format("%02d", second()));
        if (day() == previousDay) return;
        previousDay = day();
        uiComponentContainer.labels().get("lblDate").setText(String.format("%02d", day()) + "/" + String.format("%02d", month()) + "/" + year());
    }

    private void btnClicked(String buttonCode) {
        if (buttonCode.startsWith("btnSelectedRack"))
            selectionManager.updateSelectedRack(buttonCode.replace("btnSelectedRack", ""));
        else if (buttonCode.startsWith("btnSelectedColumn"))
            selectionManager.updateSelectedColumn(buttonCode.replace("btnSelectedColumn", ""));
        else if (buttonCode.startsWith("btnPlay")) {
            if (buttonCode.equals("btnPlayPlus")) {
                simulationManager.increaseSimulationSpeed();
                return;
            }
            if (buttonCode.equals("btnPlayMinus")) {
                simulationManager.decreaseSimulationSpeed();
                return;
            }
        }
        updateUI = true;
    }

    private void tglChanged(Toggle tgl, int state) {
        String toggleCode = tgl.getCode();
        if (toggleCode.startsWith("tglSupply") || toggleCode.startsWith("tglExhaust"))
            coolingToggleManager.toggleCoolingUnit(tgl, state);
        else if (toggleCode.equals("tglPlay")) {
            if (simulationManager.isSyncingPlayToggle()) return;
            simulationManager.toggleSimulation();
        }
    }

    private void calculateTemperatureRange(Datacenter datacenter, TemperatureSystemOptions temperatureOptions) {
        double ambientTemperature = temperatureOptions.ambientTemperatureCelsius();
        double globalHeatDissipation = temperatureOptions.heatDissipationWattsPerCelsius();
        double highestEquilibriumTemperature = ambientTemperature;
        for (Server server : datacenter.getServers()) {
            ServerConfig config = server.getConfig();
            ServerThermalProperties thermalProperties = config.thermalProperties();
            double heatDissipation = thermalProperties != null ? thermalProperties.heatDissipationWattsPerCelsius() : globalHeatDissipation;
            double equilibriumTemperature = ambientTemperature + config.maxPowerWatts() / heatDissipation;
            highestEquilibriumTemperature = Math.max(highestEquilibriumTemperature, equilibriumTemperature);
        }
        minServerTemperatureCelsius = (float) ambientTemperature;
        maxServerTemperatureCelsius = (float) Math.ceil(highestEquilibriumTemperature);
    }

    public void draw() {
        // update
        updateClock();
        updateSnapshots();
        updateControls();
        //draw
        staticUiRenderer.drawBackground();
        controlRenderer.draw();
        selectedHotAisleTemperatureGradientRenderer.draw(selectedHotAisleTemperatures, minServerTemperatureCelsius, maxServerTemperatureCelsius);
        staticUiRenderer.drawOverlay();
    }

    private void updateClock() {
        if (!simulationManager.updateClock()) return;
        updateSnapshots = true;
    }

    private void updateSnapshots() {
        if (!updateSnapshots) return;
        simulationManager.updateSnapshots();
        updateSnapshots = false;
        updateUI = true;
    }

    private void updateControls() {
        if (!updateUI) return;
        updateHeader();
        selectedRackPanelUpdater.update(
                minServerTemperatureCelsius,
                maxServerTemperatureCelsius,
                temperatureFormat,
                percentageFormat,
                powerKwFormat
        );
        selectedHotAisleTemperatures =
                selectedAislePanelUpdater.update(
                        minServerTemperatureCelsius,
                        maxServerTemperatureCelsius,
                        temperatureFormat,
                        percentageFormat,
                        airflowFormat
                );
        roomPanelUpdater.update(minServerTemperatureCelsius, maxServerTemperatureCelsius);
        updateUI = false;
    }

    // <editor-fold defaultstate="collapsed" desc="*** mouse events ***">
    @Override
    public void mouseMoved() {
        inputManager.dispatchPointer(new PointerEvent(PointerEvent.Type.MOVE, (float) mouseX, (float) mouseY, mouseButton));
    }

    @Override
    public void mouseDragged() {
        inputManager.dispatchPointer(new PointerEvent(PointerEvent.Type.DRAG, (float) mouseX, (float) mouseY, mouseButton));
    }

    @Override
    public void mousePressed() {
        inputManager.dispatchPointer(new PointerEvent(PointerEvent.Type.PRESS, (float) mouseX, (float) mouseY, mouseButton));
    }

    @Override
    public void mouseReleased() {
        inputManager.dispatchPointer(new PointerEvent(PointerEvent.Type.RELEASE, (float) mouseX, (float) mouseY, mouseButton));
    }

    @Override
    public void mouseWheel(MouseEvent event) {
        if (event == null) return;
        inputManager.dispatchPointer(new PointerEvent(PointerEvent.Type.WHEEL, (float) mouseX, (float) mouseY, mouseButton, (float) event.getCount(), event.isShiftDown(), event.isControlDown()));
    }

    // </editor-fold>

    @Override
    public void keyReleased() {
        if (keyCode == SPACE_BAR) {
            if (simulationManager.isSyncingPlayToggle()) return;
            simulationManager.toggleSimulation();
        } else if (keyCode == PLUS) simulationManager.increaseSimulationSpeed();
        else if (keyCode == MINUS) simulationManager.decreaseSimulationSpeed();
    }

}
