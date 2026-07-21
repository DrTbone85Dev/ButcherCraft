package com.butchercraft.product.datapack;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.product.definition.ProductDefinition;
import com.butchercraft.product.definition.ProductRegistry;
import com.butchercraft.product.definition.ProductRegistryBuilder;
import com.butchercraft.product.serialization.CanonicalProductDefinitionDeserializer;
import com.butchercraft.product.serialization.ProductSchemaVersion;
import com.butchercraft.product.serialization.SerializedProductDefinition;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Assembles immutable product registries from datapack JSON content.
 */
public final class ProductDatapackLoader {
    private final ProductJsonDefinitionParser parser;
    private final CanonicalProductDefinitionDeserializer deserializer;
    private final Set<EngineId> knownCategories;

    public ProductDatapackLoader(Set<EngineId> knownCategories) {
        this(new ProductJsonDefinitionParser(), new CanonicalProductDefinitionDeserializer(), knownCategories);
    }

    ProductDatapackLoader(
            ProductJsonDefinitionParser parser,
            CanonicalProductDefinitionDeserializer deserializer,
            Set<EngineId> knownCategories
    ) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.deserializer = Objects.requireNonNull(deserializer, "deserializer");
        this.knownCategories = Set.copyOf(Objects.requireNonNull(knownCategories, "knownCategories"));
    }

    public ProductDatapackLoadResult load(Map<String, JsonElement> resources) {
        Objects.requireNonNull(resources, "resources");
        List<ProductDatapackValidationError> errors = new ArrayList<>();
        LinkedHashMap<String, SerializedProductDefinition> serializedBySource = parse(resources, errors);
        errors.addAll(duplicateIdErrors(serializedBySource));

        LinkedHashMap<String, ProductDefinition> definitions = new LinkedHashMap<>();
        for (var entry : serializedBySource.entrySet()) {
            String source = entry.getKey();
            SerializedProductDefinition serialized = entry.getValue();
            if (!serialized.schemaVersion().equals(ProductSchemaVersion.CURRENT)) {
                errors.add(ProductDatapackValidationError.of(
                        source,
                        serialized.id(),
                        ProductDatapackErrorCode.UNSUPPORTED_SCHEMA_VERSION,
                        "Unsupported product schema version: " + serialized.schemaVersion().value()
                ));
                continue;
            }

            validateCategory(source, serialized, errors);
            validateQuantityUnit(source, serialized, errors);
            validateTags(source, serialized, errors);
            validateMetadata(source, serialized, errors);
            if (hasDefinitionBlockingError(source, errors)) {
                continue;
            }

            try {
                definitions.put(source, deserializer.deserialize(serialized));
            } catch (RuntimeException exception) {
                errors.add(ProductDatapackValidationError.of(
                        source,
                        serialized.id(),
                        ProductDatapackErrorCode.MALFORMED_DEFINITION,
                        exception.getMessage()
                ));
            }
        }

        if (!errors.isEmpty()) {
            return ProductDatapackLoadResult.failure(errors);
        }

        ProductRegistryBuilder builder = ProductRegistry.builder();
        definitions.values().forEach(builder::register);
        return ProductDatapackLoadResult.success(builder.build());
    }

    private LinkedHashMap<String, SerializedProductDefinition> parse(
            Map<String, JsonElement> resources,
            List<ProductDatapackValidationError> errors
    ) {
        LinkedHashMap<String, SerializedProductDefinition> parsed = new LinkedHashMap<>();
        for (var entry : resources.entrySet()) {
            String source = Objects.requireNonNull(entry.getKey(), "resource source");
            try {
                parsed.put(source, parser.parse(entry.getValue()));
            } catch (ProductDatapackParsingException exception) {
                errors.add(ProductDatapackValidationError.withoutId(source, exception.code(), exception.getMessage()));
            } catch (RuntimeException exception) {
                errors.add(ProductDatapackValidationError.withoutId(
                        source,
                        ProductDatapackErrorCode.MALFORMED_JSON,
                        exception.getMessage()
                ));
            }
        }
        return parsed;
    }

    private void validateCategory(
            String source,
            SerializedProductDefinition serialized,
            List<ProductDatapackValidationError> errors
    ) {
        EngineId category;
        try {
            category = EngineId.of(serialized.category());
        } catch (RuntimeException exception) {
            errors.add(ProductDatapackValidationError.of(
                    source,
                    serialized.id(),
                    ProductDatapackErrorCode.UNKNOWN_CATEGORY,
                    exception.getMessage()
            ));
            return;
        }
        if (!knownCategories.contains(category)) {
            errors.add(ProductDatapackValidationError.of(
                    source,
                    serialized.id(),
                    ProductDatapackErrorCode.UNKNOWN_CATEGORY,
                    "Unknown product category " + category.value()
            ));
        }
    }

    private static void validateQuantityUnit(
            String source,
            SerializedProductDefinition serialized,
            List<ProductDatapackValidationError> errors
    ) {
        try {
            QuantityUnit.fromId(serialized.defaultQuantityUnit());
        } catch (RuntimeException exception) {
            errors.add(ProductDatapackValidationError.of(
                    source,
                    serialized.id(),
                    ProductDatapackErrorCode.UNKNOWN_QUANTITY_UNIT,
                    exception.getMessage()
            ));
        }
    }

    private static void validateTags(
            String source,
            SerializedProductDefinition serialized,
            List<ProductDatapackValidationError> errors
    ) {
        for (String tag : serialized.tags()) {
            try {
                EngineId.of(tag);
            } catch (RuntimeException exception) {
                errors.add(ProductDatapackValidationError.of(
                        source,
                        serialized.id(),
                        ProductDatapackErrorCode.MALFORMED_TAGS,
                        exception.getMessage()
                ));
            }
        }
    }

    private static void validateMetadata(
            String source,
            SerializedProductDefinition serialized,
            List<ProductDatapackValidationError> errors
    ) {
        for (String key : serialized.metadata().keySet()) {
            try {
                EngineId.of(key);
            } catch (RuntimeException exception) {
                errors.add(ProductDatapackValidationError.of(
                        source,
                        serialized.id(),
                        ProductDatapackErrorCode.MALFORMED_METADATA,
                        exception.getMessage()
                ));
            }
        }
    }

    private static boolean hasDefinitionBlockingError(String source, List<ProductDatapackValidationError> errors) {
        return errors.stream().anyMatch(error -> error.source().equals(source));
    }

    private static List<ProductDatapackValidationError> duplicateIdErrors(
            LinkedHashMap<String, SerializedProductDefinition> serializedBySource
    ) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        serializedBySource.values().stream()
                .map(SerializedProductDefinition::id)
                .forEach(id -> {
                    if (!seen.add(id)) {
                        duplicates.add(id);
                    }
                });

        if (duplicates.isEmpty()) {
            return List.of();
        }

        List<ProductDatapackValidationError> errors = new ArrayList<>();
        serializedBySource.forEach((source, definition) -> {
            if (duplicates.contains(definition.id())) {
                errors.add(ProductDatapackValidationError.of(
                        source,
                        definition.id(),
                        ProductDatapackErrorCode.DUPLICATE_ID,
                        "Duplicate product id " + definition.id()
                ));
            }
        });
        return errors;
    }
}
