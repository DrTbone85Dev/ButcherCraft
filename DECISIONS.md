# ButcherCraft Decision Log

Status: initial architecture decision log

`CONSTITUTION.md` is the governing authority for architectural decisions. Records that affect a permanent architectural invariant must cite its `AI-####` identifier, document compatibility and migration consequences, and receive explicit owner approval before implementation.

Decision statuses:

- Proposed: recommended but needs owner approval or prototype confirmation.
- Accepted: treated as project direction.
- Final: required by the user's brief or target platform.
- Superseded: replaced by a later decision.

## DEC-0001: Target Minecraft, Loader, Java, and Language

Status: Final

Decision: target Minecraft 1.21.1, NeoForge, Java 21, and Java as the primary language.

Rationale: this is a project constraint from the brief.

Consequences:

- Architecture must follow NeoForge 1.21.1 practices.
- Avoid APIs from later Minecraft or NeoForge versions unless the project target changes.

## DEC-0002: Mod Identity

Status: Accepted

Decision: use ButcherCraft as the project name, `butchercraft` as the mod id and asset namespace, and `com.butchercraft` as the base Java package.

Rationale: this is an approved owner decision made before implementation begins. The previous working name is superseded for active planning.

Consequences:

- Planning, generated assets, package paths, translation keys, registry namespaces, and future implementation should use the approved identity.
- Historical references to the earlier working name should only appear when explicitly discussing decision history.
- Artifact coordinates remain TBD until repository setup.

## DEC-0003: Core Mod Plus Substantial Expansions

Status: Superseded by DEC-0066

Decision: build ButcherCraft Core and a limited number of substantial optional expansions: ButcherCraft Harvest & Fabrication, ButcherCraft Refrigeration, ButcherCraft Further Processing, and ButcherCraft Commerce.

Rationale: this avoids a fragmented mod ecosystem while keeping advanced systems optional.

Consequences:

- Core must expose documented APIs.
- Expansions must avoid tight coupling to one another.
- No separate downloadable mod should be created for every machine.

This proposal remains the historical origin of the Meat Processing expansion strategy. DEC-0066 broadens active planning to a regional simulation platform while preserving the principle that modules should represent coherent responsibilities rather than one artifact per machine.

## DEC-0004: Manual-First Progression

Status: Final

Decision: every essential early job must be performable manually by the player before employees or advanced machines are required.

Rationale: this is a core design principle from the brief.

Consequences:

- Employee AI cannot be the only way to complete required work.
- Machines should increase throughput, consistency, or scale rather than replace the entire game loop.

## DEC-0005: Abstract, Non-Graphic Visual Direction

Status: Final

Decision: avoid graphic or unnecessarily disturbing animal-processing visuals and use abstract Minecraft-style representations.

Rationale: this is a project constraint and keeps the mod aligned with Minecraft's presentation.

Consequences:

- Placeholder assets must also follow this rule.
- Harvest/fabrication expansion must remain abstract.

## DEC-0006: Product State Uses Item Data Components

Status: Proposed

Decision: store product id, quality, freshness, temperature, and packaging state using item data components.

Rationale: item data components are the modern item stack data model for this target and support typed persistent and network-synchronized values.

Consequences:

- Component values should be immutable records with codecs.
- Tooltips and menus read component summaries rather than raw NBT.
- Product data migrations must be planned before public saves.

## DEC-0007: Business and Inspection State Uses SavedData

Status: Proposed

Decision: use `SavedData` for business ledger, order state, facility identity, and MCDA inspection history.

Rationale: these are world/facility-level systems that must persist beyond individual block entities.

Consequences:

- Saved data must call dirty marking after mutation.
- Data structures need versioning.
- Missing expansion content must not silently delete saved records.

## DEC-0008: Employee State Uses Entity Attachments

Status: Proposed

Decision: store employee skill, job role, business assignment, and work preferences on villager entities through data attachments.

Rationale: employee data belongs to individual entities and should survive entity save/load.

Consequences:

- Employee systems must handle villager death, unload, and missing business references.
- Business saved data should keep only stable references and summaries.

## DEC-0009: Server-Authoritative Simulation

Status: Proposed

Decision: the server owns product changes, quality, freshness, employee work, orders, payments, MCDA results, cleanliness, refrigeration, and persistence.

Rationale: this prevents client desync, cheating, and dedicated-server bugs.

Consequences:

- Client requests are validated on the server.
- Screens display synced state but do not decide outcomes.
- Client-only classes stay isolated.

## DEC-0010: Central Quality Service

Status: Proposed

Decision: calculate quality through a centralized service rather than embedding formula fragments in every station.

Rationale: quality depends on many systems and needs to be testable and balanceable.

Consequences:

- Stations and employees gather context and call the service.
- Formula coefficients can become config or data-driven after the shape stabilizes.
- Tests can cover quality without launching a full game session.

## DEC-0011: Event-Driven Cleanliness

Status: Proposed

Decision: model cleanliness through dirty/clean events and cached summaries rather than per-block per-tick scanning.

Rationale: facility-scale cleanliness can become expensive if simulated naively.

Consequences:

- Processing systems must emit cleanliness events.
- Facility summaries must update on relevant changes.
- Cleaning actions must persist meaningful state.

## DEC-0012: Simple Refrigeration in Core, Detailed Refrigeration in Expansion

Status: Proposed

Decision: include a simple refrigerated storage system in ButcherCraft Core and move walk-in rooms, compressors, condensers, evaporators, overload, wear, and failures into ButcherCraft Refrigeration.

Rationale: the vertical slice needs cold storage, but full refrigeration engineering is large enough to be optional.

Consequences:

- Core refrigeration API must be designed early.
- The simple storage path must remain useful as a fallback.
- Commerce and MCDA should read refrigeration summaries through stable interfaces.

## DEC-0013: Controller-Based Multiblocks

Status: Proposed

Decision: use controller block entities for scalable rooms and major multiblock machines.

Rationale: controllers give validation, persistence, UI, and inspection systems a stable anchor.

Consequences:

- Validation must be cached and event-driven.
- Member blocks need clear ownership and invalidation rules.
- Chunk boundary and unload behavior require prototypes.

## DEC-0014: Villager-Based Employees

Status: Proposed

Decision: model employees as villager-based workers with modded jobs, skills, and assignments.

Rationale: villagers fit Minecraft's world language and customer/employee theme.

Consequences:

- AI and pathfinding are a technical risk.
- The first employee role should be intentionally narrow.
- Employee data must survive unload/reload.

## DEC-0015: Data-Driven Definitions After Shape Is Proven

Status: Proposed

Decision: start the vertical slice with minimal code-defined definitions where faster, then move products, processes, order templates, and inspection rules to datapack registries or generated data once the shapes are stable.

Rationale: premature data abstraction can slow the first playable loop, but expansion content needs stable data-driven hooks.

Consequences:

- Keep service boundaries clean from the start.
- Do not hard-code logic in ways that block data-driven migration.
- Document public data formats before expansions rely on them.

## DEC-0016: Placeholder Assets Are Required When Final Art Is Missing

Status: Accepted

Decision: use placeholder assets rather than blocking implementation on final art, provided they are clearly non-graphic and Minecraft-style.

Rationale: gameplay validation should not wait for final visuals.

Consequences:

- Placeholder assets need clear names and should be easy to replace.
- Placeholder status should be documented in milestone notes.

## DEC-0017: No Silent Data Discard

Status: Final

Decision: placeholder or prototype systems must not silently discard saved product, business, order, employee, cleanliness, refrigeration, or inspection data.

Rationale: this is a project constraint and a save-safety requirement.

Consequences:

- If data is not yet supported, fail visibly in development or keep it inert but preserved.
- Migrations and unknown-id behavior must be planned.

## DEC-0018: First Playable Vertical Slice Content

Status: Final

Decision: the first playable vertical slice must include one basic meat product, one manual processing station, one grinder, one packaging station, one simple refrigerated storage system, one customer order, one basic employee job, basic cleanliness, and a basic MCDA inspection.

Rationale: this is a required milestone target from the brief.

Consequences:

- Milestone 1 should not expand content breadth before the loop is complete.
- Every included system must persist enough state to support save/load.

## DEC-0019: Split the First Vertical Slice Into Smaller Milestones

Status: Accepted

Decision: split the original first playable vertical slice into Milestones 1A through 1F: product/manual station, grinder/packaging, refrigerated storage, customer order, cleanliness/MCDA write-up, and one employee job.

Rationale: the architecture review found the original Milestone 1 too large and likely to encourage tightly coupled implementations.

Consequences:

- Each subsystem gets an independent acceptance gate before the complete loop is assembled.
- Persistence and duplication safeguards can be verified earlier.
- The full vertical slice remains the product goal, but implementation proceeds in smaller increments.
- Milestones 1B and 1C were later redefined by DEC-0023 and DEC-0024 as pure engine/framework milestones; Milestone 1D was later redefined by DEC-0025 as product data integration; visible station, refrigeration, and commerce slices are deferred for owner scheduling.

## DEC-0020: Domain Boundaries Before Gameplay Implementation

Status: Accepted

Decision: before implementing substantive gameplay, define narrow boundaries for product/quality, cleanliness, refrigeration, inspections, work orders, employee roles, facility identity, and order fulfillment.

Rationale: the architecture review identified these as high-risk coupling points and likely sources of God Objects.

Consequences:

- Systems should exchange immutable snapshots, events, ids, and reservation tokens rather than direct saved-data, block-entity, or entity references.
- Public APIs remain documented before expansions depend on them.
- The project foundation may include registration and diagnostic plumbing, but not speculative gameplay abstractions.

## DEC-0021: Server-Side Transactions for Item-Moving Systems

Status: Accepted

Decision: every future system that moves products, station inventories, packaging labels, order deliveries, or work-order outputs must use server-side transactions and duplication safeguards.

Rationale: the architecture review identified duplication and inventory-loss exploits as a critical risk.

Consequences:

- Client requests must be validated and idempotent where completion or payment is possible.
- Block break, save/load, station unload, and concurrent employee/player access require explicit tests.
- Item component merge rules must be defined before public saves.

## DEC-0022: Foundation-Only Registered Content

Status: Accepted

Decision: the repository foundation may register one harmless development test item, one ButcherCraft creative tab, common configuration, logging, data-generation providers, and a safe diagnostic command.

Rationale: these foundation elements verify NeoForge registration, metadata, assets, diagnostics, and build wiring without implementing meat-processing gameplay.

Consequences:

- The development test item must stay clearly documented as non-gameplay.
- The diagnostic command must not grant items, mutate the world, expose local paths, expose environment variables, or report sensitive system details.
- Future gameplay systems must be introduced through the split milestone plan, not hidden inside foundation code.

