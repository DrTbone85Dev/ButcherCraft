# ButcherCraft Architecture Review

Status: preserved pre-production Meat Processing design review

Reviewed documents: `AGENTS.md`, `PROJECT_VISION.md`, `GAMEPLAY_DESIGN.md`, `TECHNICAL_ARCHITECTURE.md`, `MODULE_PLAN.md`, `MILESTONES.md`, `DECISIONS.md`, `KNOWN_LIMITATIONS.md`

Scope: architecture review only; no gameplay implementation

This review remains historical rationale for the initial vertical slice. Phase 13 does not rewrite its findings; current platform direction is documented in `VISION.md`, `MODULES.md`, and `TECHNICAL_ARCHITECTURE.md`.

## Executive Summary

The planning direction is coherent, but the proposed core mod currently tries to prove too many interacting systems in the first playable milestone. The highest architectural risk is not any single system; it is the cross-product of product quality, cleanliness, refrigeration, inspections, employees, orders, and facility state all needing each other too early.

Before coding begins, define narrow interfaces for product state, facility identity, work orders, cleanliness summaries, refrigeration summaries, inspection subjects, and employee work roles. Most systems should exchange immutable snapshots or events, not direct references to saved-data classes, block entities, or villager entities.

The first vertical slice should be split into thinner proof steps. Product processing, persistence, one order, basic cleanliness/inspection, and one employee job are each independently risky enough to deserve separate acceptance gates.

## 1. Systems That Are Overly Coupled

### Quality, cleanliness, refrigeration, and inspections

Product quality currently depends on cleanliness, worker skill, station state, freshness, temperature, packaging, and process definitions. MCDA inspections also read cleanliness, product condition, refrigeration, equipment condition, unresolved violations, and possibly employee assignments. These two systems could become coupled through shared calculation logic or direct data access.

Recommendation: quality should consume a `QualityContext` snapshot. Inspections should consume `InspectionSubject` snapshots. Neither should directly query block entities, entities, inventories, or saved-data internals.

### Facility, business, orders, employees, and inspections

Facility identity is planned as the anchor for stations, orders, employees, cleanliness, inspections, refrigeration, and business progression. If the facility system directly owns all of these, it will become the central dependency of the mod.

Recommendation: facility should provide identity, membership, and summaries. Business, inspections, employees, and refrigeration should reference facility ids and ask dedicated services for summaries.

### Work orders, machines, manual actions, and employee AI

Work orders are intended to bridge manual player actions, machines, and employees. That is correct, but it is a coupling hotspot. If a work order knows about every station type, employee type, inventory layout, product rule, and UI path, it will become unmaintainable.

Recommendation: work orders should describe intent and reservation state. Execution should be delegated to station or role-specific handlers.

### ButcherCraft Refrigeration and ButcherCraft Core systems

ButcherCraft Refrigeration needs ButcherCraft Core product temperature components, cleanliness hooks, facility ids, MCDA inspection inputs, and ButcherCraft Commerce summaries. This is a wide dependency surface.

Recommendation: define a small `RefrigerationSummary` API in core. Commerce and MCDA should read summaries, not equipment block entities or room internals.

### Config presets and all simulation systems

Config affects freshness, cleanliness, inspections, employee skills, refrigeration, equipment wear, orders, and shutdowns. A global config object passed everywhere will couple every system to every option.

Recommendation: expose typed config snapshots per domain, such as `QualityConfigView`, `InspectionConfigView`, and `RefrigerationConfigView`.

## 2. Systems That Should Communicate Through Interfaces

Use interfaces or immutable records at these seams:

