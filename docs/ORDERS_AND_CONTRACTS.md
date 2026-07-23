# Orders And Contracts Framework

Status: implemented internal Core architecture, schema version 1

## Purpose

The Orders and Contracts Framework is the industry-neutral language of economic intent in ButcherCraft Core. It describes what Economic Actors request, offer, or promise without changing Inventory or executing Transactions.

The ownership boundary is strict:

```text
Contract -> governs zero or more Orders
Order -> requests an outcome
Future fulfillment system -> performs work and submits Transactions
APPLIED Transaction -> records the authoritative economic mutation
Order allocation -> records what that completed mutation fulfilled
Inventory -> records current quantities
```

Orders and Contracts never reserve or mutate Inventory. They never submit Transactions. Fulfillment recording only interprets an already `APPLIED` Transaction.

Phase 21 Economic Planning is a read-only consumer of accepted, fulfillable
Order lines. It reads exact remaining quantity and subtracts existing
Order-linked Production commitments before creating a Need. Planning never
changes Order or line status and never records fulfillment. A completed
Production Run still requires a separately APPLIED Transaction and an explicit
Order fulfillment allocation through `OrderManager`.

## Domain Model

The pure Java domain is `com.butchercraft.world.economy.order`. It has no Minecraft, NeoForge, ItemStack, block entity, menu, networking, or client dependencies.

Stable identities are `OrderId`, `OrderLineId`, `ContractId`, and `ContractLineId`. Each uses the canonical namespaced identifier format established by the Goods, Actors, Inventory, and Transaction foundations.

`EconomicOrderDefinition` is immutable and contains:

- Identity, display name, type, priority, and schema version.
- Requester and optional counterparty `ActorId` values.
- An optional governing `ContractId`.
- Immutable line definitions.
- Creation, requested-fulfillment, and latest-acceptable simulation ticks.
- Typed tags and bounded metadata.

`OrderLineDefinition` identifies one Good, exact requested quantity, canonical unit, semantic role, optional preferred Inventory ids, substitution-policy metadata, and bounded line metadata.

`EconomicContractDefinition` is immutable and contains:

- Identity, display name, type, and schema version.
- Principal and counterparty `ActorId` values.
- Optional industry scope and immutable commitment lines.
- Effective and optional expiration simulation ticks.
- Schedule, terms, and bounded metadata.

`ContractLineDefinition` identifies one Good, exact commitment quantity, canonical unit, commitment period, optional minimum and maximum, optional variance, and bounded metadata. Pure service lines without a Good are not supported by schema version 1.

## Definition And Runtime Separation

Definitions are never rewritten after authoritative registration. `EconomicOrderRuntime`, `OrderLineRuntime`, and `EconomicContractRuntime` own lifecycle state separately and reference definitions by stable id.

Managers own mutable runtime records. Public runtime access returns detached defensive snapshots. Registry definitions and query results are immutable.

Authoritative drafts are intentionally excluded. A registered Order begins in `SUBMITTED`; draft editing belongs to a future caller or presentation boundary and is not persisted as world truth.

## Order Lifecycle

```text
SUBMITTED -> ACCEPTED -> PARTIALLY_FULFILLED -> FULFILLED
    |           |                 |
    +-> REJECTED+-> CANCELLED <---+
    +-> CANCELLED
    +-> EXPIRED <-----------------+
                +-> FAILED <------+
```

Allowed transitions are:

- `SUBMITTED`: `ACCEPTED`, `REJECTED`, `CANCELLED`, `EXPIRED`.
- `ACCEPTED`: `PARTIALLY_FULFILLED`, `FULFILLED`, `CANCELLED`, `EXPIRED`, `FAILED`.
- `PARTIALLY_FULFILLED`: `FULFILLED`, `CANCELLED`, `EXPIRED`, `FAILED`.
- Terminal states: no transitions.

`FULFILLED` requires every line to have no remaining required quantity. Terminal Orders cannot become active again, and lifecycle ticks cannot move backward.

## Contract Lifecycle

```text
PROPOSED -> ACTIVE <-> SUSPENDED
    |          |          |
    +-> REJECTED          +-> TERMINATED / EXPIRED / FAILED
    +-> TERMINATED
               +-> COMPLETED / TERMINATED / EXPIRED / FAILED
```

