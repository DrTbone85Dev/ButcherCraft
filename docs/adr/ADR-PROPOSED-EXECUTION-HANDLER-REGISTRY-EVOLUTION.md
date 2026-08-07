# Proposed ADR: Execution Handler Registry Evolution And Save Compatibility

Status: PROPOSED - OWNER APPROVAL REQUIRED

Decision identifier: DG-003

Package: Execution Persistence Compatibility

Authority: This document has no authority until explicitly approved by the
project owner and recorded through the repository's accepted Decision process.
It proposes architecture only. It does not authorize runtime code, persistence
migration, schema publication, Architecture Manifest status changes, gameplay,
or resumption of the paused Cutting Table acceptance work by itself.

Canonical platform references:

- [`CONSTITUTION.md`](../../CONSTITUTION.md)
- [`Core Principles`](../../CORE_PRINCIPLES.md)
- [`Platform Canonicalization Addendum`](ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md)
- [`Evidence Lifecycle ADR`](ADR-PROPOSED-EVIDENCE-LIFECYCLE.md)
- [`Checkpoint Recovery ADR`](ADR-PROPOSED-CHECKPOINT-RECOVERY.md)
- [`RFC-0023 Deterministic Execution Engine`](../RFC-0023_DETERMINISTIC_EXECUTION_ENGINE.md)
- [`Generic Execution Runtime Foundation`](../GENERIC_EXECUTION_RUNTIME_FOUNDATION.md)

## Context

The implemented schema-1 Execution foundation owns deterministic operation
identity, lifecycle, handler registration, owner-result observation, terminal
evidence, and `execution_operations.json` persistence.

The current implementation computes one aggregate handler-registry identity
from the canonically ordered set of registered handler contracts. Schema-1
Execution persistence stores that aggregate identity. Startup requires exact
equality between the persisted aggregate identity and the current aggregate
identity before operations are loaded.

Adding one unrelated handler changes the aggregate identity. The paused
Cutting Table acceptance work adds one handler while leaving the existing
Grinder and Patty Former handler contracts unchanged. Existing schema-1 worlds
therefore fail startup even when their operations list is empty or every
persisted operation references only an unchanged existing handler.

This behavior is fail-visible, but it treats registry-set equality as if it
were operation compatibility. It prevents additive platform growth without
proving a corresponding safety benefit.

## Problem

Execution must distinguish an additive handler registration from a material
change to a handler contract already relied upon by persisted state.

The architecture must permit unrelated additive growth while continuing to
reject:

- removal of a required handler;
- substitution of one handler for another;
- reuse of a handler id for materially different behavior;
- incompatible request, authorization, owner-result, or configuration
  semantics;
- unsupported persisted schemas; and
- any legacy state whose original handler contract cannot be proven.

The solution must not silently reinterpret schema-1 operations, infer missing
contract facts from current Java objects, or overwrite historical registry
metadata merely because startup observed a compatible current registry.

## Current Repository Behavior

### Schema

`ExecutionSchema.CURRENT_VERSION` is `1`. Execution persists independently at:

`<world>/butchercraft/execution_operations.json`

The document stores:

- `schema_version`;
- `handler_registry_identity`;
- `configuration_identity`; and
- ordered operation records.

### Aggregate Registry Identity

`ExecutionHandlerRegistry.registryIdentity()` canonicalizes handlers by
operation type and hashes:

- Execution schema version;
- handler count; and
- each handler's `ExecutionHandlerContract.contractIdentity()`.

The resulting aggregate identity is deterministic and useful evidence that
two complete registry observations are equal. The digest does not expose the
member handler identities or their individual contract identities.

### Existing Handler Contract Identity

The current `ExecutionHandlerContract.contractIdentity()` is deterministic and
binds:

- Execution/handler schema version;
- canonical handler id;
- canonical operation type;
- Scheduler effect type;
- owner-result requirement;
- retry compatibility;
- maximum bounded work units; and
- handler configuration identity.

This is an existing schema-1 content identity. It is not currently persisted
beside each operation.

### Persisted Operation Binding

Schema-1 authorization evidence persists:

- operation type;
- handler id;
- Execution schema version;
- authorization content digest;
- executable work reference;
- frozen input identity;
- source freshness identity;
- configuration identity;
- World Identity; and
- explicit input identities.

This proves which handler id and operation type were authorized. It does not,
by itself, prove the complete handler contract content under which the
operation was accepted.

### Startup Order

Current startup performs this effective sequence:

1. Register the generic Execution Scheduler Work handler.
2. Initialize Scheduler runtime.
3. Construct the current Execution handler registry.
4. Read `execution_operations.json`.
5. Validate the Execution document schema.
6. Require exact aggregate handler-registry identity equality.
7. Require exact Execution runtime configuration identity equality.
8. Deserialize operations and construct the Execution manager.

The aggregate comparison occurs before operation-specific handler
compatibility can be evaluated.

### Existing Migration Gate

The Generic Execution Runtime Foundation explicitly gates migration beyond
schema 1. RFC-0023 assigns Execution-owned content and owner-snapshot
validation to Execution while leaving coordinated migration publication and
recovered-baseline selection outside Execution authority.

## Architectural Constraints

This decision is governed especially by:

- `AI-0001` deterministic simulation;
- `AI-0004` immutable identity separation;
- `AI-0009` deterministic registries;
- `AI-0011` save compatibility priority;
- `AI-0016` explicit responsibility boundaries;
- `AI-0018` versioned persistence;
- `AI-0021` explicit failure outcomes;
- `AI-0025` singular data ownership;
- `AI-0027` tests as contract; and
- `AI-0028` backward-compatible evolution.

The Platform Identity Model remains canonical. This ADR does not create a new
platform identity class.

Execution remains the singular owner of:

- Execution handler contracts;
- handler contract content identities;
- Execution registry observation identity;
- per-operation handler binding;
- registry evolution classification; and
- Execution-local compatibility diagnostics.

Checkpoint Recovery retains coordinated migration publication, committed
generation selection, Recovery, and Rollback authority. Evidence Lifecycle
retains authority over retention and compaction of historical Execution
evidence. Neither subsystem defines handler semantics.

## Definitions

### Execution Handler Contract Identity

Execution Handler Contract Identity is an Execution-owned, schema-versioned
**Content Identity** proving exact canonical equality of one immutable handler
contract.

It is not Java object identity, a class name, a registration index, or a
filesystem path.

### Execution Registry Observation Identity

Execution Registry Observation Identity is the aggregate identity of one
canonically ordered handler-registry observation. It is a replay-relevant
**Configuration Identity** and a Platform Determinism Manifest input.

It proves complete registry equality when equal. Inequality proves that the
registry observation changed, but does not by itself prove that persisted
operations are incompatible.

### Legacy Registry Compatibility Profile

A Legacy Registry Compatibility Profile is immutable, versioned,
Execution-owned compatibility metadata for one recognized historical
schema-1 aggregate registry identity. It maps that aggregate identity to the
exact canonically ordered handler ids, operation types, and handler contract
identities that produced it.

A profile is release evidence, not a best-effort reconstruction. It must be
explicit, deterministic, tested, and immutable after publication. Wildcard
profiles and name-only profiles are prohibited.

### Compatibility-Relevant Operation

A retained operation is compatibility-relevant when any of these is true:

- it is nonterminal;
- it is required for Recovery or Replay;
- it is referenced by retained owner-result or Execution result evidence;
- it is referenced by a retained checkpoint or migration record; or
- Evidence Lifecycle has not already authorized and completed its removal or
  compaction.

Under current schema-1 storage, every operation still present in
`execution_operations.json` is compatibility-relevant.

### Registry Evolution Observation

A Registry Evolution Observation is immutable Execution-owned evidence of:

- persisted registry observation identity;
- current registry observation identity;
- persisted metadata source or legacy profile identity;
- deterministic classification;
- referenced handler contract identities examined;
- failure or recovery reason when compatibility is not proven; and
- schema and compatibility-policy identity.

It grants no mutation or Execution authority.

## Decision

Adopt per-handler compatibility validation for Execution persistence.

