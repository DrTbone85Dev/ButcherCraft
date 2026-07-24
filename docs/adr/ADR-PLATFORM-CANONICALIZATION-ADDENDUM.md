# Platform Canonicalization Addendum

Status: RATIFIED ARCHITECTURAL DIRECTION - IMPLEMENTATION NOT AUTHORIZED

Package: BCSE Architecture Hardening AH-1

Governing authority: [`CONSTITUTION.md`](../../CONSTITUTION.md)

Related authorities:

- [`CORE_PRINCIPLES.md`](../../CORE_PRINCIPLES.md)
- [`DECISIONS.md`](../../DECISIONS.md)
- [`BCSE Architecture Guide`](../BCSE_ARCHITECTURE_GUIDE.md)
- [`Architecture Validation Framework`](../ARCHITECTURE_VALIDATION_FRAMEWORK.md)
- [`RFC-0022 Resource Allocation Engine`](../RFC-0022_RESOURCE_ALLOCATION_ENGINE.md)
- [`RFC-0023 Deterministic Execution Engine`](../RFC-0023_DETERMINISTIC_EXECUTION_ENGINE.md)

## 1. Purpose

This addendum is the canonical platform reference for AH-1 terminology,
identity classes, invariant ownership, recovery, replay, failure states,
cancellation boundaries, operator authority, deterministic configuration, and
the World Identity relationship.

Subsystem ADRs reference this document for platform-wide concepts instead of
redefining those concepts locally. This document introduces no implementation,
gameplay, runtime behavior, schema migration, or RFC-0023 revision by itself.

## 2. Platform Vocabulary

| Canonical term | Definition | Owner | Deprecated or conflicting terms |
| --- | --- | --- | --- |
| Authority | The exclusive architectural right to decide, validate, mutate, publish, retain, or recover a specific kind of state or evidence. | Constitution and owning subsystem | Broad "manager owns everything" wording |
| Identity | A stable value used to distinguish an entity, content value, freshness state, invocation, generation, evidence record, or configuration set. | Issuing owner | Unscoped id, display name, path-as-id |
| Entity Identity | Stable identity for a durable entity, definition, runtime record, or external reference. | The entity's authoritative owner | Random UUID where deterministic identity is required |
| Content Identity | Digest or structural identity proving exact canonical content equality. | The content-producing owner | Informal body hash |
| Freshness Identity | Deterministic identity of the authoritative source state examined by another subsystem. It may be a revision, scoped revision set, revision vector, or digest-backed snapshot identity. | Source owner | Global revision as universal invariant |
| Inventory Freshness Identity | The freshness identity for all authoritative Inventory state examined by validation. | Inventory | Inventory Revision as a fixed architecture requirement |
| Evidence Identity | Stable identity of an immutable evidence record, independent from hot or archive location. | Evidence-producing owner | Archive path as identity |
| Proposal Identity | Content identity of the exact submitted proposal. | Proposal-consuming owner | Transaction id alone |
| Validation Plan Identity | Content identity of the immutable accepted validation plan. | Transaction subsystem for Transactions | Staged changes without digest |
| Trigger Identity | Deterministic identity of a Planning eligibility trigger. | Planning, using source-owned facts | Event object identity |
| Trigger Content Identity | Content identity proving the exact trigger payload. | Trigger source and Planning | Trigger category alone |
| Invocation Identity | Deterministic identity of one Scheduler handler invocation attempt. | Scheduler | Attempt timestamp |
| Effect Identity | Deterministic identity of one consequential effect observed during a Scheduler invocation. | Scheduler plus effect owner | Non-repeatable effect id without owner |
| Owner Snapshot | Immutable owner-supplied representation of that owner's state for one checkpoint generation. | Snapshot owner | Coordinator-created subsystem state |
| CheckpointGenerationId | Deterministic generation identity for one committed checkpoint lineage position. | Checkpoint Recovery | Directory name alone |
| Validation Consumption Authority | Private, single-use, non-persisted Transaction-owned runtime authority to consume an accepted validation plan. | Transactions | Opaque Execution Authority |
| Serialized Transaction-owner Boundary | The Transaction-owned serialized boundary in which validation, freshness check, authority consumption, and mutation application are coordinated. | Transactions | Execution Critical Section, Java lock wording |
| Planning Cycle | One Planning-owned decision evaluation and publication unit. | Planning | Planning Work when referring to Planning semantics |
| Scheduler Work | One Scheduler-owned dispatch and lifecycle unit. | Scheduler | Planning Work when referring to scheduling |
| Scheduler Runtime Authority | The world-scoped authority that owns Scheduler runtime, ordered dispatch, effect policy enforcement, and Scheduler publication. | Scheduler | Scheduler Execution Authority, Simulation Execution Authority |
| Authoritative Result | Immutable result published by the subsystem that owns the decision or mutation. Observers may consume it but cannot reinterpret it. | Result owner | Caller-inferred success |
| Unknown Outcome | Runtime state where a consequential effect may or may not have completed and cannot be safely reinvoked automatically. | Effect owner and Scheduler for invocation evidence | Unknown effect quarantine |
| Recovery-Blocked State | World or authority state in which required recovery evidence is unavailable, corrupt, or unresolved and new side effects are prohibited until explicit recovery occurs. | Checkpoint Recovery or affected authority | Silent degraded continuation |
| Recovery | Selecting and loading an authoritative committed baseline after startup, rollback, interruption, or integrity failure. | Checkpoint Recovery, with owner validation | Replay |
| Replay | Deterministic reevaluation from explicit baseline, inputs, configuration, and evidence to verify equivalent outcomes. | Replaying subsystem | Recovery |
| Rollback | Recovery selection of an older valid committed generation or baseline. | Checkpoint Recovery with explicit operator authority when not automatic | Replaying lost ticks |
| Quarantined Artifact | Storage artifact that is incomplete, uncommitted, corrupt, or otherwise excluded from authority until inspected or removed. | Checkpoint Recovery or storage owner | Unknown outcome quarantine |
| Publication | The owner-controlled act that makes a complete validated state, result, snapshot, or generation authoritative and visible. | Publishing owner | Partial file visibility |
| Observation | Read-only consumption of owner-published immutable facts or snapshots without acquiring ownership. | Observer consumes; source owns | Shared mutable access |
| Configuration Identity | Deterministic identity of authoritative configuration values that affect replay or recovery. | Configuration owner; published through checkpoints | Incidental runtime setting |
| Platform Determinism Manifest | The checkpoint-published identity set for replay-relevant platform configuration, schemas, policies, registries, and migration state. | Checkpoint Recovery publishes; each source owns its entries | Platform Replay Manifest |
| Operator Authority | Explicit server-authoritative administrative permission to select recovery actions, mount archives, approve migrations, or resolve integrity states. | Platform integration boundary and affected owner | Automatic repair |

