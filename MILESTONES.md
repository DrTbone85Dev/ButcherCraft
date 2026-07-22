# ButcherCraft Milestones

Status: proposed planning document

Each milestone should remain small, testable, and rollback-friendly. Do not claim verification unless the command or manual test was actually run.

## Milestone 0.9.0 Phase 7: Supply Chain & Trade Network Foundation

Goal: establish permanent supply-chain and trade-network identity records that explain how businesses, manufacturers, territories, and commercial routes are historically connected without adding purchasing, pricing, inventory, logistics, UI, commands, or gameplay behavior.

Included work:

- Immutable pure Java trade domain models: `SupplyNetwork`, `SupplyNetworkId`, `SupplyRelationship`, `SupplyContract`, `DistributionRoute`, `DistributionTerritory`, `TradeRegion`, `PreferredSupplier`, `PreferredManufacturer`, `BusinessSpecializationProfile`, `BusinessSpecialization`, `ProductCategory`, and `TradeNetworkRegistry`.
- Strongly typed supply relationship categories, product categories, business specializations, and relationship strengths.
- Deterministic supply network generation from the saved world seed, region, settlements, businesses, manufacturers, and ownership histories.
- Supply relationships that reference supplier and customer business ids while preserving the separation between commercial properties, businesses, ownership entities, and supply networks.
- Immutable `TradeNetworkRegistry` validation and lookup by relationship id, business, manufacturer, settlement, product category, territory, and relationship type.
- World Identity schema version 6 with supply network data saved in the existing world identity persistence format.
- Development-schema migration from Phase 1 through Phase 5 identities by preserving saved identity data and generating the trade network from the saved seed and preserved business or ownership snapshot.
- Automated coverage for deterministic generation, generation-order independence, relationship id stability, registry lookup, relationship integrity, validation failures, serialization, save/load persistence, schema migration, and Minecraft dependency boundaries.

Excluded work:

- Economy, pricing, money, inventory, purchasing, contracts as gameplay, transportation simulation, trucking, vehicles, pathfinding, AI, NPC behavior, player interaction, UI, commands, recipes, machine behavior, shortages, product recalls, seasonal variation, and dynamic markets.

Acceptance criteria:

- Generated World Identity snapshots include deterministic trade regions, distribution territories, distribution routes, supply relationships, archival contracts, preferred supplier records, preferred manufacturer records, and business specialization profiles.
- Supply relationship ids, business references, manufacturer references, territory references, product categories, chronology, contracts, preferred-supplier records, and specialization coverage validate.
- Supply network data persists through the existing World Identity save format.
- Phase 1 through Phase 5 development saves migrate through a deliberate schema path rather than being regenerated wholesale.
- The trade package remains independent of Minecraft and NeoForge imports.
- Existing workstation, product, packaging, transformation, manufacturer, property, region, business, and ownership behavior remains unchanged.

Automated verification:

- `.\gradlew.bat --no-daemon clean test`
- `.\gradlew.bat --no-daemon build`
- `git diff --check`

Manual verification:

- Runtime startup validation is recommended after persistence changes. Phase 7 does not add player-facing supply-chain interaction.

Rollback considerations:

- The Phase 7 foundation is additive around the World Identity snapshot. Removing the trade domain, generator, registry, schema migration, tests, and documentation entry should restore the Phase 6 family and ownership foundation without affecting products, transformations, packaging, workstations, manufacturers, properties, businesses, or registered gameplay content.

## Milestone 0.9.0 Phase 6: Family & Ownership Identity Foundation

Goal: establish permanent family and ownership identity records that explain who built, owned, operated, inherited, sold, and merged businesses without making owners into businesses or commercial properties.

Included work:

- Immutable pure Java ownership domain models: `OwnershipEntity`, `OwnershipEntityId`, `OwnershipEntityType`, `Family`, `FamilyId`, `PersonIdentity`, `PersonId`, `OwnershipShare`, `OwnershipRecord`, `OwnershipHistory`, `FamilyRelationship`, `FamilyRegistry`, and `OwnershipRegistry`.
- Strongly typed ownership entity categories for individuals, families, partnerships, cooperatives, corporations, estates, and municipalities.
- Strongly typed acquisition methods and family reputation classifications with no gameplay effects.
- Deterministic generation of family, historical person, ownership entity, and ownership history records from the saved world seed, region, settlements, and business records.
- Ownership histories that reference business ids while businesses continue to reference only commercial property ids.
- Ownership percentages stored as basis points so future partnerships and shareholder models can split ownership without redesign.
- World Identity schema version 5 with ownership data saved in the existing world identity persistence format.
- Development-schema migration from Phase 1 through Phase 4 identities by preserving saved region, county, settlement, property, and business data and generating ownership from the saved seed and business snapshot.
- Automated coverage for deterministic generation, generation-order independence, identity stability, registry lookup, share validation, chronology validation, serialization, save/load persistence, schema migration, and Minecraft dependency boundaries.

Excluded work:

- NPCs, player families, inheritance gameplay, dialogue, marriage, children, AI, economy, payroll, lawsuits, politics, UI, commands, quests, active relationships, family simulation, physical entities, and gameplay effects.

Acceptance criteria:

- Generated World Identity snapshots include deterministic families, historical people, ownership entities, and ownership histories for generated business records.
- Ownership ids, family ids, person ids, business references, ownership shares, chronology, family references, person references, and registry ordering validate.
- Ownership data persists through the existing World Identity save format.
- Phase 1 through Phase 4 development saves migrate through a deliberate schema path rather than being regenerated wholesale.
- The ownership package remains independent of Minecraft and NeoForge imports.
- Existing workstation, product, packaging, transformation, manufacturer, property, region, and business behavior remains unchanged.

Automated verification:

- `.\gradlew.bat --no-daemon clean test`
- `.\gradlew.bat --no-daemon build`
- `git diff --check`

Manual verification:

- Runtime startup validation is recommended after persistence changes. Phase 6 does not add player-facing ownership interaction.

Rollback considerations:

- The Phase 6 foundation is additive around the World Identity snapshot. Removing the ownership domain, generator, registries, schema migration, tests, and documentation entry should restore the Phase 5 business identity foundation without affecting products, transformations, packaging, workstations, manufacturers, properties, businesses, or registered gameplay content.

## Milestone 0.9.0 Phase 5: Business Identity Foundation

Goal: establish permanent business identity records as world identity organizations that future inheritance, purchase, competition, employees, suppliers, inspections, newspapers, finances, mergers, and family legacy systems can reference without making businesses into properties or owners.

Included work:

- Immutable pure Java business domain models: `Business`, `BusinessId`, `BusinessType`, `BusinessStatus`, `BusinessReputation`, `BusinessOccupancy`, `BusinessHistory`, `BusinessOwnershipModel`, and `BusinessRelationship`.
- Strongly typed business types for family butcher shops, retail meat markets, custom processors, regional processing companies, locker plants, cold storage companies, food distribution companies, and wholesale suppliers.
- Strongly typed operational status and reputation classifications with no gameplay effects.
- Deterministic generation of business records from the saved world seed, region, settlements, and commercial properties using stable property ids.
- Occupancy history records that reference commercial property ids while leaving commercial property records independent of businesses.
- Future-ready placeholders for additional locations, corporate headquarters, business relationships, ownership metadata, and preferred manufacturer ids.
- Immutable `BusinessRegistry` with deterministic id ordering, id lookup, property lookup, settlement lookup, type lookup, status lookup, reputation lookup, and simple future-facing search.
- World Identity schema version 4 with businesses saved in the existing world identity persistence format.
- Development-schema migration from Phase 1, Phase 2, and Phase 3 identities by preserving saved region, county, settlement, and property data and generating businesses from the saved seed and property snapshot.
- Automated coverage for deterministic generation, generation-order independence, property references, registry lookup, validation failures, serialization, save/load persistence, schema migration, historical summaries, occupancy preservation, identity stability, and Minecraft dependency boundaries.

Excluded work:

- Player-owned businesses, purchasing, economy, money, employees, NPC interaction, UI, commands, recipes, machine ownership, property purchasing, retail customers, progression systems, physical buildings, active supplier relationships, inspections, taxes, newspapers, and gameplay effects.

Acceptance criteria:

- Generated World Identity snapshots include deterministic business records for occupied-capable commercial properties.
- Business ids, names within each settlement, occupancy histories, primary property references, settlement references, region references, statuses, reputations, ownership metadata, and manufacturer placeholders validate.
- Businesses persist through the existing World Identity save format.
- Phase 1, Phase 2, and Phase 3 development saves migrate through a deliberate schema path rather than being regenerated wholesale.
- The business package remains independent of Minecraft and NeoForge imports.
- Existing workstation, product, packaging, transformation, manufacturer, property, and region behavior remains unchanged.

Automated verification:

- `.\gradlew.bat --no-daemon clean test`
- `.\gradlew.bat --no-daemon build`
- `git diff --check`

Manual verification:

- Runtime startup validation is recommended after persistence changes. Phase 5 does not add player-facing business interaction.

Rollback considerations:

- The Phase 5 foundation is additive around the World Identity snapshot. Removing the business domain, generator, registry, schema migration, tests, and documentation entry should restore the Phase 4 commercial property foundation without affecting products, transformations, packaging, workstations, manufacturers, properties, or registered gameplay content.

## Milestone 0.9.0 Phase 4: Commercial Property Foundation

