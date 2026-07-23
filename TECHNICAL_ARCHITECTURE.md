# ButcherCraft Technical Architecture

Status: canonical project architecture document
Target: Minecraft 1.21.1, NeoForge, Java 21

This document records ButcherCraft's current architecture, planned system boundaries, and accepted technical constraints. Supporting design documents may expand individual systems, but this root file is the canonical Technical Architecture document. `CONSTITUTION.md` remains the higher governing authority for architectural philosophy and permanent invariants.

## Platform Orientation

ButcherCraft Core is a deterministic regional world simulation engine. Industry modules participate in shared identity, time, business, workforce, persistence, and future economic services. Meat Processing is the flagship implementation and currently owns the developed product, transformation, packaging, workstation, and machine content.

The player is a persistent participant in an existing world rather than the source of world generation. Immutable World Identity establishes regions, counties, settlements, manufacturers, properties, businesses, families, ownership, and historical supply relationships. Separate versioned runtime systems may change over simulated time without rewriting that identity.

Phase 13 changes documentation and architectural direction only. It does not add economy, population, production scheduling, logistics, employees, AI, markets, utilities, gameplay, schemas, or persistence files.

Canonical platform documents:

- `CONSTITUTION.md`: highest-level philosophy, permanent invariants, and architectural change process.
- `VISION.md`: long-term living-world purpose and player experience.
- `CORE_PRINCIPLES.md`: constitutional architecture principles.
- `MODULES.md`: Core, industry, and compatibility responsibilities.
- `SIMULATION_MODEL.md`: conceptual world-to-economy flow.
- `ECONOMY_MODEL.md`: future economic vocabulary and boundaries.
- `COMPATIBILITY.md`: future cross-mod integration philosophy.
- `ROADMAP.md`: strategic development eras.
- `docs/API_OVERVIEW.md`: planned extension concepts, not a stable API.

## Core System Relationships

```text
Immutable World Identity
  -> Runtime Player Identity
  -> Business Runtime
       -> Workforce Definitions

Simulation Clock -> deterministic Work scheduler/pipeline -> focused handlers
                 -> calendar Event Bus -> focused rollover listeners

Industry catalog
  -> immutable Good definitions + relationship graph
  -> immutable Economic Actor definitions + in-memory runtime capabilities
  -> actor-owned Inventory containers + hierarchical Storage Nodes + runtime quantities
  -> Orders and Contracts express intent and obligations by stable ids
  -> Economic Planning compiles observed Needs into bounded approved intent
  -> Production Processes + immutable Plans + mutable Runs
  -> scheduler eligibility + transaction-backed atomic completion
  -> future logistics, consumers, and markets

Datapack resources
  -> validated Product + Packaging + Transformation candidate registries
  -> atomic ContentSnapshot activation
  -> workstation resolution
  -> evaluation and atomic transaction
  -> server-owned inventory commit
```

The arrows represent references or event flow, not shared mutable ownership. Runtime records store stable identity ids. Services own their persistence and transitions. Minecraft lifecycle adapters initialize and save pure Java domains at the boundary.

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

## Project Identity

- Mod display name: ButcherCraft.
- Approved mod id: `butchercraft`.
- Approved base package: `com.butchercraft`.
- Approved asset namespace: `butchercraft`.

These identity values are approved owner decisions and should be treated as stable before source generation.

## Asset Resource Contracts

ButcherCraft uses standard Minecraft resource-pack locations for all current visual assets. Generated workstation and item models live under `assets/butchercraft/models`, generated blockstates live under `assets/butchercraft/blockstates`, and hand-authored PNG textures live under `assets/butchercraft/textures`.

Asset ownership is split by responsibility:

- Data generation owns active workstation blockstates, generated block models, generated item models, loot tables, and generated language resources.
- Hand-authored resources own PNG textures and legacy development fixture models.
- Final artwork must replace existing texture files or override standard Minecraft model/resource locations through a resource pack. Gameplay code must not change solely to adopt final textures.

The v0.8.0E asset framework establishes stable packaging replacement paths:

- Packaging supplies and Retail Ground Beef use `assets/butchercraft/textures/item/packaging/*.png`.
- Packaging Table workstation textures use `assets/butchercraft/textures/block/workstation/*.png`.
- Packaging Table GUI artwork uses `assets/butchercraft/textures/gui/packaging_table.png`.
- Development-only product fixtures may continue to use `assets/butchercraft/textures/item/development_test_item.png` until a future product item factory and final product art pass are scheduled.

Canonical current asset paths:

| Asset class | Canonical path |
| --- | --- |
| Packaging item textures | `assets/butchercraft/textures/item/packaging/*.png` |
| Workstation textures | `assets/butchercraft/textures/block/workstation/*.png` |
| GUI textures | `assets/butchercraft/textures/gui/*.png` |
| Generated item models | `assets/butchercraft/models/item/*.json` |
| Generated block models | `assets/butchercraft/models/block/*.json` |
| Generated blockstates | `assets/butchercraft/blockstates/*.json` |

Gameplay architecture must not change merely to accommodate artwork. Final textures should replace existing resource-pack paths or override standard model paths without modifying registries, datapacks, workstation execution, or product serialization.

Packaging supplies and Retail Ground Beef use unique texture paths even while their current contents are placeholders. This gives artists stable replacement targets without requiring code changes, datapack changes, item remapping, or custom renderers.

The Packaging Table GUI uses `butchercraft:textures/gui/packaging_table.png` with bounds documented in `docs/PACKAGING_TABLE_GUI_SPEC.md`. Slot and progress overlays remain code-rendered because they are functional UI elements, not final background artwork.

