# Proposed ADR: Transaction Validation Authority

Status: PROPOSED - OWNER APPROVAL REQUIRED

Decision identifier: Unassigned

Package: BCSE Architecture Hardening AH-1

Authority: This document has no authority until explicitly approved by the
project owner and recorded through the repository's accepted Decision process.

## Context

DEC-0070 establishes the Transaction Framework as the universal owner of
economic mutation. Validation produces an accepted deterministic change plan,
and execution requires matching previously accepted validation.

The current lower-level `TransactionExecutor` checks that:

- the supplied Transaction has `VALIDATED` status;
- validation is accepted; and
- validation's `TransactionId` equals the Transaction's id.

`TransactionValidation` contains the accepted Inventory changes but no
canonical Transaction content digest, Inventory revision, validation issuance
identity, or consumption state. Current `TransactionManager` validates and
executes in a synchronized immediate path, which limits ordinary exposure.
The lower-level contract remains weaker than the stated invariant.

This proposal responds to
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
- No Inventory-wide authoritative revision is part of validation.
- Validation is an ordinary immutable public value and is not single-use.

This proposal does not change Transaction code, API visibility, or runtime
behavior.

## Architectural Constraints

The proposal is governed by:

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
- Inventory remains quantity and revision authority;
- validation authority is issued only by `TransactionManager`;
- adapters submit proposals and receive results, not mutation capability;
- no random token or wall-clock expiry;
- exact proposal identity is canonical and replay-stable; and
- implementation requires separate owner authorization.

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

### Option 2: Opaque Single-Use Manager Authority

Return an opaque authority object issued and consumed only by
`TransactionManager`.

Advantages:

- prevents direct executor use and duplicate consumption;
- keeps execution authority inside the owner.

Disadvantages:

- opaque runtime identity alone is not replayable;
- restart invalidates all authorities;
- without a digest, diagnostics cannot prove exact proposal binding.

### Option 3: One Synchronized Manager Critical Section

Make validation valid only inside the synchronized submit method.

Advantages:

- simple;
- matches the current immediate path;
- no stale interval in normal use.

Disadvantages:

- not an explicit evidence contract;
- blocks future asynchronous or staged handoff;
- public lower-level objects remain forgeable;
- does not identify exact starting Inventory state.

### Option 4: Transaction Digest Plus Inventory Revision

Bind validation to both canonical Transaction content and the authoritative
Inventory starting revision.

Advantages:

- rejects different content and stale state;
- replay inputs are explicit;
- result can prove exact state transition.

Disadvantages:

- Inventory needs a global or scoped revision contract;
- reusable accepted evidence remains possible unless consumption is owned.

### Option 5: Composite Authority

Combine:

- canonical Transaction digest;
- authoritative Inventory starting revision;
- deterministic validation plan digest;
- manager-issued opaque single-use authority; and
- one manager-owned execution critical section.

Advantages:

- closes content, staleness, reuse, and ownership gaps;
- retains replayable evidence while keeping capability private.

Disadvantages:

- largest API and migration change;
- requires Inventory revision and canonical serialization contracts.

## Decision Proposed

Adopt **Option 5: composite validation authority**.

### Canonical Transaction Digest

Every submitted Transaction has a SHA-256 digest over canonical schema bytes
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

### Inventory Revision

Inventory owns a monotonically increasing `InventoryStateRevision` for each
authoritative Inventory manager/world.

Schema-1 rules:

- initial validated loaded state has revision zero or its persisted revision;
- one successfully APPLIED Transaction increments the revision exactly once;
- a failed or rejected Transaction does not increment it;
- a multi-change Transaction has one starting and one ending revision;
- revision arithmetic is checked;
- replay against a compatible baseline reproduces the same revision sequence;
  and
- revision is part of the coordinated checkpoint.

The revision is not an Inventory quantity and cannot be set by Transactions or
adapters.

### Validation Plan Digest

Accepted validation contains immutable evidence:

- Transaction id;
- Transaction proposal digest;
- Inventory starting revision;
- ordered staged Inventory changes;
- canonical validation-plan digest;
- accepted simulation tick;
- validator schema version; and
- validation diagnostics.

The plan digest covers the proposal digest, starting revision, and ordered
staged changes.

### Opaque Execution Authority

`TransactionManager` issues an opaque `TransactionValidationAuthority` only
after accepted validation. The exact Java name remains subject to
implementation review.

The authority is:

- manager-instance scoped;
- world scoped;
- bound to one proposal digest;
- bound to one starting Inventory revision;
- bound to one validation-plan digest;
- single-use;
- non-serializable;
- not exposed to Planning, Production, Allocation, Execution adapters, or
  clients; and
- invalid after manager restart or checkpoint reload.

Possession of immutable `TransactionValidation` evidence alone does not grant
execution authority.

### Manager-Owned Critical Section

Validation, authority issuance, final revision check, authority consumption,
and execution occur inside one `TransactionManager` critical section.

Immediately before applying staged changes, the manager/executor verifies:

1. authority belongs to this manager/world;
2. authority has not been consumed;
3. supplied Transaction digest matches;
4. validation proposal digest matches;
5. validation plan digest matches;
6. Inventory current revision equals starting revision;
7. Transaction status is `VALIDATED`; and
8. Transaction id has no conflicting accepted or applied digest.

Authority is consumed before Inventory commit begins. An unexpected commit
failure produces explicit failed application evidence and cannot authorize a
retry with the same authority.

### Result Evidence

An authoritative Transaction result records:

- Transaction id;
- proposal digest;
- validation plan digest;
- starting Inventory revision;
- ending Inventory revision, when applied;
- final Transaction status;
- application simulation tick;
- ordered applied-change digest;
- duplicate/idempotency classification; and
- typed failure.

Future Execution observes this evidence. It never infers application from
`TransactionId` or status alone.

### Validation Lifetime

Accepted authority remains valid only:

- inside the issuing manager instance;
- while Inventory remains at the starting revision;
- until first execution attempt;
- before checkpoint restart/reload; and
- while no same-id conflict exists.

There is no wall-clock expiry.

### Duplicate Submission

For a Transaction ID already known:

- same canonical proposal digest and APPLIED result returns immutable existing
  result evidence without reapplying Inventory changes;
- same canonical proposal digest in a terminal failed/rejected state returns
  that existing terminal evidence unless an explicit future resubmission
  contract permits otherwise;
- different digest returns `TRANSACTION_IDENTITY_CONFLICT`; and
- concurrent identical submission is serialized by the manager.

### Replay

Replay does not persist or reuse opaque authority. It:

1. starts from an explicitly identified compatible Inventory revision;
2. verifies the historical Transaction proposal digest;
3. performs fresh deterministic validation;
4. verifies that the new validation plan digest equals historical evidence;
5. executes through a replay-owned manager authority; and
6. verifies ending revision and application digest.

Replay divergence fails explicitly.

### Execution Adapter Handoff

An Execution adapter may provide immutable domain outcome and Transaction
proposal data. The Execution orchestrator submits the canonical proposal to
`TransactionManager`.

Neither adapter nor Execution receives:

- `TransactionExecutor`;
- `InventoryManager` mutation capability;
- opaque validation authority; or
- accepted staged Inventory changes as an executable capability.

Execution receives only immutable authoritative Transaction result evidence
bound to the exact proposal digest it submitted.

## Rationale

No single option except the composite contract closes all four boundaries:

- content identity;
- authoritative-state freshness;
- capability ownership; and
- replay evidence.

The manager critical section preserves the current simple synchronous path.
Digest and revision evidence make the contract explicit enough for future
Execution without granting the adapter a mutation token.

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
- Inventory gains a persisted revision.
- Transaction history/result schema gains digests and revisions.
- Lower-level executor APIs must be restricted or changed.
- Existing history requires digest/revision migration.
- A manager restart invalidates pending accepted validation.

## Compatibility

This strengthens DEC-0070 without changing its ownership decision. It requires
a new accepted Decision because it changes Transaction validation and public
contract semantics.

