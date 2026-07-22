# Changelog

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
