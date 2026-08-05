# Proposed ADR DG-002A: Workstation Endpoint Durability And Instance Identity

Status: PROPOSED - OWNER APPROVAL REQUIRED

Decision identifier: DG-002A

Authority: This document has no authority until explicitly approved by the
project owner and recorded through the repository's accepted decision process.
It proposes the minimum prerequisite architecture for IM-028A. It does not
authorize implementation, migration, schema creation, runtime behavior,
gameplay behavior, commands, content, tests, or Architecture Manifest changes.

Governing authority: [`CONSTITUTION.md`](../../CONSTITUTION.md)

Canonical platform reference:
[`Platform Canonicalization Addendum`](ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md).
Platform vocabulary, identity categories, Publication, Recovery, Replay,
failure-state, cancellation, Operator Authority, World Identity, and Platform
Determinism Manifest definitions remain canonical there.

Controlling Material Handling authority:
[`DG-002: Material Handling Custody And Recovery`](ADR-PROPOSED-MATERIAL-HANDLING-CUSTODY-AND-RECOVERY.md).

Related repository evidence:

- [`BCSE Architecture Guide`](../BCSE_ARCHITECTURE_GUIDE.md)
- [`Architecture Validation Framework`](../ARCHITECTURE_VALIDATION_FRAMEWORK.md)
- [`Checkpoint Recovery ADR`](ADR-PROPOSED-CHECKPOINT-RECOVERY.md)
- [`Evidence Lifecycle ADR`](ADR-PROPOSED-EVIDENCE-LIFECYCLE.md)
- [`Workstation Framework`](../WORKSTATION_FRAMEWORK.md)
- [`Technical Architecture`](../../TECHNICAL_ARCHITECTURE.md)

## 1. Context

DG-002 ratifies Material Handling as the singular transfer-lifecycle and exact
in-transit custody authority. Source and destination workstations retain
authority over their own slots, freshness, mutation, and owner results.

The current repository does not yet provide two prerequisites required by that
authority split:

1. Workstation identity is derived from workstation type, dimension, and block
   position. A replacement block at the same position can therefore appear to
   be the prior workstation.
2. Workstation block entities persist inventory through normal chunk NBT and
   call `setChanged()` after mutation. Delayed chunk publication cannot prove
   that an inventory effect and immutable owner result became durable before
   Material Handling advances custody.

Current owner-result patterns in Transactions, Scheduler, Execution, and
Production bind immutable identities and content digests to owner-published
facts. Current world-scoped sequence allocators persist the next sequence with
their owner state and derive canonical identities from explicit World Identity
and creation inputs. Current JSON persistence writes versioned candidates by
temporary-file replacement and rejects malformed or unsupported schemas.

DG-002 already rejects direct Material Handling slot mutation, position-only
endpoint inheritance, and ordinary delayed chunk save as sufficient transfer
evidence. IM-028A cannot safely implement its endpoint protocol until the
Workstation owner has a canonical instance identity and durable result
publication contract.

## 2. Problem

The architecture must identify one exact workstation instance and prove one
consequential endpoint effect without giving Material Handling ownership of a
workstation slot.

Without an instance generation, a stale transfer can bind to a replacement
block. Without a durable Workstation publication, a crash can leave
`material_handling.json` claiming custody while the source chunk still contains
the item, or claiming completion while the destination chunk does not contain
it.

The required solution must preserve:

- singular Workstation ownership of workstation inventory effects;
- singular Material Handling ownership of cross-transfer lifecycle and
  in-transit custody;
- deterministic, versioned identity allocation;
- exact `ItemStack` preservation through the DG-002 integration adapter;
- prepare-before-effect ordering;
- idempotent owner results;
- visible uncertainty rather than guessed recovery;
- bounded persistence and recovery work; and
- existing non-transfer workstation behavior.

## 3. Decision Summary

If ratified, DG-002A establishes the following architecture:

1. The Workstation subsystem owns one world-scoped, versioned workstation
   instance registry and its monotonic generation allocator.
2. A Workstation Instance Identity binds World Identity, workstation type,
   dimension, position, generation, schema, and allocation configuration.
