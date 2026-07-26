package com.butchercraft.world.evidence;

import java.util.Objects;

public record EvidenceOwnerId(String value) implements Comparable<EvidenceOwnerId> {
    public EvidenceOwnerId {
        value = EvidenceValidation.id(value, "evidenceOwnerId");
    }

    public static EvidenceOwnerId of(String value) {
        return new EvidenceOwnerId(value);
    }

    @Override
    public int compareTo(EvidenceOwnerId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
