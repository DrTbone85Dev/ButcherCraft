# ADR-02A: Legacy Split-Snapshot Recovery And Coordinated Checkpoint Publication

Status: RATIFIED ARCHITECTURAL DIRECTION - IM-031C-R1 THROUGH IM-031C-R4 IMPLEMENTED; ADR-02A-P1 RATIFIED; IM-031C PRODUCT OWNER ACCEPTED

Decision identifier: AH-1-ADR-02A

Package: BCSE Checkpoint Recovery Amendment

Authority: Owner-ratified architectural direction. This document amends ADR-02
with the proof-complete legacy split-snapshot recovery and coordinated live
checkpoint rules defined below. It authorizes architecture and the ordered
implementation milestone gates only. It does not itself authorize Java,
runtime recovery publication, save mutation, schema migration, operator
commands, startup hooks, checkpoint cadence integration, gameplay, later
milestones, IM-032, or application of recovery to any world. IM-031C-R1,
IM-031C-R2, IM-031C-R2A, IM-031C-R3, IM-031C-R3A, IM-031C-R3B,
IM-031C-R3C, and IM-031C-R4 were separately authorized and are mechanically
implemented within their stated boundaries.

Implementation note: IM-031C-R1 implements only immutable read-only source
inspection, Recovery Identity and analysis digest, owner-scoped recovery
evidence models, deterministic eligibility and authority-block analysis,
legacy temp classification, future operator-authorization evidence, reporting,
and generated test fixtures. It creates no live Scheduler record, owner
mutation, checkpoint generation, startup hook, command, or recovery
publication. IM-031C-R2 adds exact operator authorization and mandatory
read-only reanalysis, immutable owner preparation, frozen publication intent,
Scheduler acknowledgement/discontinuity snapshots, Policy B owner snapshots,
typed mutation restrictions, immutable recovery generation publication,
durable Recovery Result evidence, idempotent retry, and explicit Java admin
tooling. R2A validates that path against a byte-identical disposable copy of
the actual failed-world evidence. R3 adds the exact 17-owner live participant
registry, safe-boundary immutable capture, canonical empty snapshots,
asynchronous publication, alternating dual-head commit, manual and periodic
triggers, bounded graceful-shutdown capture, and diagnostics. It does not
install startup recovery, mutate live owner files from a checkpoint, or operate
on the original failing world.

R3A subsequently implements Workstation-owned durable per-instance projection.
R3B activates deterministic required-projection closure, exact embedded
payloads, loaded-state agreement, unloaded projection capture without chunk
loading, fail-closed publication, historical incompleteness classification,
and a read-only R4 candidate verifier. Those two milestones do not themselves
activate startup selection or restoration; the separately authorized R4 does.

Ratified projection amendment:
[`ADR-02A-P1 Durable Workstation Projection And Checkpoint Completeness`](ADR-PROPOSED-DURABLE-WORKSTATION-PROJECTION-AND-CHECKPOINT-COMPLETENESS.md)
ratifies the Workstation-owned durable projection needed to close the proven
unloaded-chunk completeness gap. It does not alter the anti-inference or
singular-ownership decisions below and implements no runtime behavior by
ratification alone. Historical generations with required `chunk_unloaded`
entries remain insufficient evidence for complete Workstation restoration. R3C
preserves those generations and supplies the exact successor consumed by R4.

Canonical references:

- [`CONSTITUTION.md`](../../CONSTITUTION.md)
- [`Core Principles`](../../CORE_PRINCIPLES.md)
- [`Project Rules`](../../PROJECT_RULES.md)
- [`Technical Architecture`](../../TECHNICAL_ARCHITECTURE.md)
- [`Architecture Guide`](../BCSE_ARCHITECTURE_GUIDE.md)
- [`Architecture Validation Framework`](../ARCHITECTURE_VALIDATION_FRAMEWORK.md)
- [`Platform Canonicalization Addendum`](ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md)
- [`ADR-01 Platform Evidence Lifecycle`](ADR-PROPOSED-EVIDENCE-LIFECYCLE.md)
- [`ADR-02 Coordinated Checkpoint And Crash Recovery`](ADR-PROPOSED-CHECKPOINT-RECOVERY.md)
- [`ADR-04 Deterministic Planning Cadence`](ADR-PROPOSED-PLANNING-CADENCE.md)
- [`ADR-05 Scheduler Handler Effects And Scheduler Runtime Authority`](ADR-PROPOSED-SCHEDULER-EFFECTS-AUTHORITY.md)
- [`DG-002 Material Handling Custody And Recovery`](ADR-PROPOSED-MATERIAL-HANDLING-CUSTODY-AND-RECOVERY.md)
- [`DG-002A Workstation Endpoint Durability And Instance Identity`](ADR-PROPOSED-WORKSTATION-ENDPOINT-DURABILITY-AND-INSTANCE-IDENTITY.md)
- [`DG-003 Execution Handler Registry Evolution And Save Compatibility`](ADR-PROPOSED-EXECUTION-HANDLER-REGISTRY-EVOLUTION.md)
- [`DG-005 Persistent Machine Operating State And Continuous Processing`](ADR-PROPOSED-PERSISTENT-MACHINE-OPERATING-STATE-AND-CONTINUOUS-PROCESSING.md)
- [`Checkpoint Recovery Foundation`](../CHECKPOINT_RECOVERY_FOUNDATION.md)
- [`Simulation Scheduler`](../SIMULATION_SCHEDULER.md)
- [`Machine Run-State Foundation`](../MACHINE_RUN_STATE_FOUNDATION.md)

## 1. Decision In Plain Language

ADR-02 correctly permits recovery only from a committed coherent checkpoint
and correctly rejects independent legacy files whose Clock and Scheduler do
not agree. That prohibition prevents guessed mutations and duplicate effects.

One historical failure class is narrower than arbitrary owner-file merging.
An older Scheduler snapshot can omit coordination history for consequences
that immutable Execution and owner evidence already prove happened. Restoring
the consequences would be unsafe because they already happened. Recording
that proven history without executing it can be safe when every identity and
result is exact.

This proposal adds that narrow distinction:

