package com.butchercraft.workstation.operation.persistence;

import com.butchercraft.persistence.StrictJsonPersistence;
import com.butchercraft.workstation.operation.MachineOperatingRegistry;
import com.google.gson.Gson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class MachineOperatingStorage {
    private static final String LABEL = "Workstation machine operating-state persistence";
    private static final Gson GSON = StrictJsonPersistence.gson();

    private final Path filePath;

    public MachineOperatingStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Path filePath() {
        return filePath;
    }

    public Optional<MachineOperatingRegistry> loadExisting() {
        StrictJsonPersistence.requireNoInterruptedPublication(filePath, LABEL);
        if (!Files.exists(filePath)) return Optional.empty();
        return Optional.of(deserialize(StrictJsonPersistence.read(filePath, LABEL)));
    }

    public void save(MachineOperatingRegistry registry) {
        String json = serialize(registry);
        StrictJsonPersistence.publish(filePath, json, LABEL);
        if (!deserialize(StrictJsonPersistence.read(filePath, LABEL)).equals(registry)) {
            throw new IllegalStateException("Machine operating-state persistence failed semantic read-back verification");
        }
    }

    public String serialize(MachineOperatingRegistry registry) {
        return GSON.toJson(Objects.requireNonNull(registry, "registry")) + System.lineSeparator();
    }

    public MachineOperatingRegistry deserialize(String json) {
        try {
            return Objects.requireNonNull(GSON.fromJson(json, MachineOperatingRegistry.class),
                    "Machine operating-state root");
        } catch (RuntimeException exception) {
            throw StrictJsonPersistence.corrupt(LABEL, exception);
        }
    }
}
