# ADR-DG-005A: Role-Aware Workstation Reservations And Compatible Endpoint Access

Status: RATIFIED ARCHITECTURAL DIRECTION - IM-032A IMPLEMENTED; PRODUCT OWNER ACCEPTANCE PENDING; IM-032B GATED

Decision identifier: DG-005A

Package: Workforce Workstation Reservation Compatibility

Authority: Owner-ratified architectural direction. This document narrowly
amends DG-002 reservation exclusivity and clarifies DG-005 employee operator
reservation retention. It authorizes the role-aware compatibility, ownership,
identity, persistence-evolution, recovery, and milestone direction defined
here. It does not authorize Java, persistence migration, checkpoint changes,
commands, tests, gameplay, IM-032A, IM-032B, or resumed IM-032 runtime
implementation. Every implementation milestone remains separately gated.

Canonical platform references:

- [`CONSTITUTION.md`](../../CONSTITUTION.md)
- [`Core Principles`](../../CORE_PRINCIPLES.md)
- [`Project Rules`](../../PROJECT_RULES.md)
- [`Technical Architecture`](../../TECHNICAL_ARCHITECTURE.md)
- [`BCSE Architecture Guide`](../BCSE_ARCHITECTURE_GUIDE.md)
- [`Architecture Validation Framework`](../ARCHITECTURE_VALIDATION_FRAMEWORK.md)
- [`Platform Canonicalization Addendum`](ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md)
- [`DG-002 Material Handling Custody And Recovery`](ADR-PROPOSED-MATERIAL-HANDLING-CUSTODY-AND-RECOVERY.md)
- [`DG-002A Workstation Endpoint Durability And Instance Identity`](ADR-PROPOSED-WORKSTATION-ENDPOINT-DURABILITY-AND-INSTANCE-IDENTITY.md)
- [`DG-005 Persistent Machine Operating State And Continuous Processing`](ADR-PROPOSED-PERSISTENT-MACHINE-OPERATING-STATE-AND-CONTINUOUS-PROCESSING.md)
- [`Workforce Framework`](../WORKFORCE_FRAMEWORK.md)
- [`Material Handling`](../MATERIAL_HANDLING.md)
- [`Workstation Framework`](../WORKSTATION_FRAMEWORK.md)

Canonical platform vocabulary, identity categories, Publication, Recovery,
Replay, Unknown Outcome, Recovery-Blocked State, Operator Authority, World
Identity, and Platform Determinism Manifest remain defined by the Platform
Canonicalization Addendum. This decision does not redefine them.

## 1. Decision In Plain Language

One employee must remain responsible for an employee-controlled continuous
machine until that Machine Run reaches a safe STOP. Another employee must also
be able to perform one explicitly assigned Material Handling interaction at a
compatible endpoint without becoming a second machine operator.

The existing single reservation authority therefore becomes role-aware. It may
hold one exclusive `MACHINE_OPERATOR` reservation and one narrowly scoped,
compatible `MATERIAL_HANDLER` access reservation for the same exact Workstation
Instance. The two roles grant different eligibility and never merge authority.

Multiple machine operators remain forbidden. Arbitrary concurrent handlers
remain forbidden. A reservation never grants inventory mutation, Material
Handling custody, Machine Run authority, Scheduler authority, or Execution
authority.

## 2. Proven Repository Conflict

The ratified architecture currently requires all of the following:

1. DG-005 requires the employee machine operator to retain responsibility and
   the Workstation reservation through safe STOP.
2. DG-002 schema 1 requires one Workstation reservation per employee and
   rejects simultaneous Workstation reservations.
3. Material Handling requires its assigned employee to acquire the destination
   reservation before deposit and the source reservation before withdrawal.
4. The current
   [`WorkstationReservationManager`](../../src/main/java/com/butchercraft/workstation/reservation/WorkstationReservationManager.java)
   indexes one active record by Workstation and one by employee and rejects a
   second active record for either identity.
