# ButcherCraft Platform Roadmap

Status: canonical era roadmap

This roadmap describes strategic eras. `MILESTONES.md` remains the detailed implementation history and near-term acceptance record. Completion of an era means its required foundation exists; it does not imply that every future feature named under that domain is implemented.

## Era I: Foundations

Status: completed

Delivered foundations include:

- Pure processing, product, transformation, validation, and transaction domains.
- Data-driven products, transformations, packaging definitions, and atomic content snapshots.
- Generic workstation execution with Grinder, Bandsaw, and Packaging Table proofs.
- Deterministic World Identity with regions, counties, settlements, manufacturers, commercial properties, businesses, ownership, families, and historical supply relationships.
- Persistent runtime player identity.
- Simulation Clock, calendar, scheduler, and event framework.
- Mutable Business Runtime separated from immutable Business Identity.
- Workforce definitions separated from workers.

## Era II: Platform

Status: completed by Phase 13

Delivered platform work includes:

- A canonical living-world vision that is broader than one industry.
- Constitutional core principles for identity, runtime state, server authority, events, modularity, schemas, testing, adapters, and compatibility.
- Explicit Core, industry-module, and compatibility-module responsibilities.
- A conceptual simulation flow from world identity through economy.
- Future economy and compatibility boundaries without speculative implementation.
- A planned public API surface that does not claim current stability.
- Documentation of current package alignment and future extraction criteria.

## Era III: Economic Engine

Status: common Goods, Economic Actor, Inventory, and Transaction foundations implemented; economic behavior future

Planned domains:

- Commodity and product identity foundation (implemented in Phase 14).
- Economic participant identity and capability foundation (implemented in Phase 15).
- Inventory ownership, storage hierarchy, and runtime quantity foundation (implemented in Phase 16).
- Universal validation, atomic mutation, audit, persistence, and replay pipeline (implemented in Phase 17).
- Supply.
- Demand.
- Transportation.
- Warehousing.
- Consumers.
- Markets and price formation.
- Orders, fulfillment, and custody transitions.

Era III must preserve deterministic ordering, bounded simulation, atomic inventory movement, independent persistence ownership, and identity/runtime separation.

## Era IV: Industry Systems

Status: future

Planned industry depth:

- Meat Processing.
- Agriculture.
- Dairy.
- Manufacturing.
- Forestry, Retail, Restaurants, Transportation, and Utilities as later scope permits.

The Meat Processing module remains the flagship implementation. New industries should reuse shared economic contracts rather than create parallel simulations.

## Era V: Civilization

Status: future

Planned regional systems:

- Employment and worker participation.
- Infrastructure.
- Regional growth and decline.
- Utilities and public services.
- Population and institutional demand.

These systems should favor aggregate, event-driven simulation unless individual entities create meaningful gameplay.

## Era VI: Mod Ecosystem

Status: future

Planned ecosystem work:

- Stable public API contracts.
- First-party compatibility modules.
- Third-party industry participation.
- Multiplayer regional economy behavior.
- Compatibility and migration test suites.
- Versioned extension documentation and sample consumers.

API stabilization follows real integrations; it does not precede them.

## Roadmap Guardrails

- Future eras are not authorization to implement every listed system at once.
- Each milestone must define included and excluded scope, persistence ownership, compatibility impact, tests, and rollback considerations.
- Historical milestones are not rewritten when strategic eras change.
- A second industry or real compatibility consumer should validate boundaries before packages or APIs are extracted broadly.
- No era may bypass `CORE_PRINCIPLES.md` or `PROJECT_RULES.md`.
