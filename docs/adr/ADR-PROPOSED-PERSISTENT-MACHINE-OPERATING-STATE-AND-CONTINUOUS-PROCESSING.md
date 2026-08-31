# ADR-DG-005: Persistent Machine Operating State And Continuous Processing

Status: RATIFIED ARCHITECTURAL DIRECTION - IMPLEMENTATION NOT AUTHORIZED

Decision identifier: DG-005

Package: Workstation Machine Operation, Execution Authorization, And Scheduler Dispatch

Authority: Owner-ratified architectural direction. This document authorizes
the machine operating-state, Machine Run, bounded child-cycle, START/STOP,
recovery, ownership, and milestone direction defined here. It does not itself
authorize Java, resources, persistence, migration, gameplay, UI, Workforce,
Scheduler, Execution, Architecture Manifest, machine-condition, wear, damage,
or maintenance implementation. IM-031A and all later implementation remain
separately gated.

Canonical references:

- [`CONSTITUTION.md`](../../CONSTITUTION.md)
- [`Core Principles`](../../CORE_PRINCIPLES.md)
- [`Project Rules`](../../PROJECT_RULES.md)
- [`Technical Architecture`](../../TECHNICAL_ARCHITECTURE.md)
- [`Milestones`](../../MILESTONES.md)
- [`Architecture Guide`](../BCSE_ARCHITECTURE_GUIDE.md)
- [`RFC-0023 Deterministic Execution Engine`](../RFC-0023_DETERMINISTIC_EXECUTION_ENGINE.md)
- [`Generic Execution Runtime Foundation`](../GENERIC_EXECUTION_RUNTIME_FOUNDATION.md)
- [`Grinder Execution Vertical Slice`](../GRINDER_EXECUTION_VERTICAL_SLICE.md)
- [`Simulation Scheduler`](../SIMULATION_SCHEDULER.md)
- [`Live Scheduler Effect Enforcement`](../LIVE_SCHEDULER_EFFECT_ENFORCEMENT.md)
- [`Workstation Framework`](../WORKSTATION_FRAMEWORK.md)
- [`Workforce Framework`](../WORKFORCE_FRAMEWORK.md)
- [`Grinder`](../GRINDER.md)
- [`Patty Former`](../PATTY_FORMER.md)
- [`Cutting Table`](../CUTTING_TABLE.md)
- [`DG-002 Material Handling Custody And Recovery`](ADR-PROPOSED-MATERIAL-HANDLING-CUSTODY-AND-RECOVERY.md)
- [`DG-002A Workstation Endpoint Durability And Instance Identity`](ADR-PROPOSED-WORKSTATION-ENDPOINT-DURABILITY-AND-INSTANCE-IDENTITY.md)
- [`DG-003 Execution Handler Registry Evolution And Save Compatibility`](ADR-PROPOSED-EXECUTION-HANDLER-REGISTRY-EVOLUTION.md)
- [`DG-004 Stack-Aware Workstation Inventory And Partial Transfer`](ADR-PROPOSED-STACK-AWARE-WORKSTATION-INVENTORY-AND-PARTIAL-TRANSFER.md)

## 1. Decision In Plain Language

The current one-request, one-Execution-operation, one-recipe-cycle model is an
accepted foundation. It is not the intended final operating model for powered
continuous machines.

DG-005 ratifies four separate concepts:

1. Execution owns one explicit, persistent Machine Run authorization and its
   canonical Machine Run Identity.
2. Workstation owns the machine's operating policy and durable operating
   state, including whether it is running with no valid input or is blocked by
   output capacity.
3. Every recipe cycle remains one distinct, bounded Execution operation and
   one atomic Workstation-owned inventory effect linked to the active Machine
   Run Identity.
4. Scheduler dispatches each bounded cycle but never infers continued
   permission from input presence or generates an independent processing
   loop.

The selected model is therefore:

```text
explicit START request
  -> Workstation validates machine-local preconditions
      -> Execution accepts one persistent Machine Run authorization
          -> Workstation publishes powered operating state
              -> Workstation requests at most one bounded child cycle
                  -> Execution accepts one child operation bound to the run
                      -> Scheduler dispatches that child operation
                          -> Workstation commits one atomic recipe mutation
                              -> owner result and Execution result are observed
                                  -> Workstation reevaluates the same run
```

Input presence is cycle eligibility, not START authority. Output capacity is a
cycle precondition, not STOP authority. Wear or damage is future state and is
not part of either authorization or recipe mutation.

## 2. Repository Preflight And Baseline

This proposal was prepared against the following repository state:

| Fact | Observed repository state |
| --- | --- |
| Branch | `feature/milestone-2d` |
| HEAD | `10cc350eabd1c2965ab3ab590650255b2079c29e` |
| Local upstream comparison | `origin/feature/milestone-2d`, 0 ahead and 0 behind |
| Remote refresh | Not completed; the sandbox denied `.git/FETCH_HEAD` and elevated fetch approval was not granted |
| Worktree before DG-005 edits | Clean |
| Project version | `0.10.4-alpha.1` |
| Current completed work | IM-030A and IM-030B are implemented at HEAD |
| Next recorded gate | DG-004 names an unimplemented IM-031 Employee Patty Former Operation; `MILESTONES.md` had no IM-031 section before this proposal |

The latest commit message is `IM-030A/030B stack-aware processing and material
handling`. `README.md`, `CHANGELOG.md`, the DG-004 implementation status, and
the current code agree that selective stack-aware behavior is live while
operation throughput remains one recipe quantity per explicit request.

### 2.1 Current Grinder Player Flow

The Grinder uses `WorkstationOperationStartPolicy.EXPLICIT_REQUEST`.

- Normal right-click opens the inventory and grants no operation authority.
- Shift + right-click asks the server for exactly one operation.
- Inserting valid input changes the per-cycle Workstation state to `READY` but
  does not create an Execution operation or Scheduler Work.
- One accepted request resolves one recipe, freezes the current input and
  expected output identities, creates one Execution operation, runs one
  60-tick controller cycle, submits one generic Scheduler Work item, and
  commits one recipe quantity.
- Remaining input does not create another operation.
- A full or incompatible output rejects the cycle without consuming input.

### 2.2 Current Employee Grinder Flow

IM-027 is one transient, reservation-scoped Beef Grinder request.

- `/butchercraft employee operate <employee>` requires a present employee, an
  arrived Grinder reservation, operating-range proximity, Beef Trim input,
  empty output, and an idle or ready Grinder.
- Workforce owns the transient employee states `IDLE`, `PREPARING`,
  `OPERATING`, `WAITING_FOR_COMPLETION`, `OPERATION_COMPLETE`, and `FAILURE`.
- The employee delegates to the same Grinder controller. The Grinder issues
  private Execution authorization and owns the ItemStack mutation and owner
  result.
