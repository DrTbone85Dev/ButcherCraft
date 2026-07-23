# ButcherCraft Modular Architecture

Status: canonical platform module plan

ButcherCraft is organized as a shared regional simulation platform with industry and compatibility modules. This document assigns conceptual responsibilities. It does not declare every planned module implemented, separately downloadable, or covered by a stable public API today.

## ButcherCraft Core

ButcherCraft Core is the common platform. It owns concepts that must remain consistent across industries and integrations.

### Responsibilities

- World simulation identity and stable regional references.
- Simulation Clock, calendar, scheduler, and event publication.
- Business identity and mutable business runtime foundations.
- Shared workforce structure and future cross-industry worker contracts.
- Immutable commodity and product identities shared across industries.
- Industry-neutral Economic Actor identity, capability, and Good-relationship contracts.
- Actor-owned economic inventory quantities, storage hierarchy, and capacity contracts.
- Universal economic transaction validation, atomic execution, audit, persistence, and replay contracts.
- Future economy, market, consumer, logistics, utility, and population foundations.
- Persistence ownership, schema versioning, validation, and migration policy.
- Content snapshot coordination for shared data-driven definitions.
- Public API contracts after real module or compatibility consumers prove them.
- Server lifecycle integration for shared simulation services.

Core must not become a collection of industry-specific recipes or machine behavior. It may provide generic product, transformation, transaction, and workstation foundations where those concepts are shared, while industry modules provide actual industry content and rules.

### Current State

Implemented Core foundations include deterministic World Identity, player identity, the Simulation Clock and Event Framework, Business Runtime, Workforce definitions, immutable economic Goods definitions, industry-neutral Economic Actor definitions and runtime capabilities, actor-owned Inventory and Storage runtime quantities, the universal economic Transaction Framework, generic processing product and transformation domains, atomic content snapshots, and transaction-safe workstation foundations.

Economy, logistics execution, population simulation, consumers, utilities, and a stable third-party API are planned, not implemented.

## Industry Modules

Industry modules participate in shared world identity, time, business, workforce, economy, logistics, and persistence contracts. They own domain content and behavior that would be inappropriate in Core.

### Meat Processing

Status: flagship implementation

Responsibilities:

- Meat product definitions and processing transformations.
- Fabrication, grinding, packaging, refrigeration, cleanliness, and inspection rules when scheduled.
- Industry workstations, equipment, assets, menus, and player interactions.
- Meat-industry business specializations and workforce requirements.
- Industry-specific data that uses shared product, business, and economic contracts.

Existing Grinder, Bandsaw, Packaging Table, product, packaging, processing, transformation, and workstation systems form the current implementation base.

### Agriculture

Status: future

Responsibilities:

- Crop and livestock production concepts appropriate to the platform's abstract visual standard.
- Farm-specific production cycles, equipment, inputs, outputs, and business specializations.
- Supply into food, textile, manufacturing, and retail industries through shared contracts.

### Dairy

Status: future

Responsibilities:

- Milk collection, processing, cold-chain requirements, and dairy products.
- Dairy-specific facilities, equipment, quality rules, and workforce roles.
- Shared distribution and retail participation without a separate economy.

### Manufacturing

Status: future

Responsibilities:

- General material conversion, assembly, industrial equipment, and manufactured goods.
- Factory-specific production planning and infrastructure needs.
- Shared product, order, warehouse, transport, utility, and market participation.

### Forestry

Status: future

Responsibilities:

- Renewable resource management, timber production, milling, and wood products.
- Forestry-specific land, equipment, processing, and workforce concepts.
- Supply to construction, manufacturing, utilities, and retail.

### Transportation

Status: future

Responsibilities:

- Movement services between producers, warehouses, businesses, and markets.
- Transport capacity, routes, schedules, handling constraints, and service providers.
- Industry-specific vehicles or infrastructure only after shared logistics contracts exist.

Transportation owns movement operations; Core owns shared logistics identity and contracts.

### Retail

Status: future beyond the existing retail-product foundation

Responsibilities:

- Store operations, assortments, shelf availability, purchasing, and sales presentation.
- Consumer-facing fulfillment using shared products, demand, and market contracts.
- Retail-specific facilities, workforce, and business rules.

### Restaurants

Status: future

Responsibilities:

- Ingredient procurement, menu production, service capacity, and prepared-food businesses.
- Restaurant-specific equipment, recipes, staffing, and customer service.
- Shared demand, product, supplier, utility, and market participation.

### Utilities

Status: future

Responsibilities:

- Generation or provision of power, water, fuel, waste, and other regional services.
- Capacity, reliability, infrastructure, and service contracts.
- Shared inputs to facilities and industries.

Core owns utility contracts and regional summaries; a Utilities industry module may own infrastructure gameplay and content.

## Compatibility Modules

Compatibility modules connect external mods to shared platform contracts. They do not copy an external mod's systems into Core and do not bypass server authority.

### MineColonies

- Colonies may provide population, workplaces, production participants, and demand.
- Integration should exchange bounded summaries and orders rather than control colony internals.

### Create

- Create machines and contraptions may satisfy industrial equipment or transport capabilities.
- Integration should map explicit capabilities and material movement into server-authoritative transactions.

### Farmer's Delight

- Foods, ingredients, and production may participate in shared product and supply contracts.
- Existing tags and data should be preferred over duplicate product identities.

### Immersive Engineering

- Industrial infrastructure and utilities may satisfy equipment, energy, or facility service contracts.
- Integration should preserve each mod's ownership of its own machines and energy behavior.

### Future Third-Party Integrations

Third-party mods should integrate through shared economic contracts, registries, tags, capabilities, and events after those contracts are stable. One-off access to internal managers or persisted records is not an integration API.

## Module Responsibility Rules

- Core owns one regional identity, one simulation clock, and shared persistent identifiers.
- An industry module does not create a parallel economy, calendar, business identity system, or player identity system.
- A compatibility module translates; it does not become the authoritative owner of either mod's state.
- Modules exchange stable ids and immutable snapshots rather than mutable implementation objects.
- Server-side services validate and commit consequential changes.
- Optional module removal must preserve unknown references where practical and must not delete unrelated state.
- Cross-module behavior must have deterministic ordering and explicit failure outcomes.
- A planned module name is not a commitment to a separate artifact or release.

## Current Package Alignment

The existing layout already supports the platform direction:

| Current package | Platform role |
| --- | --- |
| `com.butchercraft.world.identity` | Immutable regional identity. |
| `com.butchercraft.world.simulation` | Shared clock, scheduler, and event framework. |
| `com.butchercraft.world.business` | Immutable business identity. |
| `com.butchercraft.world.business.runtime` | Mutable business operations. |
| `com.butchercraft.world.workforce` | Cross-industry staffing structure. |
| `com.butchercraft.world.trade` | Immutable historical supply-network identity. |
| `com.butchercraft.world.goods` | Universal immutable economic commodities, products, units, metadata, and transformation relationships. |
| `com.butchercraft.world.economy.actor` | Universal economic participant definitions, capabilities, Good relationships, and separate in-memory runtime status. |
| `com.butchercraft.world.inventory` | Actor-owned economic inventory containers, storage hierarchy, capacity metadata, runtime Good quantities, and independent persistence. |
| `com.butchercraft.engine` | Pure processing and transaction foundations. |
| `com.butchercraft.product` | Product definitions and Minecraft adapters. |
| `com.butchercraft.transformation` | Generic material transformations. |
| `com.butchercraft.content` | Atomic content snapshots. |
| `com.butchercraft.processing`, `packaging`, `workstation`, `machine` | Current Meat Processing implementation and reusable boundaries. |
| `com.butchercraft.integration` | Minecraft-side content and datapack integration. |

No package rename is required for this milestone. Future extraction should occur only when a real second industry reveals a stable boundary. Recommendations are documented in `TECHNICAL_ARCHITECTURE.md` and are not implementation commitments.

## Historical Module Plan

`MODULE_PLAN.md` preserves the earlier expansion strategy and remains useful context for the flagship Meat Processing scope. This document supersedes it for active platform-level module planning.
