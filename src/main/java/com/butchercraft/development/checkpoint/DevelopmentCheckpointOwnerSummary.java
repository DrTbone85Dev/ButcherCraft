package com.butchercraft.development.checkpoint;

import com.butchercraft.world.checkpoint.OwnerSnapshotDescriptor;

import java.util.Objects;

public record DevelopmentCheckpointOwnerSummary(
        String ownerId,
        String snapshotIdentity,
        String configurationIdentity
) implements Comparable<DevelopmentCheckpointOwnerSummary> {
    public DevelopmentCheckpointOwnerSummary {
        ownerId = clean(ownerId, "ownerId");
        snapshotIdentity = clean(snapshotIdentity, "snapshotIdentity");
        configurationIdentity = clean(configurationIdentity, "configurationIdentity");
    }

    public static DevelopmentCheckpointOwnerSummary from(OwnerSnapshotDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return new DevelopmentCheckpointOwnerSummary(
                descriptor.ownerId().value(),
                descriptor.snapshotIdentity(),
                descriptor.configurationIdentity()
        );
    }

    @Override
    public int compareTo(DevelopmentCheckpointOwnerSummary other) {
        Objects.requireNonNull(other, "other");
        return ownerId.compareTo(other.ownerId);
    }

    private static String clean(String value, String field) {
        String cleaned = value == null ? "" : value.strip();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return cleaned;
    }
}
