package com.butchercraft.workstation.projection;

public record WorkstationProjectionDiagnostics(
        int totalInstances,
        int available,
        int retired,
        int legacyUnavailable,
        int blocked,
        long persistedBytes
) {
    public WorkstationProjectionDiagnostics {
        if (totalInstances < 0 || available < 0 || retired < 0 || legacyUnavailable < 0 || blocked < 0
                || persistedBytes < 0L) {
            throw new IllegalArgumentException("Workstation projection diagnostics must not be negative");
        }
        if (available + retired + legacyUnavailable + blocked != totalInstances) {
            throw new IllegalArgumentException("Workstation projection diagnostic counts do not cover all instances");
        }
    }
}
