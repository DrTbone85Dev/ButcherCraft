package com.butchercraft.world.execution;

import java.util.Objects;

public record ExecutionDomainEffectIdentity(String value) implements Comparable<ExecutionDomainEffectIdentity> {
    private static final String PREFIX = "butchercraft:execution_domain_effect/v";

    public ExecutionDomainEffectIdentity {
        value = ExecutionValidation.requireId(value, "Execution domain effect identity");
        if (!value.startsWith(PREFIX + ExecutionSchema.CURRENT_VERSION + "/")) {
            throw new IllegalArgumentException("Execution domain effect identity has unsupported prefix");
        }
    }

    static ExecutionDomainEffectIdentity derive(ExecutionOperationId operationId, ExecutionAuthorizationEvidence evidence) {
        ExecutionCanonicalDigest digest = ExecutionCanonicalDigest.create("butchercraft:execution_domain_effect");
        digest.add(ExecutionSchema.CURRENT_VERSION)
                .add(operationId.value())
                .add(evidence.operationType())
                .add(evidence.frozenInputIdentity())
                .add(evidence.authorizationContentDigest());
        return new ExecutionDomainEffectIdentity(PREFIX + ExecutionSchema.CURRENT_VERSION + "/"
                + ExecutionValidation.digestIdSuffix(digest.finish()));
    }

    @Override
    public int compareTo(ExecutionDomainEffectIdentity other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
