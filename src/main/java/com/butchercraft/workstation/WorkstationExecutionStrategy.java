package com.butchercraft.workstation;

import com.butchercraft.engine.result.OperationResult;
import com.butchercraft.transformation.TransformationRegistry;

public interface WorkstationExecutionStrategy {
    OperationResult prepare(WorkstationCapability capability, ResolvedWorkstationOperation operation);

    OperationResult commit(WorkstationCapability capability, ResolvedWorkstationOperation operation);

    static WorkstationExecutionStrategy legacy() {
        return LegacyWorkstationExecutionStrategy.INSTANCE;
    }

    static WorkstationExecutionStrategy transformation() {
        return TransformationWorkstationExecutionStrategy.INSTANCE;
    }

    static WorkstationExecutionStrategy transformation(TransformationRegistry registry) {
        return new TransformationWorkstationExecutionStrategy(registry);
    }
}
