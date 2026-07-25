package com.butchercraft.world.checkpoint;

import java.util.Objects;

public record CheckpointCapturedOwnerSnapshot(
        CheckpointOwnerSnapshotPayload payload,
        CheckpointOwnerValidationMetadata validationMetadata
) implements Comparable<CheckpointCapturedOwnerSnapshot> {
    public CheckpointCapturedOwnerSnapshot {
        payload = Objects.requireNonNull(payload, "payload");
        validationMetadata = Objects.requireNonNull(validationMetadata, "validationMetadata");
        if (!payload.descriptor().ownerId().equals(validationMetadata.ownerId())) {
            throw new IllegalArgumentException("Captured owner metadata must belong to the payload owner");
        }
    }

    public CheckpointOwnerId ownerId() {
        return payload.descriptor().ownerId();
    }

    @Override
    public int compareTo(CheckpointCapturedOwnerSnapshot other) {
        return payload.compareTo(Objects.requireNonNull(other, "other").payload);
    }
}
