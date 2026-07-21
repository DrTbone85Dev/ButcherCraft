package com.butchercraft.product.serialization;

import com.butchercraft.engine.EngineId;
import com.butchercraft.product.definition.ProductDefinition;
import com.butchercraft.product.definition.ProductPackagingMetadata;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonical serializer for the stable product definition schema contract.
 */
public final class CanonicalProductDefinitionSerializer
        implements ProductDefinitionSerializer<SerializedProductDefinition> {
    @Override
    public SerializedProductDefinition serialize(ProductDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return new SerializedProductDefinition(
                new ProductSchemaVersion(definition.schemaVersion()),
                definition.id().value(),
                definition.displayName(),
                definition.category().id().value(),
                definition.defaultQuantityUnit().id(),
                definition.tags().stream()
                        .map(EngineId::value)
                        .toList(),
                packaging(definition.packagingMetadata()),
                metadata(definition.metadata())
        );
    }

    private static Optional<SerializedProductPackagingMetadata> packaging(
            Optional<ProductPackagingMetadata> packagingMetadata
    ) {
        return packagingMetadata.map(metadata -> new SerializedProductPackagingMetadata(
                metadata.packagingDefinitionId().value(),
                metadata.sourceProductId().value()
        ));
    }

    private static Map<String, String> metadata(Map<EngineId, String> metadata) {
        LinkedHashMap<String, String> serialized = new LinkedHashMap<>();
        for (var entry : metadata.entrySet()) {
            serialized.put(entry.getKey().value(), entry.getValue());
        }
        return serialized;
    }
}
