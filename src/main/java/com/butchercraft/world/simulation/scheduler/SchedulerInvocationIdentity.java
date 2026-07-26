package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;

public record SchedulerInvocationIdentity(int schemaVersion, String value) implements Comparable<SchedulerInvocationIdentity> {
    private static final String PREFIX = "butchercraft:scheduler_invocation/v";

    public SchedulerInvocationIdentity {
        schemaVersion = SchedulerValidation.requireSchema(schemaVersion, "scheduler invocation identity");
        value = SchedulerValidation.requireId(value, "Scheduler invocation identity");
        String expectedPrefix = PREFIX + schemaVersion + "/";
        if (!value.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("Scheduler invocation identity version prefix is invalid");
        }
    }

    public static SchedulerInvocationIdentity of(String value) {
        String normalized = SchedulerValidation.requireId(value, "Scheduler invocation identity");
        int schema = parseSchema(normalized, PREFIX, "Scheduler invocation identity");
        return new SchedulerInvocationIdentity(schema, normalized);
    }

    public static SchedulerInvocationIdentity forAttempt(
            ScheduledSimulationWork work,
            SimulationWorkHandler handler,
            int attemptNumber,
            long authoritativeSimulationTick,
            SchedulerEffectPolicy policy
    ) {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(policy, "policy");
        if (attemptNumber <= 0) throw new IllegalArgumentException("Invocation attempt number must be positive");
        SchedulerValidation.requireTick(authoritativeSimulationTick, "Invocation identity tick");
        SchedulerCanonicalDigest digest = SchedulerCanonicalDigest.create("butchercraft:scheduler_invocation");
        digest.add(SchedulerSchema.CURRENT_VERSION)
                .add(work.id().value())
                .add(handler.supportedTypeId().value())
                .add(attemptNumber)
                .add(authoritativeSimulationTick)
                .add(SchedulerIdentityDigest.payloadDigest(work.payload()))
                .add(policy.policyIdentity());
        return fromDigest(digest.finish());
    }

    private static SchedulerInvocationIdentity fromDigest(String digest) {
        return new SchedulerInvocationIdentity(
                SchedulerSchema.CURRENT_VERSION,
                PREFIX + SchedulerSchema.CURRENT_VERSION + "/" + SchedulerIdentityDigest.requireDigest(
                        digest,
                        "Scheduler invocation digest"
                )
        );
    }

    static int parseSchema(String value, String prefix, String label) {
        if (!value.startsWith(prefix)) {
            throw new IllegalArgumentException(label + " prefix is invalid");
        }
        int slash = value.indexOf('/', prefix.length());
        if (slash < 0) throw new IllegalArgumentException(label + " is missing schema separator");
        try {
            return Integer.parseInt(value.substring(prefix.length(), slash));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " schema is invalid", exception);
        }
    }

    @Override
    public int compareTo(SchedulerInvocationIdentity other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
