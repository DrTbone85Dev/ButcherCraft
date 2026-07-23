# Changelog

## ButcherCraft v0.9.0-alpha.1 Phase 20 - Industry-Neutral Production Framework

Core:

- Added immutable, schema-versioned Production Process and Plan definitions with exact input/output quantities, whole-batch deterministic yield, capabilities, inventory bindings, requirements, policies, tags, and typed metadata.
- Added separately owned Production Run lifecycle, validation, deterministic indexed registries and queries, exact progress tracking, cancellation, and typed failure behavior.
- Added one internal Production scheduler handler and world lifecycle service while preserving the Simulation Clock as the sole time authority.
- Added atomic multi-input and multi-output completion through an APPLIED `PRODUCTION` Economic Transaction with an explicit ordered inventory-change plan.
- Added deterministic schema-versioned Process, Plan, and Run persistence with complete-set validation before publication.
- Added comprehensive model, registry, validation, execution, transaction, persistence, integration, dependency-boundary, requirement, regression, and scale coverage.

Scope:

- No live industry Processes, machines, workstation migrations, datapacks, automatic planning, Inventory reservation, logistics, markets, pricing, accounting, gameplay, networking, GUI, or ItemStack integration was added.

## ButcherCraft v0.9.0-alpha.1 Phase 19 - Deterministic Simulation Scheduler & Pipeline

Core:

- Added the pure Java six-stage deterministic Work scheduler, separate immutable definitions and runtime lifecycle, monotonic submission sequencing, typed handler/results, retries, cancellation, expiration, and immutable query snapshots.
- Added bounded pipeline execution with stable ordering, explicit stage failure policies, atomic generated batches, and later-stage-only same-tick generation.
- Added schema-versioned `simulation_scheduler.json` persistence and `SimulationSchedulerService` lifecycle integration after the authoritative clock and Orders/Contracts services.
- Added model, lifecycle, registry, ordering, budget, handler, generation, persistence, dependency-boundary, integration, regression, and split-scale stress coverage.

Scope:

- The live handler registry and Work queue are empty. No production, logistics, markets, population, pricing, automatic Order/Contract behavior, gameplay, networking, GUI, or ItemStack integration was added.

## ButcherCraft v0.9.0-alpha.1 Phase 18 - Orders And Contracts Framework

Core:

- Added immutable industry-neutral Order and Contract definitions with separate manager-owned runtime lifecycles.
- Added exact decimal quantities, deterministic registries, typed validation failures, and bounded query APIs.
- Added atomic allocation of APPLIED Transactions across one or more Order lines without mutating Inventory or submitting Transactions.
- Added schema-versioned `orders.json` and `contracts.json` persistence with coordinated cross-reference validation after the Transaction service.
- Added definition, lifecycle, registry, fulfillment, persistence, integration, dependency-boundary, regression, and regional-scale stress coverage.

Scope:

- No pricing, currency, accounting, markets, production, logistics, reservation, automatic scheduling, AI, networking, GUI, ItemStack integration, or gameplay was added.

## ButcherCraft v0.9.0-alpha.1 Phase 17 - Transaction Framework

### Core

- Added the pure Java universal economic transaction schema, validation pipeline, executor, manager, audit registry, results, and typed metadata.
- Added atomic inventory add, remove, transfer, and adjustment execution through the transaction framework.
- Restricted inventory quantity mutation to executor-authorized validated batches and changed runtime access to defensive snapshots.
- Added independent schema-versioned transaction history at `<world>/butchercraft/transactions.json`.
- Added `TransactionService` lifecycle integration after the Inventory service.

### Stability

- Added explicit failures for duplicate ids, unknown references, invalid units and endpoints, invalid statuses, underflow, capacity violations, malformed persistence, and unsupported schemas.
- Added deterministic replay and automated coverage for a 1,000,000-transaction audit registry.
- Preserved all existing Goods, Economic Actors, inventory data, processing, ItemStack, workstation, content registry, and gameplay behavior.

## ButcherCraft v0.9.0-alpha.1 Phase 16 - Inventory And Storage Framework

### Core

