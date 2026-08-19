# Machine Run-State / START-STOP Foundation

Status: IM-031A implemented foundation; IM-031B Grinder policy activated.

This note records the generic runtime authorized by DG-005 and its first live
policy activation. IM-031B activates continuous player control for the Grinder
only. Patty Former continuous behavior and employee Machine Run control remain
gated.

## Singular Owners

- Execution owns Machine Run identity, instance-local generation, START/STOP
  acceptance, active uniqueness, lifecycle, child authorization, and Run
  persistence.
- Workstation owns machine operating policy, current operating state,
  simulation-tick duration evidence, endpoint availability, and operating-state
  persistence.
- Scheduler continues to own bounded dispatch and invocation/effect identity.
- The integration coordinator sequences owner publications but persists no
  state and grants no authority independently.

Machine Run and child Execution operation identities are distinct. Production
Run, employee assignment, Scheduler Work/effect, and Workstation owner-result
identities remain separate.

## Persistence

Execution persists schema-1 Machine Runs at:

`<world>/butchercraft/execution_machine_runs.json`

Workstation persists schema-1 machine operating state at:

`<world>/butchercraft/machine_operating_states.json`

Both files use strict durable replacement and semantic read-back. Legacy
absence creates an empty owner registry. Malformed, interrupted, unsupported,
or identity/configuration-mismatched state fails visibly.

## Publication

START publication is ordered:

1. Workstation durably publishes `STARTING`, the immutable START authorization
   identity, and exact local policy/revision context.
2. Execution durably accepts one canonical Run and advances the monotonic
   instance-local generation allocator.
3. Workstation durably binds that exact Run and publishes `RUNNING`.

Repeated source request identity observes the same Run. A different START
against an active instance returns a typed conflict or already-running result.

STOP publication targets an exact Run:

1. Workstation validates the exact state and issues immutable STOP evidence.
2. Execution durably commits `STOP_REQUESTED`, which closes child admission.
3. Workstation durably publishes `STOPPING`.
4. A prepared, non-invoked child may use existing safe cancellation. An invoked
   child must reach a proven terminal result.
5. Workstation publishes `OFF`, then Execution publishes `STOPPED`.

A STOP for a historical Run cannot affect a later generation.

## Child Admission

An active Run may prepare one child authorization bound to the exact Run,
Workstation instance, monotonic child sequence, policy, operation type,
freshness, and configuration. Execution must durably accept the child before
Workstation binds its active-child observation. A second nonterminal child is
rejected deterministically. Terminal observation clears the child before a
later sequence may be prepared.

The generic IM-031A owners never invent follow-on work. IM-031B's Grinder
integration re-evaluates the exact instance, Run, input, output capacity, and
recovery state after each proven terminal child before asking Execution to
admit the next bounded child. Scheduler still dispatches one child only; no
inventory loop or stack-sized operation exists.

## Live Grinder Policy

The Grinder uses `POWERED_CONTINUOUS_EXPLICIT_STOP`.

- Normal right-click opens the inventory in every ordinary operating state.
- GUI START creates or observes one exact-instance Machine Run. Shift +
  right-click while `OFF` is the same START shortcut.
- GUI STOP targets the exact active Run and closes later child admission.
  Shift + right-click while active is the same STOP shortcut.
- Valid input permits repeated separately identified 60-tick recipe children,
  with at most one nonterminal child.
- Empty or ordinarily invalid input publishes powered `RUNNING_EMPTY`. Adding
  compatible input later resumes the same Run; insertion grants no START.
- Full or incompatible output publishes `OUTPUT_BLOCKED` without consuming
  input or generating repeated failed Scheduler work. Restored capacity resumes
  the same Run.
- Restart Policy B exposes GUI RESUME/STOP. RESUME preserves the exact Run and
  generation; no automatic restart occurs.
- Identical empty/blocked observations are not durably republished each tick.

## Restart And Endpoints

Startup reconciliation follows World Identity, Workstation instance/endpoint,
Workstation operating state, generic Execution, Machine Runs, Scheduler/child
evidence, then cross-owner reconciliation. An accepted active Run keeps its
identity and generation but enters `SUSPENDED_RESTART_REQUIRED`; Workstation
publishes `RESTART_REQUIRED`. Resume or STOP must target that exact Run.

An unavailable chunk preserves Run authorization but blocks child admission.
No chunk is force-loaded. Eligibility returns only after the exact Workstation
instance reconciles. Retired, replaced, or identity-conflicting endpoints enter
explicit recovery and cannot inherit the old Run.

## Diagnostics

Read-only diagnostics expose Workstation Instance Identity, Run identity and
lifecycle, operating state, active child operation, next child sequence, owner
revisions, endpoint availability, and recovery detail. They do not expose
mutation authority.

## Gated Behavior

- Continuous Patty Former cycling.
- Employee machine START/STOP or employee-owned persistent Runs.
- Automatic restart or forced chunk loading.
- Wear, damage, jams, maintenance, or DG-006 behavior.
- Production machine control or public extension API.
