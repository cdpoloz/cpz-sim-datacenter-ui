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
import com.cpz.sim.datacenter.cooling.CoolingZoneDefinition;
import com.cpz.sim.datacenter.ui.config.HotAisleConfiguration;
import com.cpz.sim.datacenter.ui.config.HotAisleConfigurationLoader;
import com.cpz.sim.datacenter.ui.config.HotAisleDefinition;
import com.cpz.sim.datacenter.ui.input.MainInputLayer;
import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.factory.CoolingConfigurationFactory;
import com.cpz.sim.datacenter.cooling.CoolingSnapshotCoordinator;
import com.cpz.sim.datacenter.cooling.DatacenterCoolingTickInputProvider;
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
import static com.cpz.sim.datacenter.ui.util.Constantes.*;

/**
 * @author CPZ
 */
public class Sketch extends PApplet {

    private final Map<String, HotAisleDefinition> hotAisleByColumn = new HashMap<>();
    private InputManager inputManager;
    private OverlayManager overlayManager;
    private ProcessingKeyboardAdapter processingKeyboardAdapter;
    private Map<String, Control> controls;
    private Map<String, Indicator> indicadores, indicadoresSlotIA, indicadoresAlerta;
    private Map<String, Indicator> indicadoresPasilloElegidoServidorTemperaturaMaxima, indicadoresRackElegido, indicadoresPasilloNull;
    private Map<String, Indicator> indicadoresPasilloElegido, indicadoresRack, indicadoresRackCondicion;
    private Map<String, Button> botonesPasilloElegidoRacks, botonesColumnas;
    private Map<String, Label> labels;
    private Map<String, Toggle> toggles;
    private PImage fondo, overlayEstatico, fondoPasilloElegido, fondoRackElegido, fondoSala;
    private boolean showOverlay;
    private Timer timerSimulacion;
    private boolean updateSnapshots, updateUI;
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
    private String columnaElegida, rackElegido;
    private HotAisleConfiguration hotAisleConfiguration;
    private HotAisleDefinition pasilloCalienteSeleccionado;
    private Map<String, Rack> racks;
    private float minServerTemperatureCelsius, maxServerTemperatureCelsius; //*******
    private List<Float> temperaturasPasilloCalienteSeleccionado;
    private CoolingConfiguration coolingConfiguration;
    private CoolingSystem coolingSystem;
    private CoolingSnapshotCoordinator coolingSnapshotCoordinator;
    private CoolingSnapshotTemperatureReferenceProvider coolingTemperatureReferenceProvider;
    private CoolingSnapshot coolingSnapshot;
    private String formatoTemperatura, formatoTemperaturaSimple, formatoPorcentaje, formatoPotenciaKw, formatoPotenciaMw, formatoVelocidad, formatoPresion, formatoFlujoAire;

    public void settings() {
        LOG.info("Starting settings");
        PJOGL.setIcon("data" + File.separator + "img" + File.separator + PROPS.getProperty("window.icon"));
        // tamaño de ventana
        size(Integer.parseInt(PROPS.getProperty("sketch.width")), Integer.parseInt(PROPS.getProperty("sketch.height")), P2D);
        // smoothing
        smooth(Integer.parseInt(PROPS.getProperty("sketch.smoothing")));
        LOG.info("Finished settings");
    }

