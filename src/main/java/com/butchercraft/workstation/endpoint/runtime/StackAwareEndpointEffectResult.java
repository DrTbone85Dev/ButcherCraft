package com.butchercraft.workstation.endpoint.runtime;

import com.butchercraft.workstation.endpoint.WorkstationEndpointOwnerResultV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;

import java.util.Objects;
import java.util.Optional;

public record StackAwareEndpointEffectResult(
        WorkstationEndpointResultCode code,
        Optional<WorkstationEndpointOwnerResultV2> ownerResult,
        String detail
) {
    public StackAwareEndpointEffectResult {
        code = Objects.requireNonNull(code, "code");
        ownerResult = Objects.requireNonNull(ownerResult, "ownerResult");
        detail = Objects.requireNonNull(detail, "detail");
        boolean successful = code == WorkstationEndpointResultCode.APPLIED
                || code == WorkstationEndpointResultCode.DUPLICATE_OBSERVED;
        if (successful != ownerResult.isPresent()) {
            throw new IllegalArgumentException("Successful schema-2 effect result must contain owner evidence");
        }
    }

    public static StackAwareEndpointEffectResult applied(WorkstationEndpointOwnerResultV2 result) {
        return new StackAwareEndpointEffectResult(WorkstationEndpointResultCode.APPLIED, Optional.of(result), "applied");
    }

    public static StackAwareEndpointEffectResult duplicate(WorkstationEndpointOwnerResultV2 result) {
        return new StackAwareEndpointEffectResult(
                WorkstationEndpointResultCode.DUPLICATE_OBSERVED,
                Optional.of(result),
                "existing authoritative result observed"
        );
    }

    public static StackAwareEndpointEffectResult failed(WorkstationEndpointResultCode code, String detail) {
        return new StackAwareEndpointEffectResult(code, Optional.empty(), detail);
    }

    public boolean succeeded() {
        return ownerResult.isPresent();
    }
}
