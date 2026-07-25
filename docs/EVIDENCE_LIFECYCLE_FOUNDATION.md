# Evidence Lifecycle Foundation

Status: IM-002 pure Java foundation implemented.

This foundation implements the first Evidence Lifecycle primitives only. It does
not implement archival storage, filesystem layout, checkpoint publication,
rollback, recovery, save migration, subsystem pruning, gameplay behavior, or
operator archive workflows.

## Ownership Boundary

Evidence Lifecycle owns:

- Canonical evidence classification primitives.
- Evidence owner and source metadata primitives.
- Evidence identity validation and content-conflict detection.
- Versioned retention-policy inputs.
- Deterministic retention-decision evaluation.
- Typed lifecycle failure reporting.

Evidence Lifecycle does not own:

- Planning artifacts, Planning Cycles, or Planning publication.
- Transaction validation, Transaction mutation authority, or Transaction history.
- Scheduler runtime state, effect enforcement, or invocation authority.
- Production facts, Production Plans, or Production Runs.
- Allocation reports, traces, history, commitments, or provider observations.
- Inventory quantities or Inventory Freshness Identity.
- Execution runtime, Execution evidence content, or Execution completion rules.
- Checkpoint generation publication, recovery, rollback, or archive movement.

Every evidence descriptor retains exactly one originating fact owner. The
descriptor may classify owner evidence and evaluate a lifecycle request, but it
does not carry subsystem payloads and cannot reinterpret or replace the source
owner's authoritative fact.

## Implemented Primitives

The foundation adds `com.butchercraft.world.evidence` as a pure Java package.
Its records and enums model:

- `EvidenceOwnerId`
- `EvidenceIdentity`
- `EvidenceSource`
- `EvidenceClassification`
- `EvidenceLifecycleDisposition`
- `EvidenceRetentionPolicy`
- `EvidenceRetentionRequest`
- `EvidenceRetentionDecision`
- `EvidenceLifecycleFailure`

The deterministic evaluator uses only explicit inputs supplied in the request
and policy. It does not inspect wall-clock time, the filesystem, live world
state, player presence, chunk state, global mutable configuration, or
environment variables.

## Retention Semantics

The foundation can decide whether evidence is:

- Retained hot.
- Eligible for archive.
- Retained in cold archive.
- Eligible for derived-data rebuild.
- Eligible for deterministic diagnostic expiry.
- Protected from deletion.
- Blocked by missing lifecycle guarantees.

Eligibility decisions do not execute archive, deletion, compaction, recovery, or
publication work. Those remain gated by later architecture-authorized
milestones.

Permanent audit evidence and authoritative runtime evidence cannot be silently
expired. Replay-critical evidence is protected while it is inside the explicit
replay horizon. Disposable diagnostics can expire only when the policy
explicitly enables deterministic diagnostic expiry. Derived summaries can be
rebuilt only while their source evidence remains available.

## Manifest State

The architecture manifest now marks the IM-002 foundation contracts as enforced:

- `butchercraft:platform_contract/evidence_classification_foundation`
- `butchercraft:platform_contract/evidence_retention_policy_foundation`
- `butchercraft:platform_contract/evidence_retention_decision_foundation`

The broader Evidence Lifecycle ADR remains partially implemented. Archival
storage, compaction records, integrity verification, query policy, checkpoint
integration, subsystem migration, and operator workflows remain
implementation-gated.

## Future Integration Rules

Future subsystem integrations should publish owner-issued evidence descriptors
or adapters without transferring fact ownership to Evidence Lifecycle. A future
archive or compaction milestone may consume retention decisions, but it must
still preserve source ownership, checkpoint recovery boundaries, and the
platform identity model.
