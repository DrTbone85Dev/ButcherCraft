# ButcherCraft Product and Processing Definitions

Status: Milestones 2A through 2E definitions foundation

## Purpose

Milestone 2A moves product catalog and processing workflow facts into server-authoritative datapack definitions. The definitions describe species, broad processing profiles, product types, and processing operations that later stations can consume.

This milestone does not add machines, workstations, player-triggered processing, employees, refrigeration, cleanliness, MCDA inspections, commerce, menus, screens, final artwork, or live poultry content.

## Why Data-Driven

Definitions are loaded from custom datapack registries so ButcherCraft Core, future expansions, and datapacks can add or override content through stable resource identifiers. Java code validates and resolves references, but workflow selection is not hardcoded by species.

The pure engine remains Minecraft-independent. Datapack registry keys, `ResourceLocation`, `RegistryAccess`, and holder lookup code live outside `com.butchercraft.engine`.

## Registry IDs and Paths

Custom datapack registries:

| Concept | Registry id | Generated resource path pattern |
| --- | --- | --- |
| Species | `butchercraft:species` | `data/<namespace>/butchercraft/species/<id>.json` |
| Processing profile | `butchercraft:processing_profile` | `data/<namespace>/butchercraft/processing_profile/<id>.json` |
| Product | `butchercraft:product` | `data/<namespace>/butchercraft/product/<id>.json` |
| Processing operation | `butchercraft:processing_operation` | `data/<namespace>/butchercraft/processing_operation/<id>.json` |

Built-in ButcherCraft definitions therefore generate under paths such as:

```text
src/generated/resources/data/butchercraft/butchercraft/species/beef.json
src/generated/resources/data/butchercraft/butchercraft/processing_profile/red_meat.json
src/generated/resources/data/butchercraft/butchercraft/product/beef_trim.json
src/generated/resources/data/butchercraft/butchercraft/product/ground_beef.json
src/generated/resources/data/butchercraft/butchercraft/processing_operation/grind_beef.json
src/generated/resources/data/butchercraft/butchercraft/processing_operation/break_beef_forequarter.json
src/generated/resources/data/butchercraft/butchercraft/processing_operation/break_beef_hindquarter.json
```

## Definition Models

`SpeciesDefinition` stores display translation key, processing-profile reference, product-family id, edible/enabled flags, and bounded capability markers.

`ProcessingProfileDefinition` stores display translation key, profile category, allowed operation categories, required broad workflow stages, bounded compatibility markers, and whether cross-profile operations are permitted by default.

`ProductDefinition` stores display translation key, species reference, product category, processing state, allowed quantity unit, edible flag, bone state, spoilage eligibility, bounded traits, optional packaging metadata, and graph input/output flags.

`ProcessingOperationDefinition` stores display translation key, operation category, required processing profiles, input product reference, input state, ordered output definitions, base duration, minimum quantity, minimum cleanliness, minimum equipment condition, static modifiers, optional workstation capability, self-loop permission, and cross-species permission.

Each output definition stores product, state, exact yield numerator and denominator, quality adjustment, quantity unit, and zero-output policy. Single-output operations use the same `outputs` array with one entry.

## Resolver Behavior

`ProcessingDefinitionResolver` works from a reload-scoped `DefinitionRegistryView`. It can be built from `RegistryAccess` at the Minecraft boundary or from immutable maps in tests.

The resolver validates:

- Species to processing-profile references.
- Product to species references.
- Operation input and output product references.
- Operation required profile references.
- Input/output species compatibility.
- Required input state and output state.
- Output product state, quantity unit, duplicate output ids, and total yield limits.
- Operation category compatibility with the species processing profile.
- Minimum quantity unit compatibility.
- Forbidden zero-output operations.
- Self-loops unless explicitly permitted.

Failures return `DefinitionResolution` and `DefinitionValidationReport`; they do not return `null` or silently substitute fallback definitions.

## Graph Behavior

`ProcessingGraph` is a read-only view built explicitly from operation definitions. It supports deterministic lookup of operations by input product, one-step reachable outputs across every operation output, direct transformation checks, validation report access, duplicate operation-id detection for test or import contexts, and cycle reporting.

Cycles are warnings, not universal errors, because future rework operations may be legitimate. Self-loops remain errors unless the operation explicitly permits them.

## Validation Policy

Structural errors that can corrupt processing results are treated as errors and should fail datapack loading or world use once these definitions become gameplay-critical. Current validation reports are deterministic and developer-readable so malformed content does not degrade into unexplained null-pointer failures.

Warnings are reserved for non-corrupting design concerns such as graph cycles.

## ProductStackData Relationship

`ProductDefinition` describes a loaded product type. `ProductStackData` describes one actual ItemStack snapshot.

`ProductStackDefinitionValidator` compares stack data against loaded definitions without mutating the stack. It checks product existence, source/species compatibility, processing state, quantity unit, and quality bounds. Temperature, freshness, packaging, batch history, and order metadata remain deferred.

Optional product packaging metadata links a retail product definition to a packaging definition id and a source product id. This is descriptive graph data only; it does not mutate ItemStacks or execute packaging.

## Built-In Red-Meat Examples

The prototype dataset contains three red-meat grinding flows:

```text
butchercraft:beef_trim --butchercraft:grind_beef--> butchercraft:ground_beef
butchercraft:pork_trim --butchercraft:grind_pork--> butchercraft:ground_pork
butchercraft:bison_trim --butchercraft:grind_bison--> butchercraft:ground_bison
```

Prototype balance values:

- Duration: `3000` milliseconds.
- Yield: `9/10`.
- Base quality delta: `-5`.
- Minimum input quantity: `100 gram`.
- Minimum cleanliness factor: `600`.
- Minimum equipment condition factor: `500`.
- Workstation capability: `butchercraft:grinding`.
- Zero output: forbidden.

