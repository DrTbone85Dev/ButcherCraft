# Startup Checkpoint Recovery

Status: IM-031C-R4 implemented; IM-031C Product Owner accepted.

This document records the mechanically implemented startup-selection and owner-
native restoration boundary. ADR-02, ADR-02A, and ADR-02A-P1 remain controlling.

## Startup Order

Before consequential subsystem mutation is permitted, startup:

1. establishes World Identity;
2. reads restoration and checkpoint metadata;
3. reads owner-native state without invoking consequential owner APIs;
4. analyzes cross-owner coherence;
5. selects coherent `LIVE` state when it is provable;
6. otherwise selects the latest valid committed `COMPLETE_RESTORABLE`
   checkpoint or recovery generation; and
7. otherwise remains blocked with typed recovery diagnostics.

Selection uses verified generation/head evidence, not filesystem timestamps.
Owners from different generations are never merged.

## Restoration Transaction

One logical restoration follows this durable order:

```text
RESTORE_INTENT_DURABLE
OWNER_RESTORE_IN_PROGRESS
ALL_OWNER_PAYLOADS_PUBLISHED
ALL_OWNER_PAYLOADS_REVERIFIED
RESTORATION_RESULT_COMMITTED
MUTABLE_STARTUP_ELIGIBLE
```

The Restoration Intent binds World Identity, selected generation, manifest
digest, source, participant plans, expected native files and digests,
configuration, recovery evidence, and authority blocks. Its content-addressed
Restoration Identity is stable across restart.

Every native file is prepared before owner replacement begins. Each file is
published through `AtomicFilePublication` from frozen bytes and then re-read.
An already matching target is observed as exact. A target matching neither the
frozen pre-restore state nor the frozen restored state is a
`RESTORATION_CONFLICT`. An interrupted startup resumes the same intent and
generation; it does not allocate a second logical restoration.

The Restoration Result is committed only after all 17 participants verify. It
binds final native digests, restored tick, Workstation projection results,
authority blocks, Policy B Runs, and completion evidence. The next startup may
select coherent `LIVE` state; an old Result does not force repeated restore.

## Owner Adapters

Checkpoint Recovery coordinates the adapters. Each subsystem retains authority
over its native schema, parser, validation, and logical state.

| Owner | Accepted checkpoint owner schema | Native publication | Transformation and verification |
| --- | --- | --- | --- |
| Clock | Current checkpoint schema or legacy recovery proof | `simulation_state.json` | Clock parser verifies the exact selected tick. |
| Scheduler | Current checkpoint schema or legacy recovery proof | `simulation_scheduler.json`, optional `simulation_scheduler_recovery.json` | Scheduler parser verifies the selected tick, recovery discontinuity, acknowledgements, and admission boundary. |
| Execution | 1 | `execution_operations.json`, `execution_machine_runs.json` | Execution parsers verify operations and Runs; active Runs enter persisted Policy B without creating a replacement Run or child. |
| Workstation | 1, 2, 3 | `machine_operating_states.json`, endpoint journal, instance registry, reservations, and sharded durable projections | Workstation parsers verify native state and endpoint schema; powered state enters Policy B; schema-2 projection evidence restores exact per-instance records without chunk loading. |
| Material Handling | 1, 2 | `material_handling.json` | The matching Material Handling parser verifies exact lifecycle and custody without withdrawal, deposit, return, cancellation, or retry. |
| Planning | 1 | Seven Planning files plus `planning_recovery_authority.json` when required | Planning files remain exact; legacy Recovery Result installs the same persisted mutation gate without resolving or replaying the outcome. |
| Production | 1 | `production_processes.json`, `production_plans.json`, `production_runs.json` | Schema and World Identity are validated; exact owner files are re-read. |
| Transactions | 1 | `transactions.json` | Schema and World Identity are validated; no Transaction is submitted. |
| Inventory | 1 | `inventory.json` | Schema and World Identity are validated; no quantity mutation API is called. |
| Business Runtime | 1 | `business_calendar_runtime.json`, `business_runtime.json`, `world_time.json` | Schema and World Identity are validated; exact owner files are re-read. |
| Workforce | 1 | `departments.json`, `employee_records.json`, `employee_material_handling_assignments.json`, `workforce_definitions.json` | Schema and World Identity are validated; exact owner files are re-read. |
| Goods | 1 | `goods.json` | Schema and World Identity are validated; exact owner file is re-read. |
| Economic Actors | 1 | `economic_actors.json` | Schema and World Identity are validated; exact owner file is re-read. |
| Orders | 1 | `orders.json` | Schema and World Identity are validated; exact owner file is re-read. |
| Contracts | 1 | `contracts.json` | Schema and World Identity are validated; exact owner file is re-read. |
| Player Identity | 1 | `player_identities.json` | Schema and World Identity are validated; exact owner file is re-read. |
| Checkpoint Recovery | 1 | No owner-native replacement | The participant verifies checkpoint/recovery evidence. Restoration Intent and Result are finalized outside the immutable selected generation. |

Unsupported owner or evidence schemas fail visibly before mutable startup.
Canonical-empty owner state remains an explicit participant rather than an
omitted owner.

## Workstation Reconciliation

The durable projection is Workstation-owned recovery authority independent of
chunk availability. Restoration publishes exact sharded projection records; it
does not rewrite block-entity NBT or force-load chunks.

When a matching physical instance loads, Workstation first applies the durable
projection and verifies exact state. A single later Policy B operating-state
reference may advance the projection only when the immutable Restoration Result
identifies the exact Run and the Workstation-owned operating record proves the
one-revision `powered -> RESTART_REQUIRED` transition. A replacement instance
or any other divergence remains fail-visible.

## Mutation And Replay Boundary

The startup gate blocks consequential mutation until source selection and any
restoration are complete. Persisted authority blocks are installed before
runtime services become eligible. A coherent restored world may therefore load
for observation and diagnostics while remaining intentionally read-only.

Restoration publishes native state only. It never dispatches Scheduler work,
executes an operation, commits Workstation processing or endpoint effects,
moves Material Handling custody, runs Planning or Production consequences, or
submits a Transaction.

## Diagnostics

Permission level 2 exposes:

```text
/butchercraft diagnostic checkpoint startup-status
```

The command reports startup state, live coherence, selected source and
generation, previous valid generation, Workstation restorability, Restoration
Identity, participant completion, restored tick, authority blocks, Policy B
Runs, preserved authorized work, mutation permission, fallback/failure reason,
last Restoration Result, required operator action, and measured phase timings.

## Historical Validation

The protected original and evidence worlds remain immutable. R3C successor
generation `2/39872` is the controlling historical candidate. It contains all
17 participants and six exact Workstation projections. Generation `1/39872`
remains immutable and is rejected as Workstation-incomplete.

Disposable historical validation restored generation `2/39872`, preserved
Clock tick `39872`, Scheduler recovery evidence, exact Execution child 10,
exact Machine Run identity, Policy B, Workstation inventory, Material Handling
state, and the unresolved Planning authority block. Minecraft loaded the
restored disposable world. A later restart selected coherent live state and did
not create another Restoration Intent or Result.

## Remaining Gates

The following remain outside R4:

- application to the protected original world;
- resolution of the historical Planning ambiguity or authority block;
- checkpoint retention, deletion, compaction, or storage budgets;
- general schema migration;
- recovery-selection gameplay UI;
- automatic machine restart or consequence replay;
- IM-032 and later gameplay work.
