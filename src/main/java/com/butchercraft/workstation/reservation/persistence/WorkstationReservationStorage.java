package com.butchercraft.workstation.reservation.persistence;

import com.butchercraft.persistence.AtomicFilePublication;
import com.butchercraft.workstation.reservation.LegacyWorkstationReservation;
import com.butchercraft.workstation.reservation.WorkstationReservationConflictDomain;
import com.butchercraft.workstation.reservation.WorkstationReservationDirectory;
import com.butchercraft.workstation.reservation.WorkstationReservationEndpointDirection;
import com.butchercraft.workstation.reservation.WorkstationReservationEndpointPurpose;
import com.butchercraft.workstation.reservation.WorkstationReservationEndpointScope;
import com.butchercraft.workstation.reservation.WorkstationReservationId;
import com.butchercraft.workstation.reservation.WorkstationReservationMigrationResolver;
import com.butchercraft.workstation.reservation.WorkstationReservationRecord;
import com.butchercraft.workstation.reservation.WorkstationReservationRole;
import com.butchercraft.workstation.reservation.WorkstationReservationSchema;
import com.butchercraft.workstation.reservation.WorkstationReservationState;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public final class WorkstationReservationStorage {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final String SCHEMA_VERSION = "schema_version";
    private static final String RESERVATIONS = "reservations";
    private final Path filePath;

    public WorkstationReservationStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Path filePath() {
        return filePath;
    }

    public WorkstationReservationDirectory load(
            WorldIdentityRootIdentity worldIdentity,
            WorkstationReservationMigrationResolver migrationResolver
    ) {
        if (!Files.exists(filePath)) return WorkstationReservationDirectory.empty(worldIdentity);
        return deserialize(
                AtomicFilePublication.readUtf8(filePath, "workstation reservations"),
                worldIdentity,
                migrationResolver
        );
    }

    public void save(WorkstationReservationDirectory directory) {
        AtomicFilePublication.publishUtf8(
                filePath,
                serialize(Objects.requireNonNull(directory, "directory")),
                "workstation reservations"
        );
    }

    public String serialize(WorkstationReservationDirectory directory) {
        Objects.requireNonNull(directory, "directory");
        JsonObject root = new JsonObject();
        root.addProperty(SCHEMA_VERSION, WorkstationReservationSchema.CURRENT_VERSION);
        root.add("world_identity", serializeWorldIdentity(directory.worldIdentity()));
        root.addProperty("next_sequence", directory.nextSequence());
        root.addProperty("owner_revision", directory.ownerRevision());
        JsonArray reservations = new JsonArray();
        directory.records().forEach(record -> reservations.add(serializeRecord(record)));
        root.add(RESERVATIONS, reservations);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public WorkstationReservationDirectory deserialize(
            String json,
            WorldIdentityRootIdentity expectedWorldIdentity,
            WorkstationReservationMigrationResolver migrationResolver
    ) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(expectedWorldIdentity, "expectedWorldIdentity");
        Objects.requireNonNull(migrationResolver, "migrationResolver");
        try {
            JsonObject root = requireObject(JsonParser.parseString(json), "workstation reservation root");
            int schemaVersion = requireInt(root, SCHEMA_VERSION);
            return switch (schemaVersion) {
                case WorkstationReservationSchema.LEGACY_VERSION ->
                        migrateSchemaOne(root, expectedWorldIdentity, migrationResolver);
                case WorkstationReservationSchema.CURRENT_VERSION -> deserializeSchemaTwo(root, expectedWorldIdentity);
                default -> throw new IllegalArgumentException(
                        "Unsupported workstation reservation schema version: " + schemaVersion);
            };
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt workstation reservation persistence", exception);
        }
    }

    private WorkstationReservationDirectory deserializeSchemaTwo(
            JsonObject root,
            WorldIdentityRootIdentity expectedWorldIdentity
    ) {
        WorldIdentityRootIdentity worldIdentity = deserializeWorldIdentity(requireObject(
                root.get("world_identity"), "world_identity"));
        if (!worldIdentity.equals(expectedWorldIdentity)) {
            throw new IllegalArgumentException("Workstation reservations belong to a different World Identity");
        }
        long nextSequence = requireLong(root, "next_sequence");
        long ownerRevision = requireLong(root, "owner_revision");
        List<WorkstationReservationRecord> records = new ArrayList<>();
        for (JsonElement element : requireArray(root, RESERVATIONS)) {
            records.add(deserializeRecord(requireObject(element, "workstation reservation record")));
        }
        return WorkstationReservationDirectory.of(worldIdentity, nextSequence, ownerRevision, records);
    }

    private WorkstationReservationDirectory migrateSchemaOne(
            JsonObject root,
            WorldIdentityRootIdentity worldIdentity,
            WorkstationReservationMigrationResolver migrationResolver
    ) {
        List<LegacyWorkstationReservation> legacy = new ArrayList<>();
        for (JsonElement element : requireArray(root, RESERVATIONS)) {
            legacy.add(deserializeLegacyRecord(requireObject(element, "legacy workstation reservation record")));
        }
        legacy.sort(Comparator.comparingLong(LegacyWorkstationReservation::createdTick)
                .thenComparing(LegacyWorkstationReservation::workstationIdentity)
                .thenComparing(LegacyWorkstationReservation::employeeIdentity));
        List<WorkstationReservationRecord> migrated = new ArrayList<>();
        long sequence = 1L;
        long revision = 0L;
        for (LegacyWorkstationReservation record : legacy) {
            revision++;
            migrated.add(WorkstationReservationRecord.migrateLegacy(
                    worldIdentity,
                    sequence++,
                    revision,
                    record.workstationIdentity(),
                    record.workstationType(),
                    record.employeeIdentity(),
                    record.state(),
                    record.createdTick(),
                    record.expirationTick(),
                    record.invalidationReason(),
                    record.dimensionIdentity(),
                    record.workstationX(), record.workstationY(), record.workstationZ(),
                    record.operatingX(), record.operatingY(), record.operatingZ(),
                    record.anchorRadius(),
                    migrationResolver.resolve(record)
            ));
        }
        return WorkstationReservationDirectory.of(worldIdentity, sequence, revision, migrated);
    }

    private static JsonObject serializeRecord(WorkstationReservationRecord record) {
        JsonObject object = new JsonObject();
        object.addProperty(SCHEMA_VERSION, record.schemaVersion());
        object.addProperty("reservation_identity", record.reservationId().value());
        object.addProperty("sequence", record.sequence());
        object.add("world_identity", serializeWorldIdentity(record.worldIdentity()));
        object.addProperty("request_identity", record.requestIdentity());
        object.addProperty("workstation_identity", record.workstationIdentity());
        object.addProperty("exact_workstation_instance", record.exactWorkstationInstance());
        object.addProperty("workstation_generation", record.workstationGeneration());
        object.addProperty("workstation_type", record.workstationType());
        object.addProperty("employee_identity", record.employeeIdentity());
        object.addProperty("role", record.role().serializedName());
        record.assignmentReference().ifPresent(value -> object.addProperty("assignment_reference", value));
        record.transferReference().ifPresent(value -> object.addProperty("transfer_reference", value));
        object.add("endpoint_scope", serializeScope(record.endpointScope()));
        object.addProperty("lifecycle_evidence", record.lifecycleEvidence());
        object.addProperty("lifecycle_evidence_revision", record.lifecycleEvidenceRevision());
        object.addProperty("state", record.state().serializedName());
        object.addProperty("created_tick", record.createdTick());
        record.expirationTick().ifPresent(value -> object.addProperty("expiration_tick", value));
        record.invalidationReason().ifPresent(value -> object.addProperty("invalidation_reason", value));
        object.addProperty("dimension_identity", record.dimensionIdentity());
        object.addProperty("workstation_x", record.workstationX());
        object.addProperty("workstation_y", record.workstationY());
        object.addProperty("workstation_z", record.workstationZ());
        object.addProperty("operating_x", record.operatingX());
        object.addProperty("operating_y", record.operatingY());
        object.addProperty("operating_z", record.operatingZ());
        object.addProperty("anchor_radius", record.anchorRadius());
        object.addProperty("creation_revision", record.creationRevision());
        object.addProperty("last_update_revision", record.lastUpdateRevision());
        object.addProperty("configuration_identity", record.configurationIdentity());
        return object;
    }

    private static WorkstationReservationRecord deserializeRecord(JsonObject object) {
        return new WorkstationReservationRecord(
                requireInt(object, SCHEMA_VERSION),
                new WorkstationReservationId(requireString(object, "reservation_identity")),
                requireLong(object, "sequence"),
                deserializeWorldIdentity(requireObject(object.get("world_identity"), "world_identity")),
                requireString(object, "request_identity"),
                requireString(object, "workstation_identity"),
                requireBoolean(object, "exact_workstation_instance"),
                requireLong(object, "workstation_generation"),
                requireString(object, "workstation_type"),
                requireString(object, "employee_identity"),
                WorkstationReservationRole.fromSerializedName(requireString(object, "role")),
                optionalString(object, "assignment_reference"),
                optionalString(object, "transfer_reference"),
                deserializeScope(requireObject(object.get("endpoint_scope"), "endpoint_scope")),
                requireString(object, "lifecycle_evidence"),
                requireLong(object, "lifecycle_evidence_revision"),
                WorkstationReservationState.fromSerializedName(requireString(object, "state")),
                requireLong(object, "created_tick"),
                optionalLong(object, "expiration_tick"),
                optionalString(object, "invalidation_reason"),
                requireString(object, "dimension_identity"),
                requireInt(object, "workstation_x"), requireInt(object, "workstation_y"), requireInt(object, "workstation_z"),
                requireInt(object, "operating_x"), requireInt(object, "operating_y"), requireInt(object, "operating_z"),
                requireInt(object, "anchor_radius"),
                requireLong(object, "creation_revision"),
                requireLong(object, "last_update_revision"),
                requireString(object, "configuration_identity")
        );
    }

    private static LegacyWorkstationReservation deserializeLegacyRecord(JsonObject object) {
        int recordSchema = requireInt(object, SCHEMA_VERSION);
        if (recordSchema != WorkstationReservationSchema.LEGACY_VERSION) {
            throw new IllegalArgumentException("Legacy reservation record has unsupported schema: " + recordSchema);
        }
        return new LegacyWorkstationReservation(
                requireString(object, "workstation_identity"),
                requireString(object, "workstation_type"),
                requireString(object, "employee_identity"),
                WorkstationReservationState.fromSerializedName(requireString(object, "state")),
                requireLong(object, "created_tick"),
                optionalLong(object, "expiration_tick"),
                optionalString(object, "invalidation_reason"),
                requireString(object, "dimension_identity"),
                requireInt(object, "workstation_x"), requireInt(object, "workstation_y"), requireInt(object, "workstation_z"),
                requireInt(object, "operating_x"), requireInt(object, "operating_y"), requireInt(object, "operating_z"),
                requireInt(object, "anchor_radius")
        );
    }

    private static JsonObject serializeScope(WorkstationReservationEndpointScope scope) {
        JsonObject object = new JsonObject();
        object.addProperty("purpose", scope.purpose().serializedName());
        object.addProperty("direction", scope.direction().serializedName());
        scope.endpointIdentity().ifPresent(value -> object.addProperty("endpoint_identity", value));
        object.addProperty("conflict_domain", scope.conflictDomain().serializedName());
        return object;
    }

    private static WorkstationReservationEndpointScope deserializeScope(JsonObject object) {
        return new WorkstationReservationEndpointScope(
                WorkstationReservationEndpointPurpose.fromSerializedName(requireString(object, "purpose")),
                WorkstationReservationEndpointDirection.fromSerializedName(requireString(object, "direction")),
                optionalString(object, "endpoint_identity"),
                WorkstationReservationConflictDomain.fromSerializedName(requireString(object, "conflict_domain"))
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
                requireString(object, "identity"),
                requireInt(object, "schema_version"),
                requireString(object, "root_digest")
        );
    }

    private static JsonObject requireObject(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException(label + " must be an object");
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonArray()) throw new IllegalArgumentException(field + " must be an array");
        return element.getAsJsonArray();
    }

    private static String requireString(JsonObject object, String field) {
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

    private static int requireInt(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) throw new IllegalArgumentException(field + " is required");
        return element.getAsInt();
    }

    private static long requireLong(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) throw new IllegalArgumentException(field + " is required");
        return element.getAsLong();
    }

    private static boolean requireBoolean(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) throw new IllegalArgumentException(field + " is required");
        return element.getAsBoolean();
    }

    private static OptionalLong optionalLong(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) return OptionalLong.empty();
        if (!element.isJsonPrimitive()) throw new IllegalArgumentException(field + " must be a number");
        return OptionalLong.of(element.getAsLong());
    }
}
