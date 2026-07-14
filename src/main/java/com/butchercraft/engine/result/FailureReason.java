package com.butchercraft.engine.result;

import java.util.Objects;

/**
 * Immutable explicit domain failure reason.
 *
 * <p>Failures use codes and messages instead of null or booleans. Codes are stable enough for
 * tests and diagnostics; Minecraft integration may translate messages later.</p>
 */
public record FailureReason(String code, String message) {
    public FailureReason {
        code = Objects.requireNonNull(code, "code").strip();
        message = Objects.requireNonNull(message, "message").strip();
        if (code.isEmpty() || message.isEmpty()) {
            throw new IllegalArgumentException("Failure reason code and message must be present");
        }
    }
}
