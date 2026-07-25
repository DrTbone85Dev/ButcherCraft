package com.butchercraft.world.evidence;

import java.util.Objects;

public record EvidenceSource(
        EvidenceOwnerId ownerId,
        String sourceType,
        String sourceIdentity,
        int schemaVersion
) implements Comparable<EvidenceSource> {
    public EvidenceSource {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        sourceType = EvidenceValidation.id(sourceType, "sourceType");
        sourceIdentity = EvidenceValidation.id(sourceIdentity, "sourceIdentity");
        schemaVersion = EvidenceValidation.positive(schemaVersion, "schemaVersion");
    }

    @Override
    public int compareTo(EvidenceSource other) {
        Objects.requireNonNull(other, "other");
        int ownerComparison = ownerId.compareTo(other.ownerId);
        if (ownerComparison != 0) {
            return ownerComparison;
        }
        int typeComparison = sourceType.compareTo(other.sourceType);
        if (typeComparison != 0) {
            return typeComparison;
        }
        int identityComparison = sourceIdentity.compareTo(other.sourceIdentity);
        if (identityComparison != 0) {
            return identityComparison;
        }
        return Integer.compare(schemaVersion, other.schemaVersion);
    }
}
