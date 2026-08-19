package com.butchercraft.world.execution.persistence;

import com.butchercraft.persistence.StrictJsonPersistence;
import com.butchercraft.world.execution.MachineRunRegistry;
import com.google.gson.Gson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class MachineRunStorage {
    private static final String LABEL = "Execution Machine Run persistence";
    private static final Gson GSON = StrictJsonPersistence.gson();

    private final Path filePath;

    public MachineRunStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Path filePath() {
        return filePath;
    }

    public Optional<MachineRunRegistry> loadExisting() {
        StrictJsonPersistence.requireNoInterruptedPublication(filePath, LABEL);
        if (!Files.exists(filePath)) return Optional.empty();
        return Optional.of(deserialize(StrictJsonPersistence.read(filePath, LABEL)));
    }

    public void save(MachineRunRegistry registry) {
        String json = serialize(registry);
        StrictJsonPersistence.publish(filePath, json, LABEL);
        if (!deserialize(StrictJsonPersistence.read(filePath, LABEL)).equals(registry)) {
            throw new IllegalStateException("Execution Machine Run persistence failed semantic read-back verification");
        }
    }

    public String serialize(MachineRunRegistry registry) {
        return GSON.toJson(Objects.requireNonNull(registry, "registry")) + System.lineSeparator();
    }

    public MachineRunRegistry deserialize(String json) {
        try {
            return Objects.requireNonNull(GSON.fromJson(json, MachineRunRegistry.class), "Machine Run root");
        } catch (RuntimeException exception) {
            throw StrictJsonPersistence.corrupt(LABEL, exception);
        }
    }
}
