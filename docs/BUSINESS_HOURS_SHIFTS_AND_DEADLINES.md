# Business Hours, Shifts, And Production Deadlines

Status: implemented foundation in IM-022

IM-022 adds the first operational business-time layer on top of the existing
World Time and Business Calendar foundation. It follows
`docs/BUSINESS_SIMULATION_BIBLE.md`: time should encourage planning, not
rushing.

This foundation answers:

- whether the plant is currently open;
- when the plant opens or closes next;
- which configured shift is active;
- which configured shift comes next;
- whether a Production deadline is upcoming, due now, overdue, completed, or
  cancelled.

It does not add employees, attendance, payroll, customers, reputation, money,
delivery windows, holidays, seasons, maintenance schedules, autonomous
Production scheduling, Allocation, public APIs, startup recovery, or
checkpoint activation.

## Authority Boundary

World Time owns scaled Minecraft `dayTime`, Business Calendar derivation,
world-day identity, movement classification, and day-length configuration
identity.

Business Runtime owns plant operating schedules, open/closed observations,
shift definitions, active and next shift observation, business-hours evidence,
and Business Runtime configuration identity.

Production owns Production Run deadlines, deadline identity, deadline
assignment and lock state, deadline status, and terminal completion timing.

Scheduler remains the owner of Work dispatch and effect enforcement. Planning
remains the owner of Planning cadence and eligibility. Client UI is
presentation only.

## Configuration

The server-authoritative common config contains a `business_runtime` section.
Schema 1 supports one operating window per weekday and explicitly closed days.

Default operating hours:

```toml
[business_runtime]
enabled = true
timezone_mode = "BUSINESS_CALENDAR"

[business_runtime.operating_hours]
monday = "06:00-18:00"
tuesday = "06:00-18:00"
wednesday = "06:00-18:00"
thursday = "06:00-18:00"
friday = "06:00-18:00"
saturday = "CLOSED"
sunday = "CLOSED"
```

Shift definitions are configured as deterministic strings:

```toml
shifts = [
  "day_shift|Day Shift|06:00-14:30|MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY",
  "evening_shift|Evening Shift|14:30-18:00|MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY"
]
```

Production Order deadlines are controlled by:

```toml
production_order_deadlines_enabled = true
production_order_default_deadline_minutes = 240
```

Config changes apply prospectively. They change Business Runtime configuration
identity, but they do not rewrite past Production evidence or locked
deadlines.

## Operating Hours

Operating windows use `HH:MM-HH:MM`. `24:00` is rejected because it is
ambiguous; use `00:00` with an overnight window instead.

Boundary rules:

- opening time is inclusive;
- closing time is exclusive;
- `06:00-18:00` means `06:00 <= time < 18:00`;
- at exactly `18:00`, the plant is closed;
- closed days have no operating window;
- overnight windows are supported when the end time is less than or equal to
  the start time, for example `18:00-02:00`;
- overlapping operating windows are rejected.

Business Runtime derives plant status from the current Business Calendar
snapshot. It does not maintain an independently advancing clock.

## Shift Definitions

A shift definition is a scheduled labor window. It does not mean employees
exist, employees arrived, Production starts automatically, or the plant must
perform work.

Schema 1 validation:

- shift ids must be stable lowercase identifiers;
- display names must be nonblank;
- time ranges use `HH:MM-HH:MM`;
- days must be explicit;
- duplicate shift ids are rejected;
- overlapping shifts are rejected;
- shifts outside plant operating hours are rejected;
- shifts are ordered deterministically by canonical shift id.

Business Runtime exposes:

- active shift, if one is scheduled now;
- next shift, if one is scheduled later in the configured week;
- shift identity and shift-set identity.

## Identity

Business Runtime publishes deterministic identities for:

- operating schedule;
- individual shift;
- shift set;
- Business Runtime calendar configuration.

These identities bind schema version, canonical normalized times, weekday
mapping, shift ids, shift ordering, and the World Time configuration identity
where appropriate. They do not bind wall-clock time.

Production deadline identity binds:

- Production Run identity;
- deadline type;
- business day index;
- business time;
- Business Runtime configuration identity;
- source world-day identity;
- source dimension identity;
- deadline source identity.