The v0.8.0E presentation foundation does not introduce final textures, dynamic rendering, block entity renderers, animations, connected textures, emissive textures, generated AI art, item recipes, new product content, or gameplay balance changes.

## Main Java Packages and Owners

Every major system has a proposed technical owner in package form:

Packages that already exist describe current ownership. Entries for packages not yet present are future boundary recommendations and do not authorize implementation or establish a public API.

| Package | Owner responsibility |
| --- | --- |
| `com.butchercraft` | Mod entry point, constants, top-level setup. |
| `com.butchercraft.architecture` | Explicit current architecture manifest and build-time validation entry point. |
| `com.butchercraft.architecture.validation` | Pure Java immutable architecture descriptors, deterministic rule registry, validator, and structured reports. |
| `com.butchercraft.engine` | Minecraft-independent product, quality, quantity, modifier, operation, context, validation, evaluation, result, and transaction domain rules. |
| `com.butchercraft.transformation` | Minecraft-independent generic material-transformation ids, material amounts, definitions, contexts, evaluations, evaluators, and compatibility adapters. |
| `com.butchercraft.packaging` | Minecraft-independent retail packaging definitions, supply-reference serialization, datapack validation, and registry access. |
| `com.butchercraft.registration` | Blocks, items, creative tabs, block entities, menus, entity types, data components, attachment types, recipe serializers. |
| `com.butchercraft.config` | Common/server/client config definitions and preset mapping. |
| `com.butchercraft.api` | Future documented public API after real module consumers validate contracts; not currently implemented or stable. |
| `com.butchercraft.api.product` | Future product ids, traits, and quality/freshness contracts. |
| `com.butchercraft.api.processing` | Future public process definitions and station interaction contracts. |
| `com.butchercraft.api.refrigeration` | Future cooling capability contracts and room summary types. |
| `com.butchercraft.api.business` | Future order, customer, and facility identity contracts. |
| `com.butchercraft.product` | Minecraft-facing product data components, ItemStack adapters, product item fixtures, quality summaries, freshness and temperature services. |
| `com.butchercraft.processing` | Manual stations, processing recipes, process state, yield results. |
| `com.butchercraft.workstation` | Reusable server-side workstation state, operation resolution, inventory reservation, progress, failure reporting, and temporary development workstation fixtures. |
| `com.butchercraft.machine` | Grinder, Bandsaw, packaging station, base machine block entities, tick helpers. |
| `com.butchercraft.world` | Minecraft-facing world identity service, immutable generated world snapshot access, business runtime service, and server lifecycle integration. |
| `com.butchercraft.world.player` | Pure player legacy template domain, career profiles, starting scenarios, and scenario registry. |
| `com.butchercraft.world.player.runtime` | Runtime player identity creation, immutable player identity registry, independent player identity persistence, and server-login initialization. |
| `com.butchercraft.world.simulation` | Simulation clock, configurable calendar, event scheduler, event bus, independent simulation-state persistence, and server tick lifecycle integration. |
| `com.butchercraft.world.simulation.scheduler` | Pure immutable simulation Work definitions, separate runtime lifecycle, stable stages, handler contracts, deterministic indexes, bounded pipeline, reports, and schema-versioned persistence. |
| `com.butchercraft.world.business.runtime` | Pure business runtime state, hours, shifts, operational status, runtime registry, manager transitions, event listener, validation, and JSON persistence. |
| `com.butchercraft.world.workforce` | Pure workforce definitions, positions, staffing rules, shift assignments, skill levels, certifications, registry, manager lookup, validation, and JSON persistence. |
| `com.butchercraft.world.goods` | Pure immutable economic commodity and product definitions, industry ids, units, storage/transport metadata, transformation relationships, deterministic registry, manager, validation, and JSON persistence. |
| `com.butchercraft.world.economy.actor` | Pure economic actor ids, immutable definitions, typed capabilities, Good relationships, supported-industry metadata, in-memory runtime state, deterministic registry, manager, validation, and JSON definition persistence. |
| `com.butchercraft.world.inventory` | Pure actor-owned inventory containers, hierarchical storage nodes, capacity metadata, exact runtime Good quantities, typed entry metadata, deterministic registry, manager validation, and JSON persistence. |
| `com.butchercraft.world.planning` | Pure immutable Planning artifacts, exact Needs and capacity claims, deterministic candidate evaluation and selection, typed Production submission, cycle reports, and six-file JSON persistence. |
| `com.butchercraft.world.allocation` | Pure Resource Allocation definitions, deterministic AllocationSet lifecycle and Cycle execution, detached Capacity accounting, atomic Commitment publication, immutable registries, views, history, queries, reports, traces, and typed validation. |
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

Future industry and compatibility modules should depend on the core mod and its documented `api` package once those contracts exist, not on internal implementation packages.

Minecraft integration packages may depend on `com.butchercraft.engine`, `com.butchercraft.product.definition`, `com.butchercraft.packaging`, and `com.butchercraft.transformation`. Engine, product-definition, packaging, and transformation packages must not import Minecraft or NeoForge classes.

### Package Boundary Review

The current package layout already aligns with the platform direction and requires no Phase 13 refactor:

- `com.butchercraft.world.identity`, `world.business`, `world.ownership`, `world.property`, `world.trade`, and `world.manufacturer` contain immutable or historical regional identity domains.
- `com.butchercraft.world.player.runtime`, `world.simulation`, `world.business.runtime`, and `world.workforce` own separate runtime or organizational state with independent schemas.
- `com.butchercraft.world.goods` owns immutable economic definitions and relationships. It stores no inventory quantities, prices, production state, or ItemStacks.
- `com.butchercraft.world.economy.actor` owns immutable participant definitions and separate in-memory runtime status. Actors reference Goods, Business Runtime, and Workforce only through stable ids and store no inventory, production, pricing, transport, or ItemStack state.
- `com.butchercraft.world.inventory` owns economic quantity and location state independently from Minecraft inventories. It references actors, Goods, and storage nodes by stable ids and imports no Minecraft, NeoForge, Container, menu, slot, or ItemStack APIs.
- `com.butchercraft.world.production` owns industry-neutral executable Process and Plan definitions plus separately owned Run runtime state. It references other authorities by stable ids, advances only through supplied simulation ticks, and never mutates Inventory directly.
- `com.butchercraft.world.allocation` owns immutable Requests, AllocationSets, and Commitments; AllocationSet lifecycle; the explicit deterministic Allocation Cycle; detached cycle-local Capacity accounting; atomic Commitment publication; and immutable registries, reports, traces, history, and queries. Authoritative providers retain Resource and Capacity ownership. Allocation references other subsystems only by stable external identity and has no persistence, Scheduler stage, live provider, Planning handoff, or Production execution gate.
- `com.butchercraft.engine`, `transformation`, `product.definition`, `packaging.definition`, and their serialization models remain pure Java foundations.
- `com.butchercraft.content` coordinates validated immutable content snapshots.
- `com.butchercraft.processing`, `packaging`, `workstation`, and `machine` currently form the flagship Meat Processing implementation and reusable execution boundaries.
- `com.butchercraft.integration`, registration, menus, screens, ItemStack adapters, and top-level world services remain Minecraft or NeoForge boundaries.

Future migration recommendations:

- Do not rename existing packages merely to match module branding.
- Wait for a real second industry before deciding which processing, product, transformation, or workstation types become public Core contracts.
- Introduce focused population, economy, market, order, warehouse, transport, or utility packages only in milestones that implement those systems and define persistence ownership.
- Keep first-party industry content out of a future public API package.
- Promote only the smallest proven contracts to `com.butchercraft.api`, with compatibility tests and documented lifecycle rules.

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
- The Packaging Table consumes the same workstation framework with only the `butchercraft:packaging` capability. Retail packaging output is selected through processing-operation and product packaging metadata, while supply requirements come from packaging definitions rather than table code branches.
- World Identity remains an immutable generated snapshot. Runtime player identity records are stored separately at `<world>/butchercraft/player_identities.json` and reference world settlement, property, business, ownership, and family ids without embedding or mutating those world records.
- ButcherCraft simulated world time is owned by `SimulationClock`, not Minecraft time-of-day. Future systems must schedule work through `SimulationScheduler` and observe rollover events through `SimulationEventBus` instead of owning independent timers.
- Business Identity remains immutable inside World Identity. Mutable business runtime state is stored separately at `<world>/butchercraft/business_runtime.json`, references businesses by `BusinessId`, and responds to daily and weekly simulation rollover events without owning an independent clock.
- Workforce definitions are organizational structure, not employee records. They persist separately at `<world>/butchercraft/workforce_definitions.json`, reference businesses by `BusinessId`, reference Business Runtime shift ids, and resolve required positions for a current shift without assigning workers.
- Economic Actors define participants, not economic behavior. Immutable definitions persist separately at `<world>/butchercraft/economic_actors.json`, reference goods by `GoodId`, and keep mutable runtime status and optional Business Runtime/Workforce assignments outside definition persistence.
- Economic Inventory defines ownership, location, capacity, and runtime quantities, not movement or production. Containers reference actors and storage nodes, entries reference Goods by `GoodId`, and the pure domain remains independent from Minecraft inventory representation.
- Economic Transactions define the universal runtime quantity-mutation pipeline. Future causes submit immutable requests; validation creates accepted change plans; execution alone applies atomic changes; audit history preserves deterministic submission order.
- Economic Production defines executable operational intent and lifecycle without owning Goods, time, Inventory quantities, Business Runtime, Workforce, Orders, or transaction history. Completion requires one APPLIED Production Transaction.

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

- Business runtime state currently persists in schema-versioned JSON at `<world>/butchercraft/business_runtime.json` and stores only mutable operational summaries plus stable business references. Future `SavedData` business ledgers must not duplicate this runtime state without an explicit migration decision.
- Workforce definitions currently persist in schema-versioned JSON at `<world>/butchercraft/workforce_definitions.json` and store only organizational staffing structure plus stable business and shift references. Worker identities, payroll, scheduling, and productivity must live in future employee systems.
- Economic good definitions and transformation relationships persist in schema-versioned JSON at `<world>/butchercraft/goods.json`. The file stores immutable definitions only; future inventory, warehouse, market, order, shipment, and production quantities require separate runtime owners.
- Economic actor definitions and relationship metadata persist in schema-versioned JSON at `<world>/butchercraft/economic_actors.json`. Runtime actor status and assignments are in-memory Phase 15 state; future durable runtime ownership requires a separate schema and must not rewrite immutable definitions.
- Economic inventory containers, storage nodes, runtime statuses, exact quantities, canonical units, and typed entry metadata persist in schema-versioned JSON at `<world>/butchercraft/inventory.json`. Minecraft inventory, ItemStack, slot, menu, GUI, routing, order, and production state must not be duplicated into this file.
- Economic transaction definitions, final statuses, typed metadata, and simulation ticks persist in schema-versioned JSON at `<world>/butchercraft/transactions.json`. Validation objects, undo stacks, runtime snapshots, and execution caches are not persisted.
- Production Process definitions, Plan definitions, and Run runtime state persist independently at `<world>/butchercraft/production_processes.json`, `production_plans.json`, and `production_runs.json`. All three candidates are cross-validated before publication; per-file replacement does not claim a three-file filesystem transaction.
- Economic Planning persists terminal Observations, Needs, Opportunities, Candidates, Approved Plans, Constraints, resolution/submission runtime, and reports at `<world>/butchercraft/planning_observations.json`, `planning_needs.json`, `planning_opportunities.json`, `planning_candidates.json`, `planning_approved_plans.json`, and `planning_runtime.json`. An existing set must contain all six files; complete cycle structure and external authority references are validated before publication.
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

