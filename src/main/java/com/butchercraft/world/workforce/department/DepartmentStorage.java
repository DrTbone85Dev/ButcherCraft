package com.butchercraft.world.workforce.department;

import com.butchercraft.world.identity.WorldIdentityRootIdentity;
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

public final class DepartmentStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private static final String SCHEMA_VERSION = "schema_version";
    private static final String PLANT_ENTRANCE_ANCHOR = "plant_entrance_anchor";
    private static final String DEPARTMENTS = "departments";
    private static final String DEPARTMENT_ID = "department_id";
    private static final String WORLD_IDENTITY_ROOT = "world_identity_root";
    private static final String WORLD_IDENTITY_ROOT_DIGEST = "world_identity_root_digest";
    private static final String DISPLAY_NAME = "display_name";
    private static final String ANCHOR = "anchor";
    private static final String DIMENSION_IDENTITY = "dimension_identity";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";
    private static final String RADIUS = "radius";
    private static final String COLOR = "color";
    private static final String ICON = "icon";
    private static final String RECORD_REVISION = "record_revision";

    private final Path filePath;

    public DepartmentStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public DepartmentDirectory load(WorldIdentityRootIdentity worldIdentity) {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        if (!Files.exists(filePath)) {
            return BuiltInDepartmentDefinitions.defaults(worldIdentity);
        }
        try {
            return deserialize(Files.readString(filePath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load departments from " + filePath, exception);
        }
    }

    public void save(DepartmentDirectory directory) {
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
            throw new UncheckedIOException("Failed to save departments to " + filePath, exception);
        }
    }

    public String serialize(DepartmentDirectory directory) {
        Objects.requireNonNull(directory, "directory");
        JsonObject root = new JsonObject();
        root.addProperty(SCHEMA_VERSION, DepartmentSchema.CURRENT_VERSION);
        directory.plantEntranceAnchor().ifPresent(anchor -> root.add(PLANT_ENTRANCE_ANCHOR, serializeAnchor(anchor)));
        JsonArray departments = new JsonArray();
        for (DepartmentRecord record : directory.registry().records()) {
            departments.add(serializeRecord(record));
        }
        root.add(DEPARTMENTS, departments);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public DepartmentDirectory deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = requireObject(JsonParser.parseString(json), "department root");
            int schemaVersion = requireInt(root, SCHEMA_VERSION);
            if (schemaVersion != DepartmentSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported department schema version: " + schemaVersion);
            }
            Optional<DepartmentAnchor> plantEntrance = optionalObject(root, PLANT_ENTRANCE_ANCHOR)
                    .map(this::deserializeAnchor);
            JsonArray records = requireArray(root, DEPARTMENTS);
            List<DepartmentRecord> loaded = new ArrayList<>();
            for (JsonElement element : records) {
                loaded.add(deserializeRecord(requireObject(element, "department record")));
            }
            return new DepartmentDirectory(DepartmentRegistry.of(loaded), plantEntrance);
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt department persistence", exception);
        }
    }

    private JsonObject serializeRecord(DepartmentRecord record) {
        JsonObject object = new JsonObject();
        object.addProperty(SCHEMA_VERSION, record.schemaVersion());
        object.addProperty(DEPARTMENT_ID, record.departmentId().value());
        object.addProperty(WORLD_IDENTITY_ROOT, record.worldIdentityRoot());
        object.addProperty(WORLD_IDENTITY_ROOT_DIGEST, record.worldIdentityRootDigest());
        object.addProperty(DISPLAY_NAME, record.displayName());
        record.anchor().ifPresent(anchor -> object.add(ANCHOR, serializeAnchor(anchor)));
        record.color().ifPresent(color -> object.addProperty(COLOR, color));
        record.icon().ifPresent(icon -> object.addProperty(ICON, icon));
        object.addProperty(RECORD_REVISION, record.recordRevision());
        return object;
    }

    private DepartmentRecord deserializeRecord(JsonObject object) {
        return new DepartmentRecord(
                requireInt(object, SCHEMA_VERSION),
                new DepartmentId(requireString(object, DEPARTMENT_ID)),
                requireString(object, WORLD_IDENTITY_ROOT),
                requireString(object, WORLD_IDENTITY_ROOT_DIGEST),
                requireString(object, DISPLAY_NAME),
                optionalObject(object, ANCHOR).map(this::deserializeAnchor),
                optionalString(object, COLOR),
                optionalString(object, ICON),
                requireLong(object, RECORD_REVISION)
        );
    }

    private JsonObject serializeAnchor(DepartmentAnchor anchor) {
        JsonObject object = new JsonObject();
        object.addProperty(DIMENSION_IDENTITY, anchor.dimensionIdentity());
        object.addProperty(X, anchor.x());
        object.addProperty(Y, anchor.y());
        object.addProperty(Z, anchor.z());
        object.addProperty(RADIUS, anchor.radius());
        return object;
    }

    private DepartmentAnchor deserializeAnchor(JsonObject object) {
        return new DepartmentAnchor(
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
            throw new IllegalArgumentException("Department field must be a string: " + fieldName);
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Department field must not be blank: " + fieldName);
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
            throw new IllegalArgumentException("Department field must be an array: " + fieldName);
        }
        return element.getAsJsonArray();
    }

    private static int requireInt(JsonObject object, String fieldName) {
        long value = requireLong(object, fieldName);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Department integer field is out of range: " + fieldName);
        }
        return (int) value;
    }

    private static long requireLong(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Department field must be a number: " + fieldName);
        }
        return element.getAsLong();
    }

    private static String requireString(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Department field must be a string: " + fieldName);
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Department field must not be blank: " + fieldName);
        }
        return value;
    }

    private static JsonElement requireField(JsonObject object, String fieldName) {
        JsonElement element = object.get(fieldName);
        if (element == null) {
            throw new IllegalArgumentException("Missing department field: " + fieldName);
        }
        return element;
    }
}
