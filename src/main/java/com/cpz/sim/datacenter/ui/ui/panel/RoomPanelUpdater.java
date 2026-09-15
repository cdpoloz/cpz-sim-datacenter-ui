package com.cpz.sim.datacenter.ui.ui.panel;

import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.sim.datacenter.model.Rack;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot;
import com.cpz.sim.datacenter.snapshot.ServerGroupOperationalSnapshot;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;
import com.cpz.utils.color.Colors;

import java.util.List;

import static com.cpz.sim.datacenter.ui.util.Constants.COLOR_EMPTY_RACK;
import static com.cpz.sim.datacenter.ui.util.Constants.COLOR_HOTSPOT_RACK;
import static com.cpz.sim.datacenter.ui.util.Constants.COLOR_MAX_TEMPERATURE;
import static com.cpz.sim.datacenter.ui.util.Constants.COLOR_MIN_TEMPERATURE;

/**
 * Projects rack and aisle operational snapshots into the full-room map.
 *
 * @author CPZ
 */
public class RoomPanelUpdater extends ApplicationComponent {

    private final SimulationContainer simulationContainer;
    private final UiComponentContainer uiComponentContainer;

    /**
     * Creates a room updater for the backend model and configured room Indicators.
     *
     * @param context Processing color-mapping access
     * @param simulationContainer model and operational snapshots
     * @param uiComponentContainer room Indicators to update
     */
    public RoomPanelUpdater(
            ApplicationContext context,
            SimulationContainer simulationContainer,
            UiComponentContainer uiComponentContainer
    ) {
        super(context);
        this.simulationContainer = simulationContainer;
        this.uiComponentContainer = uiComponentContainer;
    }

    /**
     * Updates every fixed hot-aisle region and all configured rack Indicators.
     *
     * @param minServerTemperatureCelsius lower color-scale bound
     * @param maxServerTemperatureCelsius upper color-scale bound
     */
    public void update(
            float minServerTemperatureCelsius,
            float maxServerTemperatureCelsius
    ) {
        updateRoomHotAisleIndicator("HA01", "indRoomHotAisleC01", minServerTemperatureCelsius, maxServerTemperatureCelsius);
        updateRoomHotAisleIndicator("HA02", "indRoomHotAisleC02-C03", minServerTemperatureCelsius, maxServerTemperatureCelsius);
        updateRoomHotAisleIndicator("HA03", "indRoomHotAisleC04-C05", minServerTemperatureCelsius, maxServerTemperatureCelsius);
        updateRoomHotAisleIndicator("HA04", "indRoomHotAisleC06-C07", minServerTemperatureCelsius, maxServerTemperatureCelsius);
        updateRoomHotAisleIndicator("HA05", "indRoomHotAisleC08", minServerTemperatureCelsius, maxServerTemperatureCelsius);
        for (Rack rack : simulationContainer.datacenter().getRacks())
            updateRackIndicator(rack, minServerTemperatureCelsius, maxServerTemperatureCelsius);
    }

    private void updateRackIndicator(Rack rack, float minServerTemperatureCelsius, float maxServerTemperatureCelsius) {
        RackLocation location = rack.getLocation();
        String rackIndicatorCode = "indRack" + rack.getColumn() + rack.getRow();
        Indicator rackIndicator = uiComponentContainer.indicatorsRack().get(rackIndicatorCode);
        if (rackIndicator == null) return;
        List<Server> rackServers = simulationContainer.datacenter().getServers(location);
        RackOperationalSnapshot rackSnapshot = simulationContainer.operationalSnapshot().getRack(location);
        boolean emptyRack = !rackSnapshot.hasInstalledServers();
        boolean rackOffline = rackSnapshot.hasInstalledServers() && !rackSnapshot.hasOnlineServers();
        boolean aiRack = rackServers.stream().anyMatch(server -> server.getRole() == ServerRole.AI);
        float averageTemperature = (float) rackSnapshot.averageOnlineTemperatureCelsius();
        boolean rackHotspot = averageTemperature > maxServerTemperatureCelsius;
        int rackColor;
        if (emptyRack) rackColor = COLOR_EMPTY_RACK;
        else if (rackOffline) rackColor = COLOR_MIN_TEMPERATURE;
        else if (rackHotspot) rackColor = COLOR_HOTSPOT_RACK;
        else rackColor = calculateRackColor(averageTemperature, minServerTemperatureCelsius, maxServerTemperatureCelsius);
        rackIndicator.setOnColor(rackColor);
        updateRackConditionIndicators(rackIndicatorCode, rackOffline, emptyRack, rackHotspot, aiRack);
    }

    private int calculateRackColor(float temperature, float minServerTemperatureCelsius, float maxServerTemperatureCelsius) {
        float factor = sketch().map(temperature, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 1);
        factor = Math.clamp(factor, 0, 1);
        return Colors.lerpColor(COLOR_MIN_TEMPERATURE, COLOR_MAX_TEMPERATURE, factor);
    }

    private void updateRackConditionIndicators(String rackIndicatorCode, boolean rackOffline, boolean emptyRack, boolean rackHotspot, boolean aiRack) {
        Indicator offlineIndicator = uiComponentContainer.indicatorsRackCondition().get(rackIndicatorCode.replace("indRack", "indRackOffline"));
        Indicator emptyIndicator = uiComponentContainer.indicatorsRackCondition().get(rackIndicatorCode.replace("indRack", "indRackEmpty"));
        Indicator hotspotIndicator = uiComponentContainer.indicatorsRackCondition().get(rackIndicatorCode.replace("indRack", "indRackHotspot"));
        Indicator aiIndicator = uiComponentContainer.indicatorsRackCondition().get(rackIndicatorCode.replace("indRack", "indRackAI"));
        offlineIndicator.setOn(rackOffline);
        emptyIndicator.setOn(emptyRack);
        hotspotIndicator.setOn(rackHotspot);
        aiIndicator.setOn(aiRack);
    }

    private void updateRoomHotAisleIndicator(String hotAisleCode, String indicatorCode, float minServerTemperatureCelsius, float maxServerTemperatureCelsius) {
        ServerGroupOperationalSnapshot aisleSnapshot =
                simulationContainer
                        .operationalSnapshot()
                        .findServerGroup(hotAisleCode)
                        .orElseThrow(() -> new IllegalStateException("Missing operational snapshot for aisle: " + hotAisleCode));
        float averageTemperature = (float) aisleSnapshot.averageOnlineTemperatureCelsius();
        float maxTemperature = (float) aisleSnapshot.maximumTemperatureCelsius();
        // Blend typical and worst-case temperature so the room view exposes both signals.
        float temperature = (maxTemperature + averageTemperature) * 0.5f;
        float factor = sketch().map(temperature, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 1);
        factor = Math.clamp(factor, 0, 1);
        int hotAisleColor = Colors.lerpColor(COLOR_MIN_TEMPERATURE, COLOR_MAX_TEMPERATURE, factor);
        int alpha = (int) sketch().map(temperature, minServerTemperatureCelsius, maxServerTemperatureCelsius, 0, 96);
        alpha = Math.clamp(alpha, 0, 128);
        int red = Colors.red(hotAisleColor);
        int green = Colors.green(hotAisleColor);
        int blue = Colors.blue(hotAisleColor);
        uiComponentContainer.indicators().get(indicatorCode).setOnColor(Colors.argb(alpha, red, green, blue));
    }
}
