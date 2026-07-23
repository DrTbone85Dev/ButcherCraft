# Resource Allocation Core Domain

Status: RFC-0022 Revision 2 Milestone M22A implemented; M22B runtime documented
separately

Milestone M22A establishes the immutable, pure Java vocabulary for future
resource allocation. It does not allocate resources, run an Allocation Cycle,
register Scheduler work, persist Allocation state, gate execution, or integrate
Planning and Production.

The governing design is
[`RFC-0022_RESOURCE_ALLOCATION_ENGINE.md`](RFC-0022_RESOURCE_ALLOCATION_ENGINE.md).
M22A is the first bounded implementation milestone of that RFC. M22B lifecycle,
registry, query, history, and report foundations are documented in
[`ALLOCATION_RUNTIME.md`](ALLOCATION_RUNTIME.md). M22C through M22F remain
separately gated.

## Ownership

Allocation owns:

- immutable `AllocationRequestDefinition` records;
- immutable `AllocationSetDefinition` atomic structural groups;
- immutable `AllocationCommitmentDefinition` records;
- stable Allocation identities, quantities, references, observed snapshots,
  ordering facts, and typed structural validation.

Allocation does not own:

- authoritative Resources or Capacity;
- Workforce, Production, Inventory, or future provider definitions;
- Planning decisions or executable work;
- Scheduler eligibility or simulation time;
- Transactions or Inventory quantities.

External owners are represented only by immutable `ExternalReference` values.
No M22A type imports a concrete provider domain.

## Package

The domain is located at:

```text
com.butchercraft.world.allocation
```

It is independent of Minecraft, NeoForge, ItemStack, SavedData, Planning,
Production, Scheduler, Inventory, and Transaction implementation packages.

## Object Graph

```text
AllocationOrderingContext
        |
        v
AllocationRequestDefinition ----> ordered RequirementId values
        |                                      |
        v                                      v
AllocationSetDefinition <---------- RequirementDefinition
                                               |
                                               v
                                  AllocationCommitmentDefinition
```

`AllocationRequestDefinition` and `AllocationSetDefinition` contain ordered
Requirement identities. Construction APIs accept complete Requirement objects
so cross-object associations can be validated before immutable identities are
stored.

## Stable Identities

Schema 1 defines value types for:

- `AllocationCycleId`
- `ResourceId`
- `CapacityId`
- `CapacityTypeId`
- `CapacityUnitId`
- `RequirementId`
- `AllocationRequestId`
- `AllocationSetId`
- `AllocationCommitmentId`
- `AllocationProviderId`
- `AllocationPolicyId`

All identifiers use canonical lowercase namespaced strings and natural lexical
ordering. Structural Request, Set, Requirement, and Commitment identities use
SHA-256 over ordered UTF-8 components with explicit separators. The first
16 digest bytes are encoded as lowercase hexadecimal under a stable
`butchercraft:` type path. Identity creation does not use locale, wall-clock
time, randomness, object identity, filesystem order, or collection hash order.

`AllocationCycleId.forTick` derives a stable identity only from an explicit
simulation tick. It does not read the Simulation Clock.

## Capacity Units And Quantities

`CapacityUnitId` is an open namespaced identity. `CapacityUnits` provides
schema-1 constants for worker slots and hours, storage volume and mass,
production slots, machine time, transport slots, payload volume and mass, dock
time, utility output, energy, and inspection slots. Extensions may use custom
units without modifying Core.

`AllocationQuantity` combines:

- a non-negative exact `BigDecimal`;
- one `CapacityUnitId`;
- maximum precision 38;
- maximum scale 9;
- canonical plain-decimal formatting.

Addition, subtraction, and amount comparison require identical units.
Subtraction rejects underflow. There is no floating-point path, implicit
conversion, conversion table, dimensional inference, or Goods-unit coupling.
Requirement and Commitment quantities must be strictly positive.

## External Authority

`ExternalReference` records:

- reference type id;
- stable external id;
- authoritative subsystem id;
- optional role id.

`ObservedResourceSnapshot` records one provider-supplied Resource observation,
including category, provider, external authority, availability, exclusivity,
explicit observation tick, typed metadata, and schema version.

`ObservedCapacitySnapshot` records a Capacity identity, Resource identity,
Capacity type, exact observed amount and unit, explicit observation tick,
external authority, typed metadata, and schema version. It can expose a typed
`CapacityKey`.

Snapshots are immutable supplied facts. They perform no world query, service
lookup, lazy resolution, capacity accounting, or provider registration.

## Resource Categories

`ResourceCategory` is an open namespaced value rather than a closed enum.
Schema-1 constants cover Workforce, Storage, Production, Transport, Utility,
Inspection, and Infrastructure. Categories carry identity only and contain no
industry behavior.

Schema 1 permits aggregate Workforce capacity such as position, role, or shift
capacity. `ObservedResourceSnapshot` rejects the
`butchercraft:individual_worker` reference type for Workforce resources.
Worker identity, personal schedules, skills, fatigue, and individual assignment
are not part of M22A.

## Requirements

`RequirementDefinition` records:

- canonical Requirement identity;
- AllocationSet identity;
- execution-work external reference;
- Resource category;
- Capacity type;
- optional exact Resource identity;
- exact positive required quantity and matching Capacity unit;
- creation simulation tick;
- typed metadata;
- schema version.

