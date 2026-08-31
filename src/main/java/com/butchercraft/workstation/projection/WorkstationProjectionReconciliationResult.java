package com.butchercraft.workstation.projection;

import java.util.Objects;
import java.util.Optional;

public record WorkstationProjectionReconciliationResult(
        WorkstationProjectionReconciliationCode code,
        Optional<DurableWorkstationProjection> projection,
        String detail
) {
    public WorkstationProjectionReconciliationResult {
        code = Objects.requireNonNull(code, "code");
        projection = Objects.requireNonNull(projection, "projection");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public boolean succeeded() {
        return code != WorkstationProjectionReconciliationCode.RECOVERY_REQUIRED
                && code != WorkstationProjectionReconciliationCode.IDENTITY_CONFLICT;
    }
}
