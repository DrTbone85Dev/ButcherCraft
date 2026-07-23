# ButcherCraft Simulation Model

Status: conceptual platform model; no new implementation

## Regional Flow

```text
World
  -> Regions
  -> Counties
  -> Settlements
  -> Population
  -> Businesses
  -> Workforce
  -> Production
  -> Distribution
  -> Consumers
  -> Markets
  -> Economy
```

This is a flow of responsibility and influence, not a single object graph and not a required execution order for every event.

## World

The world owns the stable seed, the immutable World Identity snapshot, and independent schema-versioned runtime stores. It provides the context in which all regional simulation occurs.

## Regions

Regions provide broad identity, naming, geography, and future economic context. They group counties and establish stable references; they do not directly perform production or calculate markets.

## Counties

Counties group settlements and provide a future scale for institutions, infrastructure, services, reporting, and local policy. County identity remains separate from mutable county-level simulation state.

## Settlements

Settlements are the local anchors for population, properties, businesses, demand, and services. Existing settlement records are immutable identity. Future growth or decline belongs in separate runtime state.

## Population

Population will describe bounded demographic and consumption summaries. It should create demand and workforce availability without requiring every resident to be a continuously simulated entity.

Population simulation is not implemented.

## Businesses

Businesses connect immutable identity to mutable operations. Business Identity records who and what a business is; Business Runtime records whether and how it is operating at a point in simulated time.

## Workforce

Workforce definitions describe positions, qualifications, shifts, and staffing requirements. Future workers may occupy those positions. Workforce structure does not itself create employees, AI, payroll, or productivity.

## Production

The Phase 20 Production Framework now provides industry-neutral immutable Process and Plan definitions, separately owned Run lifecycle, exact deterministic progress, one execution-stage scheduler handler, and atomic multi-input/multi-output completion through the Transaction Framework. It registers no live industry Processes and does not connect existing workstation gameplay. Industry rules, equipment, utilities, planning, and player interaction remain future module responsibilities.

## Distribution

Distribution will move goods between producers, warehouses, retailers, restaurants, and other consumers. Historical supply relationships already provide identity context, and Inventory now records current ownership and location. Active transport, inventory movement, routing, and logistics remain future systems.

## Inventory And Storage

Economic Actors own immutable inventory-container identities. Each container is located at a hierarchical Storage Node and has separate mutable runtime entries containing exact Good quantities. Capacity validation constrains candidate runtime states, but Inventory does not schedule, route, reserve, produce, consume, spoil, price, or render Goods.

## Transactions

Economic transactions are the universal mutation boundary for runtime Good quantities. Future systems decide why a change is requested, then submit an immutable transaction. Validation proves references, endpoints, status, underflow, and capacity before atomic execution. Applied and rejected audit records retain deterministic submission order and can support explicit replay from a compatible baseline. Transactions do not decide production, demand, logistics, markets, or gameplay.

## Orders And Contracts

Orders are explicit economic intent between Economic Actors. Contracts are durable agreements that may govern Orders. Runtime lifecycles and exact line fulfillment remain separate from immutable definitions. Fulfillment records reference APPLIED Transactions and therefore interpret completed economic facts without becoming a second Inventory mutation path.

Contract schedule metadata does not execute obligations. Future production, logistics, markets, and compatibility systems may consume these contracts, but Phase 18 adds no automatic behavior or gameplay.

## Deterministic Work Pipeline

The Phase 19 scheduler provides one bounded industry-neutral execution order for future subsystem Work. The authoritative Simulation Clock supplies time; the scheduler never advances it. Immutable Work definitions and separately owned runtime lifecycle records move through preparation, obligation evaluation, planning, execution, observation, and finalization stages using stable ordering and explicit budgets.

The live pipeline now has one internal `butchercraft:production_run` handler. It advances only explicitly registered Production Runs and calls their authorized domain manager; it does not create Plans, choose industry work, or interpret Contract schedules. Inventory quantity changes still use Transactions, and Contract metadata remains inert until a later milestone deliberately schedules evaluation.

## Consumers

Consumers will convert population and business needs into bounded demand. They may be represented by aggregate cohorts, institutions, businesses, players, or compatible mods. Consumer demand must be explainable and must not require per-tick simulation of every resident.

## Markets

Markets will reconcile available supply, active demand, location, time, and constraints. A market is not synonymous with a GUI, shop block, or global price table. Market state belongs to a future versioned runtime system.

## Economy

The economy is the resulting network of production, consumption, trade, services, capacity, competition, and change. No single manager should own all economic behavior. The economy emerges from focused services exchanging explicit records and events.

Economic Actors now provide the immutable identity, capabilities, Good relationships, and separate in-memory runtime status for future participants. They define who may participate, not how supply, demand, production, storage, transport, trade, or pricing behaves.

## Identity And Runtime Separation

```text
Immutable identity                 Mutable runtime
------------------                 ---------------
Region, county, settlement   --->  Future population and regional state
Business identity            --->  Business Runtime
Workforce definition         --->  Future worker occupancy
Supply relationship history  --->  Future orders and shipments
Product definition           --->  Inventory and production state
```

Runtime records reference identity by stable id. They do not edit historical identity to represent a current condition.

## Event Flow

1. The authoritative Simulation Clock advances on the server.
2. The Work scheduler promotes a bounded due prefix and executes stable stages.
3. Registered focused handlers evaluate only the state they own.
4. The clock's calendar scheduler emits due rollover events in deterministic order.
5. Proposed economic quantity changes are submitted as immutable transactions.
6. Accepted transaction changes execute atomically in deterministic order.
7. Committed state and audit history are persisted by their owning services.
8. Downstream facts are published as narrow events or immutable summaries.

This model avoids one global tick method that scans and mutates every subsystem.

## Simulation Constraints

- Systems use the shared simulation clock rather than independent time models.
- Work is bounded and scheduled at an appropriate interval.
- Ordering is deterministic and testable.
- Transactions prevent partial material or inventory changes.
- Failures are explicit and attributable.
- Server state is authoritative.
- Persistence ownership is unambiguous.
- Aggregate simulation is preferred where individual entities add cost without meaningful gameplay.
- Industry modules consume shared state through contracts; they do not bypass ownership boundaries.

## Current Boundary

World Identity, Runtime Player Identity, Simulation Clock, deterministic Work scheduler/pipeline, Business Runtime, Workforce definitions, immutable economic Goods definitions, Economic Actor definitions/runtime capabilities, economic Inventory/Storage runtime quantities, Transactions, Orders and Contracts, and the industry-neutral Production Framework exist. No live industry Process definitions are registered. Population, automatic production planning, active distribution behavior, consumers, markets, and pricing remain future work.
