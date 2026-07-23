# Industry-Neutral Production Framework

Status: implemented in v0.9.0-alpha.1 Phase 20 (RFC-0020)

## Purpose

The Production Framework is ButcherCraft Core's pure Java operational layer for turning economic Goods into other Goods over authoritative simulation time. It is industry-neutral: Core provides Processes, Plans, Runs, validation, scheduling, transactions, persistence, and queries, while future industry modules provide actual Process definitions.

Phase 20 adds no live Process, machine, workstation, block, item, recipe, GUI, automation, or player interaction. It does not convert existing Grinder or Bandsaw gameplay into regional Production.

The framework follows `CONSTITUTION.md`, `CORE_PRINCIPLES.md`, and `PROJECT_RULES.md`. In particular, definitions are immutable, runtime state is separate, time has one authority, economic mutation has one authority, and no subsystem silently repairs invalid persistence.

## System Boundaries

```text
GoodDefinition
    describes what a Good is

GoodTransformation
    describes an economic input/output relationship

ProductionProcessDefinition
    describes one reusable executable operational transformation

ProductionPlanDefinition
    records immutable authoritative intent for one Process and scale

ProductionRunRuntime
    records mutable execution state for exactly one Plan in schema 1

ScheduledSimulationWork
    determines when the Run is eligible to execute

EconomicTransaction
    atomically removes consumed inputs and adds every output

Inventory
    remains authoritative for current Good quantities
```

Orders and Contracts may provide context but do not execute Production. Workforce and Business Runtime are queried as external authorities and are never mutated by Production.

## Package Ownership

The pure domain is `com.butchercraft.world.production`, with scheduler and persistence adapters below that package. It imports no Minecraft, NeoForge, `ItemStack`, wall-clock, GUI, networking, block, or entity APIs.

`ProductionService` is the server lifecycle adapter. It resolves existing world services, loads all Production files, installs `ProductionSimulationWorkHandler`, validates queued Work after scheduler load, saves at shutdown, and clears world-bound state.

## Stable Identities

- `ProductionProcessId`: reusable Process definition.
- `ProductionPlanId`: one authoritative Production intention.
- `ProductionRunId`: one runtime execution. Schema 1 derives `<plan-id>/run`.
- `ProductionLineId`: stable input or output line identity independent of list position.

All use canonical lowercase identifier rules, stable equality, deterministic ordering, and persistence-safe strings. Phase 20 does not need a separate allocation identity because line bindings and transaction changes remain unambiguous.

## Process Schema

`ProductionProcessDefinition` is immutable and contains:

- id and display name;
- owning `IndustryId`;
- required and additional `ActorCapability` values;
- ordered input and output lines;
- optional `GoodTransformation` references;
- `ProductionDuration`;
- whole-batch `ProductionBatchPolicy`;
- typed Workforce and Business Runtime requirements;
- typed execution policy;
- bounded tags and description;
- schema version.

Core contains no built-in Process instances. Registration rejects duplicate ids, unknown industries, unknown Goods, unit mismatches, unresolved or incompatible transformation references, non-executable exact quantities, and unknown Workforce references.

### Input Lines

An input records line id, `GoodId`, exact `GoodQuantity` per batch, unit, role, consumption policy, optional transformation reference, Inventory type constraints, and bounded metadata.

Schema 1 supports:

- `CONSUME_FULL`: require and atomically remove the scaled quantity at completion.
- `REQUIRE_ONLY`: require the scaled quantity but do not remove it.

Input roles are descriptive: primary, secondary, catalyst, packaging, service, and other. Roles do not create hidden execution behavior.

### Output Lines

An output records line id, `GoodId`, exact base quantity per batch, unit, role, `GoodYieldRatio`, optional transformation reference, destination constraints, and bounded metadata.

Roles are primary, secondary, byproduct, waste, returned input, and other. All outputs use one execution path. Yield is deterministic:

```text
base quantity per batch * whole batch count * numerator / denominator
```

The decimal must be exact. Because Inventory schema 1 stores whole `long` quantities, the final scaled value must also convert exactly to whole Inventory units. No rounding, truncation, random yield, or floating point is allowed.

## Plans and Bindings

`ProductionPlanDefinition` records Process, producer Actor, optional Business, whole batch count, input/output bindings, creation and eligibility ticks, optional completion deadline, priority, optional Order/Contract context, metadata, and schema.

Each binding explicitly maps one line to one Inventory and repeats its expected Good and unit. Validation proves:

- every required line is bound exactly once;
- no unknown line is bound;
- Goods and units match;
- Inventory exists and has an allowed type;
- the producer Actor owns the Inventory under schema-1 ownership rules;
- Process, Actor, capabilities, industry, Business, Workforce, Order, and Contract references resolve.

