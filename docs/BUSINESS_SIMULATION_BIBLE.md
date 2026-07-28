# ButcherCraft Business Simulation Bible

Status: canonical gameplay philosophy created by DG-001

Scope: gameplay design guidance for business simulation, workforce, orders,
customers, economy, facility growth, time pressure, and player progression

This document is gameplay design guidance. It is not an architecture guide, an
implementation plan, a milestone authorization, a balancing table, a public API
contract, or a persistence schema.

## 1. Purpose

The Business Simulation Bible exists to keep future ButcherCraft gameplay
coherent as the mod grows from hands-on workstation play into plant
management, workforce leadership, customer fulfillment, and business
ownership.

It governs player-facing design decisions involving:

- business hours and shifts;
- production deadlines and priorities;
- workers and leadership;
- customer orders and reputation;
- retail, wholesale, and contracts;
- payroll, costs, and economic pressure;
- facility layout and product flow;
- growth, difficulty, and failure recovery.

The Architecture Guide explains ownership, determinism, persistence,
authority, and subsystem boundaries. This Bible explains why those systems
matter to play, what experience they should create, and how future features
should feel to a player.

Future contributors should use this document before proposing a business or
simulation feature. A feature should serve the player journey, connect to
Production Orders or business operation, preserve manual and automated play,
and make the plant feel more like a functioning business.

## 2. Game Identity

ButcherCraft is a hybrid of hands-on work, production management, factory
growth, workforce leadership, and business ownership.

It is not an ordinary food mod because products are not just recipes. Products
move through workstations, carry identity, participate in production chains,
and eventually serve customers and business goals.

It is not a generic factory mod because machines are not the whole point. The
point is operating a plant: people, shifts, capacity, product flow, quality,
storage, customers, deadlines, and leadership all matter.

It is not an idle automation game because the player should understand the
work before delegating it. Automation removes repetition after the player
learns the process; it does not erase the plant.

It is not a pure tycoon game because the business is physical. Layout,
workstations, product movement, employees, and visible production remain part
of the experience.

ButcherCraft should feel like running a practical Minecraft-scale processing
business, not like managing abstract numbers detached from the floor.

## 3. Player Fantasy

The central fantasy is:

```text
The player starts by doing the work and grows into the owner of a functioning
meat-processing business.
```

At the beginning, the player should feel useful, busy, and close to the work.
Every product is handled personally. Every bottleneck is felt directly.

As the business grows, the player should feel increasingly responsible for a
system. The challenge shifts from "Can I run this machine?" to "Can I make the
plant run well?"

At the highest level, the player should feel like a leader and owner. Their
choices about investment, layout, staffing, quality, customers, and company
culture shape the business.

## 4. Career Progression

ButcherCraft progression is based on capacity, responsibility, and operational
complexity rather than technology tiers alone.

### Employee / Butcher

The player operates machines, moves products, learns workflows, fulfills
simple Production Orders, and personally experiences the cost of poor layout,
blocked outputs, missing inputs, and weak planning.

This stage teaches the work by doing it. The plant may be small, but it must
already be real: inputs become outputs through server-authoritative
workstations, and the player learns why the workflow exists.

### Lead Butcher / Crew Leader

The player still works directly but begins coordinating a small team. They
assign immediate jobs, respond to bottlenecks, train workers, prioritize
orders, and decide when to jump in personally.

This stage should feel like being responsible for both production and people.
The player is not removed from the work yet; they are learning to lead it.

### Plant Manager

The player manages departments, schedules production, reviews performance,
handles staffing levels, and resolves operational problems. They may still use
workstations, but they should rarely need to personally operate every machine.

This stage should emphasize visibility, prioritization, and capacity
management. The player should understand what is blocked, why it is blocked,
and what decision would improve flow.

### Business Owner

The player invests, expands, accepts contracts, manages reputation, supervises
multiple departments or facilities, makes strategic decisions, and defines the
company culture.

This stage should make the business feel larger than one workstation chain.
The player decides what kind of company they are building.

## 5. Core Gameplay Loop

The foundational loop is:

```text
Customer or internal need
-> Production Order
-> planning and assignment
-> workstation operations
-> manual or worker product movement
-> finished goods
-> fulfillment
-> payment, reputation, or inventory outcome
```

Production Orders are the center of the game because they turn activity into
purpose. A workstation chain is not just a recipe; it is work performed for a
need. The order explains what must be made, why it matters, what is next, and
how success will be recognized.

Early orders can be simple and local. Later orders can express deadlines,
quality, packaging, customer identity, destination, contract context, and
priority. The order remains the player-facing bridge between business demand
and plant-floor action.

## 6. Hands-On Work Philosophy

Manual work matters because it teaches the player what the business actually
does.

Players should learn each workstation by using it. Tactile interaction makes
product flow understandable: load input, wait for progress, clear output,
move product forward, and respond when something blocks the chain.

Automation should be earned through understanding, not used to skip the game.
The player should know why a worker is assigned to a station, why a route is
blocked, and why a product is waiting.

Manual gameplay must remain viable after automation exists. Some players enjoy
hands-on work, some worlds may stay small, and some business problems should
still reward the owner jumping in directly.

Manual play should not be made intentionally tedious just to force automation.
The motivation to automate should come from growing demand, complexity, and
opportunity, not from punishing basic interaction.

## 7. Automation Philosophy

Workers and automation should remove repetitive labor after the player
understands the process.

Automation must not become magic. Workers should travel physically, claim real
tasks, use real workstations, carry real products, wait for operations,
encounter blocked states, and require supervision and training.

Machines, workers, and logistics must preserve the same authoritative
production rules used by manual players. A worker operating a Grinder should
not bypass the Grinder. A logistics system moving Ground Beef should not
invent a private item-transfer truth. A production chain should not complete
because a timer guessed that it probably happened.

Good automation should make the plant feel alive. It should create a new
management layer without deleting the physical process underneath.

## 8. Worker Philosophy

Employees are people, not conveyor belts with legs.

Future worker design may include:

- skill by task;
- training;
- speed;
- quality;
- reliability;
- attendance;
- fatigue where appropriate;
- experience;
- preferred or specialized work;
- morale or satisfaction where meaningful.

Workers should improve through use and training. Management should involve
choosing the right person for the right job, supporting them with a sensible
layout, and giving them conditions where they can succeed.

Avoid caricatures, random cruelty, or systems that reduce workers to
disposable resources. A worker may make mistakes, become unavailable, need
training, or struggle in a poor workplace, but those outcomes should be
explainable and fair.

## 9. Leadership and Company Culture

Leadership should affect the plant.

Future systems should respect principles such as:

- safe work;
- fair scheduling;
- training;
- reliability;
- reasonable workload;
- employee retention;
- quality expectations;
- accountability;
- respect.

The game may represent consequences of poor management: turnover, fatigue,
lower quality, missed work, slower training, blocked stations, or weaker
reputation. It should not reward abusive leadership as the optimal strategy.

Company culture should become a strategic identity. A careful high-quality
shop, a fast wholesale plant, and a flexible local butcher can all be valid,
but each should have tradeoffs.

## 10. Time Philosophy

Canonical rule:

```text
Time should encourage planning, not rushing.
```

ButcherCraft has a configurable Minecraft day length and a Business Calendar
derived from visible world time. Business time should be legible: daylight,
night, shifts, opening hours, deadlines, and customer activity should feel
connected to the world the player sees.

Deadlines should create meaningful prioritization. They should not turn every
session into constant panic. Missing a deadline should create proportional
business consequences, such as lower payment, reputation loss, a disappointed
customer, rescheduling, spoilage risk, or contract pressure. It should rarely
produce arbitrary catastrophic punishment.

Sleep and time jumps should update business time without replaying every
skipped minute. A skipped night should not instantly complete machines,
duplicate daily events, or create a storm of overdue work.

Pack developers should be able to shape time pressure. A casual modpack may
prefer generous days and loose deadlines. A stricter plant-management pack may
prefer tighter shifts and more serious consequences.

## 11. Business Hours and Shifts

Business hours should describe when a plant or department normally operates.
Shifts should describe when workers are expected to be available.

