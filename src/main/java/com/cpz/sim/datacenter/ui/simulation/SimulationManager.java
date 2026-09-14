package com.cpz.sim.datacenter.ui.simulation;

import com.cpz.processing.controls.controls.button.Button;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.app.Initializable;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;
import com.cpz.utils.time.Timer;

import java.util.Arrays;
import java.util.List;

import static com.cpz.sim.datacenter.ui.main.Launcher.PROPS;

/**
 * @author CPZ
 */
public class SimulationManager extends ApplicationComponent implements Initializable {

    private final SimulationContext context;
    private final UiComponentContainer uiComponentContainer;

    public SimulationManager(
            ApplicationContext applicationContext,
            SimulationContext context,
            UiComponentContainer uiComponentContainer
    ) {
        super(applicationContext);
        this.context = context;
        this.uiComponentContainer = uiComponentContainer;
    }

    @Override
    public void initialize() {
        initializeTimer();
    }

    private void initializeTimer() {
        context.setSimulationBasePeriodMillis(Integer.parseInt(PROPS.getProperty("simulation.timer.base-period-ms")));
        context.setSimulationSpeedFactors(parseSimulationSpeedFactors(PROPS.getProperty("simulation.timer.speed-factors")));
        context.setSimulationSpeedFactorIndex(findSimulationSpeedFactorIndex(Double.parseDouble(PROPS.getProperty("simulation.timer.initial-speed-factor"))));
        context.setSimulationTimer(new Timer());
        context.simulationTimer().setPeriodMillis(1000);
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
        for (int i = 0; i < context.simulationSpeedFactors().size(); i++) {
            if (Double.compare(context.simulationSpeedFactors().get(i), initialFactor) == 0) return i;
        }
        throw new IllegalArgumentException("simulation.timer.initial-speed-factor must exist in simulation.timer.speed-factors");
    }

    public void updateSimulationTimerPeriod() {
        double factor = context.simulationSpeedFactors().get(context.simulationSpeedFactorIndex());
        int periodMillis = (int) Math.round(context.simulationBasePeriodMillis() / factor);
        context.simulationTimer().setPeriodMillis(periodMillis);
    }

    public void updateSimulationControls() {
        boolean simulationRunning = context.simulationTimer().isRunning();
        String speedLabel = formatSimulationSpeedFactor();
        uiComponentContainer.labels().get("lblSimulationValue").setText(simulationRunning ? "Running " + speedLabel : "Stopped " + speedLabel);
        Button btnPlayMinus = uiComponentContainer.buttonsPlay().get("btnPlayMinus");
        if (btnPlayMinus != null) btnPlayMinus.setEnabled(context.simulationSpeedFactorIndex() > 0);
        Button btnPlayPlus = uiComponentContainer.buttonsPlay().get("btnPlayPlus");
        if (btnPlayPlus != null) btnPlayPlus.setEnabled(context.simulationSpeedFactorIndex() < context.simulationSpeedFactors().size() - 1);
    }

    private String formatSimulationSpeedFactor() {
        double factor = context.simulationSpeedFactors().get(context.simulationSpeedFactorIndex());
        if (factor == Math.rint(factor)) return "x" + (int) factor;
        return "x" + factor;
    }
}