- Added the pure Java `com.butchercraft.world.inventory` foundation for actor-owned Good quantities and physical storage locations.
- Added immutable inventory containers, nested storage nodes, typed capacities, exact entries, typed future metadata, and mutable runtime status and quantities.
- Added deterministic inventory registration, ownership and storage queries, capacity validation, runtime quantity updates, and validation-only atomic movement candidates.
- Added independent schema-versioned persistence at `<world>/butchercraft/inventory.json`.
- Added `InventoryService` lifecycle integration after the Economic Actor service.

### Stability

- Added validation for duplicate ids, unknown Goods, actors, and storage nodes, mismatched units, negative quantities, malformed metadata, missing runtimes, capacity violations, malformed persistence, unsupported schemas, and circular storage hierarchies.
- Added automated coverage for deterministic validation of 100,000 inventory containers and 1,000,000 runtime entries.
- Preserved all existing Goods, Economic Actors, Business Runtime, Workforce, processing, ItemStack, workstation, content registry, and gameplay behavior.

## ButcherCraft v0.9.0-alpha.1 Phase 15 - Economic Actor Framework

### Core

- Added the pure Java `com.butchercraft.world.economy.actor` foundation for universal economic participants.
- Added immutable actor definitions with stable ids, typed classifications, capabilities, Good relationships, cross-industry support metadata, and dependency metadata.
- Added mutable in-memory actor runtime state with stable Business Runtime and Workforce references and no inventory or production state.
- Added deterministic actor registration, lookup, relationship queries, validation, and independent schema-versioned persistence at `<world>/butchercraft/economic_actors.json`.
- Added `EconomicActorService` lifecycle integration after the Goods service.

### Stability

- Added validation for duplicate actors and relationships, unknown industries, goods, actors, types, and capabilities, capability-role mismatches, malformed persistence, unsupported schemas, and circular dependency chains.
- Added automated coverage for deterministic loading of 100,000 actor definitions and 10,000 relationships.
- Preserved all existing Goods, Business Runtime, Workforce, processing, ItemStack, workstation, content registry, and gameplay behavior.

## ButcherCraft v0.9.0-alpha.1 Phase 14 - Commodity And Product Framework

### Core

- Added the pure Java `com.butchercraft.world.goods` foundation for universal economic-good definitions.
- Added immutable commodity and economic product definitions with typed units, stages, economic flags, storage requirements, transport requirements, and informational item mappings.
- Added deterministic goods registration, lookup, industry validation, and transformation graph relationships without runtime quantities.
- Added independent schema-versioned persistence at `<world>/butchercraft/goods.json`.

### Stability

- Added validation for duplicate ids, duplicate transformations, unknown industries, unknown good references, malformed definitions, invalid persisted enum values, unsupported schemas, corrupt JSON, and circular transformations.
- Added automated coverage for deterministic loading and 100,000 immutable good definitions.
- Preserved all existing processing products, ItemStack integration, workstation behavior, content registries, and save schemas.

## ButcherCraft v0.9.0-alpha.1 Phase 13 - Core Platform Reorientation

### Architecture

- Redefined ButcherCraft Core as a deterministic regional world simulation engine with Meat Processing as its flagship industry implementation.
- Added canonical platform vision, core principles, module boundaries, simulation model, future economy model, compatibility philosophy, era roadmap, and planned API overview.
- Documented how industry and compatibility modules will participate in shared identity, simulation, persistence, and future economic contracts.
- Reviewed the current package layout and recorded future migration criteria without renaming packages or introducing public APIs.

### Compatibility

- Preserved all existing architecture, historical milestones, save schemas, stable ids, content, and gameplay behavior.
- Added no economy, production, employees, AI, logistics, markets, utilities, compatibility adapters, networking, or gameplay systems.

## ButcherCraft v0.9.0-alpha.1 Phase 12 - Workforce Framework

### Core

- Added the pure Java `com.butchercraft.world.workforce` foundation for business workforce definitions.
- Added immutable workforce definitions, positions, shift assignments, staffing rules, position types, skill levels, certification types, and stable workforce ids.
- Added deterministic workforce defaults for existing businesses based on Business Runtime shift structure.
- Added `WorkforceRegistry`, `WorkforceManager`, and schema-versioned `WorkforceStorage`.
- Added independent JSON persistence at `<world>/butchercraft/workforce_definitions.json`.
- Registered `WorkforceService` for server lifecycle loading and save flushing.

### Stability

