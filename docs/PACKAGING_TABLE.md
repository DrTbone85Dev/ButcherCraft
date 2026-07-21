# Packaging Table

Status: v0.8.0 Project Meat Counter workstation foundation with Sprint D packaging gameplay

## Purpose

Version 0.8.0 adds the Packaging Table as a permanent ButcherCraft workstation. It validates registration, placement, menus, inventory persistence, client screen wiring, item-handler exposure, and block-break recovery. Sprint 2 adds retail product data definitions, Sprint C adds physical packaging supply items, and Sprint D connects the table to the existing processing workstation framework for the first packaged retail flow.

The table is a processing workstation, not a crafting table. It executes the existing `butchercraft:package_retail` processing operation through the workstation resolver and controller, uses `PackagingDefinition` data to validate and consume required supplies, and produces a packaged product stack. It still does not apply labels, freshness, spoilage, order fulfillment, employees, business logic, or dynamic rendering.

## Registered Content

The table registers:

- Block: `butchercraft:packaging_table`
- Block item: `butchercraft:packaging_table`
- Block entity type: `butchercraft:packaging_table`
- Menu type: `butchercraft:packaging_table`
- Client screen: `PackagingTableScreen`
- Creative-tab entry in the ButcherCraft tab
- Generated blockstate, block model, item model, loot table, and language entries
- Related packaging supply items in the ButcherCraft creative tab: Foam Tray, Plastic Wrap Roll, Vacuum Bag, Butcher Paper Roll, Freezer Paper Roll, and Retail Label Roll
- Development output fixture: `butchercraft:retail_ground_beef_test`

Placeholder assets are abstract Minecraft-style development assets and are expected to be replaced later.

## Inventory Layout

The Packaging Table uses a workstation capability with three input slots and one result slot:

| Slot | Label | Behavior |
| --- | --- | --- |
| 0 | Meat | Accepts product-bearing stacks that resolve to a packaging operation. |
| 1 | Tray | Accepts known packaging supply items. |
| 2 | Wrap | Accepts known packaging supply items. |
| 3 | Result | Rejects manual insertion and receives packaged output after processing. |

The built-in flow uses Ground Beef Test Product, Foam Tray, and Plastic Wrap Roll to produce Retail Ground Beef Test Product. The supply slots are validated against the active packaging definition rather than hardcoded tray/wrap recipe logic.

## Architecture

`PackagingTableBlockEntity` extends `AbstractProcessingWorkstationBlockEntity` and uses:

- `WorkstationOperationResolver` to select `butchercraft:package_retail` from the processing graph.
- `WorkstationProcessingController` for progress, reservation, save/load, blocked state, and completion handling.
- `PackagingTableExecutionStrategy` to validate packaging metadata, required supplies, and stack decoration.
- `WorkstationInventoryCommitPlan` to consume input and supplies and insert output atomically.

The Packaging Table does not use transformation definitions or the transformation executor. That is deliberate: the current `package_retail` content is a processing operation, and packaging supplies are validated by packaging definitions.

## Transaction Lifecycle

1. Slot 0 product data resolves through the processing graph for the `butchercraft:packaging` capability.
2. The selected operation output must declare product packaging metadata.
3. The active `PackagingRegistry` must contain the referenced packaging definition.
4. Required supply item ids from the packaging definition must be present in the auxiliary input slots.
5. The output slot must be empty before processing starts.
6. While processing, all input slots are reserved and cannot be extracted through the workstation inventory.
7. On completion, the processing operation commits, the output stack is created, packaging metadata is written to the output stack, and product input plus required supplies are consumed through one commit plan.
8. If validation, output creation, output insertion, or commit-time mutation fails, the input product and supplies remain recoverable and no output is duplicated.

## Persistence And Recovery

The block entity persists all four slots under the shared workstation inventory tag. Active processing state persists through the shared processing-controller tag, including all reserved input snapshots. On block removal, processing is cancelled safely, all recoverable input and output slots are dropped once, and the inventory is cleared.

The inventory exposes the standard item-handler capability registered in `ModCapabilities`. The output slot rejects insertion, matching the existing workstation inventory contract.

## Retail Data Compatibility

The first packaging gameplay flow uses existing retail data:

- `butchercraft:retail_package`, `butchercraft:vacuum_package`, `butchercraft:butcher_paper_package`, and `butchercraft:freezer_paper_package` under `data/butchercraft/butchercraft/content/packaging`.
- `butchercraft:retail_ground_beef` under `data/butchercraft/butchercraft/content/product`.
- `butchercraft:package_retail` under the generated processing-operation definitions.

Packaging definitions may list required supply item ids. Sprint D consumes those supplies only after the packaging operation successfully completes.

## Explicit Exclusions

The Packaging Table does not implement:

- Packaging recipes or transformations
- Label generation
- Order fulfillment
- Employee operation
- Quality, freshness, or temperature changes
- Dynamic product labels, textures, overlays, or rendering
- Final art, custom sounds, particles, or animations
