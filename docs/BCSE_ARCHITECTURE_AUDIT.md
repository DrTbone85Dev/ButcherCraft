# BCSE Independent Architecture Audit

Audit date: 2026-07-23

Repository state reviewed:

- Branch: `feature/milestone-2d`
- Commit: `6a264af` (`v1.0.0-architecture`)
- Configured project version: `0.9.0-alpha.1`
- Main Java files: 764
- Test Java files: 263
- JUnit test methods: 1,043

## Executive Summary

The ButcherCraft Simulation Engine (BCSE) is a strong architecture foundation
with unusually explicit ownership, deterministic ordering, immutable
definitions, stable identities, typed failures, and carefully separated
publication boundaries. Planning, Allocation, Provider observation,
Transactions, Production, and Scheduler behavior generally match the accepted
Decisions and the implemented portions of RFC-0022. The pure Java core is
well-isolated from Minecraft and NeoForge, and the test suite provides broad
evidence for deterministic local behavior.

The architecture is not yet ready for unconditional approval as a long-lived
industrial simulation platform. Two lifecycle risks should be resolved before
additional live execution systems are connected:

1. Economic Planning executes and permanently retains a new evidence-rich
   cycle every simulation tick. There is no cadence, retention, archival, or
   compaction contract. This creates unbounded memory, save-file, save-time,
   and load-time growth even in an idle world.
2. JSON-backed simulation services persist independently on graceful server
   shutdown. There is no coordinated checkpoint generation, autosave
   contract, last-known-good snapshot, or crash-recovery policy across Clock,
   Scheduler, Planning, Transactions, Inventory, Production, Orders, and
   related owners.

These findings do not invalidate the deterministic algorithms already built.
They expose a gap between bounded single-cycle execution and bounded
world-lifetime operation. That gap becomes release-critical as more systems
produce durable runtime and evidence.

### Approval Recommendation

**Conditionally approve the implemented M22A-M22D Allocation foundation.**

Do not authorize M22E-M22F integration or implementation of RFC-0023 until:

- planning cadence and evidence retention are decided;
- coordinated persistence and crash recovery are decided;
- accepted Transaction validation is strongly bound to the transaction being
  executed;
- Scheduler effect classifications and execution authority are made explicit;
- the required Allocation stage, persistence, and handoff ADR is accepted; and
- RFC-0023's duplicate or ambiguous execution concepts are reconciled.

No current evidence indicates species-specific coupling, Minecraft leakage
into the audited pure domains, nondeterministic selection, or an ownership
violation in M22A-M22D.

## Overall Architecture Grade

**B+ (8.2/10)**

The design deserves a high score for domain rigor, determinism, evidence
models, tests, and candid scope boundaries. It does not receive an A because
durable cross-system consistency and lifetime resource bounds are foundational
requirements for a long-lived simulation platform, not optional operational
polish.

## Scope And Method

The audit compared the current implementation against:

- [README](../README.md)
- [Architecture Guide](BCSE_ARCHITECTURE_GUIDE.md)
- [Constitution](../CONSTITUTION.md)
- [Core Principles](../CORE_PRINCIPLES.md)
- [Project Rules](../PROJECT_RULES.md)
- [Architecture Decisions](../DECISIONS.md)
- [Technical Architecture](../TECHNICAL_ARCHITECTURE.md)
- [Modules](../MODULES.md)
- [Milestones](../MILESTONES.md)
- [Known Limitations](../KNOWN_LIMITATIONS.md)
- [Architecture Validation Framework](ARCHITECTURE_VALIDATION_FRAMEWORK.md)
- [RFC-0022 Allocation Engine](RFC-0022_RESOURCE_ALLOCATION_ENGINE.md)
- [RFC-0022 Architecture Review](RFC-0022_ARCHITECTURE_REVIEW.md)
- [RFC-0023 Deterministic Execution Engine](RFC-0023_DETERMINISTIC_EXECUTION_ENGINE.md)
- Allocation, Provider, Planning, Transaction, Production, Scheduler, and
  Execution supporting documents.

Implementation review covered the corresponding domain models, registries,
managers, pipelines, adapters, services, persistence, architecture manifest,
boundary tests, regression tests, and integration tests. The audit evaluates
the repository as implemented. Future RFC text is not treated as current
behavior unless an accepted Decision authorizes it.

## Scorecard

Scores use a 10-point scale. A score of 10 means the category is explicit,
implemented, verified, and suitable for long-term extension without a known
material gap.

