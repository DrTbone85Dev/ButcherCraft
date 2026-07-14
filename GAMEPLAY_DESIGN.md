# ButcherCraft Gameplay Design

Status: proposed planning document

## Design Goals

ButcherCraft should feel like a Minecraft facility-management mod where physical layout, product handling, employee skill, storage, and cleanliness all matter. The player should be able to trace outcomes back to understandable causes.

Numerical balance values are intentionally not defined yet. Thresholds, formulas, timers, payment amounts, spoilage rates, skill curves, inspection scores, equipment capacities, and MCDA penalty amounts are later design decisions.

## Basic Gameplay Loop

1. Acquire or receive a meat input through vanilla-compatible tags or a basic mod item.
2. Process the input manually or through a machine.
3. Package the result.
4. Store the product cold enough to preserve freshness.
5. Fulfill a customer order.
6. Earn payment and reputation.
7. Clean the facility and maintain equipment.
8. Expand production, hire employees, and accept larger or stricter orders.
9. Pass or respond to MCDA inspections.

The first playable loop must be short: one input, one basic product, one manual station, one grinder, one packaging station, one simple refrigerated storage system, one customer order, one basic employee job, basic cleanliness, and a basic MCDA inspection.

## System Interaction Overview

- Processing changes product form and may affect yield, freshness, quality, and cleanliness load.
- Product quality affects order acceptance, payment, reputation, and inspection risk.
- Cleanliness affects quality, employee error risk, inspection outcomes, and possibly machine efficiency.
- Refrigeration affects product temperature and freshness.
- Employee skill affects speed, quality, yield, and error chance.
- Customers create demand and reward consistent product quality.
- MCDA inspections check cleanliness, storage, product condition, and unresolved violations.
- Business progression unlocks employees, larger orders, facility upgrades, and expansion-mod systems.

## Product Quality

Product quality is a visible or inspectable product attribute stored on the item stack. It represents the combined result of process success, freshness, cleanliness, storage history, employee skill, and packaging state.

Quality should be calculated from clear contributing factors:

- Base product definition.
- Input quality and freshness.
- Processing method.
- Station or machine condition.
- Facility cleanliness at the point of work.
- Employee skill or player manual action.
- Temperature and time in storage.
- Packaging correctness.
- Error events such as overprocessing or poor handling.

The exact formula, score range, and quality names are TBD. The technical architecture should keep the calculation centralized and testable so balancing can change without rewriting station logic.

Quality should affect:

- Customer acceptance.
- Sale price or payment modifier.
- Reputation gains or losses.
- MCDA inspection findings when product condition is unsafe.
- Eligibility for higher-tier orders.

## Cleanliness

Cleanliness is continuous rather than simply clean or dirty. It should be tracked at facility-relevant locations and summarized into a facility score for inspections and quality calculations.

Gameplay expectations:

- Processing generates mess or sanitation load.
- Players can clean manually.
- Employees can be assigned cleaning work later.
- Higher throughput creates more cleaning pressure.
- Cleaner spaces improve product outcomes and reduce inspection risk.
- Neglected areas can trigger write-ups, fines, or shutdown risk.

Values and thresholds are TBD. The design should support forgiving and realistic config presets.

Cleanliness should be presented in a simple way:

- Station-level indication for immediate feedback.
- Facility-level summary for inspections and management.
- Clear warning states before severe penalties.

## MCDA Inspections

MCDA is the fictional Minecraft Department of Agriculture. It must not use USDA branding.

The inspection system should escalate in stages:

1. Informal warning or notice for minor first-time problems.
2. Write-up for clear violations.
3. Fine for repeated or serious unresolved violations.
4. Conditional shutdown for severe or repeated failures.
5. Reinspection after the player corrects issues.

Potential inspection categories:

- Facility cleanliness.
- Product temperature and freshness.
- Storage suitability.
- Packaging condition.
- Open unresolved write-ups.
- Equipment condition for refrigeration and critical machines.
- Employee assignment to unsafe or unsuitable work, if implemented later.

Inspections should feel fair. Before fines or shutdowns, the player should receive enough signals to understand what needs correction.

Inspection frequency, trigger rules, violation thresholds, fine amounts, shutdown duration, and reinspection timing are TBD.

## Employees

Employees are villager-based workers who become available as the business grows. They should not be required for the earliest essential loop.

