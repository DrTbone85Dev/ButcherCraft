package com.butchercraft.world.workforce.employee;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;
import com.butchercraft.world.workforce.PositionId;
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
import java.util.UUID;

public final class EmployeeStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private static final String SCHEMA_VERSION = "schema_version";
    private static final String NEXT_SEQUENCE = "next_sequence";
    private static final String EMPLOYEE_RECORDS = "employee_records";
    private static final String EMPLOYEE_ID = "employee_id";
    private static final String BUSINESS_ID = "business_id";
    private static final String SEQUENCE = "sequence";
    private static final String WORLD_IDENTITY_ROOT = "world_identity_root";
    private static final String WORLD_IDENTITY_ROOT_DIGEST = "world_identity_root_digest";
    private static final String DISPLAY_NAME = "display_name";
    private static final String PREFERRED_NAME = "preferred_name";
    private static final String STATUS = "status";
    private static final String PRESENCE_STATE = "presence_state";
    private static final String ASSIGNED_SHIFT = "assigned_shift";
    private static final String SHIFT_ID = "shift_id";
    private static final String SHIFT_IDENTITY = "shift_identity";
    private static final String SHIFT_DISPLAY_NAME = "shift_display_name";
    private static final String SHIFT_SET_IDENTITY = "shift_set_identity";
    private static final String CONFIGURATION_IDENTITY = "configuration_identity";
    private static final String ASSIGNED_POSITION_ID = "assigned_position_id";
    private static final String HIRE_BUSINESS_DAY = "hire_business_day";
    private static final String HIRE_BUSINESS_TIME = "hire_business_time";
    private static final String HIRE_WORLD_DAY_IDENTITY = "hire_world_day_identity";
    private static final String ENTITY_LINK = "entity_link";
    private static final String ENTITY_UUID = "entity_uuid";
    private static final String ENTITY_TYPE_ID = "entity_type_id";
    private static final String DIMENSION_IDENTITY = "dimension_identity";
    private static final String ANCHOR = "anchor";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";
    private static final String RADIUS = "radius";
    private static final String RECORD_REVISION = "record_revision";
    private static final String CREATION_SOURCE_IDENTITY = "creation_source_identity";
    private static final String CREATION_CONFIGURATION_IDENTITY = "creation_configuration_identity";

    private final Path filePath;

    public EmployeeStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Path filePath() {
        return filePath;
    }

    public EmployeeDirectory load() {
        if (!Files.exists(filePath)) {
            return EmployeeDirectory.empty();
        }
        try {
            return deserialize(Files.readString(filePath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load employee records from " + filePath, exception);
        }
    }

    public void save(EmployeeDirectory directory) {
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
            throw new UncheckedIOException("Failed to save employee records to " + filePath, exception);
        }
    }

    public String serialize(EmployeeDirectory directory) {
        Objects.requireNonNull(directory, "directory");
        JsonObject root = new JsonObject();
        root.addProperty(SCHEMA_VERSION, EmployeeSchema.CURRENT_VERSION);
        root.addProperty(NEXT_SEQUENCE, directory.nextSequence());
        JsonArray records = new JsonArray();
        for (EmployeeRecord record : directory.registry().records()) {
            records.add(serializeRecord(record));
        }
        root.add(EMPLOYEE_RECORDS, records);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public EmployeeDirectory deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = requireObject(JsonParser.parseString(json), "employee root");
            int schemaVersion = requireInt(root, SCHEMA_VERSION);
            if (schemaVersion != EmployeeSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported employee schema version: " + schemaVersion);
            }
            long nextSequence = requireLong(root, NEXT_SEQUENCE);
            JsonArray records = requireArray(root, EMPLOYEE_RECORDS);
            List<EmployeeRecord> loaded = new ArrayList<>();
            for (JsonElement element : records) {
                loaded.add(deserializeRecord(requireObject(element, "employee record")));
            }
            return new EmployeeDirectory(nextSequence, EmployeeRegistry.of(loaded));
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt employee persistence", exception);
        }
    }

    private JsonObject serializeRecord(EmployeeRecord record) {
        JsonObject object = new JsonObject();
        object.addProperty(SCHEMA_VERSION, record.schemaVersion());
        object.addProperty(EMPLOYEE_ID, record.employeeId().value());
        object.addProperty(BUSINESS_ID, record.businessId().value());
        object.addProperty(SEQUENCE, record.sequence());
        object.addProperty(WORLD_IDENTITY_ROOT, record.worldIdentityRoot());
        object.addProperty(WORLD_IDENTITY_ROOT_DIGEST, record.worldIdentityRootDigest());
        object.addProperty(DISPLAY_NAME, record.displayName());
        record.preferredName().ifPresent(value -> object.addProperty(PREFERRED_NAME, value));
        object.addProperty(STATUS, record.status().serializedName());
        object.addProperty(PRESENCE_STATE, record.presenceState().serializedName());
        record.assignedShift().ifPresent(shift -> object.add(ASSIGNED_SHIFT, serializeShift(shift)));
        record.assignedPositionId().ifPresent(positionId -> object.addProperty(ASSIGNED_POSITION_ID, positionId.value()));
        object.addProperty(HIRE_BUSINESS_DAY, record.hireBusinessDay());
        object.addProperty(HIRE_BUSINESS_TIME, record.hireBusinessTime().displayText());
        object.addProperty(HIRE_WORLD_DAY_IDENTITY, record.hireWorldDayIdentity());
        record.entityLink().ifPresent(link -> object.add(ENTITY_LINK, serializeEntityLink(link)));
        record.anchor().ifPresent(anchor -> object.add(ANCHOR, serializeAnchor(anchor)));
        object.addProperty(RECORD_REVISION, record.recordRevision());
        object.addProperty(CREATION_SOURCE_IDENTITY, record.creationSourceIdentity());
        object.addProperty(CREATION_CONFIGURATION_IDENTITY, record.creationConfigurationIdentity());
        return object;
    }

    private EmployeeRecord deserializeRecord(JsonObject object) {
        Optional<EmployeeEntityLink> link = optionalObject(object, ENTITY_LINK).map(this::deserializeEntityLink);
        Optional<EmployeeAnchor> anchor = optionalObject(object, ANCHOR).map(this::deserializeAnchor);
        return new EmployeeRecord(
                requireInt(object, SCHEMA_VERSION),
                new EmployeeId(requireString(object, EMPLOYEE_ID)),
                new BusinessId(requireString(object, BUSINESS_ID)),
                requireLong(object, SEQUENCE),
                requireString(object, WORLD_IDENTITY_ROOT),
                requireString(object, WORLD_IDENTITY_ROOT_DIGEST),
                requireString(object, DISPLAY_NAME),
                optionalString(object, PREFERRED_NAME),
                EmployeeStatus.fromSerializedName(requireString(object, STATUS)),
                EmployeePresenceState.fromSerializedName(requireString(object, PRESENCE_STATE)),
                optionalObject(object, ASSIGNED_SHIFT).map(this::deserializeShift),
                optionalString(object, ASSIGNED_POSITION_ID).map(PositionId::new),
                requireLong(object, HIRE_BUSINESS_DAY),
                parseTime(requireString(object, HIRE_BUSINESS_TIME)),
                requireString(object, HIRE_WORLD_DAY_IDENTITY),
                link,
                anchor,
                requireLong(object, RECORD_REVISION),
                requireString(object, CREATION_SOURCE_IDENTITY),
                requireString(object, CREATION_CONFIGURATION_IDENTITY)
        );
    }

    private JsonObject serializeShift(EmployeeShiftAssignment shift) {
        JsonObject object = new JsonObject();
        object.addProperty(SHIFT_ID, shift.shiftId());
        object.addProperty(SHIFT_IDENTITY, shift.shiftIdentity());
        object.addProperty(SHIFT_DISPLAY_NAME, shift.shiftDisplayName());
        object.addProperty(SHIFT_SET_IDENTITY, shift.shiftSetIdentity());
        object.addProperty(CONFIGURATION_IDENTITY, shift.configurationIdentity());
        return object;
    }

    private EmployeeShiftAssignment deserializeShift(JsonObject object) {
        return new EmployeeShiftAssignment(
                requireString(object, SHIFT_ID),
                requireString(object, SHIFT_IDENTITY),
                requireString(object, SHIFT_DISPLAY_NAME),
                requireString(object, SHIFT_SET_IDENTITY),
                requireString(object, CONFIGURATION_IDENTITY)
        );
    }

    private JsonObject serializeEntityLink(EmployeeEntityLink link) {
        JsonObject object = new JsonObject();
        object.addProperty(ENTITY_UUID, link.entityUuid().toString());
        object.addProperty(ENTITY_TYPE_ID, link.entityTypeId());
        object.addProperty(DIMENSION_IDENTITY, link.dimensionIdentity());
        return object;
    }

    private EmployeeEntityLink deserializeEntityLink(JsonObject object) {
        return new EmployeeEntityLink(
                UUID.fromString(requireString(object, ENTITY_UUID)),
                requireString(object, ENTITY_TYPE_ID),
                requireString(object, DIMENSION_IDENTITY)
        );
    }

    private JsonObject serializeAnchor(EmployeeAnchor anchor) {
        JsonObject object = new JsonObject();
        object.addProperty(DIMENSION_IDENTITY, anchor.dimensionIdentity());
        object.addProperty(X, anchor.x());
        object.addProperty(Y, anchor.y());
        object.addProperty(Z, anchor.z());
        object.addProperty(RADIUS, anchor.radius());
        return object;
    }

    private EmployeeAnchor deserializeAnchor(JsonObject object) {
        return new EmployeeAnchor(
                requireString(object, DIMENSION_IDENTITY),
                requireInt(object, X),
                requireInt(object, Y),
                requireInt(object, Z),
                requireInt(object, RADIUS)
        );
    }

    private void moveIntoPlace(Path temporaryFile) throws IOException {
        try {
            Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static BusinessTimeOfDay parseTime(String value) {
        String[] parts = value.split(":", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Employee business time must use HH:MM");
        }
        return new BusinessTimeOfDay(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private static Optional<JsonObject> optionalObject(JsonObject object, String fieldName) {
        JsonElement element = object.get(fieldName);
        if (element == null) {
            return Optional.empty();
        }
        return Optional.of(requireObject(element, fieldName));
    }

    private static Optional<String> optionalString(JsonObject object, String fieldName) {
        JsonElement element = object.get(fieldName);
        if (element == null) {
            return Optional.empty();
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Employee field must be a string: " + fieldName);
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Employee field must not be blank: " + fieldName);
        }
        return Optional.of(value);
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
            throw new IllegalArgumentException("Employee field must be an array: " + fieldName);
        }
        return element.getAsJsonArray();
    }

    private static int requireInt(JsonObject object, String fieldName) {
        long value = requireLong(object, fieldName);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Employee integer field is out of range: " + fieldName);
        }
        return (int) value;
    }

    private static long requireLong(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Employee field must be a number: " + fieldName);
        }
        return element.getAsLong();
    }

    private static String requireString(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Employee field must be a string: " + fieldName);
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Employee field must not be blank: " + fieldName);
        }
        return value;
    }

    private static JsonElement requireField(JsonObject object, String fieldName) {
        JsonElement element = object.get(fieldName);
        if (element == null) {
            throw new IllegalArgumentException("Missing employee field: " + fieldName);
        }
        return element;
    }
}
