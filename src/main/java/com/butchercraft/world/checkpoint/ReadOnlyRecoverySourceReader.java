package com.butchercraft.world.checkpoint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Reads exact source bytes without invoking owner startup, migration, or publication behavior. */
public final class ReadOnlyRecoverySourceReader {
    public RecoverySourceSnapshot read(
            Path sourceFile,
            CheckpointOwnerId ownerId,
            int ownerSchemaVersion,
            List<Integer> supportedSchemaVersions,
            String snapshotIdentity,
            long ownerRevisionOrSequence,
            long representedSimulationTick,
            WorldIdentityRootReference worldIdentityRoot,
            List<String> configurationIdentities,
            boolean ownerValidated,
            Optional<String> validationFailure
    ) {
        Path path = Objects.requireNonNull(sourceFile, "sourceFile").toAbsolutePath().normalize();
        try {
            byte[] exactBytes = Files.readAllBytes(path);
            return new RecoverySourceSnapshot(
                    ownerId,
                    ownerSchemaVersion,
                    supportedSchemaVersions,
                    snapshotIdentity,
                    CheckpointSnapshotDigest.sha256(exactBytes),
                    ownerRevisionOrSequence,
                    representedSimulationTick,
                    worldIdentityRoot,
                    configurationIdentities,
                    ownerValidated,
                    validationFailure
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed read-only recovery source inspection: " + path, exception);
        }
    }
}