- Added validation for duplicate definition ids, duplicate position ids, unknown businesses, invalid shifts, invalid position references, invalid skill levels, invalid certifications, invalid staffing rules, required positions with zero staffing, corrupt persistence, and unsupported schema versions.
- Added stress coverage for 10,000 businesses with multiple workforce definitions each.
- Kept employees, villagers, AI, hiring, firing, payroll, production, machines, inventory, economy, inspections, reputation, productivity, gameplay, GUI, and networking out of scope.

## ButcherCraft v0.9.0-alpha.1 Phase 11 - Business Operations Framework

### Core

- Added the `com.butchercraft.world.business.runtime` foundation for mutable business runtime state separate from immutable Business Identity.
- Added business operational status, business hours, shift schedules, workforce capacity, active workforce, maintenance state, and schema-versioned runtime state records.
- Added a deterministic `BusinessRuntimeRegistry` and `BusinessRuntimeManager` for lookup, validation, opening, closing, maintenance, suspension, and schedule evaluation.
- Added `BusinessEventListener` integration with daily and weekly Simulation Clock rollover events.
- Added independent JSON persistence at `<world>/butchercraft/business_runtime.json`.
- Registered server lifecycle integration for runtime load, event subscription, save flushing, and listener cleanup.

### Stability

- Added validation for duplicate business runtime ids, unknown business references, invalid statuses, invalid hours, invalid shifts, negative workforce values, inconsistent open/closed state, corrupt persistence, and unsupported runtime schema versions.
- Added stress coverage for 10,000 businesses across 365 simulated days with deterministic transitions and no duplicate state changes after the initial opening transition.
- Kept employees, production, machines, economy, payroll, inspections, AI, inventory, orders, customers, transportation, maintenance gameplay, GUI, networking, and gameplay effects out of scope.

## ButcherCraft v0.9.0-alpha.1 Phase 10 - World Simulation Clock & Event Framework

### Core

- Added the deterministic `com.butchercraft.world.simulation` foundation for simulated world time.
- Added configurable simulation timing through `SimulationConfiguration`, `SimulationTime`, and `SimulationCalendar`.
- Added `SimulationClock` as the authoritative simulated-time owner with day, week, month, year, weekday, and season calculation.
- Added `SimulationScheduler`, `ScheduledSimulationEvent`, `SimulationEventType`, `SimulationEventStatus`, and `SimulationEventBus`.
- Added built-in rollover infrastructure events for daily, weekly, monthly, and yearly cycles.
- Added independent JSON persistence at `<world>/butchercraft/simulation_state.json`.
- Registered server lifecycle integration for server start, server tick advancement, and server stop flushing.

### Stability

- Added validation for negative time, invalid configuration values, invalid calendar values, duplicate event ids, non-pending scheduled events, events scheduled in the past, corrupt persistence, and unsupported simulation schema versions.
- Added stress coverage for one million simulation ticks with deterministic event ordering and no duplicate execution.
- Kept production, economy, machines, workers, NPC AI, inspections, refrigeration, maintenance, reputation, business operations, GUI, commands, and gameplay effects out of scope.

## ButcherCraft v0.9.0-alpha.1 Phase 9 - Player Identity Instantiation & Persistence

### Core

- Added runtime player identity records that map a Minecraft UUID to a stable ButcherCraft `PlayerIdentityId`, starting scenario, career profile, world settlement, optional property/business/ownership/family references, deterministic creation timestamp, and schema version.
- Added an immutable runtime `PlayerIdentityRegistry` with UUID and identity-id indexes.
- Added `PlayerIdentityFactory`, `PlayerIdentityManager`, `PlayerIdentityStorage`, and `PlayerJoinInitializer`.
- Registered server-side player-login initialization so a player identity is created once on first join and reused on later joins.
- Added independent JSON persistence at `<world>/butchercraft/player_identities.json`.
- Preserved World Identity schema version 6; player identities are not stored inside the immutable World Identity snapshot.

### Stability

- Added validation for duplicate Minecraft UUIDs, duplicate player identity ids, invalid starting scenarios, invalid career profiles, missing settlements, missing commercial properties, missing businesses, missing ownership entities, missing families, corrupt persistence, and unsupported player identity schema versions.
- Added deterministic creation tests proving the same world seed and UUID resolve to the same identity.
- Added multiplayer simulation coverage for 100 player joins without identity collisions.
- Kept economy, production, inventory, machines, employees, NPC AI, contracts, progression, skills, money, orders, reputation changes, business simulation, rendering, and gameplay effects out of scope.

