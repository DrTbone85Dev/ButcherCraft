# Machine Run-State / START-STOP Foundation

Status: IM-031A implemented foundation; continuous machine gameplay gated.

This note records the generic runtime authorized by DG-005. It does not activate
continuous Grinder or Patty Former processing and does not change existing
player or employee operation controls.

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

IM-031A never submits Scheduler Work for these generic children and never
creates a follow-on child automatically.

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

- Continuous Grinder and Patty Former cycling.
- `RUNNING_EMPTY` or `OUTPUT_BLOCKED` live machine behavior.
- Player GUI or shift-use START/STOP controls.
- Employee machine START/STOP.
- Automatic restart or forced chunk loading.
- Wear, damage, jams, maintenance, or DG-006 behavior.
- Production machine control or public extension API.
