package com.butchercraft.world.execution;

import java.util.Objects;

public record ExecutionHandlerContractDescriptor(
        int schemaVersion,
        String handlerId,
        String operationType,
        String contractIdentity,
        String configurationIdentity
) implements Comparable<ExecutionHandlerContractDescriptor> {
    public ExecutionHandlerContractDescriptor {
        schemaVersion = ExecutionValidation.requireSchema(schemaVersion, "Execution handler contract descriptor");
        handlerId = ExecutionValidation.requireId(handlerId, "Execution handler id");
        operationType = ExecutionValidation.requireId(operationType, "Execution operation type");
        contractIdentity = ExecutionValidation.requireId(contractIdentity, "Execution handler contract identity");
        configurationIdentity = ExecutionValidation.requireId(
                configurationIdentity,
                "Execution handler configuration identity"
        );
    }

    public static ExecutionHandlerContractDescriptor from(ExecutionHandlerContract contract) {
        Objects.requireNonNull(contract, "contract");
        return new ExecutionHandlerContractDescriptor(
                contract.schemaVersion(),
                contract.handlerId(),
                contract.operationType(),
                contract.contractIdentity(),
                contract.configurationIdentity()
        );
    }

    @Override
    public int compareTo(ExecutionHandlerContractDescriptor other) {
        return handlerId.compareTo(Objects.requireNonNull(other, "other").handlerId);
    }
}
