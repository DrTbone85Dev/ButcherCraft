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
- Root planning files - keep `CONSTITUTION.md`, `PROJECT_VISION.md`, `GAMEPLAY_DESIGN.md`, `TECHNICAL_ARCHITECTURE.md`, `MODULE_PLAN.md`, `MILESTONES.md`, `DECISIONS.md`, `KNOWN_LIMITATIONS.md`, `PROJECT_RULES.md`, and `AGENTS.md` current.

If an owner later changes the approved mod id or Java package, update these docs and code together.

Before implementation work, read `CONSTITUTION.md` first, then `PROJECT_RULES.md`. The Constitution is the highest-level architectural authority; Project Rules apply its invariants to day-to-day engineering work.

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
- Keep `com.butchercraft.architecture.validation` pure Java, deterministic, explicit, and free of reflection, runtime scanning, hidden clocks, and mutable global state.
- Update the explicit architecture manifest and validation tests when an owner-approved decision changes ownership, dependency direction, persistence, Scheduler stages, registries, or simulation invariants.
- Keep `com.butchercraft.world.allocation` pure Java and independent of Planning, Production, Scheduler, Inventory, Transactions, Minecraft, and NeoForge. M22A defines immutable structural artifacts; M22B permits deterministic `AllocationSetRuntime` lifecycle mutation only through `AllocationRuntimeService`; M22C permits the explicit-input deterministic Cycle, detached temporary Capacity ledger, first-fit complete-Set evaluation, and atomic candidate-state publication. Authoritative mutation must remain confined to publication. Do not add providers, persistence, Scheduler stage 350, Planning handoff, Production gates, live observations, or gameplay integration without a later owner-authorized milestone.
- Keep `com.butchercraft.world.planning` deterministic and independent of Minecraft, NeoForge, ItemStack, wall-clock time, background mutation, and unrestricted manager access.
- Keep `com.butchercraft.world.simulation.scheduler` pure Java, deterministic, bounded, and dependent on supplied Simulation Clock ticks only. Scheduler handlers must preserve owning-domain validation and transaction boundaries.
- Keep `com.butchercraft.world.production` pure Java and industry-neutral. Processes and Plans are immutable, Runs own mutable lifecycle, Scheduler owns eligibility, Clock owns time, and every completed quantity change must use one APPLIED Production Transaction.
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
- Immutable economic actor definitions persist independently in schema-versioned `<world>/butchercraft/economic_actors.json`; actor runtime assignments remain separate and actors must reference Goods by `GoodId`, never by ItemStack.
- Economic inventory containers, storage nodes, and runtime Good quantities persist independently in schema-versioned `<world>/butchercraft/inventory.json`; the pure inventory package must not import Minecraft inventory, Container, slot, menu, or ItemStack APIs.
- Runtime economic quantity changes must be submitted through `com.butchercraft.world.transaction`; future systems must not restore direct `InventoryManager` add/remove mutation paths. Transaction history persists independently at `<world>/butchercraft/transactions.json`.
- Economic Planning owns immutable decision artifacts only. It must submit approved executable intent through typed owning-domain adapters, never reserve or mutate Inventory, submit economic Transactions, fulfill Orders, execute Production, transition Scheduler runtime, or advance time. Its six schema-1 files persist under `<world>/butchercraft/planning_*.json` as one complete validated cycle set.
- Economic intent and durable obligations belong to `com.butchercraft.world.economy.order`. Orders and Contracts never mutate Inventory or submit Transactions; fulfillment may reference only APPLIED Transactions and persists independently in schema-versioned `<world>/butchercraft/orders.json` and `<world>/butchercraft/contracts.json`.
- Scheduled simulation Work definitions and separate runtime lifecycles persist at `<world>/butchercraft/simulation_scheduler.json`. Never persist `RUNNING` Work, silently drop unknown Work types, reuse submission sequences, or add automatic catch-up without a documented schema policy.
- Production Process definitions, Plan definitions, and Run runtime state persist at `<world>/butchercraft/production_processes.json`, `production_plans.json`, and `production_runs.json`. Validate the complete candidate set and scheduler/transaction references before publication; never reserve stock implicitly or mutate Inventory outside the Transaction Framework.
- Entity-specific employee state should use attachments.
- Never create placeholder systems that silently discard saved data.
- Add version fields or migration plans before public saves.
- Preserve unknown ids from missing expansions where practical.

## Documentation Requirements

Update planning docs when behavior changes:

- `CONSTITUTION.md` only through the formal architectural decision process when governing philosophy or permanent invariants change.
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
