# BCSE Architecture Hardening Review

Status: HISTORICAL REVIEW PACKAGE - RATIFIED DIRECTIONS RECORDED IN AH-1 ADRS

Package: AH-1 Platform Lifecycle And Boundary Contracts

Review date: 2026-07-24

Authority: This package is review evidence. Ratified AH-1 direction is recorded
in the five AH-1 ADRs and the
[`Platform Canonicalization Addendum`](adr/ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md).
This document does not revise an RFC, authorize implementation, authorize
migration, or change current behavior.

## Executive Summary

The independent
[BCSE Architecture Audit](BCSE_ARCHITECTURE_AUDIT.md) found a disciplined
deterministic core with two critical world-lifecycle gaps and several boundary
contracts that must be settled before Allocation and generic Execution become
live.

This AH-1 package converts those findings into seven proposed Architecture
Decision Records:

1. bounded deterministic Planning cadence;
2. platform-wide evidence lifecycle;
3. coordinated checkpoint and crash recovery;
4. exact Transaction validation authority;
5. enforceable Scheduler handler effects and one Scheduler Runtime Authority;
6. RFC-0023 Draft 1 reconciliation; and
7. M22E-M22F Allocation integration sequencing.

The package recommends:

- hybrid Planning every 1,200 simulation ticks by default, with deterministic
  relevant-change triggers and a 20-tick minimum separation;
- three-checkpoint hot replay evidence plus immutable 100,000-tick cold
  archive partitions;
- a 6,000-tick coordinated directory-per-generation checkpoint with dual
  commit slots and deterministic rollback to the last valid generation;
- Transaction validation bound to proposal digest, Inventory Freshness
  Identity, validation-plan digest, and Transaction-owned single-use Validation
  Consumption Authority;
- effect-specific retry/publication rules and exactly one Scheduler Runtime
  Authority per world while preserving separate Allocation ownership;
- an RFC-0023 Draft 2 that separates public `ExecutionInput` from internal
  `ExecutionContext`, records conditional phases explicitly, and acknowledges
  cross-owner publication boundaries; and
- platform hardening, pure Execution foundations, one concrete Allocation
  provider, then live stage-350 Allocation before live generic Execution.

Every recommendation remains proposed. The owner must approve each Decision
and separately authorize implementation.

## Governing Authority

This package was checked against:

- [Constitution](../CONSTITUTION.md)
- [Core Principles](../CORE_PRINCIPLES.md)
- [Project Rules](../PROJECT_RULES.md)
- [Accepted Decision Log](../DECISIONS.md)
- [Technical Architecture](../TECHNICAL_ARCHITECTURE.md)
- [Architecture Guide](BCSE_ARCHITECTURE_GUIDE.md)
- [Architecture Validation Framework](ARCHITECTURE_VALIDATION_FRAMEWORK.md)
- [RFC-0022 Revision 2](RFC-0022_RESOURCE_ALLOCATION_ENGINE.md)
- [RFC-0022 Architecture Review](RFC-0022_ARCHITECTURE_REVIEW.md)
- [RFC-0023 Draft 1](RFC-0023_DETERMINISTIC_EXECUTION_ENGINE.md)
- [Simulation Scheduler](SIMULATION_SCHEDULER.md)
- [Economic Planning Engine](ECONOMIC_PLANNING_ENGINE.md)
- [Transaction Framework](TRANSACTION_FRAMEWORK.md)
- [Production Framework](PRODUCTION_FRAMEWORK.md)
- [Known Limitations](../KNOWN_LIMITATIONS.md)

The audit is evidence. The Constitution and accepted Decisions remain the
authority.

## Current Facts Reverified

The proposals are based on current repository behavior:

- Planning installs one continuation Work, executes a complete cycle each
  authoritative tick, and defers to the next tick.
- `PlanningManager` retains every cycle and `PlanningStorage` writes six
  complete Planning files.
