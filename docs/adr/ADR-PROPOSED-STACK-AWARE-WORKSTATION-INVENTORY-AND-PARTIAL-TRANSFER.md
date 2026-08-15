# ADR-DG-004: Stack-Aware Workstation Inventory And Partial Transfer

Status: RATIFIED ARCHITECTURAL DIRECTION

Decision identifier: DG-004

Package: Workstation Inventory And Material Handling Compatibility

Authority: Owner-ratified architectural direction. This document authorizes
the stack-aware ownership, identity, persistence-evolution, recovery,
compatibility, and milestone sequence defined here. It does not itself
authorize runtime implementation, item stack-size changes, persistence
migration, gameplay changes, employee Patty Former operation, IM-030A,
IM-030B, IM-031, or Architecture Manifest implementation claims. Every
implementation milestone remains separately gated.

Canonical platform references:

- [`CONSTITUTION.md`](../../CONSTITUTION.md)
- [`Core Principles`](../../CORE_PRINCIPLES.md)
- [`Project Rules`](../../PROJECT_RULES.md)
- [`Technical Architecture`](../../TECHNICAL_ARCHITECTURE.md)
- [`Architecture Guide`](../BCSE_ARCHITECTURE_GUIDE.md)
- [`Architecture Validation Framework`](../ARCHITECTURE_VALIDATION_FRAMEWORK.md)
- [`Platform Canonicalization Addendum`](ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md)
- [`DG-002 Material Handling Custody And Recovery`](ADR-PROPOSED-MATERIAL-HANDLING-CUSTODY-AND-RECOVERY.md)
- [`DG-002A Workstation Endpoint Durability And Instance Identity`](ADR-PROPOSED-WORKSTATION-ENDPOINT-DURABILITY-AND-INSTANCE-IDENTITY.md)
- [`DG-003 Execution Handler Registry Evolution And Save Compatibility`](ADR-PROPOSED-EXECUTION-HANDLER-REGISTRY-EVOLUTION.md)
- [`Material Handling`](../MATERIAL_HANDLING.md)
- [`Workstation Framework`](../WORKSTATION_FRAMEWORK.md)
- [`Workforce Framework`](../WORKFORCE_FRAMEWORK.md)

## 1. Decision In Plain Language

ButcherCraft's exact-one-item workstation rule is a safe schema-1 foundation,
not the final production model. A future stack-aware workstation must be able
to hold multiple compatible units, withdraw an exact quantity, preserve the
remainder, and later merge the transferred quantity into a compatible
destination without losing or duplicating product.

This decision evolves the existing owner protocols instead of creating a new
authority:

- Workstation continues to own slot contents, capacity, split and merge
  validation, endpoint effects, the durable endpoint journal, and owner
  results.
- Material Handling continues to own only the exact payload while it is in
  transit and the cross-workstation transfer lifecycle.
- Workforce continues to own employee assignment and navigation intent while
  observing custody.
- Execution and Scheduler continue to own operation authorization, lifecycle,
  timing, and dispatch, not product custody.

The central rule is that a partial withdrawal is one durable Workstation-owned
state transition:

```text
exact source pre-stack N
  -> exact transfer payload Q
  -> exact source post-stack N-Q
```

All three exact states are recorded. Recovery never recreates the remainder by
subtracting from an assumed old value.

## 2. Repository Baseline

The current repository implements a deliberately narrow schema-1 model:

| Area | Current repository state |
| --- | --- |
| Product items | `ProductTestItem` applies `stacksTo(1)` to all product-bearing items, including Beef Trim, Ground Beef, and Beef Patties |
| Workstation slots | `WorkstationInventory.getSlotLimit(int)` returns `1` for every slot |
| Source endpoint | Observation requires the complete exact source stack; committed withdrawal clears the slot |
| Destination endpoint | Observation requires an empty input slot; committed deposit inserts the complete exact stack |
| Source return | Return requires an empty compatible source slot and restores the complete exact stack |
| Endpoint freshness | Binds instance, slot, inventory revision, endpoint-effect revision, exact slot-content digest, lock, operation state, owner-result sequence, and configuration |
| Effect Identity | Binds endpoint schema, Workstation Instance Identity, invocation identity, and effect kind; same identity with different journal content conflicts |
| Endpoint journal | Schema 1 stores one exact effect stack and canonically derives withdrawal post-state as empty or deposit/return pre-state as empty |
| Owner result | Binds the same exact effect stack plus pre/post freshness and revisions |
| Material Handling | Schema 1 records a positive quantity and exact encoded `ItemStack`; every endpoint payload count must equal the transfer quantity |
| Workforce | Assignment persistence references a transfer and endpoints but owns no quantity or stack payload |
| Carry view | The current derived display explicitly reduces the visible copy to count one and the Employee entity rejects any other count |
| Processing | `WorkstationInventoryMaterialStore` reads product-component quantity without applying `ItemStack` count, while `WorkstationInventoryCommitPlan` clears selected input slots and replaces output stacks |
| Startup | World Identity, Workstation instance registry, endpoint journal, block-entity projection, Material Handling, then Workforce reconciliation |

