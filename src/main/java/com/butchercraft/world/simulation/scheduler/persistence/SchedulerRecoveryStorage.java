package com.butchercraft.world.simulation.scheduler.persistence;

import com.butchercraft.persistence.StrictJsonPersistence;
import com.butchercraft.world.simulation.scheduler.SchedulerRecoveryState;
import com.google.gson.Gson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class SchedulerRecoveryStorage {
    private static final String LABEL = "Scheduler recovery-state persistence";
    private static final Gson GSON = StrictJsonPersistence.gson();
    private final Path filePath;

    public SchedulerRecoveryStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Optional<SchedulerRecoveryState> loadExisting() {
        StrictJsonPersistence.requireNoInterruptedPublication(filePath, LABEL);
        if (!Files.exists(filePath)) return Optional.empty();
        return Optional.of(deserialize(StrictJsonPersistence.read(filePath, LABEL)));
    }

    public String serialize(SchedulerRecoveryState state) {
        return GSON.toJson(Objects.requireNonNull(state, "state")) + System.lineSeparator();
    }

    public SchedulerRecoveryState deserialize(String json) {
        try {
            return Objects.requireNonNull(GSON.fromJson(json, SchedulerRecoveryState.class), LABEL);
        } catch (RuntimeException exception) {
            throw StrictJsonPersistence.corrupt(LABEL, exception);
        }
    }
}
