# RFC-0022: Resource Allocation Engine

Status: M22A Core Allocation Domain owner-authorized and implemented; M22B
through M22F remain proposed and separately gated

Revision: 2

Governing authority: [`CONSTITUTION.md`](../CONSTITUTION.md)

Related authorities:

- [`CORE_PRINCIPLES.md`](../CORE_PRINCIPLES.md)
- [`DECISIONS.md`](../DECISIONS.md), especially DEC-0073 and DEC-0074
- [`ECONOMIC_PLANNING_ENGINE.md`](ECONOMIC_PLANNING_ENGINE.md)
- [`PRODUCTION_FRAMEWORK.md`](PRODUCTION_FRAMEWORK.md)
- [`SIMULATION_SCHEDULER.md`](SIMULATION_SCHEDULER.md)

Revision 2 reconciles the original draft with accepted ButcherCraft Core
ownership. Planning owns Candidate Plans and Approved Plans. Execution
subsystems own executable definitions and runtime. Allocation owns temporary
capacity Commitments only.

This RFC remains the architecture contract for the complete engine. DEC-0076
and the owner-authorized M22A implementation prompt authorize only the
immutable Core Allocation Domain documented in
[`RESOURCE_ALLOCATION_DOMAIN.md`](RESOURCE_ALLOCATION_DOMAIN.md). Runtime,
Scheduler, persistence, Planning, Production, and execution integration still
require the later owner-approved decisions listed in Part V.

## Part I: Philosophy And First Principles

### 1. Purpose

The Resource Allocation Engine answers one question:

> Given an execution-owned work definition derived from approved intent, which
> scarce resources are permitted to support its execution?

Allocation does not create Candidate Plans, Approved Plans, execution-owned
work definitions, execution runtime, Transactions, or Inventory mutations.

### 2. Motivation

Without one allocation authority, Production allocates workers, Logistics
allocates vehicles, Utilities allocate generators, and Warehousing allocates
storage independently. Those private commitment models would compete, produce
inconsistent fairness, and obscure why work did or did not execute.

### 3. Core Principle

```text
Planning owns decisions.
Allocation owns capacity Commitments.
Execution subsystems own executable definitions and runtime.
Scheduler owns Work eligibility and ordering.
Transactions own economic mutations.
Inventory owns quantities.
Authoritative providers own Resources and Capacity.
```

Responsibilities do not overlap.

### 4. Scarcity

Only finite execution capability requires Allocation. A provider may expose
unbounded capacity explicitly, but Allocation must not invent an unlimited
default when authoritative capacity is absent or malformed.

### 5. Definition Of A Resource

A Resource is an externally owned execution capability that can become
temporarily unavailable because committed work uses it.

Examples include aggregate worker slots, production slots, storage volume,
vehicles, docks, utility capacity, and inspection capacity.

### 6. Definition Of Allocation

An Allocation is a temporary permission to consume scarce execution capacity.
It does not transfer ownership, guarantee successful execution, reserve Goods,
or mutate the resource provider.

### 7. Ownership

Resources and Capacity remain authoritative in their providers:

| Resource | Authoritative owner |
| --- | --- |
| Aggregate position, role, and shift capacity | Workforce and Business Runtime |
| Inventory storage capacity | Inventory |
| Production slot capacity | Production |
| Transportation capacity | Future Logistics |
| Utility capacity | Future Utilities |
| Inspection capacity | Future Inspection |

Allocation persists the observed snapshots used by a cycle, not competing
resource definitions.

### 8. Execution Contract

Execution may begin only after its complete `AllocationSet` is `ACTIVE`.
Execution must not create, expand, replace, or bypass Commitments internally.
It may submit typed activation and release requests to Allocation authority.

### 9. Living World Principle

Allocation uses authoritative simulation time and Scheduler Work. It requires
no player, GUI, client, block, ItemStack, or loaded workstation.

### 10. Architectural Position

```text
Authoritative observations
  -> Planning decisions
  -> execution-owned work definition
  -> Allocation commitments
  -> execution runtime
  -> Transactions
  -> Inventory
```

The Scheduler pipeline adds `butchercraft:allocation` between Planning and
Execution.

### 11. Responsibilities

Allocation owns:

- resource and capacity observation snapshots;
- request and set validation;
- available-capacity ledgers;
- conflict detection and deterministic arbitration;
- atomic Commitment publication;
- set lifecycle, activation, release, failure, and expiration;
- immutable allocation reports.

### 12. Forbidden Responsibilities

Allocation never:

- owns or modifies authoritative Resources or Capacity;
- creates Planning artifacts or execution-owned work definitions;
- executes Production, Logistics, Utilities, or other work;
- schedules Allocation or Execution Work;
- advances Scheduler or Clock;
- mutates Inventory, Business Runtime, Workforce, or Orders;
- creates Transactions or records fulfillment.

### 13. Universal Allocation

