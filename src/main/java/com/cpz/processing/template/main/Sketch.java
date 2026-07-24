package com.cpz.processing.template.main;

import com.cpz.processing.controls.controls.Control;
import com.cpz.processing.controls.controls.config.ControlConfigLoader;
import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.core.input.InputManager;
import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.processing.controls.input.ProcessingKeyboardAdapter;
import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.factory.TemperatureSystemOptionsFactory;
import com.cpz.sim.datacenter.factory.WorkloadFactorProviderFactory;
import com.cpz.sim.datacenter.health.HealthThreshold;
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
import com.cpz.utils.noise.FractalNoise;
import com.cpz.utils.noise.PerlinNoise;
import com.cpz.utils.time.Timer;
import processing.core.PApplet;
import processing.core.PImage;
import processing.opengl.PJOGL;

import java.io.File;
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
    private Map<String, Indicator> indicadores, indicadoresSlotIA, indicadoresAlerta;
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
    private String columnaElegida, rackElegido;
    private Map<String, Rack> racks;
    private float potenciaIdleTotalRackElegido, potenciaMaximaTotalRackElegido;

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
        // font
        textFont(createFont("data" + File.separator + "font" + File.separator + "JetBrainsMono.ttf", 56, true));
        // imágenes
        fondo = loadImage("data" + File.separator + "img" + File.separator + "ui_fondo.png");
        overlayEstatico = loadImage("data" + File.separator + "img" + File.separator + "ui_overlay.png");

        // datacenter
        Path configPath = Path.of("data/config/datacenter-test.json");
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
        TemperatureSystem temperatureSystem = new TemperatureSystem(datacenter, temperatureOptions, new SimpleServerTemperatureModel());
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
        healthSnapshotProvider = new HealthSnapshotProvider(datacenter, healthSystem, temperatureSystem);
        // timers
        timerSimulacion = new Timer();
        timerSimulacion.setPeriodMillis(1000);
        timerSimulacion.start();
        // debug
        showOverlayEstatico = true;
        columnaElegida = "C01";
        rackElegido = "R01";
        calcularLimitesPotenciaRackElegido();
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
        indicadoresSlotIA.values().forEach(Indicator::draw);
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
        healthSnapshot = healthSnapshotProvider.snapshot(engine.currentTick());
        updateSnapshots = false;
        updateUI = true;
    }


    private void updateControles() {
        if (!updateUI) return;
        updatePanelRackElegido();
        updateUI = false;
    }

    private void updatePanelRackElegido() {
        labels.get("lblRackElegidoValor").setText(columnaElegida + "-" + rackElegido);
        Rack rack = obtenerRackElegido();
        Map<ServerLocation, ServerEnergySnapshot> energiaPorUbicacion = obtenerEnergiaPorUbicacion();
        Map<ServerLocation, ServerHealthSnapshot> saludPorUbicacion = obtenerSaludPorUbicacion();
        int servidoresInstalados = 0;
        double cargaTotal = 0;
        float potenciaAcumulada = 0;
        for (String slot : rack.getSlotCodes()) {
            ServerLocation location = new ServerLocation(columnaElegida, new RackCode(rackElegido), slot);
            Optional<Server> installedServer = datacenter.getServer(location);
            Label lblSlotCarga = labels.get("lblSlotCarga" + slot.replace("S", ""));
            Label lblSlotPotencia = labels.get("lblSlotPotencia" + slot.replace("S", ""));
            Indicator indSlotVacio = indicadores.get("indSlotVacio" + slot.replace("S", ""));
            Indicator indSlotOffline = indicadores.get("indSlotOffline" + slot.replace("S", ""));
            Indicator indSlotStatusOk = indicadores.get("indSlotOk" + slot.replace("S", ""));
            Indicator indSlotStatusAlerta = indicadores.get("indSlotAlerta" + slot.replace("S", ""));
            Indicator indAlerta = indicadoresAlerta.get("indAlerta" + slot.replace("S", ""));
            Indicator indSlotIA1 = indicadoresSlotIA.get("indSlotIA" + slot.replace("S", "") + "-1");
            Indicator indSlotIA2 = indicadores.get("indSlotIA" + slot.replace("S", "") + "-2");
            if (installedServer.isEmpty()) {
                mostrarServidorOffline(lblSlotCarga, lblSlotPotencia, indSlotVacio, indSlotOffline, indSlotStatusOk, indSlotStatusAlerta, indAlerta, indSlotIA1, indSlotIA2);
            } else {
                ServerEnergySnapshot energia = energiaPorUbicacion.get(location);
                lblSlotCarga.setTextColor(COLOR_LABEL_AZUL);
                lblSlotCarga.setText(String.format("%.0f%%", energia.utilization() * 100));
                lblSlotPotencia.setText(String.format("%.2fkW", energia.currentPowerWatts() / 1000));
                cargaTotal += energia.utilization();
                potenciaAcumulada += energia.currentPowerWatts();
                servidoresInstalados++;
                indSlotVacio.setOn(false);
                indSlotOffline.setOn(false);
                indSlotStatusOk.setOn(false);
                indAlerta.setOn(false);
                indSlotStatusAlerta.setOn(false);
                indSlotIA2.setOn(false);
                ServerHealthSnapshot salud = saludPorUbicacion.get(location);
                if (salud == null) throw new IllegalStateException("No existe snapshot de salud para el servidor: " + location);
                HardwareStatus status = salud.status();
                switch (status) {
                    case OFFLINE -> indSlotOffline.setOn(true);
                    case OK -> indSlotStatusOk.setOn(true);
                    case ALERT -> {
                        indAlerta.setOn(true);
                        indSlotStatusAlerta.setOn(true);
                    }
                }
                boolean serverIA = installedServer.get().getRole() == ServerRole.AI;
                indSlotIA1.setOn(serverIA);
                indSlotIA2.setOn(serverIA);
            }
        }
        double cargaPromedio = cargaTotal / servidoresInstalados;
        labels.get("lblRackCargaPromedioValor").setText(String.format("%.0f%%", cargaPromedio * 100));
        labels.get("lblRackPotenciaAcumuladaValor").setText(String.format("%.2fkW", potenciaAcumulada / 1000));
        actualizarBarra("RackElegidoPotencia", potenciaAcumulada, potenciaIdleTotalRackElegido, potenciaMaximaTotalRackElegido);
    }

    private void mostrarServidorOffline(
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
        Objects.requireNonNull(lblSlotCarga);
        Objects.requireNonNull(lblSlotPotencia);
        Objects.requireNonNull(indSlotVacio);
        Objects.requireNonNull(indSlotOffline);
        Objects.requireNonNull(indSlotStatusOk);
        Objects.requireNonNull(indSlotStatusAlerta);
        Objects.requireNonNull(indAlerta);
        Objects.requireNonNull(indSlotIA1);
        Objects.requireNonNull(indSlotIA2);
        lblSlotCarga.setTextColor(COLOR_LABEL_BLANCO);
        lblSlotCarga.setText("--");
        lblSlotPotencia.setText("--");
        indSlotVacio.setOn(true);
        indSlotOffline.setOn(false);
        indSlotStatusOk.setOn(false);
        indSlotStatusAlerta.setOn(false);
        indAlerta.setOn(false);
        indSlotIA1.setOn(false);
        indSlotIA2.setOn(false);
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
        if (key == 'e') showOverlayEstatico = !showOverlayEstatico;
        else if (keyCode == BARRA_ESPACIADORA) {
            if (timerSimulacion.isRunning()) timerSimulacion.stop();
            else timerSimulacion.start();
        }
    }

    private void printEnergySnapshot(EnergyConsumptionSnapshot snapshot) {
        System.out.printf(
                Locale.US,
                "Tick: %d | Simulated time: %.0f m | IT power: %.1f W | Energy: %.4f kWh | Servers: %d%n",
                snapshot.tickIndex(),
                snapshot.elapsedSeconds() / 60,
                snapshot.totalItPowerWatts(),
                snapshot.consumedEnergyKWh(),
                snapshot.serverCount()
        );
        snapshot.servers().forEach(server ->
                System.out.printf(
                        Locale.US,
                        "  %-4s | %-4s | %-7s | "
                                + "Util: %6.2f %% | Power: %6.1f W | Status: %s%n",
                        server.rackCode(),
                        server.slot(),
                        server.serverCode(),
                        server.utilization() * 100.0f,
                        server.currentPowerWatts(),
                        server.status()
                )
        );
    }
}
