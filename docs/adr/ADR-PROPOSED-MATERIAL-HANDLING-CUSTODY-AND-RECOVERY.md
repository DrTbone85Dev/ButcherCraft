# ADR-DG-002: Material Handling Custody And Recovery

Status: RATIFIED ARCHITECTURAL DIRECTION - IM-028A AUTHORIZED, NOT IMPLEMENTED

Decision identifier: DG-002

Authority: Owner-ratified architecture direction. This document authorizes the
IM-028A implementation boundary defined in Section 24. It does not itself
implement runtime behavior, gameplay behavior, migration, schema files,
commands, content, persistence, or Architecture Manifest declarations.
IM-028B and all later implementation remain separately gated.

Canonical platform reference:
[`Platform Canonicalization Addendum`](ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md).
Platform-wide vocabulary, identity classes, invariant ownership, Recovery,
Replay, failure-state, cancellation, operator-authority, World Identity, and
Platform Determinism Manifest definitions are canonical there and are not
redefined here.

Related authority:

- [`CONSTITUTION.md`](../../CONSTITUTION.md)
- [`CORE_PRINCIPLES.md`](../../CORE_PRINCIPLES.md)
- [`PROJECT_RULES.md`](../../PROJECT_RULES.md)
- [`TECHNICAL_ARCHITECTURE.md`](../../TECHNICAL_ARCHITECTURE.md)
- [`BCSE Architecture Guide`](../BCSE_ARCHITECTURE_GUIDE.md)
- [`Architecture Validation Framework`](../ARCHITECTURE_VALIDATION_FRAMEWORK.md)
- [`Checkpoint Recovery ADR`](ADR-PROPOSED-CHECKPOINT-RECOVERY.md)
- [`Evidence Lifecycle ADR`](ADR-PROPOSED-EVIDENCE-LIFECYCLE.md)
- [`Workforce Framework`](../WORKFORCE_FRAMEWORK.md)
- [`Workstation Framework`](../WORKSTATION_FRAMEWORK.md)
- [`Generic Execution Runtime Foundation`](../GENERIC_EXECUTION_RUNTIME_FOUNDATION.md)
- [`Live Scheduler Effect Enforcement`](../LIVE_SCHEDULER_EFFECT_ENFORCEMENT.md)

## 1. Context

IM-027 permits one arrived employee to request one Grinder operation through
the existing Grinder, Execution, and Scheduler authorities. The employee does
not supply input, own inventory, collect output, or move product.

The repository currently establishes that:

- workstations own their Minecraft `ItemStack` inventories and block-entity
  persistence;
- `WorkstationInventoryCommitPlan` provides atomic in-memory rollback for one
  workstation operation, not a durable cross-workstation transfer;
- Workforce owns Employee Identity, employment records, department assignment,
  movement intent, and employee observation;
- an employee may hold only one active workstation reservation;
- Production may observe a manual Grinder-to-Patty-Former chain but does not
  move its `ItemStack`;
- Execution owns generic operation lifecycle and Scheduler owns ordered Work
  dispatch and effect observation;
- Checkpoint Recovery coordinates owner snapshots without taking ownership of
  source facts;
- no Cutting Table is registered or implemented;
- the Patty Former automatically begins normal processing when valid input is
  available; and
- no authority owns an exact product stack between workstation inventories.

The Constitution requires goods movement to follow an explicit custody and
location contract. A visual held item cannot become that contract because the
client is not authoritative and Employee entities cannot become inventories.

## 2. Problem

A source workstation cannot safely clear an exact `ItemStack` unless another
singular authority can prove where that stack resides, preserve every data
component, recover interrupted publication, and prevent a stale request from
withdrawing or depositing it again.

Ordinary write ordering is insufficient. Workstation block entities and
world-level JSON owners persist independently. A crash may occur between any
two owner publications. Treating those writes as one filesystem transaction
would permit duplication, silent loss, mixed state, or guessed recovery.

The architecture must therefore define:

- one transfer and in-transit custody authority;
- owner-preserving source and destination mutation boundaries;
- exact-stack serialization fidelity;
- idempotent owner-result evidence;
- deterministic crash reconciliation;
- one-reservation-at-a-time movement;
- explicit workstation selection;
- a non-authoritative carry view; and
- a Patty Former operation gate that transport cannot bypass.

## 3. Architectural Constraints

This decision is governed by:

- `AI-0001` Deterministic Simulation;
- `AI-0002` Server Authority;
- `AI-0003` Pure Domain Isolation;
- `AI-0004` Immutable Identity Separation;
- `AI-0010` Immutable Public Views;
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

Additional constraints:

- one exact stack has one authoritative location at a time;
- an employee never owns inventory or an authoritative `ItemStack`;
- a workstation mutates only its own inventory;
- Material Handling does not mutate a workstation slot directly;
- Workforce does not acquire workstation, Execution, Scheduler, Production,
  Transaction, or Material Handling mutation authority;
- Material Handling does not become economic Inventory;
- no client request commits a transfer;
- no automatic retry may repeat an uncertain consequential mutation;
- no cross-file atomicity is assumed; and
- implementation remains limited to separately owner-authorized milestones;
  this ratification authorizes IM-028A only.

## 4. Decision

Create one future **Material Handling Runtime** as the singular authority for a
physical transfer between Minecraft workstation inventories.

Material Handling Runtime owns:

- Transfer Operation Identity and transfer sequence allocation;
- transfer lifecycle and transition validation;
- exact in-transit `ItemStack` custody;
- source and destination references;
- prepared transfer payloads;
- cross-transfer withdrawal, deposit, cancellation, and completion evidence;
- failure, Unknown Outcome, and Recovery-Required state;
- deterministic reconciliation; and
- Material Handling persistence and configuration identity.

Workforce owns:

- Material Handling Assignment Identity;
- assignment of one employee to one Transfer Operation;
- employee movement intent and employee availability decisions;
- the display-safe carry observation derived from Material Handling; and
- release of the Workforce assignment after a proven transfer outcome.

Each source or destination workstation owns:

- its inventory and slot state;
- compatibility and capacity validation;
- its local Freshness Identity;
- its own prepared endpoint marker;
- its inventory mutation;
- its idempotent owner-result receipt; and
- block-entity persistence and recovery of those local facts.

The workstation owner-result receipt proves what that workstation did.
Material Handling owns the cross-transfer evidence that validates and cites
those receipts. This refinement is required by singular ownership: Material
Handling cannot authoritatively claim another subsystem's slot mutation.

Execution, Scheduler, Production, economic Inventory, and Transactions do not
participate in schema-1 physical transfer mutation. Their existing ownership
and runtime state remain unchanged.

## 5. Authority Boundaries

| Concern | Authority | Prohibited second authority |
| --- | --- | --- |
| Exact source slot | Source workstation | Material Handling, Workforce, employee, client |
| Exact in-transit stack | Material Handling Runtime | Source, destination, Workforce, employee, client |
| Exact destination slot | Destination workstation | Material Handling, Workforce, employee, client |
| Employee transfer assignment | Workforce | Material Handling, workstation, Production |
| Transfer lifecycle | Material Handling Runtime | Workforce, workstation, Production |
| Source mutation result | Source workstation | Material Handling, Workforce |
| Destination mutation result | Destination workstation | Material Handling, Workforce |
| Cross-transfer evidence | Material Handling Runtime | Workstations, Workforce |
| Employee carry observation | Workforce, derived from Material Handling | Client and Employee entity persistence |
| Rendering | Client presentation of synchronized observation | Client gameplay authority |
| Reservation lifecycle | Workstation Reservation authority | Material Handling and Workforce internals |
| Machine operation | Workstation owner through existing operation path | Material Handling |

Material Handling may request owner operations and consume immutable results.
It receives no unrestricted inventory, Execution, Scheduler, or Production
authority.

## 6. Identity Model

All identities follow the Platform Identity Model. Display names, entity UUIDs,
positions alone, filenames, wall-clock values, and random UUIDs are not
canonical transfer identities.

### Transfer Operation Identity

Material Handling allocates one monotonically increasing world-scoped transfer
sequence. `TransferOperationId` binds:

- Material Handling schema version;
- World Identity root and digest;
- transfer sequence;
- canonical transfer request content digest; and
- Material Handling Configuration Identity.

The transfer request content digest covers employee reference when present,
source and destination workstation identities, dimension and positions,
material identity, exact quantity, and assignment type. A failed proposal that
never becomes authoritative does not consume a sequence.

### Material Handling Assignment Identity

Workforce derives `MaterialHandlingAssignmentId` from:

- Workforce assignment schema;
- World Identity root;
- Employee Identity;
- Transfer Operation Identity; and
- Workforce assignment Configuration Identity.

One active employee may reference at most one active Material Handling
Assignment. One assignment references exactly one Transfer Operation. A
schema-1 transfer moves one material from one source to one destination; a
multi-workstation chain requires separate explicitly created transfers.

### Source Withdrawal Identity

`SourceWithdrawalId` is derived from:

- Transfer Operation Identity;
- source workstation identity;
- source Freshness Identity examined during preparation;
- exact prepared stack Content Identity;
- quantity; and
- withdrawal protocol schema.

### In-Transit Custody Identity

`InTransitCustodyId` is derived from:

- Transfer Operation Identity;
- Source Withdrawal Identity;
- exact canonical serialized stack Content Identity; and
- custody schema.

### Destination Deposit Identity

`DestinationDepositId` is derived from:

- Transfer Operation Identity;
- In-Transit Custody Identity;
- destination workstation identity;
- destination Freshness Identity examined during preparation;
- quantity; and
- deposit protocol schema.

### Evidence Identity

Source and destination workstations issue owner-result identities for their own
mutations. Material Handling issues immutable withdrawal-acceptance,
custody-publication, deposit-acceptance, cancellation, failure, recovery, and
completion Evidence Identities. Every Material Handling evidence record cites
the exact owner-result identities and content digests it consumed.

Duplicate identity behavior is canonical:

- same identity and same canonical content observes the existing result;
- same identity and different content is an explicit conflict;
- a conflict never overwrites, reinterprets, retries, or reuses the identity;
- a materially new transfer requires a new Transfer Operation Identity.

### Freshness And Configuration Identity

Source and destination Freshness Identities remain owner-defined. They must
uniquely and deterministically represent every authoritative slot, controller,
lock, compatibility, capacity, and transfer-marker fact examined by that owner.
This ADR does not require one global workstation revision.

Material Handling Configuration Identity covers every replay-relevant protocol
choice, including schema, identity canonicalization, stack encoding, endpoint
contract version, reservation transition policy, supported material policy,
and reconciliation policy.

## 7. Exact ItemStack Custody

The exact stack includes:

- registered item identity;
- count;
- all data components;
- custom data;
- durability;
- future product metadata; and
- every registry-aware value required for lossless reconstruction.

