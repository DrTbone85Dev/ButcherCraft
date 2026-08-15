package com.butchercraft.workstation.endpoint.persistence;

import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectIdV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind;
import com.butchercraft.workstation.endpoint.WorkstationEndpointFreshnessIdentityV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournal;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalRecordV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalState;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationEndpointObservationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointOwnerResultV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointPreparationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;
import com.butchercraft.workstation.endpoint.WorkstationEndpointSchema;
import com.butchercraft.workstation.endpoint.WorkstationEndpointStackPayload;
import com.butchercraft.workstation.endpoint.WorkstationEndpointStackStateV2;
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

/** Schema-aware successor storage. It reads schema 1 without reinterpreting its records. */
public final class WorkstationEndpointJournalV2Storage {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Path filePath;

    public WorkstationEndpointJournalV2Storage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Optional<LoadedJournal> loadVersioned() {
        Path temporaryFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        if (!Files.exists(filePath)) {
            if (Files.exists(temporaryFile)) {
                throw new IllegalStateException("Interrupted Workstation endpoint publication requires recovery: "
                        + temporaryFile);
            }
            return Optional.empty();
        }
        try {
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            JsonObject root = WorkstationEndpointJson.object(JsonParser.parseString(json), "Workstation endpoint root");
            int schema = WorkstationEndpointJson.integer(root, "schema_version");
            if (schema == WorkstationEndpointSchema.LEGACY_ENDPOINT_PROTOCOL_VERSION) {
                WorkstationEndpointJournal legacy = new WorkstationEndpointJournalStorage(filePath).deserialize(json);
                return Optional.of(new LegacyJournal(legacy, json));
            }
            if (schema == WorkstationEndpointSchema.STACK_AWARE_JOURNAL_SCHEMA_VERSION) {
                return Optional.of(new StackAwareJournal(deserialize(json)));
            }
            throw new IllegalArgumentException("Unsupported Workstation endpoint schema version: " + schema);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load Workstation endpoint journal from " + filePath, exception);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Corrupt Workstation endpoint journal", exception);
        }
    }

    public void save(WorkstationEndpointJournalV2 journal) {
        String serialized = serialize(journal);
        StrictAtomicJsonFile.publish(filePath, serialized);
        if (!deserialize(readPublished()).equals(journal)) {
            throw new IllegalStateException("Schema-2 endpoint journal failed semantic read-back verification");
        }
    }

    public String serialize(WorkstationEndpointJournalV2 journal) {
        Objects.requireNonNull(journal, "journal");
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", journal.schemaVersion());
        root.addProperty("owner_revision", journal.ownerRevision());
        root.addProperty("next_journal_sequence", journal.nextJournalSequence());
        root.add("world_identity", worldIdentity(journal.worldIdentity()));
        root.addProperty("endpoint_configuration_identity", journal.endpointConfigurationIdentity());
        journal.immutableLegacySchema1Journal().ifPresent(value ->
                root.addProperty("immutable_legacy_schema_1_journal", value));
        JsonArray records = new JsonArray();
        journal.records().forEach(record -> records.add(serializeRecord(record)));
        root.add("endpoint_effects", records);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public WorkstationEndpointJournalV2 deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = WorkstationEndpointJson.object(JsonParser.parseString(json), "Workstation endpoint root");
            int schema = WorkstationEndpointJson.integer(root, "schema_version");
            if (schema != WorkstationEndpointSchema.STACK_AWARE_JOURNAL_SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported Workstation endpoint schema version: " + schema);
            }
            Optional<String> legacy = WorkstationEndpointJson.optionalString(
                    root,
                    "immutable_legacy_schema_1_journal"
            );
            legacy.ifPresent(value -> new WorkstationEndpointJournalStorage(filePath).deserialize(value));
            List<WorkstationEndpointJournalRecordV2> records = new ArrayList<>();
            for (JsonElement element : WorkstationEndpointJson.array(root, "endpoint_effects")) {
                records.add(deserializeRecord(WorkstationEndpointJson.object(element, "endpoint journal record")));
            }
            return new WorkstationEndpointJournalV2(
                    schema, WorkstationEndpointJson.longValue(root, "owner_revision"),
                    WorkstationEndpointJson.longValue(root, "next_journal_sequence"),
                    parseWorldIdentity(WorkstationEndpointJson.object(root.get("world_identity"), "world identity")),
                    WorkstationEndpointJson.string(root, "endpoint_configuration_identity"), legacy, records
            );
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt schema-2 Workstation endpoint journal", exception);
        }
    }

    public static JsonObject serializeObservation(WorkstationEndpointObservationV2 observation) {
        JsonObject object = new JsonObject();
        object.addProperty("evidence_identity", observation.evidenceIdentity());
        object.addProperty("content_digest", observation.contentDigest());
        object.addProperty("protocol_version", observation.protocolVersion());
        object.addProperty("instance_identity", observation.instanceId().value());
        object.add("endpoint_key", endpointKey(observation.endpointKey()));
        object.addProperty("effect_kind", observation.effectKind().name());
        object.addProperty("slot_index", observation.slotIndex());
        object.addProperty("requested_quantity", observation.requestedQuantity());
        object.add("pre_stack", serializeState(observation.preStack()));
        object.add("transfer_stack", serializeState(observation.transferStack()));
        object.add("remainder_stack", serializeState(observation.remainderStack()));
        object.add("post_stack", serializeState(observation.postStack()));
        object.addProperty("inventory_revision", observation.inventoryRevision());
        object.addProperty("endpoint_effect_revision", observation.endpointEffectRevision());
        object.addProperty("operation_state_identity", observation.operationStateIdentity());
        object.addProperty("owner_result_journal_sequence", observation.ownerResultJournalSequence());
        object.addProperty("journal_revision", observation.journalRevision());
        object.addProperty("effective_slot_capacity", observation.effectiveSlotCapacity());
        object.addProperty("freshness_identity", observation.freshnessIdentity().value());
        object.addProperty("endpoint_configuration_identity", observation.endpointConfigurationIdentity());
        return object;
    }

    public static WorkstationEndpointObservationV2 deserializeObservation(JsonObject object) {
        return new WorkstationEndpointObservationV2(
                WorkstationEndpointJson.string(object, "evidence_identity"),
                WorkstationEndpointJson.string(object, "content_digest"),
                WorkstationEndpointJson.integer(object, "protocol_version"),
                new WorkstationInstanceId(WorkstationEndpointJson.string(object, "instance_identity")),
                parseEndpointKey(WorkstationEndpointJson.object(object.get("endpoint_key"), "endpoint key")),
                WorkstationEndpointEffectKind.valueOf(WorkstationEndpointJson.string(object, "effect_kind")),
                WorkstationEndpointJson.integer(object, "slot_index"),
                WorkstationEndpointJson.integer(object, "requested_quantity"),
                deserializeState(WorkstationEndpointJson.object(object.get("pre_stack"), "pre stack")),
                deserializeState(WorkstationEndpointJson.object(object.get("transfer_stack"), "transfer stack")),
                deserializeState(WorkstationEndpointJson.object(object.get("remainder_stack"), "remainder stack")),
                deserializeState(WorkstationEndpointJson.object(object.get("post_stack"), "post stack")),
                WorkstationEndpointJson.longValue(object, "inventory_revision"),
                WorkstationEndpointJson.longValue(object, "endpoint_effect_revision"),
                WorkstationEndpointJson.string(object, "operation_state_identity"),
                WorkstationEndpointJson.longValue(object, "owner_result_journal_sequence"),
                WorkstationEndpointJson.longValue(object, "journal_revision"),
                WorkstationEndpointJson.integer(object, "effective_slot_capacity"),
                new WorkstationEndpointFreshnessIdentityV2(
                        WorkstationEndpointJson.string(object, "freshness_identity")
                ),
                WorkstationEndpointJson.string(object, "endpoint_configuration_identity")
        );
    }

    public static JsonObject serializePreparation(WorkstationEndpointPreparationV2 preparation) {
        JsonObject object = new JsonObject();
        object.addProperty("evidence_identity", preparation.evidenceIdentity());
        object.addProperty("content_digest", preparation.contentDigest());
        object.addProperty("protocol_version", preparation.protocolVersion());
        object.addProperty("journal_sequence", preparation.journalSequence());
        object.addProperty("committed_journal_revision", preparation.committedJournalRevision());
        object.addProperty("effect_identity", preparation.effectId().value());
        object.addProperty("invocation_identity", preparation.invocationIdentity());
        object.add("observation", serializeObservation(preparation.observation()));
        object.addProperty("post_inventory_revision", preparation.postInventoryRevision());
        object.addProperty("post_endpoint_effect_revision", preparation.postEndpointEffectRevision());
        object.addProperty("post_operation_state_identity", preparation.postOperationStateIdentity());
        object.addProperty("post_freshness_identity", preparation.postFreshnessIdentity().value());
        return object;
    }

    public static WorkstationEndpointPreparationV2 deserializePreparation(JsonObject object) {
        return new WorkstationEndpointPreparationV2(
                WorkstationEndpointJson.string(object, "evidence_identity"),
                WorkstationEndpointJson.string(object, "content_digest"),
                WorkstationEndpointJson.integer(object, "protocol_version"),
                WorkstationEndpointJson.longValue(object, "journal_sequence"),
                WorkstationEndpointJson.longValue(object, "committed_journal_revision"),
                new WorkstationEndpointEffectIdV2(WorkstationEndpointJson.string(object, "effect_identity")),
                WorkstationEndpointJson.string(object, "invocation_identity"),
                deserializeObservation(WorkstationEndpointJson.object(object.get("observation"), "observation")),
                WorkstationEndpointJson.longValue(object, "post_inventory_revision"),
                WorkstationEndpointJson.longValue(object, "post_endpoint_effect_revision"),
                WorkstationEndpointJson.string(object, "post_operation_state_identity"),
                new WorkstationEndpointFreshnessIdentityV2(
                        WorkstationEndpointJson.string(object, "post_freshness_identity")
                )
        );
    }

    public static JsonObject serializeOwnerResult(WorkstationEndpointOwnerResultV2 result) {
        JsonObject object = new JsonObject();
        object.addProperty("evidence_identity", result.evidenceIdentity());
        object.addProperty("content_digest", result.contentDigest());
        object.addProperty("protocol_version", result.protocolVersion());
        object.addProperty("result_code", result.resultCode().name());
        result.failureDetail().ifPresent(detail -> object.addProperty("failure_detail", detail));
        return object;
    }

    public static WorkstationEndpointOwnerResultV2 deserializeOwnerResult(
            JsonObject object,
            WorkstationEndpointPreparationV2 preparation
    ) {
        return new WorkstationEndpointOwnerResultV2(
                WorkstationEndpointJson.string(object, "evidence_identity"),
                WorkstationEndpointJson.string(object, "content_digest"),
                WorkstationEndpointJson.integer(object, "protocol_version"), preparation,
                WorkstationEndpointResultCode.valueOf(WorkstationEndpointJson.string(object, "result_code")),
                WorkstationEndpointJson.optionalString(object, "failure_detail")
        );
    }

    private JsonObject serializeRecord(WorkstationEndpointJournalRecordV2 record) {
        JsonObject object = new JsonObject();
        object.addProperty("schema_version", record.schemaVersion());
        object.addProperty("journal_sequence", record.journalSequence());
        object.add("preparation", serializePreparation(record.preparation()));
        object.addProperty("state", record.state().name());
        object.addProperty("creation_revision", record.creationRevision());
        object.addProperty("last_update_revision", record.lastUpdateRevision());
        record.ownerResult().ifPresent(result -> object.add("owner_result", serializeOwnerResult(result)));
        record.failureDetail().ifPresent(detail -> object.addProperty("failure_detail", detail));
        return object;
    }

    private WorkstationEndpointJournalRecordV2 deserializeRecord(JsonObject object) {
        WorkstationEndpointPreparationV2 preparation = deserializePreparation(
                WorkstationEndpointJson.object(object.get("preparation"), "preparation")
        );
        JsonElement resultElement = object.get("owner_result");
        Optional<WorkstationEndpointOwnerResultV2> result = resultElement == null || resultElement.isJsonNull()
                ? Optional.empty()
                : Optional.of(deserializeOwnerResult(
                        WorkstationEndpointJson.object(resultElement, "owner result"),
                        preparation
                ));
        return new WorkstationEndpointJournalRecordV2(
                WorkstationEndpointJson.integer(object, "schema_version"),
                WorkstationEndpointJson.longValue(object, "journal_sequence"), preparation,
                WorkstationEndpointJournalState.valueOf(WorkstationEndpointJson.string(object, "state")),
                WorkstationEndpointJson.longValue(object, "creation_revision"),
                WorkstationEndpointJson.longValue(object, "last_update_revision"), result,
                WorkstationEndpointJson.optionalString(object, "failure_detail")
        );
    }

    public static JsonObject serializeState(WorkstationEndpointStackStateV2 state) {
        JsonObject object = new JsonObject();
        object.addProperty("content_identity", state.contentIdentity());
        object.addProperty("compatibility_identity", state.compatibilityIdentity());
        state.stack().ifPresent(stack -> object.add("stack", serializeStack(stack)));
        return object;
    }

    public static WorkstationEndpointStackStateV2 deserializeState(JsonObject object) {
        JsonElement stack = object.get("stack");
        Optional<WorkstationEndpointStackPayload> payload = stack == null || stack.isJsonNull()
                ? Optional.empty()
                : Optional.of(deserializeStack(WorkstationEndpointJson.object(stack, "exact stack")));
        return new WorkstationEndpointStackStateV2(
                payload,
                WorkstationEndpointJson.string(object, "content_identity"),
                WorkstationEndpointJson.string(object, "compatibility_identity")
        );
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
                WorkstationEndpointJson.string(object, "workstation_type_identity"),
                WorkstationEndpointJson.string(object, "dimension_identity"),
                WorkstationEndpointJson.integer(object, "x"), WorkstationEndpointJson.integer(object, "y"),
                WorkstationEndpointJson.integer(object, "z")
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
                WorkstationEndpointJson.string(object, "identity"),
                WorkstationEndpointJson.integer(object, "schema_version"),
                WorkstationEndpointJson.string(object, "root_digest")
        );
    }

    private String readPublished() {
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read published endpoint journal", exception);
        }
    }

    public sealed interface LoadedJournal permits LegacyJournal, StackAwareJournal {
        int schemaVersion();
    }

    public record LegacyJournal(WorkstationEndpointJournal journal, String immutableCanonicalJson)
            implements LoadedJournal {
        public LegacyJournal {
            journal = Objects.requireNonNull(journal, "journal");
            immutableCanonicalJson = Objects.requireNonNull(immutableCanonicalJson, "immutableCanonicalJson");
        }

        @Override
        public int schemaVersion() {
            return WorkstationEndpointSchema.LEGACY_ENDPOINT_PROTOCOL_VERSION;
        }
    }

    public record StackAwareJournal(WorkstationEndpointJournalV2 journal) implements LoadedJournal {
        public StackAwareJournal {
            journal = Objects.requireNonNull(journal, "journal");
        }

        @Override
        public int schemaVersion() {
            return WorkstationEndpointSchema.STACK_AWARE_JOURNAL_SCHEMA_VERSION;
        }
    }
}
