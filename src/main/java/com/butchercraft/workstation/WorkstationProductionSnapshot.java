package com.butchercraft.workstation;

import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.ExecutionOwnerResultEvidence;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

public record WorkstationProductionSnapshot(
        WorkstationState state,
        Optional<ResourceLocation> selectedOperationId,
        Optional<ExecutionOperationId> activeExecutionOperationId,
        Optional<ExecutionOwnerResultEvidence> ownerResultEvidence,
        Optional<WorkstationFailure> lastFailure
) {
    public WorkstationProductionSnapshot {
        state = Objects.requireNonNull(state, "state");
        selectedOperationId = Objects.requireNonNull(selectedOperationId, "selectedOperationId");
        activeExecutionOperationId = Objects.requireNonNull(activeExecutionOperationId, "activeExecutionOperationId");
        ownerResultEvidence = Objects.requireNonNull(ownerResultEvidence, "ownerResultEvidence");
        lastFailure = Objects.requireNonNull(lastFailure, "lastFailure");
    }
}
