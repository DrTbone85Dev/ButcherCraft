# Deterministic Simulation Scheduler

Status: implemented in v0.9.0-alpha.1 Phase 19, with IM-009 live effect enforcement

This document defines ButcherCraft's deterministic simulation work scheduler and execution pipeline. `CONSTITUTION.md` remains the governing authority. The scheduler is orchestration infrastructure only: it decides when registered work is eligible, in which stable order it runs, and how much may run in one authoritative simulation tick.

The pure Java domain is `com.butchercraft.world.simulation.scheduler`. `SimulationSchedulerService` is the only Minecraft/NeoForge lifecycle adapter. The earlier `com.butchercraft.world.simulation.SimulationScheduler` remains the focused calendar-event queue owned by the Simulation Clock; Phase 19 does not remove or repurpose it.

IM-006 adds `com.butchercraft.world.simulation.scheduler.checkpoint` for
explicit Scheduler-owned checkpoint snapshot capture and restoration
candidates. It is pure Java and is not registered as a save hook, startup
recovery hook, cadence, command, or gameplay feature.

## Authority And Time

`SimulationClock` owns authoritative simulation time. The scheduler receives a tick and never advances, derives, or substitutes time. Wall-clock time, Java timers, frame rate, Minecraft time-of-day, random UUID generation, and background executors never affect eligibility or outcomes.

The live Scheduler uses a strict sequential policy:

- The next pipeline call must be exactly `last_finalized_simulation_tick + 1`.
- Duplicate and backward ticks are rejected explicitly.
- Gaps are rejected explicitly.
- There is no automatic catch-up or resume protocol.
- Every accepted tick is finalized once, including bounded or failure-policy-stopped ticks.

The live service runs on `ServerTickEvent.Post` after the authoritative clock's listener. It initializes after `OrderContractService`, reads the already-advanced clock value, executes once, and saves on server stop.

## Checkpoint Participation

The Scheduler checkpoint provider wraps Scheduler-owned schema-2 persistence
content plus a Scheduler Configuration Identity as opaque checkpoint payload
bytes. The restorer parses and validates the payload inside the Scheduler
owner boundary, including existing `RUNNING` persistence rejection and
`UNKNOWN_OUTCOME` preservation.

Checkpoint Recovery validates only cross-owner relationships, such as Clock
tick matching Scheduler `last_finalized_simulation_tick`. It does not parse or
mutate Scheduler internals directly.

## Stable Stages

| Order | Stage id | Same-tick enqueue |
| ---: | --- | --- |
| 100 | `butchercraft:tick_preparation` | No |
| 200 | `butchercraft:obligation_evaluation` | Yes |
| 300 | `butchercraft:planning` | Yes |
| 400 | `butchercraft:execution` | Yes |
| 500 | `butchercraft:observation` | Yes |
| 600 | `butchercraft:tick_finalization` | No |

Every `SimulationStageDefinition` has a stable id, display name, explicit order, default `StageFailurePolicy`, same-tick flag, and schema version. Duplicate ids and duplicate order values are invalid. The built-in default policy is `CONTINUE_STAGE`; custom internal stage registries may use `STOP_STAGE`, `STOP_TICK`, or `FAIL_PIPELINE`.

Priority never overrides stage order.

## Work Definition

`ScheduledSimulationWork` is immutable after authoritative submission. It contains:

- stable Work, type, and stage ids;
- scheduled simulation tick;
- stable priority;
- immutable origin and bounded payload;
- retry policy and maximum attempts;
- manager-assigned authoritative submission sequence;
- optional expiration and typed references;
- generation depth and schema version.

`WorkOrigin` records the source subsystem, optional source reference, submission tick, authority, correlation id, and parent Work id. Origin is diagnostic and does not grant authority.

Only `SimulationSchedulerManager` assigns submission sequences. They begin at zero, increase monotonically, persist across reload, are never reused, and reject overflow. Batch registration validates every request before assigning or committing any sequence.

## Payload Contract

`WorkPayload` is a sorted immutable list of unique typed entries, never `Map<String, Object>` or Java object serialization. Supported scalar types are:

- `string`
- `long`
- `boolean`
- exact canonical `decimal`
- canonical namespaced `identifier`

Payloads allow at most 64 entries and 8,192 combined key/value characters; one value may not exceed 2,048 characters. Decimals permit at most 38 digits of precision and 9 fractional digits. Unknown types, duplicate keys, malformed values, and over-limit payloads fail visibly.

## Runtime Lifecycle

Mutable lifecycle state belongs only to `SimulationWorkRuntime`, which the manager owns and exposes as defensive snapshots.

