# ButcherCraft Multi-Output Processing

Status: Milestone 2E ordered-output processing proof

## Purpose

Multi-output processing lets one operation create an ordered collection of product outputs while keeping the same engine, definition resolver, workstation controller, and temporary fixture mapping used by one-output Grinder operations.

The Bandsaw currently proves this with a Beef Forequarter fabrication operation. Product choice, output order, yield ratios, quality adjustment, and workstation compatibility are data facts from processing definitions, not Bandsaw Java behavior.

## Operation Model

`ProcessingOperationDefinition` stores an `outputs` array. Each output declares:

- Product id.
- Processing state.
- Exact yield ratio.
- Quality adjustment.
- Quantity unit.
- Whether zero output is allowed.

Legacy one-output operations use the same model with one output entry. The Grinder therefore remains a single-output workstation through data, not a separate code path.

## Current Bandsaw Flow

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

The current yields are `30%`, `10%`, `10%`, `10%`, `5%`, `15%`, `5%`, and `10%`, for a total of `95%`. The remaining `5%` represents prototype process loss.

## Allocation Rules

Multi-output yield allocation uses exact integer arithmetic. Each output receives its floored exact share first. Any remaining units are assigned to the largest fractional remainders, with ties resolved by output order.

Additive yield modifiers are intentionally unsupported for multi-output operations until the project defines a distribution rule.

## Terminology

The whole-brisket output is named Packer Brisket and uses `butchercraft:beef_packer_brisket`. Future cut names must follow DEC-0037 and preserve anatomical distinctions; do not rename broad product definitions into narrower retail cuts unless the modeled cut actually matches.

## Boundaries

- Engine code remains Minecraft-independent.
- The resolver chooses operations through product definitions, processing profiles, operation categories, and workstation capabilities.
- The Bandsaw supplies only `butchercraft:bandsaw`.
- The controller commits the transaction once and inserts outputs in definition order.
- Temporary fixture item mappings are development-only and are not a final product item factory.
