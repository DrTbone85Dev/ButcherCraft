package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;

public record SchedulerEffectIdentity(int schemaVersion, String value) implements Comparable<SchedulerEffectIdentity> {
    private static final String PREFIX = "butchercraft:scheduler_effect_identity/v";

    public SchedulerEffectIdentity {
        schemaVersion = SchedulerValidation.requireSchema(schemaVersion, "scheduler effect identity");
        value = SchedulerValidation.requireId(value, "Scheduler effect identity");
        String expectedPrefix = PREFIX + schemaVersion + "/";
        if (!value.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("Scheduler effect identity version prefix is invalid");
        }
    }

    public static SchedulerEffectIdentity of(String value) {
        String normalized = SchedulerValidation.requireId(value, "Scheduler effect identity");
        int schema = SchedulerInvocationIdentity.parseSchema(
                normalized,
                PREFIX,
                "Scheduler effect identity"
        );
        return new SchedulerEffectIdentity(schema, normalized);
    }

    public static SchedulerEffectIdentity forWork(
            ScheduledSimulationWork work,
            SimulationWorkHandler handler,
            SchedulerEffectPolicy policy
    ) {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(policy, "policy");
        SchedulerCanonicalDigest digest = SchedulerCanonicalDigest.create("butchercraft:scheduler_effect_identity");
        digest.add(SchedulerSchema.CURRENT_VERSION)
                .add(work.id().value())
                .add(work.authoritativeSubmissionSequence())
                .add(handler.supportedTypeId().value())
                .add(handler.effectType().name())
                .add(SchedulerIdentityDigest.payloadDigest(work.payload()))
                .add(policy.policyIdentity());
        return fromDigest(digest.finish());
    }

    private static SchedulerEffectIdentity fromDigest(String digest) {
        return new SchedulerEffectIdentity(
                SchedulerSchema.CURRENT_VERSION,
                PREFIX + SchedulerSchema.CURRENT_VERSION + "/" + SchedulerIdentityDigest.requireDigest(
                        digest,
                        "Scheduler effect digest"
                )
        );
    }

    @Override
    public int compareTo(SchedulerEffectIdentity other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
