package com.butchercraft.world.transaction.binding;

import java.util.Objects;

public record ValidationInputIdentity(
        String identity,
        int schemaVersion,
        String contentDigest
) implements Comparable<ValidationInputIdentity> {
    public ValidationInputIdentity {
        identity = TransactionBindingValidation.id(identity, "identity");
        schemaVersion = TransactionBindingValidation.positive(schemaVersion, "schemaVersion");
        contentDigest = TransactionBindingValidation.digest(contentDigest, "contentDigest");
    }

    public static ValidationInputIdentity of(String identity, String contentDigest) {
        return new ValidationInputIdentity(
                identity,
                TransactionBindingSchema.CURRENT_VERSION,
                contentDigest
        );
    }

    @Override
    public int compareTo(ValidationInputIdentity other) {
        Objects.requireNonNull(other, "other");
        int identityComparison = identity.compareTo(other.identity);
        if (identityComparison != 0) {
            return identityComparison;
        }
        int schemaComparison = Integer.compare(schemaVersion, other.schemaVersion);
        if (schemaComparison != 0) {
            return schemaComparison;
        }
        return contentDigest.compareTo(other.contentDigest);
    }
}
