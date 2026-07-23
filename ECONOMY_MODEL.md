# ButcherCraft Economy Model

Status: shared vocabulary and implemented foundations; active economy behavior future

This document defines vocabulary and boundaries for the regional economy foundations. It does not define formulas, prices, balancing constants, public Java APIs, or gameplay. Phase 14 provides immutable schema-versioned Goods definitions, Phase 15 provides Economic Actor definitions, Phase 16 provides actor-owned Inventory and Storage runtime quantities, Phase 17 provides their validated mutation and audit pipeline, and Phase 18 provides industry-neutral Orders and Contracts; no production, logistics, market, accounting, or pricing behavior exists.

All future supply, demand, inventory, production, warehousing, transport, consumption, and market state must identify goods through `GoodId`. Commodities and products are defined in `docs/GOODS_FRAMEWORK.md` independently from Minecraft ItemStacks.

## Economic Philosophy

The economy exists independently of the player. Players, NPCs, businesses, industry modules, and compatible mods participate under shared rules. No participant receives a private parallel market simply because it comes from a different module.

Economic outcomes should be explainable from supply, demand, capacity, location, time, contracts, and explicit events. Hidden random price movement is not an acceptable substitute for modeled causes.

## Economic Actors

Economic Actors are the common participant identity for future producers, consumers, warehouses, carriers, markets, processors, utilities, services, compatibility modules, NPC organizations, and player businesses. Actors relate to economic goods through `GoodId`, advertise typed capabilities, and may reference Business Runtime and Workforce through stable ids.

Actor definitions do not own inventory, quantities, production, orders, prices, transport, employment, or gameplay. Those focused future systems will identify participants by `ActorId` and retain their own state and transactions. See `docs/ECONOMIC_ACTORS.md`.

## Supply

Supply is the quantity of a product or service that producers can make available in a market over time. Future supply calculations may consider inventory, production capacity, schedules, workforce, inputs, utilities, quality, transport access, and business status.

Historical preferred suppliers and supply relationships are identity context, not active available supply.

## Demand

Demand is the requested quantity of a product or service for a location and time window. It may originate from population cohorts, businesses, institutions, players, orders, industry modules, or compatible mods.

Demand must identify its source, product or category, location, time, and priority. It should be aggregated where individual consumer simulation would add cost without meaningful decisions.

## Consumption

Consumption resolves fulfilled demand and removes or uses goods through an explicit transaction. Future consumption may represent households, restaurants, institutions, industrial inputs, maintenance, or utilities. It must not create demand and silently destroy inventory in the same opaque step.

## Production

Production turns inputs into outputs through declared transformations, recipes, schedules, capacity, and industry rules. Regional production planning will build on transaction-safe local execution but remains a separate future service.

## Warehousing

Warehousing provides location, capacity, custody, condition, and availability for goods between production and consumption. Phase 16 now provides actor-owned inventory containers, hierarchical storage locations, exact quantities, and capacity validation. Warehouse operations, custody transfers, reservations, condition, availability, and logistics remain future systems.

Inventory is never a universal collection owned by the economy. Each container belongs to an explicit Economic Actor and exists at an explicit Storage Node. See `docs/INVENTORY_FRAMEWORK.md`.

## Transactions

Every future cause of runtime economic quantity change must submit an immutable transaction rather than modifying inventory directly. The common pipeline validates stable Good, Actor, and Inventory references, canonical units, endpoint shape, underflow, status, tick, and capacity before atomic execution. Transaction history is an audit of accepted or rejected structurally valid submissions, not a replacement for accounting, orders, contracts, pricing, or scheduling. See `docs/TRANSACTION_FRAMEWORK.md`.

## Orders And Contracts

Orders express requested or offered economic outcomes. Contracts describe durable obligations that may govern zero or more Orders. Both use Economic Actors, Goods, exact quantities, canonical units, and authoritative Simulation Clock ticks. Their immutable definitions remain separate from lifecycle and fulfillment runtime state.

Fulfillment is business meaning attached to an already APPLIED Transaction. Recording that meaning never changes Inventory and never submits another Transaction. Contract schedules are descriptive only and create no work automatically. See `docs/ORDERS_AND_CONTRACTS.md`.

## Transportation

Transportation moves goods or provides movement capacity between locations. Future transport decisions may consider routes, travel time, capacity, handling requirements, cost, reliability, and infrastructure. Movement must preserve custody and prevent duplication or loss.

## Market Pricing

Prices should emerge from explicit factors such as available supply, unmet demand, location, quality, transport cost, contracts, scarcity, competition, and time. Price state must be deterministic for the same inputs and event sequence.

No pricing formula is selected in this milestone.

## Utilities

Utilities provide services such as power, water, fuel, refrigeration support, and waste handling. Utilities may constrain facility operations and create their own supply, demand, infrastructure, and service contracts. Core should define shared contracts; industry modules may provide infrastructure gameplay.

## Trade

Trade exchanges goods or services between participants under explicit terms. Future trades may be spot purchases, recurring orders, contracts, transfers, or compatibility-module transactions. Every trade must identify parties, value, custody, and completion state.

## Imports And Exports

Imports bring external supply into the simulated region; exports move regional supply out. They provide bounded pressure relief and outside demand without requiring every external region to be fully simulated. Their rules must be visible, configurable, and deterministic.

## Business Competition

Businesses may compete for demand, inputs, workforce, transport, utilities, locations, and reputation. Competition should arise through shared constraints and customer choices rather than arbitrary winner selection.

## Business Expansion

Expansion may increase capacity, locations, workforce structure, services, or market reach. It should require explicit conditions and preserve business, property, ownership, and runtime boundaries.

## Business Failure

Failure may result from sustained insolvency, unavailable inputs, loss of demand, operational suspension, inadequate capacity, or other explicit causes. Closing a business must not delete its immutable identity or historical records. Runtime status, ownership changes, vacant property, and successor businesses remain separate concerns.

## Population Growth

Future population growth may change labor availability, consumption, settlement demand, utilities, and business opportunity. Population identity and population runtime summaries must be versioned separately from immutable settlement identity.

## Future State Boundaries

Likely future owners include focused registries or services for demand, inventory, orders, markets, shipments, warehouses, utilities, and population summaries. These are planning categories, not approved class names or package commitments.

The following remain undecided until implementation milestones provide evidence:

- Currency and accounting precision.
- Price formation formulas.
- Simulation interval and aggregation scale.
- Bankruptcy and business closure rules.
- Import/export balancing.
- Multiplayer market partitioning.
- Cross-mod product equivalence.
- Persistence schemas and migration paths.
