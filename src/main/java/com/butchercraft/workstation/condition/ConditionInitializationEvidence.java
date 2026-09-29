package com.butchercraft.workstation.condition;

import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import java.util.Objects;
import java.util.Optional;

public record ConditionInitializationEvidence(
        Origin origin, WorkstationInstanceId instanceId, String policyIdentity,
        Optional<String> legacyProjectionDigest, String migrationPolicyIdentity, String identity
) {
    public enum Origin { NEW_INSTANCE, PROVEN_LEGACY_PROJECTION }
    public static final String MIGRATION_POLICY = "butchercraft:condition_initialization/v1/proven_healthy_only";

    public ConditionInitializationEvidence {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(instanceId, "instanceId");
        ConditionDigest.text(policyIdentity, "policyIdentity");
        Objects.requireNonNull(legacyProjectionDigest, "legacyProjectionDigest");
        if (!MIGRATION_POLICY.equals(migrationPolicyIdentity)
                || (origin == Origin.PROVEN_LEGACY_PROJECTION) != legacyProjectionDigest.isPresent()) {
            throw new IllegalArgumentException("Invalid condition initialization proof");
        }
        if (!calculate(origin, instanceId, policyIdentity, legacyProjectionDigest).equals(identity)) {
            throw new IllegalArgumentException("Condition initialization identity mismatch");
        }
    }

    public static ConditionInitializationEvidence create(WorkstationInstanceId instance, String policy,
            Optional<String> legacyDigest) {
        Origin origin = legacyDigest.isPresent() ? Origin.PROVEN_LEGACY_PROJECTION : Origin.NEW_INSTANCE;
        return new ConditionInitializationEvidence(origin, instance, policy, legacyDigest, MIGRATION_POLICY,
                calculate(origin, instance, policy, legacyDigest));
    }

    private static String calculate(Origin origin, WorkstationInstanceId instance, String policy,
            Optional<String> legacyDigest) {
        return ConditionDigest.identity("butchercraft:condition_initialization_evidence/v1", origin.name(),
                instance.value(), policy, legacyDigest.orElse("none"), MIGRATION_POLICY);
    }
}
