# ButcherCraft Datapack Products

Status: v0.8.0 Sprint C datapack product loading with retail metadata

## Purpose

Datapack product loading makes the immutable `ProductRegistry` data-driven without changing ItemStack product data, development product item mappings, Grinder behavior, or Bandsaw behavior.

Version 0.6.9 loads product JSON resources, maps them to canonical serialized product records, deserializes through the canonical product deserializer, and assembles products and transformations as one validated content snapshot. Version 0.7.0 expands the bundled product resources with the first multi-stage beef fabrication catalog while preserving the same loading path. Version 0.8.0 Sprint 2 adds optional retail packaging metadata and validates it against the candidate packaging registry before snapshot activation. Sprint C adds packaging supply-reference validation inside the candidate packaging registry stage.

## Resource Path

Product files live under:

```text
data/<namespace>/butchercraft/content/product/<path>.json
```

This path is intentionally separate from the Minecraft datapack registry path
`data/<namespace>/butchercraft/product/<id>.json`, which is reserved for the
existing richer processing `butchercraft:product` registry codec.

The bundled ButcherCraft resources cover the current Grinder, Bandsaw, and retail proof products:

```text
data/butchercraft/butchercraft/content/product/beef_trim.json
data/butchercraft/butchercraft/content/product/ground_beef.json
data/butchercraft/butchercraft/content/product/retail_ground_beef.json
data/butchercraft/butchercraft/content/product/pork_trim.json
data/butchercraft/butchercraft/content/product/ground_pork.json
data/butchercraft/butchercraft/content/product/bison_trim.json
data/butchercraft/butchercraft/content/product/ground_bison.json
data/butchercraft/butchercraft/content/product/beef_forequarter.json
data/butchercraft/butchercraft/content/product/beef_chuck.json
data/butchercraft/butchercraft/content/product/beef_rib.json
data/butchercraft/butchercraft/content/product/beef_packer_brisket.json
data/butchercraft/butchercraft/content/product/beef_plate.json
data/butchercraft/butchercraft/content/product/beef_shank.json
data/butchercraft/butchercraft/content/product/beef_fat.json
data/butchercraft/butchercraft/content/product/beef_bone.json
data/butchercraft/butchercraft/content/product/beef_hindquarter.json
data/butchercraft/butchercraft/content/product/beef_round.json
data/butchercraft/butchercraft/content/product/beef_sirloin.json
data/butchercraft/butchercraft/content/product/beef_short_loin.json
data/butchercraft/butchercraft/content/product/beef_flank.json
data/butchercraft/butchercraft/content/product/t_bone_steak.json
data/butchercraft/butchercraft/content/product/porterhouse_steak.json
data/butchercraft/butchercraft/content/product/beef_strip_loin.json
data/butchercraft/butchercraft/content/product/beef_tenderloin.json
data/butchercraft/butchercraft/content/product/top_round.json
data/butchercraft/butchercraft/content/product/bottom_round.json
data/butchercraft/butchercraft/content/product/eye_of_round.json
data/butchercraft/butchercraft/content/product/sirloin_tip.json
data/butchercraft/butchercraft/content/product/top_sirloin.json
data/butchercraft/butchercraft/content/product/sirloin_steak.json
data/butchercraft/butchercraft/content/product/tri_tip.json
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

Retail products may include optional packaging metadata:

```json
{
  "schema_version": 1,
  "id": "butchercraft:retail_ground_beef",
  "display_name": "Retail Ground Beef",
  "category": "butchercraft:beef",
  "default_quantity_unit": "gram",
  "tags": [
    "butchercraft:trait/retail_packaged"
  ],
  "packaging": {
    "definition": "butchercraft:retail_package",
    "source_product": "butchercraft:ground_beef"
  },
  "metadata": {
    "butchercraft:schema/source": "built_in"
  }
}
```

## Loader Pipeline

1. The Minecraft reload listener gathers JSON resources from the ButcherCraft content subtree.
2. Product resources are routed to `ProductJsonDefinitionParser`.
3. JSON is mapped to `SerializedProductDefinition`.
4. `CanonicalProductDefinitionDeserializer` creates immutable `ProductDefinition` instances.
5. `ProductDatapackLoader` validates duplicates, identity fields, schema versions, categories, quantity units, tags, metadata, and domain construction rules.
6. A candidate immutable `ProductRegistry` is built in resource order.
7. Packaging definitions load only after product loading succeeds.
8. Product packaging metadata validates against the candidate product and packaging registries.
9. Transformations load only after product and packaging validation succeeds.
10. Transformation product references validate against the candidate product registry.
11. `ContentSnapshotService` activates the candidate product, packaging, and transformation registries together.

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
- `MALFORMED_PACKAGING_METADATA`
- `UNKNOWN_PACKAGING_DEFINITION`
- `UNKNOWN_PACKAGING_SOURCE_PRODUCT`
- `PACKAGING_SELF_REFERENCE`
- `PACKAGING_INCOMPATIBLE_PRODUCT`

Content snapshot loading also includes packaging and transformation datapack errors when earlier registry stages are valid but a later stage fails.

## Atomic Snapshot Behavior

Product, packaging, and transformation registries are activated as one immutable `ContentSnapshot`.

Failed product loading prevents packaging and transformation loading. Failed packaging loading, including invalid packaging supply references, or product packaging metadata validation prevents transformation loading. Failed transformation loading rejects the full snapshot. In all cases, the previously active product, packaging, and transformation registries remain active.

## Compatibility

Product-to-ItemStack mappings remain Java-controlled development fixtures. Datapacks do not dynamically register Minecraft items, change creative tab entries, or create item models. Version 0.7.0 adds Java fixture items and mappings only for the new bundled beef fabrication products. Version 0.8.0 Sprint 2 adds `butchercraft:retail_ground_beef` as data only and does not add a fixture item mapping.

The Grinder and Bandsaw still resolve processing operations through existing processing definitions and workstation controllers. Version 0.6.9 changes how product definitions reach the pure registry, not how workstations behave.

## Out Of Scope

This milestone does not add:

- Dynamic Minecraft item registration.
- Product-to-ItemStack factories.
- Datapack-driven category catalogs.
- Schema migrations.
- Full fabrication catalogs beyond the bundled v0.7.0 beef proof chain.
- Spoilage, quality expansion, storage rules, packaging recipes, packaging choices beyond the first table flow, labels, or recipe-selection UI.
- Smoker, packaging, cooler, or other workstation migrations.
- Public expansion APIs.

## Remaining Work

Before expanded fabrication, ButcherCraft still needs larger product catalogs, product-to-item creation rules, schema migration behavior, category/catalog ownership, reload-scoped user-facing diagnostics, and manual in-game reload validation for custom datapacks.
