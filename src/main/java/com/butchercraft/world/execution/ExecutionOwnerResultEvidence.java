package com.butchercraft.world.execution;

import java.util.Objects;

public record ExecutionOwnerResultEvidence(
        int schemaVersion,
        String ownerSubsystemId,
        String ownerResultIdentity,
        ExecutionDomainEffectIdentity domainEffectIdentity,
        String ownerResultDigest,
        String contentDigest
) {
    public ExecutionOwnerResultEvidence {
        schemaVersion = ExecutionValidation.requireSchema(schemaVersion, "Execution owner result evidence");
        ownerSubsystemId = ExecutionValidation.requireId(ownerSubsystemId, "Owner result subsystem id");
        ownerResultIdentity = ExecutionValidation.requireId(ownerResultIdentity, "Owner result identity");
        domainEffectIdentity = Objects.requireNonNull(domainEffectIdentity, "domainEffectIdentity");
        ownerResultDigest = ExecutionValidation.requireDigest(ownerResultDigest, "Owner result digest");
        contentDigest = ExecutionValidation.requireDigest(contentDigest, "Owner result content digest");
    }

    public static ExecutionOwnerResultEvidence of(
            String ownerSubsystemId,
            String ownerResultIdentity,
            ExecutionDomainEffectIdentity domainEffectIdentity,
            String ownerResultDigest
    ) {
        ExecutionOwnerResultEvidence seed = new ExecutionOwnerResultEvidence(
                ExecutionSchema.CURRENT_VERSION,
                ownerSubsystemId,
                ownerResultIdentity,
                domainEffectIdentity,
                ownerResultDigest,
                ExecutionValidation.zeroDigest()
        );
        return seed.withCalculatedDigest();
    }

    public ExecutionOwnerResultEvidence withCalculatedDigest() {
        return new ExecutionOwnerResultEvidence(
                schemaVersion,
                ownerSubsystemId,
                ownerResultIdentity,
                domainEffectIdentity,
                ownerResultDigest,
                calculateDigest()
        );
    }

    public boolean digestMatches() {
        return contentDigest.equals(calculateDigest());
    }

    public String calculateDigest() {
        ExecutionCanonicalDigest digest = ExecutionCanonicalDigest.create("butchercraft:execution_owner_result");
        digest.add(schemaVersion)
                .add(ownerSubsystemId)
                .add(ownerResultIdentity)
                .add(domainEffectIdentity.value())
                .add(ownerResultDigest);
        return digest.finish();
    }
}
