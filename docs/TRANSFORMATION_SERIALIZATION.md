# ButcherCraft Transformation Serialization

Status: v0.6.5 pure Java serialization foundation

## Purpose

The Transformation Serialization layer establishes the stable external schema contract for future `TransformationDefinition` datapack loading without adding datapack loading yet.

It answers one narrow question:

```text
How is a canonical TransformationDefinition represented outside the live domain model?
```

The layer is intentionally pure Java. It must not import Minecraft, NeoForge, registry access, datapack reload types, ItemStacks, block entities, menus, screens, or resource-discovery APIs.

## Package

Serialization contracts live under:

```text
com.butchercraft.transformation.serialization
```

The package depends on the pure transformation and engine value objects only.

## Stable External Field Names

The v0.6.5 contract freezes these field names through `TransformationSerializedFieldNames`:

| Field | Meaning |
| --- | --- |
| `schema_version` | External transformation schema version. |
| `id` | Stable transformation id. |
| `display_name` | Human-readable display name. |
| `required_capability` | Optional workstation capability required by the transformation. |
| `inputs` | Ordered required input entries. |
| `outputs` | Ordered produced output entries. |
| `duration` | Transformation duration container. |
| `yield` | Declared yield ratio container. |
| `metadata` | Immutable metadata key/value entries. |
| `product_id` | Input or output product id. |
| `quantity` | Exact product quantity amount. |
| `unit` | Product quantity unit id. |
| `classification` | Output classification. |
| `milliseconds` | Duration in milliseconds. |
| `numerator` | Yield numerator. |
| `denominator` | Yield denominator. |

These names are the external contract for future file formats. A later JSON or datapack layer should map directly to these names rather than inventing loader-local aliases.

## Canonical Representation

`SerializedTransformationDefinition` represents every current `TransformationDefinition` field:

- `TransformationSchemaVersion schemaVersion`
- `String id`
- `String displayName`
- `Optional<String> requiredCapability`
- ordered `SerializedTransformationInput` list
- ordered `SerializedTransformationOutput` list
- `SerializedTransformationDuration duration`
- `SerializedTransformationYield yield`
- ordered immutable metadata map

Input and output entries use `SerializedTransformationAmount` so product id, quantity, and unit round-trip together. Output entries also store the serialized output classification.

The canonical serializer and deserializer are:

- `CanonicalTransformationDefinitionSerializer`
- `CanonicalTransformationDefinitionDeserializer`

They round-trip through pure Java records, not JSON, codecs, datapack registries, or resource locations.

## Schema Versioning

`TransformationSchemaVersion` wraps the positive external schema version value.

The current schema version mirrors `TransformationDefinition.CURRENT_SCHEMA_VERSION`. The deserializer accepts only `TransformationSchemaVersion.CURRENT` in v0.6.5. Unsupported versions fail clearly until a migration implementation exists.

`TransformationDefinitionMigration` defines the future migration shape:

```text
sourceVersion()
targetVersion()
migrate(serializedDefinition)
```

No migrations are implemented in v0.6.5.

## Validation Behavior

The serialized records perform only structural validation needed to keep the serialized representation coherent: required objects are non-null, ids and display names are not blank, required capability values are not blank when present, output classifications are not blank, and metadata keys and values are not blank.

Full transformation validation remains owned by `TransformationDefinition` construction during deserialization. This means invalid units, unsupported classifications, incomplete definitions, duplicate inputs or outputs, invalid durations, invalid yields, and inconsistent declared output totals still fail through the canonical domain rules.

Product-reference validation remains separate. A serialized or deserialized transformation can be validated later through `TransformationProductReferenceValidator` against a `ProductRegistry`.

## Built-In Grinder Compatibility

The built-in Grinder transformations remain Java-defined and registry-backed in v0.6.5:

```text
butchercraft:grind_beef
butchercraft:grind_pork
butchercraft:grind_bison
```

Tests prove all three round-trip through the canonical serialization layer without changing ids, display names, required capability, inputs, outputs, duration, yield, or metadata.

## Out Of Scope

This milestone does not add:

- Datapack loading.
- Resource reload listeners.
- JSON resource discovery.
- Minecraft codecs or `ResourceLocation` usage in the serialization package.
- Automatic schema migration execution.
- Product definition serialization.
- Product-to-item mapping.
- Bandsaw, smoker, packaging, cooler, menu, screen, or item data component migration.
- Public expansion APIs.

## Next Work Before Datapacks

Before transformation datapack integration, the project still needs:

- A concrete JSON codec or parser that maps to the frozen field names.
- Datapack resource path rules.
- Reload-scoped error reporting for malformed external definitions.
- Migration lookup and execution rules.
- Registry assembly ordering.
- Product-reference validation against `ProductRegistry`.
- Capability-reference validation against workstation capability data.
