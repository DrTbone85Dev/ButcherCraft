package com.butchercraft.transformation.serialization;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.operation.ProcessingDuration;
import com.butchercraft.engine.operation.YieldRatio;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.transformation.MaterialAmount;
import com.butchercraft.transformation.TransformationDefinition;
import com.butchercraft.transformation.TransformationInput;
import com.butchercraft.transformation.TransformationOutput;
import com.butchercraft.transformation.TransformationOutputClassification;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical deserializer for the stable v0.6.5 transformation schema contract.
 */
public final class CanonicalTransformationDefinitionDeserializer
        implements TransformationDefinitionDeserializer<SerializedTransformationDefinition> {
    @Override
    public TransformationDefinition deserialize(SerializedTransformationDefinition serialized) {
        Objects.requireNonNull(serialized, "serialized");
        if (!serialized.schemaVersion().equals(TransformationSchemaVersion.CURRENT)) {
            throw new IllegalArgumentException("Unsupported transformation schema version: " + serialized.schemaVersion().value());
        }

        TransformationDefinition.Builder builder = TransformationDefinition.builder()
                .id(serialized.id())
                .displayName(serialized.displayName())
                .schemaVersion(serialized.schemaVersion().value())
                .duration(ProcessingDuration.milliseconds(serialized.duration().milliseconds()))
                .yield(new YieldRatio(serialized.yield().numerator(), serialized.yield().denominator()))
                .metadata(metadata(serialized.metadata()));

        serialized.requiredCapability()
                .map(EngineId::of)
                .ifPresentOrElse(builder::requiredCapability, builder::noRequiredCapability);

        for (SerializedTransformationInput input : serialized.inputs()) {
            builder.input(input(input));
        }
        for (SerializedTransformationOutput output : serialized.outputs()) {
            builder.output(output(output));
        }
        return builder.build();
    }

    private static TransformationInput input(SerializedTransformationInput input) {
        return new TransformationInput(amount(input.amount()));
    }

    private static TransformationOutput output(SerializedTransformationOutput output) {
        return new TransformationOutput(amount(output.amount()), classification(output.classification()));
    }

    private static MaterialAmount amount(SerializedTransformationAmount amount) {
        return new MaterialAmount(
                EngineId.of(amount.productId()),
                new ProductQuantity(amount.quantity(), QuantityUnit.fromId(amount.unit()))
        );
    }

    private static TransformationOutputClassification classification(String value) {
        return TransformationOutputClassification.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static Map<EngineId, String> metadata(Map<String, String> metadata) {
        LinkedHashMap<EngineId, String> deserialized = new LinkedHashMap<>();
        for (var entry : metadata.entrySet()) {
            deserialized.put(EngineId.of(entry.getKey()), entry.getValue());
        }
        return deserialized;
    }
}
