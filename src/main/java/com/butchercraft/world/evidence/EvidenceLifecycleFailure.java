package com.butchercraft.world.evidence;

import java.util.Objects;

public record EvidenceLifecycleFailure(
        EvidenceLifecycleFailureCode code,
        String field,
        String message
) {
    public EvidenceLifecycleFailure {
        code = Objects.requireNonNull(code, "code");
        field = EvidenceValidation.text(field, "field");
        message = EvidenceValidation.text(message, "message");
    }
}
