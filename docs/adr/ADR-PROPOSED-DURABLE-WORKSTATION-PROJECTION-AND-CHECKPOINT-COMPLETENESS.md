# ADR-02A-P1: Durable Workstation Projection And Checkpoint Completeness

Status: RATIFIED; IM-031C-R3A THROUGH IM-031C-R4 IMPLEMENTED; FINAL PRODUCT
OWNER ACCEPTANCE PENDING

Decision identifier: AH-1-ADR-02A-P1

Package: BCSE Checkpoint Recovery Amendment

Authority: Product Owner / Systems Architect-ratified amendment to ADR-02A.
This document authorizes the ownership, identity, durability, recovery,
checkpoint-completeness, compatibility, and milestone direction defined below.
It does not authorize Java, persistence implementation, migration, checkpoint
reclassification code, startup restoration, save mutation, world operation,
gameplay, IM-031C-R3A, IM-031C-R3B, IM-031C-R3C, resumed IM-031C-R4 work, or
IM-032 work. Each implementation milestone requires separate authorization.

Subsequent owner authorizations implemented IM-031C-R3A through IM-031C-R4.
Those implementations do not retroactively broaden this ratification action and
do not authorize IM-032.

This ratified decision amends only the Workstation projection and checkpoint-
completeness boundaries of:

- [`ADR-02 Coordinated Checkpoint And Crash Recovery`](ADR-PROPOSED-CHECKPOINT-RECOVERY.md)
- [`ADR-02A Legacy Split-Snapshot Recovery And Coordinated Checkpoint Publication`](ADR-PROPOSED-LEGACY-SPLIT-SNAPSHOT-RECOVERY-AND-COORDINATED-CHECKPOINT-PUBLICATION.md)

The following ratified owner decisions remain controlling and are not replaced:

- [`DG-002A Workstation Endpoint Durability And Instance Identity`](ADR-PROPOSED-WORKSTATION-ENDPOINT-DURABILITY-AND-INSTANCE-IDENTITY.md)
- [`DG-004 Stack-Aware Workstation Inventory And Partial Transfer`](ADR-PROPOSED-STACK-AWARE-WORKSTATION-INVENTORY-AND-PARTIAL-TRANSFER.md)
- [`DG-005 Persistent Machine Operating State And Continuous Processing`](ADR-PROPOSED-PERSISTENT-MACHINE-OPERATING-STATE-AND-CONTINUOUS-PROCESSING.md)

Canonical platform vocabulary, identity classes, Publication, Recovery,
Replay, Unknown Outcome, Recovery-Blocked State, Operator Authority, World
Identity, and Platform Determinism Manifest remain defined by the
[`Platform Canonicalization Addendum`](ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md).

## 1. Decision In Plain Language

R3 checkpoints can currently preserve exact Workstation block-entity state
only while the corresponding chunk is loaded. An unloaded Workstation is
represented by availability diagnostics rather than by its exact inventory
and machine projection. R4 therefore cannot restore that Workstation to the
selected checkpoint generation.

This amendment introduces a Workstation-owned durable projection for every
active Workstation that must participate in deterministic recovery. The
projection exists independently of chunk availability. Checkpoint Recovery
captures immutable Workstation-provided projection evidence; it does not read
chunks, own inventory, or gain a mutation surface.

The intended relationship is:

```text
Workstation-authorized candidate
    -> Workstation durable publication
        -> loaded block-entity reconciliation
        -> Workstation checkpoint snapshot
        -> owner-native restoration
        -> later lazy chunk reconciliation
```

Checkpoint-time force-loading is not the primary recovery design. A generation
with a missing required projection is not eligible for complete Workstation
restoration.

## 2. Proven R4 Conflict

IM-031C-R3 records loaded Workstation block-entity projections and records
`chunk_unloaded` when an active Workstation's chunk is unavailable. The R4
startup path can validate the Workstation Instance Identity for such an entry,
but it cannot restore the exact inventory, controller, or loaded projection.

Observed repository validation evidence includes:

| Evidence set | Loaded exact projections | Active unavailable projections |
| --- | ---: | ---: |
| Latest GameTest checkpoint | 13 | 661 |
| Recovered generation 68 | 14 | 434 |

Using those generations for complete restoration could combine checkpoint-era
durable owner state with newer vanilla chunk or block-entity state. That is a
mixed-generation restoration prohibited by ADR-02 and ADR-02A.

Historical R3 publication remains valid for its original participant and
evidence contract, but those Workstation payloads are not sufficient to prove
complete restoration when any required entry is only `chunk_unloaded`. R3B
changes new generation eligibility without mutating those generations. R4
remains paused pending R3C for the protected historical R2A target.

## 3. Scope And Non-Goals

This amendment decides only:

- singular ownership of durable Workstation projection;
- exact projection identity, content, revision, and publication invariants;
- loaded and unloaded Workstation relationships;
- checkpoint completeness and generation eligibility;
- restoration and lazy reconciliation rules;
- legacy bootstrap and historical-generation classification;
- bounded storage and checkpoint representation direction; and
- the amended implementation sequence.

It does not authorize:

- Java or persistence implementation;
- changes to any protected or historical world;
- checkpoint-time force-loading as the normal strategy;
- automatic migration scans over unloaded chunks;
- a second inventory, machine-state, endpoint, Execution, Scheduler, Material
  Handling, or Checkpoint authority;