- Completion requires both matching Workstation owner-result evidence and
  Execution result evidence.
- Employee operation state is not persisted or reconstructed as startup
  recovery. Failure does not retry automatically.

### 2.3 Current Patty Former Flow

The Patty Former also uses `EXPLICIT_REQUEST`.

- Ground Beef deposit establishes `READY` only.
- Normal right-click opens the inventory.
- Shift + right-click requests one operation.
- One accepted request consumes one Ground Beef and produces one Beef Patties
  item through its own Execution handler and the generic Scheduler Work type.
- Remaining input waits for another explicit request.
- Employee delivery ends at `READY`; employee Patty Former operation does not
  exist.

### 2.4 Current Execution Operation Model

The implemented IM-011 Execution foundation is one-operation-oriented:

- private live authorization is consumed once;
- immutable authorization evidence binds source owner, executable reference,
  operation type, handler, frozen input, source freshness, configuration,
  world, issue tick, validity, and explicit input identities;
- `ExecutionOperationId` is derived deterministically from that evidence;
- one `ExecutionDomainEffectIdentity` belongs to that operation;
- lifecycle is `AUTHORIZED`, `READY`, `DISPATCHED`, `RUNNING`,
  `AWAITING_OWNER_RESULT`, then one terminal result;
- cancellation is supported only before Scheduler invocation starts;
- successful completion requires Workstation owner-result evidence;
- handler exception or unprovable consequential outcome becomes terminal
  `UNKNOWN_OUTCOME`; and
- schema-1 operations persist in
  `<world>/butchercraft/execution_operations.json`.

Duplicate authorization content observes the existing operation. Conflicting
content for the same authorization identity is rejected. The live Grinder and
Patty Former Execution Work uses `RetryPolicy.never()`. Repeated observation
after proven success returns existing evidence; terminal failure does not
create a new operation automatically.

### 2.5 Current Scheduler Work And Effect Model

Scheduler owns Work lifecycle, stage-400 dispatch, deterministic Invocation
Identity, stable consequential Effect Identity, effect-policy enforcement,
bounded work, owner-result observation, and Scheduler runtime persistence.

The generic Execution handler is `IDEMPOTENT` and owner-result-required. Each
machine operation has one Scheduler Work Identity derived from its Execution
Operation Identity. The scheduler never owns the recipe result or workstation
inventory. Persisted `RUNNING` Work is forbidden; an interrupted
consequential invocation becomes explicit uncertainty rather than automatic
reapplication.

### 2.6 Current Workstation Controller And States

`WorkstationProcessingController` owns one per-recipe lifecycle:

```text
IDLE -> READY -> PROCESSING -> COMPLETE
                    |            |
                    -> BLOCKED    -> IDLE or READY after inventory change
                    -> ERROR
```

These states describe inventory and one recipe cycle. They do not describe a
persistent powered machine. `READY` is not permission to operate, and
`PROCESSING` does not mean the machine remains powered after that cycle.

The controller validates the resolved recipe, proposed outputs, and atomic
commit plan before starting. At completion it revalidates, decrements only the
recipe-defined input quantity, merges compatible output within capacity, and
restores all snapshots if commit fails.

### 2.7 Current Save, Reload, Owner Result, And Removal Behavior

Processing block-entity NBT persists inventory, cycle state, selected
operation, progress, reserved input snapshots, completion flag, active
Execution and domain Effect identities, Scheduler submission flag, frozen
input, expected output, freshness, and Workstation owner-result fields.

Safe pre-effect controller progress may resume from NBT. Malformed active
state stops in `ERROR`; a persisted unresolved committed effect is not
reapplied. Execution and Scheduler persist independently. Full chunk unload,
world reload, abrupt restart, and coordinated Workstation/Execution/Scheduler
recovery are not claimed by current Grinder tests.

Current Grinder and Patty Former Execution references bind dimension and block
position. They do not bind the DG-002A `WorkstationInstanceId`. DG-002A
instance identity already prevents transfer effects from moving to a
replacement endpoint, but it explicitly leaves ordinary processing on the
existing Workstation path. Persistent Machine Runs must close this processing
identity gap before implementation.

The Workstation owner result binds the Execution operation, domain effect,
selected recipe, frozen input, expected output, freshness, output products,
and authoritative tick. Execution stores matching owner-result and terminal
result evidence. The current processing owner result is not a DG-002 endpoint
journal result, and DG-005 does not reinterpret the transfer endpoint journal
as a processing journal.

### 2.8 Current Diagnostics

Current diagnostics are uneven by machine:

- menu data exposes Workstation state, elapsed ticks, total ticks, and failure;
- Patty Former workstation status reports readiness, request presence,
  Execution state, Scheduler state, progress, output blockage, completion, and
  recovery state;
- employee diagnostics report reservation, recipe, Execution identity,
  transient operation state, and failure;
- Execution exposes handler-registry compatibility observations; and
- general Grinder diagnostics emphasize registration, recipes, capability,
  duration, and mappings rather than one complete live operating-state view.

There is no current machine policy, powered state, Machine Run Identity,
RUNNING_EMPTY state, stop request, or wear diagnostic.

### 2.9 Current Architecture Manifest And Persistence Owners

The Java Architecture Manifest currently enforces:

- Execution authorization evidence, single-use live consumption, operation
  lifecycle, handler boundary, owner results, duplicate conflicts, Unknown
  Outcome, persistence, and DG-003 compatibility;
- Scheduler Invocation and Effect identities, retry/effect rules, owner-result
  observation, and non-reentrant runtime authority;
- Workstation state, slot inventory, operation preconditions, private
  authorization issuance, ItemStack mutation, owner-result publication,
  explicit Patty Former operation gating, stack capacity, and endpoint
  identity/recovery;
- transient employee Grinder request and completion observation; and
- the current Execution, Scheduler, Workstation instance, endpoint journal,
  Material Handling, and Workforce persistence surfaces.

The manifest declares no persistent machine operating state, Machine Run
Identity, explicit STOP, continuous-cycle policy, RUNNING_EMPTY, powered
output blockage, restart policy, chunk-unload pause, or processing-run
instance binding. DG-005 does not change the Java manifest.

Relevant current persistence owners are:

| Persisted fact | Current owner and surface |
| --- | --- |
| One-cycle inventory and controller state | Workstation block-entity NBT |
| Execution operations and results | Execution, `execution_operations.json` |
| Scheduler Work and effect observations | Scheduler, `simulation_scheduler.json` |
| Transfer-capable workstation identity | Workstation, `workstation_instances.json` |
| Transfer endpoint effects | Workstation, `workstation_endpoint_journal.json` |
| Employee workstation reservation | Workforce integration, `workstation_reservations.json` |
| Employee operation association | Transient Employee entity state; not persisted |
| Production Run observations | Production, `production_runs.json`; no machine operating authority |