The exact stack codec and Material Handling transfer record are already
count-aware at a data-shape level. That does not make the runtime stack-aware.
The endpoint observation, journal, projection effect, workstation validators,
slot capacity, carry view, and processing commit plan all enforce or assume
the exact-one model.

DG-002A schema 1 makes these canonical assumptions:

- withdrawal pre-state is the exact one-unit stack;
- withdrawal post-state is empty;
- deposit and source-return pre-state is empty; and
- deposit and source-return post-state is the exact one-unit stack.

Those assumptions are embedded in canonical digests and recovery validation.
Changing them without a versioned protocol would reinterpret existing durable
evidence and is therefore prohibited by AI-0018, AI-0021, and AI-0028.

The repository contains the IM-029 implementation at the current baseline and
the Architecture Guide describes it as implemented. `MILESTONES.md` still
retains its manual-acceptance checklist. DG-004 does not alter that historical
status text.

## 3. Scope And Gates

DG-004 ratifies architecture for:

- stack-aware Workstation slot capacity;
- exact partial withdrawal;
- exact compatible destination merge;
- exact compatible source return;
- quantity-aware endpoint evidence;
- versioned Workstation and Material Handling persistence compatibility;
- conservative migration and downgrade gates; and
- future derived multi-count carry presentation.

DG-004 does not authorize:

- any Java, resource, schema, migration, item, or gameplay change;
- changing an item or slot capacity;
- player-selectable transfer quantity;
- employee transfer quantities greater than one;
- processing an entire stack in one operation;
- employee Patty Former operation;
- Production-driven assignment;
- automatic workstation selection;
- employee inventory;
- a new Logistics authority;
- economic Inventory integration; or
- marking future Architecture Manifest concepts implemented.

## 4. Architectural Invariants And Ownership

| Fact or action | Singular owner | Failure prevented |
| --- | --- | --- |
| Workstation slot content and revision | Workstation | Split-brain inventory and stale mutation |
| Slot capacity and stack compatibility | Workstation | Overfill and component-unsafe merge |
| Exact split into transfer and remainder | Workstation | Quantity loss, duplication, or foreign mutation |
| Endpoint preparation, effect, and owner result | Workstation | Material Handling bypassing endpoint authority |
| Workstation endpoint journal and reconciliation | Workstation | Unprovable consequential effects after crash |
| Exact in-transit payload and transfer lifecycle | Material Handling | A second in-transit product authority |
| Employee transfer assignment and movement intent | Workforce | Custody leaking into entity state |
| Derived carried-item presentation | Workforce, derived from Material Handling | Visual state becoming inventory authority |
| Operation authorization and lifecycle | Execution | Transfer implicitly starting processing |
| Timing and dispatch | Scheduler | Inventory state becoming a scheduling authority |
| Economic quantities | Economic Inventory | Minecraft slot state becoming economic authority |

The following are permanent invariants of the ratified model:

1. Exactly one authority owns each quantity at every proven lifecycle point.
2. Material Handling never computes or mutates a Workstation remainder.
3. Workstation never owns the cross-transfer lifecycle or in-transit custody.
4. Quantity-sensitive state uses quantity-sensitive Content Identity.
5. A consequential endpoint effect is acknowledged only after its exact
   effect and immutable owner result are durably published.
6. No partial destination acceptance occurs in the first stack-aware schema.
7. A retry observes an existing identical effect or is rejected as a conflict;
   it never repeats a committed split or merge.
8. Capacity, transfer quantity, recipe consumption, duration, and throughput
   are separate facts owned by their existing subsystems.

## 5. Stack-Aware Workstation Inventory

### 5.1 Slot Representation

A Workstation slot remains one Minecraft `ItemStack`. A non-empty stack is an
exact registry-aware item plus its complete data-component set and a positive
count. Empty is represented by one canonical empty-slot identity, never by an
invented product payload with count zero.

Stack-aware storage does not make every slot a 64-unit slot. Workstation owns a
versioned slot-capacity policy for every affected slot. The effective capacity
is:

```text
minimum(item maximum stack size, Workstation slot capacity)
```

The Workstation slot capacity may be lower than the item maximum. One physical
slot cannot exceed the item's maximum; a future larger machine capacity must
use multiple slots or a separately approved aggregate-storage model.

The slot-capacity policy and every behavior-affecting compatibility rule are
covered by Workstation Configuration Identity. A policy change invalidates
prior endpoint freshness and cannot alter a prepared effect silently.

### 5.2 Compatible Stack Semantics

Two non-empty stacks may merge only when:

- they have the same registered item identity;
- their complete data-component maps are canonically equal;
- the item and its components are approved as split/merge safe;
- the destination Workstation accepts the item for that exact slot and
  operation state; and
- the resulting count does not exceed effective slot capacity.

Matching registry ids alone are insufficient. Different quality, freshness,
temperature, packaging, custom data, durability, or future lot components do
not merge unless their complete component representations are equal and the
component contract explicitly permits homogeneous stacking.

Partial splitting copies no authority and invents no component values. The
transfer and remainder retain the exact component representation of the
complete pre-stack and differ only in count. A component whose semantics are
unique-per-stack, aggregate, or unsafe to duplicate keeps that item
non-stackable until a separate component migration defines safe behavior.

