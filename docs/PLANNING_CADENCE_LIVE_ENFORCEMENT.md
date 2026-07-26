# Planning Cadence Live Enforcement

Status: IM-010 implementation note

This note describes the live implementation of ADR-04 Deterministic Planning
Cadence. The canonical architecture remains the Constitution, Core Principles,
Architecture Guide, Platform Canonicalization Addendum, and accepted ADR/RFC
documents.

## Live Cadence

Planning owns cadence configuration, trigger consumption, input capture, and
Planning Cycle publication. Scheduler owns Scheduler Work lifecycle and the
runtime operation that moves pending Work to a Planning-approved eligibility
tick.

The live defaults are:

- periodic interval: 1,200 simulation ticks;
- minimum separation: 20 simulation ticks;
- maximum interval: 72,000 simulation ticks;
- pending trigger limit: 1,024 records.

The periodic interval, minimum separation, maximum interval, and trigger limit
are versioned Planning configuration inputs. Their configuration identity is
recorded with cadence evidence.

## Trigger Evidence

Planning accepts explicit source-owned trigger records for facts already read
by the current Planning pipeline. Schema 1 source owners are Orders, Contracts,
Production, Inventory, Business Runtime, Workforce, and Planning configuration.

A trigger binds:

- source owner;
- authoritative simulation tick;
- trigger type;
- source reference;
- source freshness identity;
- canonical payload metadata;
- trigger content identity.

The same Trigger Identity with the same content is a duplicate observation. The
same Trigger Identity with different content is a conflict and is rejected
before publication.

## Execution Boundary

The existing `butchercraft:economic_planning_cycle` Scheduler Work remains the
single Planning continuation Work. On invocation, the handler executes at most
one cadence-eligible Planning Cycle and defers the same Work to the next
Planning-approved eligibility tick.

If loaded cadence is overdue, Planning schedules one next eligible cycle after
the current recovered tick. It does not burst through missed historical
intervals.

## Persistence

Planning still persists the six legacy artifact files. IM-010 adds:

```text
planning_cadence.json
```

The cadence file contains Planning-owned cadence configuration, pending trigger
records, and cadence evidence for cycles produced by the live cadence. Legacy
six-file saves remain loadable and do not receive synthetic historical trigger
evidence.

## Effect Classification

Planning remains `NON_REPEATABLE` under a continuation policy. Reclassification
to `IDEMPOTENT` is intentionally not performed in IM-010 because current
Scheduler Effect Identity is scoped to the persistent Scheduler Work. A proof of
idempotence requires one stable effect identity per Planning Cycle.

## Not Implemented

IM-010 does not implement:

- source-manager automatic event hooks;
- Planning owner checkpoint snapshots;
- Evidence Lifecycle archival;
- generic Execution runtime;
- Allocation integration;
- Planning `IDEMPOTENT` effect reclassification;
- operator recovery for Scheduler Unknown Outcome;
- gameplay or player commands.
