# Proposed ADR: Scheduler Handler Effects And Execution Authority

Status: PROPOSED - OWNER APPROVAL REQUIRED

Decision identifier: Unassigned

Package: BCSE Architecture Hardening AH-1

Authority: This document has no authority until explicitly approved by the
project owner and recorded through the repository's accepted Decision process.

## Context

DEC-0072 assigns one deterministic pipeline responsibility for Scheduler Work.
Handlers declare one of four current `HandlerEffectType` values:

- `READ_ONLY`;
- `IDEMPOTENT`;
- `TRANSACTION_BACKED`; and
- `NON_REPEATABLE`.

The current handler registry validates that an effect type is non-null.
Scheduler execution does not otherwise change validation, retry, failure,
publication, or recovery behavior based on that declaration.

`SimulationPipeline` uses an instance-local reentrancy guard.
`AllocationCycleExecutor` independently uses an instance-local execution
guard. The live composition currently constructs one effective path, but the
authority invariant is not encoded at the shared manager/service boundary.

This proposal responds to
[BCSE-AUDIT-004](../BCSE_ARCHITECTURE_AUDIT.md#bcse-audit-004-scheduler-effect-types-are-descriptive-not-enforced)
and
[BCSE-AUDIT-006](../BCSE_ARCHITECTURE_AUDIT.md#bcse-audit-006-reentrancy-guards-are-scoped-to-executor-instances).

## Problem

An effect classification must have enforceable meaning. The Scheduler must
know:

- whether automatic reinvocation is safe;
- whether mutation requires Transaction evidence;
- whether an exception can leave an unknown external effect;
- whether deferral or generated Work is legal;
- what identity makes an effect idempotent;
- what evidence must publish; and
- how crash recovery treats an invocation.

BCSE also needs one explicit execution authority per world. Multiple pipeline
objects must not bypass a per-instance guard and target the same authoritative
manager concurrently.

## Current Behavior

- Effect type is required but not policy-enforced.
- Handler invocation occurs before result validation.
- Handler exception becomes typed Scheduler failure.
- Scheduler does not roll back arbitrary external side effects.
- Production declares `TRANSACTION_BACKED`.
- Economic Planning declares `NON_REPEATABLE` and returns `DEFERRED` each tick.
- `SimulationPipeline` and `AllocationCycleExecutor` own separate instance
  guards.
- Scheduler schema 1 is sequential and package-internal.
- No public third-party handler registration lifecycle exists.

This proposal does not reclassify handlers, restrict constructors, or change
Scheduler/Allocation behavior.

## Architectural Constraints

The proposal is governed by:

- `AI-0001` Deterministic Simulation;
- `AI-0006` Universal Economic Transactions;
- `AI-0016` Explicit Responsibility Boundaries;
- `AI-0017` Validation Before Execution;
- `AI-0021` Explicit Failure Outcomes;
- `AI-0022` Authoritative Simulation Time;
- `AI-0025` Singular Data Ownership;
- `AI-0026` Bounded Simulation Work;
- `AI-0027` Tests Are Part Of The Contract; and
- `AI-0028` Backward-Compatible Evolution.

Additional constraints:

- Scheduler owns eligibility and Work lifecycle, not domain mutation;
- effect policy cannot transfer domain ownership to Scheduler;
- no handler receives mutable Scheduler internals;
- schema 1 remains single-threaded and sequential;
- no automatic retry occurs after an unknown non-repeatable effect;
- all effect and invocation identities are deterministic;
- Allocation authority remains separate from Scheduler authority; and
- implementation requires separate owner authorization.

## Options Considered

### Option 1: Keep Effect Types Descriptive

Retain current labels without changing registration, retry, publication, or
recovery behavior.

This preserves compatibility but leaves callers unable to rely on the labels
as runtime guarantees.

### Option 2: Permit Only Read-Only Handlers

Require handlers to calculate results without any owner-side effect.

This is simple and retry-safe, but it prevents Scheduler-driven Production,
Planning publication, and future Execution.

### Option 3: Require Every Effect To Be Transaction-Backed

Route every handler-side state change through the Transaction Framework.

This would provide one mutation boundary but would transfer non-economic owner
runtime into Transactions and violate singular ownership.

### Option 4: Enforce Type-Specific Policy And Owner-Level Authority

Define permitted effects, retries, evidence, and recovery for each existing
effect type. Move reentrancy protection to one authoritative world-scoped
owner while keeping Scheduler and Allocation authorities separate.

This adds policy and migration complexity but preserves existing owner
boundaries and makes each label testable.

## Decision Proposed

Adopt **Option 4: enforce type-specific policy and owner-level execution
authority**.

The proposed semantics below do not become active until owner approval and a
separately authorized implementation milestone.

## Effect Identity

Every handler invocation has a deterministic `HandlerInvocationId` derived
from:

- world/checkpoint lineage identity;
- Scheduler Work id;
- Work submission sequence;
- attempt number;
- authoritative simulation tick;
- handler type id;
- canonical payload digest; and
- schema version.

Every external effect has an `EffectId` derived from the invocation id and a
handler-declared effect ordinal/type. No UUID, wall clock, or random value is
used.

The exact Java names are implementation details after approval.

## Proposed Effect Semantics

### `READ_ONLY`

Permitted:

- read immutable context and owner snapshots;
- compute an immutable result;
- request generated Scheduler Work through the returned result;
- return `COMPLETED`, `DEFERRED`, `RETRY`, or `FAILED`; and
- emit immutable diagnostic/evidence payloads.

Prohibited:

- mutate authoritative domain state;
- submit a Transaction;
- publish owner runtime;
- write files or external state; and
- perform non-deterministic I/O.

Retry:

- Scheduler may automatically reinvoke under the Work retry policy;
- exceptions may be retryable only when stage policy permits; and
- each invocation has a new attempt identity while input equivalence remains
  testable.

Required evidence:

- invocation id;
- input digest;
- result digest;
- work units;
- outcome; and
- generated Work digest.

Crash recovery:

- no external effect exists;
- rollback to a checkpoint safely permits later reinvocation.

### `IDEMPOTENT`

Permitted:

- one or more owner-authorized side effects that are guaranteed to converge to
  the same state when repeated with the same `EffectId`;
- generated Work after the owner confirms effect outcome;
- `COMPLETED`, `DEFERRED`, `RETRY`, or `FAILED` when registration policy
  permits each outcome.

Required contract:

- handler registration names the owner adapter and idempotency mechanism;
- the owner stores or recognizes `EffectId`;
- same `EffectId` and same content returns existing outcome;
- same `EffectId` and different content fails explicitly;
- duplicate invocation cannot repeat quantity, payment, or lifecycle change;
  and
- effect result is immutable and digest-bound.

Retry:

- automatic retry is allowed only with the identical `EffectId` and content;
- an unknown outcome is queried by `EffectId` before reinvocation; and
- exception retry follows registration and stage policy.

Crash recovery:

- checkpoint rollback may reinvoke only through the same idempotency identity;
- owner evidence resolves whether the effect already published.

### `TRANSACTION_BACKED`

Permitted:

- propose economic mutation through the Transaction Framework;
- observe authoritative Transaction result evidence;
- publish owner runtime only after the required Transaction result is known;
- request generated Work after result validation; and
- defer bounded work when the current invocation's effect state is known.

Required contract:

- every consequential economic mutation uses Transactions;
- proposal and result are bound under the proposed Transaction-validation ADR;
- no direct Inventory mutation exists;
- invocation evidence records exact proposal digest and Transaction result;
- duplicate submission uses Transaction idempotency rules; and
- owner runtime publication is validated against authoritative Transaction
  evidence.

Retry:

- no retry while Transaction outcome is unknown;
- a rejected proposal can retry only under explicit Work policy with a new
  deterministic attempt and either the same proposal identity or an explicitly
  new Transaction identity;
- an APPLIED proposal is never resubmitted for mutation;
- duplicate-safe result lookup precedes reinvocation after recovery.

Crash recovery:

- Transaction/Inventory and handler owner state must be in one committed
  checkpoint generation;
- if Transaction APPLIED but owner/Scheduler evidence publication is
  incomplete, recovery observes the authoritative Transaction result and
  completes reconciliation without reapplying it.

### `NON_REPEATABLE`

Permitted:

- an owner-specific effect that cannot be made read-only, idempotent, or
  Transaction-backed and whose duplicate would be unsafe.

Registration requirements:

- explicit owner approval for the handler type;
- reason other effect types are insufficient;
- durable invocation-start and effect-outcome evidence;
- no automatic retry policy;
- no exception retry;
- no same-attempt reinvocation;
- explicit operator recovery path for unknown outcome; and
- checkpoint/evidence participation.

Retry and deferral:

- Scheduler never returns a `NON_REPEATABLE` invocation to automatic `RETRY`;
- `DEFERRED` is legal only when the current invocation's effect outcome and
  continuation state are durably known and the next invocation receives a
  distinct deterministic invocation identity;
- generated Work is legal only after current outcome evidence is validated;
- an exception after invocation begins produces
  `NON_REPEATABLE_OUTCOME_UNKNOWN` unless owner evidence proves no effect.

Crash recovery:

- a recovered unknown invocation is quarantined and cannot run automatically;
- operator or owner-specific deterministic reconciliation must classify it as
  not applied, applied, or irrecoverably conflicting;
- no inference from absence of Scheduler completion evidence alone.

`NON_REPEATABLE` is an exceptional classification, not the default for any
mutable handler.

## Effect Matrix

| Rule | READ_ONLY | IDEMPOTENT | TRANSACTION_BACKED | NON_REPEATABLE |
|---|---|---|---|---|
| Authoritative domain side effect | No | Owner-idempotent only | Through Transaction plus owner publication | Exceptional owner effect |
| Automatic retry | Allowed by policy | Allowed with same EffectId | Only after known Transaction outcome | Prohibited |
| Exception retry | Allowed by stage policy | Only after effect lookup | Only after Transaction/result lookup | Prohibited |
| `DEFERRED` | Allowed | Allowed with known effect state | Allowed with known Transaction state | Conditional durable continuation only |
| Generated Work | Allowed in validated result | After effect outcome | After Transaction/result outcome | After durable outcome only |
| Transaction required | No | No, unless economic mutation | Yes for economic mutation | No, but cannot bypass Transaction rules |
| Duplicate invocation | No external effect | Same result by EffectId | Same result by Transaction/effect identity | Quarantine/conflict |
| Persistence requirement | Scheduler evidence | Owner effect evidence | Checkpoint-correlated Transaction and owner evidence | Durable start/outcome/recovery evidence |
| Unknown crash outcome | Safe to reinvoke | Resolve by EffectId | Resolve by Transaction/effect evidence | Never reinvoke automatically |

## Registration Validation

Each handler registration supplies an immutable `HandlerEffectPolicy`
containing:

- handler type id;
- effect type;
- owner subsystem id;
- allowed outcomes;
- retry permission;
- exception retry permission;
- deferral permission;
- generated-Work permission;
- idempotency/effect identity contract, when required;
- Transaction result requirement, when required;
- persistence/evidence requirement;
- maximum declared work units;
- schema version; and
- architecture Decision reference for `NON_REPEATABLE`.

Registration rejects:

- duplicate handler type;
- missing owner;
- retry-enabled `NON_REPEATABLE`;
- `TRANSACTION_BACKED` without Transaction evidence contract;
- `IDEMPOTENT` without EffectId contract;
- generated Work after an unknown effect;
- policy incompatible with stage failure rules; and
- unknown effect schema.

The Architecture Manifest declares the policy for every built-in handler.
Architecture Validation checks the declaration. Runtime registration checks
the candidate handler against it.

## Simulation Execution Authority

### One Authority Per World

Exactly one authoritative Scheduler execution authority exists per loaded
world. It owns:

- `SimulationSchedulerManager`;
- `SimulationPipeline`;
- handler registry snapshot;
- global execution/reentrancy guard;
- current invocation identity;
- current stage/tick;
- checkpoint quiescence participation; and
- execution diagnostics.

The authority is created and retained by the world lifecycle composition
service. Other modules receive submission/query contracts, not a pipeline
constructor or execute method.

### Reentrancy And Parallelism

Schema 1 rules:

- one pipeline invocation at a time per world;
- no recursive Scheduler execution;
- no second pipeline targeting the same manager;
- no parallel handler execution;
- no handler invokes the pipeline;
- no handler advances the Clock;
- no module constructs another authoritative orchestrator; and
- a second invocation fails before any Work transition or handler call.

The guard belongs to the authority object, not one pipeline instance.

### Constructor/API Direction

After implementation authorization:

- authoritative pipeline construction becomes package-private, factory-owned,
  or otherwise restricted;
- public contracts expose immutable query and constrained submission only;
- tests use explicit test-authority factories;
- third-party handler registration remains closed until a separate public API
  decision; and
- no static mutable global authority is introduced.

## Allocation Execution Authority

Allocation remains a separate owner and does not merge into Scheduler.

Exactly one `AllocationExecutionAuthority` per world owns:

- `AllocationRuntimeService`;
- `AllocationCycleExecutor`;
- provider observation snapshot used for the cycle;
- global Allocation Cycle reentrancy guard;
- current cycle identity; and
- checkpoint quiescence participation.

Schema 1 rules:

- one Allocation Cycle at a time per world;
- no recursive Allocation Cycle;
- no second executor against the same runtime authority;
- provider callbacks never invoke the cycle;
- Scheduler stage 350 may request one bounded cycle through the authority after
  M22E-M22F approval; and
- Scheduler does not acquire Allocation mutation ownership.

## Current Handler Compatibility Review

### Production

Current Production declares `TRANSACTION_BACKED`. The proposed semantics align
with DEC-0073 but require:

- exact Transaction proposal/result binding;
- checkpoint-correlated Production/Scheduler/Transaction evidence; and
- registration policy validation.

### Economic Planning

Current Planning declares `NON_REPEATABLE` and returns `DEFERRED` every tick.
It does not yet meet the proposed durable continuation contract.

Before this ADR can be implemented, owner review must choose one migration:

1. classify each Planning Cycle publication as `IDEMPOTENT` using stable cycle
   identity and identical replay result;
2. retain `NON_REPEATABLE` and add durable invocation/outcome/continuation
   evidence under coordinated checkpointing; or
3. introduce a narrower accepted effect category through a separate amendment.

This proposal recommends **Option 1** after the proposed Planning cadence makes
each cycle a distinct stable invocation. Same cycle identity with identical
input must return existing published result; different input must conflict.
No reclassification occurs in this task.

## Rationale

The four labels are useful only when they constrain runtime behavior. The
proposed matrix permits safe automation for read-only, idempotent, and
Transaction-backed work while treating unknown non-repeatable effects as an
operator-visible integrity condition.

Moving the guard to the authority object turns the current composition
assumption into a testable invariant without merging Scheduler and Allocation.

## Consequences

### Positive Consequences

- Effect declarations become enforceable contracts.
- Retry and crash behavior are explicit.
- Unknown non-repeatable outcomes cannot duplicate effects.
- Transaction-backed handlers align with exact Transaction evidence.
- One world cannot accidentally run two pipelines against one manager.
- Allocation preserves separate ownership with equivalent authority safety.
- Future Execution has a defined Scheduler invocation boundary.

### Negative Consequences

- Handler registration gains policy metadata and validation.
- Current Planning classification needs migration.
- Some exceptions that currently become ordinary failure will require
  quarantine/reconciliation.
- Constructors and tests need authority factories.
- Durable effect evidence depends on checkpoint implementation.
- Third-party handler registration remains blocked.

## Compatibility

The proposal strengthens DEC-0072's declared side-effect contracts and
single-pipeline intent. It requires a new accepted Decision before behavior or
API visibility changes.

Existing Work ids, stage ids, and persisted Work definitions remain stable.
Handler effect policy becomes additive schema data. Incompatible persisted
Work fails migration visibly.

## Migration

Migration must:

1. register immutable effect policy for each existing handler;
2. verify current Production Work against `TRANSACTION_BACKED`;
3. resolve Planning classification through owner approval;
4. persist effect policy schema/digest in the coordinated checkpoint;
5. migrate Work attempt/effect identity state;
6. ensure one authority owns each loaded manager;
7. reject persisted `RUNNING` or unknown-outcome Work under existing
   fail-visible rules;
8. create no synthetic proof that a prior effect did or did not occur; and
9. leave old Scheduler state unchanged if policy migration fails.

## Failure Behavior

Proposed explicit outcomes:

- `HANDLER_EFFECT_POLICY_MISSING`;
- `HANDLER_EFFECT_POLICY_MISMATCH`;
- `HANDLER_EFFECT_IDENTITY_CONFLICT`;
- `HANDLER_EFFECT_OUTCOME_UNKNOWN`;
- `HANDLER_RETRY_NOT_PERMITTED`;
- `HANDLER_DEFERRAL_NOT_PERMITTED`;
- `HANDLER_GENERATED_WORK_NOT_PERMITTED`;
- `HANDLER_TRANSACTION_EVIDENCE_MISSING`;
- `HANDLER_IDEMPOTENCY_EVIDENCE_MISSING`;
- `NON_REPEATABLE_OUTCOME_UNKNOWN`;
- `SCHEDULER_AUTHORITY_ALREADY_EXECUTING`;
- `SCHEDULER_AUTHORITY_MISMATCH`;
- `ALLOCATION_AUTHORITY_ALREADY_EXECUTING`; and
- `ALLOCATION_AUTHORITY_MISMATCH`.

Failure-code names are proposed contract names.

No failure after an unknown external effect permits automatic reinvocation.

## Replay Implications

Replay includes handler effect policy, invocation ids, input/result digests,
Transaction observations, and owner effect evidence. Replay may simulate
effects through isolated deterministic owner adapters; it does not invoke
external non-repeatable effects.

An idempotent replay proves the same effect identity and outcome. A
Transaction-backed replay proves the same proposal/result binding. A
non-repeatable replay consumes recorded outcome evidence only.

## Security And Integrity Implications

- Modules cannot acquire pipeline execution authority through public
  constructors.
- A handler cannot claim `READ_ONLY` while registering mutation permissions.
- Transaction-backed mutation cannot bypass Transaction evidence.
- Effect identity prevents duplicate replay/submission.
- Clients cannot select effect policy or trigger retries.
- Unknown outcome quarantine prevents duplicate consequential effects.

## Testing Requirements

Required automated tests:

- registration validation for every effect type;
- every prohibited policy combination;
- READ_ONLY retry and duplicate invocation;
- IDEMPOTENT same-EffectId replay;
- same-EffectId/different-content conflict;
- TRANSACTION_BACKED rejection without exact result evidence;
- applied Transaction duplicate-safe recovery;
- NON_REPEATABLE automatic retry rejection;
- NON_REPEATABLE deferral with and without durable continuation evidence;
- handler exception before and after effect publication;
- generated Work permission and ordering;
- effect policy persistence/migration;
- exactly one Scheduler authority per world;
- two pipeline instances targeting one manager rejected;
- recursive pipeline call rejected globally;
- schema-1 parallel execution rejected;
- exactly one Allocation authority per world;
- two Allocation executors targeting one runtime rejected;
- provider recursion into Allocation rejected;
- checkpoint quiescence during both authorities;
- current Production compatibility;
- Planning classification migration; and
- deterministic replay for all effect categories.

## Alternatives Rejected By This Proposal

- **Keep effect types descriptive only:** rejected because callers can infer
  guarantees the runtime does not enforce.
- **Treat every handler as idempotent:** rejected because idempotency requires
  owner-recognized identity and evidence.
- **Require every handler to use Transactions:** rejected because read-only and
  non-economic owner runtime do not belong to Transactions.
- **Allow per-instance guards:** rejected because multiple instances can target
  one authority.
- **Merge Scheduler and Allocation execution ownership:** rejected because they
  own different lifecycle and mutation responsibilities.

## Unresolved Questions

Owner decisions required:

1. Approve the effect matrix.
2. Approve the proposed Planning migration to `IDEMPOTENT`, or select another
   listed migration.
3. Confirm whether any schema-1 `NON_REPEATABLE` handler is permitted.
4. Confirm whether `READ_ONLY` handlers may produce owner-neutral external
   telemetry outside deterministic evidence.
5. Define the exact retry response to a Transaction timeout with no observed
   result.
6. Confirm constructor/API restriction approach.
7. Confirm whether future public handler registration requires a signed
   manifest or only explicit server startup registration.

## Owner Approval Checklist

- [ ] Approve semantics for all four effect types.
- [ ] Approve invocation and effect identities.
- [ ] Approve registration policy validation.
- [ ] Approve retry, deferral, generated Work, and exception rules.
- [ ] Approve one Scheduler authority per world.
- [ ] Approve one separate Allocation authority per world.
- [ ] Approve schema-1 no-parallel/no-recursion rules.
- [ ] Resolve current Planning handler classification.
- [ ] Approve checkpoint, evidence, and Transaction dependencies.
- [ ] Approve migration and tests.
- [ ] Authorize creation of an accepted Decision record.
- [ ] Separately authorize implementation.