DG-004 does not define pounds, kilograms, or economic quantity from Minecraft
stack count. It also does not multiply or rewrite `ProductStackData` quantity
fields. Recipe consumption remains an explicit operation concern.

## 6. Partial Source Withdrawal Model

Let the complete authoritative source pre-stack be `P` with count `N`, and let
the requested transfer quantity be `Q`.

An accepted partial withdrawal requires:

```text
1 <= Q <= N
transfer payload T.count = Q
remainder R.count = N - Q, or canonical empty when N = Q
T item and components = P item and components
R item and components = P item and components when R is non-empty
T.count + R.count = P.count
committed source post-state = R
```

The Workstation owner validates and binds:

- Workstation Instance Identity and generation;
- source endpoint and slot;
- endpoint protocol version;
- source inventory and endpoint-effect revisions;
- exact complete pre-stack payload and Content Identity;
- requested quantity;
- exact transfer payload and Content Identity;
- exact remainder payload and Content Identity, or canonical empty identity;
- exact committed post-state identity;
- pre-effect and post-effect Freshness Identities;
- operation-state identities;
- slot-capacity and endpoint Configuration Identity;
- stable invocation and Effect Identity; and
- journal sequence and immutable owner result.

Material Handling submits the requested quantity and consumes the result. It
does not split `P`, derive `R`, write `R`, or decide whether the components are
safe to split.

## 7. Quantity-Sensitive Identity Model

The existing exact-stack encoding includes stack count in the encoded
`ItemStack`, and its Content Identity is therefore quantity-sensitive. Schema
2 makes the distinct roles explicit instead of relying on one payload field:

| Identity | Meaning |
| --- | --- |
| Complete Pre-Stack Content Identity | Exact source or destination slot content before the effect |
| Transfer Payload Content Identity | Exact quantity and components crossing custody boundaries |
| Remainder Content Identity | Exact non-transferred source content, or canonical empty identity |
| Committed Post-State Identity | Exact endpoint slot state at the committed post revision |
| Endpoint Freshness Identity | Owner-issued identity covering the exact slot state plus all other validation dependencies |
| Effect Identity | Stable identity for one logical endpoint effect and retry family |
| Owner-Result Evidence Identity | Immutable proof of the exact committed effect and outcome |

Remainder Content Identity and Committed Post-State Identity are separate
typed facts even when both refer to the same encoded non-empty stack. The
post-state identity additionally binds endpoint instance, slot, revision,
effect, schema, operation state, and configuration through post-effect
freshness.

Effect Identity remains stable across retries of the same logical invocation.
Canonical effect content, stored separately, binds pre-state, quantity,
payload, remainder/post-state, freshness, capacity policy, and configuration.
Reuse of one Effect Identity with different canonical effect content is an
explicit identity conflict.

## 8. Durable Endpoint Journal Schema 2

### 8.1 Decision

Partial split and merge semantics require Workstation endpoint journal schema
2. An additive reinterpretation of schema 1 is rejected because schema-1
constructors, digests, observations, preparations, and owner results
canonically derive an empty withdrawal post-state and an empty deposit/return
pre-state.

The version-2 journal envelope supports versioned records:

- retained schema-1 records preserve their original fields, identities,
  digests, and owner results as immutable legacy evidence;
- every new stack-aware effect is a schema-2 record; and
- no schema-1 record is recomputed using schema-2 identity algorithms.

The Workstation instance registry and existing Workstation Instance
Identities do not change merely because the endpoint effect protocol evolves.
The implementation must separate the currently shared schema constant where
necessary so that a journal protocol change does not reallocate or rewrite
Workstation instance identity.

### 8.2 Minimum Schema-2 Record

One schema-2 endpoint journal record stores at minimum:

- record and endpoint-protocol versions;
- journal sequence and owner revision;
- Workstation Instance Identity, endpoint kind, and slot;
- invocation and Effect Identity;
- exact complete pre-stack payload and Content Identity, including canonical
  empty where permitted;
- requested quantity;
- exact transfer payload and Content Identity;
- exact remainder payload and Content Identity for withdrawal, including
  canonical empty;
- exact committed post-stack payload and Content Identity;
- pre/post inventory and endpoint-effect revisions;
- pre/post operation-state identities;
- previous owner-result sequence;
- pre/post endpoint Freshness Identities;
- slot-capacity policy and endpoint Configuration Identity;
- journal lifecycle state;
- immutable owner result and Evidence Identity when committed; and
- typed failure, Recovery Required, or Unknown Outcome detail.

For withdrawal, remainder and post-stack must be identical. For deposit and
return, no subtraction remainder exists; the record still stores the exact
pre-stack, transfer payload, and merged post-stack.

## 9. Atomic Publication Boundary

Schema 2 preserves DG-002A publication ordering:

1. Workstation observes and validates the complete pre-state.
2. Workstation computes the exact transfer and post-state candidate.
3. Workstation durably publishes `PREPARED`, including all exact state and the
   slot lock.
4. Inside the serialized Workstation-owner boundary, Workstation revalidates
   freshness and constructs one complete `EFFECT_COMMITTED` candidate.
