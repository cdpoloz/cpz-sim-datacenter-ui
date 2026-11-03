# Dataset replay in Processing

`Launcher` selects the sketch before constructing it. An absent JVM property
`simulation.source` or the explicit value `DEMO` starts the original map. `DATASET`
starts `DatasetReplaySketch`, without map resources, controls or simulation managers.
Unknown source values fail startup with an exception; incomplete dataset properties
produce a visible `INVALID` screen. There is no fallback to the map.

All dataset settings are JVM properties, captured once at startup. They are not
read from `data/config.properties`. That existing application properties file must
still be present when launching from the UI repository root. Restarting the
application is required to change inputs or mode.

## Local installation and new workbook

Use JDK 26 and the adjacent local backend checkout. The backend's current replay
API and synthetic generator must already be present. Keep its Maven coordinates,
version and POM: Apache POI is resolved transitively through that installed POM.
No UI dependency or version changes are needed. From the UI repository root:

```bash
cd ../cpz-sim-datacenter
mvn --batch-mode --no-transfer-progress -DskipTests install
cd ../cpz-sim-datacenter-ui
mvn --batch-mode --no-transfer-progress test
mvn --batch-mode --no-transfer-progress dependency:build-classpath -Dmdep.outputFile=target/replay-classpath.txt
```

Generate a **new** workbook. The generator refuses to overwrite any existing path.
The official template and the backend JSON are read only. No external customer
files are required. These Bash commands create an isolated temporary directory;
keep the same shell for the launch commands below:

```bash
replay_dir=$(mktemp -d /tmp/pyxis-replay.XXXXXX)
replay_cp="target/classes:$(cat target/replay-classpath.txt)"
java -cp "$replay_cp" com.cpz.sim.datacenter.example.SyntheticDatasetGenerator \
  ../cpz-sim-datacenter/docs/templates/datacenter-customer-dataset-template.xlsx \
  "$replay_dir/pilot.xlsx"
```

The workbook contains fictional data: 20 racks, two columns, ten servers per rack,
and three instantaneous observations, 15 minutes apart. Only the known pilot
metadata (`datasetId=synthetic-20-rack-pilot`,
`sourceName=SyntheticDatasetGenerator v1`, and
`configurationId=synthetic-dataset-pilot`) enables the **Synthetic** label.
Other datasets are not automatically labeled synthetic.

## Launch commands

There is no Maven execution plugin. Run the actual `Launcher` with the resolved
runtime classpath, from the UI repository root. For utilization driven replay:

```bash
java -cp "$replay_cp" \
  -Dsimulation.source=DATASET \
  -Dsimulation.datacenter.config=../cpz-sim-datacenter/data/config/synthetic-dataset-pilot.json \
  -Dsimulation.dataset.path="$replay_dir/pilot.xlsx" \
  -Dsimulation.dataset.configurationId=synthetic-dataset-pilot \
  -Dsimulation.dataset.configurationVersion=1 \
  -Dsimulation.dataset.mode=UTILIZATION_DRIVEN \
  -Dsimulation.dataset.startUtc=2026-01-01T00:00:00Z \
  -Dsimulation.dataset.steps=3 \
  -Dsimulation.dataset.step=PT15M \
  -Dsimulation.dataset.maximumHold=PT15M \
  com.cpz.sim.datacenter.ui.main.Launcher
```

For power driven replay:

```bash
java -cp "$replay_cp" \
  -Dsimulation.source=DATASET \
  -Dsimulation.datacenter.config=../cpz-sim-datacenter/data/config/synthetic-dataset-pilot.json \
  -Dsimulation.dataset.path="$replay_dir/pilot.xlsx" \
  -Dsimulation.dataset.configurationId=synthetic-dataset-pilot \
  -Dsimulation.dataset.configurationVersion=1 \
  -Dsimulation.dataset.mode=POWER_DRIVEN \
  -Dsimulation.dataset.startUtc=2026-01-01T00:00:00Z \
  -Dsimulation.dataset.steps=3 \
  -Dsimulation.dataset.step=PT15M \
  -Dsimulation.dataset.maximumHold=PT15M \
  com.cpz.sim.datacenter.ui.main.Launcher
```

