# ButcherCraft Product Data Integration

Status: Milestone 1D foundation

## Purpose

Milestone 1D connects the Minecraft-independent engine `Product` model to Minecraft `ItemStack` state through NeoForge data components.

This milestone proves that a stack can store, copy, serialize, synchronize, display, and round-trip a compact ButcherCraft product snapshot. It does not add processing gameplay, blocks, recipes, machines, employees, refrigeration, cleanliness, commerce, customers, or MCDA systems.

## Why Data Components

Product item stacks use NeoForge/Minecraft item data components instead of legacy arbitrary NBT or mutable Java objects.

Reasons:

- Component values are typed.
- Persistent `Codec` serialization is explicit.
- Network `StreamCodec` synchronization is explicit.
- Stack equality naturally includes component values.
- Integration code can validate data before reconstructing an engine `Product`.

The registered component id is:

```text
butchercraft:product_data
```

## ProductStackData Fields

`ProductStackData` is an immutable record under `com.butchercraft.product.component`.

Stored fields:

- `product_type_id`: stable engine product id, such as `butchercraft:beef_trim`.
- `source_category_id`: supported source category id, such as `butchercraft:beef`.
- `processing_state_id`: supported processing state id, such as `butchercraft:trim`.
- `quantity_value`: exact `long` quantity value.
- `quantity_unit_id`: supported engine unit id, such as `gram`.
- `quality_score`: internal quality score from `0` to `1000`.

Not stored yet:

- Temperature.
- Freshness.
- Packaging.
- Batch or lot id.
- Traceability.
- Owner, employee, machine, cleanliness, customer, or business metadata.

Those fields belong to later milestones with separate persistence and migration decisions.

## Codec Strategy

`ProductStackData.CODEC` uses stable named fields and validates decoded data before returning a value.

Invalid decoded data is rejected rather than sanitized. This prevents corrupted or unsupported stack data from silently becoming a different valid product.

Rejected examples include:

- Invalid identifiers.
- Unknown source category ids.
- Invalid processing state identifiers.
- Negative quantity.
- Unsupported quantity units.
- Quality scores below `0` or above `1000`.

Processing state ids are open engine identifiers so datapack definitions can introduce states such as `butchercraft:forequarter`, `butchercraft:primal`, `butchercraft:fat`, and `butchercraft:bone` without changing an engine enum.

## StreamCodec Strategy

`ProductStackData.STREAM_CODEC` writes the same field set for network synchronization where ItemStacks synchronize.

The stream codec reconstructs the immutable record through the same constructor validation. Invalid stream data therefore fails fast instead of creating unrelated product data.

## Conversion Boundary

`ProductStackAdapter` lives under `com.butchercraft.product.integration`.

It converts:

- Engine `Product` to `ProductStackData`.
- `ProductStackData` to engine `Product`.
- Product-bearing `ItemStack` values to and from component data.

Adapter methods return `ProductDataResult<T>` rather than using `null` or boolean-only outcomes. Failures include inspectable codes such as `missing_product_data` and `not_product_item`.

The engine remains Minecraft-independent. All Minecraft and NeoForge imports stay outside `com.butchercraft.engine`.

## Stackability Decision

Product-bearing stacks have maximum stack size `1` for this milestone.

Reason: the engine already stores exact product quantity in `ProductQuantity`. Allowing stack count to represent an additional quantity would create two independent quantity systems and could duplicate, average, or discard quality and quantity during merging.

Future stackability may be revisited only after the project defines precise rules for stack count, quantity, quality, freshness, temperature, packaging, and lot identity.

## Creative-Tab Fixtures

Fourteen development-only product fixtures are registered:

- `butchercraft:beef_trim_test`
- `butchercraft:ground_beef_test`
- `butchercraft:pork_trim_test`
- `butchercraft:ground_pork_test`
- `butchercraft:bison_trim_test`
- `butchercraft:ground_bison_test`
- `butchercraft:beef_forequarter_test`
- `butchercraft:beef_chuck_test`
- `butchercraft:beef_rib_test`
- `butchercraft:beef_packer_brisket_test`
- `butchercraft:beef_plate_test`
- `butchercraft:beef_shank_test`
- `butchercraft:beef_fat_test`
- `butchercraft:beef_bone_test`

They appear in the ButcherCraft creative tab and receive default product data from `ProductTestItem.getDefaultInstance()`.

Default values are test fixtures, not final balance:

| Item | Product type | Source | State | Quantity | Quality |
| --- | --- | --- | --- | --- | --- |
| Beef Trim Test Product | `butchercraft:beef_trim` | `butchercraft:beef` | `butchercraft:trim` | `1000 gram` | `700` |
| Ground Beef Test Product | `butchercraft:ground_beef` | `butchercraft:beef` | `butchercraft:ground` | `900 gram` | `700` |
| Pork Trim Test Product | `butchercraft:pork_trim` | `butchercraft:pork` | `butchercraft:trim` | `1000 gram` | `700` |
| Ground Pork Test Product | `butchercraft:ground_pork` | `butchercraft:pork` | `butchercraft:ground` | `900 gram` | `700` |
| Bison Trim Test Product | `butchercraft:bison_trim` | `butchercraft:bison` | `butchercraft:trim` | `1000 gram` | `700` |
| Ground Bison Test Product | `butchercraft:ground_bison` | `butchercraft:bison` | `butchercraft:ground` | `900 gram` | `700` |
| Beef Forequarter Test Product | `butchercraft:beef_forequarter` | `butchercraft:beef` | `butchercraft:forequarter` | `100000 gram` | `700` |
| Beef Chuck Test Product | `butchercraft:beef_chuck` | `butchercraft:beef` | `butchercraft:primal` | `30000 gram` | `700` |
| Beef Rib Test Product | `butchercraft:beef_rib` | `butchercraft:beef` | `butchercraft:primal` | `10000 gram` | `700` |
| Packer Brisket Test Product | `butchercraft:beef_packer_brisket` | `butchercraft:beef` | `butchercraft:primal` | `10000 gram` | `700` |
| Beef Plate Test Product | `butchercraft:beef_plate` | `butchercraft:beef` | `butchercraft:primal` | `10000 gram` | `700` |
| Beef Shank Test Product | `butchercraft:beef_shank` | `butchercraft:beef` | `butchercraft:primal` | `5000 gram` | `700` |
| Beef Fat Test Product | `butchercraft:beef_fat` | `butchercraft:beef` | `butchercraft:fat` | `5000 gram` | `700` |
| Beef Bone Test Product | `butchercraft:beef_bone` | `butchercraft:beef` | `butchercraft:bone` | `10000 gram` | `700` |

The existing `butchercraft:development_test_item` remains a harmless generic foundation item.

Milestones 2B through 2E use these product fixtures in temporary processing workstations, the Grinder, and the Bandsaw. The development-only mapping derives product definition ids from fixture item default product data; it is still not a general product item factory.

## Tooltip Behavior

Product-bearing items add a concise tooltip that reads component data without mutating the stack.

Shown by default:

- Product identifier.
- Source/species identifier.
- Processing state identifier.
- Quantity.
- Visible quality grade.

Shown with advanced tooltips:

- Internal quality score.

Missing or invalid product data shows a development warning rather than crashing. The tooltip uses common Minecraft item APIs only and does not import `net.minecraft.client` classes.

## Duplication Safeguards

Milestone 1D safeguards are intentionally narrow:

- Product data is immutable.
- Product-bearing stacks are max stack size `1`.
- ItemStack component equality distinguishes different product data.
- Copying an ItemStack preserves the product data component.
- Reading component data does not mutate the stack.
- Writing/removing product data touches only the product component.

Future inventory-moving systems still need server-side transactions before they move, consume, or create products.

## Diagnostics

`/butchercraft diagnostic` now reports:

- Product data component registration.
- Both product test item registrations.
- Fresh test stack round-trip through component data and engine product conversion.
- Quantity preservation.
- Quality preservation.

The diagnostic does not grant items, mutate inventories, modify the world, or expose local environment details.

## Explicit Exclusions

This milestone does not implement:

- Processing stations.
- Grinder behavior.
- Blocks or block entities.
- Menus or screens.
- Recipes or datapack processing operations.
- Networking request payloads.
- Temperature, freshness, or packaging.
- Employees, refrigeration, cleanliness, MCDA, customers, business systems, sounds, or final artwork.

## Future Extension Boundary

Temperature, freshness, packaging, batch identity, traceability, and order-bound metadata should be added as separate component records or carefully versioned extensions after their gameplay rules exist.

Do not expand `ProductStackData` into a catch-all metadata blob. Each future component needs clear ownership, validation, migration behavior, tooltip rules, and synchronization cost.

Milestone 2A adds `ProductStackDefinitionValidator`, which compares immutable `ProductStackData` against loaded `ProductDefinition` data without mutating the stack. It checks product existence, species/source compatibility, processing state, quantity unit, and quality bounds. This is a validation bridge only; it does not add freshness, temperature, packaging, station processing, or inventory transactions.

Milestone 2B adds station processing around existing product stacks but does not expand `ProductStackData`. Workstation progress, selected operation, failure state, and reserved input snapshots belong to the block entity/controller boundary, not to item components.

Milestone 2E keeps the same component shape while allowing data-driven processing state ids beyond the original trim and ground states. Multi-output workstation results create separate product-bearing stacks; output order and quantity come from processing definitions and the engine transaction, not from `ProductStackData`.
