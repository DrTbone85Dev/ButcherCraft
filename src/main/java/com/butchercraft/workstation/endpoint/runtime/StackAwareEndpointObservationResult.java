package com.butchercraft.workstation.endpoint.runtime;

import com.butchercraft.workstation.endpoint.WorkstationEndpointObservationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;

import java.util.Objects;
import java.util.Optional;

public record StackAwareEndpointObservationResult(
        WorkstationEndpointResultCode code,
        Optional<WorkstationEndpointObservationV2> observation,
        String detail
) {
    public StackAwareEndpointObservationResult {
        code = Objects.requireNonNull(code, "code");
        observation = Objects.requireNonNull(observation, "observation");
        detail = Objects.requireNonNull(detail, "detail");
        if ((code == WorkstationEndpointResultCode.APPLIED) != observation.isPresent()) {
            throw new IllegalArgumentException("Successful schema-2 observation must contain evidence");
        }
    }

    public static StackAwareEndpointObservationResult observed(WorkstationEndpointObservationV2 observation) {
        return new StackAwareEndpointObservationResult(
                WorkstationEndpointResultCode.APPLIED,
                Optional.of(observation),
                "Stack-aware endpoint observation accepted"
        );
    }

    public static StackAwareEndpointObservationResult failed(
            WorkstationEndpointResultCode code,
            String detail
    ) {
        return new StackAwareEndpointObservationResult(code, Optional.empty(), detail);
    }

    public boolean succeeded() {
        return observation.isPresent();
    }
}
