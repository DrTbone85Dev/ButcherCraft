package com.butchercraft.world.checkpoint;

import com.butchercraft.persistence.StrictJsonPersistence;
import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

public final class CheckpointOwnerFileBundleCodec {
    public static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = StrictJsonPersistence.gson();

    private CheckpointOwnerFileBundleCodec() {
    }

    public static byte[] encode(CheckpointOwnerFileSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        BundleDocument document = new BundleDocument(
                SCHEMA_VERSION,
                snapshot.ownerId().value(),
                snapshot.ownerSchemaVersion(),
                snapshot.ownerSequence(),
                snapshot.canonicalEmpty(),
                snapshot.files().stream().map(file -> new FileDocument(
                        file.logicalName(),
                        file.bytes().length,
                        CheckpointSnapshotDigest.sha256(file.bytes()),
                        Base64.getEncoder().encodeToString(file.bytes())
                )).toList()
        );
        return (GSON.toJson(document) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public static CheckpointOwnerFileSnapshot decode(byte[] bytes) {
        try {
            BundleDocument document = Objects.requireNonNull(
                    GSON.fromJson(new String(Objects.requireNonNull(bytes, "bytes"), StandardCharsets.UTF_8),
                            BundleDocument.class),
                    "owner checkpoint file bundle"
            );
            if (document.schemaVersion() != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported owner checkpoint file bundle schema");
            }
            List<CheckpointOwnerFileSnapshot.FilePayload> files = Objects.requireNonNull(document.files(), "files")
                    .stream()
                    .map(file -> {
                        byte[] decoded = Base64.getDecoder().decode(file.base64Content());
                        if (decoded.length != file.length()) {
                            throw new IllegalArgumentException("Owner checkpoint file length mismatch: " + file.logicalName());
                        }
                        if (!CheckpointSnapshotDigest.sha256(decoded).equals(file.digest())) {
                            throw new IllegalArgumentException("Owner checkpoint file digest mismatch: " + file.logicalName());
                        }
                        return new CheckpointOwnerFileSnapshot.FilePayload(file.logicalName(), decoded);
                    })
                    .toList();
            return new CheckpointOwnerFileSnapshot(
                    CheckpointOwnerId.of(document.ownerId()),
                    document.ownerSchemaVersion(),
                    document.ownerSequence(),
                    document.canonicalEmpty(),
                    files
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Corrupt owner checkpoint file bundle", exception);
        }
    }

    private record BundleDocument(
            int schemaVersion,
            String ownerId,
            int ownerSchemaVersion,
            long ownerSequence,
            boolean canonicalEmpty,
            List<FileDocument> files
    ) {
    }

    private record FileDocument(String logicalName, int length, String digest, String base64Content) {
    }
}
