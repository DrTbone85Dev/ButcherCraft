# ButcherCraft Multi-Output Transformations

Status: v0.6.7 Bandsaw migration on pure transaction foundation

## Purpose

Multi-output transformations let one accepted `TransformationDefinition` produce an ordered collection of material outputs atomically. This document covers the pure transformation engine layer and the v0.6.7 Bandsaw bridge that validates the live Bandsaw proof through that layer.

The goal is to keep transaction safety in pure Java while letting Minecraft-facing workstations adapt inventory snapshots at a narrow boundary.

## Transaction Model

The transaction model is pure Java and lives under:

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

The live Bandsaw proof now uses the built-in `butchercraft:break_beef_forequarter` transformation definition with the full eight-output forequarter cut list.

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

The live Bandsaw uses the atomic transformation strategy. Its ItemStack inventory is adapted to pure material stores for transaction validation, while the existing workstation controller still owns progress, save/load, output ItemStack creation, and final slot insertion.

## Out Of Scope

Version 0.6.7 does not add:

- Datapack loading.
- Resource reload listeners.
- JSON resource discovery.
- Direct Minecraft inventory mutation from the pure transformation package.
- General ItemStack conversion.
- General product-to-item mapping.
- Quality, freshness, temperature, packaging, employee, maintenance, commerce, or MCDA behavior.

## Remaining Work Before Datapack Integration And Full Fabrication

After the Bandsaw proof migration, the project still needs:

- Datapack loading for transformation definitions.
- Reload-scoped validation that transformation ids match processing-operation ids where compatibility is required.
- A real product item factory to replace the development fixture mapping.
- Full carcass and primal fabrication catalogs beyond the single beef forequarter proof.
- GameTest or manual in-game verification coverage for the live Bandsaw path before public gameplay reliance.
