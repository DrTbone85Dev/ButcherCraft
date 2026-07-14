# Planning Changelog

Status: pre-implementation reconciliation

## Changes Made

### Split the First Playable Vertical Slice

Changed `MILESTONES.md` to split the original Milestone 1 into Milestones 1A through 1F.

Why: the original milestone included product components, three stations, refrigeration, orders, business state, employee AI, cleanliness, MCDA inspection, persistence, assets, and manual verification in one step.

Architecture-review finding: "Milestone 1: First Playable Vertical Slice" was identified as too large and likely to encourage rushed, tightly coupled code.

### Clarified Interface Boundaries

Updated `TECHNICAL_ARCHITECTURE.md` with pre-implementation boundaries for product/quality, cleanliness, refrigeration, inspections, work orders, employee roles, facility state, and client menus.

Why: these systems need to cooperate without directly depending on saved-data internals, block entities, or villager entities.

Architecture-review finding: quality, cleanliness, refrigeration, inspections, facility, business, orders, employees, and work orders were identified as coupling hotspots.

### Clarified Persistence Ownership

Updated `TECHNICAL_ARCHITECTURE.md` to state which state belongs in `SavedData`, entity attachments, block entities, or rebuildable caches.

Why: the project must not silently discard invalid saved data, and early persistence decisions will shape future saves.

Architecture-review finding: facility, business, order, inspection, work-order, cleanliness, refrigeration, and employee data needed clearer ownership.

### Clarified Client/Server Ownership

Updated `TECHNICAL_ARCHITECTURE.md` to reinforce that gameplay state, inventory transactions, work completion, order fulfillment, and persistence remain server-owned.

Why: the mod must remain dedicated-server compatible and must not trust client requests for gameplay outcomes.

Architecture-review finding: network synchronization and client/server mistakes were identified as critical risks.

### Added Duplication and Inventory-Loss Safeguards

Updated `TECHNICAL_ARCHITECTURE.md` with required safeguards for block break, menus, employees, work orders, order fulfillment, product component merging, packaging labels, and unknown ids.

Why: item-moving systems are likely to cross inventories, stations, menus, employees, and orders.

Architecture-review finding: duplication exploits and inventory loss were identified as critical risks.

### Recorded Accepted Implementation Decisions

Updated `DECISIONS.md` with accepted decisions to split the first vertical slice, define domain boundaries before substantive gameplay, and require server-side transactions for item-moving systems.

Why: these are pre-implementation decisions that should guide future coding agents.

Architecture-review finding: likely God Objects, coupling risks, and duplication risks required explicit project decisions before implementation begins.

### Documented Foundation Limits

Updated `KNOWN_LIMITATIONS.md` and `AGENTS.md` to clarify that the development item and diagnostic command are foundation checks only.

Why: the project setup must not be mistaken for meat-processing gameplay implementation.

Architecture-review finding: the review warned against speculative abstractions and scope creep in the first milestone.

## Recommendations Intentionally Deferred

- Full public API definitions for expansion mods are deferred until each near-term gameplay subsystem is implemented or prototyped.
- Detailed refrigeration interfaces beyond safe summaries are deferred until the simple refrigerated storage milestone.
- Employee role APIs beyond one basic role are deferred until Milestone 1F.
- MCDA fines, shutdowns, and reinspections are deferred until the later MCDA escalation milestone.
- Commerce expansion APIs are deferred until the Commerce readiness milestone.
- A compile-only sample expansion is deferred until public APIs stabilize enough to test.
- Deep profiling is deferred until there is meaningful simulation load to profile.
