package com.cpz.sim.datacenter.ui.simulation;

import com.cpz.processing.controls.controls.button.Button;
import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.factory.CoolingConfigurationFactory;
import com.cpz.sim.datacenter.factory.DatacenterFactory;
import com.cpz.sim.datacenter.factory.WorkloadFactorProviderFactory;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.app.Initializable;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;
import com.cpz.sim.datacenter.workload.NoiseWorkloadSource;
import com.cpz.sim.datacenter.workload.ScaledWorkloadSource;
import com.cpz.sim.datacenter.workload.ServerWorkloadFactorProvider;
import com.cpz.sim.datacenter.workload.WorkloadSource;
import com.cpz.sim.foundation.time.SimulationClock;
import com.cpz.utils.noise.FractalNoise;
import com.cpz.utils.noise.PerlinNoise;
import com.cpz.utils.time.Timer;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