- JSON-backed services use independent graceful-stop save hooks.
- individual files commonly use temporary replacement, but no global
  checkpoint generation exists.
- Scheduler initialization rejects Clock/finalized-tick mismatch.
- `TransactionExecutor` matches accepted validation to a Transaction by
  `TransactionId`, without proposal digest or Inventory Freshness Identity.
- handler registration requires a non-null `HandlerEffectType` but does not
  enforce effect-specific policy.
- `SimulationPipeline` and `AllocationCycleExecutor` use instance-local
  execution guards.
- M22A-M22D are accepted and implemented.
- no production-grade Allocation provider, stage 350, persistence, Planning
  handoff, Production gate, or Execution implementation exists.
- RFC-0023 remains Draft 1 and explicitly authorizes no implementation.

No proposed ADR assumes behavior beyond these facts.

## Audit Findings Addressed

| Finding | Proposed Decision | Current severity | Affected systems | Implementation gate | Owner approval required |
|---|---|---|---|---|---|
| BCSE-AUDIT-001 unbounded every-tick Planning evidence | Planning Cadence; Evidence Lifecycle | Critical | Planning, Scheduler, persistence | Block more recurring Planning/Execution work | Yes, both ADRs |
| BCSE-AUDIT-002 no coordinated durable checkpoint | Checkpoint And Recovery; Evidence Lifecycle | Critical | All durable BCSE owners | Block Allocation/Execution persistence | Yes, paired approval |
| BCSE-AUDIT-003 validation bound only by Transaction ID | Transaction Validation Authority | Medium | Transactions, Inventory, Production, future Execution | Block Execution Transaction handoff | Yes |
| BCSE-AUDIT-004 effect types are descriptive only | Scheduler Effects And Authority | Medium | Scheduler, Production, Planning, future Execution | Block new live handlers and RFC-0023 integration | Yes |
| BCSE-AUDIT-006 instance-local execution guards | Scheduler Effects And Authority | Medium | Scheduler, Allocation, future Execution | Block module-constructed orchestrators | Yes |
| BCSE-AUDIT-007 RFC-0023 contract ambiguity | Execution RFC Reconciliation | Medium | Execution, Allocation, Scheduler, Transactions, persistence | Block RFC-0023 implementation | Yes, then separate RFC approval |
| RFC-0022 M22E-M22F continuation gate | Allocation Integration Sequencing | Critical continuation gate | Planning, Allocation, Production, Scheduler, Execution | Block M22E-M22F | Yes |

## Findings Intentionally Deferred

The following audit findings are outside AH-1:

| Finding | Reason deferred | Revisit point |
|---|---|---|
| BCSE-AUDIT-005 manifest-to-source conformance | Important assurance work but not a lifecycle or live-integration prerequisite | After AH-1 approval, before public expansion API |
| BCSE-AUDIT-008 Planning as concrete dependency hub | Current production-only scope is accepted; no second industry consumer exists | Before second independent Planning provider |
| BCSE-AUDIT-009 large core files | Refactoring without an implementation milestone would be speculative | When a proven responsibility can be extracted |
| BCSE-AUDIT-010 stale public mod metadata | Release/document maintenance, not architecture authority | Next release-readiness sprint |
| BCSE-AUDIT-011 deprecation warnings | Platform maintenance, not AH-1 architecture | Before NeoForge upgrade |
| BCSE-AUDIT-012 single Gradle module | Current module plan deliberately defers extraction | When a second industry proves a reusable boundary |
| Product terminology collision | No immediate lifecycle risk and no approved expansion API | Before cross-industry public API lock-in |

Deferral does not dismiss these findings. It prevents AH-1 from becoming an
unbounded redesign package.

## Proposed Decisions

### A. Planning Cadence

Document:
[ADR-PROPOSED-PLANNING-CADENCE](adr/ADR-PROPOSED-PLANNING-CADENCE.md)

Recommendation:

- deterministic hybrid periodic plus relevant-change triggers;
- minimum separation 20 simulation ticks;
- default periodic cadence 1,200 simulation ticks;
- configurable range 20 through 72,000 simulation ticks;
- one pending Scheduler Work item for Planning and at most one Planning Cycle
  per tick;
- ordered immutable trigger records;
- deterministic coalescing;
- one recovery cycle when overdue, never burst catch-up;
- no wall-clock or player-presence input; and
- cadence/trigger state in the coordinated checkpoint.

Owner-sensitive values:

- minimum, default, and maximum period;
- initial closed trigger categories; and
- queue capacity implementation.

### B. Evidence Lifecycle

Document:
[ADR-PROPOSED-EVIDENCE-LIFECYCLE](adr/ADR-PROPOSED-EVIDENCE-LIFECYCLE.md)

Recommendation:

- classify runtime, replay-critical, permanent audit, reports, traces,
  diagnostics, summaries, checkpoints, archives, and disposable derivations;
- retain three committed checkpoints and all required replay deltas hot;
- partition cold evidence by owner/category/schema and fixed 100,000-tick
  ranges;
- preserve permanent audit facts indefinitely, though they may move to cold
  storage;
- expire only explicitly disposable derived evidence;
- add deterministic cross-subsystem correlation;
- request a checkpoint when hot evidence approaches its bound; and
- fail visibly before new side effects when evidence capacity is exhausted.

Owner-sensitive values:

- replay horizon;
- partition size;
- hot/archive budgets;
- export/remount policy; and
- report audit horizon.

### C. Coordinated Checkpoint And Recovery

Document:
[ADR-PROPOSED-CHECKPOINT-RECOVERY](adr/ADR-PROPOSED-CHECKPOINT-RECOVERY.md)

Recommendation:

- directory per immutable checkpoint generation;
- deterministic `CheckpointGenerationId`;
- periodic checkpoint every 6,000 simulation ticks;
- manual, graceful-shutdown, migration, and evidence-pressure triggers;
- immutable end-of-finalized-tick snapshot boundary;
- owner snapshot manifests and canonical SHA-256 digests;
- two-phase publication;
- alternating `checkpoint_head_a` and `checkpoint_head_b` commit slots;
- retain at least three committed generations;
- deterministic selection of the highest complete valid generation;
- rollback to last committed generation after crash;
- no automatic catch-up or cross-owner file merging; and
- one coordinated migration from current schema-1 files.

Owner-sensitive items:

- checkpoint period;
- World Identity `SavedData` disposition;
- shutdown failure policy;
- minimum filesystem guarantee; and
- operator recovery controls.

### D. Transaction Validation Authority

Document:
[ADR-PROPOSED-TRANSACTION-VALIDATION-AUTHORITY](adr/ADR-PROPOSED-TRANSACTION-VALIDATION-AUTHORITY.md)

Recommendation:

- canonical Transaction proposal digest;
- authoritative Inventory Freshness Identity and resulting freshness evidence;
- validation-plan digest;
- manager-scoped, world-scoped, non-persisted single-use validation authority;
- one Serialized Transaction-owner Boundary;
- same-ID/different-body conflict;
- duplicate identical APPLIED submission returns existing evidence;
- stale validation rejection; and
- future Execution observes exact digest/freshness-bound result evidence.

Owner-sensitive items:

- Inventory Freshness Identity representation;
- legacy history migration when no original baseline exists;
- post-authority-consumption failure status; and
- lower-level executor visibility.

### E. Scheduler Effects And Scheduler Runtime Authority

Document:
[ADR-PROPOSED-SCHEDULER-EFFECTS-AUTHORITY](adr/ADR-PROPOSED-SCHEDULER-EFFECTS-AUTHORITY.md)

Recommendation:

- enforce semantics for all four current effect types;
- deterministic invocation and effect identities;
- registration-time policy validation;
- effect-specific retry, exception, deferral, generated Work, evidence, and
  recovery rules;
