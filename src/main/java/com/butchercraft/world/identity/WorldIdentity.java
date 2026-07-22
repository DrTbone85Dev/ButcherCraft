package com.butchercraft.world.identity;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record WorldIdentity(
        int schemaVersion,
        String id,
        long worldSeed,
        Region region,
        List<County> counties
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public WorldIdentity {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported world identity schema version: " + schemaVersion);
        }
        id = requireNonBlank(id, "id");
        region = Objects.requireNonNull(region, "region");
        counties = List.copyOf(Objects.requireNonNull(counties, "counties"));
        if (counties.isEmpty()) {
            throw new IllegalArgumentException("World identity counties must not be empty");
        }
        Set<String> countyIds = new HashSet<>();
        Set<String> settlementIds = new HashSet<>();
        for (County county : counties) {
            Objects.requireNonNull(county, "county");
            if (!region.id().equals(county.regionId())) {
                throw new IllegalArgumentException("County " + county.id() + " belongs to " + county.regionId()
                        + " instead of region " + region.id());
            }
            if (!countyIds.add(county.id())) {
                throw new IllegalArgumentException("Duplicate county id: " + county.id());
            }
            for (Settlement settlement : county.settlements()) {
                if (!settlementIds.add(settlement.id())) {
                    throw new IllegalArgumentException("Duplicate settlement id: " + settlement.id());
                }
            }
        }
    }

    public List<Settlement> settlements() {
        return counties.stream()
                .flatMap(county -> county.settlements().stream())
                .toList();
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("World identity " + fieldName + " must not be blank");
        }
        return value;
    }
}
