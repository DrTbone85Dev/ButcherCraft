package com.butchercraft.world.evidence;

import java.util.Objects;
import java.util.Optional;

public record EvidenceIdentity(
        EvidenceOwnerId ownerId,
        String value,
        int schemaVersion,
        Optional<String> contentIdentity
) implements Comparable<EvidenceIdentity> {
    public EvidenceIdentity {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        value = EvidenceValidation.id(value, "evidenceId");
        schemaVersion = EvidenceValidation.positive(schemaVersion, "schemaVersion");
        contentIdentity = EvidenceValidation.optionalContentIdentity(contentIdentity, "contentIdentity");
    }

    public EvidenceIdentity(
            EvidenceOwnerId ownerId,
            String value,
            int schemaVersion,
            String contentIdentity
    ) {
        this(ownerId, value, schemaVersion, EvidenceValidation.optional(contentIdentity));
    }

    public boolean sameEntityIdentityAs(EvidenceIdentity other) {
        Objects.requireNonNull(other, "other");
        return ownerId.equals(other.ownerId)
                && value.equals(other.value)
                && schemaVersion == other.schemaVersion;
    }

    public boolean conflictsWith(EvidenceIdentity other) {
        return sameEntityIdentityAs(other) && !contentIdentity.equals(other.contentIdentity);
    }

    public String entityKey() {
        return ownerId.value() + "|" + value + "|" + schemaVersion;
    }

    @Override
    public int compareTo(EvidenceIdentity other) {
        Objects.requireNonNull(other, "other");
        int ownerComparison = ownerId.compareTo(other.ownerId);
        if (ownerComparison != 0) {
            return ownerComparison;
        }
        int valueComparison = value.compareTo(other.value);
        if (valueComparison != 0) {
            return valueComparison;
        }
        int schemaComparison = Integer.compare(schemaVersion, other.schemaVersion);
        if (schemaComparison != 0) {
            return schemaComparison;
        }
        return contentIdentity.orElse("").compareTo(other.contentIdentity.orElse(""));
    }
}
