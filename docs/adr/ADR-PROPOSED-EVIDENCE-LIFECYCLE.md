# ADR-01: Platform Evidence Lifecycle

Status: RATIFIED ARCHITECTURAL DIRECTION - IMPLEMENTATION NOT AUTHORIZED

Decision identifier: AH-1-ADR-01

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

BCSE intentionally produces immutable evidence: Transaction history,
Scheduler outcomes, Planning artifacts, Allocation reports and traces,
Production runtime history, and future Execution reports, traces, and
histories. Immutable evidence supports deterministic replay, audit,
diagnostics, and explanation.

The current architecture defines evidence models and per-operation budgets but
does not define a platform-wide lifetime, archive, compaction, or storage
budget. Planning currently retains every cycle and every artifact in hot
runtime and rewrites the complete history on save.

This decision addresses evidence portions of
[BCSE-AUDIT-001](../BCSE_ARCHITECTURE_AUDIT.md#bcse-audit-001-planning-history-grows-without-a-lifetime-bound)
and
[BCSE-AUDIT-002](../BCSE_ARCHITECTURE_AUDIT.md#bcse-audit-002-no-coordinated-durable-simulation-checkpoint).

## Problem

BCSE must preserve immutable and authoritative evidence without allowing
world-lifetime memory, save time, load time, and disk use to grow without an
explicit policy.

The architecture needs to distinguish:

- mutable authoritative runtime;
- facts required to reconstruct or replay current runtime;
- permanent audit facts;
- explanatory reports and traces;
- derived summaries and diagnostics;
- committed checkpoints; and
- cold archived evidence.

No authoritative fact may disappear because a count, tick, or byte budget is
reached.

## Current Behavior

- Planning retains every cycle and all associated artifacts in memory.
- Planning rewrites six complete files on save.
- Transaction history is append-only and independently persisted.
- Scheduler, Production, Allocation, and Planning expose immutable reports,
  histories, traces, or outcomes with subsystem-specific rules.
- There is no cross-subsystem correlation identity.
- There is no hot/cold boundary, partition contract, compaction record,
  archive query guarantee, or storage-exhaustion behavior.
- RFC-0023 Draft 1 requires immutable Execution evidence but does not define
  retention.

This decision defines future policy only. It does not delete, compact, migrate,
or move current evidence.

## Architectural Constraints

The decision is governed by:

- `AI-0001` Deterministic Simulation;
- `AI-0004` Immutable Identity Separation;
- `AI-0010` Immutable Public Views;
- `AI-0011` Save Compatibility Priority;
- `AI-0016` Explicit Responsibility Boundaries;
- `AI-0018` Versioned Persistence;
- `AI-0020` Stable Identity Contracts;
- `AI-0021` Explicit Failure Outcomes;
- `AI-0025` Singular Data Ownership;
- `AI-0026` Bounded Simulation Work;
- `AI-0027` Tests Are Part Of The Contract; and
- `AI-0028` Backward-Compatible Evolution.

Additional constraints:

- immutable evidence records are never rewritten in place;
- archive movement does not change evidence identity or content;
- no authoritative fact is silently deleted;
- compaction must state whether replay capability changes;
- storage budgets are authoritative configuration and replay input represented
  by the Platform Determinism Manifest;
- wall-clock age and player presence do not control retention;
- current subsystem ownership does not transfer to an archive service; and
- implementation requires separate owner authorization.

Each authoritative fact remains owned by exactly one originating subsystem.
Evidence Lifecycle owns classification, retention policy, archive placement,
compaction records, integrity verification, and query policy. It does not
become an alternate authority for the facts represented by that evidence.

## Evidence Classification

### Authoritative Mutable Runtime

Current state owned by a subsystem, such as Inventory quantities, Scheduler
Work runtime, Production Run runtime, Planning cadence runtime, AllocationSet
runtime, and future Execution runtime.

Properties:

- mutable only through its owner;
- represented in a committed checkpoint;
- not an append-only evidence category;
- prior values may be explained by retained evidence; and
- never reconstructed from disposable diagnostics.

### Replay-Critical History

Immutable ordered facts required to replay from the oldest retained checkpoint
to the current authoritative state.

Examples:

- applied Transactions and their exact Inventory freshness evidence;
- Scheduler Work transitions and invocation outcomes;
- Planning trigger and cycle publication records;
- Allocation lifecycle transitions and Commitment publication/release;
- Production lifecycle and completion-Transaction observations; and
- future Execution attempts, transaction observations, and publications.

Replay-critical history cannot be expired while it remains inside the minimum
replay horizon.

### Permanent Audit History

Immutable facts that retain long-term business or integrity meaning even after
a later checkpoint subsumes them for runtime reconstruction.

Schema-1 classification:

- every APPLIED economic Transaction;
- Order and Contract fulfillment attribution;
- terminal Production outcomes and completion Transaction references;
- Allocation Commitment creation, activation, release, expiration, and
  terminal failure evidence;
- future terminal Execution completion, failure, and cancellation evidence;
- checkpoint commit and migration records; and
- integrity conflicts or recovery selections that changed the loaded
  generation.

Permanent audit history may move to a cold archive. It is never deleted or
content-compacted.

### Reports

Immutable subsystem explanations of a bounded operation. Reports are
archiveable. A report may be reconstructable from permanent facts, but the
original report identity and digest remain available when it is cited by
another authoritative record.

### Engineering Traces

Detailed deterministic phase evidence used to diagnose ordering, validation,
selection, or publication.

Two classes are required:

- **integrity traces:** cited by failure, recovery, migration, or publication
  evidence and therefore archiveable but not disposable;
- **diagnostic traces:** uncited engineering detail that may expire after its
  deterministic retention period.

### Diagnostics

Non-authoritative messages, counters, and performance observations. Diagnostics
may be deterministically expired. They cannot be the only evidence of an
authoritative transition.

### Summaries

Immutable derived indexes or aggregates over identified source evidence.
Summaries contain:

- summary identity;
- source partition identities;
- source digest set or aggregate digest;
- deterministic algorithm/schema version; and
- covered tick and sequence range.

Summaries are reconstructable and replaceable. They never replace permanent
audit facts.

### Checkpoints

Committed coordinated snapshots defined by the Checkpoint Recovery ADR. A
checkpoint can subsume prior replay deltas for runtime reconstruction but
cannot erase permanent audit facts.

### Archived Evidence

Evidence moved out of hot runtime into immutable partition files with manifests
and indexes. Archive location is not part of evidence identity.

### Disposable Derived Evidence

Uncited diagnostics, rebuildable indexes, caches, and diagnostic-only traces.
Expiry must be deterministic and recorded in a compaction report.

## Options Considered

| Option | Advantages | Disadvantages | Replay effect | Operational effect |
|---|---|---|---|---|
| 1. Count-based rolling retention | Simple hard memory bound | Count does not align with time or checkpoint recovery; can delete critical facts | Unsafe unless categories are perfectly separated | Predictable count, uneven tick coverage |
| 2. Tick-window retention | Stable simulation-time horizon | High-volume periods can exceed memory; low-volume periods retain arbitrary counts | Clear horizon if all deltas are retained | Disk and memory remain volume-sensitive |
| 3. Partitioned append-only archives | Preserves all immutable evidence and supports bounded loading | Archive count and disk still grow; indexes and recovery are required | Excellent long-term replay potential | Good hot-state behavior; archive operations required |
| 4. Checkpoint plus bounded delta history | Strong recovery boundary and small hot state | Permanent audit facts need a separate home; bad checkpoint policy can shrink auditability | Exact within retained checkpoint horizon | Predictable load and restart |
| 5. Hybrid hot evidence plus cold archives | Separates operation, replay, and audit concerns; preserves evidence | Highest specification and migration complexity | Exact recent replay plus permanent audit history | Bounded hot state with explicit archive growth |

## Decision

Adopt **Option 5: hybrid hot evidence plus cold partitioned archives**, using
committed checkpoints and bounded replay deltas.

### Evidence Identity

Evidence Identity is defined by the
[`Platform Canonicalization Addendum`](ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md#2-platform-vocabulary).
Archive movement and hot-store removal do not change evidence identity.

### Cross-Subsystem Correlation

Use the addendum's platform identity rules for cross-subsystem correlation.
The exact Java name remains an implementation decision after approval.

The root correlation identity is deterministically derived from the initiating
authoritative fact, for example:

- Order/Contract lifecycle record;
- Scheduler Work submission;
- manual server-authoritative request;
- checkpoint migration;
- system policy trigger; or
- other owner-defined stable cause.

Child evidence carries the root correlation id and its direct causal
predecessor identity. Correlation is reference-only and transfers no ownership.

### Hot Replay Horizon

Hot storage retains the schema-1 operational default horizon:

- the **three most recent committed checkpoint generations**;
- all replay-critical deltas from the oldest retained generation through the
  current authoritative tick;
- all unresolved failure and recovery evidence;
- all currently active runtime references; and
- indexes required to resolve those records.

With the schema-1 checkpoint cadence default, the minimum normal replay
horizon is at least two checkpoint intervals plus the current uncheckpointed
delta. Manual and shutdown checkpoints do not shorten the oldest retained
period. The exact generation count and cadence remain schema-1 operational
defaults aligned with the Checkpoint Recovery ADR rather than permanent
Evidence Lifecycle invariants.

No evidence is removed from hot storage until:

1. a newer checkpoint is committed;
2. the evidence is no longer required to replay from the oldest retained
   checkpoint;
3. every permanent or archiveable record is durably present in a validated
   archive partition;
4. archive manifests and indexes are committed; and
5. a deterministic compaction report records the movement or expiry.

### Archive Partitioning

Cold archives use the schema-1 operational default partitioning by:

1. owner subsystem id;
2. evidence category;
3. fixed simulation-tick range of **100,000 ticks**; and
4. schema version.

Tick range `N` is:

```text
floor(simulation_tick / 100000) * 100000
through
floor(simulation_tick / 100000) * 100000 + 99999
```

Records inside a partition are ordered by:

1. authoritative simulation tick;
2. owner sequence, when present;
3. evidence type;
4. stable evidence id; and
5. content digest.

One partition can receive append batches until its tick range closes. Each
append produces a new immutable partition segment and a new manifest revision;
existing segments are never rewritten. A closed partition is immutable.

### Retention By Category

| Category | Hot retention | Cold retention | Deletion/compaction rule |
|---|---|---|---|
| Authoritative mutable runtime | Current checkpoint plus current in-memory state | Prior runtime only when required by migration or integrity evidence | Superseded runtime is checkpoint-subsumed after replay horizon |
| Replay-critical history | Oldest retained checkpoint through current tick | Archive if also audit/history evidence | Cannot expire inside replay horizon |
| Permanent audit history | While actively referenced or in replay horizon | Permanent | Never deleted or content-compacted |
| Reports | Three checkpoints or while referenced | Permanent when cited; otherwise archive for configured audit horizon | Reconstructable uncited reports may expire only after source digest is retained |
| Integrity traces | Three checkpoints or while unresolved/cited | Permanent with cited evidence | Never deleted while cited |
| Diagnostic traces | Up to three checkpoints and 100,000 records per owner/type, whichever bound is reached first | None by default | Deterministic expiry with compaction report |
| Diagnostics | Current and previous checkpoint, maximum 10,000 records per owner/type | None | Deterministic rolling expiry; never used as authoritative evidence |
| Summaries/indexes | Current versions required for query | Rebuildable | Replaceable when source identities/digests remain |
| Checkpoint manifests | Three generations hot | Permanent commit/migration records | Generation payload may age out; commit record remains |

Report audit-horizon configuration may increase retention. It cannot shorten
retention for a report cited by permanent evidence.

### Storage Budgets

Schema 1 proposes world-authoritative operational defaults:

- maximum hot evidence records per owner/category: **100,000**;
- maximum diagnostic records per owner/type: **10,000**;
- maximum open archive segments per partition: **1,024**;
- maximum archive partitions: **10,000**;
- maximum canonical archive bytes: **10 GiB**; and
- minimum free-space reserve before a new durable publication: **256 MiB**.

Numeric retention, partition, capacity, and storage values are schema-1
operational defaults rather than permanent architectural invariants. They may
be revised through an accepted implementation milestone before public save
compatibility is promised, provided that evidence ownership, deterministic
archival, explicit replay guarantees, and the prohibition against silent loss
of authoritative or permanent audit facts remain intact.

All values are schema-versioned configuration and participate in replay.
Canonical archive bytes are measured from the exact encoded bytes covered by
the archive digest, not platform allocation size.

When replay-critical hot evidence approaches its count bound, BCSE requests a
checkpoint at the next safe boundary. If checkpoint or archival publication
cannot complete before the bound is reached, new simulation operations that
would require durable evidence are rejected before authoritative side effects.
The world enters a fail-visible `EVIDENCE_CAPACITY_EXHAUSTED` operating state.

Permanent evidence is never deleted to satisfy a byte or partition budget. An
operator must export a validated archive, increase the authoritative budget,
or provide additional storage. Recovery and inspection remain available.

### Deterministic Compaction

Compaction is a bounded Scheduler-controlled operation and uses simulation
ticks, checkpoint generations, fixed partition ranges, and canonical ordering.
It does not use file timestamps or wall-clock age.

Compaction may:

- move immutable records from hot to cold storage;
- close archive partitions;
- rebuild indexes;
- generate source-linked summaries;
- remove checkpoint-subsumed runtime snapshots; and
- expire disposable diagnostics or uncited diagnostic traces.

Compaction may not:

- rewrite evidence content;
- reuse evidence identities;
- delete permanent audit history;
- remove replay deltas inside the retained horizon;
- merge distinct authoritative facts into one replacement fact; or
- proceed when source and destination digests do not validate.

### Query Guarantees

The platform guarantees:

- lookup by owner and stable evidence id across hot and cold indexes;
- ordered range query by simulation tick and owner sequence;
- lookup by `SimulationCorrelationId`;
- retrieval of every permanent audit record;
- retrieval of every replay-critical record in the minimum replay horizon;
- explicit `ARCHIVE_NOT_MOUNTED`, `PARTITION_CORRUPT`, or
  `EVIDENCE_EXPIRED_BY_POLICY` outcomes; and
- immutable query results.

Cold queries may have higher latency. Query latency is not authoritative
simulation input.

### Server Restart

Startup loads the committed checkpoint, hot delta indexes, required archive
manifests, and unresolved integrity evidence. Cold partition bodies may load
on demand after manifest validation. Missing required replay or permanent
evidence fails visibly.

When required cold evidence is unavailable, the affected world enters the
Recovery-Blocked State defined by the
[`Platform Canonicalization Addendum`](ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md#7-failure-model).
New side-effecting simulation work for the affected authority is prohibited
until explicit recovery or operator action resolves the condition.

Disposable diagnostic expiry is not performed implicitly during load. It runs
as an explicit bounded compaction operation.

### Subsystem Classification

| Subsystem | Permanent/audit evidence | Checkpoint-subsumed or reconstructable evidence |
|---|---|---|
| Transactions | APPLIED Transaction and exact mutation/application identity | validation diagnostics and rejected proposals after policy horizon |
| Inventory | freshness evidence cited by applied Transactions and recovery conflicts | intermediate runtime snapshots before retained baseline |
| Orders/Contracts | definitions, obligations, fulfillment attribution, terminal lifecycle evidence | derived open-order summaries |
| Production | terminal outcome, completion Transaction link, failure/cancellation evidence | progress reports before retained baseline when uncited |
| Scheduler | Work submission identity, terminal outcome, recovery conflict | eligible-queue summaries and uncited per-phase diagnostics |
| Planning | Approved Plan provenance and cited decision evidence | rejected candidates, routine observations, and reconstructable summaries after archive policy |
| Allocation | Commitment lifecycle, terminal outcomes, release/expiration evidence | uncited selection diagnostics after audit horizon |
| Future Execution | completion/failure/cancellation, attempt and Transaction correlation | routine progress reports and uncited engineering detail after policy horizon |

Planning observations, needs, opportunities, and candidates are immutable
evidence. They may be archived. They are not silently deleted merely because
they are not permanent audit facts.

## Rationale

The hybrid architecture separates three different requirements:

- fast current operation;
- exact bounded recovery/replay; and
- long-term immutable audit.

Count-only or tick-only retention cannot satisfy all three. Checkpoints bound
replay state, while fixed tick partitions preserve immutable evidence without
requiring every record to remain loaded or rewritten. Fail-visible storage
exhaustion preserves integrity.

## Consequences

### Positive Consequences

- Hot memory and normal startup remain bounded.
- Permanent audit facts remain immutable and available.
- Replay horizon is explicit and testable.
- Compaction cannot silently change authoritative history.
- Cross-subsystem causal queries become possible.
- Storage exhaustion has a defined safe failure.
- Future Execution evidence depends on a platform contract rather than
  inventing another retention policy.

### Negative Consequences

- Archive manifests, indexes, compaction evidence, and correlation identities
  add schema and operational complexity.
- Permanent audit archives still grow until exported.
- Cold queries are slower than hot queries.
- Owners must classify every new evidence type.
- Existing persistence files require migration.
- A hard storage limit can pause new authoritative simulation work.

## Compatibility

Existing evidence identities and content remain valid. New category,
correlation, archive, and checkpoint metadata are additive. No current record
may be reclassified as disposable without an explicit migration rule and owner
approval.

RFC-0023 must reference this decision rather than require an independent
unbounded Execution history.

## Migration

### Existing Planning Evidence

Migration must:

1. validate the complete six-file Planning set;
2. preserve every cycle and artifact;
3. assign existing records to deterministic 100,000-tick archive partitions;
4. retain existing stable identities and calculate canonical content digests;
5. create migration correlation and archive manifests;
6. keep evidence required by the retained checkpoint horizon hot;
7. move older evidence only after the first new checkpoint and archive commit;
8. retain legacy files as a read-only migration source until at least two
   later checkpoints commit; and
9. fail without modifying legacy files if any record or cross-reference is
   invalid.

### Other Existing Evidence

Each owner provides a versioned importer. Import order follows the coordinated
checkpoint dependency order. The initial migrated checkpoint records source
file digests and imported record counts.

No importer invents missing authoritative history.

## Failure Behavior

- Missing permanent archive partition: fail world initialization or enter
  explicit recovery mode.
- Corrupt archive segment: `EVIDENCE_ARCHIVE_DIGEST_MISMATCH`.
- Duplicate evidence identity with different content:
  `EVIDENCE_IDENTITY_CONFLICT`.
- Hot capacity exhausted: request checkpoint; if unsuccessful, reject new
  evidence-producing side-effecting operations before mutation.
- Archive budget exhausted: preserve existing evidence and reject new
  operations before side effects.
- Compaction interruption: retain source evidence; ignore uncommitted
  destination segments.
- Index corruption: rebuild from validated manifests and segments.
- Summary corruption: discard and rebuild the derived summary only.
- No failure path deletes authoritative evidence or fabricates a successful
  archive.

Failure-code names are architectural contract names, not implemented
constants.

## Replay Implications

Exact replay is guaranteed from the oldest retained checkpoint through current
state. Older replay is possible only when the required checkpoint or initial
baseline and all corresponding archived deltas are retained.

Compaction does not change replay within the guaranteed horizon. A policy that
removes reconstructable old detail outside that horizon must report the
earliest replayable tick and cannot claim full-history replay.

## Security And Integrity Implications

- Canonical SHA-256 digests protect evidence and partition manifests.
- Owner ids and evidence ids prevent one subsystem from overwriting another's
  records.
- Correlation references are validated but do not grant mutation authority.
- Archive import/export requires complete manifest and digest verification.
- Clients cannot request deletion, retention reduction, or authoritative
  compaction.
- Storage exhaustion cannot trigger silent evidence loss.

## Testing Requirements

Required automated tests:

- classification rules for every evidence type;
- stable evidence and correlation identities;
- deterministic 100,000-tick partition assignment;
- canonical ordering within segments;
- archive append and partition closure;
- source record immutability after archival;
- three-checkpoint replay horizon;
- permanent evidence retained beyond the hot horizon;
- diagnostic and trace expiry at exact boundaries;
- cited report/trace retention;
- summary rebuild from source digests;
- hot-count checkpoint trigger;
- byte, partition, segment, and free-space budget failures;
- no deletion on capacity exhaustion;
- crash during each compaction phase;
- missing/corrupt partition diagnostics;
- hot and cold query equivalence;
- replay from checkpoint plus retained deltas;
- migration of all current Planning evidence;
- migration rollback on malformed legacy data;
- one million Planning cycles distributed across archives; and
- deterministic results independent of file timestamps and player presence.

## Alternatives Rejected By This Proposal

- **Count-based rolling retention:** rejected as the sole policy because it can
  erase replay-critical or permanent facts.
- **Tick-window retention:** rejected as the sole policy because evidence
  volume inside one window is unbounded.
- **Append-only archives only:** rejected because current runtime and startup
  would still need a bounded checkpoint baseline.
- **Checkpoint plus delta only:** rejected because permanent audit evidence
  must remain queryable after it is no longer needed for runtime replay.

## Ratification Notes

Owner ratification approved the hybrid evidence lifecycle model with the
revisions incorporated above:

1. Evidence Lifecycle owns evidence policy, not subsystem facts.
2. Authoritative runtime evidence and permanent audit evidence are never
   silently deleted, rewritten, or content-compacted.
3. Planning-cycle evidence remains authoritative Planning evidence even when a
   proposal is rejected, expires, or is never submitted for execution.
4. Numeric retention, partition, and capacity values remain schema-1
   operational defaults, not permanent invariants.
5. Missing, unmounted, or corrupt required cold evidence creates an
   operator-visible Recovery-Blocked State.
6. Storage exhaustion blocks affected side-effecting work before mutation and
   preserves existing committed evidence.

Implementation, migration, checkpoint code, RFC-0023 reconciliation, Planning
cadence changes, Allocation integration, Execution integration, and gameplay
remain separately gated.
