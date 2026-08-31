package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;

/** Carries owner-specific fail-closed checkpoint failures through the generic file adapter. */
public final class CheckpointOwnerSnapshotRejectedException extends RuntimeException {
    private final List<CheckpointFailure> failures;

    public CheckpointOwnerSnapshotRejectedException(List<CheckpointFailure> failures) {
        super("Owner checkpoint snapshot was rejected");
        this.failures = Objects.requireNonNull(failures, "failures").stream()
                .map(failure -> Objects.requireNonNull(failure, "failure"))
                .toList();
        if (this.failures.isEmpty()) {
            throw new IllegalArgumentException("Rejected owner snapshot requires at least one failure");
        }
    }

    public List<CheckpointFailure> failures() {
        return failures;
    }
}
