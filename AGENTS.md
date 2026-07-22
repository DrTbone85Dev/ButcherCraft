# AGENTS.md

Instructions for future coding agents working on ButcherCraft.

## Repository Layout

The repository is expected to become a NeoForge 1.21.1 Java 21 project.

Expected layout after project initialization:

- `src/main/java/com/butchercraft/` - mod source.
- `src/main/resources/` - assets, data, `META-INF/neoforge.mods.toml`, pack metadata.
- `src/generated/resources/` - generated data, if configured by the MDK.
- `src/test/java/` - Java tests, if test infrastructure is enabled.
- `docs/` - additional long-form design notes, if needed.
- Root planning files - keep `PROJECT_VISION.md`, `GAMEPLAY_DESIGN.md`, `TECHNICAL_ARCHITECTURE.md`, `MODULE_PLAN.md`, `MILESTONES.md`, `DECISIONS.md`, `KNOWN_LIMITATIONS.md`, `PROJECT_RULES.md`, and `AGENTS.md` current.

If an owner later changes the approved mod id or Java package, update these docs and code together.

Before implementation work, read `PROJECT_RULES.md` and follow it as the authoritative list of ButcherCraft invariants.

## Build Commands

Use the Gradle wrapper once the project exists.

Windows:

- `.\gradlew.bat build`
- `.\gradlew.bat runClient`
- `.\gradlew.bat runServer`
- `.\gradlew.bat runData`
- `.\gradlew.bat clean`
- `.\gradlew.bat compileJava`
- `.\gradlew.bat test`

Recommended verification order:

- `.\gradlew.bat --version`
- `.\gradlew.bat clean`
- `.\gradlew.bat compileJava`
- `.\gradlew.bat test`
- `.\gradlew.bat runData`
- `.\gradlew.bat build`
- `.\gradlew.bat runClient`, only when the environment supports a usable client launch
- `.\gradlew.bat runServer`, only when the environment supports a usable dedicated-server launch

macOS/Linux:

- `./gradlew build`
- `./gradlew runClient`
- `./gradlew runServer`
- `./gradlew runData`

Do not claim a command passed unless you actually ran it.

## Test Commands

- `.\gradlew.bat test` or `./gradlew test`, if tests are configured.
- Use GameTests when gameplay behavior needs in-game validation.
- Add pure Java tests for deterministic services such as quality calculation, cleanliness aggregation, refrigeration capacity summaries, order acceptance, and inspection escalation.
- Add pure Java tests for `com.butchercraft.engine` domain logic without importing Minecraft or NeoForge.
- Keep `com.butchercraft.world.goods` definitions, registries, graph validation, and persistence independent of Minecraft, NeoForge, ItemStack, inventory quantities, and gameplay state.
- Keep processing framework fixtures as test data unless a visible gameplay milestone explicitly schedules Minecraft content.
- Keep product data integration outside `com.butchercraft.engine`; ItemStack data-component adapters belong under product integration packages.
- Keep datapack definition registries, `ResourceLocation`, `ResourceKey`, `Holder`, and `RegistryAccess` usage outside `com.butchercraft.engine`.
- Processing-family differences such as red meat versus future poultry workflows must be represented through data-driven processing profiles and operation compatibility data, not species-specific Java switches.
- Compile and test after each milestone.
- For this repository foundation, the harmless development item and diagnostic command are allowed. They must not become gameplay systems.
- For Milestone 2B, the Development Processing Workstation is allowed as a temporary fixture. It must remain server-authoritative, development-scoped, and separate from final grinder or machine content.
- If a Codex sandbox blocks Java `Path.toRealPath()`, NeoForge artifact extraction may fail before compilation. Report that exact limitation instead of claiming compile, datagen, test, build, client, or server success.

## Data-Generation Commands

- Run `runData` after adding generated models, blockstates, loot tables, recipes, tags, language entries, or datapack registry defaults.
- Built-in species, processing-profile, product, and processing-operation definitions are datapack registry defaults and must be generated deterministically.
- Review generated files before committing.
- Keep generated data deterministic.

## Naming Conventions

