package com.butchercraft.world.workforce.machineoperation.persistence;

import com.butchercraft.persistence.AtomicFilePublication;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.workstation.reservation.WorkstationReservationId;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationAssignment;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationAssignmentDirectory;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationAssignmentId;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationAssignmentSchema;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationAssignmentState;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationFailure;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationFailureCode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class EmployeeMachineOperationAssignmentStorage {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private final Path filePath;

    public EmployeeMachineOperationAssignmentStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Path filePath() {
        return filePath;
    }

    public EmployeeMachineOperationAssignmentDirectory load() {
        AtomicFilePublication.requireNoInterruptedPublication(filePath, "Workforce machine-operation assignment state");
        if (!Files.exists(filePath)) return EmployeeMachineOperationAssignmentDirectory.empty();
        return deserialize(AtomicFilePublication.readUtf8(filePath, "Workforce machine-operation assignment state"));
    }

    public void save(EmployeeMachineOperationAssignmentDirectory directory) {
        AtomicFilePublication.publishUtf8(
                filePath, serialize(directory), "Workforce machine-operation assignment state");
        EmployeeMachineOperationAssignmentDirectory verified = deserialize(
                AtomicFilePublication.readUtf8(filePath, "Workforce machine-operation assignment state"));
        if (!verified.equals(directory)) {
            throw new IllegalStateException("Employee machine-operation assignment read-back verification failed");
        }
    }

    public String serialize(EmployeeMachineOperationAssignmentDirectory directory) {
        Objects.requireNonNull(directory, "directory");
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", directory.schemaVersion());
        root.addProperty("owner_revision", directory.ownerRevision());
        root.addProperty("next_assignment_sequence", directory.nextAssignmentSequence());
        JsonArray assignments = new JsonArray();
        directory.assignments().forEach(value -> assignments.add(assignment(value)));
        root.add("assignments", assignments);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public EmployeeMachineOperationAssignmentDirectory deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = object(JsonParser.parseString(json), "employee machine-operation assignment root");
            int schema = integer(root, "schema_version");
            if (schema != EmployeeMachineOperationAssignmentSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported employee machine-operation assignment schema: " + schema);
            }
            List<EmployeeMachineOperationAssignment> assignments = new ArrayList<>();
            for (JsonElement element : array(root, "assignments")) {
                assignments.add(assignment(object(element, "employee machine-operation assignment")));
            }
            return new EmployeeMachineOperationAssignmentDirectory(
                    schema,
                    longValue(root, "owner_revision"),
                    longValue(root, "next_assignment_sequence"),
                    assignments
            );
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt employee machine-operation assignment persistence", exception);
        }
    }

    private JsonObject assignment(EmployeeMachineOperationAssignment value) {
        JsonObject object = new JsonObject();
        object.addProperty("schema_version", value.schemaVersion());
        object.addProperty("assignment_identity", value.assignmentId().value());
        object.addProperty("assignment_sequence", value.assignmentSequence());
        object.add("world_identity", worldIdentity(value.worldIdentity()));
        object.addProperty("employee_identity", value.employeeId().value());
        object.add("workstation", workstation(value.workstation()));
        object.addProperty("machine_type", value.machineType());
        object.addProperty("operating_policy_identity", value.operatingPolicyIdentity());
        object.addProperty("operation_identity", value.operationIdentity());
        object.addProperty("input_material_identity", value.inputMaterialIdentity());
        object.addProperty("input_quantity_per_child", value.inputQuantityPerChild());
        object.addProperty("target_quantity", value.targetQuantity());
        object.addProperty("completed_quantity", value.completedQuantity());
        value.runIdentity().ifPresent(run -> object.addProperty("run_identity", run));
        value.reservationId().ifPresent(reservation ->
                object.addProperty("reservation_identity", reservation.value()));
        value.pendingSupplyTransferIdentity().ifPresent(transfer ->
                object.addProperty("pending_supply_transfer_identity", transfer));
        object.addProperty("state", value.state().name());
        object.addProperty("revision", value.revision());
        object.addProperty("created_tick", value.createdTick());
        object.addProperty("last_updated_tick", value.lastUpdatedTick());
        object.addProperty("observed_run_revision", value.observedRunRevision());
        object.addProperty("observed_child_sequence", value.observedChildSequence());
        value.failure().ifPresent(failure -> {
            JsonObject failureObject = new JsonObject();
            failureObject.addProperty("code", failure.code().serializedName());
            failureObject.addProperty("detail", failure.detail());
            object.add("failure", failureObject);
        });
        object.addProperty("configuration_identity", value.configurationIdentity());
        object.addProperty("content_digest", value.contentDigest());
        return object;
    }

    private EmployeeMachineOperationAssignment assignment(JsonObject object) {
        return new EmployeeMachineOperationAssignment(
                integer(object, "schema_version"),
                new EmployeeMachineOperationAssignmentId(string(object, "assignment_identity")),
                longValue(object, "assignment_sequence"),
                worldIdentity(object(object.get("world_identity"), "world identity")),
                new EmployeeId(string(object, "employee_identity")),
                workstation(object(object.get("workstation"), "workstation")),
                string(object, "machine_type"),
                string(object, "operating_policy_identity"),
                string(object, "operation_identity"),
                string(object, "input_material_identity"),
                integer(object, "input_quantity_per_child"),
                integer(object, "target_quantity"),
                integer(object, "completed_quantity"),
                optionalString(object, "run_identity"),
                optionalString(object, "reservation_identity").map(WorkstationReservationId::new),
                optionalString(object, "pending_supply_transfer_identity"),
                EmployeeMachineOperationAssignmentState.valueOf(string(object, "state")),
                longValue(object, "revision"),
                longValue(object, "created_tick"),
                longValue(object, "last_updated_tick"),
                longValue(object, "observed_run_revision"),
                longValue(object, "observed_child_sequence"),
                optionalObject(object, "failure").map(value -> new EmployeeMachineOperationFailure(
                        EmployeeMachineOperationFailureCode.fromSerializedName(string(value, "code")),
                        string(value, "detail")
                )),
                string(object, "configuration_identity"),
                string(object, "content_digest")
        );
    }

    private static JsonObject workstation(WorkstationEndpointReference reference) {
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

    private static WorkstationEndpointReference workstation(JsonObject object) {
        JsonObject key = object(object.get("endpoint_key"), "workstation endpoint key");
        return new WorkstationEndpointReference(
                new WorkstationInstanceId(string(object, "instance_identity")),
                new WorkstationEndpointKey(
                        string(key, "workstation_type_identity"),
                        string(key, "dimension_identity"),
                        integer(key, "x"), integer(key, "y"), integer(key, "z")),
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
                string(object, "identity"), integer(object, "schema_version"), string(object, "root_digest"));
    }

    private static JsonObject object(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonArray()) throw new IllegalArgumentException(field + " must be an array");
        return element.getAsJsonArray();
    }

    private static Optional<JsonObject> optionalObject(JsonObject object, String field) {
        JsonElement element = object.get(field);
        return element == null || element.isJsonNull() ? Optional.empty() : Optional.of(object(element, field));
    }

    private static Optional<String> optionalString(JsonObject object, String field) {
        JsonElement element = object.get(field);
        return element == null || element.isJsonNull() ? Optional.empty() : Optional.of(element.getAsString());
    }

    private static String string(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) throw new IllegalArgumentException(field + " is required");
        return element.getAsString();
    }

    private static int integer(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) throw new IllegalArgumentException(field + " is required");
        return element.getAsInt();
    }

    private static long longValue(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) throw new IllegalArgumentException(field + " is required");
        return element.getAsLong();
    }
}
