# Live Scheduler Effect Enforcement

Status: IM-009 implementation note

This note describes the live implementation of ADR-05 Scheduler Effects
Authority. The canonical architecture remains the Constitution, Core
Principles, Architecture Guide, Platform Canonicalization Addendum, and
accepted ADR/RFC documents.

## Runtime Authority

`SimulationSchedulerManager` owns the world-scoped Scheduler Runtime Authority.
`SimulationPipeline` must acquire that authority before dispatch. Recursive,
nested, or parallel execution for the same manager is rejected with
`SCHEDULER_AUTHORITY_ALREADY_EXECUTING`.

The Scheduler Runtime Authority covers Scheduler Work eligibility, dispatch
ordering, attempt lifecycle, effect-policy enforcement, retry legality,
generated Work validation, Scheduler runtime transitions, and Scheduler-owned
runtime failure state. It does not grant authority over Inventory,
Transactions, Production, Planning, Allocation, or Execution.

## Identity Model

Every live handler attempt receives `SchedulerInvocationIdentity`. It binds:

- Scheduler Work identity;
- handler type;
- attempt number;
- authoritative simulation tick;
- canonical payload digest;
- effect-policy identity.

Consequential handlers also receive `SchedulerEffectIdentity`. Effect Identity
represents the logical effect and intentionally does not include the attempt
number or execution tick. A retry may therefore have a new Invocation Identity
while retaining the same Effect Identity.

## Effect Matrix

| Effect type | Live behavior |
| --- | --- |
| `READ_ONLY` | May complete from the handler result, may retry by deterministic Scheduler policy, and may generate Scheduler Work within existing generation limits. It must not publish owner effect observation. |
| `IDEMPOTENT` | Requires stable Effect Identity and compatible owner result observation before completion or generated Work. Same identity/content is safe; same identity/different content is conflict. |
| `TRANSACTION_BACKED` | Requires stable Effect Identity and authoritative Transaction result evidence before completion. Scheduler observes the evidence; Transaction remains the only mutation owner. |
| `NON_REPEATABLE` | Is exceptional. Automatic retry is not permitted after invocation begins. Consequential uncertainty becomes `UNKNOWN_OUTCOME`. |

Planning currently uses a narrowly explicit `NON_REPEATABLE` continuation policy
that allows deferral while Planning Cadence remains gated. IM-009 does not
implement Planning Cadence.

## Unknown Outcome

`UNKNOWN_OUTCOME` means the platform cannot prove whether a consequential effect
occurred. It is a Scheduler runtime state, not a quarantined storage artifact.

Consequences:

- no automatic retry;
- no automatic reinvocation;
- terminal Scheduler runtime visibility;
- persisted Scheduler metadata;
- Clock/Scheduler checkpoint round-trip preservation;
- future owner/operator reconciliation required.

## Production Integration

Production Scheduler Work declares `TRANSACTION_BACKED`. Successful Production
completion still uses one APPLIED Production Transaction. Production returns a
Scheduler effect observation only after the Transaction owner publishes
authoritative result evidence.

Production completion Transaction identity is stable for the logical Run
completion: `<production_run_id>/completion`. Reinvocation cannot create a new
attempt-numbered Transaction ID for the same logical completion effect.

## Persistence And Checkpoint

Scheduler persistence is schema 2. Runtime records include last Invocation
Identity, last Effect Identity, effect policy identity, and owner result
observation metadata where present. Schema 1 documents remain loadable as
legacy records without fabricated identity or owner evidence.

The Scheduler checkpoint owner snapshot wraps Scheduler schema-2 persistence.
The Scheduler configuration identity binds handler effect policy identity.
Checkpoint Recovery still treats Scheduler payload bytes as owner-owned
content and does not parse Scheduler internals directly.

## Gated Work

IM-009 does not implement:

- Planning Cadence;
- generic Execution runtime;
- Allocation integration;
- operator Unknown Outcome reconciliation;
- automatic checkpoint cadence;
- startup recovery;
- player-facing commands or gameplay features.
