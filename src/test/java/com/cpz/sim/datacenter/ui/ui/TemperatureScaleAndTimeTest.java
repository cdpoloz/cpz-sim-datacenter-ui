package com.cpz.sim.datacenter.ui.ui;

import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.health.ThermalAlertLevel;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.snapshot.*;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.main.Sketch;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.ui.panel.HeaderUpdater;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TemperatureScaleAndTimeTest {
    @Test
    void scaleExpandsBeyondTheoreticalEquilibriumAndKeepsLegendAndBoundsAligned() {
        var sketch = new Sketch();
        var state = new UiStateContainer();
        state.setMinServerTemperatureCelsius(24);
        state.setMaxServerTemperatureCelsius(74);
        var controls = new UiComponentContainer();
        var labels = new HashMap<String, Label>();
        for (int i = 1; i <= 6; i++) {
            String code = "lblRoomTemperatureScale0" + i;
            labels.put(code, new Label(sketch, code, "", 0, 0, 10, 10));
        }
        controls.setLabels(labels);
        var updater = new TemperatureScaleUpdater(state, controls, "%.0f°C");
        updater.update(temperatures(1, 115));
        assertEquals(24, state.minServerTemperatureCelsius());
        assertEquals(120, state.maxServerTemperatureCelsius());
        assertEquals("24°C", labels.get("lblRoomTemperatureScale01").getText());
        assertEquals("62°C", labels.get("lblRoomTemperatureScale03").getText());
        assertEquals("120°C", labels.get("lblRoomTemperatureScale06").getText());
        assertTrue(state.updateUI());
        state.setUpdateUI(false);
        updater.update(temperatures(2, 50));
        assertEquals(120, state.maxServerTemperatureCelsius(), "Cooling must not constantly rescale colors");
        assertFalse(state.updateUI());
        updater.update(temperatures(3, -5));
        assertEquals(-10, state.minServerTemperatureCelsius());
        assertEquals("-10°C", labels.get("lblRoomTemperatureScale01").getText());
        updater.update(new TemperatureSnapshot(4, 240, 24, List.of()));
        assertEquals(-10, state.minServerTemperatureCelsius());
        assertEquals(120, state.maxServerTemperatureCelsius());
    }

    @Test
    void headerAndAlertUseSameSnapshotElapsedTimeRegardlessOfTickIndexOrPause() {
        var sketch = new Sketch();
        var controls = new UiComponentContainer();
        var labels = new HashMap<String, Label>();
        for (String code : List.of("lblRoom", "lblTime", "lblDate", "lblSimulationTime"))
            labels.put(code, new Label(sketch, code, "", 0, 0, 10, 10));
        controls.setLabels(labels);
        var running = new Indicator(sketch, "indSimulationRunning", 0, 0, 10, 10);
        controls.setIndicators(java.util.Map.of("indSimulationRunning", running));
        var simulation = new SimulationContainer();
        var header = new HeaderUpdater(new ApplicationContext(sketch), simulation, controls);
        var messages = new ArrayList<String>();
        var notifier = new SimulationAlertNotifier((severity, message) -> messages.add(message));
        header.update();
        assertEquals("00d 00:00", labels.get("lblSimulationTime").getText());
        notifier.update(new HealthSnapshot(0, 0, List.of()));
        // Tick 7 can represent 25h02m: do not assume one tick == one minute.
        var critical = new ServerHealthSnapshot("test", "C01", new RackCode("R01"), "S01",
                HardwareStatus.ALERT, Set.of(ServerAlertReason.HIGH_TEMPERATURE), .5, 110,
                ThermalAlertLevel.CRITICAL);
        simulation.setHealthSnapshot(new HealthSnapshot(7, 90120, List.of(critical)));
        header.update();
        notifier.update(simulation.healthSnapshot());
        assertEquals("01d 01:02", labels.get("lblSimulationTime").getText());
        assertTrue(messages.getFirst().contains("01d 01:02"));
        assertFalse(running.isOn());
        header.update();
        notifier.update(simulation.healthSnapshot());
        assertEquals(1, messages.size());
        assertEquals("01d 01:02", labels.get("lblSimulationTime").getText());
        assertEquals("00d 23:59", SimulationTimeFormat.daysHoursMinutes(86399));
        assertEquals("01d 00:00", SimulationTimeFormat.daysHoursMinutes(86400));
    }

    private static TemperatureSnapshot temperatures(long tick, double temperature) {
        return new TemperatureSnapshot(tick, tick * 60, 24, List.of(new ServerTemperatureSnapshot(
                "test", "C01", new RackCode("R01"), "S01", HardwareStatus.OK, .5, 100, temperature)));
    }
}
