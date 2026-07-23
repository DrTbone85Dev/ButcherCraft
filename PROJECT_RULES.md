# ButcherCraft Project Rules

Status: authoritative operational rules governed by `CONSTITUTION.md`

These rules are non-negotiable for ButcherCraft implementation work. They apply the permanent invariants in `CONSTITUTION.md`, which is the project's highest-level architectural authority. `CORE_PRINCIPLES.md` provides a concise companion summary. If wording conflicts, the Constitution governs unless formally amended through its architectural decision process.

## Platform Principles

- ButcherCraft Core is the shared deterministic regional simulation platform. Meat Processing is its flagship industry implementation, not the boundary of the platform.
- Immutable world identity and historical records remain separate from mutable runtime state.
- All future systems use the authoritative Simulation Clock and Event Framework instead of creating parallel time models.
- Core, industry modules, and compatibility modules communicate through stable ids, immutable summaries, events, registries, and deliberately documented contracts.
- Industry modules do not create parallel economies, world identities, business identities, or persistence foundations.
- Compatibility modules translate external capabilities into shared contracts and preserve source-mod ownership.
- Durable structures require schema versions and deliberate migration behavior.
- Long-term compatibility is protected through stable ids, explicit versioning, deterministic migrations, and preservation of unresolved optional references where practical.

## Core Rules

1. No product duplication.
   - Every processing operation must be transaction-safe.
   - Inputs cannot be consumed without a valid output plan.
   - Outputs cannot be created more than once.
   - Failed or cancelled operations must not silently lose product.

2. No hidden randomness.
   - Quality, yield, and failure changes must come from identifiable causes.
   - Any randomness added later must be explicit, seeded where practical, testable, and documented.

3. Minecraft-independent domain logic.
   - Core world, simulation, business, workforce, economic, product, and processing rules must not depend on Minecraft or NeoForge classes.
   - Minecraft integration may depend on pure domains.
   - Pure domains must never depend on Minecraft integration.

4. Realism serves gameplay.
   - Real-world processes should be modeled when they create meaningful player decisions.
   - Complexity that only adds repetitive burden should be simplified or configurable.

5. Every core system must be testable.
   - Domain logic must be unit-testable without launching Minecraft.
   - Important invariants and state transitions require tests.

6. No God classes.
   - Responsibilities must be divided into focused value objects, services, and contracts.
   - Avoid manager classes that control unrelated systems.

7. Explicit failure outcomes.
   - Do not use null to represent domain failure.
   - Failure reasons must be inspectable and documented.

8. Immutable data by default.
   - Prefer immutable records and value objects.
   - Mutable state must have a clear owner and explicit transition rules.

9. Server authority.
   - Future gameplay changes involving inventory, money, product state, ownership, production, employment, orders, markets, or progression must be validated and committed server-side.

10. No speculative APIs.
    - Do not create broad public expansion APIs until a real consumer exists.
    - Keep internal APIs revisable during early development.

11. No unrelated refactors.
    - Each milestone must remain within its approved scope.

12. Verification honesty.
    - Never claim a build, test, data-generation run, client launch, or server launch succeeded unless it was actually run and succeeded.
    - External owner verification must be reported as external verification.

## Definition of Done

A milestone is done only when its included scope is implemented, excluded scope has not leaked in, tests cover important invariants, persistence and duplication risks are handled or explicitly deferred, documentation reflects the result, and the required verification commands have actually succeeded in the verifying environment.

## Scope Control

Do not add a feature merely because it would be convenient later. Record future concerns in the planning documents instead of implementing speculative systems.

## Documentation

Behavioral, architectural, persistence, or workflow changes must update the appropriate planning document in the same milestone. Public APIs intended for expansions must be documented before expansion code depends on them.

## Persistence Safety

No placeholder or prototype may silently discard saved product, business, order, employee, cleanliness, refrigeration, inspection, or facility data. If persistence is not ready, the system must fail visibly in development or preserve the unknown data inertly.

## Dependency Direction

The dependency direction is engine -> integration only from the integration side: Minecraft and NeoForge code may call the engine, but the engine must not import Minecraft or NeoForge packages.

## Performance

Prefer deterministic, bounded work. Avoid per-tick full-facility scans, unbounded entity searches, heavy per-stack histories, and noisy full-state networking. Add caps, caches, coarse intervals, or profiling before shipping large simulations.
