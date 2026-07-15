# ButcherCraft Workstation Framework

Status: Milestone 2B development framework

## Purpose

Milestone 2B adds the first reusable Minecraft-facing processing workstation framework. It proves one development-only block can hold one input product, resolve one compatible operation, track server-side progress, and create one output product through the existing engine transaction model.

This is not the final grinder, not final artwork, and not a player recipe-selection system.

## Responsibility Boundaries

- `com.butchercraft.engine` remains Minecraft-independent and owns validation, yield, quality adjustment, and transaction rules.
- `com.butchercraft.processing.definition` owns datapack-backed product and operation definitions.
- `com.butchercraft.product.integration.ProductStackAdapter` owns ItemStack/product conversion.
- `com.butchercraft.workstation` owns workstation capability, state, failure, inventory, operation resolution, duration conversion, and transaction orchestration.
- `ProcessingWorkstationBlockEntity` owns local persistence, server ticking, menu creation, and block-break recovery.
- `ProcessingWorkstationMenu` is a temporary view. It does not own inventory or processing state.

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

`WorkstationInventory` has exactly two slots:

- Slot `0`: input.
- Slot `1`: output.

Input accepts product-bearing stacks only. Output rejects insertion. Product-bearing stacks remain limited to stack size one. Input extraction is blocked while processing is active. Output extraction is allowed only after completion. Automation uses the same item-handler rules.

## Operation Resolution

`WorkstationOperationResolver` reads `ProductStackData`, validates it against loaded product definitions, builds a bounded processing graph from current definitions, filters operations by profile, workstation capability, and quantity, then returns deterministic compatible operations.

Exactly one compatible operation is selected automatically. Multiple compatible operations return `MULTIPLE_COMPATIBLE_OPERATIONS`; recipe-selection UI is deferred.

## Transaction Lifecycle

The controller keeps input visible in the input slot as a reserved stack while processing. The player and automation cannot extract it while active.

On completion:

1. The input is revalidated.
2. Output slot obstruction is checked.
3. The engine transaction prepares and commits once.
4. The committed engine product is converted into an ItemStack through the temporary development mapping.
5. Input is cleared and output is inserted.
6. State becomes `COMPLETE`.

The controller does not duplicate output after completion.

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

## Synchronization

The client receives display state through menu `ContainerData`, with standard block entity update tags available for normal load/update paths:

- State ordinal.
- Elapsed ticks.
- Total ticks.
- Last public failure code.

Inventory contents synchronize through the menu slot path. Registry contents and validation reports are not synchronized.

## Temporary Product-Item Mapping

Milestone 2B uses an explicit development-only mapping:

- `butchercraft:beef_trim` -> `butchercraft:beef_trim_test`
- `butchercraft:ground_beef` -> `butchercraft:ground_beef_test`

This is not a universal item factory. Future product item creation needs a deliberate data-driven design.

## Development Workstation

Temporary block:

```text
butchercraft:development_processing_workstation
```

The block appears in the ButcherCraft creative tab, opens a plain temporary inventory menu, accepts Beef Trim Test Product, resolves `butchercraft:grind_beef`, processes for 60 ticks, and outputs Ground Beef Test Product.

## Future Extension Points

- Additional machines can define separate `WorkstationCapability` values.
- Future poultry-specific restrictions should be capability/profile data, not Java species switches.
- Cleanliness, maintenance, equipment condition, and employee operation currently use centralized prototype context values and can be replaced by real snapshots later.
- Operation selection UI is deferred until multiple compatible operations are real gameplay.

## Explicit Exclusions

Milestone 2B does not implement final grinder gameplay, final machine art, multiple machine types, power, fuel, employees, refrigeration, temperature, freshness, cleanliness gameplay, maintenance gameplay, MCDA, customers, commerce, sounds, animations, complex rendering, or public expansion API guarantees.
