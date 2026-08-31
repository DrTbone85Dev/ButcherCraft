package com.butchercraft.world.materialhandling.persistence;

import com.butchercraft.persistence.AtomicFilePublication;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationEndpointObservationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointOwnerResultV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointPreparationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointStackStateV2;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.persistence.WorkstationEndpointJournalV2Storage;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.materialhandling.MaterialHandlingRuntime;
import com.butchercraft.world.materialhandling.MaterialHandlingRuntimeV2;
import com.butchercraft.world.materialhandling.MaterialHandlingSchema;
import com.butchercraft.world.materialhandling.MaterialTransferIdV2;
import com.butchercraft.world.materialhandling.MaterialTransferLifecycle;
import com.butchercraft.world.materialhandling.MaterialTransferRecordV2;
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

/** Schema-aware Material Handling persistence. Live activation remains gated to IM-030B. */
public final class MaterialHandlingStorageV2 {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Path filePath;

    public MaterialHandlingStorageV2(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Optional<LoadedRuntime> loadVersioned() {
        AtomicFilePublication.requireNoInterruptedPublication(filePath, "Material Handling state");
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        String json = AtomicFilePublication.readUtf8(filePath, "Material Handling state");
        int schema = integer(object(JsonParser.parseString(json), "Material Handling root"), "schema_version");
        if (schema == MaterialHandlingSchema.LEGACY_SCHEMA_VERSION) {
            return Optional.of(new LegacyRuntime(new MaterialHandlingStorage(filePath).deserialize(json), json));
        }
        if (schema == MaterialHandlingSchema.STACK_AWARE_SCHEMA_VERSION) {
            return Optional.of(new StackAwareRuntime(deserialize(json)));
        }
        throw new IllegalArgumentException("Unsupported Material Handling schema version: " + schema);
    }

    public void save(MaterialHandlingRuntimeV2 runtime) {
        String serialized = serialize(runtime);
        publishStrict(serialized);
        if (!deserialize(readPublished()).equals(runtime)) {
            throw new IllegalStateException("Schema-2 Material Handling state failed semantic read-back verification");
        }
    }

    public String serialize(MaterialHandlingRuntimeV2 runtime) {
        Objects.requireNonNull(runtime, "runtime");
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", runtime.schemaVersion());
        root.addProperty("owner_revision", runtime.ownerRevision());
        root.addProperty("next_transfer_sequence", runtime.nextTransferSequence());
        root.add("world_identity", worldIdentity(runtime.worldIdentity()));
        root.addProperty("configuration_identity", runtime.configurationIdentity());
        runtime.immutableLegacySchema1Runtime().ifPresent(value ->
                root.addProperty("immutable_legacy_schema_1_runtime", value));
        JsonArray transfers = new JsonArray();
        runtime.transfers().forEach(transfer -> transfers.add(serializeTransfer(transfer)));
        root.add("transfers", transfers);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public MaterialHandlingRuntimeV2 deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = object(JsonParser.parseString(json), "Material Handling root");
            int schema = integer(root, "schema_version");
            if (schema != MaterialHandlingSchema.STACK_AWARE_SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported Material Handling schema version: " + schema);
            }
            Optional<String> legacy = optionalString(root, "immutable_legacy_schema_1_runtime");
            legacy.ifPresent(value -> new MaterialHandlingStorage(filePath).deserialize(value));
            List<MaterialTransferRecordV2> transfers = new ArrayList<>();
            for (JsonElement element : array(root, "transfers")) {
                transfers.add(deserializeTransfer(object(element, "Material Transfer record")));
            }
            return new MaterialHandlingRuntimeV2(
                    schema, longValue(root, "owner_revision"), longValue(root, "next_transfer_sequence"),
                    parseWorldIdentity(object(root.get("world_identity"), "world identity")),
                    string(root, "configuration_identity"), legacy, transfers
            );
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt schema-2 Material Handling persistence", exception);
        }
    }