- Registry names use lowercase snake case, such as `manual_processing_station`.
- Java classes use PascalCase.
- Java methods and fields use camelCase.
- Constants use UPPER_SNAKE_CASE.
- Translation keys follow the mod id namespace, such as `block.butchercraft.manual_processing_station`.
- Data component names should describe gameplay data, such as `quality`, `freshness`, `temperature`, and `packaging`.
- Avoid names that imply real USDA branding or real regulatory authority.

## Client and Server Rules

- Gameplay state changes are server-authoritative.
- The client may request actions but never decides product quality, payment, inspection outcome, employee skill, or saved business state.
- Keep client-only classes under `com.butchercraft.client`.
- Do not import Minecraft client classes from common code.
- Verify dedicated server launch before considering a milestone complete.

## Registration Conventions

- Use NeoForge 1.21.1 registration patterns.
- Prefer `DeferredRegister` for blocks, items, block entities, menus, entity types, data components, attachment types, and recipe serializers.
- Register on the mod event bus from the mod entry point.
- Do not query registries while registration is still running.
- Keep registry helper classes small and grouped by registry type.
- Use tags or documented APIs for expansion integration.

## Persistence Requirements

- Product item state belongs in item data components.
- Product-bearing stacks are max stack size one until the project deliberately defines how engine quantity and ItemStack count interact.
- World/facility/business/order/inspection state belongs in `SavedData` unless a narrower holder is clearly better.
- Immutable economic good definitions and relationships persist independently in schema-versioned `<world>/butchercraft/goods.json`; runtime quantities must belong to future inventory or economy owners.
- Entity-specific employee state should use attachments.
- Never create placeholder systems that silently discard saved data.
- Add version fields or migration plans before public saves.
- Preserve unknown ids from missing expansions where practical.

## Documentation Requirements

Update planning docs when behavior changes:

- `PROJECT_VISION.md` for player-facing direction.
- `GAMEPLAY_DESIGN.md` for system rules and interactions.
- `TECHNICAL_ARCHITECTURE.md` for package ownership, persistence, APIs, networking, and performance.
- `MODULES.md` for active Core, industry, and compatibility boundaries; `MODULE_PLAN.md` preserves the historical Meat Processing expansion plan.
- `MILESTONES.md` for delivery plan and verification.
- `DECISIONS.md` for accepted or superseded architecture decisions.
- `KNOWN_LIMITATIONS.md` for risks, prototypes, and unresolved issues.
- `PROJECT_RULES.md` for non-negotiable project invariants.
- `AGENTS.md` when contributor workflow changes.

Public APIs intended for expansions must be documented before expansion code depends on them.

## Definition of Done

A milestone is done only when:

- Included work is implemented.
- Excluded work has not leaked into scope.
- Saved data is not silently discarded.
- Placeholder assets exist for unfinished art.
- `build` passes.
- `runData` passes when generated data changed.
- Tests pass when tests exist.
- Dedicated server safety has been checked for client/server changes.
- Manual in-game verification listed in `MILESTONES.md` has been performed.
- Documentation and decision logs are updated.

## Prohibited Shortcuts

- Do not implement substantial gameplay outside the active milestone.
- Do not add unrelated refactors.
- Do not use APIs from other Minecraft or NeoForge versions.
- Do not hide failed persistence behind default empty data.
- Do not put client-only code in common packages.
- Do not hard-code expansion internals into core.
- Do not create a separate downloadable mod for every machine.
- Do not add speculative dependencies without documenting why they are needed.
- Do not use USDA branding or graphic animal-processing visuals.
- Do not claim testing that was not actually performed.

## Placeholder Asset Policy

Use placeholder assets when final art is unavailable.

Requirements:

- Minecraft-style, abstract, and non-graphic.
- Clear enough for playtesting.
- Named so they are easy to replace.
- Documented in milestone notes if they remain at milestone completion.

## Working Style

- Prefer straightforward, maintainable Java over clever abstractions.
- Keep changes scoped to the milestone.
- Read surrounding code before editing.
- Use existing project patterns once they exist.
- Ask for owner approval before changing the mod id or package, and on first product, default strictness, and expansion boundary changes.
- Compile and test after each milestone.