Goal: establish permanent commercial property records as world identity locations that future business, inheritance, purchase, renovation, inspection, newspaper, supplier, insurance, tax, and history systems can reference without making properties into businesses.

Included work:

- Immutable pure Java commercial property domain models: `CommercialProperty`, `CommercialPropertyId`, `CommercialPropertyType`, `PropertyCondition`, `PropertyStatus`, `UtilityProfile`, `OwnershipRecord`, `PropertyHistory`, `LotSize`, `BuildingSize`, and `ExpansionCapacity`.
- Strongly typed utility infrastructure fields for electrical service, water, sewer, natural gas, loading dock, rail access, highway access, and refrigeration capacity.
- Strongly typed property types for family butcher shops, vacant storefronts, locker plants, warehouses, industrial buildings, empty commercial lots, distribution centers, and cold storage facilities.
- Deterministic generation of four commercial properties for every generated settlement using stable settlement ids, property slots, and the world seed.
- Historical summaries and ownership records for each property, with owner names recorded as property history rather than business entities.
- Immutable `CommercialPropertyRegistry` with deterministic id ordering, id lookup, settlement lookup, type lookup, status lookup, condition lookup, and simple future-facing search.
- World Identity schema version 3 with commercial properties saved in the existing world identity persistence format.
- Development-schema migration from Phase 1 and Phase 2 identities by preserving saved region, county, and settlement data and generating commercial properties from the saved seed and settlements.
- Automated coverage for deterministic generation, generation-order independence, settlement references, registry lookup, validation failures, serialization, save/load persistence, schema migration, historical summaries, ownership preservation, and Minecraft dependency boundaries.

Excluded work:

- Business entities, player ownership, property purchasing, village structure generation, building placement, economy, NPC behavior, retail customers, machine placement, contracts, inspections, taxes, UI, commands, progression, and physical commercial buildings.

Acceptance criteria:

- Every generated settlement has commercial property records.
- Commercial property ids, names within each settlement, histories, ownership records, utility profiles, condition, status, sizes, and settlement references validate.
- Commercial properties persist through the existing World Identity save format.
- Phase 1 and Phase 2 development saves migrate through a deliberate schema path rather than being regenerated wholesale.
- The commercial property package remains independent of Minecraft and NeoForge imports.
- Existing workstation, product, packaging, transformation, manufacturer, and region behavior remains unchanged.

Automated verification:

- `.\gradlew.bat --no-daemon clean test`
- `.\gradlew.bat --no-daemon build`
- `git diff --check`

Manual in-game verification:

- Runtime startup validation is recommended after persistence changes. Phase 4 does not add player-facing commercial property interaction.

Rollback considerations:

- The Phase 4 foundation is additive around the World Identity snapshot. Removing the commercial property domain, generator, registry, schema migration, tests, and documentation entry should restore the Phase 3 manufacturer foundation without affecting products, transformations, packaging, workstations, or registered gameplay content.

## Milestone 0.9.0 Phase 3: Manufacturer Foundation

Goal: establish the canonical manufacturer database for future catalogs, manuals, advertisements, warranties, service records, supplier relationships, trade shows, and industrial history without adding gameplay systems.

Included work:

- Immutable pure Java manufacturer domain models: `Manufacturer`, `ManufacturerCategory`, `ManufacturerTier`, `ManufacturerBranding`, `Headquarters`, and `EngineeringPhilosophy`.
- Canonical `BuiltInManufacturerCatalog` with exactly 30 handcrafted fictional manufacturers.
- Immutable `ManufacturerRegistry` with deterministic id ordering, id lookup, category lookup, tier lookup, region lookup, and simple future-facing search.
- Manufacturer headquarters references to the five existing handcrafted World Identity regions.
- Per-company founding year, history, slogan, brand colors, visual identity, specialties, reputation, website placeholder, and catalog description placeholder.
- Validation for duplicate ids, duplicate names, duplicate slogans, invalid headquarters regions, missing categories, missing branding, invalid founding years, null engineering philosophies, empty histories, and empty specialties.
- Automated coverage for catalog integrity, distribution, lookup behavior, deterministic ordering, validation failures, immutability, future placeholder fields, and Minecraft dependency boundaries.

Excluded work:

- Gameplay, economy, purchasing, villagers, recipes, machines, UI, commands, player interaction, progression systems, commercial properties, equipment catalogs, manuals, advertisements, warranties, service bulletins, trade shows, supplier relationships, recalls, company mergers, and historical timelines.

Acceptance criteria:

- Exactly 30 manufacturers exist.
- Manufacturer ids, names, and slogans are unique.
- Every manufacturer has valid regional headquarters, categories, tier, philosophy, branding, history, specialties, reputation, and future placeholders.
- Registry ordering is deterministic and independent of catalog construction order.
- Lookups by id, category, tier, and region are deterministic.
- The manufacturer package remains independent of Minecraft and NeoForge imports.

Automated verification:

- `.\gradlew.bat --no-daemon clean test`
- `.\gradlew.bat --no-daemon build`
- `git diff --check`

Manual in-game verification:

- Not required for Phase 3 because the manufacturer registry is pure Java and is not initialized by gameplay, startup registration, commands, screens, or datapacks.

Rollback considerations:

- The Phase 3 foundation is additive. Removing the manufacturer domain, built-in catalog, registry, tests, and documentation entry should restore the Phase 2 World Identity foundation without affecting products, transformations, packaging, workstations, or runtime registration.

## Milestone 0.9.0 Phase 2: Regions And Naming

Goal: replace placeholder region generation with handcrafted region definitions and deterministic region-aware naming infrastructure without adding player-facing gameplay.

Included work:

- Canonical pure Java `RegionCatalog` containing Prairie Commonwealth, Iron Valley, Great River Basin, High Plains Territory, and Timber Ridge.
- Immutable `RegionDefinition`, `NamingProfile`, and `NamingRole` models for handcrafted regional identity and curated name pools.
- Deterministic region selection from stable region ids and the world seed.
- Deterministic county and settlement naming derived from the world seed plus stable region, profile, role, and entity ids.
- World identity schema version 2 with region description, cultural identity, and naming profile id fields.
- Development-schema migration for Phase 1 world identity saves.
- Automated coverage for built-in region completeness, uniqueness, deterministic selection, broad seed coverage, name stability, generation-order independence, duplicate-name checks, catalog validation, serialization, and migration behavior.
- Development version metadata set to `0.9.0-alpha.1`.

Excluded work:

- Manufacturers, commercial properties, economy simulation, interfaces, commands, GUI, road names, business names, property names, manufacturer names, and gameplay interactions.

Acceptance criteria:

- All five initial handcrafted regions exist and validate.
- Region selection is deterministic and independent of catalog construction order.
- County and settlement names are deterministic, nonblank, region-specific, and duplicate-free within a generated identity.
- Phase 1 development saves load through an explicit migration path rather than being regenerated.
- Existing workstation, product, packaging, and datapack gameplay remains unchanged.

Automated verification:

- `.\gradlew.bat --no-daemon clean test`
- `.\gradlew.bat --no-daemon build`
- `git diff --check`

Manual in-game verification:

- Deferred unless runtime startup validation is needed. Phase 2 does not add player-facing World Identity interactions.

Rollback considerations:

- The Phase 2 foundation is additive around the Phase 1 world identity boundary. Removing the catalog, naming infrastructure, schema migration, tests, and version/docs updates should restore the Phase 1 development architecture.

## Milestone 0.9.0 Phase 1: World Identity Foundation

Goal: establish the World Identity Engine foundation as deterministic world-level architecture without adding economy, commerce, manufacturers, or player-facing gameplay.

Included work:

- Immutable pure Java domain models for `WorldIdentity`, `Region`, `County`, and `Settlement`.
- Deterministic world identity generation from the Minecraft world seed.
- Server-side `WorldIdentityService` for creating, loading, caching, and exposing the active world identity.
- Overworld-scoped `SavedData` persistence under `butchercraft_world_identity` so generated identity data is created once per world and reloaded on subsequent sessions.
- NBT serialization and validation for the current world identity schema.
- Automated coverage for deterministic generation, model validation, serialization, persistence, service load/create behavior, source-level save/load integration, and Minecraft dependency boundaries.

Excluded work:

- Manufacturers, commercial properties, economy simulation, settlements as interactable locations, business logic, commands, GUI, networking, new world-generation features, and gameplay interactions.

Acceptance criteria:

- Identical world seeds produce identical world identity data.
- Different world seeds can produce different identity data.
- A newly generated identity is marked dirty for world persistence.
- Saved identity data round-trips through NBT and is loaded instead of regenerated.
- The World Identity domain package remains independent of Minecraft and NeoForge imports.

Automated verification:

- `.\gradlew.bat --no-daemon test`
- `.\gradlew.bat --no-daemon build`
- `git diff --check`

Manual in-game verification:

- Deferred until a future milestone exposes World Identity data through development diagnostics or gameplay. Phase 1 does not add a player-facing surface.

Rollback considerations:

- The Phase 1 foundation is additive. Removing the world identity domain, service, persistence adapter, server-start listener, tests, and documentation entry should restore the v0.8.0 feature surface without changing existing workstation, product, packaging, or datapack behavior.

## Milestone 0.8.0: Project Meat Counter - Packaging Foundation

Goal: add the Packaging Table as a permanent workstation foundation, establish the Retail Product Framework, introduce Packaging Supplies, and implement the first data-driven packaging gameplay flow without adding packaging recipes, labels, freshness, spoilage, or business logic.

