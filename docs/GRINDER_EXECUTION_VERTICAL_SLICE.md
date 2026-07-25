# Grinder Execution Vertical Slice

Status: IM-012 implemented. IM-013 automated server-world verification added. IM-014 promotes the Grinder path to normal gameplay presentation.

This note records the first player-facing workstation operation connected to the generic Execution runtime. It does not authorize a public workstation API, broad Production migration, Allocation integration, worker automation, compensation, automatic checkpoint recovery, or additional workstation operations.

## Selected Slice

The selected operation is the existing grinder transformation that accepts one valid Beef Trim product stack and produces one Ground Beef output through the existing grinder menu, controller, transformation strategy, and workstation ItemStack commit plan.

The duration remains 60 server ticks, derived from the existing 3000 millisecond processing definition. Progress remains workstation-owned presentation state.

## Player Flow

The player crafts or obtains the Grinder, places it, opens the menu, inserts a valid product-bearing Beef Trim stack, observes processing progress through the existing menu data, waits for completion, and retrieves one Ground Beef output from the output slot.

The client may display state, progress, and failure messages. It does not assert valid input, successful authorization, output contents, or Execution success.

## Authorization And Identity

The workstation owner validates legal state, selected operation, frozen input slot contents, empty output capacity, and expected output before issuing Execution authorization.

The authorization binds:

- stable grinder workstation identity from dimension and block position,
- selected operation identity,
- frozen input identity from canonical product component data,
- expected output identity,
- workstation slot freshness identity,
- handler identity,
- configuration identity,
- world identity,
- issuance tick and validity boundary.

Execution owns authorization consumption, deterministic Operation Identity, domain Effect Identity, lifecycle state, attempts, and Execution Result Evidence.

## Scheduler And Effect

The controller starts one Execution operation when processing begins. When the 60-tick workstation duration is reached, the controller submits one generic Execution Scheduler Work item. Scheduler owns dispatch, Invocation Identity, Scheduler Effect Identity, and effect-policy enforcement.

The generic Execution Scheduler handler remains `IDEMPOTENT` and owner-result-required as defined by IM-011. The selected grinder mutation is not classified as `TRANSACTION_BACKED` because this gameplay slice still uses workstation-owned Minecraft ItemStack slots, not the economic Inventory runtime.

## Inventory Mutation Boundary

The selected grinder slice does not use economic `InventoryManager` quantities and does not submit economic Transactions. The authoritative mutation boundary remains the workstation-owned `WorkstationInventoryCommitPlan`.

At effect application, the workstation owner revalidates the input snapshot, selected operation, and output capacity, then atomically clears the consumed input slot and inserts the output stack. If commit fails, the plan restores prior input and output snapshots.

## Owner Result

After the workstation effect is known, the workstation owner publishes immutable owner result evidence that binds the Execution Operation Identity, domain Effect Identity, selected operation, frozen input identity, expected output identity, output products, terminal status, and authoritative tick.

Execution succeeds only after observing this owner result and publishing Execution Result Evidence.

## Duplicate Safety

Repeated authorization content observes the existing Execution operation. Repeated Scheduler submission observes the existing Scheduler Work when the Work ID already exists. Repeated handler observation after workstation completion returns the persisted owner result evidence instead of recreating output.

Changing the frozen input identity produces a different Execution operation identity. Conflicting authorization content remains governed by the generic Execution duplicate/conflict rules.

## Save And Load

The workstation controller persists active Execution operation identity, domain Effect Identity, frozen input identity, expected output identity, source freshness identity, Scheduler submission flag, owner result reference, progress, selected operation, and reserved input snapshots.

Safe pre-effect processing may resume. If persisted active Execution state is malformed or an unresolved committed effect is detected, processing moves to a visible error state and does not recreate output. Runtime authorization authority is not persisted.

## Automated GameTest Coverage

IM-013 adds ButcherCraft GameTests under the `butchercraft` namespace, executed by `runGameTestServer` with the committed `empty_5x4x5` template. The Gradle run task copies committed GameTest structure files into the development game directory before launching the GameTest server so a clean checkout does not require manual structure preparation.

The tests verify:

- GameTest discovery and nonzero server execution.
- Grinder block placement, block entity creation, idle state, empty slots, and absence of an active Execution operation.
- Valid beef-trim insertion through the workstation inventory path, 60 server-tick processing, one ground-beef output, one consumed input, owner result evidence, Execution success, and Scheduler completion.
- Repeated insertion/use during processing and continued ticks after completion do not duplicate Execution operations, Scheduler Work, or output.
- Closing an opened test interaction does not cancel server-side processing.
- Block-entity NBT serialization and restoration preserves safe pre-effect progress and does not duplicate completed output.
- Changed input, blocked output, malformed restored state, and uncertain consequential restored state fail visibly without fabricating output or retrying automatically.

The save/load coverage is serialization-level block-entity coverage. It does not claim chunk unload/reload, full world reload, server-restart recovery, coordinated checkpoint recovery, or operator reconciliation coverage.

## IM-014 Gameplay Promotion

IM-014 keeps the legacy item registry ids for saved-world and fixture compatibility, but promotes the Beef Trim and Ground Beef presentation used by the Grinder flow:

- `butchercraft:beef_trim_test` now presents to players as Beef Trim.
- `butchercraft:ground_beef_test` now presents to players as Ground Beef.
- both items use dedicated product textures instead of the shared development placeholder.
- the Grinder uses dedicated workstation textures instead of the shared development placeholder.
- the Grinder has a generated shaped crafting recipe, block item, creative-tab entry, and loot-table drop.

This does not authorize a general product item factory. Pork, bison, bandsaw, packaging, and broader product fixture mappings remain temporary development bridges until separately promoted.

Breaking an active Grinder uses the existing workstation block-removal path: active processing is canceled before effect publication, stored input is dropped, output is not fabricated, and no runtime authority token is serialized or reused.

## Remaining Gates

- General workstation Execution framework.
- Additional grinder operations.
- Bandsaw, Packaging Table, and development workstation Execution migration.
- Production-backed workstation execution.
- Economic Inventory and Transaction integration for player workstations.
- Allocation and Planning automation.
- Operator recovery UI for unknown outcomes.
- Automatic checkpoint recovery and Evidence archival.
- Manual client usability approval.
- Chunk unload/reload, world reload, and full server-restart recovery tests.