```text
reconstruct missing historical coordination evidence
is not
reexecute the consequence
```

It also completes the already-ratified checkpoint direction for current live
owners so future hard crashes have a committed coherent generation to select.
It does not replace Checkpoint Recovery or weaken ADR-02's anti-inference
rules.

## 2. Amendment Boundary

This document amends ADR-02 only in these respects:

1. A legacy split snapshot without a committed checkpoint may enter a
   proof-bound recovery-analysis path.
2. Scheduler may publish immutable historical acknowledgements for effects
   already proven by their authoritative owners.
3. Scheduler may publish an explicit recovery discontinuity between its last
   normally finalized tick and the newer authoritative Clock boundary without
   executing or fabricating the intervening ticks.
4. An unresolved historical consequence may be retained as a typed authority
   block inside a coherent recovery generation.
5. Current durable owners, including current Execution, Machine Run,
   Workstation, Material Handling, Planning, and Production state, become
   required participants in coordinated live checkpoints when their owner
   adapters are implemented.
6. Startup may prefer complete, coherently reconcilable live state before the
   last committed checkpoint, but may never assemble that state by arbitrary
   cross-owner merging.

All other ADR-02 rules remain controlling. In particular, recovery still may
not infer owner mutations, rerun unknown effects, synthesize Scheduler ticks,
use file timestamps as authority, or become a subsystem-state owner.

## 3. Proven Legacy Failure Model

The preserved compatibility case has this owner evidence:

| Fact | Preserved value |
| --- | --- |
| Clock tick | `39872` |
| Scheduler last finalized tick | `39084` |
| Uncoordinated interval | `39085` through `39872`, inclusive |
| Missing Patty Former Scheduler history | 10 child operations |
| Proven terminal Execution operations | 9 `SUCCEEDED` |
| Unexecuted Execution operation | 1 `AUTHORIZED`, Scheduler invocation not started |
| Deferred non-repeatable Planning eligibility | tick `39601` |
| Machine Run Identity | `butchercraft:machine_run/v1/92d1b5a4f85f9e46907c4ba80bc4becfe0d5cd76600d4056156063d08bb32b3c` |
| Machine Run state | generation 1, `AUTHORIZED`, 9 terminal children, current child sequence 10 |
| Workstation Instance Identity | `butchercraft:workstation_instance/v1/243a13bf683621dc6d48a20ed39fdc81f96bc9fd776ba316feb236bf7b771996` |
| Workstation generation | 6 |
| Legacy Clock temp artifact | byte-identical to final Clock file |
| Unsupported schema | none observed |

The nine completed children are sequences 1 through 9. Their immutable
Execution results preserve exact operation, authorization, Scheduler
invocation, Scheduler effect, owner-result, result-content, Workstation
instance, Machine Run, child-sequence, and tick evidence. Child sequence 10
preserves an exact Execution operation whose `scheduler_invocation_started`
fact is false and whose owner result is absent.

This evidence proves that the nine effects must not run again. It does not by
itself prove every field of the original Scheduler Work definitions, every
Scheduler submission sequence interleaved with other Work, or the outcome of
the eligible non-repeatable Planning Work.

## 4. Canonical Terms

This proposal uses the Platform Canonicalization Addendum's identity, Recovery,
Replay, Unknown Outcome, Recovery-Blocked State, Operator Authority,
Publication, Quarantined Artifact, Owner Snapshot, and CheckpointGenerationId
definitions.

Additional terms are local to this amendment:

**Legacy Split Snapshot:** A read-only set of independently published owner
files that belong to one World Identity but do not represent one proven
cross-owner boundary.

**Consequence Evidence:** Immutable evidence published by the owner that proves
an effect's exact terminal outcome and identity bindings.

**Historical Coordination Acknowledgement:** Scheduler-owned immutable history
that acknowledges an already-proven consequence without claiming that
Scheduler is executing it during recovery.

**Scheduler Recovery Discontinuity:** Scheduler-owned evidence that the
Scheduler admission cursor moves from a last normally finalized tick to a
newer authoritative Clock boundary without per-tick execution of the enclosed
interval.

**Recovery Analysis:** A deterministic, read-only report over exact source
bytes, owner schemas, identities, revisions, and evidence. It grants no
publication authority.

**Recovery Authorization:** Explicit Operator Authority bound to one exact
Recovery Identity and one permitted disposition.

**Recovery Publication:** Checkpoint-Recovery-coordinated publication of owner-
validated recovery snapshots and immutable recovery evidence as one committed
checkpoint generation.

**Reconstruction:** Publication of missing coordination evidence whose
underlying consequence is already exactly and immutably proven.

**Reexecution:** Invocation of a handler, owner mutation, transaction,
workstation effect, external effect, or other consequence. Recovery does not
perform reexecution.

## 5. Singular Ownership

| Fact or action | Singular owner |
| --- | --- |
| Recovery analysis, Recovery Identity, operator-authorization validation, coordinated recovery publication, generation selection, and recovery diagnostics | Checkpoint Recovery |
| Clock tick and Clock snapshot | Simulation Clock |
| Scheduler Work, runtime lifecycle, historical acknowledgements, recovery discontinuity, admission cursor, and Scheduler snapshot | Scheduler |
| Execution operation, attempt, Machine Run, child binding, and Execution result evidence | Execution |
| Machine operating state, Workstation instance identity, inventory effect, endpoint journal, inventory projection, and owner result | Workstation |
| Planning eligibility, cycle publication, and Planning recovery disposition | Planning |
| Material custody and transfer lifecycle | Material Handling |
| Production definitions, plans, Runs, and Production results | Production |
| Evidence classification, retention, and archival | Evidence Lifecycle |
| Explicit approval of a recovery action | Operator Authority at the platform integration boundary |

Checkpoint Recovery coordinates owner publications. It does not create an
Execution result, change a Workstation slot, classify a Planning outcome,
move Material Handling custody, or issue Scheduler runtime authority.

Operator Authority selects among actions that the affected owners define and
validate. It does not rewrite owner facts or make unverified evidence true.

## 6. Legacy Recovery Eligibility

