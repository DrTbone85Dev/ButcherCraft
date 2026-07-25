# Transaction Validation Binding Foundation

Status: Implemented foundation only

Source architecture:

- `docs/adr/ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md`
- `docs/adr/ADR-PROPOSED-TRANSACTION-VALIDATION-AUTHORITY.md`

## Scope

IM-004 adds pure Java Transaction validation binding primitives. It does not change live Transaction submission, validation, execution, replay, persistence, schemas, Scheduler integration, Planning integration, Production behavior, Execution behavior, Allocation behavior, Checkpoint publication, gameplay, resources, or UI.

The existing live Transaction path remains authoritative until a later milestone explicitly wires these primitives into `TransactionManager`, `TransactionValidator`, `TransactionExecutor`, replay, and persistence.

## Foundation Types

`com.butchercraft.world.transaction.binding` owns Transaction-side binding primitives:

- Proposal Identity for immutable Transaction proposal content.
- Validation Plan Identity for exact approved staged mutations and explicit preconditions.
- Transaction Validation Binding for Proposal Identity, Inventory Freshness Identity, Validation Plan Identity, and explicit validation inputs.
- Validation Consumption Authority as a private, package-local, single-use runtime primitive.
- Authoritative Transaction Result Evidence binding Transaction identity, Proposal Identity, starting Inventory Freshness Identity, Validation Plan Identity, terminal result, explicit validation inputs, and resulting Inventory freshness evidence.
- Duplicate observation and conflict classification primitives.
- Typed validation failures for missing components, mismatched identities, hidden inputs, unsupported schema, duplicate conflicts, consumed authority, and evidence mismatches.

`com.butchercraft.world.inventory.freshness` owns Inventory-side freshness primitives:

- Inventory Freshness Component.
- Inventory Freshness Identity.

Inventory Freshness Identity is source-owned and deterministic. It intentionally does not require or introduce a global Inventory revision.

## Rules Enforced

- Transaction identity and Proposal Identity are distinct.
- Proposal Identity ignores mutable Transaction status.
- Proposal Identity changes when canonical proposal content changes.
- Inventory Freshness Identity is a source-owned freshness identity, not a global revision.
- Validation Plan Identity is immutable and changes when the staged mutation plan changes.
- Binding validation requires Proposal Identity, Inventory Freshness Identity, Validation Plan Identity, and every explicit validation input.
- Hidden validation inputs are reported as typed failures.
- Same Transaction identity with the same canonical Proposal Identity is a duplicate observation.
- Same Transaction identity with a different canonical Proposal Identity is an explicit conflict.
- Validation Consumption Authority is single-use and cannot be reconstructed from immutable result evidence.
- Result evidence must match its calculated content digest and the binding identities it claims.

## Explicit Deferrals

The following remain unauthorized by IM-004:

- Live Transaction validation binding integration.
- Serialized Transaction-owner boundary implementation.
- Persistent Transaction result evidence schema changes.
- Replay migration to explicit recovered baselines.
- Checkpoint publication or generation selection.
- Evidence Lifecycle retention integration.
- Scheduler, Planning, Production, Execution, or Allocation integration.
- Gameplay behavior.
