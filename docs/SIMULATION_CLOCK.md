# Simulation Clock

Status: v0.9.0 Phase 10 foundation

The Simulation Clock is ButcherCraft's authoritative source of simulated world time. Minecraft server ticks provide execution cadence, but Minecraft time-of-day is not the business simulation.

## Architecture

The simulation package owns:

- `SimulationConfiguration` for all timing scale values.
- `SimulationTime` for simulation tick, elapsed minutes, hour, and minute.
- `SimulationCalendar` for day, week, month, year, weekday, and season.
- `SimulationClock` for current simulation time and advancement.
- `SimulationScheduler` for deterministic pending event ordering.
- `ScheduledSimulationEvent` for schema-stable event records.
- `SimulationEventBus` for listener publication.
- `SimulationStateStorage` for independent JSON persistence.
- `SimulationClockService` for Minecraft server lifecycle integration.

Only `SimulationClockService` imports Minecraft or NeoForge APIs. The clock, calendar, scheduler, event records, bus, state, and storage are Java-only.

## Lifecycle

Server start:

1. Resolve `<world>/butchercraft/simulation_state.json`.
2. Load the saved simulation state if present.
3. Otherwise create a schema-versioned initial state.
4. Create one active `SimulationClock`.
5. Resume pending scheduled events.

Server tick:

1. NeoForge emits a server post-tick event.
2. `SimulationClockService` advances the active clock by one simulation tick.
3. Due events execute in deterministic order.
4. Executed events publish through `SimulationEventBus`.

Server stop:

1. The service captures the active clock state.
2. Pending scheduler events are flushed.
3. The state is saved to JSON.

## Configuration

`SimulationConfiguration` defines:

- ticks per simulation minute
- minutes per hour
- hours per day
- days per week
- weeks per month
- months per year

Calendar math derives from this configuration. The standard configuration is currently:

```text
20 ticks per simulation minute
60 minutes per hour
24 hours per day
7 days per week
4 weeks per month
12 months per year
```

## Scheduler

The scheduler accepts pending `ScheduledSimulationEvent` records, rejects duplicate ids, rejects events scheduled in the past, supports cancellation, and orders simultaneous events by:

1. scheduled simulation tick
2. event type priority
3. event id

Built-in event types are infrastructure only:

- `daily_rollover`
- `weekly_rollover`
- `monthly_rollover`
- `yearly_rollover`

The clock schedules these rollover events automatically and republishes them through the event bus. It does not directly invoke gameplay systems.

## Persistence

Simulation state is stored separately from World Identity and Player Identity:

```text
<world>/butchercraft/simulation_state.json
```

The file contains:

- `schema_version`
- `simulation_tick`
- `calendar`
- `pending_scheduled_events`

The simulation schema version is `1`.

World Identity remains schema version `6`. Player Identity remains schema version `1`.

## Determinism

Given the same simulation configuration and advancement sequence, the clock produces identical:

- simulation ticks
- time-of-day values
- calendar values
- rollover events
- pending scheduler state

The framework does not depend on client frame rate, rendering, Minecraft time-of-day, or random event ordering.

## Validation

The simulation framework rejects:

- negative time
- invalid configuration values
- invalid calendar values
- duplicate event ids
- non-pending scheduled events
- events scheduled in the past
- corrupt JSON
- unsupported schema versions

## Extension Points

Future systems should subscribe to `SimulationEventBus` and schedule work through `SimulationScheduler` or a narrow service built on top of it.

Out of scope for Phase 10:

- production
- economy
- machines
- workers
- NPC AI
- inspections
- refrigeration
- maintenance
- reputation
- business operations
- GUI
- commands
- gameplay events