Included work:

- `butchercraft:packaging_table` block, block item, block entity, menu type, client screen, creative-tab entry, language entries, loot table, and placeholder generated assets.
- Inventory-only workstation base for station foundations that need persisted slots and menu synchronization without processing-controller behavior.
- Slot-aware workstation input validation for multi-input processing workstations.
- Shared workstation commit plan that atomically consumes selected input slots and inserts outputs with rollback.
- Packaging Table inventory layout with Meat, Tray, Wrap, and Result slots.
- Server-owned inventory persistence, menu opening, item-handler capability exposure, and block-break inventory recovery.
- Data-driven `PackagingDefinition` loading from `data/<namespace>/butchercraft/content/packaging`.
- Atomic content snapshot activation for product, packaging, and transformation registries.
- Optional product packaging metadata for packaged retail products.
- Built-in `butchercraft:retail_package`, `butchercraft:vacuum_package`, `butchercraft:butcher_paper_package`, and `butchercraft:freezer_paper_package` packaging definitions.
- Built-in `butchercraft:retail_ground_beef` product definition.
- `butchercraft:package_retail` processing operation using `butchercraft:packaging`.
- Packaging supply items: Foam Tray, Plastic Wrap Roll, Vacuum Bag, Butcher Paper Roll, Freezer Paper Roll, and Retail Label Roll.
- Optional `required_supply_items` validation on packaging definitions.
- Packaging Table execution for Ground Beef plus Foam Tray plus Plastic Wrap Roll into Retail Ground Beef.
- Stack-level packaging metadata on packaged product ItemStacks.
- Asset framework foundation for the active packaging build: canonical texture directories, per-asset packaging texture paths, a Packaging Table GUI texture contract, placeholder texture dimensions, asset manifest, and artist handoff specifications.
- Automated coverage for registration, menu wiring, inventory layout, serialization, generated data, packaging definition loading, supply reference validation, creative-tab population, content-snapshot compatibility, product metadata validation, packaging execution, blocked output behavior, and rollback safety.
- Documentation in `docs/PACKAGING_TABLE.md`.
- Documentation in `docs/RETAIL_PRODUCT_FRAMEWORK.md`.
- Documentation in `docs/PACKAGING_SUPPLIES.md`.
- Documentation in `docs/ASSET_MANIFEST.md`.
- Documentation in `docs/ASSET_SPECIFICATIONS.md`.
- Documentation in `docs/PACKAGING_TABLE_GUI_SPEC.md`.

Excluded work:

- Packaging recipes, product transformations, labels, quality changes, freshness, spoilage, order fulfillment, employees, commerce, storage rules, custom sounds, animations, final artwork, generated AI artwork, custom renderers, or dynamic product item creation.
- Migration of Grinder, Bandsaw, smoker, coolers, or any other workstation behavior.

Acceptance criteria:

- The Packaging Table is visible in the ButcherCraft creative tab, can be placed, and opens its processing GUI.
- All four workstation slots persist through save/load and drop safely when the block is removed.
- The table exposes item-handler inventory capability and executes packaging through the processing resolver and controller while remaining outside transformation definitions and transformation execution.
- Packaging definition datapacks load deterministically and malformed definitions produce structured validation errors.
- Packaging supply items are registered, localized, modeled with placeholder assets, and visible in the ButcherCraft creative tab.
- Packaging definitions can reference known supply item ids and reject malformed or unknown supply ids.
- Product, packaging, and transformation registries activate atomically.
- `package_retail` appears in the processing graph and is executed by the Packaging Table when required supplies are present.
- Required supplies are consumed only after successful completion, and blocked or failed packaging does not consume or duplicate items.
- Existing Grinder and Bandsaw behavior and datapack reload compatibility remain unchanged.
- Packaging assets use stable resource-pack replacement paths, placeholder textures are 16x16 or documented GUI-sized PNGs, generated model JSON references existing textures, and no placeholder asset is marked production approved.

Automated verification:

- `.\gradlew.bat --no-daemon clean`
- `.\gradlew.bat --no-daemon runData`
- `.\gradlew.bat --no-daemon test`
- `.\gradlew.bat --no-daemon build`
- `.\gradlew.bat --no-daemon runClient`
- `git diff --check`

Manual in-game verification:

- Recommended before upload: launch a client, create or load a development world, find the Packaging Table, Retail Ground Beef Test Product, and all six packaging supply items in the creative tab, place the table, inspect the block and item rendering from multiple facings, open the GUI, package Ground Beef Test Product with Foam Tray and Plastic Wrap Roll, verify Retail Ground Beef appears in the result slot, verify missing supplies and blocked output preserve input, and confirm existing Grinder and Bandsaw test flows still work.

Rollback considerations:

- The v0.8.0 feature is additive. Removing the Packaging Table registrations, supply item registrations, assets, menu/screen, retail packaging definitions, retail product definition, `package_retail` operation, packaging execution strategy, retail fixture item, asset manifest/specification docs, tests, and docs should restore the v0.7.0 feature surface without changing Grinder or Bandsaw behavior.

## Milestone 0.7.0: Beef Fabrication Expansion

Goal: add the first substantial multi-stage beef fabrication content chain using the existing datapack product, transformation, processing-definition, workstation, and atomic transaction architecture.

Included work:

- Sixteen additional bundled product definitions for Beef Hindquarter, Beef Round, Beef Sirloin, Beef Short Loin, Beef Flank, T-Bone Steak, Porterhouse Steak, Beef Strip Loin, Beef Tenderloin, Top Round, Bottom Round, Eye of Round, Sirloin Tip, Top Sirloin, Sirloin Steak, and Tri-Tip.
- Four bundled Bandsaw transformation definitions: `break_beef_hindquarter`, `cut_beef_short_loin`, `cut_beef_round`, and `cut_beef_sirloin`.
- Matching processing-operation definitions so the existing operation resolver can discover the new flows through `butchercraft:bandsaw`.
- Development fixture items, language entries, generated placeholder item models, creative-tab entries, and controlled product-to-item mappings for the new products.
- Regression tests for product loading, transformation loading, atomic content snapshots, graph edges, resolver behavior, runtime output order and quantities, and fixture item mappings.
- Documentation in `docs/BEEF_FABRICATION_EXPANSION.md`.

Excluded work:

- Transformation engine, registry, transaction, reload, workstation, menu, or screen redesign.
- Datapack-driven Minecraft item registration or a general product item factory.
- Full carcass fabrication, recipe-selection UI, dynamic cut catalogs, spoilage, quality expansion, packaging states, storage rules, employees, commerce, or MCDA behavior.
- Migration of smoker, packaging, cooler, or any other workstation.

Acceptance criteria:

- Product and transformation datapack resources load in deterministic order.
- The active content snapshot validates all new transformation product references against the product registry.
- The Bandsaw can resolve and complete the new beef fabrication operations through the existing capability-based path.
- Output ItemStacks are inserted in transformation order and preserve exact product ids, states, quantities, and no-duplication behavior.
- Grinder behavior and the existing forequarter Bandsaw proof remain compatible.

Automated verification:

- `.\gradlew.bat --no-daemon runData`
- `.\gradlew.bat --no-daemon test`
- `.\gradlew.bat --no-daemon build`
- `git diff --check`

Manual in-game verification:

- Recommended before upload: place the Bandsaw, process Beef Hindquarter, Beef Short Loin, Beef Round, and Beef Sirloin test products, then confirm ordered outputs and no partial-output behavior when slots are obstructed.

Rollback considerations:

- The v0.7.0 content is additive. Removing the new product and transformation resources, fixture items, and mappings should restore the v0.6.9 proof catalog without changing workstation machine behavior.

## Milestone 0.6.9: Datapack Product Loading And Content Snapshots

Goal: load product definitions from datapack JSON and activate product and transformation registries as one validated content snapshot.

Included work:

- Stable serialized product-definition schema with canonical serializer and deserializer contracts.
- Product JSON parser for `data/<namespace>/butchercraft/content/product/*.json`.
- Immutable candidate `ProductRegistry` assembly from product datapack content.
- Product validation for duplicate ids, missing ids or display names, unsupported schema versions, unknown categories, unknown units, malformed tags, malformed metadata, and malformed JSON.
- Candidate transformation loading after product loading succeeds.
- Transformation product-reference validation against the candidate product registry.
- Atomic active content snapshot replacement for product and transformation registries together.
- Bundled datapack JSON resources for the current Grinder and Bandsaw proof products.
- Regression tests for loader validation, deterministic ordering, atomic replacement, candidate-product references, and Grinder/Bandsaw compatibility.
- Documentation in `docs/DATAPACK_PRODUCTS.md`.

Excluded work:

- Dynamic Minecraft item registration from datapacks.
- Product-to-ItemStack mapping changes.
- Product schema migrations, datapack-driven category catalogs, expanded fabrication catalogs, public expansion APIs, or other workstation migrations.
- Changes to evaluator, executor, transaction engine, Grinder behavior, or Bandsaw behavior.

Acceptance criteria:

- Current product definitions load from bundled datapack JSON resources in deterministic order.
- Transformations only load after candidate products are valid.
- If either product or transformation loading fails, both previously active registries remain active.
- Grinder and Bandsaw use the same runtime behavior as v0.6.8.
- Minecraft and NeoForge code remains outside pure product and transformation domains.

Automated verification:

- `.\gradlew.bat --no-daemon test`
- `.\gradlew.bat --no-daemon build`
- `git diff --check`

