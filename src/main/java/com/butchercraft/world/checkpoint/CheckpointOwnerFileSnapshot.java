package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;

/** Immutable owner-produced files captured at one live checkpoint boundary. */
public record CheckpointOwnerFileSnapshot(
        CheckpointOwnerId ownerId,
        int ownerSchemaVersion,
        long ownerSequence,
        boolean canonicalEmpty,
        List<FilePayload> files
) {
    public CheckpointOwnerFileSnapshot {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        ownerSchemaVersion = CheckpointValidation.positive(ownerSchemaVersion, "ownerSchemaVersion");
        ownerSequence = CheckpointValidation.nonNegative(ownerSequence, "ownerSequence");
        files = Objects.requireNonNull(files, "files").stream()
                .map(file -> Objects.requireNonNull(file, "file"))
                .sorted()
                .toList();
        if (files.isEmpty()) {
            throw new IllegalArgumentException("Owner checkpoint snapshot requires at least one canonical file");
        }
        for (int index = 1; index < files.size(); index++) {
            if (files.get(index - 1).logicalName().equals(files.get(index).logicalName())) {
                throw new IllegalArgumentException("Duplicate owner checkpoint file: " + files.get(index).logicalName());
            }
        }
    }

    public record FilePayload(String logicalName, byte[] bytes) implements Comparable<FilePayload> {
        public FilePayload {
            logicalName = CheckpointValidation.text(logicalName, "logicalName");
            if (logicalName.contains("..") || logicalName.contains("/") || logicalName.contains("\\")) {
                throw new IllegalArgumentException("Owner checkpoint logical name must be a file name");
            }
            bytes = Objects.requireNonNull(bytes, "bytes").clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public int compareTo(FilePayload other) {
            return logicalName.compareTo(Objects.requireNonNull(other, "other").logicalName);
        }
    }
}
