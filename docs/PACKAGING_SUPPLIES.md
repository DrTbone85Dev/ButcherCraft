# ButcherCraft Packaging Supplies

Status: v0.8.0 Sprint D physical supply foundation with Packaging Table consumption

## Purpose

Packaging Supplies introduces the fixed Minecraft items that retail packaging gameplay can require. Sprint C made the items and data references available. Sprint D connects the Packaging Table to those references for the first live flow: required supplies are consumed only after successful packaging completion.

## Registered Items

The sprint adds these physical items:

| Item id | Display name | Current behavior |
| --- | --- | --- |
| `butchercraft:foam_tray` | Foam Tray | Required by the built-in tray-wrap retail package. |
| `butchercraft:plastic_wrap_roll` | Plastic Wrap Roll | Required by the built-in tray-wrap retail package. |
| `butchercraft:vacuum_bag` | Vacuum Bag | Registered and valid for future vacuum package flows. |
| `butchercraft:butcher_paper_roll` | Butcher Paper Roll | Registered and valid for future butcher-paper package flows. |
| `butchercraft:freezer_paper_roll` | Freezer Paper Roll | Registered and valid for future freezer-paper package flows. |
| `butchercraft:retail_label_roll` | Retail Label Roll | Creative-tab supply item reserved for future label systems. |

Each item uses normal `DeferredRegister` item registration, appears in the ButcherCraft creative tab, has English localization, and receives a generated item model using the shared abstract `packaging_supply_placeholder` texture.

## Packaging Definition Supply References

`PackagingDefinition` now includes immutable `requiredSupplyItems`. The serialized JSON field is:

```json
{
  "required_supply_items": [
    "butchercraft:foam_tray",
    "butchercraft:plastic_wrap_roll"
  ]
}
```

The field is optional. Older packaging definitions that omit it still deserialize with an empty supply list.

The built-in packaging definitions currently prove four formats:

| Packaging id | Format | Required supply items |
| --- | --- | --- |
| `butchercraft:retail_package` | `tray_wrap` | `butchercraft:foam_tray`, `butchercraft:plastic_wrap_roll` |
| `butchercraft:vacuum_package` | `vacuum` | `butchercraft:vacuum_bag` |
| `butchercraft:butcher_paper_package` | `butcher_paper` | `butchercraft:butcher_paper_roll` |
| `butchercraft:freezer_paper_package` | `freezer_paper` | `butchercraft:freezer_paper_roll` |

`butchercraft:retail_label_roll` is registered as a supply item but intentionally not referenced by the built-in packaging definitions because product labels are not implemented.

## Runtime Consumption

The Packaging Table reads required supply ids from the active `PackagingDefinition`. On successful completion of `butchercraft:package_retail`, the shared workstation commit plan consumes the input product and the required supply stacks, then inserts the packaged output.

If the product is missing, a required supply is missing or invalid, the result slot is blocked, output creation fails, or commit-time inventory mutation fails, no supplies are consumed and no packaged output is created.

## Validation

Packaging datapack loading now validates required supply references against the fixed built-in supply item id set. Invalid references fail the packaging registry load with structured diagnostics:

- `MALFORMED_REQUIRED_SUPPLIES` for non-string or malformed supply ids.
- `UNKNOWN_SUPPLY_ITEM` for well-formed ids that are not registered built-in supply item ids.

Because packaging, product, and transformation registries activate as one content snapshot, invalid packaging supply references reject the candidate snapshot and preserve the previously active registries. Runtime supply failures become explicit Packaging Table blocked states.

## Boundaries

This sprint does not implement packaging recipes, labels on products, dynamic rendering, weight, freshness, spoilage, custom sounds, animations, or business logic.

The supply item list is Java-controlled Minecraft content. Datapacks may reference known supply ids in packaging definitions, but datapacks do not dynamically register supply items, textures, models, or creative-tab entries.