- replay of Workstation effects;
- automatic recovery of Unknown Outcome;
- checkpoint cleanup, general content deduplication, compression, or a new
  retention policy;
- gameplay, UI, commands, public APIs, or machine behavior;
- IM-032; or
- resumption of IM-031C-R4 before the new gates are satisfied.

## 4. Singular Ownership

| Fact or action | Singular owner | Permitted consumers |
| --- | --- | --- |
| Workstation inventory, slot capacity, controller state, and exact current projection | Workstation | Menus, machine controllers, Checkpoint Recovery through immutable snapshots |
| Workstation durable projection schema, revision, publication, reconciliation, and tombstone | Workstation | Checkpoint Recovery, diagnostics, startup lifecycle |
| Workstation Instance Identity and monotonic generation | Workstation | All cross-owner references |
| Endpoint effect commitment, journal, and immutable endpoint owner result | Workstation | Material Handling and Checkpoint Recovery by immutable evidence |
| Machine Operating State and operating revision | Workstation in its existing durable owner record | Block entity and durable projection by exact reference or labeled cache |
| Machine Run lifecycle and child admission | Execution | Workstation and Checkpoint Recovery by exact reference |
| Scheduler Work and effect observation | Scheduler | Execution and Checkpoint Recovery by exact reference |
| In-transit ItemStack custody and transfer lifecycle | Material Handling | Workstation endpoints and Checkpoint Recovery by exact reference |
| Checkpoint generation, completeness validation, selection, and publication | Checkpoint Recovery | Owners and startup lifecycle |

The durable projection is Workstation evidence. It is not a Checkpoint-owned
inventory cache, another block-entity inventory, or an alternate mutation API.
Only a Workstation-authorized serialized owner path may advance it.

## 5. Durable Projection Model

For every active, non-retired Workstation Instance Identity required by the
checkpoint dependency closure, Workstation maintains one exact durable current
projection independent of chunk availability.

A durable projection is:

- bound to one World Identity and one immutable Workstation Instance Identity;
- a complete representation of the Workstation-owned state required to restore
  and reconcile that exact instance;
- schema-versioned and content-digested;
- revisioned monotonically within the instance generation;
- published atomically through repository-wide `AtomicFilePublication`;
- immutable for one publication attempt; and
- readable without loading the workstation's chunk.

It is not:

- a copy from which another subsystem may mutate Workstation state;
- a replacement for the instance registry, endpoint journal, processing owner
  results, or machine operating-state record;
- a duplicate of Execution Machine Run or Scheduler lifecycle state;
- Material Handling custody; or
- permission for Checkpoint Recovery to interpret Workstation internals.

## 6. Minimum Projection Contents

Each projection contains or binds the following Workstation-owned facts:

1. projection schema version;
2. exact World Identity root identity, schema, and digest;
3. exact Workstation Instance Identity and expanded canonical inputs;
4. workstation type, dimension, block position, instance generation, and
   instance-allocation Configuration Identity;
5. monotonic Workstation projection revision and projection Content Identity;
6. every authoritative slot in canonical slot order;
7. exact ItemStack registry identity, count, and complete component map for
   every non-empty slot;
8. the canonical empty representation for empty slots;
9. effective per-slot capacities and capacity Configuration Identity where
   required to validate the represented state;
10. Workstation-owned processing/controller lifecycle and progress only where
    that state is authoritative and recovery-relevant;
11. active recipe or operation identity where Workstation owns that selection;
12. inventory revision and processing/effect freshness required to reject stale
    application;
13. endpoint revision, last-applied journal sequence, endpoint Effect Identity,
    and owner-result reference required to reconcile the current projection;
14. exact reference to the authoritative Workstation machine operating-state
    record and revision when that record exists;
15. exact Machine Run and current child references when the Workstation's
    projection must reconcile against those externally owned identities;
16. Workstation owner-result identities, revisions, and content digests needed
    to establish causal descent;
17. block-state or facing data only when required to verify or reconstruct the
    Workstation-owned block-entity projection;
18. projection Configuration Identity and determinism identity inputs; and
19. active, pending-binding, retiring, retired, replacement-conflict, or
    recovery-blocked disposition as applicable.

The projection does not copy authoritative Machine Run lifecycle, Scheduler
Work, Material Handling custody, Production Run state, or another owner's
mutable record. It stores exact references and owner-issued freshness or
evidence identities needed for reconciliation.

Machine Operating State remains authoritative in the existing Workstation-
owned `machine_operating_states.json` record. A block-entity convenience copy
may appear in the projection only when clearly labeled as a non-authoritative
cache and bound to the exact authoritative operating revision.

Transient client animation, render interpolation, menu state, packets,
pathfinding, and other non-authoritative presentation data are excluded.

## 7. Exact ItemStack And Slot Representation

The projection reuses DG-004's exact stack semantics:

- registry identity is exact;
- count is exact and positive for a non-empty stack;
- the complete component map is encoded through the registry-aware canonical
  codec;
- slot identity and order are explicit;
- empty has one canonical representation; and
- projection identity is quantity-sensitive and component-sensitive.

No count-only, display-name, item-name-only, or default-component
reconstruction is permitted. A projection that cannot decode every exact
stack under a supported registry and schema is Recovery-Blocked and cannot
satisfy checkpoint completeness.

## 8. Loaded Workstation Semantics

