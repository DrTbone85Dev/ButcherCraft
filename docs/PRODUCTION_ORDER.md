# Production Order

Status: IM-019 player-facing guidance foundation, extended by IM-022 deadline display

The Production Order is a narrow player-facing item for the current manual Beef Patties chain. It exists to make the already implemented Grinder to Patty Former Production path understandable without developer diagnostics or internal identities.

It does not add a general Production authoring system, worker automation, automated item transfer, logistics, Allocation, customer orders, economics, checkpoint recovery, operator reconciliation, or public workflow APIs.

## Supported Chain

The only supported Production Order template is:

```text
Beef Trim
-> Grinder
-> Ground Beef
-> manual player transfer
-> Patty Former
-> Beef Patties
```

The order creates one fixed Beef Patties Production Run when an unlinked order item is opened on the server. Reopening the same item observes the existing run referenced by the item component. Stale references remain visible instead of creating a replacement run.

IM-022 adds an optional configured target deadline to newly created Beef
Patties Production Order runs when Business Runtime deadline configuration is
enabled. The deadline is Production-owned status only; it does not add customer
orders, penalties, money changes, reputation, automatic failure, or automatic
retry.

## Player Flow

1. Obtain a Production Order from the ButcherCraft creative tab.
2. Use the order to create or inspect the Beef Patties run.
3. Hold the order and interact with one Grinder to assign the Grinder step.
4. Hold the order and interact with one Patty Former to assign the Patty Former step.
5. Open the order to see the current step, expected input, expected output, and next action.
6. Insert Beef Trim into the assigned Grinder.
7. Let the Grinder process Beef Trim into Ground Beef.
8. Open or view the order after Grinder completion so Production observes the owner result.
9. Move Ground Beef from the Grinder output to the Patty Former input manually.
10. Let the Patty Former process Ground Beef into Beef Patties.
11. Open or view the order after Patty Former completion so Production observes chain completion.
12. Retrieve Beef Patties normally from the Patty Former output.

The order never inserts, extracts, teleports, reserves, or moves ItemStacks.

## Assignment

Assignment is server validated from the clicked block entity. The client does not provide the authoritative workstation type.

The assignment rules are:

- A Grinder can be assigned only to the Grinder step.
- A Patty Former can be assigned only to the Patty Former step.
- Identical duplicate assignment is safe.
- Conflicting assignment is rejected after a step is assigned or after execution begins.
- Terminal or cancelled runs do not accept new assignments.
- Missing assigned workstations are reported visibly.

## Status And Progress

The screen displays player-facing guidance instead of raw Production, Execution, Scheduler, or evidence identities.

Major next actions include:

- create the Beef Patties run
- assign the Grinder
- load Beef Trim
- wait for Grinder processing
- move Ground Beef to the Patty Former
- assign the Patty Former
- load Ground Beef
- wait for Patty Former processing
- collect Beef Patties
- cancelled before start
- failed
- recovery required
- stale order reference

Progress is read from assigned workstation owner state through bounded menu data. Production and the screen observe progress; they do not own or mutate it.

The screen also displays plant status, current business day and time, current
or next shift, deadline time, and deadline status when those observations are
available. The display intentionally omits hashes, raw ticks, evidence
identities, configuration identities, and other internal authority tokens.

## Failure Guidance

The order maps typed runtime state to player-readable guidance:

- Wrong or missing Grinder input: insert Beef Trim into the Grinder.
- Grinder output blocked: clear the Grinder output slot.
- Manual transfer required: move Ground Beef from the Grinder to the Patty Former.
- Wrong or missing Patty Former input: insert Ground Beef into the Patty Former.
- Patty Former output blocked: clear the Patty Former output slot.
- Missing workstation: place or reassign the expected workstation.
- Unknown Outcome: recovery is required and the operation will not restart automatically.
- Stale run reference: the linked Production Run is no longer present.

The UI does not imply repair, retry, compensation, or automatic recovery.

## Persistence

The Production Order item persists only the fixed template identity and optional Production Run reference. Production persists run, chain, assignment, execution, completion evidence references, and optional deadline records. Grinder, Patty Former, Execution, Scheduler, World Time, and Business Runtime state remain with their owning systems.

Legacy Production Runs without a deadline field remain loadable as
`NO_DEADLINE`; no deadline is fabricated for old runs.

## Multiplayer Limitations

All creation, assignment, cancellation, and status observation are server-side. Duplicate interactions observe existing authoritative state, and viewing status grants no Execution or Scheduler authority.

The current repository does not define player claims, permissions, shared factory ownership, or private run visibility. Production Run visibility therefore follows the existing world-level ownership model.

## Tests And Verification

IM-019 adds focused unit and boundary tests for Production Order data, state mapping, registration, and authority boundaries. It also adds 27 server-world GameTests covering order creation, assignment, duplicate safety, manual-transfer guidance, read-only progress, missing references, cancellation, and full-chain completion.

Human client acceptance remains required before calling the experience manually accepted. The manual checklist includes creating the order, assigning both workstations, observing both progress bars, proving Ground Beef is not moved automatically, saving and reloading during manual transfer, and reviewing client/server logs.

## Remaining Gates

The following remain gated:

- additional Production templates
- arbitrary workflow graphs
- recipe selection UI
- customer-order-authored deadlines
- deadline penalties
- worker AI
- automated transfer
- Allocation
- logistics
- customer orders
- economics
- startup recovery
- checkpoint activation
- operator reconciliation
- public Production APIs
- final UI and art polish
