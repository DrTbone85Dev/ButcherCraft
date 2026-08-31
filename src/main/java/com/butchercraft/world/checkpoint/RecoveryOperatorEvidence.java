package com.butchercraft.world.checkpoint;

import java.util.Objects;

public record RecoveryOperatorEvidence(
        String principalIdentity,
        RecoveryOperatorAuthority authority,
        String evidenceIdentity,
        String evidenceContentDigest
) {
    public RecoveryOperatorEvidence {
        principalIdentity = CheckpointValidation.id(principalIdentity, "operatorPrincipalIdentity");
        authority = Objects.requireNonNull(authority, "authority");
        evidenceIdentity = CheckpointValidation.id(evidenceIdentity, "operatorEvidenceIdentity");
        evidenceContentDigest = CheckpointValidation.digest(
                evidenceContentDigest,
                "operatorEvidenceContentDigest"
        );
    }

    public void requirePublicationAuthority() {
        if (!authority.mayPublishRecovery()) {
            throw new SecurityException("Operator or administrator authority is required for recovery publication");
        }
    }
}
