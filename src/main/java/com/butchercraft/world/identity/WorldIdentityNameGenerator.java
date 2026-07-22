package com.butchercraft.world.identity;

import java.util.List;
import java.util.Objects;

public final class WorldIdentityNameGenerator {
    private static final long NAME_SALT = 0x4f3a6c2d91b5e807L;

    public String selectName(
            long worldSeed,
            RegionCatalog catalog,
            RegionDefinition region,
            NamingRole role,
            String stableEntityId
    ) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(role, "role");
        if (stableEntityId == null || stableEntityId.isBlank()) {
            throw new IllegalArgumentException("Stable entity id must not be blank");
        }
        NamingProfile profile = catalog.namingProfile(region);
        List<String> names = profile.namesFor(role);

        // Determinism contract: every name is selected from the world seed plus stable
        // region, profile, role, and entity identifiers. No shared Random sequence is
        // used, so generating one entity cannot rename another.
        int index = WorldIdentityDeterminism.stableIndex(
                worldSeed,
                NAME_SALT,
                names.size(),
                region.id(),
                profile.id(),
                role.serializedName(),
                stableEntityId
        );
        String name = names.get(index);
        if (name.isBlank()) {
            throw new IllegalArgumentException("Generated blank name for " + stableEntityId);
        }
        return name;
    }
}