```text
SCHEDULED -> ELIGIBLE -> RUNNING -> COMPLETED
    |           |          |----> RETRY_WAIT -> ELIGIBLE
    |           |          |----> DEFERRED   -> ELIGIBLE
    |           |          |----> FAILED
    |           |          `----> UNKNOWN_OUTCOME
    |           |----> DEFERRED / FAILED
    |           |----> CANCELLED / EXPIRED
    `---------------> CANCELLED / EXPIRED
```

`COMPLETED`, `FAILED`, `CANCELLED`, `EXPIRED`, and `UNKNOWN_OUTCOME` are irreversible. Attempts and runtime revisions are monotonic. Cancellation requires a nonterminal, non-running record and a non-backward authoritative tick. Work expires only after its expiration tick, allowing execution on the expiration tick itself.

Persisting `RUNNING` work is forbidden. Save refuses it, and load rejects it. Schema 2 does not silently rerun or complete interrupted consequential work. Schema 1 records load as legacy records without fabricated Invocation Identity, Effect Identity, or owner result evidence.

## Handlers

`SimulationWorkHandlerRegistry` is runtime-only, immutable, deterministic, and permits one handler for each `SimulationWorkTypeId`. Handlers declare an effect contract:

- `READ_ONLY`
- `IDEMPOTENT`
- `TRANSACTION_BACKED`
- `NON_REPEATABLE`

A handler validates its payload, receives an immutable `SimulationExecutionContext`, performs bounded work through its explicitly supplied dependencies, and returns a typed `SimulationWorkResult`. It cannot receive mutable scheduler internals, control the clock, or mutate scheduler registries.

Persisted Work whose type has no registered handler rejects the whole scheduler load. It is never discarded. Phase 20 installs the internal `butchercraft:production_run` handler before scheduler loading. Phase 21 also installs `butchercraft:economic_planning_cycle` and ensures one persistent continuation Work exists after Planning loads. No public handler registration lifecycle is established.

The scheduler cannot roll back arbitrary external side effects. Handlers must validate before mutation, and economic quantity mutations must continue through the Transaction Framework. Unexpected read-only handler exceptions become explicit failed Work records and reports. Unexpected consequential handler exceptions after invocation begins become `UNKNOWN_OUTCOME`.

## Live Effect Enforcement

IM-009 turns handler effect declarations into live Scheduler-owned policy. Every bounded handler attempt receives deterministic `SchedulerInvocationIdentity` that binds Work id, handler type, attempt number, authoritative tick, canonical payload digest, and effect-policy identity.

Consequential handlers receive a stable `SchedulerEffectIdentity` for the logical effect. Retries may have different Invocation Identity while referring to the same Effect Identity. `READ_ONLY` handlers do not receive Effect Identity.

| Effect type | Completion | Retry | Generated Work | Exception after invocation |
| --- | --- | --- | --- | --- |
| `READ_ONLY` | Handler result is sufficient. | Allowed by deterministic retry policy. | Allowed within Scheduler generation rules. | Fails with `HANDLER_EXCEPTION`. |
| `IDEMPOTENT` | Requires compatible owner result observation. | Allowed only with compatible identity/result evidence. | Requires owner result observation. | `UNKNOWN_OUTCOME`. |
| `TRANSACTION_BACKED` | Requires authoritative Transaction result evidence. | Requires compatible owner result evidence. | Not allowed by default. | `UNKNOWN_OUTCOME`. |
| `NON_REPEATABLE` | Requires owner result evidence unless explicitly gated. | Not automatically retried. | Not allowed. | `UNKNOWN_OUTCOME`. |

`UNKNOWN_OUTCOME` means the platform cannot prove whether a consequential effect occurred. It is a Scheduler runtime state, not storage artifact quarantine. It is terminal for automatic execution, persists in Scheduler state, survives Clock/Scheduler checkpoint round trip, and requires future owner/operator reconciliation outside IM-009.

Scheduler observes `SchedulerEffectObservation` records published by handlers. The observation carries Effect Identity, effect type, owner subsystem id, owner-result identity, and owner-published content digest. The same Effect Identity with the same canonical content may be observed safely. The same Effect Identity with different content fails explicitly as a conflict. Scheduler does not infer Transaction success from Transaction id, absence of failure, timeout, or missing Work.

`SimulationSchedulerManager` owns the world-scoped Scheduler Runtime Authority. Recursive, nested, or parallel pipeline execution for the same manager is rejected with `SCHEDULER_AUTHORITY_ALREADY_EXECUTING`.

## Deterministic Ordering

