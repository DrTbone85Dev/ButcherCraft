# ButcherCraft Processing Framework

Status: Milestones 1C through 2E framework

## Purpose

The processing framework extends the Minecraft-independent engine. It describes how an operation is validated, how supplied processing conditions affect the proposal, and how a transaction prepares output without committing it.

The pure framework does not register items, blocks, block entities, menus, screens, recipes, machines, employees, refrigeration, cleanliness simulation, MCDA, customers, business accounts, sounds, art, or public expansion APIs.

## Dependency Direction

Framework classes live under `com.butchercraft.engine`.

Allowed direction:

- Minecraft and NeoForge integration may depend on the engine.
- Engine and framework classes must not import Minecraft or NeoForge packages.

The dependency-boundary test scans engine source files for forbidden `net.minecraft` and `net.neoforged` imports.

## ProcessingOperation

`ProcessingOperation` is an immutable definition of a product transformation.

It owns:

- Stable operation id.
- Human-readable name.
- Required input product type.
- Optional required source category.
- Required input processing state.
- Ordered output definitions.
- Exact base duration in milliseconds.
- Ordered validation rules.
- Static modifiers.
- Whether zero output is permitted.

Each output definition owns output product type, output processing state, exact base yield, base quality adjustment, quantity unit, and whether zero quantity is allowed. Legacy single-output operations are represented as a one-entry output list and still expose first-output helper methods for existing callers.

It does not own transaction state, progress, inventory behavior, machine behavior, employee behavior, recipe loading, or Minecraft data.

Minecraft integration converts game ticks to `ProcessingDuration` at the boundary. Vanilla timing is commonly 50 milliseconds per tick, but the engine does not assume ticks. Milestone 2B performs this conversion in the workstation layer, where `3000` milliseconds resolves to `60` ticks.

## ProcessingContext

`ProcessingContext` is immutable input supplied when an operation is evaluated.

It contains:

- Input product.
- Requested operation.
- Cleanliness factor.
- Operator skill factor.
- Equipment condition factor.
- Additional explicit modifiers.

Factors use an exact 0-1000 scale.

Defaults:

- Cleanliness: `1000`, ideal and neutral.
- Equipment condition: `1000`, ideal and neutral.
- Operator skill: `500`, neutral.

The context represents facts supplied by future integrations. The engine does not determine how a workstation became clean, how an operator gained skill, or how equipment condition was measured.

## ValidationRule

`ValidationRule` evaluates a `ProcessingContext` and returns a `ValidationResult`, never a boolean-only result.

Validation results support:

- Accepted.
- Rejected.
- Stable failure reason code.
- Human-readable explanation for diagnostics.
- Optional non-rejecting warning.

Implemented reusable rules:

- Required product type.
- Required source category when configured.
- Required processing state.
- Minimum input quantity.
- Minimum cleanliness.
- Minimum equipment condition.
- Zero-output prevention when zero output is not permitted.
- Warning-only fixture rule.

Rules are evaluated in the order stored on `ProcessingOperation`. Evaluation stops at the first rejection, and warnings from earlier accepted rules are preserved.

## Evaluation Sequence

`ProcessingEvaluator` has one responsibility: prepare an inspectable proposal.

Sequence:

1. Confirm the context operation id matches the operation being evaluated.
2. Evaluate validation rules in operation order.
3. Stop on the first rejecting rule.
4. Preserve warnings from accepted rules.
5. Collect static operation modifiers, derived context modifiers, and additional context modifiers.
6. Sort modifiers with the existing deterministic modifier order.
7. Calculate proposed output quantities with exact yield arithmetic.
8. Calculate proposed quality for each output through `ProductQuality`.
9. Create immutable proposed output products in definition order.
10. Return an `OperationResult` suitable for `ProcessingTransaction`.
11. Do not commit automatically.

## Exact Yield Strategy

`YieldRatio` stores a numerator and denominator. Yield modifiers use additive basis points:

- `10,000` basis points = 100%.
- `100` basis points = 1%.
- Positive yield modifiers explicitly increase yield.
- Negative yield modifiers reduce yield.

For single-output operations, effective yield is:

```text
base ratio + additive yield basis points
```

The result rounds half up to the nearest smallest quantity unit. Effective yield cannot become negative. The final amount must fit in a `long`, and the output keeps the input quantity unit.

If the final output is zero and the operation forbids zero output, evaluation rejects the operation.