- Product definitions: product id, category tags, storage requirements, default quality/freshness rules.
- Processing definitions: process id, input requirements, output rules, station compatibility.
- Station execution: what a station can do, how it exposes inventory, and how it reports progress.
- Work orders: queue, reservation, cancellation, completion, and failure reason contracts.
- Employee roles: whether a worker can perform a task, expected duration, error modifiers, and skill gain.
- Facility index: stable facility id lookup, membership summary, station lists, and owner/dimension references.
- Cleanliness: dirty event sink, cleaning action sink, and read-only cleanliness snapshots.
- Refrigeration: read-only storage/room summary, target temperature, capacity status, and product temperature service.
- Quality calculation: `QualityContext`, `QualityResult`, and optional trace entries.
- Inspection: inspection subjects, rule inputs, violation results, escalation decisions, and reinspection scheduling.
- Orders and customers: order template, active order, fulfillment check, payment/reputation result.
- Persistence: stable ids, versioned codecs, unknown-id behavior, and migration hooks.
- Networking: payload conventions and server-side action validators.
- Expansion integration: public API registration, tags, optional capability lookups, and datapack registry contracts.

Interfaces should be small and domain-specific. Avoid a single `IFacility` or `IBusiness` interface that becomes a dumping ground.

## 3. Likely "God Objects"

These classes or concepts are likely to become God Objects if implementation is not constrained:

- `FacilitySavedData`: risk of owning facilities, stations, employees, cleanliness, orders, inspections, and refrigeration.
- `BusinessSavedData`: risk of becoming ledger, reputation, unlock manager, order store, employee registry, and inspection record.
- `FacilityControllerBlockEntity`: risk of owning room validation, station lookup, refrigeration, cleanliness, UI, inspections, and work orders.
- `QualityService`: risk of absorbing every product, station, worker, cleanliness, freshness, and config rule.
- `InspectionManager` or `MCDASystem`: risk of directly scanning the world, applying fines, managing shutdowns, sending UI, and mutating business data.
- `WorkOrderManager`: risk of encoding every station, employee, machine, routing, inventory, and customer rule.
- `EmployeeTaskSelector` or `EmployeeBrain`: risk of combining pathfinding, reservations, skills, job definitions, business logic, and station execution.
- `RefrigerationRoomControllerBlockEntity`: risk of storing full room membership, equipment graph, product temperatures, inventory, UI state, wear, and inspection data.
- `OrderBoardBlockEntity`: risk of becoming the business UI, order store, payment processor, reputation manager, and customer generator.
- `ModConfig`: risk of becoming a single global dependency for every formula and behavior.
- Registry classes such as `ModBlocks` and `ModItems`: risk is lower, but they can become hard to navigate if content volume grows without grouping.

Mitigation: keep saved data as storage plus transactional methods, keep block entities local, keep services pure where possible, and use domain snapshots for cross-system reads.

## 4. Data That Will Need Network Synchronization

### Item stack data

- Product id and form.
- Quality summary and visible quality tier.
- Freshness or spoilage state.
- Temperature band or storage warning state.
- Packaging state, label, and order-related metadata if visible.

These should use network-synchronized item data components only for values needed by client tooltips, inventories, and menus.

### Menus and screens

- Station inventory slots.
- Station progress and current process.
- Grinder and packaging station progress.
- Refrigerated storage temperature/status.
- Room validation and capacity summary.
- Order board active orders, selected order, requirements, and fulfillment status.
- Employee assignment, current job, skill display, and blocked state.
- MCDA notices, inspection reports, violation list, fine/shutdown status.
- Business ledger summary, reputation tier, and unlock state.

### World feedback

- Cleanliness display state for stations, rooms, or facility summaries.
- Work-order state changes and reservation status.
- Refrigeration overload/failure warnings.
- Multiblock valid/invalid state.
- Server-approved particles and sounds for processing, cleaning, inspection notices, and machine status.

### Server config visibility

- Client-visible parts of server config, especially strictness presets and UI-relevant thresholds.

Do not synchronize full facility internals every tick. Sync compact summaries on open menus, state changes, and coarse intervals.

## 5. Systems That Should Use SavedData

Use `SavedData` for durable world-level or facility-level state:

- Business ledger summary, reputation, progression unlocks, and completed-order history.
- Active and completed customer orders when they are not purely tied to one block entity.
- Facility registry: facility ids, controller positions, dimension keys, owner references, and membership summaries.
- MCDA inspection history, active write-ups, fines, shutdown state, reinspection windows, and violation history.
- Work-order queues and reservations if they must survive save/load.
- Facility-level cleanliness summaries if cleanliness is not fully local to stations or chunks.
- Refrigeration room registry and durable room summaries for walk-in rooms.
- Known lots or batches if lot ids become meaningful across orders, recalls, or inspections.
- Unknown-id preservation records when expansion content is missing.

SavedData should not store high-frequency transient caches, pathfinding state, menu state, or full per-stack thermal histories.

## 6. Systems That Should Use Attachments

Use attachments for data that belongs to a specific supported holder:

- Employee villager data: employment state, facility id, job role, skill levels, training progress, current assignment summary, and preferences.
- Customer villager data, if individual recurring customers exist: customer archetype, loyalty, cooldowns, and active visit state.
- Inspector entity data, if inspectors become actual entities: assigned facility, current inspection stage, and temporary report builder.
- Chunk or zone cleanliness aggregates, if facility cleanliness is spatial and chunk-level storage proves useful.
- Chunk or zone facility markers, if used for fast lookup and rebuildable from SavedData.
- Player-specific business UI preferences or tutorial flags, if later needed.
- Non-persistent runtime caches only when they can be rebuilt safely.

Do not use attachments for item stack product state. Use item data components for that.

## 7. Systems That Belong on Entities

Entity-owned systems:

- Employee identity as a villager worker.
- Employee role, skill, work cooldowns, current task target, pathing state, and local AI memory.
- Customer identity, visit intent, order pickup/dropoff behavior, and interaction cooldowns.
- Inspector presence and pathing, if MCDA inspectors are represented physically.
- Short-lived animation or behavior state associated with entity actions.

Entity systems should not own:

- Business ledger.
- Facility registry.
- Active order source of truth.
- MCDA violation history.
- Facility-wide cleanliness.
- Refrigeration state.

Entities can mirror ids and summaries, but saved world state should remain in SavedData or local block entities.

## 8. Systems That Belong on Block Entities

Block-entity-owned systems:

- Manual processing station local inventory, progress, station cleanliness, and current process state.
- Grinder inventory, progress, machine condition, and local output state.
- Packaging station inventory, progress, package selection, and local cleanliness.
- Simple refrigerated storage inventory, target temperature band, local storage status, and local cooling state.
- Multiblock controller validation cache, member summary, room mode, and UI-facing room status.
- Refrigeration equipment condition, capacity contribution, wear, overload state, and failure state.
- Order board UI anchor and local interaction state, if it is a block.
- Facility controller local anchor state, if controller-based facilities are approved.

Block entities should not own:

- Global order history.
- Business finances.
- MCDA escalation history.
- Employee skill source of truth.
- Expansion-wide registries.

Block entities should expose capabilities or domain interfaces, not be passed directly into business, inspection, or employee systems.

## 9. Most Likely Performance Bottlenecks

- Facility-wide scans for cleanliness, station lookup, inspections, or refrigeration.
- Multiblock room validation, especially across chunk boundaries and after frequent block changes.
- Refrigeration simulation if every stored product stack updates every tick.
- Employee AI if every worker scans all orders and all stations every tick.
- Pathfinding in dense player-built facilities.
- Capability lookups performed repeatedly without caching.
- Network traffic from syncing full order boards, facility summaries, or room data too often.
- SavedData churn if large structures are marked dirty every tick.
- Item data component churn if quality/freshness/temperature are rewritten too frequently.
- Inspection scans that enumerate all inventories, products, machines, and rooms at once.
- Optional expansion lookups that repeatedly resolve missing ids or tags.

Mitigations should be part of the first implementation pass: event-driven invalidation, coarse intervals, capped scans, dirty-region queues, lazy product freshness updates, cached capability lookups, and compact network summaries.

## 10. Duplication Exploits to Prevent