5. The current schema-1
   [`WorkstationReservationRecord`](../../src/main/java/com/butchercraft/workstation/reservation/WorkstationReservationRecord.java)
   contains no role, assignment reference, transfer reference, endpoint scope,
   or exact Workstation Instance generation.

Those rules prevent the following required IM-032 interactions:

```text
Employee A retains MACHINE_OPERATOR responsibility
    + machine is RUNNING_EMPTY
    + Employee B has an explicit compatible input delivery
    -> current reservation exclusivity rejects Employee B
```

```text
Employee A retains MACHINE_OPERATOR responsibility
    + machine is OUTPUT_BLOCKED
    + Employee B has an explicit compatible output transfer
    -> current reservation exclusivity rejects Employee B
```

Removing the operator reservation would violate DG-005. Bypassing reservation
for Material Handling would violate DG-002 and the current employee navigation
contract. Giving Material Handling machine authority would violate singular
ownership. An architectural amendment is therefore required before IM-032.

## 3. Governing Constraints

This decision is governed by:

- `AI-0001` Deterministic Simulation;
- `AI-0002` Server Authority;
- `AI-0011` Save Compatibility Priority;
- `AI-0016` Explicit Responsibility Boundaries;
- `AI-0017` Validation Before Execution;
- `AI-0018` Versioned Persistence;
- `AI-0019` Formal Invariant Change Control;
- `AI-0020` Stable Identity Contracts;
- `AI-0021` Explicit Failure Outcomes;
- `AI-0025` Singular Data Ownership;
- `AI-0026` Bounded Simulation Work;
- `AI-0027` Tests Are Part Of The Contract; and
- `AI-0028` Backward-Compatible Evolution.

The amendment must also preserve:

- one exact Workstation Instance Identity per physical machine instance;
- one Machine Run authority in Execution;
- one endpoint mutation authority in Workstation;
- one transfer lifecycle and in-transit custody authority in Material Handling;
- one employee assignment authority in Workforce;
- one reservation authority;
- no automatic workstation search;
- no force-loading; and
- no inference of authority from inventory contents, proximity, animation, or
  machine presentation state.

## 4. Decision

Evolve the existing Workstation Reservation authority from one generic
exclusive employee/Workstation record into one role-aware reservation set.

The initial active roles are:

- `MACHINE_OPERATOR`; and
- `MATERIAL_HANDLER`.

One `MACHINE_OPERATOR` may coexist with one compatible `MATERIAL_HANDLER` for
the same exact Workstation Instance. Compatibility is admitted only when the
handler record is bound to one valid, nonterminal Material Handling transfer
and one exact endpoint interaction.

The initial conflict policy remains deliberately conservative:

- at most one active `MACHINE_OPERATOR` per Workstation Instance;
- at most one active `MATERIAL_HANDLER` per Workstation Instance;
- at most one active Workstation reservation per employee; and
- no role compatibility across different Workstation Instances.

The handler grant is endpoint-scoped even though the initial handler conflict
domain is the complete Workstation Instance. Independent concurrent endpoint
domains are future work and are not authorized by this decision.

## 5. Singular Reservation Authority

The existing Workstation Reservation authority remains singular. It is a
Workforce-owned authority boundary because it governs employee access,
presence, and responsibility. Its current `WorkstationReservationManager` and
`WorkstationReservationService` implementation names and package locations do
not transfer reservation ownership to Workstation inventory or endpoint
authority.

The reservation authority owns:

- Workstation Reservation Identity;
- role-aware compatibility validation;
- active reservation lifecycle;
- employee exclusivity;
- Workstation conflict-domain exclusivity;
- arrival and operating-position evidence;
- release and invalidation evidence;
- role-aware reservation persistence; and
- its owner-native checkpoint snapshot.

It does not own:

- employee assignment policy outside the reservation request;
- Material Handling transfer lifecycle or custody;
- Workstation Instance allocation;
- Workstation endpoint mutation;
- Machine Run lifecycle;
- Scheduler dispatch; or
- Execution operations.