## Economic Goods Architecture

Phase 14 introduces `com.butchercraft.world.goods` as the universal immutable language for future economic goods.

`GoodDefinition` is a sealed base with exactly two current categories:

- `CommodityDefinition` for generic resources, utilities, capacities, livestock, and raw materials.
- `com.butchercraft.world.goods.ProductDefinition` for manufactured or processed economic goods with a source industry and transformation stage.

The economic `ProductDefinition` does not replace `com.butchercraft.product.definition.ProductDefinition`. The existing definition remains authoritative for processing content, datapack product references, ItemStack adapters, and workstation execution. No automatic mapping or catalog migration exists in Phase 14.

`GoodRegistry` is immutable and deterministic. It validates duplicate ids, known industries, transformation input/output references, duplicate relationships, and graph cycles. `GoodManager` replaces registry snapshots for definition registration and lookup but owns no quantities. `GoodTransformation` records an input, output, exact yield ratio, and owning industry without executing production.

`ItemMappingMetadata` contains pure provider and item identifiers only. It is informational and cannot resolve, create, inspect, or mutate an ItemStack. `GoodService` is the sole Minecraft lifecycle adapter for loading and saving `goods.json`.

Future inventories, warehouses, orders, production plans, shipments, consumers, utilities, and markets must reference goods by `GoodId` and own their mutable quantities separately.

## Economic Actor Architecture

Phase 15 introduces `com.butchercraft.world.economy.actor` as the universal industry-neutral participant model for future economic systems.

`EconomicActorDefinition` is immutable and records a stable `ActorId`, display name, typed actor classification, primary `IndustryId`, immutable capabilities, immutable Good relationships, and schema version. `ActorRelationship` references one `GoodId`, records a typed `GoodRole`, may declare supported industries, and may declare an optional actor dependency for deterministic graph validation. Relationships are metadata only and do not move, consume, store, produce, buy, or sell goods.

`EconomicActorRegistry` is immutable and deterministic. It validates duplicate actor ids and relationships, actor capability compatibility, known industries and goods, actor dependency references, and dependency cycles. `EconomicActorManager` provides lookup, relationship queries, runtime access, and assignment validation but performs no scheduling or economic behavior.

`EconomicActorRuntime` is mutable state separate from the immutable definition. It records status, enabled and operational flags, optional `BusinessId` and `WorkforceDefinitionId` assignments, and a monotonic last simulation tick. Runtime assignments are stable references rather than embedded Business Runtime or Workforce records.

`EconomicActorService` depends on `GoodService`, loads definitions only after the active Goods registry is available, and owns world lifecycle persistence at `<world>/butchercraft/economic_actors.json`. The actor package remains pure Java; only the service imports Minecraft and NeoForge APIs. Phase 15 registers no built-in actors and persists no actor runtime state.

Future inventory, production, demand, order, market, transport, utility, NPC, compatibility, and player-business systems must identify participants by `ActorId`, identify goods by `GoodId`, and own their mutable transactions outside actor definitions.

## Inventory And Storage Architecture

Phase 16 introduces `com.butchercraft.world.inventory` as the universal runtime ownership and physical-location model for economic Goods.

`InventoryContainer` is immutable identity metadata linking one `InventoryId` to one owner `ActorId`, one `StorageNodeId`, one typed inventory classification, and one capacity definition. `StorageNode` is immutable location metadata with a typed `StorageRequirement`, capacity, and optional parent node. Parent references form a validated acyclic hierarchy for distribution centers, warehouses, coolers, freezers, vehicles, shelves, bins, and future compatibility locations.

`InventoryRuntime` is mutable state separate from container identity. It stores status, deterministic entries, last simulation tick, and schema version. `InventoryEntry` stores exact non-negative `long` quantity by `GoodId` and the Good definition's canonical `UnitOfMeasure`. Typed optional lot, expiration-tick, quality-basis-point, and origin-actor metadata is persisted but has no behavior in Phase 16.

`StorageCapacity` provides optional weight, volume, discrete-unit, and distinct-Good limits. Validation uses exact deterministic unit conversion for supported weight and volume units. Container limits and aggregate storage-node/ancestor limits are validated before manager additions commit.

`InventoryRegistry` is immutable and deterministic. It validates duplicate ids, owner and storage references, capacity nesting, missing parents, and hierarchy cycles. `InventoryManager` owns runtime records, validates Good, unit, actor, metadata, and capacity references, and provides quantity, ownership, and storage-hierarchy queries. Runtime access returns defensive snapshots. Candidate change validation is public, but mutation requires executor-only transaction authority and applies complete validated batches atomically.

`InventoryService` depends on `EconomicActorService`, loads only after Goods and Actors are active, and owns world lifecycle persistence at `<world>/butchercraft/inventory.json`. The inventory package remains pure Java; only the service imports Minecraft and NeoForge APIs. Phase 16 registers no built-in inventories or storage nodes and creates no ItemStack bridge.

Future production, order, warehouse-operation, logistics, market, spoilage, and compatibility systems must identify inventory by `InventoryId`, preserve server authority, and submit explicit economic transactions. Minecraft inventory adapters must remain outside the pure inventory domain.

