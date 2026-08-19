# ButcherCraft Grinder

Status: Milestone 2C/2D machine wrapper through IM-017 recipe expansion, IM-027 employee operation, IM-030B stack-aware processing, and IM-031B continuous player Runs

## Purpose

The Grinder is the first named machine built on the generic processing workstation framework. It proves a final-named machine can process products without owning species, product, yield, quality, or operation-selection logic. Versions 0.6.1 through 0.7.0 establish its transformation, registry, serialization, product-definition, transaction, datapack, and Bandsaw-compatibility foundations. IM-014 promotes the Grinder, Beef Trim, and Ground Beef presentation for normal gameplay while preserving existing registry ids. IM-015 promotes Pork Trim to Ground Pork, and IM-017 expands the same resolver, transformation, Execution, Scheduler, Production observation, and owner-result path to six promoted trim-to-ground flows. IM-031B places repeated player cycles under one persistent Execution-owned Machine Run without changing any recipe definition or bounded child operation.

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

IM-030B configures Grinder input and output capacity `64`. Beef Trim and Ground
Beef stack to 64; the other promoted trim and ground products retain their
existing item limits. Every bounded child consumes one recipe input and merges
one compatible output. A full or component-incompatible output blocks before
input mutation.

IM-031B activates `POWERED_CONTINUOUS_EXPLICIT_STOP` for player operation:

- Normal right-click opens the Grinder inventory in all ordinary Run states.
- GUI START, or Shift + right-click while `OFF`, creates or observes one exact
  Workstation Instance-bound Machine Run.
- While that Run remains authorized, each proven terminal child permits a new
  eligibility check and at most one next bounded Execution/Scheduler cycle.
- Empty input produces powered `RUNNING_EMPTY`; adding valid trim resumes the
  same Run without another START.
- Full or incompatible output produces powered `OUTPUT_BLOCKED`; removing
  enough output resumes the same Run without consuming blocked input.
- GUI STOP, or Shift + right-click while active, closes later child admission
  and turns the Grinder `OFF` at the deterministic safe boundary.
- After restart, Policy B preserves the exact Run as `RESTART_REQUIRED`; GUI
  RESUME continues that Run and GUI STOP ends it. There is no automatic restart.

The employee `/butchercraft employee operate <employee>` path remains one
reservation-scoped bounded Beef operation and is rejected while a player Run
is active. It does not grant employee START/STOP or persistent Run authority.
Opening the menu and inserting material never grants START authority.

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
- Stack-aware coverage for multi-count Beef Trim input, one-unit bounded child
  consumption, compatible Ground Beef output merge, full-output atomic
  blocking, and exact save/reload counts.
- Machine Run coverage for duplicate/concurrent START and STOP, generation,
  stale STOP, child admission, persistence, restart Policy B, chunk
  availability, replacement identity, and malformed or unsupported schemas.
- In-world coverage for multi-cycle processing, 64 bounded children under one
  Run, `RUNNING_EMPTY`, same-Run input resumption, `OUTPUT_BLOCKED`, same-Run
  capacity resumption, safe STOP, GUI access, and employee overlap rejection.

Manual verification should craft or obtain the Grinder, place it, load multiple
Beef Trim, open the GUI normally, and press START. Observe one item processed
per 60-tick child until input is empty, then confirm `RUNNING_EMPTY` stays on
without creating items or wear. Add more Beef Trim and confirm the same Run
resumes. Fill output, confirm `OUTPUT_BLOCKED` preserves input, remove capacity,
and confirm the same Run resumes. STOP during a child and confirm no later child
starts. Save an active Run, reload, and verify explicit RESUME/STOP from
`RESTART_REQUIRED`. Also verify Patty Former remains one-cycle. Automated
implementation does not claim a human acceptance pass unless a human tester
completes it.
