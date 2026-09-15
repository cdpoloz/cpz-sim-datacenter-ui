package com.cpz.sim.datacenter.ui.ui.panel;

import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot;
import com.cpz.sim.datacenter.snapshot.ServerEnergySnapshot;
import com.cpz.sim.datacenter.snapshot.ServerHealthSnapshot;
import com.cpz.sim.datacenter.snapshot.ServerTemperatureSnapshot;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;
import com.cpz.sim.datacenter.ui.ui.selection.SelectionManager;
import com.cpz.utils.color.Colors;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.cpz.sim.datacenter.ui.main.Launcher.PROPS;
import static com.cpz.sim.datacenter.ui.util.Constants.*;

/**
 * Projects per-server and aggregate snapshots into the selected rack panel.
 *
 * <p>Energy, temperature, and health snapshots are joined by {@link ServerLocation} so each
 * physical slot is updated from the same engine tick.</p>
 *
 * @author CPZ
 */
public class SelectedRackPanelUpdater extends ApplicationComponent {

    private final SimulationContainer simulationContainer;
    private final UiComponentContainer uiComponentContainer;
    private final SelectionManager selectionManager;

    /**
     * Creates an updater that resolves the current rack through {@link SelectionManager}.
     *
     * @param context Processing color and bar mapping access
     * @param simulationContainer backend model and current snapshots
     * @param uiComponentContainer selected-rack controls
     * @param selectionManager source of the active column and rack
     */
    public SelectedRackPanelUpdater(
            ApplicationContext context,
            SimulationContainer simulationContainer,
            UiComponentContainer uiComponentContainer,
            SelectionManager selectionManager
    ) {
        super(context);
        this.simulationContainer = simulationContainer;
        this.uiComponentContainer = uiComponentContainer;
        this.selectionManager = selectionManager;
    }

    /**
     * Updates slot values, status/alert Indicators, and aggregate rack metrics.
     *
     * @param minServerTemperatureCelsius lower color-scale bound
     * @param maxServerTemperatureCelsius upper color-scale bound
     * @param temperatureFormat temperature label format
     * @param percentageFormat utilization label format
     * @param powerKwFormat power label format
     */
    public void update(
            float minServerTemperatureCelsius,
            float maxServerTemperatureCelsius,
            String temperatureFormat,
            String percentageFormat,
            String powerKwFormat
    ) {
        uiComponentContainer.labels().get("lblSelectedRackValue").setText(selectedColumn() + "-" + selectedRack());
        Rack rack = resolveSelectedRack();
        // Resolve every domain snapshot against the same physical column/rack selection.
        RackLocation rackLocation = new RackLocation(selectedColumn(), new RackCode(selectedRack()));
        RackOperationalSnapshot rackSnapshot =
                simulationContainer
                        .operationalSnapshot()
                        .findRack(rackLocation)
                        .orElseThrow(() -> new IllegalStateException("Missing operational snapshot for rack: " + rackLocation.code()));
        Map<ServerLocation, ServerEnergySnapshot> energyByLocation = resolveEnergyByLocation();
        Map<ServerLocation, ServerTemperatureSnapshot> temperatureByLocation = resolveTemperatureByLocation();
        Map<ServerLocation, ServerHealthSnapshot> healthByLocation = resolveHealthByLocation();
        for (String slot : rack.getSlotCodes())
            updateSlot(
                    slot,
                    energyByLocation,
                    temperatureByLocation,
                    healthByLocation,
                    minServerTemperatureCelsius,
                    maxServerTemperatureCelsius,
                    temperatureFormat,
                    percentageFormat,
                    powerKwFormat
            );
        updateRackSummary(rackSnapshot, temperatureFormat, percentageFormat, powerKwFormat);
    }

