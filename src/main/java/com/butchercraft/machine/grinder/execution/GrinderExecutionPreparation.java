package com.butchercraft.machine.grinder.execution;

import com.butchercraft.world.execution.ExecutionAuthorization;

import java.util.Objects;

public record GrinderExecutionPreparation(
        ExecutionAuthorization authorization,
        String frozenInputIdentity,
        String expectedOutputIdentity,
        String sourceFreshnessIdentity
) {
    public GrinderExecutionPreparation {
        authorization = Objects.requireNonNull(authorization, "authorization");
        frozenInputIdentity = Objects.requireNonNull(frozenInputIdentity, "frozenInputIdentity");
        expectedOutputIdentity = Objects.requireNonNull(expectedOutputIdentity, "expectedOutputIdentity");
        sourceFreshnessIdentity = Objects.requireNonNull(sourceFreshnessIdentity, "sourceFreshnessIdentity");
    }
}
