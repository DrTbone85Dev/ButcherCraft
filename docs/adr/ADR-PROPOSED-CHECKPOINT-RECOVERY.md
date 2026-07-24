# Proposed ADR: Coordinated Checkpoint And Crash Recovery

Status: PROPOSED - OWNER APPROVAL REQUIRED

Decision identifier: Unassigned

Package: BCSE Architecture Hardening AH-1

Authority: This document has no authority until explicitly approved by the
project owner and recorded through the repository's accepted Decision process.

## Context

Current BCSE persistence owners write schema-versioned JSON independently.
Most live services save through separate graceful server-stopping hooks.
Individual storage classes commonly write a temporary file and replace one
target atomically where the filesystem supports `ATOMIC_MOVE`.

That protects one file from a torn replacement. It does not publish one
consistent durable state across the Simulation Clock, Scheduler, Planning,
Inventory, Transactions, Orders, Production, or other owners. Multi-file
subsystems such as Planning and Production also lack a filesystem-wide commit
boundary.

The Scheduler rejects a loaded state whose finalized tick differs from the
authoritative Clock. This fail-visible behavior protects integrity but provides
no automatic recovery.

This proposal responds to
[BCSE-AUDIT-002](../BCSE_ARCHITECTURE_AUDIT.md#bcse-audit-002-no-coordinated-durable-simulation-checkpoint).

## Problem

BCSE needs one durable definition of "the world simulation state at tick T."
It must:

- capture every participating owner at one quiescent boundary;
- validate cross-owner references before committing;
- distinguish prepared files from a committed generation;
- survive process interruption during every phase;
- select a deterministic last-known-good generation;
- preserve owner authority;
- make data loss after a crash explicit;
- support migration from current independent files; and
- avoid claiming atomicity that the filesystem cannot provide.

## Current Behavior

- Simulation Clock, Business Runtime, Workforce, Goods, Economic Actors,
  Inventory, Transactions, Orders/Contracts, Production, Scheduler, and
  Planning register independent save hooks.
- World Identity uses Minecraft `SavedData`; Player Identity has separate
  runtime storage.
- Scheduler schema 1 requires sequential ticks and exact Clock agreement.
- Planning has six files that are individually replaced.
- Production has three files that are individually replaced.
- No global generation identity, owner snapshot manifest, commit marker,
  autosave cadence, prior-generation retention, or recovery selection exists.
- Schema 1 explicitly does not claim crash recovery or catch-up.

This proposal does not implement a checkpoint coordinator or alter current
save behavior.

## Architectural Constraints

The proposal is governed by:

- `AI-0001` Deterministic Simulation;
- `AI-0002` Server Authority;
- `AI-0004` Immutable Identity Separation;
- `AI-0011` Save Compatibility Priority;
- `AI-0016` Explicit Responsibility Boundaries;
- `AI-0018` Versioned Persistence;
- `AI-0019` Formal Invariant Change Control;
- `AI-0020` Stable Identity Contracts;
- `AI-0021` Explicit Failure Outcomes;
- `AI-0022` Authoritative Simulation Time;
- `AI-0025` Singular Data Ownership;
- `AI-0027` Tests Are Part Of The Contract; and
- `AI-0028` Backward-Compatible Evolution.

Additional constraints:

- a checkpoint coordinator coordinates publication but does not own subsystem
  data;
- no subsystem reads another subsystem's mutable internals to serialize it;
- snapshots are immutable values supplied by their owners;
- checkpoint cadence uses simulation ticks, not wall-clock time;
- player presence does not affect checkpoint policy;
- cross-subsystem commit is distinct from in-memory atomic publication;
- an incomplete generation is never treated as committed; and
- implementation requires separate owner authorization.

## Atomicity Levels

This decision distinguishes four non-equivalent guarantees.

### 1. In-Memory Atomic Publication

One owner validates a candidate state and swaps or applies it without exposing
partial in-memory mutation. Examples include Transaction-backed Inventory
change application and Allocation candidate-service publication.

This does not make the state durable.

### 2. Per-File Atomic Replacement

A temporary file replaces one target file, ideally through same-filesystem
atomic rename.

This does not coordinate multiple files or owners.

### 3. Per-Subsystem Durable Snapshot Consistency

Every file owned by one subsystem is represented by one owner snapshot
manifest and either all files validate or the owner snapshot is rejected.

This does not commit other owners at the same simulation generation.

### 4. Cross-Subsystem Committed Checkpoint Consistency

All required owner snapshot manifests are validated for one simulation tick
and referenced by one durable checkpoint commit record. Only this level
represents a loadable BCSE world simulation generation.

## Options Considered

| Option | Advantages | Disadvantages | Crash behavior | Migration/complexity |
|---|---|---|---|---|
| Independent owner saves | Existing simple ownership | Mixed generations and no global recovery | Loader may see valid but incompatible files | No migration, risk remains |
| Ordered shutdown-only saves | Easy incremental improvement | Crash before shutdown loses all progress; interruption still mixes generations | Order narrows but does not remove inconsistency | Small change, insufficient guarantee |
| Global directory per generation | Immutable complete snapshots and easy rollback | Requires duplicate data and commit pointer | Incomplete directory is ignored | Moderate migration |
| Write-ahead manifest | Can announce intended files and detect incompleteness | Manifest updates and recovery semantics become complex | Requires careful prepare/commit states | Moderate to high |
| Two-phase checkpoint publication | Validates all owner snapshots before one commit record | Requires quiescent boundary and coordinator | Last valid commit remains authoritative | High but explicit |
| Checkpoint plus append-only journal | Small recovery loss and exact forward replay | Introduces durable mutation journal across owners and complex truncation | Can replay after checkpoint | Highest; not realistic for first hardening schema |

## Decision Proposed

Adopt a **directory-per-generation checkpoint with two-phase publication and
dual commit slots**. Do not introduce a cross-owner write-ahead mutation
journal in the first checkpoint schema.

### Checkpoint Coordinator Ownership

A Core checkpoint coordinator owns:

- checkpoint trigger deduplication;
- participant ordering;
- snapshot preparation orchestration;
- cross-owner validation orchestration;
- durable generation publication;
- commit-slot publication;
- generation selection during recovery; and
- checkpoint diagnostics.

It does not own:

- Clock state;
- Scheduler Work;
- Inventory quantities;
- Transactions;
- Orders, Contracts, Production, Planning, Allocation, Execution, Businesses,
  Workforce, Goods, Actors, World Identity, or Player Identity;
- subsystem serialization semantics; or
- subsystem migration rules.

Each owner supplies and validates its own immutable checkpoint snapshot.

### `CheckpointGenerationId`

Introduce a stable deterministic generation identity with:

- checkpoint schema version;
- monotonically increasing committed sequence;
- authoritative simulation tick;
- previous committed generation id; and
- previous committed manifest digest.

The proposed canonical textual form is:

```text
butchercraft:checkpoint/<20-digit-sequence>/<simulation-tick>
```

The generation manifest has a separate SHA-256 digest. A failed preparation
does not consume a sequence. Retrying at a later tick uses the same next
sequence and that later tick.

No UUID, wall clock, filesystem timestamp, or random value participates.

### Participants

Every durable authoritative BCSE owner participates either as a mutable
snapshot owner or an immutable root owner.

Current participants:

- World Identity and deterministic identity catalogs;
- Player Identity runtime;
- Simulation Clock and Clock configuration;
- Business Runtime;
- Workforce definitions/runtime;
- Goods and Economic Actors;
- Inventory;
- Transactions;
- Orders and Contracts;
- Production definitions, plans, and runtime;
- Scheduler definitions/runtime;
- Planning cadence, artifacts, and runtime; and
- checkpoint/evidence indexes.

Future mandatory participants when implemented:

- Allocation definitions, providers, runtime, commitments, reports, and
  required evidence;
- Execution definitions, runtime, attempts, reports, history, and traces;
- additional business, ownership, trade, logistics, market, or industry
  runtime that becomes authoritative; and
- any future durable owner named by the architecture manifest.

Immutable roots may be content-addressed and shared by generations, but the
generation manifest records their exact schema and digest. Sharing storage
does not weaken generation validation.

Minecraft chunk, entity, block-entity, and ItemStack persistence remains
outside this decision unless a future accepted boundary explicitly makes it a
participant. A BCSE mutation that also requires vanilla world-state atomicity
needs a separate bridge decision.

### Checkpoint Cadence

Schema 1 proposes:

- periodic checkpoint every **6,000 authoritative simulation ticks**;
- manual server-authoritative request, executed at the next safe boundary;
- graceful-shutdown checkpoint when authoritative state changed after the
  latest committed generation;
- migration checkpoint during first successful legacy import; and
- emergency checkpoint requested by evidence-capacity policy.

Trigger requests are deduplicated. One checkpoint can record multiple causes.
No player connection and no wall-clock interval is an input.

If a periodic deadline is missed because the server is paused or stopped, no
checkpoint is synthesized for each elapsed wall-clock interval. Simulation
ticks did not advance.

### Snapshot Boundary

The checkpoint boundary occurs after the Scheduler has finalized one
authoritative simulation tick and before any owner begins mutation for the
next tick.

At the boundary:

1. the coordinator prevents new BCSE authoritative operations from starting;
2. in-flight operations either finish their owner-level atomic publication or
   leave no publication;
3. owners prepare immutable snapshots in canonical dependency order;
4. the Clock tick and Scheduler finalized tick must equal the checkpoint tick;
5. no snapshot may describe a later tick;
6. cross-owner revisions and references are validated; and
7. normal simulation resumes only after snapshot capture completes.

Durable file writes may continue after immutable snapshots are captured.
Simulation does not need to remain paused while bytes are encoded and written,
provided all encoded values come only from captured snapshots.

Only one checkpoint preparation or publication can exist per world.

### Owner Snapshot Contract

Each participant returns:

- owner id;
- owner snapshot schema version;
- checkpoint generation id;
- represented simulation tick;
- owner revision or sequence;
- canonical immutable snapshot value;
- ordered output file descriptors;
- ordered external references;
- prerequisite owner ids;
- content digest after canonical encoding; and
- validation result.

Owners retain codecs, migrations, and internal reference validation.

### Dependency-Aware Order

Proposed schema-1 preparation and load order:

1. checkpoint configuration and World Identity roots;
2. Simulation Clock;
3. immutable Goods, Actors, Workforce, and other definition roots;
4. Business and Player runtime;
5. Inventory;
6. Transactions;
7. Orders and Contracts;
8. Production;
9. Scheduler;
10. Planning;
11. Allocation, when implemented;
12. Execution, when implemented;
13. evidence/archive indexes; and
14. cross-owner validation report.

This order does not transfer ownership. It ensures prerequisites exist before
dependents validate.

### Preparation Phase

The coordinator:

1. assigns the next candidate generation id;
2. captures participant snapshots;
3. validates owner completeness;
4. validates Clock/Scheduler tick agreement;
5. validates Transaction/Inventory revision agreement;
6. validates Planning/Scheduler Work and cycle references;
7. validates Production/Transaction and Order references;
8. validates future Allocation/Execution authorization and lifecycle
   references;
9. calculates owner digests; and
10. builds a candidate generation manifest.

Any failure aborts before durable commit. Captured snapshots are discarded;
authoritative live state remains unchanged.

### Durable Write Phase

Proposed layout:

```text
<world>/butchercraft/checkpoints/
  generations/
    <generation-id>/
      generation_manifest.json
      owners/
        <owner-id>/
          owner_manifest.json
          <owner files>
  checkpoint_head_a.json
  checkpoint_head_b.json
  abandoned/
```

Directory names use a filesystem-safe canonical representation of the
generation id.

The coordinator writes to a unique temporary directory on the same filesystem,
then:

1. writes and flushes all owner files;
2. writes and flushes owner manifests;
3. verifies all owner digests by reading encoded bytes;
4. writes and flushes the generation manifest;
5. verifies the complete candidate directory;
6. renames the temporary directory to its immutable final generation path;
7. writes the inactive checkpoint-head slot through a temporary file;
8. verifies the new head slot and complete generation; and
9. marks the checkpoint committed by completing the head-slot replacement.

The final generation directory is immutable.

### Generation Manifest

The manifest contains:

- generation id and schema;
- authoritative simulation tick;
- previous generation id and digest;
- ordered participant owner ids;
- owner schemas, revisions, file paths, lengths, and SHA-256 digests;
- cross-owner validation summary digest;
- checkpoint trigger causes;
- evidence horizon and archive manifest references;
- canonical manifest digest; and
- format capability flags.

It contains no mutable `COMMITTED` field. Commit status comes only from a valid
head slot that references the immutable manifest.

### Dual Commit Slots

`checkpoint_head_a.json` and `checkpoint_head_b.json` alternate by committed
sequence parity. Each contains:

- head schema version;
- generation id;
- generation manifest digest;
- sequence;
- simulation tick;
- previous head generation id; and
- head-record digest.

The loader validates both slots and selects the highest-sequence slot whose
entire generation and predecessor link validate. Replacing one slot cannot
destroy the other valid slot.

If the filesystem supports same-filesystem atomic rename, it is used for the
slot replacement. If it does not, BCSE still writes and validates the inactive
slot while preserving the active slot. The durability guarantee becomes
"at least the prior committed slot survives" rather than atomic replacement of
the new slot.

### Last-Known-Good Selection

Recovery:

1. validates both head slots;
2. sorts valid candidates by descending committed sequence;
3. validates each complete generation and predecessor reference;
4. selects the highest complete valid generation;
5. records why any newer candidate was rejected;
6. never merges owners from different generations; and
7. fails visibly if no committed generation and no valid legacy migration
   source exists.

Directory modification time is never used.

### Crash Behavior By Phase

| Crash point | Recovery behavior |
|---|---|
| Before snapshot capture | Existing committed head remains authoritative |
| During capture/validation | No durable candidate is committed |
| During owner file writes | Temporary generation is ignored and later quarantined |
| After owner files, before generation manifest | Candidate is incomplete and ignored |
| After generation manifest, before final directory rename | Temporary candidate is ignored |
| After final directory rename, before head slot | Complete but uncommitted generation is ignored or quarantined |
| During inactive head-slot write | Previous active head remains authoritative |
| After valid new head slot | New generation is committed |
| During old-generation cleanup | Both retained committed generations remain valid; cleanup resumes later |

### Rollback And Catch-Up Policy

Schema 1 recovery rolls back to the last committed generation. It does not:

- merge newer owner files;
- infer missing mutations;
- advance the Clock from wall-clock time;
- rerun unknown non-repeatable effects;
- synthesize skipped Scheduler ticks; or
- automatically catch up lost ticks.

All in-memory simulation progress after the selected checkpoint is lost after
a hard crash. With the proposed periodic default, the normal exposure is fewer
than 6,000 simulation ticks. The recovery report states the last observed
uncommitted tick when such evidence is available, but does not treat it as
authoritative.

A future append-only journal may reduce this exposure only through another
accepted Decision.

### Transaction And Inventory Consistency

The Inventory snapshot has a stable revision. The Transaction snapshot records:

- last authoritative submission sequence;
- every retained APPLIED Transaction digest;
- starting and ending Inventory revision for each retained application; and
- the final Inventory revision represented.

Checkpoint validation requires exact agreement between Transaction and
Inventory revisions and application evidence. It never replays a Transaction
against the captured Inventory merely to make files match.

### Clock And Scheduler Consistency

The checkpoint tick, Clock tick, and Scheduler finalized tick are identical.
Every nonterminal Scheduler Work reference and next eligible tick is validated.
Planning cadence and future Allocation/Execution Work references must resolve
inside the same generation.

### Generation Retention

Retain at least:

- current committed generation;
- previous committed generation;
- second previous committed generation; and
- any generation required by the minimum replay horizon or unresolved
  migration/recovery evidence.

An abandoned temporary generation may be deleted only after it is proven
unreferenced by both head slots and all retained manifests. Cleanup is bounded,
deterministic, and produces diagnostics.

### Manual And Shutdown Checkpoints

A manual request is server-authoritative and creates a trigger for the next
safe boundary. It cannot force snapshot capture in the middle of an owner
publication.

Graceful shutdown requests a checkpoint, waits for the result, and:

- closes normally after success;
- closes with the prior committed generation and explicit failure reporting if
  policy permits; or
- refuses a clean-success status when the requested checkpoint failed.

The exact server shutdown UX is an implementation decision. It may not claim
the newest state is durable after failure.

## Rationale

Immutable generation directories avoid rewriting the last-known-good state.
Two head slots ensure that publication of a new pointer does not destroy the
previous pointer. A quiescent end-of-tick snapshot provides a coherent
simulation boundary without holding the world paused during all file I/O.

A journal is intentionally deferred. The current repository already has
owner-specific full snapshot codecs. Directory-per-generation publication can
reuse those concepts with less behavioral risk than designing a cross-owner
mutation log at the same time.

## Consequences

### Positive Consequences

- One loadable generation represents all BCSE authorities.
- Crash recovery is deterministic and fail-visible.
- The last-known-good state remains available during new publication.
- Multi-file owners gain one durable owner manifest.
- Clock/Scheduler and Transaction/Inventory mismatches are prevented at commit.
- Checkpoint identities correlate evidence across subsystems.
- Future Allocation and Execution persistence have a required platform home.

### Negative Consequences

- Full generations require additional temporary and retained disk space.
- Snapshot capture requires a brief quiescent simulation boundary.
- Every owner needs immutable snapshot and validation contracts.
- Existing independent files require migration.
- A hard crash can lose simulation work after the latest checkpoint.
- Filesystem flush and rename guarantees vary by platform.
- Vanilla Minecraft persistence is not made atomic with BCSE by this decision.

## Compatibility

The proposal introduces a new persistence architecture and requires a new
accepted Decision. Existing schemas remain valid migration inputs. They do not
become committed checkpoint generations merely by existing together.

Public stable ids and domain ownership remain unchanged. File locations and
load orchestration change only after migration.

## Migration

### Legacy Import

Migration creates generation sequence 1:

1. copy or read every current legacy persistence source without modifying it;
2. validate every owner using its current schema;
3. validate all current cross-owner references;
4. require Clock/Scheduler tick agreement;
5. require complete Planning and Production file sets;
6. calculate source file digests;
7. create owner snapshots and migration evidence;
8. prepare and commit generation 1;
9. start runtime only from generation 1; and
10. retain legacy sources read-only until at least two later generations have
   committed successfully.

If validation fails, no checkpoint head is published and current files remain
unchanged.

### World Identity `SavedData`

Migration must select one durable authority. The proposed target is an owner
snapshot inside the checkpoint generation, with Minecraft `SavedData` retained
only as a migration source or compatibility projection. It must not remain a
second mutable authority.

This specific migration requires owner approval because it changes the durable
representation accepted by DEC-0054 without changing World Identity ownership.

### Schema Evolution

Each owner migrates its own snapshot. The checkpoint manifest schema migrates
separately. A generation is committed only after every owner migration and
cross-owner validation succeeds.

## Filesystem And Durability Assumptions

- Temporary and final generation paths are on the same filesystem.
- Canonical bytes are flushed before manifest publication.
- Directory metadata is flushed where the Java/platform implementation can
  provide it.
- SHA-256 detects corruption but does not make a write durable.
- Atomic rename is used when supported and never assumed when unsupported.
- The dual-slot protocol preserves one prior candidate under a failed
  non-atomic replacement.
- Sudden hardware loss can still violate guarantees if the filesystem or
  storage device reports a flush complete without durable media persistence.

The implementation must report detected filesystem capabilities and the
resulting durability level. It must not advertise stronger guarantees.

## Failure Behavior

Proposed explicit outcomes include:

- `CHECKPOINT_ALREADY_IN_PROGRESS`;
- `CHECKPOINT_OWNER_PREPARATION_FAILED`;
- `CHECKPOINT_OWNER_SET_INCOMPLETE`;
- `CHECKPOINT_TICK_MISMATCH`;
- `CHECKPOINT_REFERENCE_VALIDATION_FAILED`;
- `CHECKPOINT_TRANSACTION_INVENTORY_MISMATCH`;
- `CHECKPOINT_OWNER_DIGEST_MISMATCH`;
- `CHECKPOINT_MANIFEST_DIGEST_MISMATCH`;
- `CHECKPOINT_DURABLE_WRITE_FAILED`;
- `CHECKPOINT_HEAD_PUBLICATION_FAILED`;
- `CHECKPOINT_NO_VALID_GENERATION`;
- `CHECKPOINT_UNSUPPORTED_SCHEMA`;
- `CHECKPOINT_LEGACY_MIGRATION_FAILED`; and
- `CHECKPOINT_FILESYSTEM_GUARANTEE_REDUCED`.

Failure-code names are proposed contract names.

Incomplete candidates are quarantined or deleted only after deterministic
reference checks. They are never loaded partially.

## Replay Implications

The selected checkpoint is the authoritative replay baseline. Replay consumes
retained ordered deltas under the evidence-lifecycle decision. Recovery itself
does not replay uncommitted post-checkpoint activity in schema 1.

Checkpoint generation id, owner digests, configuration, and evidence horizon
are replay inputs. Identical committed generation and retained deltas produce
identical recovered state.

## Security And Integrity Implications

- Each owner can publish only under its registered owner id.
- Owner and generation manifests are content-digested.
- Path traversal and duplicate path descriptors are rejected.
- Clients cannot create or select a checkpoint generation.
- Manual requests require server authority.
- Loader selection is sequence/digest based, never timestamp based.
- A malicious or corrupt newer directory cannot shadow an older valid head.
- Cross-owner validation prevents a forged Transaction file from authorizing a
  different Inventory snapshot merely by matching a filename.

## Testing Requirements

Required automated and fault-injection tests:

- deterministic generation identity and sequence;
- duplicate and overflow sequence rejection;
- canonical owner ordering;
- every participant present exactly once;
- Clock/Scheduler tick agreement;
- Transaction/Inventory revision agreement;
- Planning six-file and Production three-file owner consistency;
- future Allocation/Execution participant validation;
- snapshot capture while no next-tick mutation can start;
- simulation resume after immutable capture;
- crash before, during, and after every durable-write step;
- corrupt or partial owner file;
- corrupt owner manifest;
- corrupt generation manifest;
- corrupt head A and valid head B;
- valid heads with different sequences;
- complete uncommitted generation ignored;
- deterministic last-known-good selection;
- no mixed-generation owner loading;
- retained three-generation rollback;
- abandoned-generation cleanup interruption;
- periodic, manual, shutdown, migration, and evidence-pressure triggers;
- trigger deduplication;
- no player-presence or wall-clock dependence;
- legacy schema-1 import;
- failed migration leaves all legacy files unchanged;
- unsupported owner or checkpoint schema;
- atomic-rename supported and unsupported filesystem adapters;
- canonical byte and digest verification;
- full restart equivalence;
- hard-crash rollback with explicit lost-tick diagnostic; and
- large-world checkpoint time, memory, and disk acceptance budgets.

## Alternatives Rejected By This Proposal

- **Independent owner saves:** rejected because valid files can represent
  incompatible generations.
- **Ordered shutdown-only saves:** rejected because ordering is not a commit
  protocol and does not handle hard crashes.
- **Directory-per-generation without two-phase head publication:** rejected
  because complete directories still need one authoritative commit decision.
- **Write-ahead manifest alone:** rejected because a mutable intent manifest
  can itself become a mixed or torn authority.
- **Checkpoint plus append-only journal:** deferred because it introduces a
  larger cross-owner mutation protocol than current schema requires.

## Unresolved Questions

Owner decisions required:

1. Approve or replace the 6,000-tick periodic cadence.
2. Approve the three-generation minimum retention.
3. Approve the dual head-slot protocol.
4. Confirm whether World Identity migrates from `SavedData` into checkpoint
   storage or remains an immutable external root with a validated digest.
5. Define acceptable shutdown behavior when a final checkpoint fails.
6. Define minimum supported filesystem guarantees for release.
7. Confirm whether BCSE/vanilla ItemStack mutations require a future joint
   durability bridge.
8. Confirm operator access to select an older valid generation manually.
9. Confirm whether the initial implementation may compress owner files while
   preserving canonical uncompressed content digests.

## Owner Approval Checklist

- [ ] Approve the four atomicity-level definitions.
- [ ] Approve directory-per-generation two-phase publication.
- [ ] Approve `CheckpointGenerationId`.
- [ ] Approve participant scope and owner order.
- [ ] Approve checkpoint cadence and triggers.
- [ ] Approve snapshot boundary and coordinator authority.
- [ ] Approve dual head slots and recovery selection.
- [ ] Approve explicit rollback/no-catch-up policy.
- [ ] Approve generation retention.
- [ ] Approve legacy migration, including World Identity disposition.
- [ ] Approve filesystem guarantee wording.
- [ ] Approve fault-injection requirements.
- [ ] Authorize creation of an accepted Decision record.
- [ ] Separately authorize implementation.

