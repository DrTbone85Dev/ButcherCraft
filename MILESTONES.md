# ButcherCraft Milestones

Status: proposed planning document

Each milestone should remain small, testable, and rollback-friendly. Do not claim verification unless the command or manual test was actually run.

## Milestone 0: Repository Foundation

Goal: create a clean NeoForge 1.21.1 Java 21 project foundation without gameplay systems.

Included work:

- Initialize NeoForge MDK-style project.
- Set mod id, package, display name, license, and Gradle wrapper.
- Add empty registry classes and mod entry point only if needed by the MDK.
- Add central registration for one harmless development test item and the ButcherCraft creative tab.
- Add safe diagnostic command for foundation verification.
- Add common config foundation, logging, placeholder asset, language entry, and data-generation setup.
- Add documentation files from this planning pass.
- Add placeholder asset policy and data-generation skeleton only if the project template expects it.

Excluded work:

- Gameplay blocks, items, entities, employees, orders, inspections, and machines.
- Custom data persistence beyond template setup.

Dependencies:

- Owner approval for mod id and package.
- NeoForge 1.21.1 MDK selection.

Acceptance criteria:

- Project imports in an IDE.
- Mod starts in a development client with no gameplay content beyond template-safe basics.
- Dedicated server start does not load client-only classes.
- The development test item is registered under `butchercraft` and has no gameplay powers.
- The diagnostic reports project identity and registration state without modifying the world.

Automated verification:

- `gradlew build`
- `gradlew compileJava`
- `gradlew test`, if tests are configured
- `gradlew runData`, if data generation skeleton exists

Manual in-game verification:

- Launch development client.
- Confirm the mod appears with the correct name and no startup errors.

Rollback considerations:

- Revert only template and metadata files from this milestone.
- Keep planning documents unless owner requests replacement.

## Milestone 1A: Product Component and Manual Station Prototype

Goal: prove one product item, its persistent components, and one manual processing station without employees, orders, refrigeration, or MCDA.

Included work:

- One basic meat product.
- Product, quality, freshness, temperature, and packaging component shapes.
- One manual processing station.
- Placeholder assets where final art is unavailable.
- Save/load verification for product item state.

Excluded work:

- Grinder.
- Packaging station.
- Refrigerated storage.
- Customer orders.
- Employees.
- Cleanliness simulation beyond a local placeholder field.
- MCDA inspections.

Dependencies:

- Milestone 0.
- Approved first product choice.

Acceptance criteria:

- Player can obtain and process one basic product through one manual station.
- Product item data persists across save/load.
- Different product states do not merge unless component rules allow it.

Automated verification:

- `gradlew build`
- `gradlew test`, if configured
- Product component persistence tests if test infrastructure is available

Manual in-game verification:

- Start a new world.
- Obtain the product and manual station.
- Process the product.
- Save, quit, reload, and confirm item data remains.

Rollback considerations:

- Component codecs must be kept or migrated after public saves.

## Milestone 1B: ButcherCraft Engine Foundation

Goal: create a Minecraft-independent processing engine and project rules before adding visible gameplay systems.

Included work:

- `PROJECT_RULES.md` with non-negotiable development rules.
- Pure Java engine package under `com.butchercraft.engine`.
- Product, product quality, product quantity, modifier system, operation result, and processing transaction concepts.
- Unit tests for engine invariants, arithmetic, modifier ordering, transaction state transitions, and dependency boundaries.
- Engine documentation in `docs/BUTCHERCRAFT_ENGINE.md`.

Excluded work:

- Minecraft items, blocks, block entities, menus, screens, networking, or data components.
- Employees, machines, grinder, packaging station, refrigeration, cleanliness, MCDA, customers, orders, business accounts, and expansion APIs.
- Datapack recipes, registries, artwork, sounds, or in-game commands.

Dependencies:

- Milestone 0.
- Approved project identity.

Acceptance criteria:

- The engine has no `net.minecraft` or `net.neoforged` imports.
- Product quantity uses exact arithmetic and prevents negative, underflow, overflow, and incompatible-unit behavior.
- Product quality uses a documented 0-1000 score and five deterministic grades.
- Modifiers apply in deterministic, inspectable order.
- Operation results are explicit success/failure values rather than booleans or null.
- Processing transactions validate, prepare, commit once, cancel before commit, reject invalid input, and preserve input on failure.
- No excluded gameplay system is introduced.

Automated verification:

- `gradlew compileJava`
- `gradlew test`
- `gradlew build`
- External owner verification is required if the Codex sandbox blocks NeoForge artifact extraction before compilation.

Manual in-game verification:

- None. This milestone is domain-only and should not create visible gameplay.

Rollback considerations:

- Engine code is isolated under `com.butchercraft.engine`.
- The previous grinder and packaging station work formerly listed as Milestone 1B is deferred until the owner schedules the next visible station milestone.

## Milestone 1C: ButcherCraft Processing Framework

Goal: extend the Minecraft-independent engine with operation definitions, processing contexts, validation rules, and deterministic proposal evaluation before adding visible gameplay systems.

Included work:

- Immutable `ProcessingOperation` transformation definitions.
- Immutable `ProcessingContext` inputs for cleanliness, operator skill, equipment condition, and explicit modifiers.
- `ValidationRule` contract and reusable validation rules.
- Deterministic `ProcessingEvaluator`.
- Exact yield modifiers using additive basis points and documented half-up rounding.
- Beef Trim to Ground Beef test fixture only.
- Transaction integration tests proving prepare, commit, cancel, rejection, and failure behavior.
- Framework documentation in `docs/PROCESSING_FRAMEWORK.md`.

Excluded work:

- Minecraft items, item data components, blocks, block entities, menus, screens, networking, recipe JSON, datapack loading, grinder block, grinder GUI, machine power, employees, villager AI, player skill, refrigeration, product temperature, freshness, facility cleanliness simulation, cleaning tools, MCDA, customers, business finances, sounds, artwork, animation, and public expansion API commitments.

Dependencies:

- Milestone 1B.

Acceptance criteria:

- `ProcessingOperation`, `ProcessingContext`, and `ValidationRule` exist and are immutable or side-effect free as appropriate.
- Validation returns inspectable accept, reject, and warning results.
- Validation ordering is deterministic and stops at the first rejection.
- Yield uses exact arithmetic, additive basis-point modifiers, overflow protection, and documented rounding.
- Evaluation prepares proposed output without committing.
- Existing transaction guarantees remain intact.
- Beef Trim to Ground Beef is proven through tests as a fixture only.
- Engine packages have no `net.minecraft` or `net.neoforged` imports.
- No excluded gameplay feature is introduced.

Automated verification:

- `gradlew compileJava`
- `gradlew test`
- `gradlew build`
- External owner verification is required if the Codex sandbox blocks NeoForge artifact extraction before compilation.

Manual in-game verification:

- None. This milestone is domain-only and should not create visible gameplay.

Rollback considerations:

- Framework code is isolated under `com.butchercraft.engine`.
- The previous simple refrigerated storage work formerly listed as Milestone 1C is deferred until the owner schedules the next visible storage milestone.

## Milestone 1D: Minecraft Product Data Integration

Goal: prove that Minecraft ItemStacks can safely store, retrieve, copy, serialize, display, and round-trip a ButcherCraft engine product snapshot through a NeoForge data component.

Included work:

- Immutable `ProductStackData` component value.
- Registered `butchercraft:product_data` item data component.
- Engine `Product` to `ProductStackData` conversion.
- `ProductStackData` to engine `Product` conversion.
- Focused ItemStack adapter with explicit success and failure results.
- Development-only Beef Trim Test Product and Ground Beef Test Product fixtures.
- Creative tab fixture stacks with default product data.
- Product tooltip display for fixture stacks.
- Diagnostic checks for component registration, fixture registration, and product round-trip preservation.
- Codec, StreamCodec, adapter, ItemStack copy, merge-safety, dependency-boundary, and asset tests.
- Documentation in `docs/PRODUCT_DATA_INTEGRATION.md`.

