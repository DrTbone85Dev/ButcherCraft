package com.butchercraft.world.planning;

import com.butchercraft.persistence.StrictJsonPersistence;
import com.google.gson.Gson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class PlanningRecoveryStorage {
    private static final String LABEL = "Planning recovery authority persistence";
    private static final Gson GSON = StrictJsonPersistence.gson();
    private final Path filePath;

    public PlanningRecoveryStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Optional<PlanningRecoveryState> loadExisting() {
        StrictJsonPersistence.requireNoInterruptedPublication(filePath, LABEL);
        if (!Files.exists(filePath)) return Optional.empty();
        return Optional.of(deserialize(StrictJsonPersistence.read(filePath, LABEL)));
    }

    public String serialize(PlanningRecoveryState state) {
        return GSON.toJson(Objects.requireNonNull(state, "state")) + System.lineSeparator();
    }

    public PlanningRecoveryState deserialize(String json) {
        try {
            return Objects.requireNonNull(GSON.fromJson(json, PlanningRecoveryState.class), LABEL);
        } catch (RuntimeException exception) {
            throw StrictJsonPersistence.corrupt(LABEL, exception);
        }
    }
}