Allowed transitions are:

- `PROPOSED`: `ACTIVE`, `REJECTED`, `TERMINATED`.
- `ACTIVE`: `SUSPENDED`, `COMPLETED`, `TERMINATED`, `EXPIRED`, `FAILED`.
- `SUSPENDED`: `ACTIVE`, `TERMINATED`, `EXPIRED`, `FAILED`.
- Terminal states: no transitions.

Only an active Contract can accept a newly governed Order. Parties and Goods must remain within the Contract scope. Schedules and commitment periods are descriptive metadata only: they do not create Orders, queue work, or execute obligations.

## Exact Quantity Policy

`GoodQuantity` uses normalized `BigDecimal` values, never `float` or `double`. Values are locale-independent, have at most 38 digits of precision and 9 fractional digits, reject negatives, and serialize with `toPlainString()` after trailing-zero normalization. Requested, committed, and fulfillment quantities must be positive where the operation requires a contribution.

There is no unit-conversion engine in schema version 1. Every Order line and Contract line must exactly match the referenced Good's canonical `UnitOfMeasure`. A fulfillment allocation must also exactly match the Transaction unit.

The existing Transaction Framework currently records integral `long` quantities. Decimal Order quantities remain valid, but a referenced Transaction can contribute only within its integral authoritative quantity.

## Transaction Allocation

`OrderManager.recordFulfillment` accepts one or more explicit allocation requests containing Order id, line id, Transaction id, exact quantity, and simulation tick.

The operation validates all requests before changing authoritative Order runtime state:

- The Order and line resolve and the Order is fulfillable.
- The Transaction resolves and has status `APPLIED`.
- Good and unit match exactly.
- Allocation quantity is positive.
- Fulfillment and Transaction ticks are monotonic.
- A Transaction is not counted twice against one line.
- Aggregate allocation across all Orders and lines does not exceed the Transaction quantity.
- A line does not exceed its requested quantity unless its governing Contract explicitly permits over-fulfillment.

Validation and application occur against detached staged runtime copies. If any request fails, no Order runtime, line runtime, revision, or allocation total changes. Successful application may update several Orders and lines atomically. Recording fulfillment never calls an Inventory mutation API.

## Registries And Queries

`OrderRegistry` and `ContractRegistry` are immutable, preserve authoritative insertion order, reject duplicate ids, and build deterministic indexes. Common party, Good, type, Contract, industry, and schedule lookups use indexes. Tick-range and current-runtime status queries are deterministic scans in registration order.

`OrderManager` queries include identity, requester, counterparty, either party, Good, type, status, governing Contract, line id, creation range, requested-fulfillment range, open, fulfillable, and overdue Orders. It also derives exact fulfilled and remaining quantities.

`ContractManager` queries include identity, principal, counterparty, either party, Good, type, status, schedule type, industry, active-at-tick definitions, expiration ranges, and governed Order ids.

There is no generic query language. Managers assume serialized server-thread authority and do not claim concurrent mutation safety.

## Validation And Failure Outcomes

Normal domain rejection uses `OrderOperationResult`, `ContractOperationResult`, and typed failure codes. Exceptions are reserved for malformed persistence, programmer contract violations, and impossible loaded state.

Registration validates Actor, Good, Inventory, industry, unit, party, scope, schedule, term, tick, and duplicate references. Capability checks are deliberately narrow: PURCHASE requires requester BUY and known counterparty SELL; SALE and SUPPLY reverse those roles. Other types are not overconstrained until existing Actor capabilities can express their meaning reliably.

Substitution policies are metadata only. No equivalence or approval engine exists.

## Persistence

The lifecycle integration service loads after `TransactionService` and stores:

- `<world>/butchercraft/orders.json`
- `<world>/butchercraft/contracts.json`

Both documents use schema version 1, deterministic field and array ordering, UTF-8 JSON, temporary-file writes, and atomic replacement where the filesystem supports it. Missing files load as empty registries.

`orders.json` stores immutable definitions, submission order, runtime status, line allocations, transaction references, lifecycle ticks, reasons, revisions, and schema versions. `contracts.json` stores immutable definitions, registration order, runtime status, activation and termination ticks, governed Order references, period identity, reasons, revisions, and schema versions.

