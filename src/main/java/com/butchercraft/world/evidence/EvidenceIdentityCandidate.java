package com.butchercraft.world.evidence;

import java.util.Optional;

public record EvidenceIdentityCandidate(
        String ownerId,
        String evidenceId,
        int schemaVersion,
        Optional<String> contentIdentity
) {
    public EvidenceIdentityCandidate {
        ownerId = normalize(ownerId);
        evidenceId = normalize(evidenceId);
        contentIdentity = contentIdentity == null
                ? Optional.empty()
                : contentIdentity.map(String::strip).filter(value -> !value.isEmpty());
    }

    public EvidenceIdentityCandidate(
            String ownerId,
            String evidenceId,
            int schemaVersion,
            String contentIdentity
    ) {
        this(ownerId, evidenceId, schemaVersion, EvidenceValidation.optional(contentIdentity));
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip();
    }
}
