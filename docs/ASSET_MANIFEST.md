# ButcherCraft Asset Manifest

Status: v0.8.0E asset framework baseline

## Purpose

This manifest records the visual assets that are currently relevant to the active ButcherCraft gameplay build. It separates stable resource-pack contracts from final artwork. Assets marked `Placeholder` load in-game and are intentionally unfinished.

Status values used here:

- `Missing` - required resource is not present.
- `Placeholder` - development art exists and loads, but must be replaced before art approval.
- `Framework Ready` - model, path, and dimensions are stable enough for artist replacement.
- `Review Required` - needs in-game art review after final art is supplied.
- `Deferred` - registered or documented for future gameplay, not currently part of the first packaging flow.

No current ButcherCraft v0.8.0 visual asset is production approved.

## Canonical Resource Structure

| Resource type | Canonical path |
| --- | --- |
| Blockstates | `src/generated/resources/assets/butchercraft/blockstates/*.json` or hand-authored `src/main/resources/assets/butchercraft/blockstates/*.json` when generation is not used. |
| Block models | `src/generated/resources/assets/butchercraft/models/block/*.json` for generated workstation models. |
| Item models | `src/generated/resources/assets/butchercraft/models/item/*.json` for generated items; legacy hand-authored development fixtures may remain in `src/main/resources/assets/butchercraft/models/item`. |
| Workstation textures | `src/main/resources/assets/butchercraft/textures/block/workstation/*.png`. |
| Packaging textures | `src/main/resources/assets/butchercraft/textures/item/packaging/*.png`. |
| GUI textures | `src/main/resources/assets/butchercraft/textures/gui/*.png`. |
| Development fixture textures | `src/main/resources/assets/butchercraft/textures/item/development_test_item.png`. |

## v0.8.0 Packaging Asset Section

| Asset name | Registry/resource id | Category | Associated content | Source path | Generated | Required dimensions | Current status | Placeholder | Intended material | Model dependency | Texture dependency | Artist notes | In-game review |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Packaging Table block model | `butchercraft:block/packaging_table` | Workstation block model | `butchercraft:packaging_table` | `src/generated/resources/assets/butchercraft/models/block/packaging_table.json` | Yes | JSON model within 0-16 block bounds | Framework Ready | Yes | Stainless steel, food-grade surface, simple film roll | `assets/butchercraft/blockstates/packaging_table.json`, `models/item/packaging_table.json` | `butchercraft:block/workstation/packaging_table_surface`, `butchercraft:block/workstation/packaging_table_frame`, `butchercraft:block/workstation/packaging_table_roll` | Preserve clean table-height silhouette, lower shelf, practical roll position, no decorative machinery. | Review Required |
| Packaging Table item model | `butchercraft:item/packaging_table` | Workstation item model | `butchercraft:packaging_table` | `src/generated/resources/assets/butchercraft/models/item/packaging_table.json` | Yes | Parent block model | Framework Ready | Yes | Same as block | `butchercraft:block/packaging_table` | Same as block | Block item intentionally inherits the block model. | Review Required |
| Packaging Table GUI | `butchercraft:textures/gui/packaging_table.png` | GUI texture | Packaging Table menu | `src/main/resources/assets/butchercraft/textures/gui/packaging_table.png` | No | 256x256 PNG, active 176x166 canvas | Framework Ready | Yes | Clean neutral workstation UI | `PackagingTableGuiLayout` | Background texture plus code-rendered slot/progress overlays | Final art must keep documented slot and progress regions clear. | Review Required |
| Foam Tray | `butchercraft:foam_tray` | Packaging supply item | Tray-wrap retail package | `src/generated/resources/assets/butchercraft/models/item/foam_tray.json` | Yes | 16x16 PNG texture | Framework Ready | Yes | White foam | `minecraft:item/generated` | `butchercraft:item/packaging/foam_tray` | Flat inventory sprite; keep silhouette readable at 16x16. | Review Required |
| Plastic Wrap Roll | `butchercraft:plastic_wrap_roll` | Packaging supply item | Tray-wrap retail package | `src/generated/resources/assets/butchercraft/models/item/plastic_wrap_roll.json` | Yes | 16x16 PNG texture | Framework Ready | Yes | Clear packaging film on roll | `minecraft:item/generated` | `butchercraft:item/packaging/plastic_wrap_roll` | Sprite should read as a roll without transparency-dependent gameplay. | Review Required |
| Vacuum Bag | `butchercraft:vacuum_bag` | Packaging supply item | Future vacuum package flows | `src/generated/resources/assets/butchercraft/models/item/vacuum_bag.json` | Yes | 16x16 PNG texture | Framework Ready | Yes | Clear food-safe bag | `minecraft:item/generated` | `butchercraft:item/packaging/vacuum_bag` | Registered and modeled, but not consumed by current Packaging Table flow. | Deferred |
| Butcher Paper Roll | `butchercraft:butcher_paper_roll` | Packaging supply item | Future butcher-paper flows | `src/generated/resources/assets/butchercraft/models/item/butcher_paper_roll.json` | Yes | 16x16 PNG texture | Framework Ready | Yes | Kraft butcher paper | `minecraft:item/generated` | `butchercraft:item/packaging/butcher_paper_roll` | Keep color distinct from freezer paper. | Deferred |
| Freezer Paper Roll | `butchercraft:freezer_paper_roll` | Packaging supply item | Future freezer-paper flows | `src/generated/resources/assets/butchercraft/models/item/freezer_paper_roll.json` | Yes | 16x16 PNG texture | Framework Ready | Yes | White freezer paper | `minecraft:item/generated` | `butchercraft:item/packaging/freezer_paper_roll` | Keep sprite distinct from plastic wrap roll. | Deferred |
| Retail Label Roll | `butchercraft:retail_label_roll` | Packaging supply item | Future label systems | `src/generated/resources/assets/butchercraft/models/item/retail_label_roll.json` | Yes | 16x16 PNG texture | Framework Ready | Yes | White label stock | `minecraft:item/generated` | `butchercraft:item/packaging/retail_label_roll` | Registered for future labels only; no label gameplay yet. | Deferred |
| Retail Ground Beef Test Product | `butchercraft:retail_ground_beef_test` | Product fixture item | `butchercraft:retail_ground_beef` | `src/generated/resources/assets/butchercraft/models/item/retail_ground_beef_test.json` | Yes | 16x16 PNG texture | Framework Ready | Yes | Tray-wrapped retail ground beef | `minecraft:item/generated` | `butchercraft:item/packaging/retail_ground_beef` | Must remain non-graphic, packaged, and readable as retail product. | Review Required |

