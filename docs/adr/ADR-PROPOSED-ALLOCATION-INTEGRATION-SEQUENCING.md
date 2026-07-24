# Proposed ADR: Allocation M22E-M22F Integration Sequencing

Status: PROPOSED - OWNER APPROVAL REQUIRED

Decision identifier: Unassigned

Package: BCSE Architecture Hardening AH-1

Authority: This document has no authority until explicitly approved by the
project owner and recorded through the repository's accepted Decision process.
It does not authorize M22E, M22F, RFC-0023, persistence, or gameplay.

## Context

RFC-0022 Revision 2 is accepted and implemented through:

- M22A immutable Allocation domain;
- M22B runtime and registry ownership;
- M22C deterministic Allocation Cycle; and
- M22D Resource and Capacity provider framework.

M22D deliberately registers no production-grade provider and introduces no
live Scheduler stage, Planning handoff, Production gate, persistence, or
Execution integration.

RFC-0022 and its architecture review require another owner-approved ADR before
M22E-M22F. The independent audit additionally requires lifecycle hardening
before more live durable owners are connected.

## Problem

The project needs an explicit order for:

- proving one concrete provider;
- adding stage `butchercraft:allocation` at order 350;
- changing Planning submission;
- invoking Allocation;
- activating Commitments;
- gating Production/Execution;
- releasing Commitments;
- persisting Allocation in coordinated checkpoints;
- retaining Allocation evidence; and
- integrating future generic Execution.

The order must prevent temporary ownership violations, bypass paths, and
persistence schemas that immediately require replacement.

## Current Behavior

- Planning stage order is 300.
- Execution/Production stage order is 400.
- No Allocation stage 350 exists.
- Planning currently submits Production intent directly through a typed
  Production adapter.
- Production executes without AllocationSet authorization.
- Allocation has no persistence or live service composition.
- The provider registry is intentionally empty.
- Allocation Cycle invocation is explicit and pure; it is not Scheduler-driven.
- `AllocationCycleExecutor` has an instance-local guard.
- RFC-0023 is Draft 1 and unimplemented.

This proposal does not change any current behavior.

## Architectural Constraints

The proposal is governed by:

- DEC-0073 Production ownership;
- DEC-0074 Planning ownership;
- DEC-0076 through DEC-0079 Allocation ownership;
- RFC-0022 Revision 2 owner gates;
- all constitutional invariants, especially `AI-0001`, `AI-0003`,
  `AI-0006`, `AI-0007`, `AI-0015` through `AI-0028`; and
- approved versions of every AH-1 dependency decision.

Required ownership:

- Planning owns Candidate and Approved Plans;
- Allocation owns Requests, Sets, Commitments, Allocation runtime, and
  Commitment lifecycle;
- providers own Resource and Capacity facts;
- Scheduler owns Work eligibility and stage order;
- Production owns Production definitions/plans/runs;
- future Execution owns generic execution runtime/evidence;
- Transactions own economic mutation;
- Inventory owns quantities; and
- checkpoint coordination owns only durable generation publication.

## Options Considered

### Option 1: Complete Live Allocation Before Execution Implementation

Sequence:

1. concrete provider;
2. stage 350;
3. Planning handoff;
4. Production gate;
5. persistence;
6. later migrate gated Production to generic Execution.

Advantages:

- proves Allocation immediately;
- avoids waiting for RFC-0023;
- current Production provides a real consumer.

Disadvantages:

- creates one live authorization handoff that generic Execution later changes;
- risks duplicate migration of Production Work and persistence;
- effect/checkpoint contracts must still be solved first.

### Option 2: Build Pure Execution Foundation, Then Integrate Allocation Before
Live Execution

Sequence:

1. hardening decisions and implementations;
2. accepted RFC-0023 pure definitions/runtime/pipeline milestones;
3. concrete Allocation provider;
4. stage 350, Planning handoff, Production gate, and Allocation persistence;
5. live Execution integration later consumes the same active set.

