package com.cpz.sim.datacenter.ui.ui;

import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;

/**
 * Updates the UI header.
 *
 * @author CPZ
 */
public class HeaderUpdater extends ApplicationComponent {

    private final SimulationContainer simulationContainer;
    private final UiComponentContainer uiComponentContainer;
    private int previousSecond;
    private int previousDay;

    public HeaderUpdater(ApplicationContext context, SimulationContainer simulationContainer, UiComponentContainer uiComponentContainer) {
        super(context);
        this.simulationContainer = simulationContainer;
        this.uiComponentContainer = uiComponentContainer;
    }

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