package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RestorationResult(
        int schemaVersion,
        String resultIdentity,
        RestorationIdentity restorationIdentity,
        String intentContentDigest,
        CheckpointGenerationId generationId,
        String generationManifestDigest,
        RestorationSource source,
        long restoredSimulationTick,
        List<RestorationParticipantResult> participants,
        RecoveryMutationGate mutationGate,
        List<String> policyBRunIdentities,
        Optional<String> completionTimestampMetadata,
        String contentDigest
) {
    public static final int CURRENT_SCHEMA = 1;
    private static final String PREFIX = "butchercraft:restoration_result/v1/";

    public RestorationResult {
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported restoration-result schema");
        }
        resultIdentity = CheckpointValidation.id(resultIdentity, "restorationResultIdentity");
        restorationIdentity = Objects.requireNonNull(restorationIdentity, "restorationIdentity");
        intentContentDigest = CheckpointValidation.digest(intentContentDigest, "intentContentDigest");
        generationId = Objects.requireNonNull(generationId, "generationId");
        generationManifestDigest = CheckpointValidation.digest(
                generationManifestDigest,
                "generationManifestDigest"
        );
        source = Objects.requireNonNull(source, "source");
        restoredSimulationTick = CheckpointValidation.nonNegative(
                restoredSimulationTick,
                "restoredSimulationTick"
        );
        participants = Objects.requireNonNull(participants, "participants").stream().sorted().toList();
        if (!participants.stream().map(RestorationParticipantResult::ownerId).toList()
                .equals(LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS)) {
            throw new IllegalArgumentException("Restoration result requires all 17 owners");
        }
        mutationGate = Objects.requireNonNull(mutationGate, "mutationGate");
        policyBRunIdentities = Objects.requireNonNull(policyBRunIdentities, "policyBRunIdentities").stream()
                .map(value -> CheckpointValidation.id(value, "policyBRunIdentity"))
                .distinct().sorted().toList();
        completionTimestampMetadata = Objects.requireNonNull(
                completionTimestampMetadata,
                "completionTimestampMetadata"
        ).map(value -> CheckpointValidation.text(value, "completionTimestampMetadata"));
        contentDigest = CheckpointValidation.digest(contentDigest, "restorationResultContentDigest");
        String expected = calculateDigest(
                restorationIdentity,
                intentContentDigest,
                generationId,
                generationManifestDigest,
                source,
                restoredSimulationTick,
                participants,
                mutationGate,
                policyBRunIdentities,
                completionTimestampMetadata
        );
        if (!contentDigest.equals(expected)
                || !resultIdentity.equals(PREFIX + expected.substring("sha256:".length()))) {
            throw new IllegalArgumentException("Restoration result is not canonical");
        }
    }

    public static RestorationResult complete(
            RestorationIntent intent,
            List<RestorationParticipantResult> participants,
            RecoveryMutationGate mutationGate,
            List<String> policyBRunIdentities,
            Optional<String> completionTimestampMetadata
    ) {
        List<RestorationParticipantResult> values = participants.stream().sorted().toList();
        List<String> policyB = policyBRunIdentities.stream().distinct().sorted().toList();
        String digest = calculateDigest(
                intent.restorationIdentity(),
                intent.contentDigest(),
                intent.generationId(),
                intent.generationManifestDigest(),
                intent.source(),
                intent.generationId().authoritativeSimulationTick(),
                values,
                mutationGate,
                policyB,
                completionTimestampMetadata
        );
        return new RestorationResult(
                CURRENT_SCHEMA,
                PREFIX + digest.substring("sha256:".length()),
                intent.restorationIdentity(),
                intent.contentDigest(),
                intent.generationId(),
                intent.generationManifestDigest(),
                intent.source(),
                intent.generationId().authoritativeSimulationTick(),
                values,
                mutationGate,
                policyB,
                completionTimestampMetadata,
                digest
        );
    }

    private static String calculateDigest(
            RestorationIdentity identity,
            String intentDigest,
            CheckpointGenerationId generation,
            String manifestDigest,
            RestorationSource source,
            long restoredTick,
            List<RestorationParticipantResult> participants,
            RecoveryMutationGate gate,
            List<String> policyB,
            Optional<String> completionTimestampMetadata
    ) {
        CheckpointCanonicalDigest digest = CheckpointCanonicalDigest.create(
                "butchercraft:restoration_result"
        ).add(CURRENT_SCHEMA)
                .add(identity.value())
                .add(intentDigest)
                .add(generation.canonicalValue())
                .add(manifestDigest)
                .add(source.name())
                .add(restoredTick)
                .add(participants.size());
        participants.stream().sorted().forEach(participant -> digest
                .add(participant.ownerId().value())
                .add(participant.contentDigest()));
        digest.add(gate.wholeWorldConsequentialMutationBlocked())
                .add(gate.blockedAuthorityIdentities().size());
        gate.blockedAuthorityIdentities().forEach(digest::add);
        digest.add(gate.blockIdentities().size());
        gate.blockIdentities().forEach(digest::add);
        digest.add(policyB.size());
        policyB.forEach(digest::add);
        digest.add(completionTimestampMetadata.isPresent());
        completionTimestampMetadata.ifPresent(digest::add);
        return digest.finish();
    }
}
