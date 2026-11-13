package com.cpz.sim.datacenter.ui.ui;

import com.cpz.processing.controls.core.overlay.notification.NotificationManager;
import com.cpz.processing.controls.core.overlay.notification.NotificationSeverity;
import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.health.ThermalAlertLevel;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.snapshot.HealthSnapshot;
import com.cpz.sim.datacenter.snapshot.ServerHealthSnapshot;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.BiConsumer;

/**
 * @author CPZ
 */
public final class SimulationAlertNotifier {

    private final BiConsumer<NotificationSeverity, String> notificationSink;
    private Map<ServerLocation, AlertState> previousStates = new HashMap<>();
    private long lastTick = -1L;

    public SimulationAlertNotifier(NotificationManager notifications) {
        this(sinkFor(notifications));
    }

    SimulationAlertNotifier(BiConsumer<NotificationSeverity, String> notificationSink) {
        this.notificationSink = Objects.requireNonNull(notificationSink);
    }

    private static BiConsumer<NotificationSeverity, String> sinkFor(NotificationManager notifications) {
        Objects.requireNonNull(notifications);
        return (severity, message) -> notifications.show(message, severity);
    }

    public void update(HealthSnapshot snapshot) {
        if (snapshot == null || snapshot.tickIndex() == lastTick) return;
        boolean baseline = lastTick < 0L || snapshot.tickIndex() < lastTick;
        Map<ServerLocation, AlertState> currentStates = new HashMap<>();
        String simulatedTime = formatSimulatedTime(snapshot.elapsedSeconds());
        var orderedServers = snapshot.servers().stream()
                .sorted(Comparator.comparing(ServerHealthSnapshot::column)
                        .thenComparing(server -> server.rackCode().value())
                        .thenComparing(ServerHealthSnapshot::slot))
                .toList();

        for (ServerHealthSnapshot server : orderedServers) {
            if (server.status() == HardwareStatus.OFFLINE) continue;
            ServerLocation location = server.location();
            AlertState current = new AlertState(server.alertReasons(), server.thermalAlertLevel());
            AlertState previous = previousStates.get(location);
            currentStates.put(location, current);
            if (baseline) continue;
            if (current.equals(previous)) continue;
            if (previous == null && current.reasons().isEmpty()) continue;
            String prefix = "%s-%s-%s · %s"
                    .formatted(
                            server.column(),
                            server.rackCode().value(),
                            server.slot(),
                            simulatedTime
                    );
            if (current.reasons().isEmpty()) {
                notificationSink.accept(HealthPresentation.notificationSeverity(server), prefix + "\nAlertas resueltas");
                continue;
            }
            String reasons = current.reasons().stream()
                    .sorted()
                    .map(reason -> reasonText(reason, current.thermalLevel()))
                    .collect(Collectors.joining(", "));
            String message = prefix + "\n" + reasons;
            notificationSink.accept(HealthPresentation.notificationSeverity(server), message);
        }
        previousStates = currentStates;
        lastTick = snapshot.tickIndex();
    }

    private static String formatSimulatedTime(double elapsedSeconds) {
        long totalMinutes = (long) (elapsedSeconds / 60.0);
        long days = totalMinutes / 1440;
        long hours = (totalMinutes % 1440) / 60;
        long minutes = totalMinutes % 60;
        return "%02dd %02d:%02d".formatted(days, hours, minutes);
    }

    private static String reasonText(ServerAlertReason reason, ThermalAlertLevel thermalLevel) {
        return switch (reason) {
            case HIGH_UTILIZATION -> "High utilization";
            case HIGH_TEMPERATURE -> thermalLevel == ThermalAlertLevel.CRITICAL ? "Critical temperature" : "High temperature";
            default -> reason.name().replace('_', ' ');
        };
    }

    private record AlertState(Set<ServerAlertReason> reasons, ThermalAlertLevel thermalLevel) {
        private AlertState {
            reasons = Set.copyOf(reasons);
            Objects.requireNonNull(thermalLevel);
        }
    }
}
