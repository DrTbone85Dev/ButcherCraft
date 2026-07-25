# Live Transaction Validation Binding

Status: Implemented live runtime foundation

Source architecture:

- `docs/adr/ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md`
- `docs/adr/ADR-PROPOSED-TRANSACTION-VALIDATION-AUTHORITY.md`
- `docs/TRANSACTION_VALIDATION_BINDING_FOUNDATION.md`

## Scope

IM-008 wires the Transaction Validation Binding foundation into live
`TransactionManager` submission, validation, execution, replay, duplicate
handling, and Production observation.

It does not add Transaction or Inventory checkpoint owners, startup recovery,
durable result-evidence migration, Scheduler effect enforcement, Planning
cadence changes, Execution, Allocation, gameplay, resources, or UI.

## Live Flow

1. A caller submits an immutable `EconomicTransaction` proposal.
2. Transactions compute the canonical `TransactionProposalIdentity`.
3. Transactions ask Inventory for a scoped `InventoryFreshnessIdentity`
   covering the Inventory state, container facts, storage facts, Goods, and
   actors examined by validation.
4. Validation produces the ordered immutable Inventory change plan.
5. Transactions compute the `TransactionValidationPlan` and bind it to the
   proposal and freshness identities.
6. The Transaction manager issues a private live validation grant for the
   accepted binding.
7. The executor verifies proposal identity, current Inventory freshness,
   Validation Plan Identity, binding content, lifecycle state, and authority
   availability inside the serialized Transaction-owned application boundary.
8. Authority is consumed before Inventory commit begins.
9. Inventory applies only the bound staged changes.
10. Transactions publish `AuthoritativeTransactionResultEvidence` for bound
    terminal outcomes.

## Duplicate And Conflict Behavior

The Transaction ID remains separate from Proposal Identity.

- Same Transaction ID and same Proposal Identity observes the stored
  authoritative live result and does not reapply Inventory changes.
- Same Transaction ID and different Proposal Identity returns an explicit
  `TRANSACTION_IDENTITY_CONFLICT` and does not mutate Inventory.
- Concurrent identical submissions are serialized by `TransactionManager`; at
  most one submission applies and later callers observe the duplicate result.

Persisted schema-1 history from before live binding has no durable result
evidence. A duplicate observation after loading such history is rejected with
`PERSISTENCE_COMPATIBILITY_FAILURE` instead of fabricating evidence.

## Authority Boundary

`ValidationConsumptionAuthority` remains package-private in the Transaction
binding package. Public validation evidence does not contain it and cannot
consume it.

The live manager creates a package-private `LiveTransactionValidation` wrapper
for manager-to-executor handoff. The wrapper carries a non-persisted grant that
can be consumed only through the Transaction-owned execution authority. Public
`TransactionExecutor.execute(EconomicTransaction, TransactionValidation)` does
not mutate Inventory because immutable validation evidence alone is not
authority.

## Production And Scheduler

Production still submits a normal `EconomicTransaction` for Run completion.
Successful completion now requires the returned `TransactionResult` to include
Transaction-owned result evidence for the completion transaction. Production
does not validate plans, issue authority, consume authority, or mutate
Inventory directly.

Scheduler behavior is unchanged. Scheduler can invoke work that submits or
observes Transactions, but it receives no Validation Consumption Authority.

## Persistence And Checkpoint Limits

Transaction persistence remains schema 1 and stores transaction history,
status, metadata, and ordered production change plans. It does not persist
Validation Consumption Authority or durable binding/result evidence.

Replay from a supplied baseline revalidates and rebinds applied history through
the live path. Full migration of historical result evidence, Transaction and
Inventory checkpoint participation, post-consumption crash recovery, startup
recovery, and checkpoint reconciliation remain gated by later milestones.

Existing gameplay-visible valid Transaction outcomes are intended to remain
unchanged; IM-008 strengthens validation authority and observation semantics.
