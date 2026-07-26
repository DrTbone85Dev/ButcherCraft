package com.butchercraft.world.evidence;

import java.util.Objects;

public record EvidenceDescriptor(
        EvidenceIdentity identity,
        EvidenceSource source,
        EvidenceClassification classification,
        long simulationTick,
        long ownerSequence,
        int schemaVersion,
        boolean citedByAuthoritativeEvidence
) {
    public EvidenceDescriptor {
        identity = Objects.requireNonNull(identity, "identity");
        source = Objects.requireNonNull(source, "source");
        classification = Objects.requireNonNull(classification, "classification");
        simulationTick = EvidenceValidation.nonNegative(simulationTick, "simulationTick");
        ownerSequence = EvidenceValidation.nonNegative(ownerSequence, "ownerSequence");
        schemaVersion = EvidenceValidation.positive(schemaVersion, "schemaVersion");
        if (!identity.ownerId().equals(source.ownerId())) {
            throw new IllegalArgumentException("Evidence identity owner must match the source owner");
        }
    }
}
