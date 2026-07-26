# World Time And Business Calendar

Status: implemented foundation in IM-021

ButcherCraft separates three time domains:

- `gameTime` is Minecraft's server tick counter. It advances once per server
  tick and continues to drive block ticks, entity AI, redstone, Scheduler
  invocation, Execution attempts, Production progress, workstation progress,
  and GameTest timing.
- `dayTime` is Minecraft's visible day/night clock. It controls the sun, moon,
  daylight, night, villager schedules, daylight-sensitive behavior, and
  Minecraft day count. IM-021 scales only this value.
- Business Calendar is a read-only ButcherCraft snapshot derived from the
  scaled Overworld `dayTime`. It does not own an independent clock and cannot
  drift away from the visible world.

## Configuration

The canonical server-authoritative configuration lives in the existing
ButcherCraft common config:

```toml
[world_time]
enabled = true
day_length_minutes = 60
```

`day_length_minutes` is the real-time duration of one full 24,000-unit
Minecraft day at 20 server ticks per second. The valid schema-1 range is
20 to 1,440 minutes.

Examples:

| Configured minutes | Meaning |
| ---: | --- |
| 20 | Vanilla-equivalent day length |
| 30 | One and one-half vanilla day length |
| 60 | Default ButcherCraft day length, one-third normal day-time speed |
| 90 | Four and one-half vanilla day length |
| 120 | Six vanilla day lengths |

Invalid zero, negative, or over-range values are rejected by the config spec.

## Advancement Model

Vanilla Minecraft treats one day as 24,000 day-time units. ButcherCraft derives
an exact rational rate from the configured day length:

```text
day-time units per server tick = 24000 / configured_day_server_ticks
```

For the default 60-minute day:

```text
configured_day_server_ticks = 60 * 60 * 20 = 72000
rate = 24000 / 72000 = 1/3
```

The authoritative ButcherCraft state stores the fractional accumulator
remainder as an integer numerator. Binary floating point is not persisted as
authoritative state. Restarting after two ticks of a 60-minute day restores the
`2/3` remainder and the next server tick advances `dayTime` by exactly one
unit.

NeoForge's variable day-time hook is used only to suppress vanilla's automatic
per-tick daylight increment without disabling the daylight gamerule. The
ButcherCraft accumulator remains the replay-relevant authority for scaled
advancement.

## Business Calendar Mapping

The Business Calendar derives from the Overworld `dayTime` source.

Schema-1 epoch:

```text
Minecraft day 0 = Monday
```

Minecraft's visible clock places `dayTime = 0` near morning rather than
midnight. ButcherCraft encapsulates one canonical offset:

```text
visible-business time = (dayTime + 6000) mod 24000
```

Examples:

| Minecraft `dayTime` | Business display |
| ---: | --- |
| 0 | Monday 06:00 |
| 6000 | Monday 12:00 |
| 12000 | Monday 18:00 |
| 18000 | Tuesday 00:00 |

The snapshot exposes:

- business day index;
- day-of-week;
- business time-of-day;
- normalized day fraction;
- world-day identity;
- configuration identity;
- source dimension identity;
- observation `gameTime`.

## Sleep And Time Commands

Sleeping remains usable because ButcherCraft does not disable the daylight
gamerule. When vanilla sleep advances the Overworld to morning, the World Time
service observes the resulting `dayTime` jump and updates the Business
Calendar directly.

Explicit `/time set`, `/time add`, admin tools, and other external day-time
changes are classified as forward jumps, backward jumps, configuration
transitions, or external authority conflicts. These observations do not cause
missed minutes, hours, or days to replay.

The no-catch-up rule is:

- Business Calendar display moves directly to the observed `dayTime`;
- Scheduler remains simulation-tick based;
- Planning cadence remains simulation-tick based;
- Production progress remains simulation-tick based;
- workstation progress remains server-tick based;
- no burst of skipped daily events is generated.

## Dimension Policy

Schema 1 has one Business Calendar source: the Overworld.

- The Overworld is the scaled visible-day source.
- Fixed-time dimensions are not given artificial day/night cycles.
- Nether and End behavior remains vanilla.
- Cross-dimension players observe the same server Business Calendar snapshot
  derived from the Overworld.
- Custom dimension support is explicit future work.

## Vanilla System Effects

Scaling `dayTime` changes visible daylight and darkness duration in real time.

Expected consequences:

- daylight lasts longer;
- nighttime lasts longer;
- villager schedules follow the slower visible day;
- moon phases advance more slowly in real time;
- daylight-sensitive burning follows the scaled sunrise;
- hostile spawn windows last longer in real time while darkness remains;
- crops, furnaces, random ticks, block ticks, entity AI, weather timers,
  redstone, Scheduler, Execution, Production, and workstation processing keep
  their normal tick-driven pace.

IM-021 does not add mob-spawn balancing, shortened nights, seasons, holidays,
deadlines, shifts, operating hours, payroll, deliveries, offline progression,
or an independent real-time mode.

## Persistence

World time persistence is stored at:

```text
<world>/butchercraft/world_time.json
```

Schema version 1 stores only continuity and diagnostic state:

- schema version;
- configuration identity;
- fractional accumulator remainder;
- last observed raw `dayTime`;
- last expected scaled `dayTime`;
- source dimension identity;
- observation `gameTime`;
- last movement classification;
- consecutive unexpected-change count;
- external conflict flag.

Minecraft remains authoritative for the actual saved world `dayTime`.
ButcherCraft does not duplicate that value as a second source of truth.

If the configured day length changes between sessions, the current sun
position remains unchanged. The new rate applies prospectively, the
configuration identity changes, and the accumulator remainder is normalized.

## Client Display Synchronization

The server sends bounded display snapshots to clients. Snapshots include the
business day, day-of-week, business time, configured day length, enabled flag,
source dimension, movement classification, and conflict flag.

Snapshots do not include private accumulator authority, Scheduler state,
Execution tokens, recovery metadata, or other subsystem internals.

## Other Time Mods

Only one mod should be the authoritative day-time scaler for a world.
ButcherCraft detects repeated unexpected `dayTime` changes and exposes an
external conflict diagnostic. Pack developers can disable ButcherCraft scaling
with:

```toml
[world_time]
enabled = false
```

When disabled, ButcherCraft observes vanilla or external time for display
without publishing scaled `dayTime`.

## Diagnostics

Use:

```text
/butchercraft time status
```

The command reports configuration, scale ratio, current `gameTime`, current
`dayTime`, Business Calendar identity, accumulator remainder, source dimension,
last movement classification, and external conflict state.

## Future Gates

The following remain unauthorized by IM-021:

- business hours;
- employee shifts;
- holidays;
- seasonal demand;
- payroll;
- deliveries;
- customer deadlines;
- production-order due dates;
- operating-hours UI;
- real-time mode;
- independent Business Calendar mode;
- offline progression;
- public calendar API;
- startup recovery or operator reconciliation.
