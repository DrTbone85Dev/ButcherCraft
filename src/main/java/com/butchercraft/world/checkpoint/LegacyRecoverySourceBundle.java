package com.butchercraft.world.checkpoint;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** Canonical exact-byte digest used by legacy recovery source evidence and restoration. */
public final class LegacyRecoverySourceBundle {
    private LegacyRecoverySourceBundle() {
    }

    public static String digest(CheckpointOwnerId ownerId, List<SourceFile> files) {
        Objects.requireNonNull(ownerId, "ownerId");
        List<SourceFile> ordered = Objects.requireNonNull(files, "files").stream().sorted().toList();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                write(output, "butchercraft:legacy_source_bundle/v1");
                write(output, ownerId.value());
                output.writeInt(ordered.size());
                for (SourceFile file : ordered) {
                    write(output, file.logicalName());
                    output.writeLong(file.bytes().length);
                    output.write(file.bytes());
                }
            }
            return CheckpointSnapshotDigest.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to hash exact legacy owner source bundle", exception);
        }
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    public record SourceFile(String logicalName, byte[] bytes) implements Comparable<SourceFile> {
        public SourceFile {
            logicalName = CheckpointValidation.text(logicalName, "legacySourceLogicalName");
            bytes = Objects.requireNonNull(bytes, "bytes").clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public int compareTo(SourceFile other) {
            return logicalName.compareTo(Objects.requireNonNull(other, "other").logicalName);
        }
    }
}