## Economic Transaction Architecture

Phase 17 introduces `com.butchercraft.world.transaction` as the universal state-mutation pipeline for economic inventory quantities.

`EconomicTransaction` is an immutable schema-versioned request containing stable transaction, actor, inventory, and Good references; exact quantity and canonical unit; simulation tick; status; typed audit metadata; and an optional ordered inventory-change plan. Schema version 1 executes inventory add, remove, transfer, direction-explicit adjustment, and Production. Existing records without an `inventory_changes` field retain their prior behavior; other additive transaction types remain reserved.

`TransactionValidator` owns reference, endpoint, status, underflow, capacity, and current-state validation without mutation. Accepted validation records contain the exact inventory changes authorized for one transaction. `TransactionExecutor` requires the matching accepted record and a `VALIDATED` transaction, rechecks current state, and applies the complete batch through `InventoryManager`. Failed batches do not change any inventory.

`TransactionRegistry` stores audit history in authoritative submission order, rejects duplicate ids, preserves position during status replacement, and provides lookup and type/status queries. `TransactionManager` owns submit, validate, execute, query, history, and explicit replay orchestration. Replay applies only historical `APPLIED` records to a supplied compatible baseline and fails visibly on divergence.

`TransactionService` depends on `InventoryService` and owns world lifecycle persistence at `<world>/butchercraft/transactions.json`. The transaction package remains pure Java; only the service imports Minecraft and NeoForge APIs. Production supplies its explicit change plan through this existing authority; transaction validation still decides whether the whole batch can commit.

Future systems decide why a change is requested, then submit a transaction. They must not mutate `InventoryRuntime` or inventory quantities directly. See `docs/TRANSACTION_FRAMEWORK.md` for the schema, pipelines, audit rules, persistence contract, and examples.

## Orders And Contracts Architecture

Phase 18 introduces `com.butchercraft.world.economy.order` as the industry-neutral economic intent and obligation domain. `EconomicOrderDefinition` and `EconomicContractDefinition` are immutable schema-versioned definitions. `EconomicOrderRuntime`, per-line runtime records, and `EconomicContractRuntime` remain separately owned mutable lifecycle state exposed only through defensive snapshots.

`OrderRegistry` and `ContractRegistry` preserve authoritative insertion order and build immutable deterministic indexes for common Actor, Good, type, Contract, schedule, and industry queries. `OrderManager` and `ContractManager` own explicit transition tables, monotonic Simulation Clock ticks, typed rejection outcomes, and coordinated reference validation.

Fulfillment recording stages all requested line allocations against detached runtime copies. Only existing APPLIED Transactions with matching Good, unit, quantity, and tick may contribute. Aggregate allocation cannot exceed a Transaction's authoritative quantity. A failed batch changes no Order state, and the framework never mutates Inventory or submits Transactions.

`OrderContractService` depends on `TransactionService`. It loads Contracts and Orders as one validated world-bound state before publishing either manager, then persists separate schema-versioned documents at `<world>/butchercraft/contracts.json` and `<world>/butchercraft/orders.json`. The domain and persistence packages remain pure Java; only the lifecycle service imports Minecraft and NeoForge.

Contract schedules, commitment periods, priorities, substitutions, and future-facing types are descriptive metadata. Phase 18 does not generate Orders, execute schedules, reserve Goods, price trade, run production or logistics, expose gameplay, or create a public API. See `docs/ORDERS_AND_CONTRACTS.md` for lifecycle tables, exact quantity rules, allocation validation, persistence, queries, invariants, and limitations.

## Deterministic Simulation Scheduler Architecture

Phase 19 introduces `com.butchercraft.world.simulation.scheduler` as the pure Java orchestration domain for future simulation Work. It does not replace `SimulationClock` or the existing calendar-event scheduler. The clock remains the sole time authority; the Work pipeline receives the already-advanced authoritative tick and enforces strict sequential execution with no automatic catch-up.

Six stable broad stages order immutable `ScheduledSimulationWork` records. `SimulationSchedulerManager` alone assigns monotonic persisted submission sequences and owns separate `SimulationWorkRuntime` records plus deterministic status/due indexes. Ordering is stage, scheduled tick, descending priority, sequence, then Work id. Runtime snapshots and queries are immutable.

`SimulationPipeline` executes a bounded prefix using positive item, stage, work-unit, generation, same-tick, retry, and depth budgets. Handler failures are typed and isolated by stage policy. Generated requests commit atomically and may run in the same tick only in a later unstarted stage that permits enqueue. The scheduler never mutates Inventory or interprets Orders, Contracts, production policy, logistics, or markets.

`SimulationSchedulerService` initializes after `OrderContractService`, executes after the Simulation Clock's post-tick listener, and persists schema-1 state at `<world>/butchercraft/simulation_scheduler.json`. Unknown persisted Work types, mismatched clock ticks, and persisted `RUNNING` state fail visibly. Phase 20 installs `butchercraft:production_run`; Phase 21 installs `butchercraft:economic_planning_cycle` before scheduler loading and keeps one deferred continuation Work in the PLANNING stage. No public runtime registration API is established. See `docs/SIMULATION_SCHEDULER.md` for schemas, lifecycle, ordering, invariants, measured scale, and limitations.

## Industry-Neutral Production Architecture

Phase 20 introduces `com.butchercraft.world.production` as a pure Java operational domain. It does not replace the economic `GoodTransformation` relationship or the existing local workstation transformation engine. A `ProductionProcessDefinition` describes a reusable executable Process with exact input and output lines, whole-batch deterministic yield, duration, required capabilities, optional Business and Workforce requirements, policy, tags, and typed metadata.

