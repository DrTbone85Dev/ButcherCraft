# Generic Execution Runtime Foundation

Status: IM-011 implemented foundation.

This note records the first generic Execution runtime implementation slice. It does not change RFC-0023, ADR status, Allocation integration, or public extension contracts. IM-012 later connects the first Grinder workstation operation to this runtime, IM-015 extends that same handler to Pork Trim, IM-016 lets Production observe terminal Grinder Execution evidence without acquiring Execution authority, IM-018 adds the Patty Former handler on the same generic path, IM-031A adds a distinct persistent Machine Run ledger above bounded child operations, IM-031B activates repeated bounded child admission for one player-controlled Grinder Run, and IM-031C activates the same machine-neutral coordination for one player-controlled Patty Former Run. General public workstation invocation remains gated.

## Implemented Scope

- Execution owns deterministic operation identity, domain Effect Identity, runtime lifecycle, attempts, terminal result evidence, explicit Unknown Outcome state, and versioned operation persistence.
- Execution accepts only private live `ExecutionAuthorization` objects. Immutable `ExecutionAuthorizationEvidence` is persisted and replay-readable, but it does not grant mutation authority by itself.
- Execution freezes inputs through explicit identity fields in authorization evidence and binds operation identity to authorization content, executable reference, handler identity, configuration identity, and world identity.
- Execution registers one generic Scheduler Work handler for `butchercraft:generic_execution_operation`.
- Scheduler owns Scheduler invocation identity, Scheduler Effect Identity, Work dispatch, Work runtime status, and effect observation.
- Successful Execution completion requires owner result evidence before Scheduler completion observes the result.
- Duplicate authorization content observes the existing operation. Conflicting content for the same authorization identity is rejected explicitly.
- Unresolved consequential outcomes are represented as Execution `UNKNOWN_OUTCOME` and do not authorize silent reapplication.

## Persistence

Execution persists schema-1 operation records at:

`<world>/butchercraft/execution_operations.json`

The file contains immutable authorization evidence, operation lifecycle state, attempt records, owner result evidence, terminal result evidence, and typed failure state. Live runtime authorization consumption tokens are not persisted.

IM-031A additionally persists Execution-owned Machine Runs at:

`<world>/butchercraft/execution_machine_runs.json`

Machine Run identity, generation, START/STOP evidence, lifecycle, child
sequence and bindings, recovery evidence, and revisions remain distinct from
generic child operation persistence. One active Run may authorize at most one
nonterminal child operation. IM-031B/IM-031C's shared machine-neutral
integration prepares the next Grinder or Patty Former child only after the
preceding child has a proven terminal result; machine-specific adapters retain
recipe authorization and owner-result handling, and the generic Machine Run
owner still does not invent child work itself.

Schema 1 now classifies exact aggregate identity as `IDENTICAL` and permits a
strict additive handler set only through exact historical contract proof. The
owner-ratified
[`DG-003 Execution Handler Registry Evolution ADR`](adr/ADR-PROPOSED-EXECUTION-HANDLER-REGISTRY-EVOLUTION.md)
is implemented for the released pre-Cutting-Table Grinder and Patty Former
profile. Retained operations are validated through their persisted handler id,
operation type, configuration identity, and immutable authorization binding
against that profile. Unknown registry identities remain recovery-blocked,
incompatible contracts fail visibly, unchanged additive-compatible startup
does not rewrite historical persistence, and general Execution migration
remains gated.

## Explicitly Gated

- Allocation-to-Execution handoff.
- Planning-to-Execution handoff.
- Production-owned Execution authorization or lifecycle mutation.
- General workstation or player-facing invocation beyond the promoted Grinder and Patty Former slices.
- Startup recovery orchestration beyond the implemented IM-031A Machine Run /
  Workstation operating-state boundary and existing local unresolved-outcome
  classification.
- Employee Machine START/STOP controls and employee-owned persistent Runs.
  Player Grinder and Patty Former START/STOP/RESUME are the live activations.
- Checkpoint-owned live Execution snapshot publication.
- Evidence archival, retention, or compaction.
- Public handler API or mod/plugin registration surface.
- Compensation or automatic reapplication after Unknown Outcome.
- Migration beyond schema 1.
