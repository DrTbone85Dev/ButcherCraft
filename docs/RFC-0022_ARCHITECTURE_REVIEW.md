# RFC-0022 Revision 2 Architecture Review

Status: Reconciled architecture; M22A-M22D owner-authorized and implemented,
remaining RFC milestones awaiting separate approval

Reviewed against:

- [`CONSTITUTION.md`](../CONSTITUTION.md)
- [`CORE_PRINCIPLES.md`](../CORE_PRINCIPLES.md)
- [`DECISIONS.md`](../DECISIONS.md), including DEC-0073 and DEC-0074
- RFC-0022 Revision 1 Parts I-VI
- [`RFC-0022_RESOURCE_ALLOCATION_ENGINE.md`](RFC-0022_RESOURCE_ALLOCATION_ENGINE.md)

This review originally changed documentation only. DEC-0076 subsequently
authorized M22A's immutable Core Allocation Domain, DEC-0077 authorized M22B's
lifecycle, registries, immutable queries, history, and report structures, and
DEC-0078 authorized M22C's pure explicit-input Cycle, detached Capacity
accounting, deterministic selection, and atomic in-memory publication.
DEC-0079 authorized M22D's generic immutable provider registry, canonical
observation service, failure isolation, and observation bundles. It does not
authorize production-grade concrete providers, Scheduler, persistence,
Planning, Production, or execution integration.

## Executive Finding

Revision 1 established a sound need for one deterministic Capacity Commitment
authority, but several contracts conflicted with accepted Production and
Planning ownership or could not be implemented deterministically from the
specified fields.

Revision 2 resolves those conflicts without superseding DEC-0073 or DEC-0074:

```text
Planning owns Candidate Plans and Approved Plans.
Execution subsystems own executable definitions and runtime.
Allocation owns temporary Capacity Commitments and AllocationSet runtime.
Providers own Resources and Capacity.
Scheduler owns Work eligibility.
```

One owner-approved ADR and explicit Scheduler, Planning, and Production schema
compatibility decisions remain mandatory before implementation.

## Required Corrections

### 1. Executable Plan Ownership

**Revision 1 issue:** It stated that Planning owns Plans and that Allocation
operates directly on executable Plans.

**Correction:** Planning owns Candidate and Approved Plans only. Allocation
references execution-owned work through `ExternalReference`. Production
continues to own `ProductionPlanDefinition` and `ProductionRunRuntime`.

**Reason:** DEC-0073 assigns executable Production definitions and runtime to
Production. DEC-0074 assigns decision artifacts, not execution, to Planning.

**Constitutional references:** `AI-0016`, `AI-0019`, `AI-0023`, `AI-0025`.

**Affected RFC sections:** 1, 3, 7, 10, 20-23, 32-39, 44-45, 68, 80-83,
104-125, 129-130, 140, 150.

**Implementation impact:** The Planning submission adapter must obtain an
execution-owned definition from the owning subsystem and provide Allocation
only stable work references and Requirements. Allocation must not construct
Production Plans or Runs.

### 2. Scheduler Allocation Stage

**Revision 1 issue:** Allocation was placed conceptually between Planning and
Execution but no deterministic Scheduler stage or persistence impact was
defined.

**Correction:** Add `butchercraft:allocation` at execution order 350 between
Planning at 300 and Execution at 400.

**Reason:** Scheduler stage ordering must make the commitment gate observable,
deterministic, bounded, and player-independent.

**Constitutional references:** `AI-0001`, `AI-0011`, `AI-0018`, `AI-0022`,
`AI-0026`, `AI-0028`.

**Affected RFC sections:** 10, 49-50, 68, 119-120, 138-146.

**Implementation impact:** Built-in Scheduler stages and persisted stage
validation require schema migration. No migration is implemented by this task.

### 3. Execution Handoff

**Revision 1 issue:** It required Planning to submit Allocation Work and
Allocation to submit no Execution Work, but did not identify who schedules
execution or how execution is gated.