- exact Transaction evidence for `TRANSACTION_BACKED`;
- no automatic retry for unknown `NON_REPEATABLE` effects;
- exactly one Scheduler authority per world;
- exactly one separate Allocation authority per world;
- global owner-level reentrancy guards; and
- no schema-1 recursive or parallel execution.

Owner-sensitive items:

- reclassify Planning to `IDEMPOTENT` after cadence migration or retain
  `NON_REPEATABLE` with stronger durable evidence;
- whether any schema-1 `NON_REPEATABLE` handler is permitted; and
- future public registration policy.

### F. RFC-0023 Reconciliation

Document:
[ADR-PROPOSED-EXECUTION-RFC-RECONCILIATION](adr/ADR-PROPOSED-EXECUTION-RFC-RECONCILIATION.md)

Recommendation:

- prepare Draft 2 only after dependency Decisions are accepted;
- public immutable `ExecutionInput`;
- internal ephemeral validated `ExecutionContext`;
- canonical trace positions with `EXECUTED`, `NOT_REQUIRED`,
  `SKIPPED_AFTER_FAILURE`, and `NOT_REACHED_DUE_TO_BUDGET`;
- adapter returns domain outcome and `TransactionProposalData`;
- Execution Core constructs/submits the Transaction proposal;
- exact digest/freshness-bound Transaction observation;
- deterministic identities for attempt, history, report, trace, completion,
  failure, cancellation, and publication;
- owner-local Execution atomicity distinguished from Transaction application
  and durable checkpoint publication;
- recovery-required state when Transaction is APPLIED but Execution
  publication fails;
- one Execution authority per world;
- effect-aware retry/attempt rules;
- no independent Execution persistence; and
- sustained-lifecycle acceptance tests.

Owner-sensitive items:

- terminology;
- exact phase set;
- recovery-required lifecycle representation;
- evidence identity formulas; and
- Draft 2 editorial structure.

### G. Allocation M22E-M22F Sequencing

Document:
[ADR-PROPOSED-ALLOCATION-INTEGRATION-SEQUENCING](adr/ADR-PROPOSED-ALLOCATION-INTEGRATION-SEQUENCING.md)

Recommendation:

1. implement approved platform hardening;
2. revise/accept RFC-0023 and implement pure M23A-M23C foundations;
3. prove one concrete Production capacity provider without live activation;
4. migrate Scheduler/Planning/Production/Allocation through one checkpoint;
5. add stage `butchercraft:allocation` at order 350;
6. add Planning submission handoff;
7. invoke one bounded Allocation Cycle;
8. activate complete sets before Production progress;
9. release Commitments explicitly after terminal outcome; and
10. integrate live generic Execution only after the Production-gated slice is
    stable.

Owner-sensitive items:

- first concrete provider;
- migration of active legacy Production Work;
- Allocation WAITING reevaluation trigger;
- expiration request ownership; and
- milestone numbering.

## Alternatives Evaluated

| Decision | Alternatives evaluated | Recommended |
|---|---|---|
| Planning cadence | every tick; fixed periodic; evidence-triggered; hybrid; demand-only | deterministic hybrid |
| Evidence lifecycle | count rolling; tick window; append-only archives; checkpoint+deltas; hybrid hot/cold | hybrid hot/cold |
| Checkpointing | independent saves; ordered shutdown; generation directory; write-ahead manifest; two-phase; journal | generation directory plus two-phase dual heads |
| Transaction authority | digest; runtime token; serialized boundary; digest+freshness; composite | composite |
| Handler effects | descriptive labels; all idempotent; all Transaction-backed; enforced effect matrix | enforced effect matrix |
| Execution input | literal Draft 1; remove Transactions; merge input/context; separate input/context | separate public input/internal context |
| Allocation sequencing | Allocation first; pure Execution then Allocation; joint vertical slice; persistence first | pure Execution foundation then Allocation |

