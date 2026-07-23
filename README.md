# ButcherCraft

ButcherCraft is a Minecraft 1.21.1 NeoForge project building a deterministic regional world simulation platform. Industry modules participate in one shared simulation; Meat Processing is the flagship implementation and retains the existing product, workstation, packaging, and facility-management direction.

Registered content remains limited to the existing development fixtures, Grinder, Bandsaw, Packaging Table, retail-product proof, and Packaging Supplies. The platform foundation now also includes immutable regional identity, manufacturers, properties, businesses, families, ownership, historical supply networks, runtime player identity, a simulation clock and event framework, mutable business operations, workforce definitions, economic Goods and Actors, actor-owned Inventory and Storage, a universal Transaction Framework, Orders and Contracts, the deterministic simulation Work pipeline, an industry-neutral Production Framework, and the Economic Planning Engine. The scheduler now runs internal Production and Planning handlers; Planning can compile accepted open Order lines into bounded Production Plans without executing or reserving stock. No live industry Process definitions, pricing, logistics, markets, accounting, or gameplay were added.

## Project Identity

- Project name: ButcherCraft
- Mod ID: `butchercraft`
- Java package: `com.butchercraft`
- Asset namespace: `butchercraft`
- Minecraft: `1.21.1`
- NeoForge: `21.1.235`
- Java: `21`
- Version: `0.9.0-alpha.1`

## Commands

Use the Gradle wrapper from the repository root.

Windows:

```powershell
.\gradlew.bat --version
.\gradlew.bat clean
.\gradlew.bat compileJava
.\gradlew.bat test
.\gradlew.bat runData
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
```

macOS/Linux:

```bash
./gradlew --version
./gradlew clean
./gradlew compileJava
./gradlew test
./gradlew runData
./gradlew build
./gradlew runClient
./gradlew runServer
```

If Java is not on `PATH`, set `JAVA_HOME` to a Java 21 JDK before running Gradle.

In Codex environments without a system Java installation, future sessions may need to provide a local Java 21 JDK and set `JAVA_HOME` for the current shell before running the wrapper. If Java reports `AccessDeniedException` from `Path.toRealPath()` inside the sandbox, NeoForge artifact extraction can fail before source compilation; report that environment limitation explicitly.

## Development Diagnostic

In any world or server console with commands available, run:

```text
/butchercraft info
```

The info command displays the installed ButcherCraft version and current Early Development / Project Meat Counter status for ordinary players without exposing development diagnostics.

In a development world or server console with commands available, run:

```text
/butchercraft diagnostic
```

The diagnostic reports project name, mod id, mod version, Minecraft version, NeoForge version when available, whether common initialization completed, whether the development fixtures are registered, and whether product data can round-trip through the ItemStack component boundary. It does not grant items, modify the world, expose local paths, expose environment variables, or report sensitive system information.

The diagnostic also reports whether the species, processing-profile, product, and processing-operation datapack registries are available, whether the built-in beef, pork, and bison definitions resolve, whether the initial graph validates, and whether the Beef, Pork, and Bison Trim to matching ground-product edges exist.

The diagnostic also reports whether the Development Processing Workstation block, the Grinder, the Bandsaw, their block entities, menus, capabilities, resolver paths, duration conversion, prototype context, graph checks, and temporary output mappings are available.

## Development Item

`butchercraft:development_test_item` is a harmless development-only item. It appears in the ButcherCraft creative tab, has generated English display text, and uses a placeholder texture. It has no gameplay powers or world-changing behavior.

The trim, ground, forequarter, and beef fabrication test products are development-only product data fixtures. They appear in the ButcherCraft creative tab with default `butchercraft:product_data`, max stack size `1`, English display text, product tooltips, and reused placeholder models/textures. They are not food, recipes, commerce products, or final content.

`butchercraft:development_processing_workstation` is a development-only workstation fixture. It opens a plain temporary menu and client screen, accepts the current red-meat trim test products, resolves the single compatible grinding operation, processes for 60 ticks, and outputs the matching ground test product through an explicit temporary mapping.

`butchercraft:grinder` is the current Grinder proof block. It uses `butchercraft:grinding` and the same processing graph/resolver/controller path to process Beef Trim, Pork Trim, and Bison Trim test products without species-specific Grinder behavior.

