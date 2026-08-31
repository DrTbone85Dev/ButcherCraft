package com.butchercraft.workstation.projection;

import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationEndpointStackPayload;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class WorkstationProjectionCodec {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Gson COMPACT_GSON = new GsonBuilder().disableHtmlEscaping().create();

    public byte[] freeze(DurableWorkstationProjection projection) {
        return (GSON.toJson(toJson(Objects.requireNonNull(projection, "projection"))) + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    public DurableWorkstationProjection decode(byte[] frozenBytes) {
        Objects.requireNonNull(frozenBytes, "frozenBytes");
        if (frozenBytes.length == 0 || frozenBytes.length > WorkstationProjectionSchema.MAXIMUM_RECORD_BYTES) {
            throw new IllegalArgumentException("Durable Workstation projection record size is invalid");
        }
        try {
            JsonObject root = object(JsonParser.parseString(new String(frozenBytes, StandardCharsets.UTF_8)), "root");
            int schema = integer(root, "schema_version");
            if (schema != WorkstationProjectionSchema.CURRENT_VERSION) {
                throw new UnsupportedWorkstationProjectionSchemaException(schema);
            }
            JsonObject world = object(required(root, "world_identity"), "world identity");
            WorldIdentityRootIdentity worldIdentity = new WorldIdentityRootIdentity(
                    string(world, "identity"), integer(world, "schema_version"), string(world, "root_digest"));
            JsonObject endpoint = object(required(root, "endpoint_key"), "endpoint key");
            List<WorkstationProjectionSlot> slots = new ArrayList<>();
            for (JsonElement element : array(root, "slots")) {
                JsonObject slot = object(element, "slot");
                Optional<WorkstationEndpointStackPayload> stack = slot.has("exact_stack")
                        ? Optional.of(stack(object(required(slot, "exact_stack"), "exact stack")))
                        : Optional.empty();
                slots.add(new WorkstationProjectionSlot(
                        integer(slot, "slot_index"), integer(slot, "configured_capacity"),
                        integer(slot, "effective_capacity"), stack));
            }
            Optional<WorkstationOperatingStateReference> operating = root.has("operating_state_reference")
                    ? Optional.of(operating(object(
                    required(root, "operating_state_reference"), "operating-state reference")))
                    : Optional.empty();
            String exactProjection = COMPACT_GSON.toJson(required(root, "exact_block_entity_projection"));
            WorkstationProjectionNbtCodec.decode(exactProjection);
            return new DurableWorkstationProjection(
                    schema,
                    worldIdentity,
                    new WorkstationInstanceId(string(root, "instance_identity")),
                    new WorkstationEndpointKey(
                            string(endpoint, "workstation_type_identity"),
                            string(endpoint, "dimension_identity"),
                            integer(endpoint, "x"), integer(endpoint, "y"), integer(endpoint, "z")),
                    longValue(root, "instance_generation"),
                    string(root, "instance_allocation_configuration_identity"),
                    string(root, "projection_configuration_identity"),
                    string(root, "block_entity_type_identity"),
                    longValue(root, "instance_registry_revision"),
                    longValue(root, "projection_revision"),
                    longValue(root, "inventory_revision"),
                    longValue(root, "endpoint_effect_revision"),
                    longValue(root, "last_applied_journal_sequence"),
                    string(root, "slot_capacity_configuration_identity"),
                    slots,
                    exactProjection,
                    optionalString(root, "prepared_endpoint_effect_identity"),
                    optionalString(root, "last_endpoint_effect_identity"),
                    optionalString(root, "last_endpoint_owner_result_identity"),
                    optionalString(root, "processing_operation_identity"),
                    optionalString(root, "processing_owner_result_identity"),
                    operating,
                    WorkstationProjectionStatus.valueOf(string(root, "status")),
                    optionalString(root, "retirement_reason"),
                    optionalString(root, "prior_active_projection_digest"),
                    string(root, "state_digest")
            );
        } catch (UnsupportedWorkstationProjectionSchemaException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Corrupt durable Workstation projection", exception);
        }
    }

    public CompoundTag decodeBlockEntityProjection(DurableWorkstationProjection projection) {
        return WorkstationProjectionNbtCodec.decode(projection.exactBlockEntityProjection());
    }

    public String encodeBlockEntityProjection(CompoundTag projection) {
        return WorkstationProjectionNbtCodec.encode(projection);
    }

    private static JsonObject toJson(DurableWorkstationProjection projection) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", projection.schemaVersion());
        JsonObject world = new JsonObject();
        world.addProperty("identity", projection.worldIdentity().identity());
        world.addProperty("schema_version", projection.worldIdentity().schemaVersion());
        world.addProperty("root_digest", projection.worldIdentity().rootDigest());
        root.add("world_identity", world);
        root.addProperty("instance_identity", projection.instanceId().value());
        JsonObject endpoint = new JsonObject();
        endpoint.addProperty("workstation_type_identity", projection.endpointKey().workstationTypeIdentity());
        endpoint.addProperty("dimension_identity", projection.endpointKey().dimensionIdentity());
        endpoint.addProperty("x", projection.endpointKey().x());
        endpoint.addProperty("y", projection.endpointKey().y());
        endpoint.addProperty("z", projection.endpointKey().z());
        root.add("endpoint_key", endpoint);
        root.addProperty("instance_generation", projection.instanceGeneration());
        root.addProperty("instance_allocation_configuration_identity",
                projection.instanceAllocationConfigurationIdentity());
        root.addProperty("projection_configuration_identity", projection.projectionConfigurationIdentity());
        root.addProperty("block_entity_type_identity", projection.blockEntityTypeIdentity());
        root.addProperty("instance_registry_revision", projection.instanceRegistryRevision());
        root.addProperty("projection_revision", projection.projectionRevision());
        root.addProperty("inventory_revision", projection.inventoryRevision());
        root.addProperty("endpoint_effect_revision", projection.endpointEffectRevision());
        root.addProperty("last_applied_journal_sequence", projection.lastAppliedJournalSequence());
        root.addProperty("slot_capacity_configuration_identity", projection.slotCapacityConfigurationIdentity());
        JsonArray slots = new JsonArray();
        projection.slots().forEach(slot -> {
            JsonObject json = new JsonObject();
            json.addProperty("slot_index", slot.slotIndex());
            json.addProperty("configured_capacity", slot.configuredCapacity());
            json.addProperty("effective_capacity", slot.effectiveCapacity());
            slot.exactStack().ifPresent(stack -> json.add("exact_stack", stack(stack)));
            slots.add(json);
        });
        root.add("slots", slots);
        root.add("exact_block_entity_projection",
                WorkstationProjectionNbtCodec.encodedElement(projection.exactBlockEntityProjection()));
        addOptional(root, "prepared_endpoint_effect_identity", projection.preparedEndpointEffectIdentity());
        addOptional(root, "last_endpoint_effect_identity", projection.lastEndpointEffectIdentity());
        addOptional(root, "last_endpoint_owner_result_identity", projection.lastEndpointOwnerResultIdentity());
        addOptional(root, "processing_operation_identity", projection.processingOperationIdentity());
        addOptional(root, "processing_owner_result_identity", projection.processingOwnerResultIdentity());
        projection.operatingStateReference().ifPresent(reference -> {
            JsonObject json = new JsonObject();
            json.addProperty("workstation_instance_identity", reference.workstationInstanceIdentity());
            json.addProperty("revision", reference.revision());
            json.addProperty("state", reference.state());
            json.addProperty("content_digest", reference.contentDigest());
            root.add("operating_state_reference", json);
        });
        root.addProperty("status", projection.status().name());
        addOptional(root, "retirement_reason", projection.retirementReason());
        addOptional(root, "prior_active_projection_digest", projection.priorActiveProjectionDigest());
        root.addProperty("state_digest", projection.stateDigest());
        return root;
    }

    private static JsonObject stack(WorkstationEndpointStackPayload stack) {
        JsonObject json = new JsonObject();
        json.addProperty("encoding_identity", stack.encodingIdentity());
        json.addProperty("item_identity", stack.itemIdentity());
        json.addProperty("count", stack.count());
        json.addProperty("content_digest", stack.contentDigest());
        json.addProperty("encoded_stack", stack.encodedStack());
        return json;
    }

    private static WorkstationEndpointStackPayload stack(JsonObject json) {
        return new WorkstationEndpointStackPayload(
                string(json, "encoding_identity"), string(json, "item_identity"), integer(json, "count"),
                string(json, "content_digest"), string(json, "encoded_stack"));
    }

    private static WorkstationOperatingStateReference operating(JsonObject json) {
        return new WorkstationOperatingStateReference(
                string(json, "workstation_instance_identity"), longValue(json, "revision"),
                string(json, "state"), string(json, "content_digest"));
    }

    private static void addOptional(JsonObject root, String key, Optional<String> value) {
        value.ifPresent(item -> root.addProperty(key, item));
    }

    private static JsonElement required(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(label + " must be an object");
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = required(object, key);
        if (!value.isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        return value.getAsJsonArray();
    }

    private static String string(JsonObject object, String key) {
        return required(object, key).getAsString();
    }

    private static int integer(JsonObject object, String key) {
        return required(object, key).getAsInt();
    }

    private static long longValue(JsonObject object, String key) {
        return required(object, key).getAsLong();
    }

    private static Optional<String> optionalString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? Optional.of(object.get(key).getAsString()) : Optional.empty();
    }
}
