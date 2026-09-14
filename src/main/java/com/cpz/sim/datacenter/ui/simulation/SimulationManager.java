package com.cpz.sim.datacenter.ui.simulation;

import com.cpz.processing.controls.controls.button.Button;
import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.cooling.CoolingSnapshotCoordinator;
import com.cpz.sim.datacenter.cooling.CoolingUnitType;
import com.cpz.sim.datacenter.cooling.DatacenterCoolingTickInputProvider;
import com.cpz.sim.datacenter.factory.CoolingConfigurationFactory;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.factory.TemperatureSystemOptionsFactory;
import com.cpz.sim.datacenter.factory.WorkloadFactorProviderFactory;
import com.cpz.sim.datacenter.health.HealthThreshold;
import com.cpz.sim.datacenter.health.ServerHealthOptions;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.snapshot.DatacenterOperationalSnapshotProvider;
import com.cpz.sim.datacenter.snapshot.EnergyConsumptionSnapshotProvider;
import com.cpz.sim.datacenter.snapshot.HealthSnapshotProvider;
import com.cpz.sim.datacenter.snapshot.ServerGroupDefinition;
import com.cpz.sim.datacenter.snapshot.TemperatureSnapshotProvider;
import com.cpz.sim.datacenter.system.CoolingSystem;
import com.cpz.sim.datacenter.system.EnergyConsumptionSystem;
import com.cpz.sim.datacenter.system.PowerConsumptionSystem;
import com.cpz.sim.datacenter.system.ServerHealthSystem;
import com.cpz.sim.datacenter.system.TemperatureSystem;
import com.cpz.sim.datacenter.system.WorkloadSystem;
import com.cpz.sim.datacenter.temperature.CoolingSnapshotTemperatureReferenceProvider;
import com.cpz.sim.datacenter.temperature.SimpleServerTemperatureModel;
import com.cpz.sim.datacenter.temperature.TemperatureSystemOptions;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.app.Initializable;
import com.cpz.sim.datacenter.ui.config.HotAisleConfiguration;
import com.cpz.sim.datacenter.ui.config.HotAisleConfigurationLoader;
import com.cpz.sim.datacenter.ui.config.HotAisleDefinition;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;
import com.cpz.sim.datacenter.workload.NoiseWorkloadSource;
import com.cpz.sim.datacenter.workload.ScaledWorkloadSource;
import com.cpz.sim.datacenter.workload.ServerWorkloadFactorProvider;
import com.cpz.sim.datacenter.workload.WorkloadSource;
import com.cpz.sim.foundation.engine.SimulationEngine;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.utils.noise.FractalNoise;
import com.cpz.utils.noise.PerlinNoise;
import com.cpz.utils.time.Timer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.cpz.sim.datacenter.ui.main.Launcher.PROPS;

/**
 * @author CPZ
 */
public class SimulationManager extends ApplicationComponent implements Initializable {

    private final SimulationContainer simulationContainer;
    private final UiComponentContainer uiComponentContainer;

    public SimulationManager(
            ApplicationContext applicationContext,
            SimulationContainer simulationContainer,
            UiComponentContainer uiComponentContainer
    ) {
        super(applicationContext);
        this.simulationContainer = simulationContainer;
        this.uiComponentContainer = uiComponentContainer;
    }

    @Override
    public void initialize() {
        initializeTimer();
        initializeDatacenter();
        initializeWorkloads();
        initializeEngine();
        initializeSystems();
        initializeSnapshots();
        initializeHotAisles();
        initializeOperationalSnapshots();
    }

    private void initializeTimer() {
        simulationContainer.setSimulationBasePeriodMillis(Integer.parseInt(PROPS.getProperty("simulation.timer.base-period-ms")));
        simulationContainer.setSimulationSpeedFactors(parseSimulationSpeedFactors(PROPS.getProperty("simulation.timer.speed-factors")));
        simulationContainer.setSimulationSpeedFactorIndex(findSimulationSpeedFactorIndex(Double.parseDouble(PROPS.getProperty("simulation.timer.initial-speed-factor"))));
        simulationContainer.setSimulationTimer(new Timer());
        simulationContainer.simulationTimer().setPeriodMillis(1000);
        updateSimulationTimerPeriod();
        updateSimulationControls();
    }

