package com.cpz.sim.datacenter.ui.ui;

import com.cpz.processing.controls.core.overlay.notification.NotificationSeverity;
import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.health.ThermalAlertLevel;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.model.ThermalProtectionState;

import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable presentation of a server alert. The observation time is the simulated elapsed time
 * at the tick when the UI created or replaced this alert state.
 */
public record ServerAlertPresentation(ServerLocation location, Set<ServerAlertReason> visibleReasons,
                                      ThermalAlertLevel thermalAlertLevel, ThermalProtectionState protectionState,
                                      NotificationSeverity severity, double observedStateSinceSeconds) {
    public ServerAlertPresentation {
        Objects.requireNonNull(location);
        visibleReasons = Set.copyOf(visibleReasons);
        Objects.requireNonNull(thermalAlertLevel);
        Objects.requireNonNull(protectionState);
        Objects.requireNonNull(severity);
    }

    /** Formats the evaluated condition for both the summary and the expanded list. */
    public String conditionText() {
        if (protectionState == ThermalProtectionState.SHUTDOWN) return "Thermal protection shutdown";
        String reasons = visibleReasons.stream()
                .sorted(Comparator.comparingInt((ServerAlertReason reason) -> switch (reason) {
                    case HIGH_TEMPERATURE -> 0;
                    case HIGH_UTILIZATION -> 1;
                    default -> 2;
                }).thenComparingInt(ServerAlertReason::ordinal))
                .map(this::reasonText)
                .collect(Collectors.joining(", "));
        if (protectionState == ThermalProtectionState.THROTTLED) {
            return reasons.isEmpty() ? "Thermal throttling" : reasons + ", Thermal throttling";
        }
        return reasons;
    }

    private String reasonText(ServerAlertReason reason) {
        return switch (reason) {
            case HIGH_UTILIZATION -> "High utilization";
            case HIGH_TEMPERATURE -> thermalAlertLevel == ThermalAlertLevel.CRITICAL
                    ? "Critical temperature" : "High temperature";
            default -> reason.name().replace('_', ' ');
        };
    }
}
