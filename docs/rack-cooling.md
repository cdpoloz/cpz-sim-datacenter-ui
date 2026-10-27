# Integrated rack cooling

## Example and configuration

The default `data/config/datacenter.json` now includes a top-level `rackCooling`
block with `RACK-COOLING-C01-R01`, linked to `C01-R01` by `column` and `rackCode`.
The existing `layout.racks` entry and all server slots are retained. This thermal
attachment does not reserve rack units or assert a mounting position or footprint.

| Parameter | Demonstration value |
| --- | ---: |
| Initial local air | 24 °C |
| Effective local air capacity | 10,000 J/°C |
| Room exchange conductance | 50 W/°C |
| Airflow | 0.3 m³/s |
| Nominal thermal capacity | 4,000 W |
| Supply air | 18 °C |
| Electrical power when enabled | 1,000 W |
| Heat rejection destination | `ROOM` |
| Initially enabled | `true` |
| Air density / specific heat | 1.204 kg/m³ / 1,005 J/(kg·K) |

These values exercise the feature and have not been calibrated. `C01-R02` has no
integrated unit and can be selected to inspect the absent-unit state; its server
population/load is not guaranteed to match `C01-R01`.

Change `heatRejectionDestination` to `EXTERNAL` in the JSON and restart to send
rejected heat outside the modeled room. `ROOM` returns removed heat plus the
unit's electrical dissipation to the room; `EXTERNAL` exports both. Destination
and physical ratings are immutable configuration, not runtime UI settings.

The backend requires the coupled `roomAirTemperature` model and utilization or
power input. Removing `rackCooling` retains supported room-only/legacy behavior;
removing only `roomAirTemperature` while retaining rack cooling is invalid.

## Selected rack tooltip and fan

`SelectedRackCoolingControls` resolves the selected `RackLocation` against
`SimulationThermalPipeline.rackCoolingSystem()`. That accessor returns the same
control object owned by `CoupledRoomTemperatureSystem`; there is no second unit
registry or UI thermal calculation. Any configured rack unit can use this panel,
without a new fixed toggle for every rack or a naming convention for its code.

Hover over the rack illustration or its fan to display the same tooltip. The
illustration target uses canvas-relative bounds and follows resizing. The fan
remains clickable; the tooltip is informational and does not consume pointer
input. Both targets share one tooltip instance, styled in `toggles.json` with
JetBrains Mono, a dark background and cyan border. The header and equipment-code
lines are omitted, leaving six data fields. Font sizing is configured through
`ui.rack-cooling.tooltip.text-size=0.00906344410876` in `config.properties`.
As with label sizing, this factor multiplies canvas height (9 px at 993 px).
The tooltip library takes absolute pixel sizes, so the UI resolves the factor
before each overlay refresh, including while paused and on resize. The JSON
style's 9 px value supplies the initial/default-window font size.

| Tooltip field | Source and meaning |
| --- | --- |
| State | Live enabled state; this is not an automatic temperature controller |
| Heat rejection | Immutable `ROOM` or `EXTERNAL` destination |
| Air (last step) | Local air temperature from the latest rack capture |
| Cooling (last step) | Actual removal rate at the end of that step, in kW |
| Electrical power | Live enabled-state electrical power, in kW |
| Rated capacity | Configured nominal thermal capacity, including when disabled |

The six `lblRackCooling...` controls were removed from `labels.json`. The server
average and IT power labels remain separate server-only metrics. Tooltip sizing
reserves room for every line because the library defaults to a single-line-height
box. Active overlays render after the full scene and static foreground.

Projection sets the toggle under a re-entry guard. Operator commands update the
unit, state and electrical-power tooltip fields immediately, including while
paused; they do not advance time or replace historical snapshots. Air and actual
cooling stay at the last completed step until simulation resumes. Before the
first capture, these two values read `--`. An uncooled rack has no cooling tooltip
and hides/disables the fan; room air is never substituted for local rack air.

`Sketch` registers a `TooltipOverlayController` and passive `TooltipInputLayer`
using the existing infrastructure. Refreshing after panel updates keeps a visible
tooltip current even when the mouse does not move, clears it when selection moves
to an uncooled rack, and handles changed canvas dimensions. Disposal unregisters
the tooltip. Hovering does not change equipment state.

The fan sits in the lower part of the rack illustration as an equipment control;
its position does not allocate server slots. Rack and aisle color scales continue
to describe server temperatures, not local air.

## Meaning of thermal and electrical readouts

`Air (last step)` is the local well-mixed rack air at the end of the last completed
thermal tick, not internal server temperature, room air, or the 18 °C supply setting.
`Cooling (last step)` is the end-of-tick heat removal rate (kW thermal), not an
average over the tick, cumulative energy, or electrical consumption. The model
limits it by nominal capacity and airflow times air density, specific heat and
the positive local-air/supply-temperature difference.

The enabled-state command is On/Off. Enabled electricity stays at the configured
rated electrical power (1 kW in the UI example); disabled electricity is zero.
The 4 kW capacity is a thermal upper bound. Actual heat removal below that bound
does not mean that a compressor-speed control, adjustable load or variable
power/COP model is implemented.

The backend PUE includes rack electrical power along with IT and room cooling:
`(IT + room cooling electricity + rack cooling electricity) / IT`. The heat
rejection destination does not exempt electrical power from this sum. Readouts
of PUE and facility load represent the completed snapshot; an operator command
propagates to those totals on the next simulation tick. With two PUE decimals,
a 1 kW contribution against 230 kW IT alone gives about 1.00435, displayed as 1.00.

## Electrical totals and history

The existing history recorder forwards the backend rack thermal capture and
facility energy ledger into each operational snapshot. Global cooling load,
facility power, PUE, and their histories use those combined backend totals.
The HVAC utilization percentage adds reported room-zone usage and actual rack
removal, divided by their combined available thermal capacity.
Server energy remains IT-only. The selected-aisle supply/exhaust panel continues
to describe room equipment; integrated units are shown in their rack panel.

Resetting the engine restores initial unit state and clears thermal captures and
cumulative cooling energy. Clear the recorder and refresh snapshots before replay,
as with the existing room-air integration. The headless `RoomAirReferenceScenarios`
runner deliberately excludes rack cooling and local rack nodes to preserve its
published room-only comparison.

## Verification

`mvn clean test` checks legacy and room-only pipelines, both rack rejection
destinations, listener re-entry, paused commands, absent-unit selection, actual
versus nominal cooling, facility power, immutable history and deterministic reset.
The configured labels and SVG toggle were also loaded and rendered offscreen
using Processing's Java2D renderer at the default 1920 × 993 layout. This verifies
layout and asset loading; it does not exercise an interactive P2D desktop session.

## Complete scenario files

[Scenario generation and verification](rack-cooling-scenarios.md) documents the
standalone generator, six full-layout JSON variants, source preservation and a
manual P2D checklist. Room control children/masters now initialize from backend
states, including examples that begin with room HVAC enabled.

Tooltip verification also checks stationary-pointer refresh, removal/restoration
on selection changes, canvas resizing, fan hover, passive pointer handling and
overlay disposal. The multiline tooltip was rendered offscreen using the actual
JSON font/style, selected-rack server indicators and static foreground.