A legacy split snapshot may produce a recovery candidate only when all of the
following are true:

1. Every source is opened read-only and its exact bytes are content-digested.
2. Every required source belongs to the same exact World Identity root.
3. Every schema is supported by its owner without permissive fallback.
4. The Platform Determinism Manifest can identify the exact relevant codecs,
   canonicalization rules, handler contracts, effect policies, registries,
   configuration, and migration state.
5. Every source owner validates its own snapshot and references.
6. The Clock tick is not older than the Scheduler's last normally finalized
   tick.
7. Every historical acknowledgement binds exact immutable owner
   evidence; no field required for the acknowledgement is guessed.
8. Every active Workstation reference binds the exact Workstation Instance
   Identity and generation. A replacement instance is not equivalent.
9. Every active Machine Run, child, Execution operation, Material Handling
   transfer, Planning item, and Production reference is classified as proven,
   unresolved, or conflicting.
10. Every unresolved item is represented explicitly in the candidate and its
    authority block is validated.
11. No owner snapshot claims state later than the Clock boundary.
12. No required external consequence is known only from a missing, mutable, or
    diagnostic artifact.
13. The recovery analysis has one canonical digest and no conflicting prior
    result for the same Recovery Identity.
14. Publication targets a new checkpoint generation and never edits source
    files in place.

Failure of any condition produces a typed Recovery-Blocked State. It does not
fall back to best-effort import.

## 7. Immutable Evidence Required

Each reconstruction entry must bind:

- World Identity root identity, schema, and digest;
- source owner id, schema, revision or sequence, file digest, and evidence id;
- exact Execution Operation Identity;
- exact authorization identity and content digest;
- exact Machine Run Identity and child sequence when applicable;
- exact Workstation Instance Identity and generation when applicable;
- exact domain Effect Identity;
- exact Scheduler Invocation Identity and Scheduler Effect Identity when an
  invocation is claimed;
- exact owner-result identity and content digest;
- exact Execution terminal result identity and content digest;
- exact start/completion ticks preserved by the owner evidence;
- exact handler and Handler Contract Identity;
- exact Scheduler Work Identity when its canonical derivation is proven;
- reconstruction policy schema and content identity; and
- any relevant owner freshness and configuration identities.

An implementation may omit an optional historical field only when the
historical acknowledgement schema explicitly defines it as unavailable and
does not require it for identity, ordering, or integrity. It may not populate
the field with a reconstructed estimate.

## 8. Terminal Scheduler Reconstruction Model

### 8.1 Acknowledgement Instead Of Invocation

For a proven terminal Execution operation missing from the Scheduler snapshot,
Scheduler may publish one Historical Coordination Acknowledgement. Publication
does not call a handler, create an Execution operation, mutate Workstation
state, change Inventory, move Material Handling custody, dispatch Scheduler
Work, or replay an external effect.

The acknowledgement states only that:

1. the exact operation and invocation existed;
2. the exact owner consequence is already proven;
3. the exact terminal result is already authoritative; and
4. Scheduler history now acknowledges that result for cross-owner recovery.

### 8.2 Work Identity

The current Execution contract derives a Scheduler Work Identity from the
exact Execution Operation Identity by the versioned canonical `operation +
/work` rule. Recovery may use that identity only when the recovered Handler
Contract Identity and Platform Determinism Manifest prove that the same rule
governed the operation.

Recovery does not allocate a new Work Identity. If the Work Identity or rule
cannot be proven, reconstruction is prohibited.

### 8.3 Ordinary Work Records Are Not Fabricated

An ordinary terminal Scheduler Work record may be reconstructed only if every
required definition and runtime field, including submission ordering, payload,
handler, stage, retry policy, invocation, effect, owner result, status, and
ticks, is immutably proven.

When that full proof is unavailable, Scheduler publishes the narrower
Historical Coordination Acknowledgement instead. It does not invent a
submission sequence or pretend the acknowledgement is an ordinary dispatched
Work record.

### 8.4 Terminal Mapping

- Proven Execution `SUCCEEDED` plus exact owner-result evidence maps to a
  successful historical acknowledgement.
- Proven Execution `FAILED` maps to a failed historical acknowledgement only
  when immutable evidence proves the Scheduler invocation began and preserves
  the exact terminal failure. A pre-submission Execution failure creates no
  Scheduler acknowledgement.
- `AUTHORIZED` with no Scheduler invocation and no owner result is not
  terminal and is not reconstructed as Work.
- Missing, conflicting, or incomplete result evidence is not reconstructable.

The acknowledgement is immutable, replay-critical, retained under Evidence
Lifecycle policy, and correlated to the Recovery Identity and resulting
checkpoint generation.

## 9. Authorized-But-Unscheduled Child

The tenth preserved Patty Former child remains exactly what its Execution
owner proves:

```text
Execution operation: AUTHORIZED
Scheduler invocation started: false
Owner result: absent
Consequence: not claimed
```

Recovery must:

1. preserve the exact Machine Run Identity and generation;
2. preserve the exact child sequence and Execution Operation Identity;
3. add no Scheduler Work or historical acknowledgement for that child;
4. allocate no replacement Run, child, operation, Work, invocation, or effect
   identity;
5. ask Execution to publish the Run as `SUSPENDED_RESTART_REQUIRED`;
6. ask Workstation to publish the machine as `RESTART_REQUIRED`; and
7. commit both owner states in the recovery generation.

Explicit `RESUME` targets the same Run. The existing child may enter the normal
Scheduler submission and admission path only after current endpoint identity,
authorization, freshness, configuration, recipe, capacity, and authority
checks pass. Failure of those checks is explicit. Recovery does not silently
replace or cancel the child.

No new child is admitted during recovery publication or startup.

## 10. Non-Repeatable Planning Outcome Policy

Planning Work eligible inside the discontinuity is not replayed, marked
successful, or silently discarded unless exact Planning-owned outcome evidence
proves what happened.

When exact outcome evidence is absent:

1. Scheduler records the Work reference in the Recovery Discontinuity as
   unresolved. It does not fabricate an Invocation Identity or Effect Identity.
