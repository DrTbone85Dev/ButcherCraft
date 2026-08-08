# ButcherCraft Material Handling

Status: DG-002 and DG-002A ratified; IM-028A and IM-028B foundations implemented

## Authority

Material Handling Runtime is the singular authority for transfer lifecycle and
exact in-transit ItemStack custody. Workstation owns endpoint identity,
inventory mutation, the durable endpoint journal, and immutable owner results.
Workforce owns employee transfer assignment intent, physical movement, and the
non-authoritative carry presentation.

The controlling architecture is:

- [DG-002 Material Handling Custody And Recovery](adr/ADR-PROPOSED-MATERIAL-HANDLING-CUSTODY-AND-RECOVERY.md)
- [DG-002A Workstation Endpoint Durability And Instance Identity](adr/ADR-PROPOSED-WORKSTATION-ENDPOINT-DURABILITY-AND-INSTANCE-IDENTITY.md)

No subsystem may infer custody from slot appearance or mutate another owner's
state directly.

## Implemented Flow

IM-028B implements one bounded explicit transfer:

```text
one Cutting Table Beef Trim byproduct output
-> Workstation-proven source withdrawal
-> Material Handling exact custody
-> one Employee carry display
-> Workstation-proven Grinder deposit
-> completed transfer
```

The request binds one employee, one source endpoint instance, one destination
endpoint instance, the current world identity, configuration identities, and
exactly one Beef Trim. There is no material selector, quantity selector,
workstation search, cross-dimension path, or automatic queue.

## Commands

```text
/butchercraft employee transfer <employee> <source-x> <source-y> <source-z> <destination-x> <destination-y> <destination-z>
/butchercraft employee transfer-status <employee>
/butchercraft employee transfer-cancel <employee>
```

The commands are permission-gated consistently with operator diagnostics and
use synchronized built-in command argument types. Employee references accept
`#1`, a unique display name, a quoted display name, or canonical Employee
Identity. Both coordinates resolve only in the command source's current
dimension. The source must be a Cutting Table whose dedicated byproduct output
contains one Beef Trim, and the destination must be a Grinder. The Cutting
Table input and primary T-Bone Steak output do not participate in transfer.

## Reservation And Movement

Schema 1 permits one employee reservation at a time:

1. Workforce acquires the explicit Cutting Table reservation.
2. The Employee physically walks to a valid source approach.
3. Material Handling requests source withdrawal after physical arrival.
4. Workstation commits the exact withdrawal and publishes its owner result.
5. Material Handling accepts exact custody.
6. Workforce releases the source reservation.
7. Workforce acquires the explicit Grinder reservation.
8. The Employee physically walks to a valid destination approach.
9. Material Handling requests destination deposit after physical arrival.
10. Workstation commits the exact deposit and publishes its owner result.
11. Material Handling clears custody and publishes completion.
12. The Employee remains arrived and reserved at the Grinder.

The Grinder remains idle. Processing begins only through the separately
explicit `/butchercraft employee operate <employee>` command.

## Carry View

The Employee's held item is a display projection, not inventory. The tracked
snapshot contains only:

- transfer reference;
- one display ItemStack copied from proven custody;
- display state;
- observation revision.

Minecraft's normal held-item renderer displays the actual registered Beef Trim
model. The view appears only after custody is proven, remains while custody is
proven, and clears only after a proven deposit or source return. Tracking,
login, and entity reload use normal tracked entity data; server startup and
assignment reload reconstruct the view from reconciled Material Handling
custody. Stale and equal-revision conflicting updates are rejected. No packet
is sent merely because another server tick occurred.

Employee records and assignment persistence contain no ItemStack, hidden slot,
path node, renderer state, or client authority.

## Cancellation And Recovery

Before custody, cancellation releases the source reservation and leaves the
Cutting Table outputs unchanged. After custody, cancellation keeps the Beef Trim
visible, releases any destination reservation, reacquires the explicit source,
physically returns, and invokes the existing Material Handling source-return
protocol. The display clears only after a matching Workstation owner result
proves the exact return to the dedicated Beef Trim output. Duplicate cancellation observes existing state and
cannot return twice.

`RECOVERY_REQUIRED` may enter source return only when Material Handling proves
exact custody. `UNKNOWN_OUTCOME` performs no consequential action until owner
reconciliation proves one location. Employee removal, endpoint replacement,
plant closure, off-shift state, reservation loss, or navigation failure never
abandons proven custody.

## Persistence And Startup

Material Handling persists at:

```text
<world>/butchercraft/material_handling.json
```

It owns the exact ItemStack only while custody is in transit or unresolved.
Workforce assignment intent persists separately at:

```text
<world>/butchercraft/employee_material_handling_assignments.json
```

That file stores schema, assignment identity, employee identity, transfer
identity, explicit endpoint references, assignment state, revision, and typed
failure only. Both stores reject unsupported schema and interrupted temporary
publication visibly.

Startup ordering is:

1. World Identity.
2. Workstation instance registry.
3. Workstation endpoint journal.
4. Block-entity projection reconciliation.
5. Material Handling persistence and reconciliation.
6. Workforce employee and reservation state.
7. Workforce transfer assignments.
8. Carry display derivation and navigation resumption.

Prepared effects without reconciled owner results do not resume movement or
retry consequential effects.

## Current Gates

The following remain unimplemented and unauthorized by IM-028B:

- Ground Beef transport;
- Patty Former destination or employee operation;
- multiple materials or quantities;
- employee inventory or item drops;
- cross-dimension transfer;
- Production-driven assignment;
- automatic workstation selection;
- autonomous queues or general Logistics;
- Scheduler, Execution, economic Inventory, Transaction, or Allocation use;
- public transport APIs;
- custom carrying animation frameworks.