5. That single candidate durably freezes the exact pre-state, transfer
   payload, remainder/post-state, revisions, and immutable owner result.
6. Only after successful durable publication does Workstation apply the exact
   post-state to the live block-entity projection.
7. Workstation validates the projection and publishes the frozen result for
   Material Handling observation.

No acknowledged state may expose the transfer payload without also proving
the source post-state. No acknowledged state may expose the source post-state
without also proving the transfer payload.

At source `EFFECT_COMMITTED`, the source owns the exact remainder and Material
Handling owns the exact transfer payload according to the already-ratified
DG-002/DG-002A authority transition. The Workstation journal owns evidence,
not in-transit custody. If Material Handling has not yet published its normal
`IN_TRANSIT` view, startup reconciliation derives that view only from its
matching prepared transfer and the immutable Workstation owner result.

At destination or return `EFFECT_COMMITTED`, the destination Workstation owns
the exact merged post-stack and Material Handling no longer owns the deposited
payload. Material Handling retains recovery evidence until its matching owner
result is observed and terminal state is durably published.

## 10. Stack-Aware Destination Deposit

A destination effect binds current destination pre-stack `D`, complete
transfer payload `T`, and exact post-stack `M`.

The Workstation owner accepts only:

- an empty compatible destination, where `M = T`; or
- a non-empty destination with the same item and complete component map,
  where `M.count = D.count + T.count` and all components remain identical.

It rejects:

- incompatible item identity or components;
- a destination that is not valid for the slot;
- stale destination freshness;
- an operation or endpoint lock conflict; and
- any result above effective slot capacity.

Schema 2 uses **all-or-nothing destination deposit**. If the destination can
accept only part of `T`, it accepts none of it. The destination remains `D`,
Material Handling retains all of `T`, and the transfer becomes blocked or
Recovery Required according to whether the rejection is proven and
recoverable. No schema-2 result represents a partial deposit.

This policy avoids dividing one custody payload during deposit, avoids a new
partial-custody lifecycle, and makes duplicate merge detection exact. Partial
destination acceptance requires a later ADR and schema revision.

## 11. Cancellation And Source Return

Cancellation after withdrawal never restores the original pre-stack by
assumption. It requests one new Workstation-owned source-return effect for the
exact payload still proven in Material Handling custody.

The source owner observes the source's current state and applies the same
all-or-nothing merge rules as destination deposit:

| Current source state | Return result |
| --- | --- |
| Empty and compatible | Insert the exact custody payload |
| Compatible components with sufficient capacity | Merge the complete payload |
| Compatible components without sufficient capacity | Reject without mutation; retain Material Handling custody |
| Incompatible item or components | Reject without mutation; retain Material Handling custody |
| Revision changed before preparation | Discard stale observation and re-observe; no mutation |
| Revision changed after preparation | Prepared slot lock or freshness failure blocks the effect; no silent rebase |
| Workstation replaced | Old instance cannot be inherited; retain custody and enter Recovery Required or Unknown Outcome according to evidence |
| Source unavailable or unloaded | Do not force-load; retain custody and Recovery Required |

The transfer-level return invocation remains stable. Read-only observations
may be repeated before one preparation is accepted. Once a schema-2
preparation is durable, its exact content is immutable. Same Effect Identity
and same content observes the existing state; same identity and different
content conflicts.

Material Handling clears custody only after the return owner result proves the
complete merge. A rejected, unavailable, full, or incompatible source never
causes payload deletion, automatic rerouting, or item reconstruction.

## 12. Transfer Quantity And Gameplay Authorization

Schema-2 endpoint and Material Handling architecture accepts a positive,
bounded quantity `Q`. The bound is the exact source count, exact destination
capacity, item maximum, Workstation policy, serialized payload limit, and
configured transfer policy.

Architectural capability does not grant gameplay access. An implementation
may activate quantity-aware protocols while an employee command continues to
authorize exactly one item per assignment. Arbitrary player-selected quantity,
batch transport, route search, queues, and autonomous logistics remain gated.

The Material Handling Configuration Identity covers the active transfer
quantity policy. Changing an authorized quantity limit cannot reinterpret an
existing Transfer Identity or prepared effect.

## 13. Capacity Is Not Processing Quantity

The following are separate facts:

- item maximum stack size;
- Workstation slot capacity;
- transfer quantity;
- operation input consumption quantity;
- operation output quantity;
- operation duration; and
- machine throughput.

A Grinder input holding 64 Beef Trim does not authorize one operation to
consume 64. A Patty Former input holding 64 Ground Beef does not authorize one
operation to consume 64.

The current material-store adapter does not multiply processing quantity by
Minecraft stack count, and `WorkstationInventoryCommitPlan` clears selected
consumed slots and replaces output stacks. Therefore no processing input or
output slot may be activated above one until its Workstation-owned operation
commit plan can:

- bind each exact input pre-stack;
- consume the operation's configured bounded count;
- preserve each exact input remainder;
- bind each exact output pre-stack;
- merge only exact compatible generated output within capacity;
- preserve every unaffected output; and
- publish the complete input/output candidate atomically.

