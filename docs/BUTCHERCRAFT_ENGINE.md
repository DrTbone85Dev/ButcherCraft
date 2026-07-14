# ButcherCraft Engine

Status: Milestone 1B foundation

## Purpose

The ButcherCraft engine is a small Minecraft-independent domain layer for deterministic product processing. It exists so product, quality, quantity, modifier, result, and transaction rules can be tested without launching Minecraft.

This milestone creates no visible gameplay, items, blocks, block entities, menus, networking, machines, employees, refrigeration, cleanliness, customers, business accounts, MCDA systems, datapack recipes, or expansion APIs.

## Dependency Direction

Engine classes live under `com.butchercraft.engine`.

Allowed direction:

- Minecraft and NeoForge integration may depend on the engine.
- The engine must not import Minecraft or NeoForge classes.

A unit test scans engine source files for forbidden `net.minecraft` and `net.neoforged` imports.

## Product Model

`Product` is an immutable value object with:

- Stable `EngineId` product type.
- `ProductCategory` source category.
- `ProcessingState`.
- `ProductQuantity`.
- `ProductQuality`.

Equality is value equality across all fields. Metadata, temperature, freshness, packaging, traceability, and Minecraft registry identifiers are intentionally excluded from Milestone 1B.

## Quality Scale

`ProductQuality` stores an integer score from `0` to `1000`.

Direct construction rejects scores outside that range. Operational adjustments clamp after applying documented modifiers so a prepared output always has valid quality.

Visible grade boundaries:

| Grade | Inclusive score range |
| --- | --- |
| Poor | 0-199 |
| Fair | 200-399 |
| Good | 400-699 |
| Excellent | 700-899 |
| Premium | 900-1000 |

The grade names are fictional gameplay labels and avoid real regulatory terminology.

## Quantity Precision

`ProductQuantity` stores an exact `long` amount plus a `QuantityUnit`.

Milestone 1B uses grams for processing weight. A piece unit exists only to prove compatibility validation and no-conversion behavior.

Rules:

- Negative quantities are rejected.
- Zero is allowed deliberately.
- Addition and subtraction require matching units.
- Addition uses overflow-checked arithmetic.
- Subtraction rejects underflow.
- No silent conversion occurs between units.

`YieldRatio` applies exact integer ratios and rejects ratios that would require rounding.

## Modifier Ordering

`ProcessingModifier` is immutable and inspectable. It records:

- Stable id.
- Human-readable reason.
- Category.
- Numeric effect.
- Priority.

Milestone 1B implements quality and warning categories. Yield modifiers are deferred until a concrete gameplay consumer needs them.

`ModifierSystem` sorts modifiers by:

1. Priority, ascending.
2. Modifier id.
3. Category.
4. Reason.
5. Effect.

The ordered modifiers are returned in the result so future diagnostics and UI can explain why output changed.

## Operation Result

`OperationResult` is a sealed interface with immutable success and failure records.

It supports:

- Success or failure.
- Explicit failure reason.
- Proposed output.
- Committed output.
- Applied modifiers.
- Resulting quality.
- Resulting quantity.
- Warnings.
- Transaction state.

Invalid combinations are rejected. Success cannot use failure terminal states, prepared success requires proposed output, committed success requires committed output, and failure never contains committed output.

## Transaction States

Normal transaction flow:

```text
CREATED -> VALIDATED -> PREPARED -> COMMITTED
```

Terminal failure states:

```text
CANCELLED
REJECTED
FAILED
```

Allowed transitions:

| From | Allowed action | To | Notes |
| --- | --- | --- | --- |
| CREATED | validate valid input | VALIDATED | No output is created. |
| CREATED | validate invalid input | REJECTED | Input is preserved. |
| CREATED | prepare valid input | PREPARED | Validation occurs first, then proposed output is created. |
| VALIDATED | prepare | PREPARED | Proposed output is inspectable but not committed. |
| PREPARED | commit | COMMITTED | Output is committed exactly once. |
| CREATED, VALIDATED, PREPARED | cancel | CANCELLED | Input is preserved; proposed output may remain inspectable. |
| PREPARED | prepare again | PREPARED | The action is rejected; state remains prepared. |
| COMMITTED | commit again | COMMITTED | The action is safely rejected and no second output is created. |
| COMMITTED | cancel | COMMITTED | The action is rejected. |
| CANCELLED | commit | CANCELLED | The action is rejected. |

Preparation failures caused by invalid exact arithmetic move the transaction to `FAILED`.

## Transaction Guarantees

The engine transaction:

- Validates input and operation requirements.
- Prepares output without consuming input.
- Commits output once.
- Cancels before commit.
- Rejects invalid input without mutating the input product.
- Prevents double output.
- Prevents commit after cancellation.
- Prevents cancellation after commit.
- Exposes explicit failure reasons.
- Keeps proposed and committed output inspectable for future server-side inventory integration.

## Future Minecraft Integration Boundary

Future Minecraft code should treat the engine as a pure domain service:

- Convert item stack data components into engine products.
- Run transaction validation and preparation server-side.
- Commit Minecraft inventory changes only after an engine transaction has a valid committed output plan.
- Store or synchronize only the compact summaries needed by clients.
- Keep registry ids, item stacks, worlds, block entities, menus, and packets outside the engine packages.
