package com.butchercraft.world.allocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public record AllocationMetadata(Map<String, AllocationMetadataValue> values) {
    private static final AllocationMetadata EMPTY = new AllocationMetadata(Map.of());

    public AllocationMetadata {
        if (values == null) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.NULL_VALUE,
                    "metadata",
                    "Allocation metadata is required"
            );
        }
        TreeMap<String, AllocationMetadataValue> ordered = new TreeMap<>();
        values.forEach((key, value) -> {
            String canonicalKey = AllocationValidation.id(key, "metadataKey");
            if (value == null) {
                throw AllocationValidation.failure(
                        AllocationValidationFailureCode.NULL_VALUE,
                        "metadataValue",
                        "Allocation metadata value is required for " + canonicalKey
                );
            }
            ordered.put(canonicalKey, value);
        });
        if (ordered.size() > AllocationSchema.MAXIMUM_METADATA_ENTRIES) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.INVALID_METADATA,
                    "metadata",
                    "Allocation metadata exceeds " + AllocationSchema.MAXIMUM_METADATA_ENTRIES + " entries"
            );
        }
        values = Collections.unmodifiableMap(new LinkedHashMap<>(ordered));
    }

    public static AllocationMetadata empty() {
        return EMPTY;
    }

    public static AllocationMetadata of(Map<String, AllocationMetadataValue> values) {
        return new AllocationMetadata(values);
    }
}
