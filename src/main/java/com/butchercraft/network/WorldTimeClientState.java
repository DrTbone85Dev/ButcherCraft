package com.butchercraft.network;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldTimeClientState {
    private static final AtomicReference<WorldTimeClientSnapshot> LATEST = new AtomicReference<>();

    private WorldTimeClientState() {
    }

    public static void accept(WorldTimeClientSnapshot snapshot) {
        LATEST.set(snapshot);
    }

    public static Optional<WorldTimeClientSnapshot> latest() {
        return Optional.ofNullable(LATEST.get());
    }

    public static void clear() {
        LATEST.set(null);
    }
}
