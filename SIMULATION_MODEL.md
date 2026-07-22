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

Production will transform inputs into outputs under industry rules, facility capacity, workforce, utilities, schedules, and transaction guarantees. Existing workstation transformations prove local product execution but are not yet a regional production scheduler.

## Distribution

Distribution will move goods between producers, warehouses, retailers, restaurants, and other consumers. Historical supply relationships already provide identity context; active transport, inventory movement, and logistics are future systems.

## Consumers

Consumers will convert population and business needs into bounded demand. They may be represented by aggregate cohorts, institutions, businesses, players, or compatible mods. Consumer demand must be explainable and must not require per-tick simulation of every resident.

## Markets

Markets will reconcile available supply, active demand, location, time, and constraints. A market is not synonymous with a GUI, shop block, or global price table. Market state belongs to a future versioned runtime system.

## Economy

The economy is the resulting network of production, consumption, trade, services, capacity, competition, and change. No single manager should own all economic behavior. The economy emerges from focused services exchanging explicit records and events.

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
2. The scheduler emits due events in deterministic order.
3. Focused services evaluate only the state they own.
4. Proposed changes are validated before commit.
5. Committed state is persisted by its owning service.
6. Downstream facts are published as narrow events or immutable summaries.

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

World Identity, Runtime Player Identity, Simulation Clock, Business Runtime, Workforce definitions, and immutable economic Goods definitions exist. Population, quantities, inventory, regional production, active distribution, consumers, markets, and the economy remain conceptual future work.
