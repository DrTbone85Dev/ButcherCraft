package com.butchercraft.world.checkpoint;

import java.util.Objects;

public record LegacySplitRecoveryPublicationFailure(Code code, String field, String detail)
        implements Comparable<LegacySplitRecoveryPublicationFailure> {
    public LegacySplitRecoveryPublicationFailure {
        code = Objects.requireNonNull(code, "code");
        field = CheckpointValidation.text(field, "recoveryFailureField");
        detail = CheckpointValidation.text(detail, "recoveryFailureDetail");
    }

    @Override
    public int compareTo(LegacySplitRecoveryPublicationFailure other) {
        int codeComparison = code.compareTo(Objects.requireNonNull(other, "other").code);
        return codeComparison != 0 ? codeComparison : field.compareTo(other.field);
    }

    public enum Code {
        ANALYSIS_NOT_ELIGIBLE,
        AUTHORIZATION_REQUIRED,
        AUTHORIZATION_STALE,
        AUTHORIZATION_MISMATCH,
        OPERATOR_AUTHORITY_REQUIRED,
        PROTECTED_PATH,
        OWNER_PREPARER_MISSING,
        OWNER_PREPARER_DUPLICATE,
        OWNER_PREPARATION_FAILED,
        OWNER_SOURCE_MISMATCH,
        INCOMPLETE_PARTICIPANT_SET,
        GENERATION_CONFLICT,
        CHECKPOINT_PUBLICATION_FAILED,
        RECOVERY_RESULT_CONFLICT,
        UNSUPPORTED_SCHEMA,
        PUBLICATION_INTERRUPTED
    }
}
