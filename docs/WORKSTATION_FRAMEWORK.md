# ButcherCraft Workstation Framework

Status: Milestones 2B through 2E workstation framework, with v0.8.0 Packaging Table gameplay

## Purpose

Milestone 2B adds the first reusable Minecraft-facing processing workstation framework. Milestone 2E extends the same framework so one input product can resolve one compatible operation, track server-side progress, and create an ordered collection of output products through the existing engine transaction model. Version 0.6.1 adds a capability-based execution strategy hook and migrates only the Grinder to the transformation execution bridge. Version 0.6.2 makes that bridge query the immutable transformation registry. Version 0.6.6 adds pure Java transformation transactions. Version 0.6.7 migrates only the Bandsaw to the atomic transformation bridge while preserving its existing controller, inventory, paired-block, menu, and persistence behavior. Version 0.6.8 makes the transformation registry datapack-backed. Version 0.6.9 makes product and transformation registries reload-safe as one content snapshot. Version 0.7.0 adds more Bandsaw content without changing workstation behavior. Version 0.8.0 adds the Packaging Table foundation, Sprint 2 adds retail product data, Sprint C adds packaging supply items, and Sprint D connects the table to the shared processing controller for the first supply-consuming packaging flow.

This is not final artwork, not a player recipe-selection system, not a label system, and not a complete product item factory.

## Responsibility Boundaries

- `com.butchercraft.engine` remains Minecraft-independent and owns validation, yield, quality adjustment, and transaction rules.
- `com.butchercraft.processing.definition` owns datapack-backed product and operation definitions.
- `com.butchercraft.product.integration.ProductStackAdapter` owns ItemStack/product conversion.
- `com.butchercraft.workstation` owns workstation capability, state, failure, inventory, operation resolution, duration conversion, and execution orchestration.
- `com.butchercraft.transformation` owns pure Java transformation evaluation and execution. Minecraft-facing workstations adapt their advertised capabilities into this pure model only at the integration boundary.
- `AbstractInventoryWorkstationBlockEntity` owns shared inventory persistence, menu creation, item-handler inventory ownership, update tags, and block-break recovery for workstation foundations.
- `AbstractProcessingWorkstationBlockEntity` adds processing controller state, server ticking, operation resolution, and completion behavior for machines that execute processing.
- `ProcessingWorkstationMenu` and its client screens are temporary views. They do not own inventory or processing state.

## State Machine

Allowed transitions:

```text
IDLE -> READY
READY -> PROCESSING
PROCESSING -> COMPLETE
PROCESSING -> BLOCKED
BLOCKED -> READY or PROCESSING when recoverable
COMPLETE -> IDLE after output removal
ERROR -> IDLE only through safe reset
```

Invalid transitions throw in tests and are not used by the controller.

## Failure Model

`WorkstationFailure` records a stable `WorkstationFailureCode`, optional relevant resource ids, a developer explanation, and a player-facing translation key. Player messages do not expose Java exception text directly.

Minimum failure codes from the milestone are represented, with one additional explicit `MISSING_PRODUCT_DATA` and `MULTIPLE_COMPATIBLE_OPERATIONS` code for clearer resolver behavior.

## Inventory Ownership

`WorkstationInventory` derives its slot layout from the owning `WorkstationCapability`. Current layouts are:

| Machine | Input slots | Output slots | Total slots |
| --- | --- | --- | --- |
| Development Processing Workstation | 1 | 1 | 2 |
| Grinder | 1 | 1 | 2 |
| Bandsaw | 1 | 8 | 9 |
| Packaging Table | 3 | 1 | 4 |

Slot `0` is the first input for current machines. Output slots start at the configured first output slot, which is slot `1` for current one-input machines and slot `3` for the Packaging Table.

Processing machine primary inputs accept product-bearing stacks only. Slot-aware validation allows multi-input workstations to define auxiliary input rules. The Packaging Table accepts product-bearing stacks in slot `0` and known packaging supply items in slots `1` and `2`. Output slots reject insertion. Product-bearing stacks remain limited to stack size one. Input extraction is blocked while processing is active. Output extraction is allowed only after completion for processing machines. Automation uses the same item-handler rules.

## Operation Resolution

`WorkstationOperationResolver` reads `ProductStackData`, validates it against loaded product definitions, builds a bounded processing graph from current definitions, filters operations by profile, workstation capability, and quantity, then returns deterministic compatible operations.