No `MaterialHandlingReservationManager`, machine-specific reservation manager,
or second reservation persistence owner may compete for the same access state.

## 6. Reservation Roles

### 6.1 MACHINE_OPERATOR

`MACHINE_OPERATOR` grants:

- exclusive employee operating responsibility for one exact Workstation
  Instance;
- eligibility to request START and STOP through the canonical Machine Run
  integration boundary;
- eligibility to request Policy B RESUME where DG-005 and the authorized
  implementation permit it;
- responsibility retention through an active Run and safe STOP; and
- an assignment binding that can be observed by diagnostics and recovery.

It does not grant:

- direct inventory mutation;
- Material Handling custody;
- endpoint journal mutation;
- Scheduler child authority;
- private Execution authority; or
- a second Machine Run authority.

Execution remains the sole owner of Machine Run identity, START, STOP, RESUME,
lifecycle, and bounded child admission. Workstation remains the sole owner of
operating policy, operating state, recipe validation, inventory mutation, and
owner results. The operator reservation is eligibility and responsibility
evidence only.

### 6.2 MATERIAL_HANDLER

`MATERIAL_HANDLER` grants temporary employee access for one exact Material
Handling endpoint interaction. It may establish:

- the employee's right to approach the bound endpoint;
- arrival and presence at the endpoint;
- eligibility for Material Handling to request the already authorized
  Workstation endpoint effect; and
- the access evidence needed by cancellation and recovery.

It does not grant:

- START, STOP, or RESUME;
- Machine Run ownership or operating responsibility;
- ItemStack custody;
- direct slot mutation;
- unrelated endpoint access; or
- any Workstation mutation outside the bound transfer protocol.

Material Handling remains the sole transfer lifecycle and in-transit custody
authority. Workstation remains the sole endpoint mutation authority. The
handler reservation is access eligibility, not evidence that an endpoint
mutation succeeded.

## 7. Reservation Identity And Binding

Every active role record has one stable Workstation Reservation Identity under
the Platform Identity Model. The exact textual encoding and digest algorithm
remain implementation policy, but the identity binding must include:

- World Identity;
- Employee Identity;
- exact DG-002A Workstation Instance Identity;
- reservation role;
- owning Workforce assignment reference;
- transfer reference for `MATERIAL_HANDLER`;
- source or destination endpoint purpose;
- endpoint direction;
- relevant slot or endpoint identity when exposed by the Workstation endpoint
  contract;
- reservation schema; and
- reservation configuration identity.

`MACHINE_OPERATOR` binds the exact future machine-operation assignment. A
Machine Run reference may be added as observed runtime evidence after START,
but it does not replace the reservation or assignment identity.

`MATERIAL_HANDLER` binds exactly one Transfer Operation Identity and exactly
one source, destination, or source-return endpoint purpose. Reusing the same
reservation identity with different canonical content is an explicit conflict.

The same logical request with the same identity and canonical content observes
the existing authoritative record. It does not create a duplicate.

## 8. Compatibility Matrix

| Existing active role | Requested role | Initial result |
| --- | --- | --- |
| None | `MACHINE_OPERATOR` | Allow when assignment and Workstation Instance are valid |
| None | `MATERIAL_HANDLER` | Allow when transfer and endpoint scope are valid |
| `MACHINE_OPERATOR` | Same logical `MACHINE_OPERATOR` | Idempotent observation |
| `MACHINE_OPERATOR` | Different `MACHINE_OPERATOR` | Reject `operator_conflict` |
| `MACHINE_OPERATOR` | Compatible `MATERIAL_HANDLER` | Allow compatible coexistence |
| `MACHINE_OPERATOR` | Invalid or unrelated `MATERIAL_HANDLER` | Reject `endpoint_access_denied` |
| `MATERIAL_HANDLER` | Compatible `MACHINE_OPERATOR` | Allow compatible coexistence |
| `MATERIAL_HANDLER` | Same logical `MATERIAL_HANDLER` | Idempotent observation |
| `MATERIAL_HANDLER` | Any different active handler for the instance | Reject `handler_conflict` |
| Any active role | Same employee holding another active reservation | Reject `employee_reservation_conflict` |
| Any active role | Replacement Workstation at the same coordinates | Reject `stale_workstation_instance` |
| Migration-only `LEGACY_EXCLUSIVE` | Any new role | Reject until explicit release or proven migration |