After successful startup and reconciliation, the loaded block entity is the
live in-memory Workstation projection used by menus and machine controllers.
It remains inside Workstation authority. The durable projection is the
authoritative recovery surface and the durable completion boundary for the
exact current Workstation projection.

Before a loaded instance becomes mutation-capable:

1. verify World Identity;
2. resolve the active Workstation Instance Identity from the instance registry;
3. load and validate the durable projection;
4. reconcile endpoint journal and machine operating-state references;
5. compare the block-entity projection by identity, revision, exact content,
   and causal owner evidence; and
6. publish either active readiness or a typed blocked state.

The loaded block entity may not overwrite durable evidence merely because it
loaded later or carries a larger local revision. A larger block-entity
revision is accepted only when Workstation-owned journal or owner-result
evidence proves it is a causally valid unpublished durable projection and the
owner can finish publication deterministically. Otherwise the mismatch is
`UNKNOWN_OUTCOME` or Recovery-Blocked.

## 9. Unloaded Workstation Semantics

Chunk unload changes availability, not authority. The last committed durable
projection remains available for checkpoint capture, verification, startup
selection, and recovery analysis.

When a required chunk is unloaded:

- checkpoint capture reads the Workstation-owned durable projection;
- no chunk ticket or force-load is created;
- no placeholder unavailable projection substitutes for exact state;
- processing does not advance merely because a projection exists; and
- normal chunk load later reconciles the block entity to the already-known
  authoritative projection.

An unloaded Workstation with an exact validated projection is the normal
scalable case and is eligible for checkpoint capture.

## 10. Mutation Publication Ordering

Every Workstation mutation that changes a recovery-relevant projection uses
one serialized Workstation-owner boundary and one frozen candidate. The later
implementation milestone derives the precise ordering from the existing
Workstation effect and owner-result publication architecture. It must prove
these ordering constraints:

1. exact instance, current freshness, owner references, pre-state,
   configuration, and locks are validated before commitment;
2. exact post-state bytes, next durable freshness evidence, Content Identity,
   and owner-result references are frozen for the publication attempt;
3. existing effect-commit and owner-result semantics retain their controlling
   phase and are not reinterpreted by the projection store;
4. the exact durable Workstation projection is published consistently with
   those existing semantics before outside observers may treat an incompatible
   state as authoritative;
5. the matching loaded block-entity projection is applied or reconciled
   idempotently and verified; and
6. external result visibility follows only when the controlling owner protocol
   permits it.

For DG-002A endpoint effects, existing journal semantics remain controlling:

```text
PREPARED
    -> endpoint EFFECT_COMMITTED with exact frozen post-state and owner result
        -> durable Workstation projection publication
            -> loaded block-entity reconciliation
                -> RESULT_PUBLISHED
```

A crash after endpoint `EFFECT_COMMITTED` but before projection publication
does not repeat the effect. The endpoint journal's exact frozen post-state is
the authoritative reconciliation evidence from which Workstation finishes the
same projection publication.

For a non-endpoint Workstation mutation, the durable projection publication is
part of authoritative completion. The mutation is not externally acknowledged
as durably complete before that publication succeeds. Existing immutable
processing owner results remain evidence and must bind the same frozen
post-state and projection revision where they are consequence proof.

No implementation may weaken an existing journal or owner-result boundary in
order to add the projection. Checkpoint capture rejects an instance while an
owner publication is between stable phases or while projection reconciliation
is unresolved.

## 11. Projection Revision And Identity

Each Workstation instance owns monotonic durable projection revision or
freshness evidence. Where an existing canonical Workstation revision covers
the complete durable projection cleanly, the implementation reuses it. A
dedicated projection revision is introduced only when existing revisions do
not completely identify the recovery projection. In either case allocation is
serialized, never reused within that instance generation, and never
transferred to a replacement instance.

The projection Content Identity binds at minimum:

- projection schema;
- World Identity root;
- Workstation Instance Identity and generation;
- projection revision;
- exact canonical Workstation-owned projection bytes;
- relevant configuration identities; and
- exact external owner references and freshness evidence needed to interpret
  the projection.

Endpoint effect revision, inventory revision, processing revision, and machine
operating revision retain their existing meanings. Reuse is permitted only
when one canonical Workstation revision already covers every fact required by
the complete projection. Otherwise the projection freshness evidence records
the exact values or references of the narrower revisions without creating a
competing authority for them.

The Workstation checkpoint collection has a separate Content Identity derived
from the canonically sorted required instance identities and each exact
projection identity, revision, digest, disposition, and dependency reference.

## 12. Vanilla Chunk And Block-Entity NBT Relationship

Vanilla chunk NBT remains Minecraft's physical storage for blocks and the
loaded block-entity mirror. After durable projection activation, it is not the
authoritative ButcherCraft recovery surface for Workstation-owned state.

Reconciliation never uses timestamps or file visibility:

| Observed relationship | Required action |
| --- | --- |
| Same instance, older matching causal block-entity projection | Apply the exact durable projection idempotently before activation |
| Same instance, equal revision and exact content | Publish readiness |
| Same instance, greater local revision with exact Workstation owner proof | Finish the already-proven durable publication, then reconcile |
| Same instance, greater or conflicting local state without proof | Recovery-Blocked or `UNKNOWN_OUTCOME`; do not choose either by timestamp |
| Different instance at the same endpoint | Replacement-instance conflict; never apply historical state |
| Identity cannot be proven | `RECOVERY_REQUIRED`; no mutation authority |

