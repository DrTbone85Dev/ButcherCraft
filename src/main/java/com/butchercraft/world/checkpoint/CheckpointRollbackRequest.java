package com.butchercraft.world.checkpoint;

import java.util.Objects;
import java.util.Optional;

public record CheckpointRollbackRequest(
        String operatorIntentId,
        CheckpointGenerationId targetGenerationId,
        String reason,
        Optional<String> auditReference
) {
    public CheckpointRollbackRequest {
        operatorIntentId = operatorIntentId == null ? "" : operatorIntentId.strip();
        targetGenerationId = Objects.requireNonNull(targetGenerationId, "targetGenerationId");
        reason = reason == null ? "" : reason.strip();
        auditReference = Objects.requireNonNull(auditReference, "auditReference")
                .map(String::strip)
                .filter(value -> !value.isEmpty());
    }
}
