# Minimal Filesystem Checkpoint Publication

Status: IM-005 implemented foundation only

Source architecture:

- `docs/adr/ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md`
- `docs/adr/ADR-PROPOSED-CHECKPOINT-RECOVERY.md`
- `docs/CHECKPOINT_RECOVERY_FOUNDATION.md`

## Scope

IM-005 implements the smallest filesystem-backed checkpoint publication proof for Checkpoint Recovery. It writes and recovers explicit fixture owner snapshots supplied as opaque bytes. It does not replace existing Minecraft or NeoForge save hooks, migrate live subsystem persistence, load worlds from checkpoints at startup, schedule checkpoint cadence, implement operator commands, archive Evidence, or change gameplay.

IM-006 adds a minimal explicit Clock/Scheduler owner snapshot integration on
top of this filesystem proof. See
`docs/MINIMAL_LIVE_OWNER_SNAPSHOT_INTEGRATION.md`.

Subsystem owners still own snapshot content, schema, validation, runtime state, and restoration. The checkpoint store validates owner identity, snapshot identity, schema metadata, digest, required participation, payload file existence, and payload size. It never parses owner payload internals.

## Filesystem Layout

The store root is supplied explicitly to `CheckpointFilesystemStore`.

```text
<store-root>/
  staging/
    <generation-directory>/
      generation_manifest.json
      owners/
        <owner-directory>/
          owner_manifest.json
          snapshot_payload.bin
  generations/
    <generation-directory>/
      generation_manifest.json
      owners/
        <owner-directory>/
          owner_manifest.json
          snapshot_payload.bin
  checkpoint_head_a.json
  checkpoint_head_b.json
  quarantine/
```

Generation directory names are deterministic from checkpoint schema, committed sequence, and simulation tick. Owner directory names are deterministic encodings of owner ids. Staging directories are never authoritative. Final generation directories are immutable from the store's perspective after publication.

## Publication Phases

Publication uses these phases:

1. Ensure the explicit store root, staging, generation, and quarantine directories exist.
2. Build a generation manifest from explicit owner snapshots, World Identity root reference, and Platform Determinism Manifest reference.
3. Validate required owners and owner payload digests before writing.
4. Write opaque owner payload files under staging.
5. Write owner manifest files under staging.
6. Read payload bytes back and verify digests.
7. Write the canonical generation manifest under staging.
8. Read and validate the staged generation.
9. Atomically move the staging directory to the immutable final generation directory.
10. Publish the dual-slot checkpoint head by writing the target slot through a temporary file.
11. Read and validate the newly published head.

The prior committed head remains authoritative until the new head validates.

## Head Publication

Schema-1 head publication uses two slots:

- `checkpoint_head_a.json`
- `checkpoint_head_b.json`

Odd committed sequences publish to slot A. Even committed sequences publish to slot B. Recovery validates both slots and chooses by checkpoint sequence, not by filesystem timestamps.

Each head record contains schema version, head sequence, selected generation identity, selected manifest digest, predecessor metadata, and canonical head digest.

## Recovery Selection

Recovery scans the explicit store root only. Filesystem enumeration order is sorted before evaluation.

Recovery:

- reports incomplete staging directories and temporary head files as Quarantined Artifacts;
- reads valid head records;
- reads generation manifests, owner manifests, and owner payloads;
- validates manifest, head, owner payload, World Identity, Platform Determinism Manifest, and predecessor digests;
- selects the highest valid head-supported generation;
- falls back to an older valid generation when a newer head or generation is corrupt; and
- returns Recovery-Blocked State when no valid committed generation exists.

Recovery never merges owners from different generations and never treats an unheaded staging directory as committed.

## Artifact Classification

IM-005 reports storage artifacts without silently deleting them:

- incomplete staging directories;
- temporary head files;
- corrupt generation directories;
- invalid head files;
- complete generation directories with no valid head when no head exists; and
- conflicting existing generation directories.

Quarantine is storage-only. It does not represent runtime Unknown Outcome.

## Filesystem Guarantees

IM-005 relies on Java NIO same-filesystem `ATOMIC_MOVE` for final generation directory publication. If atomic generation publication is unsupported, publication fails with an explicit typed failure and the previous committed head remains authoritative.

Head publication writes the inactive or parity-selected head slot through a temporary file. If atomic replacement of the head file is unsupported, the store falls back to replacement and reports `FILESYSTEM_GUARANTEE_REDUCED`. This preserves the prior slot but does not claim a stronger head-replacement guarantee than the filesystem provides.

Files are written through `FileChannel` and `force(true)` before publication checks. This proves process-visible completion and asks the filesystem to flush file contents. It does not prove absolute power-loss durability on hardware or filesystems that acknowledge flushes before durable media persistence.

Directory metadata flushing is not claimed by IM-005.

## Proven By IM-005

- Explicit-root checkpoint store construction.
- Deterministic generation directory naming.
- Staged owner payload and manifest writes.
- Opaque owner payload digest verification.
- Canonical generation manifest bytes.
- Immutable final generation publication through atomic directory move.
- Dual-slot head publication.
- Deterministic recovery selection and fallback.
- Explicit storage artifact reporting.
- Crash-point coverage around publication phases.

## Still Runtime-Gated

- Additional live owner snapshot adapters beyond the IM-006 Clock and
  Scheduler proof.
- Existing save-hook replacement.
- Startup recovery.
- Save-schema migration.
- Checkpoint cadence.
- Operator commands or GUI.
- Evidence archive integration.
- Live Platform Determinism Manifest collection.
- Gameplay-facing recovery.
