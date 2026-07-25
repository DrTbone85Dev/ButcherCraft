# Minimal Live Owner Snapshot Integration

IM-006 integrates the checkpoint foundation with the smallest live BCSE owner
set: Simulation Clock and Simulation Scheduler. World Identity remains an
immutable external root that checkpoint generations reference by identity,
schema version, and digest.

This milestone does not replace existing save files, add automatic checkpoint
cadence, add startup recovery, expose commands, migrate saves, or change
gameplay.

IM-007 later adds a development-only diagnostic command adapter for explicit
capture, list, validate, and inspect operations. That adapter does not change
the IM-006 owner snapshot contracts and does not make live loaded-world
restoration public or automatic.

## Owner Snapshot Contracts

Checkpoint owners implement narrow provider and restorer contracts.

Providers supply:

- owner id
- owner snapshot schema version
- snapshot identity
- Configuration Identity
- opaque canonical payload bytes
- expected payload digest
- required participation state
- owner validation metadata

Restorers receive:

- validated owner snapshot descriptor
- opaque payload bytes
- selected `CheckpointGenerationId`
- Platform Determinism Manifest reference
- World Identity root reference

Each owner parses, validates, and prepares its own restoration candidate.
Checkpoint Recovery coordinates the candidates and never parses owner payload
internals.

## Integrated Owners

Simulation Clock owns the checkpoint payload for deterministic Clock state. The
payload wraps the existing `SimulationState` schema-1 JSON and the Clock
configuration identity. It contains the authoritative simulation tick, derived
calendar, and pending Clock-owned calendar events. It contains no wall-clock
timestamps.

Simulation Scheduler owns the checkpoint payload for Scheduler runtime state.
The payload wraps Scheduler schema-2 JSON and the Scheduler
configuration identity. It contains stage definitions, Work definitions, Work
runtime records, Invocation Identity and Effect Identity metadata where present,
owner result observation metadata where present, next submission sequence, and
last finalized simulation tick. Existing Scheduler validation still rejects
persisted `RUNNING` Work and preserves `UNKNOWN_OUTCOME` records without
automatic reinvocation.

World Identity owns deterministic external-root digest derivation. Checkpoint
Recovery consumes the resulting root reference but does not serialize,
rewrite, migrate, or replace World Identity.

## Coordinated Restoration

Restoration uses a two-step boundary:

1. Checkpoint Recovery supplies each required owner its opaque payload.
2. Each owner validates the payload and prepares a restoration candidate.
3. Checkpoint Recovery validates cross-owner relationships.
4. Every candidate must pass pre-publication validation.
5. Only then are owner candidates published.

If any owner fails validation or pre-publication checks, restoration returns a
Recovery-Blocked State and no owner publishes recovered state.

If an owner publication fails after publication begins, Checkpoint Recovery
invokes the owner-supplied rollback hook for every attempted candidate before
returning Recovery-Blocked State. If rollback succeeds, no owner remains
published. If an owner cannot roll back its own publication, the report exposes
typed partial-restoration diagnostics.

## Cross-Owner Validation

IM-006 validates only relationships:

- Clock snapshot tick matches Scheduler finalized tick.
- Required Clock and Scheduler owners both participate.
- Duplicate owner providers, restorers, or payloads are rejected.
- Owner snapshot identities match generation metadata.
- Owner configuration identities match owner metadata.
- World Identity root and Platform Determinism Manifest references match the
  selected generation.

Checkpoint Recovery does not validate Clock or Scheduler internals directly.

## Existing Save Compatibility

The existing save paths remain unchanged:

- `<world>/butchercraft/simulation_state.json`
- `<world>/butchercraft/simulation_scheduler.json`
- Overworld `SavedData` named `butchercraft_world_identity`

No existing world requires checkpoint files to load. No checkpoint-generated
state silently overrides existing saved state.

## Explicit Invocation Only

IM-006 exposes explicit Java APIs and tests for capture, publication, recovery,
and coordinated restoration. It does not register save hooks, startup recovery,
automatic cadence, gameplay commands, or operator UI.

IM-007 exposes only development diagnostic commands and a controlled Java
harness. The harness can prove coordinated Clock/Scheduler restoration, while
the live command surface rejects restoration until a safe runtime boundary is
authorized.

## Still Gated

- Inventory owner snapshots
- Transaction owner snapshots
- Planning owner snapshots
- Production owner snapshots
- Allocation integration
- Execution integration
- Evidence archive integration
- automatic checkpoint scheduling
- existing save-hook replacement
- startup recovery
- migration
- runtime operator rollback
- cold archives
- full Platform Determinism Manifest collection
- gameplay-facing recovery
- live loaded-world restore command
