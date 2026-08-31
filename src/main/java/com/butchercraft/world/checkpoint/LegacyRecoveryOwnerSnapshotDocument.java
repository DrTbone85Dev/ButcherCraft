package com.butchercraft.world.checkpoint;

import com.butchercraft.persistence.StrictJsonPersistence;
import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public record LegacyRecoveryOwnerSnapshotDocument(
        int schemaVersion,
        CheckpointOwnerId ownerId,
        LegacySplitRecoveryIdentity recoveryIdentity,
        String analysisDigest,
        String sourceSnapshotIdentity,
        String sourceSnapshotContentDigest,
        long sourceOwnerRevision,
        long representedSimulationTick,
        String ownerStateIdentity,
        List<Field> fields
) {
    public static final int CURRENT_SCHEMA = 1;
    private static final Gson GSON = StrictJsonPersistence.gson();

    public LegacyRecoveryOwnerSnapshotDocument {
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported legacy recovery owner snapshot schema");
        }
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        recoveryIdentity = Objects.requireNonNull(recoveryIdentity, "recoveryIdentity");
        analysisDigest = CheckpointValidation.digest(analysisDigest, "analysisDigest");
        sourceSnapshotIdentity = CheckpointValidation.id(sourceSnapshotIdentity, "sourceSnapshotIdentity");
        sourceSnapshotContentDigest = CheckpointValidation.digest(
                sourceSnapshotContentDigest,
                "sourceSnapshotContentDigest"
        );
        sourceOwnerRevision = CheckpointValidation.nonNegative(sourceOwnerRevision, "sourceOwnerRevision");
        representedSimulationTick = CheckpointValidation.nonNegative(
                representedSimulationTick,
                "representedSimulationTick"
        );
        ownerStateIdentity = CheckpointValidation.id(ownerStateIdentity, "ownerStateIdentity");
        fields = Objects.requireNonNull(fields, "fields").stream()
                .map(value -> Objects.requireNonNull(value, "field"))
                .sorted()
                .toList();
        for (int index = 1; index < fields.size(); index++) {
            if (fields.get(index - 1).key().equals(fields.get(index).key())) {
                throw new IllegalArgumentException("Duplicate owner snapshot field: " + fields.get(index).key());
            }
        }
    }

    public static LegacyRecoveryOwnerSnapshotDocument create(
            CheckpointOwnerId ownerId,
            LegacySplitRecoveryOwnerPreparationRequest request,
            RecoverySourceSnapshot source,
            String ownerStateIdentity,
            List<Field> fields
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(source, "source");
        if (!source.ownerId().equals(ownerId)) {
            throw new IllegalArgumentException("Owner snapshot source belongs to another owner");
        }
        return new LegacyRecoveryOwnerSnapshotDocument(
                CURRENT_SCHEMA,
                ownerId,
                request.plan().recoveryIdentity(),
                request.plan().analysisDigest(),
                source.snapshotIdentity(),
                source.contentDigest(),
                source.ownerRevisionOrSequence(),
                request.plan().authoritativeClockTick(),
                ownerStateIdentity,
                fields
        );
    }

    public byte[] serialize() {
        return (GSON.toJson(this) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public static LegacyRecoveryOwnerSnapshotDocument deserialize(byte[] bytes) {
        try {
            return Objects.requireNonNull(
                    GSON.fromJson(new String(Objects.requireNonNull(bytes, "bytes"), StandardCharsets.UTF_8),
                            LegacyRecoveryOwnerSnapshotDocument.class),
                    "legacy recovery owner snapshot"
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Corrupt legacy recovery owner snapshot", exception);
        }
    }

    public record Field(String key, String value) implements Comparable<Field> {
        public Field {
            key = CheckpointValidation.text(key, "ownerSnapshotFieldKey");
            value = CheckpointValidation.text(value, "ownerSnapshotFieldValue");
        }

        public static Field of(String key, Object value) {
            return new Field(key, Objects.requireNonNull(value, "value").toString());
        }

        @Override
        public int compareTo(Field other) {
            return key.compareTo(Objects.requireNonNull(other, "other").key);
        }
    }
}