Plan validation does not reserve Inventory, consume inputs, schedule Work, fulfill an Order, or guarantee completion.

Priority maps directly from Production `LOW`, `NORMAL`, `HIGH`, `URGENT`, and `CRITICAL` to the Scheduler priority of the same name. Scheduler stage order always wins over priority.

## Run Lifecycle

Schema 1 uses one Plan to one Run. Definitions remain immutable; only `ProductionRunRuntime` changes.

```text
PLANNED -> READY -> SCHEDULED -> RUNNING
   |         |          |          |
   +---------+----------+----------+-> BLOCKED
                                  +-> PAUSED
                                  +-> AWAITING_TRANSACTION -> COMPLETED

Any nonterminal state -> FAILED | CANCELLED | EXPIRED
BLOCKED | PAUSED -> READY/SCHEDULED/RUNNING after reevaluation
```

Terminal states are irreversible. Ticks never move backward, progress never decreases, one Scheduler Work id cannot bind multiple Runs, and one APPLIED completion transaction cannot complete multiple Runs.

Blocked and paused records carry a typed reason, last evaluated tick, and future reevaluation tick. Scheduler retry counters and Production execution attempts remain distinct.

## Duration and Progress

Duration and progress use authoritative simulation ticks only. Required work units are:

```text
base duration ticks * whole batch count
```

Work units are exact integral Production progress, not real time, wages, electricity, or Scheduler budget units. The first eligible execution starts the Run without granting elapsed work. Later executions add authoritative elapsed simulation ticks, capped by remaining work, and defer the same Scheduler Work to a future tick based on the Process quantum.

This avoids one Work definition per game tick, survives save/reload, preserves pauses, and leaves a deterministic extension point for future explicitly modeled work-rate policies. Phase 20 applies no skill, equipment, utility, or random modifiers.

## Requirement Evaluation

Plan acceptance validates structure. Run scheduling and every handler execution revalidate authoritative state:

- Actor exists, is operational, belongs to the Process industry, and has every capability.
- Business state satisfies required operational, open, shift, maintenance, workforce, and allowed-status rules.
- Workforce definition, positions, certifications, skill levels, and active count satisfy typed requirements.
- input quantities are currently sufficient;
- source and destination Inventory statuses allow their operations;
- completion preflight satisfies aggregate destination capacity.

Execution policy maps temporary loss to `BLOCK`, `PAUSE`, `FAIL`, or `CANCEL`. Transaction rejection uses the narrower `BLOCK` or `FAIL` policy. Standard schema-1 behavior blocks input, Business, and destination loss; pauses Workforce loss; blocks rejected completion transactions; and reevaluates after 20 simulation ticks.

## Scheduler Integration

Production registers one Work type:

```text
butchercraft:production_run
```

It executes in `butchercraft:execution`. The payload contains only `butchercraft:production_run_id`; Process and Plan data remain authoritative in Production. Work also carries stable references to Process, Plan, and Run.

The handler:

1. resolves and validates the Run binding;
2. resolves Plan and Process;
3. reevaluates every authority;
4. checks deterministic Scheduler budget before mutation;
5. starts, resumes, or advances exact progress;
6. defers the same Work to a future tick while incomplete;
7. builds and submits one completion transaction when progress is complete;
8. records `COMPLETED` only after transaction history reports `APPLIED`.

No Production Work recursively schedules itself in the same tick. The handler returns typed completed, deferred, or failed outcomes and never accesses mutable Scheduler internals.

## Transaction Completion

Phase 20 activates `TransactionType.PRODUCTION` through an additive schema-1 `inventory_changes` plan. Legacy transactions keep their original single-Good fields and remain readable when the new array is absent.

The ordered plan includes every metadata-specific input removal and every output addition. `TransactionValidator` rechecks Good, unit, Inventory ownership, underflow, status, and aggregate capacity. `InventoryManager` stages the complete candidate state and commits only after all changes validate. `TransactionExecutor` still requires the previously accepted validation.

Consequences:

- unrelated add/remove transactions cannot partially complete Production;
- failed preflight creates no transaction history entry and no Inventory mutation;
- rejected submitted transactions never complete the Run;
- replay preserves the complete ordered change plan;
- completion transaction metadata identifies Run, Plan, Process, Actor, Scheduler Work, completion tick, and optional Order/Contract context.

Outputs do not automatically fulfill Orders. A future explicit orchestration owner may allocate the APPLIED transaction through `OrderManager`.

## Persistence

World-owned schema-1 state is stored at:

```text
<world>/butchercraft/production_processes.json
<world>/butchercraft/production_plans.json
<world>/butchercraft/production_runs.json
```

