# ButcherCraft Technical Architecture

Status: proposed planning document  
Target: Minecraft 1.21.1, NeoForge, Java 21

This document proposes an architecture before gameplay implementation begins. It assumes a new NeoForge MDK-style Java project because the current workspace does not yet contain mod source, Gradle files, or existing conventions.

## Version-Specific Ground Rules

- Use NeoForge 1.21.1 APIs for the pinned MDK version.
- Use Java 21 language features conservatively.
- Use `DeferredRegister` and mod-event-bus registration patterns.
- Use item data components for item stack product state.
- Use data attachments for block entity, chunk, and entity data where they fit.
- Use `SavedData` for facility, business, order, and inspection data that must survive level reloads.
- Use `CustomPacketPayload`, `StreamCodec`, and `RegisterPayloadHandlersEvent` for networking.
- Keep gameplay simulation server-authoritative.
- Keep client classes under a client package and register client-only screens/renderers only from client setup paths.
- Do not use APIs from a different Minecraft or NeoForge version.

## Proposed Identity

- Mod display name: ButcherCraft.
- Approved mod id: `butchercraft`.
- Approved base package: `com.butchercraft`.
- Approved asset namespace: `butchercraft`.

These identity values are approved owner decisions and should be treated as stable before source generation.

## Main Java Packages and Owners

Every major system has a proposed technical owner in package form:

| Package | Owner responsibility |
| --- | --- |
| `com.butchercraft` | Mod entry point, constants, top-level setup. |
| `com.butchercraft.engine` | Minecraft-independent product, quality, quantity, modifier, operation, context, validation, evaluation, result, and transaction domain rules. |
| `com.butchercraft.transformation` | Minecraft-independent generic material-transformation ids, material amounts, definitions, contexts, evaluations, evaluators, and compatibility adapters. |
| `com.butchercraft.registration` | Blocks, items, creative tabs, block entities, menus, entity types, data components, attachment types, recipe serializers. |
| `com.butchercraft.config` | Common/server/client config definitions and preset mapping. |
| `com.butchercraft.api` | Documented public API intended for expansion mods. |
| `com.butchercraft.api.product` | Product ids, product traits, quality/freshness API contracts. |
| `com.butchercraft.api.processing` | Public process definitions and station interaction contracts. |
| `com.butchercraft.api.refrigeration` | Cooling capability contracts and room summary types. |
| `com.butchercraft.api.business` | Order, customer, and facility identity contracts exposed to expansions. |
| `com.butchercraft.product` | Minecraft-facing product data components, ItemStack adapters, product item fixtures, quality summaries, freshness and temperature services. |
| `com.butchercraft.processing` | Manual stations, processing recipes, process state, yield results. |
| `com.butchercraft.workstation` | Reusable server-side workstation state, operation resolution, inventory reservation, progress, failure reporting, and temporary development workstation fixtures. |
| `com.butchercraft.machine` | Grinder, Bandsaw, packaging station, base machine block entities, tick helpers. |
| `com.butchercraft.multiblock` | Room/facility validation, controller membership, cached shape data. |
| `com.butchercraft.refrigeration` | Storage, thermal simulation, cooling equipment, overload/wear model. |
| `com.butchercraft.cleanliness` | Cleanliness data, dirty events, cleaning actions, facility summaries. |
| `com.butchercraft.inspection` | MCDA schedules, violation model, escalation, reinspection flow. |
| `com.butchercraft.employee` | Employee data, skill progression, job roles, task selection. |
| `com.butchercraft.customer` | Customer archetypes, order generation hooks, demand summaries. |
| `com.butchercraft.business` | Facility identity, ledger, reputation, unlock state, order board. |
| `com.butchercraft.work` | Work-order queue, reservations, task lifecycle, shared player/employee work. |
| `com.butchercraft.network` | Payload records, registration, server/client payload handlers. |
| `com.butchercraft.menu` | Server-side menus and data synchronization models. |
| `com.butchercraft.client` | Screens, renderers, client event handlers, client-only helpers. |
| `com.butchercraft.data` | Data generation providers. |
| `com.butchercraft.test` | Game tests or test-only helpers when the project adds test infrastructure. |

