# DG-006 Architecture Proposal Completion Report

Current status annotation: DG-006 is now RATIFIED ARCHITECTURAL DIRECTION -
IMPLEMENTATION GATED. See the [ratification completion report](#ratification-completion-report)
and the ADR's [Ratification Notes](adr/ADR-PROPOSED-MACHINE-CONDITION-WEAR-DAMAGE-MAINTENANCE-AND-BREAKDOWN.md#37-ratification-notes).
The original proposal report below, through its Recommended Next Step section,
is historical evidence. Its pending choices and validation counts describe the
proposal stage, not current approval status. The ratification report appended
after it records the subsequent Product Owner decision without rewriting that
earlier evidence.

Status: PROPOSAL PREPARED - OWNER RATIFICATION REQUIRED; NO IMPLEMENTATION

Review date: 2026-09-26.

## Summary

Created the [proposed DG-006 ADR](adr/ADR-PROPOSED-MACHINE-CONDITION-WEAR-DAMAGE-MAINTENANCE-AND-BREAKDOWN.md)
and minimal discoverability/status references. It recommends Workstation-owned
aggregate mechanical condition with typed exposure/service state, exact joint
processing/wear evidence, and recoverable per-instance durability. Every
recommendation remains pending owner ratification; this report grants nothing.

## Preflight

| Check | Observed result |
| --- | --- |
| Branch | `feature/milestone-2d` |
| HEAD | `96499ac46f4ef1b8052b52b7f8def53d50468132` |
| Upstream | `origin/feature/milestone-2d` |
| Ahead / behind | `0 / 0` |
| Remote | Read-only remote query confirmed the same full HEAD |
| Initial working tree | Clean; no `git status --short` entries |
| Version | `gradle.properties`: `mod_version=0.10.7-alpha.1` |
| IM-032 | Complete / Product Owner accepted, including A and B |
| Previous DG-006 status | Architecture/design may begin; no proposal or implementation |

All seven supplied attachments were reconciled. The last four contain the same
continuation text; their only detected difference was line-ending encoding.

## Existing Architecture Findings

Execution owns Machine Runs and child authority. Workstation owns inventory,
operating state and exact local effects. Scheduler dispatches bounded work;
Clock supplies authoritative time. Workforce owns finite employee assignments
and role-aware access; Material Handling owns custody. Durable Workstation
projections and R4 owner-native restoration already preserve unloaded state.

Repository code does not define mechanical motion while either machine is
blocked. Operating-duration totals do not prove loaded physical activity.
The prototype engine equipment factor is fixed at IDEAL and can affect
evaluation; connecting it to wear would accidentally broaden scope. Current
machine drops do not preserve a condition-bearing item. Exact source links are
in ADR Section 3, not inferred from prior conversation.

## Proposed DG-006 Identifier / ADR

Identifier: `DG-006`, already reserved by DG-005 and MILESTONES.

Path: `docs/adr/ADR-PROPOSED-MACHINE-CONDITION-WEAR-DAMAGE-MAINTENANCE-AND-BREAKDOWN.md`.

Status: `PROPOSED - OWNER RATIFICATION REQUIRED`.

## Recommended Condition Authority

Workstation. It already owns the local product/operating/durable boundary.
Execution, Scheduler, Workforce and Checkpoint remain observers/coordinators,
not competing condition owners. No public API or new global condition owner.

## Terminology

Condition is current mechanical capability; wear is expected cumulative loss;
damage is abnormal loss; exposure is a proven stress interval/event; service
is durable servicing state; fault is a typed impediment; breakdown requires
repair before work; maintenance is preventative service; repair restores
capability or clears a proven fault. None means RECOVERY_REQUIRED.

## Condition Model Alternatives

A, aggregate only, is simple but cannot explain causes/service. B, full
components, is expressive but overbuilt for this alpha. Recommend C, aggregate
condition plus typed exposure/service, with future explicit component evolution.

## Recommended Authoritative Condition Model

Exact instance binding, bounded integral mechanical loss, service facts, one
controlling fault, active exposure/cumulative counters, exact arithmetic
remainder, condition revision/digest, frozen policy identity and retained
effect references. Bands/percentage are derived, not another owner record.

## Condition Revision / Effect Identity

Use a separate monotonic condition revision, not inventory/endpoint/Run
generation. Bind each effect to its exact cause, instance, pre-state, policy
and immutable result. Same identity/content observes; conflicting content
rejects. Old receipt retention is necessary beyond a last-effect pointer.

## Ordinary Processing Wear

Recommend product and wear in one Workstation owner result and joint durable
projection. No successful product followed by a fallible detached wear callback.
Rejected/no-effect work earns no ordinary wear. Duplicate observation never
duplicates product or wear.

## Time-Based Exposure

Explicit eligible intervals, cumulative counters and exact remainders settle
at deterministic boundaries. Threshold ticks do not depend on polling frequency.
Grace progress does not reset when a polling batch, STOP or reload changes.

## Persistence Frequency

No condition-triggered per-tick writes. Couple wear to existing child
completion; settle active exposure at bounded cadence, state/availability
transitions, thresholds and checkpoint preparation. No global condition file.

## Simulation Clock Interaction

Clock supplies ticks; Scheduler supplies bounded dispatch. State occupancy is
not mechanical proof. Recovery discontinuities never become powered exposure.

## Chunk Unload

Settle proven loaded time on orderly unload, then suspend without force-loading.
Keep exact counters/anchors. Durable condition remains inspectable unloaded.

## Restart / Policy B

Preserve exact Run identity, suspend exposure, require explicit RESUME/STOP.
No server-offline wear. Proposed crash accounting keeps the last durably
accounted cutoff and reports any unproven tail; owner approval is explicit.

## Grinder Dry-Running Model

Architecture: exact eligible exposure and idempotent consequences. Proposed
gameplay: brief empty operation has grace; prolonged empty operation adds
accelerated wear; severe continued exposure may fault after the repair gate.
Balance remains TBD. No engineering damage-time claim is made.

## Grinder OUTPUT_BLOCKED Recommendation

Recommend powered but mechanism idle, no V1 blocked mechanical loss. Turning
or stalled alternatives need their own explicit policy/proof. Owner must choose;
the repository establishes powered waiting, not physical motion.

## Patty Former Condition Model

Independent successful-cycle wear and service warnings. Recommend idle mechanism
while empty; do not copy Grinder cutting-interface exposure. Jam/overload
capability remains reserved until an authoritative trigger is approved.

## Patty Former OUTPUT_BLOCKED Recommendation

Recommend powered but mechanism idle with no V1 blocked loss. Empty cycling
or load stall are separate owner choices, not assumed commercial-machine facts.

## Wear vs Damage

Distinct typed causes may affect the same aggregate measure. Expected productive
wear is not an abnormal damage event. Historical cause evidence remains exact.

## Fault / Breakdown

One controlling typed fault with exact cause and clearing evidence. Breakdown
denies START/RESUME and later child admission, not legitimate STOP. Neither
restart nor menu interaction repairs it; authority uncertainty remains separate.

## Faulted Run Recommendation

Workstation first proves fault/inhibits work; Execution terminalizes the exact
Run as FAILED at a safe boundary. Repair requires a new explicit START. An
unknown child is recovery-blocked, never treated as clean mechanical failure.

## Maintenance / Repair Boundary

Normally OFF and terminal/no Run, no in-flight child or unresolved effect.
Recommend a narrow reconciled RESTART_REQUIRED exception without a child;
service does not RESUME. Resource consumption and restoration must share an
exact owner result. Minimal player repair precedes breakdown activation.

## Lubrication Boundary

Reserve typed machine-specific service state; no universal oil simulation.
Product-mediated cutting-interface cooling is distinct from bearing/gear oil.

## Sanitation Boundary

Separate future architecture. Mechanical maintenance does not clean products,
alter food safety or create facility sanitation state.

## Persistence Architecture

Recommend condition embedded in an explicitly evolved per-instance Workstation
projection, with retained exact owner receipts and bounded hot lookup. No
separate condition authority or reuse of the transfer endpoint journal.

## Durable Workstation Projection

Freeze product/resource and condition post-state together; stage exact evidence;
publish/verify joint durable state; reconcile loaded view; expose result.
Operating-state transitions require exact paired evidence, not an assumed
filesystem-atomic two-file write. All such schema changes remain future work.

## Checkpoint / Restoration

Workstation prepares stable exact condition for its required dependency set,
including unloaded instances. Incomplete condition rejects a new candidate
before head commit. R4 restores owner-native bytes without replaying effects.

## Historical Machine Migration

Recommend explicit healthy initialization for proven pre-condition schemas with
valid exact instances and no conflicting evidence. No inferred past wear.
Historical checkpoints remain immutable; condition-aware successor migration
does not reinterpret their original validity or rewrite active legacy children.

## Hard-Crash Recovery

Committed product/condition is inseparable. Uncertain publication blocks.
File retries only frozen bytes, never consequences. Settled exposure survives;
unproven exposure after the durable cutoff is explicitly not reconstructed.

## Crash Matrix

ADR Section 29 covers A-Q: pre-preparation, prepared work, forbidden product-
only split, staged condition receipt, stale block entity, interval open,
unsettled tail, settlement prepared/committed, fault creation, Run-observation
gap, maintenance prepared, forbidden resource-only split, repair publication,
checkpoint race, exact restoration and replacement. Every row states evidence,
allowed continuation, replay prohibition, Workstation ownership, Run and recovery.

## Concurrency Matrix

ADR Section 28 covers processing versus maintenance/repair/fault, STOP and supply
versus exposure, output clearing, checkpoint freeze, replacement/retirement,
duplicate service/repair, breakdown plus STOP, and configuration reload.
Serialization and exact freshness determine one inspectable owner order.

## Determinism

Exact integer/rational calculation, frozen policy, stable causal order, canonical
identities and explicit failure. No uncontrolled RNG, wall-clock authority,
render ticks, filesystem-time selection or hidden quality/speed change.

## Performance Model

Work scales with active effects/loaded exposure instances. A due-work index is
rebuildable, not authoritative. Future scale validation uses 1,000 registered
machines with a small active subset and measures writes, storage and checkpoints.
No checkpoint retention/compaction implementation is included.

## Diagnostics / GUI Direction

Expose condition/service/fault, exact policy/revisions, active exposure and
durable cutoff in read-only diagnostics. Player display favors bands, warnings
and actionable faults. Existing menu synchronization is preferred; no UI or
networking is implemented. Sound/particles never become evidence.

## Compatibility

DG-002 custody, DG-002A instance/journal, DG-004 stacks, DG-005 Runs and DG-005A
roles retain ownership. Future condition-aware processing contracts require
DG-003-compatible explicit versioning, not in-place historical changes.
Checkpoint schemas/migration need separate implementation authorization.
Planning/Production/Allocation do not gain machine control. Cutting Table is
outside powered-condition scope.

## Proposed Implementation Sequence

Verified no existing tracked IM-033/IM-034 reservation. Provisional IM-033A
foundation, IM-033B Grinder with minimal repair before breakdown, IM-033C
independent Patty Former activation, IM-034 richer maintenance/repair.
Final numbering awaits ratification and roadmap recheck. Scope, exclusions,
acceptance and validation for each are in ADR Section 33. None is started.

## Files Modified

1. `docs/adr/ADR-PROPOSED-MACHINE-CONDITION-WEAR-DAMAGE-MAINTENANCE-AND-BREAKDOWN.md` (new proposal).
2. `docs/DG-006-PROPOSAL-REVIEW.md` (new completion report).
3. `MILESTONES.md` (current proposal status and provisional sequence).
4. `KNOWN_LIMITATIONS.md` (unimplemented runtime and owner gates).
5. `TECHNICAL_ARCHITECTURE.md` (current/proposed distinction).
6. `docs/BCSE_ARCHITECTURE_GUIDE.md` (discoverability).
7. `docs/WORKSTATION_FRAMEWORK.md` (proposed ownership link).
8. `docs/GRINDER.md` (proposal-only reference).
9. `docs/PATTY_FORMER.md` (independent proposal-only reference).
10. `docs/MACHINE_RUN_STATE_FOUNDATION.md` (condition does not own Runs).
11. `docs/STARTUP_CHECKPOINT_RECOVERY.md` (future completeness direction only).

Historical ADR ratification records, milestone acceptance evidence and release
history were not rewritten. Java Architecture Manifest is unchanged.

## Validation

Documentation checks passed across the 11 intended Markdown files: 119 local
links, 7 heading fragments, 327 headings, balanced code fences, ASCII, no
trailing whitespace, and final newlines. The exact changed-path allowlist
passed. `git diff --check` passed with only normal LF-to-CRLF normalization
warnings. No repository documentation-checking tool was found; a read-only
PowerShell check validated these properties, including both new files.

No Java/runtime/test/resource/build/version/save path changed. No Java tests,
GameTests, datagen, build, or client launch was run for this documentation task.
Protected saves were not opened or hash-audited.

## Explicit Product Owner Decisions

The detailed choices and architecture/gameplay/recovery implications are in
[ADR Section 36](adr/ADR-PROPOSED-MACHINE-CONDITION-WEAR-DAMAGE-MAINTENANCE-AND-BREAKDOWN.md#36-owner-decisions).
The following is the matching review checklist, not a second definition of the
contracts. Every recommendation remains pending approval or revision.

1. Authority: Workstation / another documented owner. Recommend Workstation for one local consequence boundary.
2. Model: aggregate / full components / aggregate plus exposure/service. Recommend the third for explainable bounded state.
3. Scope: Grinder plus Patty Former / broader review. Recommend only those two; no unintended condition on manual tables.
4. Ordinary wear: yes / no. Recommend per-successful-child wear, coupled durably to product.
5. Randomness: deterministic / specified seeded model. Recommend deterministic thresholds for reproducibility.
6. Grinder empty: grace then wear/fault / harmless forever / revise. Recommend grace then gated consequences for meaningful STOP responsibility.
7. Grinder blocked: turning / powered-idle / stall model. Recommend powered-idle; no load signal exists to justify stall wear.
8. Patty empty: cycling / powered-idle / explicit alternative. Recommend powered-idle, without Grinder dry-friction assumptions.
9. Patty blocked: idle / cycling / stalled. Recommend idle for the existing no-cycle capacity wait.
10. Wear/damage: separate / merge. Recommend separate typed causes for diagnosis and exact evidence.
11. Faults: one controlling / multiple components. Recommend one with deterministic escalation, keeping repair/recovery bounded.
12. Breakdown: deny new work / allow use. Recommend deny START/RESUME/new children but preserve legitimate STOP.
13. Faulted Run: terminal FAILED / suspended repair RESUME / revise. Recommend FAILED and new explicit START after repair.
14. In-flight service: prohibit / new concurrency protocol. Recommend prohibit to protect product and resource atomicity.
15. Normal service: OFF and terminal/no Run / any nonprocessing Run. Recommend OFF, except the explicit restart case below.
16. Restart service: allow reconciled no-child exception / require STOP. Recommend narrow exception without implicit RESUME.
17. Repair sequence: repair before breakdown / accept irreparable gameplay / keep failure gated. Recommend validated player repair first.
18. Sanitation: separate / broaden. Recommend separate to preserve mechanical versus food-safety ownership.
19. Lubrication: reserve typed service / full simulation. Recommend reserve only justified machine-specific service.
20. Degradation: warnings/fault only / speed/quality/yield changes. Recommend unchanged throughput and product semantics.
21. Legacy state: healthy explicit migration / alternate baseline. Recommend healthy without inferred historic wear.
22. Storage: embedded projection / separate document. Recommend embedded for joint durability and unloaded recovery.
23. Exposure: bounded settlement / per-tick persistence. Recommend bounded exact intervals and threshold timing.
24. Offline: no exposure / aging model. Recommend no wall-clock mechanical wear.
25. Unload: suspend / remote operation model. Recommend suspension without force-load or fabricated motion.
26. Checkpoint: existing owner-native model / alternate ownership. Recommend Workstation payload completeness and immutable history.
27. Sequence: provisional IM-033A/B/C and IM-034 / revise. Recommend staged scope with final numbering after ratification.
28. Crash tail: last durable cutoff / stronger durable activity proof. Recommend explicit bounded tail loss, never lost coupled processing wear.
29. Grace reset: successful child / each empty entry / revise. Recommend successful child so polling and STOP/reload do not renew grace.
30. Jam/overload: reserve only / authorize new triggers. Recommend reserve; output fullness is not proof of jam.
31. Service restoration: debt only with repair for loss / every service heals / components now. Recommend first, retaining meaningful service distinction.
32. Break/re-place: accept disclosed reset / separate preservation amendment. Recommend temporary acceptance without changing identity or drops silently.
33. Evidence: exact receipts plus current state / hard cap / full tick journal. Recommend retained receipts and bounded hot index; never evict required proof.
34. Balance: separate versioned-data review / fixed numbers now. Recommend separate review and explicit safe policy activation.

## Architecture Stop Findings

No required contradiction with ratified ownership was found. The draft makes
future contract/schema evolution, joint durability and unresolved product
choices explicit gates. If later implementation cannot satisfy them, it must
stop for clarification rather than reduce the guarantees.

## Preserved Gates

No Java/runtime implementation; no persistence schema implementation; no
condition fields; no wear; no damage; no maintenance; no lubrication; no
breakdown; no repair; no recipes/resources/tests/commands/UI changes; no
Architecture Manifest runtime claims; no save changes; no version change;
no implementation milestone started. DG-005A remains ratified and IM-032A,
IM-032B and overall IM-032 remain accepted. Version is `0.10.7-alpha.1`.

Protected worlds are untouched by scope; this documentation task performs no
fresh protected-world hash audit. Changes remain uncommitted and unpushed.

## Recommended Next Step

Product Owner review and ratification of DG-006, including explicit physical
policy and recovery tradeoff choices. Do not begin implementation automatically.

## Ratification Completion Report

### Summary

Recorded explicit Product Owner approval of DG-006, all 34 decisions and
controlling invariants A-J. Retained the established ADR-PROPOSED filename and
updated only current documentation/status references. No implementation began.

### Preflight

| Check | Observed result at ratification |
| --- | --- |
| Branch | `feature/milestone-2d` |
| HEAD | `96499ac46f4ef1b8052b52b7f8def53d50468132` |
| Upstream | `origin/feature/milestone-2d` |
| Ahead / behind | `0 / 0` |
| Remote | Read-only `git ls-remote` confirmed the same full HEAD after the restricted-network attempt failed |
| Working tree | Nine modified tracked Markdown files and two untracked Markdown files, all existing DG-006 proposal work; not clean |
| Version | `gradle.properties`, `mod_version=0.10.7-alpha.1` |
| Numbering | No competing IM-033/IM-034 reservations; current matches belonged to this DG-006 proposal |
| Convention | Ratified ADRs retain ADR-PROPOSED filenames and use status, authority and Ratification Notes to record approval |

The existing proposal was preserved and ratified in place. No commit, push,
tag or publication was performed. Historical proposal preflight above remains
historical; it is not a claim that ratification began with a clean tree.

### Previous DG-006 Status

`PROPOSED - OWNER RATIFICATION REQUIRED`.

### New DG-006 Status

`RATIFIED ARCHITECTURAL DIRECTION - IMPLEMENTATION GATED`.

### Ratified Owner Decisions

All 34 are recorded individually in the ADR's canonical
[Ratification Notes](adr/ADR-PROPOSED-MACHINE-CONDITION-WEAR-DAMAGE-MAINTENANCE-AND-BREAKDOWN.md#37-ratification-notes).
This mapped confirmation summarizes that disposition, not a second contract:

| Decision | Approval recorded |
| --- | --- |
| 1 | Workstation singular condition authority; all other owners preserved. |
| 2 | Aggregate condition plus typed exposure/service; future explicit components. |
| 3 | Grinder and Patty Former only; Cutting Table excluded. |
| 4 | Successful bounded processing wear jointly durable with product/result. |
| 5 | Deterministic initial failure; no uncontrolled randomness. |
| 6 | Grinder empty grace, then accelerated dry wear; severe consequences repair-gated. |
| 7 | Grinder blocked POWERED-IDLE V1; no wear merely powered-blocked. |
| 8 | Patty Former empty POWERED-IDLE V1; no idle forming wear. |
| 9 | Patty Former blocked POWERED-IDLE V1; no copied Grinder dry exposure. |
| 10 | Separate wear and damage with typed cause evidence. |
| 11 | One controlling active mechanical fault initially. |
| 12 | Breakdown denies START/RESUME/later children, not legitimate STOP. |
| 13 | Proven breakdown leads to exact-Run FAILED; repair needs new START. |
| 14 | No in-flight maintenance/repair mutation. |
| 15 | Normal service requires OFF and terminal/no active Run. |
| 16 | Narrow reconciled no-child RESTART_REQUIRED service exception; no implicit RESUME. |
| 17 | Validated minimal player repair before irreversible breakdown activation. |
| 18 | Sanitation remains separate future architecture. |
| 19 | Typed machine-specific lubrication capability, not universal simulation. |
| 20 | No initial speed, quality, yield or recipe-output degradation. |
| 21 | Proven legacy machines initialize healthy; no inferred historical wear. |
| 22 | Condition embedded in evolved per-instance durable projection with exact receipts. |
| 23 | Bounded deterministic interval settlement with exact arithmetic/remainder. |
| 24 | No server-offline wall-clock wear. |
| 25 | Suspend on unload; settle proven loaded exposure; no force-loading. |
| 26 | Workstation-owned checkpoint condition; immutable historical generations. |
| 27 | IM-033A -> IM-033B -> IM-033C -> IM-034 sequence, numbering checked. |
| 28 | Last durable accounted crash cutoff; no reconstructed unproven tail. |
| 29 | Only successful Grinder processing resets dry grace. |
| 30 | Jam/overload capability reserved; no initial triggers. |
| 31 | Maintenance restores service/debt, not accumulated mechanical wear. |
| 32 | New-instance fresh condition accepted as an ALPHA LIMITATION. |
| 33 | Exact receipts plus bounded hot lookup; no eviction of required proof. |
| 34 | Separate owner balance review and explicit versioned policy identity. |

### Additional Ratified Invariants

The canonical A-J table is in the same Ratification Notes. All ten are recorded:
A separate operating/condition dimensions; B mechanical failure is not recovery
uncertainty; C legitimate STOP remains; D no per-tick global scan; E no per-tick
persistence; F no wall-clock wear; G exact instance/no coordinate inheritance;
H deterministic initial failure; I joint product/wear durability; J repair gate.

### Condition Authority

Workstation alone owns mechanical condition and consequential local results.
Execution owns Runs, Scheduler dispatch/timing, Clock time, Workforce
assignments/reservations, Material Handling custody and Checkpoint coordination.

### Authoritative Condition Model

Aggregate mechanical condition plus typed exposure/service and one controlling
fault. No initial full component simulation or second condition owner.

### Ordinary Wear

Deterministic successful-child wear is inseparable from the durable product and
owner result. Rejected/no-effect work earns no ordinary wear; duplicates observe
the existing result rather than reapplying consequences.

### Exposure Accounting

Bounded settlement uses authoritative transitions/time anchors and exact
integer/rational arithmetic with retained remainder. No per-tick persistence
or global machine scan. Operating-state duration alone is not proof of motion.

### Grinder RUNNING_EMPTY

Brief empty running has configurable grace; continued eligible exposure then
earns accelerated dry wear. Only successful processing resets grace. Severe
fault/breakdown still requires repair gameplay and activation authorization.

### Grinder OUTPUT_BLOCKED

POWERED-IDLE in V1: no new cycle, idle cutting mechanism and no blocked-state
wear merely powered. This is a gameplay model, not a universal equipment claim.

### Patty Former RUNNING_EMPTY

POWERED-IDLE in V1. No forming-cycle wear merely powered without processable input.

### Patty Former OUTPUT_BLOCKED

POWERED-IDLE in V1. No forming-cycle wear while output capacity blocks another
cycle; no inherited Grinder dry-running policy.

### Wear vs Damage

Expected cumulative deterioration and abnormal deterioration retain distinct
typed causes even when both affect aggregate condition.

### Fault / Breakdown

One controlling fault initially. Breakdown denies START/RESUME/later children,
not legitimate exact-Run STOP. Mechanical failure is never a substitute for
RECOVERY_REQUIRED authority uncertainty. Jam/overload triggers remain gated.

### Faulted Run Lifecycle

Workstation proves/inhibits; Execution terminalizes the exact Run FAILED at
a safe boundary. Repair never revives it. Unknown child outcome remains
RECOVERY_REQUIRED; only a new explicit START creates a post-repair Run.

### Maintenance / Repair Boundary

No service during an in-flight child. Normally OFF and terminal/no active Run.
The narrow RESTART_REQUIRED exception needs reconciled owners and no active
child, and never implies RESUME. Minimal validated player repair precedes
irreversible breakdown activation.

### Service vs Repair

Routine maintenance restores service state/debt, NOT accumulated mechanical
wear. Wear restoration requires an authorized repair/component-replacement
class action. Maintenance is not a generic condition-bar refill.

### Lubrication

Typed machine-specific service capability only. Product-mediated Grinder
cutting-interface cooling/lubrication is distinct from bearing/gear lubrication.

### Sanitation

Separately gated architecture; no cleaning or food-safety mechanics authorized.

### Historical Machine Initialization

Proven pre-condition machines receive explicit canonical healthy/default
initialization. No inferred wear, silent reset of newer schemas or alteration
of historical checkpoint bytes.

### Persistence / Durable Projection

Future condition belongs in explicitly evolved per-instance Workstation
projection with exact owner/effect receipts. No global condition file, second
authority, actual schema change or condition field was added in ratification.

### Checkpoint / Recovery

Workstation snapshots own exact condition and required evidence. Recovery
restores owner-native state without effects replay or coordinator authority.
Historical generations remain immutable.

### Crash Tail Policy

Keep the last durable accounted cutoff. Never fabricate the unproven exposure
tail; never discard ordinary wear coupled to a committed product or fully
committed exposure evidence.

### Chunk Unload / Offline Time

Settle proven loaded exposure at the valid unload boundary, suspend, preserve
anchors/counters and do not force-load. Offline wall-clock time earns no wear.

### Break / Replace Alpha Limitation

Replacement receives a new instance and fresh canonical condition. This reset
is explicitly accepted alpha behavior, not necessarily final portable-machine
behavior. No coordinate inheritance or condition-preserving item is introduced.

### Evidence Retention

Exact condition-effect receipts with bounded hot lookup/index. Required
recovery/checkpoint evidence is not evicted by age. No unbounded per-tick journal.

### Balance Gate

Mechanisms only are ratified. Before Grinder activation the owner must review
condition scale, ordinary wear, dry grace/rate, service/critical/fault-breakdown
thresholds, maintenance restoration and repair restoration. Policy identity
must be explicit/versioned; numeric defaults are not approved here.

### Ratified Implementation Sequence

DG-006 ratification -> IM-033A Machine Condition Foundation -> IM-033B Grinder
Condition Activation with minimal repair before breakdown -> IM-033C Patty
Former Condition Activation -> IM-034 Richer Maintenance And Repair Gameplay.
No competing numbering was found. IM-033A is recommended next but unauthorized;
employee maintenance remains separately gated.

### Files Modified

The cumulative proposal/ratification set remains exactly these 11 documents:

1. [DG-006 ADR](adr/ADR-PROPOSED-MACHINE-CONDITION-WEAR-DAMAGE-MAINTENANCE-AND-BREAKDOWN.md) (new, untracked).
2. [Proposal review and ratification report](DG-006-PROPOSAL-REVIEW.md) (new, untracked).
3. [MILESTONES.md](../MILESTONES.md).
4. [KNOWN_LIMITATIONS.md](../KNOWN_LIMITATIONS.md).
5. [TECHNICAL_ARCHITECTURE.md](../TECHNICAL_ARCHITECTURE.md).
6. [BCSE_ARCHITECTURE_GUIDE.md](BCSE_ARCHITECTURE_GUIDE.md).
7. [WORKSTATION_FRAMEWORK.md](WORKSTATION_FRAMEWORK.md).
8. [GRINDER.md](GRINDER.md).
9. [PATTY_FORMER.md](PATTY_FORMER.md).
10. [MACHINE_RUN_STATE_FOUNDATION.md](MACHINE_RUN_STATE_FOUNDATION.md).
11. [STARTUP_CHECKPOINT_RECOVERY.md](STARTUP_CHECKPOINT_RECOVERY.md).

### Validation

Documentation validation passed across all 11 intended Markdown files:
134 local links, 12 heading fragments, 364 headings and 27 tables. Heading
hierarchy, code fences, table columns, ASCII, trailing whitespace and final
newlines passed with no issues. The ADR retains numbered sections 1-38, with
exactly 34 mapped ratified decisions and ten controlling invariants A-J.

`git diff --check` passed with only normal Windows LF-to-CRLF normalization
warnings. The exact changed-path allowlist passed: nine tracked modifications
and two untracked documents, with no runtime/test/resource/schema/Manifest/save
or version paths. The original proposal report body was compared with its
pre-ratification contents and remains unchanged beneath the status annotation.
No repository Markdown-checking tool was found; read-only PowerShell checks
validated both tracked and untracked documents.

No Java tests, GameTests, build, datagen or client launch was run or claimed
for this documentation-only ratification. Version was rechecked as
`mod_version=0.10.7-alpha.1`. Protected saves were not opened or hash-audited.

### Preserved Gates

IM-033A, IM-033B, IM-033C and IM-034 have NOT started. No Java/runtime
implementation, test change, condition schema, condition field, wear, damage,
maintenance, repair, lubrication, breakdown, resource/recipe, command, UI or
save change. No Architecture Manifest runtime claim was added. No Production
machine control or employee maintenance. DG-005A remains ratified; IM-032A,
IM-032B and overall IM-032 remain accepted. Version remains `0.10.7-alpha.1`.

Protected worlds were untouched by task scope, not freshly hash-reverified.
Changes remain uncommitted, unpushed, untagged and unpublished. Stop after
ratification; implementation requires a separate Product Owner authorization.
