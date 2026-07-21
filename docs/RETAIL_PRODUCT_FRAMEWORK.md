# ButcherCraft Retail Product Framework

Status: v0.8.0 Sprint 2 data foundation

## Purpose

The Retail Product Framework establishes datapack-backed definitions for packaged retail products without implementing packaging gameplay. It gives future packaging work an authoritative content layer while the Packaging Table remains an inventory-only workstation foundation.

This sprint adds:

- A pure Java `PackagingDefinition` model, serializer, deserializer, datapack loader, immutable registry, and registry service.
- A packaging registry inside the active `ContentSnapshot` so product, packaging, and transformation registries activate atomically.
- Optional packaging metadata on canonical product definitions.
- One built-in packaging definition: `butchercraft:retail_package`.
- One built-in retail product definition: `butchercraft:retail_ground_beef`.
- One processing graph operation: `butchercraft:package_retail`.

It does not add recipes, execution, supply consumption, labels, weights, freshness, spoilage, textures, overlays, business logic, GUI changes, sounds, or animations.

## Packaging Definition Schema

`PackagingDefinition` is immutable and includes:

- Stable `EngineId`.
- Nonblank display name.
- Positive schema version.
- Typed `PackagingFormat`.
- Default `QuantityUnit`.
- Immutable compatible product categories.
- Immutable compatible product tags.
- Immutable metadata keyed by `EngineId`.

At least one compatibility rule is required. A packaging definition may match by category, tag, or both. The built-in retail package matches red-meat categories and the `butchercraft:trait/ground` source-product tag.

## Resource Path

Packaging definition JSON resources live under:

```text
data/<namespace>/butchercraft/content/packaging/<path>.json
```

The bundled proof resource is:

```text
data/butchercraft/butchercraft/content/packaging/retail_package.json
```

## JSON Shape

```json
{
  "schema_version": 1,
  "id": "butchercraft:retail_package",
  "display_name": "Retail Package",
  "format": "retail",
  "default_quantity_unit": "gram",
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

Packaging datapack loading rejects malformed roots, missing IDs, missing display names, duplicate IDs, unsupported schema versions, unknown formats, unknown categories, unknown quantity units, malformed category arrays, malformed tags, malformed metadata, and domain construction failures.

Product packaging metadata validation rejects unknown packaging definitions, unknown source products, self-references, category or unit mismatches, and source products that are incompatible with the selected packaging definition.

## Out Of Scope

This framework intentionally excludes packaging recipes, packaging execution, supply consumption, labels, weights, freshness, spoilage, vacuum packaging, dynamic textures, overlay rendering, business logic, GUI changes, sounds, animations, and item factory behavior.
