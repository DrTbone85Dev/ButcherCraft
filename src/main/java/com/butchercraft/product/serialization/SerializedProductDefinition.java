package com.butchercraft.product.serialization;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure Java serialized representation of the external product definition schema.
 */
public record SerializedProductDefinition(
        ProductSchemaVersion schemaVersion,
        String id,
        String displayName,
        String category,
        String defaultQuantityUnit,
        List<String> tags,
        Map<String, String> metadata
) {
    public SerializedProductDefinition {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        id = Objects.requireNonNull(id, "id").strip();
        displayName = Objects.requireNonNull(displayName, "displayName").strip();
        category = Objects.requireNonNull(category, "category").strip();
        defaultQuantityUnit = Objects.requireNonNull(defaultQuantityUnit, "defaultQuantityUnit").strip();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Serialized product id cannot be blank");
        }
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("Serialized product display name cannot be blank");
        }
        if (category.isEmpty()) {
            throw new IllegalArgumentException("Serialized product category cannot be blank");
        }
        if (defaultQuantityUnit.isEmpty()) {
            throw new IllegalArgumentException("Serialized product default quantity unit cannot be blank");
        }
        tags = copyTags(tags);
        metadata = copyMetadata(metadata);
    }

    private static List<String> copyTags(List<String> tags) {
        return List.copyOf(Objects.requireNonNull(tags, "tags").stream()
                .map(tag -> Objects.requireNonNull(tag, "tag").strip())
                .peek(tag -> {
                    if (tag.isEmpty()) {
                        throw new IllegalArgumentException("Serialized product tags cannot be blank");
                    }
                })
                .toList());
    }

    private static Map<String, String> copyMetadata(Map<String, String> metadata) {
        LinkedHashMap<String, String> copied = new LinkedHashMap<>();
        for (var entry : Objects.requireNonNull(metadata, "metadata").entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "metadata key").strip();
            String value = Objects.requireNonNull(entry.getValue(), "metadata value").strip();
            if (key.isEmpty() || value.isEmpty()) {
                throw new IllegalArgumentException("Serialized product metadata keys and values cannot be blank");
            }
            copied.put(key, value);
        }
        return Collections.unmodifiableMap(copied);
    }
}
