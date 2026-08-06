package com.butchercraft.workstation.endpoint.persistence;

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
import java.util.Objects;

final class StrictAtomicJsonFile {
    private StrictAtomicJsonFile() {
    }

    static void publish(Path filePath, String canonicalJson) {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(canonicalJson, "canonicalJson");
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
            String published = Files.readString(filePath, StandardCharsets.UTF_8);
            if (!published.equals(canonicalJson)) {
                throw new IOException("Published file failed read-back verification: " + filePath);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed strict atomic publication of " + filePath, exception);
        } finally {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException ignored) {
                // A retained temporary artifact is visible evidence of an interrupted publication.
            }
        }
    }
}
