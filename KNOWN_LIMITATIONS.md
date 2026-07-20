# ButcherCraft Known Limitations

Status: proposed planning document

This file records expected risks, limitations, and prototype areas. It should be updated whenever implementation proves or disproves an assumption.

## Current Repository Limitations

- The current workspace is being prepared with a NeoForge foundation, but substantive gameplay systems remain intentionally unimplemented.
- Milestone 1B adds only a Minecraft-independent engine foundation; it is not visible gameplay.
- Milestone 1C adds only a Minecraft-independent processing framework; it is not visible gameplay.
- Milestone 1D adds only Minecraft ItemStack product data integration and development fixture items; it is not player-facing processing gameplay.
- Milestone 2A adds datapack-backed species, processing-profile, product, and operation definitions plus graph validation. It still does not add player-triggered processing, machines, workstations, poultry content, or visible gameplay.
- Milestone 2B adds a development-only processing workstation framework and block. It proves one prototype Beef Trim to Ground Beef transaction, but it is not the final grinder, not final machine content, and not a full recipe-selection system.
- Milestone 2D adds pork and bison red-meat grinding prototype definitions and development fixture items to prove the Grinder remains data-driven. It does not add full species catalogs, animal entities, regulatory rules, final product items, or final balance.
- Milestone 2E adds a permanent Bandsaw machine and one prototype beef forequarter fabrication flow. It does not add animal entities, carcass entities, full cut catalogs, recipe-selection UI, power, employees, refrigeration, commerce, regulatory rules, or final balance.
- Version 0.6.0 begins the pure Java Material Transformation Engine foundation. Version 0.6.1 migrates only the Grinder to capability-based transformation execution. It does not migrate datapack registries, Bandsaw behavior, smoker behavior, packaging behavior, cooler behavior, menus, screens, or product data components.
- The packaging station scope formerly listed as Milestone 1B has been deferred and needs owner scheduling before order or employee milestones depend on packaged output.
- The simple refrigerated storage scope formerly listed as Milestone 1C has been deferred and needs owner scheduling before cold-storage gameplay depends on it.
- The customer order and business summary scope formerly listed as Milestone 1D has been deferred until product output, packaging, and persistence dependencies are scheduled.
- Client and dedicated-server launches may depend on local graphics and runtime support.
- In this Codex Windows sandbox, Java `Path.toRealPath()` is denied even for readable workspace files. This currently blocks NeoForge's `createMinecraftArtifacts` task during the external `installertools` server-jar extraction step before source compilation.
- License status, repository status, and artifact coordinates are not yet owner-approved.
- All architecture choices in the planning docs are proposed unless marked final by the brief.

## Version and API Limitations

- The target is Minecraft 1.21.1 and NeoForge for that version. Later NeoForge examples may use APIs that are not available or have different signatures.
- The official NeoForge 1.21.1 documentation is version-specific but no longer the latest documentation line.
- Exact helper method signatures should be confirmed against the pinned MDK before implementation.
- Avoid copying code from tutorials for other Minecraft versions.

## Foundation Limitations

- The development test item is only a harmless registration and diagnostics check.
- The engine foundation is pure Java domain code and does not prove item components, station inventories, menus, world persistence, or in-game processing behavior.
- The processing framework uses test fixture definitions such as Beef Trim to Ground Beef only to prove domain behavior. Those fixtures are not registered Minecraft content and are not final gameplay balance.
- The Beef, Pork, and Bison trim/ground test product items are development-only integration fixtures. They carry product data but provide no food, recipe, commerce, or world behavior outside the temporary workstation and Grinder processing proofs.
- The Beef Forequarter, Chuck, Rib, Packer Brisket, Plate, Shank, Fat, and Bone test product items are development-only integration fixtures for the Bandsaw proof. They are not final product catalogs or final economic balance.
- The built-in red-meat grinding definition dataset is prototype data used to prove registry loading, graph validation, and generic Grinder resolution. Its duration, yield, quality adjustment, and thresholds are not final balance.
- The built-in Beef Forequarter fabrication definition is prototype data used to prove ordered multi-output operation handling. Its cut names, ratios, duration, quality adjustment, and process loss are not final balance.
- Multi-output yield modifiers are intentionally unsupported until the project defines how an additive modifier should be distributed across multiple outputs.
- The transformation evaluator currently validates material availability and workstation capability only. The transformation executor returns pure declared outputs from an accepted matching evaluation. It does not perform inventory mutation, quality calculation, random output selection, ItemStack conversion, or transaction commits.
- The Development Processing Workstation is a temporary fixture block. Its explicit product-definition-to-item mapping supports only the registered development product fixture items.
- The shared workstation menu and client screens are plain development views over server-owned state and are not final UI.
- Manual test worlds created before the Milestone 2E slot-layout fix may contain stale workstation block entity data if a prior removal crashed mid-cleanup. Load once with the fixed code, or use a fresh development test world if the chunk remains corrupted.
- Manual test worlds created from the unreleased Milestone 2E branch before the canonical terminology pass may contain the retired prototype product id `butchercraft:beef_brisket`. No alias or migration is provided for this pre-release fixture id.
- Poultry is a deferred design case. Milestone 2A tests profile compatibility with hypothetical test definitions but does not add live poultry species, products, operations, regulations, or equipment.
- The diagnostic command is safe by design and must not grant items, alter the world, expose local paths, expose environment variables, or report sensitive system details.
- The project foundation should not be treated as proof that meat processing, employees, refrigeration, cleanliness, MCDA, orders, or business gameplay have been implemented.
- GameTest coverage may begin as a foundation check and should expand only when gameplay behavior exists.