`ProductionPlanDefinition` is immutable authoritative intent. It references one Process, producer Actor, batch count, explicit line-to-Inventory bindings, priority, optional Order/Contract context, and scheduling metadata. Registration validates structure and references but reserves no stock. One schema-1 Plan owns exactly one separately managed `ProductionRunRuntime`.

The Run owns lifecycle, exact accumulated work, scheduler Work reference, attempt count, blocking or failure facts, and final Transaction reference. Scheduler Work type `butchercraft:production_run` executes in the standard execution stage. The handler receives the already-authoritative tick, evaluates Business Runtime, Workforce, capabilities, bindings, stock, and output capacity, then starts, advances, blocks, or completes the Run under typed policy. It never advances time or owns scheduler state.

Completion constructs one ordered explicit transaction change plan: all consumed inputs first, preserving Inventory entry metadata, followed by every produced output. `TransactionValidator` validates the complete candidate state and `TransactionExecutor` commits the batch atomically through `InventoryManager`. The Run becomes completed only after authoritative Transaction history records `APPLIED`; rejection or commit failure leaves no partial Inventory mutation.

`ProductionService` initializes after Goods, Actors, Inventory, Transactions, Orders/Contracts, Business Runtime, and Workforce are available. It loads Process, Plan, and Run files as one cross-validated candidate, installs the Production handler before scheduler loading, then validates persisted Work references after the scheduler is active. See `docs/PRODUCTION_FRAMEWORK.md` for schemas, transition rules, requirements, persistence, examples, invariants, measured scale, and extension points.

## Economic Planning Architecture

Phase 21 introduces `com.butchercraft.world.planning` as the pure Java decision owner between authoritative economic facts and Production intent. The canonical immutable artifacts are Observation, Need, Constraint, Opportunity, Candidate Plan, and Approved Plan. Separate runtime records track Need resolution, submission outcome, and one terminal cycle report.

Schema 1 observes accepted open Order lines, subtracts matching active Production commitments, discovers compatible Process/Actor/Inventory Opportunities, generates exact whole-batch Candidates, and selects a bounded deterministic prefix through detached cycle-local capacity ledgers. Explicit Need and Candidate comparators, stable hash-derived ids, exact `GoodQuantity`, and insertion-ordered maps make equivalent inputs repeatable. Capacity claims are not reservations.

Approved executable Production intent crosses one typed boundary, `ProductionPlanningSubmissionAdapter`. It calls the idempotent `ProductionManager.registerAndSchedulePlan` operation, which returns existing identical state, rejects identity conflicts, and removes a newly registered unscheduled Plan after Scheduler rejection. Planning never executes a Run, mutates Inventory, submits Transactions, records Order fulfillment, advances time, or transitions Scheduler runtime.

`EconomicPlanningService` installs its handler before Scheduler load, initializes after Production and Scheduler binding, loads six complete-set files, and ensures one continuation Work exists in the PLANNING stage. Interrupted cycles, malformed or partial files, invalid provenance graphs, and missing external references fail visibly. See `docs/ECONOMIC_PLANNING_ENGINE.md` for the schemas, pipeline, ownership boundaries, persistence, invariants, measured scale, and deferred scope.

## Resource Allocation Domain And Runtime

RFC-0022 M22A introduces `com.butchercraft.world.allocation` as a pure Java
structural domain. Canonical namespaced identities, exact bounded
`AllocationQuantity`, open Capacity unit and Resource category identities,
typed immutable metadata, and generic `ExternalReference` values make the
domain industry-neutral and independent of concrete provider models.

Provider-supplied `ObservedResourceSnapshot` and `ObservedCapacitySnapshot`
records capture explicit simulation-tick facts without querying world state.
Schema 1 represents Workforce only as aggregate position, role, or shift
capacity and rejects the individual-worker reference concept.

Immutable `RequirementDefinition` records belong to one externally owned
executable work reference and one future atomic `AllocationSetDefinition`.
`AllocationOrderingContext` captures every replay input for horizon, priority,
required-by, starvation age, Need age, stable sequence, and final Request-id
ordering. `AllocationRequestDefinition`, `AllocationSetDefinition`, and
`AllocationCommitmentDefinition` validate canonical identity, association,
unit, tick, evidence, duplicate, and immutable collection contracts.

M22B adds one mutable `AllocationSetRuntime` per Set behind
`AllocationRuntimeService`. Runtime identity remains `AllocationSetId`.
Explicit requests move state through REQUESTED, WAITING, ALLOCATED, ACTIVE,
RELEASED, FAILED, and EXPIRED while enforcing monotonic simulation ticks,
revisions, complete Commitment structure, and terminal states. Every public
read is an immutable `AllocationRuntimeView`.

Canonical immutable registries index definitions, runtime views, and reports.
`AllocationHistory` validates complete transition chains, and
`AllocationQueryService` provides detached lookup by definition, association,
status, Planning Cycle reference, Resource, work reference, Cycle, tick, and
history range. Immutable reports carry outcome, Commitment, conflict, Capacity,
ordering, work-bound, failure, policy, tick, and schema evidence without
running an algorithm.

M22C adds `AllocationCycleExecutor` over explicit immutable input. It subtracts
supplied ALLOCATED and ACTIVE Commitments into a detached
`WorkingCapacityLedger`, orders eligible Sets through the accepted Request
comparator, evaluates each Set on a private ledger branch, and selects the first
complete Resource-id/Capacity-id match for each Requirement. Successful Sets
merge all exact quantities and privately construct one deterministic Commitment
per Requirement; waiting or failed Sets leave the parent ledger unchanged.