3. The Workstation subsystem owns one versioned endpoint journal containing
   consequential endpoint prepare, effect, result, and reconciliation state.
4. The endpoint journal is authoritative for whether a transfer endpoint
   effect committed and which immutable owner result was published.
5. Block-entity inventory is the live projection of committed endpoint state.
   Block-entity NBT alone is never sufficient evidence that a DG-002 transfer
   mutation committed.
6. Material Handling may consume only a matching immutable Workstation owner
   result after endpoint reconciliation. It never allocates Workstation
   identity, writes the endpoint journal, or repairs block-entity inventory.
7. Legacy workstations remain usable for existing behavior but are ineligible
   as transfer endpoints until the Workstation owner enrolls them.
8. IM-028A remains blocked until this proposal is ratified. Ratification would
   preserve the existing IM-028A scope: Cutting Table source and Grinder
   destination only.

## 4. Ownership

| Concern | Singular owner | Consumers |
| --- | --- | --- |
| Workstation instance generation | Workstation | Block entity, Material Handling by reference |
| Workstation instance registry | Workstation | Endpoint reconciliation, diagnostics |
| Endpoint mutation preparation | Workstation | Material Handling observes immutable result |
| Endpoint inventory effect | Workstation | Material Handling never mutates it |
| Endpoint freshness | Workstation | Material Handling binds it |
| Endpoint journal | Workstation | Material Handling reads validated results |
| Endpoint owner result | Workstation | Material Handling and future Evidence Lifecycle |
| Block-entity projection | Workstation | Menus and existing workstation controllers |
| Transfer lifecycle and custody | Material Handling | Workstation receives narrow requests |
| Cross-transfer evidence | Material Handling | Workstation is only a cited source |
| Startup ordering | Platform lifecycle with owner-controlled publication | Workstation and Material Handling |

The instance registry and endpoint journal are two persistence candidates under
one Workstation authority. They do not create two owners. The registry answers
which entity exists. The journal answers what a transfer endpoint did.

## 5. Workstation Instance Identity

### 5.1 Canonical Inputs

Schema 1 defines one Workstation Instance Identity from these frozen inputs:

- Workstation instance identity schema version;
- World Identity root identity, schema version, and root digest;
- canonical workstation type identity;
- canonical dimension identity;
- exact block position;
- Workstation-owned monotonically increasing instance generation; and
- Workstation instance-allocation Configuration Identity.

The canonical identity form is:

```text
butchercraft:workstation_instance/v1/<lowercase-sha256>
```

The digest covers a length-delimited canonical representation of every input.
The registry retains the expanded fields so the identity can be verified
without trusting only the digest.

The allocation Configuration Identity is frozen placement provenance. A later
configuration change does not rename an existing workstation. Current
endpoint configuration is represented separately in Freshness Identity.

### 5.2 Prohibited Inputs

Canonical Workstation Instance Identity must not derive from:

- random UUIDs;
- Java object identity;
- wall-clock time;
- simulation tick alone;
- block position alone;
- display names;
- chunk load order;
- filesystem metadata;
- unordered iteration; or
- a Material Handling sequence or identity.

### 5.3 Stability

The instance identity is immutable after allocation. It survives save/load,
chunk unload/reload, server restart, and block-entity reconstruction. The block
entity persists the identity as a projection, while the Workstation instance
registry remains the authoritative allocation record.

## 6. Allocation Authority And Protocol

The Workstation subsystem owns one world-scoped next-instance-generation
counter. Generations are positive, monotonic, never reused, and allocated on
the serialized server-owner boundary.

Allocation uses this protocol:

1. Validate World Identity, workstation type, dimension, position, schema, and
   allocation Configuration Identity.
2. If the matching block-entity projection already carries the active registry
   identity, return that existing identity. Otherwise reject an existing active
   instance at the same endpoint key.
3. Allocate the current next generation in a candidate registry.
4. Add a `PENDING_BINDING` instance record and increment the next generation in
   that same candidate.
5. Durably publish the complete registry candidate.
6. Bind the allocated identity to the matching block-entity projection.
7. Validate the projection against the registry and mark the registry record
   `ACTIVE` through another durable candidate publication.
8. Admit endpoint operations only after the record and projection agree.

