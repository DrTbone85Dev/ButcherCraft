# Allocation Resource And Capacity Provider Framework

Status: RFC-0022 Revision 2 Milestone M22D implemented

Milestone M22D adds the pure Java contracts through which externally
authoritative subsystems may expose immutable Resource and Capacity
observations to Allocation. It does not add a concrete provider, run an
Allocation Cycle, publish Commitments, mutate Allocation runtime, schedule
work, persist observations, or integrate gameplay.

The governing design remains
[`RFC-0022_RESOURCE_ALLOCATION_ENGINE.md`](RFC-0022_RESOURCE_ALLOCATION_ENGINE.md).
The immutable domain, runtime, and deterministic Cycle are documented in
[`RESOURCE_ALLOCATION_DOMAIN.md`](RESOURCE_ALLOCATION_DOMAIN.md),
[`ALLOCATION_RUNTIME.md`](ALLOCATION_RUNTIME.md), and
[`ALLOCATION_CYCLE.md`](ALLOCATION_CYCLE.md).

## Permanent Authority Model

Provider subsystems retain authority over:

- Resource definitions and identity;
- Capacity definitions and current calculations;
- provider runtime;
- provider-specific availability and eligibility;
- provider-specific authoritative views.

Allocation owns:

- stable external references;
- immutable observed Resource and Capacity snapshots;
- generic provider contracts and validation;
- immutable observation bundles, reports, failures, warnings, and digests;
- Requests, AllocationSets, Commitments, lifecycle, Cycles, and reports.

The provider framework observes. It does not become a second Resource catalog
or Capacity authority. Planning still owns decisions, Scheduler owns execution
ordering, execution subsystems own executable work and runtime, Transactions
own economic mutation, and Inventory owns quantities.

## Package And Purity

The framework is located in:

```text
com.butchercraft.world.allocation
```

It is pure Java and imports no Minecraft, NeoForge, Planning, Production,
Scheduler, Inventory, or Transaction implementation. Providers receive no
world, server, service locator, runtime service, cycle executor, or mutable
registry.

Schema 1 invokes providers sequentially. It uses no asynchronous execution,
parallel stream, reflection, runtime scanning, classpath discovery, filesystem
ordering, clock, random source, or hidden global registry.

## Provider Contract

`AllocationResourceProvider` is the smallest observation adapter contract:

```java
AllocationProviderDescriptor descriptor();

AllocationObservationResult observe(AllocationObservationContext context);
```

A concrete adapter may capture a narrow typed authoritative collaborator in
its own constructor. The generic Allocation contract never accepts `Object`,
an unchecked state map, or a concrete provider-domain type.

Providers must:

- expose one stable descriptor and `AllocationProviderId`;
- treat observation as read-only;
- use only the explicit immutable context and their narrow authoritative
  collaborator;
- return one immutable typed result;
- return snapshots only within their declared owner and capability scope;
- avoid wall-clock time, randomness, caller iteration order, and mutation.

The framework does not implement production-grade Workforce, Production,
Inventory, Logistics, Utility, Inspection, or Infrastructure providers in
M22D. Tests use small industry-neutral fixtures only.

## Provider Identity

`AllocationProviderId` is the existing M22A namespaced identity. It requires a
lowercase canonical `namespace:path` value, has value equality, and sorts
lexically. It does not use locale, UUIDs, object identity, filesystem state, or
time.

Provider identity identifies the adapter contract. It does not replace the
authoritative owner subsystem identity carried by `ExternalReference`.

## Provider Descriptor

`AllocationProviderDescriptor` declares:

- provider id;
- authorized external subsystem ids;
- Resource categories the provider may expose;
- Capacity type ids the provider may expose;
- Capacity unit ids the provider may expose;
- immutable typed Allocation metadata;
- schema version.

Owner and capability declarations are immutable, bounded, duplicate-free, and
canonically ordered. They validate provider scope; they are not a dynamic
permissions system. Resource categories, Capacity types, units, and owner ids
remain open namespaced identities rather than closed industry enums.

## Observation Context And Request

`AllocationObservationContext` contains every generic fact passed to a
provider:

- authoritative simulation tick;
- optional requested Resource-category filter;
- optional requested Capacity-type filter;
- immutable scope metadata;
- explicit per-provider Resource and Capacity limits;
- schema version.

An empty category or Capacity filter means all declared values. Filters are
immutable, duplicate-free, and canonically ordered.

`AllocationObservationRequest` adds orchestration-only facts:

- the immutable context;
- an optional canonical provider selection;
- total Resource and Capacity bounds;
- schema version.

An empty provider selection invokes every registered provider. A nonempty
selection that references an unknown provider fails before any provider is
invoked. Context and request schemas must match.

## Provider Results

`AllocationObservationResult` represents exactly one provider invocation.

A successful result contains:

- provider id;
- observation tick;
- zero or more `ObservedResourceSnapshot` values;
- zero or more `ObservedCapacitySnapshot` values;
- optional immutable warnings;
- schema version.

A failed result contains:

- provider id;
- observation tick;
- one or more typed failures;
- schema version.

A failed result cannot also contain snapshots or warnings. A provider
contribution is therefore either complete successful evidence or explicit
failure; it cannot masquerade as partial success.

Unexpected provider exceptions become fixed `PROVIDER_EXCEPTION` evidence.
Exception messages, stack traces, object identity strings, and class names do
not enter canonical results or digests. Typed snapshot-construction failures
are mapped to deterministic provider failure codes where the source failure is
known.

## Resource Snapshot Validation

Providers return the M22A `ObservedResourceSnapshot` type. The observation
service validates:

- result and snapshot tick agreement;
- schema agreement;
- snapshot provider-id agreement;
- declared Resource category;
- requested Resource category;
- authorized external owner;
- unique Resource identity within the provider result;
- Schema-1 aggregate Workforce scope.

Malformed references and individual-worker Workforce concepts are rejected by
the M22A immutable snapshot constructor before they can become observations.
Conflicting duplicate Resource facts fail explicitly.

## Capacity Snapshot Validation

Providers return the M22A `ObservedCapacitySnapshot` type. The observation
service validates:

- result and snapshot tick agreement;
- schema agreement;
- Resource resolution within the same provider result;
- declared and requested Capacity type;
- declared Capacity unit;
- authorized external owner;
- unique Capacity id and `CapacityKey`.

`AllocationQuantity` preserves precision 38, scale 9, exact decimal value, and
`CapacityUnitId`. Negative quantities, incompatible units, and invalid schema
values are rejected by immutable construction. Zero Capacity is valid and is
not treated as omission. The framework performs no arithmetic, addition,
conversion, prediction, or estimation.

Schema 1 permits an unavailable Resource to retain an observed Capacity
snapshot because availability and quantity are separate facts. The existing
Allocation Cycle will not select unavailable Capacity.

## Provider Registry

`AllocationProviderRegistryBuilder` explicitly registers provider instances.
`AllocationProviderRegistry` is immutable and provides:

- canonical provider-id ordering;
- duplicate-id rejection;
- bounded registration;
- `contains`, `find`, descriptor lookup, `size`, lists, and streams;
- immutable public collections;
- a canonical registry digest;
- detached builder reconstruction.

Equivalent registries built in different insertion orders have the same
descriptor order and digest. The registry performs no reflective construction,
service loading, classpath discovery, or global publication. It stores no
authoritative Resource or Capacity state.

Schema 1 permits at most 20,000 registered providers.

## Observation Service

`AllocationObservationService` is stateless after construction with an
immutable provider registry. For one request it:

1. validates provider selection;
2. captures canonical provider order from the registry;
3. invokes each selected provider once and sequentially;
4. validates the provider result envelope;
5. validates Resource and Capacity snapshots;
6. discards a malformed provider contribution as one typed provider failure;
7. aggregates successful provider contributions;
8. detects cross-provider Resource and Capacity claims;
9. builds an immutable report and bundle.

The service never invokes `AllocationCycleExecutor`, reads or mutates
`AllocationRuntimeService`, registers Commitments, schedules work, retries a
provider, selects a fallback provider, or persists data.

## Canonical Aggregation

Aggregation uses:

- provider-id order;
- `ObservedResourceSnapshot` natural order;
- `ObservedCapacitySnapshot` natural order;
- canonical failure and warning order;
- exact observation-tick equality.

Two providers may not claim the same `ResourceId`, `CapacityId`, or
`CapacityKey`. Equal claims are duplicates; unequal claims are conflicts. The
framework performs no last-writer-wins selection, registration-order
precedence, implicit quantity addition, or metadata-derived identity.

Conflicts retain both provider results as evidence and make the bundle
unusable. The framework never guesses which provider is correct.

## Failure Isolation

The failure policy is explicit:

- malformed registry construction is registry-fatal;
- malformed request or unknown provider selection prevents all invocation;
- a provider-local failure preserves unrelated successful provider results;
- an unexpected provider exception is invoked once and is not retried;
- a cross-provider duplicate or conflict is bundle-global;
- a warning does not invalidate an otherwise complete bundle.

Collection bounds never silently truncate Resource or Capacity observations.
A provider contribution that would exceed a request total bound becomes an
explicit failed contribution and contributes no partial snapshots. Retained
failure and warning evidence has an explicit schema bound; exceeding it adds a
`RESULT_LIMIT_EXCEEDED` bundle failure.

## Observation Bundle

`AllocationObservationBundle` contains:

- authoritative simulation tick;
- invoked provider ids and typed provider results;
- canonical Resource and Capacity snapshots;
- provider and bundle failures;
- warnings;
- completeness status;
- immutable provider report and summary;
- scope metadata;
- schema version;
- canonical digest.

