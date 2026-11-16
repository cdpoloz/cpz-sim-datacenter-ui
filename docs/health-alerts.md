# Classic view health and alerts

The room-map hotspot flag, the Global Overview `SYSTEM STATUS`, and persistent
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
Its initial upper bound (74 °C in the demo configuration) expands to contain observed server temperatures. Reaching the current upper bound can saturate its
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

`SimulationAlertNotifier` maintains one persistent notification per complete
ServerLocation using `cpz-mvvm-processing-controls:0.9.15`. It uses
`showPersistent()` and retains the returned ID for programmatic `dismiss()`.
The UI consumes backend `alertReasons()` and `thermalAlertLevel()`; the backend
continues to own thresholds and hysteresis.

- Initial snapshots immediately show existing online alerts.
- Unchanged reasons and thermal levels retain the same notification and timestamp.
- Changed reasons or severity replace that server's notification. ELEVATED →
  CRITICAL uses ERROR; CRITICAL → ELEVATED uses WARNING.
- Partial recovery removes only the resolved reasons from the replacement message.
  High utilization can therefore remain after thermal recovery.
- Full recovery dismisses the item without creating a recovery notification.
- Offline servers and locations absent from the next snapshot have their items
  dismissed. This does not claim that an offline server has thermally recovered.
- A backward tick reset dismisses only this notifier's items, then displays the
  new snapshot's active alerts. Unrelated manager notifications are preserved.

Repeated snapshots at the same tick and drawing paused frames do not create
new items. Null snapshots retain existing alerts until new health data arrives.
Cooling controls in pause do not reevaluate health; the next completed tick
updates the backend snapshot and the alert list.

The message timestamp uses simulated elapsed time in `00d hh:mm` format, with
the same formatter and snapshot time as the header. It records the last change
of reasons or severity, rather than advancing with every tick.

`data/config/notifications.json` retains three visible items and severity
priority. Persistent overflow remains pending in the manager and appears as
space becomes available; pending items can also be dismissed or replaced by ID.
`persistentDismissible: false` hides manual close buttons so active conditions
remain controlled by the simulation. Timed durations still apply to unrelated
transient messages, but do not apply to these persistent alerts, including
while paused. The panel is a view of active conditions, not an event history.

## Validation

`HealthPresentationTest` drives the real backend health system through normal,
elevated, critical, retained-critical, downgraded and recovered states. It checks
actual room/global control states, notification severity, same-tick suppression,
full-location identity, offline behavior and session resets without a window.
`SimulationAlertNotifierTest` also exercises the real notification manager for
persistence, replacement, partial recovery, pending overflow, removal, and
isolation from unrelated notifications.

Manual acceptance: run the classic sketch, heat a server through the configured
critical boundary and cool it through recovery. Its rack marker and SYSTEM
STATUS should follow the backend level, with ERROR on escalation and WARNING
on downgrade. Recovery must remove the alert; pausing must retain it.
No automatic resize or frame-rate changes are introduced.
