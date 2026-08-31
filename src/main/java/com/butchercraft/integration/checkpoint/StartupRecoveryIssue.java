package com.butchercraft.integration.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.StartupRecoveryFailureCode;

import java.util.Objects;
import java.util.Optional;

public record StartupRecoveryIssue(
        StartupRecoveryFailureCode code,
        Optional<CheckpointOwnerId> ownerId,
        String detail
) implements Comparable<StartupRecoveryIssue> {
    public StartupRecoveryIssue {
        code = Objects.requireNonNull(code, "code");
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        detail = Objects.requireNonNull(detail, "detail").strip();
        if (detail.isEmpty()) throw new IllegalArgumentException("Startup recovery issue detail is blank");
    }

    @Override
    public int compareTo(StartupRecoveryIssue other) {
        int codeComparison = code.compareTo(Objects.requireNonNull(other, "other").code);
        if (codeComparison != 0) return codeComparison;
        return ownerId.map(CheckpointOwnerId::value).orElse("")
                .compareTo(other.ownerId.map(CheckpointOwnerId::value).orElse(""));
    }
}