## ButcherCraft v0.9.0-alpha.1 Phase 8 - Player Legacy Foundation

### Core

- Added immutable player legacy domain models for player identities, career profiles, starting scenarios, starting assets, starting relationships, inheritance records, legacy progress, and player backgrounds.
- Added a deterministic built-in starting scenario catalog covering inherited family businesses, vacant property purchases, existing business managers, startup operations, county contracts, and cooperative assignments.
- Added immutable `PlayerRegistry` validation and lookup support by scenario id, starting scenario type, and career profile.
- Preserved World Identity schema version 6 because Phase 8 does not generate or persist player save data yet.

### Stability

- Added validation for duplicate scenario ids, duplicate scenario names, missing career coverage, missing scenario type coverage, missing inheritance records, orphaned placeholders, invalid player scenario references, incompatible career profiles, unknown starting settlements, missing summaries, and malformed typed fields.
- Added regression coverage proving player legacy templates do not alter World Identity persistence or migration behavior.
- Preserved the principle that the player enters an existing simulation rather than creating the world.
- Kept character creation, UI, networking, economy, inventory, machines, NPCs, quests, commands, progression systems, and gameplay effects out of scope.

## ButcherCraft v0.9.0-alpha.1 Phase 7 - Supply Chain & Trade Network Foundation

### Core

- Added immutable supply-chain and trade-network identity models for supply networks, trade regions, distribution territories, distribution routes, supply relationships, archival supply contracts, preferred suppliers, preferred manufacturers, and business specialization profiles.
- Added deterministic trade network generation from the saved world seed, region, settlements, businesses, manufacturers, and ownership histories.
- Added immutable `TradeNetworkRegistry` validation and lookup support by relationship id, business, manufacturer, settlement, product category, territory, and relationship type.
- Extended World Identity persistence to schema version 6 so supply network records are saved with the existing world identity snapshot.

### Stability

- Added migration from Phase 1 through Phase 5 development world identity schemas by preserving saved identity data and generating supply network records from the saved seed and preserved business or ownership snapshot.
- Added validation for duplicate ids, invalid business references, invalid manufacturer references, invalid territories, broken chronology, duplicate relationships, missing product categories, self-supply relationships, missing contracts, missing preferred-supplier records, orphaned territories, orphaned trade regions, and missing business specialization coverage.
- Preserved the permanent separation between commercial properties, businesses, ownership entities, and supply networks.
- Kept economy, pricing, purchasing, inventory, transportation simulation, UI, commands, NPCs, and gameplay effects out of scope.

## ButcherCraft v0.9.0-alpha.1 Phase 6 - Family & Ownership Identity Foundation

### Core

- Added immutable family and ownership identity domain models for families, historical persons, ownership entities, ownership shares, ownership records, ownership histories, and family relationship placeholders.
- Added deterministic ownership generation from the saved world seed, region, settlements, and business records.
- Added immutable `FamilyRegistry` and `OwnershipRegistry` validation and lookup support.
- Extended World Identity persistence to schema version 5 so family and ownership records are saved with the existing world identity snapshot.

### Stability

- Added migration from Phase 1 through Phase 4 development world identity schemas by preserving saved identity data and generating ownership records from the saved seed and business snapshot.
- Added validation for duplicate ids, missing business references, missing family/person references, orphaned ownership entities, invalid shares, ownership totals above 100 percent, broken chronology, and incomplete typed fields.
- Preserved the permanent separation between commercial properties, businesses, and ownership entities.
- Kept NPCs, player families, inheritance gameplay, dialogue, marriage, children, AI, economy, payroll, lawsuits, politics, UI, commands, and gameplay effects out of scope.

## ButcherCraft v0.9.0-alpha.1 Phase 5 - Business Identity Foundation

### Core

- Added immutable business identity domain models for business ids, business types, operational status, reputation, occupancy history, ownership metadata, lineage placeholders, and future manufacturer references.
- Added deterministic business generation from the saved world seed, region, settlements, and commercial property records.
- Added an immutable `BusinessRegistry` with deterministic ordering and lookup by id, property, settlement, business type, status, reputation, and search text.
- Extended World Identity persistence to schema version 4 so business records are saved with the existing world identity snapshot.

### Stability

