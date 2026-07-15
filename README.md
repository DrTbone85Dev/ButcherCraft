# ButcherCraft

ButcherCraft is a Minecraft 1.21.1 NeoForge mod planned as a meat-processing and business-management simulation. This repository currently contains the project foundation and early development frameworks.

Registered content is limited to development fixtures used to verify registration, assets, data generation, diagnostics, ItemStack product data integration, data-driven processing definitions, and the temporary workstation framework. Milestones 1B and 1C add pure Java engine and processing-framework foundations only; Milestone 1D connects product snapshots to ItemStacks; Milestone 2A adds server datapack definitions and a processing graph; Milestone 2B adds a development-only Processing Workstation block that proves the server-authoritative Beef Trim to Ground Beef transaction. It is not the final grinder.

## Project Identity

- Project name: ButcherCraft
- Mod ID: `butchercraft`
- Java package: `com.butchercraft`
- Asset namespace: `butchercraft`
- Minecraft: `1.21.1`
- NeoForge: `21.1.235`
- Java: `21`
- Version: `0.1.0`

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

In a development world or server console with commands available, run:

```text
/butchercraft diagnostic
```

The diagnostic reports project name, mod id, mod version, Minecraft version, NeoForge version when available, whether common initialization completed, whether the development fixtures are registered, and whether product data can round-trip through the ItemStack component boundary. It does not grant items, modify the world, expose local paths, expose environment variables, or report sensitive system information.

The diagnostic also reports whether the species, processing-profile, product, and processing-operation datapack registries are available, whether the built-in beef definitions resolve, whether the initial graph validates, and whether the Beef Trim to Ground Beef edge exists.

The diagnostic also reports whether the Development Processing Workstation block, block entity, menu, capability, resolver path, duration conversion, prototype context, and temporary output mapping are available.

## Development Item

`butchercraft:development_test_item` is a harmless development-only item. It appears in the ButcherCraft creative tab, has generated English display text, and uses a placeholder texture. It has no gameplay powers or world-changing behavior.

`butchercraft:beef_trim_test` and `butchercraft:ground_beef_test` are development-only product data fixtures. They appear in the ButcherCraft creative tab with default `butchercraft:product_data`, max stack size `1`, English display text, product tooltips, and reused placeholder models/textures. They are not food, recipes, or processing gameplay.

`butchercraft:development_processing_workstation` is a development-only workstation fixture. It opens a plain temporary menu, accepts Beef Trim Test Product, resolves `butchercraft:grind_beef`, processes for 60 ticks, and outputs Ground Beef Test Product through an explicit temporary mapping.

## Documentation

Planning and architecture documents live at the repository root. Treat `PROJECT_IDENTITY.md`, `PROJECT_RULES.md`, and accepted decisions in `DECISIONS.md` as authoritative. The pure domain engine is documented in `docs/BUTCHERCRAFT_ENGINE.md`, the processing framework is documented in `docs/PROCESSING_FRAMEWORK.md`, the ItemStack product data bridge is documented in `docs/PRODUCT_DATA_INTEGRATION.md`, datapack-backed definitions are documented in `docs/PRODUCT_AND_PROCESSING_DEFINITIONS.md`, and the workstation framework is documented in `docs/WORKSTATION_FRAMEWORK.md`.