### 2.10 Repository Conflicts And Stale Descriptions

Repository truth contains three relevant documentation inconsistencies:

1. The state section of `WORKSTATION_FRAMEWORK.md` and one paragraph in
   `TECHNICAL_ARCHITECTURE.md` still describe only the Patty Former as using
   explicit-start behavior. Current Grinder code, `GRINDER.md`, `README.md`,
   and IM-030B show that the Grinder is also explicit-request.
2. `SIMULATION_SCHEDULER.md` still contains a stale known-limitation statement
   that no live gameplay Execution handler is registered. Current code,
   Architecture Manifest declarations, and Grinder/Patty Former documents show
   the generic Execution handler and live machine handlers.
3. RFC-0023 Draft 2 remains a non-authorizing proposed full architecture while
   IM-011 implements a narrower accepted schema-1 foundation. The Architecture
   Guide explicitly records this distinction. DG-005 uses the implemented
   foundation as current authority and does not silently adopt the full RFC.

These inconsistencies do not justify runtime changes during DG-005. They must
be corrected with the implementation documentation when a ratified milestone
changes current behavior.

## 3. Governing Constraints

The decision directly applies:

- `AI-0001` deterministic simulation;
- `AI-0002` server authority;
- `AI-0004` immutable identity separation;
- `AI-0011` save compatibility priority;
- `AI-0016` explicit responsibility boundaries;
- `AI-0017` validation before execution;
- `AI-0018` versioned persistence;
- `AI-0021` explicit failure outcomes;
- `AI-0022` authoritative Simulation Clock;
- `AI-0025` singular data ownership;
- `AI-0026` bounded simulation work; and
- `AI-0028` backward-compatible evolution.

DG-004 remains controlling: stack capacity, recipe consumption, recipe output,
duration, and throughput are separate. A stack of ten items never authorizes a
ten-item mutation or ten operations.

DG-003 remains controlling: a changed machine handler contract cannot be
presented as an unchanged schema-1 contract. Persistent-run child operation
linkage requires an additive compatible handler or an explicitly versioned
Execution persistence and migration plan.

## 4. Separate Authoritative Dimensions

DG-005 ratifies four orthogonal dimensions. No implementation may collapse
them into one enum, boolean, or inferred fact.

| Dimension | Singular owner | Meaning |
| --- | --- | --- |
| Run authorization | Execution | Whether one explicit START continues to permit future bounded cycles |
| Machine operating state | Workstation | Whether this exact machine instance is off, powered, empty-running, blocked, stopping, restart-suspended, faulted, or recovery-blocked |
| Processing-cycle state | Workstation plus Execution references | The current one-recipe validation, progress, atomic mutation, and owner result |
| Machine condition | Future separately ratified owner | Wear, damage, maintenance, breakdown, and repair |

Inventory readiness remains a Workstation fact. Scheduler eligibility remains
a Scheduler fact. Employee assignment remains a Workforce fact. Production
Run state remains a Production fact. None of those facts independently creates
Run authorization.

## 5. Machine Operating State Model

Workstation owns a new machine operating-state model only for workstation
types whose explicit operating policy requires it.

| State | Powered meaning | New cycle eligibility |
| --- | --- | --- |
| `OFF` | Not mechanically running | Never |
| `STARTING` | Explicit START is being reconciled; power is not yet assumed | Never |
| `RUNNING` | Powered Run is active; a cycle may be active or eligible | At most one, with active Run proof |
| `RUNNING_EMPTY` | Powered Run is active but no valid recipe input exists | Never until valid input appears under the same Run |
| `OUTPUT_BLOCKED` | Powered Run is active but a complete output commit cannot fit | Never until exact capacity becomes valid |
| `STOPPING` | STOP is committed for this Run; power remains on only through the current safe cycle boundary | Never after the already admitted cycle |
| `RESTART_REQUIRED` | The configured restart policy deliberately suspends a preserved Run; prior powered state remains evidence, not current power | Never until explicit resume of the same Run |
| `FAULTED` | A machine-owned fault blocks cycles; exact power disposition must be explicit in future fault policy | Never |
| `RECOVERY_REQUIRED` | Cross-owner evidence cannot safely prove a normal state | Never |

`STARTING` and `STOPPING` represent committed lifecycle boundaries, not client
animation. A transient client click is not an authoritative state.

The existing `WorkstationState` remains the per-cycle inventory/controller
state. A machine may therefore be:

- operating `OFF` while cycle state is `READY` because input is loaded;
- operating `RUNNING` while cycle state is `PROCESSING`;
- operating `RUNNING_EMPTY` while cycle state is `IDLE`;
- operating `OUTPUT_BLOCKED` while cycle state is `BLOCKED`;
- operating `STOPPING` while the final admitted cycle is still `PROCESSING`;
- operating `RESTART_REQUIRED` while the exact pre-restart operating state is
  retained as suspended evidence.

## 6. Machine Operating Policy

Workstation owns an immutable, versioned operating policy. The policy
describes how an already-authorized Run behaves. It never creates permission.

Ratified schema-1 policy kinds are:

| Policy | Behavior after explicit authority |
| --- | --- |
| `MANUAL_DISCRETE` | One explicit operation authorizes one cycle; no persistent powered Run or RUNNING_EMPTY semantics |
| `POWERED_ONE_CYCLE` | One explicit START creates a powered Run for at most one bounded cycle, followed by deterministic stop |
| `POWERED_CONTINUOUS_EXPLICIT_STOP` | One START permits repeated bounded cycles and powered empty/block states until explicit STOP, fault, or recovery block |
| `POWERED_CONTINUOUS_AUTO_STOP` | Reserved future policy with explicit auto-stop conditions; not authorized by DG-005 implementation |

Policy identity binds policy kind, supported states, cycle-admission rules,
stop-boundary rules, empty-input behavior, output-block behavior, schema, and
configuration identity. Changing policy does not reinterpret an active Run.

## 7. START Authority

START authority remains inside the Execution boundary.

Sources may submit START intent through narrow adapters:

- player server interaction;
- Workforce-owned employee assignment after reservation and arrival checks;
- future Production-owned intent through a separately approved adapter; or
- future automation explicitly authorized by later architecture.

The source does not create a Run. The Workstation owner validates:

- exact `WorkstationInstanceId` and active generation;
- supported operating policy;
- current operating state and revision;
- machine capability and configuration;
- absence of unresolved cycle, stop, fault, or recovery state; and
- source-specific local preconditions without treating material as authority.

It then publishes a typed START proposal and immutable authorization evidence.
Execution alone accepts or rejects that proposal, consumes the live authority,
allocates or returns the active Machine Run Identity, and owns the Run
authorization lifecycle.