Manual in-game verification:

- Recommended before upload: reload datapacks in a development world, then confirm Grinder trim-to-ground flows and Bandsaw forequarter fabrication still complete.

Rollback considerations:

- The active content snapshot can reset to bundled datapack resources. Java development item mappings remain unchanged, so rollback can remove product datapack loading without introducing dynamic item registration.

## Milestone 0.6.8: Datapack Transformation Loading

Goal: replace Java-defined transformation registrations with datapack-driven loading while preserving identical Grinder and Bandsaw runtime behavior.

Included work:

- Datapack JSON parser for the frozen transformation serialization field names.
- Conversion from JSON into `SerializedTransformationDefinition`.
- Reuse of `CanonicalTransformationDefinitionDeserializer` to construct `TransformationDefinition`.
- Immutable `TransformationRegistry` assembly from datapack content.
- Reload-safe active registry replacement after successful validation.
- Structured validation errors for malformed datapacks.
- Bundled datapack JSON resources for `grind_beef`, `grind_pork`, `grind_bison`, and `break_beef_forequarter`.
- Regression tests for successful bundled loading, duplicate ids, unknown products, unknown capabilities, unsupported schema versions, malformed definitions, resource coverage, and reload-safe replacement.
- Documentation in `docs/DATAPACK_TRANSFORMATIONS.md`.

Excluded work:

- Changes to `TransformationEvaluator`, `TransformationExecutor`, `TransformationTransaction`, or workstation behavior.
- Expanded fabrication catalogs, recipe-selection UI, product-to-item factories, datapack product-definition loading, schema migrations, or public expansion APIs.
- Migration of smoker, packaging, coolers, or other workstations.

Acceptance criteria:

- Existing Grinder and Bandsaw transformations load from datapack resources and produce the same registry ids, quantities, capabilities, durations, yields, and output order.
- Failed datapack reloads report structured errors and do not replace the previously active registry.
- Duplicate ids, unknown products, unknown capabilities, unsupported schema versions, and malformed definitions are rejected.
- Minecraft-specific reload code remains outside the pure transformation model.

Automated verification:

- `.\gradlew.bat --no-daemon test`
- `.\gradlew.bat --no-daemon build`
- `git diff --check`

Manual in-game verification:

- Recommended before upload: confirm Grinder trim-to-ground flows and Bandsaw forequarter fabrication still complete after a datapack reload in a development world.

Rollback considerations:

- The runtime registry service can reset to bundled datapack resources. Workstation strategies still resolve by operation id and can continue using the prior transformation definitions if datapack reload fails.

## Milestone 0.6.7: Bandsaw Transformation Migration

Goal: migrate only the Bandsaw to capability-based, registry-driven, atomic multi-output transformation execution while preserving existing Bandsaw gameplay and lifecycle behavior.

Included work:

- Built-in Bandsaw transformation capability coverage through `butchercraft:bandsaw`.
- Built-in `butchercraft:break_beef_forequarter` transformation definition with eight ordered outputs.
- Minimum pure product definitions for the existing Bandsaw proof product ids.
- Minecraft-side workstation inventory material-store bridge for transformation transaction validation.
- Controlled development product-to-ItemStack mapping coverage for the existing Bandsaw proof outputs.
- Bandsaw completion through `TransformationEvaluator`, `TransformationExecutor`, and `TransformationTransaction`.
- Regression tests for ordered completion, obstruction, missing transformation definitions, missing output mappings, save/load idempotence, material-store bridge behavior, registry definitions, and Grinder compatibility.
- Documentation in `docs/BANDSAW_TRANSFORMATION_MIGRATION.md`.

Excluded work:

- Migration of Grinder behavior beyond preserving its existing path.
- Migration of smoker, packaging, coolers, or any other workstation.
- Datapack transformation loading, JSON discovery, reload listeners, complete carcass fabrication, new GUI design, spoilage, packaging, power, employees, commerce, or public expansion APIs.

Acceptance criteria:

- Bandsaw operation lookup still resolves through existing processing definitions and workstation capability data.
- Bandsaw transformation execution queries the immutable transformation registry by resolved operation id.
- If any output cannot fit or cannot be mapped to a development ItemStack, input remains in place and no partial output is inserted.
- Existing paired-block placement/removal, obstruction behavior, processing duration, save/load behavior, menus, and player interaction remain unchanged.
- Pure transformation and product-definition packages remain free of Minecraft and NeoForge imports.

Automated verification:

- `.\gradlew.bat --no-daemon test`
- `.\gradlew.bat --no-daemon build`
- `git diff --check`

Manual in-game verification:

- Recommended before upload: place and break both halves of the Bandsaw, insert Beef Forequarter Test Product, confirm the eight ordered outputs after processing, and confirm blocked output recovery in a development world.

Rollback considerations:

- The migration is isolated to the Bandsaw block entity execution strategy, built-in transformation/product registry entries, and workstation bridge validation. Other workstations remain on their previous strategies.

## Milestone 0.6.6: Atomic Multi-Output Transformations

Goal: extend the pure Java transformation engine with deterministic, atomic multi-output transactions while preserving existing Grinder behavior.

Included work:

- `TransformationMaterialStore` contract for pure material storage.
- `InMemoryTransformationMaterialStore` with ordered quantities, optional per-material capacity limits, material-slot capacity, snapshots, extraction, and insertion.
- `TransformationTransaction` and `TransformationTransactionState` for prepare, commit, rejection, and rollback.
- Executor overload that commits a transformation against input and output material stores.
- Multi-output execution support for any number of ordered outputs declared by `TransformationDefinition`.
- Regression tests for output ordering, capacity failures, rollback, commit-time revalidation, snapshot restore, and built-in Grinder one-output compatibility.
- Documentation in `docs/MULTI_OUTPUT_TRANSFORMATIONS.md`.

Excluded work:

- Bandsaw migration to transformation execution.
- Datapack loading, resource reload listeners, JSON resource discovery, or transformation file parsing.
- Minecraft inventory mutation, ItemStack conversion, menus, screens, or product-to-item mapping changes.
- New product definitions, new gameplay content, or public expansion APIs.

Acceptance criteria:

- Transformation transactions validate all required input extraction and output insertion before committing.
- If any output cannot be inserted, no input is consumed and no partial output remains.
- Output order follows `TransformationDefinition.outputs()`.
- Existing built-in Grinder transformations still execute as one-input/one-output transformations.
- Transformation sources remain free of Minecraft and NeoForge imports.

Automated verification:

- `gradlew test`
- `gradlew build`
- `git diff --check`

Manual in-game verification:

- None required. Bandsaw and live workstation migration are intentionally deferred.

Rollback considerations:

- The transaction model is additive to the transformation package. The live Grinder bridge can continue using the side-effect-free executor path while future migrations adopt the transaction commit path deliberately.

## Milestone 0.6.5: Transformation Serialization Foundation

Goal: introduce a pure Java serialization layer for `TransformationDefinition` that freezes the stable external schema contract before datapack loading exists.

Included work:

- Serializer and deserializer interfaces for transformation definitions.
- Canonical pure Java serialized transformation representation.
- Stable external field-name constants for every current transformation schema field.
- `TransformationSchemaVersion` abstraction.
- Future migration interface with no implemented migrations.
- Round-trip serialization of id, display name, schema version, required capability, inputs, outputs, duration, yield, and metadata.
- Unit tests for field names, round trips, built-in Grinder transformations, validation, defensive copying, null handling, and unsupported schema versions.
- Documentation in `docs/TRANSFORMATION_SERIALIZATION.md`.

Excluded work:

- Datapack loading, resource reload listeners, JSON resource discovery, codecs tied to Minecraft registries, or automatic schema migrations.
- Bandsaw, smoker, packaging, cooler, menu, screen, item data component, product-to-item mapping, or workstation migrations.
- Embedding product definitions inside transformation definitions.

Acceptance criteria:

- Serialization code remains pure Java and free of Minecraft and NeoForge imports.
- All current `TransformationDefinition` fields are represented by the canonical serialized form.
- Built-in Grinder transformations round-trip through the serializer and deserializer unchanged.
- Unsupported schema versions fail clearly until migrations are deliberately implemented.

Automated verification:

- `gradlew test`
- `gradlew build`
- `git diff --check`

Manual in-game verification:

- None required. This milestone is a pure domain serialization foundation and does not change live workstation behavior.

Rollback considerations:

- The serialization package is additive. Rollback can remove the canonical serialization layer while leaving the current transformation registry, product registry, and Grinder runtime path intact.

## Milestone 0.6.4: Product Definition Foundation

Goal: introduce a pure Java canonical product definition system so transformation product ids can be validated against authoritative immutable product data.

Included work:

- Immutable `ProductDefinition` with stable id, display name, schema version, typed category, default quantity unit, tags, and typed metadata.
- Fluent product definition builder.
- Immutable `ProductRegistry` and `ProductRegistryBuilder`.
- Ordered lookup APIs for id, category, and tag queries.
- Built-in product definitions for the six current Grinder products only.
- Separate deterministic transformation product-reference validation against a product registry.
- Unit tests for schema validation, registry behavior, dependency boundaries, built-in Grinder products, and transformation reference validation.
- Documentation in `docs/PRODUCT_DEFINITION_SYSTEM.md`.

Excluded work:

- Serialization, codecs, JSON, datapack loading, reload listeners, product-to-item mapping, spoilage, quality expansion, packaging states, storage rules, or other workstation migrations.
- Embedding `ProductDefinition` instances inside `TransformationDefinition`.
- Replacing existing ItemStack product data or datapack-backed processing definitions.

Acceptance criteria:

- Product definition and transformation domains remain free of Minecraft and NeoForge imports.
- Existing Grinder behavior remains unchanged.
- Built-in Grinder transformation input and output product ids validate against the built-in product registry.
- Transformations can still be constructed before a product registry exists.

Automated verification:

- `gradlew test`
- `gradlew build`
- `git diff --check`

Manual in-game verification:

- Optional before release: insert Beef Trim, Pork Trim, and Bison Trim test products into the Grinder and confirm each still produces the matching ground product after the normal processing duration.

Rollback considerations:

- The product definition package is additive. The Grinder runtime path does not depend on it yet, so rollback can remove the validation foundation without changing workstation behavior.

## Milestone 0.6.3: Canonical Transformation Definition Schema

Goal: formalize `TransformationDefinition` as the canonical immutable schema for all future transformations before serialization or datapack loading exists.

Included work:

- Canonical transformation definition fields for id, display name, schema version, required capability, inputs, outputs, duration, yield, and metadata.
- Fluent builder API for complete definition construction.
- Construction-time validation for incomplete, duplicate, inconsistent, or invalid transformations.
- Immutable defensive copying for inputs, outputs, and metadata.
- Legacy constructor compatibility for existing Grinder transformation definitions and adapter tests.
- Unit tests for validation, equality, immutability, canonical defaults, and builder behavior.

Excluded work:

- Serialization, codecs, JSON files, datapack loading, migration systems, or public expansion APIs.
- Bandsaw, smoker, packaging, cooler, menu, screen, data-component, or product item migration.
- New gameplay systems or new transformation authoring formats.

Acceptance criteria:

- Transformation schema code remains pure Java with no Minecraft or NeoForge imports.
- Existing Grinder transformation behavior remains unchanged.
- Invalid definitions fail during construction, before registry registration or execution.
- Registry, evaluator, executor, and compatibility adapter continue to work with the canonical schema.

Automated verification:

- `gradlew test`
- `gradlew build`

Manual in-game verification:

- Optional before release: insert Beef Trim, Pork Trim, and Bison Trim test products into the Grinder and confirm each still produces the matching ground product after the normal processing duration.

Rollback considerations:

- The legacy constructor bridge allows the schema expansion to be rolled back independently from the v0.6.2 registry if needed.

## Milestone 0.6.2: Transformation Registry

Goal: make an immutable pure Java transformation registry the authoritative source of transformation definitions used by the Grinder transformation execution bridge.

Included work:

- `TransformationRegistryBuilder` for registering definitions before freeze.
- Immutable `TransformationRegistry` with `contains`, `find`, `size`, `stream`, and `findByCapability`.
- Duplicate id and null registration rejection.
- Insertion-order preservation for registry streams and capability queries.
- Built-in Grinder transformations registered through the registry.
- Grinder transformation execution now resolves the already selected operation id through the registry before evaluation and execution.
- Regression tests for registry invariants and Grinder registry lookup.

Excluded work:

- Datapack loading for transformation definitions.
- Bandsaw, smoker, packaging, cooler, menu, screen, data-component, or product item migration.
- New gameplay systems or new transformation authoring formats.

Acceptance criteria:

- Registry code remains pure Java with no Minecraft or NeoForge imports.
- Existing Grinder behavior remains unchanged for Beef, Pork, and Bison Trim fixtures.
- Missing registry definitions fail safely instead of consuming input or creating output.
- Existing `ProcessingOperation` compatibility adapter remains available.

Automated verification:

- `gradlew test`
- `gradlew build`

Manual in-game verification:

- Optional before release: insert Beef Trim, Pork Trim, and Bison Trim test products into the Grinder and confirm each still produces the matching ground product after the normal processing duration.

Rollback considerations:

- The Grinder can be returned to the legacy strategy or the v0.6.1 registry-free transformation bridge if the registry lookup path proves too early.

## Milestone 0.6.1: Grinder Transformation Execution Bridge

Goal: connect the workstation framework to the pure Java transformation engine by migrating only the Grinder to capability-based transformation execution.

Included work:

- Pure Java `TransformationExecutor` that executes only previously accepted matching evaluations.
- Pure Java transformation `WorkstationCapability` model for workstation capability advertisement.
- Minecraft-facing `WorkstationCapability` adapter into the pure transformation capability model.
- Workstation execution strategies so migrated and un-migrated machines can share the controller without changing each machine at once.
- Grinder-only opt-in to transformation execution.
- Regression tests proving Grinder processing still produces the same outputs and rejects transformation execution when the workstation resolves by category but does not advertise `butchercraft:grinding`.

Excluded work:

- Bandsaw, smoker, packaging, cooler, development workstation, menu, screen, datapack registry, or product component migration.
- Datapack loading for transformation definitions.
- New gameplay systems, recipes, product items, or machine behaviors.

Acceptance criteria:

- Grinder behavior remains data-driven through product definitions, processing-operation definitions, the processing graph, and `butchercraft:grinding`.
- Bandsaw and other workstations continue using the legacy execution strategy.
- Existing `ProcessingOperation` compatibility remains available through `ProcessingOperationTransformationAdapter`.
- Transformation code remains free of Minecraft and NeoForge imports.

Automated verification:

- `gradlew test`
- `gradlew build`

Manual in-game verification:

- Optional before release: insert Beef Trim, Pork Trim, and Bison Trim test products into the Grinder and confirm each still produces the matching ground product after the normal processing duration.

Rollback considerations:

- Reverting the Grinder to the default workstation execution strategy returns it to the legacy transaction path while preserving the transformation package.

## Milestone 0.6.0: Material Transformation Engine Foundation

Goal: create a backwards-compatible pure Java foundation for generic material transformations without replacing the existing processing framework.

Included work:

- `com.butchercraft.transformation` domain records for transformation ids, material amounts, inputs, outputs, output classifications, definitions, contexts, evaluations, and deterministic evaluation.
- Exact quantity handling through existing engine quantity types.
- Optional workstation capability matching through pure engine identifiers.
- A compatibility adapter from existing `ProcessingOperation` values into `TransformationDefinition` values for concrete input amounts.
- Unit tests for validation, defensive copying, deterministic rejection, capability matching, valid acceptance, and the adapter.
- Documentation in `docs/MATERIAL_TRANSFORMATION_ENGINE.md`.

Excluded work:

- Datapack registry migration.
- Grinder, Bandsaw, workstation block entity, menu, screen, or data component changes.
- Inventory mutation, transaction commits, or ItemStack operations.
- Quality, lineage, operator skills, energy, temperature, probabilistic yield, spoilage, organizations, optional ingredients, tags, substitutes, catalysts, or public expansion APIs.

Acceptance criteria:

- Existing `ProcessingOperation` APIs and tests continue to pass.
- Transformation sources have no Minecraft or NeoForge imports.
- Evaluation is side-effect free and returns stable reason codes.
- The transformation adapter proves current processing definitions can be represented by the new foundation when supplied a concrete input amount.

Automated verification:

- `gradlew test`
- `gradlew build`

Manual in-game verification:

- None required. This milestone is pure domain foundation work and does not change live workstation behavior.

Rollback considerations:

- The new package is additive and isolated.
- Current workstations continue using the existing processing resolver/controller path.

## Milestone 0: Repository Foundation

Goal: create a clean NeoForge 1.21.1 Java 21 project foundation without gameplay systems.

Included work:

- Initialize NeoForge MDK-style project.
- Set mod id, package, display name, license, and Gradle wrapper.
- Add empty registry classes and mod entry point only if needed by the MDK.
- Add central registration for one harmless development test item and the ButcherCraft creative tab.
- Add safe diagnostic command for foundation verification.
- Add common config foundation, logging, placeholder asset, language entry, and data-generation setup.
- Add documentation files from this planning pass.
- Add placeholder asset policy and data-generation skeleton only if the project template expects it.

Excluded work:

- Gameplay blocks, items, entities, employees, orders, inspections, and machines.
- Custom data persistence beyond template setup.

Dependencies:

- Owner approval for mod id and package.
- NeoForge 1.21.1 MDK selection.

Acceptance criteria:

- Project imports in an IDE.
- Mod starts in a development client with no gameplay content beyond template-safe basics.
- Dedicated server start does not load client-only classes.
- The development test item is registered under `butchercraft` and has no gameplay powers.
- The diagnostic reports project identity and registration state without modifying the world.

Automated verification:

- `gradlew build`
- `gradlew compileJava`
- `gradlew test`, if tests are configured
- `gradlew runData`, if data generation skeleton exists

Manual in-game verification:

- Launch development client.
- Confirm the mod appears with the correct name and no startup errors.

Rollback considerations:

- Revert only template and metadata files from this milestone.
- Keep planning documents unless owner requests replacement.

## Milestone 1A: Product Component and Manual Station Prototype

Goal: prove one product item, its persistent components, and one manual processing station without employees, orders, refrigeration, or MCDA.

Included work:

- One basic meat product.
- Product, quality, freshness, temperature, and packaging component shapes.
- One manual processing station.
- Placeholder assets where final art is unavailable.
- Save/load verification for product item state.

Excluded work:

- Grinder.
- Packaging station.
- Refrigerated storage.
- Customer orders.
- Employees.
- Cleanliness simulation beyond a local placeholder field.
- MCDA inspections.

