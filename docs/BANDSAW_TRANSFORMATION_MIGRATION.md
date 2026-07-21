# Bandsaw Transformation Migration

Status: v0.7.0 implementation note

## Scope

Version 0.6.7 migrates only the Bandsaw to capability-based, registry-driven, atomic multi-output transformation execution. Version 0.6.8 keeps that behavior and loads the transformation definition from bundled datapack JSON. Version 0.6.9 loads the required product definitions from bundled datapack JSON and activates product and transformation registries together. Version 0.7.0 adds more bundled beef fabrication content to the same Bandsaw migration path.

The migration does not add full carcass fabrication, recipe-selection UI, new product item systems, spoilage, packaging, power, employees, commerce, or any other workstation migration.

## Bandsaw Transformation

The original bundled transformation datapack resources include:

```text
butchercraft:break_beef_forequarter
required_capability: butchercraft:bandsaw
input: butchercraft:beef_forequarter, 100000 gram
duration: 6000 ms
yield: 19/20
```

Ordered outputs:

| Order | Product | Quantity |
| --- | --- | --- |
| 1 | `butchercraft:beef_chuck` | `30000 gram` |
| 2 | `butchercraft:beef_rib` | `10000 gram` |
| 3 | `butchercraft:beef_packer_brisket` | `10000 gram` |
| 4 | `butchercraft:beef_plate` | `10000 gram` |
| 5 | `butchercraft:beef_shank` | `5000 gram` |
| 6 | `butchercraft:beef_trim` | `15000 gram` |
| 7 | `butchercraft:beef_fat` | `5000 gram` |
| 8 | `butchercraft:beef_bone` | `10000 gram` |

The definition mirrors the existing processing-operation output order and quantities. Bandsaw machine classes do not contain these cut ids.

Version 0.7.0 adds `butchercraft:break_beef_hindquarter`, `butchercraft:cut_beef_short_loin`, `butchercraft:cut_beef_round`, and `butchercraft:cut_beef_sirloin`. These use the same Bandsaw capability, active transformation registry lookup, and atomic transaction behavior.

## Product Definitions And Item Mapping

The pure product registry adds only the current Bandsaw proof product ids:

```text
butchercraft:beef_forequarter
butchercraft:beef_chuck
butchercraft:beef_rib
butchercraft:beef_packer_brisket
butchercraft:beef_plate
butchercraft:beef_shank
butchercraft:beef_fat
butchercraft:beef_bone
```

The existing development fixture items remain the only ItemStack outputs for this proof. `DevelopmentProductItemMappings.fixtureMapping()` maps these product ids to the matching test items. This is still a controlled development bridge, not a universal product item factory.

## Inventory Bridge

`WorkstationInventoryMaterialStore` is the Minecraft-facing adapter from workstation ItemStack inventory to the pure transformation material-store contract.

- The input store reads the reserved input stack through `ProductStackAdapter`.
- The output store exposes current output-slot occupancy and output material-slot capacity.
- The pure transformation package remains free of Minecraft and NeoForge imports.

The adapter is used by the Bandsaw atomic execution strategy to validate transaction feasibility before the controller clears input or writes output ItemStacks.

## Execution Flow

1. The existing resolver selects one processing operation for the Bandsaw capability.
2. The atomic transformation strategy looks up the same operation id in `TransformationRegistry`.
3. The registered definition is rebased to the concrete input quantity.
4. `TransformationEvaluator` validates the definition, context, and advertised capability.
5. `TransformationExecutor` commits the accepted evaluation through `TransformationTransaction` using adapted input and output stores.
6. The legacy processing transaction still creates the committed `Product` outputs so quality and compatibility behavior remain unchanged.
7. The controller converts committed products to development ItemStacks and fills the ordered output slots exactly once.

## Failure Mapping

- Missing transformation definitions or rejected evaluations block processing as `PROCESSING_VALIDATION_REJECTED`.
- Material-store adaptation failures block processing as `PROCESSING_VALIDATION_REJECTED`.
- Transaction output-capacity failures block processing before input is consumed.
- Missing development product-item mappings block completion as `RESULT_CREATION_FAILED`.
- Output obstruction remains `OUTPUT_OCCUPIED`.
- Unexpected ItemStack insertion failures restore the input and output snapshots, then block completion as `RESULT_CREATION_FAILED`.

Blocked completion preserves input and does not insert partial outputs.

## Preserved Bandsaw Behavior

The migration preserves:

- Two-block paired placement and removal.
- Upper-block forwarding.
- Obstruction checks.
- Six-second processing duration.
- Existing save/load fields and completion idempotence.
- Menu and player interaction behavior.
- Block-break recovery through the lower block entity.

## Remaining Work

Before full fabrication, ButcherCraft still needs:

- A real data-driven product-to-ItemStack creation system.
- Full cut catalogs and additional fabrication transformations.
- External schema migration behavior for future transformation versions.
- Manual in-game validation before public gameplay reliance.