    private static JsonObject serializeTransfer(MaterialTransferRecordV2 transfer) {
        JsonObject object = new JsonObject();
        object.addProperty("schema_version", transfer.schemaVersion());
        object.addProperty("sequence", transfer.sequence());
        object.addProperty("transfer_identity", transfer.transferId().value());
        object.add("world_identity", worldIdentity(transfer.worldIdentity()));
        object.add("source", reference(transfer.source()));
        object.add("destination", reference(transfer.destination()));
        object.addProperty("material_identity", transfer.materialIdentity());
        object.addProperty("quantity", transfer.quantity());
        object.addProperty("assignment_type_identity", transfer.assignmentTypeIdentity());
        transfer.employeeReference().ifPresent(value -> object.addProperty("employee_reference", value));
        object.addProperty("lifecycle", transfer.lifecycle().name());
        object.add("exact_transfer_stack", WorkstationEndpointJournalV2Storage.serializeState(
                transfer.exactTransferStack()));
        transfer.exactInTransitCustody().ifPresent(value -> object.add("exact_in_transit_custody",
                WorkstationEndpointJournalV2Storage.serializeState(value)));
        JsonArray observations = new JsonArray();
        transfer.endpointObservations().forEach(value ->
                observations.add(WorkstationEndpointJournalV2Storage.serializeObservation(value)));
        object.add("endpoint_observations", observations);
        JsonArray preparations = new JsonArray();
        transfer.endpointPreparations().forEach(value ->
                preparations.add(WorkstationEndpointJournalV2Storage.serializePreparation(value)));
        object.add("endpoint_preparations", preparations);
        JsonArray results = new JsonArray();
        transfer.endpointOwnerResults().forEach(value -> {
            JsonObject wrapped = new JsonObject();
            wrapped.add("preparation", WorkstationEndpointJournalV2Storage.serializePreparation(value.preparation()));
            wrapped.add("result", WorkstationEndpointJournalV2Storage.serializeOwnerResult(value));
            results.add(wrapped);
        });
        object.add("endpoint_owner_results", results);
        object.addProperty("configuration_identity", transfer.configurationIdentity());
        object.addProperty("creation_revision", transfer.creationRevision());
        object.addProperty("last_update_revision", transfer.lastUpdateRevision());
        transfer.failureDetail().ifPresent(value -> object.addProperty("failure_detail", value));
        return object;
    }

    private static MaterialTransferRecordV2 deserializeTransfer(JsonObject object) {
        List<WorkstationEndpointObservationV2> observations = new ArrayList<>();
        for (JsonElement element : array(object, "endpoint_observations")) {
            observations.add(WorkstationEndpointJournalV2Storage.deserializeObservation(
                    MaterialHandlingStorageV2.object(element, "endpoint observation")));
        }
        List<WorkstationEndpointPreparationV2> preparations = new ArrayList<>();
        for (JsonElement element : array(object, "endpoint_preparations")) {
            preparations.add(WorkstationEndpointJournalV2Storage.deserializePreparation(
                    MaterialHandlingStorageV2.object(element, "endpoint preparation")));
        }
        List<WorkstationEndpointOwnerResultV2> results = new ArrayList<>();
        for (JsonElement element : array(object, "endpoint_owner_results")) {
            JsonObject wrapped = MaterialHandlingStorageV2.object(element, "endpoint owner result wrapper");
            WorkstationEndpointPreparationV2 preparation = WorkstationEndpointJournalV2Storage.deserializePreparation(
                    MaterialHandlingStorageV2.object(wrapped.get("preparation"), "endpoint preparation"));
            results.add(WorkstationEndpointJournalV2Storage.deserializeOwnerResult(
                    MaterialHandlingStorageV2.object(wrapped.get("result"), "endpoint owner result"), preparation));
        }
        JsonElement custody = object.get("exact_in_transit_custody");
        Optional<WorkstationEndpointStackStateV2> custodyState = custody == null || custody.isJsonNull()
                ? Optional.empty()
                : Optional.of(WorkstationEndpointJournalV2Storage.deserializeState(
                        MaterialHandlingStorageV2.object(custody, "exact in-transit custody")));
        return new MaterialTransferRecordV2(
                integer(object, "schema_version"), longValue(object, "sequence"),
                new MaterialTransferIdV2(string(object, "transfer_identity")),
                parseWorldIdentity(MaterialHandlingStorageV2.object(object.get("world_identity"), "world identity")),
                parseReference(MaterialHandlingStorageV2.object(object.get("source"), "source")),
                parseReference(MaterialHandlingStorageV2.object(object.get("destination"), "destination")),
                string(object, "material_identity"), integer(object, "quantity"),
                string(object, "assignment_type_identity"), optionalString(object, "employee_reference"),
                MaterialTransferLifecycle.valueOf(string(object, "lifecycle")),
                WorkstationEndpointJournalV2Storage.deserializeState(
                        MaterialHandlingStorageV2.object(object.get("exact_transfer_stack"), "exact transfer stack")),
                custodyState, observations, preparations, results, string(object, "configuration_identity"),
                longValue(object, "creation_revision"), longValue(object, "last_update_revision"),
                optionalString(object, "failure_detail")
        );
    }