The ADRs contain full advantages, disadvantages, replay, determinism,
runtime, persistence, compatibility, and migration analysis.

## Decision Dependency Graph

```text
Platform Canonicalization Addendum
              |
              v
Evidence Lifecycle
Checkpoint Recovery
Transaction Validation Authority
Planning Cadence
Scheduler Effects Authority
              |
              v
RFC-0023 Draft 2 Reconciliation
              |
              v
Allocation Integration Sequencing
```

Normative dependency statements:

- The Platform Canonicalization Addendum is the canonical source for shared
  vocabulary, identity classes, failure states, recovery/replay distinction,
  operator authority, Platform Determinism Manifest, and World Identity
  disposition.
- Evidence Lifecycle, Checkpoint Recovery, Transaction Validation Authority,
  Planning Cadence, and Scheduler Effects Authority depend on the addendum for
  platform-wide definitions.
- RFC-0023 Draft 2 reconciliation depends on the addendum and the five
  ratified platform ADRs.
- Allocation integration sequencing remains downstream of RFC-0023
  reconciliation and the ratified platform ADRs.
- The dependency graph contains no platform-document cycle.

## Historical Approval Order

The following approval order was the review recommendation before AR-007
ratification. It is retained as historical review evidence; the ratified
dependency direction is the graph above.

### Approval Wave 1: Lifecycle Pair

Review and approve together:

- B Evidence Lifecycle;
- C Coordinated Checkpoint And Recovery.

Neither should be accepted alone with contradictory replay horizons,
generation retention, or archival rules.

### Approval Wave 2: Work Creation And Mutation Binding

After Wave 1:

- A Planning Cadence;
- D Transaction Validation Authority.

These can be approved in parallel once their checkpoint/evidence assumptions
are aligned.

### Approval Wave 3: Invocation Safety

- E Scheduler Effects And Scheduler Runtime Authority.

This depends on the durable evidence and Transaction result contracts.

### Approval Wave 4: Execution Architecture

- F RFC-0023 Reconciliation ADR;
- prepare RFC-0023 Draft 2;
- separately review and accept Draft 2.

The reconciliation ADR is not RFC acceptance.

### Approval Wave 5: Allocation Continuation

- G Allocation M22E-M22F Sequencing.

Implementation authorization remains separate after all waves.

## Cross-Decision Consistency Verification

### Planning Cadence And Checkpoint Cadence

- Planning default: 1,200 simulation ticks.
- Checkpoint default: 6,000 simulation ticks.
- Under idle defaults, five periodic Planning opportunities occur per
  checkpoint interval.
- Relevant triggers can schedule earlier but never violate the 20-tick minimum.
- Both use the authoritative Simulation Clock.
- Both persist their state in one generation.
- Neither uses wall clock or player presence.

No conflict identified.

### Evidence Retention And Recovery

- Three committed checkpoints are retained hot.
- Replay-critical evidence from the oldest retained checkpoint through current
  state remains available.
- Permanent audit facts move to cold archives but are not deleted.
- Compaction cannot remove recovery deltas inside the retained horizon.
- Checkpoint manifests reference exact archive/evidence indexes.

No required recovery delta is deleted by the proposed policy.

### Transaction And Inventory Consistency

- Inventory owns its freshness identity.
- Transaction validation binds proposal, plan, and Inventory Freshness
  Identity.
- APPLIED result records resulting freshness evidence.
- The checkpoint validates exact Transaction/Inventory freshness agreement.
- No load path merges one owner's newer files with another generation.

No ownership transfer identified.

### Transaction Binding Across Execution

- adapter supplies proposal data only;
- Execution Core builds the canonical proposal;
- TransactionManager owns validation authority;
- Execution receives immutable result evidence only; and
- result must match exact proposal digest and Inventory freshness evidence.

ID-only inference is prohibited.

### Scheduler Effect Rules And Transaction-Backed Execution