A crash after generation allocation may leave a `PENDING_BINDING` record. The
generation remains consumed. If the expected block type at the exact endpoint
has no conflicting instance identity, reconciliation may complete that already
published binding. It does not allocate another generation.

Legacy workstations without instance identity are not scanned or enrolled in
chunk-load order. They receive identity only through an explicit Workstation-
owned enrollment event, such as server-authoritative placement after schema-1
activation or first authorized transfer-endpoint enrollment. Those events and
their serialized order are explicit identity inputs.

## 7. Instance Registry Model

Each instance record contains at minimum:

- schema version;
- instance identity and expanded canonical inputs;
- instance generation;
- endpoint key of type, dimension, and position;
- allocation evidence identity and content digest;
- allocation Configuration Identity;
- lifecycle state;
- creation and last-update owner revisions;
- replacement or retirement reason when present; and
- endpoint-journal references when unresolved.

Schema-1 instance lifecycle is:

```text
PENDING_BINDING -> ACTIVE -> RETIRED
                 |      |
                 |      -> IDENTITY_CONFLICT
                 -> RECOVERY_REQUIRED
```

`RETIRED`, `IDENTITY_CONFLICT`, and unresolved `RECOVERY_REQUIRED` records are
not silently removed. A retired generation is never made active for a new
block.

## 8. Replacement Semantics

### 8.1 Normal Break And Replacement

Before a transfer-capable workstation is normally removed, the Workstation
owner checks its own instance registry and endpoint journal. It does not query
Material Handling or acquire Material Handling runtime authority.

- With no active or unresolved endpoint operation, the owner durably retires
  the current instance before a replacement can become endpoint-eligible.
- With a prepared or unresolved effect, normal endpoint release is rejected.
  If external gameplay still removes the block, the old identity and journal
  remain authoritative evidence and the affected operation enters the typed
  recovery path.
- A replacement receives a new generation and new identity. It never inherits
  endpoint locks, results, freshness, or Material Handling references.

Material Handling later observes the immutable retirement or replacement fact
through the integration boundary and classifies its own affected transfer. A
bound endpoint with no committed effect may fail as `endpoint_replaced`;
proven custody remains with its current owner and follows DG-002 recovery.

### 8.2 Stale Block-Entity Data

If block-entity NBT identifies a retired or different instance from the active
registry record at that endpoint, reconciliation reports
`endpoint_identity_conflict`. It does not overwrite either identity or bind
the block to an old transfer.

### 8.3 Structure Copy Or Duplicated NBT

An identity copied to a different dimension, position, or workstation type
fails expanded-field verification. The copy is transfer-ineligible and reports
`endpoint_identity_conflict`. The original registry record remains unchanged.

### 8.4 Rollback

A rollback is valid only when the instance registry, endpoint journal,
block-entity owner snapshot, and Material Handling owner snapshot belong to the
same accepted checkpoint generation. Mixed-generation state is rejected.

Outside coordinated checkpoint restoration, an older block-entity projection
does not supersede a newer durable registry or journal. A mismatch becomes
`RECOVERY_REQUIRED` when the committed location is proven and
`UNKNOWN_OUTCOME` when it is not.

### 8.5 Workstation Type Change

A different workstation type at the same position is always a different
instance. Any reference to the former type fails with `endpoint_replaced` or
`endpoint_identity_conflict`; it never resolves through positional similarity.

## 9. Durable Endpoint Journal

The Workstation endpoint journal is the durable authority for consequential
DG-002 endpoint effect commitment and immutable owner-result publication.

Each journal record contains at minimum:

- journal schema version and monotonic journal sequence;
- workstation instance identity;
- endpoint slot identity;
- endpoint operation kind;
- transfer, withdrawal, deposit, or return identity as applicable;
- idempotent endpoint Effect Identity;
- exact expected pre-state payload and Content Identity;
- exact committed post-state payload and Content Identity;
- pre-effect and post-effect Freshness Identities;
- pre-effect and post-effect inventory and effect revisions;
- endpoint Configuration Identity;
- local lifecycle state;
- immutable owner-result identity, digest, and outcome frozen at effect commit;
- failure or recovery classification; and
- retained cross-owner references required by DG-002.

