package com.butchercraft.world.checkpoint;

import java.util.Objects;

public record RecoveryIssue(
        Code code,
        String referenceIdentity,
        String detail
) implements Comparable<RecoveryIssue> {
    public RecoveryIssue {
        code = Objects.requireNonNull(code, "code");
        referenceIdentity = CheckpointValidation.id(referenceIdentity, "recoveryIssueReferenceIdentity");
        detail = CheckpointValidation.text(detail, "recoveryIssueDetail");
    }

    @Override
    public int compareTo(RecoveryIssue other) {
        Objects.requireNonNull(other, "other");
        int codeComparison = code.compareTo(other.code);
        return codeComparison != 0 ? codeComparison : referenceIdentity.compareTo(other.referenceIdentity);
    }

    public enum Code {
        UNSUPPORTED_RECOVERY_SCHEMA,
        UNSUPPORTED_OWNER_SCHEMA,
        WORLD_IDENTITY_MISMATCH,
        OWNER_VALIDATION_FAILED,
        DUPLICATE_OWNER_SOURCE,
        OWNER_SNAPSHOT_AFTER_CLOCK,
        CLOCK_OLDER_THAN_SCHEDULER,
        MISSING_HISTORICAL_EVIDENCE,
        CONFLICTING_HISTORICAL_EVIDENCE,
        ORDINARY_WORK_NOT_PROVABLE,
        MATERIAL_HANDLING_RECOVERY_BLOCKED,
        MATERIAL_HANDLING_UNKNOWN_OUTCOME,
        REPLACEMENT_WORKSTATION_CONFLICT,
        LEGACY_ARTIFACT_REQUIRES_OWNER_ANALYSIS,
        LEGACY_ARTIFACT_UNSUPPORTED_SCHEMA,
        LEGACY_ARTIFACT_IDENTITY_CONFLICT,
        LEGACY_ARTIFACT_MALFORMED
    }
}