Future design should support:

- plant opening and closing;
- worker shifts;
- overtime;
- scheduled breaks;
- staffing levels;
- weekend operation;
- multi-shift plants.

Business hours should influence worker availability, customer activity,
deliveries, production planning, and operating expectations.

Not every player should be forced into a strict realistic schedule. Profiles
and configuration should support casual, sandbox, realistic, retail-focused,
and wholesale-focused pacing.

## 12. Production Order Philosophy

Production Orders are the heartbeat of the game.

Future order types may include:

- internal stock order;
- retail replenishment;
- custom customer order;
- wholesale order;
- contract order;
- seasonal order;
- urgent order.

Production Orders should identify:

- required products;
- quantities;
- priority;
- due time;
- customer or destination;
- quality or packaging requirements where applicable;
- current status.

The current repository implements one narrow Production Order for the Beef
Patties chain. This Bible describes the design direction for future orders; it
does not authorize broader templates, customers, deadlines, economics, or
automated fulfillment by itself.

## 13. Customer Philosophy

Customers drive production. They give the plant a reason to make specific
products at specific times.

Future customer categories may include:

- walk-in retail;
- custom processing customers;
- restaurants;
- grocery stores;
- institutions;
- fairs and events;
- recurring wholesale accounts.

Customer behavior should be understandable and evidence-based. A customer
should react to what happened: late delivery, wrong product, poor quality,
missing packaging, inconsistent service, or reliable fulfillment.

Reputation should reflect:

- reliability;
- product quality;
- order accuracy;
- timeliness;
- service;
- consistency.

Avoid arbitrary invisible customer punishment. When reputation changes, the
player should understand the cause.

## 14. Economy Philosophy

Profit should come from good management.

Income and cost categories may include:

- product sales;
- processing fees;
- wages;
- overtime;
- ingredients;
- utilities;
- maintenance;
- equipment;
- waste;
- spoilage;
- expansion;
- logistics.

The economy should reward planning, reliable fulfillment, sensible layout,
quality control, capacity investment, and good leadership. It should not be
based primarily on grinding currency or exploiting repetitive loopholes.

Economic difficulty should be configurable for modpacks. Numeric balance,
prices, wages, costs, spoilage, and demand volume belong to later balancing
and configuration decisions.

## 15. Growth Philosophy

Progression should be based primarily on:

- capacity;
- workflow complexity;
- demand;
- staffing;
- reputation;
- contracts;
- facility growth.

The same workstation may remain useful throughout the game. Growth should not
require replacing every machine merely because a technology tier increased.

Better equipment can be valuable, but it should add capability, reliability,
scale, speed, precision, energy needs, maintenance concerns, or workflow
options. It should not make early systems meaningless by default.

## 16. Facility and Layout Philosophy

Product flow should matter.

Plants should naturally develop departments such as:

- receiving;
- harvest or slaughter, where approved and abstractly represented;
- fabrication;
- processing;
- packaging;
- refrigeration;
- storage;
- shipping;
- retail.

The design should encourage forward product flow, sensible storage, clear
work areas, and readable departments. Poor layout should create visible
bottlenecks, extra walking, blocked workstations, storage pressure, or quality
risk.

Avoid hard requirements that prevent creative building. The game should reward
efficient and safe layouts without forcing every plant into one exact shape.

## 17. Workstation Philosophy

Workstations should have a clear input, clear output, understandable purpose,
and visible role in a production chain.

A workstation should support both player and worker operation where future
worker systems are approved. Workers should use the same workstation rules as
players, and workstation state should remain owned by the workstation.

Each workstation UI should eventually include:

- workstation-specific visual identity;
- brushed stainless-steel styling where appropriate;
- labeled input and output;
- expected product indication;
- progress;
- current status;
- actionable error guidance.

Player-facing workstation UI should not expose internal platform terms.
Players need to know what to insert, what will come out, what is blocked, and
what to do next.

## 18. Machine Animation and Sound Philosophy

Machines should feel alive.

Future presentation should include:

- moving parts corresponding to real function;
- animation synchronized to authoritative state;
- startup, running, processing, completion, and shutdown sounds;
- real recorded equipment where practical;
- restraint to avoid repetitive noise fatigue;
- server-safe and client-safe presentation boundaries.

Animations and sounds must represent machine state but must not become
authority. If a client animation stutters, the server-owned operation remains
the truth.

## 19. Product and Process Authenticity

Use real processing logic where it improves understanding and gameplay.

ButcherCraft may represent:

- trim;
- ground products;
- patties;
- sausage;
- cuts;
- packaging;
- storage;
- waste and byproducts where meaningful.

Authenticity should make workflows easier to understand. It should not create
excessive realism that becomes repetitive busywork without decisions.

Content should remain abstract, Minecraft-like, and non-graphic. The goal is
to model work and business, not to shock the player.

## 20. Quality, Error, and Waste Philosophy

Future quality systems should create meaningful tradeoffs.

Possible influences include:

- worker skill;
- workstation condition;
- input quality;
- process selection;
- sanitation;
- time;
- storage.

Errors should be visible, explainable, preventable, and recoverable where
realistic. Waste should matter, but it should not become constant punishment.

Quality should never feel like a hidden random grade. A player should be able
to improve outcomes by understanding the plant.

## 21. Failure Philosophy

Failure should create stories and decisions.

Examples include:

- missed deadline;
- blocked machine;
- absent worker;
- equipment failure;
- spoiled inventory;
- incorrect product;
- customer complaint.

Failure should rarely destroy an entire playthrough. Recovery should involve
reprioritization, staffing, maintenance, replacement, communication, layout
improvement, or accepting a business consequence.

The best failures teach the player how the business works. The worst failures
feel arbitrary, silent, or unrecoverable.

## 22. Difficulty and Configuration

Pack developers must be able to shape the experience.

Future configuration profiles may include:

- Sandbox;
- Casual Plant;
- Realistic Plant;
- Wholesale Focus;
- Retail Focus;
- Seasonal Business;
- Hardcore Operations.

Profiles may affect:

- day length;
- order volume;
- deadline strictness;
- wages;
- equipment cost;
- customer tolerance;
- worker training speed;
- spoilage;
- demand.

Canonical gameplay rules describe relationships and responsibilities.
Configuration should tune numeric balance, strictness, frequency, and
consequence severity. Hidden toggles that silently break progression should be
avoided.

## 23. Seasonal and Business Events

Future event design may include:

- holidays;
- hunting seasons;
- county fairs;
- restaurant contracts;
- retail rushes;
- supply delays;
- employee absences;
- maintenance windows.

Events should feel grounded in operating a real business. They should create
planning choices, not random punishment.

Seasonal demand should be understandable. A fair creates demand because people
will buy food. A hunting season creates processing pressure because more
customers arrive. A supply delay matters because a known input is late.

## 24. Multiplayer Philosophy

Future multiplayer roles may include:

- owner;
- manager;
- lead;
- worker;
- customer where appropriate.

Players should be able to divide responsibilities. One player may run
workstations while another handles orders, layout, or staffing.

Casual cooperative play should not require rigid permissions for every action.
At the same time, server-authoritative ownership and conflict prevention are
needed for long-lived servers. Multiplayer design should protect shared state
without making ordinary teamwork exhausting.

## 25. Education Through Play

ButcherCraft should teach authentic concepts indirectly:

- product flow;
- scheduling;
- bottlenecks;
- inventory;
- quality;
- staffing;
- capacity;
- customer fulfillment;
- business growth.

The mod should not become a lecture. Learning should emerge from operating the
plant, seeing consequences, and improving the system.

When a player says "I understand why this order is late" or "I need a second
person on packaging," the design is doing its job.

## 26. Ethical Boundaries

ButcherCraft may portray difficult realities of work and business, but it
should do so responsibly.

Design principles:

- no reward for abusive worker treatment;
- no trivialization of unsafe food practices;
- no misleading claims about real meat safety;
- no graphic cruelty as entertainment;
- no mechanic whose optimal strategy is deliberate exploitation without
  consequence.

The fictional MCDA and other future regulatory or safety systems must remain
fictional and abstract. They should teach care and responsibility without
claiming to simulate real law.