The aggregate registry identity remains authoritative evidence of complete
registry equality, but ceases to be the sole startup compatibility authority.

A registry change is compatible only when Execution can prove that every
compatibility-relevant persisted operation remains bound to the exact handler
contract under which it was authorized. New handlers unrelated to retained
operations do not invalidate those operations.

No Execution authority becomes available until registry evolution and every
required operation binding have been classified successfully.

## Handler Contract Model

Every Execution handler contract identity shall bind all facts whose change
could alter validation, invocation, retry, owner-result interpretation, or
deterministic outcome.

The canonical contract content shall include, directly or through an
explicitly versioned referenced identity:

- handler contract identity schema;
- canonical handler id;
- canonical operation type;
- accepted request/operation schema identity;
- authorization evidence schema identity;
- adapter or handler input schema identity;
- Scheduler effect classification;
- retry compatibility and bounded-work semantics;
- owner-result requirement and owner-result contract identity;
- terminal result interpretation contract where handler-specific;
- replay-relevant handler configuration identity; and
- canonicalization algorithm identity when not fixed by the enclosing schema.

Irrelevant runtime state is excluded. The identity shall not bind:

- current operation count;
- current tick;
- object allocation or class-loader identity;
- block/entity instance state;
- diagnostics configuration;
- log settings; or
- unordered iteration state.

For current schema 1, the existing `ExecutionHandlerContract.contractIdentity()`
is the legacy handler contract identity. Execution record and authorization
schema 1 supply the single schema-1 request, authorization, owner-result, and
result interpretation context. Any future divergence among those schemas
requires a new contract identity schema rather than implicit interpretation.

## Persisted Operation Binding

Future Execution persistence shall bind each operation to:

- canonical handler id;
- canonical operation type;
- exact Execution Handler Contract Identity; and
- the operation, authorization, and result schema identities required to
  verify that binding.

The handler contract identity becomes part of the immutable operation binding.
It cannot be replaced when a world loads under a newer registry.

The runtime shall validate that:

1. the referenced handler id exists;
2. the handler is registered for the persisted operation type;
3. its current contract identity equals the operation's persisted contract
   identity;
4. authorization and result evidence remain internally valid; and
5. every referenced schema is supported without reinterpretation.

Failure of any check is incompatible or recovery-required. The runtime shall
not substitute a handler with the same display purpose or infer compatibility
from a matching operation type alone.

## Registry Evolution Classification

Execution classifies one persisted/current registry pair as exactly one of:

### Identical

`IDENTICAL` requires:

- equal aggregate registry observation identities; and
- successful validation of every compatibility-relevant operation binding.

Aggregate equality does not excuse malformed operation records.

### Additive-Compatible

`ADDITIVE_COMPATIBLE` requires all of:

1. The persisted registry contract map is available from native metadata or a
   recognized Legacy Registry Compatibility Profile.
2. Every persisted handler id remains registered.
3. Every persisted handler's operation type and contract identity are exactly
   unchanged.
4. The current registry contains one or more additional handler ids.
5. No persisted operation references an additional handler as though it had
   existed under the prior registry.
6. Every compatibility-relevant operation validates against its exact
   persisted handler contract.
7. Execution runtime configuration and required schemas remain compatible.

The additional handlers do not alter the meaning of retained operations.

### Incompatible

`INCOMPATIBLE` applies when proof establishes any of:

- a compatibility-relevant operation references a missing handler;
- a handler id is reused with a different operation type;
- a referenced handler contract identity changed;
- a required request, authorization, owner-result, result, or configuration
  schema changed without an accepted migration;
- persisted metadata is internally contradictory;
- a recognized prior handler is removed under schema-1 policy; or
- canonical content is invalid despite matching names.

Startup fails visibly for the affected Execution authority. It does not
silently skip, fail, complete, or reinterpret individual operations.

### Indeterminate / Recovery Required

`INDETERMINATE_RECOVERY_REQUIRED` applies when compatibility cannot be proven,
including:

- unknown historical aggregate registry identity;
- missing per-handler contract metadata without a recognized legacy profile;
- unsupported compatibility-profile schema;
- unavailable required evidence;
- retention state that cannot prove whether removed-handler evidence is still
  required; or
