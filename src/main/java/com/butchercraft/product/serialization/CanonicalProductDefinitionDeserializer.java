package com.butchercraft.product.serialization;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.product.definition.ProductDefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical deserializer for the stable product definition schema contract.
 */
public final class CanonicalProductDefinitionDeserializer
        implements ProductDefinitionDeserializer<SerializedProductDefinition> {
    @Override
    public ProductDefinition deserialize(SerializedProductDefinition serialized) {
        Objects.requireNonNull(serialized, "serialized");
        if (!serialized.schemaVersion().equals(ProductSchemaVersion.CURRENT)) {
            throw new IllegalArgumentException("Unsupported product schema version: " + serialized.schemaVersion().value());
        }

        ProductDefinition.Builder builder = ProductDefinition.builder()
                .id(serialized.id())
                .displayName(serialized.displayName())
                .schemaVersion(serialized.schemaVersion().value())
                .category(ProductCategory.fromId(EngineId.of(serialized.category())))
                .defaultQuantityUnit(QuantityUnit.fromId(serialized.defaultQuantityUnit()))
                .metadata(metadata(serialized.metadata()));

        serialized.tags().stream()
                .map(EngineId::of)
                .forEach(builder::tag);
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
