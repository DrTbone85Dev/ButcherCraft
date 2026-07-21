package com.butchercraft.packaging.serialization;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.packaging.definition.PackagingDefinition;
import com.butchercraft.packaging.definition.PackagingFormat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical deserializer for the stable packaging definition schema contract.
 */
public final class CanonicalPackagingDefinitionDeserializer
        implements PackagingDefinitionDeserializer<SerializedPackagingDefinition> {
    @Override
    public PackagingDefinition deserialize(SerializedPackagingDefinition serialized) {
        Objects.requireNonNull(serialized, "serialized");
        if (!serialized.schemaVersion().equals(PackagingSchemaVersion.CURRENT)) {
            throw new IllegalArgumentException("Unsupported packaging schema version: " + serialized.schemaVersion().value());
        }

        PackagingDefinition.Builder builder = PackagingDefinition.builder()
                .id(serialized.id())
                .displayName(serialized.displayName())
                .schemaVersion(serialized.schemaVersion().value())
                .format(PackagingFormat.fromId(serialized.format()))
                .defaultQuantityUnit(QuantityUnit.fromId(serialized.defaultQuantityUnit()))
                .metadata(metadata(serialized.metadata()));

        serialized.compatibleCategories().stream()
                .map(EngineId::of)
                .forEach(builder::compatibleCategory);
        serialized.compatibleTags().stream()
                .map(EngineId::of)
                .forEach(builder::compatibleTag);
        return builder.build();
    }

    private static Map<EngineId, String> metadata(Map<String, String> metadata) {
        LinkedHashMap<EngineId, String> deserialized = new LinkedHashMap<>();
        for (var entry : metadata.entrySet()) {
            deserialized.put(EngineId.of(entry.getKey()), entry.getValue());
        }
        return deserialized;
    }
}
