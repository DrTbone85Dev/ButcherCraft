package com.butchercraft.workstation.checkpoint;

import com.butchercraft.persistence.StrictJsonPersistence;
import com.butchercraft.workstation.endpoint.WorkstationInstanceLifecycle;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRegistry;
import com.butchercraft.workstation.endpoint.persistence.WorkstationInstanceStorage;
import com.butchercraft.workstation.projection.DurableWorkstationProjection;
import com.butchercraft.workstation.projection.WorkstationProjectionCodec;
import com.butchercraft.workstation.projection.WorkstationProjectionStatus;
import com.butchercraft.world.checkpoint.CheckpointOwnerFileBundleCodec;
import com.butchercraft.world.checkpoint.CheckpointOwnerFileSnapshot;
import com.butchercraft.world.checkpoint.CheckpointRecoveredGeneration;
import com.butchercraft.world.checkpoint.CheckpointSnapshotDigest;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Read-only R4 candidate gate. It performs no selection and publishes no recovered authority. */
public final class WorkstationCheckpointRestorabilityVerifier {
    private static final Gson GSON = StrictJsonPersistence.gson();
    private static final WorkstationProjectionCodec PROJECTION_CODEC = new WorkstationProjectionCodec();

    private WorkstationCheckpointRestorabilityVerifier() {
    }

    public static boolean isGenerationWorkstationRestorable(CheckpointRecoveredGeneration generation) {
        return verify(generation).restorable();
    }

    public static WorkstationCheckpointRestorabilityReport verify(CheckpointRecoveredGeneration generation) {
        Objects.requireNonNull(generation, "generation");
        try {
            var payload = generation.ownerSnapshots().stream()
                    .filter(snapshot -> snapshot.descriptor().ownerId()
                            .equals(LegacySplitRecoveryParticipants.WORKSTATION))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Workstation owner snapshot is absent"));
            CheckpointOwnerFileSnapshot bundle = CheckpointOwnerFileBundleCodec.decode(payload.payloadBytes());
            return verify(bundle, payload.payloadBytes().length);
        } catch (RuntimeException exception) {
            return corrupt(exception);
        }
    }

    public static WorkstationCheckpointRestorabilityReport verify(CheckpointOwnerFileSnapshot bundle) {
        Objects.requireNonNull(bundle, "bundle");
        try {
            return verify(bundle, CheckpointOwnerFileBundleCodec.encode(bundle).length);
        } catch (RuntimeException exception) {
            return corrupt(exception);
        }
    }

    private static WorkstationCheckpointRestorabilityReport verify(
            CheckpointOwnerFileSnapshot bundle,
            long bundleBytes
    ) {
        try {
            byte[] instancesBytes = file(bundle, "workstation_instances.json");
            WorkstationInstanceRegistry instances = new WorkstationInstanceStorage(Path.of("workstation_instances.json"))
                    .deserialize(new String(instancesBytes, StandardCharsets.UTF_8));
            int activeRequired = (int) instances.records().stream()
                    .filter(instance -> instance.lifecycle() != WorkstationInstanceLifecycle.RETIRED)
                    .count();
            byte[] projections = optionalFile(bundle, "workstation_projections.json");
            if (projections == null) {
                return historicalIncomplete(activeRequired, bundleBytes,
                        "Workstation projection participant is absent");
            }
            JsonObject root = JsonParser.parseString(new String(projections, StandardCharsets.UTF_8)).getAsJsonObject();
            int schema = requiredInt(root, "schema_version");
            if (schema == WorkstationCheckpointProjectionService.LEGACY_SCHEMA_VERSION) {
                return verifyLegacy(root, activeRequired, bundleBytes);
            }
            if (schema != WorkstationCheckpointProjectionService.SCHEMA_VERSION) {
                return new WorkstationCheckpointRestorabilityReport(
                        WorkstationCheckpointCompletenessStatus.UNSUPPORTED,
                        activeRequired, 0, 0, 0, bundleBytes,
                        List.of("Unsupported Workstation checkpoint projection schema: " + schema));
            }
            return verifyCurrent(root, instances, bundleBytes);
        } catch (RuntimeException exception) {
            return corrupt(exception);
        }
    }