Dependencies:

- Milestone 0.
- Approved first product choice.

Acceptance criteria:

- Player can obtain and process one basic product through one manual station.
- Product item data persists across save/load.
- Different product states do not merge unless component rules allow it.

Automated verification:

- `gradlew build`
- `gradlew test`, if configured
- Product component persistence tests if test infrastructure is available

Manual in-game verification:

- Start a new world.
- Obtain the product and manual station.
- Process the product.
- Save, quit, reload, and confirm item data remains.

Rollback considerations:

- Component codecs must be kept or migrated after public saves.

## Milestone 1B: ButcherCraft Engine Foundation

Goal: create a Minecraft-independent processing engine and project rules before adding visible gameplay systems.

Included work:

- `PROJECT_RULES.md` with non-negotiable development rules.
- Pure Java engine package under `com.butchercraft.engine`.
- Product, product quality, product quantity, modifier system, operation result, and processing transaction concepts.
- Unit tests for engine invariants, arithmetic, modifier ordering, transaction state transitions, and dependency boundaries.
- Engine documentation in `docs/BUTCHERCRAFT_ENGINE.md`.

Excluded work:

- Minecraft items, blocks, block entities, menus, screens, networking, or data components.
- Employees, machines, grinder, packaging station, refrigeration, cleanliness, MCDA, customers, orders, business accounts, and expansion APIs.
- Datapack recipes, registries, artwork, sounds, or in-game commands.

Dependencies:

- Milestone 0.
- Approved project identity.

Acceptance criteria:

- The engine has no `net.minecraft` or `net.neoforged` imports.
- Product quantity uses exact arithmetic and prevents negative, underflow, overflow, and incompatible-unit behavior.
- Product quality uses a documented 0-1000 score and five deterministic grades.
- Modifiers apply in deterministic, inspectable order.
- Operation results are explicit success/failure values rather than booleans or null.
- Processing transactions validate, prepare, commit once, cancel before commit, reject invalid input, and preserve input on failure.
- No excluded gameplay system is introduced.

Automated verification:

- `gradlew compileJava`
- `gradlew test`
- `gradlew build`
- External owner verification is required if the Codex sandbox blocks NeoForge artifact extraction before compilation.

Manual in-game verification:

- None. This milestone is domain-only and should not create visible gameplay.

Rollback considerations:

- Engine code is isolated under `com.butchercraft.engine`.
- The previous grinder and packaging station work formerly listed as Milestone 1B is deferred until the owner schedules the next visible station milestone.

## Milestone 1C: ButcherCraft Processing Framework

Goal: extend the Minecraft-independent engine with operation definitions, processing contexts, validation rules, and deterministic proposal evaluation before adding visible gameplay systems.

Included work:

- Immutable `ProcessingOperation` transformation definitions.
- Immutable `ProcessingContext` inputs for cleanliness, operator skill, equipment condition, and explicit modifiers.
- `ValidationRule` contract and reusable validation rules.
- Deterministic `ProcessingEvaluator`.
- Exact yield modifiers using additive basis points and documented half-up rounding.
- Beef Trim to Ground Beef test fixture only.
- Transaction integration tests proving prepare, commit, cancel, rejection, and failure behavior.
- Framework documentation in `docs/PROCESSING_FRAMEWORK.md`.

Excluded work:

- Minecraft items, item data components, blocks, block entities, menus, screens, networking, recipe JSON, datapack loading, grinder block, grinder GUI, machine power, employees, villager AI, player skill, refrigeration, product temperature, freshness, facility cleanliness simulation, cleaning tools, MCDA, customers, business finances, sounds, artwork, animation, and public expansion API commitments.

Dependencies:

- Milestone 1B.

Acceptance criteria:

- `ProcessingOperation`, `ProcessingContext`, and `ValidationRule` exist and are immutable or side-effect free as appropriate.
- Validation returns inspectable accept, reject, and warning results.
- Validation ordering is deterministic and stops at the first rejection.
- Yield uses exact arithmetic, additive basis-point modifiers, overflow protection, and documented rounding.
- Evaluation prepares proposed output without committing.
- Existing transaction guarantees remain intact.
- Beef Trim to Ground Beef is proven through tests as a fixture only.
- Engine packages have no `net.minecraft` or `net.neoforged` imports.
- No excluded gameplay feature is introduced.

Automated verification:

- `gradlew compileJava`
- `gradlew test`
- `gradlew build`
- External owner verification is required if the Codex sandbox blocks NeoForge artifact extraction before compilation.

Manual in-game verification:

- None. This milestone is domain-only and should not create visible gameplay.

Rollback considerations:

- Framework code is isolated under `com.butchercraft.engine`.
- The previous simple refrigerated storage work formerly listed as Milestone 1C is deferred until the owner schedules the next visible storage milestone.

## Milestone 1D: Minecraft Product Data Integration

Goal: prove that Minecraft ItemStacks can safely store, retrieve, copy, serialize, display, and round-trip a ButcherCraft engine product snapshot through a NeoForge data component.

Included work:

- Immutable `ProductStackData` component value.
- Registered `butchercraft:product_data` item data component.
- Engine `Product` to `ProductStackData` conversion.
- `ProductStackData` to engine `Product` conversion.
- Focused ItemStack adapter with explicit success and failure results.
- Development-only Beef Trim Test Product and Ground Beef Test Product fixtures.
- Creative tab fixture stacks with default product data.
- Product tooltip display for fixture stacks.
- Diagnostic checks for component registration, fixture registration, and product round-trip preservation.
- Codec, StreamCodec, adapter, ItemStack copy, merge-safety, dependency-boundary, and asset tests.
- Documentation in `docs/PRODUCT_DATA_INTEGRATION.md`.

Excluded work:

- Grinder block or behavior.
- Processing station.
- Blocks, block entities, menus, screens, recipes, or datapack processing operations.
- Employees, villagers, refrigeration, temperature, freshness, packaging, cleanliness, MCDA, customers, business systems, sounds, final artwork, or public expansion APIs.

Dependencies:

- Milestone 1B.
- Milestone 1C.
- Existing NeoForge foundation registration and data-generation setup.

Acceptance criteria:

- `ProductStackData` is immutable, validated, and rejects malformed decoded data.
- `butchercraft:product_data` is registered with persistent and network codecs.
- Engine product round-trips through the ItemStack component representation.
- Quantity and quality are preserved exactly.
- Product-bearing fixture stacks have maximum stack size `1`.
- Creative-tab fixture stacks receive valid independent product data.
- Tooltips display product identifier, source, state, quantity, grade, and advanced quality score.
- The diagnostic reports successful registration and round-trip checks without mutating world or inventory.
- Engine packages still have no Minecraft or NeoForge imports.
- No excluded gameplay system is introduced.

Automated verification:

- `gradlew compileJava`
- `gradlew test`
- `gradlew runData`
- `gradlew build`
- External owner verification is required if the Codex sandbox blocks NeoForge artifact extraction before compilation.

Manual in-game verification:

- Launch development client.
- Confirm both product test items appear in the ButcherCraft creative tab.
- Confirm both fixture items show product tooltip data.
- Copy fixture stacks and confirm product data remains visible.
- Run `/butchercraft diagnostic` and confirm product data registration and round-trip checks are true.
- Confirm no missing texture appears beyond the documented reused placeholder texture.
- Launch a dedicated server and confirm no client-only class-loading error is introduced.

Rollback considerations:

- Component field names should be treated as save-relevant once public saves exist.
- Product stackability remains locked to one until merge rules are explicitly designed.
- Customer order and business-summary work formerly listed here is deferred until packaged product output and order persistence are scheduled.

## Milestone 1E: Basic Cleanliness and MCDA Write-Up

Goal: prove basic cleanliness feedback and a simple MCDA write-up without full escalation.

Included work:

- Basic continuous cleanliness.
- A basic MCDA inspection that can issue a warning or write-up.
- Cleanliness and inspection persistence ownership.

Excluded work:

- Fines.
- Shutdowns.
- Reinspections.
- Complex inspection schedules.
- Employee cleaning jobs.

Dependencies:

- Milestone 1A.
- Approved default config strictness.

Acceptance criteria:

- Cleanliness affects product result or inspection result in a visible way.
- MCDA can issue at least a warning or write-up based on a basic violation.
- Inspection state persists and is not silently discarded.

Automated verification:

- `gradlew build`
- Cleanliness aggregation and inspection write-up tests if available

Manual in-game verification:

- Create a cleanliness problem and trigger a basic MCDA inspection.
- Correct the issue enough for the write-up path to be understandable.

Rollback considerations:

- MCDA write-ups can remain advisory until escalation is separately implemented.

## Milestone 1F: One Basic Employee Job

Goal: prove one villager-based employee can complete one already-working task through the work-order path.

Included work:

- One basic employee job.
- Employee attachment for role and skill seed data.
- Work-order reservation token for the supported job.

Excluded work:

- Multiple employee roles.
- Wages, morale, schedules, or payroll.
- Advanced pathfinding management.
- Employee-driven MCDA or refrigeration behavior.

Dependencies:

- Milestone 1B.
- Milestone 1C.
- Future station-chain milestone for a useful employee task.
- Work-order reservation boundary from the technical architecture.

Acceptance criteria:

- One employee can complete one useful job through the work-order path.
- Employee skill/assignment data persists across unload/reload.
- Reservation release or restore works after station unload, failure, cancellation, or save/load.

