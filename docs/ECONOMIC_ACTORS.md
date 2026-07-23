# Economic Actor Framework

Status: v0.9.0 Phase 15 foundation

The Economic Actor Framework defines who may participate in ButcherCraft's future regional economy. It is a pure Java identity, relationship, and runtime-capability layer. It does not own goods, inventory, quantities, production, orders, prices, markets, transport execution, employees, or gameplay.

## Actor Philosophy

The economy is composed of actors. Actors relate to goods through stable `GoodId` values and never through Minecraft `ItemStack` objects.

An actor may describe a producer, consumer, storage provider, carrier, market, processor, utility, service, or multi-role participant. The same schema applies to Core content, industry modules, compatibility modules, NPC organizations, automated systems, and player businesses. Industry-specific behavior remains outside this framework.

```text
GoodService
  -> immutable GoodRegistry snapshot
  -> EconomicActorService
       -> immutable EconomicActorRegistry
       -> in-memory EconomicActorRuntime state
```

The dependency direction is intentional. Actor definitions may reference known goods and industries. Goods do not depend on actors.

## Actor Definition

`EconomicActorDefinition` is immutable and schema-versioned. It contains:

- stable `ActorId`
- display name
- `ActorType`
- primary `IndustryId`
- immutable `ActorCapability` set
- immutable relationship metadata
- schema version

Actor ids support namespaced lowercase identifiers so future modules can contribute actors without sharing an implementation package. Every definition must declare at least one capability.

## Actor Types

Schema version 1 defines:

- producer
- consumer
- storage
- transport
- market
- processor
- utility
- service
- multi-role

Types are descriptive classifications. They do not dispatch behavior. Future types must be additive and receive a deliberate schema review.

## Capabilities

Capabilities describe what an actor is allowed to participate in later:

- produce
- consume
- store
- transport
- transform
- buy
- sell
- import
- export
- maintain

Capabilities do not execute work. A relationship role must be compatible with its actor's capabilities. For example, an output relationship requires `PRODUCE` or `TRANSFORM`, and a stored relationship requires `STORE`.

Future capabilities must be additive. Persisted unknown capability names fail visibly until the running version understands them.

## Relationships

`ActorRelationship` is immutable metadata connecting one actor definition to one `GoodId`. It records:

- good id
- `GoodRole`
- immutable supported-industry ids
- optional dependency actor id
- schema version

Supported industries let a utility, carrier, warehouse, terminal, or service describe cross-industry support while retaining one primary owning industry.

Good roles are:

- input
- output
- consumed
- stored
- transported
- supported

Optional actor dependencies describe declared metadata chains only. They do not create contracts, orders, scheduling, custody, or automatic transfers. The registry rejects self-dependencies, missing actor references, and circular dependency chains.

## Actor Lifecycle

Immutable definitions and mutable runtime state have separate owners.

`EconomicActorRuntime` stores only:

- actor id
- runtime status
- enabled flag
- operational flag
- optional assigned business runtime reference by `BusinessId`
- optional assigned workforce reference by `WorkforceDefinitionId`
- last simulation tick

Runtime status is one of disabled, available, operational, or suspended. State transitions keep the status and flags consistent and reject simulation ticks that move backward.

Runtime objects are created in memory when definitions load. Phase 15 does not persist runtime state. The actor definition file therefore remains immutable content rather than an operational save. Business Runtime and Workforce remain independent authorities; actor runtime stores only stable references.

## Registry

`EconomicActorRegistry` is an immutable deterministic snapshot. It supports:

- registration through `EconomicActorRegistryBuilder`
- lookup by actor id
- lookup by actor type
- lookup by primary industry
- lookup by capability
- lookup by related good and good role
- relationship and dependency queries
- complete validation

Definitions are sorted by `ActorId`; relationships are sorted by stable relationship fields. Input collection order does not affect registry order or serialized output.

`EconomicActorManager` owns the current registry and in-memory runtime map. It provides lookup, registration, relationship queries, runtime access, and optional validation of business/workforce assignments. It performs no scheduling or economic behavior.

## Persistence

Immutable actor definitions persist at:

```text
<world>/butchercraft/economic_actors.json
```

The root schema version is `1`. Definitions and relationships also record schema version `1`. The root contains `schema_version` and `actors`; relationships are nested inside their owning actor definition.

`EconomicActorStorage` writes deterministic pretty-printed JSON through a temporary file and atomically replaces the active file where supported. Missing files load as an empty actor registry against the current Goods and industry catalogs.

Runtime status, assignments, and simulation ticks are deliberately excluded from this file.

## Validation

Registry construction and persistence loading reject:

- duplicate actor ids
- blank or malformed ids and display names
- missing capabilities
- unknown actor types or capabilities
- unknown primary or supported industries
- unknown goods
- incompatible relationship roles and capabilities
- duplicate relationships
- unknown or self-referencing actor dependencies
- circular dependency chains
- malformed JSON
- missing fields
- unsupported root, definition, or relationship schema versions

Failures are explicit. Invalid persisted content is never replaced silently with partial actor data.

## Examples

These examples describe future definitions, not registered Phase 15 content or gameplay:

| Participant | Typical type | Typical capabilities and relationships |
| --- | --- | --- |
| Producer | `PRODUCER` | Produces outputs and may sell them. |
| Consumer | `CONSUMER` | Consumes goods and may buy inputs. |
| Warehouse | `STORAGE` | Stores goods for supported industries. |
| Rail Terminal | `TRANSPORT` | Transports goods and may support import/export flows. |
| Restaurant | `PROCESSOR` or `MULTI_ROLE` | Buys inputs, transforms or consumes goods, and sells outputs. |
| Retail Store | `MARKET` or `MULTI_ROLE` | Buys, stores, and sells supported goods. |
| Power Plant | `UTILITY` | Produces an energy good and supports multiple industries. |
| MineColonies Colony | `MULTI_ROLE` | A compatibility module may expose its owned production and consumption through stable goods. |
| Player Business | Type determined by the business | References the player's business runtime and workforce without becoming a special economy. |

## Future Extension Points

Future focused systems may use actors as stable participants in:

- actor-owned inventories
- production plans
- demand and consumption
- orders and contracts
- warehouse custody
- transport and logistics
- markets and pricing
- utilities and maintenance
- player, NPC, and compatibility-module participation

Those systems must own their own mutable state and transactions. They must not add quantities, prices, stock, schedules, or ItemStack references to immutable actor definitions.

## Out Of Scope

- inventory and warehousing implementation
- quantities, custody, and stock
- orders and contracts
- scheduling and production
- prices, markets, and economy simulation
- transport and logistics execution
- employment and worker occupancy
- AI, networking, GUI, commands, and gameplay

