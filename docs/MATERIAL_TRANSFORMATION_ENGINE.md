# ButcherCraft Material Transformation Engine

Status: v0.6.0 foundation

## Purpose

The Material Transformation Engine is the first generic pure Java layer for describing whether a requested material transformation can run from explicit material amounts and an optional workstation capability.

This foundation extends the existing processing framework. It does not replace `ProcessingOperation`, datapack processing-operation registries, Grinder behavior, Bandsaw behavior, workstation block entities, menus, screens, or item data components.

## Package

The new domain lives under:

```text
com.butchercraft.transformation
```

It imports only pure Java and existing Minecraft-independent engine value types such as `EngineId`, `ProductQuantity`, and `ProcessingDuration`. It must not import Minecraft or NeoForge classes.

## Domain Model

`TransformationId` wraps the existing engine identifier validation for stable transformation ids.

`MaterialAmount` pairs a material or product definition id with a positive exact `ProductQuantity`. It rejects zero, negative, and intentionally oversized amounts before they can enter transformation arithmetic.

`TransformationInput` describes one required `MaterialAmount`.

`TransformationOutput` describes one produced `MaterialAmount` plus a classification:

```text
PRIMARY
BYPRODUCT
WASTE
```

`TransformationDefinition` stores a stable id, ordered non-empty input and output lists, positive duration, and an optional workstation capability id. It defensively copies lists, preserves order, and rejects duplicate input or output material ids.

`TransformationContext` stores the currently available material amounts and optional runtime workstation capability. It does not contain worlds, block entities, inventories, menus, clients, networking, registry access, or ItemStacks.

`TransformationEvaluation` returns a stable result code and readable message. Codes currently include:

```text
ACCEPTED
MISSING_INPUT
INSUFFICIENT_INPUT
UNSUPPORTED_CAPABILITY
INVALID_CONTEXT
```

`TransformationEvaluator` validates a definition and context deterministically without mutating inventory or material state.

`ProcessingOperationTransformationAdapter` converts an existing `ProcessingOperation` and concrete input amount into a compatible `TransformationDefinition`. This is a bridge for future migration and comparison work, not a replacement for the current processing pipeline.

## Relationship To Existing Processing

The existing processing framework remains authoritative for current workstation processing:

- `ProcessingOperation` still describes product state, source category, yield ratios, quality deltas, validation rules, and modifiers.
- `ProcessingDefinitionResolver` still converts datapack-backed operation definitions into `ProcessingOperation`.
- `WorkstationOperationResolver` still chooses operations for the Grinder, Bandsaw, and development workstation.
- `WorkstationProcessingController` still owns inventory reservation, progress, transaction commit, and output insertion.

The transformation domain is narrower. It answers the generic question:

```text
Can these material amounts satisfy this transformation definition on this capability?
```

Future slices can gradually map definition data into transformation definitions once the material model is proven, while preserving the current processing behavior.

## Deterministic Evaluation Order

Evaluation order is stable:

1. Reject missing definition or context as `INVALID_CONTEXT`.
2. Reject unsupported workstation capability as `UNSUPPORTED_CAPABILITY`.
3. Check required inputs in definition order.
4. Return the first `MISSING_INPUT` or `INSUFFICIENT_INPUT`.
5. Accept only after all required inputs pass.

The evaluator does not consume inputs, create outputs, inspect ItemStacks, or commit transactions.

## Out Of Scope

The v0.6.0 foundation intentionally does not add:

- Datapack migration to transformation definitions.
- Grinder, Bandsaw, block entity, menu, or screen changes.
- ItemStack or product data component changes.
- Inventory mutation or transaction commits.
- Quality, freshness, temperature, packaging, lineage, lot history, employees, operator skill, energy, maintenance, spoilage, organizations, MCDA, commerce, or probabilistic yields.
- Optional ingredients, tags, substitutes, catalysts, random outputs, or recipe-selection UI.
- Public expansion APIs.

## Next Proposed Slice

The next slice should adapt resolved datapack `ProcessingOperationDefinition` data into `TransformationDefinition` values at the integration boundary, including workstation capability ids and ordered output classifications when the data model supports them.

After that, the project can compare transformation evaluation with the existing workstation resolver in tests before deciding whether any live workstation path should consume the new model.