Exact stack payloads use the DG-002 Minecraft integration codec. The pure
Workstation journal model owns opaque canonical bytes and Content Identity, not
Minecraft classes.

## 10. Durable Publication Contract

An acknowledged consequential endpoint effect requires successful publication
of one complete validated endpoint-journal candidate.

Schema-1 publication requires:

1. validate the complete candidate and configured capacity;
2. serialize in canonical order;
3. write a sibling temporary file;
4. force written file content and metadata through the available filesystem
   API;
5. atomically replace the authoritative file;
6. verify the published candidate identity and digest; and
7. only then expose the new journal state to other owners.

Non-atomic replacement is not an acceptable fallback for an acknowledged
endpoint effect. If the filesystem cannot provide the required publication
contract, the endpoint rejects consequential mutation with
`durable_publication_unavailable` and Material Handling does not advance.

The repository does not currently define a platform-wide directory-fsync
guarantee. DG-002A therefore claims durability only after the endpoint store's
forced file write and atomic replacement complete successfully. Temporary,
orphaned, or malformed artifacts are classified on startup and never outrank
the last valid authoritative candidate.

## 11. Journal Lifecycle

The owner-local lifecycle is:

```text
REQUESTED
  -> PREPARED
  -> EFFECT_COMMITTED
  -> RESULT_PUBLISHED
  -> RECONCILED
```

Exceptional terminal or blocking states are:

```text
REJECTED
FAILED
UNKNOWN_OUTCOME
RECOVERY_REQUIRED
```

State meaning:

| State | Meaning |
| --- | --- |
| `REQUESTED` | A narrow request exists in the serialized owner boundary but no durable mutation authority exists |
| `PREPARED` | Exact pre-state, post-state, effect, freshness, and lock are durably published; no effect has committed |
| `EFFECT_COMMITTED` | One journal publication durably commits the owner effect and freezes its immutable owner result; the live block-entity projection must converge to its post-state |
| `RESULT_PUBLISHED` | The already-durable immutable owner result becomes externally observable after projection validation |
| `RECONCILED` | The block-entity projection and journal relationship have been verified for the current startup/runtime generation |
| `REJECTED` | Validation failed before effect commitment |
| `FAILED` | A proven failure occurred without uncertain item location |
| `UNKNOWN_OUTCOME` | Available evidence cannot prove whether the item is in the pre-state, custody, or post-state location |
| `RECOVERY_REQUIRED` | Custody or committed location is proven, but projection or lifecycle completion requires explicit recovery |

`EFFECT_COMMITTED` is the owner-local commit point. Applying its already
committed post-state to a stale projection is reconciliation, not automatic
reissuance of the consequential command.

## 12. Prepare, Effect, And Result Protocol

### 12.1 Preparation

The Workstation owner validates:

- exact instance and slot identity;
- current block type and endpoint capability;
- exact expected stack and Content Identity;
- compatibility and capacity;
- current inventory, effect, schema, and configuration revisions;
- no conflicting endpoint lock or machine operation; and
- duplicate Effect Identity behavior.

It then durably publishes `PREPARED`. The record locks only the exact endpoint
slot and instance. Preparation does not transfer custody and does not authorize
Material Handling to mutate the slot.

### 12.2 Effect Commitment

The owner revalidates every prepared fact. It builds one candidate record that
contains the exact post-state, post-effect Freshness Identity, incremented
inventory and effect revisions, immutable owner-result identity and content,
and `EFFECT_COMMITTED`. It publishes that candidate before changing the live
block-entity projection. Effect commitment and owner-result creation therefore
share one Workstation-owned durable publication boundary.

The serialized Workstation-owner boundary remains held from final validation
through journal publication, live projection application, and result-visibility
publication. No menu, capability, processing controller, or other endpoint
request may observe or mutate an intermediate owner state.

After durable commitment, the owner applies the exact post-state idempotently
to the projection and persists the journal sequence, Effect Identity, result
reference when available, and revisions in block-entity NBT.

### 12.3 Result Publication

After the live projection matches the committed post-state, the Workstation
owner publishes the already-frozen immutable owner result for external
observation. Publication changes result visibility, not result identity or
content. The result binds:

- Workstation Instance Identity;
- endpoint and operation kind;
- request and Effect Identity;
- exact stack Content Identity;
- pre-effect and post-effect Freshness Identities;
- post-effect revisions;
- outcome;
- schema and Configuration Identity; and
- owner-result Evidence Identity and content digest.

Material Handling may advance only after validating this result against its
exact prepared request and the reconciled endpoint instance.

### 12.4 Duplicate And Conflict Behavior

- Same Effect Identity and same canonical content observes the existing state
  or owner result without repeating the effect.
- Same Effect Identity and different canonical content is
  `endpoint_identity_conflict`.
- A stale Freshness Identity is rejected and requires a new preparation.
- An unresolved effect blocks conflicting mutation of only its instance and
  slot.
- No uncertain effect is automatically retried.

## 13. Source Withdrawal And Destination Deposit

For source withdrawal:

- expected pre-state is the exact one-unit source stack;
- committed post-state is the empty source slot;
- the source remains authoritative through `PREPARED`;
- durable `EFFECT_COMMITTED` freezes the owner result and, with the exact
  prepared payload, proves the owner withdrawal commitment; and
- Material Handling accepts the result and publishes normal in-transit custody
  only after matching owner-result visibility and validation.

At source `EFFECT_COMMITTED`, the matching DG-002 prepared Material Handling
payload becomes authoritative custody as DG-002 requires. `RESULT_PUBLISHED`
controls normal cross-owner visibility and lifecycle advancement; it does not
delay or recreate that already-proven authority transfer.

For destination deposit or source return:

- expected pre-state is the exact compatible empty or owner-approved slot
  state;
- committed post-state contains the exact custody stack;
- Material Handling retains authoritative custody through `PREPARED`;
- durable destination `EFFECT_COMMITTED`, including the frozen owner result,
  transfers the committed location to the destination owner under DG-002
  reconciliation rules; and
- Material Handling clears its recovery payload only after matching
  owner-result publication and its own durable terminal publication.

## 14. Block-Entity Relationship

Block-entity inventory remains the live Workstation-owned projection used by
menus, capabilities, and existing processing controllers. The journal does not
give Material Handling access to that projection.

Every authoritative mutation of a transfer-capable endpoint slot, including
manual, automation, processing, and transfer mutation, increments the
Workstation-owned inventory revision. Only consequential endpoint-journal
effects increment the endpoint effect revision. Both revisions survive normal
block-entity persistence.

For transfer effects:

- the endpoint journal is authoritative for effect commitment and owner-result
  history;
- block-entity NBT stores instance identity, inventory revision, effect
  revision, last-applied journal sequence, Effect Identity, owner-result digest,
  and inventory projection;
- block-entity NBT alone never proves that a transfer effect committed;
- a matching projection confirms application but cannot create an owner result
  absent a journal record; and
- later Workstation-owned mutations may advance inventory revision while
  retaining the last-applied endpoint marker needed to prove causal descent.

On reconciliation:

- projection revision below a committed journal revision is stale and may be
  advanced only from the exact committed journal post-state;
- equal revision requires exact content, instance, effect, and digest match;
- greater revision is accepted only when the retained endpoint marker proves
  the committed effect is in its causal history;
- conflicting content or identity is `UNKNOWN_OUTCOME` or
  `endpoint_identity_conflict`; and
- missing or unloaded projection with proven journal custody is
  `RECOVERY_REQUIRED`, not item loss.

Ordinary non-transfer workstation behavior remains governed by existing
Workstation ownership and block-entity persistence. DG-002A does not require
all manual or processing mutations to become Material Handling journal events.

## 15. Endpoint Freshness Identity

The Workstation owner issues one deterministic endpoint Freshness Identity
covering every authoritative fact examined by endpoint validation:

- schema version;
- Workstation Instance Identity;
- endpoint slot identity;
- inventory revision;
- endpoint effect revision;
- exact current stack Content Identity or canonical empty identity;
- endpoint lock state and active Effect Identity when present;
- workstation operation state relevant to compatibility;
- endpoint Configuration Identity; and
- owner-result journal revision relevant to the endpoint.

