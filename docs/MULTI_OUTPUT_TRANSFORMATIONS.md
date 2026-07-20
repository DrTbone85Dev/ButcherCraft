# ButcherCraft Multi-Output Transformations

Status: v0.6.6 pure Java transformation transaction foundation

## Purpose

Multi-output transformations let one accepted `TransformationDefinition` produce an ordered collection of material outputs atomically. This document covers the pure transformation engine layer, not the existing processing-operation Bandsaw path.

The goal is to prove transaction safety before any multi-output workstation is migrated to transformation execution.

## Transaction Model

The v0.6.6 transaction model is pure Java and lives under:

```text
com.butchercraft.transformation
```

Core types:

- `TransformationMaterialStore`: material-store contract for quantity lookup, ordered material snapshots, extraction, insertion, capacity checks, and restore.
- `InMemoryTransformationMaterialStore`: deterministic material store with ordered quantities, optional per-material capacity limits, material-slot capacity, snapshots, extraction, and insertion.
- `TransformationMaterialStoreSnapshot`: immutable snapshot of material quantities used for rollback.
- `TransformationTransaction`: stages a definition, context, accepted evaluation, input store, and output store.
- `TransformationTransactionState`: `CREATED`, `PREPARED`, `COMMITTED`, `REJECTED`, and `ROLLED_BACK`.

`TransformationExecutor` keeps the original side-effect-free execution method and adds a transactional overload that commits against explicit material stores.

## Atomic Commit Behavior

Transaction commit is deterministic:

1. Reconfirm the supplied evaluation is accepted and still matches the definition and context.
2. Validate that the input store can extract every required input.
3. Validate that the output store can insert every declared output.
4. Snapshot both stores.
5. Extract inputs in definition order.
6. Insert outputs in definition order.
7. If any mutation fails after partial progress, restore both snapshots and report rollback.

Rejected or rolled-back executions do not expose partial outputs.

## Multi-Output Ordering

Outputs are inserted in the same order as `TransformationDefinition.outputs()`.

The engine-level test fixture uses a small forequarter-style transformation:

```text
butchercraft:beef_forequarter -> [
  butchercraft:beef_chuck,
  butchercraft:beef_rib,
  butchercraft:beef_trim
]
```

This fixture exists only to prove the transformation transaction model. It does not migrate the live Bandsaw machine.

## Capacity Failures

`InMemoryTransformationMaterialStore` can reject output insertion through:

- Material-slot capacity.
- Per-material quantity capacity.
- Quantity unit mismatch.
- Arithmetic overflow.

If an output store cannot accept all produced materials, the transaction returns `OUTPUT_REJECTED` and leaves input and output stores unchanged.

If a store reports capacity but throws during commit, the transaction restores both snapshots and returns `TRANSACTION_ROLLED_BACK`.

## Compatibility

One-input/one-output transformations remain valid. Tests prove the built-in `butchercraft:grind_beef` transformation can still consume Beef Trim and produce Ground Beef through the transaction-capable executor.

The live Grinder workstation behavior is unchanged. It still uses the transformation strategy as a validation bridge and delegates product quality, ItemStack creation, and inventory mutation to the existing workstation processing path.

## Out Of Scope

Version 0.6.6 does not add:

- Bandsaw migration to transformation execution.
- Datapack loading.
- Resource reload listeners.
- JSON resource discovery.
- Minecraft inventory mutation.
- ItemStack conversion.
- Product-to-item mapping changes.
- Quality, freshness, temperature, packaging, employee, maintenance, commerce, or MCDA behavior.

## Remaining Work Before Bandsaw Migration

Before the Bandsaw moves to transformation execution, the project still needs:

- Built-in Bandsaw `TransformationDefinition` coverage for the full ordered output set.
- A workstation bridge from committed transformation outputs to existing `Product` and ItemStack output creation.
- Output-slot capacity mapping from workstation inventories into pure material-store capacity checks.
- Error mapping from transformation transaction failures into player-safe workstation failure messages.
- Regression coverage proving Bandsaw block break, output obstruction, save/load, and paired-block behavior stay atomic on the new path.
