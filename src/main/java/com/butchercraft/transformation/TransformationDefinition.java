package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.operation.ProcessingDuration;

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
}
