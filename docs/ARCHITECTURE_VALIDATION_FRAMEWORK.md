# BCSE Architecture Validation Framework

Status: Phase 1 implemented

The BCSE Architecture Validation Framework turns documented architectural
contracts into deterministic, executable checks. It validates an immutable
description of architecture. It does not execute simulation, discover classes,
inspect runtime state, change subsystem ownership, or mutate any registry,
persistence service, Scheduler, Transaction, Planning, or Production state.

The framework is implemented under
`com.butchercraft.architecture.validation`. The current ButcherCraft manifest
adapter is implemented under `com.butchercraft.architecture`.

## Purpose

ButcherCraft's Constitution, accepted decisions, RFCs, and technical
architecture define contracts for:

- singular ownership;
- downward dependency direction;
- deterministic registries;
- versioned and separately owned persistence;
- stable Scheduler stages;
- transaction authority;
- Planning and Production ownership;
- deterministic simulation and replay.

The framework provides one reusable validation pipeline for those contracts.
It does not replace the governing documents. A rule proves a declared
contract; it cannot approve a new architecture decision or make a proposed RFC
effective.

## Design Boundaries

The framework is pure Java and deterministic.

- No Minecraft or NeoForge imports.
- No reflection.
- No classpath, package, resource, or runtime scanning.
- No hidden randomness.
- No global mutable registry.
- No wall-clock sampling.
- No mutation of observed systems.
- No dynamic rule discovery.

Callers supply an immutable `ValidationContext`. Rule registration is explicit.
The immutable `ValidationRuleRegistry` canonicalizes rules by category and rule
id. Every result sorts its detail messages, so equivalent inputs produce equal
reports.

`ButcherCraftArchitectureManifest.current()` is the explicit Phase 1 snapshot
of accepted current architecture. It references current pure Scheduler stage
and Work-type constants where those constants already exist. All other
contracts remain declarations, not introspection.

## Core Model

### ValidationContext

`ValidationContext` is an immutable aggregate containing:

- architecture components;
- ownership assignments;
- ownership contracts;
- observed dependency edges;
- forbidden dependency constraints;
- registry descriptors and entry references;
- persistence descriptors;
- Scheduler descriptors and stage dependencies;
- simulation invariant declarations.

`ValidationContextBuilder` is a local construction helper. Building a context
copies every collection. Public context views are immutable.

### ValidationRule

A rule declares:

- a stable `ArchitectureId`;
- a human-readable description;
- one `ValidationCategory`;
- one `ValidationSeverity`;
- a deterministic validation function.

Each rule produces exactly one `ValidationResult`. A result is either passed,
failed, warning, or informational. Violation details are immutable and
canonically sorted.

If a rule throws, returns null, or returns metadata inconsistent with its
registration, `ArchitectureValidator` converts that malformed execution into
an explicit deterministic error result. One malformed extension cannot make
the remaining rules disappear.

### ValidationRuleRegistry

The registry:

- rejects null rules;
- rejects duplicate rule ids;
- validates required rule metadata;
- orders rules by category and stable id;
- supports `contains`, `find`, `size`, `stream`, and category lookup;
- exposes immutable views.

The builder is intentionally local and mutable during assembly. The built
registry is immutable and safe to reuse.

### ArchitectureValidator

`ArchitectureValidator` evaluates every registered rule sequentially in the
registry's canonical order. It returns one immutable `ValidationReport`.

The validator never reads a clock. `validate(context)` records
`Duration.ZERO`. A caller that measures execution externally may call
`validate(context, duration)`. The supplied duration is report metadata and is
therefore an explicit input rather than a hidden nondeterministic observation.

### ValidationReport And ValidationSummary

The report includes:

- context id;
- ordered results;
- passed rules;
- failed rules;
- warnings;
- informational messages;
- total rule count;
- category counts;
- caller-supplied execution time.

Warnings and informational messages do not make a report unsuccessful. Any
failed rule does.

## Categories

Phase 1 defines these additive categories:

| Category | Current purpose |
| --- | --- |
| `OWNERSHIP` | Singular ownership and general owner contracts. |
| `DEPENDENCIES` | Known edges, forbidden direction, and graph cycles. |
| `PERSISTENCE` | Schema, id, path, ordering, ownership, and references. |
| `SCHEDULER` | Stage ids, orders, gaps, dependencies, and cycles. |
| `REGISTRIES` | Stable ids, duplicates, ordering, and references. |
| `TRANSACTIONS` | Transaction-specific ownership contracts. |
| `PLANNING` | Planning-specific ownership contracts. |
| `PRODUCTION` | Production-specific ownership contracts. |
| `ALLOCATION` | M22A-M22C Allocation definition, lifecycle, Cycle, detached accounting, selection, registry, report, trace, and history ownership. |
| `EXECUTION` | Runtime execution ownership contracts. |
| `SIMULATION` | Replay, ordering, stable id, randomness, and bounded-work facts. |
| `GENERAL` | Framework and component integrity. |

DEC-0076 through DEC-0078 authorize RFC-0022 M22A-M22C. The current manifest
declares the Allocation package; definition, lifecycle, Cycle, detached
Capacity accounting, Commitment selection, registry, report, trace, and
history ownership; and canonical definition, runtime, report, and trace
registries. It contains no Allocation stage, persistence, live provider, or
integration declaration.

## Standard Rules

`ArchitectureRules.standardRegistry()` currently installs:

1. Component id and package-root integrity.
2. Singular responsibility ownership.
3. Category-specific ownership-contract validation.
4. Dependency reference and duplicate validation.
5. Forbidden dependency validation.
6. Dependency cycle detection.
7. Registry id, entry id, duplicate, and reference identity validation.
8. Canonical, explicit, or insertion ordering validation.
9. Registry reference resolution.
10. Persistence id, path, owner, and schema validation.
11. Immutable-definition and mutable-runtime authority separation.
12. Persisted registry-reference resolution.
13. Scheduler and stage identity validation.
14. Scheduler ordering and gap validation.
15. Scheduler dependency reference, order, and cycle validation.
16. Simulation replay, deterministic ordering, and stable-id validation.

Rules aggregate all violations they find instead of stopping at the first
error. This keeps reports useful while preserving deterministic output.

## Ordering Policies

Registry and persistence descriptors use one explicit `OrderingPolicy`.

- `CANONICAL_ID`: entries must already be sorted by stable id.
- `EXPLICIT_ORDER`: order values must be non-negative and unique, and entries
  must be stored by order then id.
- `INSERTION`: immutable list order is authoritative.
- `UNSPECIFIED`: validation failure.

Phase 1 does not infer ordering from collection implementation classes.

## Ownership Validation

An `OwnershipAssignment` records the observed owner of one responsibility. An
`OwnershipContract` records the expected owner and governing category.

Standard rules detect:

- unknown owners;
- duplicate assignments;
- multiple owners;
- missing contract owners;
- owner mismatches;
- duplicate ownership contracts.

The current manifest encodes, among other accepted contracts:

- Simulation owns authoritative simulation time.
- Inventory owns economic quantities.
- Transactions own economic mutations.
- Planning owns Planning decisions and Approved Plans.
- Production owns executable Production Plans.
- Production owns Production Run runtime.
- Scheduler owns Work eligibility.

## Dependency Validation

`DependencyDescriptor` records one observed architectural dependency.
`DependencyConstraint` records one forbidden direction and its rationale.

Standard rules detect:

- duplicate edges;
- unknown consumers or providers;
- forbidden edges;
- dependency loops.

The current manifest describes accepted high-level contracts. It does not scan
Java imports. Existing source dependency-boundary tests remain responsible for
checking concrete package text where that is appropriate.

## Registry Validation

`RegistryDescriptor` and `RegistryEntryDescriptor` permit candidate data to
contain malformed ids, duplicates, incorrect ordering, and unresolved
references so those conditions can be reported instead of prevented before
validation.

An `ArchitectureReference` identifies a target registry and target entry.
References resolve against the complete candidate registry set, matching the
project's candidate-snapshot validation philosophy.

The current manifest includes architectural components, built-in Simulation
stages, and current internal Scheduler Work types.

## Persistence Validation

Each `PersistenceDescriptor` declares:

- stable persistence id;
- normalized path;
- owning component;
- schema version;
- data kind;
- ordering policy;
- registry references.

The standard rules reject:

- duplicate ids;
- duplicate paths;
- non-canonical ids;
- non-positive schemas;
- unknown owners;
- mixed immutable and mutable authority;
- unspecified ordering;
- unresolved references.

