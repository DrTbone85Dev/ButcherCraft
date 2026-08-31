package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record LegacySplitRecoveryPublicationIntent(
        int schemaVersion,
        String intentIdentity,
        LegacySplitRecoveryIdentity recoveryIdentity,
        String analysisDigest,
        String authorizationIdentity,
        String authorizationContentDigest,
        WorldIdentityRootReference worldIdentityRoot,
        PlatformDeterminismManifestReference platformDeterminismManifest,
        CheckpointGenerationId generationId,
        Optional<CheckpointGenerationId> predecessorGenerationId,
        Optional<String> predecessorManifestDigest,
        List<CheckpointOwnerId> requiredOwners,
        List<PreparedOwnerReference> preparedOwners,
        String contentDigest
) {
    public static final int CURRENT_SCHEMA = 1;
    private static final String PREFIX = "butchercraft:legacy_recovery_publication_intent/v1/";

    public LegacySplitRecoveryPublicationIntent {
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported recovery publication-intent schema");
        }
        intentIdentity = CheckpointValidation.id(intentIdentity, "recoveryPublicationIntentIdentity");
        recoveryIdentity = Objects.requireNonNull(recoveryIdentity, "recoveryIdentity");
        analysisDigest = CheckpointValidation.digest(analysisDigest, "analysisDigest");
        authorizationIdentity = CheckpointValidation.id(authorizationIdentity, "authorizationIdentity");
        authorizationContentDigest = CheckpointValidation.digest(
                authorizationContentDigest,
                "authorizationContentDigest"
        );
        worldIdentityRoot = Objects.requireNonNull(worldIdentityRoot, "worldIdentityRoot");
        platformDeterminismManifest = Objects.requireNonNull(
                platformDeterminismManifest,
                "platformDeterminismManifest"
        );
        generationId = Objects.requireNonNull(generationId, "generationId");
        predecessorGenerationId = Objects.requireNonNull(predecessorGenerationId, "predecessorGenerationId");
        predecessorManifestDigest = CheckpointValidation.optionalDigest(
                predecessorManifestDigest,
                "predecessorManifestDigest"
        );
        requiredOwners = Objects.requireNonNull(requiredOwners, "requiredOwners").stream()
                .map(value -> Objects.requireNonNull(value, "requiredOwner"))
                .distinct().sorted().toList();
        preparedOwners = Objects.requireNonNull(preparedOwners, "preparedOwners").stream()
                .map(value -> Objects.requireNonNull(value, "preparedOwner"))
                .sorted()
                .toList();
        if (!preparedOwners.stream().map(PreparedOwnerReference::ownerId).toList().equals(requiredOwners)) {
            throw new IllegalArgumentException("Recovery intent participant set is incomplete or duplicated");
        }
        contentDigest = CheckpointValidation.digest(contentDigest, "recoveryPublicationIntentContentDigest");
        String expected = calculateDigest(
                recoveryIdentity,
                analysisDigest,
                authorizationIdentity,
                authorizationContentDigest,
                worldIdentityRoot,
                platformDeterminismManifest,
                generationId,
                predecessorGenerationId,
                predecessorManifestDigest,
                requiredOwners,
                preparedOwners
        );
        if (!contentDigest.equals(expected)
                || !intentIdentity.equals(PREFIX + expected.substring("sha256:".length()))) {
            throw new IllegalArgumentException("Recovery publication intent is not canonical");
        }
    }

    public static LegacySplitRecoveryPublicationIntent freeze(
            SplitSnapshotRecoveryPlan plan,
            RecoveryOperatorAuthorization authorization,
            CheckpointGenerationId generationId,
            Optional<CheckpointGenerationId> predecessorGenerationId,
            Optional<String> predecessorManifestDigest,
            List<CheckpointOwnerId> requiredOwners,
            List<LegacySplitRecoveryPreparedOwnerSnapshot> preparedOwners
    ) {
        List<PreparedOwnerReference> references = preparedOwners.stream()
                .map(PreparedOwnerReference::from)
                .sorted()
                .toList();
        String digest = calculateDigest(
                plan.recoveryIdentity(),
                plan.analysisDigest(),
                authorization.authorizationIdentity(),
                authorization.contentDigest(),
                plan.worldIdentityRoot(),
                plan.platformDeterminismManifest(),
                generationId,
                predecessorGenerationId,
                predecessorManifestDigest,
                requiredOwners,
                references
        );
        return new LegacySplitRecoveryPublicationIntent(
                CURRENT_SCHEMA,
                PREFIX + digest.substring("sha256:".length()),
                plan.recoveryIdentity(),
                plan.analysisDigest(),
                authorization.authorizationIdentity(),
                authorization.contentDigest(),
                plan.worldIdentityRoot(),
                plan.platformDeterminismManifest(),
                generationId,
                predecessorGenerationId,
                predecessorManifestDigest,
                requiredOwners,
                references,
                digest
        );
    }

    public boolean targets(SplitSnapshotRecoveryPlan plan, RecoveryOperatorAuthorization authorization) {
        return recoveryIdentity.equals(plan.recoveryIdentity())
                && analysisDigest.equals(plan.analysisDigest())
                && authorizationIdentity.equals(authorization.authorizationIdentity())
                && authorizationContentDigest.equals(authorization.contentDigest())
                && worldIdentityRoot.equals(plan.worldIdentityRoot())
                && platformDeterminismManifest.equals(plan.platformDeterminismManifest());
    }

    private static String calculateDigest(
            LegacySplitRecoveryIdentity recoveryIdentity,
            String analysisDigest,
            String authorizationIdentity,
            String authorizationDigest,
            WorldIdentityRootReference world,
            PlatformDeterminismManifestReference platform,
            CheckpointGenerationId generation,
            Optional<CheckpointGenerationId> predecessor,
            Optional<String> predecessorDigest,
            List<CheckpointOwnerId> requiredOwners,
            List<PreparedOwnerReference> preparedOwners
    ) {
        CheckpointCanonicalDigest digest = CheckpointCanonicalDigest.create(
                "butchercraft:legacy_recovery_publication_intent"
        ).add(CURRENT_SCHEMA)
                .add(recoveryIdentity.value())
                .add(analysisDigest)
                .add(authorizationIdentity)
                .add(authorizationDigest)
                .add(world.identity())
                .add(world.schemaVersion())
                .add(world.rootDigest())
                .add(platform.identity())
                .add(platform.schemaVersion())
                .add(platform.manifestDigest())
                .add(generation.canonicalValue())
                .add(predecessor.isPresent())
                .add(predecessor.map(CheckpointGenerationId::canonicalValue).orElse(""))
                .add(predecessorDigest.orElse(""))
                .add(requiredOwners.size());
        requiredOwners.stream().sorted().forEach(owner -> digest.add(owner.value()));
        digest.add(preparedOwners.size());
        preparedOwners.stream().sorted().forEach(owner -> digest
                .add(owner.ownerId().value())
                .add(owner.snapshotIdentity())
                .add(owner.contentDigest())
                .add(owner.sourceSnapshotIdentity())
                .add(owner.sourceSnapshotContentDigest())
                .add(owner.ownerSequence()));
        return digest.finish();
    }

    public record PreparedOwnerReference(
            CheckpointOwnerId ownerId,
            String snapshotIdentity,
            String contentDigest,
            String sourceSnapshotIdentity,
            String sourceSnapshotContentDigest,
            long ownerSequence
    ) implements Comparable<PreparedOwnerReference> {
        public PreparedOwnerReference {
            ownerId = Objects.requireNonNull(ownerId, "ownerId");
            snapshotIdentity = CheckpointValidation.id(snapshotIdentity, "preparedSnapshotIdentity");
            contentDigest = CheckpointValidation.digest(contentDigest, "preparedSnapshotContentDigest");
            sourceSnapshotIdentity = CheckpointValidation.id(sourceSnapshotIdentity, "sourceSnapshotIdentity");
            sourceSnapshotContentDigest = CheckpointValidation.digest(
                    sourceSnapshotContentDigest,
                    "sourceSnapshotContentDigest"
            );
            ownerSequence = CheckpointValidation.nonNegative(ownerSequence, "preparedOwnerSequence");
        }

        static PreparedOwnerReference from(LegacySplitRecoveryPreparedOwnerSnapshot snapshot) {
            OwnerSnapshotDescriptor descriptor = snapshot.payload().descriptor();
            return new PreparedOwnerReference(
                    descriptor.ownerId(),
                    descriptor.snapshotIdentity(),
                    descriptor.contentDigest(),
                    snapshot.sourceSnapshotIdentity(),
                    snapshot.sourceSnapshotContentDigest(),
                    descriptor.ownerSequence()
            );
        }

        @Override
        public int compareTo(PreparedOwnerReference other) {
            return ownerId.compareTo(Objects.requireNonNull(other, "other").ownerId);
        }
    }
}
