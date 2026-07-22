# ButcherCraft Platform Vision

Status: canonical long-term vision

## Core Vision

ButcherCraft Core is a deterministic regional world simulation engine.

Industry modules participate in a shared economy.

Players, NPCs, and compatible mods all become participants within the same simulation.

The Meat Processing module is the flagship implementation of this platform. It proves that physical production, products, workstations, businesses, workforce structures, and regional identity can share one coherent simulation without making a single industry the boundary of the project.

## Long-Term Vision

ButcherCraft models a persistent region that has identity, institutions, businesses, infrastructure, and history before a player acts. Over time, the same foundation can support multiple industries, economic relationships, compatible mods, and multiplayer participants without creating a separate simulation for each feature.

The platform should make the world feel established rather than staged for the player. A settlement can contain businesses the player does not own. A manufacturer can have history before its products are purchased. Supply relationships and workforce requirements can exist before gameplay systems activate them. Future economic state may change while preserving those immutable identities.

## Project Philosophy

The player is not the center of the world. The world exists independently.

Businesses may open and close. Goods may move between regions. Markets may fluctuate. People may consume. Industries may compete. The player participates in those systems, influences them, and can build a legacy within them, but does not create the world merely by arriving.

This philosophy applies equally to players, simulated participants, and compatible mods. Each should enter through shared contracts and operate under the same server-authoritative rules.

## Simulation Goals

- Generate stable regional identity deterministically from a world seed.
- Preserve immutable historical identity while runtime state changes over simulated time.
- Use one authoritative simulation clock and event framework across industries.
- Allow businesses, workforce structures, production, distribution, consumers, and markets to become connected without collapsing them into one system.
- Keep outcomes attributable to explicit state and rules rather than hidden randomness.
- Support long-lived worlds through schema versioning and deliberate migration.
- Let industry modules add depth without owning duplicate clocks, economies, identities, or persistence foundations.
- Let compatible mods participate through shared economic contracts when those contracts have real implementations and consumers.

## Player Experience

The intended experience is participation in a living regional economy. A player may begin as a worker, successor, manager, independent owner, cooperative participant, or another future role supported by world identity. Progress comes from learning how the region works, finding a place within existing relationships, making operational decisions, and building a durable legacy.

The flagship Meat Processing module retains its hands-on progression: practical workstations, product handling, facility growth, packaging, storage, workforce planning, and business management. Future industries should offer similarly grounded interactions while sharing the same regional context.

The simulation should remain legible. Players should be able to understand why an operation succeeded, why a business changed state, why demand moved, or why a relationship mattered. Realism is valuable when it creates decisions, not when it creates unexplained burden.

## Design Priorities

1. Preserve deterministic identity and explainable outcomes.
2. Keep mutable runtime state separate from immutable identity.
3. Make the server authoritative for consequential state changes.
4. Build reusable regional services before duplicating systems inside industries.
5. Keep pure domain logic independent of Minecraft and NeoForge.
6. Add public contracts only when a real integration proves their shape.
7. Protect save compatibility through stable ids, schema versions, validation, and migration plans.
8. Prefer bounded, testable systems over broad speculative implementations.
9. Keep each industry playable and coherent without making it its own isolated world model.
10. Preserve Minecraft-like clarity and the established abstract, non-graphic visual direction.

## Current Foundation

The repository already contains the foundations for world identity, geography, manufacturers, commercial properties, businesses, ownership, supply relationships, player identity, simulation time, business runtime state, workforce definitions, products, transformations, packaging, and workstations.

These foundations are not a completed economy. Production scheduling, consumers, markets, prices, transportation, utilities, employees, and AI remain future work. Their absence is intentional: this milestone establishes the direction those systems must follow without implementing them prematurely.

## Relationship To Existing Documents

- `CORE_PRINCIPLES.md` defines the non-negotiable principles that govern implementation.
- `MODULES.md` assigns responsibilities to Core, industry modules, and compatibility modules.
- `SIMULATION_MODEL.md` describes the conceptual regional simulation flow.
- `ECONOMY_MODEL.md` records future economic concepts without implementing them.
- `ROADMAP.md` organizes future development into eras.
- `PROJECT_VISION.md` remains the historical and player-facing vision for the flagship Meat Processing module.