The canonical identity is digest-backed and namespaced. Any covered change
invalidates prior preparation. A replacement workstation necessarily has a
different instance and cannot satisfy old freshness.

## 16. Startup And Recovery Ordering

The required logical publication order is:

1. World Identity loads and publishes its immutable root identity and digest.
2. Workstation instance registry loads, validates, and publishes a candidate.
3. Workstation endpoint journal loads, validates instance references, and
   publishes a recovery candidate.
4. Block entities load as unverified projections when their chunks become
   normally available. Recovery does not force-load chunks.
5. Workstation reconciliation validates or repairs each referenced projection
   from already committed journal evidence and publishes endpoint readiness or
   a typed blocked outcome.
6. Material Handling persistence loads and validates as a candidate against
   World Identity, instance registry, and immutable endpoint results.
7. Material Handling reconciliation publishes mutable transfer authority only
   for transfers whose required endpoints are reconciled.

Raw file decoding may occur earlier for implementation convenience, but owner
candidate publication and mutation authority must preserve this order.

Material Handling remains paused for an affected transfer until every
referenced endpoint is reconciled, proven unavailable, or placed in an
explicit recovery state. Unrelated reconciled transfers may remain available
within configured bounded isolation rules.

## 17. Crash-Boundary Behavior

| Crash boundary | Authoritative evidence and recovery | Material Handling |
| --- | --- | --- |
| Before endpoint prepare is durable | No journal preparation exists; workstation pre-state remains authoritative | Must not advance |
| After `PREPARED`, before effect commitment | Durable exact preparation and lock exist; no effect is applied automatically | Waits for explicit resume or cancellation |
| After `EFFECT_COMMITTED`, before live projection | Journal proves committed post-state and contains the frozen owner result; Workstation reconciliation applies the projection idempotently | Waits for result visibility |
| After live effect, before `RESULT_PUBLISHED` | Journal proves effect commitment and frozen result; projection is verified or repaired, then the result is exposed once | Advances only after result publication |
| After `RESULT_PUBLISHED`, before chunk save | Journal result is authoritative; stale NBT is repaired from the committed post-state | Remains paused until endpoint reconciliation after restart |
| After chunk save, before `RESULT_PUBLISHED` | Matching projection plus the frozen result in `EFFECT_COMMITTED` permits idempotent result publication | Waits for result visibility |
| During endpoint replacement | Old instance remains bound to its evidence; new block cannot inherit it | `endpoint_replaced`, `RECOVERY_REQUIRED`, or `UNKNOWN_OUTCOME` according to proven custody |
| During block break | Registry retirement, journal state, projection marker, and Material Handling references are reconciled; no evidence is discarded | Advances only from proven owner result |
| During journal replacement | Last complete valid candidate remains authoritative; incomplete artifact is quarantined | Must not consume incomplete state |
| During schema migration | Original schema remains authoritative until complete migrated candidate validation and publication | Remains read-only or Recovery-Blocked |
| During downgrade | Newer state is retained; incompatible runtime refuses mutation | Requires operator resolution when downgrade gate fails |

Any state outside the protocol, including a changed projection with only a
`PREPARED` journal, is `UNKNOWN_OUTCOME`. Recovery must not infer commitment
from visual inventory similarity.

## 18. Persistence Layout

Schema 1 uses these Workstation-owned files:

```text
<world>/butchercraft/workstation_instances.json
<world>/butchercraft/workstation_endpoint_journal.json
```

`workstation_instances.json` contains:

- document schema and owner revision;
- World Identity root reference;
- next instance generation;
- instance allocation Configuration Identity;
- canonical instance records; and
- retirement, conflict, and unresolved recovery references.

`workstation_endpoint_journal.json` contains:

- document schema and owner revision;
- World Identity root reference;
- next journal sequence;
- endpoint Configuration Identity;
- canonical endpoint records and immutable owner results; and
- retention, failure, and recovery metadata.

Both files use complete-candidate validation, canonical ordering, forced
temporary-file publication, and atomic replacement. No record is published by
appending to an unvalidated authoritative file.

Legacy worlds with neither file load an empty instance registry and endpoint
journal. Existing workstations retain existing behavior but are not transfer
endpoints until explicitly enrolled.

