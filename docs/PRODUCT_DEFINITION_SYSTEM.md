# ButcherCraft Product Definition System

Status: v0.6.4 pure Java foundation

## Purpose

The Product Definition System gives stable product ids an authoritative pure Java source of descriptive data. It is separate from Minecraft ItemStack product snapshots, existing datapack-backed processing product definitions, and temporary development item mappings.

This foundation exists so transformation input and output product ids can be validated against product definitions without embedding product definition objects inside `TransformationDefinition`.

## Package

The pure product definition domain lives under:

```text
com.butchercraft.product.definition
```

This package must not import Minecraft or NeoForge classes.

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

## Built-In Grinder Products

Version 0.6.4 registers only the products currently used by the Grinder transformation proof:

```text
butchercraft:beef_trim
butchercraft:ground_beef
butchercraft:pork_trim
butchercraft:ground_pork
butchercraft:bison_trim
butchercraft:ground_bison
```

All use `gram` as the default quantity unit. Trim products use the `butchercraft:trait/trim` tag. Ground products use the `butchercraft:trait/ground` tag.

## Transformation Validation

Transformation definitions continue to reference product ids through `EngineId` values. They do not embed `ProductDefinition` instances and do not require a `ProductRegistry` during construction.

`TransformationProductReferenceValidator` provides the separate deterministic validation step. It reports missing input products, missing output products, input unit mismatches, and output unit mismatches in definition order.

This separation allows future serialization to decode transformation definitions before all registries are assembled, then validate references once registries are available.

## Out Of Scope

This slice does not add serialization, codecs, JSON files, datapack loading, reload listeners, product-to-item mapping, spoilage, quality expansion, packaging states, storage rules, recipe selection, or other workstation migrations.

## Before Datapack Integration

After v0.6.5, transformation definitions have a pure Java serialization contract. Product definitions still need their own external schema before product datapack integration can move onto the pure product registry.

For product definitions and datapack integration, the project still needs:

- Stable serialized field names.
- Metadata key policy.
- Schema migration rules.
- External validation error reporting.
- Registry assembly order.
- A clear relationship between pure product definitions and existing datapack-backed processing product definitions.
