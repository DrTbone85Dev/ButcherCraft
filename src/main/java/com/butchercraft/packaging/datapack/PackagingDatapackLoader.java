package com.butchercraft.packaging.datapack;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.packaging.definition.PackagingDefinition;
import com.butchercraft.packaging.definition.PackagingFormat;
import com.butchercraft.packaging.definition.PackagingRegistry;
import com.butchercraft.packaging.definition.PackagingRegistryBuilder;
import com.butchercraft.packaging.serialization.CanonicalPackagingDefinitionDeserializer;
import com.butchercraft.packaging.serialization.PackagingSchemaVersion;
import com.butchercraft.packaging.serialization.SerializedPackagingDefinition;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Assembles immutable packaging registries from datapack JSON content.
 */
public final class PackagingDatapackLoader {
    private final PackagingJsonDefinitionParser parser;
    private final CanonicalPackagingDefinitionDeserializer deserializer;
    private final Set<EngineId> knownCategories;
    private final Set<EngineId> knownSupplyItems;

    public PackagingDatapackLoader(Set<EngineId> knownCategories) {
        this(knownCategories, Set.of());
    }

    public PackagingDatapackLoader(Set<EngineId> knownCategories, Set<EngineId> knownSupplyItems) {
        this(new PackagingJsonDefinitionParser(), new CanonicalPackagingDefinitionDeserializer(), knownCategories,
                knownSupplyItems);
    }