Material identity and quantity alone are insufficient. Material Handling uses
Minecraft's registry-aware `ItemStack` serialization contract through an outer
adapter. It does not reconstruct a default item from an item id. The canonical
serialized payload receives a Content Identity and must round-trip without
semantic change.

Pure Material Handling domain values contain identities, lifecycle, digests,
and opaque custody-payload references. Minecraft and NeoForge types remain in
the Material Handling integration and persistence adapter. Both layers form
one Material Handling authority; the adapter is not a second owner.

Prepared payload copies may exist for recovery before custody transfers, but a
prepared copy is explicitly non-authoritative. Recovery copies and evidence
digests never become inventory merely because they contain stack bytes.

## 8. Lifecycle And Custody Location

Schema 1 uses these states:

| State | Authoritative stack location | Meaning |
| --- | --- | --- |
| `REQUESTED` | Source inventory, if the request is valid | Transfer exists but no endpoint is bound |
| `SOURCE_BOUND` | Source inventory | Exact source endpoint and source freshness are accepted |
| `SOURCE_WITHDRAW_PREPARED` | Source inventory | Material Handling durably holds a non-authoritative exact prepared payload; source is locked for this withdrawal |
| `SOURCE_WITHDRAW_COMMITTED` | Material Handling Runtime | A durable source owner result proves removal; the prepared payload is now authoritative custody pending normal in-transit publication |
| `IN_TRANSIT` | Material Handling Runtime | Source owner result proves removal and Material Handling has accepted custody |
| `DESTINATION_BOUND` | Material Handling Runtime | Destination reservation, endpoint, and freshness are accepted |
| `DESTINATION_DEPOSIT_PREPARED` | Material Handling Runtime | Destination is locked for the exact custody and deposit identity |
| `DESTINATION_DEPOSIT_COMMITTED` | Destination inventory | A durable destination owner result proves insertion; the retained Material Handling payload is a non-authoritative recovery copy pending completion publication |
| `COMPLETED` | Destination inventory | Destination owner result proves insertion and Material Handling has published completion |
| `CANCELLED` | Source inventory, or unchanged source before withdrawal | Cancellation completed without unresolved custody |
| `FAILED` | Source inventory, or safely returned source | Failure is terminal only when no product is unresolved |
| `UNKNOWN_OUTCOME` | No location is declared authoritative until resolved | A consequential owner mutation may or may not have committed; every implicated copy and endpoint is mutation-blocked |
| `RECOVERY_REQUIRED` | Required persisted custody-location field, proven by valid evidence | Product location is known and preserved, but normal progression cannot continue without explicit recovery |

`UNKNOWN_OUTCOME` and `RECOVERY_REQUIRED` are not aliases. Unknown Outcome
means authority cannot be proven. Recovery Required may retain proven Material
Handling custody while an endpoint, employee, schema, or operator decision is
unavailable.

Every `RECOVERY_REQUIRED` record identifies exactly one proven custody location
and the evidence establishing it. An `UNKNOWN_OUTCOME` record deliberately
identifies no authoritative location and blocks every implicated stored copy
until evidence or explicit recovery resolves the ambiguity.

Valid normal transitions are:

```text
REQUESTED
  -> SOURCE_BOUND
  -> SOURCE_WITHDRAW_PREPARED
  -> SOURCE_WITHDRAW_COMMITTED
  -> IN_TRANSIT
  -> DESTINATION_BOUND
  -> DESTINATION_DEPOSIT_PREPARED
  -> DESTINATION_DEPOSIT_COMMITTED
  -> COMPLETED
```

Any nonterminal state may enter a compatible explicit failure, cancellation,
Unknown Outcome, or Recovery-Required path. Terminal state is irreversible.
A completed transfer is never "cancelled backward"; compensation requires a
new transfer.

## 9. Transfer Protocol

### 9.1 Request And Source Binding

1. A server-authoritative caller submits an explicit canonical transfer
   proposal.
2. Material Handling validates World Identity, same-dimension schema-1 policy,
   exact quantity one, supported material, endpoint references, and duplicate
   identity behavior.
3. Material Handling durably publishes `REQUESTED` before another owner may
   reference the Transfer Operation.
4. Workforce separately validates and publishes the employee assignment when
   an employee is used. Source mutation remains prohibited until the transfer
   and assignment cross-reference validates in both owner candidates.
5. A crash after `REQUESTED` but before assignment publication leaves one
   unassigned request with no mutation authority; it may be inspected and
   cancelled deterministically.
6. The employee acquires the source reservation through the existing
   reservation authority.
7. Material Handling requests a read-only source endpoint observation.
8. The source owns validation of workstation type, slot, exact stack,
   compatibility, lock state, and source Freshness Identity.
9. Material Handling binds only the exact source result. No position search or
   substitution is permitted.

### 9.2 Withdrawal Preparation

1. Material Handling captures the exact registry-aware source stack payload
   and Content Identity from the source-owned validated observation.
2. Material Handling durably publishes `SOURCE_WITHDRAW_PREPARED` before
   requesting mutation.
3. The prepared record is non-authoritative custody and cites the exact source
   Freshness Identity.
4. The source owner durably locks the selected slot to the
   `SourceWithdrawalId` or rejects the request.
5. Any freshness or content change requires a new validation result; stale
   preparation cannot authorize removal.

### 9.3 Withdrawal Commit

1. Material Handling presents the exact prepared withdrawal identity to the
   source owner.
2. The source revalidates the current Freshness Identity, exact stack Content
   Identity, quantity, lock, and transfer identity.
