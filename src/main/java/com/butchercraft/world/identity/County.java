package com.butchercraft.world.identity;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record County(
        String id,
        String displayName,
        String regionId,
        List<Settlement> settlements
) {
    public County {
        id = requireNonBlank(id, "id");
        displayName = requireNonBlank(displayName, "displayName");
        regionId = requireNonBlank(regionId, "regionId");
        settlements = List.copyOf(Objects.requireNonNull(settlements, "settlements"));
        if (settlements.isEmpty()) {
            throw new IllegalArgumentException("County settlements must not be empty");
        }
        Set<String> settlementIds = new HashSet<>();
        for (Settlement settlement : settlements) {
            Objects.requireNonNull(settlement, "settlement");
            if (!id.equals(settlement.countyId())) {
                throw new IllegalArgumentException("Settlement " + settlement.id() + " belongs to " + settlement.countyId()
                        + " instead of county " + id);
            }
            if (!settlementIds.add(settlement.id())) {
                throw new IllegalArgumentException("Duplicate settlement id in county " + id + ": " + settlement.id());
            }
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("County " + fieldName + " must not be blank");
        }
        return value;
    }
}
