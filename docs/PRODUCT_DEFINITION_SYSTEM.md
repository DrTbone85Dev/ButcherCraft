# ButcherCraft Product Definition System

Status: v0.7.0 datapack-backed product registry foundation

## Purpose

The Product Definition System gives stable product ids an authoritative pure Java source of descriptive data. It is separate from Minecraft ItemStack product snapshots, existing datapack-backed processing product definitions, and temporary development item mappings.

This foundation exists so transformation input and output product ids can be validated against product definitions without embedding product definition objects inside `TransformationDefinition`. Version 0.6.9 makes the current product definitions datapack-backed while keeping the pure product model independent of Minecraft. Version 0.7.0 expands the bundled product catalog for beef fabrication through the same datapack-backed path.

## Package

The pure product definition domain lives under:

```text
com.butchercraft.product.definition
```

This package must not import Minecraft or NeoForge classes.

Product serialization and datapack loading live under:

```text
com.butchercraft.product.serialization
com.butchercraft.product.datapack
```

These packages also remain free of Minecraft and NeoForge imports. Minecraft reload integration lives under `com.butchercraft.integration.datapack`.

## Product Schema

`ProductDefinition` is immutable and includes:

- Stable `EngineId`.
- Nonblank display name.
- Positive schema version.
- Typed `ProductCategory`.
- Default `QuantityUnit`.
- Immutable tags keyed by `EngineId`.
- Immutable metadata keyed by `EngineId`.

The builder is the preferred construction API. Construction rejects incomplete definitions, blank display names, invalid schema versions, null tags, null metadata, and blank metadata values.

Metadata follows the same philosophy as transformation metadata: typed keys, simple string values, immutable storage, and no unrestricted object payloads.

## Product Registry

`ProductRegistryBuilder` is the mutable construction boundary.

`ProductRegistry` is immutable after build and supports:

- `contains`
- `find`
- `size`
- `stream`
- `findByCategory`
- `findByTag`

Registration and query streams preserve insertion order. Duplicate product ids are rejected during building.

## Serialized Product Schema

Version 0.6.9 adds the canonical serialized product representation:

- `schema_version`
- `id`
- `display_name`
- `category`
- `default_quantity_unit`
- `tags`
- `metadata`

The JSON form is documented in `docs/DATAPACK_PRODUCTS.md`.

## Built-In Products

Version 0.6.4 introduced the products used by the Grinder transformation proof:

```text
butchercraft:beef_trim
butchercraft:ground_beef
butchercraft:pork_trim
butchercraft:ground_pork
butchercraft:bison_trim
butchercraft:ground_bison
```

Version 0.6.7 added the minimum current Bandsaw proof products:

```text
butchercraft:beef_forequarter
butchercraft:beef_chuck
butchercraft:beef_rib
butchercraft:beef_packer_brisket
butchercraft:beef_plate
butchercraft:beef_shank
butchercraft:beef_fat
butchercraft:beef_bone
```

Version 0.6.9 moves these definitions into bundled content snapshot JSON resources under `data/butchercraft/butchercraft/content/product`. This path is separate from the existing Minecraft datapack registry path `data/butchercraft/butchercraft/product`, which continues to use the richer processing `ProductDefinition` codec. All content snapshot products use `gram` as the default quantity unit. Trim products use the `butchercraft:trait/trim` tag. Ground products use the `butchercraft:trait/ground` tag. Bandsaw proof products use the minimum forequarter, primal, fat, and bone tags needed for registry queries and validation.

Version 0.7.0 adds the first beef fabrication expansion products:

```text
butchercraft:beef_hindquarter
butchercraft:beef_round
butchercraft:beef_sirloin
butchercraft:beef_short_loin
butchercraft:beef_flank
butchercraft:t_bone_steak
butchercraft:porterhouse_steak
butchercraft:beef_strip_loin
butchercraft:beef_tenderloin
butchercraft:top_round
butchercraft:bottom_round
butchercraft:eye_of_round
butchercraft:sirloin_tip
butchercraft:top_sirloin
butchercraft:sirloin_steak
butchercraft:tri_tip
```

These remain descriptive product definitions only. Java development fixture items and mappings are still separate Minecraft-facing bridge data.

## Transformation Validation

Transformation definitions continue to reference product ids through `EngineId` values. They do not embed `ProductDefinition` instances and do not require a `ProductRegistry` during construction.

`TransformationProductReferenceValidator` provides the separate deterministic validation step. It reports missing input products, missing output products, input unit mismatches, and output unit mismatches in definition order.

This separation allows future serialization to decode transformation definitions before all registries are assembled, then validate references once registries are available.

Version 0.6.8 uses this registry to reject transformation datapack resources that reference unknown products. Version 0.6.9 validates those transformation references against the candidate product registry assembled during the same reload, then activates product and transformation registries together.

## Out Of Scope

This slice does not add codecs tied to Minecraft registries, product-to-item mapping, spoilage, quality expansion, packaging states, storage rules, recipe selection, or other workstation migrations.

## Remaining Work

After v0.7.0, product definitions have a pure Java serialization contract, datapack loading path, and bounded beef fabrication proof catalog. The project still needs:

- Metadata key policy.
- Schema migration rules.
- Datapack-driven category catalogs or category validation ownership.
- A clear relationship between pure product definitions and existing datapack-backed processing product definitions.
