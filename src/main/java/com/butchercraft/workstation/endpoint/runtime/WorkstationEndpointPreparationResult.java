package com.butchercraft.workstation.endpoint.runtime;

import com.butchercraft.workstation.endpoint.WorkstationEndpointPreparation;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;

import java.util.Objects;
import java.util.Optional;

public record WorkstationEndpointPreparationResult(
        WorkstationEndpointResultCode code,
        Optional<WorkstationEndpointPreparation> preparation,
        String detail
) {
    public WorkstationEndpointPreparationResult {
        code = Objects.requireNonNull(code, "code");
        preparation = Objects.requireNonNull(preparation, "preparation");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public static WorkstationEndpointPreparationResult prepared(WorkstationEndpointPreparation preparation) {
        return new WorkstationEndpointPreparationResult(
                WorkstationEndpointResultCode.APPLIED,
                Optional.of(preparation),
                "Workstation endpoint effect prepared"
        );
    }

    public static WorkstationEndpointPreparationResult failed(WorkstationEndpointResultCode code, String detail) {
        return new WorkstationEndpointPreparationResult(code, Optional.empty(), detail);
    }

    public boolean succeeded() {
        return preparation.isPresent();
    }
}
