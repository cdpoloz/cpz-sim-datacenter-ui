package com.cpz.sim.datacenter.ui.ui;

import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.processing.controls.core.overlay.notification.Notification;
import com.cpz.processing.controls.core.overlay.notification.NotificationManager;
import com.cpz.processing.controls.core.overlay.notification.NotificationSeverity;
import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.health.ThermalAlertLevel;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.snapshot.HealthSnapshot;
import com.cpz.sim.datacenter.snapshot.ServerHealthSnapshot;
import org.junit.jupiter.api.Test;
import processing.core.PApplet;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SimulationAlertNotifierTest {
    private final NotificationManager notifications = new NotificationManager(new PApplet(), new OverlayManager());
    private final java.util.ArrayList<Notification> active = new java.util.ArrayList<>();
    private final SimulationAlertNotifier notifier = new SimulationAlertNotifier((severity, message) -> {
        var item = notifications.showPersistent(message, severity);
        active.add(item);
        return item.getId();
    }, id -> {
        assertTrue(notifications.dismiss(id), "Dismiss must target an existing visible or pending item");
        active.removeIf(item -> item.getId().equals(id));
    });

    @Test
    void productionAdapterShowsAndDismissesThroughTheManager() {
        var production = new SimulationAlertNotifier(notifications);
        production.update(snapshot(1, server("C01", "S01", ThermalAlertLevel.CRITICAL, false)));
        assertEquals(1, notifications.size());
        production.update(snapshot(2));
        assertEquals(0, notifications.size());
    }

    @Test
    void initialAlertsPersistAcrossUnchangedTicksAndPausedFramesThenDisappearOnRecovery() {
        notifier.update(snapshot(1, server("C01", "S01", ThermalAlertLevel.ELEVATED, true)));
        var initial = active.getFirst();
        assertTrue(initial.isPersistent());
        assertFalse(initial.isExpired(initial.getCreatedAtMillis() + 86_400_000L));
        assertFalse(notifications.isPersistentDismissible());
        assertTrue(initial.getMessage().contains("High temperature, High utilization"));
        notifier.update(snapshot(2, server("C01", "S01", ThermalAlertLevel.ELEVATED, true)));
        notifier.update(snapshot(2, server("C01", "S01", ThermalAlertLevel.ELEVATED, true)));
        notifier.update(null);
        assertEquals(List.of(initial), active);
        notifier.update(snapshot(3, server("C01", "S01", ThermalAlertLevel.NORMAL, false)));
        assertEquals(0, notifications.size());
    }

    @Test
    void severityAndPartialRecoveryReplaceTheExistingItemWithoutDuplicates() {
        notifier.update(snapshot(1, server("C01", "S01", ThermalAlertLevel.ELEVATED, true)));
        String oldId = active.getFirst().getId();
        notifier.update(snapshot(2, server("C01", "S01", ThermalAlertLevel.CRITICAL, true)));
        assertEquals(1, notifications.size());
        var critical = active.getFirst();
        assertNotEquals(oldId, critical.getId());
        assertEquals(NotificationSeverity.ERROR, critical.getSeverity());
        assertTrue(critical.getMessage().contains("Critical temperature"));
        notifier.update(snapshot(3, server("C01", "S01", ThermalAlertLevel.ELEVATED, true)));
        assertEquals(NotificationSeverity.WARNING, active.getFirst().getSeverity());
        notifier.update(snapshot(4, server("C01", "S01", ThermalAlertLevel.NORMAL, true)));
        assertEquals(1, notifications.size());
        assertTrue(active.getFirst().getMessage().endsWith("High utilization"));
        assertFalse(active.getFirst().getMessage().contains("temperature"));
        notifier.update(snapshot(5, server("C01", "S01", ThermalAlertLevel.NORMAL, false)));
        assertEquals(0, notifications.size());
    }

    @Test
    void pendingAlertsSurviveTheVisibleLimitAndCanBeResolvedBeforeBecomingVisible() {
        notifications.setMaxVisible(3);
        notifications.setPrioritizeSeverity(true);
        notifier.update(snapshot(1,
                server("C01", "S01", ThermalAlertLevel.ELEVATED, false),
                server("C01", "S02", ThermalAlertLevel.ELEVATED, false),
                server("C01", "S03", ThermalAlertLevel.ELEVATED, false),
                server("C01", "S04", ThermalAlertLevel.CRITICAL, false)));
        assertEquals(4, notifications.size());
        assertTrue(active.stream().allMatch(item -> item.isPersistent()));
        notifier.update(snapshot(2,
                server("C01", "S01", ThermalAlertLevel.NORMAL, false),
                server("C01", "S02", ThermalAlertLevel.ELEVATED, false),
                server("C01", "S03", ThermalAlertLevel.ELEVATED, false),
                server("C01", "S04", ThermalAlertLevel.CRITICAL, false)));
        assertEquals(3, notifications.size());
        assertTrue(active.stream().noneMatch(item -> item.getMessage().startsWith("C01-R01-S01")));
    }

    @Test
    void fullLocationIdentityOfflineRemovalAndResetOnlyDismissOwnedAlerts() {
        var unrelated = notifications.showPersistent("Unrelated");
        active.add(unrelated);
        notifier.update(snapshot(5,
                server("C01", "S01", ThermalAlertLevel.CRITICAL, false),
                server("C02", "S01", ThermalAlertLevel.CRITICAL, false)));
        assertEquals(3, notifications.size());
        var offline = new ServerHealthSnapshot("offline", "C01", new RackCode("R01"), "S01",
                HardwareStatus.OFFLINE, Set.of(ServerAlertReason.HIGH_TEMPERATURE), .5, 110, ThermalAlertLevel.CRITICAL);
        notifier.update(snapshot(6, offline, server("C02", "S01", ThermalAlertLevel.CRITICAL, false)));
        assertEquals(2, notifications.size());
        notifier.update(snapshot(7));
        assertEquals(List.of(unrelated), active);
        notifier.update(snapshot(8, server("C02", "S01", ThermalAlertLevel.CRITICAL, false)));
        String oldId = active.getLast().getId();
        notifier.update(snapshot(1, server("C02", "S01", ThermalAlertLevel.CRITICAL, false)));
        assertEquals(2, notifications.size());
        assertNotEquals(oldId, active.getLast().getId());
        assertTrue(active.getLast().getMessage().contains("00d 00:01"));
        notifier.update(snapshot(0));
        assertEquals(List.of(unrelated), active);
    }

    private static HealthSnapshot snapshot(long tick, ServerHealthSnapshot... servers) {
        return new HealthSnapshot(tick, tick * 60, List.of(servers));
    }

    private static ServerHealthSnapshot server(String column, String slot, ThermalAlertLevel thermal, boolean utilization) {
        var reasons = new java.util.HashSet<ServerAlertReason>();
        if (thermal != ThermalAlertLevel.NORMAL) reasons.add(ServerAlertReason.HIGH_TEMPERATURE);
        if (utilization) reasons.add(ServerAlertReason.HIGH_UTILIZATION);
        return new ServerHealthSnapshot(column + "-R01-" + slot, column, new RackCode("R01"), slot,
                reasons.isEmpty() ? HardwareStatus.OK : HardwareStatus.ALERT, reasons, utilization ? .95 : .5,
                thermal == ThermalAlertLevel.CRITICAL ? 110 : 85, thermal);
    }
}