- corrupt compatibility metadata.

Execution enters the platform Recovery-Blocked State and publishes explicit
diagnostics. Operator action cannot declare the contracts equal without an
accepted migration or recovery artifact.

## Classification Algorithm

The algorithm is deterministic:

1. Parse the Execution document without granting runtime authority.
2. Validate document schema, required fields, canonical ordering, and runtime
   configuration identity.
3. Build the current registry contract map ordered by canonical handler id.
4. Obtain the persisted registry contract map from native persistence
   metadata or an exact Legacy Registry Compatibility Profile.
5. If no map can be proven, classify
   `INDETERMINATE_RECOVERY_REQUIRED`.
6. Validate the persisted aggregate identity against the proven persisted
   contract map.
7. Compare each persisted handler entry with the current entry by handler id,
   operation type, and contract identity.
8. Validate each compatibility-relevant operation against its persisted
   contract entry and current exact match.
9. Classify `IDENTICAL` when the complete maps and aggregate identities are
   equal.
10. Classify `ADDITIVE_COMPATIBLE` when the persisted map is a strict exact
    subset of the current map and every operation check succeeds.
11. Classify `INCOMPATIBLE` for a proven removal, substitution, contract
    change, contradictory binding, or unsupported required schema.
12. Otherwise classify `INDETERMINATE_RECOVERY_REQUIRED`.
13. Publish an immutable in-memory Registry Evolution Observation.
14. Permit Execution authority only for `IDENTICAL` or
    `ADDITIVE_COMPATIBLE`.

Collection comparison uses canonical ids and canonical content identities.
Filesystem order, registration order, Java type discovery, and hash-map order
are prohibited inputs.

## Startup Validation Order

The required startup order is:

1. Resolve World Identity and the selected authoritative baseline according to
   accepted Recovery architecture.
2. Load Execution persistence as an untrusted candidate.
3. Observe the current Execution handler registry without enabling handler
   invocation.
4. Validate Execution schema and runtime configuration identity.
5. Resolve native registry metadata or an exact legacy compatibility profile;
6. classify registry evolution;
7. validate every compatibility-relevant persisted operation against its
   referenced handler contract;
8. validate Execution/Scheduler references required by loaded nonterminal
   operations;
9. publish the loaded Execution candidate and compatibility observation; and
10. enable Execution authority.

Any failure before step 9 leaves Execution unpublished. No handler may accept
new authorization while compatibility is unresolved.

## Legacy Schema-1 Policy

Schema 1 does not persist enough per-handler content identity beside each
operation to prove compatibility from the operation record alone.

Schema-1 compatibility therefore requires one of:

- exact aggregate identity equality with the current registry; or
- an exact Legacy Registry Compatibility Profile for the persisted aggregate
  identity.

Current Java handler objects are not historical evidence. A profile shall not
be generated by assuming that today's handler contract is the old contract.

### Empty Schema-1 Operation Set

An empty operation set contains no operation semantics to reinterpret. For a
recognized historical profile whose handler map is an exact subset of the
current registry, startup is `ADDITIVE_COMPATIBLE` and succeeds without
rewriting the file.

An unknown aggregate identity remains indeterminate even when the operation
set is empty because the implementation cannot prove that the observed change
is the approved additive transition rather than corrupt or unsupported
metadata.

### Schema-1 Grinder Operations Plus Cutting Table Addition

Startup succeeds as `ADDITIVE_COMPATIBLE` when:

- the persisted aggregate identity resolves to the explicit released
  Grinder/Patty Former legacy profile;
- each retained Grinder operation names the expected handler and operation
  type;
- the current Grinder contract identity exactly matches that profile;
- all operation evidence validates; and
- the only registry difference is the additional Cutting Table handler.

### Changed Schema-1 Handler Contract

A retained operation whose handler contract differs from the recognized
legacy profile is `INCOMPATIBLE`. Matching handler id or operation type does
not make it compatible.

### Removed Schema-1 Handler