Excluded work:

- Grinder block or behavior.
- Processing station.
- Blocks, block entities, menus, screens, recipes, or datapack processing operations.
- Employees, villagers, refrigeration, temperature, freshness, packaging, cleanliness, MCDA, customers, business systems, sounds, final artwork, or public expansion APIs.

Dependencies:

- Milestone 1B.
- Milestone 1C.
- Existing NeoForge foundation registration and data-generation setup.

Acceptance criteria:

- `ProductStackData` is immutable, validated, and rejects malformed decoded data.
- `butchercraft:product_data` is registered with persistent and network codecs.
- Engine product round-trips through the ItemStack component representation.
- Quantity and quality are preserved exactly.
- Product-bearing fixture stacks have maximum stack size `1`.
- Creative-tab fixture stacks receive valid independent product data.
- Tooltips display product identifier, source, state, quantity, grade, and advanced quality score.
- The diagnostic reports successful registration and round-trip checks without mutating world or inventory.
- Engine packages still have no Minecraft or NeoForge imports.
- No excluded gameplay system is introduced.

Automated verification:

- `gradlew compileJava`
- `gradlew test`
- `gradlew runData`
- `gradlew build`
- External owner verification is required if the Codex sandbox blocks NeoForge artifact extraction before compilation.

Manual in-game verification:

- Launch development client.
- Confirm both product test items appear in the ButcherCraft creative tab.
- Confirm both fixture items show product tooltip data.
- Copy fixture stacks and confirm product data remains visible.
- Run `/butchercraft diagnostic` and confirm product data registration and round-trip checks are true.
- Confirm no missing texture appears beyond the documented reused placeholder texture.
- Launch a dedicated server and confirm no client-only class-loading error is introduced.

Rollback considerations:

- Component field names should be treated as save-relevant once public saves exist.
- Product stackability remains locked to one until merge rules are explicitly designed.
- Customer order and business-summary work formerly listed here is deferred until packaged product output and order persistence are scheduled.

## Milestone 1E: Basic Cleanliness and MCDA Write-Up

Goal: prove basic cleanliness feedback and a simple MCDA write-up without full escalation.

Included work:

- Basic continuous cleanliness.
- A basic MCDA inspection that can issue a warning or write-up.
- Cleanliness and inspection persistence ownership.

Excluded work:

- Fines.
- Shutdowns.
- Reinspections.
- Complex inspection schedules.
- Employee cleaning jobs.

Dependencies:

- Milestone 1A.
- Approved default config strictness.

Acceptance criteria:

- Cleanliness affects product result or inspection result in a visible way.
- MCDA can issue at least a warning or write-up based on a basic violation.
- Inspection state persists and is not silently discarded.

Automated verification:

- `gradlew build`
- Cleanliness aggregation and inspection write-up tests if available

Manual in-game verification:

- Create a cleanliness problem and trigger a basic MCDA inspection.
- Correct the issue enough for the write-up path to be understandable.

Rollback considerations:

- MCDA write-ups can remain advisory until escalation is separately implemented.

## Milestone 1F: One Basic Employee Job

Goal: prove one villager-based employee can complete one already-working task through the work-order path.

Included work:

- One basic employee job.
- Employee attachment for role and skill seed data.
- Work-order reservation token for the supported job.

Excluded work:

- Multiple employee roles.
- Wages, morale, schedules, or payroll.
- Advanced pathfinding management.
- Employee-driven MCDA or refrigeration behavior.

Dependencies:

- Milestone 1B.
- Milestone 1C.
- Future station-chain milestone for a useful employee task.
- Work-order reservation boundary from the technical architecture.

Acceptance criteria:

- One employee can complete one useful job through the work-order path.
- Employee skill/assignment data persists across unload/reload.
- Reservation release or restore works after station unload, failure, cancellation, or save/load.

Automated verification:

- `gradlew build`
- Employee attachment and work-order reservation tests if available

Manual in-game verification:

- Assign the basic employee job and observe completion.
- Reload the world and confirm assignment/skill state remains understandable.

Rollback considerations:

- Employee job behavior should be gated so the manual path remains playable if AI is unstable.

## Milestone 2A: Data-Driven Product and Processing Definitions

Goal: create server-authoritative datapack definitions and a read-only processing graph before adding visible processing stations.

Included work:

- Custom datapack registries for species, processing profiles, products, and processing operations.
- Immutable codec-backed definitions and generated built-in data for beef, red meat, beef trim, ground beef, and grind beef.
- Resolver validation for missing references, species mismatch, profile incompatibility, state mismatch, self-loops, and forbidden zero-output operations.
- Conversion of resolved operation definitions into the existing Minecraft-independent `ProcessingOperation`.
- Read-only processing graph lookup and validation report.
- ProductStackData-to-definition validation bridge.
- Diagnostic command checks for registries, built-in definitions, graph validation, and the Beef Trim to Ground Beef edge.
- Documentation in `docs/PRODUCT_AND_PROCESSING_DEFINITIONS.md`.

Excluded work:

- Machines, blocks, block entities, menus, screens, player-triggered processing, employees, refrigeration, cleanliness, MCDA, customers, business systems, poultry content, final assets, sounds, animations, and public stable expansion API guarantees.

Acceptance criteria:

- Species, processing profiles, products, and operations are loaded through custom datapack registries.
- Built-in beef definitions are generated deterministically.
- The processing graph contains `butchercraft:beef_trim -> butchercraft:ground_beef` through `butchercraft:grind_beef`.
- Invalid references and incompatible profiles are reported explicitly.
- Engine packages remain Minecraft-independent.
- Tests prove a future poultry processing profile is possible without hardcoded species workflow checks.

Automated verification:

- `gradlew clean`
- `gradlew compileJava`
- `gradlew compileTestJava`
- `gradlew test`
- `gradlew runData`
- `gradlew build`

Manual in-game verification:

- Launch development client or server.
- Run `/butchercraft diagnostic`.
- Confirm all four definition registries are available.
- Confirm beef, red meat, beef trim, ground beef, and grind beef resolve.
- Confirm the initial graph validates and the Beef Trim to Ground Beef edge is reported.
- Confirm existing product test item tooltips still display valid product data.

Rollback considerations:

- Registry ids and JSON field names become save/datapack-relevant once worlds or external packs depend on them.
- The beef prototype values are test balance only and can be revised before public content stabilization.

## Milestone 2B: Processing Workstation Framework

Goal: create a reusable server-authoritative workstation framework and one development-only workstation block that proves Beef Trim Test Product can be processed into Ground Beef Test Product through the existing definition graph and engine transaction.

Included work:

- Workstation capability, state, and failure models.
- One-input, one-output `WorkstationInventory`.
- `WorkstationOperationResolver` that consumes loaded definitions, `ProcessingGraph`, and `ProductStackData`.
- `WorkstationProcessingController` that reserves input, tracks ticks, commits exactly once, and creates output through the existing engine transaction.
- Development Processing Workstation block, block entity, block item, temporary menu and client screen, item-handler capability, placeholder resources, loot table, and language entries.
- Diagnostic checks for workstation registration, resolver behavior, duration conversion, prototype context validation, and temporary output mapping.
- Tests for state transitions, duration conversion, resolver behavior, controller lifecycle, inventory rules, registration/assets, and dependency boundaries.
- Documentation in `docs/WORKSTATION_FRAMEWORK.md`.

