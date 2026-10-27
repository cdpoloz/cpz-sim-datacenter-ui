# Reproducible rack-cooling UI scenarios

The default UI layout includes one integrated unit on `C01-R01`, rated at 4 kW
thermal and 1 kW electrical. The main JSON remains the startup default and is not
modified by the generator below. These are demonstration parameters, not measured
performance.

## Build and generate complete configurations

Install the matching local backend first (`mvn clean install` in
`cpz-sim-datacenter`), and make the pinned foundation/runtime dependencies
available. Use the Java release in this repository's `pom.xml` (Java 26 at this
revision). From the UI repository root:

```bash
mvn package
mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:$(cat target/classpath.txt)" \
  com.cpz.sim.datacenter.ui.example.RackCoolingExampleConfigurations
```

The generator writes full JSON files into `target/rack-cooling-scenarios/`.
Optional arguments select a source JSON and output directory:

```bash
java -cp "target/classes:$(cat target/classpath.txt)" \
  com.cpz.sim.datacenter.ui.example.RackCoolingExampleConfigurations \
  data/config/datacenter.json target/rack-cooling-scenarios
```

The source must contain valid room and rack cooling templates. Layout, servers,
models, temperatures and all other settings are preserved. Only the optional rack
block, rejection destination and unit enabled flags vary. Existing generated
files in the chosen directory are replaced; replacing the source itself is
rejected before generation. `mvn clean` removes the default output directory, so
regenerate after cleaning the build.

| File | Rack cooling | Rejection | Room SUPPLY/EXHAUST |
| --- | --- | --- | --- |
| `no_unit.json` | Absent, no local rack air node | — | Off |
| `disabled.json` | Installed, off; local air node remains | None while off | Off |
| `room.json` | On | Room | Off |
| `external.json` | On | External | Off |
| `room_with_hvac.json` | On | Room | Both on |
| `external_with_hvac.json` | On | External | Both on |

All configured rack units are affected when a custom template contains several.
Both room control masters and their children initialize from backend equipment
state, including mixed enabled states, rather than control-JSON default values.

## Launch one case

For example, start the EXTERNAL case in a graphical session:

```bash
java -Dsimulation.datacenter.config=target/rack-cooling-scenarios/external.json \
  -cp "target/classes:$(cat target/classpath.txt)" \
  com.cpz.sim.datacenter.ui.main.Launcher
```

Restart with another file for a fresh simulation. The UI retains its variable
workload, and `C01-R02` has a different installed population/load from `C01-R01`.
This visual exercise does not isolate cooling causally. For a controlled matched
comparison, use the backend's small JSON, standalone runner and
[recorded six-scenario results](https://github.com/cdpoloz/cpz-sim-datacenter/blob/master/docs/rack-cooling-examples.md).
That small backend JSON does not match this UI's fixed 8-column/12-row controls.

## Manual desktop verification

The automated tests load and advance all six generated files through the real
`SimulationThermalPipeline`, check equipment state, facility totals and the energy
ledger, and project room controls with listeners attached. The rack-control tests
also cover absent units, paused commands, independent units, immutable history
and reset. Stage 7 rendered the configured controls offscreen with Java2D.

An interactive Processing P2D run is still required on a graphical machine:

1. Open `room.json` and inspect initial selection `C01-R01`. Its fan is on,
   hover over the rack drawing or its fan. The tooltip shows ROOM rejection,
   1.00 kW electrical power and 4.0 kW rated capacity. Actual cooling varies and
   must not be interpreted as electrical power.
2. Pause. Switch the rack fan off. State and electrical power change immediately; air and actual cooling keep
   the last completed measurement. Resume for one tick and check
   zero rack heat removal and zero rack electrical contribution in that capture.
3. Select `C01-R02`. The tooltip disappears, there is no fan control and no local air measurement. Return to `C01-R01`; its previous control state is retained.
4. Restart with `external.json` and verify the EXTERNAL destination. Compare room
   behavior over identical initial conditions/schedules, allowing for the UI's
   variable workload; the controlled backend CSV provides numerical reference.
5. Open each `_with_hvac` case and verify room fan children and masters start on.
   Toggle a room group and the rack separately; one must not change the other's
   enabled state. Total electrical load and PUE update on the next completed tick.
6. Open `disabled.json` and `no_unit.json`. The first retains local rack air and
   an off fan; the second has neither. Server temperatures need not follow the
   same trajectory because their thermal topology differs.

Record the selected file, simulated time, control schedule and screenshot for
any discrepancy. No interactive P2D session was claimed by the automated check.
Real measurements and physical calibration remain pending.