Compatibility is symmetric for admission. Lifecycle consequences are not
symmetric: operator release follows Machine Run termination rules, while
handler release follows Material Handling assignment and transfer evidence.

## 9. Endpoint Scope And Safety

A handler reservation binds one exact endpoint interaction. It never grants
whole-machine mutation authority.

The initial handler conflict domain is the complete Workstation Instance. This
coarse conflict rule prevents concurrent handlers until the repository has a
separately ratified endpoint-topology model. The grant itself still records the
exact source, destination, or source-return purpose and relevant endpoint.

Reservation compatibility does not imply endpoint mutation compatibility.
After admission, Workstation may still reject or defer an effect because of:

- incompatible slot content;
- insufficient capacity;
- an active owner effect;
- stale Freshness Identity;
- unresolved endpoint journal state;
- replacement identity;
- unavailable chunk; or
- explicit recovery state.

The reservation authority records access eligibility only. Material Handling
and Workstation publish the authoritative transfer and endpoint outcomes.

## 10. Continuous Machine Interaction

A compatible handler may coexist with an operator while the machine is in
`RUNNING_EMPTY`, `RUNNING`, `OUTPUT_BLOCKED`, or `STOPPING`, provided the exact
endpoint owner accepts the requested effect in that state.

### 10.1 RUNNING_EMPTY Delivery

```text
Employee A retains MACHINE_OPERATOR
Grinder remains in one existing RUNNING_EMPTY Machine Run
Employee B receives one destination MATERIAL_HANDLER grant
Material Handling requests the exact Workstation-owned deposit
Workstation proves the deposit
Material Handling completes the transfer
Employee B's handler grant releases
Employee A retains MACHINE_OPERATOR
the same Machine Run becomes eligible for another bounded child
```

The delivery creates no START and no new Machine Run.

### 10.2 OUTPUT_BLOCKED Service

When an explicit supported Material Handling transfer targets a blocked output,
another employee may receive one source `MATERIAL_HANDLER` grant and request
the exact Workstation-owned withdrawal. The operator remains responsible for
the machine. Clearing output does not require a STOP/START cycle and does not
grant the handler any machine control.

If no supported explicit Material Handling route exists, the reservation model
does not invent one.

## 11. Player Interaction

Player authority remains unchanged. This decision does not require a Workforce
reservation for existing player interaction and does not alter player START,
STOP, GUI, inventory, or endpoint semantics.

Later IM-032 behavior may permit an authorized player STOP to terminate an
employee-controlled Run under DG-005. That rule does not make the player a
Workforce reservation holder.

## 12. Assignment Integration

The future Workforce machine-operation assignment owns the employee's
machine-operation intent and observes `MACHINE_OPERATOR` reservation state.
The existing Workforce Material Handling assignment owns employee transfer
intent and observes `MATERIAL_HANDLER` reservation state.

The singular reservation authority owns both grants. Neither assignment owns
the other's lifecycle, and neither record contains both responsibilities.

One employee remains limited to one active Workstation reservation at a time.
An employee may perform different roles sequentially after the prior grant is
released. Concurrent multi-role tending by one employee remains future work.

## 13. Reservation Lifecycle

Role and lifecycle are separate dimensions. Current reservation lifecycle
concepts remain applicable:

- requested;
- reserved;
- employee en route;
- employee arrived;
- released; and
- invalidated.

Role compatibility is evaluated when a candidate becomes active and whenever
recovery reconstructs the active set. Lifecycle transition does not change a
reservation's role, assignment binding, transfer binding, endpoint scope, or
Workstation Instance Identity.

