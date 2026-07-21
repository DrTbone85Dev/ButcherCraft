# ButcherCraft Module Plan

Status: proposed planning document

## Module Strategy

ButcherCraft should be one core mod with a limited number of substantial optional expansions. ButcherCraft Core must provide the first playable vertical slice and the public extension APIs. Expansions should add coherent feature families, not one downloadable mod per machine.

ButcherCraft Core should remain playable by itself. Expansions should depend on ButcherCraft Core and should not depend on one another unless explicitly approved.

## ButcherCraft Core

Approved mod id: `butchercraft`

ButcherCraft Core responsibilities:

- Shared API packages for products, processing, refrigeration summaries, business ids, and orders.
- Basic product item and item data components.
- One basic meat product.
- One manual processing station.
- One grinder.
- One packaging station.
- Version 0.8.0 provides the Packaging Table block and inventory foundation only; packaged-product gameplay remains future core work.
- One simple refrigerated storage system.
- One customer order.
- One basic employee job.
- Basic cleanliness tracking.
- Basic MCDA inspection with escalation data model.
- Basic business ledger, reputation foundation, and unlock state.
- Core config presets for forgiving and realistic play.
- Tags and data hooks for expansion products and machines.

ButcherCraft Core should avoid deep late-game realism until the basic loop is stable.

Dependency boundaries:

- ButcherCraft Core has no dependency on expansions.
- ButcherCraft Core owns persistence formats and public API contracts.
- ButcherCraft Core exposes extension points through documented APIs, tags, datapack registries when ready, and selected capabilities.
- ButcherCraft Core does not hard-code expansion content ids except for optional integration checks.

## ButcherCraft Harvest & Fabrication

Purpose: add upstream preparation and product variety without graphic visuals.

Possible content:

- Abstract harvest inputs represented as Minecraft-style items and blocks.
- Additional manual fabrication stations.
- Cut types, trim, primal/sub-primal style product forms using non-graphic art.
- More yield and quality decisions.
- Compatibility tags for vanilla and other mod food inputs.
- Employee roles focused on fabrication.

Dependency boundary:

- Depends on core product and processing APIs.
- Registers new products, processes, work orders, and station blocks.
- Does not own business ledger, MCDA escalation, or refrigeration simulation.
- Should not require ButcherCraft Refrigeration, though its products should benefit from better refrigeration when present.

Reason not to merge fully into core:

- It can add substantial content volume and recipes after the first loop proves fun.
- It may need separate balance pacing for players who want more upstream detail.

## ButcherCraft Refrigeration

Purpose: deepen cold storage into scalable facility engineering.

Possible content:

- Walk-in cooler and freezer multiblocks.
- Compressors.
- Condensers.
- Evaporators.
- Insulated panels, doors, shelves, and sensors.
- Cooling capacity simulation by room volume and target temperature.
- Freezer-specific capacity demand.
- Equipment overload, wear, failure, and maintenance.
- More detailed product temperature tracking.
- Refrigeration employee or maintenance job.

Dependency boundary:

- Depends on core refrigeration API, product temperature components, cleanliness hooks, and facility ids.
- Replaces or extends simple core refrigeration behavior through documented interfaces.
- Does not own customer order generation, finances, or advanced commerce.
- Exposes public refrigeration room summaries for Commerce and MCDA systems.

Reason not to merge fully into core:

- It is a substantial simulation area with performance risk.
- Players may want the food business loop without detailed cold-room engineering.

## ButcherCraft Further Processing

Purpose: add higher-value prepared products and additional machine chains.

Possible content:

- Sausage-style products, patties, cured or seasoned products, smoked products, cooked products, or ready-to-sell packaged lines.
- Mixers, stuffers, smokers, slicers, ovens, curing racks, and related stations.
- Additional packaging types and labels.
- Longer processing times and more quality decisions.
- New cleanliness and inspection risks tied to more complex processing.

Dependency boundary:

- Depends on core product, processing, packaging, cleanliness, and work-order APIs.
- May optionally integrate with ButcherCraft Refrigeration for stricter storage requirements.
- Does not own base employee AI or base order persistence.

Reason not to create one mod per machine:

- These machines are a coherent product-family expansion.
- Shared recipes, product definitions, assets, and balancing should live together.

## ButcherCraft Commerce

Purpose: deepen business management after the production loop is stable.

Possible content:

- Retail customer variety.
- Wholesale contracts.
- Recurring orders.
- Delivery logistics and refrigerated shipping.
- More detailed finances.
- Reputation tiers and customer loyalty.
- Market price variation.
- Order clerks or sales employees.
- Advanced MCDA records, reinspection scheduling, and compliance planning.

Dependency boundary:

- Depends on core business, order, customer, product quality, and facility APIs.
- May optionally read refrigeration summaries if ButcherCraft Refrigeration is installed.
- Does not own low-level processing recipes or machine implementations.
- Should continue to accept products from Harvest/Fabrication and Further-Processing through core product APIs.

Reason not to merge fully into core:

- Advanced finance and logistics can overwhelm the first playable experience.
- Commerce needs mature product and quality systems before deeper balancing is useful.

## Optional Integration Rules

- Expansions communicate through core APIs, tags, datapack registries, and documented capabilities.
- Optional cross-expansion behavior must be guarded by mod-loaded checks and null-safe API calls.
- Missing expansion data should never corrupt or delete saved core data.
- Public APIs intended for expansions must be documented before expansion code depends on them.
- ButcherCraft Core should provide graceful fallback behavior when an expansion is removed from a save.

## Proposed Dependency Graph

```text
ButcherCraft Harvest & Fabrication -> ButcherCraft Core
ButcherCraft Refrigeration           -> ButcherCraft Core
ButcherCraft Further Processing      -> ButcherCraft Core
ButcherCraft Commerce                -> ButcherCraft Core

Optional integrations:
ButcherCraft Commerce -> ButcherCraft Refrigeration summaries, when present
ButcherCraft Further Processing -> ButcherCraft Refrigeration storage requirements, when present
ButcherCraft Harvest & Fabrication -> ButcherCraft Commerce demand categories, when present
```

No expansion should require all other expansions unless a future major release explicitly changes the product strategy.
