package com.butchercraft.world.business.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

public final class BusinessRuntimeCalendarStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();
    private static final String SCHEMA_VERSION = "schema_version";

    private final Path filePath;

    public BusinessRuntimeCalendarStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath").toAbsolutePath().normalize();
    }

    public Optional<BusinessRuntimeCalendarState> load() {
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(deserialize(Files.readString(filePath, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load Business Calendar Runtime state", exception);
        }
    }

    public void save(BusinessRuntimeCalendarState state) {
        Objects.requireNonNull(state, "state");
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.writeString(temporary, serialize(state), StandardCharsets.UTF_8);
            moveIntoPlace(temporary);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save Business Calendar Runtime state", exception);
        }
    }

    public String serialize(BusinessRuntimeCalendarState state) {
        JsonObject object = new JsonObject();
        object.addProperty(SCHEMA_VERSION, state.schemaVersion());
        object.addProperty("operating_schedule_identity", state.operatingScheduleIdentity().value());
        object.addProperty("shift_set_identity", state.shiftSetIdentity().value());
        object.addProperty("configuration_identity", state.configurationIdentity().value());
        object.addProperty("last_observed_world_day_identity", state.lastObservedWorldDayIdentity());
        object.addProperty("last_observed_open", state.lastObservedOpen());
        optionalString(object, "last_active_shift_identity", state.lastActiveShiftIdentity());
        optionalString(object, "last_evaluated_boundary", state.lastEvaluatedBoundary());
        object.addProperty("last_movement_classification", state.lastMovementClassification());
        return GSON.toJson(object) + System.lineSeparator();
    }

    public BusinessRuntimeCalendarState deserialize(String json) {
        try {
            JsonObject object = object(JsonParser.parseString(Objects.requireNonNull(json, "json")),
                    "business runtime calendar state");
            int schema = intValue(object, SCHEMA_VERSION);
            if (schema != BusinessRuntimeCalendarSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported business runtime calendar state schema: " + schema);
            }
            return new BusinessRuntimeCalendarState(
                    schema,
                    new BusinessOperatingScheduleIdentity(string(object, "operating_schedule_identity")),
                    new BusinessShiftSetIdentity(string(object, "shift_set_identity")),
                    new BusinessRuntimeConfigurationIdentity(string(object, "configuration_identity")),
                    string(object, "last_observed_world_day_identity"),
                    bool(object, "last_observed_open"),
                    optionalString(object, "last_active_shift_identity"),
                    optionalString(object, "last_evaluated_boundary"),
                    string(object, "last_movement_classification")
            );
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt Business Calendar Runtime state", exception);
        }
    }

    private void moveIntoPlace(Path temporaryFile) throws IOException {
        try {
            Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void optionalString(JsonObject object, String name, Optional<String> value) {
        if (value.isPresent()) {
            object.addProperty(name, value.orElseThrow());
        } else {
            object.add(name, JsonNull.INSTANCE);
        }
    }

    private static Optional<String> optionalString(JsonObject object, String name) {
        JsonElement element = field(object, name);
        return element.isJsonNull() ? Optional.empty() : Optional.of(string(element, name));
    }

    private static String string(JsonObject object, String name) {
        return string(field(object, name), name);
    }

    private static String string(JsonElement element, String name) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Business runtime calendar field must be a string: " + name);
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Business runtime calendar field must not be blank: " + name);
        }
        return value;
    }

    private static int intValue(JsonObject object, String name) {
        JsonElement element = field(object, name);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Business runtime calendar field must be a number: " + name);
        }
        return element.getAsInt();
    }

    private static boolean bool(JsonObject object, String name) {
        JsonElement element = field(object, name);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Business runtime calendar field must be a boolean: " + name);
        }
        return element.getAsBoolean();
    }

    private static JsonObject object(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Expected JSON object for " + label);
        }
        return element.getAsJsonObject();
    }

    private static JsonElement field(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null) {
            throw new IllegalArgumentException("Missing business runtime calendar field: " + name);
        }
        return element;
    }
}
