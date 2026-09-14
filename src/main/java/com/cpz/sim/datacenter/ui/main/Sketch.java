package com.cpz.sim.datacenter.ui.main;

import com.cpz.processing.controls.controls.button.Button;
import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.controls.toggle.Toggle;
import com.cpz.processing.controls.core.input.InputManager;
import com.cpz.processing.controls.core.input.PointerEvent;
import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.processing.controls.input.ProcessingKeyboardAdapter;
import com.cpz.sim.datacenter.cooling.CoolingZoneDefinition;
import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.snapshot.*;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.datacenter.ui.app.ApplicationBootstrap;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.config.HotAisleDefinition;
import com.cpz.sim.datacenter.ui.controls.ControlManager;
import com.cpz.sim.datacenter.ui.input.MainInputLayer;
import com.cpz.sim.datacenter.ui.resources.ResourceContainer;
import com.cpz.sim.datacenter.ui.resources.ResourceManager;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.simulation.SimulationManager;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;
import com.cpz.utils.color.Colors;
import processing.core.PApplet;
import processing.event.MouseEvent;
import processing.opengl.PJOGL;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private boolean updateSnapshots, updateUI, syncingPlayToggle;
    private boolean syncingCoolingToggles;
    private int previousSecond, previousDay;
    private String selectedColumn, selectedRack;
    private HotAisleDefinition selectedHotAisle;
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
        // simulation manager
        simulationContainer = new SimulationContainer();
        simulationManager = new SimulationManager(context, simulationContainer, uiComponentContainer);
        simulationManager.initialize();
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
        // default column and rack
        selectedColumn = "C01";
        selectedRack = "R01";
        resolveSelectedHotAisle();
        showSelectedRackHighlight();
        showSelectedAisleHighlight();
        calculateTemperatureRange(simulationContainer.datacenter(), simulationContainer.temperatureOptions());
        simulationManager.initializeInitialSimulationState();
        // initial update
        updateUI = true;
        updateSnapshots = true;
        updateSnapshots();
        simulationManager.initializeCoolingToggleGroups();
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

    private void increaseSimulationSpeed() {
        if (simulationContainer.simulationSpeedFactorIndex() >= simulationContainer.simulationSpeedFactors().size() - 1)
            return;
        int newIndex = simulationContainer.simulationSpeedFactorIndex() + 1;
        simulationContainer.setSimulationSpeedFactorIndex(newIndex);
        simulationManager.updateSimulationTimerPeriod();
        simulationManager.updateSimulationControls();
    }

    private void decreaseSimulationSpeed() {
        if (simulationContainer.simulationSpeedFactorIndex() <= 0) return;
        int newIndex = simulationContainer.simulationSpeedFactorIndex() - 1;
        simulationContainer.setSimulationSpeedFactorIndex(newIndex);
        simulationManager.updateSimulationTimerPeriod();
        simulationManager.updateSimulationControls();
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
            updateSelectedRack(buttonCode.replace("btnSelectedRack", ""));
        else if (buttonCode.startsWith("btnSelectedColumn"))
            updateSelectedColumn(buttonCode.replace("btnSelectedColumn", ""));
        else if (buttonCode.startsWith("btnPlay")) {
            if (buttonCode.equals("btnPlayPlus")) {
                increaseSimulationSpeed();
                return;
            }
            if (buttonCode.equals("btnPlayMinus")) {
                decreaseSimulationSpeed();
                return;
            }
        }
        updateUI = true;
    }

    private void tglChanged(Toggle tgl, int state) {
        String toggleCode = tgl.getCode();
        if (toggleCode.startsWith("tglSupply") || toggleCode.startsWith("tglExhaust")) coolingToggleClicked(tgl, state);
        else if (toggleCode.equals("tglPlay")) {
            if (syncingPlayToggle) return;
            toggleSimulation();
        }
    }

    private void toggleSimulation() {
        simulationContainer.simulationTimer().toggle();
        syncingPlayToggle = true;
        try {
            uiComponentContainer.toggles().get("tglPlay").setState(simulationContainer.simulationTimer().isRunning() ? 1 : 0);
        } finally {
            syncingPlayToggle = false;
        }
        simulationManager.updateSimulationControls();
    }

    private void coolingToggleClicked(Toggle tgl, int state) {
        if (syncingCoolingToggles) return;
        String toggleCode = tgl.getCode();
        boolean enabled = state == 1;
        if (toggleCode.equals("tglSupply")) {
            updateChildCoolingToggles(simulationContainer.supplyToggleCodes(), enabled);
            return;
        }
        if (toggleCode.equals("tglExhaust")) {
            updateChildCoolingToggles(simulationContainer.exhaustToggleCodes(), enabled);
            return;
        }
        if (simulationContainer.supplyToggleCodes().contains(toggleCode)) {
            updateCoolingUnit(tgl, enabled);
            updateMasterToggle("tglSupply", simulationContainer.supplyToggleCodes());
            return;
        }
        if (simulationContainer.exhaustToggleCodes().contains(toggleCode)) {
            updateCoolingUnit(tgl, enabled);
            updateMasterToggle("tglExhaust", simulationContainer.exhaustToggleCodes());
        }
    }

    private void updateChildCoolingToggles(List<String> toggleCodes, boolean enabled) {
        syncingCoolingToggles = true;
        try {
            for (String toggleCode : toggleCodes) {
                Toggle toggle = uiComponentContainer.toggles().get(toggleCode);
                if (toggle == null) throw new IllegalStateException("Missing toggle: " + toggleCode);
                toggle.setState(enabled ? 1 : 0);
                updateCoolingUnit(toggle, enabled);
            }
        } finally {
            syncingCoolingToggles = false;
        }
    }

    private void updateMasterToggle(String masterToggleCode, List<String> childToggleCodes) {
        boolean anyEnabled = childToggleCodes
                .stream()
                .map(uiComponentContainer.toggles()::get)
                .anyMatch(toggle -> toggle != null && toggle.getState() == 1);
        syncingCoolingToggles = true;
        try {
            Toggle masterToggle = uiComponentContainer.toggles().get(masterToggleCode);
            if (masterToggle == null) throw new IllegalStateException("Missing master toggle: " + masterToggleCode);
            masterToggle.setState(anyEnabled ? 1 : 0);
        } finally {
            syncingCoolingToggles = false;
        }
    }

    private void updateCoolingUnit(Toggle tgl, boolean enabled) {
        String unitType = "";
        String tglCode = tgl.getCode();
        if (tglCode.startsWith("tglSupply")) unitType = "SUPPLY";
        else if (tglCode.startsWith("tglExhaust")) unitType = "EXHAUST";
        if (unitType.isEmpty()) return;
        boolean individualToggle = tglCode.startsWith("tglSupplyC") || tglCode.startsWith("tglExhaustC");
        if (!individualToggle) return;
        String unitCode = unitType
                + "-"
                + tglCode
                .replace("tglSupply", "")
                .replace("tglExhaust", "");
        simulationContainer.coolingSystem().setEnabled(unitCode, enabled);
    }

    private void updateSelectedRack(String clickedRack) {
        selectedRack = "R" + clickedRack.toLowerCase().replace("right", "").replace("left", "");
        if (!selectedColumn.equals(PROPS.getProperty("datacenter.first.column")) && !selectedColumn.equals(PROPS.getProperty("datacenter.last.column"))) {
            if (clickedRack.toLowerCase().contains("left")) selectedColumn = selectedHotAisle.columns().getFirst();
            else if (clickedRack.toLowerCase().contains("right")) selectedColumn = selectedHotAisle.columns().getLast();
        }
        showSelectedRackHighlight();
    }

    private void updateSelectedColumn(String selectedColumn) {
        this.selectedColumn = selectedColumn;
        resolveSelectedHotAisle();
        showSelectedAisleHighlight();
        showSelectedRackHighlight();
    }

    private void showSelectedRackHighlight() {
        int i = Integer.parseInt(selectedColumn.replace("C", ""));
        String side = i % 2 == 0 ? "Left" : "Right";
        String selectedRackIndicatorCode = "indSelectedRack" + side + selectedRack.replace("R", "");
        String selectedButtonCode = selectedRackIndicatorCode.replace("ind", "btn");
        for (Button btn : uiComponentContainer.buttonsSelectedAisleRack().values()) {
            if (selectedColumn.equals(PROPS.getProperty("datacenter.first.column")) && btn.getCode().toLowerCase().contains("left"))
                btn.setVisible(false);
            else if (selectedColumn.equals(PROPS.getProperty("datacenter.last.column")) && btn.getCode().toLowerCase().contains("right"))
                btn.setVisible(false);
            else btn.setVisible(!btn.getCode().equals(selectedButtonCode));
        }
        for (Indicator ind : uiComponentContainer.indicatorsSelectedRack().values())
            ind.setOn(ind.getCode().equals(selectedRackIndicatorCode));
    }

    private void showSelectedAisleHighlight() {
        uiComponentContainer.indicatorsSelectedAisle().values().forEach(ind -> ind.setOn(false));
        String selectedAisleIndicatorCode = "indSelectedAisle";
        switch (selectedHotAisle.code()) {
            case "HA01" -> selectedAisleIndicatorCode += "C01";
            case "HA02" -> selectedAisleIndicatorCode += "C02-C03";
            case "HA03" -> selectedAisleIndicatorCode += "C04-C05";
            case "HA04" -> selectedAisleIndicatorCode += "C06-C07";
            case "HA05" -> selectedAisleIndicatorCode += "C08";
            default -> {
                return;
            }
        }
        uiComponentContainer.indicatorsSelectedAisle().get(selectedAisleIndicatorCode).setOn(true);
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
        drawBackground();
        drawControls();
        drawSelectedHotAisleTemperatureGradient();
        drawOverlay();
    }

    private void updateClock() {
        if (simulationContainer.simulationTimer() == null || simulationContainer.engine() == null) return;
        if (!simulationContainer.simulationTimer().pollPeriodPulse()) return;
        simulationContainer.engine().step();
        updateSnapshots = true;
    }

    private void updateSnapshots() {
        if (!updateSnapshots) return;
        simulationContainer.setEnergySnapshot(
                simulationContainer
                        .energySnapshotProvider()
                        .snapshot(simulationContainer.engine().currentTick())
        );
        simulationContainer.setTemperatureSnapshot(
                simulationContainer
                        .temperatureSnapshotProvider()
                        .snapshot(simulationContainer.engine().currentTick())
        );
        simulationContainer.setHealthSnapshot(
                simulationContainer
                        .healthSnapshotProvider()
                        .snapshot(simulationContainer.engine().currentTick())
        );
        simulationContainer.setOperationalSnapshot(
                simulationContainer
                        .operationalSnapshotProvider()
                        .snapshot(simulationContainer.energySnapshot(), simulationContainer.temperatureSnapshot(), simulationContainer.healthSnapshot())
        );
        updateSnapshots = false;
        updateUI = true;
    }

    private void updateControls() {
        if (!updateUI) return;
        updateHeader();
        updateSelectedRackPanel();
        updateSelectedAislePanel();
        updateRoomPanel();
        updateUI = false;
    }

    private void updateSelectedRackPanel() {
        uiComponentContainer.labels().get("lblSelectedRackValue").setText(selectedColumn + "-" + selectedRack);
        Rack rack = resolveSelectedRack();
        RackLocation rackLocation = new RackLocation(selectedColumn, new RackCode(selectedRack));
        RackOperationalSnapshot rackSnapshot = simulationContainer.operationalSnapshot()
                .findRack(rackLocation)
                .orElseThrow(() -> new IllegalStateException("Missing operational snapshot for rack: " + rackLocation.code()
                ));
        Map<ServerLocation, ServerEnergySnapshot> energyByLocation = resolveEnergyByLocation();
        Map<ServerLocation, ServerTemperatureSnapshot> temperatureByLocation = resolveTemperatureByLocation();
        Map<ServerLocation, ServerHealthSnapshot> healthByLocation = resolveHealthByLocation();
        for (String slot : rack.getSlotCodes()) {
            String slotNumber = slot.replace("S", "");
            ServerLocation location = new ServerLocation(selectedColumn, new RackCode(selectedRack), slot);
            Optional<Server> installedServer = simulationContainer.datacenter().getServer(location);
            Label slotTemperatureLabel = uiComponentContainer.labels().get("lblSlotTemperature" + slotNumber);
            Label slotLoadLabel = uiComponentContainer.labels().get("lblSlotLoad" + slotNumber);
            Label slotPowerLabel = uiComponentContainer.labels().get("lblSlotPower" + slotNumber);
            Indicator indSlot = uiComponentContainer.indicators().get("indSlot" + slotNumber);
            Indicator emptySlotIndicator = uiComponentContainer.indicators().get("indSlotEmpty" + slotNumber);
            Indicator indSlotOffline = uiComponentContainer.indicators().get("indSlotOffline" + slotNumber);
            Indicator okSlotStatusIndicator = uiComponentContainer.indicators().get("indSlotOk" + slotNumber);
            Indicator alertSlotStatusIndicator = uiComponentContainer.indicators().get("indSlotAlert" + slotNumber);
            Indicator alertIndicator = uiComponentContainer.indicatorsAlert().get("indAlert" + slotNumber);
            Indicator aiSlotIndicator1 = uiComponentContainer.indicatorsAiSlot().get("indSlotAI" + slotNumber + "-1");
            Indicator aiSlotIndicator2 = uiComponentContainer.indicators().get("indSlotAI" + slotNumber + "-2");
            if (installedServer.isEmpty()) {
                showEmptySlot(indSlot,
                        slotTemperatureLabel,
                        slotLoadLabel,
                        slotPowerLabel,
                        emptySlotIndicator,
                        indSlotOffline,
                        okSlotStatusIndicator,
                        alertSlotStatusIndicator,
                        alertIndicator,
                        aiSlotIndicator1,
                        aiSlotIndicator2
                );
                continue;
            }
            ServerTemperatureSnapshot temperature = temperatureByLocation.get(location);
            if (temperature == null)
                throw new IllegalStateException("Missing temperature snapshot for server: " + location);
            ServerEnergySnapshot energy = energyByLocation.get(location);
            if (energy == null) throw new IllegalStateException("Missing energy snapshot for server: " + location);
            ServerHealthSnapshot health = healthByLocation.get(location);
            if (health == null) throw new IllegalStateException("Missing health snapshot for server: " + location);
            updateSlotColor(indSlot, (float) temperature.temperatureCelsius());
            slotTemperatureLabel.setText(String.format(temperatureFormat, temperature.temperatureCelsius()));
            slotLoadLabel.setText(String.format(percentageFormat, energy.utilization() * 100));
            slotPowerLabel.setText(String.format(powerKwFormat, energy.currentPowerWatts() / 1000));
            HardwareStatus status = health.status();
            okSlotStatusIndicator.setOn(status == HardwareStatus.OK);
            alertSlotStatusIndicator.setOn(status == HardwareStatus.ALERT);
            alertIndicator.setOn(status == HardwareStatus.ALERT);
            boolean loadAlert = health.hasAlertReason(ServerAlertReason.HIGH_UTILIZATION);
            slotLoadLabel.setTextColor(loadAlert ? COLOR_MAGENTA_LABEL : COLOR_BLUE_LABEL);
            boolean temperatureAlert = health.hasAlertReason(ServerAlertReason.HIGH_TEMPERATURE);
            updateTemperatureLabelColor(temperatureAlert, slotTemperatureLabel, temperature.temperatureCelsius());
            emptySlotIndicator.setOn(false);
            indSlotOffline.setOn(status == HardwareStatus.OFFLINE);
            boolean aiServer = installedServer.orElseThrow().getRole() == ServerRole.AI;
            aiSlotIndicator1.setOn(aiServer);
            aiSlotIndicator2.setOn(aiServer);
        }
        Label averageTemperatureLabel = uiComponentContainer.labels().get("lblRackAverageTemperatureValue");
        Label averageLoadLabel = uiComponentContainer.labels().get("lblRackAverageLoadValue");
        if (rackSnapshot.hasOnlineServers()) {
            averageTemperatureLabel.setTextColor(COLOR_YELLOW_LABEL);
            averageTemperatureLabel.setText(String.format(temperatureFormat, rackSnapshot.averageOnlineTemperatureCelsius()));
            averageLoadLabel.setText(String.format(percentageFormat, rackSnapshot.averageOnlineUtilization() * 100));
        } else {
            averageTemperatureLabel.setTextColor(COLOR_WHITE_LABEL);
            averageTemperatureLabel.setText("--");
            averageLoadLabel.setText("--");
        }
        uiComponentContainer.labels().get("lblRackCurrentPowerValue").setText(String.format(powerKwFormat, rackSnapshot.currentPowerWatts() / 1000));
        updateBar("SelectedRackPowerBar", rackSnapshot.currentPowerWatts(), rackSnapshot.idlePowerWatts(), rackSnapshot.maxPowerWatts());
    }

    private void updateSlotColor(Indicator indSlot, float temperature) {
        Objects.requireNonNull(indSlot);
        float fColor = map(temperature, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 1);
        fColor = Math.clamp(fColor, 0, 1);
        int colorSlot = Colors.lerpColor(COLOR_MIN_TEMPERATURE, COLOR_MAX_TEMPERATURE, fColor);
        indSlot.setOnColor(colorSlot);
        indSlot.setOn(true);
    }

    private void showEmptySlot(
            Indicator indSlot,
            Label slotTemperatureLabel,
            Label slotLoadLabel,
            Label slotPowerLabel,
            Indicator emptySlotIndicator,
            Indicator indSlotOffline,
            Indicator okSlotStatusIndicator,
            Indicator alertSlotStatusIndicator,
            Indicator alertIndicator,
            Indicator aiSlotIndicator1,
            Indicator aiSlotIndicator2
    ) {
        Objects.requireNonNull(indSlot);
        Objects.requireNonNull(slotTemperatureLabel);
        Objects.requireNonNull(slotLoadLabel);
        Objects.requireNonNull(slotPowerLabel);
        Objects.requireNonNull(emptySlotIndicator);
        Objects.requireNonNull(indSlotOffline);
        Objects.requireNonNull(okSlotStatusIndicator);
        Objects.requireNonNull(alertSlotStatusIndicator);
        Objects.requireNonNull(alertIndicator);
        Objects.requireNonNull(aiSlotIndicator1);
        Objects.requireNonNull(aiSlotIndicator2);
        slotTemperatureLabel.setTextColor(COLOR_WHITE_LABEL);
        slotLoadLabel.setTextColor(COLOR_WHITE_LABEL);
        slotTemperatureLabel.setText("--");
        slotLoadLabel.setText("--");
        slotPowerLabel.setText("--");
        indSlot.setOn(false);
        emptySlotIndicator.setOn(true);
        indSlotOffline.setOn(false);
        okSlotStatusIndicator.setOn(false);
        alertSlotStatusIndicator.setOn(false);
        alertIndicator.setOn(false);
        aiSlotIndicator1.setOn(false);
        aiSlotIndicator2.setOn(false);
    }

    private void updateTemperatureLabelColor(boolean alert, Label label, double temperature) {
        if (alert) label.setTextColor(COLOR_MAGENTA_LABEL);
        else if (temperature >= Double.parseDouble(PROPS.getProperty("simulation.health.temperature.warning-threshold-celsius")))
            label.setTextColor(COLOR_YELLOW_LABEL);
        else label.setTextColor(COLOR_GREEN_LABEL);
    }

    private void updateBar(String type, double value, double minValue, double maxValue) {
        if (type == null || type.isEmpty()) return;
        String key = "ind" + type;
        int iMax = (int) map((float) value, (float) minValue, (float) maxValue, 1, 6);
        for (int i = 0; i < 6; i++) uiComponentContainer.indicators().get(key + (i + 1)).setOn(i < iMax);
    }

    private Rack resolveSelectedRack() {
        return simulationContainer.datacenter().findRack(selectedColumn, selectedRack).orElseThrow(() -> new IllegalStateException("Rack not found: " + selectedColumn + "-" + selectedRack));
    }

    private void updateSelectedAislePanel() {
        boolean leftEdgeAisleSelected = selectedColumn.equals(PROPS.getProperty("datacenter.first.column"));
        boolean rightEdgeAisleSelected = selectedColumn.equals(PROPS.getProperty("datacenter.last.column"));
        uiComponentContainer.indicatorsNullAisle().get("indNullAisleLeft").setOn(leftEdgeAisleSelected);
        uiComponentContainer.indicators().get("indColdAirArrowsLeft").setOn(!leftEdgeAisleSelected);
        uiComponentContainer.indicatorsNullAisle().get("indNullAisleRight").setOn(rightEdgeAisleSelected);
        uiComponentContainer.indicators().get("indColdAirArrowsRight").setOn(!rightEdgeAisleSelected);
        resolveSelectedHotAisle();
        uiComponentContainer.labels().get("lblSelectedAisleValue").setText(selectedHotAisle.displayName());
        // additional data
        List<String> aisleZoneCodes = resolveAisleCoolingZoneCodes(selectedHotAisle);
        CoolingZoneGroupSnapshot coolingGroupSnapshot = simulationContainer.coolingSnapshot().aggregateZones(selectedHotAisle.code(), aisleZoneCodes);
        double thermalCoverage = coolingGroupSnapshot.thermalCoverage();
        uiComponentContainer.labels().get("lblSelectedAisleThermalCoverageValue").setText(String.format(percentageFormat, thermalCoverage * 100.0));
        double deltaTinOut = coolingGroupSnapshot.airTemperatureRiseCelsius();
        uiComponentContainer.labels().get("lblSelectedAisleTemperatureDeltaValue").setText(String.format(temperatureFormat, deltaTinOut));
        double recirculation = coolingGroupSnapshot.averageRecirculationFraction();
        uiComponentContainer.labels().get("lblSelectedAisleRecirculationValue").setText(String.format(percentageFormat, recirculation * 100.0));
        double supplyAirflow = aisleZoneCodes.stream()
                .map(simulationContainer.coolingSnapshot()::findZone)
                .flatMap(Optional::stream)
                .mapToDouble(CoolingZoneSnapshot::supplyAirflowCubicMetersPerSecond)
                .sum();
        double exhaustAirflow =
                aisleZoneCodes
                        .stream()
                        .map(simulationContainer.coolingSnapshot()::findZone)
                        .flatMap(Optional::stream)
                        .mapToDouble(CoolingZoneSnapshot::exhaustAirflowCubicMetersPerSecond)
                        .sum();
        uiComponentContainer.labels().get("lblSelectedAisleAirflowValue").setText(String.format(airflowFormat, supplyAirflow, exhaustAirflow));
        // installed servers
        ServerGroupOperationalSnapshot aisleSnapshot = simulationContainer.operationalSnapshot()
                .findServerGroup(selectedHotAisle.code())
                .orElseThrow(() -> new IllegalStateException("Missing operational snapshot for aisle: " + selectedHotAisle.code()));
        Label averageTemperatureLabel = uiComponentContainer.labels().get("lblSelectedAisleAverageTemperatureValue");
        Label maxTemperatureLabel = uiComponentContainer.labels().get("lblSelectedAisleMaximumTemperatureValue");
        Label averageLoadLabel = uiComponentContainer.labels().get("lblSelectedAisleITLoadValue");
        uiComponentContainer.indicatorsSelectedAisleMaximumTemperatureServer().values().forEach(ind -> ind.setOn(false));
        if (!aisleSnapshot.hasInstalledServers()) {
            averageTemperatureLabel.setTextColor(COLOR_WHITE_LABEL);
            maxTemperatureLabel.setTextColor(COLOR_WHITE_LABEL);
            averageTemperatureLabel.setText("--");
            maxTemperatureLabel.setText("--");
            averageLoadLabel.setText("--");
            return;
        }
        // Maximum temperature considers all installed servers
        double maxTemperature = aisleSnapshot.maximumTemperatureCelsius();
        int maxTemperatureColor = resolveTemperatureRangeColor((float) maxTemperature);
        maxTemperatureLabel.setTextColor(maxTemperatureColor);
        maxTemperatureLabel.setText(String.format(temperatureFormat, maxTemperature));
        ServerLocation maxTemperatureLocation = aisleSnapshot
                .maximumTemperatureLocation()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "The aisle has installed servers, "
                                        + "but does not report the location "
                                        + "of the maximum temperature: "
                                        + selectedHotAisle.code()
                        )
                );
        String maxTemperatureColumn = maxTemperatureLocation.column();
        String maxTemperatureSide;
        if (maxTemperatureColumn.equals(PROPS.getProperty("datacenter.first.column")))
            maxTemperatureSide = "Right";
        else if (maxTemperatureColumn.equals(PROPS.getProperty("datacenter.last.column")))
            maxTemperatureSide = "Left";
        else {
            int columnNumber = Integer.parseInt(maxTemperatureColumn.replace("C", ""));
            maxTemperatureSide = columnNumber % 2 == 0 ? "Left" : "Right";
        }
        String rackNumber = maxTemperatureLocation.rackCode().value().replace("R", "");
        String indicatorCode = "indSelectedAisleMaximumTemperatureServer" + maxTemperatureSide + rackNumber;
        Indicator maxTemperatureIndicator = uiComponentContainer.indicatorsSelectedAisleMaximumTemperatureServer().get(indicatorCode);
        if (maxTemperatureIndicator == null)
            throw new IllegalStateException("Missing maximum temperature indicator: " + indicatorCode);
        maxTemperatureIndicator.setOnColor(maxTemperatureColor);
        maxTemperatureIndicator.setOn(true);
        /*
         * Averages only include online servers.
         */
        if (aisleSnapshot.hasOnlineServers()) {
            double averageTemperature = aisleSnapshot.averageOnlineTemperatureCelsius();
            double averageLoad = aisleSnapshot.averageOnlineUtilization();
            int averageTemperatureColor = resolveTemperatureRangeColor((float) averageTemperature);
            averageTemperatureLabel.setTextColor(averageTemperatureColor);
            averageTemperatureLabel.setText(String.format(temperatureFormat, averageTemperature));
            averageLoadLabel.setText(String.format(percentageFormat, averageLoad * 100));
        } else {
            averageTemperatureLabel.setTextColor(COLOR_WHITE_LABEL);
            averageTemperatureLabel.setText("--");
            averageLoadLabel.setText("--");
        }
        // hot aisle temperature gradient
        selectedHotAisleTemperatures = new ArrayList<>();
        List<String> rackCodes = resolveAisleRackCodes(selectedHotAisle);
        for (String rackCode : rackCodes) {
            float averageRackTemperature = 0;
            for (String column : selectedHotAisle.columns()) {
                RackLocation rackLocation = new RackLocation(column, new RackCode(rackCode));
                RackOperationalSnapshot rackSnapshot = simulationContainer.operationalSnapshot().findRack(rackLocation).orElseThrow();
                averageRackTemperature += (float) rackSnapshot.representativeTemperatureCelsius();
            }
            averageRackTemperature /= selectedHotAisle.columns().size();
            selectedHotAisleTemperatures.add(averageRackTemperature);
        }
        float fColor = map(
                selectedHotAisleTemperatures.getFirst(),
                minServerTemperatureCelsius,
                maxServerTemperatureCelsius,
                0,
                1);
        int temperatureEffectColor = Colors.lerpColor(COLOR_MIN_TEMPERATURE, COLOR_MAX_TEMPERATURE, fColor);
        uiComponentContainer.indicators().get("indSelectedAisleTemperatureEffect").setOnColor(temperatureEffectColor);
    }

    private List<String> resolveAisleRackCodes(HotAisleDefinition aisle) {
        String referenceColumn = aisle.columns().getFirst();
        return simulationContainer.datacenter()
                .getRacks()
                .stream()
                .filter(rack -> rack.getLocation().column().equals(referenceColumn))
                .map(rack -> rack.getCode().value())
                .sorted()
                .toList();
    }

    private List<String> resolveAisleCoolingZoneCodes(HotAisleDefinition aisle) {
        Set<String> aisleColumns = new HashSet<>(aisle.columns());
        return simulationContainer.coolingConfiguration()
                .zones()
                .stream()
                .filter(zone -> zone.serverLocations().stream().anyMatch(location -> aisleColumns.contains(location.column())))
                .map(CoolingZoneDefinition::code)
                .sorted()
                .toList();
    }

    private int resolveTemperatureRangeColor(float temperature) {
        if (temperature >= Float.parseFloat(PROPS.getProperty("simulation.health.temperature.alert-threshold-celsius")))
            return COLOR_MAGENTA_LABEL;
        else if (temperature >= Float.parseFloat(PROPS.getProperty("simulation.health.temperature.warning-threshold-celsius")))
            return COLOR_YELLOW_LABEL;
        else return COLOR_GREEN_LABEL;
    }

    private void resolveSelectedHotAisle() {
        selectedHotAisle = simulationContainer.hotAisleByColumn().get(selectedColumn);
        if (selectedHotAisle == null)
            throw new IllegalArgumentException("No hot aisle configured for column: " + selectedColumn);
    }

    private Map<ServerLocation, ServerEnergySnapshot> resolveEnergyByLocation() {
        return simulationContainer.energySnapshot().servers()
                .stream()
                .collect(Collectors.toUnmodifiableMap(ServerEnergySnapshot::location, Function.identity()));
    }

    private Map<ServerLocation, ServerTemperatureSnapshot> resolveTemperatureByLocation() {
        return simulationContainer.temperatureSnapshot().servers()
                .stream()
                .collect(Collectors.toMap(
                        server -> new ServerLocation(server.column(), server.rackCode(), server.slot()),
                        Function.identity()
                ));
    }

    private Map<ServerLocation, ServerHealthSnapshot> resolveHealthByLocation() {
        return simulationContainer.healthSnapshot().servers()
                .stream()
                .collect(Collectors.toMap(
                        server -> new ServerLocation(server.column(), server.rackCode(), server.slot()),
                        Function.identity()
                ));
    }

    private void updateRoomPanel() {
        updateRoomHotAisleIndicator("HA01", "indRoomHotAisleC01");
        updateRoomHotAisleIndicator("HA02", "indRoomHotAisleC02-C03");
        updateRoomHotAisleIndicator("HA03", "indRoomHotAisleC04-C05");
        updateRoomHotAisleIndicator("HA04", "indRoomHotAisleC06-C07");
        updateRoomHotAisleIndicator("HA05", "indRoomHotAisleC08");
        for (Rack rack : simulationContainer.datacenter().getRacks()) {
            RackLocation location = rack.getLocation();
            String rackIndicatorCode = "indRack" + rack.getColumn() + rack.getRow();
            Indicator rackIndicator = uiComponentContainer.indicatorsRack().get(rackIndicatorCode);
            if (rackIndicator == null) continue;
            List<Server> rackServers = simulationContainer.datacenter().getServers(location);
            RackOperationalSnapshot rackSnapshot = simulationContainer.operationalSnapshot().getRack(location);
            boolean emptyRack = !rackSnapshot.hasInstalledServers();
            boolean rackOffline = rackSnapshot.hasInstalledServers() && !rackSnapshot.hasOnlineServers();
            boolean aiRack = rackServers.stream().anyMatch(server -> server.getRole() == ServerRole.AI);
            float averageTemperature = (float) rackSnapshot.averageOnlineTemperatureCelsius();
            boolean rackHotspot = averageTemperature > maxServerTemperatureCelsius;
            int rackColor;
            if (emptyRack) rackColor = COLOR_EMPTY_RACK;
            else if (rackOffline) rackColor = COLOR_MIN_TEMPERATURE; // OFFLINE_RACK_COLOR
            else if (rackHotspot) rackColor = COLOR_HOTSPOT_RACK;
            else rackColor = calculateRackColor(averageTemperature);
            rackIndicator.setOnColor(rackColor);
            updateRackConditionIndicators(rackIndicatorCode, rackOffline, emptyRack, rackHotspot, aiRack);
        }
    }

    private int calculateRackColor(float temperature) {
        float fColor = map(temperature, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 1);
        fColor = Math.clamp(fColor, 0, 1);
        return Colors.lerpColor(COLOR_MIN_TEMPERATURE, COLOR_MAX_TEMPERATURE, fColor);
    }

    private void updateRackConditionIndicators(
            String rackIndicatorCode,
            boolean rackOffline,
            boolean emptyRack,
            boolean rackHotspot,
            boolean aiRack
    ) {
        Indicator offlineIndicator = uiComponentContainer.indicatorsRackCondition().get(rackIndicatorCode.replace("indRack", "indRackOffline"));
        Indicator emptyIndicator = uiComponentContainer.indicatorsRackCondition().get(rackIndicatorCode.replace("indRack", "indRackEmpty"));
        Indicator hotspotIndicator = uiComponentContainer.indicatorsRackCondition().get(rackIndicatorCode.replace("indRack", "indRackHotspot"));
        Indicator aiIndicator = uiComponentContainer.indicatorsRackCondition().get(rackIndicatorCode.replace("indRack", "indRackAI"));
        offlineIndicator.setOn(rackOffline);
        emptyIndicator.setOn(emptyRack);
        hotspotIndicator.setOn(rackHotspot);
        aiIndicator.setOn(aiRack);
    }

    private void updateRoomHotAisleIndicator(String hotAisleCode, String indicatorCode) {
        float averageTemperature =
                (float) simulationContainer.operationalSnapshot().findServerGroup(hotAisleCode)
                        .orElseThrow(() -> new IllegalStateException("Missing operational snapshot for aisle: " + hotAisleCode))
                        .averageOnlineTemperatureCelsius();
        float maxTemperature =
                (float) simulationContainer.operationalSnapshot().findServerGroup(hotAisleCode)
                        .orElseThrow(() -> new IllegalStateException("Missing operational snapshot for aisle: " + hotAisleCode))
                        .maximumTemperatureCelsius();
        float temperature = (maxTemperature + averageTemperature) * 0.5f;
        float f = map(temperature, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 1);
        f = Math.clamp(f, 0, 1);
        int hotAisleColor = lerpColor(COLOR_MIN_TEMPERATURE, COLOR_MAX_TEMPERATURE, f);
        int a = (int) map(temperature, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 96);
        a = Math.clamp(a, 0, 128);
        int r = Colors.red(hotAisleColor);
        int g = Colors.green(hotAisleColor);
        int b = Colors.blue(hotAisleColor);
        uiComponentContainer.indicators().get(indicatorCode).setOnColor(Colors.argb(a, r, g, b));
    }

    private void drawBackground() {
        background(COLOR_BACKGROUND);
        pushStyle();
        imageMode(CORNER);
        resourceContainer.backgroundImages().forEach(img -> image(img, 0, 0, width, height));
        popStyle();
    }

    private void drawControls() {
        uiComponentContainer.indicatorsAlert().values().forEach(Indicator::draw);
        uiComponentContainer.labels().values().forEach(Label::draw);
        uiComponentContainer.indicators().values().forEach(Indicator::draw);
        uiComponentContainer.indicatorsSelectedAisle().values().forEach(Indicator::draw);
        uiComponentContainer.indicatorsSelectedAisleMaximumTemperatureServer().values().forEach(Indicator::draw);
        uiComponentContainer.indicatorsSelectedRack().values().forEach(Indicator::draw);
        uiComponentContainer.indicatorsRack().values().forEach(Indicator::draw);
        uiComponentContainer.indicatorsNullAisle().values().forEach(Indicator::draw);
        uiComponentContainer.indicatorsAiSlot().values().forEach(Indicator::draw);
        uiComponentContainer.indicatorsRackCondition().values().forEach(Indicator::draw);
        uiComponentContainer.buttonsSelectedAisleRack().values().forEach(Button::draw);
        uiComponentContainer.buttonsColumn().values().forEach(Button::draw);
        uiComponentContainer.buttonsPlay().values().forEach(Button::draw);
        uiComponentContainer.toggles().values().forEach(Toggle::draw);
    }

    private void drawSelectedHotAisleTemperatureGradient() {
        pushStyle();
        noFill();
        strokeWeight(1);
        float y = Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.y")) * height;
        float h = Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.height")) * height;
        float totalH = y + selectedHotAisleTemperatures.size() * h;
        float x = Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.x")) * width;
        float minW = Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.min.width")) * height;
        float maxW = Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.max.width")) * height;
        float minY = y;
        float maxY = y + h;
        float fColor = map(
                selectedHotAisleTemperatures.getFirst(),
                minServerTemperatureCelsius,
                maxServerTemperatureCelsius,
                0,
                1);
        int color = Colors.lerpColor(COLOR_MIN_TEMPERATURE, COLOR_MAX_TEMPERATURE, fColor);
        for (int j = (int) minY; j < (int) maxY; j++) {
            float w = map(j, y, totalH, minW, maxW);
            stroke(color);
            line(x - w * 0.5f, j, x + w * 0.5f, j);
        }
        for (int i = 0; i < selectedHotAisleTemperatures.size() - 1; i++) {
            float temperature = selectedHotAisleTemperatures.get(i);
            float nextTemperature = selectedHotAisleTemperatures.get(i + 1);
            minY = y + (i + 1) * h;
            maxY = y + (i + 2) * h;
            color = Colors.lerpColor(
                    COLOR_MIN_TEMPERATURE,
                    COLOR_MAX_TEMPERATURE,
                    map(temperature, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 1)
            );
            int nextColor = Colors.lerpColor(
                    COLOR_MIN_TEMPERATURE,
                    COLOR_MAX_TEMPERATURE,
                    map(nextTemperature, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 1)
            );
            for (int j = (int) minY; j < (int) maxY; j++) {
                float w = map(j, y, totalH, minW, maxW);
                fColor = map(j, minY, maxY, 0, 1);
                int lineColor = Colors.lerpColor(color, nextColor, fColor);
                stroke(lineColor);
                line(x - w * 0.5f, j, x + w * 0.5f, j);
            }
        }
        strokeWeight(Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.stroke.weigth")) * height);
        stroke(COLOR_TEMPERATURE_GRADIENT_BORDER);
        line(
                Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.x.00")) * width,
                Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.y.00")) * height,
                Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.x.03")) * width,
                Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.y.03")) * height
        );
        line(
                Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.x.01")) * width,
                Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.y.01")) * height,
                Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.x.02")) * width,
                Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.y.02")) * height
        );
        popStyle();
    }

    private void drawOverlay() {
        overlayManager.getActiveOverlays().forEach(entry -> entry.getRender().run());
        pushStyle();
        imageMode(CORNER);
        image(resourceContainer.staticOverlay(), 0, 0, width, height);
        popStyle();
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
        if (keyCode == SPACE_BAR) toggleSimulation();
        else if (keyCode == PLUS) increaseSimulationSpeed();
        else if (keyCode == MINUS) decreaseSimulationSpeed();
    }

}
