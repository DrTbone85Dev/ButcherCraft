# Commodity And Product Framework

Status: v0.9.0 Phase 14 foundation

The Goods Framework is the pure Java language for economic goods across ButcherCraft Core, industry modules, and future compatibility modules. It defines immutable identity and relationships only. It does not store quantities, inventory, prices, production state, or ItemStacks.

## Goods

Every economic good has one stable `GoodId` and belongs to exactly one `GoodCategory`:

- `COMMODITY`: a generic resource, utility, capacity, livestock class, or raw material.
- `PRODUCT`: a manufactured or processed good with a source industry and transformation stage.

`GoodDefinition` is the sealed immutable base class. `CommodityDefinition` and `ProductDefinition` are its only current implementations.

All definitions include:

- stable good id
- display name
- good category
- owning industry id
- unit of measure
- economic stackability metadata
- immutable typed economic flags
- storage requirement
- transport requirement
- immutable informational item mappings
- schema version

Stackability describes whether an economic good can be grouped as interchangeable units. It is not a Minecraft stack-size rule and does not imply an ItemStack representation.

## Commodities

`CommodityDefinition` adds a typed `CommodityType`:

- livestock
- agricultural
- energy
- utility
- fuel
- transport capacity
- labor capacity
- raw material
- water
- other

`OTHER` provides a deliberate compatibility category. New first-class commodity types require an explicit enum and schema review; arbitrary strings are rejected.

## Products

`com.butchercraft.world.goods.ProductDefinition` is the economic product definition. It adds:

- source industry id
- transformation stage

Stages are raw, intermediate, finished, packaged, consumable, recyclable, and waste.

This class is intentionally separate from `com.butchercraft.product.definition.ProductDefinition`. The existing product definition describes processing content used by transformations, datapacks, ItemStack adapters, and workstations. The economic definition describes how a future regional economy identifies a good. Phase 14 does not migrate or duplicate the current processing catalog, and no automatic bridge between the two definitions exists yet.

## Industries

Every definition and transformation references a typed `IndustryId`. Registry construction receives an explicit known-industry catalog and rejects unknown ids.

`BuiltInIndustryCatalog` currently declares the platform industries documented in `MODULES.md`:

- Core
- Meat Processing
- Agriculture
- Dairy
- Manufacturing
- Forestry
- Transportation
- Retail
- Restaurants
- Utilities

The registry accepts an injected collection rather than hardcoding validation internally. Future module registration can extend the authoritative catalog after a public module contract is designed.

## Units

`UnitOfMeasure` schema version 1 supports:

- each
- pound
- kilogram
- liter
- gallon
- bushel
- ton
- kilowatt-hour
- head
- pallet
- crate
- box

Units have stable serialized names. Future units are added through an explicit schema-compatible enum addition or migration; unknown persisted values are rejected rather than silently interpreted.

## Storage Metadata

Each good declares one descriptive `StorageRequirement`:

- ambient
- refrigerated
- frozen
- climate controlled
- hazardous

This metadata does not create storage, temperature, spoilage, capacity, or inventory behavior.

## Transport Metadata

Each good declares one descriptive `TransportRequirement`:

- standard
- refrigerated
- frozen
- livestock
- liquid
- bulk
- hazardous

This metadata does not create vehicles, routes, shipments, logistics, or transportation simulation.

## Economic Flags

Schema version 1 provides typed flags for tradeable, consumable, perishable, regulated, hazardous, and capacity goods. Flags are immutable descriptive facts. They do not activate market, consumption, spoilage, regulation, or capacity behavior.

## Informational Item Mappings

`ItemMappingMetadata` records a provider id and item id as validated pure Java identifiers. It allows a good to describe possible future physical item representations without importing Minecraft or resolving an ItemStack.

Mappings are informational only:

```text
Economic Good
  -> informational provider/item reference
  -> future Minecraft integration resolver
  -> possible physical item representation
```

Phase 14 does not register items, map existing products, read inventories, or guarantee that a referenced item exists.

## Transformation Graph

`GoodTransformation` records:

- input good id
- output good id
- exact positive numerator/denominator yield ratio
- owning industry id
- schema version

It describes a directed relationship only. It does not execute production, consume inputs, create outputs, schedule work, or mutate inventory.

`GoodRegistry` validates the complete graph with an iterative topological check. Self-cycles and multi-node cycles are rejected. Transformation inputs and outputs must resolve to registered goods, and the owning industry must be known.

## Registry And Manager

`GoodRegistry` is an immutable deterministic snapshot. It provides:

- lookup by good id
- lookup by category and industry
- ordered definition and transformation streams
- transformation lookup by input, output, and industry
- duplicate-id validation
- reference validation
- industry validation
- cycle validation

Definitions and transformations are sorted by stable ids, so input collection order does not affect the resulting registry.

`GoodManager` owns replacement of the current immutable registry when definitions or relationships are registered. It exposes lookup and validation but stores no quantities or runtime economic state.

## Persistence

Goods persist independently at:

```text
<world>/butchercraft/goods.json
```

The root schema version is `1`. Each definition and transformation also stores schema version `1`.

The root contains:

- `schema_version`
- `goods`
- `transformations`

`GoodStorage` writes deterministic pretty-printed JSON through a temporary file and atomically replaces the active file where supported. Missing files load as an empty registry using the current industry catalog. Corrupt JSON, unsupported schemas, malformed definitions, duplicate ids, invalid enum values, unknown industries, unknown good references, and circular graphs fail visibly.

`GoodService` is the Minecraft lifecycle adapter. It loads on server start and saves on orderly server stop. Only the service imports Minecraft and NeoForge classes; `com.butchercraft.world.goods` remains pure Java.

## Current Content Boundary

Phase 14 registers no built-in economic goods. The examples in the project brief and tests prove the schema but do not create gameplay content. A later milestone must deliberately map or migrate existing processing products after ownership, data loading, and compatibility rules are approved.

## Future Extension Points

Future systems may reference `GoodId` for:

- inventory and warehouses
- supply and demand
- production planning
- orders and markets
- transport and logistics
- utilities and capacity
- compatibility-module mappings

Those systems must own their own quantities and mutable runtime state. They must not add runtime quantity, price, stock, shipment, or production fields to immutable good definitions.

## Out Of Scope

- inventory and warehousing
- quantities and stock
- pricing, markets, and economy execution
- orders and demand
- transportation and shipments
- production, recipes, machines, and transformations execution
- spoilage and utility simulation
- ItemStack conversion
- datapack loading
- networking, GUI, commands, and gameplay

