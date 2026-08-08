# ButcherCraft Cutting Table

Status: IM-028B acceptance recipe and employee source use implemented

## Purpose

The Cutting Table is the bounded source workstation for the current Material
Handling proof and now supports exactly one player-operated acceptance recipe:

```text
Beef Short Loin -> T-Bone Steak + Beef Trim
```

It has one input, one primary output, and one dedicated Beef Trim byproduct
output. Beef Trim is never a Cutting Table input. The operation uses the
existing Workstation-owned validation and atomic commit path, Execution owns
its operation lifecycle, and Scheduler owns timing. It does not participate
in Production and employees cannot operate it.

Registered block:

```text
butchercraft:cutting_table
```

## Ownership

The Cutting Table and Workstation endpoint runtime own:

- the input, primary output, and Beef Trim byproduct output;
- canonical workstation instance identity and monotonic generation;
- endpoint freshness;
- transfer-aware withdrawal and source-return validation;
- durable prepare/effect/result journal publication;
- immutable endpoint owner results;
- projection reconciliation after startup;
- inventory persistence and block-removal recovery.

Material Handling may submit narrow withdrawal or return requests and consume
immutable results. It cannot mutate either inventory slot. Workforce may reserve the
table and navigate an employee to it. It cannot extract, insert, or infer
custody.

## Player Use

The block opens a three-slot menu labeled `Input`, `Primary Output`, and
`Beef Trim Output`. Placing one Beef Short Loin in the input starts the one
accepted recipe. Completion atomically consumes that input and creates one
T-Bone Steak in the primary output plus one Beef Trim in the byproduct output.
Either occupied output blocks the operation without consuming input. Normal
output insertion remains prohibited.

For development-only transfer tests, a permission-level-2 operator may still
preload exactly one Beef Trim directly into the byproduct output:

```text
/butchercraft workstation preload-cutting-table-output <x> <y> <z>
```

An identical preload observes the existing byproduct output; an occupied or
transfer-locked byproduct output is rejected. This command remains a temporary
development harness. Normal player acceptance uses the recipe above. IM-028B
permits an operator to bind the table's exact coordinates as the source of an
explicit employee transfer. No command searches for another table.

The employee must hold the table reservation and physically reach a valid
approach before Material Handling requests withdrawal. Visible carrying begins
only after the Workstation owner result proves removal and Material Handling
accepts exact custody.

## Source Return

Post-custody cancellation returns only to the original endpoint instance. The
employee reacquires that source reservation and physically returns before
Material Handling invokes source return to the same Beef Trim output slot. A removed or replaced Cutting Table
does not inherit the earlier identity; custody remains fail-visible rather than
being inserted into a replacement block.

## Persistence And Recovery

The block entity is the live inventory projection. Consequential transfer
effects are durably represented by the Workstation endpoint journal under the
same owner publication boundary as immutable results. Startup restores the
instance registry and journal before reconciling the block-entity projection
and before Material Handling or Workforce may resume.

The table drops recoverable contents on ordinary removal only when endpoint
retirement proves that doing so cannot duplicate unresolved journal custody.
Any active reservation is invalidated on removal.

## Current Limits

- one Beef Short Loin input;
- one T-Bone Steak primary output;
- one Beef Trim byproduct output used as the schema-1 transfer source;
- one accepted player-operated recipe only;
- exactly one Beef Trim per Material Handling request;
- no broader fabrication catalog or recipe selection;
- no automatic restocking;
- no workstation search;
- no Ground Beef or Patty Former flow;
- no public expansion API guarantee.