## DEC-0023: Minecraft-Independent Engine Foundation

Status: Accepted

Decision: implement Milestone 1B as a Minecraft-independent engine under `com.butchercraft.engine` before adding visible gameplay systems. The engine owns product, quality, quantity, modifier, operation-result, and processing-transaction rules and must not import `net.minecraft` or `net.neoforged`.

Rationale: the architecture review identified product state, quality, and item-moving transactions as high-risk coupling and duplication areas. A pure Java engine lets those invariants be tested before Minecraft integration.

Consequences:

- Minecraft integration may depend on the engine, but the engine must not depend on Minecraft integration.
- The previous grinder and packaging station scope formerly listed as Milestone 1B is split across later visible station milestones. Packaging gameplay remains deferred after the v0.8.0 table foundation.
- Quality score boundaries are fixed for the engine as Poor 0-199, Fair 200-399, Good 400-699, Excellent 700-899, and Premium 900-1000.
- Quantity uses exact `long` amounts with explicit units; no silent conversion or rounded yield is allowed in the engine.
- Processing transactions must prepare output before commit and prevent double output.

## DEC-0024: Processing Framework Before Visible Stations

Status: Accepted

Decision: implement Milestone 1C as a Minecraft-independent processing framework under `com.butchercraft.engine` before adding grinder, station, refrigeration, or other visible gameplay systems.

Rationale: operation validation, context handling, yield math, modifier ordering, warnings, and transaction preparation are high-risk foundations for later item-moving gameplay. Proving them in pure Java reduces duplication and coupling risk.

Consequences:

- `ProcessingOperation` defines transformation rules but owns no transaction state or mutable work progress.
- `ProcessingContext` carries explicit facts supplied by future integrations and does not know how those facts were obtained.
- `ValidationRule` returns inspectable validation results instead of boolean-only outcomes.
- Yield modifiers use additive basis points, with `10,000` basis points equal to 100%.
- Yield rounds half up to the nearest smallest quantity unit and rejects negative effective yield or overflow.
- The Beef Trim to Ground Beef operation exists only as test fixture data until Minecraft content is explicitly scheduled.
- The simple refrigerated storage scope formerly listed as Milestone 1C is deferred until the owner schedules a visible storage milestone.

## DEC-0025: Product ItemStacks Use One Product Data Component and Do Not Stack Yet

Status: Accepted

Decision: implement Milestone 1D with one registered data component, `butchercraft:product_data`, storing immutable `ProductStackData` on product-bearing ItemStacks. Product-bearing development fixture items have maximum stack size `1`.

Rationale: the engine already stores exact product quantity. Allowing ItemStack count to also represent quantity before merge rules exist would create two independent quantity systems and could duplicate, average, or discard quantity or quality.

Consequences:

- Product stack data is stored through a persistent codec and network stream codec, not arbitrary legacy NBT.
- ItemStack copies preserve product data through the component system.
- Distinct product data participates in stack equality through data components.
- Future stackability requires an explicit design for ItemStack count, engine quantity, quality, freshness, temperature, packaging, and lot identity before product items can safely merge.
- Beef Trim Test Product and Ground Beef Test Product are development-only fixtures and not final gameplay content.

## DEC-0026: Processing Families Are Data-Driven Profiles

Status: Accepted

Decision: represent processing-family differences, such as red meat versus future poultry workflows, through datapack-backed processing profiles and operation compatibility data rather than hardcoded species checks.

Rationale: poultry may require scalding, picking, poultry-specific evisceration, chilling, sanitation, equipment, inspection, and cross-contamination rules that differ fundamentally from red meat. Encoding these differences as data keeps future expansions and datapacks from depending on Java species switches.

Consequences:

- Species definitions reference processing profiles by stable registry id.
- Operation definitions declare required processing profiles and categories.
- The processing graph rejects incompatible profile/category combinations through validation.
- Future workstation capabilities and inspection profiles should attach to data definitions, not literal species ids.
- ButcherCraft Core currently includes red-meat prototype grinding definitions for beef, pork, and bison; poultry remains deferred content.

## DEC-0027: Server-Authoritative Workstation Ticking

Status: Accepted

Decision: processing workstations tick and mutate inventory only on the logical server.

Rationale: workstation processing moves product stacks and commits engine transactions, so client authority would create duplication and desync risks.

Consequences:

- Clients receive display state only.
- Block entities own local ticking and persistence.
- Menus and future screens must not decide processing outcomes.

## DEC-0028: Workstation Menus Are Views

Status: Accepted

Decision: workstation menus are temporary views over block entity inventory and synced display data; they do not own workstation state.

Rationale: inventory and processing state must survive menu close, chunk unload, save, and reload.

Consequences:

- The block entity and controller remain the source of truth.
- Menu shift-click behavior must respect input/output slot rules.
- Future screens should remain client-only display code.

## DEC-0029: Automatic Operation Selection Requires Exactly One Match

Status: Accepted

Decision: a workstation may automatically select an operation only when exactly one compatible operation resolves.

Rationale: arbitrary selection would become incorrect once multiple valid operations exist.

Consequences:

- Multiple compatible operations return a selection-required failure.
- Recipe-selection GUI is deferred until a milestone explicitly adds it.
- Resolver ordering remains deterministic for diagnostics and future UI.

## DEC-0030: Workstation Completion Is Idempotent Across Save/Reload

Status: Accepted

Decision: workstation completion commits the engine transaction once, persists enough runtime state for recovery, and never recreates output after completion.

Rationale: processing blocks are a high-risk source of item duplication or silent item loss.

Consequences:

- Input remains visibly reserved in the input slot while processing.
- Malformed active state stops processing and preserves recoverable inventory.
- Block removal drops recoverable input and completed output.

## DEC-0031: Development-Only Product Definition To Item Mapping

Status: Accepted

Decision: early workstation and Grinder milestones use a small development-only mapping from prototype product definitions to registered test product items.

Rationale: a universal product item factory would be premature before final product item design, packaging, freshness, and lot identity exist.

Consequences:

- `butchercraft:beef_trim` maps to Beef Trim Test Product.
- `butchercraft:ground_beef` maps to Ground Beef Test Product.
- `butchercraft:pork_trim` maps to Pork Trim Test Product.
- `butchercraft:ground_pork` maps to Ground Pork Test Product.
- `butchercraft:bison_trim` maps to Bison Trim Test Product.
- `butchercraft:ground_bison` maps to Ground Bison Test Product.
- `butchercraft:beef_forequarter` maps to Beef Forequarter Test Product.
- Milestone 2E beef fabrication outputs map to their matching development fixture items.
- Future product item creation remains a separate design task.

## DEC-0032: No Machine-Specific Species Switches

Status: Accepted

Decision: workstation compatibility is expressed through operation categories, workstation capability ids, and processing profiles rather than Java species switches.

Rationale: future poultry or other product families need different workflows without hardcoded species branches.

Consequences:

- Generic workstation code must stay product-agnostic.
- Development fixtures may reference prototype product ids only in clearly named fixture or product-integration classes.
- Future poultry-specific machines can be modeled through separate capabilities and datapack operation data.

## DEC-0033: Product Source Categories Are Open Engine IDs

Status: Accepted

Decision: represent an engine product source category as an immutable `EngineId` value with a few convenience constants, rather than as a closed enum of every supported species.

Rationale: Milestone 2D adds bison and proves future species can pass through product components, definition validation, and engine transactions without adding species constants or branches to the pure engine.

Consequences:

- Product component construction still validates identifier syntax, quantity units, processing states, and quality bounds.
- Loaded product definitions remain responsible for rejecting unknown or mismatched species/source ids.
- Engine validation compares source-category values by id equality.
- New species should be introduced through definitions and fixture/content registration, not engine enum edits.

## DEC-0034: Grinder Multi-Species Behavior Is Definition-Driven

Status: Accepted

Decision: Milestone 2D proves Grinder genericity by adding pork and bison red-meat grinding definitions that use the existing `butchercraft:grinding` capability and red-meat processing profile.

Rationale: the Grinder should be a machine wrapper over the workstation framework, not a place for species-specific behavior.

Consequences:

- `GrinderBlock`, `GrinderBlockEntity`, `GrinderMenu`, and `GrinderScreen` must remain free of beef, pork, bison, or operation-id branches.
- Generic workstation code must remain free of Grinder and species-specific branches.
- The temporary product-item fixture mapping may list registered development fixture items, but operation selection and compatibility must come from definitions and the processing graph.
- Pork and bison are prototype red-meat proof data only, not full species catalogs or regulatory systems.

## DEC-0035: Processing Operations Own Ordered Output Collections

Status: Accepted

Decision: processing operations represent outputs as immutable ordered collections. Existing single-output operations are represented as a one-element output list for compatibility.

Rationale: fabrication operations such as breaking a forequarter produce several related products at once. Modeling this in the engine and definition schema avoids machine-specific output logic and keeps Grinder behavior on the same generic path.

Consequences:

- `ProcessingOperation`, `OperationResult`, and `ProcessingTransaction` expose ordered output-result lists while retaining first-output helpers for older single-output callers.
- Definition JSON supports an `outputs` array with product, state, exact yield, quality adjustment, quantity unit, and zero-output policy per output.
- Multi-output yield allocation uses integer arithmetic and deterministic largest-remainder rounding with output-order tie breaks.
- Yield modifiers remain supported for legacy single-output operations; multi-output yield modifiers need a deliberate allocation design before they are enabled.

## DEC-0036: Bandsaw Is A Two-Block Machine Wrapper Over The Workstation Framework

Status: Accepted

Decision: the industrial Bandsaw is a lower functional block plus an upper forwarding block. The lower block owns all state, inventory, ticking, persistence, menu behavior, and item recovery; the upper block has no block entity.

Rationale: the machine should behave like one tall workstation in the world without duplicating inventories or creating multiple state owners.

Consequences:

- `butchercraft:bandsaw` is the only half that owns a block entity and drops recoverable inventory.
- `butchercraft:bandsaw_upper` forwards interaction to the lower block and removes the paired machine when broken.
- Placement requires replaceable space above the lower block, and both halves share horizontal facing.
- Beef forequarter fabrication is selected through the `butchercraft:bandsaw` capability and processing definitions, not Bandsaw Java branches.

## DEC-0037: Midwestern Butcher-Cut Terminology Is Canonical

Status: Accepted