2. Planning preserves its last proven cadence and cycle evidence.
3. Planning enters `Recovery-Blocked State` with reason
   `LEGACY_SPLIT_PLANNING_OUTCOME_UNPROVEN`.
4. Checkpoint Recovery records `Operator Intervention Required` for the
   affected Planning authority.
5. The recovery generation contains the unresolved record and its exact source
   evidence; it does not claim a Planning result.
6. Planning and any authority whose next mutation requires the unresolved
   Planning outcome remain blocked.

A recovery generation may be coherent while carrying this explicit authority
block. Mutable work may resume only for authorities whose owner-validated
dependency closure does not depend on the unresolved outcome. If that closure
cannot be proven, normal mutable world startup remains blocked.

Schema 1 of this proposal authorizes no generic operator claim that the
Planning Work did or did not execute. A later owner-specific resolution may:

- attach exact owner evidence and reconstruct the proven result;
- prove no invocation/effect and re-admit the same Work under Planning and
  Scheduler rules; or
- publish an explicit owner-defined abandonment/compensation outcome under a
  separately ratified Planning recovery rule.

Until one of those actions is authorized and proven, the unresolved Planning
outcome remains visible and automatic Planning execution remains prohibited.

## 11. Clock And Scheduler Reconciliation

The authoritative Clock remains at tick `39872`. Recovery does not roll it
back merely to match Scheduler tick `39084`.

Scheduler does not execute ticks `39085` through `39872`, does not call the
normal pipeline for those ticks, and does not emit 788 synthetic tick reports.
Instead, Scheduler publishes one Recovery Discontinuity containing:

- the last normally finalized Scheduler tick;
- the authoritative Clock boundary;
- the inclusive unexecuted interval;
- the ordered historical acknowledgements proved inside the interval;
- unresolved Work references inside the interval;
- the exact source Scheduler snapshot identity and digest;
- the Recovery Identity; and
- the resulting checkpoint generation reference.

The Scheduler admission cursor becomes the authoritative Clock boundary only
through this recovery publication. The last normally finalized tick remains
historically visible and is not reinterpreted as `39872`. The next normal
Scheduler pipeline admission is tick `39873`.

For an ordinary checkpoint, ADR-02's equality remains unchanged:

```text
checkpoint tick == Clock tick == Scheduler normally finalized tick
```

For a legacy recovery generation only, the equivalent validation is:

```text
checkpoint tick == Clock tick == Scheduler recovery admission cursor
and
the Scheduler Recovery Discontinuity exactly covers the difference from the
last normally finalized tick
```

This is historical alignment, not tick execution or catch-up.

## 12. Recovery Analysis And Typed States

Read-only detection may return:

- `NOT_SPLIT`: no mismatch requiring this proposal;
- `RECOVERABLE_WITH_COMPLETE_PROOF`: every required repair is exactly proven;
- `RECOVERABLE_WITH_AUTHORITY_BLOCKS`: all published facts are coherent but
  one or more unresolved effects remain explicitly blocked;
- `RECOVERY_REQUIRED`: more evidence or an owner disposition is required;
- `CONFLICT`: identities or authoritative owner facts disagree;
- `UNSUPPORTED_SCHEMA`: an owner cannot interpret its source safely; or
- `NOT_ELIGIBLE`: the case is outside this amendment's compatibility boundary.

These names are architectural contract names, not implemented constants.

Analysis is deterministic from source bytes and configuration. It performs no
cleanup, migration, recovery publication, checkpoint publication, owner
mutation, or live startup.

## 13. Operator Authorization

Publication initially always requires explicit Operator Authority.

The required flow is:

```text
startup detects split snapshot
-> mutable startup remains blocked
-> owners validate read-only source snapshots
-> Checkpoint Recovery publishes a Recovery Analysis only
-> operator selects one permitted disposition for that exact analysis
-> owners prepare recovery snapshots
-> Checkpoint Recovery publishes one committed recovery generation
-> startup loads only that generation
-> DG-005 restart Policy B remains in force
```

The authorization binds:

- exact Recovery Identity;
- exact analysis digest;
- operator principal identity;
- selected disposition;
- acknowledged unresolved authority blocks;
- target world identity;
- authorization schema; and
- deterministic authorization content digest.

Wall-clock time may be recorded as non-authoritative audit metadata. It does
not participate in recovery ordering or recovered state.

Authorization is rejected if source bytes, owner revisions, evidence,
configuration, analysis, or world identity changed after analysis.

The original source world is never modified by analysis or trial recovery.
Recovery tooling writes to a distinct target copy and new checkpoint
generation. Applying a validated result to the original world requires a later
explicit Product Owner decision.

## 14. Recovery Identity And Immutable Result

The Recovery Identity is a content identity with canonical form:

```text
butchercraft:legacy_split_recovery/v1/<sha256>
```

Its canonical digest input contains:

1. recovery schema and policy identity;
2. World Identity root identity, schema, and digest;
3. Platform Determinism Manifest identity and digest;
4. ordered source owner ids, schemas, revisions/sequences, and file digests;
5. Clock tick and Scheduler last normally finalized tick;
6. ordered Historical Coordination Acknowledgements;
7. ordered unresolved and conflicting references;
8. exact active Machine Run, child, Workstation instance, Material Handling,
   Planning, and Production references;
9. canonical legacy artifact classifications; and
10. final proposed authority-block set.

The immutable Recovery Result binds:

- Recovery Identity and authorization identity;
- exact analysis digest;
- every source snapshot identity and digest;
- every acknowledgement published;
- every unresolved item preserved;
- every owner snapshot identity and digest;
- Scheduler Recovery Discontinuity identity;
- final coherent boundary;
- resulting CheckpointGenerationId and manifest digest;
- publication outcome; and
- typed failures or reduced filesystem guarantees.

Recovery evidence is permanent audit history and replay-critical for every
generation that depends on it.

## 15. Recovery Publication And Idempotency

Recovery publication uses ADR-02's owner preparation, validation, immutable
generation, and dual-head commit protocol.

1. Source files remain read-only.
2. Each owner validates the candidate change to its own state and returns an
   immutable candidate snapshot.