Changing any binding requires a new reservation identity. It is not an update
to an existing consequential grant.

## 14. Persistence And Schema Evolution

Role-aware reservations continue to persist at:

```text
<world>/butchercraft/workstation_reservations.json
```

The file remains owned by the singular Workforce Workstation Reservation
authority. Checkpoint Recovery may capture and restore its frozen owner-native
snapshot but does not parse, migrate, grant, release, or reinterpret records.

Future implementation requires a new explicit persistence schema. It must
persist at least:

- reservation schema and owner revision;
- Workstation Reservation Identity;
- exact Workstation Instance Identity;
- employee identity;
- role;
- lifecycle;
- assignment reference;
- transfer reference where applicable;
- endpoint purpose, direction, and scope;
- conflict domain;
- created and optional deterministic expiration tick;
- operating-position evidence;
- release or invalidation evidence;
- configuration identity; and
- canonical ordering metadata.

Unsupported newer schemas fail visibly. Malformed role, missing required
binding, duplicate identity, incompatible active set, and stale Workstation
Instance references are not silently dropped.

## 15. Schema-1 Migration Direction

The current schema-1 record has generic exclusive meaning and lacks role and
assignment bindings. Migration must preserve it without guessing.

The ratified future schema-2 migration direction is:

1. Read and validate the complete schema-1 candidate before publication.
2. Convert a record to `MATERIAL_HANDLER` only when the exact employee,
   Workstation Instance, nonterminal Transfer Operation, assignment, endpoint,
   and current transfer stage are proven by authoritative owner state.
3. Do not convert a schema-1 record to `MACHINE_OPERATOR`; no pre-IM-032 durable
   machine-operation assignment exists that can prove that role.
4. Preserve every other valid active schema-1 record as migration-only
   `LEGACY_EXCLUSIVE`.
5. `LEGACY_EXCLUSIVE` retains the prior exclusive conflict behavior and only
   the eligibility already present before migration. It grants no new Machine
   Run START, STOP, or RESUME authority.
6. A legacy record leaves migration-only state through explicit release,
   invalidation, or a separately proven owner migration. A later request then
   creates a new role-bound reservation.

Migration must not infer role from:

- current machine contents;
- operating state;
- employee proximity;
- display state;
- workstation type alone; or
- coordinates without exact Workstation Instance Identity.

If exact Workstation Instance binding cannot be proven for an active legacy
record, the record must remain conservatively exclusive or enter a visible
recovery state. It must not be granted broader access.

## 16. Cancellation, Release, And Staleness

### 16.1 Operator

Cancelling a machine-operation assignment requests termination through the
canonical Machine Run boundary. `MACHINE_OPERATOR` releases only after the
exact Run reaches the DG-005 safe STOP boundary or authoritative evidence proves
that no Run was created. Cancellation never deletes an invoked child or
rewrites Workstation operating state.

### 16.2 Handler

Material Handling cancellation follows DG-002 custody and return rules.
`MATERIAL_HANDLER` release requires authoritative assignment and transfer
evidence that endpoint access is no longer valid or required. Releasing handler
access never clears Material Handling custody and never releases the operator.

If custody remains proven but employee access ends, Material Handling retains
custody and enters its existing explicit recovery or cancellation path. The
reservation record does not become an inventory.

### 16.3 Deterministic Stale Cleanup

A handler grant becomes stale when authoritative evidence proves that:

- its transfer is terminal;
- its transfer or assignment was cancelled;
- its employee is invalid;
- its endpoint was replaced;
- its Workstation Instance was retired; or
- its identity or configuration no longer matches the bound operation.

Stale cleanup uses authoritative simulation ticks and owner evidence, never
wall-clock time. An operator grant is not expired merely because a Run is idle,
empty, blocked, or waiting for safe STOP.

## 17. Replacement Workstation Behavior

Every role binds exact DG-002A Workstation Instance Identity. A new block at the
same dimension and coordinates inherits none of the following:

- operator reservation;
- handler reservation;
- assignment;
- transfer endpoint access; or
- Machine Run.

Replacement invalidates both roles independently. Material Handling retains
any proven custody and follows DG-002 recovery. Execution and Workstation follow
DG-005 replacement and Run recovery. Coordinates never transfer authority.

## 18. Recovery And Hard-Crash Semantics

Recovery validates the complete reservation candidate and its cross-owner
references before publishing active access.

| Crash state | Required recovery |
| --- | --- |
| Operator only | Restore exact operator grant and assignment binding. Any active Run follows DG-005 restart Policy B; no automatic work is admitted. |
| Handler only | Restore only when the exact nonterminal transfer, assignment, employee, endpoint, and instance still validate. Terminal transfer evidence releases the grant idempotently. |
| Operator plus handler | Restore both exact identities only when their compatibility and every cross-owner reference validate. Do not collapse them into one record. |
| Transfer commits, then crash before handler release | Material Handling completion evidence remains authoritative. Release the handler idempotently while preserving the operator. Never repeat the endpoint effect. |
| STOP requested while handler is active | Continue exact-Run STOP under Execution. Preserve or release handler solely from transfer and endpoint evidence; STOP does not cancel custody. |
| Handler cancelled while operator Run is active | Follow DG-002 cancellation/return. Release only handler access when proven; preserve operator and Run. |
| Workstation replaced while either role is persisted | Reject both stale instance bindings. Preserve independent owner recovery data; grant no access to the replacement. |
| Reservation publication interrupted | Use the last complete valid owner publication. A malformed or unsupported candidate blocks reservation authority visibly; never merge records from different publications. |

An unresolved handler blocks only its bound transfer and endpoint conflict
domain unless reservation authority cannot safely validate the complete active
set. An unresolved operator blocks machine-operation control for its exact
instance. Recovery never fabricates a grant from another owner's live state.

## 19. Checkpoint Behavior

Role-aware reservations are Workforce-owned durable runtime and participate in
coordinated checkpoints through one owner-native reservation snapshot.

A restorable checkpoint containing active reservations must preserve:

- every reservation identity;
- exact role;
- lifecycle;
- employee identity;
- exact Workstation Instance Identity;
- assignment and transfer references;
- endpoint scope and conflict domain;
- owner revision and configuration identity; and
- canonical ordering.

Checkpoint cross-owner validation must prove that referenced employee,
Workstation Instance, Material Handling transfer, machine-operation assignment,
and Machine Run evidence belong to the same captured generation where they are
required. Checkpoint Recovery coordinates selection and restoration only.

Owner-native restoration publishes the exact reservation snapshot. Later
reservation reconciliation may release a handler proven terminal or invalidate
a replacement instance, but it may not invent compatibility or broaden scope.

## 20. Concurrency And Publication

All compatibility decisions and active-set transitions are serialized through
the singular reservation-owner boundary. This is an architectural boundary,
not a prescribed Java synchronization mechanism.

Required race outcomes are deterministic:

- two employees race for `MACHINE_OPERATOR`: one winner;
- a valid operator and compatible handler race: both may coexist;
- two handlers race for the same Workstation Instance: one winner;
- same logical request races with itself: one record and one idempotent
  observation; and
- a replacement Workstation races with a stale request: replacement identity
  wins and stale authority is rejected.

Candidate validation and authoritative publication occur as one reservation-
owner boundary. No caller may check compatibility and then publish outside that
boundary.

## 21. Diagnostics And Command Feedback

Reservation diagnostics must distinguish operator and handler state. They
should expose:

- reservation identity;
- Workstation Instance Identity;
- employee identity;
- role;
- assignment and transfer reference;
- endpoint purpose and scope;
- lifecycle;
- conflict domain;
- compatibility result;
- owner revision; and
- release, invalidation, or recovery reason.

Machine diagnostics may display the active operator and temporary handler as
separate observations. Material Handling and later IM-032 command feedback must
distinguish at least:

- `operator_conflict`;
- `handler_conflict`;
- `compatible_handler_admitted`;
- `employee_reservation_conflict`;
- `stale_workstation_instance`; and
- `endpoint_access_denied`.

The generic message `already reserved` is insufficient when a typed role or
endpoint conflict is known.

## 22. Amendment To DG-002

This decision narrowly replaces DG-002's blanket simultaneous-reservation
prohibition with:

> Simultaneous conflicting Workstation reservations remain prohibited. One
> employee may hold at most one active Workstation reservation. For one exact
> Workstation Instance, one exclusive MACHINE_OPERATOR may coexist with one
> explicitly compatible, endpoint-scoped MATERIAL_HANDLER grant bound to a
> valid Material Handling transfer. The coexistence grants neither role the
> other's authority.

All DG-002 custody, transfer, endpoint mutation, source/destination ordering,
explicit selection, cancellation, evidence, and recovery rules remain in
force.

## 23. Clarification To DG-005

DG-005's rule that the employee operator retains the Workstation reservation
through STOP means:

> The employee retains the exclusive MACHINE_OPERATOR role through the exact
> Machine Run and safe STOP. That exclusive operating responsibility does not
> exclude one compatible MATERIAL_HANDLER from temporary endpoint access under
> DG-002 and DG-005A.

The handler cannot START, STOP, RESUME, admit children, or become responsible
for the Run. All other DG-005 authority and restart rules remain unchanged.

## 24. Required Validation For Future Implementation

A future implementation must prove at minimum:

1. operator alone succeeds;
2. second operator is rejected;
3. valid handler alone succeeds;
4. operator and compatible handler coexist;
5. handler cannot START;
6. handler cannot STOP;
7. handler cannot RESUME;
8. second handler for the instance is rejected;
9. duplicate same logical handler is idempotent;
10. `RUNNING_EMPTY` delivery succeeds while operator is retained;
11. `OUTPUT_BLOCKED` service succeeds while operator is retained;
12. handler cancellation leaves operator intact;
13. operator cancellation leaves a safe independent transfer intact;
14. terminal transfer releases handler;
15. save/reload preserves exact roles and bindings;
16. checkpoint capture and restoration preserve exact roles and bindings;
17. hard-crash recovery preserves exact compatible state;
18. replacement Workstation invalidates both roles;
19. stale handler cannot access a replacement instance;
20. Material Handling custody remains singular;
21. no second Machine Run is created;
22. exact reservation identity is persisted;
23. schema-1 migration and `LEGACY_EXCLUSIVE` behavior are deterministic;
24. unknown schema and malformed role records fail visibly;
25. conflicting cross-owner generations are not published;
26. no force-loading occurs; and
27. existing player and one-cycle employee operation behavior remains intact.

## 25. Alternatives Rejected By This Proposal

- **Release the operator reservation while the Run remains active:** rejected
  because it removes exclusive operating responsibility and violates DG-005.
- **Let Material Handling bypass reservations:** rejected because employee
  endpoint access and navigation would lose its authoritative access boundary.
- **Give Material Handling Machine Run authority:** rejected because custody
  does not grant START, STOP, RESUME, or Execution ownership.
- **Merge machine-operation assignment and Material Handling assignment:**
  rejected because the responsibilities, lifecycles, recovery, and evidence
  are independently owned.
- **Allow unlimited compatible reservations:** rejected because endpoint
  conflict domains and concurrency are not yet modeled broadly enough.
- **Create a second handler reservation manager:** rejected because it creates
  competing authority over the same Workstation resource.
- **Infer role from inventory or operating state:** rejected because it is
  nondeterministic, loses assignment identity, and can broaden stale authority.
- **Treat every legacy reservation as MACHINE_OPERATOR:** rejected because
  schema 1 contains no durable evidence for that interpretation.
- **Stop the machine for every handler interaction:** rejected because it is
  unnecessary when Workstation can safely validate an exact endpoint effect and
  it defeats the ratified continuous operating model.

## 26. Ratified Implementation Sequence