- `TRANSACTION_BACKED` requires exact result evidence.
- unknown outcome prevents automatic resubmission.
- duplicate APPLIED outcome is observed, not reapplied.
- owner runtime publication and durable checkpointing are separate from
  Transaction application.

No retry conflict identified.

### RFC-0023 Publication And Durable Boundaries

- Transaction/Inventory application is one owner-local publication.
- Execution candidate publication is another.
- the checkpoint is cross-owner durable publication.
- APPLIED Transactions are never claimed to be rolled back by Execution.
- failed post-APPLIED Execution publication enters recovery.

No impossible cross-owner in-memory rollback is claimed.

### Allocation Checkpoint Participation

- no independent Allocation shutdown file set becomes authoritative;
- Allocation joins the checkpoint with Planning, Production, Scheduler,
  Transaction, Inventory, and future Execution;
- stage migration and persistence migration commit together; and
- active/released Commitment evidence follows the evidence lifecycle.

No independent persistence island is introduced.

### Replay Inputs Under Retention

- checkpoint baseline, cadence/trigger state, provider observations,
  Allocation evidence, effect policy, Transaction results, and Execution
  evidence remain in the minimum replay horizon;
- permanent facts remain archived; and
- no replay queries live providers or external effects.

No replay-input gap identified within the guaranteed horizon.

### Ownership And Time

No proposed decision transfers:

- decision ownership from Planning;
- Resource/Capacity ownership from providers;
- Commitment ownership from Allocation;
- Work lifecycle from Scheduler;
- Production runtime from Production;
- generic runtime from future Execution;
- mutation from Transactions;
- quantities and freshness identities from Inventory; or
- time from the Simulation Clock.

Player presence is absent from cadence, checkpoint, evidence, retry, and
integration policy.

## Compatibility Implications

The package proposes additive identities and schemas but requires coordinated
migration:

- Planning cadence/trigger runtime replaces every-tick continuation behavior.
- Evidence records gain category, correlation, digest, archive, and horizon
  metadata.
- current files migrate into checkpoint generation 1.
- Inventory gains authoritative freshness identity.
- Transactions gain proposal/plan/application digests and freshness evidence.
- Scheduler handlers gain effect policy and invocation identity.
- Scheduler schema later gains stage 350.
- Planning and Production gain Allocation references.
- Allocation gains checkpoint/evidence representation.
- future Execution uses reconciled definitions from inception.

Stable domain ids remain unchanged. No old ID is repurposed.

## Migration Implications

Migration order is:

1. validate every current persistence owner and cross-reference;
2. establish evidence classification and archive import;
3. establish Inventory/Transaction freshness and digest binding;
4. migrate Planning cadence and current continuation Work;
5. register handler effect policy and authority ownership;
6. commit the first coordinated checkpoint;
7. retain legacy sources;
8. later migrate stage 350 and Allocation references in one new generation;
  and
9. add Execution persistence only after RFC/persistence milestones are
  separately approved.

Ambiguous, incomplete, or unsupported legacy state fails visibly. No migration
silently resets a subsystem or fabricates evidence.

## Implementation Gates

| Gate | Blocked until | Exit condition |
|---|---|---|
| AH-1 decision acceptance | Owner approves each proposed ADR | Accepted Decision records exist |
| Lifecycle implementation | B/C/A accepted and milestone authorized | sustained cadence, archive, checkpoint, recovery tests pass |
| Transaction hardening | D accepted and milestone authorized | digest/freshness/authority and migration tests pass |
| Scheduler hardening | E accepted and dependencies implemented | effect/authority/concurrency tests pass |
| RFC-0023 Draft 2 | F accepted | tracked Draft 2 prepared |
| RFC-0023 implementation | Draft 2 separately accepted | pure milestone authorization |
| M22E provider | G accepted and lifecycle hardening complete | one concrete provider proof passes |
| M22F live integration | provider and coordinated migration ready | stage 350 vertical slice/recovery tests pass |
| Live generic Execution | all prior gates stable | approved Execution integration milestone |
| Gameplay exposure | separate gameplay approval | manual/game tests and release review |

