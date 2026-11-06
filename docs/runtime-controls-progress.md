# Runtime controls: progress

1. **Startup and visual structure:** implemented and visually accepted by Carlos.
2. **Layout and room map generation:** implemented and visually accepted by Carlos.
3. **Room simulation and state:** implemented and visually accepted by Carlos.
4. **Selected aisle:** implemented and visually accepted by Carlos.
5. **Selected rack:** implemented and visually accepted by Carlos, with minor visual follow-ups.
6. **Cooling and functional equivalence**, including summary, trends and alerts: in progress (6a accepted; 6b partial, visual acceptance pending).
7. **Adaptation to other layouts:** pending.

## Hito 1

`Launcher` selects `RUNTIME` through the existing `simulation.source` JVM
property; `DEMO` remains the default and `DATASET` retains its replay path.
At milestone 1, `RuntimeSketch` owned an independent Processing lifecycle. `RuntimeLayout` exposes
the shared area, title and content bounds; `RuntimeView` draws the shell and
constructs public library `Label` controls. The eight areas contain explicit
pending markers. At that point no layout, assets, simulation, selection or graph data was
loaded. The 8 × 12 × 12 example is not a design constant.

Validated with Oracle JDK 26.0.1 and `mvn test`: 36 tests passed. The Maven
classpath was generated with `mvn --batch-mode --no-transfer-progress
dependency:build-classpath -Dmdep.outputFile=target/classpath.txt`. Runtime
opened in the graphical session. A screenshot was partly obscured by another
window, so visual acceptance was then pending; the window manager also
resized the requested 1920 × 993 surface to 1920 × 1014. The classic mode
opened and entered setup without a reported exception before the eight-second
timeout; its full visual and interaction checks remain pending.

Historical manual acceptance plan: launch using the README command, bring the full window to
the foreground, check the eight titles and pending markers for clipping and
overlap at the configured resolution, then launch `DEMO` and verify its usual
selection and simulation controls. Replay instructions remain in
`dataset-replay.md`.

## Resources and dependencies

The runtime shell uses JetBrains Mono and the existing palette, but no classic
graphical JSON or image layers. The seven `ui_background*.png` layers provide
the classic background, room, aisle, rack, header, footer and overview; the
`ui_overlay.png` foreground provides its static overlay. Their decorative roles
are replaced by the shell's Processing frames, so new bitmaps are unnecessary
for this milestone. Classic button, toggle and indicator SVG/PNG assets provide
interactive and state visuals and are also unused here; assess reuse when those
features are implemented. Per-row `btnSelectedRackLeft/Right` files are not
needed for the new shell. `data/img/common/circle.svg` and `square.svg` exist;
they are reserved for later use and are unnecessary for these frames. All
resources remain available to the classic mode.

The consumed controls dependency is 0.9.12. Its local documentation and the
0.9.12 public `Label` API were inspected; no library blocker was identified.
No dependency or version was changed.

Carlos has accepted milestones 1 and 2, including the complete footer, room map and selection.

## Hito 2

Runtime loads the backend definition once, using `simulation.datacenter.config`
or `data/config/datacenter.json`. It retains the layout's hot-aisle associations
and each rack's resolved slots, without starting the simulation or using the
classic mapping. Columns and row values use lexical order; equal row values
align across columns and missing positions remain empty. Duplicate locations or
positions fail with a diagnostic. Selection uses the backend `RackLocation`
(column and rack code), and public Button and Label controls are created once;
resize updates their bounds without reloading or registering listeners again.

The example contains eight columns and 96 racks. Automated tests cover the
default and alternative paths, different sized columns, repeated rack codes,
pointer selection before and after resize, and geometry at multiple aspect
ratios. `mvn test` with Oracle JDK 27 targeting release 26 passed 41 tests.
The existing DEMO and DATASET tests also passed. IntelliJ MCP reported no
errors in the changed runtime production files. These checks do not establish
visual acceptance at that time. RUNTIME was started for 15 seconds and reported an effective
1920 × 1011 surface, with no exception in its console; no screenshot or visual
interaction was observed. Carlos subsequently accepted the map and selection.
Extreme density, scrolling and zoom remain outside this milestone.

Runtime uses the existing font and Processing decoration, with no bitmap or
SVG assets. The classic backgrounds, overlay and row-specific selection SVGs
remain for the classic mode. `circle.svg` and `square.svg` add no value to the
rectangular map controls, and no replacement resources are needed.

## Footer and relative geometry follow-up

The original geometry placed the footer inside its calculated canvas with a
bottom margin, but stored pixel bounds and did not update them after a resize.
It is therefore not established that the reported clipping originated in the
drawing. In the observed desktop session Processing reports the requested
1920 × 993 canvas was changed by the window manager to 1920 × 1014; the
decorated window measured 1920 × 1051 at y=29 on a 1920 × 1080 screen.