3. Checkpoint Recovery validates the complete candidate and authority blocks.
4. All candidate bytes and manifests are written under a new staging
   generation.
5. The complete generation is verified and moved to its immutable final path.
6. The inactive checkpoint-head slot is published and verified.
7. Only the valid head makes the recovery generation authoritative.
8. Live files may be projected from the selected generation only after commit
   and only through their owners.

Idempotency rules:

- the same Recovery Identity, authorization content, and resulting generation
  returns the existing immutable Recovery Result;
- the same Recovery Identity cannot create duplicate Scheduler
  acknowledgements, owner results, Workstation effects, Material Handling
  transfers, Machine Run children, or checkpoint generations;
- the same Recovery Identity with different reconstruction content or
  disposition is an identity conflict;
- a crash before head publication leaves the prior committed generation, or no
  generation, authoritative;
- a crash after head publication reloads the same committed result; and
- retry never invokes a recovered consequence.

## 16. Legacy Temp Artifact Classification

Pre-AtomicFilePublication fixed `<target>.tmp` files are non-authoritative
artifacts unless an owner-specific accepted protocol proves otherwise.

| Temp relationship | Classification | Permitted action |
| --- | --- | --- |
| Byte-identical to authoritative final | Stale non-authoritative debris | Ignore for recovery; retain or clean later under explicit bounded artifact policy |
| Different from authoritative final, both owner-valid | Quarantined Artifact and recovery-analysis input only | Compare schema, identity, revision, sequence, and digests through the owner; never select by timestamp |
| Different and temp invalid | Quarantined Artifact | Report exact validation failure; do not replace final |
| Different and final invalid | Recovery-Blocked State | Owner-specific proof or migration required; temp does not become authoritative merely because final failed |
| Either has unsupported schema or identity conflict | Unsupported/conflict | Block affected recovery |

A newer revision in a temp file is not commit proof by itself. Filesystem
creation, modification, or access time never determines authority. Cleanup is
separate from recovery selection, and blanket deletion is prohibited.

The preserved `simulation_state.json.tmp` is byte-identical to its final file,
so this proposal classifies it as stale non-authoritative debris and not as the
cause of the split snapshot.

## 17. Coordinated Live Checkpoint Model

This proposal retains ADR-02's single Checkpoint Recovery coordinator,
directory-per-generation publication, and dual-head selection. It does not
create a second checkpoint authority.

### 17.1 Safe Boundary

Capture occurs after one normal Scheduler tick is finalized and all
consequential owner publications admitted for that tick have reached an
owner-defined stable state, before mutation for the next tick begins.

The coordinator pauses new authoritative admissions only long enough to:

1. identify the exact participant set;
2. allow in-flight owner publications to complete or remain unpublished;
3. capture immutable owner snapshots; and
4. validate their represented boundary and references.

Encoding and durable file writes may continue after snapshot capture if they
use only the frozen owner snapshots.

### 17.2 Required Participants

The Architecture Manifest defines the complete implemented participant set.
At minimum, once corresponding adapters exist, one coordinated generation
contains:

- World Identity external root;
- Platform Determinism Manifest;
- Simulation Clock;
- Scheduler Work, runtime, acknowledgements, and discontinuities;
- Execution operations, attempts, results, and Machine Runs;
- Workstation instance registry, endpoint journal, machine operating state,
  exact relevant inventory/effect projection, and owner results;
- Material Handling runtime and exact custody state, including a canonical
  empty/idle snapshot when no transfer is active;
- Planning cadence, artifacts, pending triggers, and authority blocks;
- Production definitions, plans, Runs, and result references;
- Transactions and Inventory freshness/result evidence;
- Business Runtime, Workforce, Goods, Actors, Orders, Contracts, Player
  Identity, and every other implemented durable owner already required by
  ADR-02; and
- checkpoint and recovery evidence indexes.

An inactive owner participates with a canonical owner-valid empty snapshot. It
is not omitted merely because no current work is visible.

### 17.3 Workstation And Vanilla Persistence Boundary

Checkpoint Recovery does not parse block-entity NBT or become the Workstation
owner. Workstation freezes and validates its own exact checkpoint snapshot.
That snapshot must cover every Workstation state needed to prove or restore a
consequential effect referenced by Execution, Material Handling, or Machine
Runs.

Vanilla chunk files remain outside the checkpoint commit protocol. On recovery,
Workstation reconciles its block-entity projection from its own committed
snapshot, instance registry, endpoint journal, and owner-result evidence.
This is an owner-provided recovery bridge, not shared ownership or arbitrary
chunk-file merging.

If Workstation cannot prove that the loaded block and generation are the exact
referenced instance, the affected state is Recovery-Blocked. A replacement
block never inherits the old instance identity or effect.

### 17.4 Cadence And Retention

Checkpoint cadence, generation count, compression, storage layout details,
cleanup batch size, and checksum implementation remain operational policy or
configuration under ADR-02 and Evidence Lifecycle. This amendment ratifies no
new numeric cadence or retention invariant.

## 18. Checkpoint Commit Boundary

A generation is committed only when a valid dual-head record references its
complete immutable generation manifest.

The protocol is:

1. allocate the next CheckpointGenerationId without consuming it on failed
   preparation;
2. capture all required owner snapshots at one validated boundary;
3. validate owner schemas, digests, prerequisite order, World Identity,
   configuration identity, and every cross-owner reference;
4. write and force owner payloads and owner manifests to a unique staging
   generation;
5. read back and verify every payload and manifest;
6. write, force, read back, and verify the generation manifest;
7. publish the immutable final generation directory;
8. write and force the inactive checkpoint-head slot;
9. read back the head and complete generation; and
10. complete head publication.

An incomplete or unheaded generation is never committed. A crash during
capture, staging, validation, final-directory publication, or inactive-head
publication leaves the previous valid head authoritative. Cleanup cannot
delete any generation referenced by either retained valid head or recovery
evidence.

## 19. Startup Recovery Preference

After coordinated checkpoints are implemented, startup evaluates in this
order:

1. **Coherent live state.** Use current live owner state only when every owner
   proves one World Identity, one determinism configuration, a common committed
   checkpoint lineage, complete post-checkpoint owner evidence, exact
   cross-owner references, and no unresolved consequence outside an explicit
   authority block.
2. **Last committed coherent checkpoint.** If live state is not coherently
   reconcilable, select the highest complete valid checkpoint under ADR-02.
3. **Explicit recovery analysis.** If neither exists, remain mutation-blocked
   and offer read-only split-snapshot analysis.

Live reconciliation does not mean selecting each owner's newest file. All
owners must trace to the same checkpoint and complete evidence boundary. If
that proof fails, startup discards no files and performs no merge.

The selected checkpoint may be older than visible live files. Those newer
files remain non-authoritative recovery evidence unless a separately accepted
delta-replay contract proves them.

## 20. External Consequence Rules

| Effect class | Evidence required for historical acknowledgement | Recovery execution |
| --- | --- | --- |
| Read-only | Exact invocation evidence if history must be reconstructed | Not required and not performed |
| Replay-safe internal evidence | Exact immutable owner result and identities | Not performed; consume evidence only |
| Idempotent effect | Exact Effect Identity and authoritative owner result proving the existing effect | Never reinvoke merely to repair history |
| Transaction-backed effect | Exact APPLIED Transaction result, proposal/freshness/plan binding, and resulting Inventory freshness evidence | Never reapply the Transaction |
| Workstation effect | Exact Workstation Instance Identity, effect identity, immutable owner result, revisions, and projection reconciliation | Never repeat slot mutation |
| Material Handling effect | Exact transfer, custody, endpoint journal, and owner-result evidence | Never withdraw, deposit, or return during historical reconstruction |
| Non-repeatable internal effect | Exact terminal owner evidence | Never replay; absent proof remains blocked |
| Non-repeatable external effect | Exact authoritative external acknowledgement under an accepted owner contract | Never replay during recovery |
| Unknown Outcome | Owner-specific deterministic reconciliation or explicit operator-authorized owner disposition | Automatic execution prohibited |

Scheduler absence alone never proves that an effect did not happen. Owner-file
absence alone never proves that a non-repeatable effect did not happen.

## 21. Required Recovery Matrix

| Case | Authoritative evidence | Reconstruction | Replay/execution | Operator action | Final recovery state |
| --- | --- | --- | --- | --- | --- |
| A. Clock newer, Scheduler older, all missing operations proven terminal | Clock; source Scheduler; exact Execution and owner results for every missing item | Historical acknowledgements and one Recovery Discontinuity permitted | Forbidden | Authorize exact Recovery Identity | Coherent recovery generation; Policy B where active Runs exist |
| B. Clock newer, Scheduler older, one or more missing operations unproven | Exact source files plus incomplete/conflicting owner proof | Proven entries only; unproven entries remain explicit blocks | Forbidden for unproven items | Authorize blocked generation or abort; no success classification | `RECOVERABLE_WITH_AUTHORITY_BLOCKS` or `RECOVERY_REQUIRED` |
| C. Missing Scheduler operation proven `SUCCEEDED` | Exact Execution success, invocation/effect, owner result, identity bindings | Successful historical acknowledgement permitted | Workstation/handler/Transaction replay forbidden | Authorize recovery | Proven terminal acknowledgement |
| D. Missing Scheduler operation proven `FAILED` | Exact Execution failure and proof Scheduler invocation began | Failed historical acknowledgement permitted; pre-submission failure creates none | Retry/replay forbidden during recovery | Authorize recovery | Proven failed acknowledgement |
| E. Execution `AUTHORIZED`, never scheduled | Execution operation; `scheduler_invocation_started=false`; no owner result | No Scheduler Work or acknowledgement | Automatic scheduling forbidden | Preserve exact child; later explicit `RESUME` or `STOP` | Same operation under Policy B `RESTART_REQUIRED` |
| F. Scheduler Work exists, Execution result absent | Scheduler definition/runtime and any invocation/effect evidence | No terminal reconstruction without owner result | Automatic retry forbidden when consequence may have begun | Owner reconciliation or operator review | Pending only if provably unstarted and still valid; otherwise Unknown Outcome/Recovery-Blocked |
| G. Non-repeatable Planning Work eligible in gap, outcome unknown | Deferred Work, eligibility tick, Planning snapshots, no exact terminal evidence | No Planning result reconstructed | Replay and silent discard forbidden | Preserve authority block; later owner-specific resolution | Planning `Recovery-Blocked State`; dependent mutations blocked |
| H. Machine Run active across split | Exact Run, child list, Workstation instance, operating state, child evidence | Proven children acknowledged; current exact child preserved | No child admitted during recovery | Authorize recovery, then explicit `RESUME` or `STOP` | Same Run `SUSPENDED_RESTART_REQUIRED`; machine `RESTART_REQUIRED` |
| I. Workstation owner effect committed, Scheduler history missing | Exact instance/generation, owner result, effect/revisions, reconciled projection | Historical acknowledgement permitted | Inventory effect replay forbidden | Authorize recovery | Owner state unchanged; Scheduler history acknowledges result |
| J. Material Handling active across split | Exact transfer, custody, endpoint identities/journals, owner results | Preserve proven custody and lifecycle only | No automatic withdrawal/deposit/return | Owner reconciliation; operator only if unresolved | Existing lifecycle, `RECOVERY_REQUIRED`, or Unknown Outcome according to DG-002 |
| K. Replacement Workstation instance exists | Instance registry and generation conflict | Prohibited across replacement | Old-instance effect replay forbidden | Owner repair or explicit recovery review | Recovery-Blocked for affected references |
| L. Legacy temp byte-identical to final | Exact byte digests | None needed | None | No recovery action; optional later cleanup | Stale non-authoritative debris |
| M. Legacy temp differs from final | Exact bytes plus owner schema/identity/revision validation | None unless an accepted owner protocol independently proves authority | Replacement by timestamp forbidden | Owner-specific review | Quarantined Artifact or Recovery-Blocked |
| N. Committed coherent checkpoint exists | Valid head, immutable generation, owner manifests, predecessor chain | Legacy reconstruction normally unnecessary | No post-checkpoint replay without accepted delta contract | Automatic highest-valid selection; operator for older rollback | Selected committed generation |
| O. No coherent checkpoint exists | Legacy source set and analysis | Only proof-complete reconstruction under this amendment | Consequence replay forbidden | Explicit authorization required | New recovery generation or Recovery-Blocked |
| P. Recovery crashes before publication | Staging artifacts; no valid new head | Retry same Recovery Identity; no duplicate acknowledgement | Consequence replay forbidden | Retry or inspect | Prior head remains authoritative; staging quarantined |
| Q. Recovery commits, startup crashes before gameplay | Valid recovery head, generation, Recovery Result | No additional reconstruction | Consequence replay forbidden | Restart normally | Same committed recovery generation selected idempotently |

