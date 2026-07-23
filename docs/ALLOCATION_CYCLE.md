# Deterministic Allocation Cycle

Status: RFC-0022 Revision 2 Milestone M22C implemented

Milestone M22C adds the pure Java deterministic Allocation Cycle that evaluates
explicit immutable snapshots, accounts for existing consuming Commitments,
selects observed Capacity, constructs complete Commitment sets, and publishes
approved results atomically through `AllocationRuntimeService`.

The governing design remains
[`RFC-0022_RESOURCE_ALLOCATION_ENGINE.md`](RFC-0022_RESOURCE_ALLOCATION_ENGINE.md).
M22A definitions and M22B runtime remain compatible. Scheduler, provider,
persistence, Planning, Production, and gameplay integration remain deferred.

## Ownership Boundaries

Allocation owns:

- immutable cycle input, result, summary, failures, report, and trace evidence;
- deterministic first-fit selection and Commitment construction;
- detached cycle-local Capacity accounting;
- atomic AllocationSet evaluation;
- atomic publication through the Allocation runtime boundary.

Authoritative provider systems still own Resources and Capacity. Planning owns
decision artifacts. Execution subsystems own executable definitions and
runtime. Scheduler owns work ordering. Transactions own economic mutation, and
Inventory owns quantities.

The cycle consumes immutable `ExternalReference` values and observed snapshots.
It performs no live provider lookup and imports no concrete owner package.

## Explicit Cycle Input

`AllocationCycleInput` contains:

- `AllocationCycleContext`, including the authoritative simulation tick,
  deterministic Cycle id, first-fit policy id, metadata, and schema version;
- observed Resource and Capacity snapshots for that tick;
- the complete immutable Allocation definition and runtime snapshots needed
  for reference validation;
- an explicit canonical candidate AllocationSet id batch.

Construction defensively copies, sorts, bounds, duplicate-checks, and validates
all collections. Equivalent unordered inputs have equal canonical input
digests. The candidate batch may contain at most 50,000 Sets; complete
definition and runtime snapshots have separate 100,000-entry safety bounds.

Cycle construction rejects malformed ticks or schemas, duplicate identities,
unknown associations, conflicting Capacity keys, impossible exclusivity,
unsupported policy, incompatible active Commitments, stale observations,
multiple Planning Cycles in one candidate batch, and candidate runtime states
other than REQUESTED or WAITING.

## Canonical Pipeline

`AllocationCycleExecutor` records these phases exactly once:

1. Capture explicit cycle input.
2. Validate the cycle envelope.
3. Validate references and associations.
4. Observe supplied active Commitments.
5. Construct the detached working Capacity ledger.
6. Canonically order eligible AllocationSets.
7. Evaluate every AllocationSet atomically.
8. Construct private Commitments for successful Sets.
9. Validate the complete proposed result.
10. Publish the result atomically.
11. Produce immutable report, summary, history, and trace evidence.

The executor is non-recursive. It reads no wall clock, randomness, filesystem
order, live subsystem state, Minecraft state, or NeoForge state.

## Working Capacity Ledger

`WorkingCapacityLedger` is temporary and nonpersistent. Each immutable entry
contains:

- Capacity id and key;
- Resource category, availability, and exclusivity;
- observed, existing-committed, proposed-committed, and remaining quantities;
- observation tick and Resource/Capacity evidence references;
- canonical existing and proposed Commitment ids;
- canonical consuming AllocationSet ids.

Every entry enforces:

```text
remaining =
    observed
    - existing active commitments
    - newly proposed commitments
```

Arithmetic uses exact `AllocationQuantity` values. Units must match exactly.
There is no floating point, unit conversion, negative remainder, hidden
reservation, or persisted ledger.

## Existing Commitments

Only Commitments attached to ALLOCATED or ACTIVE runtime snapshots consume
Capacity. RELEASED, FAILED, and EXPIRED runtime does not consume Capacity.
Every applicable Commitment is sorted and subtracted exactly once.

An active Commitment must resolve to the current Resource and Capacity
observations, match its Requirement category, type, and unit, and not exceed
observed Capacity. An expired Commitment still attached to consuming runtime
is a cycle-fatal lifecycle inconsistency. M22C reports it and performs no
implicit expiration or release transition.

## Exclusive And Shared Capacity

Schema-1 exclusive Resources expose exactly one compatible unit. The ledger
tracks one explicit consuming AllocationSet owner per exclusive Resource, in
addition to exact quantity accounting. A second active or proposed owner
receives deterministic EXCLUSIVITY conflict evidence.

Shared Capacity is divisible between AllocationSets through exact quantities.
One Requirement must still be satisfied completely by one Resource/Capacity
entry. Schema 1 does not fragment a Requirement across multiple Resources.

## Ordering And Fairness

Sets are sorted once through their immutable source Request context:

1. horizon precedence ascending;
2. priority descending;
3. required-by tick ascending, absent last;
4. starvation age descending;
5. Need creation tick ascending;
6. stable request sequence ascending;
7. AllocationRequest id ascending;
8. AllocationSet id as the final batch tie-break.

Starvation age is derived from the explicit cycle tick and immutable Need
creation tick. There is no mutable starvation counter, weighted score,
priority mutation, or random tie-break.

## Deterministic First Fit

For each Set, Requirements are evaluated by Requirement id. Compatible
Capacity candidates are ordered by Resource id and then Capacity id. The first
available candidate that can satisfy the complete Requirement is selected.

Category and Capacity type must match. Capacity unit must match exactly. An
exact Resource Requirement can use only its explicit Capacity key. Missing or
incompatible structural references fail visibly; ordinary finite Capacity
loss produces WAITING evidence.