## Workstation Asset Audit

| Asset name | Registry/resource id | Category | Source path | Generated | Required dimensions | Current status | Placeholder | Intended material | Model dependency | Texture dependency | Notes | In-game review |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Grinder block model | `butchercraft:block/grinder` | Workstation block model | `src/generated/resources/assets/butchercraft/models/block/grinder.json` | Yes | JSON model within 0-16 block bounds | Placeholder | Yes | Stainless steel grinder body | `assets/butchercraft/blockstates/grinder.json` | `butchercraft:item/development_test_item` | Shape is only a development proof and needs future art pass. | Review Required |
| Grinder item model | `butchercraft:item/grinder` | Workstation item model | `src/generated/resources/assets/butchercraft/models/item/grinder.json` | Yes | Parent block model | Placeholder | Yes | Same as block | `butchercraft:block/grinder` | Same as block | Inherits generated block model. | Review Required |
| Bandsaw lower block model | `butchercraft:block/bandsaw` | Workstation block model | `src/generated/resources/assets/butchercraft/models/block/bandsaw.json` | Yes | JSON model within 0-16 block bounds | Placeholder | Yes | Stainless steel bandsaw body | `assets/butchercraft/blockstates/bandsaw.json` | `butchercraft:item/development_test_item` | Paired block behavior is production gameplay; visuals are not final. | Review Required |
| Bandsaw upper block model | `butchercraft:block/bandsaw_upper` | Workstation block model | `src/generated/resources/assets/butchercraft/models/block/bandsaw_upper.json` | Yes | JSON model within 0-16 block bounds | Placeholder | Yes | Stainless steel upper frame | `assets/butchercraft/blockstates/bandsaw_upper.json` | `butchercraft:item/development_test_item` | Must preserve two-block readability after final art. | Review Required |
| Bandsaw item model | `butchercraft:item/bandsaw` | Workstation item model | `src/generated/resources/assets/butchercraft/models/item/bandsaw.json` | Yes | Parent lower block model | Placeholder | Yes | Same as lower block | `butchercraft:block/bandsaw` | Same as lower block | Item uses lower block representation. | Review Required |
| Development Processing Workstation | `butchercraft:development_processing_workstation` | Development fixture block/item | `src/main/resources/assets/butchercraft/models/block/development_processing_workstation.json` | No | JSON model and generated item parent | Placeholder | Yes | Development-only fixture | Hand-authored block and item models | `butchercraft:item/development_test_item` | Legacy diagnostic fixture retained for tests. | Deferred |

## Development Product Fixture Audit

The following product fixture item models are active development content and intentionally share `butchercraft:item/development_test_item` until a product item factory and final product art are scheduled:

`beef_trim_test`, `ground_beef_test`, `pork_trim_test`, `ground_pork_test`, `bison_trim_test`, `ground_bison_test`, `beef_forequarter_test`, `beef_chuck_test`, `beef_rib_test`, `beef_packer_brisket_test`, `beef_plate_test`, `beef_shank_test`, `beef_fat_test`, `beef_bone_test`, `beef_hindquarter_test`, `beef_round_test`, `beef_sirloin_test`, `beef_short_loin_test`, `beef_flank_test`, `t_bone_steak_test`, `porterhouse_steak_test`, `beef_strip_loin_test`, `beef_tenderloin_test`, `top_round_test`, `bottom_round_test`, `eye_of_round_test`, `sirloin_tip_test`, `top_sirloin_test`, `sirloin_steak_test`, and `tri_tip_test`.

| Asset family | Category | Source paths | Generated | Required dimensions | Current status | Placeholder | Intended material | Dependencies | Notes | In-game review |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Development product fixture items | Product fixture item models | `src/main/resources/assets/butchercraft/models/item/*_test.json` for legacy grinder fixtures and `src/generated/resources/assets/butchercraft/models/item/*_test.json` for newer fixtures | Mixed | 16x16 shared placeholder texture | Placeholder | Yes | Generic development product marker | `butchercraft:item/development_test_item` | Not production retail/product art. Retail Ground Beef now has its own packaging texture target. | Deferred |
| Development product placeholder texture | `butchercraft:item/development_test_item` | Shared development texture | `src/main/resources/assets/butchercraft/textures/item/development_test_item.png` | No | 16x16 PNG | Placeholder | Yes | Neutral unfinished marker | Used by legacy fixtures and non-packaging prototype workstation models | Replaced from 1x1 to 16x16 to avoid mip warnings. | Deferred |

## Audit Notes

- Generated resources mirror standard Minecraft resource-pack directories.
- No duplicate packaging supply texture path is retained; the former shared `butchercraft:item/packaging_supply_placeholder` path has been retired.
- Current resource-pack replacement targets are stable for packaging assets, but visual approval remains a future art-production step.
- Known vanilla/NeoForge launch warnings about union resource URLs and vanilla command ambiguity are outside ButcherCraft asset ownership.