Material insertion may establish `READY`. It may never create START evidence,
call the START adapter, allocate a Run, or wake an old stopped Run.

## 8. Persistent Run Authorization And Execution Ownership

DG-005 ratifies a first-class Execution-owned Machine Run record above current
one-cycle operations. It is not one infinitely running Execution transaction
and is not represented only by a Workstation boolean.

The Run lifecycle is:

```text
AUTHORIZED
  -> STOP_REQUESTED -> STOPPED
  -> SUSPENDED_RESTART_REQUIRED -> AUTHORIZED or STOPPED
  -> FAILED
  -> RECOVERY_REQUIRED
```

`STOPPED` and `FAILED` are terminal. `RECOVERY_REQUIRED` cannot authorize new
cycles. An operator-resolution design remains separately gated.

Execution owns:

- Machine Run Identity and run generation;
- active-run uniqueness per Workstation Instance Identity;
- accepted START authorization evidence;
- Run authorization lifecycle and revision;
- accepted STOP target and evidence;
- child-cycle Execution references and sequence association;
- duplicate and conflict classification;
- terminal Run evidence; and
- Execution-owned run persistence and recovery validation.

Workstation owns:

- operating policy;
- machine operating state and revision;
- input and output observations;
- cycle admission proposal under an active Run;
- recipe validation, duration, inventory lock, and atomic commit;
- Workstation owner result; and
- machine operating-state persistence and diagnostics.

The Workstation run coordinator may request the next child cycle only after it
observes matching active Run evidence and terminal evidence for the previous
cycle. Execution validates the parent Run again when accepting and dispatching
the child. This is not a second authorization authority; it is an owner-domain
eligibility decision constrained by Execution permission.

## 9. Machine Run Identity

The canonical identity is named `MachineRunIdentity` to avoid confusion with
the existing Production Run identity.

Ratified canonical form:

```text
butchercraft:machine_run/v1/<lowercase-sha256>
```

Its frozen inputs are:

- schema version;
- World Identity;
- exact DG-002A Workstation Instance Identity;
- Execution-owned positive monotonic machine-run generation;
- accepted Machine START authorization identity;
- operating-policy identity; and
- Execution machine-run Configuration Identity.

The active-run index is keyed by exact Workstation Instance Identity. A retry
of the same START authorization returns the same Run. A later START after a
terminal STOP allocates a later generation and a different Run. A replacement
workstation has a different instance identity and cannot resolve the old key.

Run Identity is distinct from:

- Production Run identity;
- employee operation assignment and reservation identity;
- each child Execution Operation Identity;
- Scheduler Work, Invocation, and Effect identities;
- Workstation domain Effect Identity;
- Workstation owner-result identity; and
- START and STOP effect identities.

## 10. START And STOP Effect Identity

Consequential START and STOP requests require canonical effect identities.

A START effect binds at minimum:

- World Identity;
- Workstation Instance Identity;
- expected Workstation operating revision;
- source owner and source request/correlation identity;
- requested operating-policy identity;
- configuration identity; and
- accepted server action sequence or other deterministic source ordering.

A STOP effect binds at minimum:

- World Identity;
- Workstation Instance Identity;
- exact target Machine Run Identity;
- expected Run and Workstation operating revisions;
- source owner and source request/correlation identity; and
- configuration identity.

Same identity and same content observes the existing result. Same identity and
different content conflicts. A STOP targeted at Run N can never stop later Run
N+1. A source path may not use block position alone as either identity.

## 11. Repeated Bounded Processing Cycles

Every recipe cycle remains distinct and bounded.

For an input count of ten and recipe quantity one:

```text
Run R, cycle sequence 1: input 10 -> 9, output +1
Run R, cycle sequence 2: input  9 -> 8, output +1
...
```

Each sequence has exactly one child Execution Operation Identity. The child
authorization binds:

- parent Machine Run Identity and current Run revision;
- exact Workstation Instance Identity;
- positive monotonic cycle sequence within the Run;
- selected recipe and handler contract identity;
- frozen input and expected output identities;
- source freshness and operating-policy identity; and
- world and configuration identities.

There is at most one nonterminal child cycle per Machine Run. A retry or
recovery of the same cycle reuses the same authorization, Execution Operation
Identity, Scheduler Work Identity, domain Effect Identity, and Workstation
owner-result identity. The next cycle sequence is allocated only after the
prior cycle reaches a proven terminal boundary and the Workstation operating
record publishes that observation.

One cycle validates and commits only the recipe-defined quantity. A future
batch recipe may explicitly define a larger bounded quantity; persistent Run
authorization alone never converts the remaining stack into one giant
mutation.

## 12. Scheduler Role

Scheduler remains timing and dispatch authority for each bounded child
operation. DG-005 does not authorize Scheduler-generated machine loops.

The selected repository-consistent sequence is:

```text
active Run evidence
  -> Workstation admits one child cycle
      -> Execution accepts one bounded child operation
          -> Scheduler dispatches one generic Execution Work item
              -> Workstation owner result
                  -> Execution terminal result
                      -> Workstation reevaluates Run and local conditions
```

Scheduler must validate or receive proof that the controlling Run is still
authorized before a child handler may apply its effect. Scheduler does not
decide that remaining input means continue, does not allocate Run Identity,
does not transition machine power, and does not bypass Workstation recipe or
capacity validation.

The Workstation coordinator may admit at most one child cycle per bounded
server-owner action. Continued operation therefore remains bounded by normal
Scheduler budgets and cannot recurse through same-tick generated work.

## 13. STOP Authority And Safe Boundary

STOP uses the same authority chain as START. Player, employee, future safety,
future Production, or future automation paths may submit a typed STOP intent
only when separately authorized for that source.

Execution accepts STOP against one exact active Run and publishes
`STOP_REQUESTED`. Workstation observes it and publishes `STOPPING`.

The deterministic stop boundary is:

- if no child cycle is active, transition to `OFF` and terminal `STOPPED`;
- if one child cycle is active, allow that already admitted cycle to reach its
  normal proven terminal boundary;
- admit no later child cycle after STOP commit;
- never interrupt a Workstation atomic inventory commit; and
- if the active child becomes `UNKNOWN_OUTCOME`, preserve STOP intent and move
  the machine and Run to `RECOVERY_REQUIRED` rather than guessing OFF.

This decision deliberately does not use mid-effect cancellation. Existing
pre-invocation cancellation remains available for removal or recovery policy,
but ordinary operator STOP completes at the next cycle boundary for simple,
deterministic gameplay.

Repeated STOP is idempotent. It does not roll back a committed recipe or
create a second terminal result.

## 14. Grinder Continuous-Run Policy

The ratified Grinder policy is `POWERED_CONTINUOUS_EXPLICIT_STOP`.

Intended behavior:

1. Beef Trim may be loaded while the Grinder is `OFF`.
2. Loading material alone does not START it.
3. An authorized player or employee explicitly requests START.
4. Execution accepts one Machine Run and Workstation publishes `RUNNING`.
5. The Grinder processes repeated, separate, bounded recipe cycles while the
   Run is authorized, exact input is valid, exact output capacity is valid,
   and no fault or recovery state blocks progress.
6. Every cycle consumes and produces only its recipe-defined quantity.
7. Empty input stops cycle creation but does not stop the Run.
8. Full or incompatible output stops cycle creation and mutation but does not
   stop the Run.
9. Explicit STOP is required unless a future separately ratified safety policy
   says otherwise.
10. Future empty-running wear remains gated.

This policy reduces repetitive clicking without making the Grinder
self-starting, self-stopping, or immune to operator error.

## 15. RUNNING_EMPTY

When no valid input remains after a proven cycle boundary, Workstation
publishes `RUNNING_EMPTY` under the same Machine Run Identity.

In `RUNNING_EMPTY`:

- the machine remains powered;
- no child Execution operation or Scheduler Work is created;
- no inventory mutation occurs;
- inserting valid input does not create START authority;
- the already-active Run makes the new input eligible, so the Workstation may
  admit the next bounded cycle after exact validation; and
- explicit STOP targets the same Run normally.

The operating record stores the state-entry simulation tick, operating
revision, Run Identity, and deterministic accumulated operating-duration
summary. This makes empty-running durably observable without calculating wear
or damage in DG-005.

## 16. Output-Blocked Policy

Output blockage remains part of the same active Run and the machine remains
powered.

Two boundaries are recognized:

- Before child admission, exact output capacity failure publishes
  `OUTPUT_BLOCKED`; no child operation is created.
- If capacity changes after admission, the Workstation atomic commit either
  succeeds completely or returns a proven no-effect blockage. Input remains
  unchanged, no product is lost, and no duplicate output is created.

Clearing the output does not create START authority. It restores cycle
eligibility under the existing active Run. The Workstation may then admit one
new bounded child cycle. A conflicting or uncertain commit moves to
`RECOVERY_REQUIRED`, not ordinary `OUTPUT_BLOCKED`.

## 17. Patty Former Policy

The reusable framework supports the Patty Former without forcing every
workstation to behave identically.

The ratified Patty Former policy is
`POWERED_CONTINUOUS_EXPLICIT_STOP`, because a commercial former plausibly
continues producing discrete patties while compatible material and output
capacity remain available. Its current explicit one-cycle behavior remains
unchanged until a separately authorized and implemented IM-031C.

## 18. Cutting Table Boundary

The Cutting Table remains `MANUAL_DISCRETE`.

It has no persistent machine power state, no Machine Run, no RUNNING_EMPTY,
and no powered output-blocked state. Its current manual/discrete processing
may continue using one explicit or readiness-authorized cycle according to its
own accepted interaction model.

The existence of a reusable machine operating framework does not require
manual tables, packaging surfaces, or every workstation to implement it.

## 19. Player Controls

The clearest future control is an explicit server-authoritative GUI control:

- `START` while `OFF`;
- `STOP` while `RUNNING`, `RUNNING_EMPTY`, or `OUTPUT_BLOCKED`;
- `STOPPING` displayed but not reissued as a conflicting command;
- explicit `RESUME` or `STOP` while restart recovery requires a decision; and
- fault/recovery detail when normal controls are blocked.

Normal right-click should continue opening the GUI. Shift + right-click may be
retained as an optional state-aware shortcut:

```text
OFF -> START
RUNNING or RUNNING_EMPTY or OUTPUT_BLOCKED -> STOP active Run
```

The GUI is the ratified primary control direction because it can show the exact
Run target, powered state, stop-pending state, and recovery reason. DG-005 does
not implement either UI. Until implementation, Shift + right-click continues
to request exactly one operation.

## 20. Employee Operation Implications

Future employee machine operation should become a Run assignment rather than
one item authorization at a time:

```text
employee reserves and reaches exact machine instance
  -> employee explicitly requests START
      -> observes matching Machine Run Identity and RUNNING state
          -> monitors according to later operator-presence policy
              -> explicitly requests STOP
                  -> completes only after OFF or typed recovery outcome
```

Workforce continues to own reservation, arrival, assignment, and operator
intent. It does not own Run authorization, machine power, Scheduler Work,
recipe mutation, or wear.

The future employee assignment should reference, not duplicate:

- Workstation Instance Identity;
- Machine Run Identity;
- START and STOP effect identities;
- current child Execution Operation Identity; and
- observed Workstation and Execution results.

Employee arrival alone remains passive. Assignment completion must not occur
at START if the employee is responsible for STOP. Reservation loss must not
silently turn a machine off or leave a Workforce record pretending to own the
Run.

One employee supervising multiple machines, skills, and unattended risk remain
later gameplay-policy gates. The ratified initial IM-032 policy is
conservative: the starting employee retains the reservation, stays within
operating tolerance, and remains responsible through STOP. IM-032 runtime
changes remain separately gated.

## 21. Persistence Ownership

DG-005 ratifies two new owner-specific persistence surfaces. Names are
architectural targets, not implemented files.

### 21.1 Workstation Machine Operating State

Workstation owns:

```text
<world>/butchercraft/machine_operating_states.json
```

Each versioned record contains at minimum:

- exact Workstation Instance Identity and expanded endpoint key;
- operating-policy and configuration identities;
- current Machine Operating State;
- last proven powered disposition where recovery or fault makes it relevant;
- active Machine Run Identity reference, if any;
- START and STOP effect/result references;
- current child Execution Operation Identity, if any;
- operating revision and monotonic transition sequence;
- state-entry, last-transition, and last-observed simulation ticks;
- deterministic accumulated duration by operating state, without wear math;
- input and output eligibility summaries and their source freshness;
- fault or recovery classification; and
- schema version and integrity digest.

The world-scoped operating record is authoritative. Block-entity NBT stores a
matching projection for loaded interaction and per-cycle processing. Chunk
load order does not allocate Run Identity or overwrite a newer operating
record.

### 21.2 Execution Machine Run State

Execution owns:

```text
<world>/butchercraft/execution_machine_runs.json
```

Each versioned record contains at minimum:

- Machine Run Identity and generation;
- exact Workstation Instance Identity;
- accepted START authorization and result evidence;
- operating-policy and Execution configuration identities;
- Run lifecycle, revision, and simulation ticks;
- accepted STOP effect and target Run Identity, if present;
- next cycle sequence and current child Execution reference;
- ordered child operation/result references without duplicating Workstation
  inventory or owner state;
- restart suspension or terminal evidence;
- typed failure or recovery classification; and
- schema version and integrity digest.

