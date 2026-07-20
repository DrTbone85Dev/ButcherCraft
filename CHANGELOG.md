# Changelog

## ButcherCraft v0.6.2 - Transformation Registry

### Core

- Added an immutable pure Java transformation registry and builder.
- Registered the built-in Grinder transformations through the registry.
- Updated Grinder transformation execution to query registered definitions by resolved operation id.

### Stability

- Added registry tests for insertion order, lookup, duplicate rejection, null rejection, capability queries, and built-in Grinder coverage.
- Preserved existing Grinder processing output behavior while making transformation definitions registry-backed.

## ButcherCraft v0.6.1 - Grinder Transformation Bridge

### Core

- Connected the Grinder workstation path to the pure Java material transformation engine.
- Added capability-based transformation execution while preserving existing Grinder product behavior.
- Kept Bandsaw and other workstations on the existing execution path for a future deliberate migration.

### Stability

- Added regression coverage for accepted-evaluation execution and Grinder capability advertisement.
- Preserved compatibility with existing processing-operation definitions through the transformation adapter.

## ButcherCraft v0.5.2 - Foundation Update

### Core

- Expanded the internal processing framework for future production systems.
- Improved workstation architecture in preparation for functional processing equipment.
- Enhanced product data handling for future inventory and quality simulation.
- Refined internal systems supporting upcoming gameplay features.

### Performance and Stability

- Improved internal code organization.
- Expanded automated test coverage where appropriate.
- Corrected framework issues discovered during development.
- Improved project stability for future updates.

### Player-Facing

- Added `/butchercraft info` to display the installed version and current development status.

### Developer Note

This release focuses on strengthening ButcherCraft's foundation. Although it does not introduce a major gameplay system, it prepares the project for future processing, inventory, employee, inspection, and business-management features.
