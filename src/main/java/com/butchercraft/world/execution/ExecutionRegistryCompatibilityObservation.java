package com.butchercraft.world.execution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ExecutionRegistryCompatibilityObservation(
        int executionSchemaVersion,
        String compatibilityPolicyIdentity,
        String persistedRegistryIdentity,
        String currentRegistryIdentity,
        ExecutionRegistryCompatibilityClassification classification,
        Optional<String> historicalProfileIdentity,
        int historicalHandlerCount,
        int currentHandlerCount,
        List<String> addedHandlerIds,
        List<String> missingHandlerIds,
        List<String> incompatibleContractHandlerIds,
        int retainedOperationCount,
        ExecutionRetainedOperationValidation retainedOperationValidation,
        List<ExecutionRegistryCompatibilityFailure> failures
) {
    public ExecutionRegistryCompatibilityObservation {
        executionSchemaVersion = ExecutionValidation.requireSchema(
                executionSchemaVersion,
                "Execution registry compatibility observation"
        );
        compatibilityPolicyIdentity = ExecutionValidation.requireId(
                compatibilityPolicyIdentity,
                "Execution registry compatibility policy identity"
        );
        persistedRegistryIdentity = ExecutionValidation.requireId(
                persistedRegistryIdentity,
                "Persisted Execution registry identity"
        );
        currentRegistryIdentity = ExecutionValidation.requireId(
                currentRegistryIdentity,
                "Current Execution registry identity"
        );
        classification = Objects.requireNonNull(classification, "classification");
        historicalProfileIdentity = Objects.requireNonNull(historicalProfileIdentity, "historicalProfileIdentity")
                .map(value -> ExecutionValidation.requireId(value, "Execution historical profile identity"));
        if (historicalHandlerCount < 0 || currentHandlerCount < 0 || retainedOperationCount < 0) {
            throw new IllegalArgumentException("Execution compatibility counts must not be negative");
        }
        addedHandlerIds = canonicalIds(addedHandlerIds, "added handler id");
        missingHandlerIds = canonicalIds(missingHandlerIds, "missing handler id");
        incompatibleContractHandlerIds = canonicalIds(
                incompatibleContractHandlerIds,
                "incompatible handler id"
        );
        retainedOperationValidation = Objects.requireNonNull(
                retainedOperationValidation,
                "retainedOperationValidation"
        );
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        if (classification.permitsExecutionAuthority() && !failures.isEmpty()) {
            throw new IllegalArgumentException("Compatible Execution registry observation cannot contain failures");
        }
        if (classification.permitsExecutionAuthority()
                && retainedOperationValidation != ExecutionRetainedOperationValidation.VALID) {
            throw new IllegalArgumentException("Compatible Execution registry observation requires valid operations");
        }
    }

    public boolean permitsExecutionAuthority() {
        return classification.permitsExecutionAuthority();
    }

    public boolean operatorRecoveryRequired() {
        return classification.operatorRecoveryRequired();
    }

    public String diagnosticSummary() {
        return "classification=" + classification
                + ", persisted_registry=" + persistedRegistryIdentity
                + ", current_registry=" + currentRegistryIdentity
                + ", profile=" + historicalProfileIdentity.orElse("none")
                + ", historical_handlers=" + historicalHandlerCount
                + ", current_handlers=" + currentHandlerCount
                + ", added_handlers=" + addedHandlerIds
                + ", missing_handlers=" + missingHandlerIds
                + ", incompatible_contracts=" + incompatibleContractHandlerIds
                + ", retained_operations=" + retainedOperationCount
                + ", retained_operation_validation=" + retainedOperationValidation
                + ", operator_recovery_required=" + operatorRecoveryRequired()
                + ", failures=" + failures;
    }

    private static List<String> canonicalIds(List<String> values, String label) {
        return Objects.requireNonNull(values, label).stream()
                .map(value -> ExecutionValidation.requireId(value, label))
                .distinct()
                .sorted()
                .toList();
    }
}
