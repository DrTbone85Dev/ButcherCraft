package com.butchercraft.world.checkpoint;

import java.util.Objects;

public record CheckpointFailure(
        CheckpointFailureCode code,
        String field,
        String message
) {
    public CheckpointFailure {
        code = Objects.requireNonNull(code, "code");
        field = CheckpointValidation.text(field, "field");
        message = CheckpointValidation.text(message, "message");
    }
}
