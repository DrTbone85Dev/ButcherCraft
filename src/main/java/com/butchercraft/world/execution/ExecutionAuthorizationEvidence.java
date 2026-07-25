package com.butchercraft.world.execution;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

public record ExecutionAuthorizationEvidence(
        int schemaVersion,
        String authorizationIdentity,
        String authorizationSourceOwner,
        String executableWorkReferenceType,
        String executableWorkReferenceId,
        String operationType,
        String handlerId,
        String frozenInputIdentity,
        String sourceFreshnessIdentity,
        String configurationIdentity,
        String worldIdentity,
        long issuedSimulationTick,
        OptionalLong validUntilSimulationTick,
        List<String> explicitInputIdentities,
        String authorizationContentDigest
) {
    public ExecutionAuthorizationEvidence {
        schemaVersion = ExecutionValidation.requireSchema(schemaVersion, "Execution authorization evidence");
        authorizationIdentity = ExecutionValidation.requireId(authorizationIdentity, "Execution authorization identity");
        authorizationSourceOwner = ExecutionValidation.requireId(authorizationSourceOwner, "Authorization source owner");
        executableWorkReferenceType = ExecutionValidation.requireId(
                executableWorkReferenceType,
                "Executable work reference type"
        );
        executableWorkReferenceId = ExecutionValidation.requireId(
                executableWorkReferenceId,
                "Executable work reference id"
        );
        operationType = ExecutionValidation.requireId(operationType, "Execution operation type");
        handlerId = ExecutionValidation.requireId(handlerId, "Execution handler id");
        frozenInputIdentity = ExecutionValidation.requireId(frozenInputIdentity, "Execution frozen input identity");
        sourceFreshnessIdentity = ExecutionValidation.requireId(
                sourceFreshnessIdentity,
                "Execution source freshness identity"
        );
        configurationIdentity = ExecutionValidation.requireId(
                configurationIdentity,
                "Execution configuration identity"
        );
        worldIdentity = ExecutionValidation.requireId(worldIdentity, "Execution world identity");
        issuedSimulationTick = ExecutionValidation.requireTick(issuedSimulationTick, "Authorization issue tick");
        validUntilSimulationTick = Objects.requireNonNull(validUntilSimulationTick, "validUntilSimulationTick");
        long issued = issuedSimulationTick;
        validUntilSimulationTick.ifPresent(until -> {
            ExecutionValidation.requireTick(until, "Authorization valid-until tick");
            if (until < issued) {
                throw new IllegalArgumentException("Authorization valid-until tick precedes issue tick");
            }
        });
        explicitInputIdentities = Objects.requireNonNull(explicitInputIdentities, "explicitInputIdentities").stream()
                .map(input -> ExecutionValidation.requireId(input, "Explicit Execution input identity"))
                .sorted()
                .toList();
        if (explicitInputIdentities.isEmpty() || explicitInputIdentities.size() > 64) {
            throw new IllegalArgumentException("Execution authorization requires 1-64 explicit input identities");
        }
        authorizationContentDigest = ExecutionValidation.requireDigest(
                authorizationContentDigest,
                "Execution authorization content digest"
        );
    }

    public static ExecutionAuthorizationEvidence issued(
            String authorizationSourceOwner,
            String executableWorkReferenceType,
            String executableWorkReferenceId,
            String operationType,
            String handlerId,
            String frozenInputIdentity,
            String sourceFreshnessIdentity,
            String configurationIdentity,
            String worldIdentity,
            long issuedSimulationTick,
            OptionalLong validUntilSimulationTick,
            List<String> explicitInputIdentities
    ) {
        ExecutionAuthorizationEvidence seed = new ExecutionAuthorizationEvidence(
                ExecutionSchema.CURRENT_VERSION,
                "butchercraft:execution_authorization/v" + ExecutionSchema.CURRENT_VERSION + "/"
                        + "0".repeat(64),
                authorizationSourceOwner,
                executableWorkReferenceType,
                executableWorkReferenceId,
                operationType,
                handlerId,
                frozenInputIdentity,
                sourceFreshnessIdentity,
                configurationIdentity,
                worldIdentity,
                issuedSimulationTick,
                validUntilSimulationTick,
                explicitInputIdentities,
                ExecutionValidation.zeroDigest()
        );
        String digest = seed.calculateContentDigest();
        return new ExecutionAuthorizationEvidence(
                ExecutionSchema.CURRENT_VERSION,
                "butchercraft:execution_authorization/v" + ExecutionSchema.CURRENT_VERSION + "/"
                        + ExecutionValidation.digestIdSuffix(digest),
                seed.authorizationSourceOwner,
                seed.executableWorkReferenceType,
                seed.executableWorkReferenceId,
                seed.operationType,
                seed.handlerId,
                seed.frozenInputIdentity,
                seed.sourceFreshnessIdentity,
                seed.configurationIdentity,
                seed.worldIdentity,
                seed.issuedSimulationTick,
                seed.validUntilSimulationTick,
                seed.explicitInputIdentities,
                digest
        );
    }

    public boolean expiredAt(long tick) {
        ExecutionValidation.requireTick(tick, "Authorization expiry check tick");
        return validUntilSimulationTick.isPresent() && tick > validUntilSimulationTick.orElseThrow();
    }

    public boolean digestMatches() {
        return authorizationContentDigest.equals(calculateContentDigest());
    }

    public String calculateContentDigest() {
        ExecutionCanonicalDigest digest = ExecutionCanonicalDigest.create("butchercraft:execution_authorization");
        digest.add(schemaVersion)
                .add(authorizationSourceOwner)
                .add(executableWorkReferenceType)
                .add(executableWorkReferenceId)
                .add(operationType)
                .add(handlerId)
                .add(frozenInputIdentity)
                .add(sourceFreshnessIdentity)
                .add(configurationIdentity)
                .add(worldIdentity)
                .add(issuedSimulationTick)
                .add(validUntilSimulationTick.isPresent());
        validUntilSimulationTick.ifPresent(digest::add);
        digest.add(explicitInputIdentities.size());
        explicitInputIdentities.forEach(digest::add);
        return digest.finish();
    }
}
