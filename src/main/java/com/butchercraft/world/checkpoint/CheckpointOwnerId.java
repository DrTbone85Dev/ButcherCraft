package com.butchercraft.world.checkpoint;

import java.util.Objects;

public record CheckpointOwnerId(String value) implements Comparable<CheckpointOwnerId> {
    public CheckpointOwnerId {
        value = CheckpointValidation.id(value, "checkpointOwnerId");
    }

    public static CheckpointOwnerId of(String value) {
        return new CheckpointOwnerId(value);
    }

    @Override
    public int compareTo(CheckpointOwnerId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
