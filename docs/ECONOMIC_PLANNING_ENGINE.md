# Economic Planning Engine

Status: implemented schema 1 foundation

## Purpose

The Economic Planning Engine converts authoritative economic facts into bounded,
deterministic Production intent. It answers one schema-1 question:

> Which existing Production Plans should be registered and scheduled to address
> accepted, unfulfilled Order lines?

Planning is a decision owner, not an execution owner. It does not create Goods,
advance time, reserve or mutate Inventory, execute Production, submit economic
Transactions, fulfill Orders, or mutate Scheduler runtime state directly.

The pure Java domain is `com.butchercraft.world.planning`.
`EconomicPlanningService` is the Minecraft/NeoForge lifecycle adapter.

## Authority Boundaries

Planning reads immutable snapshots from existing authorities:

- Goods for stable product identity and unit validation.
- Economic Actors for industry and capability compatibility.
- Business Runtime and Workforce for operational requirements.
- Inventory for current exact stock, status, and destination capacity.
- Orders and Contracts for outstanding intent and obligation context.
- Production for Processes and existing Plan/Run commitments.
- Scheduler for capacity facts and authoritative submission.
- Transactions for validation and audit references only.

Planning owns only its observations, detected needs, constraints, opportunities,
candidate plans, approved plans, submission runtime, cycle reports, and
persistence. Its only mutation boundary is a typed submission adapter. Schema 1
uses `ProductionPlanningSubmissionAdapter`, which asks Production to atomically
register and schedule an accepted Plan through Scheduler authority.

## Canonical Concepts

### Observation

An `ObservationDefinition` is an immutable, timestamped fact captured from one
authoritative source during the observation phase. It has a stable id, provider,
typed observation kind, origin, sorted references, bounded payload, and schema
version.

Current observations cover open Order lines, existing Production commitments,
compatible Production opportunities, and Scheduler capacity. Later phases
consume these captured facts and immutable opportunity parameters rather than
re-reading mutable managers.

### Need

A `NeedDefinition` is an immutable requirement derived from observations.
Schema 1 supports `OUTSTANDING_ORDER_LINE` needs only. Each Need records exact
`GoodQuantity`, canonical unit, priority, horizon, due/expiration ticks,
aggregation identity, split policy, provenance, and metadata.

Existing active Production commitments associated with the same Order and Good
are subtracted before a Need is emitted. Planning does not treat a Plan as Order
fulfillment; it only avoids creating duplicate intent for already committed
output.

### Constraint

A `ConstraintDefinition` is an immutable blocking or warning fact with typed
scope, severity, provenance, optional expiration, and optional parent
constraints. Parent links must resolve and the graph must remain acyclic.

Examples include no compatible Process, unavailable Business operation,
unachievable due tick, capacity exhaustion, and policy rejection.

### Opportunity

An `OpportunityDefinition` represents one available way to address a Need.
Schema 1 opportunities bind a compatible Production Process to an eligible
Actor and existing Inventories. They capture only the immutable process facts
needed by later Planning phases: exact inputs, batch bounds, work units per
batch, output quantity, bindings, capacity, and availability. They do not copy
or replace the complete authoritative Process definition.

### Candidate Plan

A `CandidatePlanDefinition` is a deterministic proposal derived from one or
more Needs and one Opportunity. It records the proposed Production action,
exact capacity claims, expected output, quantity addressed, overproduction,
whole-batch count, expected completion, feasibility, constraints, provenance,
and a stable deduplication key.

Candidates are proposals only. They reserve no capacity and create no
Production or Scheduler state.

### Approved Plan

An `ApprovedPlanDefinition` is a policy-selected Candidate with exact Need
coverage and accepted cycle-local capacity claims. Immediate schema-1
Production approvals are executable; non-executable future horizons remain
advisory.

Approval is still not execution. Submission runtime separately records whether
the typed adapter submitted, rejected, deferred, failed, or cancelled the
approved intent.

## Deterministic Cycle

One Planning Cycle runs for one authoritative simulation tick:

```text
authoritative simulation tick
  -> observe immutable subsystem facts
  -> detect and order Needs
  -> detect Constraints
  -> discover compatible Opportunities
  -> generate whole-batch Candidates
  -> evaluate feasibility and deadlines
  -> rank by the stable comparator contract
  -> greedily select within cycle-local capacity ledgers
  -> approve bounded Plans
  -> submit executable approvals through the typed adapter
  -> publish one immutable terminal cycle snapshot
```

The canonical Need ordering is horizon, descending priority, required tick,
creation tick, type, Good id, and Need id. Candidate ordering is horizon,
descending priority, completion tick, descending addressed quantity,
overproduction, batch count, and Candidate id.

All quantities use exact `GoodQuantity` arithmetic. Whole-batch conversion uses
decimal ceiling and the Process batch minimum, maximum, and increment. No
floating-point math, random tie breaking, wall-clock time, unordered iteration,
or background thread affects an outcome.

Selection uses a bounded greedy pass. It maintains detached cycle-local ledgers
for Opportunity batch capacity and shared input Inventory quantities. These
claims prevent over-allocation inside one cycle but are not reservations.
Production revalidates every requirement when submitted and executed.

## Execution Budgets

`PlanningExecutionBudget` independently bounds observations, Needs,
Constraints, Opportunities, Opportunities per Need, Candidates, Candidates per
Need, evaluations, approvals, approvals per Need, submissions, aggregation
groups, recursion depth, provider work, total work, and payload size.

Budget exhaustion does not discard accepted artifacts. The cycle publishes
`COMPLETED_WITH_REMAINDER`, sets the report's truncation flag, and leaves
unresolved Need state and blocking Constraint provenance visible for a later
authoritative cycle. Reports persist the exact configured budget plus provider
and total work-unit consumption. Candidates that exceed the evaluation budget
remain explicit as `UNEVALUATED` and cannot be selected.

