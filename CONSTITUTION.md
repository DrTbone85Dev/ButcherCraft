# ButcherCraft Core Constitution

Part I: Philosophy and Architectural Invariants

Constitution version: 1.0

Status: Governing architectural authority

## 1. Project Identity

ButcherCraft Core is a deterministic industrial and regional simulation platform. It provides the shared identity, time, economic, transaction, persistence, and integration foundations upon which industries and player experiences are built.

ButcherCraft Core is not a meat processing mod. Meat Processing is the flagship industry implementation and the first substantial proof of the platform. It demonstrates the platform's purpose without defining its limit. Agriculture, manufacturing, retail, transportation, utilities, food service, and other industries may participate in the same world through the same Core contracts.

This Constitution is the highest-level architectural authority for ButcherCraft Core, its industry modules, and its compatibility modules. Supporting principles, project rules, technical architecture, decision records, and roadmaps interpret this document and shall not contradict it.

## 2. Mission Statement

ButcherCraft exists to create a coherent simulated world in which industries, businesses, workers, goods, infrastructure, and players participate under shared and explainable rules.

Core shall provide a durable platform that is deterministic, server-authoritative, testable, extensible, and safe for long-lived worlds. Industry implementations shall turn that platform into meaningful play without replacing shared systems with private shortcuts.

## 3. Long-Term Vision

The long-term vision is a living regional simulation whose identity persists, whose economy evolves, and whose industrial systems interact through explicit contracts. The player enters that world as a participant rather than becoming the reason the world exists.

The platform should support multiple industries without parallel clocks, economies, identities, inventories, or persistence foundations. A farm, processing facility, warehouse, carrier, retailer, restaurant, utility, and compatible external mod should be able to exchange stable facts through Core while retaining ownership of specialized behavior.

The project shall favor foundations that remain understandable and maintainable across many years of development, save evolution, platform changes, and contributor turnover.

## 4. Core Philosophy

Simulation comes before gameplay. Core establishes consistent state, causes, constraints, and transitions. Gameplay emerges from participating in that simulation.

This does not mean that realism is pursued without judgment. Simulation exists to produce comprehensible choices, consequences, and stories. Detail that creates no meaningful decision should be simplified, aggregated, or omitted.

The preferred model is data-oriented and compositional. Stable identities, immutable definitions, mutable runtime records, deterministic registries, explicit events, validated transactions, and narrow adapters compose into larger behavior. Deep inheritance hierarchies, hidden global state, and subsystem-specific exceptions are disfavored.

The project values architectural integrity over local convenience. A short implementation that violates ownership, determinism, or compatibility is more expensive than a deliberate implementation that preserves them.

## 5. Architectural Principles

### Simulation Before Gameplay

Core models authoritative state and transitions before presentation or interaction is allowed to depend on them. Gameplay code requests actions and presents outcomes; it does not invent a parallel truth.

### Gameplay Emerges From Simulation

Whenever practical, player choices should use the same businesses, goods, inventories, transactions, capacities, schedules, and constraints as other participants. Player-facing exceptions require an explicit reason and must not undermine shared rules.

### Composition Over Inheritance

Systems should be assembled from focused records, services, policies, capabilities, and adapters. Inheritance is appropriate only where a genuine substitutable relationship exists and composition would obscure rather than clarify ownership.

### Immutable Identity, Mutable Runtime

Identity answers what something is and preserves durable historical meaning. Runtime answers what is happening now. These concerns remain separate, reference one another through stable identifiers, and evolve under different rules.

### Pure Java Domain Isolation

Core domain rules should be expressible and testable without Minecraft or NeoForge. Platform integration belongs at system boundaries and depends inward on the domain.

### Deterministic Execution

The same authoritative inputs, saved state, configuration, explicit seeds, and ordered events must produce the same results. Ordering, validation, and failure behavior are part of the model.

### Layered Architecture

Each layer owns a distinct responsibility and may depend only on lower-level contracts. Integration composes layers without granting lower layers knowledge of higher-level policy or presentation.

### Industry Neutrality

Core concepts describe shared industrial and regional needs. Industry-specific terminology, recipes, equipment behavior, and presentation belong to industry modules.

### Extensibility Before Specialization

Core should expose the smallest general contract proven by real consumers. Specialized behavior should be represented through data, capabilities, policies, or modules rather than hardcoded into shared machinery.

### Data-Oriented Architecture

Stable identifiers, immutable definitions, typed metadata, registries, snapshots, events, and transactions are preferred over opaque object graphs. Data contracts must make ownership, validation, ordering, and persistence visible.

### Long-Term Maintainability

Clarity, bounded responsibility, explicit failure, schema discipline, documentation, and tests are design requirements. Cleverness that makes a system harder to reason about is not an architectural advantage.