## 22. Proposed Recovery Invariants

These invariants amend and specialize ADR-02:

1. Recovery may add missing historical coordination evidence only when the
   underlying consequence is already immutably proven.
2. Recovery never recreates the consequence itself.
3. Recovery never invents Scheduler ticks.
4. Recovery never silently rolls authoritative Clock backward.
5. Recovery never reallocates historical identities.
6. Recovery never changes Workstation Instance Identity.
7. Recovery never guesses non-repeatable Planning outcomes.
8. Recovery is idempotent.
9. Recovery itself is durably evidenced.
10. Mutable gameplay resumes only from a coherent cross-owner boundary.
11. Scheduler owns historical acknowledgements and recovery discontinuities;
    Checkpoint Recovery only coordinates their publication.
12. Unresolved consequences remain explicit authority blocks and cannot be
    hidden by checkpoint commit.
13. A committed recovery generation is visible atomically through the existing
    dual-head protocol.
14. Every implemented durable owner participates in a live checkpoint with an
    owner-valid snapshot, including a canonical empty snapshot when inactive.

## 23. Compatibility Boundary

This amendment supports only legacy split snapshots where:

- one World Identity and compatible determinism configuration are proven;
- all source schemas are supported;
- Clock is newer than or equal to Scheduler;
- every reconstructed item has exact immutable owner evidence;
- every unknown item can remain explicitly authority-blocked;
- every referenced Workstation instance and generation is exact;
- owner snapshots can prepare one cross-owner recovery generation without
  changing historical identities; and
- no required consequence depends solely on timestamps, diagnostics, mutable
  memory, or an uncommitted artifact.

It does not authorize:

- generic save repair;
- arbitrary owner-file merging;
- newest-file-wins selection;
- timestamp-based authority;
- unsupported-schema import;
- World Identity replacement;
- replacement Workstation identity inheritance;
- Transaction, Workstation, Material Handling, Planning, Production, or
  external-effect replay;
- Clock rollback solely for Scheduler alignment;
- skipped-tick catch-up;
- retrospective creation of unproven Work definitions or submission ordering;
- silent Planning abandonment;
- automatic recovery publication on ordinary startup;
- modification of the original preserved failing world;
- IM-032; or
- any runtime implementation without separate milestone authorization.

If exact proof is incomplete, the compatibility result is Recovery-Blocked,
not best-effort recovery.

## 24. Rejected Alternatives

- **Roll Clock back to Scheduler.** Rejected because it discards newer
  authoritative Clock evidence merely to satisfy an older file.
- **Run 788 Scheduler ticks.** Rejected because it fabricates progression and
  could repeat non-repeatable consequences.
- **Reinvoke the nine Patty Former operations.** Rejected because immutable
  owner results prove the effects already occurred.
- **Create ordinary Scheduler Work with guessed submission sequences.**
  Rejected because missing coordination fields are not consequence proof.
- **Treat the tenth child as completed.** Rejected because no Scheduler
  invocation or owner result exists.
- **Allocate a replacement tenth child.** Rejected because the exact existing
  authorized child remains authoritative.
- **Rerun or mark Planning successful.** Rejected because the exact outcome is
  unproven and the effect is non-repeatable.
- **Select the newest owner file or temp artifact.** Rejected because
  filesystem time and visibility are not authority.
- **Let Checkpoint Recovery rewrite owner state directly.** Rejected because it
  creates a second authority.
- **Commit partial owner recovery.** Rejected because it preserves the split
  rather than repairing it.
- **Make every owner share one mutation service.** Rejected because coordinated
  checkpoint publication does not transfer subsystem ownership.
- **Automatically repair on startup.** Rejected for the initial recovery
  implementation because publication requires explicit operator intent.

## 25. Ratified Implementation Milestones

Ratification does not authorize implementation. The accepted sequence uses
reviewable IM-031C recovery submilestones without renumbering completed
history:

1. **IM-031C-R1 - Split-Snapshot Recovery Analysis Foundation.** Add read-only
   owner evidence adapters, exact Recovery Identity, eligibility analysis,
   historical-acknowledgement candidates, discontinuity candidates, temp
   classification, and diagnostics. No publication or source mutation.
2. **IM-031C-R2 - Operator-Authorized Recovery Publication.** Add owner prepare
   contracts, Scheduler acknowledgements/discontinuity, Planning authority
   blocks, Policy B owner transitions, immutable Recovery Result, and
   all-or-nothing checkpoint publication. Validate only synthetic and copied
   fixtures first.
3. **IM-031C-R2A - Copied Failing-World Recovery Validation.** Apply the exact
   authorized flow only to a preserved copy, prove idempotency, prove nine
   effects are not repeated, preserve the tenth child, and validate explicit
   Planning blocking. This submilestone changes no original world.
4. **IM-031C-R3 - Coordinated Live Checkpoint Owner Adoption.** Add all required
   current owner snapshots, Architecture Manifest participation, safe-boundary
   capture, configured cadence integration, graceful-shutdown capture, and
   Workstation projection reconciliation.
5. **IM-031C-R4 - Startup Selection And Hard-Crash Validation.** Implement live
   coherence evaluation, checkpoint fallback, explicit split analysis, crash
   injection at every commit phase, and repeated Windows hard-crash tests.