`SEPARATED_DEFINITIONS_AND_RUNTIME` describes a format that stores both kinds
under one clearly defined owner and schema without making identity mutable.
`MIXED_AUTHORITY` is always a failure.

The framework validates declarations only. It does not load files, migrate
schemas, or replace persistence candidates.

## Scheduler Validation

`SchedulerDescriptor` declares one positive expected order step and an ordered
stage list. Each stage declares a stable id, execution order, and earlier-stage
dependencies.

Rules detect:

- duplicate Scheduler ids;
- duplicate stage ids;
- duplicate execution orders;
- non-canonical ids;
- non-positive orders;
- unexpected gaps;
- list order that differs from execution order;
- duplicate or unknown dependencies;
- dependencies on the same or a later stage;
- dependency loops.

The current manifest maps the accepted six stages at orders 100 through 600.
It does not add the proposed RFC-0022 Allocation stage.

## Simulation Validation

Simulation declarations are typed as:

- replay compatibility;
- deterministic ordering;
- stable identifiers;
- explicit randomness;
- bounded work;
- known invariants.

The standard Simulation rule requires declarations for replay compatibility,
deterministic ordering, and stable identifiers. It rejects duplicate or
unsatisfied declarations.

## Adding A Rule

1. Implement `ValidationRule`.
2. Give it a canonical stable id.
3. Select one category and severity.
4. Read only immutable data from `ValidationContext`.
5. Sort any generated facts before returning them.
6. Return one `ValidationResult`.
7. Register it explicitly through `ValidationRuleRegistryBuilder`.
8. Add success, failure, null, malformed, deterministic replay, and scale tests
   appropriate to the rule.
9. Update this document and the current manifest when the rule represents an
   accepted ButcherCraft contract.

Rules must not obtain subsystem managers, mutable registries, world state, file
systems, class loaders, clocks, or random sources.

## Adding A Descriptor Type

A new descriptor is appropriate only when an accepted architectural contract
cannot be represented by existing component, ownership, dependency, registry,
persistence, Scheduler, or simulation facts.

New descriptor types must be immutable, use stable ids, make ordering explicit,
and remain independent from Minecraft and NeoForge. Adding a descriptor does
not transfer ownership of the described data to the validation framework.

## Current Integration

`ButcherCraftArchitectureValidation.validateCurrentArchitecture()` validates
the explicit current manifest with the standard rule set. Phase 1 invokes this
through automated tests. It is not registered on server startup, reload,
simulation ticks, commands, menus, networking, or gameplay.

RFC-0022 M22D extends the manifest with external Resource/Capacity authority,
Allocation-owned observation and provider-framework responsibilities, and the
empty canonical `butchercraft:allocation_providers` registry. The empty entry
is deliberate evidence that the generic framework exists while no
production-grade concrete provider is active. Allocation still has no
persistence descriptor or Scheduler stage 350.

This build-time integration is deliberate:

- validation cannot change world behavior;
- no server runtime cost is introduced;
- no malformed optional extension can prevent world loading through an
  undocumented hook;
- architecture failures remain visible in the automated test report.

## Testing

Automated coverage includes:

- successful full-manifest validation;
- every violation family;
- duplicate ids and registrations;
- malformed and throwing rules;
- null and edge handling;
- immutable collection behavior;
- warning and informational summaries;
- caller-supplied execution time;
- deterministic replay equality;
- extension-rule registration;
- 100,000 registry entries;
- 20,000 extension rules;
- source dependency boundaries.

The source boundary test prevents Minecraft, NeoForge, reflection, scanning,
randomness, and wall-clock APIs from entering the framework package.

## Future Extensions

Future accepted architecture may add:

- M22E through M22F Allocation persistence, integration, and stage contracts;
- concrete owner-domain adapters through the M22D provider contract;
- additional execution-owner contracts;
- persistence migration declarations;
- public API compatibility rules;
- module dependency manifests;
- industry/Core boundary declarations;
- generated manifest adapters, provided generation is deterministic and does
  not introduce runtime scanning.

These are additive possibilities, not Phase 1 implementation commitments.

Any rule or manifest update that changes a constitutional invariant, subsystem
owner, dependency direction, persisted schema, or public API still requires
the normal ADR and owner-approval process. Passing validation cannot substitute
for that process.
