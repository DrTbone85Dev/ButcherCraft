# Machine Condition Foundation

Status: IM-033A COMPLETE / PRODUCT OWNER ACCEPTED.

See the [completion report](IM-033A-COMPLETION-REPORT.md) for exact validation and
the complete changed-path inventory. The
[Product Owner acceptance record](IM-033A-COMPLETION-REPORT.md#product-owner-acceptance-record)
records acceptance on 2026-09-28 without activating production policies.

Authority: [ratified DG-006](adr/ADR-PROPOSED-MACHINE-CONDITION-WEAR-DAMAGE-MAINTENANCE-AND-BREAKDOWN.md).
Version remains `0.10.7-alpha.1`. This foundation does not activate production
wear, dry-running damage, breakdown, maintenance, repair, or lubrication.
IM-033B, IM-033C, and IM-034 require separate authorization.

## Ownership And Applicability

Workstation owns condition and its publication. Grinder and Patty Former have
independent inert policies. Cutting Table has an explicit NOT_APPLICABLE marker,
not a condition record. Execution owns Machine Runs and bounded operations;
Scheduler owns dispatch; Clock supplies simulation ticks; Workforce owns
assignments and reservations; Material Handling owns custody. Checkpoint freezes
and restores owner evidence without calculating or applying condition effects.

The pure `workstation.condition` domain contains immutable state, arithmetic,
policies, identities, and evidence. The Workstation processing bridge belongs
outside that arithmetic package. Client code has no condition mutation path.

## Canonical State

Each condition record binds its exact DG-002A Workstation Instance, schema,
immutable policy, positive monotonic condition revision, bounded integer
mechanical loss, separate nonnegative service debt, typed exposure accounts,
optional active exposure, optional controlling fault, durable accounted tick,
suspension reason, initialization evidence, exact effect count/reference, and
canonical SHA-256 digest. Presentation bands are derived.

Condition revision is independent of inventory, endpoint, projection, and Run
revisions. Projection revision advances whenever its complete content changes.
Equal condition revision with different content, stale candidates, wrong
instances, unsupported schemas, unknown required policies, and missing evidence
fail visibly. Arithmetic uses checked integers and exact rational remainders;
mechanical loss saturates at the policy maximum, while invalid arithmetic fails
before publication. Service debt never restores wear.

## Joint Processing Boundary

Preparation freezes the exact pre-state, policy, freshness identity, and
mechanical/service/grace/fault consequence before Execution authorization.
Commit revalidates condition and inventory freshness. A successful child stages
an immutable receipt, then publishes one complete Workstation projection with
the product, condition post-state, and versioned joint owner result. The receipt
binds the operation, domain effect, exact post-inventory digest, condition
pre/post-state, preparation, policy, and planned projection revision.

`AtomicFilePublication` and semantic read-back precede the terminal result
returned to Execution. In-memory changes remain inside the existing owner
mutation boundary. A publication exception restores the tentative loaded view
and fences further mutation until exact recovery; it never silently retries the
recipe. A receipt staged without committed projection/result proof is not an
applied effect. Existing exact owner-result repair may advance stale projection
only with complete joint receipt, inventory, and condition proof.

Duplicate observations do not produce another product or condition effect.
Rejected input, blocked output with no child, and cancelled pre-effect work earn
no ordinary wear. STOP can cancel a child before invocation or finish an already
invoked child according to the existing Execution contract.

## Exposure And Time

Typed exposure includes DRY_RUNNING, BLOCKED_POWERED, and
LOADED_MECHANICAL_STRESS capability. Production policies enable none of them.
Test-only policies prove nonzero consequences without changing live balance.

An exposure binds a proven operating transition, exact instance and policy,
loaded-availability evidence, start tick, and accounted-through tick. Intervals
are half-open. Settlement retains exact remainder and grace progress. Successful
processing resets only the policy-selected grace counters. STOP/reload do not
renew grace. A policy change closes the old frozen-policy interval at its
explicit simulation-tick boundary before opening an eligible new interval.

A transient due index visits at most 64 due entries per ordinary server tick.
Only active exposures enter that index; it is not persistence authority and is
rebuildable from projections. Cadence and exact threshold deadlines bound
publication. A threshold records its first semantic tick even if observed later.
There is no global per-tick machine scan or per-tick condition journal.

This is a condition-effect write bound, not a claim that all existing Workstation
persistence is cadence-limited. Existing processing-progress projections may
still publish each processing tick and carry unchanged condition fields. IM-033A
does not add a per-tick condition mutation or a separate per-tick condition write.

Orderly unload closes only the proven loaded interval. Restart and Clock
discontinuity suspend at the last durable accounted cutoff; omitted time is not
reconstructed. Policy B remains controlling and RESTART_REQUIRED earns no
exposure. No wall-clock time, random generator, or forced chunk loading is used.
OUTPUT_BLOCKED remains powered idle with no production loss.

## Operating-State Pairing

The separate `machine_operating_states.json` owner record is not moved into
condition. A consequential transition first closes/binds condition and records
an exact pending prior/successor operating reference. The operating successor
then publishes, after which the Workstation verifies the pair and clears the
pending marker. Child/inventory mutation and complete checkpoint capture reject
an unresolved pair. No independent owner is introduced for this coordination.

## Persistence And Compatibility

| Surface | Current format | Historical handling |
| --- | --- | --- |
| Durable Workstation projection | Payload schema 2 | Schema 1 decodes unchanged; explicit safe migration creates a successor. |
| Condition state/receipt | Schema 1 | Unsupported versions fail visibly. |
| Workstation checkpoint projection collection | Schema 3, including exact receipt closure | Historical schema 2 remains pre-condition evidence; schema 1 keeps its prior completeness rules. |
| Live Workstation participant | Schema 5 | Existing schema-specific historical restoration remains supported. |
| Execution persistence document | Schema 2 | Schema 1 retains historical handlers and is explicitly classified under DG-003. |
| Operating state / endpoint journal | Unchanged | No reinterpretation of endpoint effects or separate Material Handling wear. |

Projection files remain in the existing sharded `workstations/projections/v1`
physical directory; directory naming is not payload schema authority. Before a
schema-1 projection is replaced by its condition successor, exact source bytes
are retained under `legacy_sources`. Migration binds that historical digest and
the same instance, initializes healthy without inferred wear, and is idempotent.
Unresolved endpoint effects or legacy children defer migration. Historical
checkpoint generations are never rewritten.

Immutable receipts reside at
`workstations/condition_effects/v1/<shard>/<shard>/<digest>.json`. The projection
head determines applied closure; file presence does not. The hot receipt cache
is bounded to 256 entries; required durable evidence is not evicted or deleted.
No condition-global state file or compaction policy is added.

New condition-aware Grinder and Patty Former handler contracts are additive.
Historical handler identities/configurations remain unchanged. Execution schema
2 persists full handler descriptors, per-operation contract bindings, compatible
evolution evidence, and a determinism-manifest reference. Old child evidence is
not upgraded to a condition-aware effect by inference.

## Checkpoint And Restoration

The existing Workstation participant includes the complete required instance
set, exact projection bytes, and transitive immutable condition receipt closure,
including unloaded instances and referenced retirement tombstones. Missing or
conflicting condition evidence rejects the candidate before head commit.

Owner-native restoration expands that frozen payload into the same owner files,
verifies bytes and semantics, and does not replay effects or recompute historical
wear. Loaded block entities reconcile from the restored owner projection;
stale chunk data cannot overwrite newer durable condition. Unloaded condition is
readable without loading its chunk. Replacement gets a distinct instance and
fresh healthy condition; retired effects cannot target its coordinates.

## Diagnostics

Use `/butchercraft workstation status <x> <y> <z>` in the current dimension.
The existing permission-gated command is read-only and uses synchronized built-in
arguments. Its condition section reports applicability, instance, schema,
revision, loss/band/service debt, policy, exposure/cutoff, suspension and discarded
unproven-tail flag, fault, recent effect/head, projection freshness, and coherence.
There is no condition-edit, service, repair, or wear command.

## Limits And Gates

Mechanical fault is valid condition state, not RECOVERY_REQUIRED. Test policy
can deny future child eligibility while preserving legitimate STOP. Production
policies cannot cause a mechanical fault in IM-033A. Maintenance/repair effect
kinds and safe-service eligibility are foundation contracts, not gameplay.

Grinder activation remains IM-033B; Patty Former activation remains IM-033C.
Richer maintenance/repair remains IM-034, with separate minimal repair approval
required before any breakdown activation. There is no employee maintenance,
sanitation, component-level simulation, quality/speed degradation, or portable
condition-preserving machine item. Break/re-place fresh condition remains the
ratified alpha limitation. Checkpoint/receipt retention and compaction remain
unresolved; this milestone does not delete recovery evidence.