    private List<Double> parseSimulationSpeedFactors(String rawFactors) {
        if (rawFactors == null || rawFactors.isBlank())
            throw new IllegalArgumentException("simulation.timer.speed-factors must not be empty");
        List<Double> factors = Arrays.stream(rawFactors.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Double::parseDouble)
                .toList();
        if (factors.isEmpty())
            throw new IllegalArgumentException("simulation.timer.speed-factors must contain at least one value");
        if (factors.stream().anyMatch(factor -> factor <= 0.0))
            throw new IllegalArgumentException("simulation.timer.speed-factors must contain only positive values");
        return factors;
    }

    private int findSimulationSpeedFactorIndex(double initialFactor) {
        for (int i = 0; i < simulationContainer.simulationSpeedFactors().size(); i++) {
            if (Double.compare(simulationContainer.simulationSpeedFactors().get(i), initialFactor) == 0) return i;
        }
        throw new IllegalArgumentException("simulation.timer.initial-speed-factor must exist in simulation.timer.speed-factors");
    }

    private String formatSimulationSpeedFactor() {
        double factor = simulationContainer.simulationSpeedFactors().get(simulationContainer.simulationSpeedFactorIndex());
        if (factor == Math.rint(factor)) return "x" + (int) factor;
        return "x" + factor;
    }

    private void initializeDatacenter() {
        Path configPath = Path.of("data/config/datacenter-test-complete-rezoned-edge-cases-custom-v2.json");
        DatacenterDefinition definition = new JsonDatacenterConfigLoader().load(configPath);
        simulationContainer.setDatacenter(new DatacenterFactory().create(definition));
        simulationContainer.setRoomName(definition.layout().room().name());
        simulationContainer.setHotAisleByColumn(new HashMap<>());
        simulationContainer.setCoolingConfiguration(
                new CoolingConfigurationFactory()
                        .create(definition, simulationContainer.datacenter())
                        .orElseThrow(() -> new IllegalStateException(
                                "The datacenter configuration does not contain a cooling block."
                        ))
        );
        Map<String, Rack> racks = new HashMap<>();
        for (Rack rack : simulationContainer.datacenter().getRacks())
            racks.put(rack.getCode().value(), rack);
        simulationContainer.setRacks(racks);
        simulationContainer.setDatacenterDefinition(definition);
    }

    private void initializeWorkloads() {
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
        ServerWorkloadFactorProvider factorProvider =
                new WorkloadFactorProviderFactory()
                        .create(simulationContainer.datacenterDefinition());
        simulationContainer.setWorkloadSource(
                new ScaledWorkloadSource(
                        baseWorkloadSource,
                        factorProvider
                )
        );
        simulationContainer.setClock(new SimulationClock(Duration.ofMinutes(1)));
    }

    private void initializeEngine() {
        simulationContainer.setEngine(new SimulationEngine(simulationContainer.clock()));
    }