Each document has deterministic ordering, canonical exact numbers, stable field names, and its own schema version. Saves write all temporary files before replacing targets in Process, Plan, Run order. Filesystem APIs cannot replace three files as one transaction; therefore load parses all documents, constructs candidates, validates every cross-reference, and publishes no manager unless the complete set is valid.

Missing members of an existing three-file set, malformed JSON, unsupported schema, duplicate ids, unknown authorities, illegal lifecycle state, non-APPLIED completion references, or mismatched Scheduler Work fail visibly. Unknown Runs are never deleted or silently reset. Persistence is refused while a Run is transiently `AWAITING_TRANSACTION`.

## Service Initialization

Startup order is:

1. Simulation Clock, Goods, Actors, Business Runtime, Workforce, Inventory, Transactions, and Orders/Contracts initialize.
2. `ProductionService` loads and validates Processes, Plans, and Runs.
3. Production installs its explicitly constructed handler.
4. `SimulationSchedulerService` loads queued Work with that handler available.
5. Production validates every persisted Scheduler Work reference.
6. Scheduler tick execution begins.

The Scheduler domain does not depend on Production. The world integration layer performs the binding.

## Query Model

Immutable Process registry indexes industry, capability, input Good, output Good, and transformation reference. Immutable Plan registry indexes Process, Actor, Business, Order, Contract, priority, source/destination Inventory, and creation tick. `ProductionManager` indexes Run status, Plan, Scheduler Work, completion transaction, and terminal ticks while using Plan indexes for Process, Actor, Business, Order, and Contract queries.

Public results are immutable and deterministically ordered. No generic query language or mutable registry view exists.

## Example Flows

1. One input/one output: a future module defines A -> B, binds source and destination, schedules one Run, and completes through one transaction.
2. Multi-input/multi-output: A plus B produces C plus byproduct D; all four changes validate and commit together.
3. Missing input: readiness or completion preflight marks the Run blocked; no quantity changes.
4. Business closure: the handler applies the typed Business loss policy and reevaluates later.
5. Workforce shortage: the standard policy pauses progress and preserves accumulated work.
6. Scheduled progress: the same Work defers to future simulation ticks until exact required work is reached.
7. Atomic completion: all consumed inputs and all outputs appear in one APPLIED history record.
8. Transaction rejection: the Run blocks or fails by policy and never becomes completed.
9. Save/reload: Process, Plan, progress, attempt count, Work id, and failure state reload exactly.
10. Cancellation: nonterminal Work is cancelled through Scheduler authority before the Run becomes terminal.
11. Expiration: the authoritative completion deadline terminates the Run without Inventory rollback.
12. Future meat processing: an industry expansion may define fabrication Processes without placing meat rules in Core.
13. Future agriculture: an expansion may define milling or crop processing against the same contracts.
14. Future manufacturing: an expansion may define assembly with components, products, and waste outputs.
15. Future utilities: an expansion may define deterministic utility production after a dedicated utility RFC.

These are illustrative only. Phase 20 registers none of them.

## Invariants

`PF-0001` through `PF-0025` are the subsystem invariants: immutable Process and Plan definitions; runtime-only lifecycle; no direct Inventory mutation; APPLIED transaction completion; exact deterministic quantities; Scheduler-owned eligibility; Clock-owned time; irreversible terminal state; no implicit reservation; external Goods, Actors, Business, Workforce, Scheduler, Transaction, and Order authority; immutable views; fail-visible persistence; and industry neutrality.

## Measured Phase 20 Scale

Measured in the repository JUnit environment on the implementation workstation:

- 100,000 Processes representing 500,000 lines: 1.186 seconds.
- 250,000 Plans with indexed queries: 1.373 seconds.
- 250,000 Runs including 50,000 scheduled, 100,000 blocked, and 100,000 completed references: 5.107 seconds.
- 1,000 eligible Runs under the standard stage cap: exactly 250 executed in 2.718 seconds.
- 10,000 Plan and Run JSON records serialized and reloaded: 1.601 seconds.

These are measured test durations, not production latency guarantees. Gradle startup, NeoForge test bootstrap, JVM warmup, hardware, memory pressure, and other suites affect wall time.

## Current Limitations and Extension Points

- no Inventory reservation or allocation ledger;
- whole batches and deterministic yield only;
- final Inventory values must be whole schema-1 units;
- no variable quality, spoilage, equipment rate, power, fuel, maintenance, or utility consumption;
- no automatic planning, Order fulfillment, logistics, markets, pricing, accounting, AI, or gameplay;
- no Process datapack or public third-party registration lifecycle;
- no Process supersession or schema migration beyond fail-visible version rejection;
- no cross-file filesystem transaction, only complete-set validation before publication;
- no live industry definitions.

Future RFCs may add reservations, explicit work-rate policies, deterministic seeded yield policies, industry content, logistics coordination, or public registration. Those additions must preserve the authority and atomicity boundaries above.
