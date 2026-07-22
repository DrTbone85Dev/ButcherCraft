# ButcherCraft World Identity Architecture

Status: proposed canonical planning document
Target: Minecraft 1.21.1, NeoForge, Java 21
Scope: long-term world identity, regional context, commercial geography, manufacturer identity, and business-start architecture

This document defines the long-term architecture for the World Identity Engine. It is a planning and specification document, not a gameplay implementation plan. It should guide future milestones without prescribing Java classes, storage formats, UI flows, world-generation hooks, or concrete mechanics before those systems are scheduled.

## Purpose

The World Identity Engine is the architectural layer that gives ButcherCraft's surrounding world persistent commercial, regional, and historical identity.

Its purpose is to describe the simulated context around the player's business: where the business exists, what region it belongs to, what counties and settlements surround it, what commercial properties are available, what manufacturers operate in the market, and how the player's business becomes part of that larger world over time.

The engine does not replace the processing, product, packaging, workstation, refrigeration, employee, inspection, or business systems documented elsewhere. Those systems should consume world identity data when they need context. They should not each reinvent regional names, commercial locations, manufacturer catalogs, or historical business facts independently.

## Vision

ButcherCraft is a business simulation inside a living world, not simply a machine simulator.

Workstations, products, packaging, refrigeration, employees, and inspections are core systems, but they are more meaningful when they exist inside a place. The player should feel that a butcher shop is not only a collection of blocks and inventories. It is a business operating in a region, serving settlements, buying from suppliers, responding to local opportunities, and building a name that can persist beyond a single production run.

The World Identity Engine should support that sense of place through deterministic identity, persistent history, and future-facing extension points. It should make exploration, expansion, manufacturer selection, and commercial growth feel grounded without forcing a scripted campaign or replacing Minecraft's open-ended play.

## Current Goals And Future Expansion

The initial goal is architectural foundation: define the concepts that future systems will consume and ensure those concepts have stable meaning.

Current goals:

- Establish canonical terminology for world identity.
- Define the conceptual hierarchy from world to business.
- Distinguish regional, county, settlement, property, manufacturer, and business identity.
- Provide a shared planning reference for future milestone work.
- Keep world identity independent from workstation execution and product transformation logic.

Future expansion may add interactive property markets, regional demand, supplier catalogs, manufacturer promotions, equipment recalls, trade events, newspapers, historical archives, and larger procedural settlements. Those systems should build on the foundation described here rather than introducing separate identity models.

## Design Philosophy

World identity is generated before it is utilized.

The engine should establish stable identity first, then allow later systems to use that identity. A newspaper, supplier catalog, regional demand system, or property market should read from the same world identity foundation rather than inventing new names and relationships on demand.

The world influences opportunity, not player ability.

Regional identity may shape available properties, local demand, manufacturer presence, event flavor, and business opportunities. It must not grant unavoidable seed-based advantages or disadvantages that make one player inherently more capable than another.

Exploration should reveal opportunity.

The player should discover useful places, properties, suppliers, and commercial history by moving through the world and engaging with settlements. The engine should support opportunity discovery without requiring a mandatory story path.

Story emerges through gameplay.

ButcherCraft should not depend on a fixed campaign. A business legacy should emerge from player choices, property purchases, production decisions, regional relationships, inspection outcomes, employees, expansion, and long-term persistence.

Architecture first.

World identity should begin as a stable model before broad gameplay systems depend on it. This keeps future commerce, property, manufacturer, and regional-demand systems coherent.

Gameplay systems consume world data rather than recreating it.

Business systems, supplier systems, employee systems, inspection systems, and future commerce interfaces should consume shared world identity data. They should not each maintain unrelated manufacturer lists, settlement names, or property histories.

## World Generation Hierarchy

The World Identity Engine uses a conceptual hierarchy:

```text
World
Region
County
Settlement
Commercial Property
Business
```

Each layer owns a different scale of identity.

