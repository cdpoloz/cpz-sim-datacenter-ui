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

## Selected rack panel

`SelectedRackCoolingControls` resolves the selected `RackLocation` against
`SimulationThermalPipeline.rackCoolingSystem()`. That accessor returns the same
control object owned by `CoupledRoomTemperatureSystem`; there is no second unit
registry or UI thermal calculation. Any configured rack unit can use this panel,
without a new fixed toggle for every rack or a naming convention for its code.

| Display | Source and meaning |
| --- | --- |
| `ON/OFF > ROOM/EXTERNAL` | Live control state and immutable destination |
| `AIR` | Local air temperature from the last recorded rack thermal snapshot |
| `LAST Q` | Actual thermal removal rate at the end of that step, in kW |
| `ELEC` | Live enabled-state electrical power, in kW |
| `CAP` | Configured nominal thermal capacity, including when disabled |
| Fan | `tglSelectedRackCooling`, changes only the displayed rack's unit |
| `SERVER AVG` | Existing online server internal-temperature average |
| `IT POWER` | Existing server electrical power only |

Projection sets the toggle under a re-entry guard. Operator commands update the
unit and live readouts immediately, including while paused; they do not advance
time or replace historical snapshots. `AIR` and `LAST Q` stay at the last completed
step until simulation resumes. A rack without a unit shows `NO UNIT` and `--`
measurements and hides/disables the fan; room air is never substituted for local
rack air. A configured unit without a completed snapshot has pending measurements.

The fan sits in the lower part of the rack illustration as an equipment control;
its position does not allocate server slots. Rack and aisle color scales continue
to describe server temperatures, not local air.

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
