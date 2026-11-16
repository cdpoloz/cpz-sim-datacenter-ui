package com.cpz.sim.datacenter.ui.ui;

import com.cpz.processing.controls.core.overlay.notification.NotificationManager;
import com.cpz.processing.controls.core.overlay.notification.NotificationSeverity;
import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.health.ThermalAlertLevel;
import com.cpz.sim.datacenter.model.ThermalProtectionState;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.snapshot.HealthSnapshot;
import com.cpz.sim.datacenter.snapshot.ServerHealthSnapshot;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Reconciles backend health into one persistent notification per server with an active health or protection condition.
 * @author CPZ
 */
public final class SimulationAlertNotifier {

    private final BiFunction<NotificationSeverity, String, String> persistentSink;
    private final Consumer<String> dismissSink;
    private Map<ServerLocation, ActiveAlert> activeAlerts = new HashMap<>();
    private long lastTick = -1L;

    public SimulationAlertNotifier(NotificationManager notifications) {
        this(sinkFor(notifications), notifications::dismiss);
    }

    SimulationAlertNotifier(BiFunction<NotificationSeverity, String, String> persistentSink,
                            Consumer<String> dismissSink) {
        this.persistentSink = Objects.requireNonNull(persistentSink);
        this.dismissSink = Objects.requireNonNull(dismissSink);
    }

    private static BiFunction<NotificationSeverity, String, String> sinkFor(NotificationManager notifications) {
        Objects.requireNonNull(notifications);
        return (severity, message) -> notifications.showPersistent(message, severity).getId();
    }

    public void update(HealthSnapshot snapshot) {
        if (snapshot == null || snapshot.tickIndex() == lastTick) return;
        if (snapshot.tickIndex() < lastTick) {
            activeAlerts.values().forEach(alert -> dismissSink.accept(alert.notificationId()));
            activeAlerts.clear();
        }
        Map<ServerLocation, ActiveAlert> currentAlerts = new HashMap<>();
        String simulatedTime = SimulationTimeFormat.daysHoursMinutes(snapshot.elapsedSeconds());
        var orderedServers = snapshot.servers().stream()
                .sorted(Comparator.comparing(ServerHealthSnapshot::column)
                        .thenComparing(server -> server.rackCode().value())
                        .thenComparing(ServerHealthSnapshot::slot))
                .toList();

        for (ServerHealthSnapshot server : orderedServers) {
            if (!HealthPresentation.hasActiveCondition(server)) continue;
            ServerLocation location = server.location();
            AlertState current = new AlertState(server.alertReasons(), server.thermalAlertLevel(), server.thermalProtectionState());
            ActiveAlert previous = activeAlerts.get(location);
            if (previous != null && current.equals(previous.state())) {
                currentAlerts.put(location, previous);
                continue;
            }
            // Replace the old item, including if it is pending behind the visible limit.
            if (previous != null) dismissSink.accept(previous.notificationId());
            String prefix = "%s-%s-%s · %s"
                    .formatted(server.column(), server.rackCode().value(), server.slot(), simulatedTime);
            String reasons = current.reasons().stream()
                    .sorted(Comparator
                            .comparingInt((ServerAlertReason reason) -> switch (reason) {
                                case HIGH_TEMPERATURE -> 0;
                                case HIGH_UTILIZATION -> 1;
                                default -> 2;
                            })
                            .thenComparingInt(ServerAlertReason::ordinal))
                    .map(reason -> reasonText(reason, current.thermalLevel()))
                    .collect(Collectors.joining(", "));
            if (current.protectionState() == ThermalProtectionState.SHUTDOWN) {
                reasons = "Thermal protection shutdown";
            } else if (current.protectionState() == ThermalProtectionState.THROTTLED) {
                reasons = reasons.isEmpty() ? "Thermal throttling" : reasons + ", Thermal throttling";
            }
            String id = persistentSink.apply(HealthPresentation.notificationSeverity(server), prefix + "\n" + reasons);
            currentAlerts.put(location, new ActiveAlert(current, id));
        }
        // Recovery, operator-owned offline states and removed locations no longer have an active item.
        activeAlerts.forEach((location, alert) -> {
            if (!currentAlerts.containsKey(location)) dismissSink.accept(alert.notificationId());
        });
        activeAlerts = currentAlerts;
        lastTick = snapshot.tickIndex();
    }

    private record ActiveAlert(AlertState state, String notificationId) {
    }

    private static String reasonText(ServerAlertReason reason, ThermalAlertLevel thermalLevel) {
        return switch (reason) {
            case HIGH_UTILIZATION -> "High utilization";
            case HIGH_TEMPERATURE -> thermalLevel == ThermalAlertLevel.CRITICAL ? "Critical temperature" : "High temperature";
            default -> reason.name().replace('_', ' ');
        };
    }

    private record AlertState(Set<ServerAlertReason> reasons, ThermalAlertLevel thermalLevel,
                              ThermalProtectionState protectionState) {
        private AlertState {
            reasons = Set.copyOf(reasons);
            Objects.requireNonNull(thermalLevel);
            Objects.requireNonNull(protectionState);
        }
    }
}