Excluded work:

- Final grinder block or model.
- Multiple machine types.
- Player recipe-selection GUI.
- Power, fuel, employees, refrigeration, temperature, freshness, cleanliness gameplay, maintenance gameplay, MCDA, customers, commerce, sounds, animations, complex rendering, poultry content, or public expansion API guarantees.

Acceptance criteria:

- Generic workstation code does not hardcode beef, grind beef, or poultry species switches.
- Automatic operation selection occurs only when exactly one compatible operation exists.
- `3000` milliseconds converts to `60` ticks.
- Active input cannot be extracted while processing.
- Output insertion is blocked.
- Completion consumes input and creates output once.
- Output obstruction blocks completion without deleting input.
- Block removal drops recoverable input and completed output.
- Save/load state stores inventory, selected operation, progress, failure code, reserved input snapshot, and completion flag.
- Engine packages remain Minecraft-independent.

Automated verification:

- `gradlew clean`
- `gradlew compileJava`
- `gradlew compileTestJava`
- `gradlew test`
- `gradlew runData`
- `gradlew build`
- External owner verification is required if the Codex sandbox blocks NeoForge artifact creation before source compilation.

Manual in-game verification:

- Launch development client.
- Confirm Development Processing Workstation appears in the ButcherCraft creative tab.
- Place the workstation and open the temporary menu.
- Insert Beef Trim Test Product.
- Confirm processing starts and completes after about three seconds.
- Confirm Ground Beef Test Product appears in output with `900 gram` and adjusted quality.
- Confirm Ground Beef Test Product and vanilla items are rejected as processing inputs.
- Confirm output obstruction, block break, and save/reload preserve product safely.
- Run `/butchercraft diagnostic` and confirm workstation checks report true.
- Launch a dedicated server and confirm no client-only class-loading error.

Rollback considerations:

- The development block and explicit product-item mapping are temporary fixture content.
- The state and persistence fields are save-relevant once public worlds use the block.
- Future final machine content should consume the framework rather than duplicating transaction logic.

## Milestone 2D: Data-Driven Multi-Species Grinding

Goal: prove the Grinder is generic by adding pork and bison red-meat grinding flows through definitions and fixture data without changing Grinder behavior.

Included work:

- Built-in species definitions for `butchercraft:pork` and `butchercraft:bison`, both using `butchercraft:red_meat`.
- Product definitions for pork trim, ground pork, bison trim, and ground bison.
- Processing-operation definitions for `butchercraft:grind_pork` and `butchercraft:grind_bison`, both declaring `butchercraft:grinding`.
- Development-only pork and bison product fixture items with placeholder models and tooltip-ready product data.
- Diagnostic checks, tests, and docs proving beef, pork, and bison use the same graph/resolver/controller path.

Excluded work:

- Full species catalogs, animal entities, regulatory rules, final product items, final artwork, recipe-selection UI, employees, refrigeration, commerce, or new Grinder logic.

Acceptance criteria:

- Existing Beef Trim to Ground Beef grinding still works.
- Pork Trim resolves to Grind Pork and produces Ground Pork.
- Bison Trim resolves to Grind Bison and produces Ground Bison.
- Grinder classes and generic workstation framework contain no species-specific branches for beef, pork, bison, or operation ids.
- Product source ids can pass through the engine without adding a bison engine enum value.
- Generated definition JSON and language/model resources are deterministic.

Automated verification:

- `gradlew clean`
- `gradlew compileJava`
- `gradlew compileTestJava`
- `gradlew test`
- `gradlew runData`
- `gradlew build`

Manual in-game verification:

- Launch development client.
- Confirm Beef Trim, Pork Trim, Bison Trim, Ground Beef, Ground Pork, and Ground Bison test products appear in the ButcherCraft creative tab.
- Insert Beef Trim, Pork Trim, and Bison Trim test products into the Grinder and confirm each produces the matching ground test product after about three seconds.
- Run `/butchercraft diagnostic` and confirm the multi-species definition, graph, Grinder resolution, and output-mapping checks report true.
- Launch a dedicated server and confirm no client-only class-loading error.

