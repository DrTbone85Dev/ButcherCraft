package com.butchercraft.machine.pattyformer.execution;

import com.butchercraft.world.execution.ExecutionAuthorization;

import java.util.Objects;

public record PattyFormerExecutionPreparation(
        ExecutionAuthorization authorization,
        String frozenInputIdentity,
        String expectedOutputIdentity,
        String sourceFreshnessIdentity
) {
    public PattyFormerExecutionPreparation {
        authorization = Objects.requireNonNull(authorization, "authorization");
        frozenInputIdentity = Objects.requireNonNull(frozenInputIdentity, "frozenInputIdentity");
        expectedOutputIdentity = Objects.requireNonNull(expectedOutputIdentity, "expectedOutputIdentity");
        sourceFreshnessIdentity = Objects.requireNonNull(sourceFreshnessIdentity, "sourceFreshnessIdentity");
    }
}