Normal chunk saves may persist the reconciled mirror without creating another
authority. Projection writes do not require or trigger a chunk save, and chunk
saves do not automatically advance the projection. Only the Workstation owner
mutation protocol advances authoritative projection revision.

## 13. New Instance Initialization And Retirement

After implementation activation, a new Workstation follows the DG-002A
pending-binding protocol with one added completeness requirement:

1. allocate and durably record the pending Workstation Instance Identity;
2. construct and atomically publish its exact initial durable projection;
3. bind the loaded block entity to that identity and projection;
4. validate exact agreement; and
5. mark the instance active.

An instance is not active or operation-eligible without its projection. If
projection publication fails, activation fails visibly and the generation
remains consumed under DG-002A. Reconciliation may finish the same pending
identity; it does not allocate a replacement identity silently.

Retirement publishes a Workstation-owned tombstone or retired projection
before the registry transition is considered complete. The tombstone binds
the exact instance, final projection revision, retirement cause, relevant
owner references, and retirement revision. A crash between tombstone and
registry publication leaves an explicit reconcilable transition and blocks a
replacement until Workstation resolves it.

Historical projection evidence is retained while referenced by any committed
checkpoint, unresolved endpoint effect, owner result, Machine Run, Execution
operation, Material Handling transfer, recovery result, or Evidence Lifecycle
retention rule. A replacement at the same coordinates always receives a later
instance generation and never inherits the retired projection.

## 14. Legacy Bootstrap And Initial Activation

Legacy Workstations may lack a durable projection. No exact state is fabricated
for an unloaded legacy instance.

A loaded legacy Workstation may bootstrap its first projection only when the
Workstation owner proves all of the following at one serialized boundary:

- exact World Identity and Workstation Instance Identity;
- expected workstation type, dimension, position, and generation;
- a coherent exact block-entity inventory and controller state;
- supported schema and configuration;
- reconciliation with the instance registry, endpoint journal, machine
  operating-state record, owner results, and all active cross-owner references;
- no unresolved effect, replacement conflict, Recovery Required state, or
  Unknown Outcome; and
- successful atomic publication and read-back of the initial projection.

Until then, the instance has typed status
`LEGACY_PROJECTION_UNAVAILABLE` or a repository-equivalent explicit state.
That status blocks any checkpoint that requires the instance from being
classified as fully Workstation-restorable.

Existing healthy worlds improve coverage as legacy chunks load naturally. No
automatic checkpoint-time force-load or unbounded migration scan is
authorized. A later bounded operator migration tool may be proposed separately
but is not required by this amendment.

New worlds created after activation must establish every new Workstation
projection during initialization and therefore do not inherit the legacy
coverage gap.

## 15. Required Projection Eligibility

For one checkpoint boundary, the required Workstation projection set is the
deterministic owner dependency closure of:

1. every active, non-retired Workstation instance in the selected World
   Identity;
2. every Workstation referenced by a nonterminal or recovery-relevant
   Execution operation;
3. every Workstation referenced by an active, suspended, stopping, faulted, or
   recovery-relevant Machine Run;
4. every Workstation referenced by an active, cancellation-pending,
   Recovery Required, or Unknown Outcome Material Handling transfer;
5. every Workstation referenced by an unresolved endpoint effect, immutable
   owner result, or recovery authority block;
6. every Workstation required by another participating owner's exact snapshot
   reference closure; and
7. any retired or tombstoned instance still needed to resolve one of the
   preceding references.

An unrelated retired historical instance is not required merely because its
evidence remains retained. The Workstation owner enumerates and validates the
set in canonical Workstation Instance Identity order. Checkpoint Recovery
validates collection completeness and cross-owner reference closure but does
not decide Workstation content.

A generation is fully Workstation-restorable only when every required entry
has one exact supported durable projection payload, identity, revision, and
digest in the generation's Workstation owner snapshot.

## 16. Missing Projection And Loaded Mismatch Behavior

If any required projection is missing, unsupported, malformed, stale,
conflicting, unreadable, mid-publication, or not reconciled with a loaded block
entity, the checkpoint candidate is rejected before generation commit. The
typed failure identifies the exact instance and reason, such as:

- `workstation_projection_required`;
- `legacy_projection_unavailable`;
- `workstation_projection_schema_unsupported`;
- `workstation_projection_digest_mismatch`;
- `workstation_projection_publication_in_progress`;
- `workstation_projection_mismatch`;
- `replacement_instance_conflict`; or
- `workstation_projection_dependency_incomplete`.

R3B does not publish a new committed diagnostic-only checkpoint generation for
this failure. Existing ADR-02 behavior already keeps a failed candidate
uncommitted and preserves the prior valid head. Diagnostics and immutable
attempt evidence may record the rejection without presenting the candidate as
restorable.

`chunk_unloaded` remains an availability diagnostic, not a projection
substitute. An unloaded exact projection passes. A loaded mismatch fails until
Workstation reconciliation reaches one owner-defined stable state.

## 17. Checkpoint Participant And Representation

Workstation remains one checkpoint participant. Its owner snapshot binds:

- Workstation owner snapshot schema;
- Workstation projection collection identity;
- the canonically sorted required instance set;
- per-instance projection identity, revision, schema, digest, disposition, and
  exact payload;