Decision: ButcherCraft uses Midwestern butcher-counter terminology for player-facing butcher cuts and product ids, while preserving anatomical distinctions. The whole brisket prototype is `butchercraft:beef_packer_brisket` with display name "Packer Brisket". Future applicable products should use "Kansas City Strip Steak" instead of "New York Strip", "Picanha" only for an anatomically correct sirloin-cap/top-sirloin-cap product, "Prime Rib" only for an intact rib roast, and "Denver Steak" only for the correct chuck underblade cut.

Rationale: player-facing cut names should match the project's intended butcher-shop language without flattening broader anatomical products into retail steak names.

Consequences:

- Product definitions, generated resources, fixture items, docs, diagnostics, and tests use canonical names when a modeled product matches the canonical cut.
- Broad cuts such as rib, chuck, or plate must not be blanket-renamed to narrower retail cuts.
- No unsafe alias or migration system is introduced for the retired pre-release `butchercraft:beef_brisket` fixture id.
- Grinder, Bandsaw, workstation framework, resolver, and pure engine code remain product-agnostic and must not translate cut names.

## DEC-0038: Material Transformation Extends Processing Without Replacing It

Status: Accepted

Decision: begin version 0.6.0 with an additive pure Java material-transformation domain under `com.butchercraft.transformation`. It uses existing engine identifiers, quantities, and durations; returns stable evaluation codes; and provides a compatibility adapter from `ProcessingOperation` to `TransformationDefinition`.

Rationale: ButcherCraft needs a generic material-transformation vocabulary before future production systems broaden beyond current product processing. Keeping the first slice additive protects the working Grinder, Bandsaw, workstation controller, datapack registries, and item data components.

Consequences:

- Existing `ProcessingOperation` APIs remain supported.
- Workstations continue using the current resolver and controller until a later milestone deliberately migrates an integration path.
- Transformation code must stay free of Minecraft and NeoForge imports.
- The first evaluator validates capability and material availability only; quality, probabilistic outputs, inventory mutation, ItemStack operations, and public expansion APIs remain out of scope.

## DEC-0039: Grinder Uses Capability-Based Transformation Execution First

Status: Accepted

Decision: version 0.6.1 migrates only the Grinder onto a capability-based transformation execution strategy. The shared workstation controller delegates preparation and commit through `WorkstationExecutionStrategy`; un-migrated machines keep the default legacy strategy.

Rationale: this proves the v0.6.0 transformation engine can execute a live workstation path without forcing every workstation through the new bridge at once or rewriting the established processing framework.

Consequences:

- The pure transformation executor executes only accepted evaluations that still match the same definition and context.
- Minecraft-facing workstations advertise capabilities through `WorkstationCapability`, which can be adapted into the pure transformation capability model at the boundary.
- Grinder operation resolution still comes from product definitions, processing-operation definitions, the processing graph, and `butchercraft:grinding`.
- Product quality, source data, ItemStack creation, and inventory mutation remain owned by the existing processing transaction and workstation controller.
- Bandsaw, smoker, packaging, coolers, datapack transformation loading, menus, and screens are not migrated in this decision.

## DEC-0040: Transformation Definitions Are Resolved Through An Immutable Registry

Status: Accepted

Decision: version 0.6.2 introduces a pure Java immutable `TransformationRegistry` as the authoritative lookup source for transformation definitions. A mutable `TransformationRegistryBuilder` is used only before the registry is frozen.

Rationale: the Grinder should execute registered transformation definitions rather than constructing transformation definitions directly from the resolved operation at the workstation boundary. This gives the transformation engine a stable definition source before datapack loading exists.

Consequences:

- Registry iteration and capability queries preserve insertion order.
- Duplicate transformation ids and null registrations are rejected during building.
- The built-in Grinder transformations are registered centrally in the pure transformation package.
- The Grinder transformation strategy looks up the resolved operation id in the registry, rebases the registered definition to the current input quantity, and then evaluates/executes it.
- Datapack loading, public expansion APIs, and other workstation migrations remain out of scope.

## DEC-0041: TransformationDefinition Is The Canonical Transformation Schema

Status: Accepted

Decision: version 0.6.3 expands `TransformationDefinition` into the canonical immutable schema for future transformations. The schema includes id, display name, schema version, required capability, ordered inputs, ordered outputs, duration, yield, and typed metadata.

Rationale: serialization and datapack loading need a complete domain schema before any file format is introduced. Validating the pure Java schema first keeps future loading work focused on decoding and reporting rather than inventing rules at the I/O boundary.

Consequences:

- Definitions validate completeness, duplicate materials, yield consistency, nonblank display names, positive schema versions, and metadata values during construction.
- A fluent builder is the preferred construction API for new definitions.
- Legacy constructors remain as compatibility bridges for existing Grinder transformation and adapter code.
- Metadata is stored as an immutable `Map<EngineId, String>` so keys are typed and Minecraft-independent.
- Serialization, datapack loading, schema migration, and public expansion APIs remain out of scope.

## DEC-0042: Product Definitions Are Pure Registry Data Separate From Transformations

Status: Accepted

Decision: version 0.6.4 introduces `com.butchercraft.product.definition` as a pure Java product definition foundation. Transformations continue to reference stable product ids, and product-reference validation runs as a separate deterministic step against a `ProductRegistry`.

Rationale: future serialization needs product ids to resolve against authoritative descriptive data, but transformation definitions must be decodable before every registry is assembled. Keeping validation separate protects the assembly order and avoids embedding product data into transformations.

Consequences:

- `ProductDefinition` contains id, display name, schema version, typed category, default quantity unit, tags, optional packaging metadata, and typed metadata.
- `ProductRegistry` preserves insertion order and supports id, category, and tag lookup.
- Built-in product definitions began with the six current Grinder products and now also include bounded Bandsaw and retail proof products.
- `TransformationProductReferenceValidator` checks missing product ids and quantity-unit mismatches after construction.
- Product definition code must remain free of Minecraft and NeoForge imports.
- Product-to-item mapping, spoilage, broader packaging execution beyond the first Packaging Table flow, storage rules, and broader product catalogs remain out of scope unless a later milestone explicitly schedules them.

## DEC-0043: Transformation Serialization Is A Pure Schema Contract

Status: Accepted; execution deferral superseded by DEC-0052

Decision: version 0.6.5 introduces `com.butchercraft.transformation.serialization` as a pure Java serialization contract for `TransformationDefinition`. The canonical serialized representation freezes external field names for id, display name, schema version, required capability, inputs, outputs, duration, yield, and metadata.

Rationale: future datapack loading needs a stable external schema before resource discovery, reload behavior, and migration handling are added. Keeping serialization separate from Minecraft and NeoForge allows definitions to be decoded, validated, tested, and migrated without registry access or game runtime dependencies.

Consequences:

- Serializer and deserializer contracts operate on pure Java types and current transformation definitions.
- Built-in Grinder transformations must round-trip through canonical serialization without behavior changes.
- `TransformationSchemaVersion` is the explicit version boundary for external transformation schema data.
- A migration interface is defined for future version changes, but v0.6.5 does not implement migrations.
- Datapack loading, resource reload listeners, JSON resource discovery, Minecraft codecs, registry access, and workstation migrations remain out of scope.

## DEC-0044: Transformation Transactions Must Be Atomic Before Bandsaw Migration

Status: Accepted; execution deferral superseded by DEC-0052

Decision: version 0.6.6 adds a pure Java in-memory transaction model to the transformation engine before migrating any multi-output workstation. `TransformationTransaction` validates all input extraction and output insertion against material stores before committing, and restores snapshots if a commit-time mutation fails after partial progress.

Rationale: multi-output transformations are more duplication-prone than one-output grinding. Proving atomic input consumption and ordered output insertion in the pure transformation domain lets future workstation migrations reuse tested behavior instead of coupling transaction safety to a specific block entity.

Consequences:

- `TransformationExecutor` keeps the existing side-effect-free execution path and gains an overload for transactional material-store execution.
- `TransformationMaterialStore` and `InMemoryTransformationMaterialStore` model material quantities and capacity without Minecraft inventories or ItemStacks.
- Ordered outputs are committed in definition order and roll back as a unit on failure.
- Existing Grinder behavior remains unchanged and one-output transformations remain compatible.
- Bandsaw, datapack loading, resource reload listeners, Minecraft inventory mutation, menus, screens, and product-to-item mapping remain out of scope.

## DEC-0045: Bandsaw Uses Atomic Transformation Execution Through A Minecraft Boundary Adapter

Status: Accepted

Decision: version 0.6.7 migrates only the Bandsaw to the registry-backed atomic transformation execution strategy. The Bandsaw continues resolving operations through the existing processing definitions and workstation resolver, then validates completion through `TransformationEvaluator`, `TransformationExecutor`, and `TransformationTransaction` using Minecraft-side material-store adapters.

Rationale: the Bandsaw is the first live multi-output workstation and is the right proof that the transformation engine can govern ordered output capacity without moving species, product, or cut-list logic into machine classes. Keeping the adapter in `com.butchercraft.workstation` preserves the pure transformation and product-definition package boundaries.

Consequences:

- `butchercraft:break_beef_forequarter` is registered in the immutable transformation registry with the same ordered outputs as the existing processing-operation definition.
- The built-in product registry now includes the minimum current Bandsaw proof product ids in addition to the Grinder proof products.
- `WorkstationInventoryMaterialStore` adapts Bandsaw ItemStack inventory snapshots to pure input and output `TransformationMaterialStore` instances for transaction validation.
- Missing transformation definitions, rejected transaction validation, output capacity failures, and missing development product-item mappings surface as explicit workstation blocked states.
- The controller still owns progress, save/load, ItemStack creation, output insertion, and block-entity lifecycle; the pure transaction validates atomic feasibility before those Minecraft mutations occur.
- Grinder behavior is preserved. Smoker, packaging, coolers, datapack loading, full carcass fabrication, and public expansion APIs remain out of scope.

## DEC-0046: Transformation Definitions Load From Datapack JSON

Status: Accepted

Decision: version 0.6.8 replaces Java-defined built-in transformation registrations with datapack JSON resources under `data/<namespace>/butchercraft/transformation`. The loader parses JSON into `SerializedTransformationDefinition`, deserializes through the canonical schema deserializer, validates products and capabilities, and atomically replaces the active immutable `TransformationRegistry` only after a successful reload.

Rationale: transformation serialization is already the stable external schema contract. Loading that schema from datapacks proves the registry can become data-driven without changing evaluator, executor, transaction, Grinder, or Bandsaw behavior.

Consequences:

- Bundled Grinder and Bandsaw transformations live as datapack JSON resources.
- `TransformationRegistryService` owns the reload-safe active registry reference.
- Workstation transformation strategies query the active registry at execution time instead of capturing a static Java-built registry.
- Malformed datapacks produce structured validation errors for duplicate ids, unknown products, unknown capabilities, unsupported schema versions, and malformed definitions.
- Minecraft reload listener code lives outside the pure transformation model.
- Expanded fabrication, schema migrations, product datapack loading, product-to-item factories, and public expansion APIs remain out of scope.

## DEC-0047: Product And Transformation Registries Activate As One Content Snapshot

Status: Accepted

Decision: version 0.6.9 moves the current built-in product definitions into content snapshot JSON resources under `data/<namespace>/butchercraft/content/product` and introduces a reload-safe content snapshot containing the immutable `ProductRegistry` and immutable `TransformationRegistry`. Version 0.8.0 Sprint 2 extends that snapshot with the immutable `PackagingRegistry`. The product path remains separate from the existing Minecraft datapack registry path `data/<namespace>/butchercraft/product`, which is decoded by the richer processing product schema. Product content is validated first; packaging content and product packaging metadata validate next; transformations then validate their product references against that candidate product registry. The active snapshot is replaced only when every candidate registry loads successfully.

Rationale: transformations reference product ids, so reloading those registries separately can briefly expose mismatched content or incorrectly validate against stale products. A single content snapshot keeps the data boundary deterministic while preserving pure Java product and transformation domains.

Consequences:

- Product definitions have a canonical serialized schema with stable field names for id, display name, schema version, category, default quantity unit, tags, optional packaging metadata, and metadata.
- Bundled Grinder and Bandsaw proof products now live as datapack JSON resources.
- `ProductRegistryService`, `PackagingRegistryService`, and `TransformationRegistryService` are compatibility facades over the active content snapshot.
- Failed product reloads prevent packaging and transformation loading. Failed packaging reloads or invalid product packaging metadata prevent transformation loading. Failed transformation reloads reject the full snapshot.
- Product-to-ItemStack mappings remain Java-controlled development fixtures; datapacks do not dynamically register Minecraft items.
- Expanded fabrication, schema migrations, datapack-driven category catalogs, product-to-item factories, and public expansion APIs remain out of scope.

## DEC-0048: Beef Fabrication Expansion Is Data Content Over Existing Bandsaw Architecture

Status: Accepted

Decision: version 0.7.0 adds the first substantial beef fabrication chain as bundled product datapack resources, bundled transformation datapack resources, processing-operation definitions, and development fixture item mappings. The Bandsaw continues to advertise only `butchercraft:bandsaw` and remains free of product-specific cut-list behavior.

Rationale: the project needs broader fabrication content to prove the v0.6.x registry, snapshot, resolver, and atomic transaction architecture under a larger multi-stage chain. Adding content through existing definitions verifies the architecture without expanding scope into a machine redesign or dynamic item factory.

Consequences:

- Beef Hindquarter, Round, Sirloin, Short Loin, Flank, T-Bone Steak, Porterhouse Steak, Beef Strip Loin, Beef Tenderloin, Top Round, Bottom Round, Eye of Round, Sirloin Tip, Top Sirloin, Sirloin Steak, and Tri-Tip are bundled product definitions and development fixture items.
- `break_beef_hindquarter`, `cut_beef_short_loin`, `cut_beef_round`, and `cut_beef_sirloin` are bundled Bandsaw transformation definitions.
- Matching processing-operation definitions are required so the existing processing graph and operation resolver can discover the new transformations by input product and capability.
- Product-to-ItemStack output remains a controlled Java development fixture mapping until a real product item creation system is scheduled.
- Full carcass fabrication, recipe selection, balancing, dynamic product items, other workstation migrations, and public expansion APIs remain out of scope.

## DEC-0049: Packaging Table Foundation Was Initially Inventory-Only

Status: Accepted; execution deferral superseded by DEC-0052

Decision: the initial version 0.8.0 Packaging Table milestone added `butchercraft:packaging_table` as a permanent workstation foundation with persisted inventory, a placeholder menu and screen, creative-tab visibility, item-handler capability exposure, and block-break recovery. That foundation intentionally deferred packaging execution, transformations, product mutation, labels, order fulfillment, and employee behavior until later scheduled work.

Rationale: Project Meat Counter needs the player-facing station surface before packaging rules are designed. Keeping the block entity outside the processing controller path avoids hardcoding premature product, tray, wrap, or package semantics into generic workstation logic.

Consequences:

- `AbstractInventoryWorkstationBlockEntity` owns shared inventory persistence and menu-provider behavior for workstation foundations that do not execute processing.
- The Packaging Table advertises `butchercraft:packaging` and a three-input, one-result layout. Sprint 2 originally added a graph-only processing operation for that capability; DEC-0052 now adds controller-backed execution for the first retail packaging flow.
- Existing Grinder and Bandsaw processing paths remain unchanged.
- Future packaging gameplay must add data-driven definitions and transaction rules deliberately rather than reusing placeholder slot names as hidden logic.

## DEC-0050: Retail Product Framework Is Data-Only Until Packaging Execution Is Scheduled

Status: Accepted

Decision: version 0.8.0 Sprint 2 adds a pure Java `PackagingDefinition` schema, canonical serialization records, datapack loader, immutable `PackagingRegistry`, and `PackagingRegistryService`. It also adds optional packaging metadata to canonical product definitions, validates that metadata against candidate product and packaging registries, and activates product, packaging, and transformation registries as one `ContentSnapshot`.

Rationale: packaged retail products need stable data contracts before recipe execution, supply consumption, labels, freshness, spoilage, or business systems can safely depend on them. Keeping the framework data-only prevents the Packaging Table placeholder slots from becoming hidden gameplay logic.

Consequences:

- Packaging resources load from `data/<namespace>/butchercraft/content/packaging`.
- `butchercraft:retail_package` is the first built-in packaging definition.
- `butchercraft:retail_ground_beef` is the first built-in packaged retail product and references `butchercraft:ground_beef` as its source product.
- `butchercraft:package_retail` is registered as a processing-operation definition so the graph can represent retail packaging. Sprint D executes it on the Packaging Table without adding a transformation definition.
- Existing products without packaging metadata remain valid.
- Packaging recipes, supply consumption, labels, weights, freshness, spoilage, dynamic textures, overlay rendering, business logic, GUI changes, sounds, animations, and item factory behavior remain out of scope.

## DEC-0051: Packaging Supplies Are Fixed Items Referenced By Data Only

Status: Accepted; consumption deferral superseded by DEC-0052

Decision: version 0.8.0 Sprint C adds Foam Tray, Plastic Wrap Roll, Vacuum Bag, Butcher Paper Roll, Freezer Paper Roll, and Retail Label Roll as fixed Minecraft item registrations. `PackagingDefinition` gains optional immutable required supply item ids, and packaging datapack loading validates those ids against the fixed built-in supply item set.

Rationale: future packaging recipes need stable physical materials before supply consumption, labels, spoilage, or business rules are designed. Keeping supply ids in packaging definitions proves the data contract without making the Packaging Table consume items or execute packaging operations.

Consequences:

- Built-in packaging definitions now prove `tray_wrap`, `vacuum`, `butcher_paper`, and `freezer_paper` formats.
- Older packaging definitions without `required_supply_items` still load with an empty supply list.
- Datapacks may reference known supply item ids but do not dynamically register supply items, models, textures, or creative-tab entries.
- Malformed supply arrays and unknown supply ids fail packaging loading with structured validation errors and preserve the previously active content snapshot.
- The Retail Label Roll is registered for future label systems but does not add labels to products in this sprint.
- Packaging recipes, labels on products, dynamic rendering, weight, freshness, spoilage, custom sounds, animations, GUI redesign, and business logic remain out of scope.

## DEC-0052: Packaging Table Executes Through Processing And A Shared Commit Plan

Status: Accepted

Decision: version 0.8.0 Sprint D connects only the Packaging Table to the existing processing workstation controller for `butchercraft:package_retail`. The table uses `PackagingTableExecutionStrategy` to validate output product packaging metadata, resolve the active `PackagingDefinition`, validate required supply items, create packaged output stack metadata, and select consumed input slots. `WorkstationInventoryCommitPlan` performs the Minecraft inventory mutation by snapshotting all inputs and outputs, consuming the selected product and supply slots, inserting output, and restoring snapshots if commit-time mutation fails.

Rationale: packaging gameplay moves multiple input stacks and creates a product output, so it has the same duplication and inventory-loss risk as Grinder and Bandsaw processing. The Packaging Table needed to join the existing server-authoritative processing framework instead of adding a hidden recipe path or one-off table transaction.

Consequences:

- The Packaging Table advertises `butchercraft:packaging`, resolves operation selection through the processing graph, and remains outside transformation definitions and transformation execution.
- Product selection comes from processing-operation definitions. Packaging definition ids and source products come from product packaging metadata. Required supplies and packaging format come from the active `PackagingRegistry`.
- Slot-aware input validation lets slot `0` accept resolvable product stacks and auxiliary slots accept known packaging supply items.
- Stack-level packaging metadata is stored on `ProductStackData` for packaged output stacks. Existing stacks without packaging metadata continue decoding with an empty packaging value.
- The first supported flow is Ground Beef Test Product plus Foam Tray plus Plastic Wrap Roll into Retail Ground Beef Test Product.
- Packaging recipes, labels, freshness, spoilage, dynamic rendering, business logic, employee operation, order fulfillment, and dynamic product item creation remain out of scope.

## DEC-0053: Packaging Asset Paths Are Stable Before Final Art

Status: Accepted

Decision: version 0.8.0 Sprint E establishes stable resource-pack paths for active packaging presentation assets without creating final artwork. Packaging supplies and Retail Ground Beef use individual `assets/butchercraft/textures/item/packaging/*.png` targets, the Packaging Table block model uses `assets/butchercraft/textures/block/workstation/*.png` targets, and the Packaging Table GUI uses `assets/butchercraft/textures/gui/packaging_table.png`.

Rationale: artists need stable filenames, model dependencies, and GUI bounds before production artwork begins. Using normal Minecraft resource locations preserves resource-pack override compatibility and avoids changing gameplay architecture for visual iteration.

Consequences:

- Placeholder textures now live at the same paths final artwork will replace.
- The old shared packaging supply placeholder texture path is retired instead of retained as a duplicate mask for stale references.
- Packaging Table model geometry is still generated by datagen, but now exposes separate surface, frame, and roll texture dependencies.
- Packaging supply and Retail Ground Beef item models remain simple `minecraft:item/generated` models with inherited vanilla display transforms.
- Packaging Table GUI layout constants are documented and tested so final GUI art can target the active 176x166 region in a 256x256 texture.
- Final textures, generated AI artwork, custom renderers, animations, dynamic labels, item recipes, gameplay balance changes, and new product content remain out of scope.