The runtime layout now stores public `ControlBounds.relative` values: x is
relative to surface width, while y and both dimensions use surface height.
The view resolves these bounds for decoration, passes the same measures to
labels, and recalculates them when the effective surface size changes. Startup
estimates the available screen area and decoration insets before creating the
runtime surface, scaling the configured size only if needed. This estimate is
not sufficient on the observed window manager: it still reports the resize
above. The footer was subsequently accepted by Carlos.

`mvn test` on JDK 26.0.1 passed 38 tests, including bounds and overlap tests
at three aspect ratios and relative-width resolution. Runtime opened after the
change; visual acceptance was then pending. Further work must account for actual window-manager decorations and
maximization without resizing during Processing setup, which caused a reentrant
draw failure in an attempted approach. No library patch was identified.

## Hito 3

The active backend definition is loaded once and shared by the room map and
runtime session. The session uses the same input factories, thermal pipeline,
health and energy systems and snapshot recorder as the classic composition,
without the classic column mapping or rack-code index. Snapshots are projected
by `RackLocation`, using average online server temperature and the existing
temperature range. Empty, offline and unavailable states have distinct fills;
selection changes the border and preserves the fill. Cooling participates in
the backend; its controls remain for milestone 6.

As in the classic mode, the session performs one initial step and displays its
actual elapsed time; it then starts paused. Each timer pulse advances one
simulated minute and captures the resulting snapshot. Play, pause and speed
changes restart the timer period, so paused time and delayed frames do not
cause catch-up steps. Speed factors and health thresholds come from the
existing properties. The UI advances on the Processing thread.

Oracle JDK 27 targeting release 26: `mvn test` passed 45 tests, including
controlled timer tests, snapshot projection and all three supported input modes
with backend-compatible configurations. The existing coupled and legacy
thermal pipeline tests also pass. IntelliJ MCP reported no errors in the
changed runtime production files. Carlos subsequently accepted milestone 3.
Runtime started and stayed open until the 15-second test timeout;
classic DEMO completed setup before its eight-second timeout. Neither window
nor interaction was visually inspected; both reported an effective 1920 × 1011
surface. No new graphics assets were required.

## Hito 4

The selector traverses `layout.hotAisles` and their columns in declared order;
row values retain the room map's lexical order and missing positions remain
empty. Selecting a room rack selects its associated aisle, or explicitly shows
that it has none. Changing aisle retains only a rack in that aisle. Rack buttons
in both panels share `RackLocation` and selection; a separate asterisk marks
the rack containing the maximum-temperature server without replacing its border.

Runtime builds backend server groups once using actual server locations in the
declared columns. A single operational snapshot supplies both views. Average
temperature and IT utilization cover online servers; the maximum covers all
installed servers, including offline ones, and uses the backend's location and
tie-breaking. Empty, offline and missing values are displayed explicitly.
Cooling continues in the simulation; its metrics and controls remain for hito 6.

Oracle JDK 27 targeting release 26: `mvn test` passed 52 tests, including
selection, repeated rack codes, row gaps, no aisles, backend group membership,
offline maximum, resize geometry and pointer dispatch. IntelliJ MCP reported
no errors in the modified runtime files. RUNTIME started until a 15-second
timeout; the classic mode completed setup before an eight-second timeout.
Neither window was observed in that run. Carlos subsequently accepted hito 4,
including selector, metrics and synchronized selection.

Runtime continues using the existing font and Processing controls. Classic
backgrounds, overlay and row-specific SVGs remain available to the classic
mode; circle.svg and square.svg are unnecessary for the text marker, and no
replacement asset is required.

## Hito 5

Rack detail follows the shared `RackLocation` selection. Installation and role
are indexed once from the existing backend model; energy, temperature and
health are joined by full `ServerLocation` from the same step snapshot. Slot
rows retain the order returned by `RackSlotResolver`, including empty slots;
selection changes replace the old labels, while a tick only refreshes values.
Missing or non-finite data is shown as `--` and flagged, and offline server
values remain those supplied by the backend. Summary averages and rack power
come directly from `RackOperationalSnapshot`. U and T are backend alert reasons.

The provenance legend describes simulated input and mode-specific inferred or
assigned values; thermal-model results are labelled as modelled, not measured.
It follows the available configuration and input-pipeline semantics rather
than claiming independent per-value provenance metadata.

Oracle JDK 27 with release 26: `mvn test` passed 57 tests, including snapshot
joins with reordered lists, repeated rack/slot codes, role and workload
differences, alerts, offline/missing data, and a backend-validated layout with
three ordered nonconsecutive slots. Tests cover replacing and clearing rows and
geometry at 1920 × 1011 and 800 × 800. IntelliJ MCP reported no errors in the
affected runtime files. RUNTIME and DEMO started until their respective timeout;
neither window was observed in that run. Carlos subsequently accepted hito 5.

### Slot indicator readability adjustment

