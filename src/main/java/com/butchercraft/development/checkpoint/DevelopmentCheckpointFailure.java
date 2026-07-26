package com.butchercraft.development.checkpoint;

import java.util.Objects;

public record DevelopmentCheckpointFailure(
        DevelopmentCheckpointFailureCode code,
        String field,
        String message
) implements Comparable<DevelopmentCheckpointFailure> {
    public DevelopmentCheckpointFailure {
        code = Objects.requireNonNull(code, "code");
        field = field == null ? "" : field.strip();
        message = message == null ? "" : message.strip();
        if (field.isEmpty()) {
            throw new IllegalArgumentException("Development checkpoint failure field cannot be blank");
        }
        if (message.isEmpty()) {
            throw new IllegalArgumentException("Development checkpoint failure message cannot be blank");
        }
    }

    @Override
    public int compareTo(DevelopmentCheckpointFailure other) {
        Objects.requireNonNull(other, "other");
        int codeComparison = code.name().compareTo(other.code.name());
        if (codeComparison != 0) {
            return codeComparison;
        }
        int fieldComparison = field.compareTo(other.field);
        if (fieldComparison != 0) {
            return fieldComparison;
        }
        return message.compareTo(other.message);
    }
}
