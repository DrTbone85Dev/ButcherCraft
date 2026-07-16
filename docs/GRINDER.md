# ButcherCraft Grinder

Status: Milestone 2C/2D machine wrapper and data-driven grinding proof

## Purpose

The Grinder is the first named machine built on the generic processing workstation framework. It proves a final-named machine can process products without owning species, product, yield, quality, or operation-selection logic.

## Boundaries

- `GrinderBlock`, `GrinderBlockEntity`, `GrinderMenu`, and `GrinderScreen` remain thin machine-specific wrappers.
- The Grinder declares `butchercraft:grinding` through `GrinderWorkstation.capability()`.
- Operation selection still belongs to `WorkstationOperationResolver`, `ProcessingGraph`, and loaded definitions.
- Transaction preparation and completion still belong to `WorkstationProcessingController`.
- Grinder operations are represented as one-element output lists in the shared multi-output operation model.
- Product output items still use the temporary development fixture mapping until a real product item factory is designed.

The Grinder must not switch on beef, pork, bison, poultry, or other species ids.

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
- Grinder and generic workstation source scans for species-specific branches.
- Generated operation JSON using `butchercraft:grinding`.

Manual verification should insert Beef Trim, Pork Trim, and Bison Trim test products into the Grinder and confirm each produces the matching ground test product after about three seconds.
