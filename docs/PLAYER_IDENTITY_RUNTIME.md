# Player Identity Runtime

Status: v0.9.0 Phase 9 foundation

The Player Identity Runtime creates persistent ButcherCraft identities for Minecraft players who join a world. It connects the player to the existing world simulation without making the player the creator of that world.

## Architecture

World Identity remains immutable generated history. Player identities are mutable participants stored beside it.

- `WorldIdentity` stores regions, counties, settlements, commercial properties, businesses, families, ownership, and supply-network records.
- `PlayerIdentityRecord` stores only references into that world plus the Minecraft UUID and player identity id.
- `PlayerIdentityRegistry` is an immutable collection with indexes by Minecraft UUID and `PlayerIdentityId`.
- `PlayerIdentityFactory` creates first-time identities deterministically from world seed plus Minecraft UUID.
- `PlayerIdentityManager` coordinates load, lookup, creation, validation, save, and synchronized access.
- `PlayerIdentityStorage` owns JSON serialization, deserialization, schema validation, and file persistence.
- `PlayerJoinInitializer` is the Minecraft-facing login bridge.

Only `PlayerJoinInitializer` imports Minecraft or NeoForge APIs. The record, registry, factory, manager, and storage classes remain Java-only.

## Identity Lifecycle

1. A server-side player login event fires.
2. `PlayerJoinInitializer` obtains the immutable `WorldIdentity` through `WorldIdentityService`.
3. The initializer resolves the world-specific player identity file.
4. `PlayerIdentityManager` loads the existing registry if needed.
5. If the Minecraft UUID already exists, the existing identity is returned.
6. If the UUID is new, `PlayerIdentityFactory` creates a deterministic identity.
7. The candidate registry validates all world references.
8. `PlayerIdentityStorage` saves the updated registry.

An existing identity is never recreated during normal load.

## Persistence

Player identities are stored outside World Identity:

```text
<world>/butchercraft/player_identities.json
```

World Identity remains in its existing persistence path and schema. Phase 9 does not increase `WorldIdentity.CURRENT_SCHEMA_VERSION`, which remains `6`.

The player identity file root contains:

```json
{
  "schema_version": 1,
  "player_identities": []
}
```

Each player identity stores:

- `schema_version`
- `minecraft_uuid`
- `player_identity_id`
- `starting_scenario`
- `career_profile`
- `settlement_id`
- `commercial_property_id`
- `business_id`
- `ownership_entity_id`
- `family_id`
- `creation_timestamp`

Nullable references are written explicitly as JSON `null`.

## Determinism

First-time identity creation is deterministic per world seed and Minecraft UUID. The same UUID in the same world resolves to the same identity fields if regenerated from the same world snapshot.

The creation timestamp is a deterministic in-world foundation timestamp, not a real-time login audit field.

## Validation

The runtime rejects:

- Duplicate Minecraft UUIDs.
- Duplicate `PlayerIdentityId` values.
- Unknown starting scenarios.
- Career profiles unsupported by the selected starting scenario.
- Missing settlement references.
- Missing commercial property references.
- Missing business references.
- Missing family references.
- Missing ownership entity references.
- Business/property/settlement mismatches.
- Ownership entity/business mismatches.
- Corrupt JSON.
- Unsupported root or record schema versions.

## Future Extension Points

Future milestones can attach business management, employees, production, economy, reputation, progression, contracts, and family legacy systems to `PlayerIdentityId` without changing World Identity generation.

Out of scope for Phase 9:

- Economy.
- Production.
- Machines.
- Inventory.
- Employees.
- NPC AI.
- Contracts.
- Progression.
- Skills.
- Money.
- Orders.
- Reputation changes.
- Business simulation.
- Rendering.
- UI.
- Commands.