### Documentation-Driven Architecture

Material architecture is documented before dependent systems rely upon it. Documents state ownership and constraints; code and tests prove that implementation conforms.

## 6. Architectural Invariants

Architectural invariant identifiers are permanent. They shall not be renumbered or reused. If an invariant is amended or retired, its identifier remains in the historical record and the governing ADR explains the change.

### AI-0001: Deterministic Simulation

Simulation shall remain deterministic. Identical authoritative inputs, saved state, configuration, explicit seeds, and ordered events shall produce identical outcomes.

### AI-0002: Server Authority

The server is the sole authority for consequential simulation and gameplay state. Clients may present state and request actions but shall not commit authoritative outcomes.

### AI-0003: Pure Domain Isolation

Pure domain packages shall never depend on Minecraft or NeoForge. Platform adapters may depend on pure domains; pure domains shall not depend on adapters.

### AI-0004: Immutable Identity Separation

Immutable identity shall never contain mutable runtime state. Runtime conditions shall be stored by a separate owner and reference identity through stable identifiers.

### AI-0005: Runtime Cannot Rewrite Definitions

Mutable runtime state shall never modify immutable definitions or historical identity to represent current conditions.

### AI-0006: Universal Economic Transactions

Every economic mutation shall occur through the Transaction Framework. A subsystem may determine why a change is requested, but it shall not create a private mutation path.

### AI-0007: Transaction-Owned Inventory Mutation

Inventory quantities shall never be modified outside validated Transaction execution. Validation failure shall leave inventory unchanged.

### AI-0008: Immutable Goods

Goods and their defining relationships shall remain immutable definitions. Quantities, ownership, location, condition, and market state belong to separate runtime owners.

### AI-0009: Deterministic Registries

Registries shall reject duplicate stable identifiers and provide deterministic ordering, lookup, validation, and replacement behavior.

### AI-0010: Immutable Public Views

Public APIs shall expose immutable values, snapshots, or deliberately constrained requests. They shall not leak mutable internal collections or runtime records.

### AI-0011: Save Compatibility Priority

Save compatibility takes precedence over implementation convenience. Persisted data shall not be silently discarded, reinterpreted, or reset because a newer implementation is easier to write.

### AI-0012: Industries Depend On Core

Industry modules may depend on Core contracts and services.

### AI-0013: Core Does Not Depend On Industries

Core shall never depend on industry modules, industry registry identifiers, industry recipes, or industry-specific behavior.

### AI-0014: Emergent Gameplay

Gameplay should emerge naturally from authoritative simulation whenever practical. Presentation and interaction may simplify access but shall not create a conflicting simulation.

### AI-0015: Industry-Neutral Core

Core systems shall remain industry-neutral. Shared abstractions shall be named and modeled for their cross-industry responsibility rather than their first consumer.

### AI-0016: Explicit Responsibility Boundaries

Every subsystem shall define both its responsibilities and its forbidden responsibilities. Ownership shall be singular and discoverable.

### AI-0017: Validation Before Execution

Every state-changing subsystem shall define validation before execution. Execution shall consume an accepted request or plan and shall not bypass validation.

### AI-0018: Versioned Persistence

Every persisted format shall be schema-versioned. Unsupported versions shall fail visibly, and migrations shall be explicit, deterministic, and tested.

### AI-0019: Formal Invariant Change Control

An architecture change that affects a constitutional invariant requires a formal Architecture Decision Record and explicit owner approval before implementation.

### AI-0020: Stable Identity Contracts

Durable entities and definitions shall use stable identifiers. Renaming a display value shall not change identity, and released identifiers shall not be repurposed for unrelated meaning.

### AI-0021: Explicit Failure Outcomes

Domain failure shall be represented by explicit, inspectable outcomes. Null values, ignored exceptions, silent partial work, and implicit fallback shall not conceal failure.

### AI-0022: Authoritative Simulation Time

Shared simulated time shall come from the Core Simulation Clock and Event Framework. Subsystems shall not create parallel authoritative clocks or hidden global timers.

### AI-0023: Downward Dependency Flow

Architectural dependencies shall flow from higher layers toward lower-level contracts. A lower layer shall not acquire policy, presentation, or industry knowledge from a higher layer.

### AI-0024: Explicit Randomness

Randomness shall be identifiable, deliberately seeded where practical, ordered deterministically, and testable. Hidden randomness shall not determine authoritative outcomes.

### AI-0025: Singular Data Ownership

Every mutable datum shall have one authoritative owner. Other systems may retain stable references, immutable summaries, or rebuildable caches but shall not create competing authorities.

### AI-0026: Bounded Simulation Work

Simulation work shall be deterministic and bounded. Large systems shall use intervals, indexes, summaries, queues, caps, or profiling rather than unbounded per-tick global scans.

