# Grinder Execution Vertical Slice

Status: IM-012 implemented. IM-013 automated server-world verification added. IM-014 promotes the Grinder path to normal gameplay presentation. IM-015 promotes the Pork Trim to Ground Pork second grinder process. IM-016 integrates the promoted Grinder path with Production observation. IM-017 expands the promoted Grinder catalog to six trim-to-ground products. IM-018 uses the Grinder as the first step in the manual Grinder to Patty Former Production chain. IM-027 permits one arrived employee to request and observe only the Beef Trim operation.

This note records the first player-facing workstation operation connected to the generic Execution runtime. It does not authorize a public workstation API, broad Production migration beyond the IM-016 promoted Grinder observation path and IM-018 narrow manual two-step chain, Allocation integration, worker automation, compensation, automatic checkpoint recovery, or additional workstation operations.

## Selected Slice

The selected operations are the existing promoted grinder transformations that accept one valid Beef, Pork, Chicken, Buffalo, Lamb, or Venison Trim product stack and produce one matching Ground product through the existing grinder menu, controller, transformation strategy, and workstation ItemStack commit plan.

Both promoted operations use 60 server ticks, derived from the existing 3000 millisecond processing definition. Progress remains workstation-owned presentation state.

## Player Flow

The player crafts or obtains the Grinder, places it, opens the menu, inserts a valid product-bearing Beef, Pork, Chicken, Buffalo, Lamb, or Venison Trim stack, observes processing progress through the existing menu data, waits for completion, and retrieves the matching Ground output from the output slot.

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

IM-015 makes the selected operation binding explicit in the authorization identity inputs. IM-017 verifies all six promoted Grinder operations produce distinct Operation Identities even when they use the same workstation, duration, handler, and configuration.

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
- IM-015 adds focused Pork Trim coverage for live Execution completion, process isolation against Ground Beef output, serialization resume, duplicate interaction safety, and active block-break preservation.
- IM-017 adds coverage for Chicken, Buffalo, Lamb, and Venison completion, duplicate safety, serialization resume, blocked output, and wrong-output prevention, plus coexistence, deterministic process lookup, and unsupported input rejection across the six promoted operations.

The save/load coverage is serialization-level block-entity coverage. It does not claim chunk unload/reload, full world reload, server-restart recovery, coordinated checkpoint recovery, or operator reconciliation coverage.

## IM-014 Gameplay Promotion

IM-014 keeps the legacy item registry ids for saved-world and fixture compatibility, but promotes the Beef Trim and Ground Beef presentation used by the Grinder flow:

- `butchercraft:beef_trim_test` now presents to players as Beef Trim.
- `butchercraft:ground_beef_test` now presents to players as Ground Beef.
- both items use dedicated product textures instead of the shared development placeholder.
- the Grinder uses dedicated workstation textures instead of the shared development placeholder.
- the Grinder has a generated shaped crafting recipe, block item, creative-tab entry, and loot-table drop.

This does not authorize a general product item factory. Bison, bandsaw, packaging, and broader product fixture mappings remain temporary development bridges until separately promoted.

Breaking an active Grinder uses the existing workstation block-removal path: active processing is canceled before effect publication, stored input is dropped, output is not fabricated, and no runtime authority token is serialized or reused.

## IM-015 Second Process Promotion

IM-015 promotes Pork Trim and Ground Pork presentation for the Grinder while retaining their legacy item registry ids:

- `butchercraft:pork_trim_test` now presents to players as Pork Trim.
- `butchercraft:ground_pork_test` now presents to players as Ground Pork.
- both items use dedicated product textures instead of the shared development placeholder.
- `butchercraft:grind_pork` is the one additional promoted live Grinder operation.

The existing bison grinding definition remains prototype fixture content. It verifies data-driven definitions and resolver behavior but is not included in the promoted live Grinder Execution authorization set.

## IM-016 Production Observation

The promoted Grinder is now production-capable for one narrow integration path. Production may assign a Run to a Grinder workstation identity and promoted Grinder process identity, ask the existing Grinder controller to process through normal validation, and observe the resulting Execution Operation Identity.

Production completes the Run only after observing both Grinder owner result evidence and Execution result evidence. It records Production-owned completion evidence that references those identities and digests. It does not mutate Grinder ItemStack slots, issue or consume Execution authorization, infer success from elapsed time or Scheduler completion alone, duplicate Grinder state, or automatically rerun rejection, failure, cancellation-after-start, or `UNKNOWN_OUTCOME`.

## IM-017 Recipe Expansion

IM-017 promotes four additional trim-to-ground recipes through the same Grinder execution path:

- Chicken Trim to Ground Chicken.
- Buffalo Trim to Ground Buffalo, using retained `butchercraft:bison_*` registry identities.
- Lamb Trim to Ground Lamb.
- Venison Trim to Ground Venison.

The promoted Grinder operation set is exactly Beef, Pork, Chicken, Buffalo, Lamb, and Venison. Each operation runs for 60 server ticks, resolves from authoritative product data and definitions, binds the selected operation into Execution identity, and publishes workstation owner result evidence before Execution success. No recipe adds species-specific Grinder code, a recipe-selection UI, Production automation, or new workstation behavior.

## IM-027 Employee Operation

One employee with an arrived Grinder reservation may request
`butchercraft:grind_beef` when Beef Trim is already present in the
Grinder-owned input slot. The employee request delegates to the existing
controller path; the Grinder still resolves and validates the recipe, issues
private Execution authorization, commits its own slots, and publishes owner
result evidence. Scheduler timing and Execution lifecycle are unchanged.

The request is made only through the permission-gated development/operator
command `/butchercraft employee operate <employee>`. For manual acceptance,
`/butchercraft employee operate #1` targets the first employee. Arrival at the
reserved Grinder does not initiate operation automatically.

The employee observes the matching Execution identity, Grinder owner result,
and Execution result evidence before reporting completion. It does not create
or insert Beef Trim, collect Ground Beef, dispatch Scheduler Work, receive
Execution authority, or retry a terminal failure. The completed output remains
in the Grinder.

## Remaining Gates

- General workstation Execution framework.
- Bandsaw, Packaging Table, and development workstation Execution migration.
- Employee Patty Former operation, additional employee Grinder recipes,
  product carrying, output collection, logistics, job claiming, and autonomous
  Production dispatch.
- Additional Production-backed workstation execution beyond the promoted Grinder path and IM-018 narrow manual two-step chain.
- Economic Inventory and Transaction integration for player workstations.
- Allocation and Planning automation.
- Operator recovery UI for unknown outcomes.
- Automatic checkpoint recovery and Evidence archival.
- Manual client usability approval.
- Chunk unload/reload, world reload, and full server-restart recovery tests.
