# BCSE Architecture Guide

Status: Contributor architecture guide

Governing authority: [`CONSTITUTION.md`](../CONSTITUTION.md)

This guide explains the ButcherCraft Core Simulation Engine (BCSE) as a
coherent platform. It summarizes accepted current architecture and clearly
labels proposed architecture. It does not replace the Constitution, accepted
decisions, the canonical Technical Architecture, or subsystem RFCs.

When documents differ, use this authority order:

1. [`CONSTITUTION.md`](../CONSTITUTION.md)
2. Accepted records in [`DECISIONS.md`](../DECISIONS.md)
3. [`TECHNICAL_ARCHITECTURE.md`](../TECHNICAL_ARCHITECTURE.md)
4. Accepted RFC scope and focused subsystem documents
5. This guide

A draft RFC does not become current architecture merely because this guide
mentions it.

## 1. Purpose Of BCSE

BCSE is the shared deterministic regional simulation kernel beneath
ButcherCraft industries and player experiences. It provides common identity,
time, scheduling, economic definitions, runtime ownership, planning,
transactions, persistence policy, validation, and integration boundaries.

The kernel exists so industries participate in one coherent world. Production,
retail, agriculture, logistics, utilities, and future domains should not each
invent a private clock, economy, inventory authority, or save foundation.

BCSE is not the Meat Processing industry. Meat Processing is the flagship
consumer and currently owns the developed products, transformations,
workstations, machines, packaging, assets, menus, and player interactions.

The player participates in an existing simulation. The world, its identities,
businesses, and future industrial activity do not depend on a connected player
in order to remain valid.

## 2. Architectural Philosophy

BCSE follows a small set of recurring principles:

- **Singular ownership:** every definition, runtime value, mutation, and
  historical fact has one authoritative owner.
- **Immutable identity, mutable runtime:** definitions describe what something
  is; runtime records describe what is happening now.
- **Deterministic operation:** explicit inputs, canonical ordering, exact
  arithmetic, bounded work, and typed failures make outcomes reproducible.
- **Validation before mutation:** consequential changes require an accepted
  validation result before an owning executor can apply them.
- **Atomic publication:** candidate state is validated privately and becomes
  visible as one consistent result.
- **Evidence and replay:** reports, history, traces, and digests explain
  outcomes and permit deterministic comparison.
- **Downward dependency flow:** integration code may depend on pure domains;
  pure domains do not depend on Minecraft, NeoForge, presentation, or industry
  details.
- **Industry-neutral Core:** generic contracts belong in Core only when their
  responsibility is truly shared and their shape is proven.
- **Explicit incompleteness:** missing references, malformed persistence,
  unsupported schemas, and invalid transitions fail visibly.

BCSE favors focused services and immutable values over a universal manager.
Subsystems cooperate through stable identifiers, immutable views, typed
requests, adapters, providers, events, and Transactions.

## 3. Kernel Overview

The kernel is a set of cooperating authorities, not one call stack or mutable
object graph.

| Kernel area | Current state |
| --- | --- |
| World Identity and player identity | Implemented with deterministic generation and separate persistence |
| Simulation Clock and calendar events | Implemented and authoritative |
| Work Scheduler | Implemented as a bounded six-stage pipeline |
| Goods, Actors, Inventory, Orders, and Contracts | Implemented foundations |
| Transactions | Implemented validation, atomic Inventory mutation, audit, persistence, and explicit replay |
| Production | Implemented generic definitions, runtime, Scheduler handler, persistence, and Transaction-backed completion |
| Planning | Implemented deterministic Production planning and Scheduler integration |
| Provider Framework | Allocation M22D contracts implemented; no production-grade concrete provider is active |
| Allocation | RFC-0022 M22A-M22D implemented as a pure explicit-input domain and Cycle |
| Generic Execution | Specified by RFC-0023 Draft 1 only; not accepted or implemented |
| Architecture Validation | Phase 1 implemented for explicit immutable manifests |

