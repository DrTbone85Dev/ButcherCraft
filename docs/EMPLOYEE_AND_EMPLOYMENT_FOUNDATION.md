# Employee And Employment Record Foundation

Status: implemented foundation in IM-023

IM-023 adds the first individual employee layer on top of the existing
Workforce Framework and Business Runtime shift definitions.

This foundation answers:

- which employee identities exist;
- which business employs an employee;
- which configured Business Runtime shift an employee is assigned to;
- whether an employee is pending, active, inactive, or terminated;
- whether an employee is off shift, scheduled, present, absent, or unavailable;
- which in-world Employee entity is linked to an employee record.

It does not add workstation operation, job claiming, item carrying, logistics,
machine reservation, Production task assignment, wages, payroll, overtime,
breaks, morale, fatigue, productivity modifiers, training, customer
interaction, attendance penalties, Allocation, public workforce APIs, startup
recovery, checkpoint activation, or operator reconciliation.

## Authority Boundary

Workforce owns Employee Identity, Employment Records, employment status,
assigned shift references, optional position references, employee profile,
presence state, entity linkage, employee persistence, and employee diagnostics.

Business Runtime owns plant operating hours, shift definitions, shift identity,
active-shift observation, and Business Runtime configuration identity.

World Identity owns immutable world and business identity records. Employee
Identity references those records but does not mutate or duplicate them.

The Employee entity owns only physical representation: a persistent Employee
Identity link, display synchronization, read-only inspection, and bounded idle
movement around an anchor.

Production, Scheduler, Execution, Transactions, Planning, Inventory,
Allocation, and workstations do not receive employee authority in IM-023.

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
- wanders only within a bounded anchor radius.

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

## Diagnostics

The employee diagnostics are:

```text
/butchercraft employee create [name]
/butchercraft employee list
/butchercraft employee status <employee>
/butchercraft employee set-shift <employee> <shift_id>
/butchercraft employee set-presence <employee> <state>
```

Employee references accept the listed employee number, such as `#1`, an
unambiguous display name, or the canonical Employee Identity. Tab completion
suggests player-friendly employee numbers and display names, not canonical
identity strings.

These commands are development/operator tools. They do not grant Production,
Scheduler, Execution, Allocation, Inventory, or workstation authority.

## Validation

IM-023 adds pure Java tests for Employee Identity, record validation, lifecycle
transitions, presence classification, persistence, and dependency boundaries.

It also adds twenty-eight GameTests covering live employee creation, entity
linkage, entity persistence tags, shift references, presence states, and the
Scheduler boundary.

## Remaining Gates

The following remain gated:

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