    private void updateSlot(
            String slot,
            Map<ServerLocation, ServerEnergySnapshot> energyByLocation,
            Map<ServerLocation, ServerTemperatureSnapshot> temperatureByLocation,
            Map<ServerLocation, ServerHealthSnapshot> healthByLocation,
            float minServerTemperatureCelsius,
            float maxServerTemperatureCelsius,
            String temperatureFormat,
            String percentageFormat,
            String powerKwFormat
    ) {
        String slotNumber = slot.replace("S", "");
        ServerLocation location = new ServerLocation(selectedColumn(), new RackCode(selectedRack()), slot);
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
            showEmptySlot(
                    indSlot,
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
            return;
        }
        ServerTemperatureSnapshot temperature = temperatureByLocation.get(location);
        if (temperature == null)
            throw new IllegalStateException("Missing temperature snapshot for server: " + location);
        ServerEnergySnapshot energy = energyByLocation.get(location);
        if (energy == null)
            throw new IllegalStateException("Missing energy snapshot for server: " + location);
        ServerHealthSnapshot health = healthByLocation.get(location);
        if (health == null)
            throw new IllegalStateException("Missing health snapshot for server: " + location);
        updateSlotColor(
                indSlot,
                (float) temperature.temperatureCelsius(),
                minServerTemperatureCelsius,
                maxServerTemperatureCelsius
        );
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

    private void updateRackSummary(
            RackOperationalSnapshot rackSnapshot,
            String temperatureFormat,
            String percentageFormat,
            String powerKwFormat
    ) {
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
        uiComponentContainer
                .labels()
                .get("lblRackCurrentPowerValue")
                .setText(String.format(powerKwFormat, rackSnapshot.currentPowerWatts() / 1000));
        updateBar("SelectedRackPowerBar", rackSnapshot.currentPowerWatts(), rackSnapshot.idlePowerWatts(), rackSnapshot.maxPowerWatts());
    }

    private void updateSlotColor(
            Indicator indSlot,
            float temperature,
            float minServerTemperatureCelsius,
            float maxServerTemperatureCelsius
    ) {
        Objects.requireNonNull(indSlot);
        float fColor =
                sketch().map(
                        temperature,
                        minServerTemperatureCelsius,
                        maxServerTemperatureCelsius,
                        0,
                        1
                );
        // Values beyond the calculated scale retain an endpoint color instead of extrapolating.
        fColor = Math.clamp(fColor, 0, 1);

        int colorSlot =
                Colors.lerpColor(
                        COLOR_MIN_TEMPERATURE,
                        COLOR_MAX_TEMPERATURE,
                        fColor
                );

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

    private void updateTemperatureLabelColor(
            boolean alert,
            Label label,
            double temperature
    ) {
        if (alert) {
            label.setTextColor(COLOR_MAGENTA_LABEL);
        } else if (temperature >= Double.parseDouble(
                PROPS.getProperty("simulation.health.temperature.warning-threshold-celsius")
        )) {
            label.setTextColor(COLOR_YELLOW_LABEL);
        } else {
            label.setTextColor(COLOR_GREEN_LABEL);
        }
    }

    private void updateBar(
            String type,
            double value,
            double minValue,
            double maxValue
    ) {
        if (type == null || type.isEmpty()) return;
        String key = "ind" + type;
        int iMax = (int) sketch().map((float) value, (float) minValue, (float) maxValue, 1, 6);
        for (int i = 0; i < 6; i++)
            uiComponentContainer.indicators().get(key + (i + 1)).setOn(i < iMax);
    }

    private Rack resolveSelectedRack() {
        return simulationContainer
                .datacenter()
                .findRack(selectedColumn(), selectedRack())
                .orElseThrow(() -> new IllegalStateException("Rack not found: " + selectedColumn() + "-" + selectedRack()));
    }

    private Map<ServerLocation, ServerEnergySnapshot> resolveEnergyByLocation() {
        return simulationContainer
                .energySnapshot()
                .servers()
                .stream()
                .collect(Collectors.toUnmodifiableMap(ServerEnergySnapshot::location, Function.identity()));
    }

    private Map<ServerLocation, ServerTemperatureSnapshot> resolveTemperatureByLocation() {
        return simulationContainer
                .temperatureSnapshot()
                .servers()
                .stream()
                .collect(
                        Collectors.toMap(
                                server -> new ServerLocation(
                                        server.column(),
                                        server.rackCode(),
                                        server.slot()
                                ),
                                Function.identity()
                        )
                );
    }

    private Map<ServerLocation, ServerHealthSnapshot> resolveHealthByLocation() {
        return simulationContainer
                .healthSnapshot()
                .servers()
                .stream()
                .collect(
                        Collectors.toMap(
                                server -> new ServerLocation(
                                        server.column(),
                                        server.rackCode(),
                                        server.slot()
                                ),
                                Function.identity()
                        )
                );
    }

    private String selectedColumn() {
        return selectionManager.selectedColumn();
    }

    private String selectedRack() {
        return selectionManager.selectedRack();
    }
}
