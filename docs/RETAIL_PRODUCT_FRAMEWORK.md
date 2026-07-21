# ButcherCraft Retail Product Framework

Status: v0.8.0 Sprint C data foundation

## Purpose

The Retail Product Framework establishes datapack-backed definitions for packaged retail products without implementing packaging gameplay. It gives future packaging work an authoritative content layer while the Packaging Table remains an inventory-only workstation foundation. Sprint C adds physical packaging supply items and optional supply references on packaging definitions, but still does not execute packaging.

This sprint adds:

- A pure Java `PackagingDefinition` model, serializer, deserializer, datapack loader, immutable registry, and registry service.
- A packaging registry inside the active `ContentSnapshot` so product, packaging, and transformation registries activate atomically.
- Optional packaging metadata on canonical product definitions.
- Four built-in packaging definitions: `butchercraft:retail_package`, `butchercraft:vacuum_package`, `butchercraft:butcher_paper_package`, and `butchercraft:freezer_paper_package`.
- One built-in retail product definition: `butchercraft:retail_ground_beef`.
- One processing graph operation: `butchercraft:package_retail`.
- Six registered packaging supply items documented in `docs/PACKAGING_SUPPLIES.md`.

It does not add recipes, execution, supply consumption, labels, weights, freshness, spoilage, dynamic textures, overlays, business logic, GUI changes, sounds, or animations.

## Packaging Definition Schema

`PackagingDefinition` is immutable and includes:

- Stable `EngineId`.
- Nonblank display name.
- Positive schema version.
- Typed `PackagingFormat`.
- Default `QuantityUnit`.
- Immutable required supply item ids.
- Immutable compatible product categories.
- Immutable compatible product tags.
- Immutable metadata keyed by `EngineId`.

At least one compatibility rule is required. A packaging definition may match by category, tag, or both. Required supply items are descriptive until packaging execution is scheduled. The built-in retail package matches red-meat categories and the `butchercraft:trait/ground` source-product tag.

## Resource Path

Packaging definition JSON resources live under:

```text
data/<namespace>/butchercraft/content/packaging/<path>.json
```

The bundled proof resources are:

```text
data/butchercraft/butchercraft/content/packaging/retail_package.json
data/butchercraft/butchercraft/content/packaging/vacuum_package.json
data/butchercraft/butchercraft/content/packaging/butcher_paper_package.json
data/butchercraft/butchercraft/content/packaging/freezer_paper_package.json
```

## JSON Shape

```json
{
  "schema_version": 1,
  "id": "butchercraft:retail_package",
  "display_name": "Retail Package",
  "format": "tray_wrap",
  "default_quantity_unit": "gram",
  "required_supply_items": [
    "butchercraft:foam_tray",
    "butchercraft:plastic_wrap_roll"
  ],
  "compatible_categories": [
    "butchercraft:beef",
    "butchercraft:pork",
    "butchercraft:bison"
  ],
  "compatible_tags": [
    "butchercraft:trait/ground"
  ],
  "metadata": {
    "butchercraft:schema/source": "built_in"
  }
}
```

## Product Packaging Metadata

Canonical product JSON may now include an optional `packaging` object:

```json
{
  "packaging": {
    "definition": "butchercraft:retail_package",
    "source_product": "butchercraft:ground_beef"
  }
}
```

Existing products may omit this field and continue to load unchanged.

The bundled retail product is:

```text
butchercraft:retail_ground_beef
```

It references `butchercraft:retail_package` and `butchercraft:ground_beef`, uses the beef category, uses grams, and carries the `butchercraft:trait/retail_packaged` tag.

## Atomic Reload

`ContentSnapshotService` now assembles candidate registries in this order:

1. Product definitions.
2. Packaging definitions.
3. Product packaging metadata validation against the candidate product and packaging registries.
4. Transformation definitions, validated against the candidate product registry.
5. Atomic activation of the candidate product, packaging, and transformation registries.

If any step fails, the previously active snapshot remains active.

## Processing Graph

The processing registry includes:

```text
butchercraft:ground_beef --butchercraft:package_retail--> butchercraft:retail_ground_beef
```

The operation uses:

- Operation category: `butchercraft:operation_category/packaging`.
- Workstation capability: `butchercraft:packaging`.
- Duration: `3000` milliseconds.
- Yield: `1/1`.
- Quantity unit: `gram`.

This operation is graph content only in this sprint. The Packaging Table does not create a processing controller, does not execute the operation, and has no transformation definition for packaging.

## Validation

Packaging datapack loading rejects malformed roots, missing IDs, missing display names, duplicate IDs, unsupported schema versions, unknown formats, unknown categories, unknown quantity units, malformed required supplies, unknown supply items, malformed category arrays, malformed tags, malformed metadata, and domain construction failures.

Product packaging metadata validation rejects unknown packaging definitions, unknown source products, self-references, category or unit mismatches, and source products that are incompatible with the selected packaging definition.

## Out Of Scope

This framework intentionally excludes packaging recipes, packaging execution, supply consumption, labels, weights, freshness, spoilage, vacuum packaging gameplay, dynamic textures, overlay rendering, business logic, GUI changes, sounds, animations, and item factory behavior.
