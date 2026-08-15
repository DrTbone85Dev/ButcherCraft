package com.butchercraft.workstation.endpoint.runtime;

import com.butchercraft.workstation.endpoint.WorkstationEndpointPreparationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;

import java.util.Objects;
import java.util.Optional;

public record StackAwareEndpointPreparationResult(
        WorkstationEndpointResultCode code,
        Optional<WorkstationEndpointPreparationV2> preparation,
        String detail
) {
    public StackAwareEndpointPreparationResult {
        code = Objects.requireNonNull(code, "code");
        preparation = Objects.requireNonNull(preparation, "preparation");
        detail = Objects.requireNonNull(detail, "detail");
        if ((code == WorkstationEndpointResultCode.APPLIED) != preparation.isPresent()) {
            throw new IllegalArgumentException("Successful schema-2 preparation must contain evidence");
        }
    }

    public static StackAwareEndpointPreparationResult prepared(WorkstationEndpointPreparationV2 preparation) {
        return new StackAwareEndpointPreparationResult(
                WorkstationEndpointResultCode.APPLIED,
                Optional.of(preparation),
                "Stack-aware endpoint effect prepared"
        );
    }

    public static StackAwareEndpointPreparationResult failed(
            WorkstationEndpointResultCode code,
            String detail
    ) {
        return new StackAwareEndpointPreparationResult(code, Optional.empty(), detail);
    }

    public boolean succeeded() {
        return preparation.isPresent();
    }
}
