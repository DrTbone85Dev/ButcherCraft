package com.butchercraft.engine.result;

import com.butchercraft.engine.EngineId;

import java.util.Objects;

/**
 * Immutable warning emitted during an operation.
 *
 * <p>Warnings are non-fatal, inspectable outcomes. They stay independent from Minecraft text
 * components so integration code can choose how to display them.</p>
 */
public record OperationWarning(EngineId sourceModifierId, String message) {
    public OperationWarning {
        Objects.requireNonNull(sourceModifierId, "sourceModifierId");
        message = Objects.requireNonNull(message, "message").strip();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("Warning message cannot be blank");
        }
    }
}