The live Scheduler currently contains Production and Economic Planning
handlers. The accepted six stages remain at orders 100 through 600. There is no
Allocation stage 350, Allocation Work handler, generic Execution service, or
live Allocation-to-Execution gate.

## 4. High-Level Kernel Diagram

The target causal flow is:

```text
Authoritative Observation
  -> Planning
      -> Domain-Owned Work Definitions
          -> Provider Framework
              -> Allocation
                  -> Execution
                      -> Transactions
                          -> Authoritative State
```

This is an ownership and data-flow diagram, not the current Scheduler stage
list.

- Observation captures facts without taking ownership from their source.
- Planning decides what should happen.
- A domain owner creates executable work.
- Providers expose immutable Resource and Capacity facts.
- Allocation authorizes scarce Capacity through Commitments.
- Execution performs bounded authorized work.
- Transactions validate and apply authoritative mutations.
- State remains owned by Inventory or another focused domain.

The final generic Execution segment is proposed by
[`RFC-0023`](RFC-0023_DETERMINISTIC_EXECUTION_ENGINE.md). The current
Production handler performs its own accepted runtime progression and
Transaction-backed completion until a separately approved migration changes
that architecture.

## 5. Subsystem Responsibilities

| Subsystem | Owns | Intentionally does not own | Status |
| --- | --- | --- | --- |
| Planning | Observations used by a cycle, Needs, Constraints, Opportunities, Candidate Plans, Approved Plans, cycle evidence, and submission state | Execution, Inventory mutation, Transactions, Scheduler runtime, durable Capacity reservations | Implemented |
| Providers | Their source-domain Resources, Capacity, and current facts | Planning decisions, Allocation Commitments, execution, or cross-domain mutation | Generic Allocation provider contract implemented; concrete providers deferred |
| Allocation | Requirements, Requests, AllocationSets, Commitments, set runtime, detached accounting, Cycle reports, traces, and observation aggregation | Source Resources, source Capacity, Planning artifacts, executable definitions, Transactions, or Inventory | M22A-M22D implemented; live integration deferred |
| Execution | Generic execution runtime, attempts, progress, lifecycle, Reports, History, and Trace | Plans, Commitments, Resources, Capacity, Transactions, Inventory, or domain semantics | RFC-0023 Draft 1 only |
| Transactions | Economic mutation validation, accepted change plans, atomic execution, audit history, and replay orchestration | Business decisions, production semantics, planning, scheduling, or source-domain policy | Implemented |
| Inventory | Economic quantities, inventory runtime, storage hierarchy, and capacity invariants | The reason a quantity changes, Production policy, Orders, or market behavior | Implemented |
| Architecture Validation | Immutable architecture descriptions, rules, deterministic validation, and reports | Any described subsystem state, architecture approval, runtime discovery, or simulation behavior | Implemented build-time framework |

### Planning

Planning translates authoritative facts into approved intent. It may detect a
Need, discover Opportunities, rank Candidates, and submit an Approved Plan
through a typed domain adapter. Its detached cycle-local claims prevent
over-selection inside one cycle but are not Inventory reservations or
Allocation Commitments.

### Providers

A provider is a read-only boundary around an authoritative subsystem. It
translates owner-specific state into a narrow immutable observation without
moving authority into the consumer.

The current provider framework belongs to Allocation and emits observed
Resource and Capacity snapshots. Providers are explicit, canonically ordered,
bounded, and failure-isolated. M22D deliberately registers no concrete
provider.

### Allocation

Allocation answers which scarce Capacity is committed to an externally owned
work definition. It uses exact quantities, immutable Requirements,
all-or-nothing AllocationSets, detached Capacity accounting, deterministic
selection, and atomic Commitment publication.

An Allocation does not guarantee Inventory availability or successful work.
It grants temporary execution authorization. Resource and Capacity owners
remain external.

### Execution