Expansion mods should depend on the core mod and its documented `api` package, not on internal implementation packages.

Minecraft integration packages may depend on `com.butchercraft.engine` and `com.butchercraft.transformation`. Engine and transformation packages must not import Minecraft or NeoForge classes.

## Pre-Implementation Boundary Decisions

The architecture review identified facility, quality, cleanliness, refrigeration, inspections, work orders, and employees as coupling hotspots. Before gameplay implementation begins, these systems must communicate through narrow interfaces, immutable snapshots, or events rather than direct references to saved-data objects, block entities, or villager entities.

Required boundaries:

- Product processing starts with pure engine records, processing contexts, validation rules, evaluators, and transactions under `com.butchercraft.engine`; item stacks and registries convert to engine products at the Minecraft boundary.
- Generic material transformation starts with pure records and evaluators under `com.butchercraft.transformation`; it extends existing processing concepts and does not replace current workstation behavior until a later milestone schedules that migration.
- Product and quality systems exchange `QualityContext` and `QualityResult` style records.
- Cleanliness exposes dirty events, cleaning events, local snapshots, and facility summaries.
- Refrigeration exposes storage and room summaries rather than equipment internals.
- Inspections consume inspection-subject snapshots and produce violation/escalation results.
- Work orders own intent, reservation token, state, completion, and failure reason; station-specific execution remains outside the order record.
- Employee roles expose task filters, skill modifiers, execution timing, and skill gain contracts.
- Facility state owns identity and membership summaries only; business, inspections, work orders, refrigeration, and cleanliness keep their own domain state.
- Client menus and screens consume synchronized summaries only. They do not mutate product quality, business state, employee skill, inspection outcomes, or saved data directly.
- Workstations consume definition registries and engine transactions through explicit resolver/controller boundaries. Generic workstation code must not hardcode species, operation, or product ids.
- The Grinder consumes the same workstation framework with only the `butchercraft:grinding` capability. Beef, pork, and bison grinding flows are selected through product/species/profile/operation definitions, not Grinder code branches.
- The Bandsaw consumes the same workstation framework with only the `butchercraft:bandsaw` capability. Beef forequarter fabrication outputs are selected through operation output definitions, not Bandsaw code branches.

Any future public interface under `com.butchercraft.api` must document data ownership, persistence behavior, and server/client expectations before an expansion depends on it.

## Registries

Use `DeferredRegister` classes grouped by registry concern:

- `ModBlocks`
- `ModItems`
- `ModBlockEntityTypes`
- `ModMenuTypes`
- `ModEntityTypes`
- `ModCreativeTabs`
- `ModDataComponents`
- `ModAttachmentTypes`
- `ModRecipeSerializers`
- `ModRecipeTypes`
- `ModPoiTypes`, if villager job sites require points of interest

Guidelines:

- Register from the mod constructor through the mod event bus.
- Do not query registries while registration is ongoing.
- Use stable lowercase registry names.
- Keep block and item registration paired only where it is actually needed.
- Register data components with persistent codecs and network codecs as appropriate.
- Use custom registries only when expansion mods need stable extension points.

Proposed data-driven registries, introduced only when needed:

- Species definitions.
- Processing-profile definitions.
- Product definitions.
- Processing-operation definitions.
- Order templates.
- Inspection rule definitions.
- Employee job role definitions.

Milestone 2A introduces the first four as custom datapack registries:

- `butchercraft:species`
- `butchercraft:processing_profile`
- `butchercraft:product`
- `butchercraft:processing_operation`

These registries are server-authoritative and resolved through reload-scoped registry access. Processing-family differences, including future poultry behavior, belong in processing profiles and operation compatibility data rather than Java species switches.