A missing instance registry loads as a provisional empty legacy candidate only
when no endpoint journal or block-entity endpoint marker proves prior use. A
missing endpoint journal loads provisionally empty only when the instance
registry has no journal references and no block-entity marker proves a prior
endpoint effect. Later Material Handling candidate validation rejects any
transfer reference that cannot resolve against those provisional candidates;
it never synthesizes missing Workstation state. Local prior-use evidence makes
absence immediately fail-visible Recovery-Blocked state.

Malformed files, unsupported schemas, duplicate generations, regressed next
sequences, unresolved instance references, digest mismatches, and conflicting
endpoint records reject authoritative publication. They are never replaced
with empty state.

## 19. Retention And Capacity

Workstation persistence is bounded by positive configured limits included in
the relevant Configuration Identity. Schema 1 defines limits for:

- active and retained instance records;
- endpoint records;
- exact retained payload bytes;
- records examined in one reconciliation pass; and
- mutations accepted in one server-owner action.

Exact numeric defaults are implementation policy, not architecture.

No unresolved, `UNKNOWN_OUTCOME`, `RECOVERY_REQUIRED`, active, or externally
referenced owner result may be pruned. Terminal payloads may be compacted to
identity, digest, and owner-result evidence only after Material Handling no
longer requires recovery data and Evidence Lifecycle policy permits it.
Instance generations and journal sequences are never reused.

Reaching a capacity limit rejects new endpoint preparation with a typed
failure. It does not prune evidence automatically or prevent inspection and
recovery of existing records.

## 20. Migration And Downgrade

Migration is owner-controlled, deterministic, versioned, and candidate-based.
The old candidate remains authoritative until the complete migrated instance
registry and journal validate and publish. Migration must preserve identities,
expanded canonical inputs, exact unresolved payloads, sequence monotonicity,
owner results, and cross-owner references.

Downgrade is permitted only when all of these are proven:

- no active or prepared endpoint operation exists;
- no unresolved owner result exists;
- no endpoint is `UNKNOWN_OUTCOME` or `RECOVERY_REQUIRED`;
- no authoritative Material Handling custody references the newer schema;
- every retained terminal result is either readable by the target version or
  exported through an approved migration; and
- instance generations and journal sequences will not be silently reset or
  reused.

If any condition fails, downgrade is unsupported. The newer files remain
preserved and startup fails visibly or enters an operator-visible read-only
state. Uninstalling or downgrading must never silently delete custody or owner
evidence.

## 21. Existing Workstation Impact

DG-002A authorizes no implementation by itself. If ratified for IM-028A:

- Cutting Table integrates as the only source endpoint;
- Grinder integrates as the only destination endpoint;
- Patty Former remains excluded until its separately gated explicit operation
  initiation work is accepted;
- Packaging Table remains unchanged;
- Bandsaw and Bandsaw upper structure remain unchanged;
- Development Processing Workstation remains unchanged; and
- no existing workstation is required to expose a general transfer API.

The narrow endpoint framework may be internal and revisable. It is not a public
expansion API. Other workstations receive instance identity only when a later
owner-authorized endpoint or identity consumer requires it.

IM-028A's non-employee proof remains an integration/test harness using explicit
Cutting Table and Grinder endpoints. This addendum does not authorize a command,
employee transport, visible carrying, machine operation, automatic selection,
or autonomous logistics.

## 22. Checkpoint And Evidence Interaction

Checkpoint Recovery does not own Workstation instance, endpoint, inventory, or
Material Handling content. A future checkpoint participant may capture owner
snapshots only through owner-provided immutable payloads.

Until Workstation endpoint and Material Handling snapshots participate in one
coordinated generation, checkpoint publication must exclude their
implementation authority or reject capture while a transfer is active. Mixed
generation restoration is never accepted.

Evidence Lifecycle may later archive terminal owner-result evidence without
changing its Evidence Identity or ownership. Unresolved evidence remains hot
and retained. Neither Checkpoint nor Evidence Lifecycle may issue Workstation
owner results or advance Material Handling custody.

## 23. Future Architecture Manifest Declarations

After ratification and only during an owner-authorized implementation milestone,
the Architecture Manifest may declare each mechanically true fact:

- Workstation Instance Registry ownership;
- Workstation instance-generation allocation ownership;
- Workstation Endpoint Journal ownership;
- Workstation durable endpoint Effect Identity and owner-result publication;
- Workstation endpoint Freshness Identity;
- Workstation endpoint reconciliation ownership;
- `workstation_instances.json` persistence;
- `workstation_endpoint_journal.json` persistence;
- Material Handling dependency on immutable Workstation identities, freshness,
  and owner results;
- forbidden Material Handling ownership of workstation inventory or instance
  allocation; and
- no direct Workstation dependency on Material Handling runtime internals.

Every declaration remains unimplemented until code, persistence, startup
ordering, and tests make it mechanically true. This proposed ADR changes no
current manifest status.

## 24. Architecture Gates Preserved

DG-002A does not authorize:

- IM-028A implementation before owner ratification;
- IM-028B;
- employee assignment, movement, or carried-item rendering;
- a transfer command;
- Patty Former transport or operation changes;
- machine operation initiation;
- Production-driven transport;
- Execution, Scheduler, Allocation, Transaction, or economic Inventory changes;
- automatic workstation search;
- cross-dimension transfer;
- autonomous logistics;
- public APIs;
- broad migration of existing workstations; or
- automatic retry of uncertain mutation.

## 25. Required Validation After Future Implementation

Future implementation must prove at minimum:

- deterministic instance allocation and identity reproduction;
- replacement identity separation at the same position;
- copied-NBT and stale-projection conflict detection;
- instance registry and endpoint journal schema validation;
- exact stack and data-component round trip;
- preparation-before-effect ordering;
- effect and owner-result idempotency;
- same-identity/different-content conflict rejection;
- source and destination freshness invalidation;
- crash recovery at every boundary in Section 17;
- no double withdrawal, deposit, return, or output;
- no Material Handling slot mutation;
- block-entity NBT alone cannot synthesize a transfer result;
- endpoint reconciliation precedes Material Handling mutation authority;
- absent legacy files are safe only without prior-use evidence;
- malformed and unsupported files fail visibly;
- bounded capacity and retention behavior;
- downgrade gates;
- Cutting Table source and Grinder destination scope only;
- no machine operation or autonomous runtime loop; and
- Architecture Manifest ownership and dependency validation.

## 26. Required Owner Decisions

1. **Instance identity owner:** Approve Workstation as the singular owner of
   Workstation Instance Identity and its registry; or require a named existing
   owner before IM-028A.
2. **Allocation mechanism:** Approve a persisted world-scoped monotonic
   generation allocated on the serialized Workstation-owner boundary, with no
   generation reuse; or require another deterministic mechanism.
3. **Durable publication:** Approve forced temporary-file publication plus
   atomic replacement with no non-atomic fallback for acknowledged endpoint
   effects; or define another durability contract.
4. **Journal authority:** Approve the Workstation endpoint journal as the
   authority for transfer effect commitment and immutable owner-result history;
   or select another Workstation-owned durable authority.
5. **Block-entity projection:** Approve block-entity inventory as the live
   projection, with NBT alone insufficient to prove a transfer effect; or define
   another owner-preserving relationship.
6. **Startup order:** Approve World Identity, instance registry, endpoint
   journal, block-entity reconciliation, then Material Handling publication and
   reconciliation; or provide another acyclic owner order.
7. **Replacement behavior:** Approve new generation on replacement and typed
   stale, copied, rollback, and type-conflict outcomes with no silent rebinding;
   or define another non-inheriting rule.
8. **Downgrade policy:** Approve downgrade only after proving no active,
   unresolved, recovery-required, unknown, or newer-schema custody reference;
   or define another no-loss migration policy.
9. **IM-028A scope:** Approve Cutting Table source and Grinder destination as
   the only endpoint integrations, with every other workstation gated; or
   explicitly revise the milestone scope.
10. **Implementation authorization:** Confirm that ratification of DG-002A
    removes the two architecture blockers and leaves the existing bounded
    IM-028A implementation authorization in force; or keep IM-028A blocked for
    further architecture work.

Until all ten decisions are ratified, IM-028A implementation remains blocked.
