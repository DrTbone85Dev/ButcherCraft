package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Exact read-only source bytes plus owner-supplied validation metadata. */
public record RecoverySourceSnapshot(
        CheckpointOwnerId ownerId,
        int ownerSchemaVersion,
        List<Integer> supportedSchemaVersions,
        String snapshotIdentity,
        String contentDigest,
        long ownerRevisionOrSequence,
        long representedSimulationTick,
        WorldIdentityRootReference worldIdentityRoot,
        List<String> configurationIdentities,
        boolean ownerValidated,
        Optional<String> validationFailure
) implements Comparable<RecoverySourceSnapshot> {
    public RecoverySourceSnapshot {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        ownerSchemaVersion = CheckpointValidation.positive(ownerSchemaVersion, "ownerSchemaVersion");
        supportedSchemaVersions = Objects.requireNonNull(
                supportedSchemaVersions,
                "supportedSchemaVersions"
        ).stream().map(value -> CheckpointValidation.positive(value, "supportedSchemaVersion"))
                .distinct().sorted().toList();
        if (supportedSchemaVersions.isEmpty()) {
            throw new IllegalArgumentException("At least one supported owner schema is required");
        }
        snapshotIdentity = CheckpointValidation.id(snapshotIdentity, "recoverySourceSnapshotIdentity");
        contentDigest = CheckpointValidation.digest(contentDigest, "recoverySourceContentDigest");
        ownerRevisionOrSequence = CheckpointValidation.nonNegative(
                ownerRevisionOrSequence,
                "ownerRevisionOrSequence"
        );
        representedSimulationTick = CheckpointValidation.nonNegative(
                representedSimulationTick,
                "representedSimulationTick"
        );
        worldIdentityRoot = Objects.requireNonNull(worldIdentityRoot, "worldIdentityRoot");
        configurationIdentities = Objects.requireNonNull(
                configurationIdentities,
                "configurationIdentities"
        ).stream().map(value -> CheckpointValidation.id(value, "configurationIdentity"))
                .distinct().sorted().toList();
        validationFailure = Objects.requireNonNull(validationFailure, "validationFailure")
                .map(value -> CheckpointValidation.text(value, "validationFailure"));
        if (ownerValidated == validationFailure.isPresent()) {
            throw new IllegalArgumentException(
                    "Owner validation must have either success or one explicit failure, but not both"
            );
        }
    }

    public boolean ownerSchemaSupported() {
        return supportedSchemaVersions.contains(ownerSchemaVersion);
    }

    @Override
    public int compareTo(RecoverySourceSnapshot other) {
        Objects.requireNonNull(other, "other");
        int ownerComparison = ownerId.compareTo(other.ownerId);
        return ownerComparison != 0 ? ownerComparison : snapshotIdentity.compareTo(other.snapshotIdentity);
    }
}