**Correction:** The Planning submission adapter remains responsible for
scheduling both Allocation Work and execution-owned Work for the same future
tick. Allocation schedules nothing. Execution submits a typed activation
request and refuses all progress and side effects unless its complete set is
`ACTIVE`.

**Reason:** This preserves Planning's submission responsibility, Scheduler
authority, Allocation's narrow Commitment ownership, and execution ownership.

**Constitutional references:** `AI-0016`, `AI-0017`, `AI-0021`, `AI-0023`,
`AI-0025`.

**Affected RFC sections:** 8, 10-12, 48-50, 60-68, 109, 119-123, 129, 140.

**Implementation impact:** Planning submission runtime gains Allocation
references. Execution-owned Work gains a required `AllocationSetId` and an
activation gate. Cross-owner in-memory submission requires validation and
explicit compensation behavior.

### 4. Pre-Commit Runtime Identity

**Revision 1 issue:** `AllocationRuntime` required an
`AllocationCommitmentId` while representing `REQUESTED` and `WAITING`, before a
Commitment exists.

**Correction:** `AllocationSetRuntime` is keyed by `AllocationSetId`.
Commitment ids are empty before successful allocation and become an immutable
ordered list at `ALLOCATED`.

**Reason:** Identity cannot depend on a future outcome. Mutable lifecycle must
remain separate from immutable Commitment definitions.

**Constitutional references:** `AI-0004`, `AI-0005`, `AI-0020`, `AI-0021`,
`AI-0025`.

**Affected RFC sections:** 23, 37-43, 60-67, 94-96, 104-110, 123-124, 136-138.

**Implementation impact:** Persistence and queries are set-centric. Execution
activates and releases the set through Allocation authority.

### 5. Ordering Metadata

**Revision 1 issue:** The comparator required horizon, required-by tick,
starvation age, Need creation tick, and stable sequence, but Request fields did
not carry them.

**Correction:** Add immutable `AllocationOrderingContext` to every Request.
Capture horizon, priority, required-by tick, Need creation tick, Planning Cycle,
Approved Plan, request creation tick, and manager-assigned stable sequence.

**Reason:** Arbitration must not reread mutable Planning state or depend on
registry insertion order.

**Constitutional references:** `AI-0001`, `AI-0009`, `AI-0010`, `AI-0021`,
`AI-0024`, `AI-0026`.

**Affected RFC sections:** 35-36, 50, 58-59, 67, 77, 86-88, 101, 128,
131-132, 139.

**Implementation impact:** The submission adapter snapshots ordering facts.
Replay inputs include request-sequence state.

### 6. Resource And Capacity Authority

**Revision 1 issue:** It stated that Resources remain externally owned but also
introduced Allocation-owned Resource and Capacity definitions and persistence.

**Correction:** Replace those definitions with cycle-scoped
`ObservedResourceSnapshot` and `ObservedCapacitySnapshot`. Each carries provider
provenance, external reference, and observation tick.

**Reason:** Persisting a second authoritative Resource catalog would violate
singular ownership and could diverge from Workforce, Inventory, Production, or
future providers.

**Constitutional references:** `AI-0004`, `AI-0005`, `AI-0010`, `AI-0011`,
`AI-0016`, `AI-0025`.

**Affected RFC sections:** 5, 7, 17, 23-31, 39, 44-47, 51-54, 90-93,
104-118, 124, 129, 134, 138.

**Implementation impact:** Providers remain read-only authorities. Allocation
persistence stores replay evidence, not live provider definitions.

### 7. Generic Exact Capacity Model

**Revision 1 issue:** Capacity amount and Unit were named but no generic exact
types, comparison rules, or conversion boundary was defined.

**Correction:** Introduce `AllocationQuantity`, `CapacityTypeId`, and
`CapacityUnitId`. Arithmetic is exact, bounded, canonical, and unit conversion
is prohibited.