- Added migration from Phase 1, Phase 2, and Phase 3 development world identity schemas by preserving saved identity data and generating business records from the saved seed, settlements, and commercial properties.
- Added validation for duplicate ids, duplicate business names within a settlement, invalid property references, invalid manufacturer references, invalid founding years, missing summaries, empty occupancy history, broken timelines, and invalid typed fields.
- Preserved the permanent separation between commercial properties, businesses, and owners.
- Kept player-owned businesses, purchasing, economy, money, employees, NPC interaction, UI, commands, recipes, machine ownership, property purchasing, retail customers, progression systems, and physical buildings out of scope.

## ButcherCraft v0.9.0-alpha.1 Phase 4 - Commercial Property Foundation

### Core

- Added immutable commercial property domain models for permanent property locations, property ids, types, condition, status, utilities, sizes, expansion capacity, history, and ownership records.
- Added deterministic generation of commercial properties for every generated settlement.
- Added an immutable `CommercialPropertyRegistry` with deterministic ordering and lookup by id, settlement, property type, status, condition, and search text.
- Extended World Identity persistence to schema version 3 so commercial properties are saved with the existing world identity snapshot.

### Stability

- Added migration from Phase 1 and Phase 2 development world identity schemas by preserving saved identity data and generating commercial properties from the saved seed and settlements.
- Added validation for duplicate ids, duplicate property names within a settlement, invalid settlement references, invalid construction years, empty ownership history, missing utility profiles, missing summaries, and invalid typed fields.
- Kept business entities, player ownership, purchasing, economy, village structures, building placement, inspections, taxes, UI, commands, NPC behavior, and gameplay effects out of scope.

## ButcherCraft v0.9.0-alpha.1 Phase 3 - Manufacturer Foundation

### Core

- Added immutable manufacturer domain models for manufacturers, categories, market tiers, headquarters, branding, and engineering philosophies.
- Added a canonical built-in manufacturer catalog containing 30 handcrafted fictional companies.
- Added an immutable `ManufacturerRegistry` with deterministic ordering and lookup by id, category, tier, region, and search text.
- Integrated manufacturer headquarters with the existing handcrafted World Identity regions.

### Stability

- Added validation for duplicate ids, duplicate names, duplicate slogans, invalid headquarters regions, missing categories, missing branding, invalid founding years, empty histories, empty specialties, and missing engineering philosophies.
- Added regression coverage for catalog integrity, regional distribution, lookup behavior, deterministic ordering, validation failures, future placeholder fields, immutability, and Minecraft dependency boundaries.
- Kept gameplay, economy, purchasing, villagers, recipes, machines, UI, commands, player interaction, progression, and commercial properties out of scope.

## ButcherCraft v0.9.0-alpha.1 - World Identity Regions And Naming

### Core

- Added a canonical handcrafted region catalog for Prairie Commonwealth, Iron Valley, Great River Basin, High Plains Territory, and Timber Ridge.
- Added immutable region definitions, naming profiles, naming roles, and deterministic name selection infrastructure.
- Updated world identity generation to select regions from the catalog and generate county and settlement names from region-specific curated name pools.
- Updated the world identity persistence schema to version 2 with region description, cultural identity, and naming profile id fields.
- Updated development version metadata to `0.9.0-alpha.1`.

### Stability

- Added a deliberate migration path for Phase 1 development world identity saves.
- Added validation for duplicate region ids, missing region definitions, unsupported naming profiles, malformed naming data, blank generated names, and duplicate generated names.
- Kept manufacturers, commercial properties, economy systems, interfaces, commands, and other player-facing gameplay out of scope.

## ButcherCraft v0.9.0 Phase 1 - World Identity Foundation

### Core

- Added immutable World Identity domain models for world identity, region, county, and settlement data.
- Added deterministic world identity generation from the Minecraft world seed.
- Added world-level SavedData persistence so each world identity is generated once and reloaded on later sessions.
- Added a server-start World Identity service boundary for creating, loading, and providing access to the active identity.

### Stability

- Kept manufacturers, commercial properties, economy systems, gameplay interactions, commands, screens, and world-generation changes out of scope.
- Added regression coverage for deterministic generation, validation, serialization, persistence, service load/create behavior, and Minecraft dependency boundaries.

## ButcherCraft v0.8.0 - Project Meat Counter

### Core