    public void setup() {
        LOG.info("Starting initial setup");
        background(COLOR_FONDO);
        frameRate(Integer.parseInt(PROPS.getProperty("sketch.fps")));
        getSurface().setTitle(PROPS.getProperty("window.title"));
        LOG.info("Finished initial setup");
        // input manager
        inputManager = new InputManager();
        MainInputLayer mainInputLayer = new MainInputLayer(0);
        // overlay manager
        overlayManager = new OverlayManager();
        // controles
        Map<String, Control> controles;
        // labels
        controles = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "label.json");
        labels = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Label).forEach(lbl -> labels.put(lbl.getCode(), (Label) lbl));
        // indicadores
        controles = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicadores.json");
        indicadores = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicadores.put(ind.getCode(), (Indicator) ind));
        // indicadoresSlotIA
        controles = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicadoresSlotIA.json");
        indicadoresSlotIA = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicadoresSlotIA.put(ind.getCode(), (Indicator) ind));
        // indicadoresAlerta
        controles = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicadoresAlerta.json");
        indicadoresAlerta = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicadoresAlerta.put(ind.getCode(), (Indicator) ind));
        // indicadoresOverlay
        controles = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicadoresPasilloNull.json");
        indicadoresPasilloNull = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicadoresPasilloNull.put(ind.getCode(), (Indicator) ind));
        // indicadoresRackElegido
        controles = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicadoresRackElegido.json");
        indicadoresRackElegido = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicadoresRackElegido.put(ind.getCode(), (Indicator) ind));
        // indicadoresPasilloElegido
        controles = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicadoresPasilloElegido.json");
        indicadoresPasilloElegido = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicadoresPasilloElegido.put(ind.getCode(), (Indicator) ind));
        // indicadoresRack
        controles = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicadoresRack.json");
        indicadoresRack = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicadoresRack.put(ind.getCode(), (Indicator) ind));
        // indicadoresRackCondicion
        controles = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicadoresRackCondicion.json");
        indicadoresRackCondicion = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicadoresRackCondicion.put(ind.getCode(), (Indicator) ind));
        // indicadoresPasilloElegidoServidorTemperaturaMaxima
        controles = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicadoresPasilloElegidoServidorTemperaturaMaxima.json");
        indicadoresPasilloElegidoServidorTemperaturaMaxima = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicadoresPasilloElegidoServidorTemperaturaMaxima.put(ind.getCode(), (Indicator) ind));
        // botonesPasilloElegidoRacks
        controles = new ControlConfigLoader(this, overlayManager, inputManager).load("data" + File.separator + "config" + File.separator + "botonesPasilloElegidoRacks.json");
        botonesPasilloElegidoRacks = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Button).forEach(btn -> botonesPasilloElegidoRacks.put(btn.getCode(), (Button) btn));
        botonesPasilloElegidoRacks.values().forEach(btn -> {
            mainInputLayer.addPointerTarget(btn::handlePointerEvent);
            btn.setClickListener(() -> btnClicked(btn.getCode()));
        });
        // botonesPasilloElegidoRacks
        controles = new ControlConfigLoader(this, overlayManager, inputManager).load("data" + File.separator + "config" + File.separator + "botonesColumnaElegida.json");
        botonesColumnas = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Button).forEach(btn -> botonesColumnas.put(btn.getCode(), (Button) btn));
        botonesColumnas.values().forEach(btn -> {
            mainInputLayer.addPointerTarget(btn::handlePointerEvent);
            btn.setClickListener(() -> btnClicked(btn.getCode()));
        });
        // toggles
        controles = new ControlConfigLoader(this, overlayManager, inputManager).load("data" + File.separator + "config" + File.separator + "toggle.json");
        toggles = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Toggle).forEach(tgl -> toggles.put(tgl.getCode(), (Toggle) tgl));
        toggles.values().forEach(tgl -> {
            mainInputLayer.addPointerTarget(tgl::handlePointerEvent);
            tgl.setChangeListener(estado -> tglClicked(tgl.getCode(), estado));
        });
        // registro de capas en inputLayer
        inputManager.registerLayer(mainInputLayer);
        //inputManager.registerLayer(new TooltipInputLayer(1000, tooltips));
        // font
        textFont(createFont("data" + File.separator + "font" + File.separator + "JetBrainsMono.ttf", 96, true));
        // imágenes
        fondo = loadImage("data" + File.separator + "img" + File.separator + "ui_fondo.png");
        fondoPasilloElegido = loadImage("data" + File.separator + "img" + File.separator + "ui_fondoPasilloElegido.png");
        fondoRackElegido = loadImage("data" + File.separator + "img" + File.separator + "ui_fondoRackElegido.png");
        fondoSala = loadImage("data" + File.separator + "img" + File.separator + "ui_fondoSala.png");
        overlayEstatico = loadImage("data" + File.separator + "img" + File.separator + "ui_overlay.png");
        // datacenter
        Path configPath = Path.of("data/config/datacenter-test-complete-rezoned-edge-cases-custom-v2.json");
        DatacenterDefinition definition = new JsonDatacenterConfigLoader().load(configPath);
        datacenter = new DatacenterFactory().create(definition);
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
        // sistemas
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
        // pasillos calientes
        Path configurationPath = Path.of(dataPath("config" + File.separator + "hot-aisle-mapping.json"));
        HotAisleConfigurationLoader loader = new HotAisleConfigurationLoader();
        try {
            hotAisleConfiguration = loader.load(configurationPath);
            initializeHotAisleMapping(hotAisleConfiguration);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo cargar la configuración de pasillos calientes", e);
        }
        // grupos operacionales
        List<ServerGroupDefinition> operationalGroups = createOperationalGroups(hotAisleConfiguration);
        operationalSnapshotProvider = new DatacenterOperationalSnapshotProvider(datacenter, operationalGroups);
        // timers
        timerSimulacion = new Timer();
        timerSimulacion.setPeriodMillis(100);
        timerSimulacion.start();
        // valores iniciales
        columnaElegida = "C01";
        rackElegido = "R01";
        obtenerPasilloCalienteElegido();
        mostrarAuraRackSeleccionado();
        mostrarAuraPasilloElegido();
        calculateTemperatureRange(datacenter, temperatureOptions);
        engine.step();
        updateUI = true;
        updateSnapshots = true;
        updateSnapshots();
        formatoPorcentaje = PROPS.getProperty("number.format.percentage");
        formatoTemperatura = PROPS.getProperty("number.format.temperature");
        formatoTemperaturaSimple = PROPS.getProperty("number.format.temperature.simple");
        formatoPotenciaKw = PROPS.getProperty("number.format.power.kw");
        formatoPotenciaMw = PROPS.getProperty("number.format.power.mw");
        formatoVelocidad = PROPS.getProperty("number.format.velocidad");
        formatoPresion = PROPS.getProperty("number.format.presion");
        formatoFlujoAire = PROPS.getProperty("number.format.airflow");
        labels.get("lblSalaEscalaTemperatura01").setText(String.format(formatoTemperaturaSimple, minServerTemperatureCelsius));
        for (int i = 0; i < 5; i++) {
            float temperatura = map(i, 0, 5, minServerTemperatureCelsius, maxServerTemperatureCelsius);
            String codigoLblEscalaTemperatura = "lblSalaEscalaTemperatura0" + (i + 1);
            labels.get(codigoLblEscalaTemperatura).setText(String.format(formatoTemperaturaSimple, temperatura));
        }
        labels.get("lblSalaEscalaTemperatura06").setText(String.format(formatoTemperaturaSimple, maxServerTemperatureCelsius));
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
        labels.get("lblSalaServidoresTotalValor").setText(String.valueOf(totalInstalledServers));
        labels.get("lblSalaServidoresOnlineValor").setText(String.valueOf(totalOnlineServers));
        // debug
        showOverlay = true;
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

    private void btnClicked(String codigoBoton) {
        if (codigoBoton.startsWith("btnRackElegido"))
            actualizarRackSeleccionado(codigoBoton.replace("btnRackElegido", ""));
        else if (codigoBoton.startsWith("btnColumnaElegida"))
            actualizarColumnaSeleccionada(codigoBoton.replace("btnColumnaElegida", ""));
        updateUI = true;
    }

    private void tglClicked(String codigoToggle, int estado) {
        if (codigoToggle.toLowerCase().contains("ventilador") || codigoToggle.toLowerCase().contains("extractor"))
            actualizarUnidadRefrigeracion(codigoToggle, estado == 1);
    }

    private void actualizarUnidadRefrigeracion(String tglCode, boolean enabled) {
        String unitType = "";
        if (tglCode.contains("Ventilador")) unitType = "SUPPLY";
        else if (tglCode.contains("Extractor")) unitType = "EXHAUST";
        if (unitType.isEmpty()) return;
        String unitCode = unitType
                + "-"
                + tglCode
                .replace("tglSala", "")
                .replace("Ventilador", "")
                .replace("Extractor", "");
        coolingSystem.setEnabled(unitCode, enabled);
    }

    private void actualizarRackSeleccionado(String rackClic) {
        rackElegido = "R" + rackClic.toLowerCase().replace("der", "").replace("izq", "");
        if (!columnaElegida.equals(PROPS.getProperty("datacenter.first.column")) && !columnaElegida.equals(PROPS.getProperty("datacenter.last.column"))) {
            if (rackClic.toLowerCase().contains("izq")) columnaElegida = pasilloCalienteSeleccionado.columns().getFirst();
            else if (rackClic.toLowerCase().contains("der")) columnaElegida = pasilloCalienteSeleccionado.columns().getLast();
        }
        mostrarAuraRackSeleccionado();
    }

    private void actualizarColumnaSeleccionada(String columnaElegida) {
        this.columnaElegida = columnaElegida;
        obtenerPasilloCalienteElegido();
        mostrarAuraPasilloElegido();
        mostrarAuraRackSeleccionado();
    }

    private void mostrarAuraRackSeleccionado() {
        int i = Integer.parseInt(columnaElegida.replace("C", ""));
        String lado = i % 2 == 0 ? "Izq" : "Der";
        String codigoRackSeleccionado = "indRackElegido" + lado + rackElegido.replace("R", "");
        String codigoBotonSeleccionado = codigoRackSeleccionado.replace("ind", "btn");
        for (Button btn : botonesPasilloElegidoRacks.values()) {
            if (columnaElegida.equals(PROPS.getProperty("datacenter.first.column")) && btn.getCode().toLowerCase().contains("izq"))
                btn.setVisible(false);
            else if (columnaElegida.equals(PROPS.getProperty("datacenter.last.column")) && btn.getCode().toLowerCase().contains("der"))
                btn.setVisible(false);
            else btn.setVisible(!btn.getCode().equals(codigoBotonSeleccionado));
        }
        for (Indicator ind : indicadoresRackElegido.values()) ind.setOn(ind.getCode().equals(codigoRackSeleccionado));
    }

    private void mostrarAuraPasilloElegido() {
        indicadoresPasilloElegido.values().forEach(ind -> ind.setOn(false));
        String codigoIndPasilloSleccionado = "indPasilloElegido";
        switch (pasilloCalienteSeleccionado.code()) {
            case "HA01" -> codigoIndPasilloSleccionado += "C01";
            case "HA02" -> codigoIndPasilloSleccionado += "C02-C03";
            case "HA03" -> codigoIndPasilloSleccionado += "C04-C05";
            case "HA04" -> codigoIndPasilloSleccionado += "C06-C07";
            case "HA05" -> codigoIndPasilloSleccionado += "C08";
            default -> {
                return;
            }
        }
        indicadoresPasilloElegido.get(codigoIndPasilloSleccionado).setOn(true);
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
        updateControles();
        //draw
        dibujarFondo();
        dibujarControles();
        dibujarGradienteTemperaturaEnPasilloElegido();
        dibujarOverlay();
    }

    private void updateClock() {
        if (timerSimulacion == null || engine == null) return;
        if (!timerSimulacion.pollPeriodPulse()) return;
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

    private void updateControles() {
        if (!updateUI) return;
        updatePanelRackElegido();
        updatePanelPasilloElegido();
        updatePanelSala();
        updateUI = false;
    }

    private void updatePanelRackElegido() {
        labels.get("lblRackElegidoValor").setText(columnaElegida + "-" + rackElegido);
        Rack rack = obtenerRackElegido();
        RackLocation rackLocation = new RackLocation(columnaElegida, new RackCode(rackElegido));
        RackOperationalSnapshot rackSnapshot = operationalSnapshot
                .findRack(rackLocation)
                .orElseThrow(() -> new IllegalStateException("No existe snapshot operacional para el rack: " + rackLocation.code()
                ));
        Map<ServerLocation, ServerEnergySnapshot> energiaPorUbicacion = obtenerEnergiaPorUbicacion();
        Map<ServerLocation, ServerTemperatureSnapshot> temperaturaPorUbicacion = obtenerTemperaturaPorUbicacion();
        Map<ServerLocation, ServerHealthSnapshot> saludPorUbicacion = obtenerSaludPorUbicacion();
        for (String slot : rack.getSlotCodes()) {
            String numeroSlot = slot.replace("S", "");
            ServerLocation location = new ServerLocation(columnaElegida, new RackCode(rackElegido), slot);
            Optional<Server> installedServer = datacenter.getServer(location);
            Label lblSlotTemperatura = labels.get("lblSlotTemperatura" + numeroSlot);
            Label lblSlotCarga = labels.get("lblSlotCarga" + numeroSlot);
            Label lblSlotPotencia = labels.get("lblSlotPotencia" + numeroSlot);
            Indicator indSlot = indicadores.get("indSlot" + numeroSlot);
            Indicator indSlotVacio = indicadores.get("indSlotVacio" + numeroSlot);
            Indicator indSlotOffline = indicadores.get("indSlotOffline" + numeroSlot);
            Indicator indSlotStatusOk = indicadores.get("indSlotOk" + numeroSlot);
            Indicator indSlotStatusAlerta = indicadores.get("indSlotAlerta" + numeroSlot);
            Indicator indAlerta = indicadoresAlerta.get("indAlerta" + numeroSlot);
            Indicator indSlotIA1 = indicadoresSlotIA.get("indSlotIA" + numeroSlot + "-1");
            Indicator indSlotIA2 = indicadores.get("indSlotIA" + numeroSlot + "-2");
            if (installedServer.isEmpty()) {
                mostrarSlotVacio(indSlot,
                        lblSlotTemperatura,
                        lblSlotCarga,
                        lblSlotPotencia,
                        indSlotVacio,
                        indSlotOffline,
                        indSlotStatusOk,
                        indSlotStatusAlerta,
                        indAlerta,
                        indSlotIA1,
                        indSlotIA2
                );
                continue;
            }
            ServerTemperatureSnapshot temperatura = temperaturaPorUbicacion.get(location);
            if (temperatura == null) throw new IllegalStateException("No existe snapshot de temperatura para el servidor: " + location);
            ServerEnergySnapshot energia = energiaPorUbicacion.get(location);
            if (energia == null) throw new IllegalStateException("No existe snapshot de energía para el servidor: " + location);
            ServerHealthSnapshot salud = saludPorUbicacion.get(location);
            if (salud == null) throw new IllegalStateException("No existe snapshot de salud para el servidor: " + location);
            actualizarColorSlot(indSlot, (float) temperatura.temperatureCelsius());
            lblSlotTemperatura.setText(String.format(formatoTemperatura, temperatura.temperatureCelsius()));
            lblSlotCarga.setText(String.format(formatoPorcentaje, energia.utilization() * 100));
            lblSlotPotencia.setText(String.format(formatoPotenciaKw, energia.currentPowerWatts() / 1000));
            HardwareStatus status = salud.status();
            indSlotStatusOk.setOn(status == HardwareStatus.OK);
            indSlotStatusAlerta.setOn(status == HardwareStatus.ALERT);
            indAlerta.setOn(status == HardwareStatus.ALERT);
            boolean alertaPorCarga = salud.hasAlertReason(ServerAlertReason.HIGH_UTILIZATION);
            lblSlotCarga.setTextColor(alertaPorCarga ? COLOR_LABEL_MAGENTA : COLOR_LABEL_AZUL);
            boolean alertaPorTemperatura = salud.hasAlertReason(ServerAlertReason.HIGH_TEMPERATURE);
            actualizarColorLabelPorTemperatura(alertaPorTemperatura, lblSlotTemperatura, temperatura.temperatureCelsius());
            indSlotVacio.setOn(false);
            indSlotOffline.setOn(status == HardwareStatus.OFFLINE);
            boolean serverIA = installedServer.orElseThrow().getRole() == ServerRole.AI;
            indSlotIA1.setOn(serverIA);
            indSlotIA2.setOn(serverIA);
        }
        Label lblTemperaturaPromedio = labels.get("lblRackTemperaturaPromedioValor");
        Label lblCargaPromedio = labels.get("lblRackCargaPromedioValor");
        if (rackSnapshot.hasOnlineServers()) {
            lblTemperaturaPromedio.setTextColor(COLOR_LABEL_AMARILLO);
            lblTemperaturaPromedio.setText(String.format(formatoTemperatura, rackSnapshot.averageOnlineTemperatureCelsius()));
            lblCargaPromedio.setText(String.format(formatoPorcentaje, rackSnapshot.averageOnlineUtilization() * 100));
        } else {
            lblTemperaturaPromedio.setTextColor(COLOR_LABEL_BLANCO);
            lblTemperaturaPromedio.setText("--");
            lblCargaPromedio.setText("--");
        }
        labels.get("lblRackPotenciaAcumuladaValor").setText(String.format(formatoPotenciaKw, rackSnapshot.currentPowerWatts() / 1000));
        actualizarBarra("RackElegidoPotencia", rackSnapshot.currentPowerWatts(), rackSnapshot.idlePowerWatts(), rackSnapshot.maxPowerWatts());
    }

    private void actualizarColorSlot(Indicator indSlot, float temperatura) {
        Objects.requireNonNull(indSlot);
        float fColor = map(temperatura, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 1);
        fColor = Math.clamp(fColor, 0, 1);
        int colorSlot = Colors.lerpColor(COLOR_TEMPERATURA_MINIMA, COLOR_TEMPERATURA_MAXIMA, fColor);
        indSlot.setOnColor(colorSlot);
        indSlot.setOn(true);
    }

    private void mostrarSlotVacio(
            Indicator indSlot,
            Label lblSlotTemperatura,
            Label lblSlotCarga,
            Label lblSlotPotencia,
            Indicator indSlotVacio,
            Indicator indSlotOffline,
            Indicator indSlotStatusOk,
            Indicator indSlotStatusAlerta,
            Indicator indAlerta,
            Indicator indSlotIA1,
            Indicator indSlotIA2
    ) {
        Objects.requireNonNull(indSlot);
        Objects.requireNonNull(lblSlotTemperatura);
        Objects.requireNonNull(lblSlotCarga);
        Objects.requireNonNull(lblSlotPotencia);
        Objects.requireNonNull(indSlotVacio);
        Objects.requireNonNull(indSlotOffline);
        Objects.requireNonNull(indSlotStatusOk);
        Objects.requireNonNull(indSlotStatusAlerta);
        Objects.requireNonNull(indAlerta);
        Objects.requireNonNull(indSlotIA1);
        Objects.requireNonNull(indSlotIA2);
        lblSlotTemperatura.setTextColor(COLOR_LABEL_BLANCO);
        lblSlotCarga.setTextColor(COLOR_LABEL_BLANCO);
        lblSlotTemperatura.setText("--");
        lblSlotCarga.setText("--");
        lblSlotPotencia.setText("--");
        indSlot.setOn(false);
        indSlotVacio.setOn(true);
        indSlotOffline.setOn(false);
        indSlotStatusOk.setOn(false);
        indSlotStatusAlerta.setOn(false);
        indAlerta.setOn(false);
        indSlotIA1.setOn(false);
        indSlotIA2.setOn(false);
    }

    private void actualizarColorLabelPorTemperatura(boolean alerta, Label lbl, double temperatura) {
        if (alerta) lbl.setTextColor(COLOR_LABEL_MAGENTA);
        else if (temperatura >= Double.parseDouble(PROPS.getProperty("simulation.health.temperature.warning-threshold-celsius")))
            lbl.setTextColor(COLOR_LABEL_AMARILLO);
        else lbl.setTextColor(COLOR_LABEL_VERDE);
    }

    private void actualizarBarra(String tipo, double valor, double valorMin, double valorMax) {
        if (tipo == null || tipo.isEmpty()) return;
        String llave = "indBarra" + tipo;
        int iMax = (int) map((float) valor, (float) valorMin, (float) valorMax, 1, 6);
        for (int i = 0; i < 6; i++) indicadores.get(llave + (i + 1)).setOn(i < iMax);
    }

    private Rack obtenerRackElegido() {
        return datacenter.findRack(columnaElegida, rackElegido).orElseThrow(() -> new IllegalStateException("Rack no encontrado: " + columnaElegida + "-" + rackElegido));
    }

    private void updatePanelPasilloElegido() {
        boolean pasilloExtremoIzquierdoElegido = columnaElegida.equals(PROPS.getProperty("datacenter.first.column"));
        boolean pasilloExtremoDerechoElegido = columnaElegida.equals(PROPS.getProperty("datacenter.last.column"));
        indicadoresPasilloNull.get("indPasilloNullIzq").setOn(pasilloExtremoIzquierdoElegido);
        indicadores.get("indFlechasAireFrioIzq").setOn(!pasilloExtremoIzquierdoElegido);
        indicadoresPasilloNull.get("indPasilloNullDer").setOn(pasilloExtremoDerechoElegido);
        indicadores.get("indFlechasAireFrioDer").setOn(!pasilloExtremoDerechoElegido);
        obtenerPasilloCalienteElegido();
        labels.get("lblPasilloElegidoValor").setText(pasilloCalienteSeleccionado.displayName());
        // datos adicionales
        List<String> zoneCodesDelPasillo = obtenerCoolingZoneCodesPasillo(pasilloCalienteSeleccionado);
        CoolingZoneGroupSnapshot coolingGroupSnapshot = coolingSnapshot.aggregateZones(pasilloCalienteSeleccionado.code(), zoneCodesDelPasillo);
        double thermalCoverage = coolingGroupSnapshot.thermalCoverage();
        labels.get("lblPasilloElegidoCoberturaTermicaValor").setText(String.format(formatoPorcentaje, thermalCoverage * 100.0));
        double deltaTinOut = coolingGroupSnapshot.airTemperatureRiseCelsius();
        labels.get("lblPasilloElegidoDeltaTemperaturaValor").setText(String.format(formatoTemperatura, deltaTinOut));
        double recirculation = coolingGroupSnapshot.averageRecirculationFraction();
        labels.get("lblPasilloElegidoRecirculacionValor").setText(String.format(formatoPorcentaje, recirculation * 100.0));
        double supplyAirflow = zoneCodesDelPasillo.stream()
                .map(coolingSnapshot::findZone)
                .flatMap(Optional::stream)
                .mapToDouble(CoolingZoneSnapshot::supplyAirflowCubicMetersPerSecond)
                .sum();
        double exhaustAirflow =
                zoneCodesDelPasillo
                        .stream()
                        .map(coolingSnapshot::findZone)
                        .flatMap(Optional::stream)
                        .mapToDouble(CoolingZoneSnapshot::exhaustAirflowCubicMetersPerSecond)
                        .sum();
        labels.get("lblPasilloElegidoFlujoAireValor").setText(String.format(formatoFlujoAire, supplyAirflow, exhaustAirflow));
        // servidores instalados
        ServerGroupOperationalSnapshot aisleSnapshot = operationalSnapshot
                .findServerGroup(pasilloCalienteSeleccionado.code())
                .orElseThrow(() -> new IllegalStateException("No existe snapshot operacional para el pasillo: " + pasilloCalienteSeleccionado.code()));
        Label lblTemperaturaPromedio = labels.get("lblPasilloElegidoTemperaturaPromedioValor");
        Label lblTemperaturaMaxima = labels.get("lblPasilloElegidoTemperaturaMaximaValor");
        Label lblCargaPromedio = labels.get("lblPasilloElegidoCargaITValor");
        indicadoresPasilloElegidoServidorTemperaturaMaxima.values().forEach(indicador -> indicador.setOn(false));
        if (!aisleSnapshot.hasInstalledServers()) {
            lblTemperaturaPromedio.setTextColor(COLOR_LABEL_BLANCO);
            lblTemperaturaMaxima.setTextColor(COLOR_LABEL_BLANCO);
            lblTemperaturaPromedio.setText("--");
            lblTemperaturaMaxima.setText("--");
            lblCargaPromedio.setText("--");
            return;
        }
        // Temperatura máxima: considera todos los servidores instalados
        double temperaturaMaxima = aisleSnapshot.maximumTemperatureCelsius();
        int colorTemperaturaMaxima = obtenerColorRangoTemperatura((float) temperaturaMaxima);
        lblTemperaturaMaxima.setTextColor(colorTemperaturaMaxima);
        lblTemperaturaMaxima.setText(String.format(formatoTemperatura, temperaturaMaxima));
        ServerLocation ubicacionTemperaturaMaxima = aisleSnapshot
                .maximumTemperatureLocation()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "El pasillo tiene servidores instalados, "
                                        + "pero no informa la ubicación "
                                        + "de la temperatura máxima: "
                                        + pasilloCalienteSeleccionado.code()
                        )
                );
        String columnaTemperaturaMaxima = ubicacionTemperaturaMaxima.column();
        String ladoTemperaturaMaxima;
        if (columnaTemperaturaMaxima.equals(PROPS.getProperty("datacenter.first.column")))
            ladoTemperaturaMaxima = "Der";
        else if (columnaTemperaturaMaxima.equals(PROPS.getProperty("datacenter.last.column")))
            ladoTemperaturaMaxima = "Izq";
        else {
            int numeroColumna = Integer.parseInt(columnaTemperaturaMaxima.replace("C", ""));
            ladoTemperaturaMaxima = numeroColumna % 2 == 0 ? "Izq" : "Der";
        }
        String numeroRack = ubicacionTemperaturaMaxima.rackCode().value().replace("R", "");
        String codigoIndicador = "indPasilloElegidoServidorTemperaturaMaxima" + ladoTemperaturaMaxima + numeroRack;
        Indicator indicadorTemperaturaMaxima = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(codigoIndicador);
        if (indicadorTemperaturaMaxima == null) throw new IllegalStateException("No existe el indicador de temperatura máxima: " + codigoIndicador);
        indicadorTemperaturaMaxima.setOnColor(colorTemperaturaMaxima);
        indicadorTemperaturaMaxima.setOn(true);
        /*
         * Los promedios consideran únicamente servidores online.
         */
        if (aisleSnapshot.hasOnlineServers()) {
            double temperaturaPromedio = aisleSnapshot.averageOnlineTemperatureCelsius();
            double cargaPromedio = aisleSnapshot.averageOnlineUtilization();
            int colorTemperaturaPromedio = obtenerColorRangoTemperatura((float) temperaturaPromedio);
            lblTemperaturaPromedio.setTextColor(colorTemperaturaPromedio);
            lblTemperaturaPromedio.setText(String.format(formatoTemperatura, temperaturaPromedio));
            lblCargaPromedio.setText(String.format(formatoPorcentaje, cargaPromedio * 100));
        } else {
            lblTemperaturaPromedio.setTextColor(COLOR_LABEL_BLANCO);
            lblTemperaturaPromedio.setText("--");
            lblCargaPromedio.setText("--");
        }
        // gradiente color pasillo caliente
        temperaturasPasilloCalienteSeleccionado = new ArrayList<>();
        List<String> rackCodes = obtenerRackCodesPasillo(pasilloCalienteSeleccionado);
        for (String rackCode : rackCodes) {
            float temperaturaPromedioRacks = 0;
            for (String column : pasilloCalienteSeleccionado.columns()) {
                RackLocation rackLocation = new RackLocation(column, new RackCode(rackCode));
                RackOperationalSnapshot rackSnapshot = operationalSnapshot.findRack(rackLocation).orElseThrow();
                temperaturaPromedioRacks += (float) rackSnapshot.averageOnlineTemperatureCelsius();
            }
            temperaturaPromedioRacks /= pasilloCalienteSeleccionado.columns().size();
            temperaturasPasilloCalienteSeleccionado.add(temperaturaPromedioRacks);
        }
        float fColor = map(
                temperaturasPasilloCalienteSeleccionado.getFirst(),
                minServerTemperatureCelsius,
                maxServerTemperatureCelsius,
                0,
                1);
        int colorEfectoTemperatura = Colors.lerpColor(COLOR_TEMPERATURA_MINIMA, COLOR_TEMPERATURA_MAXIMA, fColor);
        indicadores.get("indPasilloElegidoEfectoTemperatura").setOnColor(colorEfectoTemperatura);
    }

    private List<String> obtenerRackCodesPasillo(HotAisleDefinition pasillo) {
        String columnaReferencia = pasillo.columns().getFirst();
        return datacenter
                .getRacks()
                .stream()
                .filter(rack -> rack.getLocation().column().equals(columnaReferencia))
                .map(rack -> rack.getCode().value())
                .sorted()
                .toList();
    }

    private List<String> obtenerCoolingZoneCodesPasillo(HotAisleDefinition pasillo) {
        Set<String> columnasPasillo = new HashSet<>(pasillo.columns());
        return coolingConfiguration
                .zones()
                .stream()
                .filter(zone -> zone.serverLocations().stream().anyMatch(location -> columnasPasillo.contains(location.column())))
                .map(CoolingZoneDefinition::code)
                .sorted()
                .toList();
    }

    private int obtenerColorRangoTemperatura(float temperatura) {
        if (temperatura >= Float.parseFloat(PROPS.getProperty("simulation.health.temperature.alert-threshold-celsius")))
            return COLOR_LABEL_MAGENTA;
        else if (temperatura >= Float.parseFloat(PROPS.getProperty("simulation.health.temperature.warning-threshold-celsius")))
            return COLOR_LABEL_AMARILLO;
        else return COLOR_LABEL_VERDE;
    }

    private void obtenerPasilloCalienteElegido() {
        pasilloCalienteSeleccionado = hotAisleByColumn.get(columnaElegida);
        if (pasilloCalienteSeleccionado == null) throw new IllegalArgumentException("No hot aisle configured for column: " + columnaElegida);
    }

    private Map<ServerLocation, ServerEnergySnapshot> obtenerEnergiaPorUbicacion() {
        return energySnapshot.servers()
                .stream()
                .collect(Collectors.toUnmodifiableMap(ServerEnergySnapshot::location, Function.identity()));
    }

    private Map<ServerLocation, ServerTemperatureSnapshot> obtenerTemperaturaPorUbicacion() {
        return temperatureSnapshot.servers()
                .stream()
                .collect(Collectors.toMap(
                        server -> new ServerLocation(server.column(), server.rackCode(), server.slot()),
                        Function.identity()
                ));
    }

    private Map<ServerLocation, ServerHealthSnapshot> obtenerSaludPorUbicacion() {
        return healthSnapshot.servers()
                .stream()
                .collect(Collectors.toMap(
                        server -> new ServerLocation(server.column(), server.rackCode(), server.slot()),
                        Function.identity()
                ));
    }

    private void updatePanelSala() {
        updateSalaIndicadorPasilloCaliente("HA01", "indSalaPasilloCalienteC01");
        updateSalaIndicadorPasilloCaliente("HA02", "indSalaPasilloCalienteC02-C03");
        updateSalaIndicadorPasilloCaliente("HA03", "indSalaPasilloCalienteC04-C05");
        updateSalaIndicadorPasilloCaliente("HA04", "indSalaPasilloCalienteC06-C07");
        updateSalaIndicadorPasilloCaliente("HA05", "indSalaPasilloCalienteC08");
        for (Rack rack : datacenter.getRacks()) {
            RackLocation location = rack.getLocation();
            String codigoIndRack = "indRack" + rack.getColumn() + rack.getRow();
            Indicator indRack = indicadoresRack.get(codigoIndRack);
            if (indRack == null) continue;
            List<Server> servidoresEnRack = datacenter.getServers(location);
            RackOperationalSnapshot rackSnapshot = operationalSnapshot.getRack(location);
            boolean rackVacio = !rackSnapshot.hasInstalledServers();
            boolean rackOffline = rackSnapshot.hasInstalledServers() && !rackSnapshot.hasOnlineServers();
            boolean rackIA = servidoresEnRack.stream().anyMatch(server -> server.getRole() == ServerRole.AI);
            float temperaturaPromedio = (float) rackSnapshot.averageOnlineTemperatureCelsius();
            boolean rackHotspot = temperaturaPromedio > maxServerTemperatureCelsius;
            int colorRack;
            if (rackVacio) colorRack = COLOR_RACK_VACIO;
            else if (rackOffline) colorRack = COLOR_TEMPERATURA_MINIMA; //COLOR_RACK_OFFLINE
            else if (rackHotspot) colorRack = COLOR_RACK_HOTSPOT;
            else colorRack = calcularColorRack(temperaturaPromedio);
            indRack.setOnColor(colorRack);
            actualizarIndicadoresRackCondicion(codigoIndRack, rackOffline, rackVacio, rackHotspot, rackIA);
        }
    }

    private int calcularColorRack(float temperatura) {
        float fColor = map(temperatura, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 1);
        fColor = Math.clamp(fColor, 0, 1);
        return Colors.lerpColor(COLOR_TEMPERATURA_MINIMA, COLOR_TEMPERATURA_MAXIMA, fColor);
    }

    private void actualizarIndicadoresRackCondicion(
            String codigoIndRack,
            boolean rackOffline,
            boolean rackVacio,
            boolean rackHotspot,
            boolean rackIA
    ) {
        Indicator indOffline = indicadoresRackCondicion.get(codigoIndRack.replace("indRack", "indRackOffline"));
        Indicator indVacio = indicadoresRackCondicion.get(codigoIndRack.replace("indRack", "indRackVacio"));
        Indicator indHotspot = indicadoresRackCondicion.get(codigoIndRack.replace("indRack", "indRackHotspot"));
        Indicator indIA = indicadoresRackCondicion.get(codigoIndRack.replace("indRack", "indRackIA"));
        indOffline.setOn(rackOffline);
        indVacio.setOn(rackVacio);
        indHotspot.setOn(rackHotspot);
        indIA.setOn(rackIA);
    }

    private void updateSalaIndicadorPasilloCaliente(String codigoPasilloCaliente, String codigoIndicador) {
        float temperaturaPromedio =
                (float) operationalSnapshot.findServerGroup(codigoPasilloCaliente)
                        .orElseThrow(() -> new IllegalStateException("No existe snapshot operacional para el pasillo: " + codigoPasilloCaliente))
                        .averageOnlineTemperatureCelsius();
        float temperaturaMaxima =
                (float) operationalSnapshot.findServerGroup(codigoPasilloCaliente)
                        .orElseThrow(() -> new IllegalStateException("No existe snapshot operacional para el pasillo: " + codigoPasilloCaliente))
                        .maximumTemperatureCelsius();
        float temperatura = (temperaturaMaxima + temperaturaPromedio) * 0.5f;
        float f = map(temperatura, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 1);
        f = Math.clamp(f, 0, 1);
        int colorPasilloCaliente = lerpColor(COLOR_TEMPERATURA_MINIMA, COLOR_TEMPERATURA_MAXIMA, f);
        int a = (int) map(temperatura, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 96);
        a = Math.clamp(a, 0, 128);
        int r = Colors.red(colorPasilloCaliente);
        int g = Colors.green(colorPasilloCaliente);
        int b = Colors.blue(colorPasilloCaliente);
        indicadores.get(codigoIndicador).setOnColor(Colors.argb(a, r, g, b));
    }

    private void dibujarFondo() {
        background(128);
        pushStyle();
        imageMode(CORNER);
        image(fondo, 0, 0, width, height);
        image(fondoSala, 0, 0, width, height);
        image(fondoPasilloElegido, 0, 0, width, height);
        image(fondoRackElegido, 0, 0, width, height);
        popStyle();
    }

    private void dibujarControles() {
        indicadoresAlerta.values().forEach(Indicator::draw);
        labels.values().forEach(Label::draw);
        indicadores.values().forEach(Indicator::draw);
        indicadoresPasilloElegido.values().forEach(Indicator::draw);
        indicadoresPasilloElegidoServidorTemperaturaMaxima.values().forEach(Indicator::draw);
        indicadoresRackElegido.values().forEach(Indicator::draw);
        indicadoresRack.values().forEach(Indicator::draw);
        indicadoresPasilloNull.values().forEach(Indicator::draw);
        indicadoresSlotIA.values().forEach(Indicator::draw);
        indicadoresRackCondicion.values().forEach(Indicator::draw);
        botonesPasilloElegidoRacks.values().forEach(Button::draw);
        botonesColumnas.values().forEach(Button::draw);
        toggles.values().forEach(Toggle::draw);
    }

    private void dibujarGradienteTemperaturaEnPasilloElegido() {
        pushStyle();
        noFill();
        strokeWeight(1);
        float y = Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.y")) * height;
        float h = Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.height")) * height;
        float totalH = y + temperaturasPasilloCalienteSeleccionado.size() * h;
        float x = Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.x")) * width;
        float minW = Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.min.width")) * height;
        float maxW = Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.max.width")) * height;
        float minY = y;
        float maxY = y + h;
        float temperatura = temperaturasPasilloCalienteSeleccionado.getFirst();
        int color = Colors.lerpColor(
                COLOR_TEMPERATURA_MINIMA,
                COLOR_TEMPERATURA_MAXIMA,
                map(temperatura, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 1)
        );
        for (int j = (int) minY; j < (int) maxY; j++) {
            float w = map(j, y, totalH, minW, maxW);
            stroke(color);
            line(x - w * 0.5f, j, x + w * 0.5f, j);
        }
        for (int i = 0; i < temperaturasPasilloCalienteSeleccionado.size() - 1; i++) {
            temperatura = temperaturasPasilloCalienteSeleccionado.get(i);
            float temperaturaSiguiente = temperaturasPasilloCalienteSeleccionado.get(i + 1);
            minY = y + (i + 1) * h;
            maxY = y + (i + 2) * h;
            color = Colors.lerpColor(
                    COLOR_TEMPERATURA_MINIMA,
                    COLOR_TEMPERATURA_MAXIMA,
                    map(temperatura, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 1)
            );
            int colorSiguiente = Colors.lerpColor(
                    COLOR_TEMPERATURA_MINIMA,
                    COLOR_TEMPERATURA_MAXIMA,
                    map(temperaturaSiguiente, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 1)
            );
            for (int j = (int) minY; j < (int) maxY; j++) {
                float w = map(j, y, totalH, minW, maxW);
                float fColor = map(j, minY, maxY, 0, 1);
                int colorLinea = Colors.lerpColor(color, colorSiguiente, fColor);
                stroke(colorLinea);
                line(x - w * 0.5f, j, x + w * 0.5f, j);
            }
        }
        strokeWeight(Float.parseFloat(PROPS.getProperty("ui.temperature.gradient.stroke.weigth")) * height);
        stroke(COLOR_BORDE_GRADIENTE_TEMPERATURA);
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

    private void dibujarOverlay() {
        overlayManager.getActiveOverlays().forEach(entry -> entry.getRender().run());
        if (!showOverlay) return;
        pushStyle();
        imageMode(CORNER);
        image(overlayEstatico, 0, 0, width, height);
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
        if (key == 'm') showOverlay = !showOverlay;
        else if (keyCode == BARRA_ESPACIADORA) timerSimulacion.toggle();
    }

}
