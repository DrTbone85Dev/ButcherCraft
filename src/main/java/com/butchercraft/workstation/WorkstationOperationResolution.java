package com.butchercraft.workstation;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record WorkstationOperationResolution(
        Optional<ResolvedWorkstationOperation> operation,
        Optional<WorkstationFailure> failure,
        List<ResourceLocation> compatibleOperationIds
) {
    public WorkstationOperationResolution {
        operation = Objects.requireNonNull(operation, "operation");
        failure = Objects.requireNonNull(failure, "failure");
        compatibleOperationIds = List.copyOf(Objects.requireNonNull(compatibleOperationIds, "compatibleOperationIds"));
        if (operation.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("Resolution must contain exactly one operation or failure");
        }
    }

    public static WorkstationOperationResolution success(ResolvedWorkstationOperation operation) {
        return new WorkstationOperationResolution(
                Optional.of(Objects.requireNonNull(operation, "operation")),
                Optional.empty(),
                List.of(operation.operationId())
        );
    }

    public static WorkstationOperationResolution failure(WorkstationFailure failure) {
        return new WorkstationOperationResolution(Optional.empty(), Optional.of(Objects.requireNonNull(failure, "failure")), List.of());
    }

    public static WorkstationOperationResolution multiple(List<ResourceLocation> compatibleOperationIds) {
        return new WorkstationOperationResolution(
                Optional.empty(),
                Optional.of(new WorkstationFailure(
                        WorkstationFailureCode.MULTIPLE_COMPATIBLE_OPERATIONS,
                        compatibleOperationIds,
                        "Multiple compatible operations require an explicit selection"
                )),
                compatibleOperationIds
        );
    }

    public boolean succeeded() {
        return operation.isPresent();
    }
}