    private static JsonObject reference(WorkstationEndpointReference reference) {
        JsonObject object = new JsonObject();
        object.addProperty("instance_identity", reference.instanceId().value());
        object.add("endpoint_key", endpointKey(reference.endpointKey()));
        object.addProperty("generation", reference.generation());
        return object;
    }

    private static WorkstationEndpointReference parseReference(JsonObject object) {
        return new WorkstationEndpointReference(
                new WorkstationInstanceId(string(object, "instance_identity")),
                parseEndpointKey(MaterialHandlingStorageV2.object(object.get("endpoint_key"), "endpoint key")),
                longValue(object, "generation")
        );
    }

    private static JsonObject endpointKey(WorkstationEndpointKey key) {
        JsonObject object = new JsonObject();
        object.addProperty("workstation_type_identity", key.workstationTypeIdentity());
        object.addProperty("dimension_identity", key.dimensionIdentity());
        object.addProperty("x", key.x());
        object.addProperty("y", key.y());
        object.addProperty("z", key.z());
        return object;
    }

    private static WorkstationEndpointKey parseEndpointKey(JsonObject object) {
        return new WorkstationEndpointKey(
                string(object, "workstation_type_identity"), string(object, "dimension_identity"),
                integer(object, "x"), integer(object, "y"), integer(object, "z")
        );
    }

    private static JsonObject worldIdentity(WorldIdentityRootIdentity identity) {
        JsonObject object = new JsonObject();
        object.addProperty("identity", identity.identity());
        object.addProperty("schema_version", identity.schemaVersion());
        object.addProperty("root_digest", identity.rootDigest());
        return object;
    }

    private static WorldIdentityRootIdentity parseWorldIdentity(JsonObject object) {
        return new WorldIdentityRootIdentity(
                string(object, "identity"), integer(object, "schema_version"), string(object, "root_digest")
        );
    }

    private void publishStrict(String serialized) {
        AtomicFilePublication.publishUtf8(filePath, serialized, "Material Handling state");
    }

    private String readPublished() {
        return AtomicFilePublication.readUtf8(filePath, "Material Handling state");
    }

    private static JsonObject object(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException(label + " must be an object");
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonArray()) throw new IllegalArgumentException(field + " must be an array");
        return element.getAsJsonArray();
    }

    private static String string(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) throw new IllegalArgumentException(field + " is required");
        return element.getAsString();
    }

    private static Optional<String> optionalString(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) return Optional.empty();
        if (!element.isJsonPrimitive()) throw new IllegalArgumentException(field + " must be a string");
        return Optional.of(element.getAsString());
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

    public sealed interface LoadedRuntime permits LegacyRuntime, StackAwareRuntime {
        int schemaVersion();
    }

    public record LegacyRuntime(MaterialHandlingRuntime runtime, String immutableCanonicalJson)
            implements LoadedRuntime {
        public LegacyRuntime {
            runtime = Objects.requireNonNull(runtime, "runtime");
            immutableCanonicalJson = Objects.requireNonNull(immutableCanonicalJson, "immutableCanonicalJson");
        }

        @Override
        public int schemaVersion() {
            return MaterialHandlingSchema.LEGACY_SCHEMA_VERSION;
        }
    }

    public record StackAwareRuntime(MaterialHandlingRuntimeV2 runtime) implements LoadedRuntime {
        public StackAwareRuntime {
            runtime = Objects.requireNonNull(runtime, "runtime");
        }

        @Override
        public int schemaVersion() {
            return MaterialHandlingSchema.STACK_AWARE_SCHEMA_VERSION;
        }
    }
}
