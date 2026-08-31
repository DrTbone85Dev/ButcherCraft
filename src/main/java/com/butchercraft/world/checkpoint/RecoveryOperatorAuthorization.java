package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable authorization evidence for the explicit R2 publication path. */
public record RecoveryOperatorAuthorization(
        int schemaVersion,
        String authorizationIdentity,
        LegacySplitRecoveryIdentity recoveryIdentity,
        String analysisDigest,
        WorldIdentityRootReference worldIdentityRoot,
        Disposition disposition,
        List<String> authorityBlockIdentities,
        List<RecoverySourceSnapshot> sourceSnapshots,
        RecoveryOperatorEvidence operatorEvidence,
        Optional<String> authorizationTimestampMetadata,
        String contentDigest
) {
    private static final int AUTHORIZATION_SCHEMA = 2;
    private static final String PREFIX = "butchercraft:legacy_split_recovery_authorization/v2/";

    public RecoveryOperatorAuthorization {
        if (schemaVersion != AUTHORIZATION_SCHEMA) {
            throw new IllegalArgumentException("Unsupported recovery authorization schema");
        }
        authorizationIdentity = CheckpointValidation.id(
                authorizationIdentity,
                "recoveryAuthorizationIdentity"
        );
        if (!authorizationIdentity.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Recovery authorization identity has unsupported prefix");
        }
        recoveryIdentity = Objects.requireNonNull(recoveryIdentity, "recoveryIdentity");
        analysisDigest = CheckpointValidation.digest(analysisDigest, "authorizedAnalysisDigest");
        worldIdentityRoot = Objects.requireNonNull(worldIdentityRoot, "worldIdentityRoot");
        disposition = Objects.requireNonNull(disposition, "disposition");
        authorityBlockIdentities = Objects.requireNonNull(
                authorityBlockIdentities,
                "authorityBlockIdentities"
        ).stream().map(value -> CheckpointValidation.id(value, "authorityBlockIdentity"))
                .distinct().sorted().toList();
        sourceSnapshots = Objects.requireNonNull(sourceSnapshots, "sourceSnapshots").stream()
                .map(value -> Objects.requireNonNull(value, "sourceSnapshot"))
                .sorted()
                .toList();
        operatorEvidence = Objects.requireNonNull(operatorEvidence, "operatorEvidence");
        operatorEvidence.requirePublicationAuthority();
        authorizationTimestampMetadata = Objects.requireNonNull(
                authorizationTimestampMetadata,
                "authorizationTimestampMetadata"
        ).map(value -> CheckpointValidation.text(value, "authorizationTimestampMetadata"));
        contentDigest = CheckpointValidation.digest(contentDigest, "recoveryAuthorizationContentDigest");
        String expected = calculateDigest(
                recoveryIdentity,
                analysisDigest,
                worldIdentityRoot,
                disposition,
                authorityBlockIdentities,
                sourceSnapshots,
                operatorEvidence
        );
        if (!contentDigest.equals(expected)
                || !authorizationIdentity.equals(PREFIX + expected.substring("sha256:".length()))) {
            throw new IllegalArgumentException("Recovery operator authorization is not canonical");
        }
    }

    public static RecoveryOperatorAuthorization authorize(
            SplitSnapshotRecoveryPlan plan,
            Disposition disposition,
            RecoveryOperatorEvidence operatorEvidence,
            Optional<String> timestampMetadata
    ) {
        Objects.requireNonNull(plan, "plan");
        requireCompatibleDisposition(plan, disposition);
        List<String> blocks = plan.authorityBlocks().stream()
                .map(RecoveryAuthorityBlock::blockIdentity)
                .sorted()
                .toList();
        String digest = calculateDigest(
                plan.recoveryIdentity(),
                plan.analysisDigest(),
                plan.worldIdentityRoot(),
                disposition,
                blocks,
                plan.sourceSnapshots(),
                operatorEvidence
        );
        return new RecoveryOperatorAuthorization(
                AUTHORIZATION_SCHEMA,
                PREFIX + digest.substring("sha256:".length()),
                plan.recoveryIdentity(),
                plan.analysisDigest(),
                plan.worldIdentityRoot(),
                disposition,
                blocks,
                plan.sourceSnapshots(),
                operatorEvidence,
                timestampMetadata,
                digest
        );
    }

    public boolean targets(SplitSnapshotRecoveryPlan plan) {
        Objects.requireNonNull(plan, "plan");
        return recoveryIdentity.equals(plan.recoveryIdentity())
                && analysisDigest.equals(plan.analysisDigest())
                && worldIdentityRoot.equals(plan.worldIdentityRoot())
                && compatibleDisposition(plan, disposition)
                && sourceSnapshots.equals(plan.sourceSnapshots())
                && authorityBlockIdentities.equals(plan.authorityBlocks().stream()
                .map(RecoveryAuthorityBlock::blockIdentity)
                .sorted()
                .toList());
    }

    private static void requireCompatibleDisposition(
            SplitSnapshotRecoveryPlan plan,
            Disposition disposition
    ) {
        if (!compatibleDisposition(plan, Objects.requireNonNull(disposition, "disposition"))) {
            throw new IllegalArgumentException(
                    "Operator disposition is incompatible with recovery eligibility " + plan.eligibility()
            );
        }
    }

    private static boolean compatibleDisposition(
            SplitSnapshotRecoveryPlan plan,
            Disposition disposition
    ) {
        return switch (disposition) {
            case ABORT -> true;
            case AUTHORIZE_PROOF_COMPLETE_PUBLICATION ->
                    plan.eligibility() == SplitSnapshotRecoveryPlan.Eligibility.RECOVERABLE_PROOF_COMPLETE
                            && plan.authorityBlocks().isEmpty();
            case AUTHORIZE_RECOVERY_WITH_AUTHORITY_BLOCKS ->
                    plan.eligibility() == SplitSnapshotRecoveryPlan.Eligibility.RECOVERABLE_WITH_AUTHORITY_BLOCKS
                            && !plan.authorityBlocks().isEmpty();
        };
    }

    private static String calculateDigest(
            LegacySplitRecoveryIdentity recoveryIdentity,
            String analysisDigest,
            WorldIdentityRootReference world,
            Disposition disposition,
            List<String> blocks,
            List<RecoverySourceSnapshot> sourceSnapshots,
            RecoveryOperatorEvidence operatorEvidence
    ) {
        CheckpointCanonicalDigest digest = CheckpointCanonicalDigest.create(
                "butchercraft:legacy_split_recovery_authorization"
        ).add(AUTHORIZATION_SCHEMA)
                .add(recoveryIdentity.value())
                .add(analysisDigest)
                .add(world.identity())
                .add(world.schemaVersion())
                .add(world.rootDigest())
                .add(Objects.requireNonNull(disposition, "disposition").name())
                .add(operatorEvidence.principalIdentity())
                .add(operatorEvidence.authority().name())
                .add(operatorEvidence.evidenceIdentity())
                .add(operatorEvidence.evidenceContentDigest())
                .add(blocks.size());
        blocks.stream().sorted().forEach(digest::add);
        List<RecoverySourceSnapshot> orderedSources = sourceSnapshots.stream().sorted().toList();
        digest.add(orderedSources.size());
        for (RecoverySourceSnapshot source : orderedSources) {
            digest.add(source.ownerId().value())
                    .add(source.ownerSchemaVersion())
                    .add(source.snapshotIdentity())
                    .add(source.contentDigest())
                    .add(source.ownerRevisionOrSequence())
                    .add(source.representedSimulationTick())
                    .add(source.worldIdentityRoot().identity())
                    .add(source.worldIdentityRoot().schemaVersion())
                    .add(source.worldIdentityRoot().rootDigest())
                    .add(source.ownerValidated())
                    .add(source.supportedSchemaVersions().size());
            source.supportedSchemaVersions().forEach(digest::add);
            digest.add(source.configurationIdentities().size());
            source.configurationIdentities().forEach(digest::add);
            digest.add(source.validationFailure().orElse(""));
        }
        return digest.finish();
    }

    public enum Disposition {
        AUTHORIZE_PROOF_COMPLETE_PUBLICATION,
        AUTHORIZE_RECOVERY_WITH_AUTHORITY_BLOCKS,
        ABORT
    }
}
