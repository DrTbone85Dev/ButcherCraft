package com.butchercraft.packaging.serialization;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.packaging.definition.PackagingDefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical serializer for the stable packaging definition schema contract.
 */
public final class CanonicalPackagingDefinitionSerializer
        implements PackagingDefinitionSerializer<SerializedPackagingDefinition> {
    @Override
    public SerializedPackagingDefinition serialize(PackagingDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return new SerializedPackagingDefinition(
                new PackagingSchemaVersion(definition.schemaVersion()),
                definition.id().value(),
                definition.displayName(),
                definition.format().id(),
                definition.defaultQuantityUnit().id(),
                definition.compatibleCategories().stream()
                        .map(ProductCategory::id)
                        .map(EngineId::value)
                        .toList(),
                definition.compatibleTags().stream()
                        .map(EngineId::value)
                        .toList(),
                metadata(definition.metadata())
        );
    }

    private static Map<String, String> metadata(Map<EngineId, String> metadata) {
        LinkedHashMap<String, String> serialized = new LinkedHashMap<>();
        for (var entry : metadata.entrySet()) {
            serialized.put(entry.getKey().value(), entry.getValue());
        }
        return serialized;
    }
}