Publication rebuilds a complete candidate `AllocationRuntimeService`, registers
Commitments, applies only REQUESTED/WAITING to ALLOCATED and REQUESTED to
WAITING transitions, validates report and trace evidence, and swaps candidate
state once. Duplicate Cycles and stale runtime snapshots fail before mutation.
Canonical digests cover input, ordering, ledgers, Commitments, reports,
publication, trace, and complete results.

M22D adds `AllocationResourceProvider`, immutable provider descriptors, an
explicit canonical `AllocationProviderRegistry`, immutable observation
contexts and requests, typed provider results, deterministic failure isolation,
and `AllocationObservationService`. Provider adapters translate their own
authoritative state into the existing M22A snapshots. Allocation never receives
a concrete owner-domain object and never becomes Resource or Capacity
authority.

The observation service invokes providers once in provider-id order, validates
tick, schema, owner, category, Capacity type/unit, Resource references, and
duplicate identities, then returns an immutable `AllocationObservationBundle`.
Bundles are explicitly COMPLETE, INCOMPLETE, or UNUSABLE; only COMPLETE is
safe for a future orchestration layer to combine with definitions and runtime
as `AllocationCycleInput`. The provider framework never invokes the Cycle,
publishes Commitments, or mutates Allocation runtime.

The architecture manifest declares an empty canonical
`butchercraft:allocation_providers` registry and external Resource/Capacity
authority. No production-grade provider instance is registered in M22D.
Canonical SHA-256 digests cover descriptors, registry, request, snapshots,
results, failures, warnings, reports, and bundles.

M22A-M22D declare no Allocation persistence file, Scheduler stage or Work type,
Planning handoff, Production execution gate, Inventory mutation, Transaction
path, Minecraft hook, or gameplay behavior. The existing six-stage Scheduler
and simulation behavior are unchanged. See
`docs/RESOURCE_ALLOCATION_DOMAIN.md`, `docs/ALLOCATION_RUNTIME.md`, and
`docs/ALLOCATION_CYCLE.md`, plus
`docs/ALLOCATION_PROVIDER_FRAMEWORK.md`.

## Architecture Validation Framework

BCSE Architecture Validation Phase 1 introduces
`com.butchercraft.architecture.validation` as a pure Java observer of explicit
architectural declarations. Immutable contexts describe components, ownership,
dependencies, registries, persistence surfaces, Scheduler stages, and
simulation invariants. A canonical immutable rule registry produces structured
pass, failure, warning, and informational results without reflection, runtime
scanning, hidden randomness, wall-clock sampling, or subsystem mutation.

`ButcherCraftArchitectureManifest` is the explicit current manifest.
`ButcherCraftArchitectureValidation` validates it during automated tests. The
framework is not registered into server startup, reload, Scheduler execution,
commands, networking, or gameplay. It does not become an owner of any
described datum and does not make proposed RFCs effective.

Rule categories include Ownership, Dependencies, Persistence, Scheduler,
Registries, Transactions, Planning, Production, Allocation, Execution,
Simulation, and General. The manifest declares M22A-M22C Allocation artifact,
lifecycle, Cycle, detached Capacity accounting, Commitment selection, registry,
report, trace, and history ownership plus forbidden dependency directions and
canonical definition, runtime, report, and trace registries. It deliberately
declares no Allocation stage, persistence, provider, or executable Work.

See `docs/ARCHITECTURE_VALIDATION_FRAMEWORK.md` for rule authoring, descriptor
contracts, deterministic reporting, current integration, tests, and extension
constraints.

## Item Data-Component Strategy

Product item stacks should use data components instead of ad hoc NBT.

Milestone 1D implements the first concrete component:

- `ProductStackData` registered as `butchercraft:product_data`: product type id, source category id, processing state id, exact quantity value, quantity unit id, quality score, and optional stack-level packaging metadata.

This component is persistent, network synchronized, immutable, and validated. Invalid decoded data is rejected rather than replaced with defaults. Product-bearing stacks are max stack size `1` until quantity and stack-count merge rules are deliberately designed.

Proposed components:

- `ProductComponent` or future extension of `ProductStackData`: product id, product form, lot id, source metadata if approved.
- `QualityComponent` or future extension of `ProductStackData`: current quality summary and optional trace of major quality contributors.
- `FreshnessComponent`: freshness state, last evaluated game time, spoilage state.
- `TemperatureComponent`: product temperature band or compact temperature value, last evaluated game time.
- Future `PackagingComponent` or future extension of `ProductStackData`: label/order metadata once labels and commerce exist. The current `ProductStackData` packaging metadata stores only packaging definition id, packaging format id, and source product id.

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

Version 0.6.1 adds a strategy bridge between the workstation framework and the pure transformation engine. The Grinder uses transformation execution after operation resolution and before legacy transaction commit. At that point, the Bandsaw and other workstations remained on the legacy execution strategy until deliberate migration.

Version 0.6.2 adds an immutable pure Java transformation registry. The Grinder transformation strategy looks up the resolved operation id in that registry before evaluation and execution. Built-in Grinder transformations are registered in Java until datapack transformation loading is deliberately added.

Version 0.6.3 formalizes `TransformationDefinition` as the canonical pure Java transformation schema. New definitions should use the fluent builder and include display name, schema version, required capability, inputs, outputs, duration, yield, and typed metadata. Serialization and datapack loading should target this schema after their error-reporting and migration rules are designed.

Version 0.6.4 adds `com.butchercraft.product.definition` as a pure Java product definition foundation. `ProductDefinition` is the authoritative descriptive source for stable product ids, and `ProductRegistry` validates transformation product references through a separate deterministic validation pass. Transformation definitions continue storing product ids rather than embedding product definition records.