## 3. Platform Identity Model

Every platform identity must be deterministic, immutable, canonical,
schema-versioned, owner-issued, serializable, replay-safe, and
collision-detecting. When identity participates in ordering, ordering must be
explicit and stable. When identity proves content or integrity, it must bind to
canonical bytes or a documented structural form. When identity represents
freshness, it must cover every authoritative source fact examined by the
consumer.

One object may legitimately have multiple identities. A Transaction, for
example, has an entity identity (`TransactionId`), a Proposal Identity for the
exact requested mutation, a Validation Plan Identity for the accepted staged
plan, and result evidence identities for authoritative publication. These
identities answer different architectural questions and must not be collapsed
into one value.

Identity classes:

- **Entity identity:** names a durable entity, definition, runtime record, or
  external reference.
- **Content identity:** proves canonical content equality.
- **Freshness identity:** proves the source state examined by a decision or
  validation.
- **Invocation identity:** names one bounded execution attempt by Scheduler or
  a future runtime owner.
- **Generation identity:** names a committed recovery lineage position.
- **Evidence identity:** names immutable evidence independent of storage
  location.
- **Configuration identity:** names replay-relevant configuration, schemas,
  policies, registry contents, and migration state.

No platform identity is derived from wall-clock time, filesystem metadata,
player presence, iteration order of unordered collections, random values, or a
mutable storage path.

## 4. Invariant Ownership

