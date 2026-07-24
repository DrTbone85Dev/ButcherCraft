# RFC-0023: Deterministic Execution Engine

Status: Architecture Specification

Revision: Draft 2

Governing authority: [`CONSTITUTION.md`](../CONSTITUTION.md)

This RFC defines the proposed industry-neutral Execution subsystem. It replaces
Draft 1 as the proposed Execution architecture and reconciles Execution with
the ratified BCSE platform architecture.

No implementation, schema change, migration, gameplay behavior, runtime
behavior, Allocation integration, Scheduler integration, Transaction
integration, or persistence implementation is authorized by this draft.

## 1. Authoritative References

Execution consumes platform architecture. It does not redefine platform-wide
concepts.

This RFC is subordinate to:

- [`CONSTITUTION.md`](../CONSTITUTION.md)
- [`CORE_PRINCIPLES.md`](../CORE_PRINCIPLES.md)
- [`BCSE Architecture Guide`](BCSE_ARCHITECTURE_GUIDE.md)
- [`Architecture Validation Framework`](ARCHITECTURE_VALIDATION_FRAMEWORK.md)
- [`Platform Canonicalization Addendum`](adr/ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md)
- [`ADR-01: Platform Evidence Lifecycle`](adr/ADR-PROPOSED-EVIDENCE-LIFECYCLE.md)
- [`ADR-02: Checkpoint Recovery`](adr/ADR-PROPOSED-CHECKPOINT-RECOVERY.md)
- [`ADR-03: Transaction Validation Authority`](adr/ADR-PROPOSED-TRANSACTION-VALIDATION-AUTHORITY.md)
- [`ADR-04: Deterministic Planning Cadence`](adr/ADR-PROPOSED-PLANNING-CADENCE.md)
- [`ADR-05: Scheduler Handler Effects And Scheduler Runtime Authority`](adr/ADR-PROPOSED-SCHEDULER-EFFECTS-AUTHORITY.md)
- [`RFC-0022: Resource Allocation Engine`](RFC-0022_RESOURCE_ALLOCATION_ENGINE.md)

The [`Platform Canonicalization Addendum`](adr/ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md)
is the canonical source for platform vocabulary, identity classes, invariant
ownership, Recovery, Replay, Rollback, Publication, Observation, failure
taxonomy, cancellation, Operator Authority, World Identity, and the Platform
Determinism Manifest.

## 2. Purpose

The Deterministic Execution Engine answers one question:

> Given approved executable work and explicit Execution Authorization Evidence,
> how does that work progress toward completion?

Execution does not decide whether work should happen.

Execution does not decide whether scarce Capacity is available.

Execution does not mutate authoritative state.

Execution coordinates bounded, deterministic progress for already approved and
authorized work.

## 3. Architectural Position

The target BCSE data flow is:

```text
Authoritative Observation
  -> Planning Cycle
      -> Domain-Owned Executable Work Definition
          -> Execution Authorization Evidence
              -> Scheduler Work
                  -> Execution
                      -> Transaction Proposal
                          -> Authoritative Result
                              -> Authoritative State
```

This is an ownership and data-flow diagram, not a Scheduler stage list and not
an implementation dependency chain.

Execution consumes externally owned facts and publishes Execution-owned runtime
and evidence. It does not become the owner of Planning, Allocation, Scheduler,
Transactions, Inventory, Checkpoint Recovery, Evidence Lifecycle, or World
Identity.

## 4. Execution Ownership

Execution owns:

- Execution Authority;
- Execution lifecycle;
- Execution runtime state;
- Execution instances;
- Execution attempts;
- Execution progress;
- Execution coordination;
- Execution observation;
- Execution reports;
- Execution traces;
- Execution summaries;
- Execution owner snapshots;
- Execution adapter contracts;
- Execution verification;
- Execution acceptance criteria.

Execution does not own:

- platform identity model;
- platform Replay model;
- platform Recovery model;
- Evidence Lifecycle;
- Checkpoint generation publication;
- Transaction validation;
- Inventory mutation;
- Planning cadence;
- Scheduler effect semantics;
- Allocation authority;
- World Identity;
- Operator Authority.

## 5. Execution Authority

Schema 1 defines one Execution Authority per loaded world when generic
Execution is implemented.

Execution Authority owns:

- accepted Execution instances;
- Execution runtime lifecycle state;
- active and historical Execution attempts;
- Execution progress records;
- Execution-local observations;
- Execution reports;
- Execution traces;
- Execution summaries;
- Execution owner snapshots.