    private static WorkstationCheckpointRestorabilityReport corrupt(RuntimeException exception) {
        return new WorkstationCheckpointRestorabilityReport(
                WorkstationCheckpointCompletenessStatus.NON_RESTORABLE_WORKSTATION_PROJECTION_INCOMPLETE,
                0, 0, 0, 0, 0L,
                List.of(exception.getMessage() == null
                        ? "Workstation checkpoint participant is corrupt"
                        : exception.getMessage()));
    }

    private static WorkstationCheckpointRestorabilityReport verifyCurrent(
            JsonObject root,
            WorkstationInstanceRegistry instances,
            long participantBytes
    ) {
        if (requiredLong(root, "workstation_instance_registry_revision") != instances.ownerRevision()) {
            return historicalIncomplete(0, participantBytes,
                    "Workstation participant registry revision mismatch");
        }
        int required = requiredInt(root, "required_projection_count");
        int available = requiredInt(root, "available_projection_count");
        int loaded = requiredInt(root, "loaded_projection_count");
        int unloaded = requiredInt(root, "unloaded_projection_count");
        if (!WorkstationCheckpointCompletenessStatus.COMPLETE_RESTORABLE.serializedName().equals(
                requiredString(root, "workstation_restorable_status"))) {
            return historicalIncomplete(required, participantBytes,
                    "Workstation participant does not declare complete-restorable state");
        }
        JsonArray entries = requiredArray(root, "required_projections");
        JsonArray blockers = requiredArray(root, "blockers");
        if (required != available || available != entries.size()
                || available != loaded + unloaded || !blockers.isEmpty()) {
            return historicalIncomplete(required, participantBytes,
                    "Workstation participant completeness counts disagree");
        }
        String collectionDigest = CheckpointSnapshotDigest.sha256(
                GSON.toJson(entries).getBytes(StandardCharsets.UTF_8));
        if (!collectionDigest.equals(requiredString(root, "projection_collection_digest"))) {
            return historicalIncomplete(required, participantBytes,
                    "Workstation projection collection digest mismatch");
        }

        Set<String> represented = new HashSet<>();
        String previous = "";
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            String identity = requiredString(entry, "instance_id");
            if (!represented.add(identity) || identity.compareTo(previous) <= 0 && !previous.isEmpty()) {
                return historicalIncomplete(required, participantBytes,
                        "Workstation projection entries are duplicate or non-canonical");
            }
            previous = identity;
            byte[] frozen = Base64.getDecoder().decode(requiredString(entry, "payload_base64"));
            if (frozen.length != requiredInt(entry, "payload_length")
                    || !CheckpointSnapshotDigest.sha256(frozen).equals(requiredString(entry, "payload_digest"))) {
                return historicalIncomplete(required, participantBytes,
                        "Workstation projection payload digest mismatch: " + identity);
            }
            DurableWorkstationProjection projection = PROJECTION_CODEC.decode(frozen);
            if (!projection.instanceId().value().equals(identity)
                    || projection.schemaVersion() != requiredInt(entry, "projection_schema")
                    || projection.projectionRevision() != requiredLong(entry, "projection_revision")
                    || !projection.stateDigest().equals(requiredString(entry, "projection_state_digest"))) {
                return historicalIncomplete(required, participantBytes,
                        "Workstation projection metadata binding mismatch: " + identity);
            }
            var instance = instances.find(projection.instanceId()).orElse(null);
            if (instance == null
                    || instance.generation() != projection.instanceGeneration()
                    || !instance.endpointKey().equals(projection.endpointKey())
                    || !instance.worldIdentity().equals(projection.worldIdentity())
                    || !instance.lifecycle().name().equals(requiredString(entry, "lifecycle"))) {
                return historicalIncomplete(required, participantBytes,
                        "Workstation projection registry binding mismatch: " + identity);
            }
            boolean retired = instance.lifecycle() == WorkstationInstanceLifecycle.RETIRED;
            if (retired != (projection.status() == WorkstationProjectionStatus.TOMBSTONED)) {
                return historicalIncomplete(required, participantBytes,
                        "Workstation projection lifecycle/tombstone mismatch: " + identity);
            }
        }
        for (var instance : instances.records()) {
            if (instance.lifecycle() != WorkstationInstanceLifecycle.RETIRED
                    && !represented.contains(instance.instanceId().value())) {
                return historicalIncomplete(required, participantBytes,
                        "Required active Workstation projection is absent: " + instance.instanceId().value());
            }
        }
        return new WorkstationCheckpointRestorabilityReport(
                WorkstationCheckpointCompletenessStatus.COMPLETE_RESTORABLE,
                required, available, loaded, unloaded, participantBytes, List.of());
    }

    private static WorkstationCheckpointRestorabilityReport verifyLegacy(
            JsonObject root,
            int activeRequired,
            long participantBytes
    ) {
        int loaded = requiredArray(root, "loaded_projections").size();
        JsonArray unavailable = requiredArray(root, "unavailable_projections");
        if (!unavailable.isEmpty() || loaded != activeRequired) {
            List<String> blockers = new ArrayList<>();
            for (JsonElement element : unavailable) {
                JsonObject record = element.getAsJsonObject();
                blockers.add(requiredString(record, "instance_id") + ": "
                        + requiredString(record, "reason"));
            }
            if (blockers.isEmpty()) blockers.add("Legacy Workstation projection set is incomplete");
            return new WorkstationCheckpointRestorabilityReport(
                    WorkstationCheckpointCompletenessStatus.NON_RESTORABLE_WORKSTATION_PROJECTION_INCOMPLETE,
                    activeRequired, loaded, loaded, 0, participantBytes, blockers);
        }
        return new WorkstationCheckpointRestorabilityReport(
                WorkstationCheckpointCompletenessStatus.COMPLETE_RESTORABLE,
                activeRequired, loaded, loaded, 0, participantBytes, List.of());
    }

    private static WorkstationCheckpointRestorabilityReport historicalIncomplete(
            int required,
            long participantBytes,
            String detail
    ) {
        return new WorkstationCheckpointRestorabilityReport(
                WorkstationCheckpointCompletenessStatus.NON_RESTORABLE_WORKSTATION_PROJECTION_INCOMPLETE,
                required, 0, 0, 0, participantBytes, List.of(detail));
    }

    private static byte[] file(CheckpointOwnerFileSnapshot snapshot, String name) {
        byte[] bytes = optionalFile(snapshot, name);
        if (bytes == null) throw new IllegalArgumentException("Workstation owner file is absent: " + name);
        return bytes;
    }

    private static byte[] optionalFile(CheckpointOwnerFileSnapshot snapshot, String name) {
        return snapshot.files().stream().filter(file -> file.logicalName().equals(name))
                .findFirst().map(CheckpointOwnerFileSnapshot.FilePayload::bytes).orElse(null);
    }

    private static int requiredInt(JsonObject object, String name) {
        if (!object.has(name)) throw new IllegalArgumentException("Missing Workstation field: " + name);
        return object.get(name).getAsInt();
    }

    private static long requiredLong(JsonObject object, String name) {
        if (!object.has(name)) throw new IllegalArgumentException("Missing Workstation field: " + name);
        return object.get(name).getAsLong();
    }

    private static String requiredString(JsonObject object, String name) {
        if (!object.has(name)) throw new IllegalArgumentException("Missing Workstation field: " + name);
        return object.get(name).getAsString();
    }

    private static JsonArray requiredArray(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonArray()) {
            throw new IllegalArgumentException("Missing Workstation array: " + name);
        }
        return object.getAsJsonArray(name);
    }
}
