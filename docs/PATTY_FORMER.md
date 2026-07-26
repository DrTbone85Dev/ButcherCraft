# ButcherCraft Patty Former

Status: IM-018 Patty Former and first manual two-workstation Production chain

## Purpose

The Patty Former is a promoted gameplay workstation for one process:

```text
Ground Beef -> Beef Patties
```

It reuses the existing Workstation -> Execution -> Scheduler -> owner-result path proven by the Grinder. It does not add automated transfer, worker AI, Allocation, packaging, cooking, refrigeration, or public workstation APIs.

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
3. Wait for server-authoritative progress to complete.
4. Retrieve one Beef Patties output.

Manual two-workstation flow:

1. Process Beef Trim in the Grinder.
2. Remove Ground Beef from the Grinder manually.
3. Insert Ground Beef into the Patty Former manually.
4. Process it into Beef Patties.

No system moves Ground Beef between machines in IM-018.

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

Patty Former block entity NBT stores workstation identity, inventory, state, selected operation, progress, reserved input, committed flag, Execution references, domain Effect Identity, owner-result reference, failure state, and schema version through the same processing workstation persistence path used by the promoted Grinder slice.

Serialization coverage is currently NBT and block-entity round trip coverage, including active pre-effect state, completed state, malformed restored state, and uncertain consequential state. IM-018 does not add startup checkpoint recovery or operator reconciliation.

Breaking an idle Patty Former drops contained items. Breaking an active pre-effect Patty Former preserves Ground Beef and does not fabricate Beef Patties. Completed output is preserved and completed operations do not rerun.

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

Manual client verification remains required before claiming human acceptance.

## Explicit Exclusions

IM-018 does not add additional Patty Former recipes, automated logistics, hoppers, conveyors, workers, Allocation, broad workflow graphs, recipe-selection UI, packaging, refrigeration, cooking, public APIs, startup recovery, automatic checkpoints, operator reconciliation, compensation, final art, or UI polish.