### AI-0027: Tests Are Part Of The Contract

Determinism, ordering, validation, persistence, migration, dependency direction, and authoritative state transitions shall be protected by automated tests proportional to their risk.

### AI-0028: Backward-Compatible Evolution

Released schemas, stable identifiers, and public contracts shall evolve additively when practical. Breaking evolution requires an explicit compatibility analysis, migration plan, and release record.

## 7. Layered Architecture Philosophy

The intended conceptual layers are:

```text
Gameplay
  -> Industry Modules
      -> Economic Simulation
          -> Transactions
              -> Inventory
                  -> Actors
                      -> Goods
                          -> Business Runtime
                              -> Business Identity
                                  -> Simulation Clock
                                      -> Core Platform
```

Dependencies flow downward only. Higher layers may coordinate lower-level capabilities through documented contracts. Lower layers do not import higher-level policy, presentation, or specialization.

The diagram is a responsibility model, not permission to create a single monolithic call chain. Focused services remain separately owned. Events, immutable snapshots, stable identifiers, registries, and transactions connect them without exposing internal mutable state.

Adapters sit at the outside edge. Minecraft lifecycle, world paths, ItemStacks, networking, menus, screens, and external-mod integration translate between platform concerns and pure Core contracts.

## 8. Industry Neutrality

Core owns concepts that must be shared across industries: identity, simulated time, businesses, workforce structure, Goods, Actors, Inventory, Transactions, persistence policy, and future common economic contracts.

Industry modules own products, transformations, facilities, machines, operating policies, specialist data, presentation, and gameplay unique to an industry. They use Core rather than recreating it.

The first industry to need a feature does not automatically own the general abstraction, and Core does not automatically absorb every reusable-looking detail. A concept belongs in Core only when its responsibility is genuinely cross-industry and its contract is proven.

Compatibility modules translate external systems into shared contracts while preserving the external source's ownership. They shall not copy private state into a competing Core authority.

## 9. Simulation Philosophy

Simulation represents causes and state transitions that can be explained. Businesses operate because runtime state, schedules, resources, workers, and events permit them. Goods move because an accepted transaction or future logistics process changes custody and location. Outcomes should be traceable to model inputs rather than arbitrary presentation logic.

Systems should simulate at the coarsest level that preserves meaningful decisions. Aggregate regional behavior is preferable to unnecessary entity-level work. Detailed local behavior is appropriate where it creates interaction, risk, planning, or visible consequence.

Simulation services own narrow domains. No universal manager shall decide identity, time, production, inventory, markets, employees, logistics, and gameplay together.

## 10. Determinism

Determinism is a platform property, not a local implementation detail. It includes stable iteration, explicit ordering, exact arithmetic where required, normalized inputs, versioned schemas, deterministic migrations, and defined failure behavior.

Concurrency shall not make authoritative outcomes dependent on thread timing. Parallel work may calculate independent candidates, but authoritative commit order must be explicit.

Randomness may enrich the simulation only when its source and seed are part of the model. A result that cannot be reproduced from recorded inputs is not suitable for authoritative simulation.

## 11. Server Authority

The server validates and commits world identity, runtime state, inventory, transactions, products, money, ownership, production, employment, orders, markets, inspections, reputation, and progression.

Clients may predict presentation where harmless, but prediction does not become authority. A client request is untrusted input until the server validates it against current state.

Server authority must remain compatible with dedicated-server operation. Client-only classes and rendering concerns shall not enter common or pure domain packages.

## 12. Data Ownership

Every datum has one authoritative owner. Identity systems own identity. Runtime services own current operational state. Goods registries own Good definitions. Inventory owns quantities and locations. Transactions own economic mutation history. Industry modules own their specialized rules and content.

Cross-system communication uses stable identifiers, immutable snapshots, narrow queries, accepted requests, events, and result records. One system shall not reach into another system's mutable collection or rewrite its persisted file.

Caches and indexes must be rebuildable or explicitly owned. A cache shall not quietly become a second source of truth.

## 13. Persistence Philosophy

Persistence is a compatibility contract. Durable state must have an explicit owner, stable identifiers, a schema version, validation rules, and a migration strategy before it is relied upon.

Loading malformed or unsupported authoritative data shall fail visibly. Systems shall not hide corruption by replacing saved state with empty defaults. Unknown optional references should be preserved inertly where practical so a temporarily absent module does not destroy data.

Writes should be deterministic and resilient to interruption. Temporary state, caches, validation objects, and rebuildable indexes should not be persisted merely because they are convenient in memory.

Schema changes are deliberate architecture. They require tests for round trips, malformed data, unsupported versions, migration, and save compatibility.

## 14. API Philosophy

Public APIs are long-term promises. They are introduced only when a real industry or compatibility consumer proves the need and ownership is documented.

