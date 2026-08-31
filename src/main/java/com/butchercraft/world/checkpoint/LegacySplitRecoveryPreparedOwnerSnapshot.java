package com.butchercraft.world.checkpoint;

import java.util.Objects;

public record LegacySplitRecoveryPreparedOwnerSnapshot(
        CheckpointOwnerSnapshotPayload payload,
        String sourceSnapshotIdentity,
        String sourceSnapshotContentDigest
) implements Comparable<LegacySplitRecoveryPreparedOwnerSnapshot> {
    public LegacySplitRecoveryPreparedOwnerSnapshot {
        payload = Objects.requireNonNull(payload, "payload");
        sourceSnapshotIdentity = CheckpointValidation.id(sourceSnapshotIdentity, "sourceSnapshotIdentity");
        sourceSnapshotContentDigest = CheckpointValidation.digest(
                sourceSnapshotContentDigest,
                "sourceSnapshotContentDigest"
        );
    }

    public static LegacySplitRecoveryPreparedOwnerSnapshot fromDocument(
            LegacyRecoveryOwnerSnapshotDocument document,
            LegacySplitRecoveryOwnerPreparationRequest request,
            long ownerSequence
    ) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(request, "request");
        byte[] bytes = document.serialize();
        String contentDigest = CheckpointSnapshotDigest.sha256(bytes);
        String ownerPath = document.ownerId().value().substring(document.ownerId().value().indexOf(':') + 1);
        OwnerSnapshotDescriptor descriptor = new OwnerSnapshotDescriptor(
                document.ownerId(),
                LegacyRecoveryOwnerSnapshotDocument.CURRENT_SCHEMA,
                "butchercraft:legacy_recovery_owner_snapshot/" + ownerPath + "/"
                        + contentDigest.substring("sha256:".length()),
                contentDigest,
                CheckpointSnapshotParticipation.REQUIRED,
                request.plan().platformDeterminismManifest().identity(),
                request.plan().worldIdentityRoot(),
                request.generationId(),
                request.plan().authoritativeClockTick(),
                ownerSequence
        );
        return new LegacySplitRecoveryPreparedOwnerSnapshot(
                CheckpointOwnerSnapshotPayload.of(descriptor, bytes),
                document.sourceSnapshotIdentity(),
                document.sourceSnapshotContentDigest()
        );
    }

    public CheckpointOwnerId ownerId() {
        return payload.descriptor().ownerId();
    }

    @Override
    public int compareTo(LegacySplitRecoveryPreparedOwnerSnapshot other) {
        return payload.compareTo(Objects.requireNonNull(other, "other").payload);
    }
}
