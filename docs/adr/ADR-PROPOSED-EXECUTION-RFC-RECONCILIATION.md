# Proposed ADR: RFC-0023 Draft 1 Reconciliation

Status: PROPOSED - OWNER APPROVAL REQUIRED

Decision identifier: Unassigned

Package: BCSE Architecture Hardening AH-1

Authority: This document has no authority until explicitly approved by the
project owner and recorded through the repository's accepted Decision process.
It proposes RFC replacement text but does not modify, approve, or implement
RFC-0023.

## Context

[RFC-0023 Draft 1](../RFC-0023_DETERMINISTIC_EXECUTION_ENGINE.md) proposes the
industry-neutral Deterministic Execution Engine. It correctly separates
Planning, Allocation, Execution, Transactions, Inventory, and Scheduler.

The independent audit identified contract ambiguities that must be reconciled
before implementation:

- `ExecutionInput` and `ExecutionContext` have overlapping definitions;
- "every phase executes exactly once" conflicts with optional Transaction
  work;
- Transaction proposal construction is assigned ambiguously;
- observed Transaction status is not explicitly bound to exact proposal
  content;
- several evidence identities are required but underspecified;
- save compatibility is stated without a persistence owner/checkpoint contract;
- Transaction APPLIED and Execution publication are separate cross-owner facts;
- one Execution authority is not defined; and
- Scheduler retry, ExecutionAttempt, and effect policy are not fully joined.

