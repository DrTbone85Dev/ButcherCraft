# ButcherCraft Core Principles

Status: authoritative and immutable without an explicit owner decision

These principles are the constitutional rules for ButcherCraft Core, industry modules, and compatibility modules. `PROJECT_RULES.md` contains operational engineering invariants and remains authoritative alongside this document. Accepted records in `DECISIONS.md` explain how individual choices apply these principles.

## 1. Deterministic Simulation

The same identity inputs, saved state, configuration, event sequence, and explicit random seed must produce the same result. Ordering must be stable. Randomness, when eventually used, must be visible in the model, deliberately seeded where practical, and testable.

## 2. Immutable Identity

Generated world identity and permanent historical records are immutable snapshots. Regions, counties, settlements, properties, businesses, families, manufacturers, ownership histories, and other identity records are referenced by stable ids rather than rewritten to represent runtime change.

## 3. Mutable Runtime State

Operational state changes over simulated time, but it must have a clear owner and explicit transitions. Runtime state references immutable identity; it does not duplicate or mutate identity records. Business operations, workforce occupancy, production, markets, and similar future systems require separate versioned state.

## 4. Server Authority

The server decides all consequential simulation and gameplay outcomes, including inventory changes, products, money, ownership, production, employment, orders, reputation, inspections, and progression. Clients display synchronized state and submit requests; they do not commit outcomes.

## 5. Event-Driven Simulation

Shared simulated time and explicit events coordinate systems. Future services observe the authoritative Simulation Clock and Event Framework instead of creating independent clocks, hidden timers, or per-tick global scans. Events carry narrow facts and do not grant unrestricted access to another system's mutable state.

## 6. Modular Architecture

Core owns shared regional identity, simulation services, persistence foundations, and eventually shared economic contracts. Industry modules own industry-specific products, transformations, equipment, operating rules, and presentation. Compatibility modules translate external capabilities into shared contracts. Modules communicate through stable ids, immutable views, events, registries, and deliberately documented APIs.

## 7. Schema Versioning

Every durable structure has a schema version before it becomes save-relevant. Unsupported schemas fail visibly. Migrations are explicit, deterministic, tested, and preserve unknown ids where practical. No placeholder system may silently discard saved state.

## 8. Extensive Automated Testing

Every core domain must be testable without launching Minecraft. Determinism, validation, ordering, serialization, migrations, persistence, dependency direction, and state transitions require automated coverage proportional to their risk. GameTests and launch checks supplement pure tests where Minecraft behavior is involved.

## 9. Minecraft Adapters Only At System Boundaries

Pure domain packages do not import Minecraft or NeoForge. Lifecycle hooks, ItemStack conversion, registry access, networking, menus, screens, and world paths are adapters around those domains. Dependency direction points from integration code toward domain code, never the reverse.

## 10. Long-Term Backwards Compatibility

Stable ids, persisted schemas, data contracts, and public APIs are compatibility commitments once released. Evolution should be additive when practical. Breaking changes require an explicit decision, migration plan, release note, and test coverage. Missing optional modules must not cause unrelated saved state to be deleted.

## Supporting Principles

- The player participates in the world; the world is not generated as a reaction to player ownership.
- Identity, runtime operations, workforce, production, logistics, consumers, and markets remain separate responsibilities.
- Transactional operations must prevent duplication, partial commits, and silent loss.
- Failures are explicit domain outcomes, not nulls or ignored exceptions.
- Realism serves comprehensible gameplay.
- Work is deterministic and bounded; large simulations require coarse intervals, indexes, summaries, and profiling.
- Public APIs are earned through real consumers rather than guessed in advance.
- Historical decisions and milestone records remain available even when later decisions supersede their active direction.

## Change Control

Changing a core principle requires an explicit owner-approved decision in `DECISIONS.md`, an explanation of compatibility consequences, and updates to affected architecture, roadmap, and rules documents. Ordinary feature milestones may apply these principles but may not silently redefine them.