A Requirement describes demand only. It has no lifecycle, provider lookup,
allocation behavior, retry behavior, or mutable status.

## Request Ordering

`AllocationOrderingContext` captures every replay input needed by the canonical
RFC-0022 Revision 2 request order:

1. horizon precedence ascending;
2. priority descending;
3. required-by simulation tick ascending, absent last;
4. starvation age descending at a caller-supplied current tick;
5. Need creation simulation tick ascending;
6. stable request sequence ascending;
7. AllocationRequest identity ascending.

The context also carries stable Planning Cycle and Approved Plan external
references plus the Request creation tick. It never reads mutable Planning
state. Starvation age is calculated from an explicit caller-supplied simulation
tick and does not accumulate runtime state in M22A.

## Allocation Requests And Sets

`AllocationRequestDefinition` describes one request for one external executable
work definition. Requirement identities are defensively copied, deduplicated,
and sorted canonically. The Request validates its canonical identity, Set
association, ordering context, creation tick, metadata, and schema.

`AllocationSetDefinition` is the future atomic allocation unit. It requires at
least one Requirement and validates:

- one external executable-work reference;
- exact source Request association;
- exact Planning Cycle association;
- canonical Set identity;
- canonical Requirement ordering;
- no duplicate Requirement identity;
- no duplicate prohibited exact Resource;
- no duplicate Requirement capacity selector;
- valid creation and optional expiration ticks.

The Set stores no partial status, mutable lifecycle, retry state, or allocation
result.

## Commitments

`AllocationCommitmentDefinition` is an immutable definition, not an active
reservation. It records:

- canonical Commitment identity;
- Allocation Cycle, Set, and Requirement identities;
- Resource and Capacity identities;
- exact positive committed quantity and matching unit;
- creation and optional expiration simulation ticks;
- one or more canonical source-observation references;
- typed metadata;
- schema version.

Construction rejects an exact-Resource mismatch, incompatible units, invalid
expiration, missing evidence, duplicate evidence, and malformed associations.
Commitment activation, release, expiration processing, lifecycle mutation, and
capacity-ledger behavior are deferred.

## Metadata And Validation

`AllocationMetadata` is a bounded immutable map ordered by canonical namespaced
key. Values are typed as Boolean, decimal, identifier, integer, or text.
Schema 1 allows at most 64 entries and 2,048 characters per text value.

Structural failures use `AllocationValidationFailureCode`,
`AllocationValidationFailure`, and `AllocationValidationException`. Composite
constructors collect all locally provable failures and return them in canonical
code, field, and message order. `AllocationOperationResult` provides an
accepted/rejected boundary for callers that should not use exceptions as
control flow.

Validation is structural only. M22A does not check live availability, remaining
capacity, provider eligibility, Inventory, Transactions, or Scheduler state.

## Serialization Contract

Every model carries schema version 1 where the RFC requires a schema field.
Identifiers expose canonical values, quantities expose canonical exact decimal
and unit values, external references expose canonical component keys, metadata
values expose stable type names and canonical values, and all collections have
deterministic order.

M22A deliberately adds no Mojang `Codec`, `StreamCodec`, Gson adapter, or
persistence DTO. Existing world domains introduce JSON adapters with their
persistence owner. M22A has no persistence owner, and adding a codec now would
implicitly freeze an unapproved M22D file contract. A later authorized
serialization milestone can map these canonical primitives without changing
the domain model.

## Architecture Manifest

The explicit architecture manifest declares:

- `butchercraft:allocation` owns the Allocation package;
- Allocation owns Requests, Sets, Commitments, M22B lifecycle, registries,
  reports, and history;
- Allocation has forbidden dependency directions toward Planning, Production,
  Scheduler, Inventory, and Transactions;
- Allocation has no persistence descriptor;
- Allocation has no Scheduler stage or Work type;
- Allocation runtime has no external subsystem dependency edge.

The manifest still describes six Scheduler stages at orders 100 through 600.
M22A does not add the proposed order-350 Allocation stage.

## Verification Scale

The bounded deterministic stress test performs two complete passes containing:

- 100,000 Resource identities;
- 100,000 exact Allocation quantities;
- 100,000 Requirements;
- 50,000 AllocationRequests;
- 50,000 AllocationSets.

Each pass reverses and canonically sorts collections, then compares repeat
digests. It uses no random source, sleep, wall-clock assertion, or filesystem
enumeration.

## Deferred From M22A

M22A itself does not implement:

- an Allocation algorithm, conflict graph, or first-fit policy;
- Allocation Cycle execution, ledgers, managers, or algorithms;
- resource observation providers;
- Scheduler stage 350 or Work handling;
- persistence, load ordering, migration, or runtime publication;
- Planning submission or Production execution gates;
- automated Commitment activation, release, expiration, or preemption;
- Inventory mutation or Transaction execution;
- Minecraft, NeoForge, gameplay, commands, networking, menus, or world hooks.

M22B now provides structural AllocationSet lifecycle, registries, immutable
queries, history, and report data structures without implementing the remaining
concerns. See `ALLOCATION_RUNTIME.md`. M22C through M22F remain gated.
