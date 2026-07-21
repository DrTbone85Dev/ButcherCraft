package com.butchercraft.packaging.serialization;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure Java serialized representation of the external packaging definition schema.
 */
public record SerializedPackagingDefinition(
        PackagingSchemaVersion schemaVersion,
        String id,
        String displayName,
        String format,
        String defaultQuantityUnit,
        List<String> requiredSupplyItems,
        List<String> compatibleCategories,
        List<String> compatibleTags,
        Map<String, String> metadata
) {
    public SerializedPackagingDefinition(
            PackagingSchemaVersion schemaVersion,
            String id,
            String displayName,
            String format,
            String defaultQuantityUnit,
            List<String> compatibleCategories,
            List<String> compatibleTags,
            Map<String, String> metadata
    ) {
        this(
                schemaVersion,
                id,
                displayName,
                format,
                defaultQuantityUnit,
                List.of(),
                compatibleCategories,
                compatibleTags,
                metadata
        );
    }

    public SerializedPackagingDefinition {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        id = requiredString(id, "id");
        displayName = requiredString(displayName, "displayName");
        format = requiredString(format, "format");
        defaultQuantityUnit = requiredString(defaultQuantityUnit, "defaultQuantityUnit");
        requiredSupplyItems = copyStrings(requiredSupplyItems, "requiredSupplyItems");
        compatibleCategories = copyStrings(compatibleCategories, "compatibleCategories");
        compatibleTags = copyStrings(compatibleTags, "compatibleTags");
        metadata = copyMetadata(metadata);
    }

    private static String requiredString(String value, String fieldName) {
        String trimmed = Objects.requireNonNull(value, fieldName).strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Serialized packaging " + fieldName + " cannot be blank");
        }
        return trimmed;
    }

    private static List<String> copyStrings(List<String> source, String fieldName) {
        return List.copyOf(Objects.requireNonNull(source, fieldName).stream()
                .map(value -> requiredString(value, fieldName + " entry"))
                .toList());
    }

    private static Map<String, String> copyMetadata(Map<String, String> metadata) {
        LinkedHashMap<String, String> copied = new LinkedHashMap<>();
        for (var entry : Objects.requireNonNull(metadata, "metadata").entrySet()) {
            copied.put(requiredString(entry.getKey(), "metadata key"), requiredString(entry.getValue(), "metadata value"));
        }
        return Collections.unmodifiableMap(copied);
    }
}