## Atomic AllocationSet Evaluation

Each Set receives a private ledger branch. Successful Requirement reservations
remain private until every Requirement succeeds.

On complete success:

- the branch is merged into the parent cycle ledger;
- exactly one private Commitment is constructed per Requirement;
- the evaluation outcome is ALLOCATABLE.

On scarcity or conflict:

- the entire branch is discarded;
- no Commitment is retained;
- the parent ledger remains byte-equivalent;
- the evaluation outcome is WAITING.

Malformed local evidence produces FAILED without changing the parent ledger.
One waiting or failed Set does not prevent unrelated valid Sets from being
evaluated.

## Commitment Construction

Commitments use the existing immutable M22A definition. Identity is derived
from Cycle, Set, Requirement, Resource, Capacity, and schema identity.
Commitments contain exact quantity and unit, the authoritative cycle tick,
approved expiration evidence, and canonical Resource and Capacity observation
references.

Commitments are private until complete proposed-result validation and atomic
publication succeed. Lifecycle state remains in `AllocationSetRuntime`, not in
the immutable Commitment definition.

## Atomic Publication

M22C adds one package-confined publication operation to
`AllocationRuntimeService`.

Publication:

1. rejects an already-published Cycle;
2. verifies the expected definition and runtime snapshots;
3. rebuilds a complete candidate service state;
4. registers every proposed Commitment in the candidate;
5. applies every legal runtime transition in the candidate;
6. registers the report and engineering trace in the candidate;
7. swaps the complete validated candidate into the live service once.

No live service collection changes before the final swap. Deterministic test
faults after Commitment registration, runtime transitions, and report
registration prove that failed publication leaves definitions, runtime,
reports, history, and traces unchanged. Mutation-plus-rollback is not used.

Submitting an already-published Cycle returns `DUPLICATE_CYCLE`. It never
duplicates a Commitment, transition, report, trace, or Capacity subtraction.

## Runtime Transitions

M22C performs only these cycle-owned transitions:

- REQUESTED or WAITING to ALLOCATED after complete Commitment publication;
- REQUESTED to WAITING for ordinary scarcity.

A Set already in WAITING remains WAITING when a later explicit cycle still
lacks Capacity. M22C never transitions to ACTIVE, RELEASED, FAILED, or EXPIRED,
never retries internally, and never runs an expiration loop. A later explicit
cycle may allocate a WAITING Set from a newly supplied snapshot.

## Reports, History, And Trace

The M22B `AllocationReport` now records actual M22C outcome categories,
Commitment ids, conflict evidence, ordering contexts, exact Capacity balances,
typed failures, policy identity, and deterministic operation counts.

`AllocationCycleTrace` records the eleven phases in fixed order. Each phase has
a deterministic operation count and canonical state digest. It is engineering
evidence, not gameplay content or a wall-clock profiler.

`AllocationCycleTraceRegistry` provides immutable canonical lookup by Cycle.
Runtime transition history remains the M22B immutable `AllocationHistory`.

## Digests And Replay

Canonical SHA-256 digests cover:

- complete input;
- Set ordering;
- initial and final ledgers;
- proposed Commitments;
- report;
- published service snapshot;
- engineering trace;
- complete result.

Digest construction uses explicit UTF-8 canonical fields and length framing.
It does not use `hashCode`, default `toString`, locale-sensitive formatting,
filesystem order, wall time, or random values.

Independent equivalent cycles produce equal results, Commitment identities,
selected Resources, runtime state, reports, histories, traces, ledgers, and
digests.

## Failure Isolation

Cycle-fatal malformed input prevents all publication. AllocationSet-local
scarcity or valid conflict discards only that Set branch. Final-result,
optimistic-state, duplicate-Cycle, transition, or publication failure changes
no Allocation-owned authoritative state.

Conflict records remain immutable and canonical. M22C emits CAPACITY and
EXCLUSIVITY conflicts for currently expressible schema-1 contention.
DEPENDENCY, CHAIN, and UNSUPPORTED remain valid report vocabulary but no current
M22C input model fabricates those relationships.

## Structural Bounds

M22C introduces structural collection bounds, not a behavior-changing
execution budget. Inputs beyond a bound fail explicitly and are never
truncated. Trace detail is fixed at eleven concise phase records.

Stress coverage includes:

- 100,000 observed Capacity entries in independent ordering passes;
- 100,000 existing Commitments in independent accounting passes;
- the existing 100,000-Requirement and 50,000-Request/Set domain passes;
- two independent 5,000-Set mixed shared/exclusive contention cycles.

No stress assertion depends on elapsed time.

## Architecture Validation

The architecture manifest now assigns Allocation ownership of:

- deterministic Allocation Cycles;
- detached Capacity accounting;
- Commitment selection and construction;
- the existing definition, lifecycle, report, and history contracts.

It also declares the canonical `butchercraft:allocation_cycle_traces`
registry. Dependency-boundary tests continue to reject Scheduler, Planning,
Production, Inventory, Transaction, Minecraft, NeoForge, persistence,
provider, wall-clock, and randomness dependencies.

## Explicit Exclusions

M22C does not add:

- Scheduler stage 350 or Allocation Work registration;
- Planning submission or persistence changes;
- Production execution gating or ACTIVE transitions;
- live Resource or Capacity providers;
- Inventory or Transaction integration;
- Allocation disk persistence, codecs, save migration, or reload behavior;
- Minecraft, NeoForge, gameplay, commands, menus, networking, or assets;
- Logistics, Workforce simulation, Utilities, or Maintenance behavior.

M22D through M22F remain separately gated for providers, persistence,
Scheduler/Planning/Production integration, and execution gating.
