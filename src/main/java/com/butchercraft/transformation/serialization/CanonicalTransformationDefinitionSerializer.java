package com.butchercraft.transformation.serialization;

import com.butchercraft.engine.EngineId;
import com.butchercraft.transformation.TransformationDefinition;
import com.butchercraft.transformation.TransformationInput;
import com.butchercraft.transformation.TransformationOutput;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical serializer for the stable v0.6.5 transformation schema contract.
 */
public final class CanonicalTransformationDefinitionSerializer
        implements TransformationDefinitionSerializer<SerializedTransformationDefinition> {
    @Override
    public SerializedTransformationDefinition serialize(TransformationDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return new SerializedTransformationDefinition(
                new TransformationSchemaVersion(definition.schemaVersion()),
                definition.id().value(),
                definition.displayName(),
                definition.requiredCapability().map(EngineId::value),
                definition.inputs().stream()
                        .map(CanonicalTransformationDefinitionSerializer::input)
                        .toList(),
                definition.outputs().stream()
                        .map(CanonicalTransformationDefinitionSerializer::output)
                        .toList(),
                new SerializedTransformationDuration(definition.duration().milliseconds()),
                new SerializedTransformationYield(definition.yield().numerator(), definition.yield().denominator()),
                metadata(definition.metadata())
        );
    }

    private static SerializedTransformationInput input(TransformationInput input) {
        return new SerializedTransformationInput(amount(input.requiredAmount().materialId().value(),
                input.requiredAmount().quantity().amount(),
                input.requiredAmount().quantity().unit().id()));
    }

    private static SerializedTransformationOutput output(TransformationOutput output) {
        return new SerializedTransformationOutput(
                amount(output.producedAmount().materialId().value(),
                        output.producedAmount().quantity().amount(),
                        output.producedAmount().quantity().unit().id()),
                output.classification().name().toLowerCase(Locale.ROOT)
        );
    }

    private static SerializedTransformationAmount amount(String productId, long quantity, String unit) {
        return new SerializedTransformationAmount(productId, quantity, unit);
    }

    private static Map<String, String> metadata(Map<EngineId, String> metadata) {
        LinkedHashMap<String, String> serialized = new LinkedHashMap<>();
        for (var entry : metadata.entrySet()) {
            serialized.put(entry.getKey().value(), entry.getValue());
        }
        return serialized;
    }
}
