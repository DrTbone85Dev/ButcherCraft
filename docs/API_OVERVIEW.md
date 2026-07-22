# ButcherCraft API Overview

Status: long-term extension planning; no stable public API is implemented

## Purpose

The future ButcherCraft API will allow industry modules, compatibility modules, and third-party mods to participate in one regional simulation without depending on internal managers, persistence classes, or Minecraft adapters.

This document names intended extension concepts only. It does not freeze Java packages, class names, method signatures, codecs, network payloads, or binary compatibility. Public contracts will be introduced only when a real consumer validates them.

## Contract Principles

- Stable ids identify cross-module concepts.
- Immutable records or read-only views cross ownership boundaries.
- Requests are validated and committed by the authoritative server service.
- Explicit results describe success, rejection, and partial-resolution diagnostics.
- Persistent contracts are schema-versioned before release.
- Optional providers can be absent without corrupting unrelated state.
- Events communicate committed facts, not unrestricted mutable state.
- Tags, registries, datapacks, and NeoForge capabilities are preferred where they fit established platform contracts.

## Planned Concepts

### Industry

Describes a module or provider that contributes products, production behavior, business specializations, or economic participation. It should not own a separate world identity or economy.

Possible extension points:

- Stable industry identity.
- Declared capabilities and product categories.
- Data-driven content contributions.
- Compatibility and version metadata.

### Business

Exposes stable business references and bounded runtime summaries. External code should not mutate Business Runtime registries directly.

Possible extension points:

- Business lookup by stable id.
- Read-only operational status and schedule summaries.
- Validated requests for future business actions.
- Business lifecycle events after state commits.

### Product

Identifies goods and their descriptive definitions independently from Minecraft ItemStacks. Existing product definitions, registries, serialized schemas, and ItemStack adapters provide the current internal foundation.

Possible extension points:

- Product ids and definitions.
- Categories, quantity units, tags, and typed metadata.
- Product equivalence or compatibility tags.
- ItemStack conversion at the Minecraft boundary.

### Recipe

Represents a declared production or transformation process. Existing transformation and processing definitions are current internal foundations; a cross-industry public contract is not yet selected.

Possible extension points:

- Required capabilities.
- Inputs, outputs, duration, yield, and constraints.
- Deterministic evaluation.
- Atomic execution results.

### Consumer

Provides bounded demand for products or services. Consumers may represent population cohorts, institutions, businesses, players, or compatible mods.

Possible extension points:

- Consumer identity and location.
- Demand offers by product or category.
- Time windows, priorities, and fulfillment results.

### Producer

Provides declared production capacity or offers without exposing internal machines or inventories.

Possible extension points:

- Producer identity and location.
- Supported outputs and capacity summaries.
- Availability windows and constraints.
- Accepted production requests and completion facts.

### Market

Provides regional exchange context for supply, demand, and future price discovery.

Possible extension points:

- Market identity and territory.
- Supply and demand snapshots.
- Deterministic quotes or clearing results.
- Committed trade events.

### Order

Represents a request and its lifecycle without granting direct access to inventories or business state.

Possible extension points:

- Parties, lines, quantities, time windows, and status.
- Acceptance and rejection results.
- Reservation, fulfillment, cancellation, and completion facts.
- Stable references to products, businesses, warehouses, and shipments.

### Warehouse

Provides custody and capacity summaries for stored goods.

Possible extension points:

- Warehouse identity and location.
- Capacity and handling capabilities.
- Validated deposit, reservation, and withdrawal requests.
- Inventory summaries rather than mutable collections.

### Transport

Provides movement capacity between locations.

Possible extension points:

- Provider, route, capacity, timing, and handling constraints.
- Shipment acceptance and status.
- Custody transfer and delivery results.

### Utility

Provides facility services such as power, water, fuel, or waste handling.

Possible extension points:

- Utility type and provider identity.
- Capacity, availability, reliability, and service area summaries.
- Service requests and committed usage results.

### Population

Provides aggregate demographic, workforce, and consumption context without requiring every resident to be a persistent entity.

Possible extension points:

- Settlement or regional population summaries.
- Workforce availability bands.
- Consumer cohort demand.
- Growth and migration facts.

## Existing Internal Foundations

The current product, transformation, processing, packaging, content snapshot, world identity, simulation, business runtime, and workforce packages provide implementation evidence. They remain internal unless explicitly promoted through a later accepted API decision.

There is currently no stable `com.butchercraft.api` contract. Internal classes must not be treated as public compatibility guarantees merely because they are accessible on the classpath.

## API Maturity Path

1. Implement a focused internal system with clear ownership.
2. Build a real second consumer or compatibility adapter.
3. Identify the smallest reusable contract.
4. Document authority, lifecycle, persistence, threading, failures, and versioning.
5. Add compatibility tests and an example consumer.
6. Mark the contract experimental.
7. Stabilize only after release experience demonstrates that the shape is durable.

## Out Of Scope

This milestone adds no API classes, service loader, event registration, network protocol, datapack type, compatibility adapter, economy service, or gameplay behavior.