Rollback considerations:

- Pork and bison definitions are prototype red-meat proof data and can be removed before public content stabilization if needed.
- Product fixture item ids and generated definition ids become datapack/save relevant once public worlds depend on them.

## Milestone 2E: Industrial Bandsaw and Multi-Output Fabrication

Goal: prove processing operations can create an ordered collection of outputs by adding a permanent Bandsaw machine and a data-driven Beef Forequarter fabrication flow.

Included work:

- Engine, result, transaction, definition, resolver, and processing graph support for immutable ordered output collections.
- Backward-compatible single-output operations represented as one-element output lists so Grinder behavior remains unchanged.
- Built-in product definition for `butchercraft:beef_forequarter` plus beef chuck, rib, brisket, plate, shank, fat, and bone output definitions.
- Built-in processing-operation definition for `butchercraft:break_beef_forequarter`, declaring `butchercraft:bandsaw`.
- Deterministic integer largest-remainder allocation for multi-output yields, with ties resolved by output order.
- Permanent two-block-tall Bandsaw block, upper forwarding block, block entity, menu, screen, registration, placeholder assets, loot, language, diagnostics, and tests.
- Development-only fixture items and output mapping for the new beef fabrication products.

Excluded work:

- Animal entities, carcass entities, full cut catalogs, final fabrication balance, recipe-selection UI, power, sounds, animations, cleanliness gameplay, employees, refrigeration, commerce, regulatory rules, final artwork, or public expansion API guarantees.

Acceptance criteria:

- Existing Beef, Pork, and Bison Trim to matching ground-product Grinder flows still work through the same definitions and resolver path.
- `butchercraft:beef_forequarter` resolves to `butchercraft:break_beef_forequarter` only on the `butchercraft:bandsaw` capability.
- A `100000 gram` Beef Forequarter produces ordered outputs: Chuck, Rib, Brisket, Plate, Shank, Trim, Fat, and Bone with 95% total yield.
- Bandsaw lower block owns the block entity, inventory, processing state, menu provider, persistence, ticking, and drops.
- Bandsaw upper block has no block entity, forwards interaction to the lower block, and paired break/removal drops recoverable inventory exactly once.
- Generic workstation and Grinder code contain no product, species, or operation-id branches for the Bandsaw fabrication flow.
- Generated definition JSON and placeholder assets are deterministic.

Automated verification:

- `gradlew clean`
- `gradlew compileJava`
- `gradlew compileTestJava`
- `gradlew test`
- `gradlew runData`
- `gradlew build`

Manual in-game verification:

- Launch development client.
- Confirm the Bandsaw and new beef fabrication test products appear in the ButcherCraft creative tab.
- Place the Bandsaw facing each horizontal direction and confirm the upper half is placed, synchronized, and removed with the lower half.
- Confirm placement fails when the upper space is obstructed.
- Insert Beef Forequarter Test Product and confirm the eight ordered outputs appear after processing.
- Break the upper and lower halves in separate checks and confirm recoverable input or completed outputs drop once.
- Run `/butchercraft diagnostic` and confirm the Bandsaw, graph, resolver, and output-mapping checks report true.
- Launch a dedicated server and confirm no client-only class-loading error.

Rollback considerations:

- The Bandsaw ids are permanent machine ids, while placeholder art and prototype beef fabrication balance can still change before public content stabilization.
- Product fixture item ids and generated definition ids become datapack/save relevant once public worlds depend on them.
- Multi-output operation schema should remain backward compatible with one-element output lists for existing Grinder definitions.

## Milestone 2: Persistence and Data Hardening

Goal: make the vertical slice reliable across saves, reloads, and basic content changes.