Every Core execution subsystem that commits scarce execution capacity must use
Allocation. Industry and compatibility modules may provide resource adapters,
but may not create a second commitment authority.

### 14. Determinism

Identical observed snapshots, active Commitments, requests, policy, Scheduler
ordering, and simulation tick produce identical outcomes. Random ordering,
wall-clock decisions, locale ordering, filesystem ordering, and unordered-map
iteration are prohibited.

### 15. Fairness

Starvation prevention is deterministic and applies within an equivalent
higher-order class: the same horizon, priority, and required-by precedence.
Fairness does not allow an old low-priority request to defeat newer critical
work. No global no-starvation guarantee is made while higher-order work
continues to arrive indefinitely.

### 16. Conservation

For each Capacity key:

```text
remaining = observed - active commitments - cycle-local staged commitments
```

Remaining Capacity never becomes negative. Released, failed, cancelled, or
expired Commitments cease consuming capacity at their authoritative transition
tick and become reusable during the next Allocation Cycle.

### 17. Temporary Nature

Resources may outlive Commitments. Commitment history may outlive a removed
Resource. Missing authoritative references fail active loading or validation
explicitly and are never silently deleted.

### 18. Allocation Is Not Inventory Reservation

Allocation commits execution capacity. Inventory reservation would prevent
specific Goods from being consumed elsewhere. Schema 1 implements no Inventory
reservation. Execution and Transaction validation may still fail after
allocation if authoritative Inventory quantities change.

### 19. Resource Categories

Schema-1 categories are:

- `WORKFORCE`
- `STORAGE`
- `PRODUCTION`
- `TRANSPORT`
- `UTILITY`
- `INSPECTION`
- `INFRASTRUCTURE`

Categories remain industry-neutral and additive.

### 20. First Principles

Planning never commits resources. Execution never allocates resources.
Providers never create Allocation Commitments. Allocation is the sole owner of
temporary execution-capacity Commitments.

### 21. Part I Result

Revision 2 preserves DEC-0073 and DEC-0074: Planning owns Approved Plans,
Production owns `ProductionPlanDefinition` and `ProductionRunRuntime`, and
Allocation operates against stable references to execution-owned work.

## Part II: Resource And Allocation Domain Model

### 22. Domain Philosophy

The Allocation domain is pure Java. Immutable definitions and snapshots remain
separate from mutable set runtime. Every external fact carries source
provenance and an authoritative observation tick.

### 23. Primary Schema-1 Concepts

Schema 1 uses these concepts:

1. External Reference
2. Observed Resource Snapshot
3. Observed Capacity Snapshot
4. Requirement
5. Allocation Request
6. Allocation Set
7. Allocation Commitment
8. Allocation Set Runtime
9. Allocation Report

The snapshots are historical cycle inputs, not authoritative resource
definitions.

### 24. Stable Identifiers

Implement immutable, canonical, namespaced, persistable, comparable identifiers:

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

No identifier uses implicit UUID generation.

### 25. External Reference

`ExternalReference` contains:

- reference type id;
- stable external id;
- authoritative subsystem id;
- optional role id.

It grants no mutation authority.

### 26. Observed Resource Snapshot

`ObservedResourceSnapshot` contains:

- `ResourceId`;
- `ResourceCategory`;
- authoritative provider id;
- authoritative external reference;
- availability status;
- exclusivity mode;
- observation tick;
- bounded typed metadata;
- schema version.

### 27. Resource Availability

Schema 1 supports `AVAILABLE` and `UNAVAILABLE`. Missing or unknown availability
does not become available by default.

### 28. Allocation Quantity

`AllocationQuantity` is an immutable exact decimal with:

- canonical non-scientific representation;
- no negative value;
- bounded precision and scale;
- exact addition, subtraction, and comparison;
- overflow and underflow rejection;
- value equality.

Commitment quantities must be positive. Observed and remaining Capacity may be
zero.

### 29. Capacity Units

`CapacityUnitId` is a canonical namespaced identity. Allocation compares only
equal units and performs no conversion.

Examples include:

- `butchercraft:worker_slot`
- `butchercraft:storage_volume`
- `butchercraft:production_slot`
- `butchercraft:utility_capacity`
- `butchercraft:dock_time`
- `butchercraft:inspection_slot`

### 30. Observed Capacity Snapshot

`ObservedCapacitySnapshot` contains:

- `CapacityId`;
- owning `ResourceId`;
- `CapacityTypeId`;
- exact observed amount;
- `CapacityUnitId`;
- observation tick;
- authoritative external reference;
- bounded typed metadata;
- schema version.

### 31. Capacity Key

A Capacity key is the canonical tuple:

```text
ResourceId + CapacityTypeId + CapacityUnitId
```

Commitments sharing that tuple compete for the same Capacity.

### 32. Requirement

A Requirement is immutable execution demand derived from an execution-owned
work definition by a registered adapter.

### 33. Requirement Definition