Existing Transaction IDs remain stable. Existing Transaction records without
digests/revisions remain migration inputs, not fully bound evidence.

## Migration

Migration must:

1. load and validate current Inventory and Transaction history together inside
   one checkpoint migration;
2. establish a deterministic baseline Inventory revision;
3. replay or validate ordered APPLIED history against an explicit compatible
   baseline when available;
4. calculate canonical proposal and application digests;
5. assign starting/ending revisions in authoritative application order;
6. mark unverifiable legacy history explicitly rather than fabricating proof;
7. publish Inventory and Transaction migration in one checkpoint generation;
8. persist no opaque validation authorities; and
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
- `TRANSACTION_INVENTORY_REVISION_MISMATCH`;
- `TRANSACTION_IDENTITY_CONFLICT`;
- `TRANSACTION_DUPLICATE_APPLIED`;
- `TRANSACTION_APPLICATION_DIGEST_MISMATCH`;
- `TRANSACTION_REVISION_OVERFLOW`; and
- `TRANSACTION_LEGACY_BINDING_UNVERIFIABLE`.

Failure-code names are proposed contract names.

Validation failure leaves Inventory unchanged. An unexpected failure after
authority consumption cannot reuse the authority. If Inventory commit
published before result publication failed, coordinated checkpoint/recovery
must reconcile the authoritative Inventory revision and application evidence;
the caller cannot retry by ID alone.

## Replay Implications

Proposal digest, validation-plan digest, and Inventory revisions become
required replay inputs and outputs. Replay is stronger because it proves
equivalent content and state, not only equivalent IDs.

The opaque runtime authority is deliberately absent from replay and
persistence.

## Security And Integrity Implications

- A forged immutable validation record grants no mutation capability.
- A client cannot acquire manager authority.
- Same-ID substitution is rejected by digest.
- Stale-state execution is rejected by revision.
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
- stale Inventory revision rejection;
- authority from another manager/world rejection;
- authority reuse rejection;
- authority invalid after restart;
- exactly one revision increment for a multi-change Transaction;
- no revision increment on rejection/failure;
- duplicate identical applied submission returns existing result;
- concurrent duplicate submission;
- checked revision overflow;
- plan-digest tampering;
- result evidence bound to exact proposal;
- Execution handoff receives no authority;
- replay digest and revision equivalence;
- replay divergence;
- migration from legacy history;
- unverifiable migration remains fail-visible; and
- checkpoint Transaction/Inventory revision agreement.

## Alternatives Rejected By This Proposal

- **Digest only:** rejected because it permits stale and reusable validation.
- **Opaque authority only:** rejected because it provides insufficient durable
  and replay evidence.
- **Critical section only:** rejected because the exact accepted proposal and
  state remain implicit.
- **Digest plus Inventory revision without authority:** rejected because an
  immutable accepted value can still be presented repeatedly.

## Unresolved Questions

Owner decisions required:

1. Approve a global Inventory revision or choose scoped per-inventory
   revisions.
2. Approve SHA-256 and canonical Transaction schema as the binding contract.
3. Confirm whether failed post-consumption authority creates a terminal
   Transaction failure or a recovery-required state.
4. Confirm migration behavior when the original Inventory baseline is absent.
5. Confirm whether same-digest terminal failure may ever be resubmitted under a
   new Transaction ID only.
6. Confirm lower-level `TransactionExecutor` visibility after implementation.

## Owner Approval Checklist

- [ ] Approve composite validation authority.
- [ ] Approve canonical Transaction digest fields.
- [ ] Approve Inventory revision ownership.
- [ ] Approve validation-plan digest.
- [ ] Approve manager-scoped single-use authority.
- [ ] Approve duplicate and stale behavior.
- [ ] Approve Execution handoff boundary.
- [ ] Approve checkpoint and migration requirements.
- [ ] Approve failure codes and tests.
- [ ] Authorize creation of an accepted Decision record.
- [ ] Separately authorize implementation.

