# Room-air reference configuration and comparisons

## Scope

The checked-in `data/config/datacenter.json` now explicitly enables the coupled
room-air model. This is a reproducible simulation reference, not a calibration
against a real building, equipment datasheets or measured temperature curves.
The backend still invents no room parameters when the block is absent.

| Parameter | Reference | Meaning |
| --- | ---: | --- |
| Initial room air | 24 °C | Independent of initial internal server temperature. |
| Effective room capacity | 20,000,000 J/°C (20 MJ/°C) | Assumed room air plus thermally participating non-server contents/envelope. |
| Environment temperature | 24 °C | Boundary reference; inactive when its transfer coefficient is zero. |
| Environment transfer | 0 W/°C | Isolated-room experiment; no unmeasured passive cooling is introduced. |
| Initial server temperature | 30 °C | Existing server configuration retained. |
| Supply temperature | 18 °C | Existing equipment configuration retained. |
| Initial cooling controls | All disabled | Existing on/off startup states retained. |

The effective room capacity excludes the installed servers: the coupled solver
already stores energy in those servers separately. It is not derived from the
legacy `effectiveZoneAirVolumeCubicMeters`: those virtual zone volumes are not a
survey of the actual room. The 20 MJ/°C value is an explicit intermediate point
for sensitivity experiments, not a parameter chosen to certify a target air range.

The default UI now shows numerical `ROOM AIR TEMP` and `AIR` history. The lower
`AVG SERVER TEMP`, rack values, server alerts and rack/aisle colors remain internal
server proxies. The UI starts with its usual variable noise-driven workload;
the fixed-load benchmark below will therefore not exactly match an interactive run.

The interactive dataset also includes a demonstration integrated cooling unit on
`C01-R01` (see [rack cooling](rack-cooling.md)). The benchmark runner explicitly
removes `rackCooling`, including its local air node, before constructing each
simulation. This preserves the room-only setup and the results below; disabling
a rack unit alone would retain its local air node and change that comparison.

## Reproducing the benchmark

Run the main class
`com.cpz.sim.datacenter.ui.example.RoomAirReferenceScenarios` from the project root
with the normal UI dependency classpath, for example using IntelliJ's Run action.
No Processing window is opened. Arguments are an optional config path and optional
number of simulated minutes:

```text
data/config/datacenter.json 21
data/config/datacenter.json 60
```

The runner prints CSV for four fresh cooling-control scenarios at each of
5, 20 and 50 MJ/°C. Only capacity and unit enabled states vary; the remainder
comes from the loaded configuration. Each run uses 67% constant utilization
on online servers (or equivalent power in power-driven mode), with no workload
factors or noise. This isolates cooling and inertia effects. Temperature-driven
air inference remains unsupported.

The reference dataset contains 843 installed servers, 803 online. IT power in this
benchmark is 221.643 kW. Electrical HVAC power is separate from actual thermal
removal, which varies with room temperature and is integrated in the ledger.

## Results at 20 MJ/°C

Java 26, one-minute external ticks, coupled internal substeps up to one second.
Values below are rounded from the runner's output.

| Cooling scenario | Air at 21 min (°C) | Server average at 21 min (°C) | Air at 60 min (°C) | Server average at 60 min (°C) | HVAC electrical power (kW) | PUE |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| None | 30.8324 | 44.8803 | 47.6353 | 63.2094 | 0.000 | 1.0000 |
| SUPPLY only | 29.9559 | 44.5271 | 43.6260 | 60.6026 | 133.333 | 1.6016 |
| EXHAUST only | 30.8324 | 44.8803 | 47.6353 | 63.2094 | 25.000 | 1.1128 |
| SUPPLY + EXHAUST | 24.6559 | 42.1762 | 27.5123 | 49.0615 | 158.333 | 1.7144 |

In this model EXHAUST assists SUPPLY return flow and is not outdoor ventilation.
Consequently extraction alone consumes electrical power but provides no separate
thermal sink; its air curve matches the uncooled case. Supply-only operation has
the configured high recirculation penalty. These are model assumptions, not claims
about every real ventilation installation. With both groups enabled, instantaneous
capacity and effective flow limit removal; cooling can also remove stored energy.

## Inertia sensitivity

Air temperatures at 21 simulated minutes:

| Capacity (MJ/°C) | None (°C) | SUPPLY only (°C) | EXHAUST only (°C) | Both (°C) |
| ---: | ---: | ---: | ---: | ---: |
| 5 | 41.6590 | 38.1546 | 41.6590 | 25.4967 |
| 20 | 30.8324 | 29.9559 | 30.8324 | 24.6559 |
| 50 | 27.0412 | 26.7184 | 27.0412 | 24.2570 |

A larger capacity slows warming; it does not provide cooling. The isolated,
uncooled room continues accumulating energy at sustained load. A high temperature
alone is not proof of an error, and no artificial temperature cap is applied.
The maximum absolute per-tick energy residual across the 21- and 60-minute
benchmarks was below 0.005 J, compared with approximately 13.3 MJ of IT input per
minute. This verifies numerical bookkeeping, not physical calibration.

## Calibrating a real installation

1. Define whether the room node represents all air, the cold aisle or another
   measured volume. The current node assumes the whole room is well mixed.
2. Obtain dimensions and free air volume. Air mass and specific heat provide
   an air-only capacity estimate; add non-server contents only with an explicit
   approximation of how much participates over the simulated time scale.
3. Obtain server, supply/return and room-air temperature measurements with known
   power and fan states, initial conditions and sampling intervals.
4. Estimate effective capacity and environmental heat transfer from complete
   transients and the energy ledger, rather than adjusting capacity to force
   a preferred final temperature.
5. Fit cooling/recirculation behavior against measured heat removal and validate
   on separate runs, including cooling loss and recovery. Revisit the EXHAUST
   boundary assumption if the installation actually exchanges outdoor air.

Until then, treat this configuration as an experimental reference. Removing the
top-level `roomAirTemperature` block restores the legacy thermal route and shows
`--` for room air. Remove that block when selecting `TEMPERATURE_DRIVEN`, whose
observed-temperature route remains authoritative without coupled air inference.