Default demo and explicit demo (neither requires dataset settings):

```bash
java -cp "$replay_cp" com.cpz.sim.datacenter.ui.main.Launcher
java -cp "$replay_cp" -Dsimulation.source=DEMO com.cpz.sim.datacenter.ui.main.Launcher
```

All nine dataset properties are required in dataset mode. Values are case
sensitive; blank or padded values are rejected. UTC text must end in `Z`.
`step` and `maximumHold` use ISO 8601 durations; `steps` is an integer.
The backend validates plan bounds (1–1000 points), positive durations, supported
modes, complete coverage, binding and the supported electrical scope.

## Presentation and controls

The view uses a logical inventory layout, sorted by column and row, without
physical dimensions. Rack and server buttons retain `RackLocation` and
`ServerLocation` objects; labels are never parsed into identities. Room, column,
rack, slot and the original source entity ID appear in the comparison panel.
The pilot displays all 20 rack cards and ten servers in the selected rack.
Larger inventories have previous/next controls (20 racks or ten servers per page).

Before Play, comparisons and clocks are **Pending**. An absent optional source
metric is **Not available**. Selecting another rack/server reads the same last
frame, without advancing the session. Values come directly from backend results;
the UI only formats fractions as percentages and powers as watts. Original
sample UTC, age at queried UTC, quality and provenance remain distinct from runtime
results. Cooling is not configured in this supported electrical replay scope.
No temperature, energy, health, PUE or new physical aggregates are shown.

Play and Pause control wall pulses. Slower/Faster select 0.25–8 **steps/s**.
Every eligible pulse invokes at most one advance; late pulses never skip points
or catch up with multiple advances. Speed changes reset the next wall deadline
without changing the immutable backend plan, results or query timestamps.
Observation cadence is dataset metadata; query/integration step is the plan's
`step`; presentation speed is a separate wall frequency.

The backend is the sole simulation clock authority. For frame number `k`:

- Replay UTC: `startUtc + (k - 1) * step`.
- Replay offset: `(k - 1) * step`.
- Engine elapsed: `k * step`.

The UI reads these three fields from the frame. It does not interpret tick indices
or reconstruct the runtime clock. The first frame at midnight therefore has
`Replay offset=PT0S` and `Engine elapsed=PT15M`.

First server reference values (utilization / electrical power):

| UTC | Original observation | Utilization driven result | Power driven result |
| --- | --- | --- | --- |
| 00:00 | 20% / 150 W | 20% / 140 W | 25% / 150 W |
| 00:15 | 40% / 190 W | 40% / 180 W | 45% / 190 W |
| 00:30 | 60% / 230 W | 60% / 220 W | 65% / 230 W |

## Lifecycle and ownership

`DatasetReplayController` has no Processing dependency, timers or worker threads.
The Processing animation thread synchronously parses, loads and fully validates
the session in `setup`; the same owner thread handles input, advances and restart.
The `LOADING` state is transient during this synchronous preparation, before
controls are rendered. Mutations enforce owner-thread access.

| Presentation state | Meaning |
| --- | --- |
| LOADING | Preparing and validating; controls unavailable. |
| INVALID | Property/load/preparation failure; Play and Restart blocked. |
| READY | Backend PREPARED, zero progress, no frame. |
| RUNNING | Wall pulses enabled; session PREPARED or ACTIVE. |
| PAUSED | No advances; Play schedules the next point. |
| COMPLETED | Last planned frame accepted; playback stops immediately. |
| ERROR | Advance/restart failed; Play blocked until successful Restart. |

An error keeps its category, cause chain and all structured diagnostics. Use the
mouse wheel or Previous/Next buttons to navigate wrapped error text, including severity, sheet, Excel row,
field and message. Optional failure server/query UTC are labeled as failure
context. If an advance fails, a retained comparison is labeled **PREVIOUS
SUCCESSFUL FRAME / before failure**. Its clocks remain those of that frame,
even if the failed engine advanced internally. No additional point is requested
after completion.