The proposed generic Execution subsystem answers how an already approved and
authorized work instance progresses. It orchestrates one bounded step through
an external domain adapter, observes any required Transaction result, and
publishes runtime and evidence atomically.

Execution does not decide, allocate, interpret industry semantics, or mutate
authoritative state. This contract is documented by RFC-0023 Draft 1 and is not
current implementation authority.

### Transactions

Transactions are the universal economic mutation boundary. A cause supplies an
immutable proposal. Validation produces an exact accepted change plan.
`TransactionExecutor` rechecks that plan and applies the whole batch through
Inventory authority or applies nothing.

Final Transaction status and audit history are authoritative mutation facts.
Callers may observe them; they may not infer success or bypass the executor.

### Inventory

Inventory owns current economic Good quantities and their storage locations.
It validates underflow, capacity, status, unit, and hierarchy constraints.
Mutation is restricted to the Transaction execution boundary.

Minecraft ItemStacks and workstation slots are separate integration concerns.
They do not replace the pure economic Inventory authority.

### Architecture Validation

Architecture Validation evaluates an explicit immutable description of the
accepted architecture. It checks ownership, dependency direction, registries,
persistence, Scheduler topology, and simulation invariants in deterministic
rule order.

The validator does not scan code, approve RFCs, inspect world state, or run at
server startup. A passing report proves that the supplied manifest satisfies
registered rules; it does not make a proposal accepted.

## 6. Ownership Model

BCSE separates five kinds of authority:

| Kind | Meaning | Example owner |
| --- | --- | --- |
| Definition authority | What a stable concept is | Goods owns `GoodDefinition`; Production owns Process and Plan definitions |
| Runtime authority | What is happening now | Inventory owns quantities; Production owns Run runtime |
| Decision authority | What should happen | Planning owns Candidate and Approved Plans |
| Authorization authority | Which scarce capability may be used | Allocation owns Commitments |
| Mutation authority | Which authoritative state transition is applied | Transactions own economic mutation execution |

Ownership does not follow data access. A subsystem may reference a fact without
owning it. References use stable identifiers and immutable snapshots rather
than embedded mutable owner objects.

Examples:

- Planning observes Inventory but cannot change it.
- Allocation references executable work but cannot advance it.
- Execution references Commitments but cannot create or repair them.
- A Transaction references Inventory but cannot redefine an Inventory
  container.
- Architecture Validation describes every owner but owns none of their data.

Historical evidence has an owner too. Allocation owns Allocation reports and
history. Transactions own Transaction audit history. The proposed Execution
subsystem would own Execution Reports, History, and Trace. Evidence does not
become a second runtime authority.

## 7. Architectural Layers

BCSE repeatedly uses this structure:

```text
Immutable Definitions
  -> Mutable Runtime
      -> Engine
          -> Candidate Validation
              -> Atomic Publication
                  -> Immutable Evidence
                      -> Replay
                          -> Verification
```

### Immutable Definitions

Definitions carry stable identity, schema, typed metadata, and relationships.
They are safe to compare, validate, persist, and reference. Runtime never
rewrites a definition to represent a current condition.

### Mutable Runtime

Runtime has one focused owner and an explicit lifecycle. Public access returns
immutable views or defensive snapshots. Transition tables, monotonic ticks,
terminal states, and typed failures make mutation inspectable.

### Engine

An engine evaluates explicit inputs under bounded deterministic policy. It does
not reach into unrelated mutable services during evaluation.

### Candidate Validation

Candidate state is assembled away from authority. Cross-references,
quantities, lifecycle rules, schemas, and invariants are checked before
publication.

### Atomic Publication

One accepted candidate replaces or advances authoritative state. A failure
leaves the previous state intact. Atomicity is claimed only at the boundary
that the subsystem can actually guarantee.

### Immutable Evidence

Reports, history, traces, results, and digests explain what happened. Evidence
is not mutable runtime and is not silently rewritten.

### Replay And Verification

Replay consumes explicit recorded facts instead of live providers or hidden
environmental state. Verification compares outcomes, digests, invariants, and
architecture declarations.

