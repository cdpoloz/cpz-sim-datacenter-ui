package com.cpz.sim.datacenter.ui.ui;

import com.cpz.processing.controls.core.overlay.notification.NotificationSeverity;
import com.cpz.sim.datacenter.health.ThermalAlertLevel;
import com.cpz.sim.datacenter.health.ServerAlertPolicy;
import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.ThermalProtectionState;
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
        return isThermalShutdown(server) || (server.status() != HardwareStatus.OFFLINE
                && server.thermalAlertLevel() == ThermalAlertLevel.CRITICAL);
    }

    public static boolean isThermalShutdown(ServerHealthSnapshot server) {
        return server.thermalProtectionState() == ThermalProtectionState.SHUTDOWN;
    }

    public static ServerAlertPolicy alertPolicy(DatacenterDefinition definition) {
        return definition == null ? ServerAlertPolicy.defaults() : definition.alertPolicy();
    }

    public static boolean hasActiveCondition(ServerHealthSnapshot server) {
        return hasActiveCondition(server, ServerAlertPolicy.defaults());
    }

    public static boolean hasActiveCondition(ServerHealthSnapshot server, ServerAlertPolicy policy) {
        return isThermalShutdown(server) || (server.status() != HardwareStatus.OFFLINE
                && (!policy.visibleReasons(server.alertReasons()).isEmpty() || server.thermalProtectionState() == ThermalProtectionState.THROTTLED));
    }

    public static NotificationSeverity notificationSeverity(ServerHealthSnapshot server) {
        return notificationSeverity(server, ServerAlertPolicy.defaults());
    }

    public static NotificationSeverity notificationSeverity(ServerHealthSnapshot server, ServerAlertPolicy policy) {
        if (isCritical(server)) return NotificationSeverity.ERROR;
        return hasActiveCondition(server, policy) ? NotificationSeverity.WARNING : NotificationSeverity.SUCCESS;
    }

    /** A rack remains critical if a server is critically hot online or shut down by thermal protection. */
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
        return systemStatus(snapshot, ServerAlertPolicy.defaults());
    }

    public static String systemStatus(HealthSnapshot snapshot, ServerAlertPolicy policy) {
        if (snapshot == null) return "NO DATA";
        boolean online = false;
        boolean alerts = false;
        for (ServerHealthSnapshot server : snapshot.servers()) {
            if (isCritical(server)) return "CRITICAL";
            if (server.status() == HardwareStatus.OFFLINE) continue;
            online = true;
            alerts |= hasActiveCondition(server, policy);
        }
        if (!online) return "IDLE";
        return alerts ? "WARNING" : "NORMAL";
    }
}
