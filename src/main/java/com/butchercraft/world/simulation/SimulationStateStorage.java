package com.butchercraft.world.simulation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SimulationStateStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private static final String SCHEMA_VERSION = "schema_version";
    private static final String SIMULATION_TICK = "simulation_tick";
    private static final String CALENDAR = "calendar";
    private static final String PENDING_EVENTS = "pending_scheduled_events";
    private static final String DAY = "day";
    private static final String WEEK = "week";
    private static final String MONTH = "month";
    private static final String YEAR = "year";
    private static final String WEEKDAY = "weekday";
    private static final String SEASON = "season";
    private static final String ID = "id";
    private static final String SCHEDULED_SIMULATION_TICK = "scheduled_simulation_tick";
    private static final String EVENT_TYPE = "event_type";
    private static final String PAYLOAD_REFERENCE = "payload_reference";
    private static final String EXECUTION_STATUS = "execution_status";

    private final Path filePath;
    private final SimulationConfiguration configuration;

    public SimulationStateStorage(Path filePath, SimulationConfiguration configuration) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public Path filePath() {
        return filePath;
    }

    public Optional<SimulationState> load() {
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(deserialize(Files.readString(filePath, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load simulation state from " + filePath, exception);
        }
    }

    public void save(SimulationState state) {
        Objects.requireNonNull(state, "state").validate(configuration);
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporaryFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.writeString(temporaryFile, serialize(state), StandardCharsets.UTF_8);
            moveIntoPlace(temporaryFile);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save simulation state to " + filePath, exception);
        }
    }

    public String serialize(SimulationState state) {
        Objects.requireNonNull(state, "state").validate(configuration);
        JsonObject root = new JsonObject();
        root.addProperty(SCHEMA_VERSION, state.schemaVersion());
        root.addProperty(SIMULATION_TICK, state.simulationTick());
        root.add(CALENDAR, saveCalendar(state.calendar()));
        JsonArray events = new JsonArray();
        for (ScheduledSimulationEvent event : SimulationScheduler.of(state.pendingEvents(), state.simulationTick()).pendingEvents()) {
            events.add(saveEvent(event));
        }
        root.add(PENDING_EVENTS, events);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public SimulationState deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = requireObject(JsonParser.parseString(json), "simulation state root");
            int schemaVersion = requireInt(root, SCHEMA_VERSION);
            if (schemaVersion != SimulationSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported simulation schema version: " + schemaVersion);
            }
            long simulationTick = requireLong(root, SIMULATION_TICK);
            SimulationCalendar calendar = loadCalendar(requireObject(requireField(root, CALENDAR), CALENDAR));
            JsonArray eventArray = requireArray(root, PENDING_EVENTS);
            List<ScheduledSimulationEvent> events = new ArrayList<>();
            for (JsonElement element : eventArray) {
                events.add(loadEvent(requireObject(element, "scheduled simulation event")));
            }
            SimulationState state = new SimulationState(schemaVersion, simulationTick, calendar, events);
            state.validate(configuration);
            return state;
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt simulation state persistence", exception);
        }
    }

    private JsonObject saveCalendar(SimulationCalendar calendar) {
        JsonObject object = new JsonObject();
        object.addProperty(DAY, calendar.day());
        object.addProperty(WEEK, calendar.week());
        object.addProperty(MONTH, calendar.month());
        object.addProperty(YEAR, calendar.year());
        object.addProperty(WEEKDAY, calendar.weekday());
        object.addProperty(SEASON, calendar.season().serializedName());
        return object;
    }

    private SimulationCalendar loadCalendar(JsonObject object) {
        SimulationCalendar calendar = new SimulationCalendar(
                requireInt(object, DAY),
                requireInt(object, WEEK),
                requireInt(object, MONTH),
                requireInt(object, YEAR),
                requireInt(object, WEEKDAY),
                Season.fromSerializedName(requireString(object, SEASON))
        );
        calendar.validate(configuration);
        return calendar;
    }

    private JsonObject saveEvent(ScheduledSimulationEvent event) {
        JsonObject object = new JsonObject();
        object.addProperty(ID, event.id());
        object.addProperty(SCHEDULED_SIMULATION_TICK, event.scheduledSimulationTick());
        object.addProperty(EVENT_TYPE, event.eventType().serializedName());
        object.addProperty(PAYLOAD_REFERENCE, event.payloadReference());
        object.addProperty(EXECUTION_STATUS, event.executionStatus().serializedName());
        return object;
    }

    private ScheduledSimulationEvent loadEvent(JsonObject object) {
        return new ScheduledSimulationEvent(
                requireString(object, ID),
                requireLong(object, SCHEDULED_SIMULATION_TICK),
                SimulationEventType.fromSerializedName(requireString(object, EVENT_TYPE)),
                requireString(object, PAYLOAD_REFERENCE),
                SimulationEventStatus.fromSerializedName(requireString(object, EXECUTION_STATUS))
        );
    }

    private void moveIntoPlace(Path temporaryFile) throws IOException {
        try {
            Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static JsonObject requireObject(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Expected JSON object for " + label);
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("Simulation field must be an array: " + fieldName);
        }
        return element.getAsJsonArray();
    }

    private static int requireInt(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Simulation field must be a number: " + fieldName);
        }
        return element.getAsInt();
    }

    private static long requireLong(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Simulation field must be a number: " + fieldName);
        }
        return element.getAsLong();
    }

    private static String requireString(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Simulation field must be a string: " + fieldName);
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Simulation field must not be blank: " + fieldName);
        }
        return value;
    }

    private static JsonElement requireField(JsonObject object, String fieldName) {
        JsonElement element = object.get(fieldName);
        if (element == null) {
            throw new IllegalArgumentException("Missing simulation field: " + fieldName);
        }
        return element;
    }
}