| Invariant | Canonical owner | Referencing ADRs |
| --- | --- | --- |
| Singular data ownership | Constitution | All platform ADRs |
| Deterministic simulation | Constitution | All platform ADRs |
| No wall-clock authority for simulation outcomes | Constitution and Simulation Clock | Planning, Checkpoint, Scheduler, Execution |
| Explicit failure outcomes | Constitution | All platform ADRs |
| No silent authoritative-data loss | Constitution; Evidence Lifecycle for evidence policy | Evidence, Checkpoint |
| Platform identity classes and vocabulary | This addendum | All platform ADRs and RFC-0023 reconciliation |
| Evidence classification, retention, archival, compaction records, integrity verification, and query policy | Evidence Lifecycle | Planning, Scheduler, Checkpoint, Transactions, Execution |
| Checkpoint generation identity, atomic checkpoint visibility, committed-generation selection, rollback, and storage-artifact quarantine | Checkpoint Recovery | Evidence, Planning, Scheduler, Transactions, Execution, Allocation |
| Owner snapshot content and validation | Each snapshot owner | Checkpoint Recovery |
| Source-owned freshness identity | Source owner; Inventory owns Inventory Freshness Identity | Transactions, Planning, Checkpoint, Execution |
| Transaction proposal, freshness, validation-plan, and result binding | Transactions | Scheduler, Execution, Checkpoint |
| Immutable accepted validation plan | Transactions | Scheduler, Execution |
| No hidden validation context | Transactions | Execution |
| Planning eligibility, trigger consumption, input capture, Planning Cycle publication, and Planning decisions | Planning | Scheduler, Evidence, Checkpoint |
| Deterministic trigger ordering and no burst catch-up | Planning | Scheduler, Evidence, Checkpoint |
| Scheduler Work lifecycle, ordered dispatch, effect policy, invocation identity, and Scheduler Runtime Authority | Scheduler | Planning, Transactions, Execution |
| Unknown consequential effects are not automatically reinvoked | Scheduler and effect owner | Checkpoint, Evidence, Execution |
| Scheduler observes domain effects but does not infer or own domain results | Scheduler and producing domain owner | Planning, Production, Transactions, Execution |
| Runtime authority is singular per world where applicable | Each runtime-owning subsystem | Scheduler, Transactions, Planning, future Execution, Allocation |
| Platform Determinism Manifest publication | Checkpoint Recovery coordinates; each configuration owner owns entries | Checkpoint, Evidence, Planning, Scheduler, Transactions, Execution |
| Schema-1 World Identity remains an immutable external root | World Identity | Checkpoint Recovery |

Numeric cadence, retention, partition, capacity, storage, retry, and budget
values are schema-1 operational defaults unless an accepted document explicitly
ratifies a value as a permanent invariant. Changing such a default before
public save compatibility is promised does not change these invariants if the
ownership, determinism, replay, and no-silent-loss rules remain intact.

## 5. Recovery Model

Recovery selects and loads an authoritative committed baseline. Checkpoint
Recovery owns generation selection, lineage validation, integrity
orchestration, rollback selection, recovery diagnostics, and publication of
the recovered generation as the loaded BCSE baseline.

Recovery may:

- select the highest valid committed generation automatically;
- select an older valid generation only under explicit operator authority;
- validate owner snapshots through their owners;
- quarantine incomplete or corrupt storage artifacts;
- enter a recovery-blocked state when required evidence is missing or corrupt;
- publish recovery evidence and a new committed generation when an approved
  recovery action changes the loaded lineage.

Recovery may not:

- merge owners from different generations;
- fabricate missing subsystem state or evidence;
- replay uncommitted post-checkpoint activity as authoritative;
- infer non-repeatable or unknown effects;
- advance the Simulation Clock from wall-clock time;
- become the owner of subsystem state.

Rollback is a recovery selection of an older valid committed generation or
baseline. It does not rewrite committed history. It may make later uncommitted
progress non-authoritative, and it must report that loss explicitly when known.

## 6. Replay Model

Replay is deterministic reevaluation from explicit inputs. It verifies whether
the same baseline, ordered inputs, authoritative external results, retained
evidence, and Platform Determinism Manifest produce equivalent outcomes.

Replay inputs include:

- the explicit recovered baseline or compatible checkpoint generation;
- ordered simulation ticks and Scheduler Work inputs;
- owner snapshots or retained source evidence;
- proposal, freshness, validation-plan, invocation, effect, evidence, and
  configuration identities;
- authoritative Transaction results, not inferred Transaction status;
- retained replay-critical evidence under the Evidence Lifecycle policy.

Replay may validate equivalence, digests, ordering, lifecycle transitions, and
failure visibility. Replay may not query live providers, reuse runtime
authority tokens, perform external non-repeatable effects, repair missing
authority, or replace Recovery as the committed-baseline selector.

## 7. Failure Model