| Category | Score | Assessment |
|---|---:|---|
| Architecture consistency | 8.2 | Major subsystems follow a common definitions/runtime/evidence pattern; persistence and retention are not yet platform-wide contracts. |
| Ownership consistency | 9.2 | Mutation authority is explicit and generally enforced; adapters translate rather than absorb owner responsibilities. |
| Dependency direction | 8.8 | Pure domains avoid Minecraft and depend on stable identities or narrow contracts; the composition layer remains appropriately outside them. |
| Naming consistency | 7.2 | Stable IDs and lifecycle terms are strong, but three distinct `ProductDefinition` concepts and broad Manager/Service/Registry naming increase cognitive load. |
| Terminology consistency | 7.8 | Allocation terminology is disciplined; RFC-0023 duplicates `ExecutionContext` and `ExecutionInput` roles and contains optional-transaction wording tension. |
| RFC consistency | 8.0 | M22A-M22D closely match RFC-0022; RFC-0023 is a coherent draft overall but requires clarification before acceptance. |
| Decision consistency | 9.2 | Implemented scope respects DEC-0070 through DEC-0079 and preserves explicit owner approval gates. |
| Constitution compliance | 8.7 | Determinism, ownership, bounded-cycle work, and explicit failures are strong; unbounded retained history conflicts with the spirit of bounded long-term operation. |
| Replay consistency | 7.6 | Individual models preserve stable identities and deterministic ordering, but there is no global durable checkpoint from which a complete world simulation can be replayed consistently. |
| Determinism | 9.2 | Exact arithmetic, canonical ordering, explicit ticks, and stable IDs are pervasive and well-tested. |
| Publication boundaries | 7.7 | In-memory Allocation and Inventory publication are strong; multi-file and cross-service durable publication is not atomic. |
| Evidence model | 8.8 | Reports, traces, histories, diagnostics, and typed outcomes are first-class; lifetime retention and archival policy are absent. |
| Runtime model | 7.4 | Definition/runtime separation and lifecycle validation are strong; planning retention and crash recovery leave world-lifetime runtime behavior underspecified. |
| Definition model | 9.1 | Definitions are immutable, schema-aware, canonically identified, and kept separate from mutable runtime. |
| Adapter model | 8.2 | Existing adapters preserve owner boundaries; future execution and provider adapters still need one concrete end-to-end proof. |
| Provider model | 8.3 | M22D provides deterministic ordering, typed isolation, and external authority; no production provider is intentionally active yet. |
| Transaction boundary | 7.6 | Transactions retain mutation authority and stage inventory atomically, but accepted validation is bound only by Transaction ID. |
| Scheduler assumptions | 7.1 | Stage ordering and budgets are strong; effect types are descriptive only, reentrancy is instance-scoped, and crash/catch-up behavior is absent. |
| Future extensibility | 7.8 | Stable identities and industry-neutral contracts are promising; persistence, retention, terminology, and composition concerns will compound with each new industry. |
| Package organization | 7.5 | Domain packages are recognizable and mostly cohesive; the single module and growing `world` composition root rely heavily on convention and tests. |
| Documentation completeness | 8.2 | Documentation is extensive and candid; a few stale M22D statements, public metadata, and RFC-0023 ambiguities remain. |
| Test coverage | 8.8 | The 1,043-test suite covers local invariants, boundaries, replay, persistence, and stress; sustained lifecycle and coordinated crash scenarios are missing. |
| Technical debt posture | 7.6 | Debt is usually documented and isolated, but several large orchestrators and deprecations will make later changes harder. |
| Architectural debt posture | 7.3 | The most important remaining debt is concentrated in explicit integration gates, but durable checkpointing and evidence retention need decisions now. |
| Long-term maintainability | 7.7 | Strong local contracts support maintenance; global lifecycle policy and concept count are the principal threats. |
| Overall consistency | 8.2 | The repository is internally disciplined and substantially aligned, with two material platform-lifecycle gaps. |

## Major Strengths

### 1. Ownership Is Explicit

