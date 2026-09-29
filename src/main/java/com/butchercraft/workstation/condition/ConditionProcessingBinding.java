package com.butchercraft.workstation.condition;

import com.butchercraft.world.execution.ExecutionOperationId;
import java.util.Objects;

/** Exact frozen inventory/result publication coordinates, not independent application authority. */
public record ConditionProcessingBinding(ExecutionOperationId operation, String postInventoryDigest,
        long plannedProjectionRevision) {
    public ConditionProcessingBinding {
        Objects.requireNonNull(operation, "operation");
        if (postInventoryDigest == null || !postInventoryDigest.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid processing inventory digest");
        }
        if (plannedProjectionRevision <= 0) throw new IllegalArgumentException("Invalid planned projection revision");
    }

    public String identity() {
        return ConditionDigest.identity("butchercraft:condition_processing_binding/v1", operation.value(),
                postInventoryDigest, Long.toString(plannedProjectionRevision));
    }
}