**Reason:** Goods units cannot represent every workforce, storage, utility,
dock, inspection, and infrastructure Capacity without industry leakage.

**Constitutional references:** `AI-0001`, `AI-0015`, `AI-0021`, `AI-0026`.

**Affected RFC sections:** 28-34, 39, 53-64, 78-93, 101, 128, 134, 141.

**Implementation impact:** Resource adapters must emit one exact unit accepted
by the corresponding Requirement. Allocation contains no conversion tables.

### 8. Schema-1 Workforce Scope

**Revision 1 issue:** Worker Resources were presented as individual Resources,
but the current Workforce foundation defines aggregate positions and shift
assignments and has no individual-worker runtime.

**Correction:** Schema 1 supports aggregate Workforce Capacity by position,
shift, and role only.

**Reason:** Inventing employee identities or occupancy would create an
unauthorized runtime owner and speculative system.

**Constitutional references:** `AI-0005`, `AI-0015`, `AI-0016`, `AI-0025`.

**Affected RFC sections:** 5, 7, 19, 26, 46, 51, 80, 89, 129, 141, 149.

**Implementation impact:** Initial Workforce providers expose aggregate slots.
Individual-worker exclusivity is deferred to a future approved schema.

### 9. Architecture Consistency

**Revision 1 issue:** Several supporting rules contradicted one another even
after the eight requested corrections.

**Correction:** The additional reconciliations below were incorporated before
implementation.

**Constitutional references:** `AI-0001`, `AI-0016`, `AI-0019`, `AI-0021`,
`AI-0025`, `AI-0028`.

**Affected RFC sections:** Listed by each additional correction.

## Additional Reconciliations

### Fairness Guarantee

Revision 1 claimed older work could never starve, while also prohibiting
fairness from overriding horizon, priority, and deadline. Both cannot be
guaranteed under an infinite stream of higher-order work.

Revision 2 guarantees age preference only within an equivalent higher-order
class and reports the limitation explicitly.

Affected sections: 15, 58, 77, 86-88, 101, 132.

### Partial Evaluation Outcome

Revision 1 listed `PARTIAL` as an evaluation outcome while prohibiting partial
Allocation. Revision 2 permits diagnostic shortfall data but only
`ALLOCATABLE`, `WAITING`, or `FAILED` as set outcomes.

Affected sections: 56-57, 63, 79, 100, 133.

### Conflict Graph Direction

Shared Capacity is naturally an undirected relationship, but Revision 1
required a directed acyclic graph without defining edge orientation.

Revision 2 directs every conflict edge from the later canonical Request to the
earlier canonical Request. Reverse dependencies and cycles fail explicitly.

Affected sections: 55, 67, 73-76, 102, 131.

### Publication And Failure Isolation

Revision 1 required whole-cycle atomic publication and also required one set
failure not to invalidate unrelated sets.

Revision 2 stages every successful set privately and publishes all successful
sets together after complete cycle validation. Nonfatal set failures remain
reported; fatal cycle failures publish nothing.

Affected sections: 61-62, 79, 84-85, 117, 135, 143.

### Release Timing

Revision 1 described release as immediate and also prohibited same-cycle
recursive reallocation.

Revision 2 records release immediately at the supplied simulation tick but
makes Capacity eligible during the next Allocation Cycle.

Affected sections: 16, 42, 66, 95-99, 137.

### Persistence Set And Ordering

Revision 1 suggested files that omitted Requirements, Requests, and Sets while
risking duplicate Resource authority.

Revision 2 defines five complete files: observations, requests, Commitments,
runtime, and reports. Reference owners save first and Allocation saves last.
Allocation loads after its external authorities.

Affected sections: 114-118, 123-125, 138, 144.

### API Stability

Revision 1 called Allocation contracts public before an expansion consumer
exists. Revision 2 documents internal contracts but withholds stable third-party
API status until a separate owner decision.

