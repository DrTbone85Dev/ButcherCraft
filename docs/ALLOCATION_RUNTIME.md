# Allocation Runtime And Registries

Status: RFC-0022 Revision 2 Milestone M22B implemented

Milestone M22B adds deterministic lifecycle state and immutable query surfaces
to the M22A Resource Allocation domain. It does not select Resources, evaluate
Capacity, schedule work, reserve Inventory, or execute Transactions.

The governing design remains
[`RFC-0022_RESOURCE_ALLOCATION_ENGINE.md`](RFC-0022_RESOURCE_ALLOCATION_ENGINE.md).
Only M22A and M22B are implemented. M22C through M22F remain separately gated.

## Ownership

`com.butchercraft.world.allocation` owns:

- immutable Requirement, Request, AllocationSet, and Commitment definitions;
- one mutable `AllocationSetRuntime` lifecycle per registered AllocationSet;
- canonical definition, runtime, and report registries;
- immutable lifecycle history, runtime views, reports, and query results;
- structural runtime validation and failure reporting.

`AllocationRuntimeService` is the only public mutation boundary. Definitions,
runtime snapshots, reports, history, and query results are detached immutable
views. Runtime does not own authoritative Resources, Capacity, Inventory
quantities, Planning decisions, Production execution, Scheduler eligibility,
or Transactions.

## Runtime Identity And State

Runtime identity is the existing `AllocationSetId`. M22B does not introduce a
second `AllocationRuntimeId`.

`AllocationSetRuntime` stores:

- AllocationSet id and lifecycle status;
- creation and last-updated simulation ticks;
- optional waiting, allocated, activated, released, and expiration ticks;
- ordered Commitment ids;
- optional typed failure code and bounded message;
- immutable typed metadata;
- monotonic revision and schema version.

Every public read returns an immutable `AllocationRuntimeView`.

## Lifecycle

The schema-1 transition graph is:

```text
REQUESTED -> WAITING | ALLOCATED | FAILED | EXPIRED
WAITING   -> ALLOCATED | FAILED | EXPIRED
ALLOCATED -> ACTIVE | RELEASED | FAILED | EXPIRED
ACTIVE    -> RELEASED | FAILED | EXPIRED
RELEASED  -> terminal
FAILED    -> terminal
EXPIRED   -> terminal
```

Transitions require a non-decreasing explicit simulation tick and the next
revision. `WAITING`, `FAILED`, and `EXPIRED` require failure evidence.
`ALLOCATED` requires exactly one known Commitment for every Requirement in the
Set, with every Commitment associated with that Set. These checks prove
structure only; M22B never creates Commitments or decides which Resource wins.

## Registries

`AllocationRegistryBuilder` constructs one immutable `AllocationRegistry` for
Requirements, Requests, AllocationSets, and Commitments. It rejects duplicate
ids and unknown or inconsistent cross-references. Canonical id ordering is
independent of registration order.

`AllocationRuntimeRegistry` indexes immutable runtime views by AllocationSet
and status. `AllocationReportRegistry` indexes reports by Allocation Cycle and
simulation tick. Their lists, maps, streams, and filtered results cannot mutate
service state.

The architecture manifest declares:

- `butchercraft:allocation_definitions`;
- `butchercraft:allocation_runtime`;
- `butchercraft:allocation_reports`.

All three use canonical-id ordering. Allocation still has no persistence
descriptor, Scheduler stage, or Work type.

## Runtime Service

`AllocationRuntimeService` supports:

- registering a REQUESTED runtime for a known AllocationSet;
- validating and applying an explicitly requested structural transition;
- registering already-created immutable Commitments;
- registering already-created immutable reports;
- publishing detached definition, runtime, report, history, and query views;
- reconstructing validated state supplied by a future persistence boundary.

The service does not contain an allocation policy, resource provider,
capacity ledger, conflict resolver, retry loop, clock, or scheduler callback.

## Queries And History

`AllocationQueryService` provides immutable lookup by:

- Request, AllocationSet, Commitment, and runtime id;
- Request-to-runtime and Planning-Cycle-to-Set association;
- Commitment Set, Requirement, Resource, and execution-work association;
- active, waiting, released, failed, and expired status;
- report Allocation Cycle, tick, and inclusive tick range;
- AllocationSet history and inclusive history tick range.

`AllocationHistory` stores immutable ordered transition records. Revisions must
be contiguous from zero, previous statuses must form the accepted lifecycle
chain, and transition ticks cannot move backward. Loaded runtime views must
match their latest history record.

## Reports

`AllocationReport` is a data structure for a future algorithm to populate. It
contains:

- successful, waiting, rejected, failed, released, and expired Set ids;
- Commitment ids;
- typed conflict evidence;
- exact Capacity observation, commitment, and remainder evidence;
- request ordering contexts;
- bounded work counts and truncation state;
- typed failures, policy id, simulation tick, and schema version.

Outcome categories are disjoint, references must resolve, Commitments must
belong to the report Cycle, ordering evidence must match its Request, and exact
Capacity evidence must use one unit and balance arithmetically. These models do
not implement a conflict graph, fairness policy, mutable ledger, or Allocation
Cycle execution.

## Validation And Determinism

Runtime failures are typed and canonically ordered. Validation rejects illegal
or terminal transitions, duplicate registration, unknown references, invalid
timestamps or revisions, incomplete Commitment sets, malformed reports,
inconsistent history, invalid schema versions, and arithmetic overflow.

No runtime type reads wall-clock time, randomness, filesystem order, Minecraft,
NeoForge, or a mutable external subsystem. Registries use stable canonical
ordering and public collections are immutable.

Stress coverage performs two order-independent passes over:

- 100,000 runtime views;
- 200,000 lifecycle history records.

It compares canonical digests without timing assertions, random input, sleep,
or filesystem enumeration.

## Deferred Work

M22B does not implement:

- an allocation algorithm, fairness policy, conflict graph, or Capacity ledger;
- Allocation Cycle execution or Scheduler stage 350;
- resource observation providers;
- Planning, Production, execution, Inventory, or Transaction integration;
- persistence, codecs, load orchestration, or runtime publication;
- Minecraft, NeoForge, gameplay, commands, menus, or networking.

Those concerns require a later owner-authorized M22C through M22F milestone.