This pattern keeps failure local, makes tests meaningful, protects save
compatibility, and prevents convenient caches or snapshots from becoming
competing authorities.

## 8. Data Flow

The kernel distinguishes five questions:

| Question | Architectural answer |
| --- | --- |
| What should happen? | Decision, owned by Planning or another policy owner |
| What scarce capability may be used? | Authorization, owned by Allocation |
| How does approved work progress? | Execution, owned by the executable domain today and proposed generic Execution later |
| What authoritative change is applied? | Mutation, owned by Transactions |
| What is true now? | State, owned by Inventory or the relevant authoritative domain |

A complete target flow is:

1. Authoritative services expose immutable observations.
2. Planning detects Needs and selects bounded Approved Plans.
3. A domain adapter creates an executable work definition owned by that domain.
4. Provider adapters expose current Resource and Capacity facts.
5. Allocation evaluates complete Requirements and publishes Commitments.
6. Scheduler determines when eligible work receives one bounded invocation.
7. Execution validates runtime and active authorization, then resolves one
   domain adapter.
8. The adapter evaluates one bounded step and may propose one Transaction.
9. The Transaction Framework validates and atomically applies required
   mutation.
10. Execution observes the authoritative Transaction result and atomically
    publishes runtime, Reports, History, Trace, and terminal evidence.
11. Authoritative state remains with Inventory or the appropriate domain.

Steps 4 through 10 describe the RFC-0022/RFC-0023 target integration. They are
not all wired into the current runtime. Current Production owns Run progression
and uses the implemented Transaction Framework directly under Scheduler
authority.

## 9. Determinism

Determinism is a whole-platform property.

### Canonical Ordering

Every meaningful collection and queue declares its ordering. Examples include
stable identifiers, explicit stage order, authoritative insertion sequence,
and complete comparator chains. Hash iteration, locale, filesystem order, and
thread timing do not decide outcomes.

### Immutable Identities

Stable namespaced identifiers survive display-name changes and save/reload.
Derived identifiers use explicit canonical inputs rather than random UUIDs or
wall-clock values.

### Exact Arithmetic

Economic and Allocation quantities use exact representations with explicit
units. A subsystem does not perform implicit conversion or floating-point tie
breaking.

### Bounded Work

Scheduler, Planning, provider observation, Allocation, and future Execution
work use positive explicit limits. Remainder is visible and deferred rather
than processed by hidden unbounded loops.

### Explicit Randomness

Schema-1 Planning, Allocation, and proposed Execution use no randomness.
Future randomness must be an explicit, seeded, ordered input. Hidden randomness
cannot influence authoritative outcomes.

### Atomicity And Determinism

Deterministic evaluation is insufficient if partial state can leak. Candidate
publication therefore validates complete state and performs one visible
commit at the declared ownership boundary.

## 10. Replay

Replay answers whether recorded inputs and evidence reproduce the same
authoritative result.

Replay inputs commonly include:

- immutable definitions;
- prior runtime or an explicit baseline;
- authoritative simulation ticks;
- ordered requests or work;
- accepted validation and observed Transaction results;
- policy identifiers and schema versions;
- recorded observations rather than live provider queries.

Replay outputs include:

- lifecycle transitions;
- resulting runtime;
- reports and history;
- engineering traces;
- typed failures;
- canonical digests.

Canonical SHA-256 digests provide compact comparison evidence. A digest does
not replace the structured record; it proves that canonicalized content is
equal.

Engineering traces explain phase order, input use, decisions, proposals,
publication, and failures. They are diagnostic evidence and do not control
outcomes. Wall-clock timings may be measured externally for engineering work
but never influence replay.

Replay is not automatic repair. Divergence, missing evidence, unsupported
schemas, or unresolved references fail explicitly.

## 11. Architecture Validation