3. The source atomically publishes its slot removal, post-mutation Freshness
   Identity, and idempotent owner-result marker inside one source-owner durable
   boundary.
4. The source acknowledges success only after that owner state is durably
   published under its persistence contract.
5. The durable source owner result transfers authority from the source slot to
   the matching prepared Material Handling payload. The resulting state is
   `SOURCE_WITHDRAW_COMMITTED`, even if a crash prevents immediate Material
   Handling file replacement.
6. Material Handling validates the owner result and durably publishes
   `IN_TRANSIT`.
7. The source reservation is released only after custody publication is
   durable.

The current `WorkstationInventory` and `WorkstationInventoryCommitPlan` do not
provide this durable transfer contract. Later implementation must add a narrow
workstation-owned endpoint without exposing internal slot setters.

### 9.4 Destination Binding And Preparation

1. The employee acquires the explicitly named destination reservation after
   source reservation release.
2. Material Handling requests a read-only destination endpoint observation.
3. The destination owns compatibility, capacity, operation-state, lock, slot,
   and destination Freshness Identity validation.
4. Material Handling binds the exact result and publishes
   `DESTINATION_BOUND`.
5. Material Handling publishes `DESTINATION_DEPOSIT_PREPARED` while retaining
   authoritative custody.
6. The destination durably locks the selected slot to the exact
   `DestinationDepositId` or rejects the request.

### 9.5 Deposit Commit And Completion

1. Material Handling presents the exact custody and prepared deposit
   identities to the destination owner.
2. The destination revalidates capacity, compatibility, lock, Freshness
   Identity, and exact stack Content Identity.
3. The destination atomically publishes exact stack insertion,
   post-mutation Freshness Identity, and an idempotent owner-result marker
   inside one destination-owner durable boundary.
4. The destination acknowledges success only after that owner state is durably
   published under its persistence contract.
5. The durable destination owner result transfers authority from Material
   Handling custody to the destination inventory. The resulting state is
   `DESTINATION_DEPOSIT_COMMITTED`; any retained Material Handling payload is
   non-authoritative recovery data.
6. Material Handling validates the destination owner result.
7. Material Handling atomically clears the recovery payload and publishes
   immutable completion evidence plus `COMPLETED`.
8. Workforce clears the employee assignment only after observing that
   Material Handling completion.
9. Material Handling relinquishes all interest in the destination reservation.
   Under schema 1 employee transport, reservation authority retains the
   arrived destination reservation until an explicit release or the existing
   authorized workstation-operation flow uses that reservation.

Terminal persistence retains stack Content Identity and evidence references,
not a second authoritative full stack payload.

### 9.6 Idempotency And Conflict Rules

Every prepare, commit, observation, and completion request is identity-bound.
An endpoint asked twice for the same identity and canonical content returns the
existing owner result. It never mutates twice.

An endpoint receiving the same transfer identity with different source,
destination, stack, freshness, quantity, or configuration reports conflict.
Material Handling records the conflict and prohibits progression.

No lifecycle transition is inferred from elapsed time, employee animation,
slot appearance alone, or client acknowledgement.

## 10. Crash And Restart Recovery

Material Handling reconciliation runs before an affected transfer, assignment,
or endpoint may accept new mutation. Reconciliation uses persisted transfer
state, exact prepared or custody payload, endpoint owner-result markers,
Freshness Identities, and evidence digests. It never guesses from a display
name or merely similar stack.

| Crash point | Deterministic recovery |
| --- | --- |
| Before source binding | Source remains authoritative; request may fail or be explicitly resumed |
| After source binding, before preparation | Source remains authoritative; stale freshness requires revalidation |
| After withdrawal preparation, before source mutation | Prepared payload is non-authoritative; valid source slot remains authoritative; preparation may be cancelled or explicitly resumed |
| During source owner mutation | If a valid durable source owner result exists, the matching prepared payload is authoritative in `SOURCE_WITHDRAW_COMMITTED`; if non-application is proven, source remains authoritative; otherwise enter `UNKNOWN_OUTCOME` |
| After source removal, before in-transit publication | Valid source owner result plus matching prepared payload deterministically publishes `IN_TRANSIT`; missing or conflicting proof enters `UNKNOWN_OUTCOME` and retains all recovery data |
| While in transit | Material Handling restores exact custody and Workforce resynchronizes the carry observation; no endpoint mutation is repeated |
| After destination binding, before preparation | Material Handling retains custody; stale destination freshness requires new explicit validation |
| After destination preparation, before insertion | Material Handling retains custody; destination preparation may be cancelled or explicitly resumed |
| During destination owner mutation | A valid durable destination owner result proves destination authority in `DESTINATION_DEPOSIT_COMMITTED`; proven non-application leaves Material Handling custody; otherwise enter `UNKNOWN_OUTCOME` |
| After destination insertion, before completion | A valid destination owner result deterministically clears the non-authoritative recovery payload and publishes completion; absent or conflicting proof enters `UNKNOWN_OUTCOME` without reinsertion |
| During cancellation before withdrawal | Prove source unchanged, release locks and reservation, then publish `CANCELLED` |
| During cancellation with custody | Retain custody and carry observation until a source return owner result is proven; otherwise enter `RECOVERY_REQUIRED` or `UNKNOWN_OUTCOME` |
| During workstation removal | Before withdrawal, fail without mutation. With proven custody, retain custody and require explicit recovery. After proven deposit, complete from destination evidence |
| During persistence replacement | Retain the last valid owner file; malformed or unsupported candidates are excluded and reported visibly |

