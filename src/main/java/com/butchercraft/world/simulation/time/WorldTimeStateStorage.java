package com.butchercraft.world.simulation.time;

import com.butchercraft.persistence.AtomicFilePublication;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class WorldTimeStateStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private static final String SCHEMA_VERSION = "schema_version";
    private static final String CONFIGURATION_IDENTITY = "configuration_identity";
    private static final String ACCUMULATOR_REMAINDER_NUMERATOR = "accumulator_remainder_numerator";
    private static final String LAST_OBSERVED_RAW_DAY_TIME = "last_observed_raw_day_time";
    private static final String LAST_EXPECTED_SCALED_DAY_TIME = "last_expected_scaled_day_time";
    private static final String SOURCE_DIMENSION_IDENTITY = "source_dimension_identity";
    private static final String LAST_OBSERVATION_GAME_TIME = "last_observation_game_time";
    private static final String LAST_MOVEMENT_CLASSIFICATION = "last_movement_classification";
    private static final String CONSECUTIVE_UNEXPECTED_CHANGES = "consecutive_unexpected_changes";
    private static final String EXTERNAL_CONFLICT_DETECTED = "external_conflict_detected";

    private final Path filePath;
    private final WorldTimeConfiguration configuration;

    public WorldTimeStateStorage(Path filePath, WorldTimeConfiguration configuration) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public Optional<WorldTimeState> load() {
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        WorldTimeState state = deserialize(AtomicFilePublication.readUtf8(filePath, "World time state"));
        state.validate(configuration);
        return Optional.of(state.restored());
    }

    public void save(WorldTimeState state) {
        Objects.requireNonNull(state, "state").validate(configuration);
        AtomicFilePublication.publishUtf8(filePath, serialize(state), "World time state");
    }

    public String serialize(WorldTimeState state) {
        Objects.requireNonNull(state, "state").validate(configuration);
        JsonObject root = new JsonObject();
        root.addProperty(SCHEMA_VERSION, state.schemaVersion());
        root.addProperty(CONFIGURATION_IDENTITY, state.configurationIdentity());
        root.addProperty(ACCUMULATOR_REMAINDER_NUMERATOR, state.accumulatorRemainderNumerator());
        root.addProperty(LAST_OBSERVED_RAW_DAY_TIME, state.lastObservedRawDayTime());
        root.addProperty(LAST_EXPECTED_SCALED_DAY_TIME, state.lastExpectedScaledDayTime());
        root.addProperty(SOURCE_DIMENSION_IDENTITY, state.sourceDimensionIdentity());
        root.addProperty(LAST_OBSERVATION_GAME_TIME, state.lastObservationGameTime());
        root.addProperty(LAST_MOVEMENT_CLASSIFICATION, state.lastMovementClassification().serializedName());
        root.addProperty(CONSECUTIVE_UNEXPECTED_CHANGES, state.consecutiveUnexpectedChanges());
        root.addProperty(EXTERNAL_CONFLICT_DETECTED, state.externalConflictDetected());
        return GSON.toJson(root) + System.lineSeparator();
    }

    public WorldTimeState deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = requireObject(JsonParser.parseString(json), "world time state root");
            int schemaVersion = requireInt(root, SCHEMA_VERSION);
            if (schemaVersion != WorldTimeSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported world time schema version: " + schemaVersion);
            }
            WorldTimeState state = new WorldTimeState(
                    schemaVersion,
                    requireString(root, CONFIGURATION_IDENTITY),
                    requireLong(root, ACCUMULATOR_REMAINDER_NUMERATOR),
                    requireLong(root, LAST_OBSERVED_RAW_DAY_TIME),
                    requireLong(root, LAST_EXPECTED_SCALED_DAY_TIME),
                    requireString(root, SOURCE_DIMENSION_IDENTITY),
                    requireLong(root, LAST_OBSERVATION_GAME_TIME),
                    WorldTimeMovementClassification.fromSerializedName(requireString(root, LAST_MOVEMENT_CLASSIFICATION)),
                    requireInt(root, CONSECUTIVE_UNEXPECTED_CHANGES),
                    requireBoolean(root, EXTERNAL_CONFLICT_DETECTED)
            );
            state.validate(configuration);
            return state;
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt world time state persistence", exception);
        }
    }

    private static JsonObject requireObject(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Expected JSON object for " + label);
        }
        return element.getAsJsonObject();
    }

    private static int requireInt(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("World time field must be a number: " + fieldName);
        }
        return element.getAsInt();
    }

    private static long requireLong(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("World time field must be a number: " + fieldName);
        }
        return element.getAsLong();
    }

    private static boolean requireBoolean(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("World time field must be a boolean: " + fieldName);
        }
        return element.getAsBoolean();
    }

    private static String requireString(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("World time field must be a string: " + fieldName);
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("World time field must not be blank: " + fieldName);
        }
        return value;
    }

    private static JsonElement requireField(JsonObject object, String fieldName) {
        JsonElement element = object.get(fieldName);
        if (element == null) {
            throw new IllegalArgumentException("Missing world time field: " + fieldName);
        }
        return element;
    }
}
