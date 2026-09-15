package com.cpz.sim.datacenter.ui.ui;

import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;

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
    }

    private void updateRoomLabel() {
        String text = "DATACENTER MAP";
        if (simulationContainer.roomName() != null && !simulationContainer.roomName().isEmpty())
            text += " - " + simulationContainer.roomName();
        uiComponentContainer.labels().get("lblRoom").setText(text);
    }

    private void updateDateTime() {
        if (sketch().second() == previousSecond) {
            return;
        }
        previousSecond = sketch().second();
        uiComponentContainer
                .labels()
                .get("lblTime")
                .setText(
                        String.format("%02d", sketch().hour())
                                + ":"
                                + String.format("%02d", sketch().minute())
                                + ":"
                                + String.format("%02d", sketch().second())
                );
        if (sketch().day() == previousDay) return;
        previousDay = sketch().day();
        uiComponentContainer
                .labels()
                .get("lblDate")
                .setText(
                        String.format("%02d", sketch().day())
                                + "/"
                                + String.format("%02d", sketch().month())
                                + "/"
                                + sketch().year()
                );
    }
}