Automatic recovery may complete only a transition proven by matching durable
owner evidence. It may not automatically retry a consequential owner command.
An unresolved transfer blocks only its bound product and endpoints unless the
integrity failure prevents Material Handling authority from loading safely.

## 11. Cancellation And Return

Cancellation is a request, not immediate deletion.

- Before source mutation, cancellation releases the source lock and
  reservation, proves the source unchanged, and publishes `CANCELLED`.
- After Material Handling accepts custody, cancellation uses the source
  workstation's transfer-aware deposit/return contract. It does not call an
  internal slot setter.
- The exact stack remains in Material Handling custody and remains visible in
  the carry observation until the source owner result proves return.
- If the source is missing, occupied, incompatible, unloaded, or otherwise
  unavailable, the transfer enters `RECOVERY_REQUIRED`; custody is not cleared.
- If return application cannot be proven, the transfer enters
  `UNKNOWN_OUTCOME`; it is not retried automatically.
- After destination insertion is proven, the original transfer is complete.
  Reversal requires a new transfer identity and normal source/destination
  validation.

`FAILED` and `CANCELLED` are legal terminal states only when the exact product
is proven to remain in, or to have returned to, an authoritative inventory.

## 12. Reservation Transitions

Schema 1 preserves the existing one-reservation-per-employee invariant:

1. the assignment binds source and destination identities before movement;
2. the employee acquires the source reservation first;
3. source reservation remains active through source preparation and withdrawal;
4. source reservation is released only after `IN_TRANSIT` is durable;
5. the employee then makes one bounded attempt to acquire the destination
   reservation;
6. destination reservation remains active through deposit completion; and
7. after employee deposit, the destination reservation remains active and the
   employee waits until explicit release or an existing authorized operation
   flow uses it; and
8. the employee never holds two active workstation reservations.

Failure policy:

| Condition | Before custody | With proven Material Handling custody |
| --- | --- | --- |
| Source occupied | Fail without mutation | Not applicable after source release |
| Destination occupied | Do not withdraw if known before preparation; otherwise retain custody and enter `RECOVERY_REQUIRED` after the bounded attempt | Retain custody; explicit resume or cancellation only |
| Reservation lost | Fail or cancel after proving no mutation | Retain custody and enter `RECOVERY_REQUIRED`; no automatic reacquisition loop |
| Employee off shift | Do not begin withdrawal | Stop movement safely, retain custody, and require explicit resume or cancellation |
| Plant closed | Do not begin withdrawal | Stop movement safely, retain custody, and require explicit resume or cancellation |
| Employee unavailable | Fail before mutation | Retain custody; schema 1 does not reassign to another employee |
| Workstation removed | Fail before mutation | Continue only if the remaining bound endpoint can complete safely; otherwise require recovery |
| Endpoint unloaded | Do not force chunk loading | Retain state and require the endpoint to become normally available or receive explicit recovery action |

No reservation grants inventory mutation or machine-operation authority.

## 13. Workstation Selection And Development Surface

Schema 1 performs no radius scan, nearest-workstation search, chunk scan, or
Production-driven selection. Source and destination are explicit immutable
inputs.

The recommended development surface is:

```text
/butchercraft employee transfer <employee> <source-position> <destination-position> <material>
```

Schema 1 uses the executing source's current dimension for both positions and
requires source, destination, and employee to be in that dimension. A future
cross-dimension transfer requires a separate decision.

The command must use synchronized built-in Minecraft or Brigadier argument
types, the existing friendly employee-reference policy, canonical registered
material suggestions, and explicit operator permissions. It submits a request
to the owning services; it receives no inventory authority.

Source and destination identities include workstation type, dimension, block
position, and owner-defined stable identity data. Replacement of a block at
the same position does not silently inherit an active endpoint identity.

Schema 1 supports quantity exactly one and only explicitly authorized endpoint
and material combinations. A chain creates one transfer per leg. Production
does not create or select those transfers.

## 14. Cutting Table Decision

The repository has no Cutting Table block, item, block entity, menu, inventory,
registration, model, recipe, or transfer contract. Existing workstation
semantics do not provide another canonical Beef Trim source with the intended
gameplay meaning.

This ADR recommends **Option A**: authorize a later minimal Cutting Table as
the canonical schema-1 Beef Trim source.

The Cutting Table begins as a narrow source workstation with one
workstation-owned Beef Trim slot, normal block-entity persistence, source
Freshness Identity, and transfer-aware withdrawal/return contract. It performs
no cutting operation and creates no product.

The Cutting Table is authorized only within the bounded IM-028A implementation
milestone below. It is not implemented by this ADR.

## 15. Patty Former Operation Gate

Transport deposit and machine-operation initiation are distinct commands owned
by different authorities. Material Handling may insert an accepted stack
through the destination owner. It may not start processing, issue Execution
authority, dispatch Scheduler Work, or publish a workstation operation result.

The current Grinder suppresses automatic processing while an active employee
reservation exists, which is why schema-1 employee transport retains the
arrived Grinder reservation after deposit. The existing IM-027 command remains
the only employee operation trigger and Material Handling does not invoke it.

The current Patty Former automatically ticks its processing controller even
when an employee reservation exists. Therefore it cannot be admitted as a
Material Handling destination while preserving the requirement that transport
stops after deposit.

The canonical future direction is:

- a valid input makes the Patty Former `READY`;
- processing starts only from an explicit workstation-owner operation request;
- player and employee requests use the same owner validation boundary;
- transport origin is not a hidden recipe or operation switch; and
- Material Handling has no operation-initiation authority.