Exactly one compatible operation is selected automatically. Multiple compatible operations return `MULTIPLE_COMPATIBLE_OPERATIONS`; recipe-selection UI is deferred.

Workstations advertise capabilities through `WorkstationCapability`. Operation resolution may match by operation category or workstation capability for compatibility with existing definition data. Transformation execution uses only advertised workstation capability ids, such as `butchercraft:grinding`.

## Execution Strategies

`WorkstationProcessingController` delegates processing preparation and commit to a `WorkstationExecutionStrategy`.

- The default legacy strategy preserves the existing processing transaction path.
- The Grinder opts into the transformation strategy, which looks up the resolved operation id in the active immutable `TransformationRegistry`, evaluates and executes the registered definition through the pure Java transformation engine, then delegates product commit to the existing transaction path.
- The Bandsaw opts into the atomic transformation strategy, which additionally adapts the workstation ItemStack inventory into pure material stores and validates transactional input extraction plus ordered output insertion before the existing controller commits Minecraft ItemStacks.
- `ContentSnapshotService` swaps the active product, packaging, and transformation registries together only after datapack content validation succeeds, including validation of packaging supply references.
- Development workstation and any future un-migrated processing workstations remain on the legacy strategy until explicitly migrated.
- The Packaging Table uses a packaging execution strategy. It resolves `butchercraft:package_retail` through the processing graph, validates required supplies through the active packaging registry, decorates the output stack with packaging metadata, and consumes input plus supplies through the shared commit plan.

The current workstation controller still owns Minecraft inventory reservation, ItemStack creation, output slot checks, and block-entity persistence.

## Transaction Lifecycle

The controller keeps input visible in the input slot as a reserved stack while processing. The player and automation cannot extract it while active.

On completion:

1. The input is revalidated.
2. Output slot obstruction is checked.
3. The selected execution strategy prepares and commits the operation.
4. Committed engine products are converted into ItemStacks through the temporary development mapping.
5. The execution strategy may decorate output stacks with workstation-specific metadata.
6. `WorkstationInventoryCommitPlan` snapshots all inputs and outputs, clears the selected consumed input slots, and inserts ordered outputs.
7. If commit-time inventory mutation fails, the input and output snapshots are restored.
8. State becomes `COMPLETE`.

The controller does not duplicate outputs after completion.

## Duration Conversion

Operation definitions store milliseconds. Workstations convert duration to ticks at the Minecraft boundary:

```text
ticks = ceil(milliseconds / 50)
```

Durations must be positive and fit in an `int` tick count. Therefore `3000` milliseconds becomes `60` ticks.

## Persistence and Recovery

Processing block entities persist:

- Workstation inventory.
- Workstation state.
- Selected operation id.
- Elapsed and total ticks.
- Last failure code.
- Reserved input snapshots for all input slots.
- Completion-committed flag.

Recovery policy: input remains visibly reserved in the input slot. If active saved state is malformed, processing stops in `ERROR` and recoverable inventory remains instead of being deleted. Completed output is never recreated if the output already exists.

Inventory-only workstations persist only their inventory. The Packaging Table used this path for the v0.8.0 foundation, but Sprint D moves it to the processing block entity path so active packaging progress and reserved inputs persist.

Inventory load keeps the machine's configured slot count. Extra saved slots are ignored, and missing saved slots remain empty, so older two-slot Grinder/development workstation saves, nine-slot Bandsaw saves, and four-slot Packaging Table saves keep their intended layouts.

## Synchronization

The client receives display state through menu `ContainerData`, with standard block entity update tags available for normal load/update paths:

- State ordinal.
- Elapsed ticks.
- Total ticks.
- Last public failure code.

Inventory contents synchronize through the menu slot path. Registry contents and validation reports are not synchronized.

## Temporary Product-Item Mapping

Early workstation and Grinder milestones use an explicit development-only mapping:

- `butchercraft:beef_trim` -> `butchercraft:beef_trim_test`
- `butchercraft:ground_beef` -> `butchercraft:ground_beef_test`
- `butchercraft:pork_trim` -> `butchercraft:pork_trim_test`
- `butchercraft:ground_pork` -> `butchercraft:ground_pork_test`
- `butchercraft:bison_trim` -> `butchercraft:bison_trim_test`
- `butchercraft:ground_bison` -> `butchercraft:ground_bison_test`
- `butchercraft:beef_forequarter` -> `butchercraft:beef_forequarter_test`
- `butchercraft:beef_chuck` -> `butchercraft:beef_chuck_test`
- `butchercraft:beef_rib` -> `butchercraft:beef_rib_test`
- `butchercraft:beef_packer_brisket` -> `butchercraft:beef_packer_brisket_test`
- `butchercraft:beef_plate` -> `butchercraft:beef_plate_test`
- `butchercraft:beef_shank` -> `butchercraft:beef_shank_test`
- `butchercraft:beef_fat` -> `butchercraft:beef_fat_test`
- `butchercraft:beef_bone` -> `butchercraft:beef_bone_test`
- `butchercraft:beef_hindquarter` -> `butchercraft:beef_hindquarter_test`
- `butchercraft:beef_round` -> `butchercraft:beef_round_test`
- `butchercraft:beef_sirloin` -> `butchercraft:beef_sirloin_test`
- `butchercraft:beef_short_loin` -> `butchercraft:beef_short_loin_test`
- `butchercraft:beef_flank` -> `butchercraft:beef_flank_test`
- `butchercraft:t_bone_steak` -> `butchercraft:t_bone_steak_test`
- `butchercraft:porterhouse_steak` -> `butchercraft:porterhouse_steak_test`
- `butchercraft:beef_strip_loin` -> `butchercraft:beef_strip_loin_test`
- `butchercraft:beef_tenderloin` -> `butchercraft:beef_tenderloin_test`
- `butchercraft:top_round` -> `butchercraft:top_round_test`
- `butchercraft:bottom_round` -> `butchercraft:bottom_round_test`
- `butchercraft:eye_of_round` -> `butchercraft:eye_of_round_test`
- `butchercraft:sirloin_tip` -> `butchercraft:sirloin_tip_test`
- `butchercraft:top_sirloin` -> `butchercraft:top_sirloin_test`
- `butchercraft:sirloin_steak` -> `butchercraft:sirloin_steak_test`
- `butchercraft:tri_tip` -> `butchercraft:tri_tip_test`

This mapping is built from registered development fixture items and their default product data. It is not a universal item factory. Future product item creation needs a deliberate data-driven design.

## Development Workstation

Temporary block:

```text
butchercraft:development_processing_workstation
```

The block appears in the ButcherCraft creative tab, opens a plain temporary inventory menu and client screen, accepts the current red-meat trim test products, resolves the single compatible grinding operation for each inserted product, processes for 60 ticks, and outputs the matching ground test product.

## Bandsaw Machine

Permanent block pair:

```text
butchercraft:bandsaw
butchercraft:bandsaw_upper
```

The lower block owns the block entity, inventory, ticker, menu, persistence, and recovery drops. The upper block forwards interaction and removes the paired machine without owning inventory. The Bandsaw uses `butchercraft:bandsaw` and can fill up to eight ordered output slots from one committed operation.

## Packaging Table

Permanent foundation block:

```text
butchercraft:packaging_table
```

The block appears in the ButcherCraft creative tab, opens a processing menu and client screen, persists Meat, Tray, Wrap, and Result slots, exposes item-handler inventory capability, and drops stored items on removal. It advertises `butchercraft:packaging`. Sprint 2 adds a `package_retail` processing-operation definition for the graph, Sprint C adds supply items that packaging definitions may reference, and Sprint D executes the first Ground Beef to Retail Ground Beef flow. Required supplies are consumed only after successful completion.

The Packaging Table is documented in `docs/PACKAGING_TABLE.md`.

## Future Extension Points

- Additional machines can define separate `WorkstationCapability` values.
- Capabilities declare how many input and output slots a machine can expose.
- Future poultry-specific restrictions should be capability/profile data, not Java species switches.
- Cleanliness, maintenance, equipment condition, and employee operation currently use centralized prototype context values and can be replaced by real snapshots later.
- Operation selection UI is deferred until multiple compatible operations are real gameplay.

## Explicit Exclusions

This framework does not implement final machine art, power, fuel, employees, refrigeration, temperature, freshness, cleanliness gameplay, maintenance gameplay, MCDA, customers, commerce, custom sounds, animations, complex rendering, recipe-selection UI, labels, or public expansion API guarantees.