6. **IM-031C final acceptance.** Completed after retesting the existing-world
   Patty Former Run, restart Policy B, copied-world recovery, and coordinated
   checkpoint startup.
7. **IM-032.** Its IM-031C and recovery-submilestone sequencing gate is
   satisfied; implementation remains separate and has not started.

Applying recovery to the original failing world is a separate Product Owner
operation after architecture ratification, implementation validation, fixture
validation, copied-world validation, backup verification, and an exact dry-run
digest comparison.

## 26. Required Implementation Validation

Later implementation milestones require, at minimum:

- deterministic Recovery Identity and analysis ordering;
- exact World Identity and Platform Determinism Manifest matching;
- all eligibility rejection paths;
- full evidence binding for successful and failed acknowledgements;
- rejection when ordinary Work fields would need invention;
- no handler, Workstation, Transaction, Material Handling, Planning, or
  external-effect invocation during recovery;
- exact preservation of an authorized-but-unscheduled child;
- DG-005 Policy B transitions by the correct owners;
- Planning unknown and dependency-block behavior;
- no per-tick execution across a Scheduler Recovery Discontinuity;
- exact next normal Scheduler admission after the recovery cursor;
- replacement Workstation rejection;
- Material Handling custody reconciliation;
- identical and differing legacy temp classification;
- duplicate analysis and publication idempotency;
- crash injection before and after every generation/head phase;
- startup preference and no arbitrary live-file merge;
- complete required-owner participation;
- owner publication rollback/failure behavior;
- copied failing-world recovery without source mutation;
- repeated hard-crash tests on Windows; and
- Architecture Manifest mechanical truth before any implemented status is
  claimed.

## 27. Ratification Notes

The Product Owner / Systems Architect ratified this amendment with the
following controlling direction:

1. ADR-02A is a ratified amendment to ADR-02. ADR-02's anti-inference rules
   remain controlling except for the exact proof-complete mechanisms defined
   here.
2. Clock tick `39872` remains authoritative. Recovery does not roll it back,
   execute 788 Scheduler ticks, or synthesize simulation history.
3. Scheduler-owned Historical Coordination Acknowledgements are approved for
   the nine exactly proven Patty Former consequences. They record history and
   never recreate the consequence.
4. Ordinary Scheduler Work may be reconstructed only when every consequential
   field, identity, ordering value, payload, and lifecycle fact is proven.
5. The nonexecuting Scheduler Recovery Discontinuity for ticks `39085` through
   `39872` is approved. Normal Scheduler admission may resume at `39873` only
   after a coherent recovery generation commits.
6. The exact authorized-but-unscheduled tenth child and exact Machine Run are
   preserved. DG-005 Policy B applies, no work is admitted automatically, and
   later explicit `RESUME` uses normal current validation.
7. The unknown non-repeatable Planning outcome remains unresolved under a
   typed Planning authority block. Scoped continuation requires proven
   dependency independence; otherwise whole-world mutation remains blocked.
8. Recovery consumes immutable result evidence only. Unknown or non-repeatable
   external consequences are never replayed merely to align histories.
9. Read-only analysis may be automatic, but recovery publication requires
   explicit Operator Authority bound to the exact analysis and Recovery
   Identity.
10. Recovery Identity is content-derived and changes when any authority-
    relevant source, acknowledgement, unresolved item, block, world identity,
    participant snapshot, or determinism/configuration identity changes.
11. Recovery publication is idempotent and observes an existing immutable
    result instead of duplicating acknowledgements, Work, operations, owner
    results, effects, children, transfers, or checkpoint generations.
12. Recovery publication uses owner preparation and ADR-02's immutable
    generation and dual-head commit boundary. Piecemeal live-owner mutation is
    prohibited.
13. Byte-identical legacy temp artifacts are stale non-authoritative debris.
    Differing temp artifacts receive owner-specific analysis and never gain
    authority from timestamps, names, filesystem order, or revision alone.
14. Every implemented durable owner required for coherent recovery
    participates in live checkpoints, including canonical empty snapshots.
    Each owner retains snapshot-content and mutation authority.
15. A checkpoint is committed only after complete owner durability,
    identity/digest/revision verification, complete participant validation,
    immutable generation publication, and valid dual-head publication.
16. Startup evaluates coherent live state, then the latest valid committed
    checkpoint, then explicit split-snapshot analysis. Newest-file selection
    and arbitrary owner merging are prohibited.
17. DG-002A Workstation Instance Identity and DG-002 Material Handling custody
    evidence remain controlling. Recovery cannot transfer history or custody
    across a replacement instance or move product to balance totals.
18. The complete A-Q recovery matrix is ratified and controlling for its listed
    failure classes.
19. The original failing world remains read-only through deterministic,
    generated, and copied-world validation. Applying recovery to it requires a
    later explicit Product Owner authorization.
20. The `IM-031C-R1`, `IM-031C-R2`, `IM-031C-R2A`, `IM-031C-R3`, and
    `IM-031C-R4` sequence is ratified and implemented. Final IM-031C Product
    Owner acceptance is complete, satisfying the IM-032 sequencing gate.
21. ADR-02A is not a general repair escape hatch. It preserves no arbitrary
    owner-file merging, no inferred mutation, no unknown-effect replay, no
    synthetic Scheduler ticks, no latest-file-wins rule, no Clock rollback for
    alignment, no fabricated identities, and no replay of proven consequences.

## 28. Ratification And Implementation Gate

Owner ratification authorizes the architecture, ownership, identity,
failure-state, publication, compatibility, and milestone direction described
here. It does not itself implement or enable recovery.

IM-031C-R1 through IM-031C-R4 are separately authorized and mechanically
implement read-only analysis, explicit operator-authorized recovery
publication, copied failing-world validation, coordinated live checkpoint
publication, historical successor validation, startup selection, and owner-
native restoration. The following remain unauthorized until separate milestone
approval and mechanical validation:

- recovery-selection operator UI;
- source-world mutation;
- application to the original failing world;
- gameplay behavior;
- IM-032; and
- any later recovery behavior not mechanically implemented by R4.