Included work:

- Versioned saved-data records for business, orders, facilities, and inspections.
- Product component migration plan.
- Missing product or expansion id handling.
- Data-generation coverage for all vertical-slice assets.
- Dedicated server smoke testing.

Excluded work:

- New production chains.
- Advanced commerce.
- Detailed refrigeration.

Dependencies:

- Milestone 1A through Milestone 1F.

Acceptance criteria:

- Existing vertical-slice saves reload cleanly after internal refactors.
- Missing or unknown product ids do not crash the world.
- Saved data is not silently discarded.
- Dedicated server launch path is clean.

Automated verification:

- `gradlew build`
- `gradlew runData`
- `gradlew test`, if configured
- Save/load GameTests or scripted manual fixture tests where practical

Manual in-game verification:

- Create a facility and order.
- Save, quit, reload, and complete the order.
- Break and replace stations.
- Confirm MCDA and cleanliness state remain understandable.

Rollback considerations:

- Keep old data readers until a release boundary permits migration removal.
- Avoid changing registry names after this milestone without explicit migration.

## Milestone 3: Facility and Work-Order Expansion

Goal: make small facilities feel manageable with more than one active task.

Included work:

- Facility controller or registration flow.
- Work-order board improvements.
- Employee reservation handling.
- Multiple station queues.
- Better cleanliness summaries.
- Basic facility-level inspection report.

Excluded work:

- Detailed finances.
- Full refrigeration room engineering.
- Large employee teams.

Dependencies:

- Milestone 2.

Acceptance criteria:

- Facility summary identifies stations, active orders, cleanliness state, and basic cold storage.
- Employees do not claim the same work at the same time.
- Player can prioritize or cancel work orders.
- Inspection report points to understandable facility issues.

Automated verification:

- `gradlew build`
- Work-order state transition tests.
- Facility summary tests.

Manual in-game verification:

- Build a facility with multiple stations.
- Queue multiple orders.
- Assign one employee and confirm work reservation behavior.
- Trigger an inspection and read the report.

Rollback considerations:

- Work-order UI can be disabled while manual station interactions remain usable.
- Facility controller data should rebuild from placed blocks where possible.

## Milestone 4: Scalable Refrigeration Prototype

Goal: prototype walk-in cooler/freezer architecture without committing to full balance.

Included work:

- Controller-based refrigerated room validation.
- Room volume and target temperature summary.
- Cooler versus freezer mode.
- Capacity demand calculation with TBD coefficients.
- Equipment overload warning state.
- Coarse simulation interval.

Excluded work:

- Final compressor/condenser/evaporator art and content breadth.
- Detailed failure model.
- Logistics or refrigerated transport.

Dependencies:

- Milestone 3.
- Owner approval that detailed refrigeration belongs in either ButcherCraft Core or ButcherCraft Refrigeration for the current release track.

Acceptance criteria:

- A valid room reports volume, target temperature, and capacity status.
- Larger rooms require more capacity than smaller rooms.
- Freezer mode requires more capacity than cooler mode.
- Overload can be observed without causing excessive tick cost.

Automated verification:

- `gradlew build`
- Room validation tests.
- Capacity summary tests.
- Performance smoke test for multiple rooms if test harness exists.

Manual in-game verification:

- Build valid and invalid rooms.
- Change room size and target mode.
- Observe product freshness behavior and overload feedback.

Rollback considerations:

- Keep simple refrigerated storage from Milestone 1 as fallback.
- Gate room prototype behind config if stability is uncertain.

## Milestone 5: Employee Skill and Cleanliness Depth

Goal: make employees and sanitation meaningfully affect quality and throughput.

Included work:

- Employee skill progression.
- Skill effects on speed, quality, yield, and errors.
- Cleaning work role.
- Better station and facility cleanliness UI.
- Configurable cleanliness strictness.

Excluded work:

- Complex schedules, wages, morale, or payroll.
- Large team management UI.

