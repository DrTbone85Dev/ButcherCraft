package com.butchercraft.world.execution;

import com.butchercraft.world.simulation.scheduler.SchedulerInvocationIdentity;

import java.util.Objects;

public record ExecutionAttemptId(String value) implements Comparable<ExecutionAttemptId> {
    private static final String PREFIX = "butchercraft:execution_attempt/v";

    public ExecutionAttemptId {
        value = ExecutionValidation.requireId(value, "Execution attempt id");
        if (!value.startsWith(PREFIX + ExecutionSchema.CURRENT_VERSION + "/")) {
            throw new IllegalArgumentException("Execution attempt id has unsupported prefix");
        }
    }

    static ExecutionAttemptId derive(
            ExecutionOperationId operationId,
            int attemptSequence,
            SchedulerInvocationIdentity invocationIdentity,
            long tick,
            String handlerId
    ) {
        ExecutionCanonicalDigest digest = ExecutionCanonicalDigest.create("butchercraft:execution_attempt");
        digest.add(ExecutionSchema.CURRENT_VERSION)
                .add(operationId.value())
                .add(attemptSequence)
                .add(invocationIdentity.value())
                .add(tick)
                .add(handlerId);
        return new ExecutionAttemptId(PREFIX + ExecutionSchema.CURRENT_VERSION + "/"
                + ExecutionValidation.digestIdSuffix(digest.finish()));
    }

    @Override
    public int compareTo(ExecutionAttemptId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
