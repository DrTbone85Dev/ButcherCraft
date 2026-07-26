package com.butchercraft.world.checkpoint;

import java.nio.file.Path;
import java.util.Objects;

public record CheckpointStorageArtifact(
        CheckpointStorageArtifactKind kind,
        Path path,
        CheckpointFailure failure
) implements Comparable<CheckpointStorageArtifact> {
    public CheckpointStorageArtifact {
        kind = Objects.requireNonNull(kind, "kind");
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        failure = Objects.requireNonNull(failure, "failure");
    }

    @Override
    public int compareTo(CheckpointStorageArtifact other) {
        Objects.requireNonNull(other, "other");
        int kindComparison = kind.name().compareTo(other.kind.name());
        if (kindComparison != 0) {
            return kindComparison;
        }
        return path.toString().compareTo(other.path.toString());
    }
}
