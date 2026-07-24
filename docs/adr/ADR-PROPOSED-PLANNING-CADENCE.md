# ADR-04: Deterministic Planning Cadence

Status: RATIFIED ARCHITECTURAL DIRECTION - IMPLEMENTATION NOT AUTHORIZED

Decision identifier: AH-1-ADR-04

Package: BCSE Architecture Hardening AH-1

Authority: Owner-ratified architecture direction. This document authorizes
documentation alignment only. It does not authorize implementation, migration,
schema changes, runtime behavior, gameplay behavior, or RFC-0023 edits.

Canonical platform reference:
[`Platform Canonicalization Addendum`](ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md).
Platform-wide vocabulary, identity classes, invariant ownership, recovery,
replay, failure-state, cancellation, operator-authority, World Identity, and
Platform Determinism Manifest definitions are canonical there and are not
redefined here.

## Context

The accepted Planning architecture assigns economic decision ownership to
Planning while preserving the Simulation Clock, Scheduler, Production,
Transactions, and Inventory as separate authorities. The current schema-1
integration installs one continuing Scheduler Work item. That Work executes a
complete Planning Cycle on every authoritative simulation tick and defers
itself to the next tick.

Per-cycle execution is deterministic and bounded. World-lifetime cycle count,
retained evidence, save cost, and load cost are not bounded.