Execution Authority does not own:

- executable work definitions;
- Planning Cycle artifacts;
- Allocation Requests, AllocationSets, Commitments, Resources, or Capacity;
- Scheduler Work lifecycle or dispatch order;
- Transaction validation, Validation Consumption Authority, or mutation;
- Inventory quantities;
- CheckpointGenerationId selection or committed-generation visibility;
- evidence retention, archival, or compaction policy.

Public callers may request Execution actions through documented contracts after
a future implementation is approved. They do not receive Execution Authority.

## 6. Execution Definition

Execution is the deterministic progression of one Execution instance through
time.

Execution begins only after the Execution Authority accepts:

- a stable reference to a domain-owned executable work definition;
- explicit Execution Authorization Evidence;
- a valid Scheduler invocation context;
- all required platform identities and configuration references;
- a valid non-terminal Execution runtime state.

Execution ends through exactly one terminal outcome:

- `COMPLETED`
- `FAILED`
- `CANCELLED`

Execution completion never implies successful authoritative state mutation
unless the required Transaction Authoritative Result proves that mutation.

## 7. Boundaries

### 7.1 Planning

Planning owns Planning Cycle cadence, Planning decisions, trigger consumption,
input capture, and Planning Cycle publication under the Planning Cadence ADR.

Execution may reference an approved executable intent. It shall not create,
rank, approve, reschedule, or reinterpret Planning artifacts.

### 7.2 Allocation

Allocation owns Resource and Capacity authority under RFC-0022.

Execution consumes Execution Authorization Evidence. Future Allocation
integration may provide that evidence. Execution does not define Allocation
authority and does not require Allocation implementation to exist.

Execution shall not search for Resources, select Resources, reserve Capacity,
create AllocationSets, create Commitments, activate Commitments, release
Commitments, repair missing authorization, or extend expired authorization.

### 7.3 Scheduler

Scheduler owns Scheduler Work lifecycle, ordered dispatch, effect policy,
Invocation Identity, Effect Identity, Scheduler publication, and Scheduler
Runtime Authority under the Scheduler Effects Authority ADR.

Execution consumes one Scheduler invocation at a time. One Scheduler invocation
may authorize at most one bounded Execution step for one Execution instance.

Execution does not own Scheduler Work, dispatch order, retry policy, effect
classification, or Unknown Outcome handling.

### 7.4 Transactions

Transactions own validation, Validation Consumption Authority, the Serialized
Transaction-owner Boundary, mutation application, and Authoritative Result
evidence under the Transaction Validation Authority ADR.

Execution may submit a canonical Transaction proposal through the approved
Transaction boundary. Transactions validate the proposal, consume Validation
Consumption Authority privately, and publish Authoritative Result evidence.

Execution observes Authoritative Result evidence. Execution never receives,
persists, reuses, or transfers Validation Consumption Authority.

### 7.5 Evidence Lifecycle

Execution owns the content of Execution reports, traces, summaries, history,
and owner snapshots.

The Evidence Lifecycle ADR owns evidence classification, retention, archival,
compaction, integrity verification, and query policy. Execution evidence does
not become a second runtime authority.

### 7.6 Checkpoint Recovery

Checkpoint Recovery owns CheckpointGenerationId, committed generation
selection, atomic checkpoint visibility, Rollback selection, Recovery, and
storage-artifact quarantine.

Execution owns its owner snapshot content and validates that content when
Checkpoint Recovery requests participant validation. Execution does not select
checkpoint generations or publish platform checkpoints.

### 7.7 World Identity

World Identity remains an immutable external root owned outside Execution.
Execution references World Identity as a platform identity input and never
regenerates, migrates, or redefines it.

## 8. Platform Concepts Consumed By Execution

Execution consumes these canonical platform concepts by reference:

- Authority;
- Identity;
- Entity Identity;
- Content Identity;
- Freshness Identity;
- Evidence Identity;
- Proposal Identity;
- Validation Plan Identity;
- Invocation Identity;
- Effect Identity;
- CheckpointGenerationId;
- Owner Snapshot;
- Publication;
- Observation;
- Recovery;
- Replay;
- Rollback;
- Authoritative Result;
- Unknown Outcome;
- Recovery-Blocked State;
- Quarantined Artifact;
- Configuration Identity;
- Platform Determinism Manifest;
- Operator Authority.