`RequirementDefinition` contains:

- `RequirementId`;
- `AllocationSetId`;
- execution-owned work reference;
- `ResourceCategory`;
- `CapacityTypeId`;
- optional exact `ResourceId`;
- exact required `AllocationQuantity`;
- `CapacityUnitId`;
- creation tick;
- bounded typed metadata;
- schema version.

### 34. Requirement Validation

Requirements require positive quantities, known categories and units, a
resolvable execution reference, and either an exact Resource or a deterministic
provider-supported selector. Schema 1 performs no unit conversion or partial
satisfaction.

### 35. Allocation Ordering Context

`AllocationOrderingContext` captures immutable values at request creation:

- horizon;
- priority;
- optional required-by tick;
- Need creation tick;
- Planning Cycle reference;
- source Approved Plan reference;
- request creation tick;
- manager-assigned stable request sequence.

Allocation does not reread mutable Planning artifacts during arbitration.

### 36. Allocation Request

`AllocationRequestDefinition` contains:

- `AllocationRequestId`;
- `AllocationSetId`;
- execution-owned work reference;
- ordered Requirement ids;
- `AllocationOrderingContext`;
- creation tick;
- bounded typed metadata;
- schema version.

Requests ask whether a complete set can obtain Capacity. Construction does not
allocate.

### 37. Allocation Set

An `AllocationSetDefinition` groups every Requirement for one execution-owned
work definition. It contains:

- `AllocationSetId`;
- execution-owned work reference;
- source request id;
- ordered Requirement ids;
- Planning Cycle reference;
- creation tick;
- optional expiration tick;
- bounded typed metadata;
- schema version.

### 38. Allocation Set Validation

Validation requires:

- one resolvable execution-owned work reference;
- at least one Requirement;
- every Requirement assigned to the same set and work reference;
- no duplicate Requirement, exact Resource, or Capacity key;
- compatible units and positive quantities;
- no self-dependency;
- no unsupported category or provider.

Failure rejects the complete set.

### 39. Allocation Commitment

An immutable `AllocationCommitmentDefinition` contains:

- `AllocationCommitmentId`;
- `AllocationCycleId`;
- `AllocationSetId`;
- `RequirementId`;
- `ResourceId`;
- `CapacityId`;
- exact committed quantity;
- `CapacityUnitId`;
- created tick;
- optional expiration tick;
- source observation references;
- bounded typed metadata;
- schema version.

### 40. Allocation Runtime Identity

Pre-commit runtime is keyed by `AllocationSetId`, not
`AllocationCommitmentId`. `AllocationSetRuntime` owns the lifecycle for the
atomic set and records ordered Commitment ids only after allocation succeeds.

### 41. Allocation Runtime Status

Statuses are:

- `REQUESTED`
- `WAITING`
- `ALLOCATED`
- `ACTIVE`
- `RELEASED`
- `FAILED`
- `EXPIRED`

`RELEASED`, `FAILED`, and `EXPIRED` are terminal.

### 42. Runtime Lifecycle

```text
REQUESTED -> ALLOCATED -> ACTIVE -> RELEASED
     |            |          |
     |            |          +-> FAILED / EXPIRED
     |            +------------> FAILED / EXPIRED / RELEASED
     +-> WAITING -> ALLOCATED
     |       |
     |       +-> FAILED / EXPIRED
     +-> FAILED / EXPIRED
```

`WAITING` never retries internally. A later explicit Planning submission may
request reevaluation. Backward transitions and terminal transitions are
prohibited.

### 43. Allocation Report

One immutable report per Allocation Cycle contains:

- successful, waiting, rejected, failed, released, and expired set ids;
- Commitment ids;
- conflict records;
- observed, committed, and remaining Capacity by key;
- fairness context;
- stage counts and bounded-work consumption;
- typed failures and truncation;
- policy id, tick, and schema version.

### 44. Allocation Graph

```text
Approved Plan
  -> execution-owned work definition
  -> Allocation Request
  -> Allocation Set
  -> Requirements
  -> observed Resources and Capacity
  -> Commitments
  -> ACTIVE set gate
  -> execution runtime
```

### 45. Allocation Ownership

Allocation owns only Requests accepted into its domain, Commitments, set
runtime, cycle snapshots, ledgers, and reports. External systems retain their
definitions and runtime.

### 46. Schema-1 Workforce Scope

Individual workers do not yet exist as authoritative runtime resources.
Workforce providers may expose only aggregate Capacity by:

- position;
- shift;
- role.

Schema 1 must not fabricate employee identities or claim individual-worker
exclusivity.

### 47. Domain Invariants

