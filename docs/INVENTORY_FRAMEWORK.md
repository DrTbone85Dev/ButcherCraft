# Inventory And Storage Framework

Status: v0.9.0 Phase 16 foundation

The Inventory and Storage Framework is ButcherCraft Core's pure Java runtime location model for economic Goods. It defines who owns quantities, where those quantities are located, how storage is organized, and whether proposed quantity changes fit declared capacity.

It does not implement Minecraft inventories, ItemStacks, slots, containers, menus, production, spoilage, logistics, orders, pricing, markets, automation, or gameplay.

## Inventory Philosophy

```text
Economic Actor
  -> owns InventoryContainer
       -> is located at StorageNode
       -> has InventoryRuntime
            -> contains InventoryEntry quantities by GoodId
```

Goods remain immutable definitions. Inventory entries reference them by stable `GoodId` and use the definition's canonical `UnitOfMeasure`. Economic Actors remain the ownership authority and are referenced by stable `ActorId`.

Inventory is runtime quantity state. It is not a Minecraft item representation and cannot inspect or mutate an ItemStack.

## Ownership Model

`InventoryContainer` is immutable identity metadata with:

- stable `InventoryId`
- display name
- owner `ActorId`
- `StorageNodeId`
- `InventoryType`
- capacity definition
- schema version

Schema version 1 inventory types are warehouse, processing, retail, transport, utility, temporary, player, and other. Types are descriptive and do not dispatch behavior.

Every container must reference a known Economic Actor and a known Storage Node. One actor may own any number of containers at different nodes.

## Storage Hierarchy

`StorageNode` is immutable physical-location metadata with:

- stable `StorageNodeId`
- display name
- `StorageRequirement`
- capacity definition
- optional parent node
- schema version

Parent relationships support nested locations such as:

```text
Distribution Center
  -> Warehouse
  -> Cooler
       -> Retail Inventory
```

The registry rejects missing parents, self-parenting, and circular hierarchies. Runtime capacity at a parent includes entries in all descendant nodes. Storage requirements are descriptive in Phase 16; no temperature, spoilage, or compatibility simulation is activated.

## Capacity Metadata

`StorageCapacity` supports optional limits for:

- maximum weight
- maximum volume
- maximum discrete units
- maximum distinct Goods

Weight limits use pounds, kilograms, or tons. Volume limits use liters, gallons, or bushels. Validation uses deterministic exact decimal conversion with kilograms and liters as comparison bases. Discrete totals include each, head, pallet, crate, and box quantities.

Capacity definitions remain metadata. They do not reserve space, choose locations, route Goods, or automate transfers. The manager validates container capacity, direct storage-node capacity, and every ancestor node's aggregate capacity before committing an addition.

Child and container capacity declarations may not exceed a comparable finite parent limit. An omitted limit remains unbounded at that level, while finite ancestor limits still apply to runtime entries.

## Runtime State

`InventoryRuntime` is mutable state separate from immutable container identity. It stores:

- inventory id
- `InventoryStatus`
- deterministically ordered current entries
- last simulation tick
- schema version

Statuses are active, locked, in transit, maintenance, and disabled. Active and in-transit inventories may currently receive and release quantities. Other statuses reject quantity changes and proposed movements.

Runtime transitions reject simulation ticks that move backward. Entry additions merge identical Good, unit, and metadata keys with overflow-checked arithmetic. Removals reject missing or insufficient quantities and remove zeroed entries.

## Inventory Entries

`InventoryEntry` contains:

- `GoodId`
- exact non-negative `long` quantity
- `UnitOfMeasure`
- immutable typed entry metadata

The entry unit must exactly match the registered Good definition. No implicit conversion changes the stored quantity.

`InventoryEntryMetadata` provides optional typed fields for future lot number, expiration simulation tick, quality basis points, and origin actor id. Phase 16 persists and validates these values but does not interpret them, age them, change quality, enforce expiration, or provide traceability gameplay.

## Registry And Manager

`InventoryRegistry` is an immutable deterministic snapshot containing inventory containers and storage nodes. It provides lookup by inventory id, owner actor, storage node, and inventory type, plus parent, child, descendant, and ancestor queries.

`InventoryManager` owns the registry and mutable runtime map. It provides:

- inventory and storage lookup
- ownership and location queries
- quantity queries by inventory, owner, and storage hierarchy
- validated entry additions and removals
- complete Good, actor, unit, metadata, hierarchy, and capacity validation
- validation-only movement checks

`validateMovement` produces an explicit `InventoryMovementValidation` result. It checks source and target existence, statuses, quantity, Good and unit validity, metadata, source availability, and target/container/storage capacity using an atomic source-removal and target-addition candidate. It does not commit the movement, schedule work, select a route, or transfer custody.

## Persistence

Inventory state persists at:

```text
<world>/butchercraft/inventory.json
```

The root schema version is `1`. The file stores:

- storage-node definitions
- inventory-container definitions
- one runtime record for every container
- runtime status and last simulation tick
- entries, exact quantities, canonical units, and typed metadata

The file does not store Minecraft inventories, ItemStacks, slots, menu state, or GUI state.

`InventoryStorage` writes deterministic pretty-printed JSON through a temporary file and atomically replaces the active file where supported. Every optional field is serialized explicitly, including null values, to keep the schema shape stable. Missing files load an empty registry. Missing runtime records, malformed JSON, unsupported schemas, partial records, and invalid references fail visibly rather than creating empty replacement state.

`InventoryService` is the Minecraft lifecycle boundary. It initializes after `EconomicActorService`, receives the active immutable Good and actor registries, and loads or saves `inventory.json`. Only the service imports Minecraft and NeoForge classes.

## Validation

The framework rejects:

- duplicate inventory ids
- duplicate storage-node ids
- duplicate or missing runtime records
- unknown owner or origin actors
- unknown Goods
- unknown storage nodes or parents
- invalid or mismatched units
- negative or non-integral quantities
- malformed typed metadata
- container, node, or ancestor capacity violations
- circular storage hierarchies
- malformed JSON and missing fields
- unsupported root or record schema versions

## Examples

These are future definitions, not registered Phase 16 gameplay content:

| Example | Container and storage relationship |
| --- | --- |
| Warehouse | Actor-owned warehouse inventory at a warehouse node. |
| Retail Cooler | Retail inventory at a refrigerated child node. |
| Freezer | Frozen storage node containing one or more inventories. |
| Truck Trailer | Transport inventory located at a trailer storage node. |
| Distribution Center | Parent node aggregating warehouse, cooler, and staging capacities. |
| Restaurant Pantry | Restaurant-owned processing inventory at an ambient pantry node. |
| Player-Owned Warehouse | Player-business actor owning warehouse containers through the same contracts. |

## Future Extension Points

Future focused systems may build on `InventoryId`, `StorageNodeId`, and validated runtime summaries for:

- reservations and custody
- warehouse operations
- production input and output transactions
- orders and contracts
- transport and logistics
- spoilage and condition
- market availability
- Minecraft ItemStack adapters at an integration boundary
- compatibility-module inventory summaries

Those systems must preserve server authority, use explicit transactions, and keep ItemStack conversion outside `com.butchercraft.world.inventory`.

## Out Of Scope

- production and consumption
- spoilage, freshness, and quality behavior
- logistics, transportation, routing, and shipments
- orders, contracts, reservations, and scheduling
- pricing, markets, and economy simulation
- AI, automation, networking, GUI, and gameplay
- Minecraft inventories, containers, slots, and ItemStack conversion

