package com.butchercraft.workstation.endpoint.runtime;

import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;

import java.util.Objects;

public record WorkstationEndpointCancellationResult(
        boolean succeeded,
        WorkstationEndpointResultCode code,
        String detail
) {
    public WorkstationEndpointCancellationResult {
        code = Objects.requireNonNull(code, "code");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public static WorkstationEndpointCancellationResult cancelled() {
        return new WorkstationEndpointCancellationResult(
                true,
                WorkstationEndpointResultCode.APPLIED,
                "Prepared endpoint effect cancelled"
        );
    }

    public static WorkstationEndpointCancellationResult failed(
            WorkstationEndpointResultCode code,
            String detail
    ) {
        return new WorkstationEndpointCancellationResult(false, code, detail);
    }
}
