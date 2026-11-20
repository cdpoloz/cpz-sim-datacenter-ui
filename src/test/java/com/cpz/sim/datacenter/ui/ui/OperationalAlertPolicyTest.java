package com.cpz.sim.datacenter.ui.ui;

import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.processing.controls.core.input.InputManager;
import com.cpz.processing.controls.core.overlay.notification.*;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.health.*;
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.snapshot.*;
import com.cpz.sim.datacenter.ui.ui.panel.AlertsPanelUpdater;
import org.junit.jupiter.api.Test;
import processing.core.PApplet;
import processing.data.JSONObject;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OperationalAlertPolicyTest {
    private static final ServerAlertPolicy OM = new ServerAlertPolicy(false);
    private static HealthSnapshot snapshot(long tick, ServerHealthSnapshot server) {
        return new HealthSnapshot(tick, tick * 60, List.of(server));
    }
    private static ServerHealthSnapshot server(Set<ServerAlertReason> reasons, ThermalAlertLevel level,
                                               ThermalProtectionState protection) {
        return new ServerHealthSnapshot("C01-R01-S01", "C01", new RackCode("R01"), "S01",
                protection == ThermalProtectionState.SHUTDOWN ? HardwareStatus.OFFLINE
                        : reasons.isEmpty() ? HardwareStatus.OK : HardwareStatus.ALERT,
                reasons, protection == ThermalProtectionState.SHUTDOWN ? 0 : 1, 90, level, protection);
    }

    @Test void demoDisablesUtilizationWhileExplicitEnableRestoresItsNotification() {
        var policy = new JsonDatacenterConfigLoader().load(Path.of("data/config/datacenter.json")).alertPolicy();
        assertFalse(policy.highUtilizationEnabled());
        var diagnostic = server(Set.of(ServerAlertReason.HIGH_UTILIZATION), ThermalAlertLevel.NORMAL, ThermalProtectionState.NORMAL);
        assertEquals("NORMAL", HealthPresentation.systemStatus(snapshot(1, diagnostic), policy));
        assertEquals("WARNING", HealthPresentation.systemStatus(snapshot(1, diagnostic)));
        var sketch = new PApplet() {
            @Override public JSONObject loadJSONObject(String path) {
                return PApplet.loadJSONObject(new File(path));
            }
        };
        var overlays = new OverlayManager();
        var manager = new NotificationManager(sketch, overlays);
        var title = new Label(sketch, "lblAlerts", "STALE", 0, 0, 200, 20);
        var hidden = new SimulationAlertNotifier(manager, policy);
        hidden.update(snapshot(1, diagnostic));
        try (var updater = AlertsPanelUpdater.create(sketch, overlays, new InputManager(), hidden, manager, title)) {
            updater.update();
            assertEquals("ALERTS", title.getText());
        }
        assertEquals(0, manager.size());
        assertTrue(hidden.activeAlerts().isEmpty());
        assertTrue(diagnostic.hasAlertReason(ServerAlertReason.HIGH_UTILIZATION));
        assertEquals(HardwareStatus.ALERT, diagnostic.status());
        assertEquals(1, diagnostic.utilization());
        var enabled = new SimulationAlertNotifier(manager, new ServerAlertPolicy(true));
        enabled.update(snapshot(1, diagnostic));
        assertEquals(1, manager.size());
        assertEquals(Set.of(ServerAlertReason.HIGH_UTILIZATION), enabled.activeAlerts().getFirst().visibleReasons());
    }

    @Test void mixedThermalAlertKeepsOnlyItsVisibleReasonAndIgnoresHiddenReasonTransitions() {
        var shown = new ArrayList<Notification>();
        var manager = new NotificationManager(new PApplet(), new OverlayManager());
        var notifier = new SimulationAlertNotifier((severity, message) -> {
            var notification = manager.showPersistent(message, severity); shown.add(notification); return notification.getId();
        }, manager::dismiss, OM);
        var mixed = server(Set.of(ServerAlertReason.HIGH_UTILIZATION, ServerAlertReason.HIGH_TEMPERATURE),
                ThermalAlertLevel.ELEVATED, ThermalProtectionState.NORMAL);
        notifier.update(snapshot(1, mixed));
        assertEquals(1, manager.size());
        assertTrue(shown.getFirst().getMessage().contains("High temperature"));
        assertFalse(shown.getFirst().getMessage().contains("utilization"));
        assertEquals("WARNING", HealthPresentation.systemStatus(snapshot(1, mixed), OM));
        var initial = notifier.activeAlerts().getFirst();
        assertEquals(Set.of(ServerAlertReason.HIGH_TEMPERATURE), initial.visibleReasons());
        notifier.update(snapshot(2, server(Set.of(ServerAlertReason.HIGH_TEMPERATURE),
                ThermalAlertLevel.ELEVATED, ThermalProtectionState.NORMAL)));
        assertEquals(1, shown.size(), "Hidden utilization changes must not replace the visible thermal event");
        assertSame(initial, notifier.activeAlerts().getFirst());
        assertEquals(60.0, initial.observedStateSinceSeconds());
        notifier.update(snapshot(3, server(Set.of(ServerAlertReason.HIGH_UTILIZATION),
                ThermalAlertLevel.NORMAL, ThermalProtectionState.NORMAL)));
        assertEquals(0, manager.size(), "Only the disabled utilization reason remains after thermal recovery");
        assertTrue(notifier.activeAlerts().isEmpty());
    }

    @Test void disablingUtilizationNeverSuppressesThermalProtection() {
        var shown = new ArrayList<Notification>();
        var manager = new NotificationManager(new PApplet(), new OverlayManager());
        var notifier = new SimulationAlertNotifier((severity, message) -> {
            var notification = manager.showPersistent(message, severity); shown.add(notification); return notification.getId();
        }, manager::dismiss, OM);
        var throttled = server(Set.of(), ThermalAlertLevel.NORMAL, ThermalProtectionState.THROTTLED);
        notifier.update(snapshot(1, throttled));
        assertEquals("WARNING", HealthPresentation.systemStatus(snapshot(1, throttled), OM));
        assertEquals(NotificationSeverity.WARNING, shown.getLast().getSeverity());
        assertTrue(shown.getLast().getMessage().contains("Thermal throttling"));
        var throttledAlert = notifier.activeAlerts().getFirst();
        assertEquals(ThermalProtectionState.THROTTLED, throttledAlert.protectionState());
        assertTrue(throttledAlert.visibleReasons().isEmpty());
        var shutdown = server(Set.of(), ThermalAlertLevel.NORMAL, ThermalProtectionState.SHUTDOWN);
        notifier.update(snapshot(2, shutdown));
        assertEquals(1, manager.size());
        assertEquals("CRITICAL", HealthPresentation.systemStatus(snapshot(2, shutdown), OM));
        assertEquals(NotificationSeverity.ERROR, shown.getLast().getSeverity());
        assertTrue(shown.getLast().getMessage().contains("Thermal protection shutdown"));
        assertEquals(List.of(new ServerAlertPresentation(shutdown.location(), Set.of(), ThermalAlertLevel.NORMAL,
                ThermalProtectionState.SHUTDOWN, NotificationSeverity.ERROR, 120.0)), notifier.activeAlerts());
    }
}