    private void initializeSystems() {
        simulationContainer.setEnergySystem(new EnergyConsumptionSystem(simulationContainer.datacenter()));
        simulationContainer.setCoolingSystem(new CoolingSystem(simulationContainer.coolingConfiguration()));
        simulationContainer.setCoolingTemperatureReferenceProvider(new CoolingSnapshotTemperatureReferenceProvider(simulationContainer.coolingConfiguration()));
        simulationContainer.setCoolingSnapshotCoordinator(
                new CoolingSnapshotCoordinator(
                        new DatacenterCoolingTickInputProvider(simulationContainer.datacenter()),
                        simulationContainer.coolingSystem(),
                        simulationContainer.coolingTemperatureReferenceProvider()
                )
        );
        TemperatureSystemOptions temperatureOptions = new TemperatureSystemOptionsFactory().create(simulationContainer.datacenterDefinition());
        simulationContainer.setTemperatureOptions(temperatureOptions);
        simulationContainer.setTemperatureSystem(
                new TemperatureSystem(
                        simulationContainer.datacenter(),
                        simulationContainer.temperatureOptions(),
                        new SimpleServerTemperatureModel(),
                        simulationContainer.coolingTemperatureReferenceProvider()
                )
        );
        HealthThreshold utilizationThreshold = new HealthThreshold(
                Double.parseDouble(PROPS.getProperty("simulation.health.utilization.alert-threshold")),
                Double.parseDouble(PROPS.getProperty("simulation.health.utilization.recovery-threshold"))
        );
        HealthThreshold temperatureThreshold = new HealthThreshold(
                Double.parseDouble(PROPS.getProperty("simulation.health.temperature.alert-threshold-celsius")),
                Double.parseDouble(PROPS.getProperty("simulation.health.temperature.recovery-threshold-celsius"))
        );
        simulationContainer.setHealthSystem(new ServerHealthSystem(simulationContainer.datacenter(), simulationContainer.temperatureSystem(), new ServerHealthOptions(utilizationThreshold, temperatureThreshold)));
        simulationContainer.engine().register(new WorkloadSystem(simulationContainer.datacenter(), simulationContainer.workloadSource()));
        simulationContainer.engine().register(new PowerConsumptionSystem(simulationContainer.datacenter()));
        simulationContainer.engine().register(tick -> simulationContainer.setCoolingSnapshot(simulationContainer.coolingSnapshotCoordinator().update(tick)));
        simulationContainer.engine().register(simulationContainer.temperatureSystem());
        simulationContainer.engine().register(simulationContainer.healthSystem());
        simulationContainer.engine().register(simulationContainer.energySystem());
    }

    private void initializeSnapshots() {
        simulationContainer.setEnergySnapshotProvider(
                new EnergyConsumptionSnapshotProvider(simulationContainer.datacenter(),
                        simulationContainer.energySystem()
                )
        );
        simulationContainer.setTemperatureSnapshotProvider(
                new TemperatureSnapshotProvider(
                        simulationContainer.datacenter(),
                        simulationContainer.temperatureSystem(),
                        simulationContainer.temperatureOptions()
                )
        );
        simulationContainer.setHealthSnapshotProvider(
                new HealthSnapshotProvider(
                        simulationContainer.datacenter(),
                        simulationContainer.healthSystem(),
                        simulationContainer.temperatureSystem()
                )
        );
    }

    private void initializeHotAisles() {
        Path configurationPath = Path.of(sketch().dataPath("config" + File.separator + "hot-aisle-mapping.json"));
        HotAisleConfigurationLoader loader = new HotAisleConfigurationLoader();
        try {
            HotAisleConfiguration configuration = loader.load(configurationPath);
            simulationContainer.setHotAisleConfiguration(configuration);
            initializeHotAisleMapping(configuration);
        } catch (IOException e) {
            throw new RuntimeException("Could not load hot aisle configuration", e);
        }
    }

    private void initializeHotAisleMapping(HotAisleConfiguration configuration) {
        simulationContainer.hotAisleByColumn().clear();
        for (HotAisleDefinition hotAisle : configuration.hotAisles()) {
            for (String column : hotAisle.columns()) {
                HotAisleDefinition previous = simulationContainer.hotAisleByColumn().put(column, hotAisle);
                if (previous != null)
                    throw new IllegalArgumentException("Column '%s' is assigned to hot aisles '%s' and '%s'".formatted(column, previous.code(), hotAisle.code()));
            }
        }
    }

