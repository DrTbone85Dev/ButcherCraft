# ButcherCraft Material Transformation Engine

Status: v0.6.9 datapack-backed content snapshot integration

## Purpose

The Material Transformation Engine is the first generic pure Java layer for describing whether a requested material transformation can run from explicit material amounts and a workstation capability.

This foundation extends the existing processing framework. Version 0.6.1 connects the Grinder to transformation execution through a compatibility bridge. Version 0.6.2 adds an immutable transformation registry as the authoritative source of transformation definitions used by that bridge. Version 0.6.3 formalizes `TransformationDefinition` as the canonical immutable schema for future transformations. Version 0.6.4 adds a separate pure Java product definition registry so transformation product ids can be validated against authoritative product data. Version 0.6.5 adds a pure Java serialization contract for the canonical transformation schema. Version 0.6.6 adds pure Java atomic multi-output material transactions. Version 0.6.7 migrates only the Bandsaw proof to registry-driven atomic transformation validation. Version 0.6.8 loads transformation definitions from datapack JSON resources. Version 0.6.9 loads product definitions from datapack JSON resources and activates products and transformations as one content snapshot. It does not replace `ProcessingOperation`, datapack processing-operation registries, other workstation behavior, menus, screens, or item data components.

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

`TransformationDefinition` is the canonical immutable schema for future transformation definitions. It stores:

- Stable `TransformationId`.
- Nonblank display name.
- Positive schema version.
- Explicit required capability field.
- Ordered non-empty input list.
- Ordered non-empty output list.
- Positive duration.
- Yield ratio.
- Immutable metadata map keyed by `EngineId`.

Definitions validate during construction. They reject incomplete data, blank display names, invalid schema versions, duplicate input materials, duplicate output materials, inconsistent same-unit yield totals, and blank metadata values.

`TransformationDefinition.Builder` is the preferred fluent construction API for new definitions. Legacy constructors remain only as compatibility bridges for existing Grinder and adapter code.

`TransformationRegistryBuilder` is the mutable construction boundary for transformation definitions. It rejects null definitions and duplicate transformation ids.

`TransformationRegistry` is immutable after build. It supports ordered `register` output through its builder, `contains`, `find`, `size`, `stream`, and `findByCapability`. Iteration and capability queries preserve registration order.

`WorkstationCapability` is the pure Java capability advertisement used by transformation evaluation. It stores a workstation id and the capability ids that workstation advertises, such as `butchercraft:grinding`.

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

`TransformationExecutor` executes only a previously accepted evaluation that still matches the supplied definition and context. The side-effect-free execution path returns the definition's declared outputs as a pure result. The transactional execution path commits against pure `TransformationMaterialStore` instances only after input extraction and output insertion are both validated.

`TransformationMaterialStore` is the pure material-storage contract for transformation transactions. `InMemoryTransformationMaterialStore` stores ordered material quantities, optional per-material capacity limits, material-slot capacity, snapshots, extraction, and insertion without Minecraft or NeoForge dependencies.

`TransformationTransaction` stages a definition, context, accepted evaluation, input store, and output store. It validates all required input extraction and ordered output insertion before commit. If a commit-time mutation fails after partial progress, it restores both stores from snapshots and reports rollback.

`TransformationProductReferenceValidator` validates transformation input and output product ids against a `ProductRegistry` after definitions are constructed. It reports missing products and quantity-unit mismatches without requiring product registry access during transformation construction.

The `com.butchercraft.transformation.serialization` package defines the stable external schema contract for serialized transformation definitions. It includes serializer and deserializer interfaces, canonical serialized records, frozen field-name constants, a schema-version abstraction, and a future migration contract. This layer remains pure Java.

The `com.butchercraft.transformation.datapack` package parses JSON resources into the serialized schema, validates references against a supplied product registry, and assembles immutable transformation registries. Minecraft reload listener code lives outside the pure transformation package. Version 0.6.9 coordinates this through `ContentSnapshotService`, which loads products first and then transformations against the candidate product registry.

`ProcessingOperationTransformationAdapter` converts an existing `ProcessingOperation` and concrete input amount into a compatible `TransformationDefinition`. This remains available for compatibility tests and future migration work, but live Grinder and Bandsaw transformation execution now query the transformation registry by resolved operation id.

## Relationship To Existing Processing

The existing processing framework remains authoritative for product quality, validation, transaction state, and ItemStack output creation:

- `ProcessingOperation` still describes product state, source category, yield ratios, quality deltas, validation rules, and modifiers.
- `ProcessingDefinitionResolver` still converts datapack-backed operation definitions into `ProcessingOperation`.
- `WorkstationOperationResolver` still chooses operations for the Grinder, Bandsaw, and development workstation.
- `WorkstationProcessingController` still owns inventory reservation, progress, processing transaction commit, ItemStack creation, and output insertion.
- The Grinder opts into a transformation execution strategy that looks up the registered transformation by resolved operation id, rebases that definition to the current input quantity, evaluates and executes it, then delegates product commit to the existing processing transaction.
- The Bandsaw opts into the atomic transformation execution strategy. `WorkstationInventoryMaterialStore` adapts the Bandsaw's ItemStack input and output slots to pure material stores, validates a transactional execution, then lets the existing controller commit the same ordered products to Minecraft inventory.
- Development workstation and all other future un-migrated workstations continue using the legacy workstation execution strategy.

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

The evaluator does not consume inputs, create outputs, inspect ItemStacks, or commit transactions. The executor creates a pure execution result from an accepted evaluation, or commits atomically against explicit pure material stores when the transactional overload is used.

## Out Of Scope

The v0.6.9 content slice intentionally does not add:

- Implemented schema migrations.
- Datapack-driven product category catalogs.
- Product definition embedding inside transformation definitions.
- Smoker, packaging, cooler, menu, or screen migration.
- ItemStack or product data component schema changes.
- Direct Minecraft inventory mutation from the pure transformation package.
- Quality, freshness, temperature, packaging, lineage, lot history, employees, operator skill, energy, maintenance, spoilage, organizations, MCDA, commerce, or probabilistic yields.
- Optional ingredients, tags, substitutes, catalysts, random outputs, or recipe-selection UI.
- Public expansion APIs.

Version 0.6.8 moves the built-in Grinder and Bandsaw transformation definitions into bundled datapack JSON resources. Version 0.6.9 moves the current product definitions into bundled datapack JSON resources and introduces an active content snapshot so product and transformation registries swap together only after successful validation. Expanded transformation catalogs remain out of scope.

## Next Proposed Slice

Before expanded fabrication, the project should define larger catalogs, product-to-item creation rules, schema migration lookup, category/catalog ownership, reload-scoped user-facing diagnostics, and validation behavior for custom content beyond the current built-in proofs.