## 27. Content Prioritization Test

A proposed feature should answer:

1. Does it make the plant feel more alive?
2. Does it create a meaningful player decision?
3. Does it fit an authentic processing or business workflow?
4. Does it support the player's career progression?
5. Does it connect to Production Orders?
6. Can it be configured appropriately?
7. Does it preserve manual and automated play?
8. Does it respect existing subsystem authority?
9. Does it add value greater than its complexity?
10. Can players understand why it exists?

Features that fail most of these questions should be reconsidered, simplified,
or deferred.

## 28. Anti-Goals

ButcherCraft should not become:

- a generic item-pipe mod;
- an idle clicker;
- a reskinned technology-tier mod;
- a pure spreadsheet simulator;
- a machine collection without production purpose;
- an automation system that bypasses all physical gameplay;
- a punishment simulator;
- a game where workers are disposable resources;
- a rigid realism simulator that leaves no room for creativity.

These anti-goals do not ban convenience, automation, numbers, or strict
profiles. They prevent those elements from replacing the core plant-running
experience.

## 29. Release Philosophy

Future releases should favor complete, player-visible loops.

Prefer one complete workstation, one complete chain, one accepted worker
workflow, or one clear business system over several unfinished systems.

Where practical, releases should alternate between:

- visible gameplay expansion;
- polish and usability;
- simulation depth;
- stability.

A release should make the plant easier to understand, more purposeful, more
alive, or more reliable. Breadth without a playable loop should be avoided.

## 30. Near-Term Roadmap Guidance

The likely design sequence is:

1. Business hours and shifts.
2. Production deadlines.
3. First worker foundation.
4. Worker operation of existing workstations.
5. Worker transfer between Grinder and Patty Former.
6. Packaging.
7. Customer orders.
8. Inventory and storage pressure.
9. Refrigeration.
10. Broader business growth.

This sequence supports the player journey. Time and shifts define when work
should happen. Deadlines explain why priority matters. Workers can then be
introduced into a world where schedules and order pressure already have
meaning. Worker operation should begin on existing workstations before adding
new content. Transfer, packaging, customers, storage, refrigeration, and
growth then build a wider business loop.

This is design guidance, not a binding milestone schedule. Repository
findings, architecture readiness, owner direction, and implementation evidence
may justify a different order.

## Terminology

Prefer player-facing terms:

- Workstation;
- Production Order;
- Production Run;
- Employee;
- Worker;
- Shift;
- Department;
- Customer Order;
- Finished Goods;
- Business Calendar.

Avoid exposing platform terms in gameplay text, menus, tutorials, or ordinary
documentation for players:

- Execution Authorization;
- Scheduler Work Identity;
- owner-result evidence;
- freshness identity;
- binding authority.

Those internal terms remain valid architecture language. They are not the
language of the plant floor.

## Design Tensions

Future design must continually balance:

- realism vs fun;
- manual work vs automation;
- time pressure vs planning;
- worker individuality vs simulation cost;
- physical logistics vs player convenience;
- business depth vs accessibility;
- failure consequence vs frustration;
- authenticity vs modpack flexibility;
- single-player pacing vs multiplayer persistence.

These tensions are not permanently solved. They should be revisited as the
gameplay surface grows.

## Current Feature Boundary

This document includes future vision. It does not mean those systems are
implemented.

Current visible foundations include the promoted Grinder, Patty Former,
Packaging Table proof content, Bandsaw proof content, the narrow Production
Order guidance path for the Beef Patties chain, and the first linked Employee
entity representation. Current platform foundations include World Identity,
Simulation Clock, Business Runtime, Workforce definitions, Employee and
Employment Records, Goods, Actors, Inventory, Transactions, Orders and
Contracts, Scheduler, Production, Planning, Allocation foundations, Execution
foundations, Checkpoint foundations, Evidence foundations, and World Time.

Future work remains gated unless separately authorized, including worker AI,
employee workstation operation, payroll, customers, reputation, deadlines, refrigeration,
cleanliness, inspections, retail demand, wholesale contracts as gameplay,
logistics, public APIs, and broad business UI.