Advantages:

- Execution contracts exist before final handoff APIs freeze;
- Allocation can still be proven against current Production;
- live integration remains split into reviewable milestones;
- no joint mega-milestone.

Disadvantages:

- Production gate exists before generic Execution uses it;
- some adapter surface may be touched twice;
- longer path to a live Allocation result.

### Option 3: Integrate Allocation And Execution In One Production Vertical Slice

Sequence:

- one change introduces provider, stage 350, Planning handoff, Allocation
  persistence, Execution runtime, Production adapter, Transaction completion,
  release, and recovery.

Advantages:

- one final end-to-end contract;
- no temporary Production-only gate.

Disadvantages:

- highest blast radius;
- difficult fault isolation and rollback;
- too many new persistence and ownership boundaries;
- violates the repository's small-milestone practice.

### Option 4: Implement Allocation Persistence Before Any Live Consumer

Advantages:

- persistence could be tested in isolation.

Disadvantages:

- freezes files without a proven live lifecycle;
- conflicts with the coordinated checkpoint decision;
- creates speculative storage and migration.

## Decision Proposed

Adopt **Option 2: pure Execution foundation first, then M22E-M22F live
Allocation before live generic Execution integration**.

Approval of this sequencing does not authorize any implementation step. Each
phase requires its own approved milestone.

## Prerequisite Decisions

No M22E-M22F implementation begins until the owner approves:

1. Planning cadence;
2. evidence lifecycle;
3. coordinated checkpoint/recovery;
4. Transaction validation authority;
5. Scheduler effects/execution authority;
6. RFC-0023 reconciliation and then RFC-0023 itself; and
7. this Allocation sequencing decision.

The corresponding hardening implementations and migrations must pass before
live Allocation persistence or stage integration.

## Proposed Milestone Sequence

### Gate 0: Platform Hardening

Implement only after separate authorization:

- bounded Planning cadence and trigger state;
- evidence hot/cold lifecycle;
- coordinated checkpoint generations;
- exact Transaction validation binding;
- Scheduler handler effect policy;
- one Scheduler and one Allocation authority per world; and
- migrations for existing worlds.

Exit evidence:

- sustained lifecycle tests;
- checkpoint fault injection;
- exact Transaction binding tests;
- current gameplay regression; and
- no mixed-generation load path.

### Gate 1: RFC-0023 Draft 2 And Pure Execution M23A-M23C

After the reconciliation ADR and dependency decisions are accepted:

- revise and independently review RFC-0023 Draft 2;
- accept RFC-0023 through explicit owner action;
- implement pure immutable Execution definitions;
- implement pure Execution runtime/lifecycle;
- implement pure bounded pipeline/evidence;
- add no live Scheduler handler;
- add no persistence;
- add no Production adapter;
- add no gameplay.

Exit evidence:

- pure Java determinism, replay, lifecycle, publication, authority, and
  Transaction-boundary tests;
- no Minecraft/NeoForge imports;
- no live service composition.

### Gate 2: M22E Concrete Provider Proof

Implement one production-grade provider adapter for the current Production
vertical slice.

The provider must:

- retain authority in its source subsystem;
- expose only immutable M22A Resource/Capacity snapshots;
- use exact quantities and declared units;
- have stable provider, Resource, and Capacity identities;
- be registered explicitly, not discovered;
- prove success, empty, local failure, duplicate conflict, and replay;
- perform no Allocation Cycle, Commitment publication, or runtime mutation;
  and
- participate in checkpoint snapshot validation when live registration is
  later enabled.

The first provider should expose the minimum current Production and Inventory
capacity needed to prove one plan. Additional Workforce or facility providers
remain separate milestones.

Gate 2 may be implemented and tested without live registry activation.

### Gate 3: M22F Schema And Persistence Preparation

Prepare additive schema changes, still without gameplay exposure:

- Scheduler schema adds `butchercraft:allocation` at order 350;
- Planning submission runtime adds Allocation Request/Set and Work references;
- Production Plan/Run runtime adds required `AllocationSetId`;
- Allocation owner snapshot schema includes definitions, runtime,
  Commitments, history, reports, traces, provider registry identity, and
  observation evidence required for recovery;
- checkpoint manifests include Allocation;
- evidence classification and archive rules include Allocation; and
- architecture manifest proposal reflects accepted contracts.

Migration requirements:

- require the exact known prior Scheduler stage set;
- insert stage 350 without changing existing stage ids, Work ids, submission
  sequences, runtime, or finalized tick;
- add absent Allocation references as explicit legacy-unallocated state;
- never treat a legacy Production Run as authorized retroactively;
- fail visibly for ambiguous active legacy Work;
- commit Scheduler, Planning, Production, and Allocation migration in one
  checkpoint generation; and
- preserve legacy files until checkpoint migration retention permits archive.

No independent five-file Allocation shutdown save is authorized. RFC-0022's
logical five-file set becomes one Allocation owner snapshot inside the
coordinated checkpoint.

### Gate 4: Planning Submission Handoff

One Planning submission operation:

1. obtains or registers executable Production intent through Production
   authority;
2. creates immutable Allocation Request and AllocationSet through Allocation
   authority;
3. records stable Planning provenance;
4. schedules Allocation Work and Production-owned execution Work for the same
   future simulation tick;
5. records both Work ids and `AllocationSetId`; and
6. publishes the cross-owner result under explicit compensation and checkpoint
   rules.

Allocation schedules no Work.

If any registration or scheduling step fails before publication, no new
cross-owner partial result remains. If an owner-local publication already
became authoritative, compensation uses that owner's typed idempotent
cancellation/withdrawal contract. No direct collection rollback is allowed.

### Gate 5: Stage 350 Allocation Cycle

At one authoritative tick:

1. Scheduler reaches stage 350 after Planning stage 300;
2. Allocation Work captures one complete provider observation bundle;
3. incomplete or conflicting observation prevents Cycle invocation;
4. Allocation authority validates one immutable Cycle input;
5. one bounded deterministic Cycle executes;
6. accepted candidate publishes Commitments and Allocation runtime atomically
   inside Allocation;
7. Allocation Work returns typed outcome/evidence; and
8. Allocation schedules no downstream Work.

Provider failure remains isolated in observation evidence. A bundle marked
incomplete cannot authorize execution.

### Gate 6: Activation And Production Gate

At execution stage 400:

1. Production-owned Work presents its required `AllocationSetId`;
2. Production/Execution requests activation through Allocation authority;
3. Allocation verifies the complete set is `ALLOCATED`, current, unexpired,
   and structurally complete;
4. Allocation atomically transitions the set and Commitments to `ACTIVE`;
5. Production performs no progress, side effect, or Transaction proposal
   before accepted activation evidence;
6. an unallocated or waiting set returns typed wait/failure according to
   accepted policy; and
7. activation evidence is correlated with Production Work and checkpoint
   generation.

Current Production remains the live executable owner at this gate. Generic
Execution later uses the same activation contract rather than acquiring
Commitment ownership.

### Gate 7: Completion And Commitment Release

Release is explicit and Allocation-owned.

Release requests can originate from:

- successful Production completion after exact APPLIED Transaction evidence;
- Production failure or cancellation;
- Execution completion/failure/cancellation after live Execution integration;
- approved expiration policy; or
- explicit owner recovery reconciliation.

Rules:

- one complete AllocationSet releases atomically;
- release uses supplied authoritative simulation tick;
- duplicate identical release returns existing evidence;
- same-id/different-cause conflicts;
- Capacity becomes eligible only in the next Allocation Cycle;
- release never recursively invokes Allocation;
- release cannot reverse an APPLIED Transaction; and
- unknown outcome enters recovery, not automatic release/retry.

