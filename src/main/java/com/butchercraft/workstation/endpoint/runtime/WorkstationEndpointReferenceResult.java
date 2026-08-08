package com.butchercraft.workstation.endpoint.runtime;

import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;

import java.util.Objects;
import java.util.Optional;

public record WorkstationEndpointReferenceResult(
        WorkstationEndpointResultCode code,
        Optional<WorkstationEndpointReference> reference,
        String detail
) {
    public WorkstationEndpointReferenceResult {
        code = Objects.requireNonNull(code, "code");
        reference = Objects.requireNonNull(reference, "reference");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public static WorkstationEndpointReferenceResult resolved(WorkstationEndpointReference reference) {
        return new WorkstationEndpointReferenceResult(
                WorkstationEndpointResultCode.APPLIED,
                Optional.of(reference),
                "Workstation endpoint reference resolved"
        );
    }

    public static WorkstationEndpointReferenceResult failed(WorkstationEndpointResultCode code, String detail) {
        return new WorkstationEndpointReferenceResult(code, Optional.empty(), detail);
    }

    public boolean succeeded() {
        return reference.isPresent();
    }
}
