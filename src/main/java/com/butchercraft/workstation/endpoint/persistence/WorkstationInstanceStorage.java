package com.butchercraft.workstation.endpoint.persistence;

import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationEndpointSchema;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.WorkstationInstanceLifecycle;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRecord;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRegistry;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class WorkstationInstanceStorage {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Path filePath;

    public WorkstationInstanceStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Path filePath() {
        return filePath;
    }

    public Optional<WorkstationInstanceRegistry> loadExisting() {
        Path temporaryFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        if (!Files.exists(filePath)) {
            if (Files.exists(temporaryFile)) {
                throw new IllegalStateException(
                        "Interrupted Workstation instance publication requires recovery: " + temporaryFile
                );
            }
            return Optional.empty();
        }
        try {
            return Optional.of(deserialize(Files.readString(filePath, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load Workstation instance registry from " + filePath, exception);
        }
    }

    public void save(WorkstationInstanceRegistry registry) {
        String serialized = serialize(registry);
        StrictAtomicJsonFile.publish(filePath, serialized);
        WorkstationInstanceRegistry verified = deserialize(readPublished());
        if (!verified.equals(registry)) {
            throw new IllegalStateException("Workstation instance registry failed semantic read-back verification");
        }
    }

    public String serialize(WorkstationInstanceRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", registry.schemaVersion());
        root.addProperty("owner_revision", registry.ownerRevision());
        root.add("world_identity", serializeWorldIdentity(registry.worldIdentity()));
        root.addProperty("next_instance_generation", registry.nextInstanceGeneration());
        root.addProperty("allocation_configuration_identity", registry.allocationConfigurationIdentity());
        JsonArray records = new JsonArray();
        registry.records().forEach(record -> records.add(serializeRecord(record)));
        root.add("instances", records);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public WorkstationInstanceRegistry deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = WorkstationEndpointJson.object(JsonParser.parseString(json), "Workstation instance root");
            int schema = WorkstationEndpointJson.integer(root, "schema_version");
            requireCurrentSchema(schema);
            List<WorkstationInstanceRecord> records = new ArrayList<>();
            for (JsonElement element : WorkstationEndpointJson.array(root, "instances")) {
                records.add(deserializeRecord(WorkstationEndpointJson.object(element, "Workstation instance record")));
            }
            return new WorkstationInstanceRegistry(
                    schema,
                    WorkstationEndpointJson.longValue(root, "owner_revision"),
                    deserializeWorldIdentity(WorkstationEndpointJson.object(root.get("world_identity"), "world identity")),
                    WorkstationEndpointJson.longValue(root, "next_instance_generation"),
                    WorkstationEndpointJson.string(root, "allocation_configuration_identity"),
                    records
            );
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt Workstation instance registry", exception);
        }
    }

    private JsonObject serializeRecord(WorkstationInstanceRecord record) {
        JsonObject object = new JsonObject();
        object.addProperty("schema_version", record.schemaVersion());
        object.addProperty("instance_identity", record.instanceId().value());
        object.add("world_identity", serializeWorldIdentity(record.worldIdentity()));
        object.add("endpoint_key", serializeKey(record.endpointKey()));
        object.addProperty("generation", record.generation());
        object.addProperty("allocation_evidence_identity", record.allocationEvidenceIdentity());
        object.addProperty("allocation_content_digest", record.allocationContentDigest());
        object.addProperty("allocation_configuration_identity", record.allocationConfigurationIdentity());
        object.addProperty("lifecycle", record.lifecycle().name());
        object.addProperty("creation_revision", record.creationRevision());
        object.addProperty("last_update_revision", record.lastUpdateRevision());
        record.terminalReason().ifPresent(value -> object.addProperty("terminal_reason", value));
        JsonArray references = new JsonArray();
        record.unresolvedJournalReferences().forEach(references::add);
        object.add("unresolved_journal_references", references);
        return object;
    }

    private WorkstationInstanceRecord deserializeRecord(JsonObject object) {
        List<String> references = new ArrayList<>();
        for (JsonElement element : WorkstationEndpointJson.array(object, "unresolved_journal_references")) {
            references.add(element.getAsString());
        }
        return new WorkstationInstanceRecord(
                WorkstationEndpointJson.integer(object, "schema_version"),
                new WorkstationInstanceId(WorkstationEndpointJson.string(object, "instance_identity")),
                deserializeWorldIdentity(WorkstationEndpointJson.object(object.get("world_identity"), "world identity")),
                deserializeKey(WorkstationEndpointJson.object(object.get("endpoint_key"), "endpoint key")),
                WorkstationEndpointJson.longValue(object, "generation"),
                WorkstationEndpointJson.string(object, "allocation_evidence_identity"),
                WorkstationEndpointJson.string(object, "allocation_content_digest"),
                WorkstationEndpointJson.string(object, "allocation_configuration_identity"),
                WorkstationInstanceLifecycle.valueOf(WorkstationEndpointJson.string(object, "lifecycle")),
                WorkstationEndpointJson.longValue(object, "creation_revision"),
                WorkstationEndpointJson.longValue(object, "last_update_revision"),
                WorkstationEndpointJson.optionalString(object, "terminal_reason"),
                references
        );
    }

    private static JsonObject serializeWorldIdentity(WorldIdentityRootIdentity identity) {
        JsonObject object = new JsonObject();
        object.addProperty("identity", identity.identity());
        object.addProperty("schema_version", identity.schemaVersion());
        object.addProperty("root_digest", identity.rootDigest());
        return object;
    }

    private static WorldIdentityRootIdentity deserializeWorldIdentity(JsonObject object) {
        return new WorldIdentityRootIdentity(
                WorkstationEndpointJson.string(object, "identity"),
                WorkstationEndpointJson.integer(object, "schema_version"),
                WorkstationEndpointJson.string(object, "root_digest")
        );
    }

    private static JsonObject serializeKey(WorkstationEndpointKey key) {
        JsonObject object = new JsonObject();
        object.addProperty("workstation_type_identity", key.workstationTypeIdentity());
        object.addProperty("dimension_identity", key.dimensionIdentity());
        object.addProperty("x", key.x());
        object.addProperty("y", key.y());
        object.addProperty("z", key.z());
        return object;
    }

    private static WorkstationEndpointKey deserializeKey(JsonObject object) {
        return new WorkstationEndpointKey(
                WorkstationEndpointJson.string(object, "workstation_type_identity"),
                WorkstationEndpointJson.string(object, "dimension_identity"),
                WorkstationEndpointJson.integer(object, "x"),
                WorkstationEndpointJson.integer(object, "y"),
                WorkstationEndpointJson.integer(object, "z")
        );
    }

    private String readPublished() {
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to verify Workstation instance registry " + filePath, exception);
        }
    }

    private static void requireCurrentSchema(int schema) {
        if (schema != WorkstationEndpointSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Workstation instance schema version: " + schema);
        }
    }
}