The world owns the complete identity snapshot for a save. A region gives broad cultural, economic, agricultural, and naming context. A county provides administrative identity and local grouping. A settlement provides a population center and commercial market. A commercial property provides a persistent location and history. A business represents the player's operation or a future non-player commercial identity associated with a property.

This hierarchy is conceptual. It should guide data ownership and future system boundaries without requiring a specific storage mechanism or generation algorithm at this stage.

## Regions

Regions are the broadest player-facing identity layer below the world.

ButcherCraft should begin with five handcrafted starting regions. Each region should have a distinct identity while remaining compatible with the same core gameplay systems. The regions should be deterministic so a world can produce stable identity across reloads, multiplayer sessions, and future systems that reference regional context.

Future versions may allow manual region selection during world or business setup. Manual selection should change regional flavor and opportunity presentation, not core player capability.

Regional identity may include:

- Common naming conventions for counties, settlements, roads, businesses, and manufacturers.
- Agricultural character, such as livestock emphasis, crop patterns, or rural density.
- Economic character, such as local retail demand, processing demand, industrial presence, or warehouse availability.
- Commercial style, such as family shops, market towns, locker plants, or industrial corridors.
- Historical tone, such as older family businesses, newly developed commercial lots, or long-standing regional manufacturers.

Regions should be handcrafted enough to feel intentional. Deterministic generation may assemble details within those identities, but it should not reduce regions to fully random name tables.

## Counties

Counties provide administrative identity between regions and settlements.

Their purpose is to group settlements and properties into a local jurisdiction-scale context. They can support future systems such as notices, county fairs, inspection districts, property listings, regional demand summaries, local market reports, and historical archives.

Future county responsibilities may include:

- Administrative naming and local identity.
- Grouping settlements for market, supplier, and inspection context.
- Supporting county-level events and notices.
- Providing a stable reference for property records and business histories.
- Giving future regional demand systems a middle layer between region and settlement.

Counties should not become complex government simulations during the foundation phase. They exist first as identity and organization.

## Settlements

Settlements are population centers and commercial opportunity anchors.

The initial implementation should enhance vanilla Minecraft villages rather than replacing them. Existing villages can receive additional identity, nearby commercial context, and future property opportunities while preserving Minecraft compatibility and avoiding early world-generation risk.

Settlement hierarchy:

- Hamlet
- Village
- Town
- Regional City

Hamlets represent small rural identity points. Villages represent the initial scale closest to vanilla Minecraft villages. Towns provide larger commercial opportunity and future property density. Regional cities represent major future hubs for trade, manufacturing, property markets, and business expansion.

Future versions may introduce larger procedural settlements. Those should build on the same settlement identity model rather than creating a separate city-specific framework.

Settlements may eventually expose:

- Commercial property listings.
- Local demand and customer patterns.
- Supplier and manufacturer presence.
- Newspaper and announcement sources.
- Event schedules.
- Historical business records.
- Expansion opportunities for the player's company.

## Commercial Properties

Commercial properties are persistent generated locations where businesses can exist.

They should support both starting scenarios and long-term expansion. A property is more than a block position or building shell. It should have identity, category, location context, and generated history that future systems can display or consume.

Property categories may include:

- Family butcher shops.
- Vacant buildings.
- Industrial lots.
- Locker plants.
- Warehouses.

Every property should have persistent generated history. That history may eventually include former businesses, years active, local reputation, sale status, renovation notes, equipment remnants, or expansion potential. The foundation should define that such history exists without requiring all details to be interactive immediately.

Commercial properties should support exploration-driven growth. The player can start with a property in one scenario, discover vacant properties later, buy additional locations, and expand naturally across settlements.

## Manufacturers

Manufacturers provide stable identity for equipment, supplies, fixtures, and future commercial catalogs.

ButcherCraft should use a hybrid manufacturer system. The core manufacturer roster should be handcrafted enough to feel credible, recognizable, and reusable, while future systems may select, filter, and present those manufacturers deterministically based on region, tier, category, and business context.

The long-term target is approximately 25 to 30 handcrafted companies.

Manufacturer categories may include:

