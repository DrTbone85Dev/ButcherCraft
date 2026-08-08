# ButcherCraft

ButcherCraft is a Minecraft 1.21.1 NeoForge project building a deterministic regional world simulation platform. Industry modules participate in one shared simulation; Meat Processing is the flagship implementation and retains the existing product, workstation, packaging, and facility-management direction.

Registered content includes the Cutting Table, Grinder, Patty Former, Bandsaw, Packaging Table, retail-product proof, Packaging Supplies, six promoted Grinder recipes for Beef, Pork, Chicken, Buffalo, Lamb, and Venison trim to matching ground products, and the Beef Patties production chain from Beef Trim to Ground Beef to Beef Patties. The player-facing Production Order item guides and observes the manual multi-workstation chain.

The v0.10.4 Material Handling Update introduces the Cutting Table foundation and its first player-operated fabrication recipe: `Beef Short Loin -> T-Bone Steak + Beef Trim`. The Cutting Table keeps separate input, primary-output, and trim-output slots so the T-Bone Steak and Beef Trim remain independently visible and owned by the workstation.

Employees can now physically move product through the plant. A player can fabricate the first Cutting Table recipe, explicitly assign an employee to collect one Beef Trim from that Cutting Table, watch the employee visibly carry the actual item across the processing floor, and load it into a selected Grinder. The existing `/butchercraft employee operate <employee>` command then processes it through the same deterministic Grinder, Execution, and Scheduler pipeline used by the player.

Under the hood, the Material Handling Runtime owns exact in-transit `ItemStack` custody while Workstation owns durable endpoint instance identity, prepare/effect/result publication, source and destination reservations, and inventory effects. Transfer recovery and cancellation preserve exact-stack custody, and the DG-003 additive Execution-handler compatibility policy allows new handlers to be registered without invalidating compatible existing saves. These foundations preserve subsystem ownership rather than introducing a second inventory or execution path.

This alpha remains deliberately bounded. Employees do not transport Ground Beef to the Patty Former, operate the Patty Former, select workstations automatically, claim Production Orders, run Production-driven or autonomous production chains, participate in general Logistics, or own an inventory.

The platform foundation also includes immutable regional identity, manufacturers, properties, businesses, families, ownership, historical supply networks, runtime player identity, a simulation clock and event framework, mutable business operations, workforce definitions, economic Goods and Actors, actor-owned Inventory and Storage, a universal Transaction Framework, Orders and Contracts, the deterministic simulation Work pipeline, an industry-neutral Production Framework, the Economic Planning Engine, the generic Execution runtime, and the RFC-0022 Resource Allocation domain, runtime, deterministic Cycle, and provider observation framework. The scheduler includes internal Production and Planning handlers; Allocation has no live provider or Scheduler handler. General worker automation, pricing, logistics, markets, accounting, and additional employee-operated production remain future work.

## Project Identity

- Project name: ButcherCraft
- Mod ID: `butchercraft`
- Java package: `com.butchercraft`
- Asset namespace: `butchercraft`
- Minecraft: `1.21.1`
- NeoForge: `21.1.235`
- Java: `21`
- Version: `0.10.4-alpha.1`

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

The diagnostic also reports whether the species, processing-profile, product, and processing-operation datapack registries are available, whether the built-in Beef, Pork, Chicken, Buffalo, Lamb, and Venison definitions resolve, whether the initial graph validates, and whether each promoted trim-to-ground edge exists.

The diagnostic also reports whether the Development Processing Workstation block, the Grinder, the Bandsaw, their block entities, menus, capabilities, resolver paths, duration conversion, prototype context, graph checks, and temporary output mappings are available.

## Development And Operator Testing Commands

The current employee and plant controls are development/testing surfaces, not final gameplay UI. Employee references accept `#1`, a unique display name, a quoted display name such as `"Casey 1"`, or a canonical Employee ID. Workstation and transfer coordinates are explicit integer coordinates in the executing source's current dimension.

Current development commands:

```text
/butchercraft employee create [name]
/butchercraft employee status <employee>
/butchercraft employee assign-department <employee> <department>
/butchercraft employee assign-workstation <employee> <x> <y> <z>
/butchercraft department status <department>
/butchercraft business status
/butchercraft time status
```