Core concepts:

- Each employee has one or more job roles.
- Skill improves through successful work.
- Skill affects speed, product quality, yield, and errors.
- Employees reserve work orders and stations to avoid conflicting with the player or other employees.
- Poor cleanliness, missing inputs, bad layout, or blocked paths reduce performance.

Initial employee job:

- One basic assistant role for the vertical slice, such as operating a simple station, packaging, or cleaning.

Future roles:

- Processor.
- Grinder operator.
- Packaging worker.
- Cleaner.
- Refrigeration maintenance worker.
- Order clerk.
- Inspector-facing manager or compliance helper, if approved later.

Exact skill levels, XP curves, pay, shift behavior, and pathfinding constraints are TBD.

## Customers

Customers turn products into business progression. The first version should use simple customer orders rather than a full market simulation.

Order properties:

- Requested product.
- Quantity.
- Minimum quality requirement.
- Freshness or temperature requirement when relevant.
- Deadline or pickup window, later TBD.
- Payment and reputation outcome.

Customer types can later include:

- Local villagers buying small retail quantities.
- Restaurants or traders requesting recurring wholesale orders.
- Special customers who require stricter quality, freshness, or packaging.

The exact order-generation logic, price formulas, reputation model, and customer schedules are TBD.

## Business Progression

Business progression ties production success to unlocks and risk.

Progression inputs:

- Completed orders.
- Reputation.
- Inspection history.
- Facility size or equipment tier.
- Employee count and skill.
- Product variety.

Progression outputs:

- Employee availability.
- Larger order volume.
- Advanced machines.
- Walk-in room construction.
- Refrigeration equipment.
- More detailed MCDA inspections.
- Expansion-mod content.

ButcherCraft Core should include a minimal business foundation, while ButcherCraft Commerce can deepen finances, reputation, contracts, and wholesale behavior.

## Refrigeration

Refrigeration controls product temperature and freshness. The first version should include a simple refrigerated storage system. Later systems can add scalable rooms, compressors, condensers, evaporators, equipment wear, overload, and failures.

Design expectations:

- Cooler and freezer behavior use the same conceptual system with different target temperature ranges and capacity requirements.
- Room volume affects cooling demand.
- Lower target temperatures require more capacity.
- Freezers require more cooling capacity per block than coolers.
- Door openings, warm products, and ambient conditions may add load later.
- Compressors and evaporators can be overloaded in the detailed system.
- Overload accelerates wear and can cause failures.

Exact thermal coefficients, capacity units, update intervals, failure chances, and target temperature presets are TBD.

## Facility Expansion

Facility growth should be physical and readable.

Early expansion:

- More stations.
- More storage.
- More cleaning responsibilities.
- First employee.

Mid expansion:

- Dedicated processing, packaging, cold storage, and customer pickup areas.
- Basic work-order routing.
- Larger order batches.

Late expansion:

- Scalable multiblock coolers and freezers.
- Multiple equipment networks.
- Specialized employee roles.
- Inspection management.
- Wholesale logistics.

Facility boundaries, room ownership rules, and whether the player explicitly registers a facility through a controller block are TBD. The technical architecture proposes a controller/index model because it gives persistence and inspection systems a stable anchor.

## Configurability

The mod must support forgiving and realistic experiences.

Likely config categories:

- Freshness and spoilage speed.
- Cleanliness decay and cleaning effectiveness.
- Inspection frequency and severity.
- Employee skill growth and error rates.
- Refrigeration capacity strictness.
- Equipment wear and failure behavior.
- Order deadlines and quality requirements.
- Whether shutdowns are advisory, temporary, or strict.

Every config should have a clear gameplay meaning. Avoid hidden toggles that silently break progression.

## Unresolved Design Questions

- What is the first basic meat product?
- What vanilla or modded inputs should the first product accept?
- What should the mod id and public package be?
- How should facility boundaries be defined by players?
- Should basic employees be hired through villagers, a workstation, a contract item, or a business ledger UI?
- Should MCDA inspections be scheduled, event-triggered, or both?
- What quality scale is easiest for players to understand?
- What is the minimum viable refrigeration UI?
- How harsh should shutdowns be in the default preset?
- Which mechanics belong in ButcherCraft Core versus ButcherCraft Commerce and ButcherCraft Refrigeration?
