# Packaging Table Foundation

Status: v0.8.0 Project Meat Counter workstation foundation

## Purpose

Version 0.8.0 adds the Packaging Table as a permanent ButcherCraft workstation shell. It exists so the project can validate registration, placement, menus, inventory persistence, client screen wiring, item-handler exposure, and block-break recovery before packaging gameplay is designed. Sprint 2 adds retail product data definitions alongside the table foundation, but the table itself remains inventory-only.

This foundation is intentionally not a packaging execution system. It does not consume inputs, create packaged outputs, apply labels, change quality, fulfill orders, run employees, or load packaging recipes.

## Registered Content

The foundation registers:

- Block: `butchercraft:packaging_table`
- Block item: `butchercraft:packaging_table`
- Block entity type: `butchercraft:packaging_table`
- Menu type: `butchercraft:packaging_table`
- Client screen: `PackagingTableScreen`
- Creative-tab entry in the ButcherCraft tab
- Generated blockstate, block model, item model, loot table, and language entries

Placeholder assets are abstract Minecraft-style development assets and are expected to be replaced later.

## Inventory Layout

The Packaging Table uses a workstation capability with three input slots and one result slot:

| Slot | Label | Behavior in v0.8.0 |
| --- | --- | --- |
| 0 | Meat | Accepts non-empty items through the placeholder inventory rules. |
| 1 | Tray | Accepts non-empty items through the placeholder inventory rules. |
| 2 | Wrap | Accepts non-empty items through the placeholder inventory rules. |
| 3 | Result | Rejects manual insertion and is reserved for future packaging output. |

The result slot has no producer in v0.8.0. The screen reserves visual space for future progress or status presentation without implementing progress behavior.

## Architecture

`PackagingTableBlockEntity` extends `AbstractInventoryWorkstationBlockEntity`, not `AbstractProcessingWorkstationBlockEntity`.

That split keeps the table out of:

- `WorkstationOperationResolver`
- `WorkstationProcessingController`
- `TransformationEvaluator`
- `TransformationExecutor`
- `TransformationTransaction`
- Product-to-ItemStack output mapping

The table still uses the shared `WorkstationInventory` and `ProcessingWorkstationMenu` layout infrastructure so future workstation foundations can reuse the same inventory and synchronization behavior.

## Persistence And Recovery

The block entity persists all four slots under the shared workstation inventory tag. On block removal, all input and output slots are dropped once and then cleared through the workstation inventory.

The inventory exposes the standard item-handler capability registered in `ModCapabilities`. The output slot rejects insertion, matching the existing workstation inventory contract.

## Retail Data Compatibility

Sprint 2 adds a data-only retail product framework:

- `butchercraft:retail_package` under `data/butchercraft/butchercraft/content/packaging`.
- `butchercraft:retail_ground_beef` under `data/butchercraft/butchercraft/content/product`.
- `butchercraft:package_retail` under the generated processing-operation definitions.

These definitions prove product and packaging data can load through the active content snapshot and processing graph. No packaging transformation JSON is added, and the Packaging Table does not create a `WorkstationProcessingController`.

## Explicit Exclusions

The Packaging Table foundation does not implement:

- Packaging recipes or transformations
- Tray or wrap item semantics
- Label generation
- Order fulfillment
- Employee operation
- Quality, freshness, or temperature changes
- Packaging execution or supply consumption
- Final art, sounds, particles, or animations
