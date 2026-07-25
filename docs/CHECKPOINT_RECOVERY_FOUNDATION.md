# Checkpoint Recovery Foundation

Status: IM-003 metadata foundation, IM-005 filesystem publication foundation,
IM-006 minimal live owner snapshot integration, and IM-007 controlled
development checkpoint invocation implemented.

This document records the narrow pure-Java checkpoint primitives introduced by
IM-003, the minimal filesystem-backed publication proof introduced by IM-005,
and the minimal Clock/Scheduler owner snapshot integration introduced by
IM-006, and the development-only explicit invocation path introduced by
IM-007. It does not authorize world startup recovery, save hooks, automatic
checkpoint scheduling, broad owner adoption, public operator commands,
gameplay, or UI behavior.

## Scope

The foundation introduces deterministic metadata types for:

- `CheckpointGenerationId`
- owner snapshot descriptors
- generation manifests
- head records
- publication state metadata
- checkpoint integrity failures
- deterministic recovery selection
- explicit rollback target selection
- explicit-root filesystem checkpoint storage
- staged generation writes
- immutable final generation publication
- dual-slot head publication
- owner payload digest verification
- deterministic filesystem recovery scanning
- storage artifact classification
- owner snapshot provider and restorer contracts
- explicit Clock owner snapshot capture and restoration
- explicit Scheduler owner snapshot capture and restoration
- World Identity external-root digest references
- all-or-nothing coordinated restoration preparation and publication
- owner-supplied rollback for attempted publication failure
- development-only explicit checkpoint capture invocation
- development-only generation listing, integrity validation, and inspection
- deterministic world-scoped development checkpoint-root resolution
- controlled Clock/Scheduler restoration proof through a harness
- live loaded-world restoration rejection until a safe runtime boundary exists

These types live under `com.butchercraft.world.checkpoint` and are independent
from Minecraft, NeoForge, wall-clock time, random sources, runtime owner
services, and save lifecycle hooks. IM-005 introduces explicit filesystem paths
only inside the checkpoint store boundary and never derives authority from
global paths, world paths, or working-directory assumptions. IM-006 adds
owner-specific adapters under the owning Clock, Scheduler, and World Identity
packages. The checkpoint package remains independent of owner implementation
classes.

## Ownership

Checkpoint Recovery owns checkpoint generation identity, generation manifest
metadata, checkpoint head metadata, metadata integrity validation, filesystem
checkpoint layout, staged publication, head publication, recovery selection,
rollback selection, storage artifact classification, and checkpoint recovery
diagnostics. IM-006 also assigns explicit owner snapshot coordination,
cross-owner relationship validation, and coordinated restoration publication
boundary to Checkpoint Recovery.

Checkpoint Recovery does not own Inventory, Transactions, Planning, Scheduler,
Production, Allocation, Execution, Evidence Lifecycle policy, World Identity
state, or any source subsystem facts. Owner snapshot descriptors identify owner
metadata only. The producing subsystem remains the authority for snapshot
content and runtime state.

Simulation Clock owns Clock snapshot content, schema, validation, and restored
Clock publication. Simulation Scheduler owns Scheduler snapshot content,
schema, validation, and restored Scheduler publication. World Identity owns
external-root identity and digest derivation.

## Generation Identity

`CheckpointGenerationId` is the canonical committed generation identity. It is
derived from:

- checkpoint schema version
- committed sequence
- authoritative simulation tick

Predecessor identity and predecessor digest are recorded as generation metadata,
not as part of the canonical generation identity.

## Generation Metadata

A checkpoint generation candidate may describe a complete candidate before it
becomes authoritative. A committed generation is represented by a generation
manifest and publication state metadata.

The foundation validates that manifests:

- use supported schema versions
- match their declared generation identity
- contain deterministic owner snapshot ordering
- do not duplicate owner snapshots
- include required owners
- do not mix owner snapshots from a different generation
- match expected World Identity root references
- match expected Platform Determinism Manifest references
- match their calculated manifest digest

The metadata model is used by the IM-005 filesystem store. IM-006 supplies only
Clock and Scheduler owner adapters. Save hooks, startup recovery, broad owner
adoption, and physical world-save replacement remain separate from this
foundation.

