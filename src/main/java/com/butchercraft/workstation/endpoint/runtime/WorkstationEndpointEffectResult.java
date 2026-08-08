package com.butchercraft.workstation.endpoint.runtime;

import com.butchercraft.workstation.endpoint.WorkstationEndpointOwnerResult;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;

import java.util.Objects;
import java.util.Optional;

public record WorkstationEndpointEffectResult(
        WorkstationEndpointResultCode code,
        Optional<WorkstationEndpointOwnerResult> ownerResult,
        String detail
) {
    public WorkstationEndpointEffectResult {
        code = Objects.requireNonNull(code, "code");
        ownerResult = Objects.requireNonNull(ownerResult, "ownerResult");
        detail = Objects.requireNonNull(detail, "detail");
        if (code == WorkstationEndpointResultCode.APPLIED && ownerResult.isEmpty()) {
            throw new IllegalArgumentException("Applied endpoint effect requires an owner result");
        }
    }

    public static WorkstationEndpointEffectResult applied(WorkstationEndpointOwnerResult result) {
        return new WorkstationEndpointEffectResult(
                WorkstationEndpointResultCode.APPLIED,
                Optional.of(result),
                "Workstation endpoint effect applied"
        );
    }

    public static WorkstationEndpointEffectResult duplicate(WorkstationEndpointOwnerResult result) {
        return new WorkstationEndpointEffectResult(
                WorkstationEndpointResultCode.DUPLICATE_OBSERVED,
                Optional.of(result),
                "Existing authoritative Workstation endpoint result observed"
        );
    }

    public static WorkstationEndpointEffectResult failed(WorkstationEndpointResultCode code, String detail) {
        return new WorkstationEndpointEffectResult(code, Optional.empty(), detail);
    }

    public boolean succeeded() {
        return code == WorkstationEndpointResultCode.APPLIED
                || code == WorkstationEndpointResultCode.DUPLICATE_OBSERVED;
    }
}
