# Workforce Framework

Status: implemented workforce, employee, and department navigation foundations

The Workforce Framework defines the staffing structure a business requires to
operate and owns individual Employee Identity, Employment Records, Department
definitions, department assignments, and department navigation anchors. It
still does not add job claiming, workstation operation, item carrying, hiring
markets, payroll, production authority, or automation.

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
- `EmployeeId` for deterministic employee identity.
- `EmployeeRecord` for employment status, profile, assigned shift reference,
  optional position reference, optional department reference, presence state,
  entity linkage, and revision.
- `EmployeeManager` for explicit employee creation, lifecycle, shift,
  department, presence, and entity-link transitions.
- `EmployeeStorage` for schema-versioned employee record persistence.
- `DepartmentId`, `DepartmentRecord`, `DepartmentRegistry`, and
  `DepartmentManager` for Workforce-owned department definitions, anchors,
  and employee assignment validation.
- `DepartmentStorage` for schema-versioned department persistence.

Only service, command, registration, client, and entity integration classes
import Minecraft or NeoForge APIs. The workforce domain packages remain
Java-only and can be tested without launching Minecraft.

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
4. Employee Manager may observe employee records against the current Business
   Runtime shift identity.
5. Present employees may target Workforce-published department anchors when a
   functional anchor exists for the assigned department.
6. Future employee systems may decide whether positions are filled or work is
   assigned.

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

Workforce definitions do not store worker identity, villager, schedule, wage,
productivity, or inventory data. Employee records are stored separately in the
employee record file.

## Employee Records

Employee records store:

- employee id
- business id
- display name
- employment status
- presence state
- assigned Business Runtime shift reference
- optional Workforce position id
- optional Workforce department id
- hire Business Calendar timestamp
- optional entity link and anchor
- schema version and revision

Employee records do not store Production tasks, workstation assignments,
machine reservations, inventory contents, wages, payroll, fatigue, morale,
training, productivity, or customer interaction state.

## Departments

Department schema version 1 defines five Workforce-owned departments:

- `processing`
- `packaging`
- `shipping`
- `office`
- `maintenance`

Processing is the only functional default department in IM-024. It has a
default Overworld anchor and bounded radius. The remaining departments are
registered definitions only until later milestones assign anchors and behavior.

Employees are assigned to departments, not workstations. Department assignment
does not claim jobs, reserve machines, submit Scheduler work, operate
Production, mutate Inventory, consume Execution authority, or move items.

## Persistence

Workforce definitions are stored at:

```text
<world>/butchercraft/workforce_definitions.json
```

The schema version is `1`.

The file stores workforce definitions and `BusinessId` references only. It does not duplicate immutable business names, property records, ownership records, settlement records, or runtime operational state.

Employee records are stored at:

```text
<world>/butchercraft/employee_records.json
```

The schema version is `1`. The file stores employee identity, employment
record state, shift references, optional position references, and entity
linkage. It does not duplicate Business Runtime shift definitions or World
Identity business records.

Department records are stored at:

```text
<world>/butchercraft/departments.json
```

The schema version is `1`. The file stores department identities, display
labels, optional anchors, optional presentation metadata, and revision. It does
not store jobs, workstation assignments, machine reservations, Production
runs, Inventory quantities, or Scheduler work.

## Validation

The workforce framework rejects:

- duplicate workforce definition ids
- duplicate position ids
- unknown business ids
- invalid shift references
- unknown position references
- unknown department references
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

Future employee systems should occupy workforce positions and departments
rather than inventing separate job structures. Future production, AI,
scheduling, economy, inspections, and automation systems should consume
workforce definitions, department definitions, and employee records through
narrow Workforce-owned services.

Out of scope after IM-024:

- villagers
- workstation AI
- job claiming
- workstation operation
- item carrying
- logistics
- machine reservation
- payroll
- production
- machines
- inventory
- economy
- inspections
- reputation
- productivity
- GUI
- networking
