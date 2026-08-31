package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;

public record RestorationParticipantResult(
        int schemaVersion,
        RestorationIdentity restorationIdentity,
        CheckpointOwnerId ownerId,
        String expectedLogicalContentDigest,
        List<PublishedFile> files,
        boolean projectionVerified,
        String contentDigest
) implements Comparable<RestorationParticipantResult> {
    public static final int CURRENT_SCHEMA = 1;

    public RestorationParticipantResult {
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported restoration participant-result schema");
        }
        restorationIdentity = Objects.requireNonNull(restorationIdentity, "restorationIdentity");
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        expectedLogicalContentDigest = CheckpointValidation.digest(
                expectedLogicalContentDigest,
                "expectedLogicalContentDigest"
        );
        files = Objects.requireNonNull(files, "files").stream().sorted().toList();
        contentDigest = CheckpointValidation.digest(contentDigest, "participantResultContentDigest");
        if (!contentDigest.equals(calculateDigest(
                restorationIdentity,
                ownerId,
                expectedLogicalContentDigest,
                files,
                projectionVerified
        ))) {
            throw new IllegalArgumentException("Restoration participant result is not canonical");
        }
    }

    public static RestorationParticipantResult complete(
            RestorationIdentity restorationIdentity,
            OwnerNativeRestorationPlan plan,
            List<PublishedFile> files,
            boolean projectionVerified
    ) {
        List<PublishedFile> values = files.stream().sorted().toList();
        String digest = calculateDigest(
                restorationIdentity,
                plan.ownerId(),
                plan.logicalContentDigest(),
                values,
                projectionVerified
        );
        return new RestorationParticipantResult(
                CURRENT_SCHEMA,
                restorationIdentity,
                plan.ownerId(),
                plan.logicalContentDigest(),
                values,
                projectionVerified,
                digest
        );
    }

    private static String calculateDigest(
            RestorationIdentity restorationIdentity,
            CheckpointOwnerId ownerId,
            String logicalDigest,
            List<PublishedFile> files,
            boolean projectionVerified
    ) {
        CheckpointCanonicalDigest digest = CheckpointCanonicalDigest.create(
                "butchercraft:restoration_participant_result"
        ).add(CURRENT_SCHEMA)
                .add(restorationIdentity.value())
                .add(ownerId.value())
                .add(logicalDigest)
                .add(files.size());
        files.stream().sorted().forEach(file -> digest
                .add(file.logicalName())
                .add(file.targetRelativePath())
                .add(file.contentDigest())
                .add(file.length())
                .add(file.publication().name()));
        return digest.add(projectionVerified).finish();
    }

    @Override
    public int compareTo(RestorationParticipantResult other) {
        return ownerId.compareTo(Objects.requireNonNull(other, "other").ownerId);
    }

    public record PublishedFile(
            String logicalName,
            String targetRelativePath,
            String contentDigest,
            int length,
            Publication publication
    ) implements Comparable<PublishedFile> {
        public PublishedFile {
            logicalName = CheckpointValidation.text(logicalName, "logicalName");
            targetRelativePath = CheckpointValidation.text(targetRelativePath, "targetRelativePath");
            contentDigest = CheckpointValidation.digest(contentDigest, "contentDigest");
            if (length < 0) throw new IllegalArgumentException("Published file length must not be negative");
            publication = Objects.requireNonNull(publication, "publication");
        }

        @Override
        public int compareTo(PublishedFile other) {
            return logicalName.compareTo(Objects.requireNonNull(other, "other").logicalName);
        }
    }

    public enum Publication {
        OBSERVED_EXACT,
        PUBLISHED_ATOMICALLY
    }
}