## DEC-0054: World Identity Is Deterministic SavedData Before Gameplay

Status: Accepted

Decision: version 0.9.0 Phase 1 introduces the World Identity foundation as immutable pure Java domain data generated deterministically from the world seed and persisted in Overworld-scoped `SavedData`. `WorldIdentityService` owns the Minecraft boundary for creating, loading, caching, and exposing the active identity when the server starts.

Rationale: future manufacturers, commercial properties, local markets, and business simulation need stable world identity context before gameplay systems depend on it. Generating the identity from the seed gives new worlds deterministic defaults, while persisting the first generated identity prevents future schema changes from silently reshaping an existing save.

Consequences:

- `WorldIdentity`, `Region`, `County`, and `Settlement` remain pure Java models without Minecraft or NeoForge imports.
- The first schema stores one region, counties, settlements, the source seed, and descriptive identity fields only.
- The data file name is `butchercraft_world_identity`, attached to the Overworld because the identity describes the whole save rather than a single dimension.
- World Identity loading is server-side and passive; no commands, GUI, economy, manufacturers, commercial properties, or player interactions are introduced in Phase 1.
- Future schema migrations must preserve existing saved identities rather than regenerating them from the seed.

## DEC-0055: Region And Naming Generation Uses Stable Catalog Identifiers

Status: Accepted

Decision: version 0.9.0 Phase 2 defines the first five handcrafted World Identity regions in a pure Java `RegionCatalog` and assigns each region a stable naming profile. Region selection and generated county or settlement names are derived from the world seed plus stable region, profile, role, and entity identifiers through ButcherCraft-owned hashing. The generator does not use a shared mutable random sequence.

Rationale: future world identity systems need names that remain stable when individual entities are generated independently. Stable identifiers prevent unrelated code motion, helper calls, or generation order from renaming existing counties and settlements.

Consequences:

- Built-in regions are Prairie Commonwealth, Iron Valley, Great River Basin, High Plains Territory, and Timber Ridge.
- Naming profiles use curated handcrafted name pools rather than uncontrolled syllable generation.
- Region selection is scored by region id instead of list position, with deterministic ordering only as a tie-breaker.
- County and settlement ids are based on stable generation slots, not selected display names.
- World identity schema version 2 adds region description, cultural identity, and naming profile id fields.
- Phase 1 development saves migrate into schema version 2 and are marked dirty so the upgraded snapshot can be persisted.
- Road names, business names, manufacturer names, commercial properties, economy behavior, commands, and UI remain out of scope.

## DEC-0056: Manufacturers Are Canonical World Identity Data Before Commerce

Status: Accepted

Decision: version 0.9.0 Phase 3 introduces manufacturers as immutable pure Java world identity domain data under a canonical `ManufacturerRegistry`. The built-in catalog contains exactly 30 handcrafted fictional companies with stable ids, regional headquarters, categories, market tiers, engineering philosophies, history, slogans, branding, specialties, reputation, and future catalog or website placeholders.

Rationale: future equipment catalogs, manuals, advertisements, warranties, supplier relationships, service bulletins, trade shows, recalls, and industrial history need a stable manufacturer identity source before commerce or machine gameplay depends on company references. Building this as a validated registry avoids scattering manufacturer names across future systems.

Consequences:

- Manufacturer headquarters reference the existing handcrafted World Identity regions.
- Manufacturer categories, market tiers, and engineering philosophies are strongly typed rather than free-form strings.
- Registry ordering is deterministic by stable id and does not depend on source list order.
- Validation rejects duplicate ids, names, slogans, unknown headquarters regions, incomplete identities, invalid founding years, empty histories, empty specialties, and missing branding.
- The manufacturer package remains independent of Minecraft and NeoForge imports.
- Equipment catalogs, purchasing, recipes, machines, UI, commands, economy, commercial properties, warranties, service records, supplier relationships, and gameplay effects remain out of scope.

## DEC-0057: Commercial Properties Are Permanent Locations Separate From Businesses

Status: Accepted

Decision: version 0.9.0 Phase 4 introduces commercial properties as immutable world identity records saved inside the existing World Identity snapshot. A commercial property represents a permanent location with construction history, utility infrastructure, expansion capacity, condition, status, and ownership history. Businesses are not modeled in this phase; historical owner names are property records only and do not create business entities.

Rationale: future purchases, inheritance, renovations, inspections, newspaper articles, supplier routes, insurance, tax records, historical preservation, and business valuation all need stable property identities before business gameplay depends on locations. Keeping properties separate from businesses allows different businesses, vacancies, and future player ownership to occupy the same location over time without replacing the property record.

Consequences:

- World Identity schema version 3 stores commercial properties alongside regions, counties, and settlements.
- Phase 1 and Phase 2 development saves migrate by preserving existing identity data and generating commercial properties from the saved world seed and settlements.
- Property generation uses stable settlement ids and fixed property slots rather than mutable random streams or iteration order.
- `CommercialPropertyRegistry` validates settlement references, duplicate ids, and duplicate names within each settlement.
- Property types, condition, status, acquisition method, electrical service, and refrigeration capacity are strongly typed.
- No business entities, player ownership, purchasing, economy, village structures, building placement, inspections, taxes, UI, commands, NPCs, progression, or gameplay effects are introduced.

## DEC-0058: Businesses Are Organizations Separate From Properties And Owners

Status: Accepted

Decision: version 0.9.0 Phase 5 introduces businesses as immutable world identity records saved inside the existing World Identity snapshot. A business represents a legal and commercial organization with type, status, reputation, founding year, occupancy history, ownership metadata, manufacturer-reference placeholders, and lineage placeholders. A commercial property remains the permanent physical place, and an owner remains the individual, family, cooperative, bank, estate, or company controlling the business record.

Rationale: future inheritance, purchasing, employees, customers, inspections, supplier contracts, finances, newspapers, mergers, bankruptcy, rebranding, and family legacy starts need stable business identity before gameplay systems depend on commercial organizations. Keeping businesses separate from properties allows a property to host many businesses over time, and keeping owners separate from businesses allows ownership history to evolve without replacing the organization.

Consequences:

- World Identity schema version 4 stores businesses alongside regions, counties, settlements, and commercial properties.
- Phase 1, Phase 2, and Phase 3 development saves migrate by preserving existing identity data and generating businesses from the saved world seed and property snapshot.
- Business generation uses stable commercial property ids and deterministic salts rather than mutable random streams or iteration order.
- `BusinessRegistry` validates property references, settlement references, region references, manufacturer references, duplicate ids, and duplicate business names within each settlement.
- Business type, status, reputation, occupancy reason, ownership type, and relationship type are strongly typed.
- No player-owned businesses, purchasing, economy, money, employees, NPC interaction, UI, commands, recipes, machine ownership, property purchasing, retail customers, progression, physical buildings, inspections, taxes, newspapers, or gameplay effects are introduced.

## DEC-0059: Ownership Entities Control Businesses Without Becoming Businesses

Status: Accepted

Decision: version 0.9.0 Phase 6 introduces ownership entities, families, historical persons, ownership shares, and ownership histories as immutable world identity records saved inside the existing World Identity snapshot. An ownership entity represents the individual, family, partnership, cooperative, corporation, estate, or municipality that controls a business for a period of time. Ownership entities reference businesses, businesses reference commercial properties, and commercial properties remain independent physical locations.

Rationale: future inheritance, wills, estate transfers, partnerships, business sales, succession, shareholder models, newspapers, inspections, finance, and legacy gameplay need stable owner identity before gameplay systems depend on business control. Keeping owners separate from businesses allows ownership to change over time without replacing the business identity, and keeping businesses separate from properties allows a property to outlive multiple organizations.

Consequences:

- World Identity schema version 5 stores families, historical persons, ownership entities, and ownership histories alongside regions, counties, settlements, commercial properties, and businesses.
- Phase 1 through Phase 4 development saves migrate by preserving existing identity data and generating ownership records from the saved world seed and business snapshot.
- Ownership generation uses stable business ids and deterministic salts rather than mutable random streams or iteration order.
- `FamilyRegistry` validates family and historical person references, and `OwnershipRegistry` validates business references, owner references, orphaned owners, duplicate histories, timeline overlap, and ownership share totals.
- Ownership categories, acquisition methods, and family reputations are strongly typed.
- No NPCs, player families, inheritance gameplay, dialogue, marriage, children, AI, economy, payroll, lawsuits, politics, UI, commands, quests, active relationships, physical entities, or gameplay effects are introduced.

## DEC-0060: Supply Chains Are Historical Relationships, Not Gameplay

Status: Accepted

Decision: version 0.9.0 Phase 7 introduces supply networks as immutable world identity records saved inside the existing World Identity snapshot. A supply network represents historical commercial relationships among businesses, manufacturers, trade territories, distribution routes, preferred suppliers, preferred manufacturers, and business specializations. Supply relationships reference businesses; they do not own inventory, prices, deliveries, or active purchasing behavior.

Rationale: future purchasing, contracts, inventory, transportation, inspections, reputation, market pressure, recalls, and regional pricing need a stable commercial ecosystem before gameplay systems depend on suppliers. Keeping the supply network separate from commercial properties, businesses, and ownership entities preserves the core identity model: locations are places, businesses are organizations, ownership entities control organizations, and supply networks describe relationships among them.

Consequences:

- World Identity schema version 6 stores the supply network alongside regions, counties, settlements, commercial properties, businesses, and ownership records.
- Phase 1 through Phase 5 development saves migrate by preserving existing identity data and generating supply network records from the saved world seed and preserved business or ownership snapshot.
- Supply network generation uses stable business ids, settlement ids, region ids, manufacturer ids, and deterministic salts rather than mutable random streams or registry order.
- `TradeNetworkRegistry` validates business references, manufacturer references, territory references, product categories, chronology, duplicate ids, duplicate relationships, missing contracts, missing preferred-supplier records, orphaned territories, orphaned trade regions, and specialization coverage.
- Supply relationship categories, product categories, business specializations, and relationship strengths are strongly typed.
- No economy, pricing, money, inventory, purchasing, transportation simulation, trucking, vehicles, pathfinding, AI, NPC behavior, player interaction, UI, commands, recipes, machine behavior, shortages, recalls, seasonal variation, or dynamic markets are introduced.

## DEC-0061: The Player Enters An Existing World

Status: Accepted