Applying this direction changes current player behavior and requires a
separate owner-authorized implementation milestone with a usable player start
surface and compatibility tests. Until that milestone is accepted and
implemented, IM-028B must not use the Patty Former as a destination. A
transport-only exception is rejected because it couples machine behavior to
the identity of the inserter and leaves two initiation models.

## 16. Carry-View Contract

Workforce publishes one bounded server-owned `EmployeeCarryObservation`
derived from a matching Material Handling transfer. It contains only:

- Employee Identity or entity reference;
- opaque Transfer Operation reference;
- displayed `ItemStack` snapshot with count exactly one; and
- transfer state.

The observation is present only when Material Handling proves authoritative
custody. It first appears immediately when the durable source owner result
establishes `SOURCE_WITHDRAW_COMMITTED`, and remains through normal
`IN_TRANSIT` publication, destination travel, destination preparation, and any
Recovery-Required state that still holds proven Material Handling custody. It
disappears immediately when a durable destination or cancellation-return owner
result transfers authority out of Material Handling custody.

The synchronized stack is a defensive display copy of the authoritative
custody payload. The client renders it with Minecraft's registered held-item
renderer. The client cannot insert, extract, drop, consume, equip, save, or
otherwise mutate it.

Employee entity NBT and equipment slots do not persist authoritative carried
product. On spawn, tracking start, dimension observation, or reconnect, the
server reconstructs the display observation from Workforce assignment plus
Material Handling custody and resynchronizes it.

If no valid matching transfer exists, the employee displays no carried item.
`UNKNOWN_OUTCOME` without proven custody displays no item. Recovery Required
with proven Material Handling custody continues to display the item so the
player can understand the unresolved physical work.

## 17. Persistence

Material Handling owns one schema-versioned runtime candidate at:

```text
<world>/butchercraft/material_handling.json
```

It persists:

- schema version and owner revision;
- next Transfer Operation sequence;
- Transfer Operation Identity and request digest;
- lifecycle state;
- World Identity root and digest;
- employee reference when present;
- source and destination identities;
- material identity and exact quantity;
- source and destination Freshness Identities;
- exact prepared stack payload while withdrawal is prepared;
- exact authoritative stack payload while Material Handling holds custody;
- stack Content Identity;
- endpoint owner-result references and digests;
- Material Handling evidence references and digests;
- failure, Unknown Outcome, or recovery state;
- Configuration Identity; and
- revision and canonical ordering metadata.

It does not persist:

- path nodes or navigation objects;
- client rendering state;
- employee inventory or equipment;
- mutable workstation inventory internals;
- Production runtime;
- Scheduler Work or Scheduler Runtime Authority;
- Execution authorization or tokens;
- economic Inventory quantities; or
- a second copy presented as authoritative after completion.

Workforce persists employee-to-transfer assignment under a separately
Workforce-owned schema. That record contains references and movement intent,
not the exact stack payload. Workstation endpoint markers persist with their
own owner state. Cross-owner candidates are validated before an affected
transfer becomes mutable.

Per-file temporary replacement does not claim a cross-owner filesystem
transaction. Owner-local inventory mutation and endpoint result publication
must share one owner durable boundary. Material Handling prepare-before-effect
ordering and idempotent owner results provide deterministic reconciliation
across those boundaries.

Legacy worlds without Material Handling persistence load an empty Material
Handling Runtime. Unsupported schema, malformed exact-stack payload, missing
required owner result, conflicting identity, or unmatched active endpoint
marker fails visibly. The affected authority enters Recovery-Blocked or
Degraded Read-Only state; it is never silently replaced with an empty runtime.

Unknown registry values or data components are preserved as inert encoded
payload where possible. They are not discarded or replaced with a default
item. A payload that cannot be safely decoded remains Recovery-Blocked.

## 18. Checkpoint, Recovery, Evidence, And Replay

Once implemented, Material Handling is a mandatory mutable checkpoint owner
whenever authoritative transfer state exists. It supplies and validates its
own immutable owner snapshot. Checkpoint Recovery coordinates generation
publication and selection but does not parse, mutate, complete, or cancel a
transfer.

A checkpoint containing an active transfer may commit only when every
referenced Workforce assignment and workstation endpoint marker belongs to the
same captured generation and cross-owner validation succeeds. Until transfer-
aware workstation block-entity state can participate in that capture, a
checkpoint must either exclude Material Handling implementation authority or
reject capture while a transfer is active. It must not publish mixed state.

The current checkpoint foundation does not yet authorize automatic world
startup recovery for every owner. Material Handling implementation must retain
its owner-local deterministic reconciliation and must not claim that the
existing Clock/Scheduler snapshot integration already protects product
transfer.

Evidence policy:

- unresolved failure and recovery evidence is retained while unresolved;
- owner-result and terminal transfer evidence retains stable Evidence Identity
  independent of hot or archive location;
- terminal evidence retains identities and digests, not authoritative stack
  payload duplication;
- archive movement does not transfer fact ownership; and
- cleanup cannot remove evidence needed to prove no double withdrawal or
  double deposit.

Replay verifies canonical inputs, lifecycle decisions, owner results,
freshness, configuration, and evidence. Replay never reuses runtime authority,
reissues a withdrawal or deposit, reconstructs a missing stack, or selects a
recovery baseline. Recovery remains the committed-baseline authority.

## 19. Failure And Operator Model

Minimum typed failure reasons include:

