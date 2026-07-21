package com.butchercraft.transformation.datapack;

import com.butchercraft.engine.EngineId;
import com.butchercraft.product.definition.ProductRegistry;
import com.butchercraft.transformation.TransformationDefinition;
import com.butchercraft.transformation.TransformationProductReferenceValidator;
import com.butchercraft.transformation.TransformationRegistry;
import com.butchercraft.transformation.TransformationRegistryBuilder;
import com.butchercraft.transformation.serialization.CanonicalTransformationDefinitionDeserializer;
import com.butchercraft.transformation.serialization.SerializedTransformationDefinition;
import com.butchercraft.transformation.serialization.TransformationSchemaVersion;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Assembles immutable transformation registries from datapack JSON content.
 */
public final class TransformationDatapackLoader {
    private final TransformationJsonDefinitionParser parser;
    private final CanonicalTransformationDefinitionDeserializer deserializer;
    private final ProductRegistry products;
    private final Set<EngineId> knownCapabilities;

    public TransformationDatapackLoader(ProductRegistry products, Set<EngineId> knownCapabilities) {
        this(new TransformationJsonDefinitionParser(), new CanonicalTransformationDefinitionDeserializer(), products, knownCapabilities);
    }

    TransformationDatapackLoader(
            TransformationJsonDefinitionParser parser,
            CanonicalTransformationDefinitionDeserializer deserializer,
            ProductRegistry products,
            Set<EngineId> knownCapabilities
    ) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.deserializer = Objects.requireNonNull(deserializer, "deserializer");
        this.products = Objects.requireNonNull(products, "products");
        this.knownCapabilities = Set.copyOf(Objects.requireNonNull(knownCapabilities, "knownCapabilities"));
    }

    public TransformationDatapackLoadResult load(Map<String, JsonElement> resources) {
        Objects.requireNonNull(resources, "resources");
        List<TransformationDatapackValidationError> errors = new ArrayList<>();
        LinkedHashMap<String, SerializedTransformationDefinition> serializedBySource = parse(resources, errors);
        errors.addAll(duplicateIdErrors(serializedBySource));

        LinkedHashMap<String, TransformationDefinition> definitions = new LinkedHashMap<>();
        for (var entry : serializedBySource.entrySet()) {
            String source = entry.getKey();
            SerializedTransformationDefinition serialized = entry.getValue();
            if (!serialized.schemaVersion().equals(TransformationSchemaVersion.CURRENT)) {
                errors.add(TransformationDatapackValidationError.of(
                        source,
                        serialized.id(),
                        TransformationDatapackErrorCode.UNSUPPORTED_SCHEMA_VERSION,
                        "Unsupported transformation schema version: " + serialized.schemaVersion().value()
                ));
                continue;
            }

            TransformationDefinition definition;
            try {
                definition = deserializer.deserialize(serialized);
            } catch (RuntimeException exception) {
                errors.add(TransformationDatapackValidationError.of(
                        source,
                        serialized.id(),
                        TransformationDatapackErrorCode.MALFORMED_DEFINITION,
                        exception.getMessage()
                ));
                continue;
            }

            definition.requiredCapability()
                    .filter(capability -> !knownCapabilities.contains(capability))
                    .ifPresent(capability -> errors.add(TransformationDatapackValidationError.of(
                            source,
                            definition.id().value(),
                            TransformationDatapackErrorCode.UNKNOWN_CAPABILITY,
                            "Unknown workstation capability " + capability.value()
                    )));

            var productReport = TransformationProductReferenceValidator.validate(definition, products);
            productReport.issues().forEach(issue -> errors.add(TransformationDatapackValidationError.of(
                    source,
                    definition.id().value(),
                    issue.code().startsWith("missing_")
                            ? TransformationDatapackErrorCode.UNKNOWN_PRODUCT
                            : TransformationDatapackErrorCode.MALFORMED_DEFINITION,
                    issue.message()
            )));

            definitions.put(source, definition);
        }

        if (!errors.isEmpty()) {
            return TransformationDatapackLoadResult.failure(errors);
        }

        TransformationRegistryBuilder builder = TransformationRegistry.builder();
        definitions.values().forEach(builder::register);
        return TransformationDatapackLoadResult.success(builder.build());
    }

    private LinkedHashMap<String, SerializedTransformationDefinition> parse(
            Map<String, JsonElement> resources,
            List<TransformationDatapackValidationError> errors
    ) {
        LinkedHashMap<String, SerializedTransformationDefinition> parsed = new LinkedHashMap<>();
        for (var entry : resources.entrySet()) {
            String source = Objects.requireNonNull(entry.getKey(), "resource source");
            try {
                parsed.put(source, parser.parse(entry.getValue()));
            } catch (RuntimeException exception) {
                errors.add(TransformationDatapackValidationError.withoutId(
                        source,
                        TransformationDatapackErrorCode.MALFORMED_JSON,
                        exception.getMessage()
                ));
            }
        }
        return parsed;
    }

    private static List<TransformationDatapackValidationError> duplicateIdErrors(
            LinkedHashMap<String, SerializedTransformationDefinition> serializedBySource
    ) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        serializedBySource.values().stream()
                .map(SerializedTransformationDefinition::id)
                .forEach(id -> {
                    if (!seen.add(id)) {
                        duplicates.add(id);
                    }
                });

        if (duplicates.isEmpty()) {
            return List.of();
        }

        List<TransformationDatapackValidationError> errors = new ArrayList<>();
        serializedBySource.forEach((source, definition) -> {
            if (duplicates.contains(definition.id())) {
                errors.add(TransformationDatapackValidationError.of(
                        source,
                        definition.id(),
                        TransformationDatapackErrorCode.DUPLICATE_ID,
                        "Duplicate transformation id " + definition.id()
                ));
            }
        });
        return errors;
    }
}
