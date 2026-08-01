# Employee And Employment Record Foundation

Status: implemented employee foundation in IM-023; department assignment and navigation foundation in IM-024; navigation quality and recovery in IM-026

IM-023 adds the first individual employee layer on top of the existing
Workforce Framework and Business Runtime shift definitions.

This foundation answers:

- which employee identities exist;
- which business employs an employee;
- which configured Business Runtime shift an employee is assigned to;
- whether an employee is pending, active, inactive, or terminated;
- whether an employee is off shift, scheduled, present, absent, or unavailable;
- which Workforce-owned department an employee is assigned to;
- which in-world Employee entity is linked to an employee record.

IM-024 adds department definitions, employee department assignment, and bounded
department-directed movement for present employees. IM-025 adds explicit
workstation reservation and arrival foundations for Grinder and Patty Former.
IM-026 improves movement reliability with deterministic approach candidates,
transient progress monitoring, bounded recovery, diagnostics, and safe
unreachable failure. These milestones still do not add workstation operation,
job claiming, item carrying, logistics, Production task assignment, wages,
payroll, overtime, breaks, morale, fatigue, productivity modifiers, training,
customer interaction, attendance penalties, Allocation, public workforce APIs,
startup recovery, checkpoint activation, or operator reconciliation.

## Authority Boundary

Workforce owns Employee Identity, Employment Records, Department Identity,
department anchors, employee department assignment, employment status, assigned
shift references, optional position references, employee profile, presence
state, entity linkage, employee persistence, department persistence, and
employee diagnostics.

Business Runtime owns plant operating hours, shift definitions, shift identity,
active-shift observation, and Business Runtime configuration identity.

World Identity owns immutable world and business identity records. Employee
Identity references those records but does not mutate or duplicate them.

The Employee entity owns only physical representation: a persistent Employee
Identity link, display synchronization, read-only inspection, and bounded
movement around Workforce-published department anchors or reservation-provided
workstation approach candidates.

Production, Scheduler, Execution, Transactions, Planning, Inventory,
Allocation, and workstations do not receive employee operation authority in
IM-024 through IM-026.

## Identity

An `EmployeeId` is deterministic and bound to:

- World Identity root identity;
- World Identity root digest;
- BusinessId;
- Workforce-owned creation sequence;
- creation source identity;
- employee schema version.

Changing display name, shift assignment, presence, entity link, or employment
status does not change the Employee Identity.

## Employment Records

Employee records persist:

- schema version;
- employee id;
- business id;
- creation sequence;
- World Identity root reference and digest;
- display name and optional preferred name;
- employment status;
- stored presence state;
- assigned Business Runtime shift identity, when assigned;
- optional Workforce position id;
- optional Workforce department id;
- hire Business Calendar day and time;
- optional entity link and anchor;
- record revision;
- creation source and configuration identity.

Employment status values are:

- `PENDING`
- `ACTIVE`
- `INACTIVE`
- `TERMINATED`

Only active employees may be present, absent, scheduled, or off shift.
Inactive, pending, and terminated employees are unavailable.

Terminated employees cannot be reactivated or rebound to an entity.

## Presence

Presence values are:

- `OFF_SHIFT`
- `SCHEDULED`
- `PRESENT`
- `ABSENT`
- `UNAVAILABLE`

`SCHEDULED` is derived from the current Business Runtime active shift and
cannot be written directly.

An employee is unavailable when:

- the employee is not active;
- no shift is assigned;
- the assigned shift is missing from the current Business Runtime config;
- the assigned shift identity no longer matches the configured shift identity;
- the employee is explicitly marked unavailable.

An employee is off shift when the assigned shift is valid but not active.

An employee is scheduled when the assigned shift is valid and active but no
explicit present or absent state is set.

`PRESENT` and `ABSENT` are explicit operator states. They do not submit
Scheduler work, start machines, move items, or affect Production.

## Departments

Department schema version 1 defines `processing`, `packaging`, `shipping`,
`office`, and `maintenance`.

Processing is the only functional default department in IM-024. It has a
default Overworld anchor and radius. The remaining departments are registered
definitions without functional anchors until later milestones.

Employees are assigned to departments, not workstations. Department assignment
does not imply a job, a reservation, a Production Run, Scheduler Work,
Execution authority, Inventory access, or item movement.

## Workstation Reservations And Navigation

Workstation reservation state is separate from employee records. IM-025 stores
active reservations at:

```text
<world>/butchercraft/workstation_reservations.json
```

The reservation domain owns reservation exclusivity, workstation identity, the
persisted operating position, reservation lifecycle, and invalidation evidence.
The employee entity consumes that reservation as movement intent only.

IM-026 adds deterministic workstation approach candidates for Grinder and Patty
Former:

- primary operator position in front of the workstation;
- front-left and front-right alternates;
- direct left and right side positions;
- extended operator-side fallback position.

The entity screens candidates for loaded-world validity, empty feet/head
collision, supporting floor, elevation bounds, and path availability. It does
not teleport, operate, open inventories, or select recipes.

If all workstation candidates fail, Workforce invalidates the reservation with
`navigation_unreachable:<reason>` and the employee returns toward its
department when possible.

## Shift Integration

Employee records store a `BusinessShiftIdentity`, shift id, shift display name,
shift-set identity, and Business Runtime configuration identity.

Workforce uses those values only as references. Business Runtime remains the
sole authority for shift definitions, active-shift observation, plant hours,
and Business Runtime configuration identity.

If a shift is removed, renamed, or structurally changed, the employee record is
preserved. Presence observation becomes unavailable until the assignment is
corrected.

## Entity

The `butchercraft:employee` entity is a narrow physical representation.

It:

- persists the Employee Identity link in entity NBT;
- synchronizes display name, status, presence, and shift summary;
- can be inspected read-only by a player;
- idles and looks around;
- navigates to the assigned department anchor when present, plant-open, and a
  functional anchor exists;
- wanders only within a bounded anchor radius;
- navigates to a valid workstation approach candidate when a reservation exists;
- opens normal wooden doors while following a path;
- exposes transient navigation diagnostics.

It does not:

- use villager job-site authority;
- claim jobs;
- select workstations;
- reserve machines;
- operate machines;
- carry items;
- interact with inventories;
- submit Scheduler work;
- mutate Production, Execution, Transactions, Inventory, Planning, or
  Allocation.

If an entity loads with a missing or conflicting employee record, it is removed
rather than creating a new employee silently.

## Persistence

Employee records persist at:

```text
<world>/butchercraft/employee_records.json
```

The schema version is `1`.

Missing files load as an empty directory for migration readiness. Unsupported
schema versions and corrupt JSON fail visibly.

Entity NBT persists only the employee link and movement anchor. The entity NBT
is not authoritative employment state.

Transient navigation state is not persisted. Path objects, candidate indexes,
retry counters, progress timers, and recovery phases are reconstructed or
discarded after reload. Reloaded employees reevaluate their authoritative
department assignment and active reservation before moving or waiting.

Department records persist at:

```text
<world>/butchercraft/departments.json
```

The schema version is `1`. The file stores Workforce-owned department
definitions, optional anchors, and revision. It does not store jobs,
workstation assignments, Production state, Scheduler state, Inventory, or
Execution authority.

## Diagnostics

The employee diagnostics are:

```text
/butchercraft employee create [name]
/butchercraft employee list
/butchercraft employee status <employee>
/butchercraft employee navigation <employee>
/butchercraft employee set-shift <employee> <shift_id>
/butchercraft employee set-presence <employee> <state>
/butchercraft employee assign-department <employee> <department>
/butchercraft department list
/butchercraft department status <department>
```

Employee references accept the listed employee number, such as `#1`, an
unambiguous display name, or the canonical Employee Identity. Tab completion
suggests player-friendly employee numbers and display names, not canonical
identity strings.

These commands are development/operator tools. They do not grant Production,
Scheduler, Execution, Allocation, Inventory, or workstation authority.

`employee navigation` reports destination type, selected candidate, candidate
count, path availability, distance, progress age, retry count, recovery phase,
last failure reason, and reservation validity.

## Validation

IM-023 adds pure Java tests for Employee Identity, record validation, lifecycle
transitions, presence classification, persistence, and dependency boundaries.
IM-024 adds pure Java tests for Department definitions, storage, assignment,
diagnostics, and dependency boundaries. IM-026 adds tests for deterministic
approach candidates, transient recovery state, blocked-primary fallback,
bounded unreachable failure, diagnostics, and the no-operation boundary.

It also adds GameTests covering live employee creation, entity linkage, entity
persistence tags, shift references, presence states, department assignment,
Processing anchor targeting, bounded department idling, unanchored department
fallback, and the Scheduler boundary.

## Remaining Gates

The following remain gated after IM-024:

- employee navigation to assigned workstations;
- job claiming;
- workstation operation;
- item carrying;
- logistics;
- machine reservation;
- Production task assignment;
- attendance consequences;
- payroll, wages, overtime, breaks, morale, fatigue, productivity, training,
  and skill effects;
- customer interaction;
- Allocation integration;
- public workforce APIs;
- startup recovery, automatic checkpoints, and operator reconciliation.

The following remain gated after IM-026:

- employee-initiated workstation operation;
- product insertion, extraction, carrying, or movement;
- Production-driven employee assignment;
- Scheduler dispatch;
- Execution authorization;
- recipe selection;
- output collection;
- employee skill, productivity, fatigue, morale, payroll, and training;
- complex crowd simulation.
