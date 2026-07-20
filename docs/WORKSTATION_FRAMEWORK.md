# ButcherCraft Workstation Framework

Status: Milestones 2B through 2E workstation framework, with v0.6.6 transformation transaction foundation

## Purpose

Milestone 2B adds the first reusable Minecraft-facing processing workstation framework. Milestone 2E extends the same framework so one input product can resolve one compatible operation, track server-side progress, and create an ordered collection of output products through the existing engine transaction model. Version 0.6.1 adds a capability-based execution strategy hook and migrates only the Grinder to the transformation execution bridge. Version 0.6.2 makes that bridge query the immutable transformation registry. Version 0.6.6 adds pure Java transformation transactions, but does not migrate the Bandsaw or workstation inventories to that path yet.

This is not final artwork, not a player recipe-selection system, and not a complete product item factory.

## Responsibility Boundaries

- `com.butchercraft.engine` remains Minecraft-independent and owns validation, yield, quality adjustment, and transaction rules.
- `com.butchercraft.processing.definition` owns datapack-backed product and operation definitions.
- `com.butchercraft.product.integration.ProductStackAdapter` owns ItemStack/product conversion.
- `com.butchercraft.workstation` owns workstation capability, state, failure, inventory, operation resolution, duration conversion, and execution orchestration.
- `com.butchercraft.transformation` owns pure Java transformation evaluation and execution. Minecraft-facing workstations adapt their advertised capabilities into this pure model only at the integration boundary.
- `ProcessingWorkstationBlockEntity` owns local persistence, server ticking, menu creation, and block-break recovery.
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

Slot `0` is the input for current machines. Output slots start at the configured first output slot, currently slot `1`.

Input accepts product-bearing stacks only. Output rejects insertion. Product-bearing stacks remain limited to stack size one. Input extraction is blocked while processing is active. Output extraction is allowed only after completion. Automation uses the same item-handler rules.

## Operation Resolution

`WorkstationOperationResolver` reads `ProductStackData`, validates it against loaded product definitions, builds a bounded processing graph from current definitions, filters operations by profile, workstation capability, and quantity, then returns deterministic compatible operations.

Exactly one compatible operation is selected automatically. Multiple compatible operations return `MULTIPLE_COMPATIBLE_OPERATIONS`; recipe-selection UI is deferred.

Workstations advertise capabilities through `WorkstationCapability`. Operation resolution may match by operation category or workstation capability for compatibility with existing definition data. Transformation execution uses only advertised workstation capability ids, such as `butchercraft:grinding`.

## Execution Strategies

`WorkstationProcessingController` delegates processing preparation and commit to a `WorkstationExecutionStrategy`.

- The default legacy strategy preserves the existing processing transaction path.
- The Grinder opts into the transformation strategy, which looks up the resolved operation id in the immutable `TransformationRegistry`, evaluates and executes the registered definition through the pure Java transformation engine, then delegates product commit to the existing transaction path.
- Bandsaw, development workstation, and any future un-migrated workstations remain on the legacy strategy until explicitly migrated.

The transformation engine now has an atomic pure material-store transaction path for future migrations. The current workstation controller still owns Minecraft inventory reservation, ItemStack creation, output slot checks, and block-entity persistence.

## Transaction Lifecycle

The controller keeps input visible in the input slot as a reserved stack while processing. The player and automation cannot extract it while active.

On completion:

1. The input is revalidated.
2. Output slot obstruction is checked.
3. The selected execution strategy prepares and commits the operation.
4. Committed engine products are converted into ItemStacks through the temporary development mapping.
5. Input is cleared and ordered outputs are inserted into output slots.
6. State becomes `COMPLETE`.

The controller does not duplicate outputs after completion.

## Duration Conversion

Operation definitions store milliseconds. Workstations convert duration to ticks at the Minecraft boundary:

```text
ticks = ceil(milliseconds / 50)
```

Durations must be positive and fit in an `int` tick count. Therefore `3000` milliseconds becomes `60` ticks.

## Persistence and Recovery

The block entity persists:

- Workstation inventory.
- Workstation state.
- Selected operation id.
- Elapsed and total ticks.
- Last failure code.
- Reserved input snapshot.
- Completion-committed flag.

Recovery policy: input remains visibly reserved in the input slot. If active saved state is malformed, processing stops in `ERROR` and recoverable inventory remains instead of being deleted. Completed output is never recreated if the output already exists.

Inventory load keeps the machine's configured slot count. Extra saved slots are ignored, and missing saved slots remain empty, so older two-slot Grinder/development workstation saves and nine-slot Bandsaw saves keep their intended layouts.

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

## Future Extension Points

- Additional machines can define separate `WorkstationCapability` values.
- Capabilities declare how many output slots a machine can expose.
- Future poultry-specific restrictions should be capability/profile data, not Java species switches.
- Cleanliness, maintenance, equipment condition, and employee operation currently use centralized prototype context values and can be replaced by real snapshots later.
- Operation selection UI is deferred until multiple compatible operations are real gameplay.

## Explicit Exclusions

This framework does not implement final machine art, power, fuel, employees, refrigeration, temperature, freshness, cleanliness gameplay, maintenance gameplay, MCDA, customers, commerce, sounds, animations, complex rendering, recipe-selection UI, or public expansion API guarantees.
