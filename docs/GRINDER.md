# ButcherCraft Grinder

Status: Milestone 2C/2D machine wrapper, data-driven grinding proof, and v0.6.7 compatibility preserved

## Purpose

The Grinder is the first named machine built on the generic processing workstation framework. It proves a final-named machine can process products without owning species, product, yield, quality, or operation-selection logic. Version 0.6.1 proves the Grinder can execute through the pure Java transformation engine without hardcoding species or product behavior into the machine. Version 0.6.2 makes the transformation registry the source of the Grinder transformation definitions. Version 0.6.3 keeps those definitions on the canonical transformation schema. Version 0.6.4 adds canonical product definitions for the Grinder product ids and validates transformation references separately. Version 0.6.5 proves those registered Grinder transformations can round-trip through the pure Java serialization contract without changing runtime behavior. Version 0.6.6 proves the same one-output Grinder definitions remain compatible with atomic transformation transactions. Version 0.6.7 keeps Grinder behavior on the existing transformation strategy while the Bandsaw migrates to the separate atomic transformation strategy.

## Boundaries

- `GrinderBlock`, `GrinderBlockEntity`, `GrinderMenu`, and `GrinderScreen` remain thin machine-specific wrappers.
- The Grinder declares `butchercraft:grinding` through `GrinderWorkstation.capability()`.
- Operation selection still belongs to `WorkstationOperationResolver`, `ProcessingGraph`, and loaded definitions.
- Transaction preparation and completion still belong to `WorkstationProcessingController`.
- The Grinder uses `WorkstationExecutionStrategy.transformation()` so resolved operation ids are looked up in the immutable transformation registry, evaluated, and executed by the transformation engine before the existing processing transaction commits product results.
- Grinder operations are represented as one-element output lists in the shared multi-output operation model.
- Product output items still use the temporary development fixture mapping until a real product item factory is designed.

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

Pork and bison use the existing `butchercraft:red_meat` processing profile for this milestone. They are prototype data only, not full species catalogs.

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
- Source coverage showing the Grinder uses the original transformation strategy while the Bandsaw uses the separate atomic transformation strategy.
- Grinder and generic workstation source scans for species-specific branches.
- Generated operation JSON using `butchercraft:grinding`.

Manual verification should insert Beef Trim, Pork Trim, and Bison Trim test products into the Grinder and confirm each produces the matching ground test product after about three seconds.
