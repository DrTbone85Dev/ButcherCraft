# Checkpoint Recovery Foundation

Status: IM-003 metadata foundation, IM-005 filesystem publication foundation,
IM-006 minimal live owner snapshot integration, IM-007 controlled development
checkpoint invocation, IM-031C-R1 read-only split-snapshot analysis,
IM-031C-R2 operator-authorized publication foundation, and IM-031C-R2A
actual-world offline recovery validation, and IM-031C-R3 coordinated live
checkpoint publication implemented; ADR-02A-P1 architecture ratified with
IM-031C-R3A durable Workstation projection foundation and IM-031C-R3B
checkpoint completeness activation implemented. R3C and R4 remain gated.

This document records the narrow pure-Java checkpoint primitives introduced by
IM-003, the minimal filesystem-backed publication proof introduced by IM-005,
and the minimal Clock/Scheduler owner snapshot integration introduced by
IM-006, and the development-only explicit invocation path introduced by
IM-007, plus the immutable read-only split-snapshot analysis introduced by
IM-031C-R1, the explicit publication foundation introduced by IM-031C-R2, and
the offline actual-world evidence validation introduced by IM-031C-R2A. R3
adds live owner capture, automatic and manual triggers, immutable generation
publication, and diagnostics. It does not install recovered state at world
startup, restore native owner files, replay consequences, or add gameplay or UI
recovery behavior. The ratified
[`ADR-02A-P1 durable Workstation projection amendment`](adr/ADR-PROPOSED-DURABLE-WORKSTATION-PROJECTION-AND-CHECKPOINT-COMPLETENESS.md)
controls the R3A Workstation-owned runtime projection and R3B checkpoint-
completeness integration.

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
- content-derived legacy split Recovery Identity and analysis digest
- exact owner source-byte and schema/world validation inputs
- Scheduler-owned nonexecuting Historical Coordination Acknowledgements
- Scheduler-owned Recovery Discontinuity and next-admission boundary evidence
- ordinary Work reconstruction proof classification without insertion
- preserved authorized-but-unscheduled child references under Policy B
- Planning-owned unresolved outcome and dependency-closure blocks
- exact Material Handling custody and replacement Workstation analysis records
- read-only legacy fixed-temp and unique-attempt classification
- immutable operator-authorization evidence binding and readable reports
- exact operator-authority, disposition, world, source, block, Recovery
  Identity, and analysis-digest validation
- mandatory read-only source reload and reanalysis before publication
- immutable owner-prepared recovery snapshots for the complete R2 participant set
- frozen schema-versioned recovery publication intent
- Scheduler acknowledgement, discontinuity, and next-admission publication
  without handler invocation or synthetic ticks
- preserved authorized-unscheduled child and Policy B owner snapshots
- typed whole-world or scoped recovery mutation restrictions
- immutable successor-generation and alternating-head publication
- schema-versioned durable Recovery Result evidence
- crash-boundary retry and exact duplicate observation
- schema-specific Workstation-reservation ownership, with historical
  Workstation schemas retaining their original file and current Workforce
  schema 2 owning the role-aware reservation file exactly once

These types live under `com.butchercraft.world.checkpoint` and are independent
from Minecraft, NeoForge, wall-clock time, random sources, runtime owner
services, and save lifecycle hooks. IM-005 introduces explicit filesystem paths
only inside the checkpoint store boundary and never derives authority from
global paths, world paths, or working-directory assumptions. IM-006 adds
owner-specific adapters under the owning Clock, Scheduler, and World Identity
packages. IM-031C-R1 permits Checkpoint analysis to consume only immutable
Scheduler and Planning recovery evidence types; it remains independent of
their managers, services, mutation APIs, persistence writers, and runtime
startup.

IM-031C-R2 owner preparers remain immutable packaging boundaries. They do not
import owner managers, mutation services, Scheduler handlers, Minecraft, or
NeoForge, and they do not publish owner runtime state. The explicit admin tool
is a Java service boundary rather than a registered Minecraft command.

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

Scheduler also owns Historical Coordination Acknowledgement and Recovery
Discontinuity evidence. Planning owns the unresolved Planning recovery
disposition. Checkpoint Recovery owns analysis, Recovery Identity, typed
cross-owner block references, future authorization-target validation, and
diagnostic formatting. R1 does not persist analysis artifacts because they are
deterministically reproducible from immutable inputs; future publication must
freeze the exact analysis through separately authorized operator evidence. R2
now performs that freeze and publication while preserving each owner's
authority over the snapshot content it supplies.

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

## Operator-Authorized Legacy Recovery Publication

IM-031C-R2 adds an explicit Java admin/service path for `analyze`, exact
authorization, publication, and status observation. Publication always reloads
the source through the read-only R1 boundary and rejects stale authorization,
the wrong world, changed source snapshots, changed authority blocks, or an
incompatible disposition before owner preparation begins.

The R2 participant set includes Clock, Scheduler, Execution, Workstation,
Material Handling, Planning, Production, Transactions, Inventory, Business
Runtime, Workforce, Goods, Economic Actors, Orders, Contracts, Player Identity,
and Checkpoint Recovery. Owner-specific preparers publish the changed recovery
facts for Clock, Scheduler, Execution, Workstation, Planning, and Checkpoint
Recovery. Other participants package their exact unchanged source snapshots;
these are not broad live owner adapters.

Preparation produces immutable owner payloads and does not mutate live state.
The frozen publication intent binds the authorization, source snapshots,
participant set, generation, and predecessor. The filesystem store then
publishes an immutable generation and updates the inactive head slot. The
durable Recovery Result records the exact committed content and preserves any
remaining whole-world or scoped authority restrictions. Retries after any
injected publication boundary converge on the same intent, generation, and
result. A previous committed generation remains selected until the new head is
durable.