## Performance Risks

- Facility-wide cleanliness scanning can become expensive if done every tick.
- Walk-in room validation can become expensive if full scans run too often.
- Refrigeration simulation can become expensive if every product stack updates every tick.
- Employee AI can become expensive if every worker scans every station and order each tick.
- Networking can become noisy if full facility summaries sync constantly.
- Large facilities crossing many chunks need careful load/unload behavior.

Mitigation direction:

- Use event-driven updates.
- Cache summaries.
- Simulate on coarse intervals.
- Cap scan sizes.
- Prefer lazy freshness/temperature updates.
- Add profiling before large-system release.

## Persistence Risks

- Product data components need a migration strategy before public saves. Milestone 1D field names should be treated as save-relevant from this point forward.
- Saved business, order, inspection, employee, cleanliness, and refrigeration data need versioning.
- Removing an expansion from a save can leave unknown product, order, or block ids.
- Placeholder systems must not silently discard saved data.
- Facility identity needs stable ids that survive block break/replacement behavior where appropriate.

Prototype needed:

- Unknown product id behavior.
- Missing expansion fallback behavior.
- Save/load test fixture for a basic facility.

## Multiblock and Refrigeration Risks

- Room validation across chunk boundaries can be tricky.
- Doors, shelves, equipment, and boundary blocks can invalidate room state frequently.
- Cooling-capacity formulas may be difficult to make both understandable and fun.
- Overload, wear, and failure can frustrate players if warnings are unclear.
- Freezer/cooler differences need readable UI.

Prototype needed:

- Minimum room controller.
- Room volume calculation.
- Capacity demand summary.
- Coarse thermal update.
- Clear overload warning.

## Employee AI Risks

- Villager pathfinding may not reliably handle dense facilities.
- Employees can block the player or one another.
- Work-order reservations can deadlock if a station becomes unavailable.
- Skill effects can become invisible or overpowered.
- Employee data needs to survive unload, death, reassignment, and business closure.

Prototype needed:

- One employee role with one station.
- Reservation release on failure.
- Save/load of employee assignment and skill.

## Cleanliness Risks

- Continuous cleanliness can be hard to explain if the UI is too numeric.
- Facility summaries can hide local problems if aggregation is too broad.
- Cleanliness decay can feel punitive if the player lacks tools to respond.
- MCDA penalties based on cleanliness must be clear and fair.

Prototype needed:

- Station-level dirty/clean event.
- Facility summary.
- Cleaning action.
- Inspection use of cleanliness.

## Product Quality Risks

- Quality can become opaque if too many factors are hidden.
- Quality formulas can overfit realism and reduce fun.
- Random errors can feel unfair if the player lacks control.
- Item stack merging behavior must respect distinct product components.

Prototype needed:

- One quality calculation with visible trace.
- Stack merge behavior for different product states.
- Tooltip presentation.

## MCDA Inspection Risks

- Escalation can feel punitive if warnings are too subtle.
- Shutdowns can trap a player if reinspection requirements are unclear.
- Inspection timing can interrupt gameplay too often.
- Real-world regulatory resemblance must be avoided beyond fictional, abstract gameplay.

Prototype needed:

- Basic write-up.
- Repeated violation escalation.
- Reinspection after correction.
- Forgiving config behavior.

## Commerce Risks

- Payment and reputation can dominate gameplay if balanced too early around incomplete systems.
- Wholesale orders can require logistics depth before storage and quality are mature.
- Advanced finances can overwhelm players who mainly want facility design.

Prototype needed:

- One order.
- Quality-gated acceptance.
- Reputation update.
- Later recurring-order mockup.

## Expansion Boundary Risks

- Expansions can accidentally depend on core internals if public APIs are incomplete.
- Optional integrations can create load-order and missing-mod problems.
- Datapack registry formats can become difficult to migrate if stabilized too early.

Mitigation direction:

- Keep expansion APIs small and documented.
- Mark experimental APIs clearly.
- Use tags and public interfaces over internal package access.
- Test expansion removal from a save before release.

## Art and Presentation Limitations

- Placeholder assets are expected early.
- Placeholders must still avoid graphic or disturbing processing visuals.
- MCDA visual identity must be fictional and must not imitate USDA branding.
- Machines and products should read clearly in Minecraft style before final polish.

## Open Prototype List

- First product and station interaction.
- Product component migration and future stackability beyond max stack size one.
- Simple refrigerated storage.
- Facility id and controller model.
- Work-order queue and reservation.
- One employee role.
- Cleanliness event and summary.
- MCDA escalation.
- Room refrigeration validation.
- Expansion API sample.
