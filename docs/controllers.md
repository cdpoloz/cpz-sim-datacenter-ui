# Input, Selection, and Controls

## Input dispatch

Processing mouse callbacks are translated by `MouseInputDispatcher` into `PointerEvent` instances. `InputManager` dispatches them to `MainInputLayer`, which visits all registered pointer targets and reports the event consumed when at least one target was present. Buttons and Toggles register their `handlePointerEvent` methods during `ControlManager.initialize()`.

Control listeners call the small `Sketch.btnClicked()` and `Sketch.tglChanged()` adapters, which delegate to `UiInteractionController`. Keyboard releases go directly from `Sketch.keyReleased()` to the same controller. This keeps Processing-facing callback signatures at the boundary and code-based routing outside the sketch.

## Button and keyboard routing

`UiInteractionController` recognizes code prefixes: `btnSelectedRack...` selects a rack row and aisle side; `btnSelectedColumn...` selects a room column; `btnPlayPlus` and `btnPlayMinus` change speed. Space toggles simulation state, while Plus and Minus change speed through keyboard constants.

Selection changes set `updateUI` after the highlight state is updated. Speed changes update timer/control state immediately and do not require new simulation snapshots.

## Selected column, rack, and hot aisle

`SelectionManager` begins at column `C01` and rack `R01`. It resolves the selected `HotAisleDefinition` through the column-to-aisle map prepared by `SimulationManager`.

When a column button is clicked, its prefix is removed to obtain a code such as `C04`; the hot aisle is resolved, room and rack highlights move, and `updateUI` requests fresh panel values.

When a rack-side button is clicked, its `Left01` / `Right01` suffix yields rack `R01` and a side. For a shared aisle, Left selects the definition's first column and Right its last column. For the first and last wall columns, the current column is preserved and nonexistent outer-side buttons are hidden. The selected rack indicator replaces the button at that location, and `updateUI` refreshes panel content.

The selected rack panel resolves an exact `RackLocation`, joins energy/temperature/health snapshots by `ServerLocation`, and updates each slot plus aggregate rack metrics. The selected aisle panel uses the aisle's operational group, discovers cooling zones serving any aisle column, and averages representative rack temperatures across the aisle columns for each rack row. That returned list becomes the gradient input.

## Simulation play and speed controls

`tglPlay` and Space both call `SimulationManager.toggleSimulation()`. The manager toggles the real-time `Timer`, then programmatically sets `tglPlay` to the authoritative timer state. `syncingPlayToggle` prevents that programmatic update from re-entering the listener and toggling the timer a second time.

Speed factors are the ordered `simulation.timer.speed-factors` list. Plus moves one index higher and Minus one lower. The timer period is `simulation.timer.base-period-ms / selected speed factor`. The UI shows `Running xN` or `Stopped xN`, and disables the corresponding speed button at either end of the list.

## Cooling toggles

At startup, `CoolingToggleManager` derives individual UI codes from backend cooling units:

```text
SUPPLY-C01-C02  <->  tglSupplyC01-C02
EXHAUST-C02-C03 <->  tglExhaustC02-C03
```

Only codes beginning `tglSupplyC` or `tglExhaustC` map to backend units. A child toggle calls `CoolingSystem.setEnabled(unitCode, enabled)`, then recomputes its master. `tglSupply` is on when any configured supply child is on; `tglExhaust` follows the same rule.

A master toggle sets every child in its derived group and updates each corresponding backend cooling unit. `syncingCoolingToggles` is set around programmatic `setState()` calls so child/master listener notifications cannot recurse or apply the same transition twice. A `try/finally` always clears the guard if a configured control is missing or another update fails.

Cooling state affects backend results on subsequent simulation steps. The manager does not fabricate a UI snapshot immediately; the normal timer -> engine -> snapshot -> control path remains authoritative.