- required retired or tombstoned records;
- exact external owner references;
- completeness status and validation evidence; and
- the same checkpoint boundary and World Identity as the generation.

For the first completeness implementation, each committed checkpoint embeds
the exact immutable required Workstation projection payloads inside the
Workstation owner snapshot, or uses equally self-verifying immutable references
already authorized by ADR-02. It never references mutable current-projection
files outside the generation. Correctness and self-contained verification take
priority over storage deduplication.

Content-addressed sharing or checkpoint-wide deduplication may reduce later
storage only after a separate accepted design defines immutable blob
publication, reference integrity, reachability, retention, cleanup, and
recovery behavior. This amendment does not broaden R3B into general checkpoint
deduplication.

The physical packing of the Workstation owner snapshot into one or multiple
generation files is an implementation policy provided the generation manifest
binds every exact payload and digest.

## 18. Restoration And Lazy Reconciliation

R4 owner-native restoration may proceed only from a generation that passes the
required Workstation projection completeness rule.

The logical Workstation restoration order is:

1. validate World Identity and Platform Determinism Manifest;
2. validate the selected checkpoint generation and Workstation collection;
3. restore and publish the Workstation instance registry candidate;
4. restore and publish the endpoint journal and owner-result evidence;
5. restore machine operating-state records and other Workstation-owned durable
   records;
6. restore the exact durable projection for every required instance;
7. validate cross-owner Execution, Machine Run, Scheduler, Material Handling,
   and recovery references;
8. publish Workstation recovery authority and typed blocked dispositions; and
9. reconcile each block entity lazily when its chunk later loads.

Startup does not force-load every chunk. The exact desired state is already
known from the restored durable projections. Before a later-loaded block
entity can expose menus, process, accept transfer effects, or admit a child
cycle, Workstation verifies identity and reconciles it to the restored
projection.

Same-instance stale vanilla state is replaced by the exact restored projection
under Workstation authority. A different instance is never overwritten. An
unprovable identity or physical-world mismatch remains explicitly blocked.

Complete Workstation projection evidence establishes what the Workstation
state must be. It does not make vanilla chunks part of the checkpoint commit
protocol and does not authorize fabrication of a missing or replacement
physical block.

## 19. Endpoint, Processing, Machine Run, And Material Handling Relationships

The durable projection complements rather than replaces existing evidence:

- The endpoint journal remains authoritative for transfer endpoint effect
  commitment and immutable endpoint owner results.
- The projection represents exact current Workstation state and carries only
  reconciliation markers and references to journal history.
- Immutable processing owner results remain consequence evidence and bind the
  corresponding projection revision or exact post-state where required.
- The Workstation machine operating-state file remains authoritative for
  operating policy and state.
- Execution remains authoritative for Machine Run and child lifecycle.
- Scheduler remains authoritative for Work and effect observation.
- Material Handling remains authoritative for in-transit custody and transfer
  lifecycle.

Checkpoint cross-owner validation must prove that one unit is owned in exactly
one place. A Workstation slot projection never becomes Material Handling
custody, and Material Handling custody never becomes a Workstation slot merely
because total quantities appear to balance.

## 20. Storage Topology And Atomic Publication

The recommended runtime persistence shape is a Workstation-owned projection
directory with bounded per-instance records rather than one monolithic file
rewritten for every machine mutation:

```text
<world>/butchercraft/workstation_projections/
    <deterministic-shard>/
        <workstation-instance-content-key>.json
```

The logical topology is architectural direction:

- one bounded current projection record per Workstation Instance Identity;
- deterministic enumeration from the authoritative instance registry;
- stable instance-derived keys, never display names or coordinates alone;
- canonical schema and exact digest validation;
- retained tombstones and referenced historical evidence; and
- no independently authoritative projection index.

Exact directory names, shard width, file extension, and packing are
implementation policy. Sharding must be deterministic and must not participate
in identity. The Workstation instance registry remains the authoritative set
of allocated and active instances; filesystem enumeration never allocates
identity or decides membership.

Every per-instance publication uses repository-wide `AtomicFilePublication`
with one frozen byte array, same-target serialization, forced temporary
publication, exact read-back, and typed failure. No new raw move helper or
non-atomic acknowledged fallback is permitted.

Creation, retirement, and journal-linked transitions span existing
Workstation-owned records. Their ordered protocol and retained recovery
evidence must make every crash state deterministic; a partial cross-file
transition blocks activation rather than choosing the newest file.

## 21. Scalability And Performance

The architecture supports hundreds or thousands of Workstations, frequent
bounded machine cycles, a majority of unloaded chunks, and checkpoints every
6,000 simulation ticks without making chunk loading an input.

Required performance properties are:

- one Workstation mutation normally republishes only that instance's bounded
  projection;
- no global projection collection file is rewritten for every child cycle;
- one instance has at most one serialized projection publication in flight;
- unchanged instances do not republish merely because another machine changes;
- checkpoint capture enumerates the registry and freezes immutable projection
  bytes in canonical order;
- the safe boundary performs bounded validation and byte freezing, while
  generation I/O may remain asynchronous under R3 rules;
- no per-machine chunk force-load, polling loop, or wall-clock ordering is
  introduced; and
- projection and checkpoint diagnostics report count, bytes, capture duration,
  unavailable reasons, and completeness without becoming authority.

