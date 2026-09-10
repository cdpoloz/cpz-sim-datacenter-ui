package com.cpz.sim.datacenter.ui.main;

import com.cpz.processing.controls.controls.Control;
import com.cpz.processing.controls.controls.button.Button;
import com.cpz.processing.controls.controls.config.ControlConfigLoader;
import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.controls.toggle.Toggle;
import com.cpz.processing.controls.core.input.InputManager;
import com.cpz.processing.controls.core.input.PointerEvent;
import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.processing.controls.input.ProcessingKeyboardAdapter;
import com.cpz.sim.datacenter.cooling.*;
import com.cpz.sim.datacenter.ui.config.HotAisleConfiguration;
import com.cpz.sim.datacenter.ui.config.HotAisleConfigurationLoader;
import com.cpz.sim.datacenter.ui.config.HotAisleDefinition;
import com.cpz.sim.datacenter.ui.input.MainInputLayer;
import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.factory.CoolingConfigurationFactory;
import com.cpz.sim.datacenter.temperature.CoolingSnapshotTemperatureReferenceProvider;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.factory.TemperatureSystemOptionsFactory;
import com.cpz.sim.datacenter.factory.WorkloadFactorProviderFactory;
import com.cpz.sim.datacenter.health.HealthThreshold;
import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.health.ServerHealthOptions;
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.snapshot.*;
import com.cpz.sim.datacenter.system.*;
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.datacenter.workload.NoiseWorkloadSource;
import com.cpz.sim.datacenter.workload.ScaledWorkloadSource;
import com.cpz.sim.datacenter.workload.ServerWorkloadFactorProvider;
import com.cpz.sim.datacenter.workload.WorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.utils.color.Colors;
import com.cpz.utils.noise.FractalNoise;
import com.cpz.utils.noise.PerlinNoise;
import com.cpz.utils.time.Timer;
import processing.core.PApplet;
import processing.core.PImage;
import processing.event.MouseEvent;
import processing.opengl.PJOGL;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
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

    private Map<String, HotAisleDefinition> hotAisleByColumn;
    private InputManager inputManager;
    private OverlayManager overlayManager;
    private ProcessingKeyboardAdapter processingKeyboardAdapter;
    private Map<String, Control> controls;
    private Map<String, Indicator> indicators, indicatorsAiSlot, indicatorsAlert;
    private Map<String, Indicator> indicatorsSelectedAisleMaximumTemperatureServer, indicatorsSelectedRack, indicatorsNullAisle;
    private Map<String, Indicator> indicatorsSelectedAisle, indicatorsRack, indicatorsRackCondition;
    private Map<String, Button> buttonsSelectedAisleRack, buttonsColumn, buttonsPlay;
    private Map<String, Label> labels;
    private Map<String, Toggle> toggles;
    private List<PImage> backgroundImages;
    private PImage staticOverlay;
    private boolean showOverlay;
    private Timer simulationTimer;
    private int simulationBasePeriodMillis;
    private List<Double> simulationSpeedFactors;
    private int simulationSpeedFactorIndex;
    private boolean updateSnapshots, updateUI, syncingPlayToggle;
    private boolean syncingCoolingToggles;
    private int previousSecond, previousDay;
    private String roomName;
    private List<String> supplyToggleCodes, exhaustToggleCodes;
    private SimulationEngine engine;
    private DatacenterOperationalSnapshot operationalSnapshot;
    private DatacenterOperationalSnapshotProvider operationalSnapshotProvider;
    private EnergyConsumptionSystem energySystem;
    private EnergyConsumptionSnapshotProvider energySnapshotProvider;
    private Datacenter datacenter;
    private EnergyConsumptionSnapshot energySnapshot;
    private ServerHealthSystem healthSystem;
    private HealthSnapshotProvider healthSnapshotProvider;
    private HealthSnapshot healthSnapshot;
    private TemperatureSystem temperatureSystem;
    private TemperatureSnapshotProvider temperatureSnapshotProvider;
    private TemperatureSnapshot temperatureSnapshot;
    private String selectedColumn, selectedRack;
    private HotAisleConfiguration hotAisleConfiguration;
    private HotAisleDefinition selectedHotAisle;
    private Map<String, Rack> racks;
    private float minServerTemperatureCelsius, maxServerTemperatureCelsius; //*******
    private List<Float> selectedHotAisleTemperatures;
    private CoolingConfiguration coolingConfiguration;
    private CoolingSystem coolingSystem;
    private CoolingSnapshotCoordinator coolingSnapshotCoordinator;
    private CoolingSnapshotTemperatureReferenceProvider coolingTemperatureReferenceProvider;
    private CoolingSnapshot coolingSnapshot;
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
        // input manager
        inputManager = new InputManager();
        MainInputLayer mainInputLayer = new MainInputLayer(0);
        // overlay manager
        overlayManager = new OverlayManager();
        // controls
        Map<String, Control> controlsByCode;
        // labels
        controlsByCode = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "labels.json");
        labels = new HashMap<>();
        controlsByCode.values().stream().filter(c -> c instanceof Label).forEach(label -> labels.put(label.getCode(), (Label) label));
        // indicators
        controlsByCode = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicators.json");
        indicators = new HashMap<>();
        controlsByCode.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicators.put(ind.getCode(), (Indicator) ind));
        // AI slot indicators
        controlsByCode = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicatorsAiSlot.json");
        indicatorsAiSlot = new HashMap<>();
        controlsByCode.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicatorsAiSlot.put(ind.getCode(), (Indicator) ind));
        // alert indicators
        controlsByCode = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicatorsAlert.json");
        indicatorsAlert = new HashMap<>();
        controlsByCode.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicatorsAlert.put(ind.getCode(), (Indicator) ind));
        // overlay indicators
        controlsByCode = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicatorsNullAisle.json");
        indicatorsNullAisle = new HashMap<>();
        controlsByCode.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicatorsNullAisle.put(ind.getCode(), (Indicator) ind));
        // selected rack indicators
        controlsByCode = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicatorsSelectedRack.json");
        indicatorsSelectedRack = new HashMap<>();
        controlsByCode.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicatorsSelectedRack.put(ind.getCode(), (Indicator) ind));
        // selected aisle indicators
        controlsByCode = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicatorsSelectedAisle.json");
        indicatorsSelectedAisle = new HashMap<>();
        controlsByCode.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicatorsSelectedAisle.put(ind.getCode(), (Indicator) ind));
        // rack indicators
        controlsByCode = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicatorsRack.json");
        indicatorsRack = new HashMap<>();
        controlsByCode.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicatorsRack.put(ind.getCode(), (Indicator) ind));
        // rack condition indicators
        controlsByCode = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicatorsRackCondition.json");
        indicatorsRackCondition = new HashMap<>();
        controlsByCode.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicatorsRackCondition.put(ind.getCode(), (Indicator) ind));
        // selected aisle max-temperature server indicators
        controlsByCode = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicatorsSelectedAisleMaximumTemperatureServer.json");
        indicatorsSelectedAisleMaximumTemperatureServer = new HashMap<>();
        controlsByCode.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicatorsSelectedAisleMaximumTemperatureServer.put(ind.getCode(), (Indicator) ind));
        // selected aisle rack buttons
        controlsByCode = new ControlConfigLoader(this, overlayManager, inputManager).load("data" + File.separator + "config" + File.separator + "buttonsSelectedAisleRacks.json");
        buttonsSelectedAisleRack = new HashMap<>();
        controlsByCode.values().stream().filter(c -> c instanceof Button).forEach(btn -> buttonsSelectedAisleRack.put(btn.getCode(), (Button) btn));
        buttonsSelectedAisleRack.values().forEach(btn -> {
            mainInputLayer.addPointerTarget(btn::handlePointerEvent);
            btn.setClickListener(() -> btnClicked(btn.getCode()));
        });
        // selected aisle rack buttons
        controlsByCode = new ControlConfigLoader(this, overlayManager, inputManager).load("data" + File.separator + "config" + File.separator + "buttonsSelectedColumn.json");
        buttonsColumn = new HashMap<>();
        controlsByCode.values().stream().filter(c -> c instanceof Button).forEach(btn -> buttonsColumn.put(btn.getCode(), (Button) btn));
        buttonsColumn.values().forEach(btn -> {
            mainInputLayer.addPointerTarget(btn::handlePointerEvent);
            btn.setClickListener(() -> btnClicked(btn.getCode()));
        });
        // play buttons
        controlsByCode = new ControlConfigLoader(this, overlayManager, inputManager).load("data" + File.separator + "config" + File.separator + "buttonsPlay.json");
        buttonsPlay = new HashMap<>();
        controlsByCode.values().stream().filter(c -> c instanceof Button).forEach(btn -> buttonsPlay.put(btn.getCode(), (Button) btn));
        buttonsPlay.values().forEach(btn -> {
            mainInputLayer.addPointerTarget(btn::handlePointerEvent);
            btn.setClickListener(() -> btnClicked(btn.getCode()));
        });
        // toggles
        controlsByCode = new ControlConfigLoader(this, overlayManager, inputManager).load("data" + File.separator + "config" + File.separator + "toggles.json");
        toggles = new HashMap<>();
        controlsByCode.values().stream().filter(c -> c instanceof Toggle).forEach(tgl -> toggles.put(tgl.getCode(), (Toggle) tgl));
        toggles.values().forEach(tgl -> {
            mainInputLayer.addPointerTarget(tgl::handlePointerEvent);
            tgl.setChangeListener(state -> tglClicked(tgl, state));
        });
        // input layer registration
        inputManager.registerLayer(mainInputLayer);
        //inputManager.registerLayer(new TooltipInputLayer(1000, tooltips));
        // font
        textFont(createFont("data" + File.separator + "font" + File.separator + "JetBrainsMono.ttf", 96, true));
        // background images
        backgroundImages = new ArrayList<>();
        backgroundImages.add(loadImage("data" + File.separator + "img" + File.separator + "ui_background.png"));
        backgroundImages.add(loadImage("data" + File.separator + "img" + File.separator + "ui_backgroundSelectedAisle.png"));
        backgroundImages.add(loadImage("data" + File.separator + "img" + File.separator + "ui_backgroundSelectedRack.png"));
        backgroundImages.add(loadImage("data" + File.separator + "img" + File.separator + "ui_backgroundRoom.png"));
        backgroundImages.add(loadImage("data" + File.separator + "img" + File.separator + "ui_backgroundHeader.png"));
        backgroundImages.add(loadImage("data" + File.separator + "img" + File.separator + "ui_backgroundFooter.png"));
        // static overlay
        staticOverlay = loadImage("data" + File.separator + "img" + File.separator + "ui_overlay.png");
        // datacenter
        Path configPath = Path.of("data/config/datacenter-test-complete-rezoned-edge-cases-custom-v2.json");
        DatacenterDefinition definition = new JsonDatacenterConfigLoader().load(configPath);
        datacenter = new DatacenterFactory().create(definition);
        roomName = definition.layout().room().name();
        hotAisleByColumn = new HashMap<>();
        coolingConfiguration =
                new CoolingConfigurationFactory()
                        .create(definition, datacenter)
                        .orElseThrow(() -> new IllegalStateException("La configuración del datacenter no contiene el bloque cooling"));
        racks = new HashMap<>();
        for (Rack r : datacenter.getRacks()) racks.put(r.getCode().value(), r);
        // workloads
        PerlinNoise perlinNoise = new PerlinNoise(1234L);
        FractalNoise fractalNoise = new FractalNoise(
                perlinNoise,
                5,
                1.0f,
                2.0f,
                0.5f
        );
        WorkloadSource baseWorkloadSource = new NoiseWorkloadSource(
                fractalNoise,
                0.001,
                0.2f,
                0.9f
        );
        ServerWorkloadFactorProvider factorProvider = new WorkloadFactorProviderFactory().create(definition);
        WorkloadSource workloadSource = new ScaledWorkloadSource(baseWorkloadSource, factorProvider);
        SimulationClock clock = new SimulationClock(Duration.ofMinutes(1));
        // engine
        engine = new SimulationEngine(clock);
        // systems
        energySystem = new EnergyConsumptionSystem(datacenter);
        coolingSystem = new CoolingSystem(coolingConfiguration);
        coolingTemperatureReferenceProvider = new CoolingSnapshotTemperatureReferenceProvider(coolingConfiguration);
        coolingSnapshotCoordinator = new CoolingSnapshotCoordinator(new DatacenterCoolingTickInputProvider(datacenter), coolingSystem, coolingTemperatureReferenceProvider);
        TemperatureSystemOptions temperatureOptions = new TemperatureSystemOptionsFactory().create(definition);
        temperatureSystem = new TemperatureSystem(datacenter, temperatureOptions, new SimpleServerTemperatureModel(), coolingTemperatureReferenceProvider);
        HealthThreshold utilizationThreshold = new HealthThreshold(
                Double.parseDouble(PROPS.getProperty("simulation.health.utilization.alert-threshold")),
                Double.parseDouble(PROPS.getProperty("simulation.health.utilization.recovery-threshold"))
        );
        HealthThreshold temperatureThreshold = new HealthThreshold(
                Double.parseDouble(PROPS.getProperty("simulation.health.temperature.alert-threshold-celsius")),
                Double.parseDouble(PROPS.getProperty("simulation.health.temperature.recovery-threshold-celsius"))
        );
        healthSystem = new ServerHealthSystem(datacenter, temperatureSystem, new ServerHealthOptions(utilizationThreshold, temperatureThreshold));
        engine.register(new WorkloadSystem(datacenter, workloadSource));
        engine.register(new PowerConsumptionSystem(datacenter));
        engine.register(tick -> coolingSnapshot = coolingSnapshotCoordinator.update(tick));
        engine.register(temperatureSystem);
        engine.register(healthSystem);
        engine.register(energySystem);
        // snapshots
        energySnapshotProvider = new EnergyConsumptionSnapshotProvider(datacenter, energySystem);
        temperatureSnapshotProvider = new TemperatureSnapshotProvider(datacenter, temperatureSystem, temperatureOptions);
        healthSnapshotProvider = new HealthSnapshotProvider(datacenter, healthSystem, temperatureSystem);
        // hot aisles
        Path configurationPath = Path.of(dataPath("config" + File.separator + "hot-aisle-mapping.json"));
        HotAisleConfigurationLoader loader = new HotAisleConfigurationLoader();
        try {
            hotAisleConfiguration = loader.load(configurationPath);
            initializeHotAisleMapping(hotAisleConfiguration);
        } catch (IOException e) {
            throw new RuntimeException("Could not load hot aisle configuration", e);
        }
        // operational groups
        List<ServerGroupDefinition> operationalGroups = createOperationalGroups(hotAisleConfiguration);
        operationalSnapshotProvider = new DatacenterOperationalSnapshotProvider(datacenter, operationalGroups);
        // simulation timer
        simulationBasePeriodMillis = Integer.parseInt(PROPS.getProperty("simulation.timer.base-period-ms"));
        simulationSpeedFactors = parseSimulationSpeedFactors(PROPS.getProperty("simulation.timer.speed-factors"));
        simulationSpeedFactorIndex = findSimulationSpeedFactorIndex(Double.parseDouble(PROPS.getProperty("simulation.timer.initial-speed-factor")));
        simulationTimer = new Timer();
        simulationTimer.setPeriodMillis(1000);
        updateSimulationTimerPeriod();
        updateSimulationControls();
        // initial values
        selectedColumn = "C01";
        selectedRack = "R01";
        resolveSelectedHotAisle();
        showSelectedRackHighlight();
        showSelectedAisleHighlight();
        calculateTemperatureRange(datacenter, temperatureOptions);
        engine.step();
        updateUI = true;
        updateSnapshots = true;
        updateSnapshots();
        supplyToggleCodes = new ArrayList<>();
        coolingSnapshot.units()
                .stream()
                .filter(unit -> unit.type() == CoolingUnitType.SUPPLY)
                .forEach(unit -> {
                    String tglCode = unit.unitCode().replace("SUPPLY-", "tglSupply");
                    supplyToggleCodes.add(tglCode);
                });
        exhaustToggleCodes = new ArrayList<>();
        coolingSnapshot.units()
                .stream()
                .filter(unit -> unit.type() == CoolingUnitType.EXHAUST)
                .forEach(unit -> {
                    String tglCode = unit.unitCode().replace("EXHAUST-", "tglExhaust");
                    exhaustToggleCodes.add(tglCode);
                });
        percentageFormat = PROPS.getProperty("number.format.percentage");
        temperatureFormat = PROPS.getProperty("number.format.temperature");
        simpleTemperatureFormat = PROPS.getProperty("number.format.temperature.simple");
        powerKwFormat = PROPS.getProperty("number.format.power.kw");
        powerMwFormat = PROPS.getProperty("number.format.power.mw");
        speedFormat = PROPS.getProperty("number.format.speed");
        pressureFormat = PROPS.getProperty("number.format.pressure");
        airflowFormat = PROPS.getProperty("number.format.airflow");
        labels.get("lblRoomTemperatureScale01").setText(String.format(simpleTemperatureFormat, minServerTemperatureCelsius));
        for (int i = 0; i < 5; i++) {
            float temperature = map(i, 0, 5, minServerTemperatureCelsius, maxServerTemperatureCelsius);
            String temperatureScaleLabelCode = "lblRoomTemperatureScale0" + (i + 1);
            labels.get(temperatureScaleLabelCode).setText(String.format(simpleTemperatureFormat, temperature));
        }
        labels.get("lblRoomTemperatureScale06").setText(String.format(simpleTemperatureFormat, maxServerTemperatureCelsius));
        int totalInstalledServers = operationalSnapshot.racks()
                .values()
                .stream()
                .mapToInt(RackOperationalSnapshot::installedServerCount)
                .sum();
        int totalOnlineServers = operationalSnapshot.racks()
                .values()
                .stream()
                .mapToInt(RackOperationalSnapshot::onlineServerCount)
                .sum();
        labels.get("lblRoomTotalServersValue").setText(String.valueOf(totalInstalledServers));
        labels.get("lblRoomOnlineServersValue").setText(String.valueOf(totalOnlineServers));
        labels.get("lblDate").setText(String.format("%02d", day()) + "/" + String.format("%02d", month()) + "/" + year());
        labels.get("lblTime").setText(String.format("%02d", hour()) + ":" + String.format("%02d", minute()) + ":" + String.format("%02d", second()));
        // debug
        showOverlay = true;
    }

    private List<Double> parseSimulationSpeedFactors(String rawFactors) {
        if (rawFactors == null || rawFactors.isBlank()) throw new IllegalArgumentException("simulation.timer.speed-factors must not be empty");
        List<Double> factors = Arrays.stream(rawFactors.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Double::parseDouble)
                .toList();
        if (factors.isEmpty()) throw new IllegalArgumentException("simulation.timer.speed-factors must contain at least one value");
        if (factors.stream().anyMatch(factor -> factor <= 0.0)) throw new IllegalArgumentException("simulation.timer.speed-factors must contain only positive values");
        return factors;
    }

    private int findSimulationSpeedFactorIndex(double initialFactor) {
        for (int i = 0; i < simulationSpeedFactors.size(); i++) {
            if (Double.compare(simulationSpeedFactors.get(i), initialFactor) == 0) return i;
        }
        throw new IllegalArgumentException("simulation.timer.initial-speed-factor must exist in simulation.timer.speed-factors");
    }

    private void updateSimulationTimerPeriod() {
        double factor = simulationSpeedFactors.get(simulationSpeedFactorIndex);
        int periodMillis = (int) Math.round(simulationBasePeriodMillis / factor);
        simulationTimer.setPeriodMillis(periodMillis);
    }

    private void increaseSimulationSpeed() {
        if (simulationSpeedFactorIndex >= simulationSpeedFactors.size() - 1) return;
        simulationSpeedFactorIndex++;
        updateSimulationTimerPeriod();
        updateSimulationControls();
    }

    private void decreaseSimulationSpeed() {
        if (simulationSpeedFactorIndex <= 0) return;
        simulationSpeedFactorIndex--;
        updateSimulationTimerPeriod();
        updateSimulationControls();
    }

    private void updateSimulationControls() {
        boolean simulationRunning = simulationTimer.isRunning();
        String speedLabel = formatSimulationSpeedFactor();
        labels.get("lblSimulationValue").setText(simulationRunning ? "Running " + speedLabel : "Stopped " + speedLabel);
        Button btnPlayMinus = buttonsPlay.get("btnPlayMinus");
        if (btnPlayMinus != null) btnPlayMinus.setEnabled(simulationSpeedFactorIndex > 0);
        Button btnPlayPlus = buttonsPlay.get("btnPlayPlus");
        if (btnPlayPlus != null) btnPlayPlus.setEnabled(simulationSpeedFactorIndex < simulationSpeedFactors.size() - 1);
    }

    private String formatSimulationSpeedFactor() {
        double factor = simulationSpeedFactors.get(simulationSpeedFactorIndex);
        if (factor == Math.rint(factor)) return "x" + (int) factor;
        return "x" + factor;
    }

    private void updateHeader() {
        // single-room layout for now; refresh here when room switching is added
        String s = "DATACENTER MAP";
        if (roomName != null && !roomName.isEmpty()) s += (" - " + roomName);
        labels.get("lblRoom").setText(s);
        updateDateTime();
    }

    private void updateDateTime() {
        if (second() == previousSecond) return;
        previousSecond = second();
        labels.get("lblTime").setText(String.format("%02d", hour()) + ":" + String.format("%02d", minute()) + ":" + String.format("%02d", second()));
        if (day() == previousDay) return;
        previousDay = day();
        labels.get("lblDate").setText(String.format("%02d", day()) + "/" + String.format("%02d", month()) + "/" + year());
    }

    private List<ServerGroupDefinition> createOperationalGroups(HotAisleConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        return configuration
                .hotAisles()
                .stream()
                .map(hotAisle -> {
                    Set<ServerLocation> serverLocations = datacenter
                            .getServers()
                            .stream()
                            .map(Server::getLocation)
                            .filter(location -> hotAisle.columns().contains(location.column()))
                            .collect(Collectors.toUnmodifiableSet()
                            );
                    return new ServerGroupDefinition(hotAisle.code(), serverLocations);
                })
                .toList();
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

    private void tglClicked(Toggle tgl, int state) {
        String toggleCode = tgl.getCode();
        if (toggleCode.startsWith("tglSupply") || toggleCode.startsWith("tglExhaust")) coolingToggleClicked(tgl, state);
        else if (toggleCode.equals("tglPlay")) {
            if (syncingPlayToggle) return;
            toggleSimulation();
        }
    }

    private void toggleSimulation() {
        simulationTimer.toggle();
        syncingPlayToggle = true;
        try {
            toggles.get("tglPlay").setState(simulationTimer.isRunning() ? 1 : 0);
        } finally {
            syncingPlayToggle = false;
        }
        updateSimulationControls();
    }

    private void coolingToggleClicked(Toggle tgl, int state) {
        if (syncingCoolingToggles) return;
        String toggleCode = tgl.getCode();
        boolean enabled = state == 1;
        if (toggleCode.equals("tglSupply")) {
            updateChildCoolingToggles(supplyToggleCodes, enabled);
            return;
        }
        if (toggleCode.equals("tglExhaust")) {
            updateChildCoolingToggles(exhaustToggleCodes, enabled);
            return;
        }
        if (supplyToggleCodes.contains(toggleCode)) {
            updateCoolingUnit(tgl, enabled);
            updateMasterToggle("tglSupply", supplyToggleCodes);
            return;
        }
        if (exhaustToggleCodes.contains(toggleCode)) {
            updateCoolingUnit(tgl, enabled);
            updateMasterToggle("tglExhaust", exhaustToggleCodes);
        }
    }

    private void updateChildCoolingToggles(List<String> toggleCodes, boolean enabled) {
        syncingCoolingToggles = true;
        try {
            for (String toggleCode : toggleCodes) {
                Toggle toggle = toggles.get(toggleCode);
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
                .map(toggles::get)
                .anyMatch(toggle -> toggle != null && toggle.getState() == 1);
        syncingCoolingToggles = true;
        try {
            Toggle masterToggle = toggles.get(masterToggleCode);
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
        coolingSystem.setEnabled(unitCode, enabled);
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
        for (Button btn : buttonsSelectedAisleRack.values()) {
            if (selectedColumn.equals(PROPS.getProperty("datacenter.first.column")) && btn.getCode().toLowerCase().contains("left"))
                btn.setVisible(false);
            else if (selectedColumn.equals(PROPS.getProperty("datacenter.last.column")) && btn.getCode().toLowerCase().contains("right"))
                btn.setVisible(false);
            else btn.setVisible(!btn.getCode().equals(selectedButtonCode));
        }
        for (Indicator ind : indicatorsSelectedRack.values()) ind.setOn(ind.getCode().equals(selectedRackIndicatorCode));
    }

    private void showSelectedAisleHighlight() {
        indicatorsSelectedAisle.values().forEach(ind -> ind.setOn(false));
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
        indicatorsSelectedAisle.get(selectedAisleIndicatorCode).setOn(true);
    }

    private void initializeHotAisleMapping(HotAisleConfiguration configuration) {
        hotAisleByColumn.clear();
        for (HotAisleDefinition hotAisle : configuration.hotAisles()) {
            for (String column : hotAisle.columns()) {
                HotAisleDefinition previous = hotAisleByColumn.put(column, hotAisle);
                if (previous != null)
                    throw new IllegalArgumentException("Column '%s' is assigned to hot aisles '%s' and '%s'".formatted(column, previous.code(), hotAisle.code()));
            }
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
        drawBackground();
        drawControls();
        drawSelectedHotAisleTemperatureGradient();
        drawOverlay();
    }

    private void updateClock() {
        if (simulationTimer == null || engine == null) return;
        if (!simulationTimer.pollPeriodPulse()) return;
        engine.step();
        updateSnapshots = true;
    }

    private void updateSnapshots() {
        if (!updateSnapshots) return;
        energySnapshot = energySnapshotProvider.snapshot(engine.currentTick());
        temperatureSnapshot = temperatureSnapshotProvider.snapshot(engine.currentTick());
        healthSnapshot = healthSnapshotProvider.snapshot(engine.currentTick());
        operationalSnapshot = operationalSnapshotProvider.snapshot(energySnapshot, temperatureSnapshot, healthSnapshot);
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
        labels.get("lblSelectedRackValue").setText(selectedColumn + "-" + selectedRack);
        Rack rack = resolveSelectedRack();
        RackLocation rackLocation = new RackLocation(selectedColumn, new RackCode(selectedRack));
        RackOperationalSnapshot rackSnapshot = operationalSnapshot
                .findRack(rackLocation)
                .orElseThrow(() -> new IllegalStateException("Missing operational snapshot for rack: " + rackLocation.code()
                ));
        Map<ServerLocation, ServerEnergySnapshot> energyByLocation = resolveEnergyByLocation();
        Map<ServerLocation, ServerTemperatureSnapshot> temperatureByLocation = resolveTemperatureByLocation();
        Map<ServerLocation, ServerHealthSnapshot> healthByLocation = resolveHealthByLocation();
        for (String slot : rack.getSlotCodes()) {
            String slotNumber = slot.replace("S", "");
            ServerLocation location = new ServerLocation(selectedColumn, new RackCode(selectedRack), slot);
            Optional<Server> installedServer = datacenter.getServer(location);
            Label slotTemperatureLabel = labels.get("lblSlotTemperature" + slotNumber);
            Label slotLoadLabel = labels.get("lblSlotLoad" + slotNumber);
            Label slotPowerLabel = labels.get("lblSlotPower" + slotNumber);
            Indicator indSlot = indicators.get("indSlot" + slotNumber);
            Indicator emptySlotIndicator = indicators.get("indSlotEmpty" + slotNumber);
            Indicator indSlotOffline = indicators.get("indSlotOffline" + slotNumber);
            Indicator okSlotStatusIndicator = indicators.get("indSlotOk" + slotNumber);
            Indicator alertSlotStatusIndicator = indicators.get("indSlotAlert" + slotNumber);
            Indicator alertIndicator = indicatorsAlert.get("indAlert" + slotNumber);
            Indicator aiSlotIndicator1 = indicatorsAiSlot.get("indSlotAI" + slotNumber + "-1");
            Indicator aiSlotIndicator2 = indicators.get("indSlotAI" + slotNumber + "-2");
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
            if (temperature == null) throw new IllegalStateException("Missing temperature snapshot for server: " + location);
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
        Label averageTemperatureLabel = labels.get("lblRackAverageTemperatureValue");
        Label averageLoadLabel = labels.get("lblRackAverageLoadValue");
        if (rackSnapshot.hasOnlineServers()) {
            averageTemperatureLabel.setTextColor(COLOR_YELLOW_LABEL);
            averageTemperatureLabel.setText(String.format(temperatureFormat, rackSnapshot.averageOnlineTemperatureCelsius()));
            averageLoadLabel.setText(String.format(percentageFormat, rackSnapshot.averageOnlineUtilization() * 100));
        } else {
            averageTemperatureLabel.setTextColor(COLOR_WHITE_LABEL);
            averageTemperatureLabel.setText("--");
            averageLoadLabel.setText("--");
        }
        labels.get("lblRackCurrentPowerValue").setText(String.format(powerKwFormat, rackSnapshot.currentPowerWatts() / 1000));
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
        for (int i = 0; i < 6; i++) indicators.get(key + (i + 1)).setOn(i < iMax);
    }

    private Rack resolveSelectedRack() {
        return datacenter.findRack(selectedColumn, selectedRack).orElseThrow(() -> new IllegalStateException("Rack not found: " + selectedColumn + "-" + selectedRack));
    }

    private void updateSelectedAislePanel() {
        boolean leftEdgeAisleSelected = selectedColumn.equals(PROPS.getProperty("datacenter.first.column"));
        boolean rightEdgeAisleSelected = selectedColumn.equals(PROPS.getProperty("datacenter.last.column"));
        indicatorsNullAisle.get("indNullAisleLeft").setOn(leftEdgeAisleSelected);
        indicators.get("indColdAirArrowsLeft").setOn(!leftEdgeAisleSelected);
        indicatorsNullAisle.get("indNullAisleRight").setOn(rightEdgeAisleSelected);
        indicators.get("indColdAirArrowsRight").setOn(!rightEdgeAisleSelected);
        resolveSelectedHotAisle();
        labels.get("lblSelectedAisleValue").setText(selectedHotAisle.displayName());
        // additional data
        List<String> aisleZoneCodes = resolveAisleCoolingZoneCodes(selectedHotAisle);
        CoolingZoneGroupSnapshot coolingGroupSnapshot = coolingSnapshot.aggregateZones(selectedHotAisle.code(), aisleZoneCodes);
        double thermalCoverage = coolingGroupSnapshot.thermalCoverage();
        labels.get("lblSelectedAisleThermalCoverageValue").setText(String.format(percentageFormat, thermalCoverage * 100.0));
        double deltaTinOut = coolingGroupSnapshot.airTemperatureRiseCelsius();
        labels.get("lblSelectedAisleTemperatureDeltaValue").setText(String.format(temperatureFormat, deltaTinOut));
        double recirculation = coolingGroupSnapshot.averageRecirculationFraction();
        labels.get("lblSelectedAisleRecirculationValue").setText(String.format(percentageFormat, recirculation * 100.0));
        double supplyAirflow = aisleZoneCodes.stream()
                .map(coolingSnapshot::findZone)
                .flatMap(Optional::stream)
                .mapToDouble(CoolingZoneSnapshot::supplyAirflowCubicMetersPerSecond)
                .sum();
        double exhaustAirflow =
                aisleZoneCodes
                        .stream()
                        .map(coolingSnapshot::findZone)
                        .flatMap(Optional::stream)
                        .mapToDouble(CoolingZoneSnapshot::exhaustAirflowCubicMetersPerSecond)
                        .sum();
        labels.get("lblSelectedAisleAirflowValue").setText(String.format(airflowFormat, supplyAirflow, exhaustAirflow));
        // installed servers
        ServerGroupOperationalSnapshot aisleSnapshot = operationalSnapshot
                .findServerGroup(selectedHotAisle.code())
                .orElseThrow(() -> new IllegalStateException("Missing operational snapshot for aisle: " + selectedHotAisle.code()));
        Label averageTemperatureLabel = labels.get("lblSelectedAisleAverageTemperatureValue");
        Label maxTemperatureLabel = labels.get("lblSelectedAisleMaximumTemperatureValue");
        Label averageLoadLabel = labels.get("lblSelectedAisleITLoadValue");
        indicatorsSelectedAisleMaximumTemperatureServer.values().forEach(ind -> ind.setOn(false));
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
                                        + "pero no informa la ubicación "
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
        Indicator maxTemperatureIndicator = indicatorsSelectedAisleMaximumTemperatureServer.get(indicatorCode);
        if (maxTemperatureIndicator == null) throw new IllegalStateException("Missing maximum temperature indicator: " + indicatorCode);
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
                RackOperationalSnapshot rackSnapshot = operationalSnapshot.findRack(rackLocation).orElseThrow();
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
        indicators.get("indSelectedAisleTemperatureEffect").setOnColor(temperatureEffectColor);
    }

    private List<String> resolveAisleRackCodes(HotAisleDefinition aisle) {
        String referenceColumn = aisle.columns().getFirst();
        return datacenter
                .getRacks()
                .stream()
                .filter(rack -> rack.getLocation().column().equals(referenceColumn))
                .map(rack -> rack.getCode().value())
                .sorted()
                .toList();
    }

    private List<String> resolveAisleCoolingZoneCodes(HotAisleDefinition aisle) {
        Set<String> aisleColumns = new HashSet<>(aisle.columns());
        return coolingConfiguration
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
        selectedHotAisle = hotAisleByColumn.get(selectedColumn);
        if (selectedHotAisle == null) throw new IllegalArgumentException("No hot aisle configured for column: " + selectedColumn);
    }

    private Map<ServerLocation, ServerEnergySnapshot> resolveEnergyByLocation() {
        return energySnapshot.servers()
                .stream()
                .collect(Collectors.toUnmodifiableMap(ServerEnergySnapshot::location, Function.identity()));
    }

    private Map<ServerLocation, ServerTemperatureSnapshot> resolveTemperatureByLocation() {
        return temperatureSnapshot.servers()
                .stream()
                .collect(Collectors.toMap(
                        server -> new ServerLocation(server.column(), server.rackCode(), server.slot()),
                        Function.identity()
                ));
    }

    private Map<ServerLocation, ServerHealthSnapshot> resolveHealthByLocation() {
        return healthSnapshot.servers()
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
        for (Rack rack : datacenter.getRacks()) {
            RackLocation location = rack.getLocation();
            String rackIndicatorCode = "indRack" + rack.getColumn() + rack.getRow();
            Indicator rackIndicator = indicatorsRack.get(rackIndicatorCode);
            if (rackIndicator == null) continue;
            List<Server> rackServers = datacenter.getServers(location);
            RackOperationalSnapshot rackSnapshot = operationalSnapshot.getRack(location);
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
        Indicator offlineIndicator = indicatorsRackCondition.get(rackIndicatorCode.replace("indRack", "indRackOffline"));
        Indicator emptyIndicator = indicatorsRackCondition.get(rackIndicatorCode.replace("indRack", "indRackEmpty"));
        Indicator hotspotIndicator = indicatorsRackCondition.get(rackIndicatorCode.replace("indRack", "indRackHotspot"));
        Indicator aiIndicator = indicatorsRackCondition.get(rackIndicatorCode.replace("indRack", "indRackAI"));
        offlineIndicator.setOn(rackOffline);
        emptyIndicator.setOn(emptyRack);
        hotspotIndicator.setOn(rackHotspot);
        aiIndicator.setOn(aiRack);
    }

    private void updateRoomHotAisleIndicator(String hotAisleCode, String indicatorCode) {
        float averageTemperature =
                (float) operationalSnapshot.findServerGroup(hotAisleCode)
                        .orElseThrow(() -> new IllegalStateException("Missing operational snapshot for aisle: " + hotAisleCode))
                        .averageOnlineTemperatureCelsius();
        float maxTemperature =
                (float) operationalSnapshot.findServerGroup(hotAisleCode)
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
        indicators.get(indicatorCode).setOnColor(Colors.argb(a, r, g, b));
    }

    private void drawBackground() {
        background(COLOR_BACKGROUND);
        pushStyle();
        imageMode(CORNER);
        backgroundImages.forEach(img -> image(img, 0, 0, width, height));
        popStyle();
    }

    private void drawControls() {
        indicatorsAlert.values().forEach(Indicator::draw);
        labels.values().forEach(Label::draw);
        indicators.values().forEach(Indicator::draw);
        indicatorsSelectedAisle.values().forEach(Indicator::draw);
        indicatorsSelectedAisleMaximumTemperatureServer.values().forEach(Indicator::draw);
        indicatorsSelectedRack.values().forEach(Indicator::draw);
        indicatorsRack.values().forEach(Indicator::draw);
        indicatorsNullAisle.values().forEach(Indicator::draw);
        indicatorsAiSlot.values().forEach(Indicator::draw);
        indicatorsRackCondition.values().forEach(Indicator::draw);
        buttonsSelectedAisleRack.values().forEach(Button::draw);
        buttonsColumn.values().forEach(Button::draw);
        buttonsPlay.values().forEach(Button::draw);
        toggles.values().forEach(Toggle::draw);
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
        if (!showOverlay) return;
        pushStyle();
        imageMode(CORNER);
        image(staticOverlay, 0, 0, width, height);
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
