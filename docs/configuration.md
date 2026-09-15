# Configuration and Assets

Configuration is split between Java properties, control JSON, UI-specific aisle mapping, and the backend datacenter definition. The application expects to start with the repository root as its working directory so relative `data/...` paths resolve correctly.

## `data/config.properties`

`Launcher` loads this file before Processing starts. Missing or invalid required values generally fail fast during startup.

| Key group | Used for |
| --- | --- |
| `window.*` | Surface title and icon. |
| `sketch.*` | Canvas size, target FPS, and smoothing level. |
| `number.format.*` | `String.format` patterns for percentages, temperatures, power, speed, pressure, and paired supply/exhaust airflow. |
| `datacenter.first.column`, `datacenter.last.column` | Edge-aisle behavior, rack-button visibility, and maximum-temperature marker side. |
| `simulation.health.*` | Backend alert/recovery thresholds and the UI warning threshold. |
| `simulation.timer.*` | Base real-time period, allowed speed factors, and initial factor. |
| `ui.temperature.gradient.*` | Normalized geometry and border weight for the selected-aisle gradient. |

The spelling of existing keys is API. In particular, `ui.temperature.gradient.stroke.weigth` is consumed with that exact spelling and must not be corrected in isolation.

### Number formats

`UiFormatLoader` reads all `number.format.*` values into `UiFormatContainer` during `UiContainersInitializer.initialize()`. Panel updaters receive only the patterns they need and apply them with `String.format`. `Launcher` sets the default locale to `en-US`, making decimal formatting stable across host locales.

Current formats include units directly, such as `%.1f°C`, `%.2fkW`, and `%.1f / %.1f m³/s`. The temperature scale uses the separate integer-like `number.format.temperature.simple` pattern.

## Control JSON

`ControlManager` uses `ControlConfigLoader` from `cpz-mvvm-processing-controls` to create controls from `labels.json`, the specialized `indicators*.json` files, three Button files, and `toggles.json`.

The resulting objects are grouped by type and indexed by `code` in `UiComponentContainer`. Java code performs direct lookups, so codes such as `tglPlay`, `btnPlayPlus`, `indSelectedRackLeft01`, and `lblSelectedAisleAirflowValue` are stable integration identifiers, not presentation text.

The JSON files also reference assets below `data/img`. Changing control bounds or resource paths changes the visual layout and should be treated separately from application logic.

## Datacenter backend configuration

`SimulationManager.initializeDatacenter()` currently loads `data/config/datacenter-test-complete-rezoned-edge-cases-custom-v2.json`. `JsonDatacenterConfigLoader` and factories from `cpz-sim-datacenter` turn this JSON into the `Datacenter`, workload factors, cooling configuration, and temperature options. The other `datacenter-*.json` files are alternative/test datasets present in the repository; they are not selected dynamically by a property in the current implementation.

Changes must preserve the assumptions encoded by the UI: rack/column codes, available rack rows/slots, cooling unit codes, and cooling zones must continue to match configured controls and aisle mapping.

## Hot-aisle mapping

`hot-aisle-mapping.json` is UI configuration loaded strictly by `HotAisleConfigurationLoader`. Unknown, missing, or null creator properties are rejected. Each entry provides:

- `code`: the operational server-group identifier, for example `HA02`;
- `displayName`: text displayed in the selected-aisle panel;
- `columns`: one wall column or the two columns sharing an aisle;
- `layout`: `WALL` for exactly one column or `SHARED` for exactly two.

Each column may belong to only one hot aisle. During initialization, the mapping is inverted into `SimulationContainer.hotAisleByColumn`; duplicate assignments fail fast. The same definitions create backend operational snapshot groups and drive selected-aisle cooling-zone aggregation.

The current room renderer and selection highlight mapping explicitly know `HA01` through `HA05`. Extending the JSON alone is therefore not sufficient to add a new rendered aisle.

## Images and fonts

`ResourceManager` loads JetBrains Mono from `data/font/JetBrainsMono.ttf` and installs it as Processing's current text font. Its license is stored at `data/font/OFL.txt`.

Static full-canvas assets include six `ui_background*.png` layers, the final `ui_overlay.png`, and `logo.png` for the window icon. Subdirectories `data/img/btn`, `data/img/ind`, and `data/img/tgl` contain control assets referenced by JSON.

Processing loads these resources at runtime rather than packaging them through Maven resource conventions, which is another reason to run from the repository root.

## Reference-template files

`template-sketch.json`, `custom-area-tooltip.json`, and `TemplateSketchConfig` support the intentionally preserved `TemplateSketch`. They demonstrate additional control types and tooltips but are not loaded by the active `Sketch` flow.