## Safe Work Before Implementation

The following documentation/review work may proceed without activating a
proposed Decision:

- owner comments and option selection;
- numerical capacity/cadence modeling using non-authoritative fixtures;
- inventory of every current persistence owner and file;
- fault-injection test-plan refinement;
- canonical field inventory for Transaction digest review;
- RFC-0023 Draft 1 section-diff preparation, kept outside the RFC until
  reconciliation approval;
- identification of candidate first provider and its authoritative source;
- correction of unquestionably stale M22D wording; and
- release metadata/document maintenance unrelated to behavior.

No production Java, schema, migration, manifest, Scheduler, Planning,
Transaction, Allocation, Execution, or gameplay change is safe under AH-1
without later authorization.

## Work That Remains Blocked

- changing Planning cadence or continuation Work;
- deleting, compacting, or archiving existing evidence;
- adding checkpoint classes/files/services;
- changing save hooks or load order;
- adding Inventory Freshness Identity;
- changing Transaction validation/executor APIs;
- enforcing HandlerEffectType policy;
- restricting/adding execution authorities;
- editing RFC-0023;
- implementing RFC-0023;
- registering a production Allocation provider;
- adding stage 350;
- adding Allocation persistence;
- changing Planning submission;
- gating Production;
- activating/releasing Commitments through live Work;
- adding Execution persistence;
- migrating current worlds; and
- exposing any gameplay.

## Risk If Decisions Are Postponed

### Continue Feature Work Without AH-1

- every-tick Planning evidence continues unbounded;
- each new durable owner multiplies mixed-generation recovery paths;
- generic Execution may freeze ambiguous input/context and proposal ownership;
- exact Transaction substitution/staleness protection remains implicit;
- handler retry can outgrow its descriptive effect label;
- stage 350 migration may freeze another independent persistence schema; and
- public worlds become harder to migrate safely.

### Approve Decisions But Postpone Implementation

The repository retains current operational risk, but future design has a clear
gate. No new live durable subsystem should proceed during that postponement.

### Implement One Decision In Isolation

- cadence without evidence policy still leaves archival undefined;
- evidence policy without checkpoints cannot establish replay horizons;
- checkpoints without Transaction freshness evidence cannot prove mutation
  consistency;
- effect enforcement without recovery cannot handle unknown outcomes;
- RFC reconciliation without its dependencies remains aspirational; and
- Allocation integration without all dependencies creates a new mixed
  persistence island.

## Recommended Repository Sequencing

1. Owner reviews this package and records requested changes.
2. Revise proposed ADRs without changing their status.
3. Owner explicitly approves B and C together.
4. Record accepted Decisions in `DECISIONS.md`; keep proposed ADRs as reviewed
   historical artifacts or mark them superseded by those accepted records.
5. Approve and record A and D.
6. Approve and record E.
7. Approve F, prepare RFC-0023 Draft 2, and review Draft 2 separately.
8. Approve G only after Draft 2 and all dependencies align.
9. Update Technical Architecture, Architecture Guide, Known Limitations,
   Milestones, and Architecture Manifest only for accepted contracts.
10. Authorize small implementation milestones in the gate order above.
11. Re-run an independent architecture review after the first complete
    checkpoint and again after the stage-350 vertical slice.

Nothing in this sequence authorizes this task to commit, tag, push, or publish.

## Exact Owner Approvals Required

### Planning Cadence

- [ ] Hybrid periodic plus relevant-change model.
- [ ] 20-tick minimum.
- [ ] 1,200-tick default.
- [ ] 72,000-tick maximum.
- [ ] trigger categories, ordering, coalescing, and no burst catch-up.

### Evidence Lifecycle