## Saved-Data Strategy

Use `SavedData` for data that belongs to a world or facility and must survive reloads:

- `BusinessSavedData`: business ledger, reputation, unlocked progression, active order ids, completed order history summary.
- `FacilitySavedData`: facility ids, owner player ids where relevant, controller positions, dimension keys, inspection state.
- `InspectionSavedData`: MCDA write-ups, fines, shutdown state, reinspection windows, violation history.
- `OrderSavedData`: active and completed customer orders if these need independent lifecycle management.

Additional persistence ownership rules:

- Work-order queues use `SavedData` only when queued or reserved work must survive save/load; transient station progress remains on the relevant block entity.
- Facility-level cleanliness summaries may use `SavedData`; local station cleanliness remains on block entities unless a chunk or zone attachment is explicitly introduced.
- Refrigeration room registries and durable room summaries may use `SavedData`; active thermal caches remain on controllers or runtime services and must be rebuildable.
- Employee skill and employment state belong on entity attachments, with only stable references or summaries mirrored in business or facility saved data.

Attach world-level data to the Overworld when it is not dimension-specific, because the Overworld is stable for cross-dimension world data. Dimension-specific facility data can reference dimension keys explicitly.

Persistence rules:

- Never create placeholder systems that silently discard saved data.
- Every saved structure needs a version field or equivalent migration plan before release.
- Unknown ids from missing expansions should be retained where practical and surfaced as inactive or unresolved, not deleted.
- Mutating saved data must mark the data dirty.
- Save only compact summaries for high-frequency simulation state. Store detailed transient caches on block entities or runtime services.

## Item Data-Component Strategy

Product item stacks should use data components instead of ad hoc NBT.

Milestone 1D implements the first concrete component:

- `ProductStackData` registered as `butchercraft:product_data`: product type id, source category id, processing state id, exact quantity value, quantity unit id, and quality score.

This component is persistent, network synchronized, immutable, and validated. Invalid decoded data is rejected rather than replaced with defaults. Product-bearing stacks are max stack size `1` until quantity and stack-count merge rules are deliberately designed.

Proposed components:

- `ProductComponent` or future extension of `ProductStackData`: product id, product form, lot id, source metadata if approved.
- `QualityComponent` or future extension of `ProductStackData`: current quality summary and optional trace of major quality contributors.
- `FreshnessComponent`: freshness state, last evaluated game time, spoilage state.
- `TemperatureComponent`: product temperature band or compact temperature value, last evaluated game time.
- `PackagingComponent`: packaged/unpackaged state, package type, label/order metadata.

Guidelines:

- Component values should be immutable records with `Codec` and `StreamCodec`.
- Components should be persistent when they affect gameplay.
- Network synchronization should be limited to values the client needs for display or menus.
- Do not store large histories on item stacks. Keep trace data small and optional.
- Item tooltips should summarize quality, freshness, and storage state without exposing raw formula internals by default.

## Attachments and Capabilities

Use data attachments for additional persistent data on supported holders:

- Entity attachments for employee villager skill, job role, employment state, and work preferences.
- Chunk attachments for aggregate cleanliness or facility area markers if chunk-level tracking proves practical.
- Block entity fields for station and machine state; attachments are optional unless a cross-cutting data model is useful.
- Non-persistent attachments only for caches that can be rebuilt.

Use NeoForge capabilities when external automation or expansion compatibility needs an interface:

- Use NeoForge-provided item handler capabilities for inventories.
- Use energy capabilities only if the project adopts an energy model.
- Consider a custom cooling or facility-service capability only for public expansion integration.
- Internal gameplay queries should use services or helpers rather than custom capabilities when no dynamic external lookup is needed.
- Invalidate block capabilities whenever the exposed object changes, appears, or disappears.
- Cache frequent block capability lookups with block capability caches.

## Networking

Networking should be minimal, versioned, and server-authoritative.

Payload categories:

- Client requests: station action, work-order action, UI button action.
- Server updates: menu data, facility summaries, inspection notices, order-board updates.
- Debug payloads: development-only, gated behind config or dev environment.

Rules:

- Register payloads with `RegisterPayloadHandlersEvent`.
- Use `CustomPacketPayload` records and `StreamCodec`.
- Validate all client requests on the server.
- Never let the client decide product quality, employee skill gain, payment, inspection outcome, or saved business state.
- Keep packet data compact and avoid syncing full facility state every tick.
- Client requests that complete work, deliver orders, or acknowledge inspection state must be idempotent or guarded by server-side transaction/reservation tokens.

## Menus and Screens

Server menus belong in `com.butchercraft.menu`. Client screens belong in `com.butchercraft.client.screen`.

Guidelines:

- Menus are views over server-side data holders; do not make block entities depend on menu classes.
- Use a client constructor with dummy/synced references and a server constructor with real data references.
- Use an extended menu factory when the client needs a block position or facility id.
- Screens must render state already synchronized by menus or explicit payloads.
- Client-only classes must not be loaded by dedicated servers.

Initial menus:

- Development processing workstation menu from Milestone 2B, as a temporary server-owned view only.
- Manual processing station menu or simple interaction UI.
- Grinder menu.
- Packaging station menu.
- Refrigerated storage status menu.
- Order board menu.
- Basic employee assignment menu.
- MCDA notice or inspection report screen.

## Multiblock Architecture

Use a controller-based model for scalable rooms and complex machines.

Core concepts:

- Controller block entity owns validation and summary state.
- Member blocks identify room boundaries, doors, vents, shelves, equipment, and optional service blocks.
- Validation scans are event-driven and cached, not full rescans every tick.
- Chunk-load and chunk-unload behavior must degrade gracefully.
- The controller stores only stable membership summaries and enough data to rebuild after load.
- Expansion mods can add valid member blocks through documented APIs or tags.

For the vertical slice, use a simple refrigerated storage block or small structure. Full walk-in cooler/freezer multiblocks can be introduced after the basic loop works.

## Employee AI Architecture

Employees should be villager-based where possible, with custom data and job behavior layered on top.

Proposed model:

- Employee data stored on villager entities through attachments.
- Business/facility assignment stored in saved data and mirrored on the employee attachment.
- Job roles map to work-order predicates and station interactions.
- A task selector picks valid work based on skill, distance, station availability, and priority.
- Work-order reservations prevent multiple employees from claiming the same task.
- Skill gain occurs server-side after successful work completion.

Pathfinding and scheduling should start simple. Avoid complex shift systems until the first employee can reliably do one useful job.

## Work-Order Architecture

The work-order system is the bridge between manual player actions, machines, and employees.

Work order fields:

- Work order id.
- Facility id.
- Desired action or process id.
- Input requirements.
- Output target.
- Priority.
- Current state.
- Reservation holder, if any.
- Failure reason, if blocked.

States:

- Draft or queued.
- Reserved.
- In progress.
- Completed.
- Failed or canceled.

The player can complete early work manually without a full order board. As automation grows, the same process definitions should support employees and machines.

## Quality Calculation Architecture

Quality calculation should be centralized in a service such as `QualityService`.

Inputs:

- Product definition.
- Input item components.
- Process definition.
- Station or machine state.
- Cleanliness snapshot.
- Worker skill snapshot.
- Temperature/freshness snapshot.
- Config profile.

Outputs:

- New `QualityComponent`.
- Yield result.
- Optional short quality trace for tooltips, logs, and debugging.

Rules:

- The service should be deterministic for the same inputs and random seed context.
- Randomness must be server-side and explicit.
- Formula constants should be configurable or data-driven once the shape stabilizes.
- Unit tests should cover pure quality functions before broad content is added.

## Cleanliness Architecture

Cleanliness should be event-driven and aggregated.

Data model:

- Local cleanliness records for stations, rooms, chunks, or facility zones.
- Facility summary cached for inspection and quality calculations.
- Dirty events emitted by processing actions, failures, spills, and neglected stations.
- Cleaning actions reduce accumulated sanitation load.

Simulation rules:

- Do not tick every block every game tick.
- Update summaries on dirty/clean events and at coarse scheduled intervals.
- Store enough persistent data to avoid resetting cleanliness on reload.
- Keep configurable thresholds for forgiving and realistic presets.

## Refrigeration Simulation Architecture

Start simple, then deepen.

Vertical slice:

- Refrigerated storage block entity with internal inventory.
- A simple target temperature band.
- Product freshness decay reduced while validly stored.
- Clear UI feedback when storage is cold enough.

Expanded architecture:

- Room controller tracks volume, target temperature, insulation/member validity, doors, shelves, and equipment links.
- Compressors, condensers, and evaporators contribute cooling capacity.
- Cooling demand depends on room volume, target temperature, freezer/cooler mode, warm product load, and ambient conditions.
- Overload increases wear and creates failure chances.
- Equipment failures create inspection and spoilage risk.

Performance rules:

- Simulate rooms at a coarse interval.
- Cache room summaries and invalidate on membership changes.
- Store product temperature compactly and update lazily when possible.
- Cap per-tick work per facility.

## Client/Server Separation

Server owns:

- Product state changes.
- Work orders.
- Employee AI.
- Business finances and reputation.
- Customer orders.
- MCDA inspection results.
- Cleanliness and refrigeration simulation.
- Saved data.
- Inventory transactions, item drops, work-order completion, and order fulfillment.

Client owns:

- Screens.
- Renderers.
- Tooltips.
- Particles and sounds triggered from server-approved events.
- Local display interpolation.

Common code must not import client-only Minecraft classes. Client-only packages must remain isolated under `com.butchercraft.client`, and dedicated-server launch checks are required as soon as any client code is added.

## Duplication and Inventory-Loss Safeguards

Every block, menu, employee action, and order flow that moves items must be implemented as a server-side transaction.

Required safeguards:

- Workstations must reserve active input, block extraction while processing, commit engine transactions exactly once, and preserve recoverable input/output on block removal or blocked completion.
- Block break and replacement must preserve or intentionally drop inventories exactly once.
- Menus, hoppers, employees, and players must not be able to extract or complete the same output twice.
- Work-order reservations require unique tokens and must release or restore cleanly after station unload, failure, cancellation, or save/load.
- Order fulfillment must be idempotent and must reject repeated client requests for the same delivered stack.
- Product item stacks with different quality, freshness, temperature, packaging, or lot data must not merge unless the components are equivalent by design.
- Packaging labels or order-bound metadata must not be copyable onto unrelated products.
- Unknown or invalid saved ids must be preserved or surfaced as unresolved; they must not be silently discarded.

Dedicated server safety is mandatory. Any class importing Minecraft client packages must stay under `com.butchercraft.client` or a clearly client-only setup path.

## Data Generation

Use data generation for:

- Blockstates.
- Block models and item models.
- Language entries.
- Loot tables.
- Recipes.
- Tags.
- Datapack registry defaults.

Generated data should be deterministic and reviewed. Hand-written JSON is acceptable for small prototypes, but generated data should become the default once patterns are stable.

Milestone 2A introduced the built-in beef prototype definitions under the custom datapack registry paths documented in `docs/PRODUCT_AND_PROCESSING_DEFINITIONS.md`.

Milestone 2B adds placeholder blockstate, model, item model, loot table, and language coverage for the temporary Development Processing Workstation. Final machine artwork remains deferred.

Milestone 2D extends built-in generated definitions with pork and bison red-meat grinding flows. These definitions use the same processing profile and Grinder capability as beef to prove multi-species behavior remains data-driven.