These values prove the data and graph model. They are not final balance.

The prototype dataset also contains red-meat fabrication flows for the Bandsaw:

```text
butchercraft:beef_forequarter --butchercraft:break_beef_forequarter--> [
  butchercraft:beef_chuck,
  butchercraft:beef_rib,
  butchercraft:beef_packer_brisket,
  butchercraft:beef_plate,
  butchercraft:beef_shank,
  butchercraft:beef_trim,
  butchercraft:beef_fat,
  butchercraft:beef_bone
]
```

Version 0.7.0 adds follow-on beef fabrication flows:

```text
butchercraft:beef_hindquarter --butchercraft:break_beef_hindquarter--> [
  butchercraft:beef_round,
  butchercraft:beef_sirloin,
  butchercraft:beef_short_loin,
  butchercraft:beef_flank,
  butchercraft:beef_trim,
  butchercraft:beef_fat,
  butchercraft:beef_bone
]

butchercraft:beef_short_loin --butchercraft:cut_beef_short_loin--> [
  butchercraft:t_bone_steak,
  butchercraft:porterhouse_steak,
  butchercraft:beef_strip_loin,
  butchercraft:beef_tenderloin,
  butchercraft:beef_trim,
  butchercraft:beef_bone
]

butchercraft:beef_round --butchercraft:cut_beef_round--> [
  butchercraft:top_round,
  butchercraft:bottom_round,
  butchercraft:eye_of_round,
  butchercraft:sirloin_tip,
  butchercraft:beef_trim,
  butchercraft:beef_fat,
  butchercraft:beef_bone
]

butchercraft:beef_sirloin --butchercraft:cut_beef_sirloin--> [
  butchercraft:top_sirloin,
  butchercraft:sirloin_steak,
  butchercraft:tri_tip,
  butchercraft:beef_trim,
  butchercraft:beef_fat,
  butchercraft:beef_bone
]
```

Prototype balance values:

- Duration: `6000` milliseconds.
- Minimum input quantity: `100000 gram`.
- Workstation capability: `butchercraft:bandsaw`.
- Ordered output yields: `30%`, `10%`, `10%`, `10%`, `5%`, `15%`, `5%`, and `10%`.
- Total output yield: `95%`, with the remaining `5%` representing process loss.
- Base quality delta: `-5` per output.
- Zero output: forbidden per output.

Multi-output allocation uses integer arithmetic and deterministic largest-remainder rounding. Ties are resolved by output order.

Sprint 2 adds a graph-only retail packaging flow:

```text
butchercraft:ground_beef --butchercraft:package_retail--> butchercraft:retail_ground_beef
```

Prototype values:

- Duration: `3000` milliseconds.
- Yield: `1/1`.
- Minimum input quantity: `100 gram`.
- Workstation capability: `butchercraft:packaging`.
- Zero output: forbidden.

`butchercraft:retail_ground_beef` declares packaging metadata referencing `butchercraft:retail_package` and `butchercraft:ground_beef`. The Packaging Table does not execute this operation in Sprint 2.

Sprint C adds physical packaging supply items and lets packaging definitions describe required supplies. This changes packaging content validation, not processing execution. `package_retail` remains graph-only, and no workstation consumes Foam Trays, Plastic Wrap Rolls, Vacuum Bags, paper rolls, or label rolls.

Milestones 2B through 2E and version 0.7.0 consume processing definitions through `WorkstationOperationResolver`. Sprint 2 adds `package_retail` to the graph, but no workstation consumes it yet. The resolver requires exactly one compatible operation for the inserted product and workstation capability before processing can begin. The Grinder and Bandsaw add no species-specific or cut-specific branches; they supply only their workstation capabilities.

## Canonical Butcher-Cut Terminology

ButcherCraft uses Midwestern butcher-counter terminology for player-facing cut names and stable product ids. The active Bandsaw prototype represents the whole brisket output as `butchercraft:beef_packer_brisket` with the display name "Packer Brisket".

Future cut definitions should use "Kansas City Strip Steak" instead of "New York Strip" when that strip-steak product exists, "Picanha" only for an anatomically correct sirloin-cap/top-sirloin-cap product, "Prime Rib" only for an intact rib roast, and "Denver Steak" only for the correct chuck underblade cut. Do not blanket-rename broader anatomical products into retail steak names.

## Poultry Extension Boundary

Poultry remains deferred content. Pork and bison are red-meat prototype species for proving data breadth, not full species catalogs or regulatory systems. The architecture supports a future poultry profile by associating workflow differences with data-driven species, processing profiles, operation categories, workstation capabilities, and later inspection profiles.

Java code must not switch on literal poultry species ids for workflow selection. Tests prove a hypothetical poultry profile can reject red-meat operations and accept poultry-profile operations without adding live poultry definitions.

## Reload and Dedicated Server Behavior

The custom registries are loaded from server datapacks and queried through current `RegistryAccess`. ButcherCraft does not cache `RegistryAccess` globally. Graph construction is explicit and bounded; it is not rebuilt every tick.

The diagnostic command reads the current server registry access and remains read-only. It does not expose filesystem paths or dump full registry contents.

## Explicit Exclusions

This milestone excludes live animal entities, slaughter interactions, carcass blocks or entities, poultry content or regulations, MCDA inspections, workstations, grinder blocks, menus, screens, player-triggered processing, employees, refrigeration, freshness, packaging, business accounts, customers, final textures, sounds, animations, and public stable expansion API guarantees. Milestone 2B adds the first temporary workstation after these definitions are in place.
