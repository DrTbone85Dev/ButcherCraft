package com.butchercraft.world.checkpoint;

import java.util.Objects;

public record LegacySplitRecoveryIdentity(String value) implements Comparable<LegacySplitRecoveryIdentity> {
    private static final String PREFIX = "butchercraft:legacy_split_recovery/v1/";

    public LegacySplitRecoveryIdentity {
        value = CheckpointValidation.id(value, "Recovery Identity");
        if (!value.startsWith(PREFIX) || value.length() != PREFIX.length() + 64) {
            throw new IllegalArgumentException("Recovery Identity has unsupported canonical form");
        }
    }

    static LegacySplitRecoveryIdentity fromDigest(String digest) {
        return new LegacySplitRecoveryIdentity(PREFIX + CheckpointValidation.digest(
                digest,
                "Recovery Identity digest"
        ).substring("sha256:".length()));
    }

    @Override
    public int compareTo(LegacySplitRecoveryIdentity other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