Embedding exact payloads in each checkpoint increases generation size. That is
an explicit initial tradeoff for self-contained restoration. Cleanup,
compression, immutable blob sharing, and storage budgets remain later policy
or architecture work and cannot weaken required projection exactness.

## 22. Existing Generation Classification

Existing checkpoint generations are immutable and are never rewritten with
invented projection data.

An historical R3 generation containing a required `chunk_unloaded` entry
without an exact embedded durable projection remains a committed checkpoint
artifact and valid evidence for the owner data it actually contains. It is not
eligible for R4 complete Workstation restoration and is classified during
selection as `NON_RESTORABLE_WORKSTATION_PROJECTION_INCOMPLETE` or a
repository-equivalent typed disposition.

The latest observed GameTest generation and recovered generation 68 therefore
must not be described as fully Workstation-restorable on current evidence.
Their immutable manifests and heads remain untouched.

The R2A generation
`butchercraft:checkpoint/00000000000000000001/39872` has the same limitation
for unloaded required Workstations. Its Recovery Identity, Recovery Result,
historical acknowledgements, discontinuity, authority blocks, owner snapshots,
and immutable generation remain valid evidence. The generation is not eligible
for complete R4 restoration until a future separately authorized successor
generation contains exact required Workstation projections.

A successor may be created only from exact owner-proven projections and must
preserve references to the original Recovery Identity, Recovery Result,
generation, and source digests. It is a new generation, not a rewrite or
reinterpretation of generation 1. If exact legacy projection evidence cannot
be established, successor publication remains blocked.

## 23. Crash Matrix

| Boundary | Authoritative evidence | Deterministic next action |
| --- | --- | --- |
| A. Workstation placed, projection not yet published | Pending instance allocation; no active projection | Keep the instance inactive; finish the same initial publication or retire the pending identity visibly |
| B. Projection published, loaded block entity not yet updated | Exact durable projection and pending/active registry evidence | Apply the projection idempotently to the matching instance before activation |
| C. Processing or endpoint effect prepared | Existing owner preparation and old committed projection | Do not infer or apply the effect; preserve the lock and old projection |
| D. Processing or endpoint effect committed | Exact Workstation journal or owner-result evidence with frozen post-state | Do not repeat the effect; finish the exact projection publication from frozen evidence |
| E. Owner result frozen but not externally visible | Existing immutable result plus exact commit/projection references | Reconcile projection, then expose the same result once |
| F. Durable projection publication begins | Prior valid projection plus frozen candidate and publication evidence | Select the last complete atomic file; retry the same frozen candidate only through Workstation authority |
| G. Durable projection published | New exact projection is authoritative recovery evidence | Reconcile the matching loaded block entity; do not roll the projection back from chunk NBT |
| H. Loaded block entity updated | Durable projection and exact equal live projection | Verify equality and publish readiness/result visibility |
| I. Chunk unload after committed mutation | Durable projection remains authoritative | Capture checkpoints without loading the chunk; reconcile normally on later load |
| J. Checkpoint requested during projection publication | Workstation is not at an owner-defined stable capture phase | Reject or defer that checkpoint candidate; preserve the prior committed head |
| K. Crash while chunk is unloaded | Last complete durable projection, registry, journals, and owner records | Recover from Workstation durable evidence without chunk load |
| L. Restore checkpoint while chunk remains unloaded | Selected generation's exact projection set | Restore durable projections and owner references first; leave chunks unloaded |
| M. Chunk later loads after restore | Restored projection plus loaded instance identity and NBT | Reconcile same-instance state before activation; block mismatch visibly |
| N. Replacement Workstation at the same coordinates | Different immutable instance generation and registry conflict evidence | Never apply the historical projection; publish replacement-instance conflict or Recovery Required state |

No crash row permits timestamp selection, duplicate effect application,
identity reuse, inventory total balancing, or Checkpoint-owned mutation.

## 24. Required Future Validation

Later authorized implementation must prove at minimum:

- exact projection identity and revision determinism;
- exact ItemStack/component round trip for every affected slot;
- bounded per-instance atomic publication and read-back;
- loaded and unloaded capture equality for the same projection revision;
- no chunk force-load during capture, verification, or selection;
- checkpoint completeness over hundreds of mostly unloaded Workstations;
- missing legacy projection rejects commit with the exact typed instance;
- natural legacy bootstrap enables later complete capture;
- endpoint committed-before-projection recovery does not reapply an effect;
- processing owner-result reconciliation does not duplicate output;
- active Machine Run, Scheduler, Material Handling, and endpoint dependency
  closure is complete;
- retirement and replacement protection;
- exact selected-generation restoration of per-instance projection storage;
- lazy same-instance chunk reconciliation;
- different-instance and unprovable-identity blocking;
- no mixed-generation vanilla Workstation inventory after restore;
- historical R3 and R2A generations remain byte-identical;
- deterministic second publication output for identical captured input;
- Windows hard-crash behavior at every matrix boundary;
- storage and capture diagnostics at scale; and
- Architecture Manifest changes only after each runtime claim is mechanically
  true.

## 25. Amended Implementation Sequence

No completed historical milestone is renumbered. The ratified sequence is:

1. **ADR-02A-P1 - Durable Workstation Projection And Checkpoint Completeness.**
   Ratified ownership, projection, completeness, historical-classification,
   and milestone direction. No runtime implementation is authorized here.