Statuses are:

- `COMPLETE`: every provider contribution and global aggregation validated;
- `INCOMPLETE`: one or more provider-local failures occurred;
- `UNUSABLE`: bundle-global conflict or retained-evidence failure occurred.

Only `COMPLETE` returns `usableForAllocationCycle() == true`. Incomplete and
unusable evidence remains inspectable but must not be supplied as complete
Allocation input.

The bundle does not contain Requests, AllocationSets, Commitments, runtime,
Scheduler work, Production plans, or mutable provider state.

## Reports And Digests

`AllocationObservationReport` records:

- canonical invocation order;
- one result digest per provider;
- typed failures and warnings;
- successful and failed provider counts;
- Resource and Capacity counts;
- deterministic operation count;
- registry and request digests;
- schema version.

Canonical SHA-256 digests cover:

- provider descriptors and registry;
- observation context and request;
- Resource and Capacity snapshots;
- provider failures and warnings;
- provider results;
- observation reports and bundles.

Digest encoding uses explicit UTF-8 length framing. It never depends on caller
insertion order, map iteration, provider instance identity, default
`toString`, locale, wall time, randomness, exception stacks, or filesystem
order. Digests validate replay evidence but never replace structural
validation.

## Aggregate Workforce Scope

M22D preserves the Schema-1 Workforce boundary. A future Workforce provider
may expose aggregate position, role, or shift Capacity. It may not expose
individual worker identity, personal availability, skills, fatigue,
assignment, attendance, wage, or schedule through this schema.

## Relationship To Allocation Cycle Input

A future orchestration layer may combine a usable
`AllocationObservationBundle` with:

- immutable Allocation definitions;
- immutable Allocation runtime views;
- candidate AllocationSet ids;
- an explicit `AllocationCycleContext`.

Those values can construct `AllocationCycleInput` through existing public
contracts. The provider framework itself performs no such construction or
cycle invocation automatically. M22D integration tests prove compatibility
without changing the existing Cycle.

## Architecture Validation

The explicit architecture manifest records:

- external Resource authorities own Resource and Capacity definitions;
- Allocation owns immutable observation snapshots and provider aggregation;
- the Allocation provider registry is canonical and currently empty;
- Allocation has no concrete external-authority dependency;
- no Allocation persistence descriptor or Scheduler stage 350 exists.

Source-boundary tests prohibit concrete owner imports, Minecraft, NeoForge,
reflection, scanning, hidden time, randomness, asynchronous execution,
provider runtime mutation paths, Allocation Cycle invocation, and Allocation
runtime mutation from the provider framework.

## Public Contracts

The M22D implementation exposes the smallest useful internal contracts:

- `AllocationProviderId`;
- `AllocationProviderDescriptor`;
- `AllocationResourceProvider`;
- `AllocationProviderRegistryBuilder`;
- `AllocationProviderRegistry`;
- `AllocationObservationContext`;
- `AllocationObservationRequest`;
- `AllocationObservationResult`;
- `AllocationProviderFailure` and `AllocationProviderWarning`;
- `AllocationObservationService`;
- `AllocationObservationOperationResult`;
- `AllocationObservationBundle`;
- `AllocationObservationReport` and `AllocationObservationSummary`.

These are documented Core contracts but are not declared a stable third-party
extension API. That still requires a real external consumer and separate owner
approval.

## Structural Bounds

Schema 1 defines:

- 20,000 providers per registry;
- 64 authoritative owner ids per descriptor;
- 256 declarations per capability collection;
- 100,000 Resources per provider and per bundle;
- 100,000 Capacities per provider and per bundle;
- 100,000 retained provider failures;
- 100,000 retained provider warnings.

Bounds fail explicitly. Tests use no timing assertion and prove canonical
replay for 20,000 providers, 100,000 Resources, 100,000 Capacities, and
high-conflict provider input.

## Explicit Exclusions

M22D does not add:

- a concrete Workforce, Production, Inventory, Logistics, Utility,
  Inspection, or Infrastructure provider;
- Scheduler stage 350 or Allocation Work;
- Planning submission changes;
- Production execution gating;
- automatic Allocation Cycle invocation;
- Commitment activation, release, or expiration integration;
- disk persistence, codecs, save migration, or world lifecycle hooks;
- Inventory mutation, Transaction execution, or Goods reservation;
- Minecraft, NeoForge, gameplay, commands, networking, menus, or assets;
- asynchronous or parallel provider execution.

## Deferred Work

M22E-M22F remain separately gated. Future owner-authorized work may add:

- production-grade concrete provider adapters in owning or integration
  packages;
- Allocation observation and runtime persistence;
- Scheduler stage 350 and Work integration;
- Planning handoff changes;
- Production activation and execution gating;
- release and expiration integration;
- stable third-party API approval.

None of that work is implied by M22D.
