# Classic view health and alerts

The room-map hotspot flag, the Global Overview `SYSTEM STATUS`, and transient
notifications use `HealthSnapshot` from the same completed simulation tick.
`HealthPresentation` maps already evaluated states to visuals; it contains no
temperature, utilization, HVAC or PUE thresholds and stores no hysteresis state.

## Backend owns evaluation and hysteresis

The current classic `SimulationManager` passes the thresholds from
`data/config.properties` to `ServerHealthOptions`:

| Condition | Activation | Recovery |
| --- | --- | --- |
| High utilization | 0.90 | 0.85 |
| Elevated server temperature | 80 °C | 75 °C |
| Critical server temperature | 100 °C | 95 °C |

These are the repository's current scenario settings, not hard-coded UI rules
or equipment safety ratings. The backend retains the thermal level inside each
hysteresis band and publishes it as `thermalAlertLevel()`. It also evaluates
`alertReasons()` independently for utilization and temperature.

All consuming machines need the updated `cpz-sim-datacenter:0.1.0-alpha.1`
artifact with the thermal-level API installed. Its coordinates have not
changed; an older JAR at those same coordinates is insufficient.

## Room map

A rack is marked critical if **any online server at that full RackLocation**
is CRITICAL. The marker uses the existing dark hotspot background and hotspot
icon. The same rack code in another column is a different rack. Empty and
fully offline racks keep their existing presentation and cannot show a critical
thermal marker.

The average-online-temperature gradient remains a continuous display scale.
Reaching its upper bound (74 °C in the demo configuration) can saturate its
fuchsia color, but no longer activates the critical/hotspot flag. The gradient
bound is derived for visualization and is not a health threshold.

## Global Overview

`SYSTEM STATUS` summarizes evaluated server health:

- CRITICAL: at least one online server has a critical thermal level.
- WARNING: an online server has active backend alert reasons, with no critical
  thermal level anywhere online.
- NORMAL: online servers exist and have no active alert reasons.
- IDLE: there are no online servers.
- NO DATA: the health snapshot is unavailable.

The previous local heuristics based on averages, maximum rack temperature,
IT/HVAC percentages and PUE no longer determine this status. HVAC, PUE and load
metrics remain displayed independently. WATCH resources remain for compatibility,
but this backend health projection does not invent a WATCH band.

## Notifications

`SimulationAlertNotifier` compares both reasons and thermal levels by complete
ServerLocation. ELEVATED → CRITICAL emits ERROR even though HIGH_TEMPERATURE
remains active. CRITICAL → ELEVATED emits WARNING; full recovery emits SUCCESS.
If utilization remains high after thermal recovery, the result is still WARNING.
The first snapshot (or a backward tick reset) establishes a silent baseline.
Offline transitions are not reported as successful recovery.

Snapshots with the same tick and unchanged states do not generate duplicate
messages. Changing selection, drawing frames and operating cooling controls in
pause cannot reevaluate health; the next simulation tick updates the snapshot.
Already displayed notifications expire according to real-time style durations,
including while simulation is paused. The message timestamp uses simulated
elapsed time in `00d hh:mm` format.

The manager still has the configured visible limit (currently three) and no
backlog. Notifications report transitions, not a persistent active-alert list:
a critical rack may stay marked after its notification expires. This change
does not add queuing or priority retention for error notifications.

## Validation

`HealthPresentationTest` drives the real backend health system through normal,
elevated, critical, retained-critical, downgraded and recovered states. It checks
actual room/global control states, notification severity, same-tick suppression,
full-location identity, offline behavior and reset baselines without a window.

Manual acceptance: run the classic sketch, heat a server through the configured
critical boundary and cool it through recovery. Its rack marker and SYSTEM
STATUS should follow the backend level, with ERROR on escalation and WARNING
on downgrade. No automatic resize or frame-rate changes are introduced.
