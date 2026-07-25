package com.butchercraft.world.execution;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ExecutionAuthorization {
    private final ExecutionAuthorizationEvidence evidence;
    private final AtomicBoolean consumed = new AtomicBoolean();

    ExecutionAuthorization(ExecutionAuthorizationEvidence evidence) {
        this.evidence = Objects.requireNonNull(evidence, "evidence");
    }

    public ExecutionAuthorizationEvidence evidence() {
        return evidence;
    }

    public static ExecutionAuthorization issue(ExecutionAuthorizationEvidence evidence) {
        return new ExecutionAuthorization(evidence);
    }

    boolean consume() {
        return consumed.compareAndSet(false, true);
    }
}
