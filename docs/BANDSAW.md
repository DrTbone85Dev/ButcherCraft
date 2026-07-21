# ButcherCraft Bandsaw

Status: Milestone 2E industrial machine proof with v0.7.0 beef fabrication content

## Purpose

The Bandsaw is the first permanent fabrication machine built on the generic workstation framework. It proves that one input operation can create an ordered collection of output products without putting product, species, or cut-list logic into machine behavior.

The Bandsaw supplies only the `butchercraft:bandsaw` workstation capability. Product choice, output order, yield, quality adjustment, and compatibility still come from definitions rather than Bandsaw code. Operation selection continues to come from datapack-backed product, species, processing-profile, and processing-operation definitions. Completion is additionally validated against the datapack-loaded transformation registry entry for the resolved operation id, using product definitions loaded in the same active content snapshot.

## Structure

The machine is a two-block-tall placement:

- `butchercraft:bandsaw`: lower functional block.
- `butchercraft:bandsaw_upper`: upper mechanical block.

The lower block owns the block entity, inventory, processing state, ticker, menu provider, persistence, and item recovery. The upper block has no block entity. It forwards player interaction to the lower block and is kept synchronized with the lower block facing.

Placement fails if the upper space cannot be replaced. Breaking either half removes the complete machine. Recoverable input and completed outputs are dropped from the lower block exactly once.

The Bandsaw inventory is configured as one input slot plus eight ordered output slots. This is intentionally larger than the Development Processing Workstation and Grinder, which remain one-input, one-output machines.

## Current Flow

The built-in red-meat prototype definitions currently support:

```text
butchercraft:beef_forequarter -> butchercraft:break_beef_forequarter
butchercraft:beef_hindquarter -> butchercraft:break_beef_hindquarter
butchercraft:beef_short_loin -> butchercraft:cut_beef_short_loin
butchercraft:beef_round -> butchercraft:cut_beef_round
butchercraft:beef_sirloin -> butchercraft:cut_beef_sirloin
```

The operation declares:

```text
workstation_capability: butchercraft:bandsaw
```

For a `100000 gram` beef forequarter, ordered outputs are:

| Order | Product | Yield |
| --- | --- | --- |
| 1 | `butchercraft:beef_chuck` | 30% |
| 2 | `butchercraft:beef_rib` | 10% |
| 3 | `butchercraft:beef_packer_brisket` | 10% |
| 4 | `butchercraft:beef_plate` | 10% |
| 5 | `butchercraft:beef_shank` | 5% |
| 6 | `butchercraft:beef_trim` | 15% |
| 7 | `butchercraft:beef_fat` | 5% |
| 8 | `butchercraft:beef_bone` | 10% |

The ratios intentionally sum to 95%, leaving 5% process loss. Rounding is deterministic and uses integer arithmetic. For multi-output operations, remaining units are assigned by largest remainders with stable ties resolved by output order.

Version 0.7.0 adds the hindquarter and primal follow-on transformations documented in `docs/BEEF_FABRICATION_EXPANSION.md`. Those definitions use the same six-second duration, Bandsaw capability, transformation registry lookup, and atomic output-slot validation path as the forequarter proof.

## Boundaries

- `BandsawBlock`, `BandsawBlockEntity`, `BandsawMenu`, and `BandsawScreen` must stay free of beef-specific output logic.
- `BandsawUpperBlock` only manages the upper-half forwarding and paired-block lifecycle.
- `WorkstationOperationResolver`, `ProcessingGraph`, and loaded definitions choose the operation.
- `WorkstationProcessingController` validates completion through the atomic transformation strategy, commits the engine transaction once, and fills the ordered output slots.
- `WorkstationInventoryMaterialStore` adapts ItemStack input and output slots into pure material stores for transaction validation.
- Product output items still use the temporary development fixture mapping until a real product item factory exists.

## Verification Notes

Automated tests cover registration, paired-block placement and removal behavior, upper-half forwarding, resolver compatibility, ordered output quantities, output-slot filling, generated assets, definition JSON, missing transformation definitions, missing output item mappings, inventory material-store bridge capacity checks, and the v0.7.0 beef hindquarter/primal fabrication chain.

Manual verification should place the Bandsaw, confirm the upper half is placed and removed with it, insert Beef Forequarter, Beef Hindquarter, Beef Short Loin, Beef Round, and Beef Sirloin test products, and confirm the ordered outputs appear after processing.

If a pre-fix manual test world contains a stale workstation block entity attached to air, first try loading the world with the fixed code so invalid saved slot data can be skipped safely. For development-only worlds, creating a fresh manual-test world is acceptable if the affected chunk remains corrupted.