| State | Meaning | Owner | New side effects |
| --- | --- | --- | --- |
| Typed Failure | Expected operation failure represented by an explicit code and evidence. | Operation owner | Allowed when the owner remains consistent |
| Rejected Operation | Validation rejected before authoritative mutation or side effect. | Validating owner | Allowed for unrelated valid operations |
| Conflict | Same identity or reference has incompatible content or authority claims. | Identity owner or affected authority | Blocked for affected operation |
| Duplicate Observation | Same identity and same content is observed again. | Original owner | May return existing authoritative result |
| Unknown Outcome | Consequential effect outcome cannot be proven applied or not applied. | Effect owner and Scheduler evidence | Prohibited for the affected invocation until resolved |
| Quarantined Artifact | Storage artifact is incomplete, corrupt, uncommitted, or excluded from authority. | Storage owner or Checkpoint Recovery | Unrelated operation may continue if required evidence remains valid |
| Recovery-Blocked State | Required recovery evidence or generation state is unavailable, corrupt, or unresolved. | Checkpoint Recovery or affected authority | Prohibited for affected authority/world |
| Degraded Read-Only State | Inspection and diagnostics remain available while mutation is blocked. | Affected authority | Prohibited |
| Terminal Subsystem Failure | Owner cannot safely continue until repaired or migrated. | Subsystem owner | Prohibited for that subsystem |
| Operator Intervention Required | Explicit operator decision is required to proceed. | Affected authority and operator boundary | Prohibited until authorized action |

Unknown Outcome is a runtime/effect state. Quarantined Artifact is a storage
state. They may be correlated, but one does not imply the other.

## 8. Cancellation Model

Cancellation is not one universal mutation. It is an explicit request whose
meaning depends on the owner of the thing being cancelled.

- Scheduler may cancel unstarted nonterminal Scheduler Work only under its
  accepted lifecycle rules.
- Scheduler cannot cancel a domain operation by deleting or rewriting the
  domain owner's runtime state.
- A domain owner defines whether its own operation can be cancelled and what
  evidence or compensation is required.
- Planning may cancel or supersede pending Planning eligibility only under
  Planning-owned cadence and trigger rules.
- A Transaction proposal may be rejected before mutation, but an applied
  Transaction is not cancelled by Scheduler, Planning, Execution, or Checkpoint
  Recovery.
- After a consequential effect publishes, cancellation must proceed through
  the effect owner's explicit model and must not silently undo evidence.
- Interrupted cancellation recovers through the checkpoint model and the
  affected owner's evidence. It must not fabricate a completed cancellation.

Cancellation request authority, validation authority, cancellation execution
responsibility, and observation authority may be different. The owner that
mutates the cancelled state remains singular.

## 9. Operator Authority

Operator authority is explicit server-authoritative administrative action. It
does not create a second owner for subsystem facts.

The following require explicit operator intent and audit evidence:

- selecting an older valid checkpoint generation;
- mounting, remounting, exporting, or acknowledging unavailable cold archives;
- resolving a recovery-blocked world;
- classifying an Unknown Outcome when owner evidence cannot do so
  automatically;
- authorizing retention-policy changes that affect replay guarantees;
- approving migration actions;
- expanding, freeing, or remounting storage after evidence capacity failure;
- clearing Quarantined Artifacts after reference checks prove they are
  uncommitted and unreferenced.

Operator actions must not rewrite committed history, fabricate evidence,
delete permanent audit facts silently, merge owner state across generations, or
make unverified evidence authoritative. An operator recovery action may create
a new committed generation when the checkpoint model validates and publishes
that result.

Exact commands, permissions, UI, and diagnostics are implementation details
for later authorized milestones.

## 10. Platform Determinism Manifest

The Platform Determinism Manifest is the canonical identity of
replay-relevant platform configuration for a committed generation. Checkpoint
Recovery publishes the manifest with the generation. Each source subsystem
owns the entries it contributes.

Minimum manifest contents:

- checkpoint schema and capability flags;
- owner snapshot schema versions;
- evidence retention and archive policy identity;
- Planning cadence and trigger policy identity;
- Scheduler stages, budgets, retry policy, and effect policy identity;
- Transaction validation schema and digest/freshness binding policy identity;
- Inventory Freshness Identity policy identity;
- registered deterministic registries that affect replay;
- provider descriptors when providers participate in replay;
- migration state and compatibility policy;
- checksum/canonicalization algorithm identities.