Removal of any handler present in a recognized schema-1 profile is
`INCOMPATIBLE` under this decision. Schema 1 does not contain sufficient
retention metadata to prove that the removed contract has no remaining Replay,
Recovery, audit, or integrity role.

A future accepted retention-aware migration may define narrower safe removal
rules. This ADR does not.

## Handler Contract Evolution

Schema 1 permits exact contract identity equality only.

An identical handler id with a different contract identity is incompatible.
There is no implicit patch-level, class-level, or semantic-version
compatibility rule.

Future compatible contract evolution requires an explicit accepted rule that
defines:

- predecessor and successor contract identities;
- affected operation and evidence schemas;
- deterministic migration or adapter behavior;
- whether active and terminal operations differ;
- Replay and Recovery proof;
- publication and rollback behavior; and
- tests for every supported predecessor.

Absent that rule, a contract identity change fails visibly.

## Handler Removal And Evidence Retention

Handler removal is not equivalent to registration cleanup.

If any compatibility-relevant operation or retained evidence references the
handler, removal is incompatible.

Removal may become compatible only after Evidence Lifecycle has already
authorized and completed removal or compaction of every dependent record and
the persisted registry metadata can prove that fact. That policy requires a
future accepted migration/retention rule. It is not available to schema 1.

## Future Persistence Metadata

A future Execution persistence schema shall contain:

- Execution persistence schema version;
- current aggregate Registry Observation Identity;
- canonical ordered handler metadata needed to verify retained operations;
- each handler id, operation type, and Handler Contract Identity;
- each operation's exact Handler Contract Identity;
- compatibility-policy identity;
- legacy profile or migration evidence identity when applicable;
- prior registry observation identity when an evolution has been published;
- Registry Evolution Observation identity and classification; and
- Platform Determinism Manifest references required by accepted checkpoint
  architecture.

Persistence shall not duplicate handler implementation state, Java class
names, runtime object state, or mutable owner state.

## No Silent Persistence Rewrite

Compatibility observation is read-only during startup.

Startup shall not replace the historical aggregate registry identity merely
because the current registry is additive-compatible. A schema-1 world with no
new Execution state may continue to retain its schema-1 file unchanged.

Execution owns compatibility metadata content and candidate validation.
Checkpoint Recovery owns coordinated migration publication and generation
activation when checkpoint migration is involved.

Before a newly added handler may create durable operations in a legacy-loaded
world, an authorized implementation must establish a persistence schema that
can store the exact new per-operation contract binding.

If compatibility metadata is later published:

- publication occurs only through an authorized Execution owner publication
  or coordinated migration boundary;
- the prior aggregate identity and compatibility observation remain retained;
- operation identities and historical authorization evidence remain
  unchanged;
- the write is atomic at the applicable owner/checkpoint boundary;
- a crash before publication leaves the old complete schema-1 state
  authoritative and loadable through its legacy profile; and
- a crash after committed publication loads the complete new state.

No partial metadata update becomes authoritative.

## Failure And Diagnostics

Execution compatibility failures use typed, explicit outcomes at least
equivalent to:

- `unsupported_execution_schema`;
- `unknown_registry_observation`;
- `legacy_profile_missing`;
- `legacy_profile_digest_mismatch`;
- `referenced_handler_missing`;
- `handler_operation_type_mismatch`;
- `handler_contract_mismatch`;
- `operation_contract_binding_missing`;
- `operation_contract_binding_invalid`;
- `registry_metadata_corrupt`; and
- `execution_registry_recovery_required`.

Diagnostics shall expose:

- persisted and current aggregate identities;
- classification;
- compatibility-policy identity;
- profile or native metadata source;
- added, removed, and changed canonical handler ids;
- each incompatible operation identity and handler id;
- exact typed reason; and
- whether Execution authority remains blocked.

Diagnostics shall not expose private runtime authorization.

## Replay And Recovery

Registry evolution is replay-relevant configuration.

Replay uses the Handler Contract Identity bound to each operation and the
Platform Determinism Manifest for its committed baseline. It does not replay a
historical operation under a different current contract merely because the
handler id matches.

