# ButcherCraft Decision Log

Status: initial architecture decision log

Decision statuses:

- Proposed: recommended but needs owner approval or prototype confirmation.
- Accepted: treated as project direction.
- Final: required by the user's brief or target platform.
- Superseded: replaced by a later decision.

## DEC-0001: Target Minecraft, Loader, Java, and Language

Status: Final

Decision: target Minecraft 1.21.1, NeoForge, Java 21, and Java as the primary language.

Rationale: this is a project constraint from the brief.

Consequences:

- Architecture must follow NeoForge 1.21.1 practices.
- Avoid APIs from later Minecraft or NeoForge versions unless the project target changes.

## DEC-0002: Mod Identity

Status: Accepted

Decision: use ButcherCraft as the project name, `butchercraft` as the mod id and asset namespace, and `com.butchercraft` as the base Java package.

Rationale: this is an approved owner decision made before implementation begins. The previous working name is superseded for active planning.

Consequences:

- Planning, generated assets, package paths, translation keys, registry namespaces, and future implementation should use the approved identity.
- Historical references to the earlier working name should only appear when explicitly discussing decision history.
- Artifact coordinates remain TBD until repository setup.

## DEC-0003: Core Mod Plus Substantial Expansions

Status: Proposed

Decision: build ButcherCraft Core and a limited number of substantial optional expansions: ButcherCraft Harvest & Fabrication, ButcherCraft Refrigeration, ButcherCraft Further Processing, and ButcherCraft Commerce.

Rationale: this avoids a fragmented mod ecosystem while keeping advanced systems optional.

Consequences:

- Core must expose documented APIs.
- Expansions must avoid tight coupling to one another.
- No separate downloadable mod should be created for every machine.

## DEC-0004: Manual-First Progression

Status: Final

Decision: every essential early job must be performable manually by the player before employees or advanced machines are required.

Rationale: this is a core design principle from the brief.

Consequences:

- Employee AI cannot be the only way to complete required work.
- Machines should increase throughput, consistency, or scale rather than replace the entire game loop.

## DEC-0005: Abstract, Non-Graphic Visual Direction

Status: Final

Decision: avoid graphic or unnecessarily disturbing animal-processing visuals and use abstract Minecraft-style representations.

Rationale: this is a project constraint and keeps the mod aligned with Minecraft's presentation.

Consequences:

- Placeholder assets must also follow this rule.
- Harvest/fabrication expansion must remain abstract.

## DEC-0006: Product State Uses Item Data Components

Status: Proposed

Decision: store product id, quality, freshness, temperature, and packaging state using item data components.

Rationale: item data components are the modern item stack data model for this target and support typed persistent and network-synchronized values.

Consequences:

- Component values should be immutable records with codecs.
- Tooltips and menus read component summaries rather than raw NBT.
- Product data migrations must be planned before public saves.

## DEC-0007: Business and Inspection State Uses SavedData

Status: Proposed

Decision: use `SavedData` for business ledger, order state, facility identity, and MCDA inspection history.

Rationale: these are world/facility-level systems that must persist beyond individual block entities.

Consequences:

- Saved data must call dirty marking after mutation.
- Data structures need versioning.
- Missing expansion content must not silently delete saved records.

## DEC-0008: Employee State Uses Entity Attachments

Status: Proposed

Decision: store employee skill, job role, business assignment, and work preferences on villager entities through data attachments.

Rationale: employee data belongs to individual entities and should survive entity save/load.

Consequences:

- Employee systems must handle villager death, unload, and missing business references.
- Business saved data should keep only stable references and summaries.

## DEC-0009: Server-Authoritative Simulation

Status: Proposed

Decision: the server owns product changes, quality, freshness, employee work, orders, payments, MCDA results, cleanliness, refrigeration, and persistence.

Rationale: this prevents client desync, cheating, and dedicated-server bugs.

Consequences:

- Client requests are validated on the server.
- Screens display synced state but do not decide outcomes.
- Client-only classes stay isolated.

## DEC-0010: Central Quality Service

Status: Proposed

Decision: calculate quality through a centralized service rather than embedding formula fragments in every station.

Rationale: quality depends on many systems and needs to be testable and balanceable.

Consequences:

- Stations and employees gather context and call the service.
- Formula coefficients can become config or data-driven after the shape stabilizes.
- Tests can cover quality without launching a full game session.

## DEC-0011: Event-Driven Cleanliness

Status: Proposed

Decision: model cleanliness through dirty/clean events and cached summaries rather than per-block per-tick scanning.

Rationale: facility-scale cleanliness can become expensive if simulated naively.

Consequences:

- Processing systems must emit cleanliness events.
- Facility summaries must update on relevant changes.
- Cleaning actions must persist meaningful state.

## DEC-0012: Simple Refrigeration in Core, Detailed Refrigeration in Expansion

Status: Proposed

Decision: include a simple refrigerated storage system in ButcherCraft Core and move walk-in rooms, compressors, condensers, evaporators, overload, wear, and failures into ButcherCraft Refrigeration.

Rationale: the vertical slice needs cold storage, but full refrigeration engineering is large enough to be optional.

Consequences:

- Core refrigeration API must be designed early.
- The simple storage path must remain useful as a fallback.
- Commerce and MCDA should read refrigeration summaries through stable interfaces.

## DEC-0013: Controller-Based Multiblocks

Status: Proposed

Decision: use controller block entities for scalable rooms and major multiblock machines.

Rationale: controllers give validation, persistence, UI, and inspection systems a stable anchor.

Consequences:

- Validation must be cached and event-driven.
- Member blocks need clear ownership and invalidation rules.
- Chunk boundary and unload behavior require prototypes.

## DEC-0014: Villager-Based Employees

Status: Proposed

Decision: model employees as villager-based workers with modded jobs, skills, and assignments.

Rationale: villagers fit Minecraft's world language and customer/employee theme.

Consequences:

- AI and pathfinding are a technical risk.
- The first employee role should be intentionally narrow.
- Employee data must survive unload/reload.

## DEC-0015: Data-Driven Definitions After Shape Is Proven

Status: Proposed

Decision: start the vertical slice with minimal code-defined definitions where faster, then move products, processes, order templates, and inspection rules to datapack registries or generated data once the shapes are stable.

Rationale: premature data abstraction can slow the first playable loop, but expansion content needs stable data-driven hooks.

Consequences:

- Keep service boundaries clean from the start.
- Do not hard-code logic in ways that block data-driven migration.
- Document public data formats before expansions rely on them.

## DEC-0016: Placeholder Assets Are Required When Final Art Is Missing

Status: Accepted

Decision: use placeholder assets rather than blocking implementation on final art, provided they are clearly non-graphic and Minecraft-style.

Rationale: gameplay validation should not wait for final visuals.

Consequences:

- Placeholder assets need clear names and should be easy to replace.
- Placeholder status should be documented in milestone notes.

## DEC-0017: No Silent Data Discard

Status: Final

Decision: placeholder or prototype systems must not silently discard saved product, business, order, employee, cleanliness, refrigeration, or inspection data.

Rationale: this is a project constraint and a save-safety requirement.

Consequences:

- If data is not yet supported, fail visibly in development or keep it inert but preserved.
- Migrations and unknown-id behavior must be planned.

## DEC-0018: First Playable Vertical Slice Content

Status: Final

Decision: the first playable vertical slice must include one basic meat product, one manual processing station, one grinder, one packaging station, one simple refrigerated storage system, one customer order, one basic employee job, basic cleanliness, and a basic MCDA inspection.

Rationale: this is a required milestone target from the brief.

Consequences:

- Milestone 1 should not expand content breadth before the loop is complete.
- Every included system must persist enough state to support save/load.

## DEC-0019: Split the First Vertical Slice Into Smaller Milestones

Status: Accepted

Decision: split the original first playable vertical slice into Milestones 1A through 1F: product/manual station, grinder/packaging, refrigerated storage, customer order, cleanliness/MCDA write-up, and one employee job.

Rationale: the architecture review found the original Milestone 1 too large and likely to encourage tightly coupled implementations.

Consequences:

- Each subsystem gets an independent acceptance gate before the complete loop is assembled.
- Persistence and duplication safeguards can be verified earlier.
- The full vertical slice remains the product goal, but implementation proceeds in smaller increments.
- Milestones 1B and 1C were later redefined by DEC-0023 and DEC-0024 as pure engine/framework milestones; Milestone 1D was later redefined by DEC-0025 as product data integration; visible station, refrigeration, and commerce slices are deferred for owner scheduling.

## DEC-0020: Domain Boundaries Before Gameplay Implementation

Status: Accepted

Decision: before implementing substantive gameplay, define narrow boundaries for product/quality, cleanliness, refrigeration, inspections, work orders, employee roles, facility identity, and order fulfillment.

Rationale: the architecture review identified these as high-risk coupling points and likely sources of God Objects.

Consequences:

- Systems should exchange immutable snapshots, events, ids, and reservation tokens rather than direct saved-data, block-entity, or entity references.
- Public APIs remain documented before expansions depend on them.
- The project foundation may include registration and diagnostic plumbing, but not speculative gameplay abstractions.

## DEC-0021: Server-Side Transactions for Item-Moving Systems

