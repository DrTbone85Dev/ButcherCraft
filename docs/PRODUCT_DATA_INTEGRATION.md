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
- Optional `packaging`: stack-level packaging metadata for packaged products.

Not stored yet:

- Temperature.
- Freshness.
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

Processing state ids are open engine identifiers so datapack definitions can introduce states such as `butchercraft:forequarter`, `butchercraft:hindquarter`, `butchercraft:primal`, `butchercraft:subprimal`, `butchercraft:steak`, `butchercraft:fat`, and `butchercraft:bone` without changing an engine enum.

## StreamCodec Strategy

`ProductStackData.STREAM_CODEC` writes the same field set for network synchronization where ItemStacks synchronize.

The stream codec reconstructs the immutable record through the same constructor validation. Invalid stream data therefore fails fast instead of creating unrelated product data.

## Packaging Metadata

Packaged product stacks may carry optional `ProductStackPackagingData`.

Stored fields:

- `packaging_definition_id`: stable id such as `butchercraft:retail_package`.
- `packaging_format_id`: stable packaging format id such as `tray_wrap`.
- `source_product_id`: stable source product id such as `butchercraft:ground_beef`.

Legacy stacks that omit the `packaging` field still decode with an empty packaging value. The current metadata does not store labels, order ids, price, freshness, spoilage, dynamic texture state, or business data.

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

Thirty-one development-only product fixtures are registered:

- `butchercraft:beef_trim_test`
- `butchercraft:ground_beef_test`
- `butchercraft:retail_ground_beef_test`
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
- `butchercraft:beef_hindquarter_test`
- `butchercraft:beef_round_test`
- `butchercraft:beef_sirloin_test`
- `butchercraft:beef_short_loin_test`
- `butchercraft:beef_flank_test`
- `butchercraft:t_bone_steak_test`
- `butchercraft:porterhouse_steak_test`
- `butchercraft:beef_strip_loin_test`
- `butchercraft:beef_tenderloin_test`
- `butchercraft:top_round_test`
- `butchercraft:bottom_round_test`
- `butchercraft:eye_of_round_test`
- `butchercraft:sirloin_tip_test`
- `butchercraft:top_sirloin_test`
- `butchercraft:sirloin_steak_test`
- `butchercraft:tri_tip_test`

They appear in the ButcherCraft creative tab and receive default product data from `ProductTestItem.getDefaultInstance()`.

Default values are test fixtures, not final balance:

| Item | Product type | Source | State | Quantity | Quality |
| --- | --- | --- | --- | --- | --- |
| Beef Trim Test Product | `butchercraft:beef_trim` | `butchercraft:beef` | `butchercraft:trim` | `1000 gram` | `700` |
| Ground Beef Test Product | `butchercraft:ground_beef` | `butchercraft:beef` | `butchercraft:ground` | `900 gram` | `700` |
| Retail Ground Beef Test Product | `butchercraft:retail_ground_beef` | `butchercraft:beef` | `butchercraft:retail_packaged` | `900 gram` | `700` |
| Pork Trim Test Product | `butchercraft:pork_trim` | `butchercraft:pork` | `butchercraft:trim` | `1000 gram` | `700` |
| Ground Pork Test Product | `butchercraft:ground_pork` | `butchercraft:pork` | `butchercraft:ground` | `900 gram` | `700` |
| Bison Trim Test Product | `butchercraft:bison_trim` | `butchercraft:bison` | `butchercraft:trim` | `1000 gram` | `700` |
| Ground Bison Test Product | `butchercraft:ground_bison` | `butchercraft:bison` | `butchercraft:ground` | `900 gram` | `700` |
| Beef Forequarter Test Product | `butchercraft:beef_forequarter` | `butchercraft:beef` | `butchercraft:forequarter` | `100000 gram` | `700` |
| Beef Chuck Test Product | `butchercraft:beef_chuck` | `butchercraft:beef` | `butchercraft:primal` | `30000 gram` | `695` |
| Beef Rib Test Product | `butchercraft:beef_rib` | `butchercraft:beef` | `butchercraft:primal` | `10000 gram` | `695` |
| Packer Brisket Test Product | `butchercraft:beef_packer_brisket` | `butchercraft:beef` | `butchercraft:primal` | `10000 gram` | `695` |
| Beef Plate Test Product | `butchercraft:beef_plate` | `butchercraft:beef` | `butchercraft:primal` | `10000 gram` | `695` |
| Beef Shank Test Product | `butchercraft:beef_shank` | `butchercraft:beef` | `butchercraft:primal` | `5000 gram` | `695` |
| Beef Fat Test Product | `butchercraft:beef_fat` | `butchercraft:beef` | `butchercraft:fat` | `5000 gram` | `695` |
| Beef Bone Test Product | `butchercraft:beef_bone` | `butchercraft:beef` | `butchercraft:bone` | `10000 gram` | `695` |
| Beef Hindquarter Test Product | `butchercraft:beef_hindquarter` | `butchercraft:beef` | `butchercraft:hindquarter` | `100000 gram` | `700` |
| Beef Round Test Product | `butchercraft:beef_round` | `butchercraft:beef` | `butchercraft:primal` | `30000 gram` | `695` |
| Beef Sirloin Test Product | `butchercraft:beef_sirloin` | `butchercraft:beef` | `butchercraft:primal` | `15000 gram` | `695` |
| Beef Short Loin Test Product | `butchercraft:beef_short_loin` | `butchercraft:beef` | `butchercraft:primal` | `15000 gram` | `695` |
| Beef Flank Test Product | `butchercraft:beef_flank` | `butchercraft:beef` | `butchercraft:primal` | `7500 gram` | `695` |
| T-Bone Steak Test Product | `butchercraft:t_bone_steak` | `butchercraft:beef` | `butchercraft:steak` | `4000 gram` | `695` |
| Porterhouse Steak Test Product | `butchercraft:porterhouse_steak` | `butchercraft:beef` | `butchercraft:steak` | `3000 gram` | `695` |
| Beef Strip Loin Test Product | `butchercraft:beef_strip_loin` | `butchercraft:beef` | `butchercraft:subprimal` | `3000 gram` | `695` |
| Beef Tenderloin Test Product | `butchercraft:beef_tenderloin` | `butchercraft:beef` | `butchercraft:subprimal` | `2000 gram` | `695` |
| Top Round Test Product | `butchercraft:top_round` | `butchercraft:beef` | `butchercraft:subprimal` | `7500 gram` | `695` |
| Bottom Round Test Product | `butchercraft:bottom_round` | `butchercraft:beef` | `butchercraft:subprimal` | `6500 gram` | `695` |
| Eye of Round Test Product | `butchercraft:eye_of_round` | `butchercraft:beef` | `butchercraft:subprimal` | `3500 gram` | `695` |
| Sirloin Tip Test Product | `butchercraft:sirloin_tip` | `butchercraft:beef` | `butchercraft:subprimal` | `5000 gram` | `695` |
| Top Sirloin Test Product | `butchercraft:top_sirloin` | `butchercraft:beef` | `butchercraft:subprimal` | `5000 gram` | `695` |
| Sirloin Steak Test Product | `butchercraft:sirloin_steak` | `butchercraft:beef` | `butchercraft:steak` | `3500 gram` | `695` |
| Tri-Tip Test Product | `butchercraft:tri_tip` | `butchercraft:beef` | `butchercraft:subprimal` | `2000 gram` | `695` |

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
- Packaging definition and format when present.

Shown with advanced tooltips:

- Internal quality score.
- Packaging source product when present.

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
- Temperature, freshness, labels, or order metadata.
- Employees, refrigeration, cleanliness, MCDA, customers, business systems, sounds, or final artwork.

## Future Extension Boundary

Temperature, freshness, labels, batch identity, traceability, and order-bound metadata should be added as separate component records or carefully versioned extensions after their gameplay rules exist.

Do not expand `ProductStackData` into a catch-all metadata blob. Each future component needs clear ownership, validation, migration behavior, tooltip rules, and synchronization cost.

Milestone 2A adds `ProductStackDefinitionValidator`, which compares immutable `ProductStackData` against loaded `ProductDefinition` data without mutating the stack. It checks product existence, species/source compatibility, processing state, quantity unit, and quality bounds. This is a validation bridge only; it does not add freshness, temperature, packaging, station processing, or inventory transactions.

Milestone 2B adds station processing around existing product stacks but does not expand `ProductStackData`. Workstation progress, selected operation, failure state, and reserved input snapshots belong to the block entity/controller boundary, not to item components.

Milestone 2E keeps the same component shape while allowing data-driven processing state ids beyond the original trim and ground states. Version 0.7.0 adds more product fixture defaults but still does not expand the component schema. Multi-output workstation results create separate product-bearing stacks; output order and quantity come from processing definitions and the engine transaction, not from `ProductStackData`.

Version 0.8.0 Sprint D adds optional stack-level packaging metadata. Packaging Table output writes only packaging definition id, packaging format id, and source product id while preserving the engine product fields created by the processing operation.
