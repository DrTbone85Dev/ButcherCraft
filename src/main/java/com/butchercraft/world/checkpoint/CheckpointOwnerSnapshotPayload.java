package com.butchercraft.world.checkpoint;

import java.util.Arrays;
import java.util.Objects;

public final class CheckpointOwnerSnapshotPayload implements Comparable<CheckpointOwnerSnapshotPayload> {
    private final OwnerSnapshotDescriptor descriptor;
    private final byte[] payloadBytes;
    private final String expectedContentDigest;

    public CheckpointOwnerSnapshotPayload(
            OwnerSnapshotDescriptor descriptor,
            byte[] payloadBytes,
            String expectedContentDigest
    ) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.payloadBytes = Objects.requireNonNull(payloadBytes, "payloadBytes").clone();
        this.expectedContentDigest = CheckpointValidation.digest(expectedContentDigest, "expectedContentDigest");
    }

    public static CheckpointOwnerSnapshotPayload of(
            OwnerSnapshotDescriptor descriptor,
            byte[] payloadBytes
    ) {
        return new CheckpointOwnerSnapshotPayload(
                descriptor,
                payloadBytes,
                CheckpointFilesystemDigest.sha256(payloadBytes)
        );
    }

    public OwnerSnapshotDescriptor descriptor() {
        return descriptor;
    }

    public byte[] payloadBytes() {
        return payloadBytes.clone();
    }

    public String expectedContentDigest() {
        return expectedContentDigest;
    }

    @Override
    public int compareTo(CheckpointOwnerSnapshotPayload other) {
        Objects.requireNonNull(other, "other");
        return descriptor.compareTo(other.descriptor);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CheckpointOwnerSnapshotPayload other)) {
            return false;
        }
        return descriptor.equals(other.descriptor)
                && expectedContentDigest.equals(other.expectedContentDigest)
                && Arrays.equals(payloadBytes, other.payloadBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(descriptor, expectedContentDigest);
        result = 31 * result + Arrays.hashCode(payloadBytes);
        return result;
    }
}