    PackagingDatapackLoader(
            PackagingJsonDefinitionParser parser,
            CanonicalPackagingDefinitionDeserializer deserializer,
            Set<EngineId> knownCategories,
            Set<EngineId> knownSupplyItems
    ) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.deserializer = Objects.requireNonNull(deserializer, "deserializer");
        this.knownCategories = Set.copyOf(Objects.requireNonNull(knownCategories, "knownCategories"));
        this.knownSupplyItems = Set.copyOf(Objects.requireNonNull(knownSupplyItems, "knownSupplyItems"));
    }

    public PackagingDatapackLoadResult load(Map<String, JsonElement> resources) {
        Objects.requireNonNull(resources, "resources");
        List<PackagingDatapackValidationError> errors = new ArrayList<>();
        LinkedHashMap<String, SerializedPackagingDefinition> serializedBySource = parse(resources, errors);
        errors.addAll(duplicateIdErrors(serializedBySource));

        LinkedHashMap<String, PackagingDefinition> definitions = new LinkedHashMap<>();
        for (var entry : serializedBySource.entrySet()) {
            String source = entry.getKey();
            SerializedPackagingDefinition serialized = entry.getValue();
            if (!serialized.schemaVersion().equals(PackagingSchemaVersion.CURRENT)) {
                errors.add(PackagingDatapackValidationError.of(
                        source,
                        serialized.id(),
                        PackagingDatapackErrorCode.UNSUPPORTED_SCHEMA_VERSION,
                        "Unsupported packaging schema version: " + serialized.schemaVersion().value()
                ));
                continue;
            }

            validateFormat(source, serialized, errors);
            validateQuantityUnit(source, serialized, errors);
            validateRequiredSupplyItems(source, serialized, errors);
            validateCategories(source, serialized, errors);
            validateTags(source, serialized, errors);
            validateMetadata(source, serialized, errors);
            if (hasDefinitionBlockingError(source, errors)) {
                continue;
            }

            try {
                definitions.put(source, deserializer.deserialize(serialized));
            } catch (RuntimeException exception) {
                errors.add(PackagingDatapackValidationError.of(
                        source,
                        serialized.id(),
                        PackagingDatapackErrorCode.MALFORMED_DEFINITION,
                        exception.getMessage()
                ));
            }
        }

        if (!errors.isEmpty()) {
            return PackagingDatapackLoadResult.failure(errors);
        }

        PackagingRegistryBuilder builder = PackagingRegistry.builder();
        definitions.values().forEach(builder::register);
        return PackagingDatapackLoadResult.success(builder.build());
    }

    private LinkedHashMap<String, SerializedPackagingDefinition> parse(
            Map<String, JsonElement> resources,
            List<PackagingDatapackValidationError> errors
    ) {
        LinkedHashMap<String, SerializedPackagingDefinition> parsed = new LinkedHashMap<>();
        for (var entry : resources.entrySet()) {
            String source = Objects.requireNonNull(entry.getKey(), "resource source");
            try {
                parsed.put(source, parser.parse(entry.getValue()));
            } catch (PackagingDatapackParsingException exception) {
                errors.add(PackagingDatapackValidationError.withoutId(source, exception.code(), exception.getMessage()));
            } catch (RuntimeException exception) {
                errors.add(PackagingDatapackValidationError.withoutId(
                        source,
                        PackagingDatapackErrorCode.MALFORMED_JSON,
                        exception.getMessage()
                ));
            }
        }
        return parsed;
    }

    private static void validateFormat(
            String source,
            SerializedPackagingDefinition serialized,
            List<PackagingDatapackValidationError> errors
    ) {
        try {
            PackagingFormat.fromId(serialized.format());
        } catch (RuntimeException exception) {
            errors.add(PackagingDatapackValidationError.of(
                    source,
                    serialized.id(),
                    PackagingDatapackErrorCode.UNKNOWN_FORMAT,
                    exception.getMessage()
            ));
        }
    }

    private static void validateQuantityUnit(
            String source,
            SerializedPackagingDefinition serialized,
            List<PackagingDatapackValidationError> errors
    ) {
        try {
            QuantityUnit.fromId(serialized.defaultQuantityUnit());
        } catch (RuntimeException exception) {
            errors.add(PackagingDatapackValidationError.of(
                    source,
                    serialized.id(),
                    PackagingDatapackErrorCode.UNKNOWN_QUANTITY_UNIT,
                    exception.getMessage()
            ));
        }
    }

    private void validateRequiredSupplyItems(
            String source,
            SerializedPackagingDefinition serialized,
            List<PackagingDatapackValidationError> errors
    ) {
        for (String supplyItemValue : serialized.requiredSupplyItems()) {
            EngineId supplyItemId;
            try {
                supplyItemId = EngineId.of(supplyItemValue);
            } catch (RuntimeException exception) {
                errors.add(PackagingDatapackValidationError.of(
                        source,
                        serialized.id(),
                        PackagingDatapackErrorCode.MALFORMED_REQUIRED_SUPPLIES,
                        exception.getMessage()
                ));
                continue;
            }
            if (!knownSupplyItems.contains(supplyItemId)) {
                errors.add(PackagingDatapackValidationError.of(
                        source,
                        serialized.id(),
                        PackagingDatapackErrorCode.UNKNOWN_SUPPLY_ITEM,
                        "Unknown packaging supply item " + supplyItemId.value()
                ));
            }
        }
    }

    private void validateCategories(
            String source,
            SerializedPackagingDefinition serialized,
            List<PackagingDatapackValidationError> errors
    ) {
        for (String categoryValue : serialized.compatibleCategories()) {
            EngineId category;
            try {
                category = EngineId.of(categoryValue);
            } catch (RuntimeException exception) {
                errors.add(PackagingDatapackValidationError.of(
                        source,
                        serialized.id(),
                        PackagingDatapackErrorCode.MALFORMED_CATEGORIES,
                        exception.getMessage()
                ));
                continue;
            }
            if (!knownCategories.contains(category)) {
                errors.add(PackagingDatapackValidationError.of(
                        source,
                        serialized.id(),
                        PackagingDatapackErrorCode.UNKNOWN_CATEGORY,
                        "Unknown product category " + category.value()
                ));
            }
        }
    }

    private static void validateTags(
            String source,
            SerializedPackagingDefinition serialized,
            List<PackagingDatapackValidationError> errors
    ) {
        for (String tag : serialized.compatibleTags()) {
            try {
                EngineId.of(tag);
            } catch (RuntimeException exception) {
                errors.add(PackagingDatapackValidationError.of(
                        source,
                        serialized.id(),
                        PackagingDatapackErrorCode.MALFORMED_TAGS,
                        exception.getMessage()
                ));
            }
        }
    }

    private static void validateMetadata(
            String source,
            SerializedPackagingDefinition serialized,
            List<PackagingDatapackValidationError> errors
    ) {
        for (String key : serialized.metadata().keySet()) {
            try {
                EngineId.of(key);
            } catch (RuntimeException exception) {
                errors.add(PackagingDatapackValidationError.of(
                        source,
                        serialized.id(),
                        PackagingDatapackErrorCode.MALFORMED_METADATA,
                        exception.getMessage()
                ));
            }
        }
    }

    private static boolean hasDefinitionBlockingError(String source, List<PackagingDatapackValidationError> errors) {
        return errors.stream().anyMatch(error -> error.source().equals(source));
    }

    private static List<PackagingDatapackValidationError> duplicateIdErrors(
            LinkedHashMap<String, SerializedPackagingDefinition> serializedBySource
    ) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        serializedBySource.values().stream()
                .map(SerializedPackagingDefinition::id)
                .forEach(id -> {
                    if (!seen.add(id)) {
                        duplicates.add(id);
                    }
                });

        if (duplicates.isEmpty()) {
            return List.of();
        }

        List<PackagingDatapackValidationError> errors = new ArrayList<>();
        serializedBySource.forEach((source, definition) -> {
            if (duplicates.contains(definition.id())) {
                errors.add(PackagingDatapackValidationError.of(
                        source,
                        definition.id(),
                        PackagingDatapackErrorCode.DUPLICATE_ID,
                        "Duplicate packaging id " + definition.id()
                ));
            }
        });
        return errors;
    }
}
