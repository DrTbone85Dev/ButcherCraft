package com.butchercraft.world.execution;

public record ExecutionRuntimeConfiguration(
        int maximumActiveOperations,
        int maximumRetainedTerminalOperations,
        int maximumAttemptsPerOperation,
        int maximumPendingOwnerResults,
        String configurationIdentity
) {
    public ExecutionRuntimeConfiguration {
        maximumActiveOperations = ExecutionValidation.requirePositive(
                maximumActiveOperations,
                "Maximum active Execution operations"
        );
        maximumRetainedTerminalOperations = ExecutionValidation.requirePositive(
                maximumRetainedTerminalOperations,
                "Maximum retained terminal Execution operations"
        );
        maximumAttemptsPerOperation = ExecutionValidation.requirePositive(
                maximumAttemptsPerOperation,
                "Maximum Execution attempts"
        );
        maximumPendingOwnerResults = ExecutionValidation.requirePositive(
                maximumPendingOwnerResults,
                "Maximum pending owner results"
        );
        configurationIdentity = ExecutionValidation.requireId(
                configurationIdentity,
                "Execution runtime configuration identity"
        );
    }

    public static ExecutionRuntimeConfiguration standard() {
        return new ExecutionRuntimeConfiguration(
                1_024,
                8_192,
                3,
                1_024,
                "butchercraft:execution_runtime_configuration/v1/standard"
        );
    }
}
