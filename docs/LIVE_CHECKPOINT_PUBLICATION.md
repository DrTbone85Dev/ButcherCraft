# Live Checkpoint Publication

Status: IM-031C-R3 through IM-031C-R4 implemented; ADR-02A-P1 ratified; final
Product Owner acceptance pending.

## Operator Commands

Privileged operators can request a checkpoint at the next safe boundary:

```text
/butchercraft diagnostic checkpoint create
```

The request reports whether it was accepted, coalesced, busy, or unavailable.
Only one generation may publish at a time.

Current publication and committed-head diagnostics are available through:

```text
/butchercraft diagnostic checkpoint status
```

Status includes the current and committed generation, previous valid
generation, checkpoint tick, participant count, trigger causes, freeze time,
publication time, head-commit time, total time, generation bytes, next periodic
eligibility tick, and typed failures.

## Runtime Policy

- Capture runs after Scheduler finalizes the authoritative Clock tick.
- All 17 required owners participate, including canonical empty snapshots.
- Workstation supplies exact durable per-instance projection bytes for every
  required identity, including unloaded instances, without force-loading.
- Checkpoint Recovery closes the deterministic required set from immutable
  owner references and rejects a candidate before head commit when any required
  projection is unavailable, corrupt, unsupported, conflicting, or recovery-
  required.
- Owner state freezes on the server thread into immutable bytes.
- Filesystem publication and verification use one asynchronous publisher.
- Automatic capture is eligible every 6,000 authoritative simulation ticks.
- Manual and periodic causes coalesce at the same safe boundary.
- A busy manual request does not start a concurrent generation.
- Graceful shutdown attempts a changed-state checkpoint with a ten-second
  wait. Timeout allows shutdown to continue; an already-frozen generation may
  still complete atomically, while an incomplete generation remains non-authoritative.
- All committed generations are retained; no cleanup policy is authorized.

## Storage

Live generations are stored below:

```text
<world>/butchercraft/checkpoints
```

Each immutable generation contains one manifest and one owner directory per
required participant. The generation becomes authoritative only after complete
verification and publication of the inactive alternating head slot. Staging or
uncommitted generations never replace the previous valid head.

## Recovery Boundary

R3/R3B creates, verifies, and reports live checkpoints. That publication path
does not select a checkpoint during world startup, restore owner-native files,
replay effects, synthesize Scheduler ticks, roll back Clock state, or resume
machine work. R4 provides the separate startup selection and restoration path.
Historical `chunk_unloaded` Workstation entries are not exact inventory or
controller projections. The read-only Workstation candidate verifier classifies
such generations as non-restorable. New R3B snapshots embed exact payloads and
are complete independent of load state when all required projections exist.

The ratified
[`ADR-02A-P1 durable Workstation projection amendment`](adr/ADR-PROPOSED-DURABLE-WORKSTATION-PROJECTION-AND-CHECKPOINT-COMPLETENESS.md)
defines the controlling direction. R3A implements its Workstation-owned durable
projection foundation and R3B activates checkpoint completeness. R3C preserves
the incomplete historical generation and publishes a self-contained successor.
R4 first selects coherent live state when provable; otherwise it selects only a
verified committed `COMPLETE_RESTORABLE` generation, persists one Restoration
Intent, restores all 17 owners through owner-native adapters, verifies logical
equivalence, commits one Restoration Result, installs authority blocks, and
permits mutable startup only when those blocks allow it. Workstation projections
restore without force-loading chunks and reconcile matching instances lazily.
