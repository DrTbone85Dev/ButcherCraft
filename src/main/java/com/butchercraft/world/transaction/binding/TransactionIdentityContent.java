package com.butchercraft.world.transaction.binding;

public record TransactionIdentityContent(
        String identity,
        int schemaVersion,
        String contentDigest
) implements Comparable<TransactionIdentityContent> {
    public TransactionIdentityContent {
        identity = TransactionBindingValidation.id(identity, "identity");
        schemaVersion = TransactionBindingValidation.positive(schemaVersion, "schemaVersion");
        contentDigest = TransactionBindingValidation.digest(contentDigest, "contentDigest");
    }

    public static TransactionIdentityContent of(String identity, String contentDigest) {
        return new TransactionIdentityContent(identity, TransactionBindingSchema.CURRENT_VERSION, contentDigest);
    }

    @Override
    public int compareTo(TransactionIdentityContent other) {
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
