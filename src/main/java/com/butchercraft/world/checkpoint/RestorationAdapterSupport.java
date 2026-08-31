package com.butchercraft.world.checkpoint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

public final class RestorationAdapterSupport {
    private RestorationAdapterSupport() {
    }

    public static OwnerSnapshotDescriptor requireSnapshot(
            OwnerNativeRestorationContext context,
            CheckpointOwnerId expectedOwner,
            CheckpointOwnerSnapshotPayload snapshot
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(expectedOwner, "expectedOwner");
        Objects.requireNonNull(snapshot, "snapshot");
        OwnerSnapshotDescriptor descriptor = snapshot.descriptor();
        if (!descriptor.ownerId().equals(expectedOwner)
                || !descriptor.worldIdentityRoot().equals(context.worldIdentityRoot())
                || !descriptor.generationId().equals(context.generationManifest().generationId())
                || !snapshot.expectedContentDigest().equals(descriptor.contentDigest())
                || !CheckpointSnapshotDigest.sha256(snapshot.payloadBytes()).equals(descriptor.contentDigest())) {
            throw new IllegalArgumentException("Checkpoint owner snapshot binding is invalid: "
                    + expectedOwner.value());
        }
        return descriptor;
    }

    public static LegacyRecoveryOwnerSnapshotDocument requireLegacyDocument(
            OwnerNativeRestorationContext context,
            CheckpointOwnerId ownerId,
            CheckpointOwnerSnapshotPayload snapshot
    ) {
        if (context.source() != RestorationSource.RECOVERY_GENERATION) {
            throw new IllegalArgumentException("Legacy owner proof is valid only for a recovery generation");
        }
        LegacyRecoveryOwnerSnapshotDocument document = LegacyRecoveryOwnerSnapshotDocument.deserialize(
                snapshot.payloadBytes());
        LegacySplitRecoveryResult result = context.legacyRecoveryResult().orElseThrow();
        if (!document.ownerId().equals(ownerId)
                || !document.recoveryIdentity().equals(result.recoveryIdentity())
                || !document.analysisDigest().equals(result.analysisDigest())) {
            throw new IllegalArgumentException("Legacy owner proof does not bind the committed Recovery Result");
        }
        result.sourceSnapshots().stream().filter(source -> source.ownerId().equals(ownerId)).findFirst()
                .ifPresent(source -> {
                    if (!document.sourceSnapshotIdentity().equals(source.snapshotIdentity())
                            || !document.sourceSnapshotContentDigest().equals(source.contentDigest())) {
                        throw new IllegalArgumentException(
                                "Legacy owner proof does not bind the exact source snapshot");
                    }
                });
        return document;
    }

    public static RecoverySourceSnapshot requireLegacySource(
            OwnerNativeRestorationContext context,
            CheckpointOwnerId ownerId
    ) {
        return context.legacyRecoveryResult().orElseThrow().sourceSnapshots().stream()
                .filter(source -> source.ownerId().equals(ownerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Recovery Result omits legacy source evidence for " + ownerId.value()));
    }

    public static List<LegacyRecoverySourceBundle.SourceFile> readAndVerifyLegacySource(
            OwnerNativeRestorationContext context,
            CheckpointOwnerId ownerId,
            List<String> logicalNames
    ) {
        RecoverySourceSnapshot source = requireLegacySource(context, ownerId);
        List<LegacyRecoverySourceBundle.SourceFile> files = logicalNames.stream().sorted().map(name -> {
            try {
                return new LegacyRecoverySourceBundle.SourceFile(
                        name,
                        Files.readAllBytes(context.ownerRoot().resolve(name))
                );
            } catch (IOException exception) {
                throw new UncheckedIOException("Legacy recovery source file is unavailable: " + name, exception);
            }
        }).toList();
        String digest = LegacyRecoverySourceBundle.digest(ownerId, files);
        if (!digest.equals(source.contentDigest())) {
            throw new IllegalStateException("Legacy source bytes differ from committed recovery evidence: "
                    + ownerId.value());
        }
        return files;
    }

    public static byte[] bytes(List<LegacyRecoverySourceBundle.SourceFile> files, String logicalName) {
        return files.stream().filter(file -> file.logicalName().equals(logicalName))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Legacy source bundle omits " + logicalName)).bytes();
    }
}
