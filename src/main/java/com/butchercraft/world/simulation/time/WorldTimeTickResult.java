package com.butchercraft.world.simulation.time;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

public record WorldTimeTickResult(
        WorldTimeState state,
        WorldTimeStatusSnapshot snapshot,
        OptionalLong dayTimeToPublish,
        List<WorldTimeFailure> failures,
        boolean shouldSynchronizeClients
) {
    public WorldTimeTickResult {
        state = Objects.requireNonNull(state, "state");
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        dayTimeToPublish = Objects.requireNonNull(dayTimeToPublish, "dayTimeToPublish");
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
    }

    public static WorldTimeTickResult success(
            WorldTimeState state,
            WorldTimeStatusSnapshot snapshot,
            OptionalLong dayTimeToPublish,
            boolean shouldSynchronizeClients
    ) {
        return new WorldTimeTickResult(state, snapshot, dayTimeToPublish, List.of(), shouldSynchronizeClients);
    }

    public static WorldTimeTickResult failure(
            WorldTimeState state,
            WorldTimeStatusSnapshot snapshot,
            List<WorldTimeFailure> failures
    ) {
        return new WorldTimeTickResult(state, snapshot, OptionalLong.empty(), failures, true);
    }

    public boolean successful() {
        return failures.isEmpty();
    }
}