When this RFC uses one of those terms, the canonical definition is the
Platform Canonicalization Addendum.

## 9. Execution Identity

Execution-specific identities follow the Platform Identity Model. This RFC
defines only what Execution-specific identities identify.

Schema 1 Execution-specific identities are:

| Identity | Identifies | Owner |
| --- | --- | --- |
| Execution Identity | The generic Execution subsystem authority for one loaded world | Execution |
| Execution Instance Identity | One accepted runtime instance for one domain-owned executable work definition | Execution |
| Execution Attempt Identity | One bounded attempt to advance one Execution instance | Execution |
| Execution Step Identity | One bounded step evaluated under one Scheduler invocation | Execution |
| Execution Report Identity | One immutable Execution report | Execution |
| Execution Trace Identity | One immutable Execution trace | Execution |
| Execution Summary Identity | One immutable Execution summary | Execution |
| Execution Owner Snapshot Identity | One Execution-owned snapshot supplied to Checkpoint Recovery | Execution |
| Execution Authorization Evidence reference | A reference to externally owned authorization evidence consumed by Execution | Source owner |

Execution also references platform and external identities including Proposal
Identity, Freshness Identity, Validation Plan Identity, Invocation Identity,
Effect Identity, Evidence Identity, CheckpointGenerationId, World Identity,
and the Platform Determinism Manifest.

Execution shall not define a global Inventory state version, Inventory
Freshness Identity scheme, Transaction proposal digest algorithm, Scheduler
invocation identity, checkpoint generation identity, evidence identity, or
world identity scheme.

## 10. Execution Authorization Evidence

Execution Authorization Evidence is the explicit evidence that externally
owned authorization conditions have been satisfied for an Execution instance.

Execution consumes this evidence. It does not own the authority that created
the evidence.

Execution Authorization Evidence may include, when required by the owning
domain or future integration:

- a domain-owned executable work reference;
- authorization source identity;
- authorization source owner;
- source-owned Freshness Identity;
- authorization content identity;
- authorized scope;
- valid lifecycle state;
- valid simulation tick range or invocation binding;
- configuration identity or Platform Determinism Manifest reference;
- integrity evidence needed by the source owner.

Missing, stale, conflicting, corrupt, or unsupported authorization evidence
prevents Execution from beginning or continuing. Execution records a typed
Execution-local failure or waiting state as appropriate, but it does not repair
or replace the missing authority.

## 11. Domain-Owned Executable Work

Executable work definitions remain owned by their authoritative domain.

Examples:

- Production owns Production work definitions.
- Future Logistics owns transport work definitions.
- Future Maintenance owns maintenance work definitions.
- Future Utilities owns utility work definitions.

Execution references a domain-owned executable work definition and invokes a
registered Execution adapter for that definition type. Execution never becomes
authoritative for the domain definition, domain policy, or domain semantics.

## 12. Execution Runtime

Execution runtime state is mutable only inside Execution Authority.

Schema 1 runtime state includes:

- Execution Instance Identity;
- executable work reference;
- Execution Authorization Evidence references;
- current lifecycle state;
- active attempt reference, if any;
- progress;
- waiting reason, if any;
- terminal outcome reference, if any;
- last accepted Scheduler invocation reference;
- last observed Transaction Authoritative Result reference, if any;
- schema version;
- Platform Determinism Manifest reference.

Public views of runtime state are immutable snapshots.

## 13. Lifecycle States

Schema 1 lifecycle states are:

- `CREATED`
- `READY`
- `RUNNING`
- `WAITING`
- `COMPLETING`
- `COMPLETED`
- `FAILED`
- `CANCELLED`

Terminal states are:

- `COMPLETED`
- `FAILED`
- `CANCELLED`

`CREATED` means the Execution instance exists but has not yet accepted all
required runtime and authorization conditions.

`READY` means Execution has accepted required conditions and the instance may
advance when Scheduler dispatches eligible Scheduler Work.

`RUNNING` means one bounded Execution step is being evaluated.

`WAITING` means the instance remains valid but cannot advance during the
current invocation.

`COMPLETING` means domain work has completed and Execution is waiting for any
required Transaction Authoritative Result or local publication condition.

`COMPLETED`, `FAILED`, and `CANCELLED` are terminal.

## 14. Lifecycle Transitions

Schema 1 permits these transitions:

```text
CREATED -> READY
CREATED -> FAILED
READY -> RUNNING
READY -> CANCELLED
RUNNING -> WAITING
RUNNING -> COMPLETING
RUNNING -> FAILED
WAITING -> RUNNING
WAITING -> FAILED
WAITING -> CANCELLED
COMPLETING -> COMPLETED
COMPLETING -> FAILED
```

Terminal states never transition. Any unlisted transition fails explicitly.

Cancellation follows the platform Cancellation Model. Execution may cancel its
own non-terminal lifecycle state when an authorized cancellation request is
accepted. Execution cancellation does not cancel applied Transactions, release
Allocation authority, clear Scheduler Work, or select rollback.

## 15. Attempts

An Execution attempt represents one bounded attempt to advance one Execution
instance.

An attempt records:

- Execution Attempt Identity;
- Execution Instance Identity;
- Scheduler Invocation Identity;
- starting lifecycle state;
- starting progress;
- Execution Authorization Evidence references used;
- adapter identity;
- Transaction proposal reference, if one was submitted;
- observed Authoritative Result reference, if one was required;
- ending lifecycle state;
- typed failure or waiting reason, if any;
- trace reference;
- schema version.

Attempts are immutable once published. Retry creates a new attempt identity
unless a future accepted Execution policy defines a narrower retry identity
rule.

## 16. Progress

Execution progress is an Execution-owned runtime fact.

Progress must be:

- deterministic;
- bounded;
- monotonic within one Execution instance unless an accepted domain adapter
  explicitly defines a reversible progress model;
- inspectable through immutable snapshots;
- explained by Execution trace evidence.

Progress alone does not complete Execution. Required Transaction Authoritative
Result evidence must be observed before Transaction-dependent completion.

## 17. Execution Input

ExecutionInput is the immutable input to one bounded Execution step.

ExecutionInput contains every fact Execution requires for that step, including:

- Execution Instance Identity;
- immutable runtime snapshot;
- executable work reference;
- Execution Authorization Evidence references;
- Scheduler Invocation Identity;
- authoritative simulation tick supplied through Scheduler;
- Platform Determinism Manifest reference;
- adapter identity;
- explicit configuration identities relevant to Execution;
- prior Transaction Authoritative Result references relevant to the instance.

ExecutionInput shall not query live provider state, Inventory state,
Transaction runtime, Scheduler runtime, wall-clock time, filesystem order,
randomness, or mutable global state.

## 18. Execution Context

ExecutionContext is the internal deterministic view derived from
ExecutionInput for adapter evaluation.

ExecutionContext may contain normalized values, resolved immutable references,
and owner-supplied snapshots that were already present in the input. It may
not introduce hidden runtime context.

Every fact required to reproduce or verify an Execution decision must be
represented by ExecutionInput, ExecutionContext, Execution Authorization
Evidence, explicit owner snapshots, resulting Execution evidence, or platform
evidence referenced by identity.

## 19. Execution Adapter

An Execution adapter translates one domain-owned executable work definition
into one bounded deterministic Execution step.

Execution adapters:

- are registered explicitly;
- have stable adapter identity;
- accept ExecutionContext;
- evaluate at most one bounded step per invocation;
- return immutable adapter output;
- may produce Transaction proposal data;
- may produce Execution-local evidence.

Execution adapters shall not:

- mutate authoritative state;
- mutate Inventory quantities;
- validate or execute Transactions;
- consume Validation Consumption Authority;
- allocate Capacity;
- mutate Scheduler Work;
- select checkpoint generations;
- perform Recovery or Replay;
- query live provider state;
- read wall-clock time or hidden randomness;
- reinterpret domain ownership.

## 20. Adapter Output

Adapter output is immutable and has one of these Execution-local meanings:

- progress advanced;
- still waiting;
- domain step failed;
- domain work completed without required Transaction;
- domain work completed with Transaction proposal data;
- cancellation accepted;
- no-op with explicit reason.

Adapter output is not an Authoritative Result and does not prove external
state mutation.

## 21. Canonical Execution Pipeline

One bounded Execution step follows this pipeline:

```text
Capture ExecutionInput
  -> Validate Execution runtime
      -> Validate Execution Authorization Evidence
          -> Resolve Execution adapter
              -> Evaluate one bounded adapter step
                  -> Build Execution candidate state
                      -> Submit or observe Transaction if required
                          -> Validate Execution publication candidate
                              -> Publish Execution runtime and evidence
```