## Production Deadlines

Schema 1 uses a single deadline type: `TARGET`.

An absent deadline means `NO_DEADLINE`. Legacy Production Runs without a
deadline field remain valid and load with no deadline.

Deadline status values:

- `NO_DEADLINE`
- `UPCOMING`
- `DUE_NOW`
- `OVERDUE`
- `COMPLETED_EARLY`
- `COMPLETED_ON_TIME`
- `COMPLETED_LATE`
- `CANCELLED`

The due instant is the exact business day and time:

- before the due instant: `UPCOMING`;
- at the due instant: `DUE_NOW`;
- after the due instant while incomplete: `OVERDUE`.

Missing a deadline does not cancel the Run, fail workstation operations, move
items, remove money, change reputation, generate customer complaints, or retry
work. It records status only.

## Completion Timing

When an explicit completion Business Calendar snapshot is supplied, Production
locks terminal deadline timing:

- completion before the due instant is `COMPLETED_EARLY`;
- completion at the exact due instant is `COMPLETED_ON_TIME`;
- completion after the due instant is `COMPLETED_LATE`.

Terminal completion timing is persisted and is not recomputed differently after
reload or after time moves backward.

## Deadline Changes

A deadline may be set before consequential execution begins.

Duplicate identical assignments are safe. Conflicting assignments after the
deadline is locked, scheduled, started, or otherwise execution-bound are
rejected explicitly.

The client cannot author arbitrary deadlines. The current player-facing source
is the fixed Beef Patties Production Order template, using the configured
default target offset.

## Time Jumps

Forward movement from sleep or `/time set` updates Business Runtime directly:

- plant status reflects the new Business Calendar;
- active and next shift reflect the new Business Calendar;
- incomplete deadlines are reclassified once when observed;
- no skipped minute, hour, or day loop runs;
- Scheduler and Planning do not burst catch-up work;
- Production evidence is not duplicated.

Backward movement updates current nonterminal display from the authoritative
Business Calendar. An incomplete overdue deadline can appear upcoming again if
time is moved before its due instant. Terminal completion timing remains
terminal and stable.

## Persistence

Business Runtime diagnostic calendar state persists at:

```text
<world>/butchercraft/business_calendar_runtime.json
```

It stores schema version, operating schedule identity, shift-set identity,
configuration identity, last observed world-day identity, last open/closed
state, last active shift identity, last evaluated boundary, and movement
classification.

It does not persist a duplicate clock.

Production deadlines persist inside Production Run persistence:

```text
<world>/butchercraft/production_runs.json
```

The deadline record stores identity, business day and time, status, lock
state, terminal completion timing when present, evaluated world-day identity,
schema, source identity, and Business Runtime configuration identity.

## Player Presentation

The Production Order screen presents:

- plant status;
- current business day and time;
- active or next shift;
- Production deadline;
- deadline status and time remaining or overdue amount;
- terminal completion timing.

The UI does not display raw ticks, hashes, configuration identities, evidence
identities, or internal authority tokens.

## Diagnostics

Use:

```text
/butchercraft business status
```

The command reports current Business Calendar day and time, plant open/closed
state, current operating window, next opening or closing boundary, active
shift, next shift, schedule identity, shift-set identity, configuration
identity, and recent time movement classification.

The command is read-only.

## Pack Examples

Sandbox:

- plant open every day;
- one all-day shift;
- Production Order deadlines disabled.

Casual plant:

- `06:00-20:00`;
- one long shift;
- generous default deadlines.

Realistic plant:

- Monday through Friday;
- `06:00-18:00`;
- day and evening shifts;
- moderate deadlines.

Wholesale focus:

- early opening;
- morning-oriented shifts;
- stricter target deadlines.

These are configuration examples only, not runtime presets.

## Remaining Gates

The following remain gated:

- worker entities;
- employee schedules and attendance;
- payroll, overtime, breaks, morale, or fatigue;
- customer orders and retail traffic;
- reputation, penalties, money, or contract consequences;
- holidays and seasons;
- delivery or maintenance windows;
- autonomous Production scheduling;
- Allocation integration;
- public Business Calendar or Business Runtime APIs;
- startup recovery, automatic checkpoints, and operator reconciliation.