- `RA-0001`: Resource and Capacity authority remains external.
- `RA-0002`: Allocation owns Commitments only.
- `RA-0003`: Capacity never becomes negative.
- `RA-0004`: A Capacity key is never overcommitted.
- `RA-0005`: Requirements and Commitments are immutable.
- `RA-0006`: Set runtime remains separate from immutable definitions.
- `RA-0007`: Execution requires one complete ACTIVE AllocationSet.
- `RA-0008`: Released Capacity is reusable in the next cycle.
- `RA-0009`: Allocation decisions are deterministic.
- `RA-0010`: Pre-commit runtime never requires a nonexistent Commitment id.

## Part III: Deterministic Allocation Pipeline

### 48. Pipeline Philosophy

Allocation transforms validated execution demand and authoritative observed
Capacity into temporary Commitments. It performs no execution.

### 49. Canonical Pipeline

Every cycle executes each phase at most once:

```text
Observe Resources
  -> Observe Existing Commitments
  -> Determine Available Capacity
  -> Validate Allocation Requests
  -> Detect Conflicts
  -> Evaluate Capacity
  -> Allocate Resources
  -> Commit Allocations
  -> Publish Allocation Report
```

### 50. Allocation Cycle

Exactly one `AllocationCycleId` is derived for each authoritative Planning
Cycle. Allocation has no independent simulation loop. The Scheduler executes
one `butchercraft:allocation_cycle` Work item.

### 51. Resource Observation

Registered read-only providers return ordered `ObservedResourceSnapshot` and
`ObservedCapacitySnapshot` values for the submitted execution references.
Providers declare stable ids, supported categories, deterministic limits, and
typed failures.

### 52. Existing Commitments

Before allocating, the cycle observes all nonterminal Commitments and runtime.
`ALLOCATED` and `ACTIVE` sets consume Capacity. Terminal sets do not.

### 53. Available Capacity

The cycle constructs a detached ledger from snapshots and active Commitments.
Unknown Resources, mismatched units, duplicate Capacity keys, stale observation
ticks, arithmetic failures, and negative remaining Capacity fail explicitly.

### 54. Request Validation

Validation resolves:

- request, set, Requirement, Planning Cycle, Approved Plan, and executable work
  provenance;
- registered provider support;
- Resource and Capacity snapshots;
- quantity and unit compatibility;
- current authoritative work eligibility;
- duplicate ids and Capacity keys;
- expiration and tick consistency.

### 55. Conflict Detection

Structurally valid sets conflict when their Requirements overlap one or more
finite Capacity keys. Malformed sets do not enter conflict arbitration.

### 56. Capacity Evaluation

Evaluation results are:

- `ALLOCATABLE`
- `WAITING`
- `FAILED`

Insufficient Capacity may include a diagnostic partial amount, but `PARTIAL`
is not a committable schema-1 result.

### 57. Partial Allocation

Partial Allocation is prohibited. Every Requirement in the set succeeds or the
set receives no Commitment.

### 58. Canonical Request Ordering

Requests are ordered by:

1. horizon precedence;
2. priority descending;
3. required-by tick ascending, absent last;
4. starvation age descending;
5. Need creation tick ascending;
6. stable request sequence ascending;
7. `AllocationRequestId`.

Starvation age is `current simulation tick - Need creation tick`, checked for
underflow and never persisted as an authority.

### 59. Allocation Policy

Schema 1 uses deterministic first-fit, complete-set allocation. Policy identity
and version are explicit and persisted in reports.

### 60. Commitment Creation

Successful Requirement-to-Capacity matches derive stable Commitment ids from
cycle, set, Requirement, Resource, and Capacity identities. Duplicate semantic
Commitments fail.

### 61. Atomic Cycle Publication

Each set uses a private ledger copy. Successful sets update the staged cycle
ledger; failed or waiting sets leave it unchanged. No Commitment becomes
authoritative until every cycle phase completes and the complete candidate
registry, runtime changes, and report validate.

A nonfatal set failure does not invalidate unrelated successful sets. A fatal
cycle failure publishes no staged Commitment or runtime transition.

### 62. Allocation Failure

Allocation records explicit set and cycle failures. It does not create a hidden
retry. Planning may submit a later explicit reevaluation.

### 63. Capacity Exhaustion

Insufficient Capacity produces `WAITING` unless policy or structural validation
requires `FAILED`. The report retains the exact Capacity shortfall.

### 64. Resource Exclusivity

Exclusive Resources expose Capacity of exactly one compatible unit or an
equivalent provider-declared exact amount. The ledger prevents a second active
Commitment.

### 65. Allocation Reports

Reports explain winners, losers, Capacity use, conflicts, ordering context,
failures, releases, and bounded remainder without becoming runtime authority.

### 66. Allocation Release

Execution submits a typed release request for completion, cancellation, or
failure. Allocation validates and owns the transition. Release never creates a
Transaction or modifies the provider.

### 67. Allocation Replay

Replay from identical snapshots, active Commitments, request batch, policy,
sequence state, and tick produces byte-stable domain results.

### 68. Scheduler Interaction

The Scheduler adds:

```text
300  butchercraft:planning
350  butchercraft:allocation
400  butchercraft:execution
```

