package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.operation.ProcessingDuration;
import com.butchercraft.engine.quantity.ProductQuantity;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable definition of a deterministic material transformation.
 */
public record TransformationDefinition(
        TransformationId id,
        List<TransformationInput> inputs,
        List<TransformationOutput> outputs,
        ProcessingDuration duration,
        Optional<EngineId> workstationCapability
) {
    public TransformationDefinition {
        Objects.requireNonNull(id, "id");
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("Transformation must define at least one input");
        }
        outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("Transformation must define at least one output");
        }
        Objects.requireNonNull(duration, "duration");
        workstationCapability = Objects.requireNonNull(workstationCapability, "workstationCapability");
        workstationCapability.ifPresent(capability -> Objects.requireNonNull(capability, "workstationCapability value"));

        validateNoDuplicateInputs(inputs);
        validateNoDuplicateOutputs(outputs);
    }

    public TransformationDefinition withInputQuantity(ProductQuantity inputQuantity) {
        Objects.requireNonNull(inputQuantity, "inputQuantity");
        if (inputs.size() != 1) {
            throw new IllegalStateException("Only single-input transformation rebasing is supported");
        }

        TransformationInput input = inputs.getFirst();
        ProductQuantity basisQuantity = input.requiredAmount().quantity();
        if (basisQuantity.unit() != inputQuantity.unit()) {
            throw new IllegalArgumentException("Rebased input quantity must use the definition input unit");
        }

        List<TransformationOutput> rebasedOutputs = outputs.stream()
                .map(output -> new TransformationOutput(
                        new MaterialAmount(
                                output.producedAmount().materialId(),
                                scaleQuantity(output.producedAmount().quantity(), basisQuantity, inputQuantity)
                        ),
                        output.classification()
                ))
                .toList();

        return new TransformationDefinition(
                id,
                List.of(new TransformationInput(new MaterialAmount(input.requiredAmount().materialId(), inputQuantity))),
                rebasedOutputs,
                duration,
                workstationCapability
        );
    }

    private static void validateNoDuplicateInputs(List<TransformationInput> inputs) {
        Set<EngineId> seenMaterials = new HashSet<>();
        for (TransformationInput input : inputs) {
            EngineId materialId = input.requiredAmount().materialId();
            if (!seenMaterials.add(materialId)) {
                throw new IllegalArgumentException("Duplicate transformation input material: " + materialId.value());
            }
        }
    }

    private static void validateNoDuplicateOutputs(List<TransformationOutput> outputs) {
        Set<EngineId> seenMaterials = new HashSet<>();
        for (TransformationOutput output : outputs) {
            EngineId materialId = output.producedAmount().materialId();
            if (!seenMaterials.add(materialId)) {
                throw new IllegalArgumentException("Duplicate or contradictory transformation output material: " + materialId.value());
            }
        }
    }

    private static ProductQuantity scaleQuantity(
            ProductQuantity outputQuantity,
            ProductQuantity basisInputQuantity,
            ProductQuantity targetInputQuantity
    ) {
        if (outputQuantity.unit() != basisInputQuantity.unit() || targetInputQuantity.unit() != basisInputQuantity.unit()) {
            throw new IllegalArgumentException("Transformation quantities must use matching units to be rebased");
        }

        BigInteger raw = BigInteger.valueOf(targetInputQuantity.amount()).multiply(BigInteger.valueOf(outputQuantity.amount()));
        BigInteger divisor = BigInteger.valueOf(basisInputQuantity.amount());
        BigInteger[] divided = raw.divideAndRemainder(divisor);
        BigInteger rounded = divided[0];
        if (divided[1].multiply(BigInteger.TWO).compareTo(divisor) >= 0) {
            rounded = rounded.add(BigInteger.ONE);
        }
        if (rounded.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            throw new ArithmeticException("Rebased transformation quantity exceeds supported range");
        }
        return new ProductQuantity(rounded.longValueExact(), outputQuantity.unit());
    }
}