Recovery validates Execution-owned compatibility metadata through Execution.
Checkpoint Recovery selects the baseline and coordinates publication; it does
not decide handler compatibility.

Indeterminate or incompatible state enters Recovery-Blocked State. Recovery
shall not fabricate a legacy profile, delete an operation, or substitute a
handler to make startup succeed.

## Architecture Manifest Impact

Future implementation should propose manifest contracts equivalent to:

- Execution Handler Contract Identity is deterministic and versioned;
- Execution Registry Observation Identity remains diagnostic and
  replay-relevant but is not the sole operation compatibility authority;
- additive registry evolution validates exact shared contracts;
- every retained operation binds its exact handler contract identity;
- unknown or incompatible binding blocks Execution authority; and
- startup compatibility observation does not silently rewrite persistence.

All such manifest entries remain **unimplemented** until runtime code,
persistence, diagnostics, and tests make them mechanically true. This proposal
does not modify the Architecture Manifest.

## Compatibility With Existing Architecture

### Constitution And Core Principles

The decision makes additive evolution practical while preserving explicit,
deterministic failure for incompatible contracts. It directly applies
`AI-0011`, `AI-0018`, and `AI-0028` without weakening `AI-0001` or `AI-0021`.

### Platform Canonicalization

Handler Contract Identity is a Content Identity. Registry Observation Identity
is a Configuration Identity. Registry evolution contributes owner-published
configuration and migration state to the Platform Determinism Manifest.

### Evidence Lifecycle

Execution owns handler semantics and operation bindings. Evidence Lifecycle
decides when terminal records cease to be compatibility-relevant through
accepted retention or compaction. Execution cannot infer that evidence has
expired.

### Checkpoint Recovery

Execution validates its own migration candidate and compatibility metadata.
Checkpoint Recovery coordinates committed migration publication and baseline
selection. This ADR creates no second migration or Recovery authority.

### RFC-0023

The decision refines Execution-owned adapter/handler verification and
persistence content. It does not give Execution authority over Scheduler,
Transactions, Inventory, domain owners, Recovery, or Replay baseline
selection.

## Cutting Table Impact

Ratification establishes the architectural policy required for one additive
Cutting Table handler.

Ratification alone does not make the current runtime save-compatible. A narrow
follow-up implementation must first:

- implement deterministic classification;
- register the exact released schema-1 legacy profile;
- validate every retained operation binding;
- add persistence capable of binding new operations to handler contracts;
- preserve historical registry observations;
- add migration, startup, and existing-world tests; and
- prove that empty and Grinder-only legacy worlds load without weakening
  incompatible-change rejection.

After that compatibility implementation passes, the paused Cutting Table
acceptance work may resume with exactly one additive handler. DG-003 does not
authorize additional recipes, general handler APIs, public plugin registration,
or broader fabrication.

## Consequences

### Positive

- Unrelated additive handlers no longer invalidate existing operations.
- Persisted operations become explicitly bound to the contracts that gave
  them meaning.
- Aggregate registry identity remains useful without becoming an overbroad
  startup lock.
- Genuine handler changes and removals remain fail-visible.
- Legacy compatibility relies on explicit release evidence rather than
  inference.
- Future diagnostics can explain exactly why registry evolution was accepted
  or blocked.

### Negative

- Execution persistence requires a new schema and migration implementation.
- Supported schema-1 aggregate identities require maintained compatibility
  profiles.
- Handler removal remains conservative until retention-aware migration exists.
- Contract identities become durable compatibility commitments.
- Startup performs bounded per-handler and per-operation validation.

## Testing Requirements

Any implementation requires automated coverage for:

- canonical Handler Contract Identity stability;
- aggregate identity canonical ordering;
- identical registry startup;
- empty schema-1 world plus additive Cutting Table handler;
- Grinder-only schema-1 operations plus unchanged Grinder and added Cutting
  Table handler;