Authoritative configuration affects simulation, recovery, validation,
retention, replay, ordering, or migration and must be identity-bound.
Incidental runtime configuration affects only diagnostics, presentation,
logging, operator UX, or external measurement and must not influence
authoritative outcomes.

The manifest does not transfer configuration ownership to Checkpoint Recovery.
It records the exact owner-published configuration identities required to
recover and replay the generation.

## 11. World Identity

Schema-1 World Identity remains an immutable external root. Checkpoint
generations reference it by stable identity, schema, and digest. They do not
make Checkpoint Recovery the World Identity owner and do not authorize
migration of World Identity from Minecraft `SavedData` into checkpoint storage.

Recovery validates that the selected generation belongs to the same World
Identity root. Migration may validate and record World Identity as an external
root input, but it must preserve the saved identity rather than regenerate or
reinterpret it. World copy or clone behavior must preserve the distinction
between immutable identity and mutable runtime state through a later accepted
policy if behavior beyond validation is required.

The Platform Determinism Manifest is tied to a checkpoint generation and
configuration state. World Identity is the immutable root that the generation
belongs to. They are related, but neither replaces the other.

## 12. ADR Integration Guide

### Evidence Lifecycle

Reference this addendum for Evidence Identity, Recovery-Blocked State,
Quarantined Artifact, Platform Determinism Manifest, evidence ownership, and
numeric-default policy. Retain ADR-specific policy for evidence classes,
retention, archival, compaction records, archive query guarantees, storage
exhaustion, and no silent loss.

### Checkpoint Recovery

Reference this addendum for CheckpointGenerationId, Owner Snapshot, Recovery,
Replay, Rollback, Quarantined Artifact, Platform Determinism Manifest, and
World Identity disposition. Retain ADR-specific policy for generation
publication, dual head slots, recovery selection, participant validation, and
schema-1 checkpoint defaults.

### Transaction Validation Authority

Reference this addendum for Proposal Identity, Inventory Freshness Identity,
Validation Plan Identity, Validation Consumption Authority, Serialized
Transaction-owner Boundary, Authoritative Result, Replay, and hidden-context
prohibition. Retain ADR-specific policy for exact Transaction validation
binding, duplicate behavior, result evidence, and post-consumption failure.

### Planning Cadence

Reference this addendum for Planning Cycle, Scheduler Work, Trigger Identity,
Trigger Content Identity, Recovery, Replay, and numeric-default policy. Retain
ADR-specific policy for hybrid eligibility, trigger consumption, input capture,
coalescing, minimum separation, and no burst catch-up.

### Scheduler Effects Authority

Reference this addendum for Scheduler Runtime Authority, Invocation Identity,
Effect Identity, Unknown Outcome, Authoritative Result, Quarantined Artifact,
Recovery, Replay, and cancellation boundaries. Retain ADR-specific policy for
effect taxonomy, effect evidence, retry/deferral rules, one Scheduler Runtime
Authority per world, and Scheduler observation without inference.

## 13. RFC-0023 Integration Guide

RFC-0023 Draft 2 should consume these platform concepts from this addendum
instead of defining them independently:

- Authority, Observation, Publication, Recovery, Replay, and Rollback;
- Platform Identity Model and all relevant identity classes;
- Platform Determinism Manifest;
- Authoritative Result and Scheduler observation without inference;
- Unknown Outcome and Recovery-Blocked State;
- Validation Consumption Authority and Serialized Transaction-owner Boundary;
- Transaction Proposal Identity, Inventory Freshness Identity, Validation Plan
  Identity, and Transaction result binding;
- Invocation Identity, Effect Identity, Scheduler Runtime Authority, and
  effect-aware retry rules;
- cancellation boundaries and operator authority;
- World Identity as an immutable external root.

RFC-0023 reconciliation remains a separate milestone. This addendum does not
edit, accept, or implement RFC-0023.

## 14. Readiness Assessment

Completion of this addendum makes the repository ready for an ADR revision
pass that removes duplicate platform definitions from the five AH-1 platform
ADRs and replaces them with references to this document.

After that ADR revision pass is complete, RFC-0023 Draft 2 reconciliation is
ready because the platform will have one canonical vocabulary, one identity
model, one recovery/replay distinction, one failure-state model, one operator
authority model, one Platform Determinism Manifest contract, and one World
Identity disposition for RFC-0023 to consume.

No implementation, migration, schema change, gameplay behavior, Architecture
Validation manifest update, RFC-0023 edit, Allocation integration, or
Execution implementation is authorized by this addendum.
