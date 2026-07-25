package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;
import java.util.regex.Pattern;

final class SchedulerIdentityDigest {
    private static final Pattern HEX_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SHA_256 = Pattern.compile("sha256:[0-9a-f]{64}");

    private SchedulerIdentityDigest() {
    }

    static String requireDigest(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (!HEX_256.matcher(normalized).matches() && !SHA_256.matcher(normalized).matches()) {
            throw new IllegalArgumentException(label + " must be a lowercase SHA-256 digest");
        }
        return normalized;
    }

    static String digestIdSuffix(String value, String label) {
        String digest = requireDigest(value, label);
        return digest.startsWith("sha256:") ? digest.substring("sha256:".length()) : digest;
    }

    static String payloadDigest(WorkPayload payload) {
        Objects.requireNonNull(payload, "payload");
        SchedulerCanonicalDigest digest = SchedulerCanonicalDigest.create("butchercraft:scheduler_payload");
        digest.add(SchedulerSchema.CURRENT_VERSION).add(payload.entries().size());
        for (WorkPayloadEntry entry : payload.entries()) {
            digest.add(entry.key()).add(entry.type().name()).add(entry.canonicalValue());
        }
        return digest.finish();
    }

    static String retryPolicyDigest(RetryPolicy retryPolicy) {
        Objects.requireNonNull(retryPolicy, "retryPolicy");
        SchedulerCanonicalDigest digest = SchedulerCanonicalDigest.create("butchercraft:scheduler_retry_policy");
        digest.add(SchedulerSchema.CURRENT_VERSION)
                .add(retryPolicy.type().name())
                .add(retryPolicy.intervalSimulationTicks().isPresent())
                .add(retryPolicy.intervalSimulationTicks().isPresent()
                        ? retryPolicy.intervalSimulationTicks().getAsLong() : -1L)
                .add(retryPolicy.maximumRetryTick().isPresent())
                .add(retryPolicy.maximumRetryTick().isPresent()
                        ? retryPolicy.maximumRetryTick().getAsLong() : -1L);
        return digest.finish();
    }
}