Version 0.6.5 adds `com.butchercraft.transformation.serialization` as a pure Java transformation serialization contract. It freezes stable external field names for the canonical `TransformationDefinition` schema, supports full field round trips through canonical serialized records, introduces explicit schema-version handling, and leaves datapack loading, resource reload listeners, JSON discovery, and implemented migrations to a later milestone.

Version 0.6.6 adds pure Java atomic multi-output transformation transactions. `TransformationMaterialStore` models bounded material quantities, `TransformationTransaction` stages and validates all input extraction and ordered output insertion before commit, and snapshot rollback prevents partial material changes if a commit-time insertion fails.

Version 0.6.7 migrates only the Bandsaw to the atomic transformation execution strategy. The Bandsaw still resolves operations through the existing processing graph, but the resolved operation id must exist in the immutable transformation registry and pass transaction validation through Minecraft-facing material-store adapters before the controller creates output ItemStacks. Grinder behavior remains on its existing transformation strategy, and un-migrated workstations remain on the legacy strategy.

Version 0.6.8 loads transformation definitions from datapack JSON resources under `data/<namespace>/butchercraft/transformation`. The loader maps JSON onto the canonical serialized transformation records, reuses the canonical deserializer, validates product and capability references, and swaps the active immutable registry only after a successful reload. The evaluator, executor, transaction engine, and workstation behavior remain unchanged.

Version 0.6.9 loads product definitions from content snapshot JSON resources under `data/<namespace>/butchercraft/content/product` and assembles product and transformation registries as a single immutable content snapshot. That path is deliberately separate from the Minecraft datapack registry path `data/<namespace>/butchercraft/product`, which remains owned by the richer processing product codec. Product loading must succeed before transformation loading starts, and transformation references validate against the candidate product registry from the same reload.

Version 0.7.0 expands the bundled beef fabrication catalog through the existing product datapack, transformation datapack, processing-operation, resolver, Bandsaw capability, and atomic transaction paths. It does not add product-specific Bandsaw code or a dynamic product item factory.

Version 0.8.0 adds the Packaging Table workstation foundation. The table registers a placeable block, item, block entity, menu, client screen, creative-tab entry, generated assets, and a three-input, one-result inventory.

Version 0.8.0 Sprint 2 adds the Retail Product Framework. `PackagingDefinition` is a pure Java schema and datapack-backed immutable registry under `data/<namespace>/butchercraft/content/packaging`. `ProductDefinition` gains optional packaging metadata linking packaged products to a packaging definition id and source product id. `ContentSnapshotService` now activates product, packaging, and transformation registries together only after candidate products, candidate packaging, product packaging metadata, and candidate transformations validate. The built-in proof adds `butchercraft:retail_package`, `butchercraft:retail_ground_beef`, and `butchercraft:package_retail`.

Version 0.8.0 Sprint C adds Packaging Supplies as fixed Minecraft item registrations and extends `PackagingDefinition` with optional immutable required supply item ids. Built-in packaging definitions now prove `tray_wrap`, `vacuum`, `butcher_paper`, and `freezer_paper` formats. Supply ids validate during packaging datapack loading through the content snapshot, but datapacks do not dynamically register supply items.

Version 0.8.0 Sprint D connects the Packaging Table to the shared processing controller. `PackagingTableExecutionStrategy` validates output product packaging metadata and required supply stacks through the active packaging registry, then `WorkstationInventoryCommitPlan` consumes product input, consumes supplies, and inserts output atomically. The table still does not use transformation definitions, packaging recipes, labels, freshness, spoilage, business logic, or a dynamic product item factory.

Version 0.8.0E establishes the asset framework and presentation foundation. Packaging supply items, the Retail Ground Beef test product, the Packaging Table block model, and the Packaging Table GUI now have stable placeholder-backed resource paths for future final art replacement without gameplay, registry, datapack, or workstation behavior changes.

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
- Economic Planning detection, exact batch allocation, deterministic ranking, idempotent submission, persistence, and ownership boundaries.
- Inspection escalation.

No command should be claimed as tested unless it was actually run.

## Performance Safeguards

- Avoid per-tick full facility scans.
- Avoid per-item-stack heavy histories.
- Use lazy product freshness and temperature updates where possible.
- Batch facility summary updates.
- Put upper bounds on employees scanning for jobs.
- Bound every Planning artifact, per-Need fan-out, evaluation, approval, submission, recursion, provider-work, and total-work dimension.
- Cache multiblock validation results and invalidate on relevant block changes.
- Keep networking incremental.
- Profile before adding complex thermal or AI behavior.
- Provide config caps for maximum facility scan size and active room count.

## Public API Documentation

`docs/API_OVERVIEW.md` records the long-term API vocabulary. It is not a stable Java API, and no current internal package is a compatibility guarantee.

Any package under `com.butchercraft.api` must include documentation for:

- Stability expectations.
- Supported extension points.
- Data ownership.
- Server/client expectations.
- Persistence expectations.
- Versioning and migration behavior.

Industry and compatibility modules must use public APIs, tags, datapack registries, capabilities, or events once those contracts exist. They should not reach into Core internal packages.

## Future Expansion Philosophy

Core owns shared regional identity, the authoritative simulation clock, persistence policy, and future cross-industry economic contracts. Industry modules own their products, equipment, transformations, operating rules, and presentation. Compatibility modules translate external systems into shared contracts without taking ownership of those systems.

New industries must not create parallel clocks, markets, business identities, or save foundations. New APIs must follow a real consumer, not precede one. Existing Meat Processing systems remain internal unless a second implementation demonstrates that a boundary is genuinely generic.

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
