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

## Milestone 1D: One Customer Order and Business Summary

Goal: prove one order can consume valid packaged product and update a minimal business summary.

Included work:

- One customer order.
- Minimal business ledger and reputation update.
- Idempotent order fulfillment transaction.

Excluded work:

- Wholesale contracts.
- Recurring orders.
- Customer simulation.
- Employees.
- MCDA escalation.

Dependencies:

- Milestone 1A.
- Milestone 1B.
- Milestone 1C.
- Future station-chain milestone for packaged product output.

Acceptance criteria:

- Player can complete one order with the packaged product.
- Repeated client requests cannot pay or complete the same order twice.
- Business/order state persists across save/load.

Automated verification:

- `gradlew build`
- Order completion tests if test infrastructure is available

Manual in-game verification:

- Complete the customer order.
- Save, quit, reload, and confirm business/order state remains.

Rollback considerations:

- Order API should remain experimental until at least one later content path uses it.

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