Planning's submission adapter remains responsible for scheduling Allocation
Work and execution-owned Work. Allocation schedules nothing.

Schema 1 schedules both for no earlier than the next authoritative tick. The
Allocation Work runs first. Execution Work must defer or fail explicitly when
its AllocationSet is not ACTIVE.

### 69. Living World Rule

The pipeline is pure domain work invoked by Scheduler authority and remains
correct without players or loaded Minecraft content.

### 70. Pipeline Invariants

- `AL-0001`: Each cycle phase executes at most once.
- `AL-0002`: Allocation never owns Resources.
- `AL-0003`: Capacity never becomes negative.
- `AL-0004`: Capacity is never committed twice.
- `AL-0005`: Execution begins only through an ACTIVE complete set.
- `AL-0006`: Commitments are immutable.
- `AL-0007`: Runtime transitions are explicit.
- `AL-0008`: Replay is deterministic.
- `AL-0009`: Publication is atomic.
- `AL-0010`: Scheduler remains Work authority.

### 71. Part III Result

Allocation is now a deterministic Scheduler stage and commitment gate, not a
replacement owner for Planning or execution.

## Part IV: Conflict Resolution, Fairness, And Multi-Resource Allocation

### 72. Conflict Philosophy

Conflict is an expected domain result. One set losing Capacity does not imply a
cycle exception or corruption.

### 73. Definition Of Conflict

A conflict exists when two or more valid sets require overlapping finite
Capacity in the same cycle.

### 74. Conflict Types

Schema-1 conflict categories are:

- `CAPACITY`
- `EXCLUSIVITY`
- `DEPENDENCY`
- `CHAIN`
- `UNSUPPORTED`

### 75. Conflict Graph

Conflict nodes are Allocation Requests. For shared Capacity, direct an edge
from the later canonical request to the earlier canonical request. Explicit
dependency edges must follow the same canonical direction. This orientation
makes graph construction deterministic and acyclic; self-edges, reverse
dependencies, and detected cycles fail the cycle.

### 76. Conflict Resolution Sequence

```text
sort requests
  -> detect shared Capacity
  -> construct directed graph
  -> validate graph
  -> evaluate sets
  -> stage Commitments
  -> publish cycle
```

### 77. Canonical Ordering

The comparator in section 58 is the only schema-1 arbitration order. Weighted
scores, floating-point ranking, and random tie breaks are prohibited.

### 78. Capacity Arbitration

For each set in canonical order, evaluate all Requirements against a detached
copy of the current staged ledger. Earlier successful sets reduce Capacity
visible to later sets.

### 79. Atomic Capacity

A set commits all Requirements or none. No successful Requirement from a
failed set changes the staged ledger.

### 80. Multi-Resource Requirements

One work definition may require, for example, workforce slots, a production
slot, and storage volume. All must succeed before Commitment publication.

### 81. Allocation Set Authority

The set is Allocation's atomic unit. It groups demand but does not own the
execution definition it references.

### 82. Allocation Set Identity

`AllocationSetId` is derived deterministically from the source Approved Plan,
execution-owned work reference, request identity, and schema version.

### 83. Allocation Set Validation

Set validation follows section 38 and runs before conflict graph construction.

### 84. Set Allocation Algorithm

1. Copy the current staged ledger.
2. Resolve Requirements in canonical Requirement-id order.
3. Match each Requirement to provider snapshots in Resource-id then Capacity-id
   order.
4. Reject ambiguous or unsupported matches.
5. Subtract exact quantities.
6. Validate no negative or mismatched unit.
7. Stage every Commitment and the updated ledger only if all Requirements
   succeed.

### 85. Ledger Isolation

Temporary ledger state is private to one evaluation. Only a successful set may
replace the staged cycle ledger. Only a successful cycle may publish the staged
ledger consequences as Commitments.

### 86. Fairness

Fairness is captured, inspectable ordering context. It is not random, mutable
priority inflation, or a persisted score.

### 87. Starvation Age

Starvation age is derived exactly from authoritative simulation time and the
captured Need creation tick.

### 88. Fairness Boundary

Horizon, priority, and required-by precedence remain stronger than starvation
age. Within that class, older Needs win deterministically.

### 89. Exclusive Resources

Exclusive snapshots permit one compatible active commitment at a time.
Aggregate workforce capacity is shared rather than individual-worker
exclusivity in schema 1.

### 90. Shared Capacity

Divisible Capacity uses exact `AllocationQuantity`. Every Commitment retains
the exact quantity and unit.

### 91. Capacity Units

Allocation never converts units. Providers and execution adapters must agree on
one `CapacityUnitId` before request acceptance.

### 92. Capacity Ledger

Each entry contains the Capacity key, observed quantity, already committed
quantity, cycle-staged quantity, and remaining quantity.

### 93. Ledger Invariants

Every arithmetic operation is exact and checked. Missing entries, duplicate
entries, mismatched units, and negative results fail visibly.

