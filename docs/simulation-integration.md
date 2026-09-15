# Backend Simulation Integration

## Dependency boundary

This repository consumes `cpz-sim-foundation`, `cpz-sim-runtime`, and `cpz-sim-datacenter` as Maven artifacts. It does not duplicate their model or simulation algorithms. `SimulationManager` is the adapter that constructs backend types from repository configuration and exposes their latest state to UI panel updaters through `SimulationContainer`.

`SimulationContainer` holds configuration/model objects, runtime services, snapshot providers, and the current cooling, energy, temperature, health, and operational snapshots.

## Initialization flow

`SimulationManager.initialize()` performs these stages in order:

1. Read timer period/speed properties and initialize play/speed controls.
2. Load the active datacenter JSON and create the backend model and cooling configuration.
3. Create deterministic Perlin/fractal-noise workload input (seed `1234L`) and scale it with configured server workload factors.
4. Create a `SimulationClock` with a one-minute simulation tick and its `SimulationEngine`.
5. Construct cooling, temperature, health, and energy systems.
6. Register systems on the engine in the required tick order.
7. Construct energy, temperature, and health snapshot providers.
8. Load hot-aisle mapping and build the column lookup.
9. Create operational snapshot server groups for every mapped aisle.

The registered engine order is workload, power consumption, cooling snapshot update, temperature, health, then energy consumption. Later stages consume state produced earlier in the same simulation tick, so this ordering must not be changed casually.

## Time and stepping

The backend `SimulationClock` advances one simulated minute per `SimulationEngine.step()`. The UI's `Timer` decides how often a step occurs in wall-clock milliseconds. It is polled from Processing's `draw()` loop rather than running the backend on another thread.

This single-threaded arrangement avoids concurrent mutation while controls and renderers read snapshots. The selected speed factor changes the timer period, not the backend tick duration. For example, factor `2` halves the real-time wait while each engine step still represents one simulated minute.

## Snapshot flow

An engine step changes backend state but does not directly mutate controls:

```text
Timer pulse
  -> SimulationEngine.step()
  -> UiStateContainer.updateSnapshots = true
  -> energy snapshot
  -> temperature snapshot
  -> health snapshot
  -> combined DatacenterOperationalSnapshot
  -> UiStateContainer.updateUI = true
  -> panel updaters mutate controls
```

The operational snapshot provider combines the three domain snapshots and produces rack/server-group metrics used throughout the UI. Hot-aisle definitions become operational server groups by collecting every server location in their configured columns.

## Cooling integration

The datacenter definition creates backend `CoolingConfiguration`, `CoolingSystem`, zones, and units. A `CoolingSnapshotCoordinator` produces the cooling snapshot used both by the temperature reference provider and selected-aisle metrics.

UI cooling toggles change `CoolingSystem` enabled state using stable backend unit codes. The next engine step and snapshot cycle propagates the effect through cooling, temperature, health, and UI metrics. See [Input and controls](controllers.md#cooling-toggles) for code mapping and recursion safeguards.

## UI projections

- The room view uses operational rack and aisle-group snapshots.
- The selected rack view joins per-server energy, temperature, and health snapshots by `ServerLocation`, plus its `RackOperationalSnapshot`.
- The selected aisle view combines an operational server group with an aggregate of cooling zones that contain any server location in the aisle's columns.
- The temperature scale is calculated once from backend ambient temperature and server thermal/max-power configuration.

The UI assumes the backend definition, hot-aisle mapping, and control-code layout describe the same physical room. Inconsistencies generally fail fast with missing rack, group, toggle, or indicator errors rather than being silently ignored.