The architecture consistently distinguishes definition ownership, runtime
ownership, and mutation authority. Inventory owns quantities, Transactions own
economic mutation, Scheduler owns work lifecycle, Planning owns decision
artifacts, Allocation owns commitment decisions, and authoritative providers
retain Resource and Capacity ownership. The [architecture manifest](../src/main/java/com/butchercraft/architecture/ButcherCraftArchitectureManifest.java#L81)
records the core ownership map and dependency constraints in executable form.

### 2. Determinism Is A Real Implementation Property

Determinism is supported by canonical IDs, insertion or canonical ordering,
explicit simulation ticks, exact arithmetic, immutable inputs, bounded
budgets, and rejection of random or wall-clock-dependent behavior in pure
domains. This is not merely an RFC aspiration; tests exercise order
independence, replay equivalence, stable persistence, and deterministic failure
selection.

### 3. Definition And Runtime Models Are Separated

Product, Good, Production, Scheduler, Planning, and Allocation definitions are
not embedded mutable state. Registries are immutable or publish immutable
snapshots, while managers or runtime services own lifecycle. This supports
future serialization, migration, inspection, and replay without making
definitions dependent on active worlds.

### 4. Atomic In-Memory Publication Is Carefully Designed

The Inventory transaction path validates all changes before applying them.
Allocation executes against detached cycle-local accounting and publishes a
validated candidate service only after the cycle is accepted. Failure paths
use typed outcomes rather than partial mutation or silent fallback.

### 5. Evidence Is First-Class

Planning and Allocation expose immutable reports, traces, histories, and
diagnostics. Scheduler and Transaction outcomes preserve typed status and
failure evidence. This is a sound base for inspection, replay diagnosis, and
future operator tooling.

### 6. Scope Gates Are Honored

M22D stops at an empty canonical provider registry and generic provider
contracts. There is no hidden Scheduler stage, Allocation persistence,
Planning handoff, Production gate, or concrete production provider. The code
and current documentation correctly preserve the owner approval gate for
M22E-M22F.

### 7. Pure Java Boundaries Are Strong

The audited core packages avoid Minecraft and NeoForge types. Minecraft
lifecycle services compose managers and storage externally. Boundary tests
explicitly check imports, forbidden clocks, randomness, reflection, and
cross-domain references in sensitive packages.

## Minor Strengths

- Duplicate IDs and malformed registry entries fail during construction.
- Stable identity types prevent incidental strings from spreading through core
  algorithms.
- Persistence readers validate schema and cross-file record consistency rather
  than silently returning empty state.
- Unsupported recovery and migration behavior is documented instead of
  simulated with lossy defaults.
- Scheduler stages and work types are explicit and deterministic.
- Planning budgets cover observations, needs, candidates, approvals,
  submissions, recursion, provider work, and total work within a cycle.
- Provider failures are isolated and reported in deterministic provider-ID
  order.
- The full test suite passes with no skipped tests.
- Repository Markdown links inspected during the audit resolve to existing
  documents.

## Architecture Risks

| Risk | Severity | Likelihood | Affected scope | Current disposition |
|---|---|---|---|---|
| Unbounded every-tick Planning evidence | Critical | High in long-lived worlds | Memory, save size, save/load duration | Requires cadence and retention ADR before more recurring work |
| Independent shutdown-only persistence | Critical | Medium, with high impact | All JSON-backed BCSE runtime | Requires checkpoint and recovery ADR before new persistent engines |
| Transaction validation bound only by ID | Medium | Low through the current manager, rising with adapters | Transactions and future Execution | Tighten before RFC-0023 implementation |
| Unenforced Scheduler effect classifications | Medium | Medium as handler count grows | Retry, failure, and side-effect safety | Define effect policy before new live handlers |
| Manifest not checked against source | Medium | Medium | Architectural assurance | Add build-time conformance decision |
| Instance-local execution guards | Medium | Low in current composition | Scheduler and Allocation concurrency | Clarify single-authority invariant before extension |
| RFC-0023 concept ambiguity | Medium | High if implementation starts from Draft 1 | Generic Execution API and persistence | Revise before acceptance |
| Planning becoming a dependency hub | Medium | Medium with a second industry | Planning maintainability and ownership | Decide provider extension only when concrete need exists |
| Product terminology collision | Low | Medium | Public APIs and contributor clarity | Resolve before expansion API lock-in |
| Large orchestrators and serializers | Low | Medium | Review and change cost | Decompose only along proven responsibilities |

The first two risks are present operational characteristics. The remaining
risks are primarily continuation gates: current code paths are constrained
enough that no active failure was observed, but the risk becomes material when
new providers, handlers, industries, or persistence owners are connected.

## Critical Issues

### BCSE-AUDIT-001: Planning History Grows Without A Lifetime Bound

**Severity:** Critical

**Observed behavior**

The live Economic Planning service installs a permanent continuation work item
for the next authoritative tick
([`EconomicPlanningService`](../src/main/java/com/butchercraft/world/EconomicPlanningService.java#L116)).
Each invocation executes a complete Planning Cycle and returns `DEFERRED` for
`tick + 1`
([`EconomicPlanningWorkHandler`](../src/main/java/com/butchercraft/world/planning/EconomicPlanningWorkHandler.java#L60)).

`PlanningManager` stores every cycle and indexes every tick in two
`LinkedHashMap` instances, with no deletion or partitioning API
([`PlanningManager`](../src/main/java/com/butchercraft/world/planning/PlanningManager.java#L21)).
`PlanningStorage.save` serializes the complete accumulated history into six
related files on each save
([`PlanningStorage`](../src/main/java/com/butchercraft/world/planning/PlanningStorage.java#L123)).
Even an otherwise idle cycle creates at least the Scheduler capacity
observation
([`PlanningPipeline`](../src/main/java/com/butchercraft/world/planning/PlanningPipeline.java#L181)).

**Why this is critical**

Per-cycle budgets limit one execution but do not limit world-lifetime memory,
disk, save duration, or load duration. At a normal 20 server ticks per second,
an always-loaded world can create approximately 72,000 Planning Cycle records
per hour and 1.7 million per day before accounting for child evidence.
Rewriting all retained evidence on shutdown compounds the cost.

This is a deterministic resource-exhaustion path, not a nondeterminism defect.
It can eventually make a valid world impractical to stop, load, inspect, or
migrate.

**Missing contract**

- Planning cadence or trigger policy.
- Hot-runtime retention limit.
- Historical evidence retention and archival policy.
- Partitioning or append strategy.
- Compaction rules that preserve required audit evidence.
- Sustained-world resource budget and acceptance test.

**Required decision**

Accept an ADR defining Planning cadence and BCSE evidence retention before
additional recurring pipelines are activated.

### BCSE-AUDIT-002: No Coordinated Durable Simulation Checkpoint

**Severity:** Critical

**Observed behavior**

Clock, Scheduler, Planning, Inventory, Transactions, Production, Orders, and
other JSON-backed services register independent `ServerStoppingEvent` save
hooks. The composition root registers the Clock save before several dependent
service saves and registers Scheduler and Planning separately
([`ButcherCraft`](../src/main/java/com/butchercraft/ButcherCraft.java#L59)).

Individual storage classes use temporary files and atomic replacement where
the filesystem supports it. For example:

- [`SimulationStateStorage`](../src/main/java/com/butchercraft/world/simulation/SimulationStateStorage.java#L166)
- [`SimulationSchedulerStorage`](../src/main/java/com/butchercraft/world/simulation/scheduler/persistence/SimulationSchedulerStorage.java#L256)
- [`PlanningStorage`](../src/main/java/com/butchercraft/world/planning/PlanningStorage.java#L177)

That protects one target file. It does not publish a consistent generation
across multiple files or services. Scheduler load requires its finalized tick
to equal the authoritative Clock tick and throws on mismatch
([`SimulationSchedulerService`](../src/main/java/com/butchercraft/world/SimulationSchedulerService.java#L121)).
The Scheduler documentation explicitly states that schema 1 has no catch-up,
partial-tick resume, crash recovery, or automatic mismatch reconciliation
([`SIMULATION_SCHEDULER.md`](SIMULATION_SCHEDULER.md#L265)).

**Why this is critical**

A process or machine failure can lose all JSON-backed runtime since the last
graceful stop. A failure during an ordered shutdown can leave individually
valid files from different simulation generations. The fail-visible load
behavior is preferable to silent repair, but it can make the newest save
unloadable without a defined recovery path. Multi-file Planning and Production
sets have the same generation problem within their own owners.

As more authoritative mutations and execution evidence are introduced, the
number of cross-owner consistency relationships increases. Retrofitting a
checkpoint generation after public worlds depend on independent files will be
materially harder.

**Missing contract**

- Autosave or checkpoint cadence.
- Cross-owner checkpoint generation identity.
- Dependency-aware snapshot boundary.
- Atomic manifest or commit marker for a completed checkpoint.
- Last-known-good generation and rollback policy.
- Crash recovery, catch-up, or explicit replay policy.
- Migration behavior for existing schema-1 files.
- Integration tests for interruption between every publication step.

**Required decision**

Accept an ADR for coordinated BCSE checkpointing and crash recovery before
Allocation persistence or generic Execution persistence is added.

## Medium Issues

### BCSE-AUDIT-003: Accepted Transaction Validation Is Bound Only By ID

`TransactionExecutor` requires a `VALIDATED` transaction and an accepted
`TransactionValidation`, but confirms only that their Transaction IDs match
([`TransactionExecutor`](../src/main/java/com/butchercraft/world/transaction/TransactionExecutor.java#L22)).
`TransactionValidation` contains the ID and staged Inventory changes, not a
canonical digest or opaque validation authority
([`TransactionValidation`](../src/main/java/com/butchercraft/world/transaction/TransactionValidation.java#L9)).

The current synchronized `TransactionManager` validates and executes through a
tightly coupled path, so ordinary internal use is protected. The lower-level
public contract nevertheless permits accepted evidence for one transaction
body to be paired with another `VALIDATED` transaction using the same ID. The
executor applies the validation's changes while reporting against the supplied
transaction.

This weakens the documented rule that the executor executes only a previously
accepted evaluation of that transaction. The risk increases when RFC-0023
adapters begin carrying Transaction proposals across subsystem boundaries.

An ADR should define whether accepted validation is:

- an opaque single-use authority;
- a canonical digest-bound value;
- valid only inside a manager-owned critical section; or
- another explicit invariant with equivalent strength.

Add an adversarial same-ID/different-body test when the contract is decided.

### BCSE-AUDIT-004: Scheduler Effect Types Are Descriptive, Not Enforced

`HandlerEffectType` defines `READ_ONLY`, `IDEMPOTENT`,
`TRANSACTION_BACKED`, and `NON_REPEATABLE`
([`HandlerEffectType`](../src/main/java/com/butchercraft/world/simulation/scheduler/HandlerEffectType.java#L3)).
Registration only checks that the value is non-null
([`SimulationWorkHandlerRegistry`](../src/main/java/com/butchercraft/world/simulation/scheduler/SimulationWorkHandlerRegistry.java#L19)).
The pipeline invokes the handler before validating the returned work result
([`SimulationPipeline`](../src/main/java/com/butchercraft/world/simulation/scheduler/SimulationPipeline.java#L154)).

No policy changes based on effect type, and the Scheduler cannot roll back
external side effects if a handler mutates state and then throws or returns an
invalid result. The Known Limitations document candidly records the absence of
global rollback for non-transactional handler effects.

The classification is useful documentation, but its name can imply an
enforced guarantee. Before more handlers are registered, an ADR should define
registration restrictions, retry rules, failure containment, and evidence
requirements for each effect type.

### BCSE-AUDIT-005: Architecture Validation Checks A Declared Model, Not Code Conformance

The Architecture Validation Framework intentionally avoids reflection,
classpath scanning, and Java import scanning. Its documentation states that
the manifest describes accepted high-level contracts and that separate source
boundary tests remain responsible for implementation dependencies
([`ARCHITECTURE_VALIDATION_FRAMEWORK.md`](ARCHITECTURE_VALIDATION_FRAMEWORK.md#L234)).

This design gives deterministic, fast validation of the manifest, but it does
not prove that packages, imports, services, persistence files, or registries in
the repository match that manifest. The manifest also models selected BCSE
components rather than every implemented world-identity domain, and it uses
nested package roots for Simulation and Scheduler
([`ButcherCraftArchitectureManifest`](../src/main/java/com/butchercraft/architecture/ButcherCraftArchitectureManifest.java#L81)).

Current package-specific source tests mitigate the issue, but coverage is
distributed and textual. A new package or dependency can remain invisible
unless a contributor remembers to extend both the manifest and the appropriate
boundary test.

This does not contradict DEC-0075. The missing assurance is a build-time
conformance layer between source structure and the accepted manifest, without
runtime scanning.

### BCSE-AUDIT-006: Reentrancy Guards Are Scoped To Executor Instances

`SimulationPipeline` protects execution with an instance-local
`AtomicBoolean`
([`SimulationPipeline`](../src/main/java/com/butchercraft/world/simulation/scheduler/SimulationPipeline.java#L15)).
`AllocationCycleExecutor` similarly uses an instance field
([`AllocationCycleExecutor`](../src/main/java/com/butchercraft/world/allocation/AllocationCycleExecutor.java#L14)).

The live composition currently creates one authoritative pipeline/executor
path, so no present regression was observed. Public constructors allow two
objects to target the same manager or runtime service, however, and each guard
would admit one concurrent call. The architecture does not state whether
single-threaded composition is an invariant, whether the authority object must
own the guard, or whether multi-instance execution must be supported.

Clarify execution authority before external modules or generic Execution
adapters can construct additional orchestrators. Tests should cover
multi-instance and recursive entry once the contract is decided.

### BCSE-AUDIT-007: RFC-0023 Contains Duplicate And Ambiguous Execution Contracts

RFC-0023 is explicitly a draft and has not authorized implementation. That is
the correct time to resolve these ambiguities:

1. Section 40 states that schema 1's primary concepts include
   `ExecutionContext` and that no additional execution concepts are introduced
   ([RFC-0023](RFC-0023_DETERMINISTIC_EXECUTION_ENGINE.md#L779)). Section 98
   later introduces `ExecutionInput` with substantially the same "every fact
   required for one bounded step" responsibility
   ([RFC-0023](RFC-0023_DETERMINISTIC_EXECUTION_ENGINE.md#L1700)). Their
   identity and relationship are not defined.
2. The pipeline says every phase executes exactly once, including constructing
   a Transaction proposal, while later text says a transaction is optional or
   required only for some work. The intended no-op or skipped-phase semantics
   need explicit wording.
3. The RFC requires persistence and replay of Runtime, history, reports, and
   traces but does not select persistence ownership, file boundaries,
   coordinated generation, load order, migration, or recovery behavior.
4. The adapter is said both to return a step result that may contain a proposal
   and to construct a proposal at the boundary. Proposal ownership should have
   one canonical statement.
5. Stable identities are required broadly, but report, trace, and history
   identity and retention rules need the same precision already used by
   Allocation.

The RFC should be revised and re-reviewed before an acceptance Decision.

### BCSE-AUDIT-008: Planning Is A Concrete Cross-Domain Aggregator

`PlanningPipeline` directly understands Orders, Contracts, Inventory,
Workforce, Production, Transactions, and Scheduler capacity. This matches the
current schema-1 production-only milestone and is represented in the manifest.
It is not presently an ownership violation.

The risk is future breadth. Adding transport, utilities, maintenance, retail,
or other industries directly to this pipeline would make Planning the
translation owner for every domain and expand an already large orchestrator.
The current documents defer public planning-provider registration. That
extension point should be decided before the second independent planning
domain is implemented, not abstracted speculatively now.

## Low Issues

### BCSE-AUDIT-009: Several Core Files Are Large

Examples observed during the audit include:

- `WorldIdentityNbtSerializer`: approximately 1,449 lines.
- `PlanningPipeline`: approximately 973 lines.
- `AllocationObservationService`: approximately 744 lines.
- `ProductionStorage`: approximately 721 lines.
- `ProductionManager`: approximately 717 lines.
- `ArchitectureRules`: approximately 632 lines.
- `ButcherCraftArchitectureManifest`: approximately 598 lines.
- `AllocationCycleExecutor`: approximately 588 lines.

Size alone is not an architecture defect, and tests cover these areas.
Nevertheless, review cost, merge conflicts, and invariant visibility will
worsen as features accumulate. Decomposition should follow proven ownership
boundaries and should not become an unrelated refactor.

### BCSE-AUDIT-010: Public Mod Metadata Describes An Earlier Foundation

The NeoForge metadata template still describes a build with registration,
configuration, diagnostics, data generation, and a harmless development item,
and says substantive gameplay systems are intentionally absent
([`neoforge.mods.toml`](../src/main/templates/META-INF/neoforge.mods.toml#L13)).
The repository now contains player-facing workstations and a substantial world
simulation foundation. The author remains `To Be Determined`.

This does not affect engine behavior, but it weakens release and contributor
expectations.

### BCSE-AUDIT-011: Java/NeoForge Deprecation Warnings Remain

A fresh test build produced five deprecation/removal warnings involving event
bus subscriber declarations, data-component registration, and one test use.
They do not fail the Java 21 build today but should be tracked before the next
NeoForge migration.

### BCSE-AUDIT-012: The Single Gradle Module Limits Compile-Time Isolation

Core, Minecraft integration, and all domains currently compile in one Gradle
module. Package rules and tests provide substantial protection, and
[`MODULES.md`](../MODULES.md) intentionally defers physical extraction until
there is evidence from another industry. That decision remains reasonable.

The future risk is that test-based boundaries can be bypassed by a new package
that is not yet covered. Re-evaluate module extraction when a second industry
creates a real reusable-core dependency, as the existing plan already
anticipates.

## Potential Debt

### Technical Debt

- five current Java/NeoForge deprecation warnings;
- several large orchestration and serialization classes;
- shutdown-time rewriting of complete retained histories;
- a growing common lifecycle-service package; and
- source-boundary tests that must be manually extended for new packages.

This debt is manageable today. The history rewrite is the only item that
directly participates in a Critical finding.

### Architectural Debt

- no coordinated persistence generation or crash-recovery contract;
- no evidence retention and archival contract;
- no enforced Scheduler effect semantics;
- Transaction validation authority is not bound to exact content;
- no manifest-to-source conformance mechanism;
- no concrete M22D provider proof;
- M22E-M22F remain intentionally gated; and
- RFC-0023 remains unaccepted and internally ambiguous in several areas.

Most architectural debt is correctly visible and milestone-gated. Coordinated
persistence and lifetime retention are exceptions because existing live
systems already depend on their absence.

## Documentation Issues

### Stale M22D Architecture Validation Summary

The Allocation section of Technical Architecture accurately describes M22D
provider contracts and the empty provider registry
([`TECHNICAL_ARCHITECTURE.md`](../TECHNICAL_ARCHITECTURE.md#L461)).
Later, its Architecture Validation section says the manifest declares
M22A-M22C and "no ... provider"
([`TECHNICAL_ARCHITECTURE.md`](../TECHNICAL_ARCHITECTURE.md#L509)).
The implemented manifest declares the M22D provider framework and
`butchercraft:allocation_providers`.

The Architecture Validation document has a similar early M22A-M22C summary
([`ARCHITECTURE_VALIDATION_FRAMEWORK.md`](ARCHITECTURE_VALIDATION_FRAMEWORK.md#L150))
followed by an accurate M22D update
([`ARCHITECTURE_VALIDATION_FRAMEWORK.md`](ARCHITECTURE_VALIDATION_FRAMEWORK.md#L353)).
The phrase "no live provider" is accurate if it means no concrete
production-grade provider; "no provider" is not.

### Persistence Documentation Is Locally Accurate But Globally Incomplete

Each subsystem documents its own files and failure behavior. No canonical
document defines a consistent BCSE checkpoint, save generation, autosave,
shutdown ordering, or recovery protocol. Readers can incorrectly infer global
durability from repeated statements that individual writes are atomic.

### RFC-0023 Needs A Terminology Pass Before Acceptance

The draft's later parts refine earlier text but do not always supersede it
explicitly. `ExecutionContext`/`ExecutionInput`, optional transaction phases,
proposal ownership, and persistence ownership need one canonical contract.

### Public Metadata Is Stale

The NeoForge description no longer represents the repository's visible
workstation and simulation foundation.

### Link Verification

The audit found no broken repository-local Markdown links in the reviewed
documentation. No duplicate canonical Technical Architecture document was
found.

## Naming Issues

### Multiple `ProductDefinition` Concepts

The repository contains three distinct classes named `ProductDefinition`:

- `com.butchercraft.product.definition.ProductDefinition`
- `com.butchercraft.processing.definition.ProductDefinition`
- `com.butchercraft.world.goods.ProductDefinition`

Their responsibilities are distinguishable in context, but imports and
cross-domain discussions are ambiguous. The economic Good definition is
especially likely to be confused with physical product content as future
industries integrate.

No rename is recommended during the current architecture gate. A terminology
ADR should establish canonical public names before these types become
expansion-facing APIs.

### Broad Role Suffixes

The repository uses many `Manager`, `Service`, `Registry`, `Storage`,
`Pipeline`, `Executor`, and `Adapter` types. Most local roles are sensible, but
the distinction is not documented as a project-wide naming convention. In
particular:

- services usually own Minecraft lifecycle composition;
- managers usually own mutable domain runtime;
- registries usually own indexed definitions or snapshots;
- pipelines/executors orchestrate bounded deterministic operations.

Documenting these observed meanings would reduce concept drift without
requiring mechanical renames.

### Composition Root Growth

`com.butchercraft.world` contains numerous lifecycle services. This is a
recognizable integration boundary today, but it will become difficult to scan
as each domain adds one or more services. Future package organization should
follow an ADR when a concrete navigation or dependency problem appears.

## Future Risks

1. **Evidence multiplication:** Planning, Allocation, Scheduler, Transactions,
   and Execution can each retain reports, traces, history, and snapshots for
   the same business event. Without retention and correlation contracts,
   storage and diagnostic complexity will multiply.
2. **Checkpoint fan-out:** Every new durable owner increases the number of
   cross-file generations that must agree after a crash.
3. **Scheduler side effects:** Generic Execution will amplify the current gap
   between handler effect labels and enforced retry/publication semantics.
4. **Adapter proliferation:** Industry-neutral cores can still become coupled
   indirectly if adapters query live state or perform owner mutation while
   claiming to translate immutable evidence.
5. **Planning centralization:** Directly adding each industry to
   `PlanningPipeline` would turn Planning into a universal dependency hub.
6. **Manifest drift:** A manifest that is not checked against source can remain
   internally valid while the implementation evolves elsewhere.
7. **Public API lock-in:** Ambiguous `ProductDefinition` names and accepted
   validation objects become harder to correct after expansion APIs depend on
   them.
8. **Migration complexity:** Allocation and Execution persistence added before
   a checkpoint-generation contract could require another public schema
   migration soon afterward.
9. **Long server sessions:** Current tests demonstrate bounded operations, not
   bounded memory and disk over millions of live ticks.
10. **Multi-instance composition:** Plugins or expansions may create additional
    pipeline/executor instances unless execution authority is explicit.

## Recommended ADRs

These are decision topics, not implementation designs. Existing accepted
Decisions should remain unchanged unless a new Decision explicitly supersedes
one.

### ADR-A: Coordinated BCSE Checkpoint And Recovery Contract

Decide:

- checkpoint cadence and trigger;
- participating persistence owners;
- checkpoint generation identity;
- publication/commit marker;
- last-known-good handling;
- crash interruption behavior;
- Scheduler/Clock mismatch policy;
- replay or catch-up policy;
- schema migration for current files; and
- testable durability guarantees.

### ADR-B: Evidence Retention, Partitioning, And Archival

Decide separately for runtime snapshots, audit history, reports, and traces:

- retention duration or count;
- hot versus archived evidence;
- deterministic compaction;
- correlation IDs;
- query guarantees;
- storage budgets; and
- behavior when a budget is reached.

### ADR-C: Planning Cadence And Trigger Policy

Decide whether Planning runs every tick, periodically, on relevant evidence
changes, or through a bounded hybrid. Define idle behavior, missed-tick
behavior, and interaction with checkpointing.

### ADR-D: Allocation M22E-M22F Integration

Preserve the already documented owner gate and decide:

- Scheduler stage 350;
- Allocation persistence ownership and schema;
- Planning submission contract;
- Production authorization gate;
- one concrete provider adapter;
- load/save dependency order; and
- failure/recovery semantics.

### ADR-E: Transaction Validation Authority

Define how accepted validation is cryptographically, structurally, or
authority-scope bound to the exact transaction body and inventory snapshot it
approves.

### ADR-F: Scheduler Handler Effect And Execution Authority

Define:

- semantic guarantees for each effect type;
- legal retry behavior;
- when Transaction backing is mandatory;
- failure containment after handler invocation;
- single authoritative pipeline ownership; and
- multi-instance/reentrant behavior.

### ADR-G: Manifest-To-Source Conformance

Decide how build-time checks map source packages, imports, registries,
persistence owners, and work types to the accepted manifest without adding
runtime reflection or scanning.

### ADR-H: Cross-Domain Product Terminology

Define the public distinction among content products, processing products, and
economic Goods before expansion APIs depend on the three current
`ProductDefinition` names.

## Recommended RFC Revisions

### RFC-0022

No rewrite is required for implemented M22A-M22D. Before M22E-M22F:

- reference the coordinated checkpoint ADR;
- define Allocation evidence retention;
- define the concrete provider proof required for integration;
- make stage 350 and load/save ordering normative;
- specify Planning submission and Production authorization failure behavior;
  and
- include crash interruption tests in acceptance criteria.

### RFC-0023

Before acceptance:

- choose one canonical relationship between `ExecutionContext` and
  `ExecutionInput`;
- distinguish mandatory pipeline phases from conditional transaction work;
- assign one owner for Transaction proposal construction;
- bind Transaction results to exact proposals;
- define report, trace, history, and attempt identities;
- define persistence files, owner, generation, load order, migration, and
  recovery;
- reference evidence retention and coordinated checkpoint ADRs;
- define handler effect and retry semantics;
- state the single execution authority invariant;
- describe atomic publication relative to external Transaction application;
  and
- add sustained replay, crash interruption, and adversarial boundary tests to
  the acceptance criteria.

## Recommended Implementation Sequencing

1. **Hold new generic execution implementation.** Keep RFC-0023 in review.
2. **Decide Planning cadence and evidence retention.** Add sustained-cycle
   tests that prove bounded memory, disk growth, save time, and load time.
3. **Decide coordinated persistence.** Add checkpoint generation, interruption,
   last-known-good, Clock/Scheduler mismatch, and migration tests.
4. **Harden boundary semantics.** Bind Transaction validation to exact content
   and formalize Scheduler effect and execution-authority rules.
5. **Accept the M22E-M22F integration ADR.** Implement one production-grade
   provider, Allocation persistence, stage 350, Planning handoff, and
   Production gate as one reviewed vertical slice.
6. **Re-audit the vertical slice.** Verify replay, crash recovery, atomic
   publication, provider isolation, and no direct Planning-to-Production
   authorization bypass.
7. **Revise and accept RFC-0023.** Begin implementation only after its
   persistence and Transaction contracts align with the live platform.
8. **Add a second industry through existing contracts.** Use that evidence to
   decide whether Planning providers, physical Gradle modules, or package
   reorganization are justified.
9. **Correct stale documentation and public metadata in a separate maintenance
   change.**

## Test Coverage Observations

### Evidence Collected

Command:

```powershell
.\gradlew.bat --no-daemon test --rerun-tasks
```

Result:

- Gradle task: PASS
- JUnit suites: 246
- Tests executed: 1,043
- Passed: 1,043
- Failed: 0
- Errors: 0
- Skipped: 0

The forced rerun compiled main and test sources rather than relying on
up-to-date task state.

### Strong Coverage Areas

- immutable model construction and validation;
- duplicate and malformed registry rejection;
- deterministic ordering and replay;
- exact quantity and capacity arithmetic;
- Planning budgets and deterministic selection;
- Allocation lifecycle, cycle-local accounting, rollback, reports, traces, and
  provider isolation;
- Transaction validation, Inventory staging, and atomic failure behavior;
- Production persistence and Scheduler integration;
- Scheduler stage ordering, retry/defer behavior, generated work, and budgets;
- storage schema and malformed-data rejection;
- pure Java package boundaries and forbidden platform imports; and
- architecture manifest consistency.

### Material Test Gaps

- long-running live Planning cadence and retained-history bounds;
- save/load behavior after millions of cycles;
- interruption between independent service save steps;
- recovery from mixed checkpoint generations;
- periodic autosave durability;
- accepted Transaction validation paired with a different same-ID body;
- enforcement of each Scheduler handler effect type;
- two pipelines/executors targeting one authority concurrently;
- manifest-to-source package and import conformance;
- one concrete M22D provider through live observation and Allocation; and
- complete M22E-M22F Planning-to-Allocation-to-Production behavior.

Stress tests that construct large immutable artifact sets are valuable, but
they do not replace sustained lifecycle tests with recurring live work and
persistence.

## Subsystem Consistency Review

### Planning

**Status:** Architecturally aligned with its current production-only scope,
with a critical lifetime-retention defect.

Planning reads externally owned snapshots, produces immutable artifacts, uses
typed submission adapters, respects per-cycle budgets, and does not mutate
Inventory or Production directly. The every-tick continuation and permanent
retention policy are the material gaps.

### Allocation

**Status:** M22A-M22D are consistent with RFC-0022 and accepted Decisions.

Definitions, runtime, detached accounting, deterministic selection, candidate
publication, histories, reports, traces, provider observation, and typed
failure isolation are present. Prohibited future integrations are absent.

### Provider Framework

**Status:** Clean foundation, not yet proven by a production provider.

Providers retain external authority and expose immutable observations.
Invocation and aggregation are deterministic. The empty canonical registry is
an intentional milestone boundary, not an implementation omission. One
concrete adapter is required before the abstraction can be considered proven.

### Transactions

**Status:** Strong owner boundary with one validation-binding weakness.

Economic mutation remains Transaction-owned and Inventory commits are staged.
The accepted validation object should be bound to exact transaction content
before generic Execution carries proposals between subsystems.

### Production

**Status:** Consistent with current Scheduler-driven execution.

Production definitions, plans, runtime, persistence, transaction construction,
and work handling preserve owner boundaries. Allocation authorization is
correctly absent pending M22E-M22F.

### Scheduler

**Status:** Deterministic and bounded per tick, but underspecified for effects,
crash recovery, and multi-instance authority.

Stage ordering, eligibility, work budgets, generated work, retries, and
deferred continuation are explicit. Handler effect declarations currently
provide metadata rather than enforcement.

### Architecture Validation

**Status:** Valuable deterministic contract model with limited source
assurance.

The manifest catches contradictory declared ownership, dependencies,
registries, stages, persistence descriptors, and architectural references. It
does not automatically prove implementation conformance and omits some current
world domains from its component map.

## Long-Term Maintainability Assessment

BCSE is maintainable at its present size because important behavior is
expressed through immutable records, focused registries, stable identities,
typed results, and extensive tests. Contributors can usually locate an owner
and understand whether a class is definition, runtime, evidence, persistence,
or integration.

The principal maintainability threat is not local code quality. It is the
growth of platform-wide concerns that no single current owner controls:

- durable checkpoint generations;
- evidence retention and archival;
- cross-system correlation;
- recurring-work cadence;
- recovery after partial publication; and
- conformance between the architecture manifest and source.

If those concerns are decided before Allocation and Execution become live,
the current architecture can support a long-lived open-source simulation
platform. If they are postponed, each new subsystem will create additional
files, histories, adapters, and recovery relationships that are individually
correct but collectively difficult to operate.

Large orchestration and serialization classes should be watched, but they are
secondary. Decompose them only when a stable responsibility can be extracted
without weakening their atomic invariants.

## Overall Confidence

**Audit confidence: High (0.90).**

Confidence is high because:

- the complete architecture and major subsystem documentation were compared;
- implementation paths for all requested domains were inspected;
- manifest, package boundaries, persistence, and composition were reviewed;
- the full 1,043-test suite was forced to rerun and passed; and
- findings are supported by direct code and document evidence.

Confidence is not absolute because:

- no multi-day live server soak was performed;
- no process-kill persistence experiment was performed;
- no production-grade Allocation provider exists to inspect;
- M22E-M22F are intentionally unimplemented; and
- RFC-0023 is a draft rather than executable architecture.

## Final Audit Conclusion

BCSE has a credible, disciplined architecture and should continue. Its
strongest properties are ownership, deterministic local behavior, immutable
definition/runtime separation, evidence quality, and test rigor. Its weakest
properties are world-lifetime resource policy and coordinated durable state.

The correct next move is not a redesign. It is to close those two platform
contracts, tighten Transaction and Scheduler boundary semantics, complete the
already gated Allocation vertical slice, and only then authorize generic
Execution. With that sequence, BCSE is well-positioned to become a durable
industrial simulation platform rather than a collection of locally correct
subsystems.