2. **IM-031C-R3A - Durable Workstation Projection Foundation.** Implement
   Workstation-owned per-instance projection identity, exact schema, atomic
   persistence, publication ordering, legacy bootstrap, retirement, and loaded
   reconciliation. Implemented; it does not activate startup restore.
3. **IM-031C-R3B - Checkpoint Workstation Projection Completeness Activation.**
   Replace `chunk_unloaded` projection substitution with exact durable
   Workstation snapshots, required-set closure, typed rejection, historical
   classification, and scale validation. Implemented; it does not install
   owner-native restore.
4. **IM-031C-R3C - Legacy Workstation Projection Successor Validation.** The
   protected R2A world remains a recovery target, so this milestone is required.
   Establish exact legacy projections
   only from owner-proven loaded state, publish a new successor generation,
   preserve all R2A identities/results, and validate byte-identical protected
   sources. If exact required projections cannot be proven, keep the target
   blocked.
5. **IM-031C-R4 - Startup Checkpoint Selection, Owner-Native Restoration, And
   Hard-Crash Recovery Validation.** Resume only after R3A and R3B are accepted
   and after R3C when the R2A target is included in acceptance.
6. **IM-031C final Product Owner acceptance.** Retest continuous Patty Former
   behavior, Policy B, complete checkpoint startup, lazy Workstation
   reconciliation, and protected-world boundaries.
7. **IM-032 - Employee Machine START/STOP Operation.** Remains gated until
   IM-031C is complete and accepted.

Each implementation item requires separate Product Owner authorization.
Ratification of this amendment alone does not authorize any item.

## 26. Alternatives Rejected By This Amendment

- **Force-load every Workstation chunk at checkpoint time.** Rejected as the
  primary design because facility scale, chunk availability, and checkpoint
  cost would become authority inputs.
- **Treat `chunk_unloaded` as exact restoration evidence.** Rejected because it
  contains no inventory or controller state.
- **Let Checkpoint Recovery serialize block entities.** Rejected because it
  creates a second Workstation-state authority.
- **Use vanilla chunk NBT as the only projection store.** Rejected because
  unloaded exact state is unavailable to checkpoint selection and can belong
  to a different generation.
- **Choose chunk or projection state by timestamp or highest local revision.**
  Rejected because neither proves causal authority.
- **Rewrite one global projection file on every machine mutation.** Rejected
  because mutation cost grows with facility size.
- **Replace endpoint journals with current projections.** Rejected because a
  current-state projection does not preserve consequential effect history and
  owner-result proof.
- **Copy Machine Run or Material Handling runtime into Workstation projection.**
  Rejected because references preserve exactness without transferring
  authority.
- **Retrofit old checkpoint generations.** Rejected because committed
  generation contents are immutable and missing state cannot be invented.
- **Activate general content-addressed checkpoint deduplication now.** Deferred
  because safe blob retention and cleanup require a broader accepted contract.

## 27. Consequences And Implementation Gate

Positive consequences:

- unloaded Workstations can participate in exact checkpoint capture without
  force-loading;
- R4 can know the exact selected-generation Workstation state before chunks
  load;
- Workstation remains the sole inventory and projection authority;
- replacement instances remain protected;
- missing legacy evidence fails visibly; and
- per-mutation persistence cost remains bounded to one instance.

Costs and limitations:

- Workstation gains a new versioned persistence surface and migration gate;
- every recovery-relevant Workstation mutation must satisfy a stronger durable
  completion protocol;
- legacy coverage becomes complete progressively unless a later bounded tool
  is authorized;
- initial checkpoint generations may grow because exact projections are
  embedded or retained through equally self-verifying immutable references;
  and
- existing R3 and R2A generations with missing projections cannot satisfy R4
  complete Workstation restoration.

Ratification changed architecture direction only. Separately authorized
IM-031C-R3A implements the Workstation-owned durable per-instance projection,
atomic sharded persistence, loaded reconciliation, legacy classification,
retirement evidence, deterministic enumeration, and frozen checkpoint-read
candidate. IM-031C-R3B implements exact required-set closure, self-contained
payload capture, fail-closed completeness, no-force-load behavior, historical
classification, and the read-only candidate verifier. IM-031C-R3C publishes and
validates the exact complete historical successor. IM-031C-R4 consumes only a
verified `COMPLETE_RESTORABLE` generation, restores durable projections without
force-loading, and reconciles matching loaded instances before activation.
IM-032 remains gated.

## 28. Additional Ratified Invariants

1. Checkpoint completeness is not chunk-load completeness. An unloaded chunk
   is not a reason for authoritative recovery information to disappear.
2. Durable projection is not another inventory. Workstation remains the one
   inventory authority.
3. Checkpoint Recovery cannot manufacture projection state. It consumes exact
   Workstation-owned immutable evidence only.
4. Vanilla NBT is not newest-wins authority. Filesystem timestamps cannot
   resolve conflicts.
5. Historical generations remain immutable. New architecture changes
   eligibility and classification, never old checkpoint contents.
6. Missing required projection fails closed. Availability never outranks
   deterministic recovery correctness.
7. Checkpointing does not force mass world loading. It remains a persistence
   operation rather than world traversal or chunk migration.

## 29. Ratification Notes

