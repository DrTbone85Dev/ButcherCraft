package com.butchercraft.workstation.reservation.persistence;

import com.butchercraft.workstation.reservation.WorkstationReservationDirectory;
import com.butchercraft.workstation.reservation.WorkstationReservationRecord;
import com.butchercraft.workstation.reservation.WorkstationReservationSchema;
import com.butchercraft.workstation.reservation.WorkstationReservationState;
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
import java.util.OptionalLong;

public final class WorkstationReservationStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private static final String SCHEMA_VERSION = "schema_version";
    private static final String RESERVATIONS = "reservations";
    private static final String WORKSTATION_IDENTITY = "workstation_identity";
    private static final String WORKSTATION_TYPE = "workstation_type";
    private static final String EMPLOYEE_IDENTITY = "employee_identity";
    private static final String STATE = "state";
    private static final String CREATED_TICK = "created_tick";
    private static final String EXPIRATION_TICK = "expiration_tick";
    private static final String INVALIDATION_REASON = "invalidation_reason";
    private static final String DIMENSION_IDENTITY = "dimension_identity";
    private static final String WORKSTATION_X = "workstation_x";
    private static final String WORKSTATION_Y = "workstation_y";
    private static final String WORKSTATION_Z = "workstation_z";
    private static final String OPERATING_X = "operating_x";
    private static final String OPERATING_Y = "operating_y";
    private static final String OPERATING_Z = "operating_z";
    private static final String ANCHOR_RADIUS = "anchor_radius";

    private final Path filePath;

    public WorkstationReservationStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Path filePath() {
        return filePath;
    }

    public WorkstationReservationDirectory load() {
        if (!Files.exists(filePath)) {
            return WorkstationReservationDirectory.empty();
        }
        try {
            return deserialize(Files.readString(filePath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load workstation reservations from " + filePath, exception);
        }
    }

    public void save(WorkstationReservationDirectory directory) {
        Objects.requireNonNull(directory, "directory");
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporaryFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.writeString(temporaryFile, serialize(directory), StandardCharsets.UTF_8);
            moveIntoPlace(temporaryFile);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save workstation reservations to " + filePath, exception);
        }
    }

    public String serialize(WorkstationReservationDirectory directory) {
        JsonObject root = new JsonObject();
        root.addProperty(SCHEMA_VERSION, WorkstationReservationSchema.CURRENT_VERSION);
        JsonArray reservations = new JsonArray();
        for (WorkstationReservationRecord record : Objects.requireNonNull(directory, "directory").records()) {
            reservations.add(serializeRecord(record));
        }
        root.add(RESERVATIONS, reservations);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public WorkstationReservationDirectory deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = requireObject(JsonParser.parseString(json), "workstation reservation root");
            int schemaVersion = requireInt(root, SCHEMA_VERSION);
            if (schemaVersion != WorkstationReservationSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported workstation reservation schema version: " + schemaVersion);
            }
            List<WorkstationReservationRecord> records = new ArrayList<>();
            for (JsonElement element : requireArray(root, RESERVATIONS)) {
                records.add(deserializeRecord(requireObject(element, "workstation reservation record")));
            }
            return WorkstationReservationDirectory.of(records);
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt workstation reservation persistence", exception);
        }
    }

    private JsonObject serializeRecord(WorkstationReservationRecord record) {
        JsonObject object = new JsonObject();
        object.addProperty(SCHEMA_VERSION, record.schemaVersion());
        object.addProperty(WORKSTATION_IDENTITY, record.workstationIdentity());
        object.addProperty(WORKSTATION_TYPE, record.workstationType());
        object.addProperty(EMPLOYEE_IDENTITY, record.employeeIdentity());
        object.addProperty(STATE, record.state().serializedName());
        object.addProperty(CREATED_TICK, record.createdTick());
        record.expirationTick().ifPresent(value -> object.addProperty(EXPIRATION_TICK, value));
        record.invalidationReason().ifPresent(value -> object.addProperty(INVALIDATION_REASON, value));
        object.addProperty(DIMENSION_IDENTITY, record.dimensionIdentity());
        object.addProperty(WORKSTATION_X, record.workstationX());
        object.addProperty(WORKSTATION_Y, record.workstationY());
        object.addProperty(WORKSTATION_Z, record.workstationZ());
        object.addProperty(OPERATING_X, record.operatingX());
        object.addProperty(OPERATING_Y, record.operatingY());
        object.addProperty(OPERATING_Z, record.operatingZ());
        object.addProperty(ANCHOR_RADIUS, record.anchorRadius());
        return object;
    }

    private WorkstationReservationRecord deserializeRecord(JsonObject object) {
        return new WorkstationReservationRecord(
                requireInt(object, SCHEMA_VERSION),
                requireString(object, WORKSTATION_IDENTITY),
                requireString(object, WORKSTATION_TYPE),
                requireString(object, EMPLOYEE_IDENTITY),
                WorkstationReservationState.fromSerializedName(requireString(object, STATE)),
                requireLong(object, CREATED_TICK),
                optionalLong(object, EXPIRATION_TICK),
                optionalString(object, INVALIDATION_REASON),
                requireString(object, DIMENSION_IDENTITY),
                requireInt(object, WORKSTATION_X),
                requireInt(object, WORKSTATION_Y),
                requireInt(object, WORKSTATION_Z),
                requireInt(object, OPERATING_X),
                requireInt(object, OPERATING_Y),
                requireInt(object, OPERATING_Z),
                requireInt(object, ANCHOR_RADIUS)
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
            throw new IllegalArgumentException(label + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return element.getAsJsonArray();
    }

    private static String requireString(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return element.getAsString();
    }

    private static Optional<String> optionalString(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        if (!element.isJsonPrimitive()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return Optional.of(element.getAsString());
    }

    private static int requireInt(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return element.getAsInt();
    }

    private static long requireLong(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return element.getAsLong();
    }

    private static OptionalLong optionalLong(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) {
            return OptionalLong.empty();
        }
        if (!element.isJsonPrimitive()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return OptionalLong.of(element.getAsLong());
    }
}
