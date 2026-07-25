# Controlled Development Checkpoint Invocation

Status: IM-007 development-only invocation foundation implemented.

This document records the narrow development adapter introduced by IM-007. It
does not authorize automatic checkpoint cadence, startup recovery, save-hook
replacement, public operator tooling, gameplay recovery, new owner snapshot
adapters, migrations, Allocation integration, or Execution integration.

## Purpose

The development checkpoint invocation path proves that a loaded development
world can explicitly capture the current Simulation Clock and Simulation
Scheduler owner snapshots, publish them through the filesystem checkpoint
store, list stored generations, validate integrity, inspect the selected
generation, and exercise coordinated restoration in a controlled harness.

The invocation layer is an adapter only. It owns command or harness inputs,
development gating, deterministic checkpoint-root resolution, and diagnostic
formatting. It does not own Clock state, Scheduler state, World Identity,
checkpoint publication, checkpoint selection, owner restoration validation, or
recovery policy.

## Development Gating

The command surface is registered under the existing development diagnostic
branch:

- `/butchercraft diagnostic checkpoint capture`
- `/butchercraft diagnostic checkpoint list`
- `/butchercraft diagnostic checkpoint validate`
- `/butchercraft diagnostic checkpoint inspect-selected`
- `/butchercraft diagnostic checkpoint inspect <generation>`
- `/butchercraft diagnostic checkpoint restore-selected`

All commands require the existing `enableDevelopmentDiagnostic` common config
gate. The command output identifies itself as development checkpoint
diagnostics. These commands are not stable public API and are not a
release-facing operator interface.

## Operations

Capture resolves the active world root, reads the already-initialized World
Identity external-root reference, uses the current narrow development Platform
Determinism Manifest reference, captures Clock and Scheduler snapshots through
their owning packages, and publishes the resulting generation through
`CheckpointFilesystemStore`.

List runs deterministic filesystem recovery scanning and reports committed
generations, heads, and visible storage artifacts.

Validate runs read-only checkpoint recovery validation against the active World
Identity and development Platform Determinism Manifest reference. It reports
head, manifest, owner payload, predecessor-chain, World Identity, and Platform
Determinism Manifest diagnostics without mutating runtime state.

Inspect selected reports the selected generation, selection outcome,
predecessor, authoritative tick, owners, owner snapshot identities,
configuration identities, World Identity root identity, Platform Determinism
Manifest identity, heads, and diagnostics. Opaque owner payload contents remain
hidden.

Inspect exact generation accepts only canonical checkpoint generation identity
strings such as `butchercraft:checkpoint/00000000000000000001/5`. The selector
is identity parsing only; it never treats user input as a filesystem path.

## Restoration Safety

Live loaded-world restoration is intentionally rejected by the command surface.
The repository does not yet expose a Scheduler Runtime Authority pause/swap
boundary or a service-level Clock/Scheduler replacement boundary that can prove
safe mutation during an active server lifecycle.

The controlled harness can restore Clock and Scheduler together through the
existing coordinator. This proves owner preparation, all-or-nothing
publication, and rollback behavior without changing loaded-world runtime state
from a player or operator command.

## Checkpoint Root

The development checkpoint root is:

`<world>/butchercraft/development_checkpoints`

The root is derived from the active world root, converted to an absolute
normalized path, checked to remain inside that world root, and kept separate
from normal subsystem save files such as:

- `<world>/butchercraft/simulation_state.json`
- `<world>/butchercraft/simulation_scheduler.json`
- Overworld `SavedData` named `butchercraft_world_identity`

No process working directory or global shared checkpoint root is used.

## Platform Determinism Manifest

IM-007 uses a narrow development Platform Determinism Manifest reference for
the current Clock/Scheduler checkpoint proof. It truthfully records that a full
runtime Platform Determinism Manifest collector does not yet exist.

## Failure Reporting

Development diagnostics use typed development failures for disabled gating,
missing world root, unavailable checkpoint root, missing World Identity,
concurrent or recursive invocation, owner capture failure, publication failure,
invalid generation selection, missing or corrupt generation, World Identity
mismatch, Platform Determinism Manifest mismatch, unsafe live restoration,
owner preparation failure, owner publication failure, rollback failure, and
Recovery-Blocked State.

Checkpoint Recovery failures remain visible alongside development failures.

## Deferred Scope

The following remain gated:

- automatic checkpoint cadence
- startup recovery
- save-hook replacement
- public operator recovery commands
- live loaded-world restore
- Inventory, Transaction, Planning, Production, Allocation, Execution, Evidence,
  Goods, Actors, Orders, Contracts, Business Runtime, Workforce, Player
  Identity, and gameplay owner snapshot adapters
- full Platform Determinism Manifest collection
- archive cleanup, compression, checksum policy changes, migrations, and cold
  archives
- gameplay or UI integration