- source missing, occupied, empty, incompatible, stale, or out of range;
- destination missing, occupied, full, incompatible, stale, or out of range;
- employee missing, unavailable, off shift, or in another dimension;
- plant closed;
- source or destination reservation lost;
- workstation replaced or unloaded;
- duplicate identity conflict;
- exact stack serialization failure;
- owner-result mismatch;
- unsupported schema or configuration;
- `UNKNOWN_OUTCOME`; and
- `RECOVERY_REQUIRED`.

Operator authority may inspect, explicitly resume a non-consequential pending
step, request cancellation, select a valid recovery action, or acknowledge that
manual resolution is required. Operator authority may not invent owner
evidence, silently recreate an item, overwrite a conflicting identity, or
automatically repeat an uncertain mutation.

Diagnostics expose transfer and assignment identities, lifecycle, endpoint
references, custody location, stack Content Identity, freshness, reservations,
last owner results, failure, and recovery state. They do not expose mutable
slot authority or full private custom data unnecessarily.

## 20. Compatibility And Dependency Direction

- Material Handling references Workforce and workstation facts by stable
  identity and immutable observations, not mutable-manager access.
- Pure Material Handling domain code does not import Minecraft or NeoForge.
- Minecraft stack codecs, endpoint adapters, entity synchronization, commands,
  and world paths remain integration concerns.
- Workforce may observe Material Handling custody to publish movement and carry
  views; it cannot transition transfer state.
- Workstations may observe exact prepared endpoint requests and return owner
  results; they cannot transition the cross-transfer lifecycle.
- Production may later observe terminal Material Handling evidence but cannot
  create or mutate schema-1 transfers.
- Execution and Scheduler remain independent. A physical transfer does not
  become generic Execution or Scheduler Work merely because an employee walks.
- Allocation, general Logistics, public APIs, and mod interoperability remain
  gated.
- Economic Inventory and economic Transactions remain separate from Minecraft
  workstation `ItemStack` custody. A future bridge requires a separate
  accepted mapping and transaction decision.

## 21. Future Architecture Manifest Impact

During IM-028A, and only as each declaration becomes mechanically true, the
Architecture Manifest should declare:

- Material Handling as a component and singular runtime authority;
- Transfer Operation, Assignment, Custody, and Evidence identity descriptors;
- Material Handling transfer lifecycle and exact custody ownership;
- Workforce assignment, movement-intent, and carry-observation ownership;
- workstation endpoint freshness, mutation, and owner-result ownership;
- versioned Material Handling and Workforce assignment persistence;
- dependencies through immutable identities and owner results;
- prohibition of employee inventory and direct cross-owner slot mutation;
- no automatic retry after Unknown Outcome;
- checkpoint participant and configuration-identity requirements when live;
  and
- explicit gates for general Logistics, Production-driven transport,
  automatic selection, multiple carried units, employee inventory, Patty
  Former operation, Allocation, autonomous chains, and public APIs.

The manifest must not mark any item implemented merely because this proposed
ADR exists.

## 22. Alternatives Rejected

- **Employee inventory or equipment slot:** rejected because it creates a new
  product owner and persistence path.
- **Visual-only item with no custody owner:** rejected because presentation
  cannot prevent loss or duplication.
- **Material identity plus quantity reconstruction:** rejected because it
  discards data components, durability, custom data, and future metadata.
- **Direct `WorkstationInventory` extraction and insertion:** rejected because
  it bypasses workstation validation and owner-result publication.
- **Material Handling mutates both workstations:** rejected because it becomes
  a second inventory authority.
- **Source workstation remains owner for the entire journey:** rejected because
  destination failure, source removal, and multi-source transfer evolution
  would make one workstation a hidden transport runtime.
- **Filesystem write ordering as atomic transfer:** rejected because separate
  owner writes are not one durable transaction.
- **Player inventory as temporary escrow:** rejected because it changes product
  ownership and permits disconnect, death, drop, or manual mutation paths.
- **Dropped item entity as custody:** rejected because collection, despawn,
  chunk unload, and external mutation are not deterministic transfer evidence.
- **Economic Inventory as exact ItemStack custody:** rejected because economic
  quantities and Minecraft stacks are intentionally separate authorities.
- **Production-owned transport:** rejected because Production currently
  observes workstation chains and does not move products.
- **Execution- or Scheduler-owned transport:** rejected because those systems
  own operation progression and dispatch, not workstation inventory custody.
- **Automatic nearest-workstation selection:** rejected because it introduces
  hidden search, chunk availability dependence, and nondeterministic ties.
- **Two simultaneous workstation reservations:** rejected because it violates
  current reservation exclusivity and increases deadlock risk.
- **Transport-specific Patty Former auto-start suppression:** rejected because
  machine semantics would depend on who inserted the same valid input.

## 23. Risks And Migration Considerations

- Exact `ItemStack` codecs may evolve with Minecraft, NeoForge, components, or
  optional mods. Migration must preserve encoded payload and Content Identity
  or fail visibly.
- Durable endpoint owner results require a stronger contract than the current
  in-memory commit helper and ordinary delayed chunk save.
- Unloaded or removed workstations can retain proven Material Handling custody
  indefinitely until explicit recovery.
- Permanent transfer evidence grows over long-lived saves and must integrate
  with Evidence Lifecycle retention without deleting unresolved proof.
- Cross-owner checkpoint capture remains incomplete in the current foundation.
- Patty Former explicit initiation changes existing player behavior and needs
  its own acceptance and compatibility review.
