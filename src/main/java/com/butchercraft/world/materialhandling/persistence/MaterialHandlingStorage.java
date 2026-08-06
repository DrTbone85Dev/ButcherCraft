package com.butchercraft.world.materialhandling.persistence;

import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectId;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind;
import com.butchercraft.workstation.endpoint.WorkstationEndpointFreshnessIdentity;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationEndpointObservation;
import com.butchercraft.workstation.endpoint.WorkstationEndpointOwnerResult;
import com.butchercraft.workstation.endpoint.WorkstationEndpointPreparation;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;
import com.butchercraft.workstation.endpoint.WorkstationEndpointStackPayload;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.materialhandling.MaterialHandlingRuntime;
import com.butchercraft.world.materialhandling.MaterialHandlingSchema;
import com.butchercraft.world.materialhandling.MaterialCustodyLocation;
import com.butchercraft.world.materialhandling.MaterialTransferId;
import com.butchercraft.world.materialhandling.MaterialTransferLifecycle;
import com.butchercraft.world.materialhandling.MaterialTransferRecord;
import com.butchercraft.world.materialhandling.MaterialTransferEvidenceReference;
import com.butchercraft.world.materialhandling.MaterialTransferTerminalEvidence;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MaterialHandlingStorage {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Path filePath;

    public MaterialHandlingStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Path filePath() {
        return filePath;
    }

    public Optional<MaterialHandlingRuntime> loadExisting() {
        Path temporaryFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        if (!Files.exists(filePath)) {
            if (Files.exists(temporaryFile)) {
                throw new IllegalStateException(
                        "Interrupted Material Handling publication requires recovery: " + temporaryFile
                );
            }
            return Optional.empty();
        }
        try {
            return Optional.of(deserialize(Files.readString(filePath, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load Material Handling state from " + filePath, exception);
        }
    }

    public void save(MaterialHandlingRuntime runtime) {
        String serialized = serialize(runtime);
        publishStrict(serialized);
        MaterialHandlingRuntime verified = deserialize(readPublished());
        if (!verified.equals(runtime)) {
            throw new IllegalStateException("Material Handling state failed semantic read-back verification");
        }
    }

    public String serialize(MaterialHandlingRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", runtime.schemaVersion());
        root.addProperty("owner_revision", runtime.ownerRevision());
        root.addProperty("next_transfer_sequence", runtime.nextTransferSequence());
        root.add("world_identity", worldIdentity(runtime.worldIdentity()));
        root.addProperty("configuration_identity", runtime.configurationIdentity());
        JsonArray transfers = new JsonArray();
        runtime.transfers().forEach(transfer -> transfers.add(transfer(transfer)));
        root.add("transfers", transfers);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public MaterialHandlingRuntime deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = object(JsonParser.parseString(json), "Material Handling root");
            int schema = integer(root, "schema_version");
            if (schema != MaterialHandlingSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported Material Handling schema version: " + schema);
            }
            List<MaterialTransferRecord> transfers = new ArrayList<>();
            for (JsonElement element : array(root, "transfers")) {
                transfers.add(transfer(object(element, "Material Transfer record")));
            }
            return new MaterialHandlingRuntime(
                    schema,
                    longValue(root, "owner_revision"),
                    longValue(root, "next_transfer_sequence"),
                    worldIdentity(object(root.get("world_identity"), "world identity")),
                    string(root, "configuration_identity"),
                    transfers
            );
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt Material Handling persistence", exception);
        }
    }

    private JsonObject transfer(MaterialTransferRecord transfer) {
        JsonObject object = new JsonObject();
        object.addProperty("schema_version", transfer.schemaVersion());
        object.addProperty("transfer_identity", transfer.transferId().value());
        object.addProperty("sequence", transfer.sequence());
        object.addProperty("request_content_digest", transfer.requestContentDigest());
        object.add("world_identity", worldIdentity(transfer.worldIdentity()));
        object.add("source", reference(transfer.source()));
        object.add("destination", reference(transfer.destination()));
        object.addProperty("material_identity", transfer.materialIdentity());
        object.addProperty("quantity", transfer.quantity());
        object.addProperty("assignment_type_identity", transfer.assignmentTypeIdentity());
        object.addProperty("source_invocation_identity", transfer.sourceInvocationIdentity());
        object.addProperty("destination_invocation_identity", transfer.destinationInvocationIdentity());
        object.addProperty("return_invocation_identity", transfer.returnInvocationIdentity());
        object.addProperty("configuration_identity", transfer.configurationIdentity());
        object.addProperty("lifecycle", transfer.lifecycle().name());
        transfer.custodyLocation().ifPresent(value -> object.addProperty("custody_location", value.name()));
        transfer.sourceObservation().ifPresent(value -> object.add("source_observation", observation(value)));
        transfer.sourcePreparation().ifPresent(value -> object.add("source_preparation", preparation(value)));
        transfer.sourceResult().ifPresent(value -> object.add("source_result", ownerResult(value)));
        transfer.exactTransferStack().ifPresent(value -> object.add("exact_transfer_stack", stack(value)));
        transfer.inTransitCustody().ifPresent(value -> object.add("in_transit_custody", stack(value)));
        transfer.destinationObservation().ifPresent(value -> object.add("destination_observation", observation(value)));
        transfer.destinationPreparation().ifPresent(value -> object.add("destination_preparation", preparation(value)));
        transfer.destinationResult().ifPresent(value -> object.add("destination_result", ownerResult(value)));
        transfer.returnObservation().ifPresent(value -> object.add("return_observation", observation(value)));
        transfer.returnPreparation().ifPresent(value -> object.add("return_preparation", preparation(value)));
        transfer.returnResult().ifPresent(value -> object.add("return_result", ownerResult(value)));
        transfer.terminalEvidence().ifPresent(value -> object.add("terminal_evidence", terminalEvidence(value)));
        transfer.terminalDetail().ifPresent(value -> object.addProperty("terminal_detail", value));
        object.addProperty("creation_revision", transfer.creationRevision());
        object.addProperty("last_update_revision", transfer.lastUpdateRevision());
        object.addProperty("state_evidence_identity", transfer.stateEvidenceIdentity());
        object.addProperty("state_content_digest", transfer.stateContentDigest());
        return object;
    }

    private MaterialTransferRecord transfer(JsonObject object) {
        return new MaterialTransferRecord(
                integer(object, "schema_version"),
                new MaterialTransferId(string(object, "transfer_identity")),
                longValue(object, "sequence"),
                string(object, "request_content_digest"),
                worldIdentity(object(object.get("world_identity"), "world identity")),
                reference(object(object.get("source"), "source endpoint")),
                reference(object(object.get("destination"), "destination endpoint")),
                string(object, "material_identity"),
                integer(object, "quantity"),
                string(object, "assignment_type_identity"),
                string(object, "source_invocation_identity"),
                string(object, "destination_invocation_identity"),
                string(object, "return_invocation_identity"),
                string(object, "configuration_identity"),
                MaterialTransferLifecycle.valueOf(string(object, "lifecycle")),
                optionalString(object, "custody_location").map(MaterialCustodyLocation::valueOf),
                optionalObject(object, "source_observation").map(this::observation),
                optionalObject(object, "source_preparation").map(this::preparation),
                optionalObject(object, "source_result").map(this::ownerResult),
                optionalObject(object, "exact_transfer_stack").map(value -> stack(value)),
                optionalObject(object, "in_transit_custody").map(value -> stack(value)),
                optionalObject(object, "destination_observation").map(this::observation),
                optionalObject(object, "destination_preparation").map(this::preparation),
                optionalObject(object, "destination_result").map(this::ownerResult),
                optionalObject(object, "return_observation").map(this::observation),
                optionalObject(object, "return_preparation").map(this::preparation),
                optionalObject(object, "return_result").map(this::ownerResult),
                optionalObject(object, "terminal_evidence").map(this::terminalEvidence),
                optionalString(object, "terminal_detail"),
                longValue(object, "creation_revision"),
                longValue(object, "last_update_revision"),
                string(object, "state_evidence_identity"),
                string(object, "state_content_digest")
        );
    }

    private JsonObject terminalEvidence(MaterialTransferTerminalEvidence evidence) {
        JsonObject object = new JsonObject();
        object.addProperty("material_identity", evidence.materialIdentity());
        object.addProperty("quantity", evidence.quantity());
        object.addProperty("stack_content_identity", evidence.stackContentIdentity());
        addReference(object, "source_observation", evidence.sourceObservation());
        addReference(object, "source_preparation", evidence.sourcePreparation());
        addReference(object, "source_result", evidence.sourceResult());
        addReference(object, "destination_observation", evidence.destinationObservation());
        addReference(object, "destination_preparation", evidence.destinationPreparation());
        addReference(object, "destination_result", evidence.destinationResult());
        addReference(object, "return_observation", evidence.returnObservation());
        addReference(object, "return_preparation", evidence.returnPreparation());
        addReference(object, "return_result", evidence.returnResult());
        object.addProperty("content_digest", evidence.contentDigest());
        return object;
    }

    private MaterialTransferTerminalEvidence terminalEvidence(JsonObject object) {
        return new MaterialTransferTerminalEvidence(
                string(object, "material_identity"),
                integer(object, "quantity"),
                string(object, "stack_content_identity"),
                optionalObject(object, "source_observation").map(this::evidenceReference),
                optionalObject(object, "source_preparation").map(this::evidenceReference),
                optionalObject(object, "source_result").map(this::evidenceReference),
                optionalObject(object, "destination_observation").map(this::evidenceReference),
                optionalObject(object, "destination_preparation").map(this::evidenceReference),
                optionalObject(object, "destination_result").map(this::evidenceReference),
                optionalObject(object, "return_observation").map(this::evidenceReference),
                optionalObject(object, "return_preparation").map(this::evidenceReference),
                optionalObject(object, "return_result").map(this::evidenceReference),
                string(object, "content_digest")
        );
    }

    private static void addReference(
            JsonObject object,
            String field,
            Optional<MaterialTransferEvidenceReference> reference
    ) {
        reference.ifPresent(value -> {
            JsonObject nested = new JsonObject();
            nested.addProperty("evidence_identity", value.evidenceIdentity());
            nested.addProperty("content_digest", value.contentDigest());
            object.add(field, nested);
        });
    }

    private MaterialTransferEvidenceReference evidenceReference(JsonObject object) {
        return new MaterialTransferEvidenceReference(
                string(object, "evidence_identity"),
                string(object, "content_digest")
        );
    }

    private JsonObject observation(WorkstationEndpointObservation observation) {
        JsonObject object = new JsonObject();
        object.addProperty("evidence_identity", observation.evidenceIdentity());
        object.addProperty("content_digest", observation.contentDigest());
        object.addProperty("instance_identity", observation.instanceId().value());
        object.addProperty("effect_kind", observation.effectKind().name());
        object.addProperty("slot_index", observation.slotIndex());
        object.add("exact_effect_stack", stack(observation.exactEffectStack()));
        object.addProperty("observed_slot_content_digest", observation.observedSlotContentDigest());
        object.addProperty("inventory_revision", observation.inventoryRevision());
        object.addProperty("endpoint_effect_revision", observation.endpointEffectRevision());
        object.addProperty("operation_state_identity", observation.operationStateIdentity());
        object.addProperty("owner_result_journal_sequence", observation.ownerResultJournalSequence());
        object.addProperty("freshness_identity", observation.freshnessIdentity().value());
        object.addProperty("endpoint_configuration_identity", observation.endpointConfigurationIdentity());
        return object;
    }

    private WorkstationEndpointObservation observation(JsonObject object) {
        return new WorkstationEndpointObservation(
                string(object, "evidence_identity"),
                string(object, "content_digest"),
                new WorkstationInstanceId(string(object, "instance_identity")),
                WorkstationEndpointEffectKind.valueOf(string(object, "effect_kind")),
                integer(object, "slot_index"),
                stack(object(object.get("exact_effect_stack"), "exact effect stack")),
                string(object, "observed_slot_content_digest"),
                longValue(object, "inventory_revision"),
                longValue(object, "endpoint_effect_revision"),
                string(object, "operation_state_identity"),
                longValue(object, "owner_result_journal_sequence"),
                new WorkstationEndpointFreshnessIdentity(string(object, "freshness_identity")),
                string(object, "endpoint_configuration_identity")
        );
    }

    private JsonObject preparation(WorkstationEndpointPreparation preparation) {
        JsonObject object = new JsonObject();
        object.addProperty("evidence_identity", preparation.evidenceIdentity());
        object.addProperty("content_digest", preparation.contentDigest());
        object.addProperty("journal_sequence", preparation.journalSequence());
        object.addProperty("effect_identity", preparation.effectId().value());
        object.addProperty("instance_identity", preparation.instanceId().value());
        object.addProperty("invocation_identity", preparation.invocationIdentity());
        object.addProperty("effect_kind", preparation.effectKind().name());
        object.addProperty("slot_index", preparation.slotIndex());
        object.add("exact_stack", stack(preparation.exactStack()));
        object.addProperty("expected_inventory_revision", preparation.expectedInventoryRevision());
        object.addProperty("expected_endpoint_effect_revision", preparation.expectedEndpointEffectRevision());
        object.addProperty("pre_freshness_identity", preparation.preFreshnessIdentity().value());
        object.addProperty("post_freshness_identity", preparation.postFreshnessIdentity().value());
        object.addProperty("endpoint_configuration_identity", preparation.endpointConfigurationIdentity());
        return object;
    }

    private WorkstationEndpointPreparation preparation(JsonObject object) {
        return new WorkstationEndpointPreparation(
                string(object, "evidence_identity"),
                string(object, "content_digest"),
                longValue(object, "journal_sequence"),
                new WorkstationEndpointEffectId(string(object, "effect_identity")),
                new WorkstationInstanceId(string(object, "instance_identity")),
                string(object, "invocation_identity"),
                WorkstationEndpointEffectKind.valueOf(string(object, "effect_kind")),
                integer(object, "slot_index"),
                stack(object(object.get("exact_stack"), "exact stack")),
                longValue(object, "expected_inventory_revision"),
                longValue(object, "expected_endpoint_effect_revision"),
                new WorkstationEndpointFreshnessIdentity(string(object, "pre_freshness_identity")),
                new WorkstationEndpointFreshnessIdentity(string(object, "post_freshness_identity")),
                string(object, "endpoint_configuration_identity")
        );
    }

    private JsonObject ownerResult(WorkstationEndpointOwnerResult result) {
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
        object.add("exact_stack", stack(result.exactStack()));
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

    private WorkstationEndpointOwnerResult ownerResult(JsonObject object) {
        return new WorkstationEndpointOwnerResult(
                string(object, "evidence_identity"),
                string(object, "content_digest"),
                integer(object, "schema_version"),
                longValue(object, "journal_sequence"),
                new WorkstationEndpointEffectId(string(object, "effect_identity")),
                new WorkstationInstanceId(string(object, "instance_identity")),
                string(object, "invocation_identity"),
                WorkstationEndpointEffectKind.valueOf(string(object, "effect_kind")),
                WorkstationEndpointResultCode.valueOf(string(object, "result_code")),
                stack(object(object.get("exact_stack"), "exact stack")),
                longValue(object, "pre_inventory_revision"),
                longValue(object, "post_inventory_revision"),
                longValue(object, "pre_endpoint_effect_revision"),
                longValue(object, "endpoint_effect_revision"),
                new WorkstationEndpointFreshnessIdentity(string(object, "pre_freshness_identity")),
                new WorkstationEndpointFreshnessIdentity(string(object, "post_freshness_identity")),
                string(object, "endpoint_configuration_identity"),
                optionalString(object, "failure_detail")
        );
    }

    private static JsonObject reference(WorkstationEndpointReference reference) {
        JsonObject object = new JsonObject();
        object.addProperty("instance_identity", reference.instanceId().value());
        object.add("endpoint_key", key(reference.endpointKey()));
        object.addProperty("generation", reference.generation());
        return object;
    }

    private static WorkstationEndpointReference reference(JsonObject object) {
        return new WorkstationEndpointReference(
                new WorkstationInstanceId(string(object, "instance_identity")),
                key(object(object.get("endpoint_key"), "endpoint key")),
                longValue(object, "generation")
        );
    }

    private static JsonObject key(WorkstationEndpointKey key) {
        JsonObject object = new JsonObject();
        object.addProperty("workstation_type_identity", key.workstationTypeIdentity());
        object.addProperty("dimension_identity", key.dimensionIdentity());
        object.addProperty("x", key.x());
        object.addProperty("y", key.y());
        object.addProperty("z", key.z());
        return object;
    }

    private static WorkstationEndpointKey key(JsonObject object) {
        return new WorkstationEndpointKey(
                string(object, "workstation_type_identity"),
                string(object, "dimension_identity"),
                integer(object, "x"),
                integer(object, "y"),
                integer(object, "z")
        );
    }

    private static JsonObject stack(WorkstationEndpointStackPayload stack) {
        JsonObject object = new JsonObject();
        object.addProperty("encoding_identity", stack.encodingIdentity());
        object.addProperty("item_identity", stack.itemIdentity());
        object.addProperty("count", stack.count());
        object.addProperty("content_digest", stack.contentDigest());
        object.addProperty("encoded_stack", stack.encodedStack());
        return object;
    }

    private static WorkstationEndpointStackPayload stack(JsonObject object) {
        return new WorkstationEndpointStackPayload(
                string(object, "encoding_identity"),
                string(object, "item_identity"),
                integer(object, "count"),
                string(object, "content_digest"),
                string(object, "encoded_stack")
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

    private void publishStrict(String canonicalJson) {
        Path temporaryFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        try {
            Path parent = filePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            byte[] bytes = canonicalJson.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(
                    temporaryFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(
                        temporaryFile,
                        filePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Atomic replacement is required for " + filePath, exception);
            }
            if (!Files.readString(filePath, StandardCharsets.UTF_8).equals(canonicalJson)) {
                throw new IOException("Material Handling read-back verification failed: " + filePath);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed strict Material Handling publication to " + filePath, exception);
        } finally {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException ignored) {
                // Orphaned temporary files never outrank the authoritative candidate.
            }
        }
    }

    private String readPublished() {
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to verify Material Handling state " + filePath, exception);
        }
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

    private static Optional<JsonObject> optionalObject(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) return Optional.empty();
        return Optional.of(object(element, field));
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
}
