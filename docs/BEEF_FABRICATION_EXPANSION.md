# Beef Fabrication Expansion

Status: v0.7.0 datapack-backed Bandsaw content expansion

## Purpose

Version 0.7.0 adds the first substantial multi-stage beef fabrication content chain while preserving the existing product-definition, transformation-registry, content-snapshot, workstation, and atomic transaction architecture.

The Bandsaw remains generic. It advertises only `butchercraft:bandsaw`; product ids, cut names, output order, quantities, and compatibility come from datapack product definitions, datapack transformation definitions, processing-operation definitions, and the existing resolver.

## Product Definitions

The bundled product datapack now includes these additional beef products:

| Product id | Display name | Role |
| --- | --- | --- |
| `butchercraft:beef_hindquarter` | Beef Hindquarter | input |
| `butchercraft:beef_round` | Beef Round | intermediate primal |
| `butchercraft:beef_sirloin` | Beef Sirloin | intermediate primal |
| `butchercraft:beef_short_loin` | Beef Short Loin | intermediate primal |
| `butchercraft:beef_flank` | Beef Flank | output primal |
| `butchercraft:t_bone_steak` | T-Bone Steak | output steak |
| `butchercraft:porterhouse_steak` | Porterhouse Steak | output steak |
| `butchercraft:beef_strip_loin` | Beef Strip Loin | output subprimal |
| `butchercraft:beef_tenderloin` | Beef Tenderloin | output subprimal |
| `butchercraft:top_round` | Top Round | output subprimal |
| `butchercraft:bottom_round` | Bottom Round | output subprimal |
| `butchercraft:eye_of_round` | Eye of Round | output subprimal |
| `butchercraft:sirloin_tip` | Sirloin Tip | output subprimal |
| `butchercraft:top_sirloin` | Top Sirloin | output subprimal |
| `butchercraft:sirloin_steak` | Sirloin Steak | output steak |
| `butchercraft:tri_tip` | Tri-Tip | output subprimal |

All use category `butchercraft:beef`, default unit `gram`, schema version `1`, and built-in schema metadata.

## Transformation Definitions

The bundled transformation datapack adds four Bandsaw definitions:

```text
butchercraft:break_beef_hindquarter
input: butchercraft:beef_hindquarter, 100000 gram
outputs: beef_round 30000, beef_sirloin 15000, beef_short_loin 15000, beef_flank 7500, beef_trim 15000, beef_fat 7500, beef_bone 10000
```

```text
butchercraft:cut_beef_short_loin
input: butchercraft:beef_short_loin, 15000 gram
outputs: t_bone_steak 4000, porterhouse_steak 3000, beef_strip_loin 3000, beef_tenderloin 2000, beef_trim 1500, beef_bone 1500
```

```text
butchercraft:cut_beef_round
input: butchercraft:beef_round, 30000 gram
outputs: top_round 7500, bottom_round 6500, eye_of_round 3500, sirloin_tip 5000, beef_trim 4000, beef_fat 1500, beef_bone 2000
```

```text
butchercraft:cut_beef_sirloin
input: butchercraft:beef_sirloin, 15000 gram
outputs: top_sirloin 5000, sirloin_steak 3500, tri_tip 2000, beef_trim 2500, beef_fat 1000, beef_bone 1000
```

Every definition declares `required_capability: butchercraft:bandsaw`, duration `6000` milliseconds, schema version `1`, and deterministic ordered outputs.

## Runtime Flow

1. The active content snapshot loads product definitions first.
2. Transformation definitions load against the candidate product registry.
3. Processing-operation definitions expose the new inputs to the existing processing graph.
4. `WorkstationOperationResolver` selects the matching operation for `butchercraft:bandsaw`.
5. The Bandsaw atomic transformation strategy looks up the active transformation by resolved operation id.
6. `TransformationEvaluator`, `TransformationExecutor`, and `TransformationTransaction` validate atomic feasibility.
7. The existing workstation controller commits the processing transaction and fills ordered output slots through the controlled development product-item mapping.

No new cut ids are hardcoded into Bandsaw machine classes or generic workstation logic.

## Development Items

Version 0.7.0 adds development-only fixture items for each new product. They carry `butchercraft:product_data`, appear in the ButcherCraft creative tab, reuse the existing placeholder product item model texture, and are mapped by `DevelopmentProductItemMappings.fixtureMapping()`.

These items are not final food, commerce products, recipes, or dynamic datapack-created items.

## Verification

Automated coverage includes:

- Built-in product registry ordering and tag queries.
- Product datapack resource loading.
- Transformation datapack resource loading.
- Atomic content snapshot activation.
- Processing graph edges for hindquarter and follow-on primal cuts.
- Bandsaw operation resolution by capability.
- Bandsaw controller completion for exact ordered output products and quantities.
- Development product-to-ItemStack mapping for all new product ids.
- Existing Grinder and forequarter Bandsaw regressions.

Manual in-game verification is still recommended before public upload because the automated tests do not launch a playable client world.

## Remaining Work

Future slices still need a full carcass-to-retail fabrication catalog, recipe-selection UI when multiple operations can apply to one input, real product item creation rules, balancing, player-facing art, and manual in-game datapack reload validation.