The operation, transfer, cancellation, and anchor mutation commands require operator permission level 2:

```text
/butchercraft employee operate <employee>
/butchercraft employee transfer <employee> <source-x> <source-y> <source-z> <destination-x> <destination-y> <destination-z>
/butchercraft employee transfer-status <employee>
/butchercraft employee transfer-cancel <employee>
/butchercraft department set-anchor <department>
/butchercraft department set-anchor <department> <x> <y> <z>
```

The world-time diagnostic is `/butchercraft time status`; there is no separate `world-time` command literal.

## Alpha Limitations

- The Cutting Table has one fabrication recipe.
- Employee Material Handling moves exactly one Beef Trim at a time.
- The transfer command requires explicit source and destination coordinates.
- Ground Beef transport is not implemented.
- Patty Former transport and employee operation are not implemented.
- Production does not assign transfers.
- Employees do not select workstations automatically.
- General Logistics and autonomous production are not implemented.
- Employees do not own inventory; the visible held item derives from proven Material Handling custody.

## Development Item

`butchercraft:development_test_item` is a harmless development-only item. It appears in the ButcherCraft creative tab, has generated English display text, and uses a placeholder texture. It has no gameplay powers or world-changing behavior.

The trim, ground, Beef Patties, forequarter, and beef fabrication products are development-stage product data fixtures. Promoted Grinder and Patty Former products appear in the ButcherCraft creative tab with dedicated placeholder product textures; broader fabrication fixtures still use shared development presentation until separately promoted. They are not food, commerce products, or final balance.

`butchercraft:development_processing_workstation` is a development-only workstation fixture. It opens a plain temporary menu and client screen, accepts the current mapped trim products, resolves the single compatible grinding operation, processes for 60 ticks, and outputs the matching ground product through an explicit temporary mapping.

`butchercraft:grinder` is the current Grinder proof block. It uses `butchercraft:grinding` and the same processing graph/resolver/controller path to process Beef, Pork, Chicken, Buffalo, Lamb, and Venison Trim products without species-specific Grinder behavior. Buffalo presentation retains the existing `butchercraft:bison_*` registry identities for compatibility.

`butchercraft:patty_former` is the current Patty Former proof block. It uses `butchercraft:patty_forming` and the same Workstation, Execution, Scheduler, and owner-result path to process Ground Beef into Beef Patties. Ground Beef transfer from the Grinder to the Patty Former is manual.

`butchercraft:production_order` is the current narrow player-facing control item for the fixed Beef Patties chain. It creates or inspects one Beef Trim to Grinder to Ground Beef to Patty Former to Beef Patties Production Run, assigns the two workstations through server-validated block interaction, and displays manual-transfer guidance without moving items automatically.

`butchercraft:bandsaw` is the current Bandsaw proof block. It uses `butchercraft:bandsaw`, the same processing graph/resolver/controller path, and the atomic transformation execution bridge to process Beef Forequarter, Beef Hindquarter, and selected beef primal test products into ordered beef fabrication outputs, including Packer Brisket, T-Bone Steak, Porterhouse Steak, Top Round, Sirloin Steak, and Tri-Tip, without product-specific Bandsaw behavior.

`butchercraft:packaging_table` is the v0.8.0 Packaging Table foundation block. It appears in the ButcherCraft creative tab, can be placed, opens a placeholder inventory GUI with Meat, Tray, Wrap, and Result slots, persists inventory, and exposes item-handler inventory capability. Sprint 2 adds datapack-backed retail product definitions and a `package_retail` graph operation. Sprint C adds Foam Tray, Plastic Wrap Roll, Vacuum Bag, Butcher Paper Roll, Freezer Paper Roll, and Retail Label Roll supply items, plus data-only supply references on packaging definitions. The table does not package products, consume supplies, or execute operations yet.

## Documentation

Planning and architecture documents live at the repository root. Start with `CONSTITUTION.md`, the project's highest-level architectural authority, then read `VISION.md`, `CORE_PRINCIPLES.md`, `PROJECT_RULES.md`, `MODULES.md`, `SIMULATION_MODEL.md`, `ROADMAP.md`, and `TECHNICAL_ARCHITECTURE.md`. Accepted decisions in `DECISIONS.md` record how specific choices conform to that hierarchy. Future economic concepts are bounded in `ECONOMY_MODEL.md`, compatibility direction is recorded in `COMPATIBILITY.md`, and the non-stable extension map is in `docs/API_OVERVIEW.md`.

