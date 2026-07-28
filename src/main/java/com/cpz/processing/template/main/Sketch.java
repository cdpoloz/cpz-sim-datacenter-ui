package com.cpz.processing.template.main;

import com.cpz.processing.controls.controls.Control;
import com.cpz.processing.controls.controls.config.ControlConfigLoader;
import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.core.input.InputManager;
import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.processing.controls.input.ProcessingKeyboardAdapter;
import com.cpz.processing.template.config.HotAisleConfiguration;
import com.cpz.processing.template.config.HotAisleConfigurationLoader;
import com.cpz.processing.template.config.HotAisleDefinition;
import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
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
import processing.opengl.PJOGL;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.cpz.processing.template.main.Launcher.LOG;
import static com.cpz.processing.template.main.Launcher.PROPS;
import static com.cpz.processing.template.util.Constantes.*;

/**
 * @author CPZ
 */
public class Sketch extends PApplet {

    private InputManager inputManager;
    private OverlayManager overlayManager;
    private ProcessingKeyboardAdapter processingKeyboardAdapter;
    private Map<String, Control> controls;
    private Map<String, Indicator> indicadores, indicadoresSlotIA, indicadoresAlerta, indicadoresPasilloElegidoServidorTemperaturaMaxima, indicadoresOverlay;
    private Map<String, Label> labels;

    private PImage fondo, overlayEstatico;
    private boolean showOverlayEstatico;

