# CPZ SIM Datacenter UI

`cpz-sim-datacenter-ui` is the Processing-based desktop user interface for the CPZ datacenter simulation. It visualizes rack health, temperature, power, workload, cooling, and hot-aisle metrics while driving the simulation engine supplied by the separate `cpz-sim-datacenter` backend project.

The UI is intentionally thin: backend domain objects and systems produce immutable snapshots, and this project translates those snapshots into Processing controls and rendered layers.

## Architecture at a glance

`Launcher` loads `data/config.properties` and starts the Processing `Sketch`. During `Sketch.setup()`, the application constructs its infrastructure, controls, assets, backend simulation, selection logic, panel updaters, and renderers. Most of those objects are startup-only wiring and therefore remain local variables. Only objects needed by `draw()` or a Processing input callback are retained as fields.

Each frame follows this order:

1. Update the header.
2. Poll and, when due, advance the simulation clock.
3. Refresh backend snapshots when a simulation step occurred.
4. Refresh controls when snapshots or user selection changed.
5. Draw static background layers.
6. Draw controls.
7. Draw the selected hot-aisle temperature gradient.
8. Draw active overlays and the static foreground overlay.

See [Architecture](docs/architecture.md), [Application lifecycle](docs/application-lifecycle.md), [Simulation integration](docs/simulation-integration.md), and [Rendering](docs/rendering.md) for the detailed flows.

## Project layout

| Path | Purpose |
| --- | --- |
| `src/main/java/com/cpz/sim/datacenter/ui/main` | Java entry point and active Processing sketch; also contains the preserved reference template. |
| `.../app` | Shared Processing context plus infrastructure containers and startup contracts. |
| `.../config` | UI-specific configuration models/loaders and reference-template configuration. |
| `.../controls` | JSON control loading, registration, and cooling-toggle behavior. |
| `.../input` | Translation and dispatch of Processing mouse events. |
| `.../resources` | Processing font and image loading. |
| `.../simulation` | Backend construction, engine stepping, snapshot capture, and simulation timing. |
| `.../ui` | UI state, number formats, update coordination, interaction routing, and header updates. |
| `.../ui/panel` | Snapshot-to-control projection for the room, selected aisle, and selected rack. |
| `.../ui/render` | Ordered drawing of controls, static imagery, overlays, and the aisle gradient. |
| `.../ui/selection` | Selected column, rack, hot aisle, and highlight state. |
| `data/config` | Window/simulation properties, control definitions, datacenter data, and hot-aisle mapping. |
| `data/img` | Background, overlay, icon, button, toggle, and indicator assets. |
| `data/font` | JetBrains Mono and its license. |
| `docs` | Architecture and developer guides. |

## Requirements

- JDK 26, matching `maven.compiler.release` in `pom.xml`.
- Apache Maven.
- A graphical environment supported by Processing's P2D renderer.
- Access to the Maven artifacts declared in `pom.xml`, including `cpz-sim-foundation`, `cpz-sim-runtime`, `cpz-sim-datacenter`, `cpz-mvvm-processing-controls`, and `cpz-utils` at the pinned versions.

Processing is consumed as the Maven dependency `org.processing:core:4.5.6`; installing the Processing IDE is not required for the Maven build.

## Build and run

Run commands from the repository root because configuration and assets are loaded from relative `data/...` paths.

Compile and package the project:

```bash
mvn package
```

There is no Maven execution plugin or wrapper configured. To run the compiled application with Maven's resolved runtime classpath:

```bash
mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:$(<target/classpath.txt)" com.cpz.sim.datacenter.ui.main.Launcher
```

The project currently has no `src/test` test suite. `mvn test` still performs compilation and runs any tests added later.

## Configuration

`data/config.properties` controls the window, frame rate, number formats, boundary columns, health thresholds, simulation speed factors, and selected-aisle gradient geometry. JSON files under `data/config` define the control layout and backend datacenter model. The active backend input is currently `datacenter-test-complete-rezoned-edge-cases-custom-v2.json`; `SimulationManager` selects it directly.

`hot-aisle-mapping.json` maps columns such as `C02` and `C03` to UI hot aisles such as `HA02`. Control codes in the JSON files are integration identifiers and must stay aligned with the code that resolves buttons, toggles, labels, and indicators.

See [Configuration and assets](docs/configuration.md) before changing configuration keys, identifiers, or files.

## Controls

- Click a column button to select its hot aisle and rack column.
- Click a left or right rack button in the selected-aisle view to select a rack on the corresponding aisle side.
- Use `tglPlay` or the Space bar to start or stop simulation stepping.
- Use `btnPlayPlus` / `btnPlayMinus`, or the Plus / Minus keys, to change the configured speed factor.
- Use an individual `tglSupply...` or `tglExhaust...` control to enable or disable its backend cooling unit.
- Use master `tglSupply` or `tglExhaust` to apply a state to every child unit of that type.

See [Input and controls](docs/controllers.md) for dispatch, selection, and toggle synchronization details.

## Development notes

The current structure separates composition from runtime work. `Sketch.setup()` is the composition root: it wires local containers and services in dependency order. `ApplicationContext` exposes the `Sketch` only to components that require Processing APIs; passive state and configuration objects do not inherit from `ApplicationComponent`. `UiUpdateCoordinator` uses `updateSnapshots` and `updateUI` as explicit invalidation flags so the 60 FPS render loop does not rebuild snapshots or controls unnecessarily.

Some methods must remain on `Sketch`: Processing discovers lifecycle and input callbacks by subclassing `PApplet`, and controls bind to sketch callback adapters during setup. Rendering helpers can be separate classes, but they call Processing through `ApplicationContext` and must execute on the sketch thread.

`src/main/java/com/cpz/sim/datacenter/ui/main/TemplateSketch.java` is intentionally retained as a reference for control types beyond Button, Label, and Toggle. It is not part of the active UI flow; `Launcher` starts `Sketch`.

See [Development notes](docs/development.md) for extension guidelines and invariants.

## License and related projects

The project is licensed under Apache License 2.0; see `LICENSE`. JetBrains Mono is distributed under the SIL Open Font License 1.1; see `data/font/OFL.txt`.

Related projects: [cpz-sim-foundation](https://github.com/cdpoloz/cpz-sim-foundation), [cpz-sim-runtime](https://github.com/cdpoloz/cpz-sim-runtime), [cpz-sim-datacenter](https://github.com/cdpoloz/cpz-sim-datacenter), [cpz-mvvm-processing-controls](https://github.com/cdpoloz/cpz-mvvm-processing-controls), and [cpz-utils](https://github.com/cdpoloz/cpz-utils).

---

## Author

**Carlos Polo Zamora**  
GitHub: https://github.com/cdpoloz  
Alias: CPZ / cepezeta / cdpoloz