Existing `execution_operations.json` remains the child-cycle owner. Required
parent-Run bindings must be represented through an explicitly versioned
authorization/handler contract. DG-003 compatibility must be satisfied before
any new durable child operation is created in an existing world.

### 21.3 Existing Owners Remain Separate

- Scheduler continues to persist each child Work in
  `simulation_scheduler.json`.
- Workstation block-entity NBT continues to own loaded inventory and current
  atomic cycle projection.
- `workstation_instances.json` continues to own instance generations.
- The transfer endpoint journal remains transfer-specific.
- Workforce reservation and future operator assignment persistence reference
  the Run but do not duplicate it.
- Production may reference Machine Run evidence later but does not own machine
  power or START/STOP.

No cross-file mismatch grants authority. Startup publishes normal mutation
authority only after candidate validation and cross-owner reconciliation.

## 22. Restart Policy B

The owner ratifies Policy B for the initial implementation. Policy A remains a
possible later proposal only after stronger recovery proof.

### Policy A: Automatic Resume

Persist active Run authorization and automatically resume cycles after all
Workstation, Execution, Scheduler, and instance evidence reconciles.

This offers stronger simulation continuity but requires coordinated startup
ordering, exact child-cycle recovery, and proven cross-owner durability that
the current Grinder tests do not claim.

### Policy B: Explicit Resume After Restart

Persist the active Run and exact prior operating state, but publish the Run as
`SUSPENDED_RESTART_REQUIRED` and the machine as `RESTART_REQUIRED` at server
startup. No new child cycle or mutation occurs until an authorized actor
explicitly resumes or stops that same Run.

Explicit resume reactivates the same Machine Run Identity after reconciliation;
it does not allocate a second Run. Explicit STOP terminates that Run. The
record retains whether the machine had been `RUNNING`, `RUNNING_EMPTY`,
`OUTPUT_BLOCKED`, or `STOPPING`.

DG-005 adopts Policy B for the first implementation because:

- current Workstation state is block-entity NBT while Execution and Scheduler
  use independent JSON files;
- current full server-restart and chunk-unload recovery is not accepted test
  coverage;
- current machine Execution references are position-based rather than
  instance-bound; and
- current checkpoint startup recovery does not coordinate Workstation owner
  state with Execution and Scheduler.

Policy A may be proposed later after exact cross-owner recovery is proven.

## 23. Chunk Unload

A loaded chunk is availability, not authority.

When a running machine unloads:

- the Machine Run remains authorized unless STOP, fault, or recovery changes
  it;
- no new child cycle is admitted while the block entity is unavailable;
- no repeated Scheduler polling or unbounded Work generation targets the
  unloaded block;
- an already active pre-effect controller cycle pauses with its exact child
  identity and progress;
- no chunk ticket is created merely because the machine is running; and
- normal chunk load may resume only after instance identity, operating
  revision, Run Identity, and child freshness reconcile.

If a child Scheduler invocation already began, the handler remains one
bounded synchronous invocation. An unavailable or conflicting endpoint before
effect produces a typed no-effect pause/failure; uncertainty after invocation
becomes `RECOVERY_REQUIRED`. The scheduler never force-loads the chunk.

## 24. Workstation Removal And Replacement

Every Machine Run and child authorization binds the DG-002A Workstation
Instance Identity, not only dimension and position.

On removal:

- no new child cycle is admitted;
- a pre-effect child may be cancelled or failed with proven no mutation;
- a committed child result must be observed and reconciled before terminal Run
  classification;
- uncertain effect state becomes `RECOVERY_REQUIRED`;
- the old Run remains bound to the retired instance; and
- a replacement at the same coordinates receives a later generation and no
  inherited Run, STOP target, active cycle, power state, or owner result.

Removal does not transfer authority to dropped item NBT or a structure copy.

## 25. Concurrency And Duplicate Semantics

The Workstation owner serializes operating-state candidates by exact instance
and expected revision. Execution separately enforces one nonterminal Machine
Run per exact instance.

Two concurrent START attempts may observe the same prior state, but only one
candidate may commit that revision and active-run key. The accepted request
receives one Run; the other observes `existing_run` with that same Run
Identity. Safety does not depend on thread timing or two handlers happening not
to overlap.

Rules:

- duplicate same-content START returns the existing START result;
- a second START while `RUNNING`, `RUNNING_EMPTY`, `OUTPUT_BLOCKED`, or
  `STOPPING` returns the existing active Run and creates no child loop;
- conflicting same-identity START fails visibly;
- duplicate same-content STOP returns the existing STOP result;
- multiple STOP requests targeting the same active Run converge on one
  `STOP_REQUESTED` lifecycle without rollback;
- stale STOP targeting an older Run is rejected or reported already terminal;
- START cannot overtake a committed STOP until the prior Run is terminal and
  the Workstation publishes `OFF`; and
- at most one nonterminal child cycle exists per Run.

## 26. Crash And Recovery Matrix

In the table, `R` is the authoritative Machine Run Identity and `C` is the
current child Execution Operation Identity.

| Boundary | Authoritative Run | New cycles | Inventory mutation | Deterministic next action |
| --- | --- | --- | --- | --- |
| A. `OFF` before START | None | No | No | Remain `OFF`; input may be `READY` without authority |
| B. START requested, not committed | No Run unless Execution has matching accepted evidence | No | No | Reconcile START identity; return to `OFF` or observe the one committed `R`; never invent `R` |
| C. Run committed, no cycle scheduled | `R` active | One may be admitted only if loaded, valid, and not restart-suspended | No until child effect | Publish matching operating state, then evaluate one child |
| D. Cycle scheduled, not started | `R` plus one `C` | No second child | No | Dispatch the same `C`, or honor STOP/removal with proven pre-effect terminal handling |
| E. Cycle executing | `R` plus one `C` | No | Only Workstation atomic commit may mutate | Finish bounded invocation; crash uncertainty blocks `R` and `C` as recovery-required |
| F. Cycle owner effect committed | `R` plus `C` and Workstation owner result | No until observed | Do not reapply | Reconcile inventory projection, then publish Execution and Scheduler observation |
| G. Cycle result observed, next not scheduled | `R`; prior `C` terminal | At most one next child if Run and local conditions remain valid | No until next effect | Clear active child reference, update operating state, allocate next sequence only if eligible |
| H. `RUNNING` with remaining input | `R` active | At most one child | One recipe quantity per child only | Continue bounded cycle sequence while loaded and authorized |
| I. `RUNNING_EMPTY` | `R` active | No | No | Remain powered; valid input restores eligibility under `R`; STOP ends `R` |
| J. `OUTPUT_BLOCKED` | `R` active | No | No | Remain powered; exact capacity change restores eligibility; conflict enters recovery |
| K. STOP requested | `R` in `STOP_REQUESTED` | No new child | Existing child only | Publish `STOPPING`; preserve target and stop evidence |
| L. STOP committed, no cycle active | `R` terminal `STOPPED` | No | No | Publish `OFF`; later START receives a new Run generation |
| M. STOP requested while cycle active | `R` plus one `C` | No new child | Existing `C` may commit once | Observe `C` terminal, then publish `STOPPED` and `OFF`; uncertainty blocks recovery |
| N. Machine removed or replaced | Old `R` remains bound to retired instance | No | Only already-proven old-instance effect may reconcile | Fail/cancel pre-effect child, observe committed result, or enter recovery; replacement gets no Run |
| O. Chunk unloaded | `R` remains authorized but endpoint unavailable | No while unloaded | No new mutation | Pause without force-load or polling; reconcile exact instance and child on normal load |
| P. Server restart under ratified Policy B | Same `R`, suspended with exact prior state | No | No | Publish `RESTART_REQUIRED` after reconciliation; require explicit resume of `R` or STOP of `R` |
| Q. `UNKNOWN_OUTCOME` or evidence conflict | Same `R` and `C` retained in recovery evidence | No | Forbidden | Publish `RECOVERY_REQUIRED`; require future operator recovery authority |