### 94. Allocation Lifetime

Set runtime follows section 42. Commitment definitions remain immutable across
activation and release.

### 95. Release Ordering

Release is recorded at an authoritative simulation tick. Capacity becomes
eligible during the next Allocation Cycle; schema 1 performs no recursive
same-cycle reallocation.

### 96. Expiration

An Allocation Cycle expires eligible nonterminal sets whose expiration tick is
before the current tick. Expiration releases their Commitments without creating
Transactions or Inventory changes.

### 97. Reallocation

Later Planning intent may explicitly resubmit a waiting set. Released Capacity
is considered from fresh authoritative snapshots in the next cycle.

### 98. Deadlock Prevention

Atomic sets and prohibited recursive allocation prevent hold-and-wait deadlock
in schema 1.

### 99. Recursive Allocation

Providers, adapters, and execution activation may not invoke a nested
Allocation Cycle.

### 100. Deferred Extensions

Schema 1 excludes partial allocation, distributed Capacity, preemption,
reservation windows, migration of active Commitments, priority inheritance,
and dynamic mid-cycle Capacity.

### 101. Conflict Reports

Reports retain successful and unsuccessful set ids, conflicting Capacity keys,
canonical winner and loser order, exact shortfall, fairness context, and release
results.

### 102. Conflict Invariants

- `AL-0011`: AllocationSets remain atomic.
- `AL-0012`: The Capacity ledger never becomes negative.
- `AL-0013`: Temporary ledgers never become authoritative.
- `AL-0014`: Conflict graphs remain acyclic.
- `AL-0015`: Recursive allocation is prohibited.
- `AL-0016`: Partial allocation is prohibited.
- `AL-0017`: Released Capacity is reconsidered next cycle.
- `AL-0018`: Fairness remains deterministic.
- `AL-0019`: Resource ownership remains external.
- `AL-0020`: Execution starts only after accepted activation.

### 103. Part IV Result

Multi-resource work is all-or-nothing, deterministic, bounded, and free of
private execution-side reservation.

## Part V: Runtime, Persistence, Services, And Public Contracts

### 104. Runtime Philosophy

Definitions, observations, Requirements, Requests, Sets, Commitments, and
reports are immutable. Only `AllocationSetRuntime` is mutable.

### 105. Runtime Ownership

Allocation runtime never modifies source snapshots, execution definitions,
Planning artifacts, or report history.

### 106. Allocation Set Runtime

Required fields:

- `AllocationSetId`;
- runtime status;
- created tick;
- optional waiting tick;
- optional allocated tick;
- optional activated tick;
- optional released tick;
- optional expiration tick;
- last-updated tick;
- ordered Commitment ids;
- optional failure code and message;
- bounded runtime metadata;
- revision;
- schema version.

### 107. Runtime Status

Runtime uses the statuses and terminal rules in sections 41 and 42.

### 108. Runtime Transitions

Only `AllocationManager` performs transitions through typed requests.
Transition tick and revision are monotonic. Terminal runtime is immutable.

### 109. Execution Activation

Execution submits an activation request identifying its execution Work and
AllocationSet. Allocation validates:

- every set Requirement has exactly one Commitment;
- every Commitment is still valid and nonexpired;
- execution Work matches the set's external reference;
- the set is `ALLOCATED`;
- no Capacity invariant is violated.

An accepted request transitions the set to `ACTIVE`. Execution begins only
after receiving that accepted immutable runtime snapshot.

### 110. Deterministic Registries

Allocation owns deterministic registries for:

- cycle observation snapshots;
- Requirements;
- Requests;
- AllocationSets;
- Commitments;
- set runtime;
- reports.

Registries reject duplicates and expose immutable views. Resource providers
remain external.

### 111. Registry Ordering

Ordering uses explicit comparators and stable ids. Hash-map, filesystem, locale,
and insertion order never decide allocation.

### 112. Allocation Manager

Responsibilities:

- register validated request batches;
- execute one bounded Allocation Cycle;
- activate, release, fail, and expire sets;
- query immutable history and Capacity use;
- validate and publish persistence snapshots.

Forbidden responsibilities remain those in section 12.

### 113. Queries

Queries include:

- Commitments by Resource, execution work, set, and Requirement;
- sets by status and Planning Cycle;
- active, released, expired, waiting, and failed sets;
- observed, committed, and remaining Capacity;
- reports by cycle and tick.

Queries never mutate runtime.

### 114. Persistence

Schema-1 Allocation state persists at:

```text
<world>/butchercraft/allocation_observations.json
<world>/butchercraft/allocation_requests.json
<world>/butchercraft/allocation_commitments.json
<world>/butchercraft/allocation_runtime.json
<world>/butchercraft/allocation_reports.json
```

`allocation_observations.json` contains cycle-scoped observed Resource and
Capacity snapshots only. It never becomes Resource authority.