### Gate 8: Live Generic Execution Integration

Only after Gates 0-7 are stable:

- Production Work delegates bounded domain execution through accepted
  RFC-0023 adapters;
- Execution consumes active Allocation authorization;
- Execution constructs/submits exact Transaction proposals;
- Transaction remains mutation authority;
- Execution observes exact result evidence;
- Allocation release follows terminal outcome;
- all owners commit through coordinated checkpointing; and
- current Production gameplay behavior remains unchanged unless separately
  authorized.

## Failure Behavior And Isolation

- One provider failure does not erase unrelated observations, but the bundle is
  incomplete and cannot enter the Cycle.
- One AllocationSet failure does not publish partial Commitments.
- One failed activation does not advance Production.
- One failed Production/Execution instance does not release unrelated sets.
- Checkpoint failure leaves the prior committed generation authoritative.
- Transaction APPLIED followed by later publication failure enters
  reconciliation and never resubmits the mutation.
- Scheduler budget deferral preserves Work and does not duplicate Requests,
  Sets, Commitments, or Production Plans.

## Crash Behavior

Schema-1 recovery selects one committed checkpoint containing:

- Clock;
- Scheduler stage/Work runtime;
- Planning provenance and cadence;
- provider registry and required observation evidence;
- Allocation definitions/runtime/Commitments;
- Production definitions/plans/runs;
- Transaction/Inventory state; and
- future Execution state when live.

No owner loads a newer independent file. Uncheckpointed progress rolls back to
the selected generation. No automatic catch-up, provider re-observation, Cycle
rerun, activation, Transaction resubmission, or release occurs merely because
the process restarted.

## Load Order

Allocation integration extends checkpoint load order:

1. immutable identity and definition roots;
2. Clock;
3. Businesses, Workforce, Goods, Actors;
4. Inventory and Transactions;
5. Orders/Contracts;
6. Production definitions and plans;
7. Scheduler definitions/runtime;
8. Planning definitions/runtime/provenance;
9. Allocation providers, definitions, runtime, Commitments, and evidence;
10. Production Runs with Allocation references;
11. future Execution runtime/evidence; and
12. final cross-owner validation.

Runtime publication occurs only after the complete generation validates.

## Rationale

Pure Execution contracts need to exist before Allocation handoff APIs are
frozen, but live Execution integration does not need to be combined with the
first Allocation gate. This sequence keeps each architecture change
reviewable:

- harden platform lifecycle;
- prove pure Execution;
- prove one provider;
- migrate schemas atomically;
- activate Allocation with current Production;
- then connect generic Execution.

## Consequences

### Positive Consequences

- No stage or persistence change precedes lifecycle hardening.
- A concrete provider proves M22D before broad registration.
- Allocation authorization becomes real before generic Execution depends on
  it.
- Planning, Allocation, Production, Execution, Scheduler, Transactions, and
  Inventory retain singular ownership.
- Each migration is checkpoint-coordinated.
- The first live vertical slice remains bounded and testable.

### Negative Consequences

- More milestones are required before live generic Execution.
- Production gains an Allocation gate before later adopting Execution.
- The first provider proves a narrow slice rather than broad capacity.
- Current saves require coordinated Scheduler, Planning, Production, and
  Allocation migration.
- Gameplay exposure remains delayed.

## Compatibility

Existing stage ids and order remain unchanged except additive stage 350 after
approved migration. Existing Work ids and submission sequences remain stable.
Existing Production definitions and Plans retain identity.

Legacy active Production Work without Allocation authorization cannot be
silently treated as authorized. Owner policy must choose deterministic
completion under legacy rules, explicit pause/migration, or fail-visible
operator recovery.

## Migration

Migration is one coordinated checkpoint operation:

- validate prior Scheduler stage set;
- insert Allocation stage 350;
- create empty Allocation owner state;
- migrate Planning/Production references additively;
- classify existing nonterminal Production Work;
- preserve all histories and evidence;
- record migration correlation and source digests;
- publish one complete generation; and
- retain prior generation/legacy files.

No independent subsystem migration can become active before the coordinated
generation commits.

## Replay Implications

Replay includes:

- provider descriptor and observations;
- Allocation Cycle input and policy;
- Request/Set/Commitment identities;
- Scheduler stage ordering;
- Planning submission provenance;
- activation/release evidence;
- Production/Execution attempts; and
- exact Transaction result.

Re-observing live providers is not replay. Replay consumes captured immutable
observation bundles.

## Security And Integrity Implications

- Providers cannot publish Commitments.
- Planning cannot activate or release sets.
- Production/Execution cannot fabricate active authorization.
- Scheduler cannot mutate Allocation runtime.
- Clients cannot create stage Work or Commitments.
- Complete-set activation prevents partial authorization.
- Checkpoint generation prevents mixed authorization and execution state.

## Testing Requirements

Required tests by gate:

- one concrete provider success/empty/failure/conflict/replay;
- provider purity and owner-boundary tests;
- Scheduler schema migration inserts only stage 350;
- old Work ids, sequences, runtime, and finalized tick preserved;
- Planning submission creates exact linked Production and Allocation state;
- failure/compensation at every submission step;
- stage order 300, 350, 400 under construction-order variation;
- incomplete provider bundle prevents Cycle;
- one Cycle per Work and duplicate Cycle rejection;
- no Allocation-scheduled Work;
- complete activation before Production progress;
- missing/WAITING/FAILED/EXPIRED set gate behavior;
- no side effect before activation;
- successful completion release;
- failure/cancellation/expiration release;
- duplicate release and conflicting release;
- Capacity available only next cycle;
- no recursive Allocation;
- checkpoint interruption at every integration publication;
- recovery without rerun/resubmission;
- migration of active legacy Production Work under approved policy;
- replay from captured observations;
- future Execution adapter consumes the same activation contract;
- current Production, Grinder, Bandsaw, and Packaging gameplay regression; and
- no player-presence or wall-clock dependency.

## Alternatives Rejected By This Proposal

- **Live Allocation before any Execution foundation:** rejected because final
  handoff contracts would be designed without the future consumer.
- **One joint Allocation/Execution vertical slice:** rejected as too broad for
  reliable ownership, migration, and recovery review.
- **Allocation persistence before a live consumer:** rejected as speculative
  and incompatible with the checkpoint gate.
- **Allow Production to infer Capacity directly:** rejected because it bypasses
  Allocation Commitment authority.

## Unresolved Questions

Owner decisions required:

1. Confirm Option 2 sequencing.
2. Select the exact first production-grade provider and capacities it exposes.
3. Choose migration policy for nonterminal legacy Production Work.
4. Confirm whether Gate 2 provider code may exist unregistered before
   checkpoint implementation.
5. Define WAITING Work resubmission trigger without an internal Allocation
   retry loop.
6. Confirm expiration policy and owner of expiration requests.
7. Confirm whether generic Execution M23A-M23C numbering remains the preferred
   milestone naming.
8. Confirm when gameplay/manual validation becomes required.

## Owner Approval Checklist

- [ ] Approve all AH-1 prerequisites.
- [ ] Approve Option 2 sequencing.
- [ ] Approve the first provider scope.
- [ ] Approve stage 350 and Scheduler migration.
- [ ] Approve Planning handoff responsibilities.
- [ ] Approve Allocation Cycle invocation boundary.
- [ ] Approve Production activation gate.
- [ ] Approve release and next-cycle Capacity rules.
- [ ] Approve checkpoint/evidence participation.
- [ ] Approve legacy migration behavior.
- [ ] Approve vertical-slice and recovery tests.
- [ ] Authorize an accepted M22E/M22F sequencing Decision.
- [ ] Separately authorize each implementation milestone.
