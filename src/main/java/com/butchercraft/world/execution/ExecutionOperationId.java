package com.butchercraft.world.execution;

import java.util.Objects;

public record ExecutionOperationId(String value) implements Comparable<ExecutionOperationId> {
    private static final String PREFIX = "butchercraft:execution_operation/v";

    public ExecutionOperationId {
        value = ExecutionValidation.requireId(value, "Execution operation id");
        if (!value.startsWith(PREFIX + ExecutionSchema.CURRENT_VERSION + "/")) {
            throw new IllegalArgumentException("Execution operation id has unsupported prefix");
        }
    }

    public static ExecutionOperationId of(String value) {
        return new ExecutionOperationId(value);
    }

    static ExecutionOperationId derive(ExecutionAuthorizationEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        ExecutionCanonicalDigest digest = ExecutionCanonicalDigest.create("butchercraft:execution_operation");
        digest.add(ExecutionSchema.CURRENT_VERSION)
                .add(evidence.authorizationIdentity())
                .add(evidence.authorizationContentDigest())
                .add(evidence.operationType())
                .add(evidence.handlerId())
                .add(evidence.executableWorkReferenceType())
                .add(evidence.executableWorkReferenceId())
                .add(evidence.frozenInputIdentity())
                .add(evidence.configurationIdentity())
                .add(evidence.worldIdentity());
        return new ExecutionOperationId(PREFIX + ExecutionSchema.CURRENT_VERSION + "/"
                + ExecutionValidation.digestIdSuffix(digest.finish()));
    }

    @Override
    public int compareTo(ExecutionOperationId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