- Added the Packaging Table workstation foundation as a placeable block with a block item, block entity, menu, client screen, creative-tab entry, language entries, loot table, and placeholder assets.
- Added a shared inventory-only workstation block entity base so non-processing station foundations can persist, synchronize, expose inventory capability, and drop contents without joining the processing controller path.
- Generalized workstation inventory, menu layout, and controller commit handling for multi-input workstations while preserving existing Grinder, Bandsaw, and Development Processing Workstation behavior.
- Added the Retail Product Framework with datapack-backed packaging definitions, optional product packaging metadata, and atomic content snapshot integration.
- Added data-only `butchercraft:retail_package`, `butchercraft:retail_ground_beef`, and the graph-only `butchercraft:package_retail` processing operation.
- Added Packaging Supplies: Foam Tray, Plastic Wrap Roll, Vacuum Bag, Butcher Paper Roll, Freezer Paper Roll, and Retail Label Roll.
- Expanded packaging definitions with `tray_wrap`, `vacuum`, `butcher_paper`, and `freezer_paper` formats plus optional required supply item references.
- Implemented the first Packaging Table gameplay flow: Ground Beef plus required retail packaging supplies now processes through `butchercraft:package_retail` into Retail Ground Beef.
- Added stack-level packaging metadata for packaged product fixtures while preserving legacy product stack data compatibility.
- Established the asset framework foundation with per-asset packaging texture paths, a Packaging Table GUI texture contract, a polished placeholder table model structure, and an asset manifest/specification handoff for future final artwork.

### Stability

- Kept packaging recipes, labels, freshness, spoilage, dynamic rendering, employee behavior, order integration, and business logic out of scope.
- Preserved existing Grinder and Bandsaw gameplay behavior while extending datapack loading to product, packaging, and transformation snapshots.
- Added atomic workstation commit planning so packaging product input, required supplies, and output insertion commit or roll back together.
- Added regression coverage for Packaging Table registration, lifecycle, inventory persistence, menu layout, generated data, packaging definition loading, supply reference validation, product metadata validation, serialization, creative-tab population, packaging execution, blocked output behavior, content-loading compatibility, asset references, GUI bounds, and placeholder manifest policy.

## ButcherCraft v0.7.0 - Beef Fabrication Expansion

### Core

- Expanded the bundled beef fabrication catalog with 16 datapack-backed product definitions.
- Added four Bandsaw transformations for hindquarter, short loin, round, and sirloin fabrication.
- Added matching processing-operation definitions so the existing resolver and processing graph can discover the new flows.
- Added development fixture items, item models, language entries, creative-tab entries, and temporary product-to-item mappings for the new products.

### Stability

- Preserved the existing Grinder and Bandsaw execution architecture.
- Kept product and cut ids out of Bandsaw and generic workstation machine logic.
- Kept content snapshot product JSON separate from the Minecraft product registry path to avoid schema collisions during world creation.
- Added regression coverage for datapack loading, content snapshots, processing graph edges, Bandsaw resolution, ItemStack mapping, and ordered runtime outputs.

## ButcherCraft v0.6.9 - Datapack Product Loading

### Core

- Added a stable serialized product-definition schema and canonical serializer/deserializer.
- Added datapack JSON loading for product definitions under `data/<namespace>/butchercraft/content/product`.
- Moved the current Grinder and Bandsaw proof product definitions into bundled datapack resources.
- Added atomic content snapshot activation so product and transformation registries reload together.

### Stability

- Added structured validation errors for malformed product datapacks.
- Rejected duplicate product ids, missing ids or display names, unsupported schema versions, unknown product categories, unknown quantity units, malformed tags, and malformed metadata.
- Validated transformation product references against the candidate product registry from the same reload.
- Preserved existing Product-to-ItemStack mappings, Grinder behavior, and Bandsaw behavior.

## ButcherCraft v0.6.8 - Datapack Transformation Loading

### Core

- Added datapack JSON loading for transformation definitions.
- Moved the existing Grinder and Bandsaw transformation definitions into bundled datapack resources.
- Added reload-safe transformation registry replacement for successful datapack reloads.

### Stability

- Added structured validation errors for malformed transformation datapacks.
- Rejected duplicate transformation ids, unknown products, unknown capabilities, unsupported schema versions, and malformed definitions.
- Preserved existing Grinder and Bandsaw runtime behavior.

## ButcherCraft v0.6.7 - Bandsaw Transformation Migration

