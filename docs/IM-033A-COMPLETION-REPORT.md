# IM-033A Machine Condition Foundation Completion Report

Status: COMPLETE / PRODUCT OWNER ACCEPTED.

Product Owner acceptance was recorded on 2026-09-28. The
[acceptance record](#product-owner-acceptance-record) distinguishes this
documentation-only closeout from the implementation and validation below.

## Summary

IM-033A adds Workstation-owned condition to Grinder and Patty Former, exact
joint product/condition results, retained effect evidence, bounded exposure,
healthy legacy migration, checkpoint completeness and owner-native restoration.
Production policies remain inert. No wear, damage, breakdown, maintenance,
repair, lubrication, sanitation or later milestone gameplay is activated.

## Preflight

- Branch: `feature/milestone-2d`.
- Baseline HEAD: `967302b14699a2fe4043a66a3ab5ea4f3e168e48`.
- Upstream: `origin/feature/milestone-2d`; initial ahead/behind `0/0`.
- Initial working tree: clean.
- Version: `0.10.7-alpha.1`, unchanged.
- DG-006: ratified at the baseline; the owner separately authorized IM-033A.
- IM-033B, IM-033C and IM-034 remain gated.

## Files Modified

The complete grouped path inventory follows at the end of this report.

## Condition Authority

Workstation is the singular condition owner. Pure condition calculations create
immutable candidates; only the existing Workstation owner boundary publishes
them. Block entities hold reconciled mirrors, not independent condition truth.

## Condition Applicability

Grinder and Patty Former are explicitly enabled with independent inert policies.
Cutting Table uses NOT_APPLICABLE and has no condition record.

## Condition State

State binds exact Workstation Instance, schema, policy, revision, mechanical
loss, service debt, typed exposure accounts, active exposure, controlling fault,
accounted tick, suspension, discarded-tail evidence, initialization evidence,
effect count/reference and canonical digest.

## Aggregate Condition Representation

Bounded integer mechanical loss is separate from service debt. Presentation
bands are derived. Checked arithmetic and reduced rational remainders prevent
floating-point drift. The inert maximum is not future gameplay balance.

## Condition Revision

Positive monotonic condition revision is independent of inventory, endpoint,
projection and Run revisions. Equal-revision divergent state and stale writes
are rejected. Initialization begins at revision 1 with no effects.

## Condition Digest

Length-delimited, explicitly ordered SHA-256 content includes instance, policy,
all authoritative state and evidence references. No object hash, hidden clock,
runtime iteration order or random value participates.

## Policy Identity

Immutable versioned policy content has a canonical identity. Policy changes
require an explicit Workstation boundary: settle/close the old frozen-policy
interval, retain earned counters/remainder and begin a new eligible interval.
Unknown policies and wrong machine types fail visibly.

## Effect Identity / Receipts

Immutable schema-1 receipts retain exact request, pre/post condition, previous
receipt, owner candidate and optional processing/operating transition bindings.
The projection head alone activates their transitive closure. Staged files do
not grant authority. Same effect/request observes evidence; conflicts and stale
effects cannot silently apply again. Hot caching is bounded to 256 receipts;
durable required evidence is not deleted.

## Ordinary Processing Wear Foundation

Preparation freezes condition freshness and consequence before Execution
authorization. Commit revalidates both inventory and condition. Product slots,
condition, exact joint owner result and receipt head publish in one durable
Workstation projection. Execution receives the terminal owner result only after
publication and semantic read-back. A failed publication restores tentative
loaded state and fences mutation; recovery never guesses a product-only result.

Condition-aware handlers are additive DG-003 contracts. Historical handlers are
unchanged. Execution persistence schema 2 records full handler descriptors,
per-operation bindings, compatibility evolution and determinism reference;
historical schema 1 is still explicitly classified and readable.

## Production Policy Gate

Grinder: INERT. Patty Former: INERT. Both use zero processing loss/service debt,
no exposure consequences and no fault threshold. Nonzero policies are confined
to tests and dedicated GameTest composition. There is no player policy-edit
command and no production condition gameplay activation.

## Exposure Foundation

DRY_RUNNING, BLOCKED_POWERED and LOADED_MECHANICAL_STRESS are typed capabilities.
Active intervals bind instance, policy, operating transition and contiguous
loaded proof. The transient due index visits at most 64 due entries per ordinary
tick, revalidating their authoritative projection before mutation.

## Grace / Remainder

Exact rational remainder survives STOP/reload/policy boundaries. Successful
processing resets only policy-selected grace; STOP does not renew it. Thresholds
record their first semantic tick, independent of polling cadence.

## Simulation Clock

Only supplied Simulation Clock ticks determine exposure. No wall-clock wear,
uncontrolled RNG, skipped-tick catch-up or forced chunk loading is introduced.

## Offline Time

Restart suspends at the last proven durable cutoff. Unproven tail time is
explicitly discarded, never inferred as powered operation.

## Chunk Unload / Reload

Orderly unload closes the proven loaded interval. Reload reconciles the exact
owner projection and cannot fabricate exposure for an unavailable interval.
Unloaded state and receipts remain readable without block-entity loading.

## RESTART_REQUIRED

Policy B is preserved. Restart-suspended operating state earns no exposure and
does not implicitly restart a Run. Only existing explicit owner authority may
resume machinery.

## OUTPUT_BLOCKED

Blocked powered idle admits no child and earns no ordinary wear. Production
policies also have no blocked exposure policy. Test policy confirms zero loss
and no processing receipt during output blockage.

## Fault Foundation

A single typed controlling fault binds its exact cause effect, instance, policy,
condition revision and semantic tick. Test-only thresholds prove deterministic
creation, persistence and denied child eligibility; legitimate STOP remains
available. No production fault can arise from the inert policies.

## Mechanical Fault vs Recovery Failure

FAULTED is coherent mechanical state. Missing/conflicting receipts, unsupported
schemas, stale projections and identity/policy conflicts are separate typed
recovery/coherence failures. No corruption is translated into gameplay damage.

## Service State

Service debt is distinct from mechanical loss. Typed future service/repair effect
kinds and a no-child terminal-Run/restart-safe eligibility predicate exist, but
there is no service action, resource consumption, wear restoration or repair UI.

## Durable Projection

Workstation projection payload schema advances 1 to 2. Physical sharded storage
remains `workstations/projections/v1`. Receipts use
`workstations/condition_effects/v1`. Workstation checkpoint collection advances
2 to 3 and its live participant 4 to 5. Endpoint journal and operating-state
schemas remain unchanged. Unsupported schemas fail visibly.

## Legacy Healthy Initialization

Explicit migration initializes healthy state on the same exact instance and
binds historical projection evidence. Original schema-1 bytes are retained in
`legacy_sources`; repeated migration is observational. Unresolved endpoint
effects or historical in-flight children defer new condition-aware admission.
No prior usage is interpreted as wear.

## Loaded Reconciliation

Inventory mutation first reconciles durable state, including before the first
block-entity tick. Stale chunk NBT cannot overwrite newer projection evidence.
Joint owner-result repair requires the exact immutable result, matching
condition receipt, post-inventory digest and planned projection revision.

## Unloaded Availability

Projection/receipt reads and checkpoint enumeration operate on persisted owner
files. The scale fixture includes 750 unloaded instances and performs zero chunk
loads. A loaded object is not required to retain condition authority.

## Replacement / Retirement

Retirement closes/suspends exposure before publishing a tombstone retaining
evidence. A replacement receives a new monotonic instance and canonical healthy
condition. Old effects cannot target a new instance at the same coordinates.

## Checkpoint Integration

The existing 17-owner live checkpoint composition remains singular. The
Workstation participant freezes exact projections and receipt closure, including
required unloaded/retired instances. Missing evidence or unresolved operating
pairs reject completeness before head commit. Historical generations are not
rewritten.

## Owner-Native Restoration

The concrete native fixture uses generation 1, tick 100, one unloaded Workstation
and two receipts. Fault tick is 31, condition revision 3, digest
`sha256:299563f3f4e5c478e199b10655ae14100094ce7c4a611cf25b6f17c21b2eb729`.
Owner-native expansion, atomic file publication and repeated verification restore
exact condition and receipt bytes without effect replay. Historical collection
schema 2 restores before explicit healthy migration; source bytes remain intact.

## Hard-Crash Recovery

Atomic fault injection before replacement retains both old product and old
condition; injection after replacement is proven by exact read-back and retains
both new values. Staging alone grants no authority. Pending condition/operating
transition blocks mutation/checkpoint completion until the exact successor is
proven or accepted checkpoint restoration resolves it. Neither missing effects
nor unproven exposure are reconstructed.

| DG-006 boundary | Foundation evidence |
| --- | --- |
| A-D, preparation/staging/joint publication | Stale-plan and staging tests plus before/after atomic replacement fault injection retain only exact old or joint new state. |
| E, durable state with stale loaded mirror | Existing durable-projection GameTests and new reload fixtures reconcile without repeating product/condition effects. |
| F-I, opened/active/prepared/committed exposure | Exact partition, staged-evidence, durable-cutoff, receipt closure and restart/Policy B fixtures preserve counters and discard unproven tails. |
| J, deterministic fault | Semantic tick 31 and exact fault receipt survive native restoration; test-only machine STOP/reload remains coherent. |
| K-N, gameplay breakdown and service/repair | Gameplay is not authorized in IM-033A. Typed fault/effect/service gates exist; no claim of implemented repair/resource crash handling is made. |
| O-P, checkpoint/restoration | Pending transition and missing closure reject completeness; exact native files restore and verify without replay. |
| Q, replacement | New instance starts healthy, old receipt application fails, referenced retired evidence remains retained. |

## Scheduler Discontinuity

A discontinuity closes the active indexed subset at durable cutoffs. Pure
regression fixtures prove identical loss, counters and remainder after restart
or discontinuity suspension; later Clock values are not processed as runtime.

## Diagnostics

`/butchercraft workstation status <x> <y> <z>` reports applicability, exact
instance, schema/revision, loss/band/service debt, policy, exposure/cutoff,
suspension/tail evidence, fault, receipt head and projection/coherence. It is
read-only, permission-gated and uses existing built-in synchronized arguments.

## Architecture Manifest

Mechanically enforced ownership contracts cover machine condition state,
effects and exposure under Workstation, plus immutable condition receipt
persistence. Projection and Execution document schema descriptors are updated.
Dependency tests prohibit condition authority in Execution, Scheduler, Workforce,
Material Handling, Checkpoint and client packages, and keep arithmetic pure.

## Processing Atomicity Evidence

A Workstation inventory commit-plan unit fixture consumes 64 input units through
64 distinct prepared owner effects, producing 64 output units. This fixture
does not dispatch 64 Scheduler children. Test loss 3 per effect saturates at 100;
condition revision is 65 and exact effect count 64. Reload preserves product,
condition and closure. Real Grinder and Patty Former GameTests separately prove
the existing Execution path produces one canonical product plus loss 3/debt 1
under injected policy. Production inert tests retain unchanged products.

## Exposure Determinism Evidence

100 ticks once and ten 10-tick settlements produce loss 37, remainder 3/5,
eligible ticks 100 and grace progress 100 under grace 6/rate 2/5. Exact final
digests differ because receipt/revision partition history intentionally differs;
semantic condition counters are identical. Threshold 10 occurs at tick 31.

## Scale / Performance

Focused measured fixture: 1,000 registered Workstations, 666 condition-enabled,
10 active exposures, 96 worn fixtures, 750 unloaded. Lookup 0.989 ms; aggregate
mutation 4,163.302 ms; due evaluation 5.127 ms; enumeration 505.899 ms; checkpoint
freeze 1,705.303 ms. These are local fixture observations, not production latency
guarantees. Zero chunks were loaded.

## Persistence Frequency

1,000 simulated ticks at cadence 20 across 10 active exposures produced 500
settlement publications and 500 periodic owner reads; inactive writes were zero.
No threshold transition was enabled in that scale policy. Threshold-specific
tests separately verify semantic-deadline publication. There is no global
condition file, all-machine per-tick scan or per-tick condition persistence.

The measured publication count is for condition effects, not every existing
Workstation write. Pre-existing processing-progress persistence can publish a
complete projection each processing tick, carrying unchanged condition fields.
That behavior is preserved; IM-033A adds no per-tick condition mutation or
separate condition-triggered write.

## Storage Impact

Average healthy enabled projection: 3,477 bytes; average added condition bytes:
1,869. Scale projection total: 2,898,884 bytes; 606 receipts: 1,884,571 bytes;
checkpoint collection: 7,401,113 bytes. Required recovery evidence is retained.

## Windows Stress

Eight independent instances each publish 40 successive condition effects using
four threads: 320 effect publications plus initial projections, exact reload and
receipt verification. No attempt/temp debris remains. Atomic interruption tests
exercise both sides of file replacement using the existing Windows-safe helper.

## Focused Tests

Eight suites, 77 tests, 77 passed, zero failures/errors/skips.

## Full Java Validation

Fresh `test --rerun-tasks`: 357 suites, 1,816 tests, 1,809 passed, zero failures,
zero errors, seven skipped. Skips are existing opt-in actual-world R2A/R3C/R4
fixtures requiring explicitly configured disposable roots/publication flags;
no test was disabled for this milestone. Reports are retained in
`work/im033a-validation/full-final` locally.

The earlier full run exposed three historical selector fixtures using the
current-schema constant with a pre-condition payload. The fixtures now name
their actual historical schema; all original recovery assertions remain intact.

## GameTests

338 registered, 338 executed, 338 passed, zero failed; successful server exit.
This is the complete suite, including 17 new condition tests. The composed
processing/empty/supply/blocked/STOP/reload/checkpoint fixture and mechanical
fault/STOP/reload fixture both passed. The world-time rate test now runs in its
own batch so concurrent tests cannot change its clock; assertions are unchanged.

The existing bounded checkpoint shutdown-grace warnings were observed, followed
by successful generation 37 at tick 30,617 with all 17 participants and normal
shutdown. The final generation size was 17,159,115 bytes. Intermediate failed
runs, including test-fixture corrections, are retained locally and are not
represented as passes.

## Build

Normal build and clean build passed, including release artifact verification.
The clean build's test task was FROM-CACHE, using the preceding successful forced
run; it is not counted as a second independent test execution. The produced jar
is `butchercraft-0.10.7-alpha.1.jar` (4,826,127 bytes). Generated NeoForge metadata
reports `version="0.10.7-alpha.1"`. No release preparation is performed.

## Datagen

Both passes passed and reported `written: 0`, `removed stale: 0`. The 133
generated resources total 91,312 bytes and retain the same sorted file/size/hash
manifest before either pass and after both:
`2a5c358863a9e69f9cc8e8ea94797756354c3ddf4ddee0f413eb414098591af8`.
Ignored datagen cache files are excluded from the content manifest.

## Client Validation

`runClient` passed. The title screen and Mods screen were visually inspected;
the latter displayed ButcherCraft `0.10.7-alpha.1`. Texture atlases/resources
loaded. No classloading, condition-schema or projection-migration errors were
observed in this client run. Existing vanilla sound/shader warnings remain.
Quit Game was selected intentionally, the log recorded normal stopping, and
Gradle exited successfully. No world was opened in the client; migration/runtime
smoke was exercised in disposable GameTests instead.

## Protected-World Status

Neither protected historical world was opened. Read-only final-validation
baseline includes every file, including session locks:

- New World (24): 62 files, 12,915,793 bytes,
  `8fba8baeb7226200957245574190a93714fcab5833fc9a963c6238d7ef2e473b`.
- New World (25): 210 files, 27,248,926 bytes,
  `cb7fbc2d7437684ab5f4f342c4b17cf37b3c15c239289abc7069537661294932`.
- New World (24)'s preserved compatibility evidence matches it exactly.

These manifests were taken during final validation, not at task start. The final
post-client comparison matched every file/size/hash in both protected worlds
and the archived evidence. This is an explicit final-validation hash check,
not a claim of a freshly captured task-start baseline.

## Regression Results

Complete Java and GameTest suites passed for Grinder, Patty Former, finite
employee operation, Material Handling, Machine Runs and checkpoint/recovery.
Disposable-world smoke covers placement, both legacy healthy migrations, inert
processing, read-only diagnostics, independent replacement and test-policy
condition behavior. No protected historical save was used as a runtime fixture.

## Documentation

The [foundation guide](MACHINE_CONDITION_FOUNDATION.md), milestone/status notes,
architecture, Workstation, both processing machines and checkpoint guides
distinguish implemented foundation from gated production activation. Historical
DG-006 ratification evidence remains historical.

Final documentation validation passed for local links/anchors, heading hierarchy,
fences, changed tables, ASCII, trailing whitespace and final newlines.
`git diff --check` passed with only Windows CRLF normalization warnings.
The report inventory matches all 91 changed/new paths exactly.

## Architecture Compliance

Workstation owns condition/inventory; Execution owns Runs; Scheduler owns
dispatch; Clock supplies time only; Workforce owns assignments/reservations;
Material Handling owns custody; Checkpoint coordinates only. No second condition
authority, per-tick persistence, global per-tick scan, wall-clock wear, forced
loading or coordinate inheritance is introduced. No production wear, dry-running
damage, breakdown or maintenance/repair gameplay is activated.

## Known Limitations

Grinder activation awaits IM-033B; Patty Former awaits IM-033C. Maintenance/repair
requires later authorization, including minimal repair before any breakdown
activation. No component simulation, sanitation or employee maintenance exists.
Break/re-place gives a fresh instance/condition under the ratified alpha policy.
Checkpoint/receipt retention and compaction remain unresolved. Recovery fails
visibly when exact evidence cannot prove a complete owner state.

## IM-033A Result

IM-033A COMPLETE / PRODUCT OWNER ACCEPTED

Product Owner acceptance is recorded below. IM-033B, IM-033C and IM-034 have
not begun. Version remains `0.10.7-alpha.1`. Nothing was committed, pushed,
tagged or published. Final branch/HEAD/upstream remain the preflight values with
ahead/behind `0/0`; the working tree contains 48 modified tracked and 43 new files.

## Recommended Next Step

Commit/push the complete IM-033A implementation and acceptance, then separately
authorize planning/balance work for IM-033B Grinder Condition Activation.
Implementation and production activation require separate owner authorization;
neither begins automatically.

## Complete Path Inventory

Current inventory: 91 paths. No unexpected paths, assets, resources,
version, release metadata or protected-save files are changed.

### Documentation

- [docs/adr/ADR-PROPOSED-MACHINE-CONDITION-WEAR-DAMAGE-MAINTENANCE-AND-BREAKDOWN.md](adr/ADR-PROPOSED-MACHINE-CONDITION-WEAR-DAMAGE-MAINTENANCE-AND-BREAKDOWN.md)
- [docs/BCSE_ARCHITECTURE_GUIDE.md](BCSE_ARCHITECTURE_GUIDE.md)
- [docs/CHECKPOINT_RECOVERY_FOUNDATION.md](CHECKPOINT_RECOVERY_FOUNDATION.md)
- [docs/GRINDER.md](GRINDER.md)
- [docs/IM-033A-COMPLETION-REPORT.md](IM-033A-COMPLETION-REPORT.md)
- [docs/LIVE_CHECKPOINT_PUBLICATION.md](LIVE_CHECKPOINT_PUBLICATION.md)
- [docs/MACHINE_CONDITION_FOUNDATION.md](MACHINE_CONDITION_FOUNDATION.md)
- [docs/MACHINE_RUN_STATE_FOUNDATION.md](MACHINE_RUN_STATE_FOUNDATION.md)
- [docs/PATTY_FORMER.md](PATTY_FORMER.md)
- [docs/STARTUP_CHECKPOINT_RECOVERY.md](STARTUP_CHECKPOINT_RECOVERY.md)
- [docs/WORKSTATION_FRAMEWORK.md](WORKSTATION_FRAMEWORK.md)
- [KNOWN_LIMITATIONS.md](../KNOWN_LIMITATIONS.md)
- [MILESTONES.md](../MILESTONES.md)
- [TECHNICAL_ARCHITECTURE.md](../TECHNICAL_ARCHITECTURE.md)

### Architecture Manifest

- [src/main/java/com/butchercraft/architecture/ButcherCraftArchitectureManifest.java](../src/main/java/com/butchercraft/architecture/ButcherCraftArchitectureManifest.java)

### Workstation Integration

- [src/main/java/com/butchercraft/ButcherCraft.java](../src/main/java/com/butchercraft/ButcherCraft.java)
- [src/main/java/com/butchercraft/workstation/block/AbstractInventoryWorkstationBlockEntity.java](../src/main/java/com/butchercraft/workstation/block/AbstractInventoryWorkstationBlockEntity.java)
- [src/main/java/com/butchercraft/workstation/WorkstationExecutionStartRequest.java](../src/main/java/com/butchercraft/workstation/WorkstationExecutionStartRequest.java)
- [src/main/java/com/butchercraft/workstation/WorkstationInventory.java](../src/main/java/com/butchercraft/workstation/WorkstationInventory.java)
- [src/main/java/com/butchercraft/world/MachineOperatingStateService.java](../src/main/java/com/butchercraft/world/MachineOperatingStateService.java)

### Diagnostics

- [src/main/java/com/butchercraft/command/ButcherCraftDiagnostics.java](../src/main/java/com/butchercraft/command/ButcherCraftDiagnostics.java)

### Checkpoint And Restoration

- [src/main/java/com/butchercraft/integration/checkpoint/NativeOwnerRestorationAdapters.java](../src/main/java/com/butchercraft/integration/checkpoint/NativeOwnerRestorationAdapters.java)
- [src/main/java/com/butchercraft/workstation/checkpoint/ConditionCheckpointClosure.java](../src/main/java/com/butchercraft/workstation/checkpoint/ConditionCheckpointClosure.java)
- [src/main/java/com/butchercraft/workstation/checkpoint/WorkstationCheckpointProjectionService.java](../src/main/java/com/butchercraft/workstation/checkpoint/WorkstationCheckpointProjectionService.java)
- [src/main/java/com/butchercraft/workstation/checkpoint/WorkstationCheckpointRestorabilityVerifier.java](../src/main/java/com/butchercraft/workstation/checkpoint/WorkstationCheckpointRestorabilityVerifier.java)
- [src/main/java/com/butchercraft/world/checkpoint/LiveCheckpointParticipantRegistry.java](../src/main/java/com/butchercraft/world/checkpoint/LiveCheckpointParticipantRegistry.java)
- [src/main/java/com/butchercraft/world/checkpoint/LiveCheckpointService.java](../src/main/java/com/butchercraft/world/checkpoint/LiveCheckpointService.java)
- [src/main/java/com/butchercraft/world/checkpoint/LivePlatformDeterminismManifest.java](../src/main/java/com/butchercraft/world/checkpoint/LivePlatformDeterminismManifest.java)

### Condition Policy

- [src/main/java/com/butchercraft/integration/machine/MachineConditionPolicies.java](../src/main/java/com/butchercraft/integration/machine/MachineConditionPolicies.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionExposurePolicy.java](../src/main/java/com/butchercraft/workstation/condition/ConditionExposurePolicy.java)
- [src/main/java/com/butchercraft/workstation/condition/MachineConditionPolicy.java](../src/main/java/com/butchercraft/workstation/condition/MachineConditionPolicy.java)
- [src/main/java/com/butchercraft/workstation/condition/MachineConditionPolicyRegistry.java](../src/main/java/com/butchercraft/workstation/condition/MachineConditionPolicyRegistry.java)

### Processing Integration

- [src/main/java/com/butchercraft/integration/machine/PoweredProcessingMachineRunService.java](../src/main/java/com/butchercraft/integration/machine/PoweredProcessingMachineRunService.java)
- [src/main/java/com/butchercraft/machine/grinder/execution/GrinderExecutionConstants.java](../src/main/java/com/butchercraft/machine/grinder/execution/GrinderExecutionConstants.java)
- [src/main/java/com/butchercraft/machine/grinder/execution/GrinderExecutionCoordinator.java](../src/main/java/com/butchercraft/machine/grinder/execution/GrinderExecutionCoordinator.java)
- [src/main/java/com/butchercraft/machine/grinder/execution/GrinderExecutionOperationHandler.java](../src/main/java/com/butchercraft/machine/grinder/execution/GrinderExecutionOperationHandler.java)
- [src/main/java/com/butchercraft/machine/pattyformer/execution/PattyFormerExecutionConstants.java](../src/main/java/com/butchercraft/machine/pattyformer/execution/PattyFormerExecutionConstants.java)
- [src/main/java/com/butchercraft/machine/pattyformer/execution/PattyFormerExecutionCoordinator.java](../src/main/java/com/butchercraft/machine/pattyformer/execution/PattyFormerExecutionCoordinator.java)
- [src/main/java/com/butchercraft/machine/pattyformer/execution/PattyFormerExecutionOperationHandler.java](../src/main/java/com/butchercraft/machine/pattyformer/execution/PattyFormerExecutionOperationHandler.java)
- [src/main/java/com/butchercraft/workstation/block/AbstractProcessingWorkstationBlockEntity.java](../src/main/java/com/butchercraft/workstation/block/AbstractProcessingWorkstationBlockEntity.java)
- [src/main/java/com/butchercraft/workstation/WorkstationConditionProcessingAccess.java](../src/main/java/com/butchercraft/workstation/WorkstationConditionProcessingAccess.java)
- [src/main/java/com/butchercraft/workstation/WorkstationProcessingController.java](../src/main/java/com/butchercraft/workstation/WorkstationProcessingController.java)
- [src/main/java/com/butchercraft/world/execution/ExecutionContractBindings.java](../src/main/java/com/butchercraft/world/execution/ExecutionContractBindings.java)
- [src/main/java/com/butchercraft/world/execution/ExecutionLegacyRegistryProfiles.java](../src/main/java/com/butchercraft/world/execution/ExecutionLegacyRegistryProfiles.java)
- [src/main/java/com/butchercraft/world/execution/persistence/ExecutionStorage.java](../src/main/java/com/butchercraft/world/execution/persistence/ExecutionStorage.java)
- [src/main/java/com/butchercraft/world/ExecutionService.java](../src/main/java/com/butchercraft/world/ExecutionService.java)

### GameTests

- [src/main/java/com/butchercraft/test/gametest/LiveCheckpointGameTests.java](../src/main/java/com/butchercraft/test/gametest/LiveCheckpointGameTests.java)
- [src/main/java/com/butchercraft/test/gametest/MachineConditionGameTestPolicies.java](../src/main/java/com/butchercraft/test/gametest/MachineConditionGameTestPolicies.java)
- [src/main/java/com/butchercraft/test/gametest/MachineConditionGameTests.java](../src/main/java/com/butchercraft/test/gametest/MachineConditionGameTests.java)
- [src/main/java/com/butchercraft/test/gametest/WorldTimeGameTests.java](../src/main/java/com/butchercraft/test/gametest/WorldTimeGameTests.java)

### Condition Domain

- [src/main/java/com/butchercraft/workstation/condition/ConditionActiveExposure.java](../src/main/java/com/butchercraft/workstation/condition/ConditionActiveExposure.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionCoherenceCode.java](../src/main/java/com/butchercraft/workstation/condition/ConditionCoherenceCode.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionCoherenceValidator.java](../src/main/java/com/butchercraft/workstation/condition/ConditionCoherenceValidator.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionDigest.java](../src/main/java/com/butchercraft/workstation/condition/ConditionDigest.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionDueIndex.java](../src/main/java/com/butchercraft/workstation/condition/ConditionDueIndex.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionEffectKind.java](../src/main/java/com/butchercraft/workstation/condition/ConditionEffectKind.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionEffectReceipt.java](../src/main/java/com/butchercraft/workstation/condition/ConditionEffectReceipt.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionEvidenceClosure.java](../src/main/java/com/butchercraft/workstation/condition/ConditionEvidenceClosure.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionExposureAccount.java](../src/main/java/com/butchercraft/workstation/condition/ConditionExposureAccount.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionExposureType.java](../src/main/java/com/butchercraft/workstation/condition/ConditionExposureType.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionFault.java](../src/main/java/com/butchercraft/workstation/condition/ConditionFault.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionInitialization.java](../src/main/java/com/butchercraft/workstation/condition/ConditionInitialization.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionInitializationEvidence.java](../src/main/java/com/butchercraft/workstation/condition/ConditionInitializationEvidence.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionProcessingBinding.java](../src/main/java/com/butchercraft/workstation/condition/ConditionProcessingBinding.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionProcessingCandidates.java](../src/main/java/com/butchercraft/workstation/condition/ConditionProcessingCandidates.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionProcessingCommit.java](../src/main/java/com/butchercraft/workstation/condition/ConditionProcessingCommit.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionProcessingPreparation.java](../src/main/java/com/butchercraft/workstation/condition/ConditionProcessingPreparation.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionProjection.java](../src/main/java/com/butchercraft/workstation/condition/ConditionProjection.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionRemainder.java](../src/main/java/com/butchercraft/workstation/condition/ConditionRemainder.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionTransitionBinding.java](../src/main/java/com/butchercraft/workstation/condition/ConditionTransitionBinding.java)
- [src/main/java/com/butchercraft/workstation/condition/MachineConditionState.java](../src/main/java/com/butchercraft/workstation/condition/MachineConditionState.java)
- [src/main/java/com/butchercraft/workstation/condition/UnsupportedConditionSchemaException.java](../src/main/java/com/butchercraft/workstation/condition/UnsupportedConditionSchemaException.java)
- [src/main/java/com/butchercraft/workstation/condition/WorkstationConditionEngine.java](../src/main/java/com/butchercraft/workstation/condition/WorkstationConditionEngine.java)

### Durable Projection And Persistence

- [src/main/java/com/butchercraft/workstation/condition/ConditionCodec.java](../src/main/java/com/butchercraft/workstation/condition/ConditionCodec.java)
- [src/main/java/com/butchercraft/workstation/condition/ConditionReceiptStorage.java](../src/main/java/com/butchercraft/workstation/condition/ConditionReceiptStorage.java)
- [src/main/java/com/butchercraft/workstation/projection/DurableWorkstationProjection.java](../src/main/java/com/butchercraft/workstation/projection/DurableWorkstationProjection.java)
- [src/main/java/com/butchercraft/workstation/projection/DurableWorkstationProjectionService.java](../src/main/java/com/butchercraft/workstation/projection/DurableWorkstationProjectionService.java)
- [src/main/java/com/butchercraft/workstation/projection/WorkstationProjectionCodec.java](../src/main/java/com/butchercraft/workstation/projection/WorkstationProjectionCodec.java)
- [src/main/java/com/butchercraft/workstation/projection/WorkstationProjectionSchema.java](../src/main/java/com/butchercraft/workstation/projection/WorkstationProjectionSchema.java)
- [src/main/java/com/butchercraft/workstation/projection/WorkstationProjectionStorage.java](../src/main/java/com/butchercraft/workstation/projection/WorkstationProjectionStorage.java)

### Java Tests

- [src/test/java/com/butchercraft/integration/checkpoint/StartupCheckpointCandidateSelectorTest.java](../src/test/java/com/butchercraft/integration/checkpoint/StartupCheckpointCandidateSelectorTest.java)
- [src/test/java/com/butchercraft/architecture/ArchitectureRulesTest.java](../src/test/java/com/butchercraft/architecture/ArchitectureRulesTest.java)
- [src/test/java/com/butchercraft/persistence/ConditionAtomicPublicationTest.java](../src/test/java/com/butchercraft/persistence/ConditionAtomicPublicationTest.java)
- [src/test/java/com/butchercraft/workstation/checkpoint/ConditionNativeRestorationTest.java](../src/test/java/com/butchercraft/workstation/checkpoint/ConditionNativeRestorationTest.java)
- [src/test/java/com/butchercraft/workstation/checkpoint/ConditionScaleCheckpointTest.java](../src/test/java/com/butchercraft/workstation/checkpoint/ConditionScaleCheckpointTest.java)
- [src/test/java/com/butchercraft/workstation/checkpoint/WorkstationCheckpointCompletenessTest.java](../src/test/java/com/butchercraft/workstation/checkpoint/WorkstationCheckpointCompletenessTest.java)
- [src/test/java/com/butchercraft/workstation/condition/ConditionArchitectureBoundaryTest.java](../src/test/java/com/butchercraft/workstation/condition/ConditionArchitectureBoundaryTest.java)
- [src/test/java/com/butchercraft/workstation/condition/ConditionPersistenceTest.java](../src/test/java/com/butchercraft/workstation/condition/ConditionPersistenceTest.java)
- [src/test/java/com/butchercraft/workstation/condition/MachineConditionFoundationTest.java](../src/test/java/com/butchercraft/workstation/condition/MachineConditionFoundationTest.java)
- [src/test/java/com/butchercraft/workstation/projection/ConditionTestFixtures.java](../src/test/java/com/butchercraft/workstation/projection/ConditionTestFixtures.java)
- [src/test/java/com/butchercraft/world/execution/ExecutionRegistryCompatibilityTest.java](../src/test/java/com/butchercraft/world/execution/ExecutionRegistryCompatibilityTest.java)

## Product Owner Acceptance Record

### Summary

On 2026-09-28, the Product Owner accepted IM-033A Machine Condition Foundation.
This closeout records that decision and corrects stale status references only.
It does not activate production condition consequences or amend DG-006.

### Preflight

- Branch: `feature/milestone-2d`.
- HEAD: `967302b14699a2fe4043a66a3ab5ea4f3e168e48`.
- Upstream: `origin/feature/milestone-2d`; ahead/behind `0/0` against the local
  tracking reference. No fresh remote query was performed for acceptance.
- Version: `gradle.properties` retains `mod_version=0.10.7-alpha.1`.
- Working tree: 48 modified tracked files and 43 untracked files from IM-033A;
  the complete implementation was preserved, not treated as a clean baseline.
- A pre-edit SHA-256 inventory captured all 2,199 tracked/nonignored untracked
  repository files to distinguish acceptance edits from cumulative work.
- No commit, push, tag, or publication was performed.

### Previous IM-033A Status

IMPLEMENTED / VALIDATED; PRODUCT OWNER ACCEPTANCE PENDING.

### New IM-033A Status

COMPLETE / PRODUCT OWNER ACCEPTED.

### Product Owner Acceptance Evidence

The explicit Product Owner acceptance instruction is the authority for this
status change. The owner accepted the implemented foundation and the validation
baseline below. This record does not invent additional manual scenarios or
represent historical validation as newly executed tests.

| Accepted evidence | Recorded result |
| --- | --- |
| Focused Java | 8 suites; 77 tests; 77 passed; 0 failures, errors, or skips. |
| Full Java | 357 suites; 1,816 tests; 1,809 passed; 0 failures; 0 errors; 7 configured/opt-in skips. |
| GameTests | 338 registered; 338 executed; 338 passed; 0 failed. |
| Build | Normal build and clean build passed; the clean build reused the successful test cache. |
| Datagen | Both passes passed and wrote zero files. |
| Client | Initialization, title screen, and version validation passed; intentionally closed normally. |
| Documentation | Documentation validation and `git diff --check` passed. |
| Windows publication | Condition publication stress passed. |
| Protected worlds | Final-validation hash comparison passed, with the baseline timing qualification recorded above. |

These runtime validations were not rerun solely for acceptance. Their detailed
evidence and limitations remain in the implementation sections above.

### Accepted Condition Foundation

Workstation remains the singular condition authority. Acceptance covers explicit
applicability, aggregate mechanical condition, separate service debt, typed
exposure, exact revision, canonical digest, versioned policy identity, exact
condition-effect identity and retained immutable receipts. It also covers
deterministic exposure/grace, Simulation Clock timing, unload and Policy B
suspension, typed mechanical faults, durable projection, healthy migration,
unloaded reads, checkpoint/restoration, crash recovery, replacement protection,
and read-only diagnostics. No other subsystem acquires condition authority.

### Production Policy Gate

Grinder production condition policy: **INERT**.
Patty Former production condition policy: **INERT**.

Acceptance activates no ordinary Grinder wear, Grinder dry-running wear/damage,
Grinder breakdown, Patty Former wear/damage/breakdown, player maintenance or
repair, or employee maintenance. Test-only nonzero policies are validation
fixtures, not approved production balance.

### Processing Atomicity

The owner accepts that successful product and its required condition consequence
cannot durably diverge. The terminal Execution owner result is exposed only
after exact Workstation durable publication/read-back. Rejected/no-effect work
earns no ordinary processing wear; duplicate observation creates neither a
duplicate product nor a duplicate condition effect.

### Exposure Accounting

Accepted accounting uses bounded settlement and the active-subset due index,
not a global per-tick Workstation scan or per-tick condition-effect persistence.
Exact rational remainder, grace and successful-work grace reset preserve
polling-frequency-independent semantic results. Receipt/revision history may
still differ by settlement partition, as documented above.

There is no wall-clock wear, Scheduler-discontinuity wear, fabricated unloaded
wear, or RESTART_REQUIRED exposure. Existing processing-progress projection
writes may still carry unchanged condition fields each processing tick; the
accepted bound concerns condition-triggered effects/writes, not all pre-existing
Workstation persistence.

### Mechanical Fault Foundation

The validated test-policy mechanical fault is coherent gameplay condition, not
RECOVERY_REQUIRED. It persists through durability and recovery, can deny future
eligibility, and does not prevent legitimate STOP. Production fault/breakdown
activation remains gated.

### Persistence / Durable Projection

Acceptance covers the implemented projection schema evolution and exact
condition receipts. Workstation retains durable authority; unloaded condition
is available without loading chunks. This acceptance changes no schema, owner,
storage path, or evidence semantics.

### Legacy Migration

Healthy pre-condition migration is accepted with exact historical source-byte
preservation, same-instance binding and idempotent successor publication. It
does not infer prior wear or rewrite historical checkpoints.

### Checkpoint / Restoration

Accepted completeness includes exact condition-enabled Workstation projections
and required receipt closure, including unloaded instances. Owner-native
restoration restores frozen owner evidence without consequence replay or wear
recalculation. Missing/conflicting evidence remains fail-visible.

### Hard-Crash Recovery

Acceptance retains the last-durable-cutoff policy. Recovery does not fabricate
an unproven exposure tail or split committed product from required condition.
Foundation fault injection and native restoration evidence are accepted; future
gameplay service/repair crash cases remain gated, not claimed implemented.

### Scale / Performance

The accepted measured fixture has 1,000 registered Workstations, 666
condition-enabled, 10 active exposures, 96 worn fixtures, 750 unloaded instances,
and zero chunk loads. This is validation evidence, not a performance guarantee.
Periodic condition work uses the active/due subset rather than a global
per-tick machine scan.

### Storage / Retention

Condition receipts and checkpoint state increase storage. Required recovery
evidence remains retained. Checkpoint/receipt retention and compaction are
unresolved; this acceptance authorizes no deletion or compaction.

### Break / Replace Alpha Limitation

A replacement receives a new Workstation Instance Identity and canonical
healthy condition. Old condition does not transfer by coordinates. This remains
DG-006's ratified alpha limitation; condition-preserving portable machine items
remain future work.

### Remaining Limitations

Both production policies are inert. Machine-specific activation and numeric
balance are unapproved. Minimal repair must be separately approved and validated
before breakdown activation; richer maintenance/repair, employee maintenance,
portable condition preservation, and storage retention/compaction remain gated
or unresolved. No limitation is silently converted into an implementation grant.

### Milestone Status

| Milestone | Current status |
| --- | --- |
| DG-006 | RATIFIED; not fully implemented. |
| IM-033A | COMPLETE / PRODUCT OWNER ACCEPTED. |
| IM-033B | Next only after separate Product Owner implementation authorization; not started. |
| IM-033C | GATED; not started. |
| IM-034 | GATED; not started. |

Before IM-033B production activation, Product Owner balance review must cover
condition scale, ordinary processing wear, dry-run grace, accelerated dry-run
wear rate, service threshold, critical threshold, fault/breakdown threshold,
and minimal repair behavior/restoration. No values are chosen by this closeout.

### Files Modified

Acceptance-only edits affect these ten existing working-tree documents:

- [MILESTONES.md](../MILESTONES.md)
- [KNOWN_LIMITATIONS.md](../KNOWN_LIMITATIONS.md)
- [TECHNICAL_ARCHITECTURE.md](../TECHNICAL_ARCHITECTURE.md)
- [Machine Condition Foundation](MACHINE_CONDITION_FOUNDATION.md)
- [Workstation Framework](WORKSTATION_FRAMEWORK.md)
- [Checkpoint Recovery Foundation](CHECKPOINT_RECOVERY_FOUNDATION.md)
- [Startup Checkpoint Recovery](STARTUP_CHECKPOINT_RECOVERY.md)
- [Architecture Guide](BCSE_ARCHITECTURE_GUIDE.md)
- [DG-006 ADR](adr/ADR-PROPOSED-MACHINE-CONDITION-WEAR-DAMAGE-MAINTENANCE-AND-BREAKDOWN.md)
- [This completion report](IM-033A-COMPLETION-REPORT.md)

Grinder, Patty Former, Live Checkpoint Publication and Machine Run guides were
inspected; their applicable status/gate wording was already correct and was
left unchanged by acceptance. Historical proposal/ratification records remain
historical. The cumulative implementation inventory above still contains 91
paths; it is not the acceptance-only changed-path list.

### Validation

Acceptance validation is documentation-only: `git diff --check`, local Markdown
links/anchors, headings, fences, tables, ASCII, trailing whitespace, final
newlines and changed-path verification. All checks passed. The audit covered
the cumulative 91 changed/new files, including 14 Markdown documents. The
pre-/post-acceptance hash comparison found exactly the ten listed Markdown
changes; the other 2,189 repository files and the file set were unchanged.
`git diff --check` exited 0 with only Windows CRLF normalization warnings.

No Java tests, GameTests, build, datagen or client launch were run for this
closeout. Protected saves were not opened or modified; their accepted
historical hash evidence was not freshly reverified.

### Preserved Gates

This acceptance introduces no runtime, test, schema, resource, version or save
changes. The cumulative implementation predates acceptance and is preserved.
Version remains `0.10.7-alpha.1`. No production wear, dry-running damage,
production fault/breakdown, maintenance/repair or lubrication is activated.
IM-033B, IM-033C and IM-034 have not started.

### Recommended Next Step

Commit/push the complete IM-033A implementation and acceptance, then separately
authorize planning/balance work for IM-033B Grinder Condition Activation.
Separate Product Owner implementation authorization is still required. This
task does not commit/push or begin any machine-specific condition activation.