Initial operation consumption and production remain one item per authorized
operation unless a later owner-approved recipe or throughput milestone changes
them. Repeated operations may accumulate compatible output only through the
bounded Workstation-owned commit plan; no hidden loop or whole-stack operation
is implied.

Any change to an Execution handler's persisted behavior or Handler Contract
Identity remains governed by DG-003. Stack-aware implementation must either
preserve the exact historical operation contract for retained work or use a
recognized compatibility profile. It may not treat changed processing
semantics as additive compatibility.

## 14. Material Handling Persistence Impact

The current Material Handling domain already stores:

- positive quantity;
- exact transfer `ItemStack` payload;
- quantity in Transfer Identity input;
- exact in-transit custody; and
- validation that every endpoint payload count equals transfer quantity.

Its lifecycle and singular-custody model do not need redesign. Its persistence
schema must nevertheless advance to schema 2 when stack-aware endpoint effects
are activated because active Material Handling records embed endpoint
observations, preparations, owner results, identities, and digests whose
canonical schema-2 shapes differ from schema 1. Keeping the document labeled
schema 1 would silently change schema-1 interpretation.

The schema-2 Material Handling candidate supports immutable legacy schema-1
terminal evidence and schema-2 transfer records. New Transfer Identities and
Configuration Identities identify the schema-2 protocol. Existing schema-1
Transfer Identities are never recalculated.

No new custody owner, lifecycle state, persistence file, or item
reconstruction rule is introduced.

## 15. Workforce And Carry View

Workforce remains an observer of transfer quantity and custody. Its assignment
continues to reference the authoritative Material Transfer Identity and exact
endpoints; it does not persist or mutate the `ItemStack` payload.

The existing assignment schema need not change merely because Material
Handling can carry a larger count. If later Workforce behavior makes quantity
an assignment decision rather than an observed transfer fact, that would
require a separately versioned Workforce change.

The carried item remains a non-authoritative defensive display copy. A future
multi-count carry view may render the same registered item, complete
components, and exact count proven by Material Handling custody. It appears
only while custody is proven and disappears at a committed deposit or return.
Client rendering, Employee equipment, and Employee NBT never become custody.

The current count-one display restriction is an implementation gate. DG-004
does not authorize removing it.

## 16. Schema-1 Compatibility And Migration

### 16.1 Compatibility Classes

| Existing world state | Automatic migration decision |
| --- | --- |
| No Material Handling or endpoint files and no prior-use markers | Create empty schema-2 candidates under normal legacy rules |
| Only reconciled or terminal schema-1 endpoint records and no active transfer | Eligible after complete candidate validation; preserve schema-1 records and identities |
| Existing Workstation instance registry | Preserve schema-1 instance identities, generations, and next-generation monotonicity |
| Existing Grinder, Patty Former, or Cutting Table slot counts zero or one | Representation is compatible; validate before publication |
| Active schema-1 one-unit transfer or Workforce assignment | Do not reinterpret; finish or cancel under schema-1 authority before migration |
| Schema-1 `PREPARED`, committed-but-unreconciled, or externally referenced endpoint effect | Block migration until reconciled and safely retained |
| `RECOVERY_REQUIRED` | Block automatic migration until owner evidence resolves the state |
| `UNKNOWN_OUTCOME` | Block migration; operator resolution is required |
| Active Execution operation affected by changed handler semantics | Apply DG-003 compatibility classification; block if not proven compatible |

### 16.2 Migration Publication

Migration is deterministic, owner-controlled, candidate-based, and ordered:

1. load and validate the complete schema-1 Workstation instance registry;
2. load and validate the complete schema-1 endpoint journal;
3. reconcile every nonterminal or referenced schema-1 endpoint effect;
4. validate block-entity projections and prove all affected slot counts are
   representable;
5. load and validate Material Handling and Workforce assignments;
6. prove no active, Recovery Required, or Unknown Outcome transfer remains;
7. classify affected Execution handler compatibility under DG-003;
8. construct complete versioned Workstation and Material Handling candidates;
9. preserve legacy record identities and monotonic sequences;
10. read back and cross-validate all owner candidates; and
11. publish owner authority in the existing startup order.

The original files remain authoritative until every required candidate is
validated. There is no destructive in-place rewrite and no fallback to empty
state.

Migration does not increase existing stack counts. It enables later
owner-authorized behavior after publication.

## 17. Downgrade

Software that understands only schema 1 must never open stack-aware state as
mutable authority.

Downgrade is unsupported by default after any schema-2 endpoint or Material
Handling state has been published. A future reverse migrator may permit it
only if it proves all of the following:

- no active, prepared, Recovery Required, or Unknown Outcome endpoint effect;
- no active Material Handling transfer or custody;
- no active Workforce assignment referencing a newer transfer;
- no Workstation slot count above one;
- no partial-withdrawal, merged-deposit, or merged-return record is required
  by retained evidence;
- every retained owner result and external reference is readable or exported
  through an approved evidence-preserving migration;
- DG-003 classifies retained Execution work as compatible;
- all instance generations and owner sequences remain monotonic; and
- the complete schema-1 candidate passes read-back validation before
  authority publication.

Without such a migrator, newer files are preserved and startup fails visibly
or enters an operator-visible read-only state. A runtime must not clamp counts,
discard records, split stacks into the world, or reset persistence to make a
downgrade appear successful.

