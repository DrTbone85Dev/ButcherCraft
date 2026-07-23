package com.butchercraft.world.transaction;

import java.util.Objects;
import java.util.Optional;

public record TransactionMetadata(
        Optional<String> reason,
        Optional<String> referenceId,
        Optional<String> user,
        Optional<String> externalSystem,
        Optional<String> comments
) {
    private static final int MAX_SHORT_VALUE_LENGTH = 256;
    private static final int MAX_COMMENTS_LENGTH = 2_048;
    private static final TransactionMetadata EMPTY = new TransactionMetadata(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
    );

    public TransactionMetadata {
        reason = normalize(reason, "reason", MAX_SHORT_VALUE_LENGTH);
        referenceId = normalize(referenceId, "reference id", MAX_SHORT_VALUE_LENGTH);
        user = normalize(user, "user", MAX_SHORT_VALUE_LENGTH);
        externalSystem = normalize(externalSystem, "external system", MAX_SHORT_VALUE_LENGTH);
        comments = normalize(comments, "comments", MAX_COMMENTS_LENGTH);
    }

    public static TransactionMetadata empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static Optional<String> normalize(Optional<String> value, String label, int maximumLength) {
        return Objects.requireNonNull(value, label).map(candidate -> {
            String normalized = Objects.requireNonNull(candidate, label).strip();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Transaction metadata " + label + " cannot be blank");
            }
            if (normalized.length() > maximumLength) {
                throw new IllegalArgumentException(
                        "Transaction metadata " + label + " exceeds " + maximumLength + " characters"
                );
            }
            return normalized;
        });
    }

    public static final class Builder {
        private String reason;
        private String referenceId;
        private String user;
        private String externalSystem;
        private String comments;

        private Builder() {
        }

        public Builder reason(String value) {
            reason = value;
            return this;
        }

        public Builder referenceId(String value) {
            referenceId = value;
            return this;
        }

        public Builder user(String value) {
            user = value;
            return this;
        }

        public Builder externalSystem(String value) {
            externalSystem = value;
            return this;
        }

        public Builder comments(String value) {
            comments = value;
            return this;
        }

        public TransactionMetadata build() {
            return new TransactionMetadata(
                    Optional.ofNullable(reason),
                    Optional.ofNullable(referenceId),
                    Optional.ofNullable(user),
                    Optional.ofNullable(externalSystem),
                    Optional.ofNullable(comments)
            );
        }
    }
}
