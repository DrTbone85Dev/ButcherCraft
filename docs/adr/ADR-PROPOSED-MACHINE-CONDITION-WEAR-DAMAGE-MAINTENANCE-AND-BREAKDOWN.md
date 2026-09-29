# ADR-DG-006: Machine Condition, Wear, Damage, Maintenance, And Breakdown

Status: RATIFIED ARCHITECTURAL DIRECTION - IM-033A COMPLETE / PRODUCT OWNER ACCEPTED; ACTIVATION GATED

Implementation status: the owner separately authorized IM-033A. Its
[foundation implementation](../MACHINE_CONDITION_FOUNDATION.md) is complete and
Product Owner accepted, with inert production policies. The
[acceptance record](../IM-033A-COMPLETION-REPORT.md#product-owner-acceptance-record)
records the 2026-09-28 owner decision. This does not mark DG-006 fully
implemented or activate IM-033B, IM-033C, or IM-034. The ratification-time
preflight and decision record below remain historical.

Decision identifier: DG-006

Package: Workstation Mechanical Condition

Authority: Product Owner-ratified architectural direction. The 34 decisions
and controlling invariants A-J in Section 37 govern future machine condition
work. Ratification does not authorize an implementation milestone, Java change,
persistence migration, gameplay activation, resource, or balance value.
Current release remains `0.10.7-alpha.1`. The established `ADR-PROPOSED-*`
filename is retained; the status above, not the filename, controls acceptance.

Canonical references:

- [Constitution](../../CONSTITUTION.md), [Core Principles](../../CORE_PRINCIPLES.md), and [Project Rules](../../PROJECT_RULES.md).
- [Architecture Guide](../BCSE_ARCHITECTURE_GUIDE.md), [Technical Architecture](../../TECHNICAL_ARCHITECTURE.md), and [Validation Framework](../ARCHITECTURE_VALIDATION_FRAMEWORK.md).
- [Platform Canonicalization Addendum](ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md) for identity classes, ownership, Publication, Recovery, Replay, configuration, and failure vocabulary.
- [Evidence Lifecycle](ADR-PROPOSED-EVIDENCE-LIFECYCLE.md) and [Checkpoint Recovery](ADR-PROPOSED-CHECKPOINT-RECOVERY.md).
- [ADR-02A](ADR-PROPOSED-LEGACY-SPLIT-SNAPSHOT-RECOVERY-AND-COORDINATED-CHECKPOINT-PUBLICATION.md) and [ADR-02A-P1](ADR-PROPOSED-DURABLE-WORKSTATION-PROJECTION-AND-CHECKPOINT-COMPLETENESS.md).
- [DG-002](ADR-PROPOSED-MATERIAL-HANDLING-CUSTODY-AND-RECOVERY.md), [DG-002A](ADR-PROPOSED-WORKSTATION-ENDPOINT-DURABILITY-AND-INSTANCE-IDENTITY.md), and [DG-004](ADR-PROPOSED-STACK-AWARE-WORKSTATION-INVENTORY-AND-PARTIAL-TRANSFER.md).
- [DG-003](ADR-PROPOSED-EXECUTION-HANDLER-REGISTRY-EVOLUTION.md), [DG-005](ADR-PROPOSED-PERSISTENT-MACHINE-OPERATING-STATE-AND-CONTINUOUS-PROCESSING.md), and [DG-005A](ADR-PROPOSED-ROLE-AWARE-WORKSTATION-RESERVATIONS-AND-COMPATIBLE-ENDPOINT-ACCESS.md).
- [Workstation Framework](../WORKSTATION_FRAMEWORK.md), [Machine Runs](../MACHINE_RUN_STATE_FOUNDATION.md), [Execution](../GENERIC_EXECUTION_RUNTIME_FOUNDATION.md), [Scheduler](../LIVE_SCHEDULER_EFFECT_ENFORCEMENT.md), and [Simulation Clock](../SIMULATION_CLOCK.md).
- [Grinder](../GRINDER.md), [Patty Former](../PATTY_FORMER.md), [Workforce](../WORKFORCE_FRAMEWORK.md), and [IM-032 acceptance](../../MILESTONES.md#im-032-employee-machine-operation-acceptance).
- [Live Checkpoints](../LIVE_CHECKPOINT_PUBLICATION.md), [Startup Recovery](../STARTUP_CHECKPOINT_RECOVERY.md), [RFC-0022](../RFC-0022_RESOURCE_ALLOCATION_ENGINE.md), and [RFC-0023](../RFC-0023_DETERMINISTIC_EXECUTION_ENGINE.md).

## 1. Context And Preflight

Proposal inspection date: 2026-09-26. These historical facts were checked before
the proposal was created. Ratification preflight is recorded in Section 37.

| Fact | Observed value |
| --- | --- |
| Branch | `feature/milestone-2d` |
| HEAD | `96499ac46f4ef1b8052b52b7f8def53d50468132` |
| Commit | `Prepare ButcherCraft v0.10.7-alpha.1` |
| Upstream | `origin/feature/milestone-2d` |
| Ahead / behind | `0 / 0` |
| Remote verification | Read-only `git ls-remote` returned the same full HEAD |
| Initial working tree | Clean; `git status --short` had no entries |
| Version authority | `gradle.properties`, `mod_version=0.10.7-alpha.1` |
| IM-032 | Complete / Product Owner accepted; IM-032A and IM-032B accepted |
| Previous DG-006 state | Design could begin; no proposal or implementation |
| Numbering | DG-006 already reserved; no tracked `IM-033` / `IM-034` reservations found |

The ordinary network attempt was unavailable; an approved read-only remote
query succeeded. No fetch, commit, push, tag, build, datagen, client, or save
operation was needed for this architecture task. Historical acceptance and
release evidence remains historical, not fresh gameplay validation.

## 2. Problem Statement

Persistent machines can run, wait empty, and wait for output capacity, but
they have no authoritative mechanical condition. A future wear feature needs
to explain what changed, why it changed, which exact machine changed, and what
survives a crash. A durability bar or periodic damage counter alone cannot
answer these questions and would conflate powered state with physical stress.

The ratified direction defines a distinct Workstation-owned condition dimension.
Successful work and proven mechanical exposure produce exact owner effects.
They do not create another Machine Run, Scheduler, inventory, or recovery
authority. Repair restores mechanical capability, not missing evidence.

## 3. Existing Architecture And Repository Evidence

These are current facts, not proposed condition implementation:

| Evidence | Architectural finding |
| --- | --- |
| [PoweredProcessingMachineRunService](../../src/main/java/com/butchercraft/integration/machine/PoweredProcessingMachineRunService.java) | Composes exact Run and Workstation publications; checks terminal children; empty/blocked input does not create an independent processing loop; unchanged state observations are not republished. |
| [MachineRunCoordinatorService](../../src/main/java/com/butchercraft/integration/machine/MachineRunCoordinatorService.java) and [ExecutionMachineRunService](../../src/main/java/com/butchercraft/world/ExecutionMachineRunService.java) | Execution owns START/STOP, Run identity/generation, active uniqueness, and child admission. The coordinator is not another owner. |
| [MachineOperatingRecord](../../src/main/java/com/butchercraft/workstation/operation/MachineOperatingRecord.java) | Workstation owns state, revision, transition sequence, entry/observed ticks, duration summaries, availability, and exact Run/child references. `durationAt` measures state occupancy; it is not proof of physical motion during every included tick. |
| [MachineOperatingState](../../src/main/java/com/butchercraft/workstation/operation/MachineOperatingState.java) | Operating states include `OFF`, `STARTING`, `RUNNING`, `RUNNING_EMPTY`, `OUTPUT_BLOCKED`, `STOPPING`, `RESTART_REQUIRED`, `FAULTED`, and `RECOVERY_REQUIRED`; none is a condition ledger. |
| [WorkstationProcessingController](../../src/main/java/com/butchercraft/workstation/WorkstationProcessingController.java) | Revalidates one recipe, prepares exact output, commits a recipe-quantity inventory plan, and returns exact Execution owner-result evidence. No wear is applied. |
| [AbstractProcessingWorkstationBlockEntity](../../src/main/java/com/butchercraft/workstation/block/AbstractProcessingWorkstationBlockEntity.java) and [inventory base](../../src/main/java/com/butchercraft/workstation/block/AbstractInventoryWorkstationBlockEntity.java) | Consequential changes use a Workstation projection-mutation boundary. Durable publication completes before the successful caller return; intermediate in-memory state is not a separate durable completion claim. |
| [DurableWorkstationProjection](../../src/main/java/com/butchercraft/workstation/projection/DurableWorkstationProjection.java) and [projection service](../../src/main/java/com/butchercraft/workstation/projection/DurableWorkstationProjectionService.java) | Exact per-instance slots, controller state, owner references, projection revision, and digest are durable independently of chunk availability. Operating state remains separately authoritative. |
| [Projection storage](../../src/main/java/com/butchercraft/workstation/projection/WorkstationProjectionStorage.java) and [schema](../../src/main/java/com/butchercraft/workstation/projection/WorkstationProjectionSchema.java) | Current schema-1 records are sharded by instance identity, bounded, and atomically replaced. Adding condition is a future supported schema evolution, not permission to append unchecked NBT. |
| [EmployeePersistentMachineOperationService](../../src/main/java/com/butchercraft/integration/employee/EmployeePersistentMachineOperationService.java) | Finite employee assignments observe exact child results, retain operators while output is blocked, and STOP without proven inbound supply. Workforce does not own machine effects. |
| [Grinder block](../../src/main/java/com/butchercraft/machine/grinder/GrinderBlock.java) and [Patty Former block](../../src/main/java/com/butchercraft/machine/pattyformer/PattyFormerBlock.java) | Removal follows endpoint retirement and content-drop paths. Replacement has a new instance. Current machine drops do not preserve a condition-bearing portable machine identity. |
| [PrototypeProcessingContextValues](../../src/main/java/com/butchercraft/workstation/PrototypeProcessingContextValues.java), [ProcessingEvaluator](../../src/main/java/com/butchercraft/engine/evaluation/ProcessingEvaluator.java), and [ValidationRules](../../src/main/java/com/butchercraft/engine/validation/ValidationRules.java) | Prototype equipment condition is fixed at `IDEAL`. The engine input can affect evaluation/eligibility; it is not a persistent machine owner and must not be wired to new wear accidentally. |
| [ModDataComponents](../../src/main/java/com/butchercraft/registration/ModDataComponents.java) | Product and Production Order components exist; no registered machine-condition component or machine item-durability contract was found. Product components must not become machine condition. |

The current Grinder and Patty Former use continuous explicit-STOP policy;
the Cutting Table is manual-discrete. `OUTPUT_BLOCKED` keeps Run/power
authorization but proves no mechanical rotation, stall load, or heat. The
repository supplies no basis for identical empty/blocked physical policies.

ADR-02A-P1 and R4 close projection/checkpoint restoration, not arbitrary future
condition effects. The current Java Architecture Manifest has no mechanically
enforced DG-006 guarantee. Historical audit/hardening findings reinforce
singular ownership and coherent publication; they are not new runtime grants.

## 4. Goals

- Explain ordinary wear and abnormal damage from exact authoritative causes.
- Preserve Run, Scheduler, inventory, custody, reservation, and recovery owners.
- Support Grinder first and a distinct Patty Former policy without a global
  machine-specific switch or a speculative general equipment framework.
- Recover exact condition for loaded and unloaded instances without replaying
  consequences or inferring past mechanical activity.
- Make service warnings useful before failure and provide repair before
  breakdown becomes player-facing.
- Keep live computation proportional to active work and meaningful events.

## 5. Non-Goals

No implementation, balance ratification, item durability bar, new power grid,
thermal simulation, sanitation, corrosion/calendar aging, RNG breakdown,
foreign-object gameplay, product quality/yield/speed change, employee skill
modifier, employee maintenance, Production machine control, autonomous
logistics, new reservation role, portable machine condition, force-loading,
global per-tick scan, public API, or checkpoint retention/compaction.

Refrigeration/compressor stress, saws, slicers, mixers, stuffers, tenderizers,
and smokehouse equipment are future policy consumers, not policies designed
here. Cutting Table condition is outside initial scope.

## 6. Terminology

Platform identity, evidence, Recovery, Replay, and failure terms retain their
[canonical definitions](ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md).

| Mechanical term | Ratified meaning |
| --- | --- |
| Condition | Workstation-owned current mechanical capability of one exact instance, separate from operating authorization/state. |
| Wear | Expected cumulative degradation from successful work or explicitly approved sustained exposure. |
| Damage | Abnormal discrete or accelerated mechanical degradation with a typed cause; not every small wear increment. |
| Exposure | A proven interval or event under a machine-specific stress policy; exposure may have zero consequence during grace. |
| Service state | Durable mechanical servicing facts such as service debt and last exact service result; not a business maintenance schedule. |
| Fault | A typed, identity-bound impediment that may be cleared without replacing a broken machine. |
| Breakdown | A proven mechanical failure requiring repair before START/new-child eligibility; not recovery uncertainty. |
| Maintenance | An eligible preventative service action that restores approved service state, not arbitrary editing of condition. |
| Repair | An eligible corrective action restoring specified mechanical capability and/or clearing a proven fault. |

Condition, service state, controlling fault, operating state, and Run lifecycle
are independent dimensions. A stopped machine can remain broken. A healthy
machine can be `RESTART_REQUIRED`. A recovery-blocked machine has uncertain
authority, not necessarily bad mechanical health.

## 7. Authority And Ownership

Workstation is the singular condition owner because it already validates recipes,
serializes local inventory effects, owns operating transitions, binds instance
identity, and publishes durable projections. A new independent condition
service owner would require a distributed product/wear commit without a
proven consumer benefit. Execution or Scheduler ownership would leak machine
semantics; Workforce ownership would make condition depend on controller type.

| Responsibility | Ratified owner | Forbidden acquisition |
| --- | --- | --- |
| Condition, service state, exposure settlement, fault, repair result, condition freshness | Workstation | Execution, Scheduler, Workforce, Material Handling, Checkpoint cannot mutate these facts. |
| Machine policy definitions and configuration | Workstation contract with machine-specific policy data | Shared pure contracts do not import Grinder/Patty internals. |
| Run identity, START/STOP/RESUME, lifecycle, child permission | Execution, unchanged | A condition eligibility result is not Run authority. |
| Bounded dispatch, due-work ordering, invocation/effect observation | Scheduler, unchanged | Scheduling a settlement does not calculate wear or create a processing child. |
| Authoritative time | Simulation Clock, unchanged | Scheduler, calendar, filesystem, client, and wall clock are not alternate clocks. |
| Employee assignment, presence, operator/handler reservations | Workforce under DG-005A | No condition edit or automatic repair from operator presence. |
| Transfer lifecycle and custody | Material Handling under DG-002 | Deposit/withdrawal is not condition mutation. |
| Economic quantities and economic mutation | Inventory / Transactions | Workstation ItemStacks are not a shortcut into economic Inventory. |
| Generation selection, coherent capture/restoration | Checkpoint Recovery | It publishes owner-native condition bytes, not mechanical judgments. |
| Evidence retention and archival policy | Evidence Lifecycle | Workstation produces receipts; a count cap cannot discard required receipts. |

The serialized Workstation-owner boundary includes local condition,
inventory/controller effects, endpoint interaction, and operating-transition
coordination. This specifies ownership, not a Java lock primitive. Reuse the
existing owner boundary; do not add an independent condition mutex or let a
condition writer acquire another owner's mutable state while holding it.

## 8. Condition Model Alternatives

| Model | Strength | Cost / limitation | Recommendation |
| --- | --- | --- | --- |
| A. Aggregate only | Cheap, clear, bounded state | Cannot explain dry-running vs ordinary wear or service debt without additional facts | Not sufficient alone. |
| B. Full components | Knife, plate, drive, bearings, gearbox and lubrication can differ | Many schemas, balances, repairs and UI dimensions before useful interactions exist | Defer; no simulation of every part. |
| C. Aggregate plus typed exposure/service | One mechanical measure with explainable causes, one fault, future explicit component migration | Initial repair cannot target a real component individually | Ratified (Decision 2). |

Grinder knife/plate friction is the motivating exposure, not proof that every
component has identical health. Model C may later migrate to a small useful
component set; it must not pretend an aggregate history proves each component's
past condition. No component expansion is automatically authorized.

## 9. Ratified Authoritative Model

The ratified Workstation-owned per-instance condition payload requires:

- schema and exact Workstation Instance / World Identity binding;
- monotonic condition revision and canonical content digest;
- nonnegative integral accumulated mechanical loss, with a policy-defined
  finite maximum; displayed remaining condition is derived;
- typed service state, last service/repair evidence, and policy-bound counters;
- one optional controlling fault with exact identity, cause, severity,
  lifecycle, creation tick, and clearing/repair reference;
- active exposure identity/type, phase, cumulative eligible ticks, exact
  arithmetic remainder, start/accounted tick, and availability evidence;
- frozen policy/configuration identity, last condition effect/result, and
  retained evidence references required for duplicate/recovery proof.

This is a logical future schema, not a field added to today's NBT or JSON.
Mechanical bounds are `0 <= loss <= configured maximum`, with saturating
mechanical loss at that maximum, never negative/NaN/infinite values. Integer
tick, revision, identity-sequence, and arithmetic overflow fail visibly rather
than wrap. Exact rational rate remainders are retained so settlement partition
does not round away wear. Malformed persisted bounds fail, not clamp silently.

GOOD / SERVICE_SOON / SERVICE_DUE / CRITICAL / BROKEN may be derived display
bands. They are not another persisted truth. Warnings alone do not deny work.
Fault lifecycle and integrity/recovery disposition remain explicit facts.

## 10. Machine-Specific Policy Architecture

An immutable registered policy binds canonical machine type, policy version,
Configuration Identity, supported exposure kinds, wear function, grace/reset
rules, thresholds, allowed maintenance/repair results, and local START/child
eligibility. It consumes explicit owner snapshots and emits a candidate plus
typed evidence; it receives no unrestricted manager access.

Generic concepts are successful processing, powered idle, exposure settlement,
fault, and service. Grinder dry cutting-interface exposure and Patty Former
forming-cycle stress belong in their own policies. JAMMED / OVERLOAD /
BLOCKED_POWERED are reserved capabilities, not proof those signals exist.
Current input rejection is not a foreign-object event; full output is not
motor overload. Initial rates may be machine-default; future recipe factors
must bind recipe/configuration, not switch on item names in shared code.

Unknown condition-enabled types or unavailable policy identities fail visibly;
there is no implicit Grinder fallback. A default policy is valid only when
explicitly registered and identity-bound for that machine type.

Prefer validated, immutable, machine-specific data definitions using existing
content-validation conventions, activated only at a safe owner boundary.
Code constants are simplest for fixtures but expensive to balance; an ordinary
hot server config risks unbound changes; datapack definitions fit existing
content composition but require versioning and controlled reload. Recommend
data-driven definitions with retained exact configuration content, not a new
runtime plugin API. Numeric defaults, storage packing, dispatch budgets and
settlement cadence remain policy, not permanent constitutional invariants.

## 11. Ordinary Processing Wear

Require **one joint Workstation owner result**, not an eventually applied
post-success wear callback. Each successfully committed bounded child binds:
exact instance, Run/child/effect, recipe, input/output identities, condition
pre-revision/digest, policy, exact product post-state, condition post-state,
and any resulting fault. No successful result becomes visible without the
joint durable completion boundary in Section 22.

Rejected/pre-effect-cancelled/no-effect-blocked children incur no ordinary
processing wear. Their durations do not prove productive work. A retry observes
the exact joint result, never reapplies loss. A long Run still has one wear
consequence per proven child, not one per stack or one per Run.

Preparation captures condition freshness. Commit revalidates it and all
existing recipe/inventory/instance preconditions. New invalidating condition
evidence rejects stale pre-effect work; it cannot modify the inputs underneath
an invoked commit. Repreparation requires owning Execution authority and proven
no-effect state, never a hidden retry.

If child N succeeds and its earned wear reaches breakdown, its exact product
and condition/fault commit together; N+1 is denied. Existing no-effect failures
remain failures with no product and no ordinary wear. No invented mid-cycle
partial product or scrap rule is authorized.

## 12. Time-Based Exposure And Clock Boundaries

Require explicit Workstation-owned, half-open eligible intervals `[a,b)`.
Opening binds exact instance, operating transition/revision, Run if present,
policy/configuration, and an owner-issued monotonic exposure identity. The
record tracks accounted-through tick, cumulative eligible exposure and rate
remainder. Interval identity is not Scheduler invocation identity.

An interval is eligible only with proof of loaded, reconciled mechanical
activity under the selected policy. Simulation Clock supplies ticks;
Scheduler may dispatch bounded settlement. State duration alone, a Run's
existence, loaded block animation, and inventory contents do not prove motion.

Settlement occurs at relevant transitions, successful child boundaries,
bounded scheduled accounting boundaries, threshold deadlines, orderly unload,
shutdown, and checkpoint preparation before freeze. Identical observations
and GUI reads create no settlement writes. New condition work introduces no
per-tick durable record or global per-tick scan.

Deterministic interval algebra is required: settling 100 eligible ticks in one
batch and ten batches of 10 produces the same mechanical/service state and
the same semantic threshold tick, given the same ordered transitions and
policy. Publication revisions/receipt partitioning may differ; each remains
exact and replayable. Compute threshold crossing within the interval, not at
the poll time. Stop accumulation at the first motion-ending consequence;
remaining elapsed time is not charged as if the broken mechanism kept moving.

No later child or other relevant mutation may overtake an overdue settlement.
If bounded scheduling cannot service a due threshold, machine eligibility
waits. Scheduler keeps its dispatch/budget authority; Workstation computes
the consequence at the supplied authoritative boundary. Initial dry-run
settlement applies only without an active processing child; future overlapping
stress modes need a policy that cannot invalidate a child mid-commit.

### Exposure Suspension And Crash Cutoff

Use the **last durably accounted boundary** for crash recovery. An open
anchor does not prove continued activity. Normal live settlement may use
owner-observed contiguous availability, but the unpersisted tail is tentative
and is not reconstructed from a later Clock value after restart. Diagnostics
report the preserved cutoff and known unaccounted range; do not call that tail
durable wear. Decision 28 explicitly accepts this bounded crash-tail tradeoff.

Settled processing wear never uses this exception. Every proven product
result retains its coupled wear. A fully committed exposure result also
survives even when later observer publication failed. Rollback selects the
whole earlier owner baseline; it does not selectively retain products but
drop their condition consequence.

Orderly unload/STOP/checkpoint settles the proven tail before the boundary.
On crash/restart, close the old eligible segment at its durable cutoff with
explicit suspension evidence; resume a new segment only after normal loaded
reconciliation and explicit operating authority. Retain cumulative exposure
and grace progress, so repeated unloads/STOPs do not silently grant fresh grace.

### Transition Table

| Boundary | Ratified condition accounting |
| --- | --- |
| STARTING | No powered exposure merely from START intent. |
| Workstation publishes RUNNING | Establish loaded operational evidence; productive wear remains child-result based. No generic duplicate powered wear. |
| RUNNING -> RUNNING_EMPTY | Open machine-specific empty exposure only after exact no-child transition. |
| RUNNING_EMPTY -> RUNNING | Settle/close empty exposure through transition tick before next child; deposit alone does not edit condition. |
| RUNNING -> OUTPUT_BLOCKED | Open only the owner-approved blocked policy; no inferred stall. |
| OUTPUT_BLOCKED -> RUNNING | Settle/close blocked exposure, then revalidate one child. |
| STOP requested / STOPPING | Close admission immediately through Execution. Already active child earns its normal joint result; no generic empty exposure invented from STOPPING. |
| STOPPING -> OFF | Settle actual preceding exposure at the physical safe boundary; no later powered exposure. |
| Fault / breakdown | Settle up to exact failure boundary, inhibit new work; publish explicit powered disposition. |
| Unloaded, unavailable, RESTART_REQUIRED, recovery-blocked | Suspend eligibility; no assumed mechanical exposure. |

## 13. Grinder Dry-Running Model

Architecture: distinguish harmless eligible empty exposure from loss and from
a severe typed fault. Preserve exact accumulated exposure and thresholds;
do not damage the machine for every `RUNNING_EMPTY` tick.

Ratified gameplay direction, not live behavior: successful cycles earn ordinary wear; brief empty
operation is acceptable; accumulated empty exposure after configurable grace
adds accelerated wear; severe continued exposure can create a dry-running
fault/breakdown only after the repair/activation gate. STOP prevents future
exposure but does not erase earned consequences.

Grace is measured over cumulative eligible empty ticks since the last
successful productive child (or initial commissioning). A successful child
resets dry-run grace progress, not accumulated condition loss. Merely polling,
inserting invalid input, changing output, STOP/START, unloading, or restarting
does not reset it. This explicit reset rule is Decision 29, not hidden balance.

The motivating product model is that product at a grinder's cutting interface
can mitigate undesirable dry friction/heat. This is a deliberately simplified
gameplay model, not a manufacturer specification. It does not equate product
flow with gearbox/bearing lubricant. No RPM, temperature, service hours, or
seconds-to-damage claim is made.

Balance TBD: loss scale, per-cycle rate, grace, post-grace rate, severe threshold,
service bands, breakdown threshold, and service/repair restoration. Product
Owner review and exact Configuration Identity are required before activation.

## 14. OUTPUT_BLOCKED Physical Semantics

DG-005 requires powered Run retention and no input mutation while blocked.
It does not require rotating cutting/forming parts or mechanical stall load.

| Grinder choice | Gameplay / mechanical consequence | Recovery consequence |
| --- | --- | --- |
| A. Mechanism keeps turning | Needs explicit dry/stall/load policy; blocked output can damage equipment without productive work | Must retain and settle exact blocked exposure. |
| B. Processing mechanism idle while powered | Capacity is a recoverable wait; no V1 cutting-interface wear while blocked | Retain Run and blocked transition, but zero mechanical blocked loss. |
| C. Intermediate / stalled drive | Richer stress model, but current runtime has no load/stall signal | Needs additional authoritative policy inputs before activation. |

Decision 7 ratifies **B: POWERED-IDLE** for V1. It matches current no-cycle capacity
waiting without inventing load or silently powering OFF. Power authorization
and mechanism movement are distinct. This is the approved product interpretation,
not a claim about all commercial grinders. Alternatives A and C remain
unapproved and require a later explicit decision with exact exposure parameters
and proof inputs; neither may be introduced as an implementation default.

## 15. Patty Former Policy

Require independent ordinary cycle wear. Decisions 8 and 9 ratify
**POWERED-IDLE** for both RUNNING_EMPTY and OUTPUT_BLOCKED in V1, with no
forming-cycle wear merely from either powered wait. Empty cycling or load
stall remain unapproved alternatives requiring their own explicit policy and
proof inputs. Neither follows automatically from Grinder behavior.

Keep service warnings and deterministic breakdown eligibility, but do not
invent a forming jam from output fullness or missing input. A future proven
jam can be a typed recoverable fault with explicit clearing; initial live jam
triggers remain gated (Decision 30). No foreign object, thermal, motor-load,
speed, yield, or product-quality mechanic is included.

The runtime still retains the same authorized Run while empty/blocked and may
admit one child when eligible again. Material Handling delivery changes input,
then Workstation's operating transition changes exposure. It never creates a
Run or directly adjusts condition.

## 16. Wear Versus Damage And Service

Ordinary wear increments expected aggregate loss from productive results.
Abnormal exposure may add accelerated loss or a discrete typed damage result.
Receipts distinguish these causes even when both affect one aggregate measure.
No hidden random damage roll exists in V1.

Routine maintenance restores only approved service state/debt, including
exposure-related service facts. It does NOT restore accumulated mechanical
wear. Restoring mechanical capability requires an authorized repair or
component-replacement class action, not a generic condition-bar refill.
Routine lubrication does not erase accumulated mechanical wear.
Initial repair need not model separate physical components or permanent
irreparable chassis loss; rich component replacement is future work
(Decision 31). Service/repair outcomes retain history rather than edit old loss
receipts.

Lubrication is a typed future service concern where justified by machine
policy, not a universal global oil meter. Product-mediated cooling is separate.
Sanitation, cleanliness, food safety, and cleaning schedules remain separately
gated; mechanical service must not silently clean products or facilities.

## 17. Fault And Breakdown

Require one controlling active fault per instance initially. It binds exact
instance, fault type, causing effect/exposure, condition revision, authoritative
tick, lifecycle, and eventual clearing/repair result. If a second cause arrives,
retain it as evidence and use explicit policy precedence to strengthen the
controlling fault; never discard a breakdown because a lesser warning arrived.
Multiple independently serviceable component faults require later evolution.

Service bands are derived warnings, not faults. A recoverable jam/fault can
deny work without total aggregate loss; breakdown denies START, RESUME and new child
admission until repair. OFF, OUTPUT_BLOCKED, and RESTART_REQUIRED do not imply
breakdown. `UNKNOWN_OUTCOME` / `RECOVERY_REQUIRED` indicate missing or conflicting
proof and cannot be cured by gameplay repair.

Fault clearing requires an exact Workstation-owned action. Opening a menu,
walking away, reloading a chunk, STOP, and restart cannot clear it. Breakdown
persists in the durable condition state and checkpoint, not in an animation.

## 18. Run And Employee Interaction

Condition contributes immutable machine-local START, RESUME, and child
eligibility evidence, including freshness. Execution remains the permission
owner and revalidates exact Run/child bindings. Checking condition only once
at START is insufficient for a long Run.

Require a proven motion-prohibiting fault/breakdown to terminalize the exact
Run as **FAILED**, using DG-005's existing terminal lifecycle vocabulary, after
any admitted child reaches a proven safe boundary. Workstation first commits
fault evidence and inhibits new local admission; Execution observes that exact
owner evidence and publishes terminal failure. Integration cannot invent a
failure result or mutate Execution directly. Repair requires a new explicit
START/Run, not automatic reanimation of the failed Run (Decision 13).

`FAULTED` is the Workstation operating projection of the condition-owned fault,
not the fault record. Its powered disposition must be explicit: the ratified
V1 failure policy stops mechanism motion at the proven boundary. A child whose
joint completion creates failure still succeeds exactly once. A proven
pre-effect fault rejection creates no product/wear. Unprovable child outcome
blocks recovery instead of declaring clean mechanical failure.

Condition never denies a legitimate exact STOP solely for poor condition. STOP
may finish OFF while the independent fault persists, and never repairs it.
Stale identity, replacement, or unprovable in-flight effects can still prevent
a claim of safe terminal STOP. If fault and STOP coincide, settle earned
condition first; the exact serialized Run outcome may be FAILED if fault wins
before terminal STOP, or remain already STOPPED if STOP was terminal. Both
retain the same mechanical consequence; no rewrite of terminal history.

Workforce observes the owner fault and terminal Run, records typed blocked or
failed assignment evidence through its own lifecycle, and releases the
operator only after a proven safe terminal boundary. It does not repair,
retry, or select another machine. A service recommendation alone does not stop
an employee. Player and employee Runs use identical mechanical policy; current
finite no-supply STOP naturally limits exposure without granting immunity.

## 19. Maintenance And Repair Boundary

Initial service actions must be player-requested, server-validated and exact.
No employee maintenance role is introduced. DG-005A MACHINE_OPERATOR and
MATERIAL_HANDLER reservations are not service authority. Employee maintenance
would need a separately ratified access-role amendment and assignment milestone.

Ordinary service requires OFF, a terminal/no Run, no active or uncertain child,
no unresolved endpoint consequence, proven custody boundaries, and exact current
instance/condition/configuration. STOPPING is insufficient. Powered empty or
blocked Runs must STOP before service. A proven faulted machine whose Run is
terminal can be repaired without START.

Decision 16 ratifies one narrow exception: RESTART_REQUIRED may be serviced with no active
child and fully reconciled owner evidence, despite a suspended nonterminal Run.
The exact Run remains suspended; service does not RESUME it. Later RESUME must
validate the new condition freshness. An authorized-but-unfinished historical
child is still active for this gate; STOP/reconcile it first. Decision 15's
normal OFF rule and Decision 16's explicit exception are ratified together;
neither permits service while a child is active or implicitly resumes a Run.

Every maintenance/repair action binds exact action/proposal identity, instance,
expected condition and resource freshness, policy, resource plan, and exact
post-state/result. Duplicates observe the existing result; conflicting payloads
are rejected. A GUI or admin button cannot directly assign health.

Future resource consumption must use the existing resource owner's accepted
mutation boundary. Prefer a Workstation-local staged service input for a later
minimal player service path so consumption and condition restoration can be one
joint Workstation result. No new slot, recipe, consumable, or direct player
inventory debit is authorized here. If chosen resources cannot share the
existing atomic owner boundary, STOP and ratify the missing cross-owner
protocol before gameplay. Economic resources still require Transactions;
physical transport still requires Material Handling. Never consume parts
first and hope a subsequent condition write succeeds.

Admin evidence reconciliation is not gameplay repair. A future diagnostic
induce/reset tool needs explicit operator authority and auditable owner effect;
it must not erase uncertainty or function as the only ordinary repair path.

## 20. Persistence Architecture And Evidence

Compare the available placements:

| Placement | Consequence | Position |
| --- | --- | --- |
| Embedded condition in existing per-instance durable Workstation projection | Same owner and atomic product/condition candidate; bounded, unloaded-readable; requires explicit projection evolution | Ratified (Decision 22). |
| Separate Workstation condition document per instance | Independent writes, but projection must bind exact revision/digest and a second-file reconciliation protocol is needed | Defer until measured size/write needs justify it. |
| Global condition file or block-entity-only state | Global rewrite cost or unavailable unloaded truth; cannot satisfy current recovery requirements | Reject. |

Require a versioned condition payload within a future supported projection
schema, not an unversioned extra tag under current schema 1. It includes the
fields in Section 9 and exact effect receipts/references. The loaded block
entity holds a reconciled view; it is not an independent condition store.
Workstation operating state remains in its current separate owner record.

For evidence, require **current condition plus exact retained owner-result
receipts**, with a bounded hot lookup index. A last-effect pointer alone is
insufficient for an old duplicate after many children. Preserve canonical
proposal identity and exact applied/not-applied result for every still-required
effect, including exposure/service effects not backed by a child. Reuse the
Evidence Lifecycle classification/retention contract; extend Workstation owner
evidence rather than reinterpret the Material Handling endpoint journal.

No per-tick condition journal is needed. A hard-capped journal that evicts
referenced receipts is unsafe; a full tick/event trace in every machine file
is unnecessary. Immutable receipt payloads may be separated from bounded
current state under Workstation ownership when needed, with exact digest
references and checkpoint dependency closure. Physical packing remains future
implementation policy; no new authority or cross-owner commit is implied.

Freeze and durably stage required receipt bytes before current projection
activation; a staged receipt without its committed projection is not applied
authority. The committed projection/result binding is the completion proof.
No observer sees success before both are available. An ambiguous publication
blocks rather than trusting a staging file. Retained exact receipts prevent
old replay even when the current record has advanced. If required evidence
cannot be retained or read, affected mutation fails visibly. This ADR
does not promise currently unimplemented archive infrastructure.

Bound current state and service summaries (last service, last repair, last
effect); retain dependency-required immutable evidence outside hot state if
necessary. No deletion/GC/compaction policy is authorized. Existing checkpoint
retention remains separate debt, not a problem DG-006 silently solves.

## 21. Condition Revision And Effect Identity

Require a separate monotonic Workstation-owned condition revision because
inventory, endpoint, operating, and projection revisions cover different facts.
Inventory can change without wear, and exposure can change condition without
inventory. Projection revision advances for the complete snapshot; condition
revision advances only for a committed condition/accounting change. Equal
condition revision with different content is conflict. A transient index has
no revision authority.

Apply the Platform Identity Model without new identity categories:

| Identity | Required binding |
| --- | --- |
| Condition entity scope | Exact World and DG-002A Workstation Instance; no coordinate-only scope. |
| Condition freshness | Instance, condition revision and digest of every condition/service/fault/accounting fact examined. |
| Processing condition effect | Exact child domain Effect Identity/operation, instance, recipe, condition pre-state, policy/configuration, proposal and joint plan content. |
| Exposure settlement effect | Exact exposure identity, accounted interval/cumulative cursor, operating/availability proof, expected condition freshness and policy/configuration. |
| Maintenance/repair effect | Exact server-authorized action sequence/proposal, instance, condition/resource freshness, resource plan and policy. |
| Result evidence | Cause, before/after condition, exact product/resource post-state when coupled, fault changes, authoritative tick and committed projection binding. |

Digest dependencies must be acyclic: first hash the canonical joint plan and
post-state content, then the result binding that content and planned projection
revision, then the enclosing projection containing the result digest. A result
must not hash the enclosing projection digest that itself hashes that result.
Read-back verifies the final projection/result pair without self-reference.

Allocate an invocation/action identity before application; freeze its content.
Same identity/same content observes its existing authoritative result. Same
identity/different content conflicts. A changed revision cannot turn a duplicate
child into new work by recalculating an effect id. Exposure ranges cannot
overlap already-accounted ranges; a new interval needs a new owner identity.
Replacement gets an independent revision sequence; retired effects never
target its coordinates. Filesystem timestamps, random IDs and render ticks
are not identity inputs.

## 22. Durable Projection And Publication Ordering

Ratified non-endpoint processing/condition publication contract:

1. Under the serialized Workstation boundary, validate exact instance,
   inventory/controller, condition freshness, operating/Run references,
   policy and authority. Reject unresolved or stale preparation.
2. Freeze the entire product/resource and condition/fault candidate plus exact
   owner result. Stage required immutable receipt bytes without applied status.
3. Publish the **joint durable projection** through AtomicFilePublication,
   binding receipt content and exact post-state. Semantic read-back must pass.
   This is the non-endpoint durable completion point, not two independent
   product and wear writes.
4. Reconcile/verify the loaded Workstation view. In-memory preparation may be
   staged inside the existing owner boundary, but cannot be observed as
   successful or permit another mutation before durability is resolved.
5. Expose the matching Workstation result to Execution, then its existing
   Scheduler/Run/Workforce observers. If fault inhibits continuation, publish
   exact operating/Run observations before admitting anything else.

There is no allowed durable state "product committed, required wear absent"
or "parts consumed, restoration undecided." A fully frozen joint result is
mandatory before commitment. Projection publication failure before completion
cannot be reported as success. If replacement outcome is uncertain, observe
exact old/new bytes; if neither is proven, enter Unknown Outcome/recovery.
File retry retries frozen bytes only, never the recipe, wear, or resource debit.

Endpoint effects retain DG-002A/DG-004 ordering: journal EFFECT_COMMITTED,
exact projection, loaded reconciliation, RESULT_PUBLISHED. Condition is not
added as an independent Material Handling effect. For a deposit that changes
machine eligibility, first finish the endpoint protocol; then Workstation
settles the old exposure and publishes the operating successor before child
admission. Both use the same explicit transition boundary tick/order; a
checkpoint cannot freeze the gap as a stable condition-enabled machine.

Operating transitions and embedded condition remain different records under
one Workstation owner. Settle and publish the condition segment closure with
the exact intended transition reference first, then publish/observe the
operating successor, then open eligible exposure/admit work. A pending
transition marker and its immutable owner evidence block external completion
and checkpoint capture until reconciliation proves the paired revisions.
Do not claim two file replacements are filesystem-atomic. Startup finishes
only an already-proven owner transition or remains blocked; it must not invent
the missing physical interval. STOP completion waits for final settlement;
already-earned loss survives a coincident STOP/fault.

## 23. Checkpoint And Restoration

Workstation remains the checkpoint participant. Its condition-enabled snapshot
must contain exact condition payloads and required receipts for the same
required active/retired dependency closure as ADR-02A-P1, including unloaded
instances. No new condition participant or coordinator-owned condition file.

Before the normal frozen capture, Workstation settles proven eligible intervals
through the checkpoint boundary under normal owner authority. Snapshot capture
itself does not execute effects. If the bounded preparation cannot reach a
stable complete state, reject/defer that candidate; retain the previous head.
Freeze cannot combine inventory after child N with condition before N.
Unsupported, missing, ambiguous, pending or inconsistent required condition
evidence makes a new condition-enabled candidate non-restorable.

R4 selection and Restoration Intent/Result remain controlling. Restore exact
condition through the Workstation native adapter, verify all owner references,
apply Policy B, install gates, and reconcile loaded blocks lazily. Restoration
does not replay processing, settlement, service, repair, or item movement.
Neither later chunk NBT nor a larger unproven local revision wins over restored
condition. Include condition policy/migration identity in owner configuration
and the Platform Determinism Manifest.

## 24. Legacy Migration, Replacement, And Retirement

Require canonical healthy/default condition for a proven pre-condition
instance whose supported historical schema contains no condition, with no
contradictory newer evidence. No wear is inferred from age, operation count,
stored duration, coordinates, item tooltip, or wall clock. Bind the exact
legacy projection digest, instance and migration policy in immutable
initialization evidence. Preserve slots, identity and all existing effects.

A missing field/file in a condition-enabled schema is corruption, not a
legacy machine. Unknown future schemas fail visibly. Active legacy children
retain their old handler contract/result and earn no retroactive wear; migrate
only at a proven safe boundary, or preserve Policy B and defer until the child
is resolved. Do not change a historical child plan during restoration.

Condition-aware activation and new checkpoint completeness cannot outrun this
migration gate. An unresolved legacy child may remain under its supported old
contract, but the instance cannot be falsely advertised as a complete new-schema
condition participant. Downgrade must not erase condition, faults, receipts or
newer-schema dependencies. Older software is unsupported after activation
unless a separately authorized compatibility proof/migration preserves every
required fact; deleting condition to make an old parser load is forbidden.

Historical checkpoints remain immutable and valid under their historical
contract. Restore the historical baseline first, then create explicitly
versioned condition-aware owner successor/migration evidence before condition
mutation. Do not retrofit old generation bytes or label an incomplete
historical Workstation projection complete. Unavailable legacy projection
remains blocked; initialization cannot fabricate inventory. Proven unloaded
durable projections can migrate without loading their chunks.

A replacement receives a new instance and canonical new condition. Retirement
freezes final condition/tombstone and resolves prepared effects before claiming
safe completion. No later ordinary wear applies to that retired instance.
Retain old condition/receipts while any Run, Execution, endpoint, transfer,
checkpoint or recovery dependency needs them. Embedded storage reuses the
Workstation tombstone; no second condition tombstone authority is needed.

Current break/drop/re-place behavior creates a new machine instance and does
not carry condition on the dropped item. Decision 32 accepts fresh canonical
condition for that new instance as an explicit **ALPHA LIMITATION**, not
necessarily the desired final portable-machine behavior. Condition never
transfers by coordinates. Future condition-preserving physical machine items
or identity require separately authorized architecture and implementation.
DG-006 does not alter drops, transfer old identity, or add such ItemStacks.

## 25. Chunk Unload, Restart, And Offline Time

Normal unload settles the proven loaded segment, suspends exposure, and retains
exact anchors/counters; no chunk ticket is created. Unexpected availability
loss recovers from the durable cutoff in Section 12, not assumed runtime.
On normal load, exact instance/projection reconciliation precedes a new eligible
segment. Existing Run authorization may remain, but availability is not motion.

Policy B suspends the exact nonterminal Run and exposes RESTART_REQUIRED;
condition remains exact and exposure is suspended. RESUME revalidates current
condition and preserves Run identity. Offline days create zero wear. OFF
creates zero powered exposure; corrosion/passive aging is not introduced.

An ADR-02A Scheduler Recovery Discontinuity represents omitted coordination,
not physical work. Never settle the gap by subtracting state-entry tick from
restored Clock tick. A restored checkpoint supplies already-settled condition,
not permission to recompute history. Keep discontinuity, unavailable time,
restart suspension and normal loaded operation distinguishable in evidence.

## 26. Determinism And Configuration Evolution

All consequential calculations use exact owner inputs, explicit ordering,
Simulation Clock ticks and frozen policy/configuration. No uncontrolled RNG,
wall time, client data, filesystem time or render cadence. Deterministic
thresholds are required initially instead of probabilistic failures; seeded stochastic
policies would require new exact seed/draw/order evidence and separate approval.

Already-settled loss never changes when balance changes. Pin active intervals
and prepared children to their original exact policy content. At an authorized
safe reload boundary, settle the old eligible segment under the old policy,
then open the successor under the new policy with an explicit conversion rule
for counters/bounds. Retain the old policy while referenced. Missing policy
content is recovery-blocked, not "use latest." No uncontrolled live config swap.
Changing the loss scale or schema is migration, not ordinary balance reload.

Within one instance serialize by existing owner action order, authoritative
tick and stable causal/action identity; use canonical instance order for equal
due settlements. Worker thread timing never chooses an outcome. Optional
parallel preparation operates on immutable candidates only.

## 27. Performance And Persistence Frequency

Live work scales with active loaded exposure machines and completed effects,
not all registered or retired Workstations. A reconstructable due-exposure
index may schedule one bounded next accounting/threshold event per eligible
instance; it owns no condition and cannot grant operation permission.
Startup rebuild is bounded/paged and respects suspended Policy B state.

No condition-triggered global per-tick scan, per-tick log or per-tick persistence.
Batch exposure mathematically between meaningful boundaries. A successful
child includes its wear in the existing required owner publication, not an
additional per-tick condition file. Rapid flapping settles exact short segments
and carries arithmetic/grace remainder; unchanged state does nothing. Retain
one receipt per meaningful consequence/publication, not every tick sample.

Checkpoint capture may enumerate its required closure; that is not permission
for per-tick enumeration. Measure current-state size, retained receipt growth,
write amplification and checkpoint volume. Sharded existing projections avoid
a new ever-growing global condition rewrite, but evidence growth is real and
retention/compaction remains separately gated.

Future validation must cover at least 1,000 registered condition-enabled
instances with a small active/dry subset, long Runs of 64+ children, flapping,
simultaneous due events and checkpoint capture. Measure active evaluation cost,
publication count/bytes, storage size and checkpoint latency with correctness
assertions. No unsupported throughput claim is made here.

## 28. Concurrency Matrix

Every row uses the serialized Workstation boundary and immutable external owner
observations. A denied request has an explicit outcome, not a dropped action.

| Race | Required deterministic order/result |
| --- | --- |
| Processing vs maintenance | An active prepared/admitted child blocks service. If service committed before preparation, preparation sees its new condition revision. Stale pre-effect candidates reject. |
| Processing vs repair | Same exclusion; repair cannot overwrite a child's frozen plan or restore condition under an invoked commit. |
| Processing vs fault | Settle due pre-admission fault first; no child. A successful child's earned fault commits jointly with product. Unprovable invoked outcome blocks, never tears the effect. |
| STOP vs dry settlement | Settle exactly through the safe physical transition, publish the closure once, then OFF. STOP intent alone does not erase the tail. |
| Supply arrival vs dry settlement | Endpoint owner finishes deposit, Workstation settles old empty segment and publishes eligibility transition before new child admission. No handler condition edit. |
| Capacity restoration vs blocked settlement | Finish the inventory effect, settle the approved blocked policy through transition, then re-evaluate. Do not infer motion from extraction. |
| Checkpoint freeze vs mutation | Capture only complete stable joint candidate and matching transition references; otherwise defer/reject before head. |
| Replacement vs prepared effect | Exact instance mismatch rejects old effect; historical receipt cannot target new coordinates. |
| Retirement vs settlement | Close proven exposure before terminal tombstone; unresolved effects keep retirement blocked. |
| Duplicate maintenance / repair | Same action/content observes receipt; conflict rejects; consume resources once. |
| Breakdown vs STOP | Earned condition persists in both orderings; exact terminal Run evidence determines FAILED vs already STOPPED, never historical rewrite. |
| Configuration reload vs active interval | Settle old policy first at an explicit safe boundary; preserve old content if that boundary cannot be reached. |

## 29. Crash And Recovery Matrix

All condition ownership in this table remains **Workstation**. `PB` means an
exact nonterminal Run is restart-suspended under Policy B, not replayed. A
terminal Run stays terminal. `BLOCK` means affected authority cannot mutate
until exact evidence is reconciled; no gameplay repair may clear it.

| Boundary | Authoritative evidence | Permitted continuation / forbidden replay | Condition, Run and recovery outcome |
| --- | --- | --- | --- |
| A. Before processing condition preparation | Old complete projection and exact child/Run evidence, if any | Fresh validation only under valid live authority; no wear from elapsed time | Old condition; PB if Run exists; normal reconciliation. |
| B. Processing prepared, before mutation | Old projection, immutable prepared joint candidate, no commit proof | Observe not-applied evidence; later exact authorized child handling only; no blind reinvocation at startup | Old condition; PB; ambiguity -> BLOCK. |
| C. Product result committed, condition publication pending | Such an independent committed result is forbidden by joint completion | An in-memory candidate is not success; exact durable joint candidate wins or old complete state remains; never apply wear as guessed catch-up | If legacy/corrupt split appears, BLOCK/Unknown Outcome; no product-only new-schema authority. |
| D. Condition result committed, projection pending | Staged receipt is not committed; only a complete joint projection binding proves application | Finish exact byte publication only when owner evidence proves it; no second effect | Old or exact joint state, never guessed intermediate; PB/BLOCK. |
| E. Projection durable, block entity stale | Exact projection, receipt and instance | Reconcile same-instance mirror; no product/wear replay | New exact condition; PB; replacement/conflict -> BLOCK. |
| F. Dry interval begins | Durable opening anchor if committed, otherwise prior state | Observe anchor; no charge before proven accounted boundary | Zero/new eligible accounting as recorded; PB suspends exposure. |
| G. Active dry interval before settlement | Last durably accounted condition/cursor | Discard unproven tail explicitly under Decision 28; do not infer activity to restart Clock | Exact cutoff state; PB; record suspension and lost/unproven tail. |
| H. Settlement prepared | Old committed cursor and frozen candidate | Finish same provable publication or retain old state; no overlapping new settlement | Exact old/new boundary; PB; uncertain replacement -> BLOCK. |
| I. Exposure effect committed | Joint condition projection and exact receipt | Observe result only; never charge the interval again | Settled condition retained; PB if still nonterminal. |
| J. Threshold crossing creates fault | Joint condition/fault evidence and semantic crossing tick | Publish matching operating/Run observation; no random reroll or repeated damage | Fault retained; deny admission; PB pending proven terminal observation, ambiguity -> BLOCK. |
| K. Breakdown committed, Run transition pending | Workstation breakdown result bound to exact Run | Execution observes proven result and terminalizes exact Run; no new child or automatic START | Broken condition; FAILED when safely proven, otherwise PB/BLOCK pending reconciliation. |
| L. Maintenance prepared | Exact action/resource plan, old condition, no complete result | Revalidate only if proven unapplied; no automatic resource debit on load | Old condition; no Run or narrow PB exception; ambiguity -> BLOCK. |
| M. Resources committed, condition pending | Independent commitment forbidden for initial joint service path | Never infer repair from absent parts; finish only complete frozen joint evidence | Split without proof -> Unknown Outcome/BLOCK; no compensating duplication. |
| N. Repair result committed, projection pending | Same joint publication rule as D | Receipt staging alone cannot clear fault; reconcile exact committed projection only | Old or exact repaired state; PB never auto-resumes; ambiguity -> BLOCK. |
| O. Checkpoint during mutation | Last stable owner snapshot or rejected candidate | No mixed product/condition snapshot; prior head remains if incomplete | Recover selected complete generation only; PB and explicit gates. |
| P. Restoration with condition | Selected manifest, owner payloads, Restoration Intent/Result | Owner-native exact replacement and lazy mirror reconciliation; no consequence replay | Exact selected condition/fault/cutoff; PB; failed validation -> BLOCK. |
| Q. Replacement with historical evidence | Old instance/tombstone plus independently allocated replacement | Reject old effects against replacement; preserve referenced historical evidence | New default condition only for proven new instance; old Run cannot transfer; conflict -> BLOCK. |

Power loss, JVM termination, filesystem interruption, torn attempts, stale
temporary files and Windows sharing retries all follow these proof boundaries.
File visibility alone does not prove commitment. A storage read-back failure
may require operator recovery even when a physical replace occurred; never
automatically reapply to make state look right.

## 30. Diagnostics And Presentation

Future read-only diagnostics should expose exact instance, schema, condition
revision/digest, aggregate loss/remaining band, service state, active exposure,
start/accounted tick, eligibility/availability proof, configuration, fault
identity/type/cause, last effect/result/service/repair, durable cutoff, pending
publication and recovery reason. Clearly distinguish settled from tentative
exposure and mechanical failure from authority failure.

Future GUI should show an understandable condition band/percentage, service
warning, dry-run warning, fault/breakdown and permitted repair requirement.
RUNNING_EMPTY alone must not falsely imply damage; show exposure warning when
policy warrants it. Keep internal revisions out of primary player labels.
Use existing menu synchronization where practical; no new network API is
designed here. Sounds, smoke, sparks, animation and particles are presentation,
never proof inputs. No presentation is implemented by this ratification.

Log meaningful threshold/fault/service/recovery transitions, not every tick.
Diagnostics may measure wear/product, exposure, service intervals, breakdown
and write frequency locally; no telemetry service is proposed.

## 31. Compatibility And Dependencies

| Existing contract | DG-006 relationship / future gate |
| --- | --- |
| Constitution / Core Principles | Applies AI-0001/0002/0003/0004, AI-0011/0016/0017/0018, AI-0021/0022/0025/0026/0027/0028; no invariant amendment proposed. |
| Platform / Evidence Lifecycle | Reuses identity, failure, retention and configuration vocabulary; does not duplicate those authorities. |
| DG-002 / DG-002A / DG-004 | Preserves exact custody, endpoint journal, instance/replacement identity and stack semantics. No endpoint schema or slot change now. |
| DG-003 / Execution | Joint result and freshness alter future handler contract semantics. Require explicit versioned new contracts and retained historical profiles; never claim changed contracts are additive unchanged handlers. |
| DG-005 | Ratifies its separately gated condition direction; preserves Run authority, explicit STOP, powered waits, bounded children, Policy B and no force-load. Fault consequence implementation remains separately gated. |
| DG-005A / IM-032 | Roles remain operator/handler only; condition contributes observed eligibility, not reservation ownership. No autonomous service or new role. |
| ADR-02 / 02A / 02A-P1 / R4 | Extends future Workstation owner payload completeness with exact condition; preserves committed-generation selection, discontinuity meaning and immutable history. |
| Planning / Production / Allocation | May later consume immutable availability facts; no condition-owned business scheduling, capacity commitments or Production machine control. Execution does not gain an Allocation prerequisite. |
| RFC-0023 | Narrow implemented Execution foundation remains distinct from the full proposed RFC; this ADR does not ratify or implement its remaining surface. |
| Simulation Clock / Scheduler | Time from Clock; bounded dispatch from Scheduler; Workstation computes mechanical effects. |
| Product engine / recipes | Keep current prototype equipment factor separate to avoid accidental quality/yield/eligibility changes beyond explicit condition gates. |
| Architecture Manifest | Java unchanged; no claim of mechanically enforced condition invariants until future authorized implementation proves them. |

Conceptual flow is owner input -> Workstation condition candidate/result ->
Execution eligibility/result observation and immutable diagnostics. Integration
composes these contracts, not mutual manager imports. Checkpoint consumes
owner snapshots. Condition does not depend on Workforce or Production policy;
Core never imports machine-specific definitions. No new dependency cycle is
required.

Existing historical ADR baselines and implementation reports remain unchanged.
Some subsystem narrative still describes earlier milestones; current code,
ratified contracts and explicit IM-032 acceptance control this ADR. Do not
use a historical no-employee-operation statement to undo accepted IM-032.

## 32. Security And Authority Boundaries

Clients submit intent only. Server validation decides condition eligibility,
effects and service results. A client-supplied percentage, item component,
display name, coordinate or GUI state cannot authorize repair or overwrite an
instance. Developer tools require existing operator permissions and exact owner
effects; no silent repair from administrative access.

Normal inventory edits, creative insertion and Material Handling transfers
retain their existing authority paths. They may lead to a Workstation state
transition, but cannot directly decrement/restore condition. STOP remains
available through its existing exact-Run authority; uncertainty remains visibly
blocked rather than bypassed by a debug command.

## 33. Ratified Implementation Sequence

No implementation step is authorized or started. A fresh repository numbering
check at ratification found only the DG-006 proposal's IM-033/IM-034 references,
with no competing milestone reservations. Decision 27 therefore ratifies the
sequence below. Sequence approval is not implementation authorization or
completed milestone acceptance; each step requires separate owner approval.

| Ratified sequence | Scope if separately authorized | Prohibited scope | Acceptance / required validation |
| --- | --- | --- | --- |
| IM-033A - Machine Condition Foundation | Pure model/policy, exact effect and revision, joint product/condition candidate, versioned projection and receipts, owner-native migration/restoration, diagnostics; successful-child/exposure math exercised by fixtures | No live wear, dry damage, fault/breakdown, service gameplay, resource changes or employee maintenance | Determinism and partition tests; same/conflicting identity; freshness/overflow/schema tests; complete A-Q crash and concurrency matrices; old 0.10.7 machine/checkpoint compatibility; loaded/unloaded/replacement tests; 1,000-instance scale; architecture, full Java/build/GameTests and protected-copy-only recovery. |
| IM-033B - Grinder Condition Activation | Approved Grinder ordinary/dry policies, warnings/display and safe fault/Run observation; one separately approved minimal player repair path must be implemented and validated BEFORE breakdown can be activated in this milestone | No rich components, employee repair, quality/speed/yield changes, Production control, or automatic restart | Owner-approved balance; conformance to ratified blocked/reset/break-drop decisions; long processing-empty-supply-blocked-STOP-service test; exact resource/repair atomicity, duplicate and crash tests; full regression/GameTests/client manual acceptance; no per-tick condition writes. If minimal repair is not authorized, keep breakdown activation gated. |
| IM-033C - Patty Former Condition Activation | Its approved independent cycle/empty/blocked policy, warnings and safe fault behavior; validated minimal repair support for this machine before breakdown | No copied Grinder dry behavior without decision; no invented jam/load signal, new reservation role or employee maintenance | Independent policy tests, exact Run safety, repair eligibility, checkpoint/crash recovery, full Grinder regression and player/employee manual validation. |
| IM-034 - Richer Maintenance And Repair Gameplay | Richer owner-approved service actions/resources/interactions beyond the minimal repair gate; component evolution only with explicit later architecture | No automatic employee maintenance, general Logistics or new access role by implication | Exact action/resource/condition atomicity, repetition/spam, forbidden-child boundary, save/checkpoint/hard-crash tests, balance and manual acceptance. |

The first gameplay activation must not strand permanently broken machines.
The minimal player repair path must be validated before IM-033B breakdown
activation; it cannot be deferred until after shipped breakdown.
Employee maintenance remains an unnumbered later architecture/implementation
gate. Foundation first, minimal safe repair before failure, rich service later.

All future runtime validation must use disposable fixtures/copies; no protected
historical save may be opened or migrated without separate permission. Future
commands, menus and networking require command-tree/server safety regression
where touched. Datagen is required only when that authorized milestone changes
generated content. No runtime tests are claimed for this ratification.

## 34. Known Risks

- Motion semantics are ratified; numeric balance remains unratified and requires
  Product Owner review before Grinder condition activation (Decision 34).
- Condition-aware handler/result contracts and projection/checkpoint schema
  evolution require compatibility work, not in-place historical reinterpretation.
- State-duration totals are not eligible-exposure proof; using them directly
  would fabricate wear during unavailable or recovered intervals.
- Last-durable-cutoff exposure recovery loses an explicitly unproven tail;
  stricter preservation needs a stronger ratified durable availability model.
- Break/re-place fresh condition is an accepted alpha limitation (Decision 32),
  not final portable-machine behavior or permission for coordinate inheritance.
- Separate operating record and embedded condition require exact transition
  pairing; failed publication must block admission/checkpoint, not split truth.
- Receipt/checkpoint growth must be measured. This ADR does not solve
  the separately gated retention/compaction architecture.
- Existing prototype equipment factor would couple condition to quality if
  reused indiscriminately; V1 leaves that input unchanged.
- Minimal repair resource semantics and balance must be approved and proven
  before failure gameplay; developer repair is not a player repair path.

No required transfer of ratified authority or prohibited runtime change was
found necessary to ratify this direction. These are explicit future approval and
implementation gates, not permission to work around them. If implementation
cannot meet a ratified joint effect/recovery boundary, stop for architecture
clarification; never simplify by publishing product and wear independently.

## 35. Rejected Alternatives

- A durability bar as sole state: loses cause, service/fault distinction and
  evidence. A bar may be a derived display only.
- Damage every empty tick: removes grace, adds write load and poll sensitivity.
- Full component/thermal/electrical simulation now: no proven initial need.
- Execution/Scheduler/Workforce-owned condition: crosses existing ownership.
- Detached wear callback after successful product publication: crash can lose
  required wear or retry it twice.
- Reuse Material Handling endpoint journal for processing condition: changes
  its ratified semantics and couples unrelated effects.
- Global condition scan/file: scales with total history instead of active work.
- Infer elapsed wear from Run or Clock alone: fabricates motion through gaps.
- Repair clears RECOVERY_REQUIRED: disguises authority uncertainty as gameplay.
- Item/component state inherited by coordinates: violates instance replacement.
- Uncontrolled RNG and retroactive rebalance: unrepeatable consequences.
- Ship breakdown now and invent repair later: traps player machines.

## 36. Owner Decisions

The Product Owner approved all 34 decisions. Section 37 is the canonical
numbered disposition, including the service-restoration clarification and
accepted break/re-place alpha limitation. Considered alternatives above remain
rationale, not unresolved approval choices. The original review checklist is
preserved as historical evidence in the [proposal review report](../DG-006-PROPOSAL-REVIEW.md).
No final balance value or implementation milestone is authorized by ratification.

## 37. Ratification Notes

Ratified by explicit Product Owner decision, recorded 2026-09-26. Previous
status was PROPOSED - OWNER RATIFICATION REQUIRED. Current status is RATIFIED
ARCHITECTURAL DIRECTION - IMPLEMENTATION GATED. The repository's established
`ADR-PROPOSED-*` filename is retained.

Ratification preflight: branch `feature/milestone-2d`, HEAD
`96499ac46f4ef1b8052b52b7f8def53d50468132`, upstream
`origin/feature/milestone-2d`, ahead/behind `0/0`. A read-only remote query
confirmed that full HEAD. The tree already contained nine modified and two
untracked DG-006 Markdown documents from the proposal; these were preserved,
not mistaken for a clean baseline. Version remains `0.10.7-alpha.1`.
No runtime, test, resource, schema, Manifest, save or version file was changed.

### Ratified Decisions 1-34

1. **Condition authority:** Workstation is the singular mechanical-condition
   owner. Execution retains Machine Runs; Scheduler bounded dispatch/timing;
   Simulation Clock authoritative time; Workforce assignments/reservations;
   Material Handling custody; Checkpoint Recovery snapshot/restoration
   coordination without condition authority.
2. **Condition model:** Aggregate mechanical condition plus typed exposure and
   service state, with future explicit component evolution. No initial full
   component simulation.
3. **Initial scope:** Grinder and Patty Former only. Cutting Table remains
   outside initial powered-machine condition scope.
4. **Ordinary wear:** Deterministic wear from successful bounded processing,
   durably coupled to the successful Workstation product/result boundary.
   Rejected and no-effect work earns no ordinary processing wear.
5. **Randomness:** Deterministic initial failure. No uncontrolled random
   breakdown; any future randomness requires exact deterministic authoritative
   evidence and separate approval.
6. **Grinder RUNNING_EMPTY:** Brief empty operation is harmless within a
   configurable grace period. Continued empty operation after grace earns
   accelerated dry-run wear. Severe exposure may fault/break down only after
   validated repair gameplay and separate breakdown activation authorization.
   Numeric balance remains unratified.
7. **Grinder OUTPUT_BLOCKED:** V1 is POWERED-IDLE. No new cycle occurs; the
   cutting mechanism is mechanically idle and earns no blocked-state wear
   merely because power remains authorized. This is a gameplay/physical model,
   not a claim about every commercial grinder.
8. **Patty Former RUNNING_EMPTY:** V1 is POWERED-IDLE. No forming-cycle wear
   merely from being powered without processable input.
9. **Patty Former OUTPUT_BLOCKED:** V1 is POWERED-IDLE. No forming-cycle wear
   while output capacity prevents a cycle. Do not copy Grinder dry exposure.
10. **Wear versus damage:** Wear is expected cumulative deterioration; damage
    is abnormal deterioration from misuse, fault or severe exposure. Both may
    affect aggregate condition while retaining exact typed cause evidence.
11. **Fault model:** One controlling active mechanical fault initially.
    Simultaneous component-specific faults remain separately gated.
12. **Breakdown:** Deny START, RESUME and later bounded-child admission.
    Legitimate exact-Run STOP remains available. Mechanical breakdown is not
    RECOVERY_REQUIRED.
13. **Faulted Run:** Workstation proves breakdown and inhibits continuation.
    Execution terminalizes the exact active Run as FAILED at a deterministic
    safe boundary. Repair never resurrects it; a new explicit START creates a
    new Run. Unknown/unresolved child outcome remains RECOVERY_REQUIRED.
14. **In-flight maintenance:** Maintenance/repair cannot mutate condition while
    a processing child is in flight. Serialize processing and service through
    the exact Workstation-owner boundary.
15. **Normal service eligibility:** OFF and terminal/no active Run, subject
    only to Decision 16 and the exact evidence gates in Section 19.
16. **RESTART_REQUIRED exception:** Allow service only with no active child
    and reconciled owner state. Service never RESUMEs the Run; it remains
    RESTART_REQUIRED unless separately STOPped or RESUMEd through DG-005.
17. **Repair before breakdown:** Foundation may precede service gameplay, but
    irreversible breakdown gameplay must not activate until a validated minimal
    player repair path exists.
18. **Sanitation:** Separate future architecture. No cleaning, sanitation or
    food-safety ownership is introduced by mechanical condition.
19. **Lubrication:** Typed machine-specific service capability, not universal
    lubricant simulation. Product-mediated Grinder cutting-interface
    cooling/lubrication is distinct from bearing/gear lubrication.
20. **Performance degradation:** No initial speed, quality, yield or
    recipe-output degradation. Initial gameplay is warnings, service need,
    fault and eventual gated breakdown; throughput/product modifiers remain
    future scope.
21. **Historical initialization:** Canonical healthy/default condition only
    for proven pre-condition machines through explicit migration. Never infer
    historical wear or rewrite historical checkpoints.
22. **Persistence:** Embed condition in an explicitly evolved per-instance
    durable Workstation projection with exact owner/effect recovery receipts.
    No second condition authority or global condition file.
23. **Exposure accounting:** Bounded deterministic interval settlement using
    exact authoritative transitions/time anchors and integer/rational
    arithmetic with retained remainder. No per-tick condition persistence.
24. **Offline time:** Server-offline wall-clock time creates zero wear or
    mechanical exposure.
25. **Chunk unload:** Suspend unavailable machines; settle proven loaded
    exposure at the valid boundary and preserve exact anchors/counters. No
    fabricated continued operation or force-loading.
26. **Checkpoint/recovery:** Condition remains Workstation-owned. Durable
    projections/checkpoints capture exact condition and owner-native recovery
    restores it without consequence replay. No coordinator condition authority
    or historical-generation rewrite.
27. **Sequence:** Ratify DG-006 -> IM-033A Machine Condition Foundation ->
    IM-033B Grinder Condition Activation with minimal repair before breakdown ->
    IM-033C Patty Former Condition Activation -> IM-034 Richer Maintenance and
    Repair Gameplay. The fresh repository numbering check found no competing
    reservations. Employee maintenance remains separately gated. None of these
    implementation milestones is authorized or started by this decision.
28. **Crash exposure tail:** Recover from the last durable accounted cutoff;
    never reconstruct an unproven powered interval after a crash. Coupled
    committed ordinary wear and fully committed exposure results are retained.
29. **Dry-run grace reset:** Successful Grinder processing resets grace.
    Re-entering RUNNING_EMPTY, STOP/reload, polling cadence or restarting an
    exposure calculation does not independently refresh it.
30. **Jam/overload:** Reserve typed capability only. Initial jam/overload
    triggers are not authorized; OUTPUT_BLOCKED alone never proves a jam.
31. **Service restoration:** Routine maintenance restores SERVICE STATE /
    maintenance debt, NOT accumulated mechanical condition/wear. Wear
    restoration requires a repair/component-replacement class action under
    future authorized gameplay. Maintenance is not a generic condition refill.
32. **Break/re-place:** Accept fresh canonical condition for a new replacement
    Workstation Instance as an explicit ALPHA LIMITATION. No coordinate
    inheritance. This is not necessarily final portable-machine behavior;
    condition-preserving physical items/identity remain separately gated.
33. **Evidence retention:** Exact condition-effect receipts plus bounded hot
    lookup/index. Never age-evict evidence required by recovery/checkpoint
    dependencies, and never create an unbounded per-tick journal.
34. **Balance:** Mechanisms, not numbers, are ratified. Before Grinder
    activation, Product Owner review must cover condition scale, ordinary wear,
    dry grace, accelerated dry wear, service/critical/fault-breakdown thresholds,
    maintenance restoration and repair restoration. Policy/balance identity
    must be explicit and versioned.

### Controlling Invariants A-J

| ID | Ratified invariant |
| --- | --- |
| A | Condition remains separate from operating state, including OFF, RUNNING, RUNNING_EMPTY, OUTPUT_BLOCKED, STOPPING and RESTART_REQUIRED. |
| B | Mechanical fault/breakdown is not RECOVERY_REQUIRED; that disposition remains authoritative-state uncertainty. |
| C | Mechanical condition cannot prevent legitimate exact-Run STOP. |
| D | Work scales primarily with active machine effects; no global per-tick scan of every registered Workstation. |
| E | Duration exposure uses bounded settlement and exact anchors, not per-tick persistence. |
| F | Only authoritative simulation/operation evidence creates condition consequences; no wall-clock wear. |
| G | Condition binds exact Workstation Instance Identity; no coordinate inheritance. |
| H | Initial condition/failure is deterministic, with no uncontrolled RNG. |
| I | A successful processing consequence and its required ordinary wear cannot diverge durably. |
| J | Gameplay breakdown remains gated until a minimal validated player repair path exists. |

### Implementation Gate

Ratification alone records architecture. The later separate IM-033A
authorization permits foundation runtime, versioned projection/effect evidence,
and recovery validation under inert production policies. IM-033B, IM-033C and
IM-034 remain gated. No production wear, damage, maintenance, repair,
lubrication, breakdown, resource or recipe activation is authorized here.
Java Architecture Manifest entries may claim only mechanically true foundation
guarantees. The release version remains unchanged.

## 38. Documentation Validation And Next Step

Documentation-only validation covers local links, heading hierarchy, code
fences, tables, ASCII, trailing whitespace, final newlines, changed paths and
`git diff --check`. No Java tests, GameTests, build, datagen or client launch
is needed solely for ratification. Protected worlds are not opened or modified.
Version remains `0.10.7-alpha.1`.

See the [ratification completion report](../DG-006-PROPOSAL-REVIEW.md#ratification-completion-report)
for ratification-time validation, complete changed paths and preserved gates.
Separate IM-033A authorization is now recorded in the implementation-status note
above; ratification itself was not permission to begin runtime work.
