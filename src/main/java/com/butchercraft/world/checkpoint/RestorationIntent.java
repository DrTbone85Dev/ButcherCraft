package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RestorationIntent(
        int schemaVersion,
        String intentIdentity,
        RestorationIdentity restorationIdentity,
        WorldIdentityRootReference worldIdentityRoot,
        CheckpointGenerationId generationId,
        String generationManifestDigest,
        RestorationSource source,
        long sourceHeadSequence,
        String sourceHeadDigest,
        PlatformDeterminismManifestReference platformDeterminismManifest,
        Optional<String> recoveryResultIdentity,
        Optional<String> recoveryResultContentDigest,
        List<ExpectedOwner> expectedOwners,
        String contentDigest
) {
    public static final int CURRENT_SCHEMA = 1;
    private static final String PREFIX = "butchercraft:restoration_intent/v1/";

    public RestorationIntent {
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported restoration-intent schema");
        }
        intentIdentity = CheckpointValidation.id(intentIdentity, "restorationIntentIdentity");
        restorationIdentity = Objects.requireNonNull(restorationIdentity, "restorationIdentity");
        worldIdentityRoot = Objects.requireNonNull(worldIdentityRoot, "worldIdentityRoot");
        generationId = Objects.requireNonNull(generationId, "generationId");
        generationManifestDigest = CheckpointValidation.digest(
                generationManifestDigest,
                "generationManifestDigest"
        );
        source = Objects.requireNonNull(source, "source");
        sourceHeadSequence = CheckpointValidation.positive(sourceHeadSequence, "sourceHeadSequence");
        sourceHeadDigest = CheckpointValidation.digest(sourceHeadDigest, "sourceHeadDigest");
        platformDeterminismManifest = Objects.requireNonNull(
                platformDeterminismManifest,
                "platformDeterminismManifest"
        );
        recoveryResultIdentity = Objects.requireNonNull(recoveryResultIdentity, "recoveryResultIdentity")
                .map(value -> CheckpointValidation.id(value, "recoveryResultIdentity"));
        recoveryResultContentDigest = CheckpointValidation.optionalDigest(
                recoveryResultContentDigest,
                "recoveryResultContentDigest"
        );
        if (recoveryResultIdentity.isPresent() != recoveryResultContentDigest.isPresent()
                || (source == RestorationSource.RECOVERY_GENERATION) != recoveryResultIdentity.isPresent()) {
            throw new IllegalArgumentException("Recovery Result binding must match restoration source");
        }
        expectedOwners = Objects.requireNonNull(expectedOwners, "expectedOwners").stream()
                .map(value -> Objects.requireNonNull(value, "expectedOwner"))
                .sorted()
                .toList();
        if (!expectedOwners.stream().map(ExpectedOwner::ownerId).toList()
                .equals(LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS)) {
            throw new IllegalArgumentException("Restoration intent requires the exact 17-owner participant set");
        }
        contentDigest = CheckpointValidation.digest(contentDigest, "restorationIntentContentDigest");
        String expected = calculateDigest(
                restorationIdentity,
                worldIdentityRoot,
                generationId,
                generationManifestDigest,
                source,
                sourceHeadSequence,
                sourceHeadDigest,
                platformDeterminismManifest,
                recoveryResultIdentity,
                recoveryResultContentDigest,
                expectedOwners
        );
        if (!contentDigest.equals(expected)
                || !intentIdentity.equals(PREFIX + expected.substring("sha256:".length()))) {
            throw new IllegalArgumentException("Restoration intent is not canonical");
        }
    }

    public static RestorationIntent prepare(
            RestorationIdentity restorationIdentity,
            CheckpointGenerationManifest manifest,
            CheckpointHeadRecord sourceHead,
            RestorationSource source,
            Optional<LegacySplitRecoveryResult> recoveryResult,
            List<OwnerNativeRestorationPlan> plans
    ) {
        List<ExpectedOwner> owners = plans.stream().map(ExpectedOwner::from).sorted().toList();
        Optional<String> resultIdentity = recoveryResult.map(LegacySplitRecoveryResult::resultIdentity);
        Optional<String> resultDigest = recoveryResult.map(LegacySplitRecoveryResult::contentDigest);
        String digest = calculateDigest(
                restorationIdentity,
                manifest.worldIdentityRoot(),
                manifest.generationId(),
                manifest.manifestDigest(),
                source,
                sourceHead.headSequence(),
                sourceHead.headRecordDigest(),
                manifest.platformDeterminismManifest(),
                resultIdentity,
                resultDigest,
                owners
        );
        return new RestorationIntent(
                CURRENT_SCHEMA,
                PREFIX + digest.substring("sha256:".length()),
                restorationIdentity,
                manifest.worldIdentityRoot(),
                manifest.generationId(),
                manifest.manifestDigest(),
                source,
                sourceHead.headSequence(),
                sourceHead.headRecordDigest(),
                manifest.platformDeterminismManifest(),
                resultIdentity,
                resultDigest,
                owners,
                digest
        );
    }

    private static String calculateDigest(
            RestorationIdentity restorationIdentity,
            WorldIdentityRootReference world,
            CheckpointGenerationId generation,
            String manifestDigest,
            RestorationSource source,
            long headSequence,
            String headDigest,
            PlatformDeterminismManifestReference platform,
            Optional<String> recoveryResultIdentity,
            Optional<String> recoveryResultDigest,
            List<ExpectedOwner> owners
    ) {
        CheckpointCanonicalDigest digest = CheckpointCanonicalDigest.create(
                "butchercraft:restoration_intent"
        ).add(CURRENT_SCHEMA)
                .add(restorationIdentity.value())
                .add(world.identity()).add(world.schemaVersion()).add(world.rootDigest())
                .add(generation.canonicalValue()).add(manifestDigest)
                .add(source.name()).add(headSequence).add(headDigest)
                .add(platform.identity()).add(platform.schemaVersion()).add(platform.manifestDigest())
                .add(recoveryResultIdentity.isPresent())
                .add(recoveryResultIdentity.orElse(""))
                .add(recoveryResultDigest.orElse(""))
                .add(owners.size());
        owners.stream().sorted().forEach(owner -> {
            digest.add(owner.ownerId().value())
                    .add(owner.snapshotIdentity())
                    .add(owner.snapshotContentDigest())
                    .add(owner.ownerSchemaVersion())
                    .add(owner.logicalContentDigest())
                    .add(owner.files().size());
            owner.files().forEach(file -> digest
                    .add(file.logicalName())
                    .add(file.targetRelativePath())
                    .add(file.contentDigest())
                    .add(file.length())
                    .add(file.observedPreRestorationDigest().isPresent())
                    .add(file.observedPreRestorationDigest().orElse("")));
            digest.add(owner.workstationProjectionDigest().orElse(""));
            digest.add(owner.mutationGate().wholeWorldConsequentialMutationBlocked())
                    .add(owner.mutationGate().blockedAuthorityIdentities().size());
            owner.mutationGate().blockedAuthorityIdentities().forEach(digest::add);
            digest.add(owner.mutationGate().blockIdentities().size());
            owner.mutationGate().blockIdentities().forEach(digest::add);
            digest.add(owner.policyBRunIdentities().size());
            owner.policyBRunIdentities().forEach(digest::add);
        });
        return digest.finish();
    }

    public record ExpectedOwner(
            CheckpointOwnerId ownerId,
            String snapshotIdentity,
            String snapshotContentDigest,
            int ownerSchemaVersion,
            List<ExpectedFile> files,
            Optional<String> workstationProjectionDigest,
            RecoveryMutationGate mutationGate,
            List<String> policyBRunIdentities,
            String logicalContentDigest
    ) implements Comparable<ExpectedOwner> {
        public ExpectedOwner {
            ownerId = Objects.requireNonNull(ownerId, "ownerId");
            snapshotIdentity = CheckpointValidation.id(snapshotIdentity, "snapshotIdentity");
            snapshotContentDigest = CheckpointValidation.digest(snapshotContentDigest, "snapshotContentDigest");
            ownerSchemaVersion = CheckpointValidation.positive(ownerSchemaVersion, "ownerSchemaVersion");
            files = Objects.requireNonNull(files, "files").stream().sorted().toList();
            workstationProjectionDigest = CheckpointValidation.optionalDigest(
                    Objects.requireNonNull(workstationProjectionDigest, "workstationProjectionDigest"),
                    "workstationProjectionDigest"
            );
            mutationGate = Objects.requireNonNull(mutationGate, "mutationGate");
            policyBRunIdentities = Objects.requireNonNull(policyBRunIdentities, "policyBRunIdentities").stream()
                    .map(value -> CheckpointValidation.id(value, "policyBRunIdentity"))
                    .distinct().sorted().toList();
            logicalContentDigest = CheckpointValidation.digest(logicalContentDigest, "logicalContentDigest");
        }

        static ExpectedOwner from(OwnerNativeRestorationPlan plan) {
            return new ExpectedOwner(
                    plan.ownerId(),
                    plan.snapshotIdentity(),
                    plan.snapshotContentDigest(),
                    plan.ownerSchemaVersion(),
                    plan.nativeFiles().stream().map(ExpectedFile::from).toList(),
                    plan.workstationProjection().map(CheckpointSnapshotDigest::sha256),
                    plan.mutationGate(),
                    plan.policyBRunIdentities(),
                    plan.logicalContentDigest()
            );
        }

        @Override
        public int compareTo(ExpectedOwner other) {
            return ownerId.compareTo(Objects.requireNonNull(other, "other").ownerId);
        }
    }

    public record ExpectedFile(
            String logicalName,
            String targetRelativePath,
            String contentDigest,
            int length,
            Optional<String> observedPreRestorationDigest
    ) implements Comparable<ExpectedFile> {
        public ExpectedFile {
            logicalName = CheckpointValidation.text(logicalName, "logicalName");
            targetRelativePath = CheckpointValidation.text(targetRelativePath, "targetRelativePath");
            contentDigest = CheckpointValidation.digest(contentDigest, "contentDigest");
            observedPreRestorationDigest = CheckpointValidation.optionalDigest(
                    Objects.requireNonNull(observedPreRestorationDigest, "observedPreRestorationDigest"),
                    "observedPreRestorationDigest"
            );
            if (length < 0) throw new IllegalArgumentException("Expected file length must not be negative");
        }

        static ExpectedFile from(OwnerNativeRestorationPlan.NativeFile file) {
            return new ExpectedFile(
                    file.logicalName(),
                    file.targetRelativePath(),
                    file.contentDigest(),
                    file.bytes().length,
                    file.observedPreRestorationDigest()
            );
        }

        @Override
        public int compareTo(ExpectedFile other) {
            return logicalName.compareTo(Objects.requireNonNull(other, "other").logicalName);
        }
    }
}
