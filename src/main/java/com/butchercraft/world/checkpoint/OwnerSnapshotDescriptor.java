package com.butchercraft.world.checkpoint;

import java.util.Objects;

public record OwnerSnapshotDescriptor(
        CheckpointOwnerId ownerId,
        int snapshotSchemaVersion,
        String snapshotIdentity,
        String contentDigest,
        CheckpointSnapshotParticipation participation,
        String configurationIdentity,
        WorldIdentityRootReference worldIdentityRoot,
        CheckpointGenerationId generationId,
        long representedSimulationTick,
        long ownerSequence
) implements Comparable<OwnerSnapshotDescriptor> {
    public OwnerSnapshotDescriptor {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        snapshotSchemaVersion = CheckpointValidation.positive(snapshotSchemaVersion, "snapshotSchemaVersion");
        snapshotIdentity = CheckpointValidation.id(snapshotIdentity, "snapshotIdentity");
        contentDigest = CheckpointValidation.digest(contentDigest, "contentDigest");
        participation = Objects.requireNonNull(participation, "participation");
        configurationIdentity = CheckpointValidation.id(configurationIdentity, "configurationIdentity");
        worldIdentityRoot = Objects.requireNonNull(worldIdentityRoot, "worldIdentityRoot");
        generationId = Objects.requireNonNull(generationId, "generationId");
        representedSimulationTick = CheckpointValidation.nonNegative(
                representedSimulationTick,
                "representedSimulationTick"
        );
        ownerSequence = CheckpointValidation.nonNegative(ownerSequence, "ownerSequence");
    }

    @Override
    public int compareTo(OwnerSnapshotDescriptor other) {
        Objects.requireNonNull(other, "other");
        int ownerComparison = ownerId.compareTo(other.ownerId);
        if (ownerComparison != 0) {
            return ownerComparison;
        }
        int identityComparison = snapshotIdentity.compareTo(other.snapshotIdentity);
        if (identityComparison != 0) {
            return identityComparison;
        }
        return contentDigest.compareTo(other.contentDigest);
    }
}
