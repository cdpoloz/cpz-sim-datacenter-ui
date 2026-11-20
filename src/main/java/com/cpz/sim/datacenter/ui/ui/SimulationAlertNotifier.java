package com.cpz.sim.datacenter.ui.ui;

import com.cpz.processing.controls.core.overlay.notification.NotificationManager;
import com.cpz.processing.controls.core.overlay.notification.NotificationSeverity;
import com.cpz.sim.datacenter.health.ServerAlertPolicy;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.snapshot.HealthSnapshot;
import com.cpz.sim.datacenter.snapshot.ServerHealthSnapshot;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Reconciles backend health into one persistent notification per server with an active health or protection condition.
 * @author CPZ
 */
public final class SimulationAlertNotifier {

    private final BiFunction<NotificationSeverity, String, String> persistentSink;
    private final Consumer<String> dismissSink;
    private final ServerAlertPolicy alertPolicy;
    private Map<ServerLocation, ActiveAlert> activeAlerts = Map.of();
    private List<ServerAlertPresentation> activeAlertPresentations = List.of();
    private long lastTick = -1L;

    public SimulationAlertNotifier(NotificationManager notifications) {
        this(notifications, ServerAlertPolicy.defaults());
    }

    public SimulationAlertNotifier(NotificationManager notifications, ServerAlertPolicy alertPolicy) {
        this(sinkFor(notifications), notifications::dismiss, alertPolicy);
    }

    SimulationAlertNotifier(BiFunction<NotificationSeverity, String, String> persistentSink,
                            Consumer<String> dismissSink) {
        this(persistentSink, dismissSink, ServerAlertPolicy.defaults());
    }

    SimulationAlertNotifier(BiFunction<NotificationSeverity, String, String> persistentSink,
                            Consumer<String> dismissSink, ServerAlertPolicy alertPolicy) {
        this.alertPolicy = Objects.requireNonNull(alertPolicy);
        this.persistentSink = Objects.requireNonNull(persistentSink);
        this.dismissSink = Objects.requireNonNull(dismissSink);
    }

    private static BiFunction<NotificationSeverity, String, String> sinkFor(NotificationManager notifications) {
        Objects.requireNonNull(notifications);
        return (severity, message) -> notifications.showPersistent(message, severity).getId();
    }

    /** Returns all active alerts, including overflow, in column, rack and slot order. */
    public List<ServerAlertPresentation> activeAlerts() {
        return activeAlertPresentations;
    }

    public void update(HealthSnapshot snapshot) {
        if (snapshot == null || snapshot.tickIndex() == lastTick) return;
        boolean rewound = snapshot.tickIndex() < lastTick;
        if (rewound) {
            activeAlerts.values().forEach(alert -> dismissSink.accept(alert.notificationId()));
            activeAlerts = Map.of();
        }
        Map<ServerLocation, ActiveAlert> currentAlerts = new LinkedHashMap<>();
        var orderedServers = snapshot.servers().stream()
                .sorted(Comparator.comparing(ServerHealthSnapshot::column)
                        .thenComparing(server -> server.rackCode().value())
                        .thenComparing(ServerHealthSnapshot::slot))
                .toList();

        for (ServerHealthSnapshot server : orderedServers) {
            if (!HealthPresentation.hasActiveCondition(server, alertPolicy)) continue;
            ServerLocation location = server.location();
            var visibleReasons = alertPolicy.visibleReasons(server.alertReasons());
            ActiveAlert previous = activeAlerts.get(location);
            if (previous != null) {
                ServerAlertPresentation shown = previous.presentation();
                if (visibleReasons.equals(shown.visibleReasons()) && server.thermalAlertLevel() == shown.thermalAlertLevel()
                        && server.thermalProtectionState() == shown.protectionState()) {
                    currentAlerts.put(location, previous);
                    continue;
                }
                // Replace the old item, including if it is pending behind the visible limit.
                dismissSink.accept(previous.notificationId());
            }
            var presentation = new ServerAlertPresentation(location, visibleReasons, server.thermalAlertLevel(),
                    server.thermalProtectionState(), HealthPresentation.notificationSeverity(server, alertPolicy),
                    snapshot.elapsedSeconds());
            String id = persistentSink.apply(presentation.severity(), notificationMessage(presentation));
            currentAlerts.put(location, new ActiveAlert(presentation, id));
        }
        // Recovery, operator-owned offline states and removed locations no longer have an active item.
        activeAlerts.forEach((location, alert) -> {
            if (!currentAlerts.containsKey(location)) dismissSink.accept(alert.notificationId());
        });
        if (rewound || !currentAlerts.equals(activeAlerts)) {
            activeAlerts = currentAlerts;
            activeAlertPresentations = currentAlerts.values().stream().map(ActiveAlert::presentation).toList();
        }
        lastTick = snapshot.tickIndex();
    }

    private static String notificationMessage(ServerAlertPresentation alert) {
        ServerLocation location = alert.location();
        String prefix = "%s-%s-%s · %s".formatted(location.column(), location.rackCode().value(), location.slot(),
                SimulationTimeFormat.daysHoursMinutes(alert.observedStateSinceSeconds()));
        return prefix + "\n" + alert.conditionText();
    }

    private record ActiveAlert(ServerAlertPresentation presentation, String notificationId) {
    }
}
