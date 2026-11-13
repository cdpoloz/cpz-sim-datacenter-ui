package com.cpz.sim.datacenter.ui.ui;

import com.cpz.processing.controls.core.overlay.notification.NotificationSeverity;
import com.cpz.sim.datacenter.health.ThermalAlertLevel;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.snapshot.HealthSnapshot;
import com.cpz.sim.datacenter.snapshot.ServerHealthSnapshot;

import java.util.HashSet;
import java.util.Set;

/** Projects evaluated backend health into visual states; never evaluates raw temperatures. */
public final class HealthPresentation {

    private HealthPresentation() {
    }

    public static boolean isCritical(ServerHealthSnapshot server) {
        return server.status() != HardwareStatus.OFFLINE
                && server.thermalAlertLevel() == ThermalAlertLevel.CRITICAL;
    }

    public static NotificationSeverity notificationSeverity(ServerHealthSnapshot server) {
        if (isCritical(server)) return NotificationSeverity.ERROR;
        return server.hasAlerts() ? NotificationSeverity.WARNING : NotificationSeverity.SUCCESS;
    }

    /** A rack is critical when any of its online servers has a critical thermal level. */
    public static Set<RackLocation> criticalRacks(HealthSnapshot snapshot) {
        if (snapshot == null) return Set.of();
        Set<RackLocation> racks = new HashSet<>();
        for (ServerHealthSnapshot server : snapshot.servers()) {
            if (isCritical(server)) racks.add(server.location().rackLocation());
        }
        return Set.copyOf(racks);
    }

    /** Overall server-health projection, not an HVAC/PUE performance rating. */
    public static String systemStatus(HealthSnapshot snapshot) {
        if (snapshot == null) return "NO DATA";
        boolean online = false;
        boolean alerts = false;
        for (ServerHealthSnapshot server : snapshot.servers()) {
            if (server.status() == HardwareStatus.OFFLINE) continue;
            online = true;
            if (isCritical(server)) return "CRITICAL";
            alerts |= server.hasAlerts();
        }
        if (!online) return "IDLE";
        return alerts ? "WARNING" : "NORMAL";
    }
}