BCSE validates architecture because ownership and dependency failures are as
dangerous as local code defects. A system may pass behavioral tests while
quietly creating a second mutation path, a circular dependency, an
unversioned persistence owner, or a nondeterministic stage order.

The implemented
[`Architecture Validation Framework`](ARCHITECTURE_VALIDATION_FRAMEWORK.md)
uses:

- immutable architecture descriptors;
- explicit ownership and dependency facts;
- registry, persistence, Scheduler, and simulation declarations;
- an explicitly assembled canonical rule registry;
- structured deterministic results and reports.

`ButcherCraftArchitectureManifest.current()` is a reviewed declaration of
accepted current architecture. The framework deliberately avoids reflection,
classpath scanning, world access, dynamic discovery, clocks, and randomness.
Source dependency tests separately inspect package boundaries where needed.

Architecture Validation is evidence, not governance. Constitutional or
ownership changes still require the accepted decision process and owner
approval.

## 12. Extension Model

Future domains integrate at narrow boundaries instead of modifying kernel
internals.

| Extension | Typical integration |
| --- | --- |
| Production | Domain-owned Process/Plan and adapter, capability Requirements, Transaction proposals |
| Logistics | Transport work definitions, vehicle or route Capacity providers, custody Transactions |
| Utilities | Utility Capacity providers, service work adapters, explicit state Transactions |
| Maintenance | Maintenance work definitions, equipment adapters, parts and completion Transactions |
| Agriculture | Industry-owned goods, Processes, facilities, adapters, and provider facts |
| Retail | Orders, demand observations, store Processes, Inventory and sale Transactions |

The principal extension mechanisms are:

- stable identifiers and immutable definitions;
- datapack or explicit registries where ownership permits;
- read-only providers for authoritative facts;
- domain adapters for specialized evaluation;
- Scheduler Work submitted through documented handlers;
- Transactions for authoritative economic mutation;
- immutable reports and events for observation;
- compatibility adapters that preserve external-mod authority.

An adapter translates semantics. It does not receive permission to mutate every
system it can reference. A provider observes owner state. It does not allocate,
schedule, or execute work.

Public APIs are introduced only after a real consumer proves the smallest
useful contract. Current internal managers and packages are not third-party
compatibility promises.

## 13. What Belongs In Core

Core contains cross-industry authorities and contracts:

- one World Identity and stable identity foundation;
- one authoritative Simulation Clock;
- deterministic Scheduler and event foundations;
- generic Goods, Actors, Inventory, Orders, and Contracts;
- universal economic Transactions;
- generic Planning contracts;
- generic Allocation contracts;
- generic Execution contracts after RFC approval and implementation;
- persistence, schema, validation, evidence, and replay foundations;
- proven public integration contracts.

Core does not contain:

- meat-processing rules or cut terminology;
- villagers or industry-specific workers;
- recipes;
- machine-specific behavior;
- Minecraft blocks, menus, or rendering logic as domain concepts;
- industry-specific routing, repair, generation, or processing policy;
- private copies of external-mod state;
- speculative APIs without a real consumer.

Industry modules own their products, equipment, facilities, operating policy,
assets, and gameplay. Minecraft and NeoForge adapters remain at the platform
boundary.

## 14. Roadmap

### RFC-0022: Resource Allocation Engine

[`RFC-0022 Revision 2`](RFC-0022_RESOURCE_ALLOCATION_ENGINE.md) defines the
complete target Resource Allocation architecture.

M22A through M22D are owner-authorized and implemented:

- immutable Allocation domain;
- runtime and registries;
- deterministic explicit-input Cycle;
- Resource and Capacity provider framework.

M22E and M22F remain separately gated. Allocation has no persistence, live
concrete provider, Scheduler stage 350, Planning handoff, Production gate, or
generic Execution integration.

### RFC-0023: Deterministic Execution Engine

[`RFC-0023 Draft 1`](RFC-0023_DETERMINISTIC_EXECUTION_ENGINE.md) specifies the
proposed generic Execution domain, runtime, pipeline, adapter framework,
Transaction observation, evidence, replay, and verification requirements.

