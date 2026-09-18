package com.cpz.sim.datacenter.ui.ui.panel;

import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;
import processing.core.PApplet;

/**
 * Updates the room title and wall-clock labels independently of simulation time.
 *
 * @author CPZ
 */
public class HeaderUpdater extends ApplicationComponent {

    private final SimulationContainer simulationContainer;
    private final UiComponentContainer uiComponentContainer;
    private int previousSecond;
    private int previousDay;

    /**
     * Creates a header updater for the active room and Processing wall clock.
     *
     * @param context source of Processing wall-clock values
     * @param simulationContainer source of the configured room name
     * @param uiComponentContainer header Labels to update
     */
    public HeaderUpdater(ApplicationContext context, SimulationContainer simulationContainer, UiComponentContainer uiComponentContainer) {
        super(context);
        this.simulationContainer = simulationContainer;
        this.uiComponentContainer = uiComponentContainer;
    }

    /** Updates the room label each frame and date/time only when their values change. */
    public void update() {
        updateRoomLabel();
        updateDateTime();
        updateSimulationTimeAndStatus();
    }

    private void updateRoomLabel() {
        String text = "DATACENTER MAP";
        if (simulationContainer.roomName() != null && !simulationContainer.roomName().isEmpty())
            text += " - " + simulationContainer.roomName();
        uiComponentContainer.labels().get("lblRoom").setText(text);
    }

    private void updateDateTime() {
        if (PApplet.second() == previousSecond) return;
        previousSecond = PApplet.second();
        uiComponentContainer.labels().get("lblTime").setText(getCurrentTime());
        if (PApplet.day() == previousDay) return;
        previousDay = PApplet.day();
        uiComponentContainer.labels().get("lblDate").setText(getCurrentDate());
    }

    private String getCurrentTime() {
        return String.format("%02d", PApplet.hour())
                + ":"
                + String.format("%02d", PApplet.minute())
                + ":"
                + String.format("%02d", PApplet.second());
    }

    private String getCurrentDate() {
        return String.format("%02d", PApplet.day())
                + "/"
                + String.format("%02d", PApplet.month())
                + "/"
                + PApplet.year();
    }

    private void updateSimulationTimeAndStatus() {
        uiComponentContainer.indicators().get("indSimulationRunning").setOn(isSimulationRunning());
        long ticks = simulationContainer.engine().currentTick().index() - 1;
        uiComponentContainer.labels().get("lblSimulationTime").setText(formatTicksAsDaysHoursMinutes(ticks));
    }

    public boolean isSimulationRunning() {
        return simulationContainer.simulationTimer() != null && simulationContainer.simulationTimer().isRunning();
    }

    private String formatTicksAsDaysHoursMinutes(long ticks) {
        long days = ticks / (24 * 60);
        long remainingMinutes = ticks % (24 * 60);
        long hours = remainingMinutes / 60;
        long minutes = remainingMinutes % 60;
        return String.format("%02dd %02d:%02d", days, hours, minutes);
    }
}