- [ ] evidence classifications.
- [ ] three-checkpoint replay horizon.
- [ ] 100,000-tick partitioning.
- [ ] storage budgets and capacity failure.
- [ ] permanent audit and disposable evidence rules.
- [ ] correlation identity and query guarantees.

### Checkpoint And Recovery

- [ ] directory-per-generation/two-phase model.
- [ ] deterministic generation identity.
- [ ] 6,000-tick cadence.
- [ ] dual head slots.
- [ ] three-generation retention.
- [ ] rollback/no-catch-up policy.
- [ ] World Identity migration disposition.
- [ ] filesystem and shutdown guarantees.

### Transaction Validation

- [ ] composite digest/freshness/single-use authority.
- [ ] Inventory Freshness Identity representation.
- [ ] duplicate and stale behavior.
- [ ] Execution handoff.
- [ ] legacy migration policy.

### Scheduler Effects And Authority

- [ ] effect matrix.
- [ ] retry/deferral/generated Work rules.
- [ ] one Scheduler authority.
- [ ] one separate Allocation authority.
- [ ] no schema-1 recursion/parallelism.
- [ ] Planning handler reclassification or alternative.

### RFC-0023 Reconciliation

- [ ] input/context split.
- [ ] canonical phase positions/dispositions.
- [ ] proposal ownership.
- [ ] exact Transaction result binding.
- [ ] evidence identities.
- [ ] checkpoint/evidence dependency.
- [ ] post-APPLIED recovery semantics.
- [ ] retry/authority semantics.
- [ ] authorization to prepare Draft 2.
- [ ] separate later acceptance of Draft 2.

### Allocation Sequencing

- [ ] Option 2 sequence.
- [ ] first provider scope.
- [ ] stage 350 migration.
- [ ] Planning handoff.
- [ ] activation and Production gate.
- [ ] release/expiration behavior.
- [ ] checkpoint/evidence participation.
- [ ] legacy active Work migration.
- [ ] separate M22E and M22F implementation authorization.

### Implementation Authority

For every approved ADR:

- [ ] authorize an accepted Decision record;
- [ ] authorize a specific implementation milestone;
- [ ] authorize schema migration when ready;
- [ ] authorize architecture manifest changes; and
- [ ] authorize gameplay exposure separately.

## Remaining Implementation And Specification Choices

The ratified platform direction leaves these later choices for implementation
or downstream RFC work:

1. Are the schema-1 cadence, partition, checkpoint, and budget defaults
   suitable for the intended simulation scale before public save compatibility
   is promised?
2. What exact operator UX is required for checkpoint selection, archive
   remount, Recovery-Blocked State, and storage recovery?
3. Which Inventory Freshness Identity representation should implementation use?
4. Is any schema-1 `NON_REPEATABLE` handler acceptable under a future
   implementation milestone?
5. How should the ratified Planning migration to `IDEMPOTENT` be implemented
   after cadence identity exists?
6. What is the first production-grade Allocation provider?
7. How should nonterminal legacy Production Work migrate when stage 350 is
   introduced?
8. What filesystem durability capability is the minimum supported target?
9. What exact RFC-0023 Draft 2 wording should reconcile with the ratified
   platform vocabulary and invariants?

## Final Authority Statement

AH-1 records a coherent path to bounded, recoverable, exactly authorized
simulation. AR-007 ratifies the platform direction in documentation while
leaving implementation and runtime behavior unchanged.

After AR-007 ratification:

- the Platform Canonicalization Addendum is the canonical platform vocabulary
  and invariant reference;
- Evidence Lifecycle, Checkpoint Recovery, Transaction Validation Authority,
  Planning Cadence, and Scheduler Effects Authority are ratified
  architectural direction;
- RFC-0023 remains Draft 1;
- M22E-M22F remain blocked;
- no checkpoint or evidence migration exists;
- current Scheduler, Planning, Transaction, Allocation, Production, and
  gameplay behavior remains authoritative; and
- no implementation is authorized.
