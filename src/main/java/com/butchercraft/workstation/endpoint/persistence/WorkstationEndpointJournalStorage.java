package com.butchercraft.workstation.endpoint.persistence;

import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectId;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind;
import com.butchercraft.workstation.endpoint.WorkstationEndpointFreshnessIdentity;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournal;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalRecord;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalState;
import com.butchercraft.workstation.endpoint.WorkstationEndpointOwnerResult;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;
import com.butchercraft.workstation.endpoint.WorkstationEndpointSchema;
import com.butchercraft.workstation.endpoint.WorkstationEndpointStackPayload;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
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

public final class WorkstationEndpointJournalStorage {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Path filePath;

    public WorkstationEndpointJournalStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Path filePath() {
        return filePath;
    }

    public Optional<WorkstationEndpointJournal> loadExisting() {
        Path temporaryFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        if (!Files.exists(filePath)) {
            if (Files.exists(temporaryFile)) {
                throw new IllegalStateException(
                        "Interrupted Workstation endpoint publication requires recovery: " + temporaryFile
                );
            }
            return Optional.empty();
        }
        try {
            return Optional.of(deserialize(Files.readString(filePath, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load Workstation endpoint journal from " + filePath, exception);
        }
    }

    public void save(WorkstationEndpointJournal journal) {
        String serialized = serialize(journal);
        StrictAtomicJsonFile.publish(filePath, serialized);
        WorkstationEndpointJournal verified = deserialize(readPublished());
        if (!verified.equals(journal)) {
            throw new IllegalStateException("Workstation endpoint journal failed semantic read-back verification");
        }
    }

    public String serialize(WorkstationEndpointJournal journal) {
        Objects.requireNonNull(journal, "journal");
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", journal.schemaVersion());
        root.addProperty("owner_revision", journal.ownerRevision());
        root.addProperty("next_journal_sequence", journal.nextJournalSequence());
        root.add("world_identity", serializeWorldIdentity(journal.worldIdentity()));
        root.addProperty("endpoint_configuration_identity", journal.endpointConfigurationIdentity());
        JsonArray records = new JsonArray();
        journal.records().forEach(record -> records.add(serializeRecord(record)));
        root.add("endpoint_effects", records);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public WorkstationEndpointJournal deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = WorkstationEndpointJson.object(JsonParser.parseString(json), "Workstation endpoint root");
            int schema = WorkstationEndpointJson.integer(root, "schema_version");
            if (schema != WorkstationEndpointSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported Workstation endpoint schema version: " + schema);
            }
            List<WorkstationEndpointJournalRecord> records = new ArrayList<>();
            for (JsonElement element : WorkstationEndpointJson.array(root, "endpoint_effects")) {
                records.add(deserializeRecord(WorkstationEndpointJson.object(element, "endpoint journal record")));
            }
            return new WorkstationEndpointJournal(
                    schema,
                    WorkstationEndpointJson.longValue(root, "owner_revision"),
                    WorkstationEndpointJson.longValue(root, "next_journal_sequence"),
                    deserializeWorldIdentity(WorkstationEndpointJson.object(root.get("world_identity"), "world identity")),
                    WorkstationEndpointJson.string(root, "endpoint_configuration_identity"),
                    records
            );
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt Workstation endpoint journal", exception);
        }
    }

    private JsonObject serializeRecord(WorkstationEndpointJournalRecord record) {
        JsonObject object = new JsonObject();
        object.addProperty("schema_version", record.schemaVersion());
        object.addProperty("journal_sequence", record.journalSequence());
        object.addProperty("effect_identity", record.effectId().value());
        object.addProperty("instance_identity", record.instanceId().value());
        object.addProperty("invocation_identity", record.invocationIdentity());
        object.addProperty("effect_kind", record.effectKind().name());
        object.addProperty("slot_index", record.slotIndex());
        object.add("exact_stack", serializeStack(record.exactStack()));
        object.addProperty("expected_inventory_revision", record.expectedInventoryRevision());
        object.addProperty("post_inventory_revision", record.postInventoryRevision());
        object.addProperty("expected_endpoint_effect_revision", record.expectedEndpointEffectRevision());
        object.addProperty("endpoint_effect_revision", record.endpointEffectRevision());
        object.addProperty("pre_operation_state_identity", record.preOperationStateIdentity());
        object.addProperty("post_operation_state_identity", record.postOperationStateIdentity());
        object.addProperty(
                "previous_owner_result_journal_sequence",
                record.previousOwnerResultJournalSequence()
        );
        object.addProperty("pre_freshness_identity", record.preFreshnessIdentity().value());
        object.addProperty("post_freshness_identity", record.postFreshnessIdentity().value());
        object.addProperty("endpoint_configuration_identity", record.endpointConfigurationIdentity());
        object.addProperty("state", record.state().name());
        object.addProperty("creation_revision", record.creationRevision());
        object.addProperty("last_update_revision", record.lastUpdateRevision());
        record.ownerResult().ifPresent(result -> object.add("owner_result", serializeResult(result)));
        record.failureDetail().ifPresent(value -> object.addProperty("failure_detail", value));
        return object;
    }

    private WorkstationEndpointJournalRecord deserializeRecord(JsonObject object) {
        return new WorkstationEndpointJournalRecord(
                WorkstationEndpointJson.integer(object, "schema_version"),
                WorkstationEndpointJson.longValue(object, "journal_sequence"),
                new WorkstationEndpointEffectId(WorkstationEndpointJson.string(object, "effect_identity")),
                new WorkstationInstanceId(WorkstationEndpointJson.string(object, "instance_identity")),
                WorkstationEndpointJson.string(object, "invocation_identity"),
                WorkstationEndpointEffectKind.valueOf(WorkstationEndpointJson.string(object, "effect_kind")),
                WorkstationEndpointJson.integer(object, "slot_index"),
                deserializeStack(WorkstationEndpointJson.object(object.get("exact_stack"), "exact stack")),
                WorkstationEndpointJson.longValue(object, "expected_inventory_revision"),
                WorkstationEndpointJson.longValue(object, "post_inventory_revision"),
                WorkstationEndpointJson.longValue(object, "expected_endpoint_effect_revision"),
                WorkstationEndpointJson.longValue(object, "endpoint_effect_revision"),
                WorkstationEndpointJson.string(object, "pre_operation_state_identity"),
                WorkstationEndpointJson.string(object, "post_operation_state_identity"),
                WorkstationEndpointJson.longValue(object, "previous_owner_result_journal_sequence"),
                new WorkstationEndpointFreshnessIdentity(
                        WorkstationEndpointJson.string(object, "pre_freshness_identity")
                ),
                new WorkstationEndpointFreshnessIdentity(
                        WorkstationEndpointJson.string(object, "post_freshness_identity")
                ),
                WorkstationEndpointJson.string(object, "endpoint_configuration_identity"),
                WorkstationEndpointJournalState.valueOf(WorkstationEndpointJson.string(object, "state")),
                WorkstationEndpointJson.longValue(object, "creation_revision"),
                WorkstationEndpointJson.longValue(object, "last_update_revision"),
                optionalResult(object),
                WorkstationEndpointJson.optionalString(object, "failure_detail")
        );
    }

    private JsonObject serializeResult(WorkstationEndpointOwnerResult result) {
        JsonObject object = new JsonObject();
        object.addProperty("evidence_identity", result.evidenceIdentity());
        object.addProperty("content_digest", result.contentDigest());
        object.addProperty("schema_version", result.schemaVersion());
        object.addProperty("journal_sequence", result.journalSequence());
        object.addProperty("effect_identity", result.effectId().value());
        object.addProperty("instance_identity", result.instanceId().value());
        object.addProperty("invocation_identity", result.invocationIdentity());
        object.addProperty("effect_kind", result.effectKind().name());
        object.addProperty("result_code", result.resultCode().name());
        object.add("exact_stack", serializeStack(result.exactStack()));
        object.addProperty("pre_inventory_revision", result.preInventoryRevision());
        object.addProperty("post_inventory_revision", result.postInventoryRevision());
        object.addProperty("pre_endpoint_effect_revision", result.preEndpointEffectRevision());
        object.addProperty("endpoint_effect_revision", result.endpointEffectRevision());
        object.addProperty("pre_freshness_identity", result.preFreshnessIdentity().value());
        object.addProperty("post_freshness_identity", result.postFreshnessIdentity().value());
        object.addProperty("endpoint_configuration_identity", result.endpointConfigurationIdentity());
        result.failureDetail().ifPresent(value -> object.addProperty("failure_detail", value));
        return object;
    }

    private Optional<WorkstationEndpointOwnerResult> optionalResult(JsonObject object) {
        JsonElement element = object.get("owner_result");
        if (element == null || element.isJsonNull()) return Optional.empty();
        JsonObject result = WorkstationEndpointJson.object(element, "endpoint owner result");
        return Optional.of(new WorkstationEndpointOwnerResult(
                WorkstationEndpointJson.string(result, "evidence_identity"),
                WorkstationEndpointJson.string(result, "content_digest"),
                WorkstationEndpointJson.integer(result, "schema_version"),
                WorkstationEndpointJson.longValue(result, "journal_sequence"),
                new WorkstationEndpointEffectId(WorkstationEndpointJson.string(result, "effect_identity")),
                new WorkstationInstanceId(WorkstationEndpointJson.string(result, "instance_identity")),
                WorkstationEndpointJson.string(result, "invocation_identity"),
                WorkstationEndpointEffectKind.valueOf(WorkstationEndpointJson.string(result, "effect_kind")),
                WorkstationEndpointResultCode.valueOf(WorkstationEndpointJson.string(result, "result_code")),
                deserializeStack(WorkstationEndpointJson.object(result.get("exact_stack"), "exact stack")),
                WorkstationEndpointJson.longValue(result, "pre_inventory_revision"),
                WorkstationEndpointJson.longValue(result, "post_inventory_revision"),
                WorkstationEndpointJson.longValue(result, "pre_endpoint_effect_revision"),
                WorkstationEndpointJson.longValue(result, "endpoint_effect_revision"),
                new WorkstationEndpointFreshnessIdentity(
                        WorkstationEndpointJson.string(result, "pre_freshness_identity")
                ),
                new WorkstationEndpointFreshnessIdentity(
                        WorkstationEndpointJson.string(result, "post_freshness_identity")
                ),
                WorkstationEndpointJson.string(result, "endpoint_configuration_identity"),
                WorkstationEndpointJson.optionalString(result, "failure_detail")
        ));
    }

    private static JsonObject serializeStack(WorkstationEndpointStackPayload stack) {
        JsonObject object = new JsonObject();
        object.addProperty("encoding_identity", stack.encodingIdentity());
        object.addProperty("item_identity", stack.itemIdentity());
        object.addProperty("count", stack.count());
        object.addProperty("content_digest", stack.contentDigest());
        object.addProperty("encoded_stack", stack.encodedStack());
        return object;
    }

    private static WorkstationEndpointStackPayload deserializeStack(JsonObject object) {
        return new WorkstationEndpointStackPayload(
                WorkstationEndpointJson.string(object, "encoding_identity"),
                WorkstationEndpointJson.string(object, "item_identity"),
                WorkstationEndpointJson.integer(object, "count"),
                WorkstationEndpointJson.string(object, "content_digest"),
                WorkstationEndpointJson.string(object, "encoded_stack")
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

    private String readPublished() {
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to verify Workstation endpoint journal " + filePath, exception);
        }
    }
}
