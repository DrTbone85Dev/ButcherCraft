package com.butchercraft.workstation;

import com.butchercraft.engine.result.OperationResult;

public interface WorkstationExecutionStrategy {
    OperationResult prepare(WorkstationCapability capability, ResolvedWorkstationOperation operation);

    OperationResult commit(WorkstationCapability capability, ResolvedWorkstationOperation operation);

    static WorkstationExecutionStrategy legacy() {
        return LegacyWorkstationExecutionStrategy.INSTANCE;
    }

    static WorkstationExecutionStrategy transformation() {
        return TransformationWorkstationExecutionStrategy.INSTANCE;
    }
}