Recovery never changes Run Identity merely to escape a conflict. It never
creates a replacement child operation for an unresolved cycle, consumes input
again, or infers successful output from elapsed time.

## 27. Diagnostics

Future diagnostics should expose one coherent read-only view containing at
least:

- Workstation Instance Identity and generation;
- machine operating policy and policy identity;
- operating state and powered disposition;
- Machine Run Identity, lifecycle, revision, and source type;
- active child Execution Operation Identity and cycle sequence;
- child Scheduler Work, Invocation, and Effect identities when present;
- Workstation per-cycle state and progress;
- exact input eligibility and output capacity status;
- operator or assignment reference when present;
- STOP requested and target Run Identity;
- chunk availability or paused status;
- fault or recovery classification and required operator action; and
- future machine condition and wear only after separately implemented.

Diagnostics observe owners. They do not issue START, STOP, resume, recovery,
or inventory mutation authority.

## 28. Architecture Manifest Implications

After separate implementation authorization, an implementation may propose
Architecture Manifest concepts for:

- Workstation-owned persistent machine operating state;
- Workstation-owned versioned machine operating policy;
- Execution-owned Machine Run Identity and active-run uniqueness;
- explicit START and exact-run-targeted STOP;
- parent Run binding for bounded child Execution operations;
- one nonterminal child cycle per Run;
- powered `RUNNING_EMPTY` and `OUTPUT_BLOCKED` states;
- selected restart policy;
- chunk-unload pause without force loading;
- Workstation Instance Identity-bound Run authority;
- durable operating-state observation for future condition logic; and
- future condition/wear implementation remaining gated.

Every new concept must remain `DECLARED_IMPLEMENTATION_GATED` until code,
persistence, migration, recovery, diagnostics, and tests make it mechanically
true. DG-005 ratification work must not mark any concept implemented.

## 29. Machine Condition, Wear, And Maintenance Boundary

DG-005 does not implement or fully assign a machine-condition subsystem.

The operating-state owner must publish durable facts that a future condition
system can consume:

- exact Workstation Instance Identity;
- Machine Run Identity;
- operating state and transition revision;
- state entry and exit simulation ticks;
- accumulated `RUNNING_EMPTY`, `OUTPUT_BLOCKED`, and powered runtime duration;
- recipe/load identity where an actual cycle occurred; and
- operator reference only as an observed input.

Future condition inputs may include empty-running duration, excessive runtime,
jam or blockage, operator skill, maintenance state, and recipe/load
characteristics. Those inputs do not change START authority or retroactively
change a committed recipe.

A separate DG-006 Machine Condition, Wear, Damage, And Maintenance ADR is
required before implementation. It should decide the singular owner,
condition identity and persistence, maintenance/repair authority, failure
effects, operator reconciliation, and whether any automatic safety stop is
permitted. DG-005 reserves no automatic damage or stop behavior.

## 30. Realism And Gameplay Balance

Persistent Run authorization removes repetitive per-item clicking while
preserving meaningful operator responsibility.

- Machines do not self-start from inventory changes.
- Players and employees do not authorize every pound or item separately.
- Empty input and blocked output do not make powered machinery perfectly
  self-managing.
- Explicit STOP remains meaningful.
- Future empty-running consequences have a durable observation boundary.
- Configuration and future safety systems may later reduce micromanagement
  through explicit, ratified policy rather than hidden automation.

## 31. Ratified Milestone Sequence

No completed milestone is renumbered. Because the previously named IM-031 has
not begun and has no section in `MILESTONES.md`, DG-005 ratifies the
future sequence as follows:

1. **DG-005 - Persistent Machine Operating State And Continuous Processing.**
   Ratify architecture only; no runtime change.
2. **IM-031A - Machine Operating Run-State And START/STOP Foundation.** Add
   instance-bound Workstation operating state, Machine Run identity and
   Execution authorization lifecycle, versioned persistence, idempotent
   START/STOP, diagnostics, restart suspension, and recovery foundations. Do
   not activate continuous recipes.
3. **IM-031B - Grinder Continuous Operation And Empty-Running State.** Activate
   `POWERED_CONTINUOUS_EXPLICIT_STOP` only for the Grinder, repeated bounded
   cycles, `RUNNING_EMPTY`, powered output blockage, and player controls. Do
   not implement wear.
4. **IM-031C - Patty Former Continuous Operation.** Activate the separately
   ratified Patty Former policy after Grinder acceptance. Do not add employee
   operation by implication.
5. **IM-032 - Employee Machine START/STOP Operation.** Replace the one-cycle
   employee concept with explicit Run assignment, monitoring, STOP
   responsibility, persistence/recovery, and the separately ratified operator
   presence policy.
6. **DG-006 - Machine Condition, Wear, Damage, And Maintenance.** Architecture
   gate before any wear, damage, breakdown, maintenance, or repair runtime.
7. **Later condition implementation milestone.** Number only after DG-006 is
   ratified and current roadmap state is rechecked.

IM-031A must explicitly address DG-003 compatibility. It may not change the
existing Grinder or Patty Former handler contract in place and call that
additive compatibility. It must also preserve current one-cycle behavior until
IM-031B or IM-031C separately activates a machine policy.

## 32. Alternatives Rejected By This Decision

- **Input starts the machine:** rejected because material presence is not
  operation authority.
- **One permanent Workstation boolean authorizes everything:** rejected because
  it lacks Execution lifecycle, identity, stop targeting, and recovery proof.
- **One infinitely running Execution operation performs every item:** rejected
  because the effect is unbounded and owner results cannot identify atomic
  recipe mutations.
- **One giant stack mutation per START:** rejected because stack capacity is
  not recipe quantity or throughput.
