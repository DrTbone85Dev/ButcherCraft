package com.butchercraft.world.checkpoint;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.Collections;

public record CheckpointOwnerValidationMetadata(
        CheckpointOwnerId ownerId,
        Map<String, String> values
) implements Comparable<CheckpointOwnerValidationMetadata> {
    public CheckpointOwnerValidationMetadata {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        TreeMap<String, String> copied = new TreeMap<>();
        Objects.requireNonNull(values, "values").forEach((key, value) -> {
            String canonicalKey = CheckpointValidation.id(key, "metadataKey");
            String canonicalValue = CheckpointValidation.text(value, "metadataValue");
            copied.put(canonicalKey, canonicalValue);
        });
        values = Collections.unmodifiableMap(copied);
    }

    public Optional<String> value(String key) {
        return Optional.ofNullable(values.get(CheckpointValidation.id(key, "metadataKey")));
    }

    @Override
    public int compareTo(CheckpointOwnerValidationMetadata other) {
        return ownerId.compareTo(Objects.requireNonNull(other, "other").ownerId);
    }
}