    private void initializeOperationalSnapshots() {
        List<ServerGroupDefinition> operationalGroups = createOperationalGroups(simulationContainer.hotAisleConfiguration());
        simulationContainer.setOperationalSnapshotProvider(new DatacenterOperationalSnapshotProvider(simulationContainer.datacenter(), operationalGroups));
    }

    private List<ServerGroupDefinition> createOperationalGroups(HotAisleConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        return configuration
                .hotAisles()
                .stream()
                .map(hotAisle -> {
                            Set<ServerLocation> serverLocations =
                                    simulationContainer.datacenter()
                                            .getServers()
                                            .stream()
                                            .map(Server::getLocation)
                                            .filter(location -> hotAisle.columns().contains(location.column()))
                                            .collect(Collectors.toUnmodifiableSet());
                            return new ServerGroupDefinition(hotAisle.code(), serverLocations);
                        }
                )
                .toList();
    }

    public void initializeCoolingToggleGroups() {
        List<String> supplyToggleCodes = new ArrayList<>();
        simulationContainer.coolingSnapshot()
                .units()
                .stream()
                .filter(unit -> unit.type() == CoolingUnitType.SUPPLY)
                .forEach(unit -> {
                    String toggleCode = unit.unitCode().replace("SUPPLY-", "tglSupply");
                    supplyToggleCodes.add(toggleCode);
                });
        simulationContainer.setSupplyToggleCodes(supplyToggleCodes);
        List<String> exhaustToggleCodes = new ArrayList<>();
        simulationContainer.coolingSnapshot()
                .units()
                .stream()
                .filter(unit -> unit.type() == CoolingUnitType.EXHAUST)
                .forEach(unit -> {
                    String toggleCode = unit.unitCode().replace("EXHAUST-", "tglExhaust");
                    exhaustToggleCodes.add(toggleCode);
                });
        simulationContainer.setExhaustToggleCodes(exhaustToggleCodes);
    }

    public void initializeInitialSimulationState() {
        simulationContainer.engine().step();
    }

    public void updateSnapshots() {
        simulationContainer.setEnergySnapshot(simulationContainer.energySnapshotProvider().snapshot(simulationContainer.engine().currentTick()));
        simulationContainer.setTemperatureSnapshot(simulationContainer.temperatureSnapshotProvider().snapshot(simulationContainer.engine().currentTick()));
        simulationContainer.setHealthSnapshot(simulationContainer.healthSnapshotProvider().snapshot(simulationContainer.engine().currentTick()));
        simulationContainer.setOperationalSnapshot(
                simulationContainer
                        .operationalSnapshotProvider()
                        .snapshot(
                                simulationContainer.energySnapshot(),
                                simulationContainer.temperatureSnapshot(),
                                simulationContainer.healthSnapshot()
                        )
        );
    }

    public void updateSimulationTimerPeriod() {
        double factor = simulationContainer.simulationSpeedFactors().get(simulationContainer.simulationSpeedFactorIndex());
        int periodMillis = (int) Math.round(simulationContainer.simulationBasePeriodMillis() / factor);
        simulationContainer.simulationTimer().setPeriodMillis(periodMillis);
    }

    public void updateSimulationControls() {
        boolean simulationRunning = simulationContainer.simulationTimer().isRunning();
        String speedLabel = formatSimulationSpeedFactor();
        uiComponentContainer.labels().get("lblSimulationValue").setText(simulationRunning ? "Running " + speedLabel : "Stopped " + speedLabel);
        Button btnPlayMinus = uiComponentContainer.buttonsPlay().get("btnPlayMinus");
        if (btnPlayMinus != null) btnPlayMinus.setEnabled(simulationContainer.simulationSpeedFactorIndex() > 0);
        Button btnPlayPlus = uiComponentContainer.buttonsPlay().get("btnPlayPlus");
        if (btnPlayPlus != null)
            btnPlayPlus.setEnabled(simulationContainer.simulationSpeedFactorIndex() < simulationContainer.simulationSpeedFactors().size() - 1);
    }

}
