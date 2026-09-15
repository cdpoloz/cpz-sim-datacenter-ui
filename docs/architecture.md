# Architecture

## Responsibilities and dependency direction

The repository is an adapter between two worlds:

- Processing owns the window, lifecycle, render thread, drawing API, and raw mouse/keyboard callbacks.
- `cpz-sim-datacenter` owns the datacenter model, simulation systems, clock-driven state changes, cooling configuration, and snapshot types.
- `cpz-mvvm-processing-controls` supplies JSON-configured controls, input layers, and overlays.
- This project composes those libraries, turns backend snapshots into UI state, and renders the result.

The central dependency path is:

```text
Launcher -> Sketch.setup()
               |
               +-> infrastructure and configured controls
               +-> backend SimulationManager -> SimulationContainer
               +-> SelectionManager
               +-> panel updaters -> UiComponentContainer
               +-> renderers

Sketch.draw() -> UiUpdateCoordinator -> snapshots -> panel controls
              -> renderers -> Processing canvas
```

## Composition root

`Sketch.setup()` is the composition root. Construction order matters because later components require objects initialized earlier:

1. `ApplicationContext` captures the active `Sketch`.
2. `InfrastructureInitializer` populates `InfrastructureContainer`.
3. `UiContainersInitializer` loads formats and establishes the initial `UiStateContainer` flags.
4. `ControlManager` loads and registers JSON controls.
5. `ResourceManager` loads fonts and images.
6. Simulation, cooling, selection, panel update, coordination, and rendering objects are wired.
7. `SimulationManager.initialize()` builds the backend graph.
8. `SelectionManager.initialize()` establishes `C01` / `R01` and highlights.
9. The temperature range is calculated, the engine takes its initial step, and initial snapshots/controls are requested.
10. Cooling groups and static labels are initialized after the first snapshot exists.

Most setup objects are local variables by design. A field would incorrectly suggest that the object participates directly in the ongoing Processing lifecycle. Only the infrastructure needed by mouse callbacks, state/render/update objects used by `draw()`, and the controller used by callbacks remain fields on `Sketch`. Other objects remain reachable through those runtime objects where needed.

## Core application types

### `ApplicationContext` and `ApplicationComponent`

`ApplicationContext` provides the active Processing `Sketch`. `ApplicationComponent` is the base class for active components that need `sketch()` for resource loading, coordinate mapping, time, or drawing. It is not a general service locator: simulation, UI, and resource dependencies are still passed explicitly to constructors.

Passive containers, records, and configuration objects do not extend `ApplicationComponent`, because they neither need Processing nor own sketch-thread behavior.

### `Initializable`

`Initializable` gives startup participants one uniform `initialize()` operation. Initialization is invoked explicitly in `Sketch.setup()` so order remains visible. It does not imply lazy initialization or automatic discovery.

### `InfrastructureContainer` and `InfrastructureInitializer`

The container holds `InputManager`, the priority-zero `MainInputLayer`, `MouseInputDispatcher`, `OverlayManager`, and a slot for `ProcessingKeyboardAdapter`. The initializer creates and registers the active mouse/input and overlay infrastructure. Keyboard shortcuts in the active UI currently arrive through `Sketch.keyReleased()` rather than `ProcessingKeyboardAdapter`.

### UI containers and initialization

- `UiComponentContainer` owns code-indexed maps of every loaded Label, Indicator, Button, and Toggle group.
- `UiFormatContainer` holds the `String.format` patterns read from `config.properties`.
- `UiFormatLoader` copies the `number.format.*` properties into that container.
- `UiStateContainer` holds render-loop invalidation flags, the global temperature scale, and temperatures for the selected aisle gradient.
- `UiContainersInitializer` loads formats and resets UI state before controls and simulation are initialized.
- `UiStateInitializer` fills values that are initialized once: temperature scale labels, initial server totals, date, and time.

### Runtime coordinators

- `SimulationContainer` is the shared backend object graph and latest-snapshot store.
- `SimulationManager` creates the datacenter and systems, registers their tick order, drives the timer/engine, captures snapshots, and synchronizes play/speed controls.
- `UiUpdateCoordinator` converts timer pulses into snapshot invalidation and snapshot changes into control invalidation.
- `UiInteractionController` routes stable button/toggle/key codes to simulation, cooling, and selection behavior.
- `CoolingToggleManager` maps UI toggle codes to cooling-unit codes and synchronizes master/child toggles.
- `SelectionManager` owns the selected column/rack/hot-aisle relationship and associated highlights.

### Panel updaters and renderers

Panel updaters project backend snapshots into already-created controls:

- `SelectedRackPanelUpdater` resolves the selected rack, updates slots and alerts, and renders rack summary values.
- `SelectedAislePanelUpdater` aggregates cooling zones and operational server groups, updates aisle metrics, and returns rack-row temperatures for the gradient.
- `RoomPanelUpdater` colors room racks/hot aisles and updates rack condition markers.
- `HeaderUpdater` updates the room title and wall-clock date/time.

Renderers only draw:

- `StaticUiRenderer` draws background images, active overlays, and the static foreground overlay.
- `ControlRenderer` draws control groups in their required stacking order.
- `SelectedHotAisleTemperatureGradientRenderer` draws the dynamic tapered heat gradient between controls and the foreground overlay.

## State invalidation

`updateSnapshots` means that the backend engine has advanced but the stored UI snapshots have not yet been refreshed. `updateUI` means controls must be projected again from the latest snapshots and current selection.

```text
timer pulse -> engine.step() -> updateSnapshots = true
updateSnapshots -> capture snapshots -> updateSnapshots = false -> updateUI = true
updateUI -> update all panels and gradient data -> updateUI = false
selection click -> update highlight state -> updateUI = true
```

The flags preserve frame-by-frame rendering while avoiding snapshot aggregation and control mutation on every frame.