Affected sections: 121, 145, 149.

## Constitutional Compliance Matrix

| Constitutional invariant | Revision 2 compliance |
| --- | --- |
| `AI-0001` Deterministic Simulation | Exact arithmetic, stable comparators, explicit ticks and sequences |
| `AI-0003` Pure Domain Isolation | Allocation domain remains Java-only; lifecycle stays at the boundary |
| `AI-0004` Immutable Identity Separation | Set runtime is separate from snapshots and Commitments |
| `AI-0009` Deterministic Registries | Duplicate rejection and explicit ordering are required |
| `AI-0010` Immutable Public Views | Queries return immutable snapshots |
| `AI-0011` Save Compatibility Priority | Explicit migrations and fail-visible unsupported state |
| `AI-0015` Industry-Neutral Core | Generic categories, quantities, units, providers, and references |
| `AI-0016` Explicit Responsibility Boundaries | Planning, Allocation, Execution, Scheduler, provider, Transaction, and Inventory owners remain distinct |
| `AI-0017` Validation Before Execution | Complete ACTIVE set is mandatory before execution |
| `AI-0018` Versioned Persistence | Five-file schema and migration gates are explicit |
| `AI-0019` Formal Invariant Change Control | No invariant is changed; follow-on architecture still requires an ADR |
| `AI-0021` Explicit Failure Outcomes | Invalid requests, conflicts, shortfalls, migrations, and references fail visibly |
| `AI-0022` Authoritative Simulation Time | Allocation uses supplied Clock ticks only |
| `AI-0023` Downward Dependency Flow | Integration adapters translate between owners; no provider imports Allocation policy |
| `AI-0025` Singular Data Ownership | Allocation persists observations and Commitments, not competing Resources |
| `AI-0026` Bounded Simulation Work | Cycle, provider, graph, request, set, and artifact limits are implementation requirements |
| `AI-0028` Backward-Compatible Evolution | Scheduler, Planning, and Production migrations require acceptance and tests |

No constitutional invariant requires amendment.

## Implementation Impact Summary

M22A-M22D now provide the pure Allocation domain, runtime, Cycle, and generic
provider observation framework. Remaining integration will require:

- production-grade provider adapters for aggregate Workforce, Inventory
  storage, and Production Capacity proven by schema-1 execution;
- Scheduler stage and schema migration;
- Allocation handler and service lifecycle integration;
- Planning submission changes that create Allocation provenance and schedule
  both Work items;
- Production Plan/Run persistence changes for required AllocationSet gating;
- typed activation and release boundaries;
- five-file deterministic Allocation persistence;
- architecture, migration, replay, stress, ownership, and regression tests.

Implementation must not introduce individual employees, Goods reservation,
partial Allocation, logistics, utilities, gameplay, GUI, networking, or
industry-specific resource semantics.

## Remaining ADR Requirements

Before M22E-M22F integration, the owner must accept an ADR that:

1. Declares Allocation the sole owner of execution-capacity Commitments.
2. Preserves Planning ownership of Candidate and Approved Plans.
3. Preserves execution-subsystem ownership of executable definitions and
   runtime, including DEC-0073 Production ownership.
4. Adds `butchercraft:allocation` at Scheduler order 350.
5. Approves Scheduler schema-1 to schema-2 migration behavior.
6. Approves additive Planning persistence evolution for Allocation references.
7. Approves additive Production persistence evolution for AllocationSet gating.
8. Confirms that ambiguous or unsupported older saves fail visibly.

The ADR may be recorded as a proposed entry before implementation, but it must
not be marked accepted without explicit owner approval.

## Review Result

RFC-0022 Revision 2 is internally consistent with the current Constitution,
Core Principles, DEC-0073, and DEC-0074. DEC-0076 through DEC-0079 authorize
M22A-M22D only.

Remaining implementation stays prohibited until the remaining ADR and
compatibility decisions are explicitly approved.