The document is complete as a draft. It is not accepted architecture and
authorizes no implementation until architectural review and explicit owner
approval.

### Future RFCs

Future focused RFCs may address Allocation integration and persistence,
Execution implementation milestones, logistics, utilities, maintenance,
markets, population, or stable public APIs. A roadmap mention does not
authorize implementation or reserve a final design.

## 15. Glossary

**Adapter**

A boundary that translates an owning domain's specialized definition or
behavior into a generic contract without transferring authority.

**Allocation**

The deterministic decision that grants temporary permission to use scarce
execution Capacity.

**AllocationSet**

The atomic group of every Requirement needed by one executable work
definition. Every Requirement succeeds or the Set receives no Commitment.

**Atomic publication**

The replacement or transition that makes a complete validated candidate
visible as one consistent result.

**Capacity**

An exact, typed, finite execution capability exposed by an authoritative
Resource owner.

**Commitment**

Allocation-owned immutable evidence that a specific exact Capacity amount is
authorized for one Requirement.

**Definition**

Immutable, schema-versioned data describing what a stable concept is.

**Evidence**

Immutable reports, history, traces, results, and digests explaining an
evaluation or transition.

**Execution**

Deterministic bounded progression of approved and authorized work. Generic
Execution is currently proposed by RFC-0023, not implemented.

**Need**

A Planning-owned exact statement of an unsatisfied desired outcome derived
from authoritative observations.

**Opportunity**

A Planning-owned immutable description of one compatible way to satisfy a
Need.

**Plan**

Context matters. Planning owns Candidate and Approved Plans as decisions.
Production owns executable `ProductionPlanDefinition` records. Allocation and
Execution reference Plans without taking ownership.

**Provider**

A read-only adapter that translates authoritative source facts into immutable
observations under a bounded deterministic contract.

**Replay**

Reconstruction or reevaluation from explicit recorded inputs and evidence,
without querying live external state.

**Resource**

An externally owned source of execution capability, such as aggregate
workforce, storage, production, transport, utility, or inspection Capacity.

**Runtime**

Separately owned mutable state describing what is currently happening to an
immutable identity or definition.

**Scheduler Work**

An immutable request for bounded handling at an authoritative Simulation Tick
and stable stage. Scheduler ownership determines eligibility and order, not
domain meaning.

**Trace**

Immutable engineering evidence recording deterministic phase behavior,
proposals, publication, failures, and digests.

**Transaction**

An immutable proposal for authoritative economic mutation. The Transaction
Framework validates and atomically applies an accepted change plan or applies
nothing.

## Supporting References

- [`CONSTITUTION.md`](../CONSTITUTION.md)
- [`CORE_PRINCIPLES.md`](../CORE_PRINCIPLES.md)
- [`DECISIONS.md`](../DECISIONS.md)
- [`TECHNICAL_ARCHITECTURE.md`](../TECHNICAL_ARCHITECTURE.md)
- [`MODULES.md`](../MODULES.md)
- [`SIMULATION_MODEL.md`](../SIMULATION_MODEL.md)
- [`Architecture Validation Framework`](ARCHITECTURE_VALIDATION_FRAMEWORK.md)
- [`Simulation Scheduler`](SIMULATION_SCHEDULER.md)
- [`Economic Planning Engine`](ECONOMIC_PLANNING_ENGINE.md)
- [`Production Framework`](PRODUCTION_FRAMEWORK.md)
- [`Transaction Framework`](TRANSACTION_FRAMEWORK.md)
- [`RFC-0022 Architecture Review`](RFC-0022_ARCHITECTURE_REVIEW.md)
- [`RFC-0022 Resource Allocation Engine`](RFC-0022_RESOURCE_ALLOCATION_ENGINE.md)
- [`RFC-0023 Deterministic Execution Engine`](RFC-0023_DETERMINISTIC_EXECUTION_ENGINE.md)