Milestone 2E extends operation definitions and the pure engine from one output to ordered output collections. It adds generated definitions and placeholder assets for the two-block Bandsaw and the Beef Forequarter fabrication proof.

Version 0.6.1 adds a strategy bridge between the workstation framework and the pure transformation engine. The Grinder uses transformation execution after operation resolution and before legacy transaction commit. Bandsaw and other workstations remain on the legacy execution strategy until they are deliberately migrated.

Version 0.6.2 adds an immutable pure Java transformation registry. The Grinder transformation strategy looks up the resolved operation id in that registry before evaluation and execution. Built-in Grinder transformations are registered in Java until datapack transformation loading is deliberately added.

Version 0.6.3 formalizes `TransformationDefinition` as the canonical pure Java transformation schema. New definitions should use the fluent builder and include display name, schema version, required capability, inputs, outputs, duration, yield, and typed metadata. Serialization and datapack loading should target this schema after their error-reporting and migration rules are designed.

Version 0.6.4 adds `com.butchercraft.product.definition` as a pure Java product definition foundation. `ProductDefinition` is the authoritative descriptive source for stable product ids, and `ProductRegistry` validates transformation product references through a separate deterministic validation pass. Transformation definitions continue storing product ids rather than embedding product definition records.

Canonical butcher-cut terminology belongs in product definitions, fixture item data, generated language, and docs. Machine code and generic workstation code must not translate or special-case cut names.

## Testing Strategy

Automated verification should scale with milestone risk:

- `gradlew build` for compile, resource validation, and packaging.
- `gradlew test` if Java unit tests are configured.
- `gradlew runData` to verify data generation once providers exist.
- Dedicated server launch checks for client/server separation.
- Game tests for processing, product data, saved data, cleanliness, work-order, and inspection behavior when feasible.
- ItemStack product data tests for component codecs, conversion, copying, and merge safety.
- Workstation tests for resolver behavior, state transitions, active-input locking, completion idempotence, save/load fields, registration, assets, and dependency boundaries.

Pure Java services should be easy to test without launching the full game:

- Engine product, quality, quantity, modifier, result, and transaction rules.
- Processing operation, context, validation, yield, and evaluator rules.
- Quality calculations.
- Cleanliness aggregation.
- Refrigeration capacity summaries.
- Order acceptance rules.
- Inspection escalation.

No command should be claimed as tested unless it was actually run.

## Performance Safeguards

- Avoid per-tick full facility scans.
- Avoid per-item-stack heavy histories.
- Use lazy product freshness and temperature updates where possible.
- Batch facility summary updates.
- Put upper bounds on employees scanning for jobs.
- Cache multiblock validation results and invalidate on relevant block changes.
- Keep networking incremental.
- Profile before adding complex thermal or AI behavior.
- Provide config caps for maximum facility scan size and active room count.

## Public API Documentation

Any package under `com.butchercraft.api` must include documentation for:

- Stability expectations.
- Supported extension points.
- Data ownership.
- Server/client expectations.
- Persistence expectations.
- Versioning and migration behavior.

Expansion mods must use public APIs, tags, datapack registries, or capabilities. They should not reach into core internal packages.

## NeoForge Reference Links

- Registries: https://docs.neoforged.net/docs/1.21.1/concepts/registries/
- Data components: https://docs.neoforged.net/docs/1.21.1/items/datacomponents/
- Data attachments: https://docs.neoforged.net/docs/1.21.1/datastorage/attachments/
- Saved data: https://docs.neoforged.net/docs/1.21.1/datastorage/saveddata/
- Capabilities: https://docs.neoforged.net/docs/1.21.1/datastorage/capabilities/
- Networking payloads: https://docs.neoforged.net/docs/1.21.1/networking/payload/
- Menus: https://docs.neoforged.net/docs/1.21.1/gui/menus/
- Sides: https://docs.neoforged.net/docs/1.21.1/concepts/sides/
- Block entities: https://docs.neoforged.net/docs/1.21.1/blockentities/
