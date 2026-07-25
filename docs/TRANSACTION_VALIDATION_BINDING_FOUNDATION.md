# Transaction Validation Binding Foundation

Status: Implemented foundation; live runtime integration implemented by IM-008

Source architecture:

- `docs/adr/ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md`
- `docs/adr/ADR-PROPOSED-TRANSACTION-VALIDATION-AUTHORITY.md`

## Scope

IM-004 added pure Java Transaction validation binding primitives.

IM-008 wires those primitives into live `TransactionManager`,
`TransactionValidator`, `TransactionExecutor`, replay, duplicate/conflict
handling, and Production observation. See
`docs/LIVE_TRANSACTION_VALIDATION_BINDING.md`.

Transaction persistence remains schema 1. Durable binding/result-evidence
migration, Transaction/Inventory checkpoint participation, startup recovery,
Scheduler effect enforcement, Planning cadence, Execution, Allocation,
gameplay, resources, and UI remain outside this foundation document.

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

The following remain unauthorized by IM-004 and IM-008:

- Persistent Transaction result evidence schema changes.
- Transaction/Inventory checkpoint publication or generation selection.
- Startup recovery or post-consumption crash recovery.
- Evidence Lifecycle retention integration.
- Scheduler effect enforcement.
- Planning cadence changes.
- Execution or Allocation integration.
- Gameplay behavior.
