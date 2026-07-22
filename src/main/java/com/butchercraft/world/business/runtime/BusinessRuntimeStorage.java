package com.butchercraft.world.business.runtime;

import com.butchercraft.world.business.BusinessId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

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

public final class BusinessRuntimeStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    private static final String SCHEMA_VERSION = "schema_version";
    private static final String RUNTIME_STATES = "business_runtime_states";
    private static final String BUSINESS_ID = "business_id";
    private static final String OPERATIONAL_STATUS = "operational_status";
    private static final String OPEN = "open";
    private static final String ACTIVE_SHIFT_ID = "active_shift_id";
    private static final String WORKFORCE_CAPACITY = "workforce_capacity";
    private static final String ACTIVE_WORKFORCE = "active_workforce";
    private static final String MAINTENANCE = "maintenance";
    private static final String LAST_STATE_CHANGE = "last_state_change_simulation_tick";
    private static final String BUSINESS_HOURS = "business_hours";
    private static final String OPENING_HOUR = "opening_hour";
    private static final String OPENING_MINUTE = "opening_minute";
    private static final String CLOSING_HOUR = "closing_hour";
    private static final String CLOSING_MINUTE = "closing_minute";
    private static final String OPERATING_WEEKDAYS = "operating_weekdays";
    private static final String SHIFTS = "shifts";
    private static final String SHIFT_ID = "id";
    private static final String START_HOUR = "start_hour";
    private static final String START_MINUTE = "start_minute";
    private static final String END_HOUR = "end_hour";
    private static final String END_MINUTE = "end_minute";
    private static final String EXPECTED_WORKFORCE = "expected_workforce";
    private static final String ACTIVE = "active";

    private final Path filePath;

    public BusinessRuntimeStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Path filePath() {
        return filePath;
    }

    public BusinessRuntimeRegistry load() {
        if (!Files.exists(filePath)) {
            return BusinessRuntimeRegistry.empty();
        }
        try {
            return deserialize(Files.readString(filePath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load business runtime from " + filePath, exception);
        }
    }

    public void save(BusinessRuntimeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporaryFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.writeString(temporaryFile, serialize(registry), StandardCharsets.UTF_8);
            moveIntoPlace(temporaryFile);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save business runtime to " + filePath, exception);
        }
    }

    public String serialize(BusinessRuntimeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        JsonObject root = new JsonObject();
        root.addProperty(SCHEMA_VERSION, BusinessRuntimeSchema.CURRENT_VERSION);
        JsonArray states = new JsonArray();
        for (BusinessRuntimeState state : registry.states()) {
            states.add(serializeState(state));
        }
        root.add(RUNTIME_STATES, states);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public BusinessRuntimeRegistry deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = requireObject(JsonParser.parseString(json), "business runtime root");
            int schemaVersion = requireInt(root, SCHEMA_VERSION);
            if (schemaVersion != BusinessRuntimeSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported business runtime schema version: " + schemaVersion);
            }
            JsonArray states = requireArray(root, RUNTIME_STATES);
            List<BusinessRuntimeState> records = new ArrayList<>();
            for (JsonElement element : states) {
                records.add(deserializeState(requireObject(element, "business runtime state")));
            }
            return BusinessRuntimeRegistry.of(records);
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt business runtime persistence", exception);
        }
    }

    private JsonObject serializeState(BusinessRuntimeState state) {
        JsonObject object = new JsonObject();
        object.addProperty(SCHEMA_VERSION, state.schemaVersion());
        object.addProperty(BUSINESS_ID, state.businessId().value());
        object.addProperty(OPERATIONAL_STATUS, state.operationalStatus().serializedName());
        object.addProperty(OPEN, state.open());
        addNullable(object, ACTIVE_SHIFT_ID, state.activeShiftId());
        object.addProperty(WORKFORCE_CAPACITY, state.workforceCapacity());
        object.addProperty(ACTIVE_WORKFORCE, state.activeWorkforce());
        object.addProperty(MAINTENANCE, state.maintenance());
        object.addProperty(LAST_STATE_CHANGE, state.lastStateChangeSimulationTick());
        object.add(BUSINESS_HOURS, serializeHours(state.businessHours()));
        JsonArray shifts = new JsonArray();
        for (BusinessShift shift : state.shifts()) {
            shifts.add(serializeShift(shift));
        }
        object.add(SHIFTS, shifts);
        return object;
    }

    private BusinessRuntimeState deserializeState(JsonObject object) {
        int schemaVersion = requireInt(object, SCHEMA_VERSION);
        if (schemaVersion != BusinessRuntimeSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported business runtime state schema version: " + schemaVersion);
        }
        return new BusinessRuntimeState(
                new BusinessId(requireString(object, BUSINESS_ID)),
                BusinessOperationalStatus.fromSerializedName(requireString(object, OPERATIONAL_STATUS)),
                requireBoolean(object, OPEN),
                optionalString(object, ACTIVE_SHIFT_ID),
                requireInt(object, WORKFORCE_CAPACITY),
                requireInt(object, ACTIVE_WORKFORCE),
                requireBoolean(object, MAINTENANCE),
                requireLong(object, LAST_STATE_CHANGE),
                deserializeHours(requireObject(requireField(object, BUSINESS_HOURS), BUSINESS_HOURS)),
                deserializeShifts(requireArray(object, SHIFTS)),
                schemaVersion
        );
    }

    private JsonObject serializeHours(BusinessHours hours) {
        JsonObject object = new JsonObject();
        object.addProperty(OPENING_HOUR, hours.openingHour());
        object.addProperty(OPENING_MINUTE, hours.openingMinute());
        object.addProperty(CLOSING_HOUR, hours.closingHour());
        object.addProperty(CLOSING_MINUTE, hours.closingMinute());
        JsonArray weekdays = new JsonArray();
        for (Integer weekday : hours.operatingWeekdays()) {
            weekdays.add(weekday);
        }
        object.add(OPERATING_WEEKDAYS, weekdays);
        return object;
    }

    private BusinessHours deserializeHours(JsonObject object) {
        JsonArray weekdays = requireArray(object, OPERATING_WEEKDAYS);
        List<Integer> operatingWeekdays = new ArrayList<>();
        for (JsonElement element : weekdays) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Business operating weekday must be a number");
            }
            operatingWeekdays.add(element.getAsInt());
        }
        return new BusinessHours(
                requireInt(object, OPENING_HOUR),
                requireInt(object, OPENING_MINUTE),
                requireInt(object, CLOSING_HOUR),
                requireInt(object, CLOSING_MINUTE),
                operatingWeekdays
        );
    }

    private JsonObject serializeShift(BusinessShift shift) {
        JsonObject object = new JsonObject();
        object.addProperty(SHIFT_ID, shift.id());
        object.addProperty(START_HOUR, shift.startHour());
        object.addProperty(START_MINUTE, shift.startMinute());
        object.addProperty(END_HOUR, shift.endHour());
        object.addProperty(END_MINUTE, shift.endMinute());
        object.addProperty(EXPECTED_WORKFORCE, shift.expectedWorkforce());
        object.addProperty(ACTIVE, shift.active());
        return object;
    }

    private List<BusinessShift> deserializeShifts(JsonArray array) {
        List<BusinessShift> shifts = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject object = requireObject(element, "business shift");
            shifts.add(new BusinessShift(
                    requireString(object, SHIFT_ID),
                    requireInt(object, START_HOUR),
                    requireInt(object, START_MINUTE),
                    requireInt(object, END_HOUR),
                    requireInt(object, END_MINUTE),
                    requireInt(object, EXPECTED_WORKFORCE),
                    requireBoolean(object, ACTIVE)
            ));
        }
        return shifts;
    }

    private void moveIntoPlace(Path temporaryFile) throws IOException {
        try {
            Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void addNullable(JsonObject object, String fieldName, Optional<String> value) {
        object.add(fieldName, value.<JsonElement>map(JsonPrimitive::new).orElse(JsonNull.INSTANCE));
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
            throw new IllegalArgumentException("Business runtime field must be an array: " + fieldName);
        }
        return element.getAsJsonArray();
    }

    private static int requireInt(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Business runtime field must be a number: " + fieldName);
        }
        return element.getAsInt();
    }

    private static long requireLong(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Business runtime field must be a number: " + fieldName);
        }
        return element.getAsLong();
    }

    private static boolean requireBoolean(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Business runtime field must be a boolean: " + fieldName);
        }
        return element.getAsBoolean();
    }

    private static String requireString(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Business runtime field must be a string: " + fieldName);
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Business runtime field must not be blank: " + fieldName);
        }
        return value;
    }

    private static Optional<String> optionalString(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (element.isJsonNull()) {
            return Optional.empty();
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Business runtime field must be null or a string: " + fieldName);
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Business runtime optional field must not be blank: " + fieldName);
        }
        return Optional.of(value);
    }

    private static JsonElement requireField(JsonObject object, String fieldName) {
        JsonElement element = object.get(fieldName);
        if (element == null) {
            throw new IllegalArgumentException("Missing business runtime field: " + fieldName);
        }
        return element;
    }
}
