# ButcherCraft Grinder

Status: Milestone 2C/2D machine wrapper, data-driven grinding proof, v0.7.0 content-snapshot compatibility preserved, IM-014 Grinder gameplay promotion, IM-015 second promoted grinder process, and IM-017 six-product Grinder recipe expansion

## Purpose

The Grinder is the first named machine built on the generic processing workstation framework. It proves a final-named machine can process products without owning species, product, yield, quality, or operation-selection logic. Version 0.6.1 proves the Grinder can execute through the pure Java transformation engine without hardcoding species or product behavior into the machine. Version 0.6.2 makes the transformation registry the source of the Grinder transformation definitions. Version 0.6.3 keeps those definitions on the canonical transformation schema. Version 0.6.4 adds canonical product definitions for the Grinder product ids and validates transformation references separately. Version 0.6.5 proves those registered Grinder transformations can round-trip through the pure Java serialization contract without changing runtime behavior. Version 0.6.6 proves the same one-output Grinder definitions remain compatible with atomic transformation transactions. Version 0.6.7 keeps Grinder behavior on the existing transformation strategy while the Bandsaw migrates to the separate atomic transformation strategy. Version 0.6.8 loads the Grinder transformation definitions from datapack JSON resources. Version 0.6.9 loads the Grinder product definitions from datapack JSON and activates them with transformations as one content snapshot. Version 0.7.0 preserves that Grinder behavior while adding Bandsaw-only beef fabrication content. IM-014 promotes the Grinder, Beef Trim, and Ground Beef presentation for normal gameplay while preserving the existing registry ids for compatibility. IM-015 promotes Pork Trim to Ground Pork as the second player-facing Grinder flow. IM-017 expands the same resolver, transformation, Execution, Scheduler, Production observation, and owner-result path to six promoted trim-to-ground flows.

## Boundaries

- `GrinderBlock`, `GrinderBlockEntity`, `GrinderMenu`, and `GrinderScreen` remain thin machine-specific wrappers.
- The Grinder declares `butchercraft:grinding` through `GrinderWorkstation.capability()`.
- Operation selection still belongs to `WorkstationOperationResolver`, `ProcessingGraph`, and loaded definitions.
- Transaction preparation and completion still belong to `WorkstationProcessingController`.
- The Grinder uses `WorkstationExecutionStrategy.transformation()` so resolved operation ids are looked up in the immutable transformation registry, evaluated, and executed by the transformation engine before the existing processing transaction commits product results.
- Grinder operations are represented as one-element output lists in the shared multi-output operation model.
- Beef Trim, Ground Beef, Pork Trim, Ground Pork, Chicken Trim, Ground Chicken, Buffalo Trim, Ground Buffalo, Lamb Trim, Ground Lamb, Venison Trim, and Ground Venison are player-facing promoted content for the Grinder flow. Beef, Pork, and Buffalo retain existing legacy item or operation registry ids for compatibility where those ids already existed.
- The promoted Grinder Execution authorization set contains exactly `butchercraft:grind_beef`, `butchercraft:grind_pork`, `butchercraft:grind_chicken`, `butchercraft:grind_bison`, `butchercraft:grind_lamb`, and `butchercraft:grind_venison`.
- Other product output items still use the temporary development fixture mapping until a real product item factory is designed.

The Grinder must not switch on beef, pork, bison, poultry, or other species ids.

Canonical butcher-cut terminology currently affects Bandsaw fabrication definitions, not Grinder trim-to-ground flows. The Grinder continues reading product identity from definitions and product data only.

## Current Flows

The promoted Grinder definitions currently support:

```text
butchercraft:beef_trim -> butchercraft:grind_beef -> butchercraft:ground_beef
butchercraft:pork_trim -> butchercraft:grind_pork -> butchercraft:ground_pork
butchercraft:chicken_trim -> butchercraft:grind_chicken -> butchercraft:ground_chicken
butchercraft:bison_trim -> butchercraft:grind_bison -> butchercraft:ground_bison
butchercraft:lamb_trim -> butchercraft:grind_lamb -> butchercraft:ground_lamb
butchercraft:venison_trim -> butchercraft:grind_venison -> butchercraft:ground_venison
```

All six operations declare:

```text
workstation_capability: butchercraft:grinding
```

Each promoted operation runs for 60 server ticks. Chicken uses the `butchercraft:poultry` processing profile. Beef, Pork, Buffalo, Lamb, and Venison use the existing `butchercraft:red_meat` processing profile. Buffalo uses retained `butchercraft:bison_*` registry identities with player-facing Buffalo localization and presentation.

The Grinder is obtainable through a generated shaped crafting recipe, appears in the ButcherCraft creative tab, drops itself through its block loot table, and drops stored contents on removal. All promoted trim and ground products are currently obtainable through the ButcherCraft creative tab as the development-stage acquisition bridge. This bridge is not final upstream butchering progression.

## Verification Notes

Automated tests cover:

- Grinder capability id stability.
- Beef, Pork, Chicken, Buffalo, Lamb, and Venison trim resolving to their matching grind operations.
- Controller completion producing the matching ground product with `900 gram` and adjusted quality.
- Regression coverage showing Grinder execution rejects an operation when the workstation resolves by category but does not advertise the `butchercraft:grinding` transformation capability.
- Regression coverage showing Grinder execution rejects a resolved operation when no registered transformation definition exists.
- Pure validation coverage showing the built-in Grinder transformation product references resolve through the built-in product registry.
- Serialization coverage showing built-in Grinder transformations round-trip through the canonical pure Java serialization contract.
- Pure transaction coverage showing the built-in Grinder transformation can consume Beef Trim and produce Ground Beef through the atomic material-store executor path.
- Datapack resource coverage showing Grinder transformations load from JSON through the canonical deserializer.
- Source coverage showing the Grinder uses the original transformation strategy while the Bandsaw uses the separate atomic transformation strategy.
- Grinder and generic workstation source scans for species-specific branches.
- Generated operation JSON using `butchercraft:grinding`.
- Generated recipe JSON making the Grinder craftable.
- GameTest coverage for promoted Beef, Pork, Chicken, Buffalo, Lamb, and Venison trim-to-ground execution, process coexistence, deterministic lookup, unsupported input rejection, process isolation, visible menu-data progress, retained legacy item compatibility, save/load non-duplication, duplicate safety, blocked output, wrong-output prevention, and active block-break input preservation.

Manual verification should craft or obtain the Grinder, place it, insert each promoted Trim product in separate runs, observe 60-tick progress for each, confirm the matching Ground output, confirm wrong inputs and blocked output show visible status, and confirm breaking an idle or active Grinder does not duplicate output. IM-017 automated implementation does not claim a human acceptance pass unless a human tester completes it.