Indexes, caches, validation results, scheduler queues, GUI state, networking state, ItemStacks, future Orders, prices, and invoices are not persisted.

Load order is Goods, Economic Actors, Inventory, Transactions, then coordinated Contracts and Orders. Contracts load first as a candidate manager; Orders then load and validate both sides of every Contract reference. Neither manager is published by `OrderContractService` until the complete snapshot validates. Malformed JSON, unsupported schemas, duplicate records, missing runtimes, unknown references, illegal lifecycle state, invalid allocation totals, and backward ticks fail visibly.

## Production Context

Phase 20 Production Plans may carry an optional requesting `OrderId` and governing `ContractId`. Registration validates those references, but they are context only. A Production Run does not reserve an Order line, interpret a Contract schedule, allocate its completion Transaction, or change either lifecycle. A separate future fulfillment owner must deliberately allocate an already APPLIED Production Transaction through the existing Order validation path.

## Subsystem Invariants

- `OC-0001`: An Order definition is immutable after authoritative registration.
- `OC-0002`: A Contract definition is immutable after authoritative registration.
- `OC-0003`: Lifecycle state exists only in runtime records.
- `OC-0004`: An Order never mutates Inventory.
- `OC-0005`: A Contract never mutates Inventory.
- `OC-0006`: Only APPLIED Transactions may contribute to fulfillment.
- `OC-0007`: A Transaction allocation cannot be counted beyond its authoritative quantity.
- `OC-0008`: Fulfillment quantities are exact and deterministic.
- `OC-0009`: A fulfilled Order has no remaining required quantity.
- `OC-0010`: Terminal Orders cannot return to active states.
- `OC-0011`: Terminal Contracts cannot return to active states.
- `OC-0012`: Simulation ticks never move backward.
- `OC-0013`: Required Actor, Good, Inventory, Transaction, Order, and Contract references resolve.
- `OC-0014`: Contract schedules describe obligations but do not execute them.
- `OC-0015`: Order and Contract APIs expose immutable views.

## Performance Characteristics

The registries index common equality lookups and preserve deterministic insertion order. The Phase 18 stress suite measured the following split scenarios in the development environment:

- 100,000 Orders and 1,000,000 line references registered and indexed in 0.800 seconds.
- 25,000 Contracts and 250,000 line references registered and indexed in 0.360 seconds.
- 1,000,000 ordered unique fulfillment allocations validated in 0.212 seconds.

These measurements are environment observations, not universal latency guarantees. Persistence was tested on representative data; the full combined stress dataset is intentionally split to keep CI memory bounded.

## Illustrative Uses

- A retailer can submit a replenishment Order for a stable Good id.
- A processor can submit a supply offer without exposing machine recipes.
- A restaurant can register a recurring supply Contract whose weekly schedule remains descriptive.
- A warehouse can request an internal transfer without moving Inventory directly.
- A Production Plan may identify an Order as context and later expose its APPLIED completion Transaction for a separate fulfillment decision.
- A future carrier Contract can govern delivery Orders without implementing routing.
- A MineColonies compatibility module can translate bounded external demand into an Order while retaining source-mod ownership.

These are examples only. No built-in Orders, Contracts, actors, goods, automation, or gameplay are added by Phase 18.

## Future Extension Points

Future milestones may add reservation ownership, automatic Production planning, logistics consumers, schedule execution, fulfillment coordination, market matching, pricing and accounting systems, public compatibility APIs, and player-facing presentation. Those systems must remain separate owners, use stable ids, submit economic mutations through Transactions, and preserve the distinction between intent, obligation, fact, and current quantity.

Schema version 1 does not provide service-only Contract lines, unit conversion, substitution execution, automatic schedule processing, maximum-open-order enforcement, breach logic, rollback, or event-sourced reconstruction.

## Supporting Documents

- `CONSTITUTION.md`
- `PROJECT_RULES.md`
- `TECHNICAL_ARCHITECTURE.md`
- `ECONOMY_MODEL.md`
- `SIMULATION_MODEL.md`
- `docs/GOODS_FRAMEWORK.md`
- `docs/ECONOMIC_ACTORS.md`
- `docs/INVENTORY_FRAMEWORK.md`
- `docs/TRANSACTION_FRAMEWORK.md`
- `docs/PRODUCTION_FRAMEWORK.md`
