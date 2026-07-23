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
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshot;
import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshotProvider;
import com.cpz.sim.datacenter.snapshot.ServerEnergySnapshot;
import com.cpz.sim.datacenter.system.EnergyConsumptionSystem;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.system.WorkloadSystem;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private Map<String, Indicator> indicators;
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
    private String columnaElegida, rackElegido;
    private Map<String, Rack> racks;

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
        // indicators
        controles = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicator.json");
        indicators = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicators.put(ind.getCode(), (Indicator) ind));
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
        EnergyConsumptionSystem energySystem = new EnergyConsumptionSystem(datacenter);
        TemperatureSystemOptions temperatureOptions = new TemperatureSystemOptionsFactory().create(definition);
        TemperatureSystem temperatureSystem = new TemperatureSystem(datacenter, temperatureOptions, new SimpleServerTemperatureModel());
        engine.register(new WorkloadSystem(datacenter, workloadSource));
        engine.register(new PowerConsumptionSystem(datacenter));
        engine.register(temperatureSystem);
        engine.register(energySystem);
        // snapshots
        energySnapshotProvider = new EnergyConsumptionSnapshotProvider(datacenter, energySystem);
        // timers
        timerSimulacion = new Timer();
        timerSimulacion.setPeriodMillis(1000);
        timerSimulacion.start();
        // debug
        showOverlayEstatico = true;
        columnaElegida = "C01";
        rackElegido = "R01";
    }

    public void draw() {
        // update
        updateClock();
        updateSnapshots();
        updateControles();

        overlayManager.getActiveOverlays().forEach(entry -> entry.getRender().run());
        //draw
        dibujarFondo();
        labels.values().forEach(Label::draw);
        /*
        se debe asegurar que los indSlotIAXX se dibujen siempre por encima de los indSlotXX, para
        ello los filtramos de la lista general y luego dibujamos encima solamente los indSlotIAXX
        */
        indicators.values().stream().filter(ind -> !ind.getCode().contains("IA")).forEach(Indicator::draw);
        indicators.values().stream().filter(ind -> ind.getCode().contains("IA")).forEach(Indicator::draw);
        if (showOverlayEstatico) dibujarOverlayEstatico();

    }

    private void updateClock() {
        if (timerSimulacion == null || engine == null) return;
        if (!timerSimulacion.pollPeriodPulse()) return;
        //SimulationTick tick = engine.step();
        engine.step();
        updateSnapshots = true;
    }

    private void updateSnapshots() {
        if (!updateSnapshots) return;
        energySnapshot = energySnapshotProvider.snapshot(engine.currentTick());
        //printEnergySnapshot(energySnapshot);
        updateSnapshots = false;
        updateUI = true;
    }


    private void updateControles() {
        if (!updateUI) return;
        updateRackElegido();
        updateUI = false;
    }

    private void updateRackElegido() {
        Rack rack = obtenerRackElegido();
        Map<ServerLocation, ServerEnergySnapshot> energiaPorUbicacion = obtenerEnergiaPorUbicacion();
        int racksInstalados = 0;
        float cargaTotal = 0;
        float potenciaAcumulada = 0;
        for (String slot : rack.getSlotCodes()) {
            ServerLocation location = new ServerLocation(columnaElegida, new RackCode(rackElegido), slot);
            Optional<Server> installedServer = datacenter.getServer(location);
            Label lblSlotCarga = labels.get("lblSlotCarga" + slot.replace("S", ""));
            Label lblSlotPotencia = labels.get("lblSlotPotencia" + slot.replace("S", ""));
            Indicator indSlotVacio = indicators.get("indSlotVacio" + slot.replace("S", ""));
            Indicator indSlotOffline = indicators.get("indSlotOffline" + slot.replace("S", ""));
            Indicator indSlotStatusOk = indicators.get("indSlotOk" + slot.replace("S", ""));
            Indicator indSlotStatusAlerta = indicators.get("indSlotAlerta" + slot.replace("S", ""));
            float limiteCargaAlerta = Float.parseFloat(PROPS.getProperty("simulation.server.load-alert-threshold"));
            if (installedServer.isEmpty()) {
                lblSlotCarga.setTextColor(COLOR_LABEL_BLANCO);
                lblSlotCarga.setText("--");
                lblSlotPotencia.setText("--");
                indSlotVacio.setOn(true);
                indSlotOffline.setOn(false);
                indSlotStatusOk.setOn(false);
                indSlotStatusAlerta.setOn(false);
            } else {
                ServerEnergySnapshot energia = energiaPorUbicacion.get(location);
                lblSlotCarga.setTextColor(COLOR_LABEL_AZUL);
                lblSlotCarga.setText(String.format("%.0f%%", energia.utilization() * 100));
                lblSlotPotencia.setText(String.format("%.2fkW", energia.currentPowerWatts() / 1000));
                cargaTotal += energia.utilization();
                potenciaAcumulada += energia.currentPowerWatts();
                racksInstalados++;
                indSlotVacio.setOn(false);
                boolean bOffline = installedServer.get().getStatus() == HardwareStatus.OFFLINE;
                boolean bAlerta = installedServer.get().getUtilization() >= limiteCargaAlerta; // TEMPORAL - HAY QUE MODIFICAR BACKEND
                indSlotOffline.setOn(bOffline);
                indSlotStatusOk.setOn(!bOffline && !bAlerta);
                indSlotStatusAlerta.setOn(!bOffline && bAlerta);
            }
            float cargaPromedio = cargaTotal / racksInstalados;
            labels.get("lblRackCargaPromedioValor").setText(String.format("%.0f%%", cargaPromedio * 100));
            labels.get("lblRackPotenciaAcumuladaValor").setText(String.format("%.2fkW", potenciaAcumulada / 1000));
        }
    }

    private Rack obtenerRackElegido() {
        return datacenter
                .findRack(columnaElegida, rackElegido)
                .orElseThrow(() -> new IllegalStateException("Rack no encontrado: " + columnaElegida + "-" + rackElegido));
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
        else if (key == 'd') {
            Indicator ind;
            for (int i = 0; i < 12; i++) {
                String s = "indSlotVacio" + String.format("%02d", i + 1);
                ind = indicators.get(s);
                //if (ind != null) ind.setOn(!ind.isOn());
            }
        } else if (keyCode == BARRA_ESPACIADORA) {
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