The pipeline is bounded. It does not loop, recursively invoke Execution, or
perform hidden work.

Each phase records enough trace evidence to explain whether the phase
completed, produced a typed failure, or was not reached because an earlier
phase ended the step.

## 22. Transaction Boundary

When completion requires authoritative economic mutation, Execution submits a
canonical Transaction proposal through the Transaction boundary.

Transactions own:

- Proposal Identity;
- Inventory Freshness Identity or other source-owned Freshness Identity;
- Validation Plan Identity;
- Validation Consumption Authority;
- Serialized Transaction-owner Boundary;
- mutation application;
- Authoritative Result evidence.

Execution owns only:

- proposal submission as an Execution-observed action;
- reference to the submitted proposal identity;
- reference to the observed Authoritative Result;
- Execution lifecycle reaction to that result.

Execution shall not infer successful mutation from proposal construction,
validation evidence alone, missing result evidence, timeout, local progress,
or adapter output.

## 23. Transaction Observation

Execution observes Transaction Authoritative Result evidence.

Execution may react to observed Transaction evidence by:

- waiting for a result;
- completing when the required Authoritative Result proves success;
- failing when the Authoritative Result proves rejection or failure;
- entering an explicit platform failure path when outcome evidence is missing,
  conflicting, or unknown.

Execution shall not define Transaction statuses. Execution-local observation
labels, if introduced by implementation, must be documented as Execution
lifecycle reactions rather than Transaction authority.

## 24. Execution Publication

Execution publication is the Execution-owned visibility boundary for Execution
runtime and Execution evidence.

One accepted publication candidate updates all affected Execution-owned facts
together:

- runtime state;
- attempt record;
- progress;
- report;
- trace;
- summary;
- completion, failure, cancellation, or waiting evidence.

Execution publication does not publish platform checkpoints. Checkpoint
Recovery coordinates durable checkpoint generation publication.

## 25. Execution History, Reports, Traces, And Summaries

Execution owns the content of these records:

| Record | Purpose |
| --- | --- |
| Execution history | Ordered lifecycle and attempt facts for an Execution instance |
| Execution report | Human- and system-readable outcome evidence for one instance or attempt |
| Execution trace | Diagnostic phase-by-phase explanation of inputs, adapter output, proposals, results, and publication |
| Execution summary | Compact query view of current or terminal Execution state |

These records are immutable once published. Their retention, archival,
compaction, integrity verification, and query policy are owned by the Evidence
Lifecycle ADR.

## 26. Execution Owner Snapshot

Execution owner snapshots are the Execution-owned snapshot content supplied to
Checkpoint Recovery.

An Execution owner snapshot includes:

- snapshot identity;
- schema version;
- Platform Determinism Manifest reference;
- active Execution runtime state;
- terminal Execution records required for recovery and replay;
- unresolved Transaction Authoritative Result references, if any;
- unresolved Scheduler invocation references, if any;
- Execution evidence required by the Evidence Lifecycle policy;
- validation digest or integrity evidence required by Checkpoint Recovery.

Checkpoint Recovery coordinates publication and Recovery. Execution validates
its own snapshot content but does not select the committed checkpoint
generation.

## 27. Persistence Boundary

Execution defines what Execution-owned data must be persisted or snapshotted.

Execution does not own:

- platform persistence architecture;
- checkpoint storage layout;
- checkpoint generation selection;
- retention policy;
- archive policy;
- compaction policy;
- Recovery;
- Replay baseline selection;
- migration execution.

Any future Execution persistence implementation requires a separate approved
implementation milestone and must integrate with Checkpoint Recovery and the
Evidence Lifecycle ADR.

## 28. Replay Boundary

Replay is defined by the Platform Canonicalization Addendum.

Execution replay consumes:

- explicit recovered baseline selected by Checkpoint Recovery;
- Execution owner snapshots;
- ExecutionInput records;
- Execution evidence;
- Scheduler Invocation Identity and related Scheduler evidence;
- Transaction proposal and Authoritative Result evidence;
- source-owned Freshness Identity references;
- Platform Determinism Manifest;
- retained replay-critical evidence under Evidence Lifecycle policy.

Execution replay may verify lifecycle transitions, progress, adapter output,
publication candidates, Transaction observation references, digests, and trace
equivalence.

Execution replay shall not:

- select a checkpoint generation;
- perform Recovery or Rollback;
- query live Allocation providers;
- query live Inventory;
- resubmit real Transactions;
- reuse Validation Consumption Authority;
- repair missing authority;
- perform non-repeatable effects;
- convert an Unknown Outcome into success;
- advance time by wall clock.

## 29. Recovery Boundary

Recovery is owned by Checkpoint Recovery with owner validation.

During Recovery, Execution may:

- validate an Execution owner snapshot;
- reject corrupt, unsupported, or internally inconsistent Execution snapshot
  content;
- report unresolved references;
- expose required evidence dependencies;
- restore Execution-owned runtime from a valid selected baseline.

Execution shall not:

- select last-known-good checkpoint generation;
- authorize Rollback;
- publish recovered platform state;
- quarantine storage artifacts outside its ownership;
- fabricate missing Execution evidence;
- replay lost ticks as authoritative state.

If required Execution evidence or snapshot content is unavailable, corrupt, or
unresolved, the affected authority follows the platform Recovery-Blocked State
rules.

## 30. Failure Boundary

Execution uses the platform failure taxonomy.

Execution may define Execution-local lifecycle failures, such as:

- invalid Execution runtime;
- missing executable work reference;
- unsupported executable work schema;
- missing Execution Authorization Evidence;
- stale Execution Authorization Evidence;
- adapter missing;
- adapter rejected input;
- adapter bounded-step failure;
- Transaction proposal construction failure;
- missing required Authoritative Result;
- publication candidate invalid;
- snapshot validation failure.

Execution-local failures must be typed, explicit, reproducible from recorded
inputs, and visible in Execution evidence.

Unknown Outcome, Quarantined Artifact, Recovery-Blocked State, Operator
Intervention Required, and related platform states retain their canonical
meaning from the Platform Canonicalization Addendum.

## 31. Cancellation Boundary

Execution cancellation is an Execution lifecycle transition requested through
an approved platform path.

Execution cancellation may stop future Execution progress for a non-terminal
Execution instance. It does not:

- cancel an applied Transaction;
- consume or revoke Validation Consumption Authority;
- release Allocation authority;
- delete Scheduler Work;
- select Rollback;
- clear Unknown Outcome;
- remove Quarantined Artifacts.

Interrupted cancellation is recovered through Checkpoint Recovery and
owner-published evidence.

## 32. Determinism Requirements

Execution determinism is a subsystem contribution to platform determinism.

Execution requires:

- explicit inputs;
- explicit identities;
- source-owned Freshness Identity where source facts are examined;
- canonical ordering for every collection;
- exact arithmetic where quantities are involved;
- no hidden wall-clock inputs;
- no hidden randomness;
- no filesystem ordering dependence;
- bounded work per Scheduler invocation;
- immutable published evidence;
- Platform Determinism Manifest reference for replay-relevant configuration.

Execution does not own the Platform Determinism Manifest. Checkpoint Recovery
publishes the manifest with the generation, and each source subsystem owns its
manifest entries.

## 33. Execution Invariants

`EX-0001`

Execution consumes platform architecture and does not redefine platform-wide
identity, Replay, Recovery, failure taxonomy, cancellation, operator authority,
checkpoint publication, evidence lifecycle, or World Identity.

`EX-0002`

Exactly one Execution Authority exists per loaded world where generic
Execution is implemented.

`EX-0003`

Execution owns Execution runtime state, lifecycle, attempts, progress,
observation, reports, traces, summaries, owner snapshots, adapter contracts,
verification, and acceptance criteria.

`EX-0004`

Execution does not own Planning, Allocation, Scheduler, Transactions,
Inventory, Checkpoint Recovery, Evidence Lifecycle, World Identity, or Operator
Authority.

`EX-0005`

Execution consumes Execution Authorization Evidence and does not create or
repair externally owned authorization.

`EX-0006`

One Scheduler invocation advances at most one bounded Execution step for one
Execution instance.

`EX-0007`

ExecutionInput contains every fact required for one bounded step and does not
query hidden runtime context.

`EX-0008`

Execution adapters are deterministic, bounded, and unable to mutate
authoritative state.

`EX-0009`

Transaction-dependent completion requires observed Authoritative Result
evidence.

`EX-0010`

Execution publication is atomic within Execution ownership.

`EX-0011`

Execution records are immutable once published; Evidence Lifecycle owns their
retention, archival, compaction, integrity verification, and query policy.