This proposal responds to
[BCSE-AUDIT-007](../BCSE_ARCHITECTURE_AUDIT.md#bcse-audit-007-rfc-0023-contains-duplicate-and-ambiguous-execution-contracts).

## Problem

RFC-0023 must become internally singular and compatible with the proposed:

- Planning cadence contract;
- evidence lifecycle contract;
- coordinated checkpoint/recovery contract;
- Transaction validation authority; and
- Scheduler handler effect/execution authority.

The reconciliation must preserve the RFC's ownership model and must not claim
cross-owner rollback that BCSE cannot provide.

## Current Behavior And RFC Status

- Front matter: `Status: Architecture Specification`.
- Revision: `Draft 1`.
- The RFC states that no implementation is authorized before complete review
  and approval.
- No accepted Decision authorizes RFC-0023 implementation.

This proposal keeps RFC-0023 unapproved and unimplemented.

## Architectural Constraints

The reconciliation is governed by:

- all constitutional invariants, especially `AI-0001`, `AI-0006`,
  `AI-0007`, `AI-0016` through `AI-0028`;
- DEC-0070 Transaction ownership;
- DEC-0072 Scheduler ownership;
- DEC-0073 Production ownership;
- DEC-0074 Planning ownership;
- DEC-0076 through DEC-0079 Allocation ownership; and
- every owner gate in RFC-0022 Revision 2.

Additional constraints:

- Execution performs authorized work; it does not plan or allocate;
- Transactions remain mutation authority;
- Inventory remains quantity authority;
- Execution evidence publication cannot roll back an already APPLIED
  Transaction;
- persistence depends on an accepted checkpoint contract;
- evidence retention depends on an accepted evidence-lifecycle contract;
- one schema-1 execution authority exists per world;
- no implementation begins from this proposed text.

## Options Considered

### Option 1: Implement Draft 1 Literally

Rejected by this proposal because duplicate concepts and optional phase wording
would force implementation-specific interpretation without owner approval.

### Option 2: Remove Transaction Integration From RFC-0023

Rejected because completed domain work often requires authoritative economic
mutation and Execution must explicitly observe that boundary.

### Option 3: Merge `ExecutionInput` And `ExecutionContext`

Viable, but one public type would mix caller-supplied facts with validated and
resolved internal orchestration values. It would also encourage persistence of
resolved adapter references.

### Option 4: Separate Public Input From Internal Context And Reconcile
Cross-Owner Publication

Recommended. `ExecutionInput` is the immutable invocation boundary.
`ExecutionContext` is internal and derived after validation. Canonical phase
positions remain stable while Transaction actions can be `NOT_REQUIRED`.

## Decision Proposed

Adopt **Option 4** and revise RFC-0023 as Draft 2 only after owner approval.

The following replacements are proposed. Text outside the listed sections
remains Draft 1 unless another listed replacement creates a direct conflict.

## Proposed RFC Replacement Text

### Front Matter

Affected text: status and revision at the top of RFC-0023.

Proposed replacement:

```text
Status: Architecture Specification

Revision: Draft 2 - OWNER REVIEW REQUIRED

Governing authority: CONSTITUTION.md

Dependencies: accepted Planning cadence, evidence lifecycle, coordinated
checkpoint/recovery, Transaction validation authority, Scheduler effects and
execution authority, and Allocation integration sequencing Decisions.

No implementation is authorized until this complete revision and every named
dependency have been explicitly approved by the owner.
```

Approval of this ADR authorizes preparation of Draft 2 text. It does not by
itself accept RFC-0023 or authorize implementation.

### Section 40: Primary Domain Objects

Problem: Section 40 says no additional schema-1 concepts exist but omits
`ExecutionInput`, later introduced by section 98.

Proposed replacement:

```text
### 40. Primary Domain Objects

Execution consists of the following first-class concepts:

- ExecutionInstance;
- ExecutionAttempt;
- ExecutionProgress;
- ExecutionAuthorization;
- ExecutionInput;
- ExecutionStep;
- ExecutionStepResult;
- ExecutionCompletion;
- ExecutionFailure;
- ExecutionCancellation;
- ExecutionTrace;
- ExecutionSummary;
- ExecutionReport;
- ExecutionOutcome.

ExecutionContext is an internal ephemeral orchestration value derived from one
validated ExecutionInput. It is not an authoritative definition, Runtime
record, or historical evidence record.

Schema 1 introduces no additional persisted Execution concepts.
```

### Section 45: `ExecutionContext`

Problem: Section 45 currently gives `ExecutionContext` the public-input role.

Proposed replacement:

```text
### 45. ExecutionInput And Internal ExecutionContext

ExecutionInput is the public immutable invocation boundary for one bounded
Execution evaluation.

ExecutionInput contains:

- authoritative Simulation Tick;
- ExecutionInstance reference;
- immutable Runtime view;
- ExecutionAuthorization;
- executable-work reference;
- bounded configuration;
- relevant authoritative Transaction observations;
- owner snapshot/revision references;
- schema version; and
- canonical input digest.

ExecutionInput contains caller-supplied immutable facts only. It never contains
a live service, mutable manager, resolved adapter object, wall clock, random
source, or query callback.

After input, Runtime, authorization, and reference validation, Execution Core
derives one internal ExecutionContext.

ExecutionContext contains:

- the validated ExecutionInput;
- resolved ExecutionAdapter identity and constrained adapter;
- validated definition/runtime references;
- remaining bounded-work budget;
- deterministic phase identity;
- current attempt identity; and
- internal canonical validation evidence.

ExecutionContext is immutable, ephemeral, never persisted, never exposed as
authoritative history, and never queries live external state.
```

### Sections 46 And 47: Attempt Creation And Identity

Problem: Attempt identity needs a normative deterministic derivation and retry
relationship.

Proposed replacement:

```text
### 46. ExecutionAttempt

ExecutionAttempt represents one actual bounded adapter evaluation.

Input rejection before adapter evaluation creates no ExecutionAttempt. It
creates typed invocation-rejection evidence.

WAITING, FAILED, or Scheduler reinvocation creates a later attempt only when
the accepted retry/effect policy permits another adapter evaluation.

ExecutionAttempt is immutable historical evidence.

### 47. ExecutionAttempt Identity

ExecutionAttemptId is derived deterministically from:

- ExecutionInstanceId;
- monotonic attempt sequence owned by Execution Runtime;
- authoritative Simulation Tick;
- ExecutionInput digest;
- ExecutionAdapterId; and
- schema version.

The attempt sequence increments only when adapter evaluation begins. Rejected
input and Scheduler budget deferral before evaluation do not consume a
sequence.

No random, wall-clock, or Scheduler invocation count is used.
```

### Sections 51 Through 57: Evidence Identities

Problem: completion, failure, cancellation, trace, summary, and report are
immutable but do not all have exact identity contracts.

Proposed replacement addition after each concept definition:

```text
Every Execution evidence record has an explicit stable identity.

- ExecutionCompletionId is derived from ExecutionInstanceId, terminal Runtime
  revision, completing ExecutionAttemptId, and schema version.
- ExecutionFailureId is derived from ExecutionInstanceId, resulting Runtime
  revision, causal ExecutionAttemptId or invocation-rejection identity, typed
  failure code, and schema version.
- ExecutionCancellationId is derived from ExecutionInstanceId, resulting
  Runtime revision, authoritative cancellation request identity, and schema
  version.
- ExecutionTraceId is derived from ExecutionAttemptId or invocation-rejection
  identity, phase-schema version, and trace kind.
- ExecutionSummaryId is derived from ExecutionInstanceId, covered Runtime
  revision, source evidence digest set, and summary-schema version.
- ExecutionReportId is derived from ExecutionInstanceId, resulting Runtime
  revision, causal attempt/rejection identity, outcome, and report-schema
  version.

An ExecutionInstance has at most one terminal completion, failure, or
cancellation identity for one terminal Runtime revision. Same identity with
different content is an integrity conflict.

All evidence also carries the platform SimulationCorrelationId and canonical
content digest defined by the accepted evidence-lifecycle Decision.
```

### Section 79: Attempt Creation

Proposed replacement:

```text
### 79. Attempt Creation

Execution Runtime owns the next attempt sequence.

One attempt is created atomically with transition into adapter evaluation.
Input, Runtime, authorization, adapter-resolution, or pre-evaluation budget
failure creates no attempt and does not consume the sequence.

One Scheduler reinvocation can create at most one attempt. One attempt
evaluates the adapter at most once.

Retry never reopens or rewrites a prior attempt.
```

### Sections 88 Through 92: Runtime Publication, History, Reports, Trace, Replay

Problem: publication and retention are stated independently of platform
checkpoint/evidence policy.

Proposed replacement:

```text
### 88. Execution Candidate Publication

Execution validates one complete candidate publication containing:

- next Execution Runtime;
- attempt evidence, when an attempt began;
- one phase trace with all canonical phase positions;
- one report;
- required history records;
- completion, failure, cancellation, or waiting evidence;
- exact Transaction observation, when applicable; and
- evidence/archive index updates.

Execution Core publishes its owner-local candidate atomically in memory.

This owner-local publication does not roll back or make atomic an already
APPLIED Transaction owned by the Transaction Framework. Cross-owner durable
consistency is established only by a committed platform checkpoint.

### 89. Execution History

Execution History is immutable and append-only while hot. Movement to a
content-identical archive is not a rewrite.

History retention, archival, compaction, and replay horizon follow the accepted
platform evidence-lifecycle Decision.

### 90. Execution Reports

Execution Reports are immutable evidence with explicit report identity,
correlation identity, content digest, and source references. Retention follows
the accepted evidence-lifecycle Decision.

### 91. Execution Trace

One canonical trace records every phase position as EXECUTED, NOT_REQUIRED,
SKIPPED_AFTER_FAILURE, or NOT_REACHED_DUE_TO_BUDGET. A phase position is never
omitted.

Trace retention follows the accepted evidence-lifecycle Decision.

### 92. Runtime Replay

Replay starts from an explicitly identified committed checkpoint and consumes
the ordered retained Execution, Scheduler, Allocation, and Transaction
evidence required by the platform replay horizon.

Replay never queries live state, reuses opaque Transaction validation
authority, or invokes external non-repeatable effects.
```

### Sections 97 And 98: Canonical Pipeline And Capture Input

Problem: the pipeline says every phase executes exactly once while Transaction
work is conditional.

Proposed replacement:

```text
### 97. Canonical Pipeline

The canonical phase positions are:

1. CAPTURE_INPUT;
2. VALIDATE_RUNTIME;
3. VALIDATE_AUTHORIZATION;
4. RESOLVE_ADAPTER;
5. CREATE_ATTEMPT;
6. EVALUATE_STEP;
7. VALIDATE_STEP_RESULT;
8. EVALUATE_TRANSACTION_PROPOSAL;
9. SUBMIT_TRANSACTION;
10. OBSERVE_TRANSACTION_RESULT;
11. CONSTRUCT_RUNTIME_TRANSITION;
12. VALIDATE_EXECUTION_PUBLICATION;
13. PUBLISH_EXECUTION_CANDIDATE;
14. PUBLISH_REPORT;
15. PUBLISH_HISTORY;
16. PUBLISH_TRACE.

Every invocation evaluates each canonical phase position exactly once for trace
construction. Evaluation does not mean that every phase performs an action.

A phase position records exactly one:

- EXECUTED;
- NOT_REQUIRED;
- SKIPPED_AFTER_FAILURE; or
- NOT_REACHED_DUE_TO_BUDGET.

No phase loops. No phase recursively invokes Execution. Transaction phase
positions remain present and record NOT_REQUIRED when the validated StepResult
requires no Transaction.

### 98. Capture Input

Capture Input accepts exactly one immutable ExecutionInput as defined by
section 45.

Capture verifies schema, canonical digest, required field presence, collection
ordering, and bounded size. It does not query any owner or resolve an adapter.

After successful capture, later phases consume only the captured Input and
values deterministically derived from it.
```

### Sections 101 Through 103: Adapter, Step, And Transaction Proposal

Problem: proposal construction is attributed to both adapter and Execution.

Proposed replacement:

```text
### 101. Execution Adapter

Execution resolves exactly one constrained ExecutionAdapter by stable
ExecutionAdapterId.

The adapter receives immutable internal ExecutionContext and evaluates one
bounded deterministic step.

### 102. Execution Step

The adapter returns one immutable DomainStepOutcome containing:

- domain progress or terminal recommendation;
- exact domain completion facts;
- immutable TransactionProposalData when economic mutation is required;
- no-proposal reason when mutation is not required;
- deterministic evidence;
- consumed work-unit count; and
- typed domain failure.

TransactionProposalData is data, not an EconomicTransaction and not validation
or execution authority.

The adapter never constructs or submits an authoritative EconomicTransaction,
mutates Runtime, allocates Capacity, or executes a Transaction.

### 103. Transaction Proposal

Execution Core is the sole constructor of an immutable EconomicTransaction
proposal from validated TransactionProposalData and canonical Execution
identity/correlation fields.

Execution Core submits the proposal through TransactionManager.
TransactionManager validates and applies it under the accepted Transaction
validation-authority Decision.

Execution Core never receives TransactionExecutor, Inventory mutation
capability, or opaque Transaction validation authority.

When no Transaction is required, proposal evaluation produces explicit
NOT_REQUIRED evidence; submit and observe phase positions also record
NOT_REQUIRED.
```

### Sections 104 And 119: Publication Relative To Transaction Application

Problem: "atomic publication" can be read as cross-owner rollback of an
already APPLIED Transaction.

Proposed replacement:

```text
### 104. Publication Boundaries

Three publication boundaries are distinct:

1. Transaction/Inventory owner-local in-memory application;
2. Execution owner-local in-memory candidate publication; and
3. cross-owner durable committed checkpoint publication.

Execution candidate validation completes before Transaction submission when
all candidate fields not dependent on Transaction result can be prepared.

If Transaction is APPLIED, that mutation is authoritative. Execution then
publishes Runtime and evidence that reference the exact authoritative
Transaction result.

Execution never claims to roll back an APPLIED Transaction.

If Execution owner-local publication fails after Transaction APPLIED:

- the ExecutionInstance enters a recovery-required condition;
- no second Transaction is submitted;
- the exact Transaction result and proposal digest remain authoritative;
- coordinated recovery reconstructs or completes Execution publication from
  the committed Transaction evidence; and
- unrelated ExecutionInstances remain isolated.

Cross-owner durability exists only when a coordinated checkpoint commits both
owners at one generation.

### 119. Execution Publication

ExecutionRuntimeService owns one atomic owner-local candidate swap containing
Runtime, History, Reports, Trace, and terminal/waiting evidence.

Candidate failure exposes none of that candidate. It does not undo another
owner's prior authoritative publication.

The checkpoint coordinator later commits Transaction, Inventory, Execution,
Scheduler, Allocation, and related owner snapshots as one durable generation.
```

### Section 105: Engineering Trace

Proposed replacement:

```text
### 105. Engineering Trace

Trace has one canonical entry for every section-97 phase position in canonical
order.

Each entry records:

- phase id;
- phase disposition;
- input digest;
- output/evidence digest when executed;
- work units;
- typed failure when present; and
- causal evidence identities.

Trace uses no wall-clock duration. Diagnostic-only detail may expire under the
evidence-lifecycle Decision; integrity and cited trace evidence remains
archiveable.
```

### Section 107: Replay

Proposed replacement:

```text
### 107. Replay

Replay is persistence-independent algorithmic verification when supplied an
explicit immutable baseline.

Platform recovery replay starts from a committed checkpoint and consumes only
retained ordered deltas.

Replay inputs include:

- Execution definitions;
- starting Runtime;
- ExecutionInput;
- Allocation authorization evidence;
- adapter identity and version;
- Scheduler/effect policy;
- exact observed Transaction proposal/result evidence; and
- authoritative configuration.

Replay never resubmits an external Transaction, reacquires live Capacity, or
invokes a non-repeatable side effect. An isolated replay Transaction manager
may validate equivalent proposals against an isolated compatible baseline.
```

### Section 115: Execution Context

Problem: section 115 duplicates section 45.

Proposed replacement:

```text
### 115. Execution Context

ExecutionInput and internal ExecutionContext have the single canonical meaning
defined by section 45.

Adapters receive ExecutionContext. Callers provide ExecutionInput. Neither
value is mutable or may query live external state.
```

### Sections 117 And 118: Transaction Boundary And Observation

Proposed replacement:

```text
### 117. Transaction Boundary

The adapter produces TransactionProposalData.
Execution Core constructs and submits the EconomicTransaction proposal.
TransactionManager validates and executes the proposal.
Execution observes immutable authoritative Transaction result evidence.

The result must contain and match:

- TransactionId;
- canonical proposal digest;
- validation-plan digest;
- starting Inventory revision;
- ending Inventory revision when applied;
- APPLIED/REJECTED/FAILED classification;
- application tick; and
- application digest.

Matching TransactionId alone is insufficient.

### 118. Transaction Observation

Transaction observation classifications are:

- NOT_REQUIRED;
- REJECTED;
- FAILED;
- APPLIED;
- OUTCOME_UNKNOWN.

VALIDATED is intermediate Transaction evidence and cannot complete Execution.

APPLIED satisfies a required mutation only when exact proposal and result
binding validates.

OUTCOME_UNKNOWN prevents automatic resubmission and places the attempt under
Scheduler effect/recovery rules.
```

### Sections 127, 129, 130, And 131: Runtime And Evidence Identity

Proposed replacement/addition:

```text
Execution Runtime identity remains ExecutionInstanceId. Runtime revision is a
monotonic checked owner sequence and is not a second entity identity.

HistoryRecordId is derived from ExecutionInstanceId, resulting Runtime
revision, transition kind, causal Attempt or invocation-rejection identity,
and schema version.

ExecutionReportId and ExecutionTraceId follow sections 51 through 57.

StepPublicationId is derived from ExecutionInstanceId, starting Runtime
revision, resulting Runtime revision, causal AttemptId, Transaction
observation digest or NOT_REQUIRED marker, and publication-schema version.

All same-identity/different-content cases are integrity conflicts.
```

### Sections 129 Through 137: Retention And Persistence Dependency

Proposed normative addition:

```text
Execution owns its Runtime and evidence content. The platform evidence
lifecycle owns no Execution fact; it defines retention, archival, compaction,
and query policy.

Execution persists no independent shutdown-time file set outside the accepted
checkpoint contract.

Execution owner snapshots participate in the coordinated checkpoint. Archive
movement does not change evidence identity or content.

No file name, directory, codec, or reload listener is authorized by this RFC
until the persistence milestone and checkpoint dependency are separately
approved.
```

### Section 134: Runtime Publication

Proposed replacement:

```text
### 134. Runtime Publication

ExecutionRuntimeService validates and atomically publishes one owner-local
candidate.

Candidate publication includes Runtime and required Execution evidence.
Owner-local atomicity does not imply Transaction rollback or cross-owner
durability.

The checkpoint coordinator records the resulting Execution snapshot with every
other participating owner. A generation with missing or mismatched Execution
references cannot commit.
```

### Section 136: Replay Validation

Proposed addition:

```text
Replay validation also verifies:

- starting checkpoint generation;
- ExecutionInput digest;
- every evidence identity;
- Transaction proposal/result digest and Inventory revisions;
- effect policy and invocation identity;
- retained replay horizon; and
- final checkpoint-compatible owner revision.
```

### Section 137: Engineering Evidence

Proposed addition:

```text
Engineering evidence is classified as integrity trace or diagnostic trace.
Retention follows the accepted evidence-lifecycle Decision. Expiry of
diagnostic trace cannot remove the only explanation for an authoritative
transition.
```

### Section 142: Identity Verification

Proposed replacement:

```text
### 142. Identity Verification

Verify deterministic identity and same-identity/different-content rejection
for:

- ExecutionInstance;
- ExecutionAttempt;
- invocation rejection;
- ExecutionCompletion;
- ExecutionFailure;
- ExecutionCancellation;
- ExecutionHistory record;
- ExecutionReport;
- ExecutionTrace;
- ExecutionSummary;
- StepPublication;
- Transaction proposal/result correlation; and
- checkpoint/evidence correlation.

No identity uses random UUID, wall-clock time, filesystem timestamp, or object
identity.
```

### Section 146: Execution Pipeline Verification

Proposed replacement:

```text
### 146. Execution Pipeline Verification

Verify every canonical section-97 phase position and phase disposition.

Transaction-required and Transaction-not-required scenarios produce the same
canonical trace shape.

Verify:

- one evaluation per phase position;
- no loop or recursion;
- no adapter evaluation after pre-attempt rejection;
- exactly one Attempt when adapter evaluation begins;
- explicit NOT_REQUIRED Transaction phases;
- exact proposal/result binding;
- owner-local candidate atomicity; and
- explicit recovery-required outcome after Transaction APPLIED followed by
  Execution publication failure.
```

### Section 149: Transaction Boundary Verification

Proposed replacement:

```text
### 149. Transaction Boundary Verification

Verify:

- adapter returns TransactionProposalData, not EconomicTransaction authority;
- Execution Core constructs one canonical proposal when required;
- TransactionManager validates and applies;
- Execution receives no executor or validation authority;
- observed result matches exact proposal digest and Inventory revisions;
- ID-only result matching is rejected;
- duplicate APPLIED proposal is not reapplied;
- OUTCOME_UNKNOWN is never automatically resubmitted; and
- completion requires exact APPLIED evidence when mutation is required.
```

### Sections 150 Through 152: Publication, Replay, Evidence Verification

Proposed replacement/addition:

```text
Publication verification distinguishes Transaction/Inventory owner-local
application, Execution owner-local candidate publication, and coordinated
durable checkpoint publication.

Replay verification begins from an explicit checkpoint/baseline and verifies
retained delta sufficiency.

Evidence verification validates identity, correlation, digest, classification,
retention category, archive movement, and phase-trace completeness.
```

### Section 153: Performance Verification

Proposed replacement:

```text
### 153. Sustained-Lifecycle And Performance Verification

Verify:

- millions of bounded Execution steps;
- bounded hot Runtime and evidence according to approved budgets;
- fixed archive partitioning;
- checkpoint creation during sustained work;
- restart from checkpoint plus retained deltas;
- no linear full-history rewrite on every checkpoint;
- deterministic work under representative large registries; and
- explicit capacity failure before evidence loss.

Wall-clock measurements may be reported as diagnostics but never determine
authoritative outcomes.
```

### Section 156: Save Compatibility

Proposed replacement:

```text
### 156. Checkpoint And Save Compatibility

Execution persistence is gated by the accepted coordinated checkpoint and
evidence-lifecycle Decisions.

Until a persistence milestone is separately approved:

- replay remains the persistence-independent correctness mechanism;
- no Execution file names are implied;
- no independent shutdown save contract is authorized;
- no Execution reload listener is authorized; and
- no persistence implementation is required for pure-domain milestones.

When persistence is authorized, Execution definitions, Runtime, History,
Reports, Trace, evidence indexes, and owner revisions participate in one
coordinated checkpoint generation. Unsupported schemas fail visibly.
```

### Section 159: Acceptance Criteria

Proposed additions:

```text
RFC-0023 acceptance also requires:

- accepted dependencies for checkpointing, evidence lifecycle, Transaction
  validation authority, and Scheduler effects/authority;
- exact Transaction binding;
- one Execution authority per world;
- deterministic phase trace shape;
- bounded evidence retention;
- checkpoint interruption and recovery tests;
- multi-instance authority tests;
- retry/effect semantics tests;
- replay from checkpoints plus retained deltas; and
- no claim of cross-owner rollback after Transaction APPLIED.
```

## Single Execution Authority

RFC-0023 Draft 2 should add a normative section after current section 14:

```text
### 14A. Schema-1 Execution Authority

Exactly one ExecutionAuthority exists per loaded world.

ExecutionAuthority owns:

- ExecutionRuntimeService;
- Execution pipeline/orchestrator;
- adapter registry snapshot;
- global Execution reentrancy guard;
- current attempt/publication state;
- Scheduler invocation boundary; and
- checkpoint quiescence participation.

Schema 1 is sequential. No recursive or parallel Execution is permitted.
Modules receive constrained submission/query contracts and cannot construct
another authoritative orchestrator.
```

The section number would be normalized during Draft 2 editing. No permanent
number is assigned by this proposed ADR.

## Retry Semantics

RFC-0023 Draft 2 should add a normative section after current section 27:

```text
### Retry And Reinvocation

Scheduler reinvocation is not automatically an Execution retry.

- Scheduler budget deferral before adapter evaluation creates no Attempt.
- WAITING can schedule later work without marking the current Attempt failed.
- A new adapter evaluation creates a new monotonic Attempt.
- FAILED is terminal unless an owning-domain policy creates a new
  ExecutionInstance or explicitly nonterminal failure state.
- Transaction rejection does not imply automatic retry.
- Transaction OUTCOME_UNKNOWN prohibits automatic resubmission.
- Handler effect policy determines whether Scheduler may reinvoke.
- No retry is silent; every new Attempt records its causal prior evidence.
```

## Sustained-Lifecycle Acceptance Tests

Draft 2 acceptance must include:

- millions of bounded steps without unbounded hot evidence;
- evidence archive and compaction boundaries;
- crash at every checkpoint phase;
- recovery to last committed generation;
- exact same-ID/different-body Transaction rejection;
- Transaction APPLIED followed by Execution publication failure;
- two Execution orchestrators targeting one authority;
- recursive and parallel invocation rejection;
- every effect-type retry/deferral combination;
- deterministic NOT_REQUIRED Transaction phase evidence;
- replay from retained checkpoint plus deltas; and
- zero dependency on wall-clock time or player presence.

## Rationale

The proposed separation keeps the public invocation surface free of resolved
runtime collaborators while giving adapters a validated internal context.
Canonical phase positions preserve deterministic trace shape without
pretending optional Transaction actions occurred.

Explicit cross-owner publication boundaries preserve Transaction authority and
avoid an impossible rollback claim. Checkpoint and evidence dependencies keep
RFC-0023 from creating another independent persistence island.

## Consequences

### Positive Consequences

- Input and context have singular meanings.
- Transaction proposal ownership is explicit.
- ID-only Transaction observation is prohibited.
- Evidence identities become deterministic and complete.
- Optional Transaction work has deterministic trace representation.
- Owner-local and cross-owner publication are distinguished.
- Persistence aligns with the platform checkpoint.
- Retry aligns with Scheduler effect policy.

### Negative Consequences

- Draft 1 requires a broad editorial revision.
- Execution persistence cannot proceed independently.
- Adapter output changes from a proposal to proposal data.
- Recovery-required state after APPLIED/publication failure adds lifecycle
  complexity.
- More evidence identities and phase dispositions must be modeled.
- RFC approval depends on five other hardening decisions.

## Compatibility

No implementation exists, so source or save compatibility is not affected.
The revision changes a draft architecture contract before API lock-in.

RFC-0022 Allocation authority and accepted Decisions remain unchanged.

## Migration

There is no Execution runtime migration because RFC-0023 is unimplemented.
Draft migration consists only of:

1. owner approval of this reconciliation;
2. preparation of Draft 2 with tracked section changes;
3. independent review against accepted hardening Decisions;
4. architecture validation manifest proposal after RFC acceptance; and
5. separate milestone authorization.

No manifest or accepted Decision changes occur in this task.

## Failure Behavior

The reconciled RFC requires explicit failure for:

- invalid input/context construction;
- same evidence identity with different content;
- Transaction proposal/result mismatch;
- unknown Transaction outcome;
- Transaction APPLIED followed by Execution publication failure;
- duplicate authority or recursive invocation;
- missing replay evidence;
- unsupported checkpoint/evidence schema; and
- retention capacity exhaustion.

No failure implies cross-owner rollback.

## Replay Implications

Replay begins from an explicit baseline or committed checkpoint. It consumes
recorded Allocation authorization, effect policy, Transaction result, and
Execution evidence. It never invokes external effects or infers status from an
ID.

## Security And Integrity Implications

- Adapters receive no mutation capability.
- Exact digests prevent proposal/result substitution.
- One authority prevents module-created concurrent orchestration.
- Canonical identities prevent random or object-identity coupling.
- Checkpoint manifests prevent mixed-generation Execution loading.
- Evidence retention cannot silently remove the replay horizon.

## Testing Requirements

The proposed section replacements and sustained-lifecycle list are normative
testing requirements if approved. Draft 2 review must map every requirement to
a future milestone and test category before implementation authorization.

## Alternatives Rejected By This Proposal

- Implementing Draft 1 with implicit interpretation.
- Persisting `ExecutionContext`.
- Giving adapters authoritative Transaction proposal/submission capability.
- Treating optional Transaction phases as absent from trace.
- Matching Transaction results by ID only.
- Claiming Execution can roll back an APPLIED Transaction.
- Defining independent Execution shutdown files.
- Allowing multiple orchestrators with per-instance guards.

## Unresolved Questions

Owner decisions required:

1. Confirm the proposed `ExecutionInput`/`ExecutionContext` split.
2. Confirm `DomainStepOutcome` and `TransactionProposalData` terminology.
3. Confirm all canonical phase positions and dispositions.
4. Confirm recovery-required lifecycle representation after
   APPLIED/publication failure.
5. Confirm evidence identity derivation fields.
6. Confirm whether ExecutionFailure is always terminal or may include a
   nonterminal recoverable condition under another name.
7. Confirm whether Draft 2 keeps current part/section organization or performs
   a separately reviewed editorial consolidation.
8. Confirm that pure Execution milestones may proceed only after dependency
   Decisions are accepted.

## Owner Approval Checklist

- [ ] Approve Draft 2 preparation, not RFC implementation.
- [ ] Approve `ExecutionInput`/`ExecutionContext` separation.
- [ ] Approve deterministic phase positions and `NOT_REQUIRED`.
- [ ] Approve Transaction proposal ownership.
- [ ] Approve exact Transaction result binding.
- [ ] Approve evidence identity contracts.
- [ ] Approve checkpoint/evidence persistence dependency.
- [ ] Approve publication-failure reconciliation.
- [ ] Approve one Execution authority.
- [ ] Approve retry/attempt semantics.
- [ ] Approve sustained-lifecycle tests.
- [ ] Authorize a tracked RFC-0023 Draft 2 revision.
- [ ] Separately review and accept Draft 2.
- [ ] Separately authorize implementation milestones.