Experienced contributors can use the
[BCSE Architecture Guide](docs/BCSE_ARCHITECTURE_GUIDE.md) as the primary
orientation to kernel ownership, layering, deterministic data flow, replay,
validation, and extension boundaries before reading individual RFCs.

The immutable economic goods language and its separation from processing products and ItemStacks are documented in `docs/GOODS_FRAMEWORK.md`.

The industry-neutral participant model, actor capabilities, Good relationships, runtime boundary, and definition persistence are documented in `docs/ECONOMIC_ACTORS.md`.

The actor-owned runtime quantity model, storage hierarchy, capacity rules, and separation from Minecraft inventories are documented in `docs/INVENTORY_FRAMEWORK.md`.

The universal economic mutation pipeline, validation and execution contract, audit history, persistence, and replay philosophy are documented in `docs/TRANSACTION_FRAMEWORK.md`.

The ratified Material Handling custody protocol and its implemented explicit
employee transfer boundary are documented in `docs/MATERIAL_HANDLING.md`. The
future fabrication workstation and its current output-source boundary are
documented in `docs/CUTTING_TABLE.md`.

The immutable Order and Contract intent model, runtime lifecycles, transaction-linked fulfillment rules, and persistence are documented in `docs/ORDERS_AND_CONTRACTS.md`.

The deterministic simulation Work definition, lifecycle, ordering, budgets, same-tick rules, strict clock policy, persistence, and extension boundaries are documented in `docs/SIMULATION_SCHEDULER.md`.

The industry-neutral Production Process, Plan, and Run schemas, scheduler integration, transaction-backed completion, persistence, and extension boundaries are documented in `docs/PRODUCTION_FRAMEWORK.md`.

The player-facing Production Order item, fixed Beef Patties chain, workstation assignment, read-only progress presentation, manual-transfer guidance, failure mapping, and remaining gates are documented in `docs/PRODUCTION_ORDER.md`.

The deterministic Observation-to-Approved-Plan pipeline, ownership rules, exact allocation policy, Production submission adapter, six-file persistence contract, and deferred scope are documented in `docs/ECONOMIC_PLANNING_ENGINE.md`.

The pure Java architecture manifest, deterministic rule registry, validation categories, structured reports, and extension constraints are documented in `docs/ARCHITECTURE_VALIDATION_FRAMEWORK.md`.

The RFC-0022 M22A immutable Resource Allocation vocabulary, ownership
boundaries, exact quantities, structural validation, and deterministic ordering
are documented in `docs/RESOURCE_ALLOCATION_DOMAIN.md`. M22B lifecycle,
registries, immutable queries, history, report structures, and deferred
integration scope are documented in `docs/ALLOCATION_RUNTIME.md`. M22C explicit
cycle input, detached Capacity accounting, deterministic first fit, atomic Set
evaluation and publication, reports, traces, and replay evidence are documented
in `docs/ALLOCATION_CYCLE.md`.

M22D provider identity, explicit registry, immutable observation requests and
results, failure isolation, canonical aggregation, bundle usability, and replay
digests are documented in `docs/ALLOCATION_PROVIDER_FRAMEWORK.md`.

RFC-0023 Draft 1 proposes the complete domain, runtime, pipeline, adapter,
Transaction-observation, evidence, replay, and verification architecture for a
future Deterministic Execution Engine in
`docs/RFC-0023_DETERMINISTIC_EXECUTION_ENGINE.md`. The completed draft is not
accepted architecture and authorizes no implementation until architectural
review and explicit owner approval.

The flagship Meat Processing implementation is documented in `PROJECT_VISION.md`, `GAMEPLAY_DESIGN.md`, and the focused documents under `docs/`, including the engine, product, transformation, packaging, workstation, Grinder, Patty Former, and Bandsaw references. `MODULE_PLAN.md` preserves the earlier meat-focused expansion plan as historical context.

Development environment verified on VS Code.
