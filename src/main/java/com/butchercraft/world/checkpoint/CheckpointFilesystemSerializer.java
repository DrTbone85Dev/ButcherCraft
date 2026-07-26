package com.butchercraft.world.checkpoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

final class CheckpointFilesystemSerializer {
    private static final String NULL = "null";

    private CheckpointFilesystemSerializer() {
    }

    static byte[] generationManifestBytes(CheckpointGenerationManifest manifest) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        field(builder, "schema_version", manifest.schemaVersion()).append(',');
        quotedField(builder, "generation_id").append(':');
        generationId(builder, manifest.generationId()).append(',');
        quotedField(builder, "predecessor_generation_id").append(':');
        optionalGenerationId(builder, manifest.predecessorGenerationId()).append(',');
        nullableStringField(builder, "predecessor_manifest_digest", manifest.predecessorManifestDigest()).append(',');
        field(builder, "authoritative_simulation_tick", manifest.authoritativeSimulationTick()).append(',');
        quotedField(builder, "world_identity_root").append(':');
        worldIdentity(builder, manifest.worldIdentityRoot()).append(',');
        quotedField(builder, "platform_determinism_manifest").append(':');
        platformManifest(builder, manifest.platformDeterminismManifest()).append(',');
        quotedField(builder, "owner_snapshots").append(':').append('[');
        for (int index = 0; index < manifest.ownerSnapshots().size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            ownerSnapshot(builder, manifest.ownerSnapshots().get(index));
        }
        builder.append("],");
        stringField(builder, "manifest_digest", manifest.manifestDigest());
        builder.append('}');
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] ownerManifestBytes(CheckpointOwnerSnapshotPayload snapshot) {
        OwnerSnapshotDescriptor descriptor = snapshot.descriptor();
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        field(builder, "schema_version", CheckpointSchema.CURRENT_VERSION).append(',');
        quotedField(builder, "owner_snapshot").append(':');
        ownerSnapshot(builder, descriptor).append(',');
        stringField(builder, "payload_path", CheckpointFilesystemLayout.OWNER_PAYLOAD_FILE).append(',');
        field(builder, "payload_length", snapshot.payloadBytes().length).append(',');
        stringField(builder, "payload_digest", snapshot.expectedContentDigest());
        builder.append('}');
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] headRecordBytes(CheckpointHeadRecord head) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        field(builder, "schema_version", head.schemaVersion()).append(',');
        field(builder, "head_sequence", head.headSequence()).append(',');
        quotedField(builder, "selected_generation_id").append(':');
        generationId(builder, head.selectedGenerationId()).append(',');
        stringField(builder, "selected_generation_manifest_digest", head.selectedGenerationManifestDigest()).append(',');
        quotedField(builder, "predecessor_generation_id").append(':');
        optionalGenerationId(builder, head.predecessorGenerationId()).append(',');
        nullableStringField(builder, "predecessor_manifest_digest", head.predecessorManifestDigest()).append(',');
        stringField(builder, "head_record_digest", head.headRecordDigest());
        builder.append('}');
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    static CheckpointGenerationManifest parseGenerationManifest(String json) {
        JsonObject root = object(JsonParser.parseString(json), "generation manifest");
        return new CheckpointGenerationManifest(
                integer(root, "schema_version"),
                parseGenerationId(object(field(root, "generation_id"), "generation_id")),
                optionalGenerationId(root.get("predecessor_generation_id")),
                optionalString(root, "predecessor_manifest_digest"),
                longValue(root, "authoritative_simulation_tick"),
                parseOwnerSnapshots(array(field(root, "owner_snapshots"), "owner_snapshots")),
                parsePlatformManifest(object(field(root, "platform_determinism_manifest"),
                        "platform_determinism_manifest")),
                parseWorldIdentity(object(field(root, "world_identity_root"), "world_identity_root")),
                string(root, "manifest_digest")
        );
    }

    static CheckpointParsedOwnerManifest parseOwnerManifest(String json) {
        JsonObject root = object(JsonParser.parseString(json), "owner manifest");
        int schema = integer(root, "schema_version");
        if (schema != CheckpointSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported owner manifest schema version: " + schema);
        }
        OwnerSnapshotDescriptor descriptor = parseOwnerSnapshot(object(field(root, "owner_snapshot"), "owner_snapshot"));
        String payloadPath = string(root, "payload_path");
        if (!CheckpointFilesystemLayout.OWNER_PAYLOAD_FILE.equals(payloadPath)) {
            throw new IllegalArgumentException("Unsupported owner payload path: " + payloadPath);
        }
        long length = longValue(root, "payload_length");
        if (length < 0L) {
            throw new IllegalArgumentException("Owner payload length must not be negative");
        }
        String digest = string(root, "payload_digest");
        return new CheckpointParsedOwnerManifest(descriptor, length, digest);
    }

    static CheckpointHeadRecord parseHeadRecord(String json) {
        JsonObject root = object(JsonParser.parseString(json), "checkpoint head");
        return new CheckpointHeadRecord(
                integer(root, "schema_version"),
                longValue(root, "head_sequence"),
                parseGenerationId(object(field(root, "selected_generation_id"), "selected_generation_id")),
                string(root, "selected_generation_manifest_digest"),
                optionalGenerationId(root.get("predecessor_generation_id")),
                optionalString(root, "predecessor_manifest_digest"),
                string(root, "head_record_digest")
        );
    }

    private static List<OwnerSnapshotDescriptor> parseOwnerSnapshots(JsonArray array) {
        List<OwnerSnapshotDescriptor> snapshots = new ArrayList<>();
        for (JsonElement element : array) {
            snapshots.add(parseOwnerSnapshot(object(element, "owner snapshot")));
        }
        return snapshots;
    }

    private static OwnerSnapshotDescriptor parseOwnerSnapshot(JsonObject object) {
        return new OwnerSnapshotDescriptor(
                CheckpointOwnerId.of(string(object, "owner_id")),
                integer(object, "snapshot_schema_version"),
                string(object, "snapshot_identity"),
                string(object, "content_digest"),
                CheckpointSnapshotParticipation.valueOf(string(object, "participation")),
                string(object, "configuration_identity"),
                parseWorldIdentity(object(field(object, "world_identity_root"), "world_identity_root")),
                parseGenerationId(object(field(object, "generation_id"), "generation_id")),
                longValue(object, "represented_simulation_tick"),
                longValue(object, "owner_sequence")
        );
    }

    private static CheckpointGenerationId parseGenerationId(JsonObject object) {
        return new CheckpointGenerationId(
                integer(object, "schema_version"),
                longValue(object, "committed_sequence"),
                longValue(object, "authoritative_simulation_tick")
        );
    }

    private static Optional<CheckpointGenerationId> optionalGenerationId(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        return Optional.of(parseGenerationId(object(element, "optional generation id")));
    }

    private static WorldIdentityRootReference parseWorldIdentity(JsonObject object) {
        return new WorldIdentityRootReference(
                string(object, "identity"),
                integer(object, "schema_version"),
                string(object, "root_digest")
        );
    }

    private static PlatformDeterminismManifestReference parsePlatformManifest(JsonObject object) {
        return new PlatformDeterminismManifestReference(
                string(object, "identity"),
                integer(object, "schema_version"),
                string(object, "manifest_digest")
        );
    }

    private static StringBuilder ownerSnapshot(StringBuilder builder, OwnerSnapshotDescriptor snapshot) {
        builder.append('{');
        stringField(builder, "owner_id", snapshot.ownerId().value()).append(',');
        field(builder, "snapshot_schema_version", snapshot.snapshotSchemaVersion()).append(',');
        stringField(builder, "snapshot_identity", snapshot.snapshotIdentity()).append(',');
        stringField(builder, "content_digest", snapshot.contentDigest()).append(',');
        stringField(builder, "participation", snapshot.participation().name()).append(',');
        stringField(builder, "configuration_identity", snapshot.configurationIdentity()).append(',');
        quotedField(builder, "world_identity_root").append(':');
        worldIdentity(builder, snapshot.worldIdentityRoot()).append(',');
        quotedField(builder, "generation_id").append(':');
        generationId(builder, snapshot.generationId()).append(',');
        field(builder, "represented_simulation_tick", snapshot.representedSimulationTick()).append(',');
        field(builder, "owner_sequence", snapshot.ownerSequence());
        builder.append('}');
        return builder;
    }

    private static StringBuilder generationId(StringBuilder builder, CheckpointGenerationId generationId) {
        builder.append('{');
        field(builder, "schema_version", generationId.schemaVersion()).append(',');
        field(builder, "committed_sequence", generationId.committedSequence()).append(',');
        field(builder, "authoritative_simulation_tick", generationId.authoritativeSimulationTick()).append(',');
        stringField(builder, "canonical_value", generationId.canonicalValue());
        builder.append('}');
        return builder;
    }

    private static StringBuilder optionalGenerationId(
            StringBuilder builder,
            Optional<CheckpointGenerationId> generationId
    ) {
        Objects.requireNonNull(generationId, "generationId");
        if (generationId.isEmpty()) {
            builder.append(NULL);
        } else {
            generationId(builder, generationId.orElseThrow());
        }
        return builder;
    }

    private static StringBuilder worldIdentity(StringBuilder builder, WorldIdentityRootReference reference) {
        builder.append('{');
        stringField(builder, "identity", reference.identity()).append(',');
        field(builder, "schema_version", reference.schemaVersion()).append(',');
        stringField(builder, "root_digest", reference.rootDigest());
        builder.append('}');
        return builder;
    }

    private static StringBuilder platformManifest(
            StringBuilder builder,
            PlatformDeterminismManifestReference reference
    ) {
        builder.append('{');
        stringField(builder, "identity", reference.identity()).append(',');
        field(builder, "schema_version", reference.schemaVersion()).append(',');
        stringField(builder, "manifest_digest", reference.manifestDigest());
        builder.append('}');
        return builder;
    }

    private static StringBuilder field(StringBuilder builder, String name, long value) {
        return quotedField(builder, name).append(':').append(value);
    }

    private static StringBuilder field(StringBuilder builder, String name, int value) {
        return quotedField(builder, name).append(':').append(value);
    }

    private static StringBuilder stringField(StringBuilder builder, String name, String value) {
        return quotedField(builder, name).append(':').append(quote(value));
    }

    private static StringBuilder nullableStringField(
            StringBuilder builder,
            String name,
            Optional<String> value
    ) {
        quotedField(builder, name).append(':');
        if (value.isPresent()) {
            builder.append(quote(value.orElseThrow()));
        } else {
            builder.append(NULL);
        }
        return builder;
    }

    private static StringBuilder quotedField(StringBuilder builder, String name) {
        return builder.append(quote(name));
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder();
        builder.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        builder.append('"');
        return builder.toString();
    }

    private static JsonObject object(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be a JSON object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonElement element, String label) {
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException(label + " must be a JSON array");
        }
        return element.getAsJsonArray();
    }

    private static JsonElement field(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null) {
            throw new IllegalArgumentException("Missing required checkpoint field: " + name);
        }
        return element;
    }

    private static String string(JsonObject object, String name) {
        JsonElement element = field(object, name);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Checkpoint field must be a string: " + name);
        }
        return element.getAsString();
    }

    private static Optional<String> optionalString(JsonObject object, String name) {
        JsonElement element = field(object, name);
        if (element.isJsonNull()) {
            return Optional.empty();
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Checkpoint field must be null or string: " + name);
        }
        return Optional.of(element.getAsString());
    }

    private static int integer(JsonObject object, String name) {
        long value = longValue(object, name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Checkpoint field exceeds integer range: " + name);
        }
        return (int) value;
    }

    private static long longValue(JsonObject object, String name) {
        JsonElement element = field(object, name);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Checkpoint field must be a number: " + name);
        }
        return element.getAsLong();
    }
}
