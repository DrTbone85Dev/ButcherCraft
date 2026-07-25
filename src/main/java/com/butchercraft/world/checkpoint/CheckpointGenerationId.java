package com.butchercraft.world.checkpoint;

import java.util.Objects;

public record CheckpointGenerationId(
        int schemaVersion,
        long committedSequence,
        long authoritativeSimulationTick
) implements Comparable<CheckpointGenerationId> {
    public CheckpointGenerationId {
        schemaVersion = CheckpointValidation.positive(schemaVersion, "schemaVersion");
        committedSequence = CheckpointValidation.positive(committedSequence, "committedSequence");
        authoritativeSimulationTick = CheckpointValidation.nonNegative(
                authoritativeSimulationTick,
                "authoritativeSimulationTick"
        );
    }

    public static CheckpointGenerationId of(long committedSequence, long authoritativeSimulationTick) {
        return new CheckpointGenerationId(
                CheckpointSchema.CURRENT_VERSION,
                committedSequence,
                authoritativeSimulationTick
        );
    }

    public String canonicalValue() {
        return "butchercraft:checkpoint/%020d/%d".formatted(committedSequence, authoritativeSimulationTick);
    }

    @Override
    public int compareTo(CheckpointGenerationId other) {
        Objects.requireNonNull(other, "other");
        int sequenceComparison = Long.compare(committedSequence, other.committedSequence);
        if (sequenceComparison != 0) {
            return sequenceComparison;
        }
        int tickComparison = Long.compare(authoritativeSimulationTick, other.authoritativeSimulationTick);
        if (tickComparison != 0) {
            return tickComparison;
        }
        return Integer.compare(schemaVersion, other.schemaVersion);
    }

    @Override
    public String toString() {
        return canonicalValue();
    }
}