The Product Owner / Systems Architect ratified all 27 owner decisions with the
revisions incorporated above:

1. **Workstation-owned durable projection.** Every active Workstation Instance
   required for deterministic recovery has one exact Workstation-owned durable
   projection independent of chunk availability.
2. **Projection authority.** Workstation is the singular projection owner.
   Checkpoint Recovery, block-entity NBT, Material Handling, Execution,
   Scheduler, and Machine Run do not gain projection mutation authority.
3. **Exact projection contents.** The minimum content and reference set in
   section 6 is ratified, including exact slots and only authoritative,
   recovery-relevant controller state. Another owner's authority is referenced,
   not duplicated for convenience.
4. **Loaded block-entity relationship.** A successfully reconciled loaded block
   entity is the live Workstation projection. It reconciles against the durable
   owner-level recovery projection and does not outrank it merely by loading.
5. **Unloaded Workstation relationship.** An exact durable projection fully
   represents an unloaded Workstation for checkpoint capture, verification,
   analysis, and restoration without force-loading.
6. **Mutation publication ordering.** The durability constraints in section 10
   are ratified. R3A derives precise sequencing from existing Workstation
   effect and owner-result protocols and may not introduce a weaker boundary.
7. **Projection freshness.** Each exact Workstation Instance has monotonic
   durable projection revision or freshness evidence. An adequate canonical
   Workstation revision is reused where possible; no competing revision
   authority is introduced, and no revision transfers to a replacement.
8. **Exact ItemStack representation.** Registry identity, count, complete
   components, and slot identity persist exactly, reusing DG-004 serialization
   where mechanically appropriate.
9. **Vanilla chunk NBT relationship.** Chunk and block-entity NBT remain
   physical persistence and a reconciled mirror. Instance identity, freshness,
   owner evidence, restoration state, and reconciliation rules decide
   conflicts, never timestamps.
10. **New Workstation initialization.** After activation, an initial durable
    projection is part of authoritative initialization. Publication failure is
    fail-visible and no active instance remains indefinitely unprojected.
11. **Retirement and tombstones.** Retirement publishes durable evidence that
    prevents historical resurrection. Replacement coordinates receive a new
    Workstation Instance Identity, and referenced historical evidence remains
    retained.
12. **Legacy Workstation bootstrap.** A legacy instance bootstraps only from
    coherent loaded exact state with proven identity and no conflicting effect,
    Unknown Outcome, or incompatible recovery state. Unloaded state is never
    fabricated.
13. **No checkpoint-time force-loading.** Mass chunk force-loading is rejected
    as the primary checkpoint strategy. A bounded administrative migration or
    loading tool requires separate authorization.
14. **Required projection eligibility.** The deterministic dependency closure
    in section 15 is ratified. Implementation may refine the mechanically exact
    set but may not weaken its coverage.
15. **Missing projection behavior.** Missing required evidence fails closed.
    The generation is not fully Workstation-restorable, exact typed
    incompleteness is reported, and the prior valid committed head remains.
    `chunk_unloaded` never substitutes for exact projection evidence.
16. **Unloaded-but-projected eligibility.** An unloaded instance with an exact
    durable projection is a normal checkpoint-eligible input.
17. **Restoration ordering.** R4 restores durable Workstation projections and
    required Workstation-owned records before runtime mutation authority.
    Loaded chunks reconcile immediately and unloaded chunks later.
18. **Lazy chunk reconciliation.** Lazy reconciliation is permitted only
    because the exact restored state is already selected. Identity, freshness,
    and evidence are verified before activation.
19. **Replacement-instance conflict.** Historical state is never applied by
    coordinates alone. A different or unprovable identity is a typed
    replacement conflict or Recovery Required state.
20. **Storage topology direction.** Bounded deterministic per-instance or
    sharded projection records are ratified. Exact shard topology remains R3A
    policy unless repository constraints reveal another authority decision.
21. **Atomic publication.** Projection persistence exclusively uses the
    repository-wide Windows-safe `AtomicFilePublication` with frozen payloads.
    No raw fixed-temp or non-atomic acknowledged fallback is authorized.
22. **Performance and scalability.** Per-instance mutation cost remains bounded
    for hundreds or thousands of mostly unloaded Workstations. Checkpoint
    enumeration may scale with relevant instances but never loads their chunks.
23. **Checkpoint representation.** Initial checkpoints embed exact projection
    payloads or use equally self-verifying immutable references already
    authorized by ADR-02. Correct self-contained restoration takes priority;
    new content-addressed deduplication remains gated.
24. **Endpoint-journal relationship.** Durable projection represents current
    Workstation state. DG-002A journals retain consequential endpoint effect,
    owner-result, and recovery history. Neither replaces the other.
25. **Historical R3 classification.** Existing generations missing any
    required exact projection remain immutable evidence but are explicitly not
    fully Workstation-restorable. Their manifests are never retrofitted.
26. **R2A generation classification.** Generation 1/39872 retains its Recovery
    Identity, Recovery Result, acknowledgements, discontinuity, authority
    blocks, and other owner evidence, but is not fully Workstation-restorable.
    Any proof-complete packaging is a new successor generation.
27. **Amended implementation sequence.** ADR-02A-P1 ratification is followed by
    separately authorized R3A, R3B, conditional R3C, resumed R4, final IM-031C
    acceptance, and then IM-032. Completed historical milestones are not
    renumbered.