`EX-0012`

Execution owner snapshots are validated by Execution and published through
Checkpoint Recovery.

`EX-0013`

Execution replay consumes the platform Replay contract and never selects a
checkpoint generation, queries live providers, resubmits real Transactions, or
reuses Validation Consumption Authority.

`EX-0014`

Execution failures are typed, explicit, reproducible, and expressed with
canonical platform failure terminology plus Execution-local lifecycle codes.

## 34. Validation

Before one bounded step publishes, Execution validates:

- Execution Instance Identity;
- runtime lifecycle state;
- schema version;
- executable work reference;
- Execution Authorization Evidence references;
- Scheduler Invocation Identity;
- adapter registration;
- ExecutionInput completeness;
- source-owned Freshness Identity references required by Execution;
- Transaction Authoritative Result reference, when required;
- publication candidate completeness;
- Evidence Lifecycle requirements for Execution-owned evidence content;
- Checkpoint Recovery owner-snapshot requirements when snapshotting.

Validation failure produces explicit Execution-local failure evidence or a
waiting state. It shall not partially publish runtime state.

## 35. Verification Requirements

An accepted implementation of this RFC must verify:

- Execution Authority is singular per loaded world where implemented;
- Execution does not own Planning, Allocation, Scheduler, Transactions,
  Inventory, Checkpoint Recovery, Evidence Lifecycle, World Identity, or
  Operator Authority;
- Execution-specific identities follow the Platform Identity Model;
- Execution Authorization Evidence is consumed but not created by Execution
  unless Execution is the source owner for a future Execution-owned condition;
- one Scheduler invocation advances at most one bounded Execution step;
- adapters cannot mutate authoritative state;
- adapters cannot consume Validation Consumption Authority;
- required Transaction completion depends on Authoritative Result evidence;
- Execution publication is atomic within Execution ownership;
- Execution owner snapshots validate deterministically;
- Replay consumes the platform Replay contract;
- Recovery consumes Checkpoint Recovery;
- failure records use canonical platform failure terminology;
- no implementation uses hidden clocks, random values, filesystem ordering, or
  mutable global state for authoritative outcomes.

## 36. Architecture Validation Framework Impact

This RFC does not modify the Architecture Validation Framework.

A future implementation milestone may add Execution descriptors, ownership
contracts, persistence declarations, dependency constraints, registry
descriptors, Scheduler integration declarations, and simulation invariants to
the validation manifest. Such changes require separate owner authorization.

## 37. Compatibility With RFC-0022

RFC-0022 owns Allocation. RFC-0023 owns Execution.

Execution consumes Execution Authorization Evidence. RFC-0022 or a future
Allocation integration may produce that evidence, but Execution remains
independent of Allocation implementation.

Allocation integration remains separately gated. This RFC does not authorize:

- Allocation persistence;
- Scheduler stage 350;
- Allocation Work handlers;
- concrete provider activation;
- Planning handoff to Allocation;
- Allocation-to-Execution runtime gate;
- Commitment activation or release by Execution.

## 38. Acceptance Criteria

RFC-0023 Draft 2 is ready for owner approval when:

- platform concepts are referenced instead of redefined;
- Execution ownership is singular and explicit;
- Execution Authorization Evidence replaces Allocation-specific authority;
- Transaction integration uses Authoritative Result evidence and does not
  expose Validation Consumption Authority to Execution;
- Execution persistence content is separated from checkpoint publication,
  Evidence Lifecycle policy, Replay, and Recovery;
- replay and recovery use canonical platform terminology;
- failure and cancellation use canonical platform terminology;
- Allocation integration remains deferred;
- no implementation work is implied.

## 39. Implementation Gate

No implementation is authorized by this RFC draft.

Before implementation begins, the owner must explicitly approve:

- RFC-0023 acceptance;
- implementation milestone scope;
- Execution package and public API surface;
- Execution persistence or owner-snapshot schema;
- Architecture Validation Framework manifest updates;
- Scheduler integration;
- Transaction integration;
- Allocation integration, if any;
- migration requirements, if any.

## 40. Final Principle

Execution performs bounded authorized work.

It does not decide, allocate, validate Transactions, mutate Inventory, publish
checkpoints, recover the world, retain evidence by policy, or redefine platform
identity.

Execution is a platform consumer with one narrow authority: deterministic
runtime progression and evidence for work that another owner already approved
and another authority explicitly authorized.