- Machine output duplication when a station is broken during processing.
- Inventory duplication when hoppers, menus, employees, and players access the same station concurrently.
- Work-order duplication when two employees or an employee and player complete the same order.
- Payment or reputation duplication from submitting the same product stack more than once.
- Order fulfillment duplication from client-side repeated button presses.
- Save/load rollback duplication where products are delivered, then world state reloads before the order marks complete.
- Product component mismatch exploits where quality, freshness, packaging, or lot data is lost during stack merging.
- Packaging label duplication if order-specific labels can be copied to unrelated products.
- Refrigerated storage extraction/insertion exploits during temperature or freshness evaluation.
- Multiblock controller duplication where multiple controllers claim the same room or equipment.
- Employee reservation duplication if a reserved station unloads and reloads without releasing or restoring the reservation.
- Block break/drop duplication for machine inventories and stored products.
- Client packet spoofing for quality, payment, employee skill gain, inspection pass/fail, or shutdown removal.

Prevent these with server-side transactions, reservation tokens, idempotent order completion, strict item component merge rules, block-break inventory tests, and packet validation.

## 11. Milestones That Appear Too Large

### Milestone 1: First Playable Vertical Slice

This is the largest risk. It includes product components, three workstations, refrigeration, orders, business updates, employee AI, cleanliness, MCDA inspection, persistence, assets, and manual verification. That is too much for one implementation milestone.

### Milestone 3: Facility and Work-Order Expansion

Facility identity, work-order UI, employee reservations, station queues, cleanliness summaries, and inspection reports are tightly related but each can reveal architecture flaws.

### Milestone 5: Employee Skill and Cleanliness Depth

Employee skill and cleanliness depth both feed quality and inspections. Developing both in one milestone could hide cause/effect bugs.

### Milestone 8: Balance, Config, and Release Candidate

Release candidates normally include broad polish, but this milestone combines balance, config, UI, profiling, dedicated server validation, migration review, docs, and asset audit. It should be treated as a release phase, not a single milestone.

## 12. Milestones That Should Be Split

Recommended split:

- Milestone 1A: product data components, one product, one manual station, save/load.
- Milestone 1B: grinder and packaging station, with no employee or MCDA dependency.
- Milestone 1C: simple refrigerated storage and freshness/temperature behavior.
- Milestone 1D: one customer order and minimal business/reputation update.
- Milestone 1E: basic cleanliness and basic MCDA write-up.
- Milestone 1F: one employee job using an already-proven work path.

Further splits:

- Milestone 2A: versioned persistence for product, order, business, and inspection data.
- Milestone 2B: unknown-id and missing-expansion fallback tests.
- Milestone 3A: facility identity/controller and membership summary.
- Milestone 3B: work-order queue, reservations, and station queues.
- Milestone 4A: room validation only.
- Milestone 4B: capacity demand and target temperature.
- Milestone 4C: overload warning, then later wear/failure.
- Milestone 5A: employee skill progression.
- Milestone 5B: cleanliness depth and cleaning role.
- Milestone 6A: warning/write-up/fine escalation.
- Milestone 6B: shutdown and reinspection.
- Milestone 7A: core order/customer API.
- Milestone 7B: compile-only sample expansion or integration test.

## 13. Gameplay Systems Likely to Require Redesign Later

- Quality scale and formula. Too many factors are planned, and the first version will likely reveal which ones players understand.
- Cleanliness aggregation. Station, room, chunk, and facility scoring may need adjustment once facilities vary in size.
- Facility boundaries. Controller-based, registered-area, and inferred-area models have different player experience costs.
- Refrigeration. Full thermal modeling may need simplification to stay performant and readable.
- Employee AI. Villager pathfinding and work reservations are likely to force redesign around real Minecraft behavior.
- Work orders. The same abstraction must support manual actions, machines, employees, and orders; early shape may be wrong.
- MCDA escalation. Penalties may need tuning to avoid frustration or accidental soft locks.
- Business progression and reputation. It will depend heavily on order generation and quality balance.
- Customer order generation. Simple orders may not scale naturally into wholesale and logistics.
- UI summaries. Players may need different levels of detail than the architecture initially predicts.
- Config presets. Forgiving versus realistic modes may require different formulas, not just different constants.
- Expansion data-driven registries. Formats may need revision after one real expansion prototype.