The ratified sequence is:

1. DG-005A architecture ratification, completed by this decision;
2. `IM-032A - Role-Aware Workstation Reservation Foundation`; then
3. `IM-032B - Employee Persistent Machine Operation`.

The split is recommended because reservation identity, schema migration,
checkpoint state, recovery, diagnostics, navigation, existing Material Handling
flows, and current one-cycle employee operation all require independent
regression protection before employee Machine Run control is added.

IM-032A may implement only the role-aware reservation foundation and compatible
Material Handling endpoint access. IM-032B may consume that accepted foundation
for employee START/STOP behavior. Neither milestone is authorized by this
decision.

## 27. Consequences And Preserved Gates

This amendment authorizes architecture direction only:

- one singular role-aware reservation authority;
- the two initial roles and compatibility matrix;
- exact role/assignment/endpoint identity binding;
- explicit schema evolution and conservative legacy migration;
- owner-native persistence, checkpoint, and recovery requirements; and
- the ratified IM-032A then IM-032B sequence.

Ratification alone would not authorize:

- runtime implementation;
- schema migration code;
- IM-032A or IM-032B;
- employee autonomous machine selection;
- employee Material Handling search;
- multiple operators;
- concurrent multiple handlers;
- one employee holding concurrent roles;
- general Logistics;
- Production-driven machine control;
- wear, damage, maintenance, or DG-006;
- new player authority; or
- any Architecture Manifest claim that is not mechanically true.

## 28. Ratification Notes

Owner ratification approved all seven decisions below:

1. `DG-005A` is the decision identifier. This decision narrowly amends DG-002
   reservation exclusivity and clarifies DG-005 employee operator reservation
   retention.
2. Workforce remains the singular Workstation Reservation authority for both
   role grants. Material Handling and machine operation receive no competing
   reservation owner.
3. Initial cardinality is one active Workstation reservation per employee, one
   `MACHINE_OPERATOR` per exact Workstation Instance, and one compatible
   `MATERIAL_HANDLER` per exact Workstation Instance.
4. One operator and one transfer-bound compatible handler may coexist without
   sharing operating responsibility or mutation authority.
5. Schema-1 records whose role cannot be proven migrate to the explicit
   migration-only `LEGACY_EXCLUSIVE` state. No historical record is inferred to
   be `MACHINE_OPERATOR`.
6. Role-aware reservation persistence and checkpoint restoration remain
   owner-native to the Workforce reservation authority and preserve exact role,
   identity, assignment, transfer, endpoint, lifecycle, and freshness evidence.
7. The implementation sequence is `IM-032A - Role-Aware Workstation
   Reservation Foundation`, followed by `IM-032B - Employee Persistent Machine
   Operation`.

Ratification alone did not implement role-aware reservations. The separately
authorized IM-032A milestone now implements the schema-2 foundation described
by this decision. Product Owner acceptance remains pending. IM-032B employee
persistent machine operation and DG-006 remain untouched and gated.

## 29. Implementation Status

IM-032A mechanically implements:

- Workforce-owned schema-2 role-aware reservation persistence;
- one reservation per employee, one operator per exact Workstation Instance,
  and one compatible transfer-bound handler per exact instance;
- exact role, request, assignment, transfer, endpoint scope, lifecycle,
  freshness, and Workstation Instance generation identity binding;
- deterministic schema-1 migration to proven `MATERIAL_HANDLER` or conservative
  `LEGACY_EXCLUSIVE`, never inferred `MACHINE_OPERATOR`;
- Material Handling source, destination, and source-return handler access;
- schema-specific checkpoint ownership transition to Workforce for current
  generations while preserving historical Workstation ownership; and
- role-specific release, diagnostics, and Architecture Manifest enforcement.

This implementation creates no employee START, STOP, RESUME, child-admission,
Machine Run, endpoint mutation, custody, Scheduler, or Execution authority.
IM-032B remains separately gated pending IM-032A Product Owner acceptance and
explicit authorization.