- Patty Former schema-1 operations under the same additive change;
- unknown legacy aggregate identity;
- malformed and unsupported legacy profile;
- profile aggregate digest mismatch;
- changed referenced handler contract;
- removed referenced handler;
- same handler id with changed operation type;
- missing per-operation contract identity in the new schema;
- terminal and nonterminal operation validation;
- no Execution authority before compatibility publication;
- no startup persistence rewrite;
- schema migration round trip;
- crash before and after atomic migration publication;
- historical aggregate/evolution evidence retention;
- deterministic diagnostics;
- unsupported future schema;
- empty, existing, and newly created world login; and
- paused Cutting Table GameTests on an existing compatible world.

## Alternatives Rejected By This Proposal

- **Ignore aggregate mismatch:** rejected because it loses detection of genuine
  registry and contract changes.
- **Treat every aggregate mismatch as incompatible:** rejected because it
  prevents safe additive platform growth.
- **Trust matching handler ids:** rejected because ids do not prove unchanged
  semantics.
- **Derive legacy contracts from current Java handlers:** rejected because
  current code is not historical evidence.
- **Rewrite the aggregate identity on startup:** rejected because it destroys
  the evidence needed to explain compatibility and can leave partial migration
  state.
- **Delete empty Execution files:** rejected because it silently discards
  authoritative persisted metadata and does not solve nonempty worlds.
- **Use Java class names or object identity:** rejected as unstable,
  noncanonical, and not replay-safe.
- **Allow schema-1 handler removal when currently unreferenced:** rejected
  because schema 1 cannot prove retained Replay, Recovery, or audit
  dependencies are absent.

## Owner Decisions Required

1. **Additive registration compatibility**
   - Approve: A strict exact-contract superset is save-compatible.
   - Revise: Continue requiring complete registry equality.

2. **Aggregate registry role**
   - Approve: Retain it as Registry Observation/Configuration Identity, not the
     sole operation compatibility authority.
   - Revise: Define another aggregate role before implementation.

3. **Per-handler contract identity**
   - Approve: Use an Execution-owned versioned Content Identity binding all
     operation-relevant handler contract facts.
   - Revise: Specify additional or excluded canonical fields.

4. **Persisted operation binding**
   - Approve: Every retained operation binds exact handler id, operation type,
     and Handler Contract Identity.
   - Revise: Define a different proof contract.

5. **Empty-operation-set compatibility**
   - Approve: A recognized schema-1 profile that is an exact subset starts
     without rewriting persistence.
   - Revise: Require migration publication before startup authority.

6. **Referenced handler removal**
   - Approve: Removal is incompatible while any compatibility-relevant record
     remains; schema 1 treats every profile removal as incompatible.
   - Revise: Authorize a specific retention-aware removal rule.

7. **Handler contract changes**
   - Approve: Schema 1 requires exact contract identity; changed identity is
     incompatible without an explicit migration.
   - Revise: Define a specific compatible predecessor/successor rule.

8. **Legacy schema-1 handling**
   - Approve: Support only exact aggregate equality or explicit immutable
     Legacy Registry Compatibility Profiles; never infer history from current
     code.
   - Revise: Define another source of historical proof.

9. **Metadata update and publication**
   - Approve: Startup is read-only; later authorized atomic publication retains
     prior registry/evolution evidence and uses owner/checkpoint boundaries.
   - Revise: Define a different publication trigger and crash contract.

10. **Paused Cutting Table work**
    - Approve: Ratification authorizes a narrow DG-003 compatibility
      implementation; Cutting Table acceptance resumes only after that
      implementation passes existing-world validation.
    - Revise: Require a separate implementation authorization after
      ratification.

## Owner Approval Checklist

- [ ] Approve additive handler registration as save-compatible under exact
  shared-contract proof.
- [ ] Approve the aggregate registry identity's diagnostic and configuration
  role.
- [ ] Approve Execution Handler Contract Identity as Content Identity.
- [ ] Approve exact per-operation handler contract binding.
- [ ] Approve recognized empty schema-1 world compatibility.
- [ ] Approve conservative handler-removal behavior.
- [ ] Approve exact schema-1 contract equality.
- [ ] Approve immutable legacy compatibility profiles.
- [ ] Approve read-only startup and later atomic metadata publication.
- [ ] Authorize the narrow compatibility implementation that must precede
  resumption of the Cutting Table acceptance work.