Eligible Work is ordered by:

1. stage execution order ascending;
2. scheduled tick ascending;
3. priority descending (`critical`, `urgent`, `high`, `normal`, `low`);
4. authoritative submission sequence ascending;
5. Work id ascending as the final deterministic tie-break.

Registry insertion order, runtime indexes, save/reload, and equivalent fixture construction do not change this execution order.

## Execution Budgets

`SimulationExecutionBudget` requires positive limits for:

- Work items per tick;
- Work items per stage;
- handler work units per tick;
- generated Work per tick;
- same-tick generated Work per tick;
- retry transitions per tick;
- generation depth.

The standard budget is 1,000 items per tick, 250 per stage, 10,000 handler units, 250 generated items, 100 same-tick submissions, 100 retry transitions, and depth 8. No elapsed-time budget changes deterministic outcomes.

When a budget is exhausted, unstarted Work remains indexed in `SCHEDULED` or `ELIGIBLE` state. It is not dropped and may continue on the next sequential tick.

## Results, Retry, And Failure

Handlers return `COMPLETED`, `DEFERRED`, `RETRY`, or `FAILED` with an execution tick, deterministic work-unit count, diagnostics, optional next tick, optional result payload, and optional generated requests.

Retry policies are `NEVER`, `NEXT_TICK`, `FIXED_INTERVAL`, `EXPONENTIAL_SIMULATION_INTERVAL`, and `HANDLER_REQUESTED`. All calculations use exact simulation ticks, enforce maximum attempts and optional maximum retry tick, and reject overflow. There is no jitter or implicit randomness.

Invalid ticks, payloads, results, generated batches, retries, and budgets use typed `WorkFailureCode` values. Effect-policy failures include missing Invocation Identity, missing required Effect Identity, conflicting effect content, missing authoritative result, illegal retry, illegal generated Work, active cancellation unsupported, and automatic retry blocked by Unknown Outcome. Transient tick and stage reports summarize all attempted Work without becoming authoritative persisted state.

## Same-Tick Generation

Generated requests are validated and committed as one atomic scheduler batch. If any request is invalid or duplicated, no generated request is registered and the parent fails explicitly.

A generated request scheduled for the current tick may run that tick only when:

- it targets a later stage;
- that stage has not started; and
- the target stage allows same-tick enqueue.

Requests targeting the current stage, a completed stage, or a stage that disallows same-tick enqueue move deterministically to `current tick + 1`. Generation count, same-tick count, and depth limits prevent recursive infinite work.

## Queries

The manager provides immutable deterministic queries by Work id, type, stage, status, scheduled range, due tick, eligible tick, origin subsystem, correlation id, and typed reference. Due and status indexes avoid a required full-registry scan during normal tick promotion. Query results never expose mutable runtime records.

## Persistence

Scheduler state persists independently at:

```text
<world>/butchercraft/simulation_scheduler.json
```

Schema version 2 stores:

- stage definitions;
- immutable Work definitions;
- exactly one runtime record per Work;
- last Invocation Identity, where an attempt exists;
- last Effect Identity and effect policy identity for consequential attempts;
- observed owner result identity and content digest, when the owner published a result;
- next authoritative submission sequence;
- last finalized simulation tick.

JSON uses stable snake-case fields, deterministic ordering, UTF-8, pretty printing, a temporary sibling file, and atomic replacement where supported. Load validates schemas, built-in stages, duplicate ids/sequences, definition/runtime pairing, lifecycle state, payloads, stages, handlers, and tick/sequence consistency before publishing a manager.

## Execution Flows

### Successful Work

```text
Clock tick -> promote due Work -> stage-order query -> validate handler
           -> ELIGIBLE -> RUNNING -> COMPLETED -> transient report -> finalize tick
```

### Retry

```text
RUNNING -> handler RETRY -> validate attempts/policy/budget
        -> RETRY_WAIT(next simulation tick) -> later ELIGIBLE -> RUNNING
```

### Budget Exhaustion

```text
eligible ordered Work -> execute bounded prefix -> limit reached
                      -> retain untouched remainder -> finalize tick with BUDGET_EXHAUSTED
```

### Same-Tick Later-Stage Generation

```text
PLANNING parent -> atomic generated batch for EXECUTION at tick N
                -> promote bounded batch -> EXECUTION child runs at tick N
```

### Handler Failure

```text
RUNNING -> typed FAILED or read-only caught exception -> persisted FAILED runtime
        -> apply stage failure policy -> continue/stop stage/stop tick/fail pipeline
```

### Unknown Consequential Outcome