### Core

- Migrated only the Bandsaw to capability-based, registry-driven transformation execution.
- Registered the built-in `butchercraft:break_beef_forequarter` transformation with eight ordered outputs.
- Added the minimum pure product definitions needed for the current Bandsaw proof products.

### Stability

- Added a Minecraft-side workstation inventory material-store bridge for atomic transformation validation.
- Preserved existing Bandsaw paired-block, obstruction, duration, save/load, menu, and block-break behavior.
- Added regression coverage for Bandsaw atomic failure handling, product mappings, bridge capacity checks, and existing Grinder compatibility.

## ButcherCraft v0.6.6 - Atomic Multi-Output Transformations

### Core

- Added a pure Java in-memory material store and transaction model for transformation execution.
- Extended transformation execution with an atomic commit path that can consume inputs and insert any number of ordered outputs.
- Added output-capacity and rollback failure codes for transformation transactions.

### Stability

- Added regression tests for multi-output ordering, capacity rejection, commit-time revalidation, rollback after partial insertion failure, and existing Grinder one-output compatibility.
- Kept Bandsaw and other workstations on their current execution paths; this release proves the transformation engine capability only.

## ButcherCraft v0.6.5 - Transformation Serialization Foundation

### Core

- Added pure Java serializer and deserializer contracts for `TransformationDefinition`.
- Added the canonical serialized transformation representation with stable external field names.
- Added a `TransformationSchemaVersion` abstraction and a future migration interface without implementing migrations.

### Stability

- Added round-trip and validation tests for transformation serialization.
- Preserved built-in Grinder transformations and kept serialization independent of Minecraft, NeoForge, datapack loading, and resource reload behavior.

## ButcherCraft v0.6.4 - Product Definition Foundation

### Core

- Added a pure Java canonical `ProductDefinition` schema and immutable `ProductRegistry`.
- Registered the six current Grinder products as built-in product definitions.
- Added deterministic transformation product-reference validation against the product registry.

### Stability

- Added tests for product schema validation, registry behavior, pure dependency boundaries, and built-in Grinder transformation product references.
- Preserved existing Grinder behavior and kept product definitions separate from ItemStack data and transformation definitions.

## ButcherCraft v0.6.3 - Transformation Schema

### Core

- Expanded `TransformationDefinition` into the canonical immutable schema for future transformations.
- Added display name, schema version, required capability, yield, and metadata fields.
- Added a fluent builder API for transformation definitions.
- Preserved legacy constructor compatibility for the existing Grinder transformation path.

### Stability

- Added schema tests for validation, equality, immutability, metadata handling, and builder behavior.
- Kept transformation schema code pure Java with no serialization or datapack loading in this slice.

## ButcherCraft v0.6.2 - Transformation Registry

### Core

- Added an immutable pure Java transformation registry and builder.
- Registered the built-in Grinder transformations through the registry.
- Updated Grinder transformation execution to query registered definitions by resolved operation id.

### Stability

- Added registry tests for insertion order, lookup, duplicate rejection, null rejection, capability queries, and built-in Grinder coverage.
- Preserved existing Grinder processing output behavior while making transformation definitions registry-backed.

## ButcherCraft v0.6.1 - Grinder Transformation Bridge

### Core

- Connected the Grinder workstation path to the pure Java material transformation engine.
- Added capability-based transformation execution while preserving existing Grinder product behavior.
- Kept Bandsaw and other workstations on the existing execution path for a future deliberate migration.

### Stability

- Added regression coverage for accepted-evaluation execution and Grinder capability advertisement.
- Preserved compatibility with existing processing-operation definitions through the transformation adapter.

## ButcherCraft v0.5.2 - Foundation Update

### Core

- Expanded the internal processing framework for future production systems.
- Improved workstation architecture in preparation for functional processing equipment.
- Enhanced product data handling for future inventory and quality simulation.
- Refined internal systems supporting upcoming gameplay features.

### Performance and Stability

- Improved internal code organization.
- Expanded automated test coverage where appropriate.
- Corrected framework issues discovered during development.
- Improved project stability for future updates.

### Player-Facing

- Added `/butchercraft info` to display the installed version and current development status.

### Developer Note

This release focuses on strengthening ButcherCraft's foundation. Although it does not introduce a major gameplay system, it prepares the project for future processing, inventory, employee, inspection, and business-management features.