- Processing equipment.
- Refrigeration equipment.
- Packaging supplies.
- Facility fixtures.
- Sanitation supplies.
- Retail and display equipment.
- Industrial and warehouse equipment.

Market tiers may include:

- Entry-level regional suppliers.
- Reliable professional suppliers.
- Premium specialist manufacturers.
- Heavy industrial manufacturers.
- Legacy or refurbished equipment sources.

Manufacturers may have regional headquarters or strong regional presence. This should shape catalog presentation, promotions, service availability, or narrative context in future systems, but it should not prevent the player from progressing.

Engineering philosophy:

- Company names should be handcrafted, stable, and internally consistent.
- Procedural systems may choose from or contextualize companies, but should not replace handcrafted company identity with fully random names.
- Manufacturer identity should support future catalogs, promotions, equipment recalls, service records, used-equipment markets, and historical archives.
- Manufacturer definitions should remain separate from workstation execution rules and product transformation definitions.

Future expansions may add more manufacturers, categories, regions, catalog entries, warranties, service levels, or market relationships through documented extension points.

## Business Starts

Business starts define the player's initial commercial context.

They should shape the opening situation and learning path without changing the core rule that essential early work remains manually playable.

Initial starts:

- Family Legacy
- Entrepreneur

Family Legacy represents inheriting or continuing an existing family butcher business. It may include a starter property, family name context, existing local recognition, and an optional guided tutorial. The tutorial should help the player understand early business operation without becoming a mandatory campaign.

Entrepreneur represents starting without property ownership. The player begins with no commercial property and is encouraged to explore settlements, discover available buildings, compare opportunities, and choose where to establish the business.

Future starts:

- Rural Locker
- Commercial Processor
- Bank Auction
- Creative Builder

Rural Locker may emphasize community cold storage and local processing. Commercial Processor may emphasize larger-volume production. Bank Auction may emphasize distressed property acquisition and renovation. Creative Builder may allow a freer start for players who want to build outside normal commercial constraints.

Future starts should reuse the same world identity, property, manufacturer, and business-history concepts rather than creating separate progression models.

## Player Identity

Player identity has two primary names:

- Family Name
- Business Name

The family name represents personal or legacy identity. It may appear in family-history context, local reputation, newspapers, property records, employee stories, and long-term business legacy.

The business name represents the commercial identity. It appears on property records, future order boards, supplier accounts, newspaper mentions, catalogs, signage, and historical archives.

The two names may be related but should remain distinct. A player may operate a family-named shop, create a new brand, or expand into a business identity that outgrows the original family property.

Future systems should avoid assuming that family identity and business identity are always identical.

## Progression Philosophy

World identity progression should be milestone-based and open-ended.

There is no game ending. The player's business can remain a small family shop, expand into multiple properties, become a regional processor, or serve as a long-term creative build. Progression should create new opportunity and context rather than forcing a final victory state.

Business legacy is the long-term measure. The player's actions should create history: properties purchased, facilities expanded, products made, employees hired, inspections passed or failed, suppliers chosen, settlements served, and commercial reputation developed.

Long-term expansion should remain compatible with the project pillars in `PROJECT_VISION.md`: hands-on first, management later; abstract presentation with meaningful consequences; configurable realism; stable persistence; documented expansion boundaries; and small testable milestones before broad content volume.

## Commercial Expansion

Additional commercial properties should exist throughout generated settlements.

Players should expand naturally through exploration and purchasing property. A settlement may reveal a vacant storefront, an old locker plant, an industrial lot, or a warehouse that supports a new phase of the business. Regional cities may eventually provide denser markets and higher-tier opportunities, while rural settlements may provide lower-overhead properties and stronger local identity.

Commercial expansion should support multiple play styles:

- A single polished family shop.
- A renovated rural locker plant.
- A town butcher shop with nearby storage.
- A warehouse-scale processor.
- A regional network of properties.

The engine should define property identity and history before future systems attach pricing, financing, renovation, zoning, equipment, employee routing, or customer demand.

## Future Consumers

The following systems may consume World Identity data in future milestones:

- Newspapers.
- Supplier catalogs.
- Property markets.
- Manufacturer promotions.
- Equipment recalls.
- County fairs.
- Trade expos.
- Regional demand.
- Historical archives.

These are future consumers, not part of the initial implementation. Their presence in this document means the world identity foundation should be designed so those systems can consume shared region, county, settlement, property, manufacturer, and business-history data later.

## API Goals

The World Identity Engine should eventually expose conceptual services and records through documented boundaries.

Conceptual types may include:

- `WorldIdentity`
- `Region`
- `County`
- `Settlement`
- `CommercialProperty`
- `Manufacturer`
- `BusinessHistory`

These names describe architectural concepts only. They do not prescribe implementation details, package names, serialization formats, registries, capabilities, or storage mechanisms.

API goals:

- Provide stable identity lookups for future gameplay systems.
- Keep world identity data server-authoritative.
- Distinguish current identity from future interactive systems that consume identity.
- Preserve generated history where practical.
- Allow future expansions to add regions, manufacturers, property categories, or settlement content through documented extension points.
- Avoid coupling world identity directly to workstation execution, transformation definitions, product serialization, or GUI rendering.

Public interfaces should follow the broader project API rules in `TECHNICAL_ARCHITECTURE.md`: data ownership, persistence behavior, server/client expectations, and versioning must be documented before expansion code depends on them.

## Persistence And Data Ownership

World identity should be persistent once generated for a save.

Future implementation should respect the persistence rules in `TECHNICAL_ARCHITECTURE.md`: saved data must not be silently discarded, unknown ids should be preserved where practical, and state that affects gameplay should have a migration plan before public saves rely on it.

The foundation should distinguish generated identity from active simulation:

- Generated identity describes stable facts such as region, county, settlement, property, manufacturer, and business-history context.
- Active simulation describes future mutable systems such as property markets, demand, events, promotions, recalls, inspections, employees, orders, and business finances.

Active simulation systems should consume identity data rather than owning duplicate identity state.

## Roadmap

### v0.9.0: World Identity Foundation

Define the initial world identity foundation. Establish canonical concepts for regions, counties, settlements, commercial properties, manufacturers, business starts, player identity, and business history. Keep the work focused on architecture and stable data boundaries before adding broad interactive systems.

### Future: Background Integration

Allow non-intrusive systems to consume world identity. Candidate consumers include supplier catalogs, property listings, manufacturer presentation, regional flavor, newspapers, and business-history displays. These systems should reveal world context without requiring a full living economy.

### Later: Interactive World

Introduce deeper interaction through property markets, regional demand, trade expos, county events, manufacturer promotions, equipment recalls, and historical archives. These features should continue to build from the same identity foundation.

### Long Term: Living Economy

Develop a living commercial ecosystem where settlements, properties, suppliers, manufacturers, customers, and business history create meaningful long-term context. This should happen only after the foundation, persistence boundaries, and core business loop are stable.

## Non-Goals

The World Identity Engine intentionally does not do the following during its foundation phase:

- No procedural company names replacing handcrafted companies.
- No gameplay advantages from world seeds.
- No mandatory story campaign.
- No economy simulation before the foundation exists.
- No replacement of Minecraft villages during the initial implementation.
- No workstation execution changes.
- No product transformation changes.
- No packaging gameplay changes.
- No refrigeration, employee, inspection, or order-system implementation by implication.
- No final UI, newspaper, catalog, property-market, or settlement-generation feature until a future milestone schedules it.

## Supporting Documents

World identity should remain consistent with:

- `PROJECT_VISION.md` for the overall player experience and product pillars.
- `TECHNICAL_ARCHITECTURE.md` for package ownership, persistence, APIs, server authority, and client/server separation.
- `DECISIONS.md` for accepted project constraints and architectural decisions.
- `MILESTONES.md` for scheduled implementation boundaries.
- `KNOWN_LIMITATIONS.md` for current risks and deferred work.

## Guiding Principle

The player is not simply building machines. They are building a business that becomes part of the world's history.
