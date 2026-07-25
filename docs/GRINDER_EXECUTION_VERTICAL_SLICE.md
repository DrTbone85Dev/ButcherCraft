# Grinder Execution Vertical Slice

Status: IM-012 implemented.

This note records the first player-facing workstation operation connected to the generic Execution runtime. It does not authorize a public workstation API, broad Production migration, Allocation integration, worker automation, compensation, automatic checkpoint recovery, or additional workstation operations.

## Selected Slice

The selected operation is the existing grinder transformation that accepts one valid beef trim fixture stack and produces one ground beef fixture output through the existing grinder menu, controller, transformation strategy, and workstation ItemStack commit plan.

The fixture duration remains 60 server ticks, derived from the existing 3000 millisecond processing definition. Progress remains workstation-owned presentation state.

## Player Flow

The player opens the existing grinder, inserts a valid product-bearing beef trim fixture stack, observes processing progress through the existing menu data, waits for completion, and retrieves one ground beef output from the output slot.

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

The generic Execution Scheduler handler remains `IDEMPOTENT` and owner-result-required as defined by IM-011. The selected grinder mutation is not classified as `TRANSACTION_BACKED` because this fixture uses workstation-owned Minecraft ItemStack slots, not the economic Inventory runtime.

## Inventory Mutation Boundary

The selected grinder slice does not use economic `InventoryManager` quantities and does not submit economic Transactions. The authoritative mutation boundary for this fixture remains the workstation-owned `WorkstationInventoryCommitPlan`.

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

## Remaining Gates

- General workstation Execution framework.
- Additional grinder operations.
- Bandsaw, Packaging Table, and development workstation Execution migration.
- Production-backed workstation execution.
- Economic Inventory and Transaction integration for player workstations.
- Allocation and Planning automation.
- Operator recovery UI for unknown outcomes.
- Automatic checkpoint recovery and Evidence archival.
