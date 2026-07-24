# ADR-03: Transaction Validation Authority

Status: RATIFIED ARCHITECTURAL DIRECTION - IMPLEMENTATION NOT AUTHORIZED

Decision identifier: AH-1-ADR-03

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

DEC-0070 establishes the Transaction Framework as the universal owner of
economic mutation. Validation produces an accepted deterministic change plan,
and execution requires matching previously accepted validation.

The current lower-level `TransactionExecutor` checks that:

- the supplied Transaction has `VALIDATED` status;
- validation is accepted; and
- validation's `TransactionId` equals the Transaction's id.

`TransactionValidation` contains the accepted Inventory changes but no
canonical Transaction proposal digest, Inventory Freshness Identity,
validation issuance identity, or consumption state. Current
`TransactionManager` validates and executes in an immediate serialized path,
which limits ordinary exposure. The lower-level contract remains weaker than
the stated invariant.

This decision responds to
[BCSE-AUDIT-003](../BCSE_ARCHITECTURE_AUDIT.md#bcse-audit-003-accepted-transaction-validation-is-bound-only-by-id).

## Problem

Accepted validation for Transaction body A must never authorize Transaction
body B merely because both use the same `TransactionId`.

The contract must also reject:

- validation against stale Inventory state;
- duplicate authority use;
- authority reuse after restart;
- ambiguous replay;
- same-id/different-body submission; and
- an Execution handoff that infers Transaction success from an id alone.

## Current Behavior

- `TransactionManager` validates a submitted Transaction, changes its status
  to `VALIDATED`, and immediately calls `TransactionExecutor`.
- `TransactionExecutor` matches validation to Transaction by ID.
- Accepted validation carries ordered Inventory changes.
- Inventory stages all accepted changes before commit.
- Structurally valid submissions remain in deterministic history.
- Replay creates fresh validation against an explicitly supplied baseline.
- No Inventory Freshness Identity is part of validation.
- Validation is an ordinary immutable public value and is not single-use.

This decision does not change Transaction code, API visibility, or runtime
behavior.

## Architectural Constraints

The decision is governed by:

- `AI-0001` Deterministic Simulation;
- `AI-0006` Universal Economic Transactions;
- `AI-0007` Transaction-Owned Inventory Mutation;
- `AI-0010` Immutable Public Views;
- `AI-0017` Validation Before Execution;
- `AI-0020` Stable Identity Contracts;
- `AI-0021` Explicit Failure Outcomes;
- `AI-0025` Singular Data Ownership;
- `AI-0027` Tests Are Part Of The Contract; and
- `AI-0028` Backward-Compatible Evolution.

Additional constraints:

- Transaction remains mutation authority;
- Inventory remains quantity and freshness-identity authority;
- validation authority is issued only by `TransactionManager`;
- adapters submit proposals and receive results, not mutation capability;
- no random token or wall-clock expiry;
- exact proposal identity is canonical and replay-stable; and
- implementation requires separate owner authorization.

Transaction Validation remains owned exclusively by the Transaction subsystem.
It does not own Inventory, Production, Scheduler, Checkpoint Recovery,
Evidence Lifecycle, Execution, Allocation, or persistence-generation
selection.

## Options Considered

### Option 1: Canonical Transaction Content Digest

Store the canonical Transaction digest in `TransactionValidation` and compare
it during execution.

Advantages:

- deterministic and persistable;
- rejects same-ID/different-body;
- useful for replay and diagnostics.

Disadvantages:

- does not detect Inventory changes after validation;
- a copied accepted validation can still be reused;
- digest schema becomes a public compatibility contract.

### Option 2: Runtime Validation Consumption Authority

Use private runtime authority issued and consumed only by the Transaction
owner.

Advantages:

- prevents direct executor use and duplicate consumption;
- keeps validation consumption authority inside the owner.

Disadvantages:

- runtime authority identity alone is not replayable;
- restart invalidates all authorities;
- without a digest, diagnostics cannot prove exact proposal binding.

### Option 3: One Synchronized Manager Critical Section

Make validation valid only inside the serialized submit boundary.

Advantages:

- simple;
- matches the current immediate path;
- no stale interval in normal use.

Disadvantages:

- not an explicit evidence contract;
- blocks future asynchronous or staged handoff;
- public lower-level objects remain forgeable;
- does not identify exact starting Inventory state.

### Option 4: Transaction Digest Plus Inventory Freshness Identity

Bind validation to both canonical Transaction content and the authoritative
Inventory state examined by validation.

Advantages:

- rejects different content and stale state;
- replay inputs are explicit;
- result can prove exact state transition.

Disadvantages:

- Inventory needs a complete freshness identity contract;
- reusable accepted evidence remains possible unless consumption is owned.

### Option 5: Composite Authority

Combine:

- canonical Transaction digest;
- authoritative Inventory Freshness Identity;
- deterministic validation plan digest;
- manager-issued single-use Validation Consumption Authority; and
- one Serialized Transaction-owner Boundary.

Advantages:

- closes content, staleness, reuse, and ownership gaps;
- retains replayable evidence while keeping capability private.

Disadvantages:

- largest API and migration change;
- requires Inventory freshness and canonical serialization contracts.

## Decision

Adopt **Option 5: composite validation authority**.

### Proposal Digest

Every submitted Transaction has a Proposal Digest over canonical schema bytes
that include:

- Transaction schema version;
- Transaction id;
- authoritative submission tick;
- Transaction type;
- source/origin;
- actor and business references;
- ordered Inventory changes;
- Good ids, units, and exact quantities;
- Order, Contract, Production, Allocation, and Execution references when
  present;
- canonical metadata entries; and
- every other field that can affect validation or execution.

Status is excluded from the proposal digest because status is manager-owned
runtime. Display strings and diagnostics are excluded unless they affect
behavior.

Canonical serialization must be versioned, documented, and tested. Unknown
fields cannot be ignored when they can affect execution.

### Inventory Freshness Identity

Inventory owns the Inventory Freshness Identity. The identity shall uniquely
and deterministically represent all authoritative Inventory state examined by
validation.

The implementation may later use a global revision, scoped revisions, revision
vectors, digest-backed snapshots, or another deterministic representation,
provided the identity completely covers every Inventory state dependency
examined during validation.

The freshness identity is not an Inventory quantity and cannot be set by
Transactions or adapters. It is part of the coordinated checkpoint and the
Platform Determinism Manifest policy identity when relevant.

### Validation Plan Digest

Accepted validation contains immutable evidence:

- Transaction id;
- Transaction proposal digest;
- Inventory Freshness Identity;
- ordered staged Inventory changes;
- canonical validation-plan digest;
- accepted simulation tick;
- validator schema version; and
- validation diagnostics.

The plan digest covers the proposal digest, Inventory Freshness Identity, and
ordered staged changes.

Once validation succeeds, the approved validation plan and its identity are
immutable. Any proposal, context, freshness identity, or plan change requires a
new validation result.

Validation must not depend on hidden runtime context. Every fact required to
reproduce or verify the decision must be represented by the proposal, explicit
validation inputs, Inventory Freshness Identity, validation plan, or resulting
evidence.

### Validation Consumption Authority

The Transaction owner issues a private Validation Consumption Authority only
after accepted validation. The exact Java name remains subject to
implementation review.

The authority is:

- manager-instance scoped;
- world scoped;
- bound to one proposal digest;
- bound to one Inventory Freshness Identity;
- bound to one validation-plan digest;
- single-use;
- non-serializable;
- not exposed to Planning, Production, Allocation, Execution adapters, or
  clients; and
- invalid after manager restart or checkpoint reload.

Possession of immutable `TransactionValidation` evidence alone does not grant
mutation authority.

### Serialized Transaction-owner Boundary

Validation, authority issuance, final freshness check, authority consumption,
and execution occur inside one serialized Transaction-owner boundary.

Immediately before applying staged changes, the manager/executor verifies:

1. authority belongs to this manager/world;
2. authority has not been consumed;
3. supplied Transaction digest matches;
4. validation proposal digest matches;
5. validation plan digest matches;
6. Inventory current freshness identity equals the validated freshness
   identity;
7. Transaction status is `VALIDATED`; and
8. Transaction id has no conflicting accepted or applied digest.

Authority is consumed before Inventory commit begins. An unexpected commit
failure produces explicit failed application evidence and cannot authorize a
retry with the same authority.

### Result Evidence

An authoritative Transaction result records:

- Transaction id;
- proposal digest;
- Inventory Freshness Identity;
- validation plan digest;
- final Transaction status;
- application simulation tick;
- ordered applied-change digest;
- duplicate/idempotency classification; and
- typed failure.

Future Execution observes this evidence. It never infers application from
`TransactionId` or status alone.

Relevant resulting Inventory freshness evidence is recorded when the
Transaction applies. The exact representation remains owned by Inventory.

### Validation Lifetime

Accepted authority remains valid only:

- inside the issuing manager instance;
- while Inventory remains at the validated freshness identity;
- until first execution attempt;
- before checkpoint restart/reload; and
- while no same-id conflict exists.

There is no wall-clock expiry.

### Duplicate Submission

For a Transaction ID already known:

- same Transaction ID and same canonical Proposal Identity observes the
  existing authoritative result without reapplying Inventory changes;
- same Transaction ID and different canonical Proposal Identity is an
  explicit architectural conflict;
- a conflicting proposal must not overwrite, reinterpret, retry, or reuse the
  existing Transaction ID;
- a materially new attempt requires a new Transaction ID; and
- concurrent identical submission is serialized by the Transaction owner.

### Replay

Replay does not persist or reuse runtime authority tokens. It:

1. starts from an explicitly identified recovered baseline;
2. verifies the historical Transaction proposal digest;
3. performs fresh deterministic validation;
4. verifies that the new validation plan digest equals historical evidence;
5. executes through a replay-owned manager authority; and
6. verifies resulting freshness evidence and application digest.

Replay divergence fails explicitly.

### Execution Adapter Handoff

An Execution adapter may provide immutable domain outcome and Transaction
proposal data. The Execution orchestrator submits the canonical proposal to
`TransactionManager`.

Neither adapter nor Execution receives:

- `TransactionExecutor`;
- `InventoryManager` mutation capability;
- Validation Consumption Authority; or
- accepted staged Inventory changes as an executable capability.

Execution receives only immutable authoritative Transaction result evidence
bound to the exact proposal digest it submitted.

Checkpoint Recovery may consume immutable Transaction snapshots and coordinate
their publication. It must not define Transaction validation rules, issue
Transaction authority, or mutate Transaction-owned state.

## Rationale

No single option except the composite contract closes all four boundaries:

- content identity;
- authoritative-state freshness;
- capability ownership; and
- replay evidence.

The Serialized Transaction-owner Boundary preserves the current simple
synchronous path without prescribing a Java locking mechanism. Digest and
freshness evidence make the contract explicit enough for future Execution
without granting the adapter a mutation token.

## Consequences

### Positive Consequences

- Same-ID/different-body authorization is impossible through the accepted API.
- Stale validation cannot apply against changed Inventory.
- Validation cannot be reused.
- Execution observes proof for the exact proposal.
- Replay validates proposal and plan equivalence.
- Mutation authority remains inside Transaction ownership.
- Duplicate identical submission becomes explicitly idempotent.

### Negative Consequences

- Canonical Transaction serialization becomes a compatibility contract.
- Inventory gains a persisted freshness identity.
- Transaction history/result schema gains digests and freshness evidence.
- Lower-level executor APIs must be restricted or changed.
- Existing history requires digest/freshness migration.
- A manager restart invalidates pending accepted validation.

## Compatibility

This strengthens DEC-0070 without changing its ownership decision.

Existing Transaction IDs remain stable. Existing Transaction records without
digests or freshness evidence remain migration inputs, not fully bound
evidence.

## Migration

Migration must:

1. load and validate current Inventory and Transaction history together inside
   one checkpoint migration;
2. establish a deterministic baseline Inventory Freshness Identity;
3. replay or validate ordered APPLIED history against an explicit compatible
   baseline when available;
4. calculate canonical proposal and application digests;
5. assign or derive starting/ending freshness evidence in authoritative
   application order;
6. mark unverifiable legacy history explicitly rather than fabricating proof;
7. publish Inventory and Transaction migration in one checkpoint generation;
8. persist no Validation Consumption Authority; and
9. leave legacy files unchanged if reconciliation fails.

If current Inventory cannot be reconciled with retained APPLIED history,
migration requires explicit owner recovery policy. It cannot reset Inventory
or discard history.

## Failure Behavior

Proposed explicit outcomes:

- `TRANSACTION_PROPOSAL_DIGEST_MISMATCH`;
- `TRANSACTION_VALIDATION_PLAN_MISMATCH`;
- `TRANSACTION_STALE_VALIDATION`;
- `TRANSACTION_VALIDATION_AUTHORITY_INVALID`;
- `TRANSACTION_VALIDATION_AUTHORITY_CONSUMED`;
- `TRANSACTION_INVENTORY_FRESHNESS_MISMATCH`;
- `TRANSACTION_IDENTITY_CONFLICT`;
- `TRANSACTION_DUPLICATE_APPLIED`;
- `TRANSACTION_APPLICATION_DIGEST_MISMATCH`;
- `TRANSACTION_FRESHNESS_IDENTITY_INVALID`; and
- `TRANSACTION_LEGACY_BINDING_UNVERIFIABLE`.

Failure-code names are architectural contract names, not implemented
constants.

Validation failure leaves Inventory unchanged. An unexpected failure after
authority consumption cannot reuse the authority. If Inventory commit
published before result publication failed, coordinated checkpoint/recovery
must reconcile the authoritative Inventory freshness evidence and application
evidence; the caller cannot retry by ID alone.

Once mutation authority has been consumed, any failure before a complete
authoritative result is published must be explicit, recoverable through the
accepted checkpoint model, and must never permit silent reapplication.

## Replay Implications

Proposal digest, Inventory Freshness Identity, and validation-plan digest
become required replay inputs and outputs. Replay is stronger because it
proves equivalent content and state, not only equivalent IDs.

Runtime validation authority is deliberately absent from replay and
persistence.

## Security And Integrity Implications

- A forged immutable validation record grants no mutation capability.
- A client cannot acquire manager authority.
- Same-ID substitution is rejected by digest.
- Stale-state execution is rejected by freshness identity.
- Single-use authority blocks repeated application.
- Canonical digest validation detects field omission or reordering.
- SHA-256 is an integrity primitive, not identity secrecy or authentication.

## Testing Requirements

Required automated tests:

- canonical digest equality for identical proposals;
- digest difference for every behavior-affecting field;
- metadata canonical ordering;
- same ID/different body rejection;
- accepted validation with different Transaction rejection;
- stale Inventory freshness rejection;
- authority from another manager/world rejection;
- authority reuse rejection;
- authority invalid after restart;
- one coherent resulting freshness identity for a multi-change Transaction;
- no freshness advancement on rejection/failure;
- duplicate identical applied submission returns existing result;
- concurrent duplicate submission;
- invalid freshness identity rejection;
- plan-digest tampering;
- result evidence bound to exact proposal;
- Execution handoff receives no authority;
- replay digest and freshness equivalence;
- replay divergence;
- migration from legacy history;
- unverifiable migration remains fail-visible; and
- checkpoint Transaction/Inventory freshness agreement.

## Alternatives Rejected By This Proposal

- **Digest only:** rejected because it permits stale and reusable validation.
- **Runtime authority only:** rejected because it provides insufficient durable
  and replay evidence.
- **Serialized boundary only:** rejected because the exact accepted proposal
  and state remain implicit.
- **Digest plus Inventory freshness without authority:** rejected because an
  immutable accepted value can still be presented repeatedly.

## Ratification Notes

Owner ratification approved Transaction Validation Authority with the
revisions incorporated above:

1. Transaction validation binds three independent identities: Proposal Digest,
   Inventory Freshness Identity, and Validation Plan Digest.
2. The architecture does not ratify a global Inventory counter. Inventory may
   later implement freshness by any deterministic representation that covers
   every authoritative Inventory dependency examined by validation.
3. Validation and application occur within a Serialized Transaction-owner
   Boundary without prescribing a Java lock mechanism.
4. Runtime Validation Consumption Authority is private, single-use,
   Transaction-owned, non-persisted, and invalid after rollback or restart.
5. Immutable validation evidence alone grants no mutation authority.
6. Duplicate Transaction ID behavior is identity-bound: identical proposal
   observes the existing authoritative result; conflicting proposal is an
   architectural conflict; a materially new attempt requires a new
   Transaction ID.
7. Once validation succeeds, the approved validation plan and identity are
   immutable, and validation may not depend on hidden runtime context.
8. Post-consumption failure semantics are limited to the invariant that silent
   reapplication is prohibited and recovery must use the accepted checkpoint
   model.

Implementation, migration, Java API changes, lower-level executor visibility
changes, RFC-0023 reconciliation, Execution integration, Allocation
integration, and gameplay remain separately gated.