```text
RUNNING consequential Work -> missing proof or exception after invocation begins
                            -> UNKNOWN_OUTCOME
                            -> no automatic retry or reinvocation
```

### Save And Reload

```text
server stop -> reject any RUNNING state -> write temporary JSON -> atomic replace
server start -> load all records -> validate handlers and invariants -> publish manager
```

### Current And Descriptive Flows

Production Run execution and the Economic Planning Cycle are the current live internal handlers. The remaining examples stay descriptive:

```text
Future Contract service -> OBLIGATION_EVALUATION Work -> future Order decision
Economic Planning Engine -> PLANNING Work -> bounded typed Production Plan submission
Production Run handler -> EXECUTION Work -> Production progress or atomic Transaction completion
Future Logistics service -> EXECUTION Work -> future shipment progress fact
Future Market observer -> OBSERVATION Work -> future immutable market observation
```

The Planning and Production handlers retain these boundaries: Scheduler owns eligibility, Planning owns decisions, Production owns Plan/Run state, Clock owns time, Transaction owns quantity mutation, Orders own fulfillment, and Inventory owns quantities. Future handlers must preserve the same focused ownership discipline.

## Scheduler Invariants

- **SS-0001:** The scheduler never owns or advances authoritative simulation time.
- **SS-0002:** Each Work definition is immutable after authoritative submission.
- **SS-0003:** Mutable lifecycle exists only in Work runtime state.
- **SS-0004:** Work ordering is deterministic.
- **SS-0005:** Authoritative submission sequences are monotonic and never reused.
- **SS-0006:** A simulation tick executes at most once without an explicit resume protocol.
- **SS-0007:** Simulation ticks never move backward.
- **SS-0008:** Stage ordering is stable and explicit.
- **SS-0009:** Priority never overrides stage ordering.
- **SS-0010:** Terminal Work states are irreversible.
- **SS-0011:** Budget exhaustion never silently discards eligible Work.
- **SS-0012:** Same-tick generated Work is bounded.
- **SS-0013:** Unknown persisted Work types never disappear silently.
- **SS-0014:** Handler failures produce explicit outcomes.
- **SS-0015:** The scheduler performs no economic domain behavior.
- **SS-0016:** The scheduler never bypasses domain validation or mutation authority.
- **SS-0017:** Runtime query results are immutable snapshots.
- **SS-0018:** Pipeline execution is world-scoped and non-reentrant.
- **SS-0019:** Wall-clock time never determines simulation outcomes.
- **SS-0020:** Persisted scheduler state is schema-versioned and deterministic.
- **SS-0021:** Every live handler attempt has deterministic Invocation Identity.
- **SS-0022:** Consequential effects use stable Effect Identity where required.
- **SS-0023:** Scheduler observes owner results but never owns domain facts.
- **SS-0024:** Unknown consequential outcomes are never automatically retried.

## Measured Phase 19 Scale

The focused Java 21/NeoForge JUnit run used a 512 MB test worker. Measurements are observations from one development run, not release guarantees:

- 1,000,000 definition and 1,000,000 runtime constructions: 685 ms.
- 100,000 retained due items: 307 ms registration, 55 ms due query.
- Bounded 250-item pipeline prefix from that set: 37 ms.
- 100,000 retry-wait plus 100,000 terminal runtime constructions: 167 ms.
- 10,000-record, 13,138,257-character JSON: 356 ms serialization, 355 ms reload.

The million-record definition/runtime scale is intentionally split from combined persistence to stay within the test worker's memory limit.

## Known Limitations And Extension Points

- Schema 2 has no catch-up, partial-tick resume, broad world migration, startup crash recovery, or automatic reconciliation with a mismatched clock.
- The live registry contains the internal Production Run, Economic Planning Cycle, and generic Execution Operation handlers. Planning ensures one cadence-gated continuation Work and may submit Production Work for accepted open Order-line Needs, but no concrete Execution domain handler, live industry Process, Allocation handoff, or gameplay execution is registered.
- Planning remains classified as `NON_REPEATABLE` with a cadence-gated continuation policy. IM-010 implements live Planning cadence, but `IDEMPOTENT` Planning classification remains blocked until Scheduler can represent cycle-scoped Effect Identity.
- Reports are transient and no profiling/audit UI exists.
- The scheduler cannot provide global rollback for side effects performed outside transaction-backed handlers.
- Handler registration is internal and not a stable public API.
- Future handlers may bind explicit focused dependencies, but the execution context must not become a service locator.
- Future persistence migration must preserve unknown identifiers where safe or fail visibly when authoritative Work cannot be interpreted.
