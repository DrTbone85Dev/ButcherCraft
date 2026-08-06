package com.butchercraft.workstation.endpoint.runtime;

import com.butchercraft.workstation.endpoint.WorkstationEndpointObservation;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;

import java.util.Objects;
import java.util.Optional;

public record WorkstationEndpointObservationResult(
        boolean succeeded,
        WorkstationEndpointResultCode code,
        Optional<WorkstationEndpointObservation> observation,
        String detail
) {
    public WorkstationEndpointObservationResult {
        code = Objects.requireNonNull(code, "code");
        observation = Objects.requireNonNull(observation, "observation");
        detail = Objects.requireNonNull(detail, "detail");
        if (succeeded != observation.isPresent()) {
            throw new IllegalArgumentException("Successful endpoint observation must contain evidence");
        }
    }

    public static WorkstationEndpointObservationResult observed(WorkstationEndpointObservation observation) {
        return new WorkstationEndpointObservationResult(
                true,
                WorkstationEndpointResultCode.APPLIED,
                Optional.of(observation),
                "Endpoint observation accepted"
        );
    }

    public static WorkstationEndpointObservationResult failed(
            WorkstationEndpointResultCode code,
            String detail
    ) {
        return new WorkstationEndpointObservationResult(false, code, Optional.empty(), detail);
    }
}