Carlos found the row layout satisfactory but requested clearer text. Slot text
now retains the existing light color, and a separate public `Indicator` shows
the temperature scale. Empty and offline indicators use the existing room-state
colors; a yellow border distinguishes unavailable temperature from any point
on the scale. Indicators are reused between snapshots and their relative bounds
are recalculated on resize. The 0.9.12 indicator's default circular renderer
provides a visible border without loading `square.svg`; no new asset is needed.

With Oracle JDK 27 and release 26, `mvn test` passed 57 tests after the
adjustment, including indicator colors, stable text color, reuse, cleanup and
resize geometry. IntelliJ MCP reported no errors in the affected files. The
adjusted view was subsequently accepted by Carlos. Slight vertical alignment of
slot indicators and legend size/readability remain minor visual follow-ups.

Runtime uses the existing font and public controls; classic backgrounds,
overlays and row-specific SVGs remain for the classic mode. Circle and square
assets do not add value to this text table, so no replacement is needed.
Hito 6 continues with cooling controls, global summary, trends and alerts.

## Hito 6a — Cooling controls

Sala offers a paginated cooling view in declared unit order, retaining the map
and selection. Unit buttons use exact backend codes and initial/live states.
Separate SUPPLY and EXHAUST masters mean "at least one enabled": clicking an
active master disables the group; clicking an inactive master enables it. A
group with no units is disabled. The rack's **Frío** view addresses the real
integrated unit by full `RackLocation` when the current thermal route exposes
one, and explicitly distinguishes no selection, no unit and unavailable route.

The same `CoolingSystem` and thermal pipeline used by the engine are retained.
Changing a control updates the live enabled state and electrical power without
stepping or replacing the recorded snapshot. Rack air temperature and actual
heat removal are labelled as the last tick; the next normal step incorporates
the new state. No additional thermal owner or UI physics is introduced.

Oracle JDK 27 with release 26: `mvn test` passed 62 tests. Tests cover mixed
states and nonstandard codes, masters, missing unit types, coupled and legacy
routes, full rack identity, paused snapshots, subsequent steps, pagination,
hidden controls and bounds after resize. IntelliJ MCP found no errors in the
affected runtime files. RUNTIME and DEMO started until their respective timeout;
neither window was visually observed in that run. Carlos subsequently accepted 6a.

Runtime uses existing font and public controls, with no new assets. Classic
backgrounds, overlays and SVGs remain unchanged; circle/square/ventilation
assets were not needed. Hito 6 remains incomplete: next are aisle cooling
aggregates and global summary, followed by trends/alerts and equivalence review.

## Hito 6b — Aisle cooling and global summary

The selected aisle can switch between operational and cooling metrics without
changing selection or the simulation. Zones are selected by actual server
locations in its declared columns, deduplicated and passed to backend
`CoolingSnapshot.aggregateZones`. A zone crossing the selected columns is
included whole and identified as shared. Coverage, air ΔT and recirculation
come from that group; the backend defines coverage as 100% for zero generated
heat. No UI-side physical aggregation is used.

The installed `cpz-sim-datacenter:0.1.0-alpha.1` group snapshot exposes no
grouped SUPPLY/EXHAUST airflow. Both fields remain `-- m³/s` and 6b remains
partial. The separate ignored prompt `prompts/pyxis-backend-agregados-refrigeracion.md`
documents the required backend API and tests. The UI does not sum zone flows.

The global summary shows room air separately from the online-server mean,
the hottest rack by its online average, IT online utilization, IT power,
electrical cooling power, facility power and PUE. Cooling and facility power
are backend fields that include room and integrated-rack cooling when present;
they are not recomputed in the UI. All power values describe the last tick,
including during pause after a cooling-control change. Absent air, online data
and unavailable PUE display `--`.

Oracle JDK 27 with release 26: `mvn test` passed 67 tests. Tests compare the
backend group and snapshot fields, shared/deduplicated zones, missing data,
zero IT, coupled and legacy pause behavior, tab input and resize bounds.
IntelliJ MCP reported no errors in affected runtime files. RUNTIME and DEMO
started until their respective timeout; neither window was visually observed.
Carlos's visual acceptance of the available 6b metrics remains pending. No new
assets were used. Hito 6 remains open for blocked airflow, trends, alerts and
equivalence review.

## Pending visual and technical items

| ID | Pending | Follow-up |
| --- | --- | --- |
| V01 | Align the left edge of Alertas with Rack seleccionado. | Alertas panel pass. |
| V02 | Align slot indicators vertically with their text. | Rack detail polish with V03. |
| V03 | Improve rack legend readability without clipping rows. | Rack detail polish with V02. |
| V04 | Evaluate a graphical relationship between units and room. | Influence is not physical location; explicit layout data is needed for location. The 6a list is accepted for development. |
| T01 | Requested 1920 × 993 versus observed 1920 × 1014/1011. | Initial clipping cause unconfirmed; footer accepted and nonblocking. Revisit if clipping returns or in hito 7. |
