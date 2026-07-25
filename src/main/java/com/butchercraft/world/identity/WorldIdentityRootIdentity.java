package com.butchercraft.world.identity;

import java.util.Objects;
import java.util.regex.Pattern;

public record WorldIdentityRootIdentity(
        String identity,
        int schemaVersion,
        String rootDigest
) {
    private static final Pattern CANONICAL_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");

    public WorldIdentityRootIdentity {
        identity = requireCanonicalId(identity, "identity");
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("World Identity root schema version must be positive");
        }
        rootDigest = requireDigest(rootDigest);
    }

    private static String requireCanonicalId(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!CANONICAL_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("World Identity root " + field + " must be canonical: " + value);
        }
        return value;
    }

    private static String requireDigest(String value) {
        Objects.requireNonNull(value, "rootDigest");
        if (!DIGEST.matcher(value).matches()) {
            throw new IllegalArgumentException("World Identity root digest must be lowercase SHA-256");
        }
        return value;
    }
}