Decision: version 0.9.0 Phase 8 introduces player legacy as immutable pure Java architecture outside the saved World Identity snapshot. Player legacy templates define career profiles, starting scenarios, starting assets, starting relationships, inheritance placeholders, background summaries, and initial legacy metadata. Player identities may reference existing simulation entities, but world identity, business, ownership, and supply-network systems do not reference player-specific data.

Rationale: future inheritance, purchases, career progression, inspections, finances, employees, customers, reputation, newspapers, retirement, succession, and multi-generation gameplay need the player to feel like a participant entering an existing industry. Keeping player legacy out of World Identity persistence until actual player save data is scheduled prevents the player from becoming the source of world generation or reshaping established simulation history.

Consequences:

- World Identity remains schema version 6; Phase 8 does not add player identity data to the existing world save.
- Built-in starting scenario templates use stable ids and placeholder references for future player-save systems.
- `PlayerRegistry` validates scenario ids, scenario type coverage, career profile coverage, inheritance records, placeholder references, player scenario references, career compatibility, and starting settlement references.
- Player career profiles, starting scenario types, acquisition types, starting relationship types, and initial reputation categories are strongly typed.
- No character creation, UI, networking, economy, inventory, machines, NPCs, quests, dialogue, commands, gameplay, progression systems, player save data generation, finances, equipment ownership, employees, customers, inspections, retirement, succession, or multi-generation gameplay is introduced.

## DEC-0062: Runtime Player Identities Are Separate Mutable World Participants

Status: Accepted

Decision: version 0.9.0 Phase 9 introduces runtime player identity persistence outside the immutable World Identity snapshot. A player identity maps a Minecraft UUID to a stable ButcherCraft player identity id, starting scenario, career profile, settlement reference, optional property/business/ownership/family references, deterministic creation timestamp, and player identity schema version. The runtime player identity registry is loaded, validated, updated, and saved independently at `<world>/butchercraft/player_identities.json`.

Rationale: players are mutable participants in the world, while World Identity is immutable generated history. Future gameplay systems need a persistent player anchor for business management, employees, production, economy, reputation, and progression without rewriting the generated world snapshot or making the player the source of world history.

Consequences:

- World Identity remains schema version 6 and does not store player identity records.
- Player identity persistence starts at schema version 1 and rejects unsupported schemas until explicit migrations are added.
- Player identity generation is deterministic from world seed plus Minecraft UUID.
- Player identity records store references to settlements, properties, businesses, ownership entities, and families rather than duplicating the referenced world data.
- Player login creates a runtime identity once, then reuses the persisted record on later joins.
- No economy, production, machines, inventory, employees, NPC AI, contracts, progression, skills, money, orders, reputation changes, business simulation, rendering, UI, commands, or gameplay effects are introduced.

## DEC-0063: ButcherCraft Simulation Time Is Not Minecraft Time-Of-Day

Status: Accepted

Decision: version 0.9.0 Phase 10 introduces `SimulationClock` as the authoritative source of ButcherCraft simulated world time. Minecraft server ticks provide execution cadence, but business simulation time is represented by configurable simulation ticks, minutes, hours, days, weeks, months, years, weekdays, and seasons. Future systems must schedule work through `SimulationScheduler` and observe events through `SimulationEventBus` rather than implementing independent timing models.

Rationale: production, employees, inspections, deliveries, business hours, maintenance, refrigeration, economy, and reputation all need shared deterministic time. Keeping simulated time separate from Minecraft time-of-day prevents client frame rate, rendering, sleep behavior, or unrelated Minecraft mechanics from becoming accidental business simulation rules.

Consequences:

- Simulation state persists independently at `<world>/butchercraft/simulation_state.json`.
- World Identity remains schema version 6 and Player Identity remains schema version 1.
- Simulation persistence starts at schema version 1 and rejects unsupported schemas until explicit migrations are added.
- Built-in daily, weekly, monthly, and yearly rollover events are infrastructure events only.
- The clock publishes events through `SimulationEventBus`; it does not directly invoke gameplay systems.
- No production, economy, machines, workers, NPC AI, inspections, refrigeration, maintenance, reputation, business operations, GUI, commands, gameplay events, or gameplay effects are introduced.

## DEC-0064: Business Runtime State Is Separate From Business Identity

Status: Accepted

Decision: version 0.9.0 Phase 11 introduces mutable business runtime state outside the immutable World Identity snapshot. Runtime records reference immutable businesses by `BusinessId`, store operational status, open/closed state, business hours, shift schedule, workforce capacity, active workforce, maintenance flag, last state-change simulation tick, and schema version, and persist independently at `<world>/butchercraft/business_runtime.json`.

Rationale: future employees, production, inspections, deliveries, refrigeration, reputation, and economy systems need businesses that can change over simulated time. Keeping runtime state separate preserves generated business history while allowing server-authoritative operations to evolve without rewriting World Identity or duplicating business records.

Consequences:

- World Identity remains schema version 6 and does not store mutable business runtime state.
- Business runtime persistence starts at schema version 1 and rejects unsupported schemas until explicit migrations are added.
- Business runtime records store references to businesses rather than duplicating display names, ownership, properties, settlements, or historical summaries.
- Business runtime subscribes to daily and weekly simulation rollover events and never owns a separate clock.
- The pure runtime package remains Minecraft-independent; only `com.butchercraft.world.BusinessRuntimeService` performs server lifecycle integration.
- No employees, production, machines, economy, payroll, inspections, AI, inventory, orders, customers, transportation, maintenance gameplay, GUI, networking, or gameplay effects are introduced.

## DEC-0065: Workforce Definitions Describe Jobs, Not Workers

Status: Accepted

Decision: version 0.9.0 Phase 12 introduces immutable workforce definitions outside Business Identity and Business Runtime. A workforce definition references one business by `BusinessId`, stores positions, shift assignments, staffing rules, skill levels, certification requirements, and schema version, and persists independently at `<world>/butchercraft/workforce_definitions.json`.

Rationale: future employees, AI, scheduling, production, payroll, and automation need a stable organizational structure before any worker identity exists. Keeping workforce definitions separate from employees prevents the framework from prematurely modeling people, payroll, productivity, or gameplay outcomes.

Consequences:

- World Identity remains schema version 6 and does not store workforce definitions.
- Business Runtime remains schema version 1 and does not store position catalogs.
- Workforce persistence starts at schema version 1 and rejects unsupported schemas until explicit migrations are added.
- Workforce records store `BusinessId` and shift ids rather than duplicating business identity or runtime state.
- Current Business Runtime shift ids can be used to resolve required positions, but no worker occupancy, hiring, payroll, production, AI, or gameplay effect is introduced.
- The pure workforce package remains Minecraft-independent; only `com.butchercraft.world.WorkforceService` performs server lifecycle integration.

## DEC-0066: ButcherCraft Core Is A Regional Simulation Platform

Status: Accepted

Decision: version 0.9.0 Phase 13 defines ButcherCraft Core as a deterministic regional world simulation engine. Industry modules participate in shared identity, simulation time, business, workforce, persistence, and future economic contracts. Meat Processing remains the flagship implementation rather than the sole architectural purpose of the project. Players, future NPCs, and compatible mods are participants in an existing world and do not become the source of world identity.

Rationale: the implemented World Identity, regional geography, manufacturers, properties, businesses, ownership, supply relationships, player identity, Simulation Clock, Business Runtime, and Workforce foundations already describe a general living region. Constraining those systems to one industry would duplicate clocks, persistence, identity, and future economy behavior when other industries or compatible mods are added.

Consequences:

- `VISION.md`, `CORE_PRINCIPLES.md`, `MODULES.md`, `SIMULATION_MODEL.md`, `ECONOMY_MODEL.md`, `COMPATIBILITY.md`, and `ROADMAP.md` are the active platform planning documents.
- `PROJECT_VISION.md`, `GAMEPLAY_DESIGN.md`, and `MODULE_PLAN.md` preserve the flagship Meat Processing experience and historical expansion rationale.
- Core owns shared regional identity, simulation services, persistence foundations, and future cross-industry contracts.
- Industry modules own industry-specific products, transformations, equipment, operating rules, and presentation.
- Compatibility modules translate external capabilities into shared contracts while preserving source-mod ownership.
- A future industry must not introduce a parallel world identity, simulation clock, business identity, economy, or save foundation.
- Public APIs remain unimplemented until a real module or compatibility consumer proves the smallest stable contract.
- Existing package names, public registry ids, schemas, save files, assets, datapacks, and gameplay behavior remain unchanged.
- Phase 13 implements no economy, production, employees, AI, logistics, markets, pricing, transportation, utilities, compatibility adapter, or gameplay system.

## DEC-0067: Economic Goods Are Independent From ItemStacks And Processing Products

Status: Accepted

Decision: version 0.9.0 Phase 14 introduces `com.butchercraft.world.goods` as the immutable economic language for commodities, manufactured products, and directed transformation relationships. Economic goods use stable `GoodId` values and typed metadata. They do not store quantities and do not depend on Minecraft ItemStacks. The economic `ProductDefinition` remains separate from the existing `com.butchercraft.product.definition.ProductDefinition` used by processing content.

Rationale: every future industry, market, warehouse, producer, consumer, logistics service, and compatibility module needs a common identity for what moves through the economy. Making ItemStacks or the existing meat-processing product schema the economic authority would couple regional simulation to Minecraft inventory representation and industry-specific content concerns.

Consequences:

- Every economic good is exactly one commodity or product.
- `GoodRegistry` is an immutable deterministic snapshot and validates known industries, references, duplicates, and cycles.
- `GoodTransformation` records relationships and exact yield metadata only; it does not execute production.
- Economic definitions persist independently at `<world>/butchercraft/goods.json` with schema version 1.
- Informational item mappings use pure identifier metadata and do not resolve or register Minecraft items.
- Existing product definitions, datapack content, transformations, ItemStack adapters, and workstation behavior are unchanged.
- No built-in economic goods are registered in Phase 14; a future mapping or migration milestone must resolve overlap with existing processing products deliberately.
- Inventory, quantities, warehousing, prices, markets, orders, production, transportation, utilities, networking, GUI, and gameplay remain out of scope.

## DEC-0068: Economic Actors Define Participants Without Owning Economic Behavior

Status: Accepted

Decision: version 0.9.0 Phase 15 introduces `com.butchercraft.world.economy.actor` as the universal participant model for future economic systems. Immutable actor definitions identify a participant's type, primary industry, capabilities, Good relationships, supported industries, and optional dependency metadata. Mutable actor runtime state is separate, references Business Runtime and Workforce through stable ids, and is not persisted with actor definitions.

