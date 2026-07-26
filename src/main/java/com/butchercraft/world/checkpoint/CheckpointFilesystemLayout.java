package com.butchercraft.world.checkpoint;

import java.nio.file.Path;
import java.util.Objects;

public final class CheckpointFilesystemLayout {
    public static final String GENERATIONS_DIRECTORY = "generations";
    public static final String STAGING_DIRECTORY = "staging";
    public static final String QUARANTINE_DIRECTORY = "quarantine";
    public static final String OWNERS_DIRECTORY = "owners";
    public static final String GENERATION_MANIFEST_FILE = "generation_manifest.json";
    public static final String OWNER_MANIFEST_FILE = "owner_manifest.json";
    public static final String OWNER_PAYLOAD_FILE = "snapshot_payload.bin";
    public static final String HEAD_A_FILE = "checkpoint_head_a.json";
    public static final String HEAD_B_FILE = "checkpoint_head_b.json";

    private final Path storeRoot;

    public CheckpointFilesystemLayout(Path storeRoot) {
        this.storeRoot = Objects.requireNonNull(storeRoot, "storeRoot").toAbsolutePath().normalize();
    }

    public Path storeRoot() {
        return storeRoot;
    }

    public Path generationsDirectory() {
        return storeRoot.resolve(GENERATIONS_DIRECTORY);
    }

    public Path stagingDirectory() {
        return storeRoot.resolve(STAGING_DIRECTORY);
    }

    public Path quarantineDirectory() {
        return storeRoot.resolve(QUARANTINE_DIRECTORY);
    }

    public Path finalGenerationDirectory(CheckpointGenerationId generationId) {
        return generationsDirectory().resolve(generationDirectoryName(generationId));
    }

    public Path stagingGenerationDirectory(CheckpointGenerationId generationId) {
        return stagingDirectory().resolve(generationDirectoryName(generationId));
    }

    public Path generationManifest(Path generationDirectory) {
        return generationDirectory.resolve(GENERATION_MANIFEST_FILE);
    }

    public Path ownersDirectory(Path generationDirectory) {
        return generationDirectory.resolve(OWNERS_DIRECTORY);
    }

    public Path ownerDirectory(Path generationDirectory, CheckpointOwnerId ownerId) {
        return ownersDirectory(generationDirectory).resolve(ownerDirectoryName(ownerId));
    }

    public Path ownerPayload(Path generationDirectory, CheckpointOwnerId ownerId) {
        return ownerDirectory(generationDirectory, ownerId).resolve(OWNER_PAYLOAD_FILE);
    }

    public Path ownerManifest(Path generationDirectory, CheckpointOwnerId ownerId) {
        return ownerDirectory(generationDirectory, ownerId).resolve(OWNER_MANIFEST_FILE);
    }

    public Path headA() {
        return storeRoot.resolve(HEAD_A_FILE);
    }

    public Path headB() {
        return storeRoot.resolve(HEAD_B_FILE);
    }

    public Path headForSequence(long sequence) {
        return sequence % 2L == 1L ? headA() : headB();
    }

    public static String generationDirectoryName(CheckpointGenerationId generationId) {
        Objects.requireNonNull(generationId, "generationId");
        return "schema_%d_sequence_%020d_tick_%d".formatted(
                generationId.schemaVersion(),
                generationId.committedSequence(),
                generationId.authoritativeSimulationTick()
        );
    }

    public static String ownerDirectoryName(CheckpointOwnerId ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        StringBuilder builder = new StringBuilder();
        for (char value : ownerId.value().toCharArray()) {
            if ((value >= 'a' && value <= 'z')
                    || (value >= '0' && value <= '9')
                    || value == '.'
                    || value == '_'
                    || value == '-') {
                builder.append(value);
            } else if (value == ':') {
                builder.append("__colon__");
            } else if (value == '/') {
                builder.append("__slash__");
            } else {
                throw new IllegalArgumentException("Unsupported owner id character: " + value);
            }
        }
        return builder.toString();
    }
}
