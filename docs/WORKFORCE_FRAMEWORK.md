# Workforce Framework

Status: v0.9.0 Phase 12 foundation

The Workforce Framework defines the staffing structure a business requires to operate. It does not create employees, villagers, AI, hiring, payroll, production, or gameplay behavior.

## Architecture

The workforce package owns:

- `WorkforceDefinition` for the complete workforce structure attached to a business.
- `WorkforceDefinitionId` for stable definition identity.
- `WorkforcePosition` and `PositionId` for immutable job-position records.
- `WorkforcePositionType` for initial position categories.
- `WorkforceSkillLevel` for qualification bands.
- `CertificationType` for required certification categories.
- `WorkforceShiftAssignment` for shift-level minimum and maximum staffing.
- `WorkforceStaffingRule` for required and optional positions plus total staffing bounds.
- `WorkforceRegistry` for deterministic lookup, validation, and loading.
- `WorkforceManager` for definition creation, validation, and runtime shift lookup.
- `WorkforceStorage` for schema-versioned JSON persistence.

Only `com.butchercraft.world.WorkforceService` imports Minecraft or NeoForge APIs. The workforce package is Java-only and can be tested without launching Minecraft.

## Lifecycle

Server start:

1. Resolve `<world>/butchercraft/workforce_definitions.json`.
2. Load existing workforce definitions if present.
3. Create deterministic default definitions for businesses that do not yet have workforce definitions.
4. Validate every `BusinessId` against World Identity.
5. Validate shift references against Business Runtime.

Runtime lookup:

1. Business Runtime exposes the current active shift id.
2. Workforce Manager finds definitions for that business.
3. Workforce Manager returns required positions for the current shift.
4. Future employee systems may decide whether those positions are filled.

Server stop:

1. Save the active workforce registry.
2. Clear the active workforce service reference.

## Staffing Model

A workforce definition stores:

- business reference
- workforce definition id
- positions
- shift assignments
- staffing rule
- schema version

A workforce position stores:

- position id
- position type
- display name
- required skill level
- required certifications
- assigned shift id
- required flag
- maximum workers

A shift assignment stores:

- shift id
- position id
- minimum workers
- maximum workers

No worker identity, villager, employee, schedule, wage, productivity, or inventory data is stored in this framework.

## Persistence

Workforce definitions are stored at:

```text
<world>/butchercraft/workforce_definitions.json
```

The schema version is `1`.

The file stores workforce definitions and `BusinessId` references only. It does not duplicate immutable business names, property records, ownership records, settlement records, or runtime operational state.

## Validation

The workforce framework rejects:

- duplicate workforce definition ids
- duplicate position ids
- unknown business ids
- invalid shift references
- unknown position references
- invalid position types
- invalid skill levels
- invalid certifications
- `none` certification combined with other certifications
- duplicate certifications
- minimum workers greater than maximum workers
- required positions with zero staffing
- assignments that exceed position maximum workers
- positions missing shift assignments
- unsupported schema versions
- corrupt JSON

## Extension Points

Future employee systems should occupy workforce positions rather than inventing separate job structures. Future production, AI, scheduling, economy, inspections, and automation systems should consume workforce definitions through `WorkforceManager` or a narrow service built on top of it.

Out of scope for Phase 12:

- employees
- villagers
- AI
- hiring
- firing
- payroll
- production
- machines
- inventory
- economy
- inspections
- reputation
- productivity
- gameplay
- GUI
- networking