## Production Submission

`ProductionPlanningSubmissionAdapter` supports executable Production approvals
only. It derives one stable `ProductionPlanId` from the Approved Plan identity,
constructs the existing `ProductionPlanDefinition`, and calls
`ProductionManager.registerAndSchedulePlan`.

The Production operation is idempotent:

- identical existing Plan and scheduled Run return the existing Run;
- an identity collision with different content is rejected;
- a newly registered Plan is removed if Scheduler rejects its Work;
- accepted Scheduler Work remains authoritative and is never rolled back by
  Planning.

The adapter returns stable Production Plan and Scheduler Work references for
Planning persistence. Planning never calls Production execution, Scheduler
runtime transitions, Inventory mutation, Transaction submission, or Order
fulfillment.

## Scheduler Integration

Planning registers one internal Work type:

```text
butchercraft:economic_planning_cycle
```

It runs in `butchercraft:planning`. The payload contains only the Planning
policy id. The handler consumes the Scheduler's authoritative tick, executes at
most one cycle for that tick, and defers the same continuation Work to the next
tick. It does not own a second timer or tick loop.

The default policy is:

```text
butchercraft:default_business_production_planning
```

The world integration layer installs the Planning handler before Scheduler
persistence loads, initializes Planning after Scheduler and Production are
bound, and creates the continuation Work only when it is absent.

## Persistence

Schema 1 persists terminal cycles under `<world>/butchercraft/`:

```text
planning_observations.json
planning_needs.json
planning_opportunities.json
planning_candidates.json
planning_approved_plans.json
planning_runtime.json
```

Files use UTF-8, stable snake-case field names, explicit schema versions,
deterministic record order, temporary-file writes, and atomic replacement when
supported. An existing persistence set must contain all six files.

Load reconstructs complete cycle snapshots and validates:

- schema versions and record structure;
- duplicate and unmatched Cycle records;
- terminal Cycle status;
- Constraint graph acyclicity;
- Need, Opportunity, Candidate, and Approved Plan provenance;
- Goods, Orders and lines, Production Processes, Actors, Businesses, and
  Inventories;
- submitted Production Plan and Scheduler Work references.

Malformed, partial, unsupported, interrupted, or externally inconsistent state
fails initialization visibly. Planning does not silently discard a cycle,
invent missing references, or automatically replay interrupted work.

## Service Lifecycle

Server start order is:

1. Production loads and installs its Scheduler handler.
2. Planning installs its delegating Scheduler handler.
3. Scheduler loads and validates persisted Work types.
4. Production validates persisted Scheduler references.
5. Planning loads after all dependencies and validates its six-file snapshot.
6. Planning ensures one continuation Work exists.

On shutdown, Production and Scheduler save before Planning. Planning retains
references to the loaded authoritative manager snapshots while validating and
writing its terminal cycles.

## Invariants

- `EP-0001`: Simulation Clock is the only time authority.
- `EP-0002`: Scheduler owns Work eligibility and runtime.
- `EP-0003`: Planning owns decisions, not execution.
- `EP-0004`: Inventory owns quantities; Planning never mutates them.
- `EP-0005`: Later phases consume captured immutable Planning facts.
- `EP-0006`: Transactions own economic quantity mutation and audit.
- `EP-0007`: Orders own fulfillment state.
- `EP-0008`: Production owns Processes, Plans, Runs, and execution.
- `EP-0009`: Exact quantities and explicit comparator chains determine results.
- `EP-0010`: Cycle-local claims are not durable reservations.
- `EP-0011`: One authoritative tick creates at most one Planning Cycle.
- `EP-0012`: Approved Plan submission is typed and idempotent.
- `EP-0013`: Terminal cycle persistence is complete-set and fail-visible.
- `EP-0014`: Minecraft and NeoForge APIs remain outside the Planning domain.
- `EP-0015`: Schema 1 supports business-scale Production planning only.

## Measured Phase 21 Scale

The split Java 21/NeoForge JUnit stress run constructed the required immutable
datasets without retaining all artifact classes concurrently:

- 1,000,000 Observations: 590 ms.
- 500,000 Needs: 1,167 ms.
- 500,000 Opportunities: 823 ms.
- 1,000,000 Candidate Plans: 598 ms.
- 100,000 Approved Plans: 68 ms.

These are observations from one development run, not production latency
guarantees. Gradle startup, NeoForge test bootstrap, JVM warmup, hardware,
memory pressure, and concurrent suites affect wall time.

## Current Scope And Extension Points

Implemented:

- open accepted Order-line observation;
- existing Production commitment subtraction;
- compatible Process, Actor, and Inventory discovery;
- exact whole-batch Candidate generation;
- bounded deterministic selection;
- typed Production Plan registration and scheduling;
- cycle reporting, queries, persistence, replay validation, and stress coverage.

Deferred:

- Inventory reservations;
- purchasing, shipment, maintenance, utility, and inspection planning;
- automated Order creation;
- pricing, markets, accounting, and demand forecasting;
- cross-cycle optimization;
- parallel providers or background evaluation;
- public third-party provider registration;
- gameplay, GUI, commands, networking, and ItemStack integration.

Supporting authorities are documented in
[`ORDERS_AND_CONTRACTS.md`](ORDERS_AND_CONTRACTS.md),
[`PRODUCTION_FRAMEWORK.md`](PRODUCTION_FRAMEWORK.md), and
[`SIMULATION_SCHEDULER.md`](SIMULATION_SCHEDULER.md).