This decision responds to
[BCSE-AUDIT-001](../BCSE_ARCHITECTURE_AUDIT.md#bcse-audit-001-planning-history-grows-without-a-lifetime-bound).
The audit is evidence, not authority.

## Problem

BCSE needs a deterministic Planning cadence that:

- reacts to economically relevant change without planning continuously;
- guarantees a bounded maximum planning frequency;
- still evaluates an idle living world periodically;
- remains replayable across pause, restart, and recovery;
- cannot create burst catch-up work after downtime;
- does not depend on wall-clock time or player presence; and
- preserves Planning as the sole owner of economic decisions.

## Current Behavior

- `EconomicPlanningService` creates
  `butchercraft:economic_planning_cycle/continuation` for the next finalized
  simulation tick.
- `EconomicPlanningWorkHandler` executes one full cycle and returns
  `DEFERRED` for `tick + 1`.
- `PlanningManager` permits at most one cycle per tick but retains every cycle.
- Scheduler budgets bound one invocation, not the number of lifetime
  invocations.
- No Planning cadence configuration, relevant-change trigger queue, missed
  cycle policy, or catch-up policy exists.

This decision does not change that behavior. It defines a replacement contract
for later owner-authorized implementation.

## Architectural Constraints

The decision is governed by:

- `AI-0001` Deterministic Simulation;
- `AI-0009` Deterministic Registries;
- `AI-0016` Explicit Responsibility Boundaries;
- `AI-0021` Explicit Failure Outcomes;
- `AI-0022` Authoritative Simulation Time;
- `AI-0025` Singular Data Ownership;
- `AI-0026` Bounded Simulation Work;
- `AI-0027` Tests Are Part Of The Contract; and
- `AI-0028` Backward-Compatible Evolution.

Additional constraints:

- no wall-clock input;
- no dependence on connected players;
- no hidden polling loop outside the Scheduler;
- no direct Planning mutation of other owners;
- no more than one Planning Cycle at one simulation tick;
- no silent loss of pending relevant-change evidence; and
- no implementation before separate owner authorization.

Planning owns Planning eligibility, trigger consumption, input capture,
Planning Cycle publication, and Planning decisions. Scheduler owns Scheduler
Work dispatch and lifecycle. Evidence Lifecycle owns retention and archival.
Checkpoint Recovery owns committed generation publication and recovery
selection.

## Options Considered

| Option | Advantages | Disadvantages | Determinism and replay | Runtime cost | Save/load effect | Migration effect |
|---|---|---|---|---|---|---|
| 1. Every simulation tick | Lowest reaction latency; matches current behavior | Unbounded cycle and evidence growth; idle worlds do maximum work | Simple deterministic sequence, but extremely large replay input | Highest and continuous | Rewrites and reloads all retained cycles | No cadence migration, but retention remains unresolved |
| 2. Fixed periodic cadence | Simple bound; easy to schedule and replay | Relevant changes can wait for the next period | Deterministic from period and last-cycle tick | Low and predictable | Much lower cycle count | Replace continuation tick with persisted next-period tick |
| 3. Evidence-change-triggered cadence | Runs only when useful | Trigger storms, missing triggers, and indefinite idle inactivity require complex guarantees | Deterministic only if every trigger is authoritative, persisted, ordered, and deduplicated | Low when idle; burst-prone | Trigger queue becomes required state | Every relevant owner needs a trigger contract |
| 4. Deterministic hybrid periodic plus relevant-change triggers | Bounded idle evaluation and responsive change handling | More state and validation than a fixed period | Deterministic when trigger records, ordering, and coalescing are explicit | Bounded by minimum separation; low when idle | Persists one cadence state and bounded pending triggers | Requires schema migration and owner adapters |
| 5. Demand-driven scheduling only | No idle Planning work | A living world can stop planning when no requester submits demand; hidden coupling to demand producers | Replay depends on complete demand submission history | Lowest baseline | Smallest baseline state | Requires every Need source to become a scheduler client |

## Decision

Adopt **Option 4: deterministic hybrid periodic plus relevant-change
triggers**.

### Cadence Values

Schema 1 of this contract uses these operational defaults:

- minimum cycle separation: **20 simulation ticks**;
- default periodic cadence: **1,200 simulation ticks**;
- configurable periodic cadence range: **20 through 72,000 simulation
  ticks**, inclusive;
- at most one Planning Cycle at one simulation tick; and
- at most one pending Scheduler Work item for Planning per world.

These are simulation ticks. Their meaning never changes with wall-clock rate,
server performance, pause duration, or player count.

Numeric cadence values are schema-1 operational defaults rather than
permanent architectural invariants. They may be revised through an accepted
implementation milestone before public save compatibility is promised if
bounded recurring work, deterministic eligibility, explicit replay inputs, and
the no-burst-catch-up rule remain intact.

The cadence values are world-authoritative, schema-versioned Planning
configuration. A configuration change is effective only from an explicit
simulation tick and is retained as replay input.

### Periodic Trigger

After a completed Planning Cycle at tick `T`, the next periodic due tick is:

```text
T + configured_periodic_cadence
```

The addition must use checked exact arithmetic. Overflow is an explicit
terminal configuration/runtime failure.

An idle world runs no more frequently than the periodic cadence. It remains
eligible whether zero, one, or many players are connected.

### Relevant-Change Triggers

A relevant-change trigger is immutable evidence published by an authoritative
owner. Schema 1 trigger categories are limited to facts Planning already reads:

- accepted Order or Contract definition/lifecycle change;
- Order fulfillment change;
- Production Plan or Run lifecycle change affecting an open Need;
- Inventory Freshness Identity affecting a Good and actor/inventory binding
  referenced by an open Need;
- Business Runtime or Workforce availability revision referenced by a
  candidate Opportunity; and
- approved Planning configuration revision.

Planning does not infer a trigger by polling or comparing live objects. The
owning subsystem publishes a stable reference and revision through an
owner-specific adapter. Publishing a trigger does not transfer ownership of
the underlying fact.

### Trigger Identity And Ordering

Each trigger has a deterministic identity derived from:

```text
source subsystem id
source stable identity
source authoritative revision
source simulation tick
trigger category
schema version
```

Triggers are ordered by:

1. source simulation tick;
2. source subsystem id;
3. source stable identity;
4. source revision;
5. trigger category; and
6. trigger identity.

Duplicate identities are rejected as duplicate submissions or recognized as
identical replay, according to the owner adapter's accepted idempotency
contract. Same-identity/different-content is an explicit conflict.

### Coalescing

All pending relevant-change triggers are coalesced into one ordered trigger set
for the next eligible Planning Cycle. Repeated trigger categories do not create
additional cycle requests.

The next eligible tick is:

```text
max(current_authoritative_tick + 1, last_cycle_tick + minimum_separation)
```

If a periodic cycle is due earlier, both causes are represented by one cycle.
The cycle records the ordered trigger identities and whether the periodic
deadline was also due.

Triggers arriving after input capture begins remain pending for a later cycle.
The capture boundary is explicit and recorded.

### Missed Cycles, Pause, And Restart

- Server pause advances no simulation ticks and creates no missed cycles.
- A graceful restart reloads `last_cycle_tick`, `next_periodic_due_tick`, and
  pending trigger records from the committed checkpoint.
- If the periodic due tick is already at or behind the recovered Clock tick,
  exactly one recovery Planning Cycle becomes eligible at the next sequential
  Scheduler tick.
- No cycle is generated for every elapsed period.
- After the recovery cycle, the next periodic due tick is based on the actual
  recovery cycle tick.
- Pending triggers are coalesced into that recovery cycle.
- A crash rolls back cadence state and trigger state to the last committed
  checkpoint under the Checkpoint Recovery ADR. BCSE performs no
  wall-clock or Minecraft-time catch-up.

### Scheduler Interaction

- Planning remains in the accepted PLANNING stage.
- One Scheduler Work item represents the next Planning Cycle, regardless of
  trigger count.
- Scheduler item, stage, work-unit, generation, retry, and same-tick budgets
  continue to apply.
- If the Planning stage budget prevents execution, the same Scheduler Work
  remains eligible under Scheduler rules. No second Scheduler Work item for
  Planning is created.
- A completed Planning Cycle consumes only the trigger set captured for that
  cycle.
- Failed input capture consumes no trigger.
- A failure after accepted Planning publication follows the Scheduler effect
  and checkpoint contracts; it must not silently schedule a duplicate Planning
  Cycle.

### Checkpoint Interaction

The Planning checkpoint snapshot includes:

- cadence schema version;
- configured periodic cadence;
- minimum separation;
- last completed cycle tick;
- next periodic due tick;
- pending ordered trigger records;
- captured-but-not-published trigger state, if any;
- current Scheduler Work identity and Scheduler reference for Planning; and
- correlation identities for the latest completed cycle and checkpoint.

The Clock, Scheduler, Planning cadence, and Planning evidence must belong to
one committed checkpoint generation. Independent restoration is prohibited.

### Replay

Replay input consists of:

- the starting committed checkpoint;
- authoritative simulation ticks;
- the cadence configuration history;
- the ordered relevant-change trigger records;
- Scheduler budget and stage inputs; and
- owner snapshots consumed by each cycle.

Given identical inputs, replay produces identical cycle ticks, trigger sets,
cycle identities, decisions, and evidence.

### Schema-1 Limitations

- Trigger categories are closed to the facts listed above.
- New trigger categories require an additive schema change and owner adapter.
- No predictive or speculative trigger exists.
- No random jitter exists.
- No dynamic cadence based on server performance exists.
- No player-presence optimization exists.
- No burst catch-up exists.
- No more than one Planning Cycle per simulation tick exists.

## Rationale

The hybrid model bounds idle work while preserving timely response to
economically meaningful change. A fixed periodic safety trigger protects the
living-world principle from missing or delayed change events. Minimum
separation and one-work coalescing prevent event storms from recreating
per-tick Planning under another name.

The schema-1 1,200-tick default reduces idle cycle creation by a factor of
1,200 relative to current behavior. The 20-tick minimum prevents more than one
Planning Cycle per 20 simulation ticks even under continuous change. The
72,000-tick upper configuration bound prevents indefinite Planning starvation.

## Consequences

### Positive Consequences

- Planning frequency is explicitly bounded.
- Idle worlds continue to plan without player presence.
- Relevant changes can schedule earlier than the periodic deadline.
- Trigger storms collapse into one deterministic cycle.
- Pause and restart behavior become replayable.
- Catch-up cannot create an unbounded work burst.
- Planning remains decision owner and Scheduler remains eligibility owner.

### Negative Consequences

- Cadence state and pending trigger records become new authoritative runtime.
- Relevant owners need narrow trigger adapters.
- A relevant change may wait up to the minimum separation.
- A missing owner trigger delays response until the periodic cycle.
- Existing per-tick cycle histories require migration.
- The schema-1 constants become compatibility-sensitive world configuration
  once public save compatibility is promised.

## Compatibility

The decision changes accepted DEC-0074 behavior after implementation is
separately authorized. It does not amend DEC-0074 in place.

Existing Planning artifact identities and cycle contents remain valid. The
cadence and trigger schema are additive. Existing worlds must not reinterpret
old cycles as trigger-driven cycles.

## Migration

The first checkpoint migration must:

1. validate all six existing Planning files as one complete legacy set;
2. preserve every existing Planning Cycle and its immutable evidence;
3. set `last_cycle_tick` to the latest validated cycle tick;
4. set `next_periodic_due_tick` to
   `last_cycle_tick + configured_periodic_cadence`;
5. create no synthetic historical triggers;
6. create one migration-correlation record;
7. replace the every-tick continuation Work with one cadence Work only in the
   same committed checkpoint generation; and
8. retain the legacy files until checkpoint migration retention permits their
   explicit archival.

If Clock, Scheduler, or Planning state cannot be reconciled, migration fails
visibly and leaves the legacy state unchanged.

## Failure Behavior

- Invalid cadence configuration rejects world initialization.
- Duplicate trigger identity with different content is
  `PLANNING_TRIGGER_IDENTITY_CONFLICT`.
- Trigger queue capacity exhaustion is
  `PLANNING_TRIGGER_CAPACITY_EXHAUSTED` and blocks creation of additional
  Scheduler Work for Planning without deleting triggers.
- Checked tick overflow is `PLANNING_CADENCE_TICK_OVERFLOW`.
- Inconsistent checkpoint cadence state is
  `PLANNING_CADENCE_CHECKPOINT_MISMATCH`.
- Multiple Scheduler Work items for Planning are
  `PLANNING_CADENCE_DUPLICATE_WORK`.
- No failure path silently discards a pending trigger or creates a second
  Planning Cycle at the same tick.

Failure-code names are architectural contract names, not implemented
constants.

## Replay Implications

Replay becomes smaller than every-tick replay while remaining exact. Trigger
records are replay-critical until subsumed by a retained checkpoint. The
evidence lifecycle decision may archive them only after preserving the minimum
replay horizon.

## Security And Integrity Implications

- Only registered owner adapters may publish a trigger for their owner id.
- Trigger content includes the source revision and is digestible canonically.
- A client cannot schedule authoritative Planning work.
- Player connection state is never an input.
- Trigger flooding is bounded by deduplication, queue capacity, minimum
  separation, and Scheduler budgets.

## Testing Requirements

Required automated tests:

- default, minimum, and maximum cadence;
- configuration below/above bounds;
- periodic idle-world behavior with zero players;
- relevant trigger scheduling;
- deterministic trigger ordering;
- duplicate trigger deduplication;
- same-ID/different-content rejection;
- multiple triggers coalesced into one cycle;
- trigger arriving during input capture;
- at most one cycle per tick;
- minimum separation under continuous triggers;
- Scheduler budget deferral without duplicate Work;
- server pause with no tick advance;
- graceful restart before and after the due tick;
- crash rollback to a committed checkpoint;
- one recovery cycle with no burst catch-up;
- replay equivalence;
- checked tick overflow;
- migration from existing every-tick continuation state;
- one million simulation ticks with bounded cycle count; and
- no wall-clock, Minecraft-time, or player-presence dependency.

## Alternatives Rejected By This Proposal

- **Every simulation tick:** rejected because per-cycle bounds do not bound
  lifetime work or evidence.
- **Fixed periodic only:** rejected because response latency is unnecessary
  when authoritative relevant changes are already known.
- **Evidence-change only:** rejected because missed triggers could stop a
  living world indefinitely.
- **Demand-driven only:** rejected because it transfers liveness to demand
  producers and can make world simulation player- or feature-dependent.

## Ratification Notes

Owner ratification approved deterministic hybrid Planning cadence with the
revisions incorporated above:

1. Planning owns Planning eligibility, trigger consumption, input capture,
   Planning Cycle publication, and Planning decisions.
2. Scheduler owns dispatch and lifecycle of Scheduler Work; it does not decide
   Planning outcomes.
3. The hybrid model, deterministic trigger identity, deterministic coalescing,
   one Planning Cycle per tick, and no burst catch-up are approved
   architectural direction.
4. The 20-tick minimum, 1,200-tick default, 72,000-tick maximum, queue limits,
   and similar numeric values are schema-1 operational defaults, not permanent
   invariants.
5. Relevant-change classification must use owner-published facts and
   freshness identities without transferring ownership to Planning.
6. Restart and rollback behavior uses committed checkpoint state and never
   wall-clock catch-up.

Implementation, Scheduler effect changes, Planning code changes, evidence
migrations, checkpoint implementation, RFC-0023 edits, Execution
implementation, Allocation integration, and gameplay remain separately gated.
