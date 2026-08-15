# ButcherCraft Patty Former

Status: IM-030B stack-aware processing and delivery with IM-028C explicit operation gate

## Purpose

The Patty Former is a promoted gameplay workstation for one process:

```text
Ground Beef -> Beef Patties
```

It reuses the existing Workstation -> Execution -> Scheduler -> owner-result path proven by the Grinder. IM-028C separates material deposit from operation authority: valid Ground Beef makes the workstation `READY`, but does not itself create an Execution operation or Scheduler work. IM-029 allows an employee to deliver one exact Ground Beef from an explicit Grinder. IM-030B gives the input and output capacity `64`, permits delivery into a compatible stack, and keeps employee Patty Former operation, worker AI, Allocation, packaging, cooking, refrigeration, and public workstation APIs gated.

## Gameplay Content

Registered content:

- `butchercraft:patty_former` block and block item.
- `butchercraft:beef_patties` product item.
- `butchercraft:form_beef_patties` processing operation and transformation.
- `butchercraft:patty_forming` workstation capability.

The Patty Former is obtainable through a shaped crafting recipe, appears in the ButcherCraft creative tab, drops itself when broken, and has generated blockstate, block model, item model, loot table, language, and placeholder textures.

The selected operation duration is `3000` milliseconds in the operation definition, which resolves to `60` server ticks through the workstation duration conversion rule.

## Player Flow

Standalone flow:

1. Place the Patty Former.
2. Insert Ground Beef.
3. Observe `READY`; waiting or reloading does not process the input.
4. Normal right-click to inspect the inventory without starting work.
5. Shift + right-click to explicitly request one operation.
6. Wait for server-authoritative progress to complete.
7. Retrieve the Beef Patties output, which may have merged with compatible existing output.

Employee-assisted two-workstation flow:

1. Process Beef Trim in the Grinder.
2. Explicitly assign an employee to transfer Ground Beef from the Grinder to the Patty Former.
3. Observe exact Material Handling custody and visible employee carrying.
4. Explicitly request one Patty Former operation.
5. Process it into Beef Patties.

The employee transfer ends at `READY`; only the player can authorize the Patty Former operation in IM-029.

## Explicit Operation Gate

`VALID INPUT != AUTHORIZED OPERATION` is an enforced workstation contract.

- Normal right-click opens the Patty Former inventory and never requests an
  operation.
- Shift + right-click requests exactly one player operation and does not open
  the inventory.

- `EMPTY` is represented by `IDLE` with no input.
- `READY` means Ground Beef is present and the canonical operation resolves, but no operation has been requested.
- An explicit request synchronously asks the Patty Former coordinator to issue one Execution authorization and enters `PROCESSING` only when accepted.
- `BLOCKED` plus `OUTPUT_OCCUPIED` represents output blockage. Input and existing output remain unchanged; removing the blockage restores `READY` and requires another explicit request.
- `COMPLETE` retains the canonical output merged within capacity until extraction.
- `ERROR` and typed failures remain visible through workstation diagnostics.

Repeated requests while `PROCESSING` observe the existing controller state and create no second Execution operation. One operation consumes exactly one Ground Beef and produces exactly one Beef Patties item. Remaining Ground Beef waits for another explicit request; there is no permanent latch and no automatic loop.

## Ownership

The Patty Former owns local ItemStack slots, visible state, process validation, progress, commit-time slot mutation, failures, and Patty Former owner-result evidence.

Execution owns operation identity, authorization consumption, lifecycle, attempts, owner-result observation, terminal Execution result evidence, and Unknown Outcome behavior.

Scheduler owns generic Execution Work dispatch, Invocation Identity, effect-policy enforcement, retry legality, and Scheduler-owned outcome publication.

Production owns optional ordered chain state, step assignments, chain progression, product-flow identity validation, and Production-owned chain completion evidence. It does not own either workstation, move items, mutate slots, or consume Execution authority.

## Identity Model

IM-018 keeps these identities distinct:

- Patty Former workstation identity.
- `butchercraft:form_beef_patties` transformation and process identity.
- Execution Operation Identity.
- Scheduler Work and Invocation Identity.
- Patty Former domain Effect Identity.
- Patty Former owner-result identity.
- Production Run identity.
- Production chain and chain-step identities.

Changing workstation, input, transformation, output, world, or configuration changes the relevant identity or produces an explicit conflict.

## Persistence And Block Breaking

Patty Former block entity NBT stores workstation identity, inventory, state, selected operation, progress, reserved input, committed flag, Execution references, domain Effect Identity, owner-result reference, failure state, and schema version through the same processing workstation persistence path used by the promoted Grinder slice. A saved READY Patty Former restores READY and remains idle; valid input never starts work as a load side effect. Existing active pre-effect and completed recovery behavior is unchanged.

Serialization coverage is currently NBT and block-entity round trip coverage, including active pre-effect state, completed state, malformed restored state, and uncertain consequential state. IM-018 does not add startup checkpoint recovery or operator reconciliation.

Breaking an idle Patty Former drops contained items. Breaking an active pre-effect Patty Former preserves Ground Beef and does not fabricate Beef Patties. Completed output is preserved and completed operations do not rerun.

## Material Handling Destination Readiness

The Patty Former now implements the existing DG-002A Workstation endpoint contract for one exact Ground Beef destination deposit. Workstation owns validation, instance identity, freshness, slot mutation, endpoint journal publication, and owner result. A committed deposit ends with `READY`; it does not call Execution and does not schedule work.

IM-029 uses the existing Workforce assignment, generic employee transfer command, carry presentation, and Grinder source endpoint to deliver exactly one Ground Beef. IM-030B permits that unit to be split from a larger Grinder output and merged into a compatible Patty Former input. It adds no automatic endpoint selection and does not make deposit an operation authorization.

## Production Chain

Production can represent one narrow ordered chain:

```text
step 0: Grinder        Beef Trim   -> Ground Beef
step 1: Patty Former   Ground Beef -> Beef Patties
```

After the Grinder step completes, Production records `AWAITING_MANUAL_TRANSFER`. It advances only after observing the Patty Former assignment, Patty Former owner result, and Execution result evidence for the second step.

Production validates product flow by identity: the Grinder step output product must match the Patty Former step input product. This is not item movement and does not prove the player transferred any specific ItemStack.

Production run persistence stores the ordered chain, step assignments, observed Execution identities, owner-result evidence references, step statuses, manual-transfer waiting state, terminal chain evidence, and schema version. Existing single-workstation Production runs remain valid because `workstation_chain` is optional.

## Validation Coverage

Automated coverage includes:

- Patty Former registration, data generation, resources, and assets.
- Ground Beef to Beef Patties operation resolution.
- Execution identity, Scheduler dispatch, owner-result requirement, duplicate safety, blocked output, wrong-result rejection, serialization, and active break behavior.
- Production chain assignment, manual-transfer waiting state, product-flow mismatch rejection, duplicate observation safety, terminal failure handling, persistence round trip, and legacy single-workstation save compatibility.
- GameTests for the real server block entities and the manual Grinder to Patty Former chain.
- Normal-use menu access with valid multi-count input and secondary-use-only
  operation authorization.
- READY-without-authority, wrong-input rejection, READY serialization, explicit player start, duplicate active request, blocked-output recovery, a separately authorized second batch, and destination-endpoint validation.
- Employee Grinder-to-Patty Former delivery, exact Ground Beef custody and carrying, reservation handoff, cancellation return, reload, endpoint replacement, and no-auto-start regressions.

Manual client verification remains required before claiming human acceptance.

## Explicit Exclusions

IM-030B does not add employee Patty Former operation, Production-driven transfer, automatic workstation selection, autonomous logistics, general Logistics, carried quantities above one, batch transport, additional Patty Former recipes, yield balancing, packaging, new species, public APIs, startup checkpoint recovery, compensation, final art, or UI polish.