`allocation_requests.json` contains Requirements, Requests, Sets, ordering
context, and external references.

All five files form one complete validated Allocation persistence set.

### 115. Save Ordering

Shutdown persistence follows reference ownership:

1. execution owners and Scheduler persist their definitions and Work;
2. Planning persists Approved Plan provenance;
3. Allocation persists last because it references all preceding authorities.

No cross-file filesystem transaction is claimed. A startup mismatch fails
explicitly.

### 116. Load Ordering

1. authoritative providers and execution definitions load;
2. Production, Planning, and Allocation handlers are installed;
3. Scheduler loads with every persisted Work type resolvable;
4. execution owners validate their Scheduler Work references;
5. Planning loads and validates its Scheduler references;
6. Allocation loads and validates Planning, execution, provider, and Scheduler
   references;
7. Scheduler tick execution begins.

The integration layer may adjust listener construction to satisfy this order
without introducing circular domain imports.

### 117. Atomic Publication

Allocation publishes a loaded or newly completed candidate state only after all
five registries, runtime transitions, reports, and external references validate.

### 118. Cross References

Unknown Resource providers, Planning Cycles, Approved Plans, execution-owned
work definitions, Scheduler Work, Requirements, Sets, Capacity snapshots, and
Commitments fail explicitly. Silent repair and deletion are prohibited.

### 119. Scheduler Integration

Work type:

```text
butchercraft:allocation_cycle
```

Stage:

```text
butchercraft:allocation
```

Stage order is 350, allows same-tick enqueue for future compatible workflows,
and remains between Planning and Execution. Schema 1 schedules work no earlier
than the tick after submission.

### 120. Execution Handoff

The Planning submission adapter:

1. asks the authoritative execution subsystem to register the executable work
   definition and runtime;
2. creates the immutable Allocation Request and Set through Allocation
   authority;
3. schedules Allocation Work and execution Work for the same future tick;
4. records all stable references in Planning submission runtime.

Allocation creates Commitments only. It schedules nothing. Execution checks and
activates its complete set before any progress or side effect.

Cross-owner submission must validate all candidate objects first and expose
explicit compensation for a rejected in-memory batch. It does not claim an
atomic filesystem transaction.

### 121. Public Contracts

The initial documented internal contracts are:

- `AllocationManager`;
- immutable Allocation registries and snapshots;
- typed provider observation interface;
- typed request-batch submission;
- typed activation and release requests;
- immutable Allocation reports.

They are not a stable third-party API until a real expansion consumer and a
separate API decision approve that commitment.

### 122. Service Lifecycle

`AllocationService` is a Minecraft boundary adapter only. It owns world paths
and lifecycle binding, not Clock or Scheduler. The pure Allocation domain has
no Minecraft or NeoForge imports.

### 123. Shutdown

Stop cycle execution, reject in-progress publication, save reference owners in
section 115 order, save Allocation, clear service references, and retain
terminal history.

### 124. Persistence Invariants

- `AR-0001`: Definitions and observations are immutable.
- `AR-0002`: Set runtime is separately mutable.
- `AR-0003`: Candidate publication is atomic.
- `AR-0004`: Registries are deterministic.
- `AR-0005`: Unknown references fail.
- `AR-0006`: Persistence is deterministic and versioned.
- `AR-0007`: Duplicate Commitments fail.
- `AR-0008`: Capacity remains consistent.
- `AR-0009`: Reports are immutable.
- `AR-0010`: Execution remains external.

### 125. Required ADR And Compatibility Decision

Implementation is blocked until the owner accepts an ADR with these decisions:

1. Preserve DEC-0073 and DEC-0074 ownership.
2. Establish Allocation as the sole execution-capacity Commitment authority.
3. Add Scheduler stage `butchercraft:allocation` at order 350.
4. Approve deterministic Scheduler persistence migration from schema 1:
   - require the exact known schema-1 built-in stage set;
   - insert the Allocation stage without changing existing ids, Work,
     submission sequences, runtime, or finalized tick;
   - reject conflicting stage id/order or malformed source state;
   - write schema 2 only after complete validation.
5. Approve additive Planning persistence evolution for Allocation Cycle, Set,
   and Work references.
6. Approve additive Production persistence evolution for required
   `AllocationSetId` execution gating.
7. Record that older schema-1 worlds either migrate deterministically or fail
   visibly; no silent stage insertion occurs outside the migration.

No migration is implemented by this architecture revision.

## Part VI: Verification And Acceptance

### 126. Verification Philosophy

Tests demonstrate behavior; architecture review demonstrates that behavior
respects singular ownership, determinism, compatibility, and boundaries.

### 127. Verification Categories

Verify identity, ownership, pipeline phases, conflict resolution, fairness,
atomic sets, ledgers, publication, runtime, release, persistence, replay,
Scheduler integration, performance, living-world behavior, and architecture.

### 128. Identity Verification