- Missing optional item registries may make a custody payload unrenderable or
  undecodable while still requiring byte-preserving recovery.
- Schema 1 deliberately limits quantity, assignments, dimensions, endpoint
  types, and selection. Generalizing any limit requires a later decision.

Existing worlds require no migration when the owner file is absent. Once
Material Handling persistence exists, rollback to a version that does not
understand it risks orphaning authoritative custody and is unsupported unless
an explicit downgrade migration proves no active transfer.

## 24. Authorized IM-028A Boundary

**IM-028A - Minimal Cutting Table And Material Handling Custody Foundation**

Owner-authorized implementation scope:

- one minimal craftable or development Cutting Table;
- one Cutting-Table-owned source slot accepting Beef Trim only;
- block-entity inventory and endpoint-marker persistence;
- Cutting Table source Freshness Identity;
- transfer-aware withdrawal and return owner contract;
- Grinder destination Freshness Identity and transfer-aware deposit owner
  contract;
- pure Material Handling lifecycle and identity model;
- Minecraft exact-stack custody adapter;
- schema-1 `material_handling.json` persistence;
- explicit source and Grinder destination binding;
- non-employee source-to-Grinder transfer proof through owner APIs;
- idempotency, cancellation, restart, conflict, and no-duplication tests;
- architecture-manifest declarations authorized by the accepted ADR; and
- diagnostics sufficient to inspect custody and recovery.

Excluded:

- employee assignment or movement;
- employee carry rendering;
- Patty Former destination or operation;
- machine operation;
- Production, Execution, Scheduler, Allocation, or economic Inventory changes;
- automatic workstation selection; and
- general Logistics.

The non-employee proof is an integration/test harness using explicit endpoints.
It does not create invisible gameplay transport or an autonomous runtime loop.

## 25. Proposed IM-028B Boundary

**IM-028B - Employee Material Handling Transport Foundation**

Authorized scope after IM-028A acceptance and separate owner approval:

- one Workforce-owned assignment to one existing Transfer Operation;
- existing friendly Employee Identity resolution;
- source reservation and physical employee travel;
- authoritative source withdrawal through Material Handling and source owner;
- visible exact one-unit held-item rendering derived from custody;
- source reservation release followed by destination reservation;
- physical destination travel and authoritative deposit;
- cancellation, restart reconciliation, workstation removal, employee
  unavailability, and reservation-loss behavior;
- display resynchronization after reconnect;
- explicit development command with bound endpoint positions; and
- focused GameTests and manual visual acceptance.

Schema-1 IM-028B should prove Beef Trim movement from the accepted Cutting
Table to the Grinder. It must not include Ground Beef movement to the Patty
Former until the separately authorized Patty Former explicit-initiation gate
is implemented.

Excluded:

- Patty Former operation;
- Patty Former destination before its operation gate;
- Production-driven assignment;
- automatic workstation selection;
- autonomous chains or retries;
- multiple transfers per employee;
- multiple carried units;
- employee inventory;
- cross-dimension movement;
- general Logistics;
- Execution, Scheduler, Allocation, or economic Inventory authority; and
- public APIs.

## 26. Required Validation For Future Implementation

Future implementation must include automated proof of:

- every legal and illegal lifecycle transition;
- canonical identity and digest stability;
- same-identity/same-content observation;
- same-identity/different-content conflict;
- exact `ItemStack` round trip with all data components;
- one authoritative custody location in every state;
- source and destination freshness rejection;
- source removal plus owner-result atomicity;
- destination insertion plus owner-result atomicity;
- crash recovery at every protocol boundary;
- no double withdrawal or deposit;
- cancellation before and after custody;
- unknown-outcome non-retry;
- recovery-required custody preservation;
- one employee reservation at a time;
- no chunk forcing or arbitrary workstation scan;
- client reconnect carry-view resynchronization;
- no employee, player, Production, Scheduler, Execution, Allocation, or
  economic Inventory mutation outside its owner;
- persistence unsupported-schema failure;
- legacy missing-file empty runtime;
- checkpoint mixed-generation rejection when integrated; and
- Architecture Manifest ownership and dependency validation.

## 27. Ratification Notes

Owner ratification approved Material Handling Custody and Recovery as follows:

1. Material Handling Runtime is the singular authority for transfer lifecycle
   and exact in-transit custody.
2. Material Handling persists the exact in-transit `ItemStack`, including data
   components and custom data.
3. Source withdrawal and destination deposit use prepare, effect, and result
   evidence with deterministic reconciliation.
4. Unprovable item location becomes `UNKNOWN_OUTCOME`; proven but unresolved
   custody becomes `RECOVERY_REQUIRED`.
5. Employees hold one workstation reservation at a time: source first, then
   destination.
6. Schema 1 uses explicit source and destination selection and performs no
   automatic workstation search.
7. IM-028A is authorized to add the minimal Cutting Table with one
   authoritative Beef Trim source slot and the Material Handling foundation
   bounded by Section 24.
8. Patty Former transport remains gated until explicit operation behavior is
   separated from automatic processing.
9. IM-028 is formally split into IM-028A and IM-028B.
10. Employee-held item rendering is a non-authoritative synchronized display
    derived only from proven Material Handling custody.

This ratification does not implement Material Handling. IM-028A may proceed
only as a separate implementation task within Section 24. Architecture
Manifest entries remain unimplemented until their corresponding contracts are
mechanically true. Employee transport, visible carrying, Patty Former
transport, IM-028B, Production-driven transport, automatic workstation
selection, autonomous chains, and general Logistics remain gated.