Restart first stops playback and invalidates the wall deadline, then clears
controller history/error and calls `session.restart()`. It returns to READY,
without rereading files or automatically playing. An old pulse cannot run a step
while READY. The controller's history is bounded by the backend plan's maximum
of 1000 frames. No second engine, workload/noise source or demo main is used.

## Automated validation

```bash
java -version
mvn --batch-mode --no-transfer-progress test
```

Tests use the adjacent backend checkout's official template and JSON and generate
new workbooks in JUnit temporary directories. If the checkout is elsewhere:

```bash
mvn --batch-mode --no-transfer-progress -Dreplay.backend=/absolute/path/to/cpz-sim-datacenter test
```

Tests cover startup/property validation, both real replay modes and all reference
values, zero-step preparation, full inventory selection, unchanged last-frame
selection, pause/resume/completion, restart during playback, restart after input
files are removed, speed/cadence independence, structured diagnostics and injected
execution failure with an advanced engine clock. The template, JSON and generated
workbook are checked byte for byte after the controller tests. The full backend
suite need not be rerun when only installing its existing sources.

## Implementation validation record (2026-11-03)

Validated with Oracle JDK 26.0.1 and Maven 3.9.9 on Linux/X11:

- Backend `mvn --batch-mode --no-transfer-progress -DskipTests install`: successful;
  backend tests were skipped and backend source changes were preserved.
- UI `mvn --batch-mode --no-transfer-progress test`: **34 tests, zero failures,
  errors or skips** (12 dataset test invocations and 22 existing tests). Maven
  compiled 69 production sources and four test sources with release 26.
- `dependency:build-classpath -Dmdep.outputFile=target/replay-classpath.txt`:
  successful; generator and both Processing modes launched using that classpath.
- A fresh workbook was generated at `/tmp/pyxis-ui-pilot-20261103.xlsx`.
  SHA-256 checks confirmed byte preservation of that workbook, the backend pilot
  JSON and the official template after all replay runs.
- Processing/Robot checks: 20 rack cards, ten server buttons per selected rack,
  pending READY values, correct first/last results in both modes, server selection
  across columns without advancing, pause, restart to READY and completion at
  exactly three points. Captures before/after waiting while paused were byte
  identical in each mode.
- A separate newly created invalid workbook produced 600 structured diagnostics.
  INVALID blocked Play and diagnostics remained readable with page navigation.
- The actual Launcher without `simulation.source` opened the original 96-rack
  map. This was an opening/rendering smoke check; exhaustive interaction testing
  of existing map controls was not performed.
- Advance failure with an internally advanced engine clock was tested through
  fault injection in the automated suite; its ERROR presentation was not
  exercised visually. Inventories larger than the pilot were not checked visually.

Screenshots are local generated artifacts under `target/replay-review/` (not
versioned): `UTILIZATION_DRIVEN-{ready,paused-first,paused-unchanged,selection,restart,completed}.png`,
`POWER_DRIVEN-{ready,paused-first,paused-unchanged,selection,restart,completed}.png`,
`diagnostics-top.png` and `diagnostics-scroll.png`.

Files to attach for source review, relative to the UI checkout:

```text
README.md
docs/dataset-replay.md
src/main/java/com/cpz/sim/datacenter/ui/main/Launcher.java
src/main/java/com/cpz/sim/datacenter/ui/main/DatasetReplaySketch.java
src/main/java/com/cpz/sim/datacenter/ui/dataset/DatasetReplayOptions.java
src/main/java/com/cpz/sim/datacenter/ui/dataset/DatasetReplayController.java
src/test/java/com/cpz/sim/datacenter/ui/dataset/DatasetReplayOptionsTest.java
src/test/java/com/cpz/sim/datacenter/ui/dataset/DatasetReplayControllerTest.java
```

For execution evidence, also attach the two `TEST-com.cpz.sim.datacenter.ui.dataset.*.xml`
reports under `target/surefire-reports/`, the screenshots listed above, the newly
generated workbook, the pilot JSON and `/tmp/pyxis-input-hashes.json`. The prepared
`/tmp/pyxis-replay-review-20261103.tar.gz` bundles these exact files; its full entry
list is `target/replay-review/review-manifest.txt`. It does not include generated
inputs in the Git checkout.