Rationale: producers, consumers, warehouses, carriers, markets, utilities, services, compatible mods, NPC organizations, and player businesses need one industry-neutral identity and capability contract. Making ItemStacks, block entities, businesses, or industry-specific machines the participant authority would fragment the regional economy and couple economic simulation to gameplay representation.

Consequences:

- Actors relate to economic goods only through `GoodId`; the actor package does not import Minecraft, NeoForge, or ItemStack APIs.
- `EconomicActorRegistry` is immutable and deterministic and validates known industries, known goods, dependencies, duplicate relationships, and dependency cycles.
- Relationship metadata may describe supported industries but does not execute production, consumption, storage, or transportation.
- `EconomicActorRuntime` stores only status, enabled/operational flags, optional stable Business Runtime and Workforce references, and the last simulation tick.
- Immutable definitions persist independently at `<world>/butchercraft/economic_actors.json` with schema version 1; runtime state is not persisted in Phase 15.
- `EconomicActorService` depends on `GoodService`, preserving the direction Goods -> Actors.
- No built-in actors are registered in Phase 15.
- Inventory, quantities, warehousing behavior, orders, contracts, scheduling, pricing, markets, production, AI, transportation, logistics, employment, networking, GUI, and gameplay remain out of scope.

## DEC-0069: Economic Inventory Is Independent From Minecraft Inventory

Status: Accepted

Decision: version 0.9.0 Phase 16 introduces `com.butchercraft.world.inventory` as the universal runtime ownership and location model for economic Goods. Economic Actors own immutable inventory-container identities, containers reference immutable hierarchical Storage Nodes, and separate mutable runtime records store exact quantities by `GoodId` and canonical `UnitOfMeasure`. Minecraft inventories, containers, slots, ItemStacks, and menus are not part of this domain.

Rationale: regional warehouses, coolers, vehicles, retailers, processors, utilities, player businesses, and compatibility modules need quantities that exist independently from loaded chunks and Minecraft item representation. Using block-entity slots as the economic authority would couple world simulation to gameplay containers, prevent aggregate off-screen simulation, and fragment ownership across industries.

Consequences:

- Inventory ownership references Economic Actors by `ActorId`; entries reference Goods by `GoodId`.
- Storage Nodes provide nested physical-location and capacity metadata without implementing logistics or environmental simulation.
- `InventoryRegistry` is immutable and deterministic; `InventoryManager` owns runtime records and validated quantity updates.
- Capacity validation covers containers and aggregate ancestor-node usage with deterministic typed unit conversion.
- Proposed movements validate atomic source-removal and target-addition candidates but do not execute transfers.
- Inventory definitions and runtime quantities persist at `<world>/butchercraft/inventory.json` with schema version 1.
- Typed lot, expiration, quality, and origin metadata is persisted but has no Phase 16 behavior.
- `InventoryService` depends on `EconomicActorService`, preserving Goods -> Actors -> Inventory dependency direction.
- No built-in inventories or storage nodes are registered in Phase 16.
- Production, spoilage, logistics, transportation, orders, contracts, scheduling, pricing, markets, AI, automation, networking, GUI, ItemStack conversion, and gameplay remain out of scope.

## DEC-0070: Economic Runtime Mutations Use Validated Transactions

Status: Accepted

Decision: version 0.9.0 Phase 17 introduces `com.butchercraft.world.transaction` as the universal mutation pipeline for runtime economic quantities. Future systems submit immutable transaction definitions, validation produces an accepted deterministic change plan, and `TransactionExecutor` is the only holder of the authority required to apply inventory quantity changes.

Rationale: direct inventory mutation would scatter validation, atomicity, audit, replay, debugging, and multiplayer-ordering rules across production, logistics, retail, compatibility, and player systems. One explicit pipeline gives every future cause of change the same state-transition contract without making the transaction framework responsible for business decisions.

Consequences:

- `InventoryManager` remains the invariant-owning execution target but no longer exposes direct public add/remove methods.
- Inventory runtime access returns defensive snapshots; mutable runtime records remain internally owned.
- Phase 17 executes inventory add, remove, transfer, and direction-explicit adjustment transactions only.
- Production, purchase, sale, delivery, consumption, spoilage, manual, and system transaction values are schema reservations and have no Phase 17 execution behavior.
- Execution requires a matching previously accepted validation and applies all inventory changes atomically.
- Structurally valid submitted transactions retain deterministic audit history in authoritative submission order.
- Applied history can replay into an explicitly supplied compatible baseline; automatic startup reconstruction is not implemented.
- Transaction history persists independently at `<world>/butchercraft/transactions.json` with schema version 1.
- `TransactionService` depends on `InventoryService`, preserving Goods -> Actors -> Inventory -> Transactions dependency direction.
- Rollback, production, markets, pricing, accounting, orders, logistics, scheduling, AI, networking, GUI, ItemStack conversion, and gameplay remain out of scope.

## DEC-0071: Orders Express Intent And Contracts Govern Obligations

Status: Accepted

Decision: version 0.9.0 Phase 18 introduces `com.butchercraft.world.economy.order` as the industry-neutral owner of economic intent and durable obligation. Orders request outcomes, Contracts govern zero or more Orders, Transactions remain authoritative mutation facts, and Inventory remains authoritative current quantity. Orders and Contracts never mutate Inventory or submit Transactions.

Rationale: production, logistics, markets, retailers, warehouses, utilities, and compatibility modules need one deterministic vocabulary for requests and obligations without gaining access to another subsystem's mutable state. Keeping definitions immutable, lifecycle runtime separate, and fulfillment linked to completed Transactions prevents Orders from becoming a second inventory or transaction engine.

Consequences:

- Authoritative Orders begin at `SUBMITTED`; draft editing remains outside persisted world state.
- Stable Order, line, Contract, and Contract-line ids use the existing canonical namespaced identity pattern.
- `GoodQuantity` uses normalized exact decimal values with canonical persistence; schema version 1 performs no unit conversion.
- Fulfillment recording accepts only APPLIED Transactions, validates Good, unit, quantity, tick, duplicate, and aggregate allocation rules, and stages multi-line/multi-Order operations atomically.
- One Transaction may explicitly contribute to multiple lines or Orders, but its total allocation cannot exceed its authoritative quantity.
- Immutable registries preserve authoritative insertion order while managers separately own runtime lifecycles and expose defensive snapshots.
- Contract schedules, priorities, commitment periods, substitutions, and maximum-open-order terms are metadata only and execute no obligations.
- Definitions and runtime state persist independently at `<world>/butchercraft/orders.json` and `<world>/butchercraft/contracts.json`, both schema version 1.
- `OrderContractService` initializes after `TransactionService` and publishes managers only after coordinated Actor, Good, Inventory, Transaction, Order, and Contract reference validation succeeds.
- No pricing, currency, accounting, reservations, markets, production, logistics, automatic scheduling, AI, networking, GUI, ItemStack integration, or gameplay is added.

## DEC-0072: One Deterministic Pipeline Orchestrates Simulation Work

Status: Accepted

Decision: version 0.9.0 Phase 19 introduces `com.butchercraft.world.simulation.scheduler` as the single industry-neutral owner of scheduled simulation Work eligibility, lifecycle, ordering, and bounded execution. The authoritative `SimulationClock` remains the sole owner of time. Immutable Work definitions are separated from manager-owned runtime state, and only the manager assigns persisted monotonic submission sequences. Six broad stages execute in stable explicit order. Handlers are runtime behavior, return typed outcomes, declare side-effect contracts, and never receive mutable scheduler internals.

Rationale: future Contract evaluation, production, logistics, markets, population, maintenance, and compatibility systems need one deterministic orchestration boundary. Independent subsystem tick handlers would duplicate scheduling, weaken ordering and failure guarantees, and encourage unbounded or hidden background mutation. A pure scheduler can define when Work runs without deciding the economic or gameplay meaning of that Work.

Consequences:

- The scheduler receives authoritative simulation ticks and never advances time or uses wall-clock scheduling for outcomes.
- Schema 1 requires exactly sequential ticks; duplicate, backward, and skipped ticks fail visibly because automatic catch-up and resume are not implemented.
- Execution order is stage, scheduled tick, descending priority, submission sequence, then Work id.
- Positive item, stage, work-unit, generation, same-tick, retry, and depth budgets preserve unexecuted Work for later ticks.
- Same-tick generated batches are atomic and may execute only in a later unstarted stage that explicitly allows them.
- Work definitions remain immutable; runtime transitions are explicit and terminal states are irreversible.
- Retry delays use simulation ticks only and contain no jitter or hidden randomness.
- Handler exceptions and invalid results become explicit failures governed by stage policy; the scheduler does not claim rollback of arbitrary external side effects.
- Unknown persisted Work types and persisted `RUNNING` state reject initialization instead of disappearing or rerunning silently.
- Scheduler state persists independently at `<world>/butchercraft/simulation_scheduler.json` with schema version 1.
- `SimulationSchedulerService` initializes after `OrderContractService`, executes after the clock's post-tick listener, and registers no live handlers or Work in Phase 19.
- The pipeline performs no Inventory mutation, Transaction submission, Contract evaluation, production, logistics, markets, population, pricing, networking, GUI, ItemStack integration, or gameplay behavior.
- The package remains internal. No public handler API or third-party registration lifecycle is established.

## DEC-0073: Production Is Scheduled Intent Completed By One Economic Transaction

Status: Accepted

Constitutional basis: `AI-0001`, `AI-0003` through `AI-0010`, `AI-0012` through `AI-0018`, `AI-0020` through `AI-0028`.

Decision: version 0.9.0 Phase 20 introduces `com.butchercraft.world.production` as the industry-neutral Core owner of executable Production definitions and runtime lifecycle. A `GoodTransformation` remains a descriptive economic relationship. A `ProductionProcessDefinition` describes reusable executable operations, a `ProductionPlanDefinition` is immutable authoritative intent with inventory bindings and scale, and a `ProductionRunRuntime` is the separately owned mutable execution record. The Simulation Scheduler determines eligibility, the Simulation Clock remains the sole time authority, and every completed Run commits all consumed inputs and produced outputs through one atomic APPLIED `PRODUCTION` Economic Transaction.

Rationale: future industries need a common operational contract without duplicating time, Inventory mutation, Goods, business status, workforce requirements, Orders, or transaction history. Separating definition, intent, and runtime preserves singular ownership and deterministic persistence. Revalidating requirements and Inventory state at execution and completion is necessary because schema 1 deliberately introduces no reservation ledger.

Consequences:

- Core Production remains industry-neutral and registers no live industry Process definitions in Phase 20.
- Processes reference Goods by `GoodId`; they do not embed or rewrite Good definitions.
- Plans are immutable intent, reserve no Inventory, and bind Process lines to existing actor-owned inventories.
- One schema-1 Plan owns exactly one mutable Run; terminal Run states are irreversible.
- Scheduler Work controls execution eligibility only. Run progress belongs to Production and advances only from supplied authoritative simulation ticks.
- Production never mutates Inventory directly. Completion requires one explicit ordered multi-change plan accepted and atomically applied by the Transaction Framework.
- A Run becomes completed only after its completion Transaction is present in authoritative history with `APPLIED` status.
- Requirements are checked against the external Business Runtime and Workforce authorities; Production stores only stable references and policy outcomes.
- Order and Contract references provide optional context only. Production does not reserve, allocate, or record Order fulfillment.
- Deterministic whole-batch quantities and yields contain no hidden randomness in schema 1.
- Processes, Plans, and Runs persist independently in three schema-versioned files and publish only after complete-set validation. Filesystem replacement is per file; schema 1 does not claim an atomic three-file filesystem transaction.
- `EconomicTransaction` gains an optional additive `inventory_changes` field. Existing schema-1 records without that field remain loadable and preserve their prior behavior.
- Machine integration, workstation migration, datapack Process loading, and industry gameplay remain future work.

## DEC-0074: Economic Planning Owns Decisions But Never Execution

Status: Accepted

Constitutional basis: `AI-0001`, `AI-0003` through `AI-0018`, `AI-0020` through `AI-0028`.

Decision: version 0.9.0 Phase 21 introduces `com.butchercraft.world.planning` as the pure Java Core owner of deterministic economic observations, Needs, Constraints, Opportunities, Candidate Plans, Approved Plans, and Planning Cycle runtime. Schema 1 plans business-scale Production only. Planning reads immutable facts from authoritative services, selects bounded intent with exact cycle-local capacity claims, and submits executable approvals through a typed Production adapter. It never advances time, reserves or mutates Inventory, submits economic Transactions, fulfills Orders, executes Production, or owns Scheduler runtime.

Rationale: automated regional behavior needs an explainable decision layer between economic facts and operational execution. Putting selection inside Orders, Production, Inventory, or Scheduler would give those systems conflicting responsibilities and make deterministic replay difficult. Keeping Planning artifacts immutable and separately persisted makes decisions inspectable while reusing existing execution authorities.

Consequences:

- One `butchercraft:economic_planning_cycle` Work runs in the existing PLANNING stage and defers to the next authoritative simulation tick; no second clock or independent tick loop exists.
- Open accepted Order lines are the only schema-1 Need source. Existing active Production commitments linked to the same Order and output Good are subtracted before a Need is emitted.
- Later Planning phases consume captured immutable observations and minimal immutable Opportunity process parameters rather than re-reading mutable registries.
- Need and Candidate comparator chains are explicit. Exact `GoodQuantity`, whole-batch arithmetic, stable ids, and bounded ordered iteration contain no random or floating-point tie breaking.
- Selection uses detached cycle-local Opportunity and input-capacity ledgers. These claims prevent over-allocation within one cycle but are not durable Inventory reservations.
- Approved Production intent is submitted only through `ProductionPlanningSubmissionAdapter`. Production atomically registers and schedules an identical Plan, returns existing identical state on replay, rejects identity conflicts, and removes a new unscheduled Plan if Scheduler rejects it.
- Planning persistence is independently owned at `<world>/butchercraft/planning_observations.json`, `planning_needs.json`, `planning_opportunities.json`, `planning_candidates.json`, `planning_approved_plans.json`, and `planning_runtime.json`.
- All six files must be present together. Terminal cycle structure, provenance, graph integrity, and external Goods, Orders, Production, Actor, Business, Inventory, and Scheduler references are validated before publication.
- Persisted interrupted cycles fail initialization visibly. Automatic partial-cycle resume or replay is not claimed in schema 1.
- Inventory reservations, purchasing, logistics, maintenance, utilities, inspections, markets, pricing, accounting, public provider registration, gameplay, networking, GUI, and ItemStack integration remain future work.

## DEC-0075: Architecture Validation Uses Explicit Immutable Manifests

Status: Accepted

Constitutional basis: `AI-0001`, `AI-0003`, `AI-0009`, `AI-0010`,
`AI-0016` through `AI-0021`, `AI-0023`, `AI-0025`, `AI-0027`, and `AI-0028`.

Decision: BCSE Architecture Validation Framework Phase 1 introduces
`com.butchercraft.architecture.validation` as a pure Java validation layer over
explicit immutable architecture manifests. Rules are registered explicitly,
ordered canonically, and produce structured deterministic results. The
framework uses no reflection, runtime scanning, hidden clocks, random sources,
or mutable global state. It observes accepted ownership, dependency, registry,
persistence, Scheduler, Transaction, Planning, Production, Execution, and
simulation contracts without changing them.

Rationale: constitutional and accepted architecture contracts need executable
regression protection, but runtime discovery would make ordering, coverage, and
failure behavior implicit. An explicit manifest makes the validated facts
reviewable and keeps the validator independent from subsystem managers and
platform lifecycle.

Consequences:

- `ButcherCraftArchitectureManifest` declares the accepted current architecture
  and is validated through automated tests.
- Validation descriptors and reports are immutable; candidate violations remain
  representable so rules can report them.
- Rule exceptions, null results, and inconsistent result metadata become
  explicit deterministic failures.
- Execution time is caller-supplied report metadata; the validator does not read
  a wall clock.
- Existing source dependency-boundary tests remain responsible for concrete
  package-text checks; the framework does not scan Java source or classpaths.
- Passing validation does not approve an ADR, accept a proposed RFC, migrate a
  schema, or transfer ownership.
- The Allocation category is additive only. RFC-0022 remains proposed and no
  Allocation implementation is authorized by this decision.
- No simulation, persistence, registry, Scheduler, Transaction, Planning,
  Production, gameplay, networking, asset, or workstation behavior changes.

## DEC-0076: M22A Establishes The Immutable Core Allocation Domain

Status: Accepted

Constitutional basis: `AI-0001`, `AI-0003`, `AI-0006`, `AI-0007`, `AI-0009`,
`AI-0010`, `AI-0016` through `AI-0021`, `AI-0023`, `AI-0025`, `AI-0027`, and
`AI-0028`.

Decision: owner-authorized RFC-0022 Revision 2 Milestone M22A introduces
`com.butchercraft.world.allocation` as a pure Java immutable structural domain.
Allocation owns canonical Requests, structurally atomic AllocationSets, and
immutable Commitment definitions. Authoritative providers continue to own
Resources and Capacity. Planning, Production, Scheduler, Transactions, and
Inventory retain their accepted authorities.

Rationale: later deterministic allocation requires one stable,
industry-neutral vocabulary before any provider, algorithm, runtime,
persistence, or integration is introduced. Establishing exact quantities,
external references, immutable observed facts, canonical ordering, and typed
validation first makes those later decisions reviewable without changing
simulation behavior.

Consequences:

- Stable Allocation identities use canonical namespaced values and
  deterministic structural hashing.
- Capacity quantities use exact bounded decimal arithmetic and open typed unit
  identities with no implicit conversion.
- Schema-1 Workforce observations represent aggregate position, role, or shift
  capacity, never individual workers.
- Allocation references execution work, Planning artifacts, Resources,
  Capacity, and observations only through stable external identities.
- The architecture manifest assigns Allocation Request, Set, and Commitment
  ownership and forbids M22A dependency directions toward Planning,
  Production, Scheduler, Inventory, and Transactions.
- M22A adds no algorithm, cycle runtime, manager, provider, persistence file,
  Scheduler stage 350, Work type, execution gate, Inventory mutation,
  Transaction execution, Minecraft integration, or gameplay.
- Domain codecs are deferred with persistence ownership. M22A exposes canonical
  primitive values, schema fields, and ordered collections without freezing an
  unapproved file or network contract.
- RFC-0022 remains only partially implemented. M22B through M22F require
  separate owner authorization and any compatibility decisions required by
  Part V.

## DEC-0077: M22B Establishes Allocation Runtime And Registry Ownership

Status: Accepted

Constitutional basis: `AI-0001`, `AI-0003`, `AI-0006`, `AI-0007`, `AI-0009`,
`AI-0010`, `AI-0016` through `AI-0021`, `AI-0023`, `AI-0025`, `AI-0027`, and
`AI-0028`.

Decision: owner-authorized RFC-0022 Revision 2 Milestone M22B adds
deterministic `AllocationSetRuntime` lifecycle state, immutable definition and
runtime registries, immutable report and history models, and detached query
surfaces. `AllocationRuntimeService` is the sole public lifecycle mutation
boundary. Runtime identity remains `AllocationSetId`; no duplicate
`AllocationRuntimeId` is introduced.

Rationale: later deterministic allocation needs explicit lifecycle,
cross-reference validation, canonical registries, audit history, and immutable
read contracts before an algorithm, provider, persistence owner, or Scheduler
integration can be reviewed. Separating these mechanics prevents future
selection policy from leaking into runtime ownership.

Consequences:

- The accepted lifecycle is REQUESTED, WAITING, ALLOCATED, ACTIVE, RELEASED,
  FAILED, and EXPIRED with only the RFC-0022 transitions and irreversible
  terminal states.
- Runtime transitions consume caller-supplied simulation ticks and revisions;
  no clock, randomness, background loop, or Scheduler callback is introduced.
- ALLOCATED structurally requires exactly one known Commitment per Requirement,
  but M22B never creates or selects a Commitment.
- Public registries, runtime views, history, reports, and query results are
  immutable and canonically ordered.
- Reports record outcome and evidence structures only. They do not implement
  conflict resolution, fairness, Capacity ledgers, or Allocation Cycles.
- The architecture manifest assigns lifecycle, registry, report, and history
  ownership and declares canonical Allocation registries while preserving all
  dependency prohibitions and adding no persistence or Scheduler stage.
- M22B adds no algorithm, provider, resource observation, persistence, stage
  350, Planning, Production, execution, Inventory, Transaction, Minecraft,
  NeoForge, command, menu, networking, or gameplay integration.
- RFC-0022 remains partially implemented. M22C through M22F require separate
  owner authorization and compatibility decisions required by Part V.

## Decisions Needing Owner Approval

- First basic meat product and input source.
- Default config preset strictness.
- Whether facility boundaries are explicit controller-based from the start or introduced after the vertical slice.
- Whether detailed refrigeration ships as part of ButcherCraft Core 1.0 or ButcherCraft Refrigeration.
- Quality UI presentation beyond the accepted engine score and grade boundaries.
- MCDA default severity for shutdowns and fines.
- Expansion release order.
