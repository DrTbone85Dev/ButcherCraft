# ButcherCraft Patty Former

Status: IM-031C persistent player-controlled continuous Run implemented

## Purpose

The Patty Former is a promoted gameplay workstation for one process:

```text
Ground Beef -> Beef Patties
```

It reuses the existing Workstation -> Execution -> Scheduler -> owner-result path proven by the Grinder. IM-028C separates material deposit from operation authority: valid Ground Beef makes processing `READY`, but does not itself create an Execution operation, Machine Run, or Scheduler work. IM-029 allows an employee to deliver one exact Ground Beef from an explicit Grinder. IM-030B gives the input and output capacity `64` and permits delivery into a compatible stack. IM-031C activates DG-005's `POWERED_CONTINUOUS_EXPLICIT_STOP` policy through the shared machine-neutral Run coordinator while Patty Former recipe authorization, inventory effects, and owner results remain Patty Former-owned. Employee Patty Former operation, worker AI, Allocation, packaging, cooking, refrigeration, and public workstation APIs remain gated.

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
5. Use GUI START, or Shift + right-click while OFF, to create one persistent Run.
6. Observe separately bounded server-authoritative cycles until input becomes empty, output becomes blocked, or STOP is requested.
7. Retrieve the Beef Patties output, which may have merged with compatible existing output. Compatible input or output capacity resumes the same active Run.
8. Use GUI STOP, or Shift + right-click while active, to close child admission and stop at the safe cycle boundary.

Employee-assisted two-workstation flow:

1. Process Beef Trim in the Grinder.
2. Explicitly assign an employee to transfer Ground Beef from the Grinder to the Patty Former.
3. Observe exact Material Handling custody and visible employee carrying.
4. Explicitly START a Patty Former Run if the machine is OFF.
5. Observe one or more bounded cycles produce Beef Patties until the machine is empty, blocked, or explicitly stopped.

The employee transfer ends with processing `READY` and a destination reservation. If the machine is OFF, only the player can authorize START. If the exact destination already owns a `RUNNING_EMPTY` Run, delivery merely restores that existing Run's eligibility; it does not grant new authority.

## Continuous Run Controls

`VALID INPUT != AUTHORIZED OPERATION` is an enforced workstation contract.

- Normal right-click opens the Patty Former inventory and never requests an
  operation or Run.
- GUI START creates or observes one exact-instance persistent Run. Shift +
  right-click while OFF is the same START shortcut.
- GUI STOP targets the exact Run and closes later child admission. Shift +
  right-click while active is the same STOP shortcut.
- GUI RESUME continues the same restart-suspended Run under restart Policy B;
  load never resumes it automatically.

- `EMPTY` is represented by `IDLE` with no input.
- `READY` means Ground Beef is present and the canonical operation resolves, but no operation has been requested.
- `RUNNING_EMPTY` means an active Run has no currently eligible input. Compatible input resumes the same Run without another START.
- An active eligible Run asks the Patty Former coordinator to issue one bounded Execution authorization and enters `PROCESSING` only when accepted.
- `OUTPUT_BLOCKED` represents output blockage while powered. Input and existing output remain unchanged; removing the blockage restores eligibility within the same Run.
- `COMPLETE` retains the canonical output merged within capacity until extraction.
- `ERROR` and typed failures remain visible through workstation diagnostics.

Repeated START requests observe the existing Run and create no second Run. At most one child is nonterminal. Each child consumes exactly one Ground Beef and produces exactly one Beef Patties item. Follow-on children are separately identified and admitted only after proven terminal observation; stack presence never becomes one unbounded inventory mutation.

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

Patty Former block entity NBT stores workstation identity, inventory, processing state, selected operation, progress, reserved input, committed flag, Execution references, domain Effect Identity, owner-result reference, failure state, and schema version through the same processing workstation persistence path used by the promoted Grinder slice. Execution persists the exact Machine Run separately at `<world>/butchercraft/execution_machine_runs.json`, and Workstation persists operating state at `<world>/butchercraft/machine_operating_states.json`. A saved READY Patty Former without a Run restores READY and remains OFF. An active saved Run enters `RESTART_REQUIRED` under Policy B; valid input never starts or resumes work as a load side effect.

Serialization coverage is currently NBT and block-entity round trip coverage, including active pre-effect state, completed state, malformed restored state, and uncertain consequential state. IM-018 does not add startup checkpoint recovery or operator reconciliation.

Breaking an idle Patty Former drops contained items. Breaking an active pre-effect Patty Former preserves Ground Beef and does not fabricate Beef Patties. Completed output is preserved and completed operations do not rerun.

## Material Handling Destination Readiness

The Patty Former implements the existing DG-002A Workstation endpoint contract for one exact Ground Beef destination deposit. Workstation owns validation, instance identity, freshness, slot mutation, endpoint journal publication, and owner result. A committed deposit ends with processing `READY`; it does not create a Machine Run, call Execution, or schedule work. The existing continuous coordinator may later observe that READY state only when an already-authoritative exact-instance Run is active.

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
- Normal-use menu access with valid multi-count input, GUI START/STOP/RESUME,
  and state-aware secondary-use controls.
- READY-without-authority, empty START, repeated bounded cycles, same-Run empty
  and blocked recovery, duplicate START safety, exact Run/instance binding,
  safe STOP, Policy B presentation, long bounded stacks, and destination-endpoint validation.
- Employee Grinder-to-Patty Former delivery, exact Ground Beef custody and carrying, reservation handoff, cancellation return, reload, endpoint replacement, and no-auto-start regressions.

Manual client verification remains required before claiming human acceptance.

## Explicit Exclusions

IM-031C does not add employee Patty Former operation, employee Machine START/STOP, Production-driven machine control or transfer, automatic workstation selection, autonomous logistics, general Logistics, carried quantities above one, batch transport, additional Patty Former recipes, yield balancing, packaging, new species, public APIs, automatic restart, forced chunk loading, wear, damage, maintenance, compensation, final art, or final UI polish.