## 18. Startup And Reconciliation

The controlling startup order remains:

1. World Identity;
2. Workstation instance registry;
3. Workstation endpoint journal and any authorized migration;
4. block-entity projection reconciliation;
5. Material Handling candidate validation against endpoint evidence;
6. Material Handling reconciliation and authority publication;
7. Workforce assignment and carry-view reconstruction; and
8. dependent operation or presentation services.

Schema-2 reconciliation compares the exact committed post-stack, not only
slot count or item id. A projection at the pre-state after
`EFFECT_COMMITTED` is advanced idempotently to the stored exact post-state. A
matching post-state confirms application. Any other content requires Recovery
Required or Unknown Outcome according to the available causal marker and may
not be repaired by arithmetic guesswork.

## 19. Partial-Transfer Recovery Matrix

In this table, `P` is the source pre-stack, `T` is the exact transfer payload,
`R` is the source remainder, `D` is the destination pre-stack, and `M` is the
exact destination or return merged post-stack.

| Boundary | Singular authoritative location | Deterministic recovery |
| --- | --- | --- |
| A. Before withdrawal preparation | Source Workstation owns all of `P` | No effect exists; a later request must observe current freshness |
| B. Withdrawal `PREPARED`, no effect | Source Workstation owns all of `P`; slot is locked | Restore/validate the prepared lock; explicit resume or cancellation, no automatic withdrawal |
| C. Effect applying | If `EFFECT_COMMITTED` did not publish, source owns `P`; if it published, source owns `R` and Material Handling owns `T` | Select the last complete journal candidate; never infer commit from the projection alone |
| D. Withdrawal committed with pre/transfer/post evidence | Source owns `R`; Material Handling owns `T`; Workstation journal owns evidence only | Reconcile projection to exact `R`, then expose the frozen owner result |
| E. Block-entity projection not reconciled | Source authority is exact journal post-state `R`; Material Handling owns `T` | Apply `R` idempotently from the committed journal and verify markers |
| F. Material Handling has not observed the source result | Source owns `R`; Material Handling custody of `T` is proven by matching prepared transfer plus committed owner result | Reconcile and publish normal custody; do not withdraw again |
| G. Material Handling accepted custody | Source owns `R`; Material Handling owns `T` | Resume only non-consequential lifecycle observation or explicit next preparation |
| H. Employee carrying | Source owns `R`; Material Handling owns `T`; Employee display owns nothing | Reconstruct the display from proven custody or clear it when custody is absent |
| I. Destination deposit `PREPARED` | Source owns `R`; Material Handling owns all of `T`; destination owns `D` | Restore destination lock; explicit resume/cancellation policy, no automatic deposit command retry |
| J. Destination effect committed | Source owns `R`; destination Workstation owns exact `M`; Material Handling owns no deposited quantity | Reconcile destination projection to `M` and expose frozen owner result |
| K. Material Handling has not observed destination result | Source owns `R`; destination owns `M` | Material Handling observes the existing result and publishes completion without merging again |
| L. Cancellation requested after custody | Source owns `R`; Material Handling owns `T` | Observe current source state and attempt one owner-authorized all-or-nothing return |
| M. Return `PREPARED` | Source owns its exact current pre-state; Material Handling owns `T` | Restore return lock; explicit resume only, no automatic merge command retry |
| N. Return committed | Source Workstation owns exact merged `M`; Material Handling owns no returned quantity | Reconcile source projection and publish cancellation completion without returning twice |
| O. Projection stale | Last complete committed journal candidate defines authoritative endpoint post-state | Reconcile exact encoded post-state when causal markers permit; otherwise block visibly |
| P. Endpoint replaced | Old Workstation Instance Identity retains its evidence and any proven location; replacement owns only its new state | Never inherit identity; retain custody or block the old effect as Recovery Required/Unknown Outcome according to proof |
| Q. Unknown Outcome | No normal product location may be asserted because singular location is unprovable | Freeze affected mutation, retain all evidence, forbid retry/reconstruction, and require operator recovery authority |

`UNKNOWN_OUTCOME` is the explicit absence of a provable singular location. It
does not grant two owners concurrent authority and it does not permit either
candidate location to be treated as normal mutable stock.

## 20. Idempotency And Concurrency

### 20.1 Idempotency

One logical source withdrawal, destination deposit, or source return has one
stable invocation and Effect Identity. Its canonical content digest binds:

- instance, generation, endpoint, and slot;
- effect kind and protocol version;
- exact pre-stack Content Identity;
- requested quantity;
- exact transfer Content Identity;
- exact remainder and post-state identities;
- pre/post revisions and freshness;
- operation-state identities; and
- capacity and endpoint Configuration Identities.

Same Effect Identity and same canonical content observes the existing journal
state or owner result. Same Effect Identity with different content is an
explicit conflict. A committed effect cannot be prepared or applied again.
These rules prevent double withdrawal, second remainder application, double
deposit, double return, and double merge.

### 20.2 Concurrent Effects

Read-only observations may both see the same revision. They do not authorize
mutation. Inside the serialized Workstation-owner boundary:

1. the first accepted preparation publishes the slot lock and expected
   freshness;
2. every later preparation for that slot sees the lock or stale freshness and
   is rejected; and
3. only the effect owning that lock may publish the next inventory and
   endpoint-effect revisions.

The same rule applies to concurrent source withdrawals, destination deposits,
source returns, menu actions, capabilities, and operation commits. All
authoritative mutation paths must honor the Workstation-owned slot lock and
revision. Correctness does not depend on thread timing or request arrival luck.

## 21. Product Stack-Size Direction

Owner ratification records the following future gameplay direction:

| Product | Approved candidate item maximum | Runtime status |
| --- | --- | --- |
| Beef Trim | 64 | Gated; currently 1 |
| Ground Beef | 64 | Gated; currently 1 |
| Beef Patties | 64 | Gated; currently 1 |

The current component model does not present an architectural conflict for
these three candidates when a stack contains homogeneous items with exactly
equal components. Minecraft stack count remains a gameplay abstraction; this
ADR does not define its conversion to physical weight or economic quantity.

Implementation must make stack size selective. `ProductTestItem` currently
forces every product-bearing item to one, so simply changing that shared
constructor would unintentionally change all products. Products outside the
three named candidates remain at their existing maximum until separately
approved. Component-bearing products that cannot satisfy homogeneous split
and merge rules remain non-stackable.

## 22. Architecture Manifest Implications

Only during separately authorized implementation may the Architecture
Manifest add concepts for:

- Workstation-owned stack-aware slot capacity;
- quantity-sensitive complete pre-stack, transfer, remainder, and post-state
  identity;
- Workstation-owned partial withdrawal;
- authoritative remainder publication;
- Workstation-owned compatible destination merge;
- quantity-aware endpoint effects and owner results;
- stack-aware cancellation return;
- Workstation endpoint schema-2 compatibility and migration;
- Material Handling schema-2 endpoint-evidence compatibility;
- all-or-nothing destination deposit; and
- non-authoritative exact-count carry presentation.

Every concept remains unimplemented until code, persistence, startup ordering,
migration, tests, and owner-authorized gameplay make it mechanically true.
The Java Architecture Manifest must not change during DG-004 ratification work.

## 23. Alternatives Rejected

- **Set every Workstation slot to 64:** rejected because item maximum and
  machine capacity are separate and some slots require lower limits.
- **Let Material Handling split the source stack:** rejected because it would
  become a second Workstation inventory authority.
- **Store only pre-stack and quantity, then calculate remainder on recovery:**
  rejected because recovery would rely on inference rather than an exact
  committed owner post-state.
- **Extend schema 1 without changing its version:** rejected because it would
  reinterpret canonical identities and persisted semantics.
- **Use item id without complete component equality:** rejected because it can
  merge materially different product state.
- **Accept as much as the destination can hold:** rejected for the initial
  schema because it creates split custody and another recovery lifecycle.
- **Process the entire input stack automatically:** rejected because capacity
  does not authorize recipe quantity or throughput.
- **Use Employee inventory for multi-count carrying:** rejected because
  Workforce and entities do not own custody.
- **Move exact stacks into economic Inventory:** rejected because economic
  quantity and Minecraft `ItemStack` custody remain separate.
- **Create a Logistics authority for splitting:** rejected because the
  existing Workstation and Material Handling owners already cover the required
  facts.

## 24. Ratified Implementation Sequence

The least disruptive sequence preserves completed milestone numbers:

1. **DG-004 - Stack-Aware Workstation Inventory And Partial Transfer
   Architecture.** This ratification records architecture without runtime
   change.
2. **IM-030A - Stack-Aware Workstation And Endpoint Foundation.** Add selective
   slot-capacity policy, bounded operation consumption and output merge,
   endpoint protocol and
   journal schema 2, versioned legacy-record support, migration/downgrade
   gates, exact partial split/merge/return owner results, and recovery tests.
   Do not change the three item maximums or expose multi-count gameplay in this
   foundation milestone.
3. **IM-030B - Product Stack Normalization And Material Handling Integration.**
   Activate max stack 64 only for Beef Trim, Ground Beef, and Beef Patties;
   configure approved machine capacities; activate Material Handling schema-2
   evidence integration; preserve one-unit employee authorization unless
   separately approved; and validate existing saves and routes.
4. **IM-031 - Employee Patty Former Operation.** Resume the already-gated
   operation milestone against the completed stack-aware foundation without
   changing Material Handling ownership.

No completed historical milestone is renumbered. Owner ratification refines
the previously referenced unimplemented `IM-030` into IM-030A and IM-030B;
neither milestone is authorized to begin by this document alone.

## 25. Implementation Risks And Required Validation

Future implementation must prove at minimum:

- exact count and component round trip for pre, transfer, remainder, and post;
- selective item maximums and Workstation-specific slot capacity;
- incompatible components never merge;
- partial withdrawal conservation for every `1 <= Q <= N` boundary used;
- all-or-nothing destination and return merge;
- exact recovery at every boundary in Section 19;
- no double split, remainder application, merge, deposit, or return;
- two same-revision observations cannot both commit;
- menu, capability, operation, and transfer paths honor one slot lock;
- operation consumption preserves stack remainder, output merge preserves
  existing compatible output, and both remain bounded;
