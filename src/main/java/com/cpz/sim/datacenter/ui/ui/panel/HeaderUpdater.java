package com.cpz.sim.datacenter.ui.ui.panel;

import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;
import com.cpz.sim.datacenter.ui.ui.SimulationTimeFormat;
import com.cpz.sim.datacenter.ui.ui.WallClockLabels;

/**
 * Updates the room title and wall-clock labels independently of simulation time.
 *
 * @author CPZ
 */
public class HeaderUpdater extends ApplicationComponent {

    private final SimulationContainer simulationContainer;
    private final UiComponentContainer uiComponentContainer;
    private final WallClockLabels wallClock;

    /**
     * Creates a header updater for the active room and system clock.
     *
     * @param context application drawing context
     * @param simulationContainer source of the configured room name
     * @param uiComponentContainer header Labels to update
     */
    public HeaderUpdater(ApplicationContext context, SimulationContainer simulationContainer, UiComponentContainer uiComponentContainer) {
        super(context);
        this.simulationContainer = simulationContainer;
        this.uiComponentContainer = uiComponentContainer;
        wallClock = new WallClockLabels(uiComponentContainer.labels().get("lblDate"),
                uiComponentContainer.labels().get("lblTime"));
    }

    /** Updates the room label each frame and date/time only when their values change. */
    public void update() {
        updateRoomLabel();
        wallClock.update();
        updateSimulationTimeAndStatus();
        updateServerCounts();
    }

    private void updateServerCounts() {
        var snapshot = simulationContainer.operationalSnapshot();
        updateCount("lblRoomTotalServersValue", snapshot == null ? null : snapshot.racks().values().stream()
                .mapToInt(com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot::installedServerCount).sum());
        updateCount("lblRoomOnlineServersValue", snapshot == null ? null : snapshot.racks().values().stream()
                .mapToInt(com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot::onlineServerCount).sum());
    }

    private void updateCount(String code, Integer count) {
        var label = uiComponentContainer.labels().get(code);
        if (label != null) label.setText(count == null ? "--" : count.toString());
    }

    private void updateRoomLabel() {
        String text = "DATACENTER MAP";
        if (simulationContainer.roomName() != null && !simulationContainer.roomName().isEmpty())
            text += " - " + simulationContainer.roomName();
        uiComponentContainer.labels().get("lblRoom").setText(text);
    }

    private void updateSimulationTimeAndStatus() {
        uiComponentContainer.indicators().get("indSimulationRunning").setOn(isSimulationRunning());
        var snapshot = simulationContainer.healthSnapshot();
        uiComponentContainer.labels().get("lblSimulationTime").setText(
                SimulationTimeFormat.daysHoursMinutes(snapshot == null ? 0.0 : snapshot.elapsedSeconds()));
    }

    public boolean isSimulationRunning() {
        return simulationContainer.simulationTimer() != null && simulationContainer.simulationTimer().isRunning();
    }

}
