# Business Runtime

Status: v0.9.0 Phase 11 foundation

The Business Runtime Framework makes immutable business identity records participate in simulated time without turning them into gameplay systems. Business identity remains part of World Identity. Runtime business state is stored separately and may change over time.

## Architecture

The runtime package owns:

- `BusinessRuntimeState` for mutable operational state snapshots.
- `BusinessOperationalStatus` for closed, opening, operating, shift-change, closing, maintenance, and suspended states.
- `BusinessHours` for simulation-calendar operating days and open/close times.
- `BusinessShift` for named shift windows and expected workforce counts.
- `BusinessRuntimeRegistry` for deterministic runtime lookup, validation, and storage.
- `BusinessRuntimeManager` for explicit open, close, shift, maintenance, suspension, and schedule evaluation transitions.
- `BusinessEventListener` for simulation event-bus integration.
- `BusinessRuntimeStorage` for schema-versioned JSON persistence.

Only `com.butchercraft.world.BusinessRuntimeService` imports Minecraft or NeoForge APIs. The business runtime package is Java-only and can be tested without launching Minecraft.

## Identity Boundary

Business Identity is immutable and remains inside the saved World Identity snapshot.

Business Runtime State is separate and references immutable businesses by `BusinessId` only. Runtime state does not duplicate business display names, founding years, ownership metadata, commercial property records, settlement records, or history.

## Lifecycle

Server start:

1. Resolve `<world>/butchercraft/business_runtime.json`.
2. Load existing runtime state if present.
3. Create default runtime state for any immutable business that does not yet have runtime state.
4. Validate every runtime `BusinessId` against the current World Identity businesses.
5. Subscribe `BusinessEventListener` to daily and weekly simulation rollover events.
6. Evaluate runtime state at the current simulation time.

Simulation events:

1. `DAILY_ROLLOVER` evaluates business hours and shift schedules for the event time.
2. `WEEKLY_ROLLOVER` performs the same deterministic evaluation and validates runtime state.
3. Runtime transitions update only mutable runtime fields.

Server stop:

1. Save the active runtime registry.
2. Unsubscribe the event listener.
3. Clear the active runtime service reference.

## Persistence

Business runtime state is stored at:

```text
<world>/butchercraft/business_runtime.json
```

The schema version is `1`.

The file stores:

- `schema_version`
- `business_runtime_states`
- referenced `business_id`
- operational status
- open/closed state
- active shift id
- workforce capacity
- active workforce
- maintenance flag
- last state-change simulation tick
- business hours
- shift schedule

It does not store immutable business identity data.

## Validation

The runtime framework rejects:

- duplicate runtime business ids
- unknown business references
- unknown operational statuses
- invalid schema versions
- corrupt JSON
- invalid business hours
- invalid operating weekdays
- invalid shifts
- duplicate shift ids
- negative workforce capacity
- negative active workforce
- active workforce greater than capacity
- active workforce greater than the active shift expectation
- active shift references that do not exist
- disabled active shifts
- inconsistent open/status/maintenance combinations

## Extension Points

Future systems should integrate through the runtime manager and simulation event bus instead of mutating immutable business identity records.

The Workforce Framework consumes active shift ids from Business Runtime to determine which positions are required for a current shift. Planned later consumers include employees, production scheduling, inspections, refrigeration, suppliers, commercial reputation, orders, and economy systems.

Those later systems are intentionally not implemented in Phase 11.

## Out Of Scope

Phase 11 does not add employees, production, machines, economy, payroll, inspections, AI, inventory, orders, customers, transportation, maintenance gameplay, GUI, networking, or gameplay effects.