The implementation deliberately does not register a Minecraft command or
startup hook, install owner snapshots into a loaded world, run Scheduler
handlers, synthesize skipped ticks, replay consequences, resume Policy B Runs,
or mutate the source evidence. Those operations remain later milestones.

## Actual-World Offline Recovery Validation

IM-031C-R2A adds a development-only, read-only adapter for the actual legacy
owner persistence formats. It hashes logical owner-file names and exact bytes,
validates supported schemas and identity bindings, and derives R1 evidence
without depending on absolute paths, directory names, modification times, or
file enumeration order. Immutable Workstation owner results retained inside
Execution persistence are included as exact Workstation recovery evidence;
this preserves Workstation ownership and does not grant Execution authority.

The protected failed-world evidence produced Clock `39872`, Scheduler `39084`,
nine proof-complete historical acknowledgements, zero ordinary Work
reconstruction, discontinuity `39085-39872`, and next admission `39873`. Exact
child 10 remains `AUTHORIZED`, unscheduled, uninvoked, and nonterminal under
Policy B. The original Machine Run and Workstation instance remain bound, and
the unproven Planning continuation at tick `39601` retains a whole-world
consequential-mutation block.

The content-derived actual-world reference values are:

- Recovery Identity: `butchercraft:legacy_split_recovery/v1/d2b39d6b69ea9eabcb5b8e05cf26e8ca34d6afcb7646f062fe6a9251ac73c5f5`
- analysis digest: `sha256:9854af750c72e74607f9b3975def0491ecaaba06438c5881ec41a714c0cd0aaa`
- committed generation: `butchercraft:checkpoint/00000000000000000001/39872`
- protected native manifest: `998b0395fa98e3621eb95bea5080f5592895bc21d4c35e2fff4db7cc20484ceb`

Offline publication, result reload, fresh-service recovery selection, exact
duplicate observation, and every R2 publication fault boundary are validated
on disposable copies. All 61 native non-lock files remain byte-identical;
checkpoint artifacts are additive. Minecraft startup was not attempted because
native owner restoration and startup generation selection belong to R4.
The exact source snapshots and publication identities are recorded in
[`IM-031C-R2A Actual-World Offline Recovery Validation`](IM-031C-R2A-ACTUAL-WORLD-OFFLINE-RECOVERY-VALIDATION.md).

## Coordinated Live Checkpoint Publication

IM-031C-R3 activates live publication without activating startup restoration.
Checkpoint Recovery owns the trigger queue, generation candidate, exact
required-participant registry, completeness and integrity verification,
immutable filesystem generation, alternating dual heads, and publication
diagnostics. Each participant remains the only authority for its snapshot
content and schema.

The canonical live participant set is Clock, Scheduler, Execution,
Workstation, Material Handling, Planning, Production, Transactions, Inventory,
Business Runtime, Workforce, Goods, Economic Actors, Orders, Contracts, Player
Identity, and Checkpoint Recovery. Empty owners publish canonical,
schema-versioned payloads rather than disappearing from the generation.

Capture occurs on the server thread after Scheduler finalizes the exact
authoritative Clock tick. Any Clock/Scheduler mismatch rejects the candidate.
Owner state is serialized into immutable bytes during that boundary; slow
filesystem publication and verification then run on one asynchronous
publisher. Workstation supplies exact durable projection bytes for required
loaded and unloaded instances. Snapshot creation never force-loads chunks,
mutates owner state, or invokes domain consequences.

The R3A Workstation-owned projection stores exact per-instance state outside
chunk NBT and exposes frozen read candidates without force-loading. R3B closes
the exact required set from owner state, verifies loaded projection agreement,
embeds each exact payload and digest, and accepts unloaded projections directly.
Missing or invalid required evidence rejects the candidate before commit and
preserves the prior valid head. Historical `chunk_unloaded` generations remain
immutable and are classified non-restorable by a read-only verifier.

R3A records use schema 1 at
`<world>/butchercraft/workstations/projections/v1/<aa>/<bb>/<identity-sha256>.json`.
They are Workstation-owned, bounded per instance, atomically published, and
readable while the block entity is absent. Operators may inspect classification
and persisted-byte totals through
`/butchercraft diagnostic checkpoint workstation-projections`. It also reports
required, available, loaded, unloaded, participant-byte, timing, blocker, and
restorability values. This diagnostic does not publish a generation.

Manual operators use `/butchercraft diagnostic checkpoint create` and
`/butchercraft diagnostic checkpoint status`. Periodic capture is eligible
every 6,000 authoritative simulation ticks. A graceful shutdown attempts a
final changed-state checkpoint and waits at most ten seconds. Timeout preserves
the prior committed head and never starts a second concurrent publication.

Committed generations are retained because archive cleanup, compression, and
storage budgets remain unspecified. R3 can inspect and verify a committed head
but does not select it for startup or install it into native owner state.
Operational details are in
[`Live Checkpoint Publication`](LIVE_CHECKPOINT_PUBLICATION.md).

## Deferred Work

The following remain explicitly deferred:

- generation archive cleanup
- compression and checksum algorithm selection
- world startup recovery
- schema migration
- archive mounting
- Evidence Lifecycle integration
- live owner recovery-state installation and reconciliation
- gameplay or UI integration
- live loaded-world restoration boundary
- R3C legacy Workstation projection successor validation for the protected
  historical recovery target

Any future milestone that adds these behaviors must preserve the accepted
Checkpoint Recovery ADR, Platform Canonicalization Addendum, Evidence Lifecycle
ADR, RFC-0022, and RFC-0023 Draft 2 boundaries.
