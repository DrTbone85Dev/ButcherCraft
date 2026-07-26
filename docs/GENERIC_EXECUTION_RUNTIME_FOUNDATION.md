# Generic Execution Runtime Foundation

Status: IM-011 implemented foundation.

This note records the first generic Execution runtime implementation slice. It does not change RFC-0023, ADR status, Allocation integration, or public extension contracts. IM-012 later connects the first grinder workstation operation to this runtime, IM-015 extends that same Grinder handler to the promoted Pork Trim process, IM-016 lets Production observe terminal Grinder Execution evidence without acquiring Execution authority, and IM-018 adds the Patty Former handler on the same generic path. General public workstation invocation remains gated.

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

## Explicitly Gated

- Allocation-to-Execution handoff.
- Planning-to-Execution handoff.
- Production-owned Execution authorization or lifecycle mutation.
- General workstation or player-facing invocation beyond the promoted Grinder and Patty Former slices.
- Startup recovery orchestration beyond local unresolved-outcome classification.
- Checkpoint-owned live Execution snapshot publication.
- Evidence archival, retention, or compaction.
- Public handler API or mod/plugin registration surface.
- Compensation or automatic reapplication after Unknown Outcome.
- Migration beyond schema 1.