`butchercraft:bandsaw` is the current Bandsaw proof block. It uses `butchercraft:bandsaw`, the same processing graph/resolver/controller path, and the atomic transformation execution bridge to process Beef Forequarter, Beef Hindquarter, and selected beef primal test products into ordered beef fabrication outputs, including Packer Brisket, T-Bone Steak, Porterhouse Steak, Top Round, Sirloin Steak, and Tri-Tip, without product-specific Bandsaw behavior.

`butchercraft:packaging_table` is the v0.8.0 Packaging Table foundation block. It appears in the ButcherCraft creative tab, can be placed, opens a placeholder inventory GUI with Meat, Tray, Wrap, and Result slots, persists inventory, and exposes item-handler inventory capability. Sprint 2 adds datapack-backed retail product definitions and a `package_retail` graph operation. Sprint C adds Foam Tray, Plastic Wrap Roll, Vacuum Bag, Butcher Paper Roll, Freezer Paper Roll, and Retail Label Roll supply items, plus data-only supply references on packaging definitions. The table does not package products, consume supplies, or execute operations yet.

## Documentation

Planning and architecture documents live at the repository root. Start with `CONSTITUTION.md`, the project's highest-level architectural authority, then read `VISION.md`, `CORE_PRINCIPLES.md`, `PROJECT_RULES.md`, `MODULES.md`, `SIMULATION_MODEL.md`, `ROADMAP.md`, and `TECHNICAL_ARCHITECTURE.md`. Accepted decisions in `DECISIONS.md` record how specific choices conform to that hierarchy. Future economic concepts are bounded in `ECONOMY_MODEL.md`, compatibility direction is recorded in `COMPATIBILITY.md`, and the non-stable extension map is in `docs/API_OVERVIEW.md`.

The immutable economic goods language and its separation from processing products and ItemStacks are documented in `docs/GOODS_FRAMEWORK.md`.

The industry-neutral participant model, actor capabilities, Good relationships, runtime boundary, and definition persistence are documented in `docs/ECONOMIC_ACTORS.md`.

The actor-owned runtime quantity model, storage hierarchy, capacity rules, and separation from Minecraft inventories are documented in `docs/INVENTORY_FRAMEWORK.md`.

The universal economic mutation pipeline, validation and execution contract, audit history, persistence, and replay philosophy are documented in `docs/TRANSACTION_FRAMEWORK.md`.

The immutable Order and Contract intent model, runtime lifecycles, transaction-linked fulfillment rules, and persistence are documented in `docs/ORDERS_AND_CONTRACTS.md`.

The deterministic simulation Work definition, lifecycle, ordering, budgets, same-tick rules, strict clock policy, persistence, and extension boundaries are documented in `docs/SIMULATION_SCHEDULER.md`.

The industry-neutral Production Process, Plan, and Run schemas, scheduler integration, transaction-backed completion, persistence, and extension boundaries are documented in `docs/PRODUCTION_FRAMEWORK.md`.

The deterministic Observation-to-Approved-Plan pipeline, ownership rules, exact allocation policy, Production submission adapter, six-file persistence contract, and deferred scope are documented in `docs/ECONOMIC_PLANNING_ENGINE.md`.

The pure Java architecture manifest, deterministic rule registry, validation categories, structured reports, and extension constraints are documented in `docs/ARCHITECTURE_VALIDATION_FRAMEWORK.md`.

The RFC-0022 M22A immutable Resource Allocation vocabulary, ownership
boundaries, exact quantities, structural validation, and deterministic ordering
are documented in `docs/RESOURCE_ALLOCATION_DOMAIN.md`. M22B lifecycle,
registries, immutable queries, history, report structures, and deferred
integration scope are documented in `docs/ALLOCATION_RUNTIME.md`.

The flagship Meat Processing implementation is documented in `PROJECT_VISION.md`, `GAMEPLAY_DESIGN.md`, and the focused documents under `docs/`, including the engine, product, transformation, packaging, workstation, Grinder, and Bandsaw references. `MODULE_PLAN.md` preserves the earlier meat-focused expansion plan as historical context.

Development environment verified on VS Code.
