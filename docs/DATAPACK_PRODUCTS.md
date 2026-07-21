# ButcherCraft Datapack Products

Status: v0.6.9 datapack product loading foundation

## Purpose

Datapack product loading makes the immutable `ProductRegistry` data-driven without changing ItemStack product data, development product item mappings, Grinder behavior, or Bandsaw behavior.

Version 0.6.9 loads product JSON resources, maps them to canonical serialized product records, deserializes through the canonical product deserializer, and assembles products and transformations as one validated content snapshot.

## Resource Path

Product files live under:

```text
data/<namespace>/butchercraft/product/<path>.json
```

The bundled ButcherCraft resources cover the current Grinder and Bandsaw proof products:

```text
data/butchercraft/butchercraft/product/beef_trim.json
data/butchercraft/butchercraft/product/ground_beef.json
data/butchercraft/butchercraft/product/pork_trim.json
data/butchercraft/butchercraft/product/ground_pork.json
data/butchercraft/butchercraft/product/bison_trim.json
data/butchercraft/butchercraft/product/ground_bison.json
data/butchercraft/butchercraft/product/beef_forequarter.json
data/butchercraft/butchercraft/product/beef_chuck.json
data/butchercraft/butchercraft/product/beef_rib.json
data/butchercraft/butchercraft/product/beef_packer_brisket.json
data/butchercraft/butchercraft/product/beef_plate.json
data/butchercraft/butchercraft/product/beef_shank.json
data/butchercraft/butchercraft/product/beef_fat.json
data/butchercraft/butchercraft/product/beef_bone.json
```

## JSON Shape

The JSON field names are stable for the current product schema:

```json
{
  "schema_version": 1,
  "id": "butchercraft:beef_trim",
  "display_name": "Beef Trim",
  "category": "butchercraft:beef",
  "default_quantity_unit": "gram",
  "tags": [
    "butchercraft:trait/trim"
  ],
  "metadata": {
    "butchercraft:schema/source": "built_in"
  }
}
```

`metadata` may be omitted. `tags` is required and may be empty when a future product has no tags.

## Loader Pipeline

1. The Minecraft reload listener gathers JSON resources from the ButcherCraft content subtree.
2. Product resources are routed to `ProductJsonDefinitionParser`.
3. JSON is mapped to `SerializedProductDefinition`.
4. `CanonicalProductDefinitionDeserializer` creates immutable `ProductDefinition` instances.
5. `ProductDatapackLoader` validates duplicates, identity fields, schema versions, categories, quantity units, tags, metadata, and domain construction rules.
6. A candidate immutable `ProductRegistry` is built in resource order.
7. Transformations load only after product loading succeeds.
8. Transformation product references validate against the candidate product registry.
9. `ContentSnapshotService` activates the candidate product and transformation registries together.

The pure product definition, product serialization, product datapack, and transformation packages do not import Minecraft or NeoForge classes. The reload listener is the Minecraft-facing bridge.

## Validation Errors

Product datapack loading reports structured errors with a source, optional product id, stable code, and message.

Current product error codes:

- `MALFORMED_JSON`
- `MALFORMED_DEFINITION`
- `DUPLICATE_ID`
- `MISSING_ID`
- `MISSING_DISPLAY_NAME`
- `UNKNOWN_CATEGORY`
- `UNKNOWN_QUANTITY_UNIT`
- `UNSUPPORTED_SCHEMA_VERSION`
- `MALFORMED_TAGS`
- `MALFORMED_METADATA`

Content snapshot loading also includes transformation datapack errors when products are valid but transformations fail.

## Atomic Snapshot Behavior

Product and transformation registries are activated as one immutable `ContentSnapshot`.

Failed product loading prevents transformation loading. Failed transformation loading rejects the full snapshot. In both cases, the previously active product registry and transformation registry remain active.

## Compatibility

Product-to-ItemStack mappings remain Java-controlled development fixtures. Datapacks do not dynamically register Minecraft items, change creative tab entries, or create item models.

The Grinder and Bandsaw still resolve processing operations through existing processing definitions and workstation controllers. Version 0.6.9 changes how product definitions reach the pure registry, not how workstations behave.

## Out Of Scope

This milestone does not add:

- Dynamic Minecraft item registration.
- Product-to-ItemStack factories.
- Datapack-driven category catalogs.
- Schema migrations.
- Expanded fabrication catalogs.
- Spoilage, quality expansion, packaging states, storage rules, or recipe-selection UI.
- Smoker, packaging, cooler, or other workstation migrations.
- Public expansion APIs.

## Remaining Work

Before expanded fabrication, ButcherCraft still needs larger product catalogs, product-to-item creation rules, schema migration behavior, category/catalog ownership, reload-scoped user-facing diagnostics, and manual in-game reload validation for custom datapacks.
