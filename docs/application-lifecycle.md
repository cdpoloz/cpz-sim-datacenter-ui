# Processing Application Lifecycle

## Startup

`Launcher.main()` sets the process locale to `en-US`, installs a log-handler shutdown hook, loads `data/config.properties` as UTF-8, and calls `PApplet.main(Sketch.class)`. Processing then controls the application thread and invokes the sketch lifecycle.

### `settings()`

Processing requires renderer-dependent window configuration in `settings()`. The method sets the window icon, reads the configured canvas size, selects Processing's `P2D` renderer, and applies smoothing. These calls cannot be moved to an ordinary service without changing how Processing initializes its surface.

### `setup()`

Processing calls `setup()` once after creating the surface. It establishes the initial background, frame rate, and window title, then acts as the application's composition root. The full startup dependency order is documented in [Architecture](architecture.md#composition-root).

The simulation engine takes one initial step before snapshots are captured. `updateSnapshots` and `updateUI` are then set so the initial control state is derived through the same update path used at runtime. Cooling toggle group codes are derived only after a cooling snapshot exists.

## Frame loop: `draw()`

Processing calls `draw()` at the configured target of 60 FPS. The method deliberately performs update work before drawing:

1. `HeaderUpdater.update()` refreshes the room label and, only when wall-clock values change, the time/date labels.
2. `UiUpdateCoordinator.updateClock()` polls the simulation timer. A pulse steps the backend engine and sets `updateSnapshots`.
3. `updateSnapshotsIfNeeded()` captures energy, temperature, health, and combined operational snapshots, then sets `updateUI`.
4. `updateControlsIfNeeded()` projects the current snapshots and selection into all three panels and saves selected-aisle temperatures for rendering.
5. `StaticUiRenderer.drawBackground()` clears the frame and draws the six background image layers.
6. `ControlRenderer.draw()` draws indicators, labels, buttons, and toggles in group order.
7. `SelectedHotAisleTemperatureGradientRenderer.draw()` draws the dynamic aisle heat shape above the controls.
8. `StaticUiRenderer.drawOverlay()` draws active control overlays and finally `ui_overlay.png`.

See [Rendering](rendering.md) for why this stacking order is significant. The simulation timer can be stopped while Processing continues to draw frames and accept input. Stopping the simulation therefore freezes backend evolution, not the UI event loop.

## Mouse events

The Processing callbacks `mouseMoved()`, `mouseDragged()`, `mousePressed()`, and `mouseReleased()` forward the current coordinates/button to `MouseInputDispatcher`. `mouseWheel(MouseEvent)` additionally forwards the wheel count and Shift/Control modifier state. A null wheel event is ignored defensively.

`MouseInputDispatcher` creates library `PointerEvent` values and sends them to `InputManager`. The registered `MainInputLayer` fans each event out to every registered pointer target. `ControlManager` registers interactive Buttons and Toggles as those targets during `setup()`.

These callback overrides must stay on the `PApplet` subclass because Processing discovers and calls them directly.

## Keyboard events

The active keyboard path is `Sketch.keyReleased()` -> `UiInteractionController.handleKeyReleased()`: Space toggles simulation play/pause; Plus selects the next configured speed factor; Minus selects the previous factor.

Button and Toggle callbacks also enter through small methods on `Sketch` because their listeners are bound during setup. Business decisions are immediately delegated to `UiInteractionController`, `SimulationManager`, `CoolingToggleManager`, or `SelectionManager`.

`InfrastructureContainer` exposes a `ProcessingKeyboardAdapter` slot for library integration, but the active flow does not initialize or use it.

## Processing-dependent code

Lifecycle and event callback methods must remain in `Sketch`. Components that load `PImage`/`PFont`, call `map()`, read canvas dimensions or wall-clock helpers, or issue drawing commands also depend on Processing; they access the sketch through `ApplicationContext`. All such work happens during setup or on the Processing sketch thread.