## 14. APIs to Define Before Coding Begins

Define these before gameplay code starts:

- Stable id policy: mod id, package, registry naming, facility id, order id, work-order id, lot id if used.
- Product component records: product, quality, freshness, temperature, and packaging.
- Product definition API: product categories, storage requirements, valid processing paths, and unknown-id fallback.
- Process definition API: input requirements, output result, station compatibility, duration, and quality hooks.
- Station interface: inventory access, progress snapshot, process support, cleanliness contribution, and block break transaction behavior.
- Quality API: `QualityContext`, `QualityResult`, visible quality tier, and trace entry shape.
- Cleanliness API: dirty events, cleaning events, local snapshot, facility summary, and inspection-facing view.
- Refrigeration API: storage summary, temperature band, capacity status, room summary, and product temperature update contract.
- Facility API: facility id lookup, controller/member summary, station listing, dimension/position references, and invalidation events.
- Work-order API: queue state, reservation token, execution request, completion result, cancellation, and failure reason.
- Employee role API: role id, task filter, skill modifiers, work duration, error rules, and skill gain contract.
- Order/customer API: order template, active order, requirement checks, fulfillment transaction, payment/reputation result.
- Inspection API: inspection subject, rule input, violation result, escalation decision, and reinspection workflow.
- Persistence API: version fields, codec strategy, missing content behavior, and migration boundary.
- Network API conventions: payload naming, validation rules, menu sync model, and idempotency for client requests.
- Expansion API stability labels: internal, experimental, and supported.

## 15. Major Risks

### Critical risks

- Vertical slice scope is too large and may encourage rushed, tightly coupled code.
- Facility state can become a God Object dependency for the whole mod.
- Save data loss or silent discard would violate a hard project constraint.
- Duplication exploits are likely because products move through inventories, employees, machines, orders, and menus.
- Client/server mistakes can corrupt business outcomes or crash dedicated servers.

### High risks

- Employee AI may be unreliable in real player-built facilities.
- Refrigeration simulation can become too expensive or too opaque.
- MCDA shutdowns can frustrate players or create soft-lock situations.
- Quality and cleanliness formulas can become impossible for players to understand.
- Expansion APIs may be defined too late, causing expansions to depend on internals.
- Missing expansion content can break saves if unknown ids are not preserved.
- Multiblock validation across chunks can cause performance and correctness bugs.
- Work-order reservations can deadlock or duplicate work if stations unload or fail.
- Registry names and data formats may stabilize too late.

### Medium risks

- Config presets may not be enough if realistic and forgiving modes need different behavior.
- Commerce may overpower production gameplay if payments and reputation are balanced too early.
- Placeholder assets may obscure usability issues if station states are not visually clear.
- Data-driven registries may be introduced either too early, slowing the slice, or too late, blocking expansions.
- Full UI scope may grow quickly because every system needs status, warnings, and explanations.
- Testing infrastructure may lag behind simulation complexity.
- Mod identity and package approval are still unresolved.

### Content and product risks

- MCDA identity must remain fictional and avoid real USDA branding.
- Processing visuals must remain abstract and non-graphic.
- The first product choice may set expectations for the whole mod.
- Expansion release order may affect what public APIs become stable first.

## Required Pre-Production Actions

1. Approve mod id, base package, first product, and default strictness.
2. Split Milestone 1 before implementation begins.
3. Define product, facility, work-order, cleanliness, refrigeration, inspection, order, and employee APIs.
4. Decide whether facility boundaries exist in the vertical slice or start later.
5. Write duplication-prevention requirements for every inventory-moving block.
6. Define save/load and unknown-id behavior before the first persistent gameplay data ships.
7. Require dedicated server checks as soon as client screens or entity AI are added.
8. Build one compile-only sample expansion before marking public APIs stable.
