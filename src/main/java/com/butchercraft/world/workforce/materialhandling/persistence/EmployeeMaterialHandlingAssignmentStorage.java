package com.butchercraft.world.workforce.materialhandling.persistence;

import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.materialhandling.MaterialTransferId;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignment;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignmentDirectory;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignmentId;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignmentSchema;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignmentState;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingFailure;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingFailureCode;
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

public final class EmployeeMaterialHandlingAssignmentStorage {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private final Path filePath;

    public EmployeeMaterialHandlingAssignmentStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Path filePath() {
        return filePath;
    }

    public EmployeeMaterialHandlingAssignmentDirectory load() {
        Path temporary = temporaryFile();
        if (!Files.exists(filePath)) {
            if (Files.exists(temporary)) {
                throw new IllegalStateException("Interrupted Workforce assignment publication requires recovery: "
                        + temporary);
            }
            return EmployeeMaterialHandlingAssignmentDirectory.empty();
        }
        try {
            return deserialize(Files.readString(filePath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load employee Material Handling assignments from " + filePath,
                    exception);
        }
    }

    public void save(EmployeeMaterialHandlingAssignmentDirectory directory) {
        String json = serialize(directory);
        Path temporary = temporaryFile();
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Atomic replacement is required for " + filePath, exception);
            }
            EmployeeMaterialHandlingAssignmentDirectory verified = deserialize(
                    Files.readString(filePath, StandardCharsets.UTF_8)
            );
            if (!verified.equals(directory)) {
                throw new IOException("Employee Material Handling assignment read-back verification failed");
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save employee Material Handling assignments to " + filePath,
                    exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // An orphaned temporary file never outranks the authoritative candidate.
            }
        }
    }

    public String serialize(EmployeeMaterialHandlingAssignmentDirectory directory) {
        Objects.requireNonNull(directory, "directory");
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", directory.schemaVersion());
        root.addProperty("owner_revision", directory.ownerRevision());
        JsonArray assignments = new JsonArray();
        directory.assignments().forEach(value -> assignments.add(assignment(value)));
        root.add("assignments", assignments);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public EmployeeMaterialHandlingAssignmentDirectory deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = object(JsonParser.parseString(json), "employee Material Handling assignment root");
            int schema = integer(root, "schema_version");
            if (schema != EmployeeMaterialHandlingAssignmentSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported employee Material Handling assignment schema: " + schema);
            }
            List<EmployeeMaterialHandlingAssignment> assignments = new ArrayList<>();
            for (JsonElement element : array(root, "assignments")) {
                assignments.add(assignment(object(element, "employee Material Handling assignment")));
            }
            return new EmployeeMaterialHandlingAssignmentDirectory(
                    schema,
                    longValue(root, "owner_revision"),
                    assignments
            );
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt employee Material Handling assignment persistence", exception);
        }
    }

    private JsonObject assignment(EmployeeMaterialHandlingAssignment assignment) {
        JsonObject object = new JsonObject();
        object.addProperty("schema_version", assignment.schemaVersion());
        object.addProperty("assignment_identity", assignment.assignmentId().value());
        object.add("world_identity", worldIdentity(assignment.worldIdentity()));
        object.addProperty("employee_identity", assignment.employeeId().value());
        object.addProperty("transfer_identity", assignment.transferId().value());
        object.add("source", reference(assignment.source()));
        object.add("destination", reference(assignment.destination()));
        object.addProperty("state", assignment.state().name());
        object.addProperty("revision", assignment.revision());
        object.addProperty("created_tick", assignment.createdTick());
        assignment.failure().ifPresent(failure -> {
            JsonObject value = new JsonObject();
            value.addProperty("code", failure.code().serializedName());
            value.addProperty("detail", failure.detail());
            object.add("failure", value);
        });
        object.addProperty("configuration_identity", assignment.configurationIdentity());
        object.addProperty("content_digest", assignment.contentDigest());
        return object;
    }

    private EmployeeMaterialHandlingAssignment assignment(JsonObject object) {
        return new EmployeeMaterialHandlingAssignment(
                integer(object, "schema_version"),
                new EmployeeMaterialHandlingAssignmentId(string(object, "assignment_identity")),
                worldIdentity(object(object.get("world_identity"), "world identity")),
                new EmployeeId(string(object, "employee_identity")),
                new MaterialTransferId(string(object, "transfer_identity")),
                reference(object(object.get("source"), "source endpoint")),
                reference(object(object.get("destination"), "destination endpoint")),
                EmployeeMaterialHandlingAssignmentState.valueOf(string(object, "state")),
                longValue(object, "revision"),
                longValue(object, "created_tick"),
                optionalObject(object, "failure").map(value -> new EmployeeMaterialHandlingFailure(
                        EmployeeMaterialHandlingFailureCode.fromSerializedName(string(value, "code")),
                        string(value, "detail")
                )),
                string(object, "configuration_identity"),
                string(object, "content_digest")
        );
    }

    private static JsonObject reference(WorkstationEndpointReference reference) {
        JsonObject object = new JsonObject();
        object.addProperty("instance_identity", reference.instanceId().value());
        object.addProperty("generation", reference.generation());
        JsonObject key = new JsonObject();
        key.addProperty("workstation_type_identity", reference.endpointKey().workstationTypeIdentity());
        key.addProperty("dimension_identity", reference.endpointKey().dimensionIdentity());
        key.addProperty("x", reference.endpointKey().x());
        key.addProperty("y", reference.endpointKey().y());
        key.addProperty("z", reference.endpointKey().z());
        object.add("endpoint_key", key);
        return object;
    }

    private static WorkstationEndpointReference reference(JsonObject object) {
        JsonObject key = object(object.get("endpoint_key"), "endpoint key");
        return new WorkstationEndpointReference(
                new WorkstationInstanceId(string(object, "instance_identity")),
                new WorkstationEndpointKey(
                        string(key, "workstation_type_identity"),
                        string(key, "dimension_identity"),
                        integer(key, "x"),
                        integer(key, "y"),
                        integer(key, "z")
                ),
                longValue(object, "generation")
        );
    }

    private static JsonObject worldIdentity(WorldIdentityRootIdentity identity) {
        JsonObject object = new JsonObject();
        object.addProperty("identity", identity.identity());
        object.addProperty("schema_version", identity.schemaVersion());
        object.addProperty("root_digest", identity.rootDigest());
        return object;
    }

    private static WorldIdentityRootIdentity worldIdentity(JsonObject object) {
        return new WorldIdentityRootIdentity(
                string(object, "identity"),
                integer(object, "schema_version"),
                string(object, "root_digest")
        );
    }

    private Path temporaryFile() {
        return filePath.resolveSibling(filePath.getFileName() + ".tmp");
    }

    private static JsonObject object(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return element.getAsJsonArray();
    }

    private static Optional<JsonObject> optionalObject(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        return Optional.of(object(element, field));
    }

    private static String string(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return element.getAsString();
    }

    private static int integer(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return element.getAsInt();
    }

    private static long longValue(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return element.getAsLong();
    }
}
