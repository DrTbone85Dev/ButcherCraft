package com.butchercraft.transformation.serialization;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure Java serialized representation of the external transformation definition schema.
 */
public record SerializedTransformationDefinition(
        TransformationSchemaVersion schemaVersion,
        String id,
        String displayName,
        Optional<String> requiredCapability,
        List<SerializedTransformationInput> inputs,
        List<SerializedTransformationOutput> outputs,
        SerializedTransformationDuration duration,
        SerializedTransformationYield yield,
        Map<String, String> metadata
) {
    public SerializedTransformationDefinition {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        id = Objects.requireNonNull(id, "id").strip();
        displayName = Objects.requireNonNull(displayName, "displayName").strip();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Serialized transformation id cannot be blank");
        }
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("Serialized transformation display name cannot be blank");
        }
        requiredCapability = Objects.requireNonNull(requiredCapability, "requiredCapability")
                .map(value -> value.strip());
        requiredCapability.ifPresent(value -> {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Serialized required capability cannot be blank");
            }
        });
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(yield, "yield");
        metadata = copyMetadata(metadata);
    }

    private static Map<String, String> copyMetadata(Map<String, String> metadata) {
        LinkedHashMap<String, String> copied = new LinkedHashMap<>();
        for (var entry : Objects.requireNonNull(metadata, "metadata").entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "metadata key").strip();
            String value = Objects.requireNonNull(entry.getValue(), "metadata value").strip();
            if (key.isEmpty() || value.isEmpty()) {
                throw new IllegalArgumentException("Serialized metadata keys and values cannot be blank");
            }
            copied.put(key, value);
        }
        return Collections.unmodifiableMap(copied);
    }
}