- **Scheduler generates the next cycle from remaining input:** rejected because
  Scheduler would invent continued authority and own machine policy.
- **A new machine authority outside Execution:** rejected because it creates a
  competing consequential operation authority.
- **Reuse Production Run identity as Machine Run identity:** rejected because
  Production intent and physical machine power are different owners and
  lifecycles.
- **Position-only Run binding:** rejected because a replacement could inherit
  stale authority.
- **STOP interrupts an atomic commit:** rejected because it creates partial or
  unprovable mutation boundaries.
- **Output blockage automatically powers off:** rejected for the ratified
  Grinder and Patty Former continuous policies because blockage and STOP
  authority are separate.
- **Cutting Table receives RUNNING_EMPTY:** rejected because it is a manual
  discrete workstation, not continuously powered machinery.
- **Implement wear inside DG-005:** rejected because condition, damage,
  maintenance, and repair need separate authority and persistence decisions.
- **Automatic restart now:** rejected as the initial recommendation because
  current cross-owner restart recovery is not proven.

## 33. Consequences

Positive consequences:

- continuous machines reduce repetitive interaction;
- explicit START and STOP remain traceable and server-authoritative;
- each recipe mutation stays atomic, bounded, and individually observable;
- Run retry, cycle retry, employee assignment, Scheduler effect, and owner
  result identities remain distinct;
- empty-running and output blockage become durable operating facts;
- replacement and stale STOP safety bind to exact machine instances and Runs;
- Scheduler remains focused on timing and dispatch; and
- future wear has a stable observation boundary.

Costs and risks:

- new Workstation and Execution persistence requires migration and startup
  ordering;
- child operations need explicit parent binding under DG-003 compatibility;
- cross-owner recovery must block rather than guess after abrupt failure;
- player and employee controls need Run-targeted status and STOP feedback;
- active Run records and duration summaries require bounded retention; and
- automatic restart remains deferred under the ratified initial policy.

## 34. Ratification Notes

Owner ratification approved all 27 decisions below:

1. Persistent machine operating state is introduced separately from existing
   Workstation recipe-cycle state.
2. Explicit START authorization is separate from every processing cycle and
   material presence never authorizes START.
3. Execution owns canonical `MachineRunIdentity`, Run lifecycle, and one active
   Run per exact Workstation Instance Identity.
4. One Machine Run may authorize repeated distinct bounded child Execution
   operations, with at most one nonterminal child at a time.
5. Explicit STOP targets one exact Machine Run and takes effect at the next
   deterministic cycle boundary without interrupting atomic commit.
6. The Grinder uses `POWERED_CONTINUOUS_EXPLICIT_STOP` as its target operating
   policy.
7. Empty Grinder input produces powered `RUNNING_EMPTY`, not automatic OFF.
8. Full or incompatible output produces a no-mutation `OUTPUT_BLOCKED` state
   within the same Run.
9. `OUTPUT_BLOCKED` keeps the Grinder and Patty Former powered until
   STOP, fault, or recovery changes the Run.
10. Workstation owns explicit operating policies that describe already
    authorized behavior but never create authority.
11. The Patty Former uses `POWERED_CONTINUOUS_EXPLICIT_STOP` as its target
    operating policy; one-cycle-per-start is not its target policy.
12. The Cutting Table remains `MANUAL_DISCRETE` and excluded from Machine Run,
    RUNNING_EMPTY, and powered blockage semantics.
13. Normal right-click remains GUI access; explicit GUI START/STOP is primary,
    with optional state-aware Shift + right-click START/STOP shortcut.
14. Future employee operation becomes a Run START/monitor/STOP assignment; the
    initial policy retains operator reservation and presence through STOP,
    while multi-machine supervision and unattended operation remain gated.
15. Execution remains the sole consequential operation and Run authorization
    lifecycle owner.
16. Scheduler remains bounded timing/dispatch authority for child operations
    and does not generate continuous loops or infer authority from input.
17. Workstation owns operating policy/state, recipe validation, capacity,
    atomic ItemStack mutation, and owner results.
18. Workstation operating state and Execution Machine Run state persist in
    separate versioned owner files with cross-owner references, not duplicated
    authority.
19. Initial restart policy is Policy B: preserve and suspend the
    same Run, then require explicit resume or STOP after reconciliation.
20. Chunk unload preserves Run authorization but pauses cycles without force
    loading or unbounded Scheduler polling.
21. Run and child authority bind DG-002A Workstation Instance Identity; a
    replacement never inherits them.
22. Duplicate START and STOP are idempotent, second START observes the active
    Run, and stale STOP cannot affect a later Run.
23. Concurrent operators are serialized by Workstation revision plus
    Execution active-run uniqueness, so at most one Run commits.
24. The crash/recovery matrix in Section 26 is the required safety contract.
25. Workstation operating records durably expose RUNNING_EMPTY and other state
    durations without implementing wear or damage.
26. Machine condition, wear, damage, maintenance, breakdown, and repair require
    a separate DG-006 architecture gate before runtime implementation.
27. The future sequence becomes DG-005, IM-031A, IM-031B, IM-031C, IM-032,
    DG-006, then separately numbered condition implementation, without
    renumbering completed milestones.

Ratification also confirms that capacity remains separate from throughput;
continuous operation is a sequence of separately identified bounded recipe
cycles, never one stack-sized atomic mutation. Machine Run authorization
remains distinct from Material Handling custody, Workforce assignment,
Workstation inventory authority, child-cycle identity, and Scheduler Effect
Identity. `RUNNING_EMPTY` creates durable operating-state evidence but grants
no wear authority. `OUTPUT_BLOCKED` consumes no input and does not imply OFF.

This ratification establishes architectural direction only. Machine Run and
START/STOP runtime, continuous Grinder or Patty Former processing,
`RUNNING_EMPTY`, employee machine START/STOP, automatic restart, forced chunk
loading, condition or wear, maintenance, Production machine control,
unattended operation, multi-machine supervision, IM-031A and all later
implementation, and Architecture Manifest implementation claims remain gated
until separately authorized and mechanically true.

## Implementation Status

- IM-031A implements the generic Machine Run, operating-state, persistence,
  restart Policy B, endpoint, and START/STOP foundation.
- IM-031B activates `POWERED_CONTINUOUS_EXPLICIT_STOP` for the Grinder only,
  including GUI and Shift-use controls, repeated bounded children,
  `RUNNING_EMPTY`, `OUTPUT_BLOCKED`, and explicit restart RESUME/STOP.
- IM-031C activates the same policy for the Patty Former through shared
  machine-neutral coordination and presentation while retaining Patty Former
  recipe, inventory-effect, and owner-result authority.
- Employee Machine Run control, machine condition/wear, and Production machine
  control remain separately gated.
