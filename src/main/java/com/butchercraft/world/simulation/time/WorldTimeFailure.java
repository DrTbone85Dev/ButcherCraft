package com.butchercraft.world.simulation.time;

import java.util.Objects;

public record WorldTimeFailure(WorldTimeFailureCode code, String message) {
    public WorldTimeFailure {
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message").strip();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("World time failure message must not be blank");
        }
    }

    public static WorldTimeFailure of(WorldTimeFailureCode code, String message) {
        return new WorldTimeFailure(code, message);
    }
}
