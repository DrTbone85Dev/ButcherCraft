# ButcherCraft Grinder

Status: Milestone 2C/2D machine wrapper, data-driven grinding proof, v0.7.0 content-snapshot compatibility preserved, IM-014 Grinder gameplay promotion, and IM-015 second promoted grinder process

## Purpose

The Grinder is the first named machine built on the generic processing workstation framework. It proves a final-named machine can process products without owning species, product, yield, quality, or operation-selection logic. Version 0.6.1 proves the Grinder can execute through the pure Java transformation engine without hardcoding species or product behavior into the machine. Version 0.6.2 makes the transformation registry the source of the Grinder transformation definitions. Version 0.6.3 keeps those definitions on the canonical transformation schema. Version 0.6.4 adds canonical product definitions for the Grinder product ids and validates transformation references separately. Version 0.6.5 proves those registered Grinder transformations can round-trip through the pure Java serialization contract without changing runtime behavior. Version 0.6.6 proves the same one-output Grinder definitions remain compatible with atomic transformation transactions. Version 0.6.7 keeps Grinder behavior on the existing transformation strategy while the Bandsaw migrates to the separate atomic transformation strategy. Version 0.6.8 loads the Grinder transformation definitions from datapack JSON resources. Version 0.6.9 loads the Grinder product definitions from datapack JSON and activates them with transformations as one content snapshot. Version 0.7.0 preserves that Grinder behavior while adding Bandsaw-only beef fabrication content. IM-014 promotes the Grinder, Beef Trim, and Ground Beef presentation for normal gameplay while preserving the existing registry ids for compatibility. IM-015 promotes Pork Trim to Ground Pork as the one additional player-facing Grinder flow, using the same resolver, transformation, Execution, Scheduler, and owner-result path as beef.

## Boundaries

- `GrinderBlock`, `GrinderBlockEntity`, `GrinderMenu`, and `GrinderScreen` remain thin machine-specific wrappers.
- The Grinder declares `butchercraft:grinding` through `GrinderWorkstation.capability()`.
- Operation selection still belongs to `WorkstationOperationResolver`, `ProcessingGraph`, and loaded definitions.
- Transaction preparation and completion still belong to `WorkstationProcessingController`.
- The Grinder uses `WorkstationExecutionStrategy.transformation()` so resolved operation ids are looked up in the immutable transformation registry, evaluated, and executed by the transformation engine before the existing processing transaction commits product results.
- Grinder operations are represented as one-element output lists in the shared multi-output operation model.
- Beef Trim, Ground Beef, Pork Trim, and Ground Pork are player-facing promoted content for the Grinder flow, but still use retained legacy registry ids for save compatibility.
- The promoted Grinder Execution authorization set contains `butchercraft:grind_beef` and `butchercraft:grind_pork`. Bison grinding remains prototype definition and fixture coverage, not a promoted live player-facing grinder process.
- Other product output items still use the temporary development fixture mapping until a real product item factory is designed.

The Grinder must not switch on beef, pork, bison, poultry, or other species ids.

Canonical butcher-cut terminology currently affects Bandsaw fabrication definitions, not Grinder trim-to-ground flows. The Grinder continues reading product identity from definitions and product data only.

## Current Flows

The built-in red-meat prototype definitions currently support:

```text
butchercraft:beef_trim -> butchercraft:grind_beef -> butchercraft:ground_beef
butchercraft:pork_trim -> butchercraft:grind_pork -> butchercraft:ground_pork
butchercraft:bison_trim -> butchercraft:grind_bison -> butchercraft:ground_bison
```

All three operations declare:

```text
workstation_capability: butchercraft:grinding
```

Beef Trim to Ground Beef and Pork Trim to Ground Pork are the promoted normal gameplay flows and each runs for 60 server ticks. Bison uses the existing `butchercraft:red_meat` processing profile as prototype data only, not a promoted gameplay flow or a full species catalog.

The Grinder is obtainable through a generated shaped crafting recipe, appears in the ButcherCraft creative tab, drops itself through its block loot table, and drops stored contents on removal. Beef Trim and Pork Trim are currently obtainable through the ButcherCraft creative tab as the development-stage acquisition bridge. This bridge is not final upstream butchering progression.

## Verification Notes

Automated tests cover:

- Grinder capability id stability.
- Beef, pork, and bison trim resolving to their matching grind operations.
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
- GameTest coverage for promoted Beef Trim to Ground Beef execution, promoted Pork Trim to Ground Pork execution, process isolation, visible menu-data progress, retained legacy item compatibility, save/load non-duplication, duplicate safety, and active block-break input preservation.

Manual verification should craft or obtain the Grinder, place it, insert Beef Trim and Pork Trim in separate runs, observe 60-tick progress for each, confirm Ground Beef and Ground Pork outputs, confirm wrong inputs and blocked output show visible status, and confirm breaking an idle or active Grinder does not duplicate output. IM-015 automated implementation does not claim the required human acceptance pass unless a human tester completes it.
