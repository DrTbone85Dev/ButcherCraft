package com.butchercraft.world.checkpoint;

import java.util.Objects;

public final class StartupRecoveryException extends IllegalStateException {
    private final StartupRecoveryFailureCode failureCode;

    public StartupRecoveryException(StartupRecoveryFailureCode failureCode, String message) {
        super(message);
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
    }

    public StartupRecoveryException(
            StartupRecoveryFailureCode failureCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
    }

    public StartupRecoveryFailureCode failureCode() {
        return failureCode;
    }
}