Automated verification:

- `gradlew build`
- Employee attachment and work-order reservation tests if available

Manual in-game verification:

- Assign the basic employee job and observe completion.
- Reload the world and confirm assignment/skill state remains understandable.

Rollback considerations:

- Employee job behavior should be gated so the manual path remains playable if AI is unstable.

## Milestone 2A: Data-Driven Product and Processing Definitions

Goal: create server-authoritative datapack definitions and a read-only processing graph before adding visible processing stations.

Included work:

- Custom datapack registries for species, processing profiles, products, and processing operations.
- Immutable codec-backed definitions and generated built-in data for beef, red meat, beef trim, ground beef, and grind beef.
- Resolver validation for missing references, species mismatch, profile incompatibility, state mismatch, self-loops, and forbidden zero-output operations.
- Conversion of resolved operation definitions into the existing Minecraft-independent `ProcessingOperation`.
- Read-only processing graph lookup and validation report.
- ProductStackData-to-definition validation bridge.
- Diagnostic command checks for registries, built-in definitions, graph validation, and the Beef Trim to Ground Beef edge.
- Documentation in `docs/PRODUCT_AND_PROCESSING_DEFINITIONS.md`.

Excluded work:

- Machines, blocks, block entities, menus, screens, player-triggered processing, employees, refrigeration, cleanliness, MCDA, customers, business systems, poultry content, final assets, sounds, animations, and public stable expansion API guarantees.

Acceptance criteria:

- Species, processing profiles, products, and operations are loaded through custom datapack registries.
- Built-in beef definitions are generated deterministically.
- The processing graph contains `butchercraft:beef_trim -> butchercraft:ground_beef` through `butchercraft:grind_beef`.
- Invalid references and incompatible profiles are reported explicitly.
- Engine packages remain Minecraft-independent.
- Tests prove a future poultry processing profile is possible without hardcoded species workflow checks.

Automated verification:

- `gradlew clean`
- `gradlew compileJava`
- `gradlew compileTestJava`
- `gradlew test`
- `gradlew runData`
- `gradlew build`

Manual in-game verification:

- Launch development client or server.
- Run `/butchercraft diagnostic`.
- Confirm all four definition registries are available.
- Confirm beef, red meat, beef trim, ground beef, and grind beef resolve.
- Confirm the initial graph validates and the Beef Trim to Ground Beef edge is reported.
- Confirm existing product test item tooltips still display valid product data.

Rollback considerations:

- Registry ids and JSON field names become save/datapack-relevant once worlds or external packs depend on them.
- The beef prototype values are test balance only and can be revised before public content stabilization.

## Milestone 2B: Processing Workstation Framework

Goal: create a reusable server-authoritative workstation framework and one development-only workstation block that proves Beef Trim Test Product can be processed into Ground Beef Test Product through the existing definition graph and engine transaction.

Included work:

- Workstation capability, state, and failure models.
- One-input, one-output `WorkstationInventory`.
- `WorkstationOperationResolver` that consumes loaded definitions, `ProcessingGraph`, and `ProductStackData`.
- `WorkstationProcessingController` that reserves input, tracks ticks, commits exactly once, and creates output through the existing engine transaction.
- Development Processing Workstation block, block entity, block item, temporary menu and client screen, item-handler capability, placeholder resources, loot table, and language entries.
- Diagnostic checks for workstation registration, resolver behavior, duration conversion, prototype context validation, and temporary output mapping.
- Tests for state transitions, duration conversion, resolver behavior, controller lifecycle, inventory rules, registration/assets, and dependency boundaries.
- Documentation in `docs/WORKSTATION_FRAMEWORK.md`.

Excluded work:

- Final grinder block or model.
- Multiple machine types.
- Player recipe-selection GUI.
- Power, fuel, employees, refrigeration, temperature, freshness, cleanliness gameplay, maintenance gameplay, MCDA, customers, commerce, sounds, animations, complex rendering, poultry content, or public expansion API guarantees.

Acceptance criteria:

- Generic workstation code does not hardcode beef, grind beef, or poultry species switches.
- Automatic operation selection occurs only when exactly one compatible operation exists.
- `3000` milliseconds converts to `60` ticks.
- Active input cannot be extracted while processing.
- Output insertion is blocked.
- Completion consumes input and creates output once.
- Output obstruction blocks completion without deleting input.
- Block removal drops recoverable input and completed output.
- Save/load state stores inventory, selected operation, progress, failure code, reserved input snapshot, and completion flag.
- Engine packages remain Minecraft-independent.

Automated verification:

- `gradlew clean`
- `gradlew compileJava`
- `gradlew compileTestJava`
- `gradlew test`
- `gradlew runData`
- `gradlew build`
- External owner verification is required if the Codex sandbox blocks NeoForge artifact creation before source compilation.

Manual in-game verification:

- Launch development client.
- Confirm Development Processing Workstation appears in the ButcherCraft creative tab.
- Place the workstation and open the temporary menu.
- Insert Beef Trim Test Product.
- Confirm processing starts and completes after about three seconds.
- Confirm Ground Beef Test Product appears in output with `900 gram` and adjusted quality.
- Confirm Ground Beef Test Product and vanilla items are rejected as processing inputs.
- Confirm output obstruction, block break, and save/reload preserve product safely.
- Run `/butchercraft diagnostic` and confirm workstation checks report true.
- Launch a dedicated server and confirm no client-only class-loading error.

Rollback considerations:

- The development block and explicit product-item mapping are temporary fixture content.
- The state and persistence fields are save-relevant once public worlds use the block.
- Future final machine content should consume the framework rather than duplicating transaction logic.

## Milestone 2D: Data-Driven Multi-Species Grinding

Goal: prove the Grinder is generic by adding pork and bison red-meat grinding flows through definitions and fixture data without changing Grinder behavior.

Included work:

- Built-in species definitions for `butchercraft:pork` and `butchercraft:bison`, both using `butchercraft:red_meat`.
- Product definitions for pork trim, ground pork, bison trim, and ground bison.
- Processing-operation definitions for `butchercraft:grind_pork` and `butchercraft:grind_bison`, both declaring `butchercraft:grinding`.
- Development-only pork and bison product fixture items with placeholder models and tooltip-ready product data.
- Diagnostic checks, tests, and docs proving beef, pork, and bison use the same graph/resolver/controller path.

Excluded work:

- Full species catalogs, animal entities, regulatory rules, final product items, final artwork, recipe-selection UI, employees, refrigeration, commerce, or new Grinder logic.

Acceptance criteria:

- Existing Beef Trim to Ground Beef grinding still works.
- Pork Trim resolves to Grind Pork and produces Ground Pork.
- Bison Trim resolves to Grind Bison and produces Ground Bison.
- Grinder classes and generic workstation framework contain no species-specific branches for beef, pork, bison, or operation ids.
- Product source ids can pass through the engine without adding a bison engine enum value.
- Generated definition JSON and language/model resources are deterministic.

Automated verification:

- `gradlew clean`
- `gradlew compileJava`
- `gradlew compileTestJava`
- `gradlew test`
- `gradlew runData`
- `gradlew build`

Manual in-game verification:

- Launch development client.
- Confirm Beef Trim, Pork Trim, Bison Trim, Ground Beef, Ground Pork, and Ground Bison test products appear in the ButcherCraft creative tab.
- Insert Beef Trim, Pork Trim, and Bison Trim test products into the Grinder and confirm each produces the matching ground test product after about three seconds.
- Run `/butchercraft diagnostic` and confirm the multi-species definition, graph, Grinder resolution, and output-mapping checks report true.
- Launch a dedicated server and confirm no client-only class-loading error.

Rollback considerations:

- Pork and bison definitions are prototype red-meat proof data and can be removed before public content stabilization if needed.
- Product fixture item ids and generated definition ids become datapack/save relevant once public worlds depend on them.

## Milestone 2E: Industrial Bandsaw and Multi-Output Fabrication

Goal: prove processing operations can create an ordered collection of outputs by adding a permanent Bandsaw machine and a data-driven Beef Forequarter fabrication flow.

Included work:

- Engine, result, transaction, definition, resolver, and processing graph support for immutable ordered output collections.
- Backward-compatible single-output operations represented as one-element output lists so Grinder behavior remains unchanged.
- Built-in product definition for `butchercraft:beef_forequarter` plus beef chuck, rib, packer brisket, plate, shank, fat, and bone output definitions.
- Built-in processing-operation definition for `butchercraft:break_beef_forequarter`, declaring `butchercraft:bandsaw`.
- Deterministic integer largest-remainder allocation for multi-output yields, with ties resolved by output order.
- Permanent two-block-tall Bandsaw block, upper forwarding block, block entity, menu, screen, registration, placeholder assets, loot, language, diagnostics, and tests.
- Development-only fixture items and output mapping for the new beef fabrication products.

Excluded work:

- Animal entities, carcass entities, full cut catalogs, final fabrication balance, recipe-selection UI, power, sounds, animations, cleanliness gameplay, employees, refrigeration, commerce, regulatory rules, final artwork, or public expansion API guarantees.

Acceptance criteria:

- Existing Beef, Pork, and Bison Trim to matching ground-product Grinder flows still work through the same definitions and resolver path.
- `butchercraft:beef_forequarter` resolves to `butchercraft:break_beef_forequarter` only on the `butchercraft:bandsaw` capability.
- A `100000 gram` Beef Forequarter produces ordered outputs: Chuck, Rib, Packer Brisket, Plate, Shank, Trim, Fat, and Bone with 95% total yield.
- Bandsaw lower block owns the block entity, inventory, processing state, menu provider, persistence, ticking, and drops.
- Bandsaw upper block has no block entity, forwards interaction to the lower block, and paired break/removal drops recoverable inventory exactly once.
- Generic workstation and Grinder code contain no product, species, or operation-id branches for the Bandsaw fabrication flow.
- Generated definition JSON and placeholder assets are deterministic.

Automated verification:

- `gradlew clean`
- `gradlew compileJava`
- `gradlew compileTestJava`
- `gradlew test`
- `gradlew runData`
- `gradlew build`

Manual in-game verification:

- Launch development client.
- Confirm the Bandsaw and new beef fabrication test products appear in the ButcherCraft creative tab.
- Place the Bandsaw facing each horizontal direction and confirm the upper half is placed, synchronized, and removed with the lower half.
- Confirm placement fails when the upper space is obstructed.
- Insert Beef Forequarter Test Product and confirm the eight ordered outputs appear after processing.
- Break the upper and lower halves in separate checks and confirm recoverable input or completed outputs drop once.
- Run `/butchercraft diagnostic` and confirm the Bandsaw, graph, resolver, and output-mapping checks report true.
- Launch a dedicated server and confirm no client-only class-loading error.

Rollback considerations:

- The Bandsaw ids are permanent machine ids, while placeholder art and prototype beef fabrication balance can still change before public content stabilization.
- Product fixture item ids and generated definition ids become datapack/save relevant once public worlds depend on them.
- Multi-output operation schema should remain backward compatible with one-element output lists for existing Grinder definitions.

## Milestone 2: Persistence and Data Hardening

Goal: make the vertical slice reliable across saves, reloads, and basic content changes.

Included work:

- Versioned saved-data records for business, orders, facilities, and inspections.
- Product component migration plan.
- Missing product or expansion id handling.
- Data-generation coverage for all vertical-slice assets.
- Dedicated server smoke testing.

Excluded work:

- New production chains.
- Advanced commerce.
- Detailed refrigeration.

Dependencies:

- Milestone 1A through Milestone 1F.

Acceptance criteria:

- Existing vertical-slice saves reload cleanly after internal refactors.
- Missing or unknown product ids do not crash the world.
- Saved data is not silently discarded.
- Dedicated server launch path is clean.

Automated verification:

- `gradlew build`
- `gradlew runData`
- `gradlew test`, if configured
- Save/load GameTests or scripted manual fixture tests where practical

Manual in-game verification:

- Create a facility and order.
- Save, quit, reload, and complete the order.
- Break and replace stations.
- Confirm MCDA and cleanliness state remain understandable.

Rollback considerations:

- Keep old data readers until a release boundary permits migration removal.
- Avoid changing registry names after this milestone without explicit migration.

## Milestone 3: Facility and Work-Order Expansion

Goal: make small facilities feel manageable with more than one active task.

Included work:

- Facility controller or registration flow.
- Work-order board improvements.
- Employee reservation handling.
- Multiple station queues.
- Better cleanliness summaries.
- Basic facility-level inspection report.

Excluded work:

- Detailed finances.
- Full refrigeration room engineering.
- Large employee teams.

Dependencies:

- Milestone 2.

Acceptance criteria:

- Facility summary identifies stations, active orders, cleanliness state, and basic cold storage.
- Employees do not claim the same work at the same time.
- Player can prioritize or cancel work orders.
- Inspection report points to understandable facility issues.

Automated verification:

- `gradlew build`
- Work-order state transition tests.
- Facility summary tests.

Manual in-game verification:

- Build a facility with multiple stations.
- Queue multiple orders.
- Assign one employee and confirm work reservation behavior.
- Trigger an inspection and read the report.

Rollback considerations:

- Work-order UI can be disabled while manual station interactions remain usable.
- Facility controller data should rebuild from placed blocks where possible.

## Milestone 4: Scalable Refrigeration Prototype

Goal: prototype walk-in cooler/freezer architecture without committing to full balance.

Included work:

- Controller-based refrigerated room validation.
- Room volume and target temperature summary.
- Cooler versus freezer mode.
- Capacity demand calculation with TBD coefficients.
- Equipment overload warning state.
- Coarse simulation interval.

Excluded work:

- Final compressor/condenser/evaporator art and content breadth.
- Detailed failure model.
- Logistics or refrigerated transport.

Dependencies:

- Milestone 3.
- Owner approval that detailed refrigeration belongs in either ButcherCraft Core or ButcherCraft Refrigeration for the current release track.

Acceptance criteria:

- A valid room reports volume, target temperature, and capacity status.
- Larger rooms require more capacity than smaller rooms.
- Freezer mode requires more capacity than cooler mode.
- Overload can be observed without causing excessive tick cost.

Automated verification:

- `gradlew build`
- Room validation tests.
- Capacity summary tests.
- Performance smoke test for multiple rooms if test harness exists.

Manual in-game verification:

- Build valid and invalid rooms.
- Change room size and target mode.
- Observe product freshness behavior and overload feedback.

Rollback considerations:

- Keep simple refrigerated storage from Milestone 1 as fallback.
- Gate room prototype behind config if stability is uncertain.

## Milestone 5: Employee Skill and Cleanliness Depth

Goal: make employees and sanitation meaningfully affect quality and throughput.

Included work:

- Employee skill progression.
- Skill effects on speed, quality, yield, and errors.
- Cleaning work role.
- Better station and facility cleanliness UI.
- Configurable cleanliness strictness.

Excluded work:

- Complex schedules, wages, morale, or payroll.
- Large team management UI.

Dependencies:

- Milestone 3.

Acceptance criteria:

- Employee skill improves through work.
- Higher skill produces observable benefits.
- Cleaning work reduces facility risk.
- Poor cleanliness can reduce quality and increase MCDA findings.

Automated verification:

- `gradlew build`
- Skill progression tests.
- Cleanliness aggregation tests.
- Quality calculation tests with worker skill inputs.

Manual in-game verification:

- Train an employee over repeated tasks.
- Compare low-skill and higher-skill results.
- Let the facility get dirty, then clean it and observe inspection/quality change.

Rollback considerations:

- Skill modifiers should be data/config driven so balance can be softened without removing the feature.

## Milestone 6: MCDA Escalation and Reinspection

Goal: complete the required inspection consequence loop.

Included work:

- Warning/write-up/fine/shutdown/reinspection escalation.
- Clear inspection notices.
- Violation history.
- Configurable severity.
- Basic shutdown behavior that blocks or limits affected business activity.

Excluded work:

- Real-world legal detail.
- USDA branding.
- Complex appeal processes.

Dependencies:

- Milestone 2.
- Milestone 5 for cleanliness depth, if inspection severity depends on it.

Acceptance criteria:

- Repeated unresolved issues escalate beyond write-ups.
- Correcting issues allows reinspection.
- Shutdown state is understandable and reversible through gameplay.
- Forgiving config preset can soften penalties.

Automated verification:

- `gradlew build`
- Inspection escalation tests.
- Saved-data persistence tests for violation history and shutdown state.

Manual in-game verification:

- Trigger a minor issue and receive a write-up.
- Ignore it and confirm escalation.
- Correct the issue and pass reinspection.

Rollback considerations:

- Shutdown enforcement can be downgraded to warning-only through config if it blocks fun or creates save risk.

## Milestone 7: ButcherCraft Commerce Readiness

Goal: prepare the core for deeper retail and wholesale systems.

Included work:

- Stable order API.
- Customer archetype API.
- Product category tags.
- Reputation tiers.
- Basic finance summaries.
- Documentation for ButcherCraft Commerce hooks.

Excluded work:

- Full ButcherCraft Commerce implementation.
- Recurring contracts and logistics.

Dependencies:

- Milestone 2.
- Milestone 3.

Acceptance criteria:

- Expansion code can register new order templates without touching core internals.
- Orders can require product quality, freshness, packaging, and temperature.
- Business summaries can be displayed without exposing raw saved-data internals.

Automated verification:

- `gradlew build`
- API compatibility tests or compile-only sample expansion once available.

Manual in-game verification:

- Complete orders with different requirements.
- Confirm reputation changes are visible and persistent.

Rollback considerations:

- Keep API marked experimental until at least one expansion prototype uses it.

## Milestone 8: Balance, Config, and Release Candidate

Goal: stabilize the core mod for a first release.

Included work:

- Config presets.
- Tooltip and UI polish.
- Placeholder asset audit.
- Performance profiling.
- Dedicated server validation.
- Save migration review.
- Documentation review.

Excluded work:

- New major systems.
- Expansion content not already scoped for release.

Dependencies:

- All release-target milestones.

Acceptance criteria:

- Default preset is playable without severe surprise penalties.
- Realistic preset makes systems stricter without becoming opaque.
- No known data-loss bugs.
- No client-only classloading on dedicated server.
- All public APIs intended for expansion are documented.

Automated verification:

- `gradlew build`
- `gradlew runData`
- `gradlew test`, if configured
- Dedicated server smoke test

Manual in-game verification:

- Play through the vertical slice from a new world.
- Reload the world multiple times.
- Test forgiving and realistic config presets.
- Confirm placeholder assets are acceptable and non-graphic.

Rollback considerations:

- Freeze registry names before release candidate.
- Defer unstable optional systems rather than shipping save-risk behavior.