Every identifier in section 24 must prove canonical validation, stable equality,
ordering, deterministic derivation, duplicate rejection, and persistence.

### 129. Ownership Verification

Architecture tests must prove:

- Planning owns only decision artifacts;
- Allocation owns only Commitments and set runtime;
- Production owns Production Plans and Runs;
- providers own Resources and Capacity;
- Scheduler owns Work runtime;
- Transactions and Inventory retain mutation authority.

### 130. Pipeline Verification

Verify every canonical phase executes at most once and receives only immutable
output from prior phases.

### 131. Conflict Verification

Identical high-contention inputs produce identical graph edges, winners,
waiting sets, failures, and reports regardless of insertion order.

### 132. Fairness Verification

Verify horizon, priority, and deadline precedence; then verify older Needs win
within an equivalent class. Do not claim starvation prevention across an
unbounded stream of higher-order work.

### 133. Allocation Set Verification

Every Requirement succeeds or no Commitment from the set is published.

### 134. Capacity Ledger Verification

Verify exact arithmetic, shared and exclusive Capacity, active Commitment
subtraction, no negative remaining value, and detached rollback.

### 135. Publication Verification

Verify successful sets publish together only after the whole cycle validates.
A fatal cycle failure publishes none. A nonfatal set failure does not discard
unrelated successful staged sets.

### 136. Runtime Verification

Verify every allowed transition in section 42, rejection of backward and
terminal transitions, monotonic ticks and revisions, activation completeness,
and immutable snapshots.

### 137. Release Verification

Verify completion, cancellation, failure, and expiration through typed
Allocation requests. Released Capacity appears in the next cycle and never
causes recursive same-cycle allocation.

### 138. Persistence Verification

Verify five-file round trips, deterministic bytes, complete-set loading,
temporary replacement, schema validation, duplicates, malformed JSON, unknown
references, unsupported versions, and no silent repair.

### 139. Replay Verification

Replay identical snapshots, request sequence, Commitments, policy, tick, and
Scheduler ordering across construction order and save/load. Results must match.

### 140. Scheduler Integration Verification

Verify Planning schedules both Work items, Allocation runs at stage 350,
Allocation schedules no Work, Execution runs at stage 400, and Execution does
no work before accepted set activation.

### 141. Performance Verification

Stress tests must include split deterministic datasets representing:

- at least 1,000,000 observed Resources;
- at least 1,000,000 Capacity snapshots;
- at least 1,000,000 Requirements;
- at least 250,000 AllocationSets;
- high-contention shared and exclusive ledgers;
- representative persistence and reload.

Measure actual construction, validation, conflict, allocation, publication,
query, persistence, and reload timings. Wall-clock measurements are diagnostic
only and never control outcomes.

### 142. Living World Verification

Run the pure pipeline and Scheduler integration without players, GUI,
networking, blocks, ItemStacks, or client classes.

### 143. Failure Isolation

One invalid or insufficient set leaves unrelated staged sets valid. Provider,
arithmetic, graph, persistence, and fatal cycle failures never corrupt the
authoritative registry or ledger.

### 144. Save Compatibility

Test the owner-approved Scheduler, Planning, and Production schema migrations.
Unsupported or ambiguous older saves fail explicitly.

### 145. Repository Requirements

Implementation must include architecture documentation, ADR acceptance,
deterministic domain tests, stress tests, replay tests, persistence and
migration tests, regression tests, invariant tests, and dependency-boundary
tests.

### 146. Architecture Gates

Implementation cannot be accepted until constitutional invariants, ownership,
determinism, replay, persistence, migration, Scheduler integration, and living
world operation pass review.

### 147. Acceptance Criteria

RFC-0022 implementation is complete only when:

- Allocation is deterministic;
- Capacity never becomes negative or double committed;
- AllocationSets remain atomic;
- conflict and fairness ordering match the contract;
- publication and persistence are atomic at their declared boundaries;
- runtime, activation, release, and expiration are valid;
- replay and schema migrations pass;
- Scheduler and ownership boundaries remain intact;
- autonomous simulation remains operational.

### 148. Completion Report

Report architecture implemented, internal public contracts, persistence,
migrations, Scheduler integration, test totals, stress measurements, replay,
known limitations, future extension points, and repository actions.

### 149. Future Expansion

Future schemas may add partial Allocation, distributed Capacity, preemption,
reservation windows, priority inheritance, dynamic Capacity, Commitment
migration, and individual-worker resources through separate approved designs.

### 150. Final Principle

```text
Planning decides.
Allocation commits execution Capacity.
Execution performs work.
Transactions mutate economic state.
Inventory owns quantities.
Providers own Resources and Capacity.
```

### 151. End RFC-0022 Revision 2

Revision 2 is architecturally reconciled with DEC-0073 and DEC-0074. It is
ready for owner review, but implementation remains prohibited until the ADR and
compatibility decisions in section 125 are explicitly approved.