Dependencies:

- Milestone 3.

Acceptance criteria:

- Employee skill improves through work.
- Higher skill produces observable benefits.
- Cleaning work reduces facility risk.
- Poor cleanliness can reduce quality and increase MCDA findings.

Automated verification:

- `gradlew build`
- Skill progression tests.
- Cleanliness aggregation tests.
- Quality calculation tests with worker skill inputs.

Manual in-game verification:

- Train an employee over repeated tasks.
- Compare low-skill and higher-skill results.
- Let the facility get dirty, then clean it and observe inspection/quality change.

Rollback considerations:

- Skill modifiers should be data/config driven so balance can be softened without removing the feature.

## Milestone 6: MCDA Escalation and Reinspection

Goal: complete the required inspection consequence loop.

Included work:

- Warning/write-up/fine/shutdown/reinspection escalation.
- Clear inspection notices.
- Violation history.
- Configurable severity.
- Basic shutdown behavior that blocks or limits affected business activity.

Excluded work:

- Real-world legal detail.
- USDA branding.
- Complex appeal processes.

Dependencies:

- Milestone 2.
- Milestone 5 for cleanliness depth, if inspection severity depends on it.

Acceptance criteria:

- Repeated unresolved issues escalate beyond write-ups.
- Correcting issues allows reinspection.
- Shutdown state is understandable and reversible through gameplay.
- Forgiving config preset can soften penalties.

Automated verification:

- `gradlew build`
- Inspection escalation tests.
- Saved-data persistence tests for violation history and shutdown state.

Manual in-game verification:

- Trigger a minor issue and receive a write-up.
- Ignore it and confirm escalation.
- Correct the issue and pass reinspection.

Rollback considerations:

- Shutdown enforcement can be downgraded to warning-only through config if it blocks fun or creates save risk.

## Milestone 7: ButcherCraft Commerce Readiness

Goal: prepare the core for deeper retail and wholesale systems.

Included work:

- Stable order API.
- Customer archetype API.
- Product category tags.
- Reputation tiers.
- Basic finance summaries.
- Documentation for ButcherCraft Commerce hooks.

Excluded work:

- Full ButcherCraft Commerce implementation.
- Recurring contracts and logistics.

Dependencies:

- Milestone 2.
- Milestone 3.

Acceptance criteria:

- Expansion code can register new order templates without touching core internals.
- Orders can require product quality, freshness, packaging, and temperature.
- Business summaries can be displayed without exposing raw saved-data internals.

Automated verification:

- `gradlew build`
- API compatibility tests or compile-only sample expansion once available.

Manual in-game verification:

- Complete orders with different requirements.
- Confirm reputation changes are visible and persistent.

Rollback considerations:

- Keep API marked experimental until at least one expansion prototype uses it.

## Milestone 8: Balance, Config, and Release Candidate

Goal: stabilize the core mod for a first release.

Included work:

- Config presets.
- Tooltip and UI polish.
- Placeholder asset audit.
- Performance profiling.
- Dedicated server validation.
- Save migration review.
- Documentation review.

Excluded work:

- New major systems.
- Expansion content not already scoped for release.

Dependencies:

- All release-target milestones.

Acceptance criteria:

- Default preset is playable without severe surprise penalties.
- Realistic preset makes systems stricter without becoming opaque.
- No known data-loss bugs.
- No client-only classloading on dedicated server.
- All public APIs intended for expansion are documented.

Automated verification:

- `gradlew build`
- `gradlew runData`
- `gradlew test`, if configured
- Dedicated server smoke test

Manual in-game verification:

- Play through the vertical slice from a new world.
- Reload the world multiple times.
- Test forgiving and realistic config presets.
- Confirm placeholder assets are acceptable and non-graphic.

Rollback considerations:

- Freeze registry names before release candidate.
- Defer unstable optional systems rather than shipping save-risk behavior.