Status: Accepted

Decision: every future system that moves products, station inventories, packaging labels, order deliveries, or work-order outputs must use server-side transactions and duplication safeguards.

Rationale: the architecture review identified duplication and inventory-loss exploits as a critical risk.

Consequences:

- Client requests must be validated and idempotent where completion or payment is possible.
- Block break, save/load, station unload, and concurrent employee/player access require explicit tests.
- Item component merge rules must be defined before public saves.

## DEC-0022: Foundation-Only Registered Content

Status: Accepted

Decision: the repository foundation may register one harmless development test item, one ButcherCraft creative tab, common configuration, logging, data-generation providers, and a safe diagnostic command.

Rationale: these foundation elements verify NeoForge registration, metadata, assets, diagnostics, and build wiring without implementing meat-processing gameplay.

Consequences:

- The development test item must stay clearly documented as non-gameplay.
- The diagnostic command must not grant items, mutate the world, expose local paths, expose environment variables, or report sensitive system details.
- Future gameplay systems must be introduced through the split milestone plan, not hidden inside foundation code.

## DEC-0023: Minecraft-Independent Engine Foundation

Status: Accepted

Decision: implement Milestone 1B as a Minecraft-independent engine under `com.butchercraft.engine` before adding visible gameplay systems. The engine owns product, quality, quantity, modifier, operation-result, and processing-transaction rules and must not import `net.minecraft` or `net.neoforged`.

Rationale: the architecture review identified product state, quality, and item-moving transactions as high-risk coupling and duplication areas. A pure Java engine lets those invariants be tested before Minecraft integration.

Consequences:

- Minecraft integration may depend on the engine, but the engine must not depend on Minecraft integration.
- The previous grinder and packaging station scope formerly listed as Milestone 1B is deferred until the owner schedules a later visible station milestone.
- Quality score boundaries are fixed for the engine as Poor 0-199, Fair 200-399, Good 400-699, Excellent 700-899, and Premium 900-1000.
- Quantity uses exact `long` amounts with explicit units; no silent conversion or rounded yield is allowed in the engine.
- Processing transactions must prepare output before commit and prevent double output.

## DEC-0024: Processing Framework Before Visible Stations

Status: Accepted

Decision: implement Milestone 1C as a Minecraft-independent processing framework under `com.butchercraft.engine` before adding grinder, station, refrigeration, or other visible gameplay systems.

Rationale: operation validation, context handling, yield math, modifier ordering, warnings, and transaction preparation are high-risk foundations for later item-moving gameplay. Proving them in pure Java reduces duplication and coupling risk.

Consequences:

- `ProcessingOperation` defines transformation rules but owns no transaction state or mutable work progress.
- `ProcessingContext` carries explicit facts supplied by future integrations and does not know how those facts were obtained.
- `ValidationRule` returns inspectable validation results instead of boolean-only outcomes.
- Yield modifiers use additive basis points, with `10,000` basis points equal to 100%.
- Yield rounds half up to the nearest smallest quantity unit and rejects negative effective yield or overflow.
- The Beef Trim to Ground Beef operation exists only as test fixture data until Minecraft content is explicitly scheduled.
- The simple refrigerated storage scope formerly listed as Milestone 1C is deferred until the owner schedules a visible storage milestone.

## DEC-0025: Product ItemStacks Use One Product Data Component and Do Not Stack Yet

Status: Accepted

Decision: implement Milestone 1D with one registered data component, `butchercraft:product_data`, storing immutable `ProductStackData` on product-bearing ItemStacks. Product-bearing development fixture items have maximum stack size `1`.

Rationale: the engine already stores exact product quantity. Allowing ItemStack count to also represent quantity before merge rules exist would create two independent quantity systems and could duplicate, average, or discard quantity or quality.

Consequences:

- Product stack data is stored through a persistent codec and network stream codec, not arbitrary legacy NBT.
- ItemStack copies preserve product data through the component system.
- Distinct product data participates in stack equality through data components.
- Future stackability requires an explicit design for ItemStack count, engine quantity, quality, freshness, temperature, packaging, and lot identity before product items can safely merge.
- Beef Trim Test Product and Ground Beef Test Product are development-only fixtures and not final gameplay content.

## Decisions Needing Owner Approval

- First basic meat product and input source.
- Default config preset strictness.
- Whether facility boundaries are explicit controller-based from the start or introduced after the vertical slice.
- Whether detailed refrigeration ships as part of ButcherCraft Core 1.0 or ButcherCraft Refrigeration.
- Quality UI presentation beyond the accepted engine score and grade boundaries.
- MCDA default severity for shutdowns and fines.
- Expansion release order.
