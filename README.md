# ButcherCraft

ButcherCraft is a Minecraft 1.21.1 NeoForge mod planned as a meat-processing and business-management simulation. This repository currently contains the project foundation and early development frameworks.

Registered content is limited to development fixtures used to verify registration, assets, data generation, diagnostics, ItemStack product data integration, data-driven processing definitions, the temporary workstation framework, and the current Grinder and Bandsaw proofs. Milestones 1B and 1C add pure Java engine and processing-framework foundations only; Milestone 1D connects product snapshots to ItemStacks; Milestone 2A adds server datapack definitions and a processing graph; Milestone 2B adds a development-only Processing Workstation block; Milestone 2D proves the Grinder can process Beef, Pork, and Bison Trim to matching ground products through data-driven definitions; Milestone 2E proves ordered multi-output fabrication through the Bandsaw; version 0.6.0 begins the pure Java Material Transformation Engine foundation; version 0.6.1 connects the Grinder to capability-based transformation execution; version 0.6.2 adds the immutable transformation registry; version 0.6.3 formalizes the canonical transformation definition schema.

## Project Identity

- Project name: ButcherCraft
- Mod ID: `butchercraft`
- Java package: `com.butchercraft`
- Asset namespace: `butchercraft`
- Minecraft: `1.21.1`
- NeoForge: `21.1.235`
- Java: `21`
- Version: `0.6.3`

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

The info command displays the installed ButcherCraft version and current Early Development / Material Transformation Engine status for ordinary players without exposing development diagnostics.

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

`butchercraft:bandsaw` is the current Bandsaw proof block. It uses `butchercraft:bandsaw` and the same processing graph/resolver/controller path to process Beef Forequarter Test Product into eight ordered beef fabrication outputs, including Packer Brisket, without product-specific Bandsaw behavior.

## Documentation

Planning and architecture documents live at the repository root. Treat `PROJECT_IDENTITY.md`, `PROJECT_RULES.md`, and accepted decisions in `DECISIONS.md` as authoritative. The pure domain engine is documented in `docs/BUTCHERCRAFT_ENGINE.md`, the processing framework is documented in `docs/PROCESSING_FRAMEWORK.md`, the Material Transformation Engine is documented in `docs/MATERIAL_TRANSFORMATION_ENGINE.md`, the ItemStack product data bridge is documented in `docs/PRODUCT_DATA_INTEGRATION.md`, datapack-backed definitions are documented in `docs/PRODUCT_AND_PROCESSING_DEFINITIONS.md`, multi-output processing is documented in `docs/MULTI_OUTPUT_PROCESSING.md`, the workstation framework is documented in `docs/WORKSTATION_FRAMEWORK.md`, the Grinder proof is documented in `docs/GRINDER.md`, and the Bandsaw proof is documented in `docs/BANDSAW.md`.

Development environment verified on VS Code.
