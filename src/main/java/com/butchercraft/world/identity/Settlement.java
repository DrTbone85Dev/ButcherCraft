package com.butchercraft.world.identity;

import java.util.Objects;

public record Settlement(
        String id,
        String displayName,
        String countyId,
        SettlementType type
) {
    public Settlement {
        id = requireNonBlank(id, "id");
        displayName = requireNonBlank(displayName, "displayName");
        countyId = requireNonBlank(countyId, "countyId");
        type = Objects.requireNonNull(type, "type");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Settlement " + fieldName + " must not be blank");
        }
        return value;
    }
}
