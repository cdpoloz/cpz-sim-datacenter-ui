package com.cpz.sim.datacenter.ui.ui;

import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.processing.controls.core.overlay.notification.Notification;
import com.cpz.processing.controls.core.overlay.notification.NotificationManager;
import com.cpz.processing.controls.core.overlay.notification.NotificationSeverity;
import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.health.ThermalAlertLevel;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.ThermalProtectionState;
import com.cpz.sim.datacenter.snapshot.HealthSnapshot;
import com.cpz.sim.datacenter.snapshot.ServerHealthSnapshot;
import org.junit.jupiter.api.Test;
import processing.core.PApplet;

import java.util.List;
import java.util.EnumSet;
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
        assertTrue(production.activeAlerts().isEmpty());
        production.update(snapshot(0));
        assertTrue(production.activeAlerts().isEmpty());
        production.update(snapshot(1, server("C01", "S01", ThermalAlertLevel.CRITICAL, false)));
        assertEquals(1, notifications.size());
        assertEquals(1, production.activeAlerts().size());
        production.update(snapshot(2));
        assertEquals(0, notifications.size());
        assertTrue(production.activeAlerts().isEmpty());
    }

    @Test
    void initialAlertsPersistAcrossUnchangedTicksAndPausedFramesThenDisappearOnRecovery() {
        notifier.update(snapshot(1, server("C01", "S01", ThermalAlertLevel.ELEVATED, true)));
        var initial = active.getFirst();
        var initialPresentation = notifier.activeAlerts().getFirst();
        assertTrue(initial.isPersistent());
        assertFalse(initial.isExpired(initial.getCreatedAtMillis() + 86_400_000L));
        assertFalse(notifications.isPersistentDismissible());
        assertTrue(initial.getMessage().contains("High temperature, High utilization"));
        notifier.update(snapshot(2, server("C01", "S01", ThermalAlertLevel.ELEVATED, true)));
        assertSame(initialPresentation, notifier.activeAlerts().getFirst());
        assertEquals(60.0, initialPresentation.observedStateSinceSeconds());
        var reconciled = notifier.activeAlerts();
        notifier.update(new HealthSnapshot(2, 125.5, List.of(server("C01", "S01", ThermalAlertLevel.CRITICAL, true))));
        notifier.update(null);
        assertSame(reconciled, notifier.activeAlerts());
        assertEquals(List.of(initial), active);
        notifier.update(snapshot(3, server("C01", "S01", ThermalAlertLevel.NORMAL, false)));
        assertEquals(0, notifications.size());
        assertTrue(notifier.activeAlerts().isEmpty());
    }

    @Test
    void severityAndPartialRecoveryReplaceTheExistingItemWithoutDuplicates() {
        var elevated = server("C01", "S01", ThermalAlertLevel.ELEVATED, true);
        notifier.update(snapshot(1, elevated));
        var initial = notifier.activeAlerts();
        String oldId = active.getFirst().getId();
        notifier.update(new HealthSnapshot(2, 123.5, List.of(server("C01", "S01", ThermalAlertLevel.CRITICAL, true))));
        assertEquals(1, notifications.size());
        var critical = active.getFirst();
        assertNotEquals(oldId, critical.getId());
        assertEquals(NotificationSeverity.ERROR, critical.getSeverity());
        assertTrue(critical.getMessage().contains("Critical temperature"));
        assertEquals(List.of(new ServerAlertPresentation(elevated.location(), elevated.alertReasons(),
                ThermalAlertLevel.CRITICAL, ThermalProtectionState.NORMAL, NotificationSeverity.ERROR, 123.5)),
                notifier.activeAlerts());
        assertEquals("C01-R01-S01 · 00d 00:02\nCritical temperature, High utilization", critical.getMessage());
        notifier.update(snapshot(3, server("C01", "S01", ThermalAlertLevel.ELEVATED, true)));
        assertEquals(NotificationSeverity.WARNING, active.getFirst().getSeverity());
        notifier.update(snapshot(4, server("C01", "S01", ThermalAlertLevel.NORMAL, true)));
        assertEquals(1, notifications.size());
        assertTrue(active.getFirst().getMessage().endsWith("High utilization"));
        assertFalse(active.getFirst().getMessage().contains("temperature"));
        assertEquals(List.of(new ServerAlertPresentation(elevated.location(), Set.of(ServerAlertReason.HIGH_UTILIZATION),
                ThermalAlertLevel.NORMAL, ThermalProtectionState.NORMAL, NotificationSeverity.WARNING, 240.0)),
                notifier.activeAlerts());
        notifier.update(snapshot(5, server("C01", "S01", ThermalAlertLevel.NORMAL, false)));
        assertEquals(0, notifications.size());
        assertEquals(List.of(new ServerAlertPresentation(elevated.location(), elevated.alertReasons(),
                ThermalAlertLevel.ELEVATED, ThermalProtectionState.NORMAL, NotificationSeverity.WARNING, 60.0)), initial);
    }

    @Test
    void pendingAlertsSurviveTheVisibleLimitAndCanBeResolvedBeforeBecomingVisible() {
        notifications.setMaxVisible(3);
        notifications.setPrioritizeSeverity(true);
        var pending = server("C01", "S01", ThermalAlertLevel.ELEVATED, false);
        var visible = server("C01", "S02", ThermalAlertLevel.CRITICAL, false);
        var otherRack = server("C01", "R02", "S01", ThermalAlertLevel.ELEVATED, false);
        var otherColumn = server("C02", "S01", ThermalAlertLevel.ELEVATED, false);
        notifier.update(snapshot(1, otherColumn, otherRack, visible, pending));
        assertEquals(4, notifications.size());
        assertTrue(active.stream().allMatch(item -> item.isPersistent()));
        var overflow = notifier.activeAlerts();
        assertEquals(List.of(pending.location(), visible.location(), otherRack.location(), otherColumn.location()),
                overflow.stream().map(ServerAlertPresentation::location).toList());
        assertThrows(UnsupportedOperationException.class, () -> overflow.removeFirst());
        assertThrows(UnsupportedOperationException.class, () -> overflow.getFirst().visibleReasons().clear());
        notifier.update(snapshot(2, otherColumn, visible, otherRack,
                server("C01", "S01", ThermalAlertLevel.NORMAL, false)));
        assertEquals(3, notifications.size());
        assertTrue(active.stream().noneMatch(item -> item.getMessage().startsWith("C01-R01-S01")));
        assertEquals(overflow.subList(1, 4), notifier.activeAlerts());
        notifier.update(snapshot(3, otherRack, otherColumn, server("C01", "S02", ThermalAlertLevel.NORMAL, false)));
        assertEquals(2, notifications.size());
        assertTrue(active.stream().noneMatch(item -> item.getMessage().startsWith("C01-R01-S02")));
        assertEquals(overflow.subList(2, 4), notifier.activeAlerts());
        assertEquals(4, overflow.size());
    }

    @Test
    void presentationCopiesMutableReasons() {
        var reasons = EnumSet.of(ServerAlertReason.HIGH_TEMPERATURE);
        var location = server("C01", "S01", ThermalAlertLevel.ELEVATED, false).location();
        var presentation = new ServerAlertPresentation(location, reasons, ThermalAlertLevel.ELEVATED,
                ThermalProtectionState.NORMAL, NotificationSeverity.WARNING, 0.0);
        reasons.clear();
        assertEquals(Set.of(ServerAlertReason.HIGH_TEMPERATURE), presentation.visibleReasons());
    }

    @Test
    void fullLocationIdentityOfflineRemovalAndResetOnlyDismissOwnedAlerts() {
        var unrelated = notifications.showPersistent("Unrelated");
        active.add(unrelated);
        notifier.update(snapshot(5,
                server("C01", "S01", ThermalAlertLevel.CRITICAL, false),
                server("C02", "S01", ThermalAlertLevel.CRITICAL, false)));
        assertEquals(3, notifications.size());
        assertEquals(2, notifier.activeAlerts().size());
        var offline = new ServerHealthSnapshot("offline", "C01", new RackCode("R01"), "S01",
                HardwareStatus.OFFLINE, Set.of(ServerAlertReason.HIGH_TEMPERATURE), .5, 110, ThermalAlertLevel.CRITICAL);
        notifier.update(snapshot(6, offline, server("C02", "S01", ThermalAlertLevel.CRITICAL, false)));
        assertEquals(2, notifications.size());
        assertEquals("C02", notifier.activeAlerts().getFirst().location().column());
        notifier.update(snapshot(7));
        assertEquals(List.of(unrelated), active);
        assertTrue(notifier.activeAlerts().isEmpty());
        notifier.update(snapshot(8, server("C02", "S01", ThermalAlertLevel.CRITICAL, false)));
        String oldId = active.getLast().getId();
        var beforeRestart = notifier.activeAlerts();
        var retainedLocation = server("C02", "S01", ThermalAlertLevel.CRITICAL, false);
        var newLocation = server("C01", "S02", ThermalAlertLevel.ELEVATED, false);
        notifier.update(snapshot(1, retainedLocation, newLocation));
        assertEquals(3, notifications.size());
        assertFalse(active.stream().anyMatch(item -> oldId.equals(item.getId())));
        assertTrue(active.getLast().getMessage().contains("00d 00:01"));
        assertEquals(List.of(newLocation.location(), retainedLocation.location()),
                notifier.activeAlerts().stream().map(ServerAlertPresentation::location).toList());
        assertTrue(notifier.activeAlerts().stream().allMatch(alert -> alert.observedStateSinceSeconds() == 60.0));
        assertEquals(480.0, beforeRestart.getFirst().observedStateSinceSeconds());
        notifier.update(snapshot(0));
        assertEquals(List.of(unrelated), active);
        assertTrue(notifier.activeAlerts().isEmpty());
        notifier.update(snapshot(1, newLocation));
        assertEquals(List.of(newLocation.location()), notifier.activeAlerts().stream().map(ServerAlertPresentation::location).toList());
        assertEquals(60.0, notifier.activeAlerts().getFirst().observedStateSinceSeconds());
    }

    private static HealthSnapshot snapshot(long tick, ServerHealthSnapshot... servers) {
        return new HealthSnapshot(tick, tick * 60, List.of(servers));
    }

    private static ServerHealthSnapshot server(String column, String slot, ThermalAlertLevel thermal, boolean utilization) {
        return server(column, "R01", slot, thermal, utilization);
    }

    private static ServerHealthSnapshot server(String column, String rack, String slot, ThermalAlertLevel thermal, boolean utilization) {
        var reasons = new java.util.HashSet<ServerAlertReason>();
        if (thermal != ThermalAlertLevel.NORMAL) reasons.add(ServerAlertReason.HIGH_TEMPERATURE);
        if (utilization) reasons.add(ServerAlertReason.HIGH_UTILIZATION);
        return new ServerHealthSnapshot(column + "-" + rack + "-" + slot, column, new RackCode(rack), slot,
                reasons.isEmpty() ? HardwareStatus.OK : HardwareStatus.ALERT, reasons, utilization ? .95 : .5,
                thermal == ThermalAlertLevel.CRITICAL ? 110 : 85, thermal);
    }
}