- schema-1 identities and terminal evidence remain unchanged;
- active, Recovery Required, and Unknown Outcome schema-1 state blocks
  migration;
- Workstation Instance Identities and monotonic generations survive migration;
- DG-003 compatibility classification for affected active Execution work;
- unsupported downgrade fails visibly without file mutation;
- Material Handling remains the only in-transit owner;
- Workforce and carry display remain non-authoritative; and
- Architecture Manifest entries become implemented only with mechanical proof.

## 26. Ratification Notes

Owner ratification approved all 21 decisions below:

1. Workstation slots become stack-aware through Workstation-owned, versioned,
   per-slot capacity policy; no universal 64-unit Workstation capacity exists.
2. Workstation exclusively computes, validates, commits, and journals partial
   withdrawal; Material Handling never mutates or derives the source remainder.
3. Complete pre-stack, transfer payload, remainder, and committed post-state
   receive distinct quantity-sensitive identity bindings with exact component
   fidelity.
4. New stack-aware endpoint effects require Workstation endpoint journal
   schema 2 with immutable schema-1 legacy records; schema 1 is not
   reinterpreted additively, and Workstation instance identities remain stable.
5. `EFFECT_COMMITTED` atomically publishes exact pre-state, transfer payload,
   remainder/post-state, revisions, and immutable owner result before live
   projection application or Material Handling result visibility.
6. Post-withdrawal cancellation uses one freshness-aware Workstation source
   return that merges the complete custody payload or changes nothing.
7. Destination deposit merges only identical item and complete component state
   within Workstation slot capacity and publishes one exact owner result.
8. Schema 2 uses all-or-nothing destination deposit and source return; partial
   destination acceptance remains gated.
9. Endpoint and Material Handling architecture becomes positive-quantity
   aware, while gameplay may continue authorizing one unit per employee
   assignment.
10. Item maximum and Workstation slot capacity remain separate; machine-specific
    capacities are versioned Workstation policy.
11. Inventory capacity remains separate from operation consumption, duration,
    and throughput; initial operations consume and produce one explicitly
    planned item, never clear an unplanned input remainder, and never overwrite
    existing output.
12. Schema-1 worlds migrate only through complete validated owner candidates
    that preserve legacy identities, evidence, instance generations, and
    sequences.
13. Any active schema-1 transfer or assignment, prepared/unreconciled endpoint
    effect, Recovery Required state, Unknown Outcome, or incompatible active
    Execution work blocks automatic migration until resolved.
14. Downgrade after schema-2 publication is unsupported unless a separately
    approved reverse migrator proves complete schema-1 representability and
    evidence preservation.
15. Material Handling advances to schema 2 for stack-aware activation because
    its persisted records embed versioned endpoint evidence; its custody owner
    and lifecycle remain unchanged.
16. Workforce remains assignment/navigation authority only, and the exact-count
    carried stack remains a non-authoritative display derived from Material
    Handling custody.
17. Beef Trim is approved as a max-stack-64 candidate, with runtime activation
    owned by the separately authorized IM-030B milestone.
18. Ground Beef is approved as a max-stack-64 candidate, with runtime
    activation owned by the separately authorized IM-030B milestone.
19. Beef Patties is approved as a max-stack-64 candidate, with runtime
    activation owned by the separately authorized IM-030B milestone; no other
    product stack size changes by implication.
20. Workstation's serialized owner boundary, endpoint slot lock, freshness, and
    monotonic revisions serialize competing source, destination, return, menu,
    capability, and operation effects deterministically.
21. Implementation proceeds, if separately authorized, as IM-030A foundation,
    IM-030B selective product and Material Handling activation, then IM-031
    employee Patty Former operation, without renumbering completed milestones.

This ratification establishes architectural direction only. It does not
implement or authorize runtime schema 2, item maximum changes, Workstation
capacity changes, partial withdrawal, remainder publication, destination
merge, stack-aware cancellation, Material Handling schema-2 runtime,
multi-count employee transport, batch transport, recipe or throughput changes,
employee Patty Former operation, Production integration, automatic Workstation
selection, general Logistics, or any Architecture Manifest implementation
claim. Those gates remain closed until their named milestones receive separate
owner authorization.

## 27. Implementation Status

IM-030A was separately authorized and implements the generic Workstation-owned
slot-capacity policy, quantity-sensitive schema-2 endpoint evidence and durable
journal candidate, exact partial split and all-or-nothing merge service,
projection reconciliation, immutable schema-1 retention, conservative
migration gate, and minimum Material Handling schema-2 evidence persistence.

IM-030B was separately authorized and activates max stack size 64 for Beef
Trim, Ground Beef, and Beef Patties; selective Cutting Table, Grinder, and
Patty Former capacities; recipe-quantity input decrement; compatible atomic
output merge; and schema-2 one-unit partial withdrawal, destination merge, and
source-return merge on the two existing employee routes. Employee custody and
carry remain one item per assignment. Immutable schema-1 evidence is retained,
and downgrade remains unsupported after authoritative schema-2 publication.
