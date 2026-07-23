# Transaction Framework

Status: v0.9.0 Phase 17 foundation

The Transaction Framework is ButcherCraft Core's universal pure Java mutation pipeline for runtime economic quantities. Transactions describe a requested change, validation proves that the request is currently valid, and execution applies the accepted change atomically through `InventoryManager`.

It defines how simulation state changes, not why a change is requested. It does not implement production, logistics, markets, purchasing, sales, delivery, consumption, spoilage, pricing, accounting, scheduling, AI, networking, GUI, or gameplay.

## Transaction Philosophy

```text
Current State
  -> immutable EconomicTransaction
  -> TransactionValidator
  -> accepted TransactionValidation
  -> TransactionExecutor
  -> atomic InventoryManager mutation
  -> New State
```

Future runtime systems must submit transactions instead of directly changing inventory quantities. The transaction layer provides one deterministic boundary for validation, execution, auditing, persistence, and replay.

Transaction definitions are immutable. Registry history stores the final status of every structurally valid submitted transaction. Runtime validation failures are retained as rejected audit records. Structurally invalid requests, including unknown references and duplicate ids, are rejected before entering durable history so persistence cannot contain references that it cannot reload.

## Schema

Transaction schema version 1 contains:

- stable `TransactionId`
- additive `TransactionType`
- optional source and destination `ActorId`
- optional source and destination `InventoryId`
- one `GoodId`
- exact non-negative `long` quantity
- canonical `UnitOfMeasure`
- non-negative simulation tick
- `TransactionStatus`
- immutable typed `TransactionMetadata`
- schema version

The four executable Phase 17 types are inventory add, inventory remove, inventory transfer, and inventory adjustment. An adjustment uses exactly one inventory endpoint: a source means removal and a destination means addition.

Production, purchase, sale, delivery, consumption, spoilage, manual, and system types are additive schema reservations. They are not executable until a focused milestone defines their ownership and invariants.

Statuses are pending, validated, applied, rejected, and rolled back. Phase 17 performs pending, validated, applied, and rejected transitions. Rolled back is reserved for future additive behavior; no rollback mechanism or undo stack exists.

## Metadata

`TransactionMetadata` provides optional typed text fields for:

- reason
- reference id
- user
- external system
- comments

Values are normalized, length-bounded, immutable, and persisted. Metadata supplies audit context only and cannot dispatch gameplay behavior or bypass validation.

## Validation Pipeline

`TransactionValidator` performs validation without mutation.

Structural and reference validation checks:

- positive quantity
- known Good
- exact canonical unit
- known optional actors
- known optional inventories
- actor ownership when an actor and inventory are supplied for the same endpoint
- valid add, remove, transfer, and adjustment endpoint shapes

Runtime validation delegates candidate inventory-state checks to `InventoryManager` and verifies:

- inventory status permits the requested change
- simulation ticks do not move backward
- source quantity is sufficient
- target and aggregate storage capacity remain valid
- all changes in a transfer are valid as one candidate batch

Validation returns an immutable `TransactionValidation` containing the transaction id, acceptance result, explicit failure code and messages, and the exact inventory change plan. Validation does not reserve stock or schedule execution.

## Execution Pipeline

`TransactionExecutor` accepts only a transaction in `VALIDATED` status and the matching previously accepted `TransactionValidation`. It rechecks the candidate immediately before execution and applies the accepted inventory changes through an executor-only authorization token.

`InventoryManager` no longer exposes direct public add or remove methods. Runtime access returns defensive snapshots. Its authorized batch application validates every source and destination before replacing any runtime entries, then commits affected inventories in deterministic inventory-id order.

This provides atomic add, remove, adjustment, and transfer behavior. Capacity failure, underflow, invalid status, invalid tick, or changed state produces no inventory mutation. Phase 17 does not implement post-commit rollback because deterministic prevalidation leaves no expected commit-time failure point.

## Results

`TransactionResult` contains:

- success state
- optional `TransactionFailureCode`
- validation messages
- immutable applied-change summaries
- execution tick

Applied-change summaries identify the inventory, add or remove direction, Good, exact quantity, and canonical unit. Failed results never claim applied changes.

## Audit Registry

`TransactionRegistry` provides duplicate-safe registration, lookup, status replacement, type and status queries, deterministic history, and streaming.

History order is authoritative submission order. Status replacement retains the original position. Persistence preserves this array order, and replay consumes the same order. Transaction ids must be globally unique within one world history.

## Persistence

Transaction history persists at:

```text
<world>/butchercraft/transactions.json
```

The root and every transaction record use schema version `1`. The file stores complete transaction definitions, final statuses, metadata, and simulation ticks. Optional fields are written explicitly as JSON nulls so the schema shape remains stable.

`TransactionStorage` writes deterministic pretty-printed JSON through a temporary file and atomically replaces the active file where supported. Loading rejects duplicate ids, malformed JSON, missing fields, unsupported schemas, unknown enums, invalid quantities, invalid units, unknown Goods, actors, or inventories, and invalid endpoint references.

Undo stacks, validation objects, applied-change caches, inventory snapshots, and temporary execution state are not persisted.

`TransactionService` depends on `InventoryService`, initializes afterward, and owns world lifecycle loading and saving. Only the service imports Minecraft and NeoForge APIs; `com.butchercraft.world.transaction` remains pure Java.

## Replay Philosophy

`TransactionManager.replayInto` applies only historical transactions whose final status is `APPLIED`. It converts each immutable historical record into an execution candidate, validates it against the supplied baseline inventory, and executes it in authoritative history order.

Replay is fail-fast. A baseline that does not match the history's expected starting state, a missing definition, or a changed invariant causes an explicit failure rather than silently skipping or rewriting history. Rejected and rolled-back records are not applied.

Phase 17 does not persist a world genesis inventory snapshot or automatically rebuild active inventory during startup. Replay is an explicit deterministic tool for tests, diagnostics, and future migration or accounting work.

## Examples

### Inventory Transfer

A transfer names distinct source and destination inventories, a Good, quantity, canonical unit, and tick. Validation proves source availability and destination capacity as one batch. Execution removes and adds atomically.

### Inventory Adjustment

A destination-only adjustment adds stock. A source-only adjustment removes stock. Metadata should explain the reason and external reference. Adjustment is not a substitute for a future purchase, sale, production, or spoilage transaction once those systems exist.

### Future Production

A future production system will submit a production transaction backed by an accepted input/output plan. Phase 17 reserves the type but defines no recipes, yields, workstation integration, or execution behavior.

### Future Sale

A future market or retail system will own pricing, payment, customer, and accounting rules. It will submit validated inventory and financial changes through a later transaction extension.

### Future Delivery

A future logistics system will own routing, custody, timing, and transport state. Delivery transactions will describe accepted state changes, not perform vehicle simulation.

### Future Spoilage

A future spoilage system will determine when and why inventory changes. It will submit explicit spoilage transactions instead of directly reducing quantities.

## Extension Rules

- New transaction types and failure codes are additive.
- New executors must preserve validation-before-execution and explicit failure results.
- Inventory mutation must remain server-authoritative and transaction-owned.
- Transaction metadata must not become an untyped gameplay dispatch channel.
- Minecraft inventory and ItemStack adaptation belongs outside the pure transaction domain.
- Financial state, accounting entries, reservations, routing, and scheduling require focused owners and schemas.
- Durable schema changes require explicit migration behavior before release.

## Out Of Scope

- production and workstation execution
- markets, prices, accounting, payments, orders, and contracts
- logistics, transportation, routing, delivery, and custody simulation
- consumption and spoilage logic
- reservations and scheduling
- rollback and undo implementation
- AI, automation, networking, GUI, and gameplay
- Minecraft inventories, slots, menus, containers, and ItemStacks