For multi-output operations, each output stores an exact ratio. The allocator uses integer arithmetic, floors each exact share, and then assigns remaining units to the largest fractional remainders until the intended total output amount is reached. Output-order ties are stable. The current framework rejects additive yield modifiers on multi-output operations until a distribution rule is deliberately designed.

## Quality Modifier Flow

Quality starts from the input product quality.

The evaluator applies:

- Operation base quality adjustment.
- Static operation quality modifiers.
- Derived context quality modifiers.
- Additional context quality modifiers.

Derived context quality modifiers are inspectable:

- Cleanliness below 1000 applies a penalty of `(cleanliness - 1000) / 20`.
- Equipment condition below 1000 applies a penalty of `(condition - 1000) / 20`.
- Operator skill applies `(skill - 500) / 25`.

`ProductQuality` clamps the adjusted score to the valid 0-1000 range.

## Transaction Integration

`ProcessingTransaction` can be created from a `ProcessingContext`.

It delegates validation and proposal preparation to `ProcessingEvaluator`, then keeps the existing transaction guarantees:

- Input is not consumed during validation or preparation.
- Proposed outputs are inspectable before commit.
- Commit succeeds exactly once.
- Repeated commit is safely rejected.
- Cancellation before commit preserves input and proposed outputs for inspection.
- Rejection and failure expose stable reasons.
- No inventory reservation or mutation occurs in this milestone.

## Beef Trim To Ground Beef Fixture

Tests include a fixture operation only:

- Input id: `butchercraft:beef_trim`.
- Output id: `butchercraft:ground_beef`.
- Operation id: `butchercraft:grind_beef`.
- Required source category: `BEEF`.
- Required input state: `RAW`.
- Output state: `PREPARED`.
- Duration: `3000` milliseconds.
- Base yield: `9/10`.
- Base quality adjustment: `-5`.
- Minimum quantity: `100` grams.
- Minimum cleanliness: `600`.
- Minimum equipment condition: `500`.

Fixture context values:

- Cleanliness: `800`.
- Operator skill: `600`.
- Equipment condition: `900`.

These values prove the framework. They are not final gameplay balance.

## Future Minecraft Integration Boundary

Milestone 1D adds ItemStack product data integration for product snapshots only. It does not load processing operations from recipes or datapacks and does not add a grinder or station.

Future integration should:

- Convert item data components to `Product`.
- Convert station, player, tool, and environment facts into `ProcessingContext`.
- Run validation and preparation server-side.
- Commit inventory changes only after the transaction is explicitly committed.
- Keep item stacks, worlds, block entities, menus, recipes, packets, and registries outside engine packages.

Milestone 2A introduces the datapack definition layer documented in `docs/PRODUCT_AND_PROCESSING_DEFINITIONS.md`. The resolver validates species, profile, product, and operation references before converting a valid operation definition into this framework's `ProcessingOperation`.

Milestone 2B introduces a server-side workstation controller that supplies prototype cleanliness, operator-skill, and equipment-condition values to `ProcessingContext`. Those values prove the integration path only; final cleanliness, worker skill, and maintenance systems remain deferred.

Milestone 2E adds the Beef Forequarter to Bandsaw fabrication fixture, which proves one operation can return multiple ordered outputs without importing Minecraft or NeoForge into the engine. The named whole-brisket output uses Packer Brisket terminology through definitions and fixture data, not engine logic.

## Material Transformation Engine Relationship

Version 0.6.0 adds `com.butchercraft.transformation` as a pure Java foundation beside the existing engine processing framework. It models generic transformation ids, material amounts, ordered inputs and outputs, optional workstation capabilities, and deterministic acceptance or rejection.

Version 0.6.1 connected the Grinder to that foundation through `WorkstationExecutionStrategy.transformation()`. The strategy executes only an accepted matching evaluation and then delegates product commit to the existing processing transaction so quality and ItemStack behavior remain unchanged.

Version 0.6.2 adds an immutable `TransformationRegistry` and updates the Grinder bridge to query registered transformation definitions by resolved operation id. The compatibility adapter remains available, but the live Grinder path no longer constructs transformation definitions directly from the resolved operation.

Version 0.6.3 expands `TransformationDefinition` into the canonical schema for future transformation serialization. The compatibility adapter and Grinder built-ins now populate the richer schema while preserving existing processing behavior.

Version 0.6.4 adds a pure Java product definition registry beside the transformation registry. Transformation definitions still store product ids only; product-reference validation is a separate pass so future decoded definitions can be checked after registries are assembled.

Bandsaw, datapack registries, menus, screens, and item data components are not migrated in this slice.
