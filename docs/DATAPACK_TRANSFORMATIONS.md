# ButcherCraft Datapack Transformations

Status: v0.6.9 transformation loading within atomic content snapshots

## Purpose

Datapack transformation loading makes the immutable `TransformationRegistry` data-driven without changing the pure evaluator, executor, transaction engine, Grinder, or Bandsaw behavior.

Version 0.6.8 loads transformation JSON resources, maps them to the existing serialized schema records, deserializes through the canonical transformation deserializer, and validates references. Version 0.6.9 loads transformations after candidate products and activates the product registry and transformation registry together only after the full content snapshot succeeds.

## Resource Path

Transformation files live under:

```text
data/<namespace>/butchercraft/transformation/<path>.json
```

The bundled ButcherCraft resources are:

```text
data/butchercraft/butchercraft/transformation/grind_beef.json
data/butchercraft/butchercraft/transformation/grind_pork.json
data/butchercraft/butchercraft/transformation/grind_bison.json
data/butchercraft/butchercraft/transformation/break_beef_forequarter.json
```

## JSON Shape

The JSON field names match the stable external serialization contract:

```json
{
  "schema_version": 1,
  "id": "butchercraft:grind_beef",
  "display_name": "Grind Beef",
  "required_capability": "butchercraft:grinding",
  "inputs": [
    {
      "product_id": "butchercraft:beef_trim",
      "quantity": 100,
      "unit": "gram"
    }
  ],
  "outputs": [
    {
      "product_id": "butchercraft:ground_beef",
      "quantity": 90,
      "unit": "gram",
      "classification": "primary"
    }
  ],
  "duration": {
    "milliseconds": 3000
  },
  "yield": {
    "numerator": 9,
    "denominator": 10
  },
  "metadata": {
    "butchercraft:schema/source": "built_in"
  }
}
```

`metadata` may be omitted. `required_capability` may be omitted for future capability-free definitions, but current Grinder and Bandsaw definitions require capabilities.

## Loader Pipeline

1. The Minecraft reload listener gathers JSON resources from the ButcherCraft content subtree.
2. `TransformationJsonDefinitionParser` maps JSON into `SerializedTransformationDefinition`.
3. `CanonicalTransformationDefinitionDeserializer` creates immutable `TransformationDefinition` instances.
4. `TransformationDatapackLoader` validates duplicate ids, product references, capabilities, schema versions, and domain construction rules against the candidate product registry from the same reload.
5. A new immutable `TransformationRegistry` is built in resource order.
6. `ContentSnapshotService` swaps the active product and transformation registries together only when loading succeeds.

The pure transformation model does not import Minecraft or NeoForge classes. The reload listener is the Minecraft-facing bridge.

## Validation Errors

Datapack loading reports structured errors with a source, optional transformation id, stable code, and message.

Current error codes:

- `MALFORMED_JSON`
- `MALFORMED_DEFINITION`
- `DUPLICATE_ID`
- `UNKNOWN_PRODUCT`
- `UNKNOWN_CAPABILITY`
- `UNSUPPORTED_SCHEMA_VERSION`

Failed reloads do not replace the previously active product registry or transformation registry.

## Migrated Transformations

The following transformations are now bundled datapack resources:

- `butchercraft:grind_beef`
- `butchercraft:grind_pork`
- `butchercraft:grind_bison`
- `butchercraft:break_beef_forequarter`

Their ids, display names, capabilities, inputs, outputs, durations, yields, and metadata match the previous Java-defined built-ins.

## Runtime Behavior

The Grinder and Bandsaw still resolve processing operations through the existing workstation resolver and processing definitions. The resolved operation id is looked up in the active transformation registry at execution time.

No gameplay behavior changes in this slice. The v0.6.8 and v0.6.9 milestones change how definitions reach the registries, not how workstations behave.

## Out Of Scope

This milestone does not add:

- Expanded fabrication catalogs.
- Transformation schema migrations.
- A general product-to-ItemStack factory.
- Recipe-selection UI.
- Smoker, packaging, cooler, or other workstation migrations.
- Public expansion APIs.

## Remaining Work

Before expanded fabrication, ButcherCraft still needs larger cut catalogs, product-to-item creation rules, schema migration behavior, datapack-driven category ownership, reload-scoped user-facing diagnostics, and manual in-game reload validation for custom datapacks.