    private Timer timerSimulacion;
    private boolean updateSnapshots, updateUI;
    private SimulationEngine engine;
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
    private Map<String, Rack> racks;
    private float potenciaIdleTotalRackElegido, potenciaMaximaTotalRackElegido;
    private float minServerTemperatureCelsius;
    private float maxServerTemperatureCelsius;

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
        controles = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicadoresOverlay.json");
        indicadoresOverlay = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicadoresOverlay.put(ind.getCode(), (Indicator) ind));
        // indicadoresPasilloElegidoServidorTemperaturaMaxima
        controles = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicadoresPasilloElegidoServidorTemperaturaMaxima.json");
        indicadoresPasilloElegidoServidorTemperaturaMaxima = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicadoresPasilloElegidoServidorTemperaturaMaxima.put(ind.getCode(), (Indicator) ind));
        // font
        textFont(createFont("data" + File.separator + "font" + File.separator + "JetBrainsMono.ttf", 56, true));
        // imágenes
        fondo = loadImage("data" + File.separator + "img" + File.separator + "ui_fondo.png");
        overlayEstatico = loadImage("data" + File.separator + "img" + File.separator + "ui_overlay.png");
        // datacenter
        Path configPath = Path.of("data/config/datacenter-test-complete.json");
        DatacenterDefinition definition = new JsonDatacenterConfigLoader().load(configPath);
        datacenter = new DatacenterFactory().create(definition);
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
        TemperatureSystemOptions temperatureOptions = new TemperatureSystemOptionsFactory().create(definition);
        temperatureSystem = new TemperatureSystem(datacenter, temperatureOptions, new SimpleServerTemperatureModel());
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
            Map<String, HotAisleDefinition> hotAisleByColumn = new HashMap<>();
            for (HotAisleDefinition hotAisle : hotAisleConfiguration.hotAisles()) {
                for (String column : hotAisle.columns()) {
                    HotAisleDefinition previous = hotAisleByColumn.put(column, hotAisle);
                    if (previous != null) throw new IllegalArgumentException("Column assigned to multiple hot aisles: " + column);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // timers
        timerSimulacion = new Timer();
        timerSimulacion.setPeriodMillis(100);
        timerSimulacion.start();
        // valores iniciales
        columnaElegida = "C01";
        rackElegido = "R01";
        obtenerPasilloCalienteElegido();
        calcularLimitesPotenciaRackElegido();
        calculateTemperatureRange(datacenter, temperatureOptions);
        engine.step();
        updateUI = true;
        updateSnapshots = true;
        updateSnapshots();
        // debug
        showOverlayEstatico = true;
    }

    private final Map<String, HotAisleDefinition> hotAisleByColumn =
            new HashMap<>();

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

    private void calcularLimitesPotenciaRackElegido() {
        potenciaIdleTotalRackElegido = 0;
        potenciaMaximaTotalRackElegido = 0;
        Rack rack = obtenerRackElegido();
        for (String slot : rack.getSlotCodes()) {
            ServerLocation location = new ServerLocation(columnaElegida, new RackCode(rackElegido), slot);
            Optional<Server> installedServer = datacenter.getServer(location);
            if (installedServer.isEmpty()) continue;
            potenciaIdleTotalRackElegido += installedServer.get().getConfig().idlePowerWatts();
            potenciaMaximaTotalRackElegido += installedServer.get().getConfig().maxPowerWatts();
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
        overlayManager.getActiveOverlays().forEach(entry -> entry.getRender().run());
        //draw
        dibujarFondo();
        indicadoresAlerta.values().forEach(Indicator::draw);
        labels.values().forEach(Label::draw);
        indicadores.values().forEach(Indicator::draw);
        indicadoresPasilloElegidoServidorTemperaturaMaxima.values().forEach(Indicator::draw);
        indicadoresSlotIA.values().forEach(Indicator::draw);
        indicadoresOverlay.values().forEach(Indicator::draw);
        if (showOverlayEstatico) dibujarOverlayEstatico();
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
        updateSnapshots = false;
        updateUI = true;
    }


    private void updateControles() {
        if (!updateUI) return;
        updatePanelRackElegido();
        updatePanelPasilloElegido();
        updateUI = false;
    }

    private void updatePanelRackElegido() {
        labels.get("lblRackElegidoValor").setText(columnaElegida + "-" + rackElegido);
        Rack rack = obtenerRackElegido();
        Map<ServerLocation, ServerEnergySnapshot> energiaPorUbicacion = obtenerEnergiaPorUbicacion();
        Map<ServerLocation, ServerTemperatureSnapshot> temperaturaPorUbicacion = obtenerTemperaturaPorUbicacion();
        Map<ServerLocation, ServerHealthSnapshot> saludPorUbicacion = obtenerSaludPorUbicacion();
        int servidoresInstalados = 0;
        int servidoresOnline = 0;
        double temperaturaPromedio = 0;
        double cargaPromedio = 0;
        float potenciaAcumulada = 0;
        for (String slot : rack.getSlotCodes()) {
            ServerLocation location = new ServerLocation(columnaElegida, new RackCode(rackElegido), slot);
            Optional<Server> installedServer = datacenter.getServer(location);
            Label lblSlotTemperatura = labels.get("lblSlotTemperatura" + slot.replace("S", ""));
            Label lblSlotCarga = labels.get("lblSlotCarga" + slot.replace("S", ""));
            Label lblSlotPotencia = labels.get("lblSlotPotencia" + slot.replace("S", ""));
            Indicator indSlot = indicadores.get("indSlot" + slot.replace("S", ""));
            Indicator indSlotVacio = indicadores.get("indSlotVacio" + slot.replace("S", ""));
            Indicator indSlotOffline = indicadores.get("indSlotOffline" + slot.replace("S", ""));
            Indicator indSlotStatusOk = indicadores.get("indSlotOk" + slot.replace("S", ""));
            Indicator indSlotStatusAlerta = indicadores.get("indSlotAlerta" + slot.replace("S", ""));
            Indicator indAlerta = indicadoresAlerta.get("indAlerta" + slot.replace("S", ""));
            Indicator indSlotIA1 = indicadoresSlotIA.get("indSlotIA" + slot.replace("S", "") + "-1");
            Indicator indSlotIA2 = indicadores.get("indSlotIA" + slot.replace("S", "") + "-2");
            if (installedServer.isEmpty()) {
                mostrarSlotVacio(
                        indSlot,
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
            } else {
                // se actualiza el valor de la temperatura (viene del snapshot)
                ServerTemperatureSnapshot temperatura = temperaturaPorUbicacion.get(location);
                if (temperatura == null) throw new IllegalStateException("No existe snapshot de temperatura para el servidor: " + location);
                actualizarColorSlot(indSlot, (float) temperatura.temperatureCelsius());
                lblSlotTemperatura.setText(String.format(PROPS.getProperty("number.format.temperature"), temperatura.temperatureCelsius()));
                // se actualiza el valor de la carga y potencia consumida (viene del snapshot)
                ServerEnergySnapshot energia = energiaPorUbicacion.get(location);
                if (energia == null) throw new IllegalStateException("No existe snapshot de energía para el servidor: " + location);
                lblSlotCarga.setText(String.format(PROPS.getProperty("number.format.percentage"), energia.utilization() * 100));
                lblSlotPotencia.setText(String.format(PROPS.getProperty("number.format.power.kw"), energia.currentPowerWatts() / 1000));
                // se acumulan los valores de las variables que se van a promediar
                temperaturaPromedio += temperatura.temperatureCelsius();
                servidoresInstalados++;
                cargaPromedio += energia.utilization();
                servidoresOnline += energia.utilization() > 0 ? 1 : 0;
                potenciaAcumulada += energia.currentPowerWatts();
                // se actualiza el estado de los indicadores Ok/Alerta
                ServerHealthSnapshot salud = saludPorUbicacion.get(location);
                if (salud == null) throw new IllegalStateException("No existe snapshot de salud para el servidor: " + location);
                HardwareStatus status = salud.status();
                indSlotStatusOk.setOn(status == HardwareStatus.OK);
                indAlerta.setOn(status == HardwareStatus.ALERT);
                indSlotStatusAlerta.setOn(status == HardwareStatus.ALERT);
                // se actualiza el color de los labels por alertas
                boolean alertaPorCarga = salud.hasAlertReason(ServerAlertReason.HIGH_UTILIZATION);
                lblSlotCarga.setTextColor(alertaPorCarga ? COLOR_LABEL_MAGENTA : COLOR_LABEL_AZUL);
                boolean alertaPorTemperatura = salud.hasAlertReason(ServerAlertReason.HIGH_TEMPERATURE);
                actualizarColorLabelPorTemperatura(alertaPorTemperatura, lblSlotTemperatura, temperatura.temperatureCelsius());
                // *****************************************************************
                // ******** PODRÍA INVOCARSE EN CADA CAMBIO DE RACK/COLUMNA ********
                indSlotOffline.setOn(status == HardwareStatus.OFFLINE);
                boolean serverIA = installedServer.get().getRole() == ServerRole.AI;
                indSlotIA1.setOn(serverIA);
                indSlotIA2.setOn(serverIA);
                // *****************************************************************
                // *****************************************************************
            }
        }
        temperaturaPromedio /= servidoresInstalados;
        cargaPromedio /= servidoresOnline;
        labels.get("lblRackTemperaturaPromedioValor").setTextColor(servidoresInstalados > 0 ? COLOR_LABEL_AMARILLO : COLOR_LABEL_BLANCO);
        labels.get("lblRackTemperaturaPromedioValor").setText(servidoresInstalados > 0 ? String.format(PROPS.getProperty("number.format.temperature"), temperaturaPromedio) : "--");
        labels.get("lblRackCargaPromedioValor").setText(String.format(PROPS.getProperty("number.format.percentage"), cargaPromedio * 100));
        labels.get("lblRackPotenciaAcumuladaValor").setText(String.format(PROPS.getProperty("number.format.power.kw"), potenciaAcumulada / 1000));
        actualizarBarra("RackElegidoPotencia", potenciaAcumulada, potenciaIdleTotalRackElegido, potenciaMaximaTotalRackElegido);
    }

    private void actualizarColorSlot(Indicator indSlot, float temperatura) {
        Objects.requireNonNull(indSlot);
        float fColor = map(temperatura, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 1);
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

    private void actualizarBarra(String tipo, float valor, float valorMin, float valorMax) {
        if (tipo == null || tipo.isEmpty()) return;
        String llave = "indBarra" + tipo;
        int iMax = (int) map(valor, valorMin, valorMax, 1, 6);
        for (int i = 0; i < 6; i++) indicadores.get(llave + (i + 1)).setOn(i < iMax);
    }

    private Rack obtenerRackElegido() {
        return datacenter.findRack(columnaElegida, rackElegido).orElseThrow(() -> new IllegalStateException("Rack no encontrado: " + columnaElegida + "-" + rackElegido));
    }

    private void updatePanelPasilloElegido() {
        // si se elige al pasillo de los extremos se oscurece la columna que no es relevante
        indicadoresOverlay.get("indPasilloNullIzq").setOn(columnaElegida.equals("C01"));
        indicadoresOverlay.get("indPasilloNullDer").setOn(columnaElegida.equals("C08"));
        // se obtiene el pasillo elegido para extraer la lista de servidores de la(s) columna(s) involucradas
        HotAisleDefinition pasilloCalienteElegido = obtenerPasilloCalienteElegido();
        labels.get("lblPasilloElegidoValor").setText(pasilloCalienteElegido.displayName());
        Map<ServerLocation, ServerEnergySnapshot> energiaPorUbicacion = obtenerEnergiaPorUbicacion();
        Map<ServerLocation, ServerTemperatureSnapshot> temperaturaPorUbicacion = obtenerTemperaturaPorUbicacion();
        List<Server> servidoresPasilloCaliente = datacenter
                .getServers()
                .stream()
                .filter(server -> pasilloCalienteElegido.columns().contains(server.getLocation().column()))
                .toList();
        // se inicializan las variables para el cálculo de los promedios necesarios
        int servidoresOnline = 0;
        int servidoresInstalados = 0;
        float temperaturaPromedio = 0;
        float temperaturaMaxima = minServerTemperatureCelsius;
        float cargaPromedio = 0;
        // se acumulan los valores a promediar y se busca la ubicación del servidor que está alcanzando la mayor temperatura,
        // con esta ubicación se determina el código del indicador respectivo para actualizar su estado
        String ladoServidorTemperaturaMaxima = "";
        String rackServidorTemperaturaMaxima = "";
        for (Server server : servidoresPasilloCaliente) {
            ServerLocation location = server.getLocation();
            ServerTemperatureSnapshot temperature = temperaturaPorUbicacion.get(location);
            ServerEnergySnapshot energy = energiaPorUbicacion.get(location);
            if (temperature == null || energy == null) continue;
            float temperaturaServidor = (float) temperature.temperatureCelsius();
            if (temperaturaServidor > temperaturaMaxima) {
                temperaturaMaxima = temperaturaServidor;
                if (server.getLocation().column().equals(PROPS.getProperty("datacenter.first.column"))) ladoServidorTemperaturaMaxima = "Der";
                else if (server.getLocation().column().equals(PROPS.getProperty("datacenter.last.column"))) ladoServidorTemperaturaMaxima = "Izq";
                else {
                    int n = Integer.parseInt(server.getLocation().column().replace("C", ""));
                    if (n % 2 == 0) ladoServidorTemperaturaMaxima = "Izq";
                    else ladoServidorTemperaturaMaxima = "Der";
                }
                rackServidorTemperaturaMaxima = server.getLocation().rackCode().value().replace("R", "");
            }
            temperaturaPromedio += (float) temperature.temperatureCelsius();
            servidoresInstalados++;
            if (server.getStatus() != HardwareStatus.OFFLINE) {
                cargaPromedio += (float) energy.utilization();
                servidoresOnline++;
            }
        }
        // se actualiza el estado del indicador del servidor que está alcanzando la temperatura máxima
        indicadoresPasilloElegidoServidorTemperaturaMaxima.values().forEach(ind -> ind.setOn(false));
        String codigoIndicadorServidorTemperaturaMaxima = "indPasilloElegidoServidorTemperaturaMaxima" + ladoServidorTemperaturaMaxima + rackServidorTemperaturaMaxima;
        Indicator indServidorTemperaturaMaxima = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(codigoIndicadorServidorTemperaturaMaxima);
        indServidorTemperaturaMaxima.setOn(true);
        temperaturaPromedio /= servidoresInstalados;
        cargaPromedio /= servidoresOnline;
        // se actualizan los colores de los labels/indicadores necesarios
        Label lblPasilloElegidoTemperaturaPromedioValor = labels.get("lblPasilloElegidoTemperaturaPromedioValor");
        Label lblPasilloElegidoTemperaturaMaximaValor = labels.get("lblPasilloElegidoTemperaturaMaximaValor");
        int colorRangoTemperaturaPromedio = obtenerColorRangoTemperatura(temperaturaPromedio);
        lblPasilloElegidoTemperaturaPromedioValor.setTextColor(colorRangoTemperaturaPromedio);
        int colorRangoTemperaturaMaxima = obtenerColorRangoTemperatura(temperaturaMaxima);
        lblPasilloElegidoTemperaturaMaximaValor.setTextColor(colorRangoTemperaturaMaxima);
        indServidorTemperaturaMaxima.setOnColor(colorRangoTemperaturaMaxima);
        // se cargan los valores a mostrar en pantalla
        lblPasilloElegidoTemperaturaPromedioValor.setText(String.format(PROPS.getProperty("number.format.temperature"), temperaturaPromedio));
        lblPasilloElegidoTemperaturaMaximaValor.setText(String.format(PROPS.getProperty("number.format.temperature"), temperaturaMaxima));
        labels.get("lblPasilloElegidoCargaITValor").setText(String.format(PROPS.getProperty("number.format.percentage"), cargaPromedio * 100));

        // *** CONTINUAR AQUÍ *****************************************************************************
        // CARGAR LAS FLECHAS DE AIRE FRÍO COMO INDICADORES PARA MOSTRARLOS/OCULTARLOS DE SER NECESARIO
        // MODIFICAR EL OVERLAY DE PASILLO NULL PARA QUE NO INCLUYA LAS FLECHAS
        // ************************************************************************************************
    }

    private int obtenerColorRangoTemperatura(float temperatura) {
        if (temperatura >= Float.parseFloat(PROPS.getProperty("simulation.health.temperature.alert-threshold-celsius")))
            return COLOR_LABEL_MAGENTA;
        else if (temperatura >= Float.parseFloat(PROPS.getProperty("simulation.health.temperature.warning-threshold-celsius")))
            return COLOR_LABEL_AMARILLO;
        else return COLOR_LABEL_VERDE;
    }

    private HotAisleDefinition obtenerPasilloCalienteElegido() {
        HotAisleDefinition hotAisle = hotAisleByColumn.get(columnaElegida);
        if (hotAisle == null) throw new IllegalArgumentException("No hot aisle configured for column: " + columnaElegida);
        return hotAisle;
    }

    private Map<ServerLocation, ServerEnergySnapshot> obtenerEnergiaPorUbicacion() {
        return energySnapshot.servers()
                .stream()
                .collect(Collectors.toMap(
                        server -> new ServerLocation(
                                server.column(),
                                server.rackCode(),
                                server.slot()
                        ),
                        Function.identity()
                ));
    }

    private Map<ServerLocation, ServerTemperatureSnapshot> obtenerTemperaturaPorUbicacion() {
        return temperatureSnapshot.servers()
                .stream()
                .collect(Collectors.toMap(
                        server -> new ServerLocation(
                                server.column(),
                                server.rackCode(),
                                server.slot()
                        ),
                        Function.identity()
                ));
    }

    private Map<ServerLocation, ServerHealthSnapshot> obtenerSaludPorUbicacion() {
        return healthSnapshot.servers()
                .stream()
                .collect(Collectors.toMap(
                        server -> new ServerLocation(
                                server.column(),
                                server.rackCode(),
                                server.slot()
                        ),
                        Function.identity()
                ));
    }

    private void dibujarFondo() {
        pushStyle();
        imageMode(CORNER);
        image(fondo, 0, 0, width, height);
        popStyle();
    }

    private void dibujarOverlayEstatico() {
        pushStyle();
        imageMode(CORNER);
        image(overlayEstatico, 0, 0, width, height);
        popStyle();
    }

    @Override
    public void keyReleased() {
        if (key == 'm') showOverlayEstatico = !showOverlayEstatico;
        else if (keyCode == BARRA_ESPACIADORA) timerSimulacion.toggle();
        else if (keyCode == 49) columnaElegida = "C01";
        else if (keyCode == 50) columnaElegida = "C02";
        else if (keyCode == 51) columnaElegida = "C03";
        else if (keyCode == 52) columnaElegida = "C04";
        else if (keyCode == 53) columnaElegida = "C05";
        else if (keyCode == 54) columnaElegida = "C06";
        else if (keyCode == 55) columnaElegida = "C07";
        else if (keyCode == 56) columnaElegida = "C08";

        else if (keyCode == 81) rackElegido = "R01";
        else if (keyCode == 87) rackElegido = "R02";
        else if (keyCode == 69) rackElegido = "R03";
        else if (keyCode == 82) rackElegido = "R04";
        else if (keyCode == 84) rackElegido = "R05";
        else if (keyCode == 89) rackElegido = "R06";
        else if (keyCode == 85) rackElegido = "R07";
        else if (keyCode == 73) rackElegido = "R08";
        else if (keyCode == 79) rackElegido = "R09";
        else if (keyCode == 80) rackElegido = "R10";
        else if (keyCode == -431) rackElegido = "R11";
        else if (keyCode == 43) rackElegido = "R12";
        else if (key == 'c') {
            String s = "indPasilloElegidoServidorTemperaturaMaximaDer01";
            Indicator ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaDer02";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaDer03";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaDer04";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaDer05";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaDer06";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaDer07";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaDer08";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaDer09";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaDer10";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaDer11";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaDer12";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaIzq01";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaIzq02";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaIzq03";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaIzq04";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaIzq05";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaIzq06";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaIzq07";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaIzq08";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaIzq09";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaIzq10";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaIzq11";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
            s = "indPasilloElegidoServidorTemperaturaMaximaIzq12";
            ind = indicadoresPasilloElegidoServidorTemperaturaMaxima.get(s);
            ind.setOn(!ind.isOn());
        }
        // *****************************************************************
        //* ******** DEBE INVOCARSE EN CADA CAMBIO DE RACK/COLUMNA *********
        obtenerPasilloCalienteElegido();
        updateUI = true;
        // *****************************************************************
    }

}