## Filesystem Publication

The IM-005 store writes to an explicit store root supplied by the caller. It
uses a deterministic layout containing `staging`, `generations`, dual head
files, and `quarantine`.

Owner payloads are opaque bytes supplied by test, fixture, or IM-006 Clock and
Scheduler owners. The store validates only owner metadata, expected payload
digests, file presence, and file size. It does not parse Planning,
Transaction, Scheduler, Clock, Inventory, Production, Allocation, Execution,
Evidence, or World Identity payload internals.

A generation is first written to staging. Payloads and owner manifests are
written and verified before the generation manifest. The final generation
directory is published through same-filesystem atomic move when supported. Head
publication uses the dual-slot model and never selects a head by timestamp.

## Recovery Selection

Recovery selection is deterministic and uses only explicit input metadata. The
selector evaluates committed generation records, head records, required owners,
World Identity root references, Platform Determinism Manifest references, and
predecessor-chain validity.

The selected generation is the newest valid committed generation supported by a
valid head and complete predecessor chain. If the newest visible generation is
invalid but an older committed generation remains valid, the selector may choose
the older valid generation and report that fallback explicitly. If no valid
generation exists, recovery returns a recovery-blocked result with typed
diagnostics.

## Rollback Selection

Rollback selection requires explicit operator intent, a target generation, and a
reason. The target must be a valid committed generation within the supplied
generation history. Selecting a rollback target does not delete newer history.
Later runtime integration must publish recovery history or evidence before
reactivating recovered state.

## Minimal Live Owner Integration

IM-006 introduces owner-facing snapshot provider and restorer contracts. Clock
and Scheduler implement those contracts within their owning packages. The
checkpoint coordinator can capture required owner snapshots, assemble an
explicit publication request, recover the selected generation from the
filesystem store, and return opaque payloads to owner restorers.

Restoration is all-or-nothing at the coordinated boundary. Each owner validates
and prepares a restoration candidate first. If any owner fails, no owner
publishes recovered state and the coordinator returns Recovery-Blocked State
diagnostics. If all owners prepare successfully, the coordinator validates the
Clock/Scheduler tick relationship and then publishes the prepared owner states.
If a late publication failure occurs, the coordinator invokes owner-supplied
rollback hooks for every attempted candidate. Successful rollback leaves no
owner published; failed rollback returns typed partial-restoration diagnostics.

This path is explicit API/test integration only. It is not registered with
server start, server stop, autosave, gameplay commands, or operator UI.

## Controlled Development Invocation

IM-007 adds a narrow adapter under `com.butchercraft.development.checkpoint`
and wires it under the existing development diagnostic command branch. The
adapter resolves `<world>/butchercraft/development_checkpoints`, checks that the
path remains inside the active world root, reads the current World Identity
external-root reference without generating or replacing it, uses the narrow
development Platform Determinism Manifest reference, and delegates capture,
publication, listing, validation, and inspection to existing Checkpoint
Recovery APIs.

The command surface exposes capture, list, validate, inspect-selected, exact
generation inspection, and a restore-selected safety rejection. Live
loaded-world restoration remains gated because no service-level Clock/Scheduler
pause/swap boundary is registered for normal world startup or gameplay
recovery. A controlled Java harness proves coordinated Clock/Scheduler
restoration and owner rollback semantics without changing normal world
save/load behavior.

## Deferred Work

The following remain explicitly deferred:

- Inventory, Transaction, Planning, Production, Allocation, Execution, Evidence,
  Goods, Actors, Orders, Contracts, Business Runtime, Workforce, Player
  Identity, and gameplay owner snapshot adapters
- generation archive cleanup
- compression and checksum algorithm selection
- owner save hooks
- world startup recovery
- automatic checkpoint scheduling
- schema migration
- archive mounting
- operator commands
- Evidence Lifecycle integration
- runtime recovery evidence publication
- gameplay or UI integration
- production/operator checkpoint command surface
- live loaded-world restoration boundary

Any future milestone that adds these behaviors must preserve the accepted
Checkpoint Recovery ADR, Platform Canonicalization Addendum, Evidence Lifecycle
ADR, RFC-0022, and RFC-0023 Draft 2 boundaries.