APIs expose stable identifiers, immutable values, snapshots, events, capabilities, requests, and explicit results. They do not expose mutable implementation collections, internal registries during construction, or direct state mutation.

An internal abstraction should remain internal while its contract is still being discovered. Extensibility means creating sound extension points, not publishing every implementation detail.

## 15. Compatibility Philosophy

Compatibility is translation, not takeover. External mods retain authority over their own items, fluids, energy, entities, and state. ButcherCraft adapters translate supported external facts into Core contracts without duplicating or corrupting source ownership.

Compatibility shall be optional and isolated. The absence of an external module must not break unrelated Core saves. Missing optional identifiers should be retained or surfaced as unresolved where practical.

Compatibility modules depend on documented Core contracts. Core does not depend on a compatibility module or on an external mod's private implementation.

## 16. Performance Philosophy

Performance is an architectural concern because the platform is intended to simulate a region, not only loaded machines.

Work should be event-driven, indexed, incremental, scheduled at appropriate intervals, and bounded by explicit limits. Full-world scans, per-tick global recomputation, unbounded histories in hot state, and excessive full-state networking are prohibited without measured justification.

Optimization shall preserve determinism and correctness. Profiling and representative stress tests should guide material optimization. Complexity added without evidence is not performance engineering.

## 17. Testing Philosophy

Core domains must be testable without launching Minecraft. Tests are required for immutable construction, validation, deterministic ordering, state transitions, transactions, serialization, persistence, migration, replay, and dependency boundaries.

Minecraft integration requires the appropriate combination of unit tests, integration tests, GameTests, data generation, client launch, and dedicated-server launch. A test at one layer does not prove behavior at another.

Stress tests should represent the scale implied by the platform and verify correctness as well as timing. Tests shall not weaken assertions to make an incorrect implementation pass.

Verification claims must be factual. A command, launch, or manual scenario is reported as successful only when it was actually performed successfully.

## 18. Contribution Philosophy

Contributors are expected to protect the architecture they inherit.

- Prefer architecture over shortcuts.
- Keep responsibilities focused and dependencies directional.
- Preserve deterministic behavior and explicit failure.
- Favor composition, immutable data, and stable identifiers.
- Protect save compatibility and document migration consequences.
- Add extension points only when a real consumer proves them.
- Avoid unrelated refactors and speculative systems.
- Document material decisions and update affected architecture records.
- Write tests before considering work complete.
- Report verification and limitations honestly.

Technical debt is sometimes unavoidable, but it must be named, bounded, and assigned a deliberate resolution path. Hidden architectural debt is not an acceptable delivery strategy.

## 19. Architectural Decision Process

Architecture Decision Records are stored in `DECISIONS.md` unless the project adopts a dedicated ADR directory through a later decision.

A formal ADR is required when a proposal:

- changes, amends, retires, or creates a constitutional invariant;
- changes subsystem ownership or dependency direction;
- introduces or breaks a public API;
- changes a persisted schema or migration policy;
- changes server authority or deterministic execution;
- moves responsibility between Core, an industry module, and a compatibility module;
- accepts significant long-term compatibility or performance consequences.

An ADR states context, decision, rationale, alternatives, consequences, compatibility impact, migration impact, and status. Proposals affecting an invariant cite its permanent identifier directly.

Constitutional invariants shall not be changed casually. An invariant change requires explicit owner approval, a formal ADR, updates to affected governing and technical documents, and a transition plan. Implementation shall not precede that approval.

Ordinary implementation decisions may apply the Constitution without creating a new ADR when ownership, compatibility, and invariants remain unchanged. When uncertainty is material, the safer choice is to document the decision.

## 20. Future Evolution

ButcherCraft is expected to evolve through broad eras:

### Foundation Era

Establish durable identity, time, business, workforce, Goods, Actors, Inventory, Transactions, persistence, validation, and module boundaries.

### Economic Interaction Era

Introduce explicit supply, demand, orders, exchange, reservations, custody, pricing, and accounting contracts through the established mutation and ownership foundations.

### Industrial Simulation Era

Develop deep industry systems that share Core while retaining specialized production, equipment, facilities, workforce needs, and gameplay.

### Regional Economy Era

Connect industries, logistics, consumers, utilities, markets, and regional capacity into a bounded and explainable economy.

### Living World Era

Allow persistent businesses, people, institutions, infrastructure, events, and player legacy to evolve over long simulated histories.

### Compatibility Era

Provide stable, documented contracts through which external mods and optional modules participate without creating competing authorities.

These eras describe direction, not a rigid release sequence. Systems may mature at different rates, and later understanding may refine the roadmap. The Constitution is intended to survive every era. Its principles govern both present implementation and future evolution until deliberately amended through the architectural decision process.
