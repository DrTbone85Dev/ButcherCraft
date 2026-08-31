package com.butchercraft.world.checkpoint;

import com.butchercraft.persistence.StrictJsonPersistence;
import com.google.gson.Gson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class LegacySplitRecoveryPublicationStorage {
    public static final String DIRECTORY_NAME = "legacy_recovery_publications";
    public static final String INTENT_FILE = "publication_intent.json";
    public static final String RESULT_FILE = "recovery_result.json";
    private static final Gson GSON = StrictJsonPersistence.gson();

    private final Path checkpointRoot;

    public LegacySplitRecoveryPublicationStorage(Path checkpointRoot) {
        this.checkpointRoot = Objects.requireNonNull(checkpointRoot, "checkpointRoot")
                .toAbsolutePath()
                .normalize();
    }

    public Path publicationDirectory(LegacySplitRecoveryIdentity recoveryIdentity) {
        String suffix = recoveryIdentity.value().substring(recoveryIdentity.value().lastIndexOf('/') + 1);
        return checkpointRoot.resolve(DIRECTORY_NAME).resolve(suffix);
    }

    public Path intentPath(LegacySplitRecoveryIdentity recoveryIdentity) {
        return publicationDirectory(recoveryIdentity).resolve(INTENT_FILE);
    }

    public Path resultPath(LegacySplitRecoveryIdentity recoveryIdentity) {
        return publicationDirectory(recoveryIdentity).resolve(RESULT_FILE);
    }

    public Optional<LegacySplitRecoveryPublicationIntent> loadIntent(
            LegacySplitRecoveryIdentity recoveryIdentity
    ) {
        Path path = intentPath(recoveryIdentity);
        if (!Files.exists(path)) return Optional.empty();
        StrictJsonPersistence.requireNoInterruptedPublication(path, "legacy recovery publication intent");
        try {
            return Optional.of(Objects.requireNonNull(
                    GSON.fromJson(
                            StrictJsonPersistence.read(path, "legacy recovery publication intent"),
                            LegacySplitRecoveryPublicationIntent.class
                    ),
                    "legacy recovery publication intent"
            ));
        } catch (RuntimeException exception) {
            throw StrictJsonPersistence.corrupt("legacy recovery publication intent", exception);
        }
    }

    public synchronized LegacySplitRecoveryPublicationIntent saveOrObserveIntent(
            LegacySplitRecoveryPublicationIntent intent
    ) {
        Objects.requireNonNull(intent, "intent");
        Optional<LegacySplitRecoveryPublicationIntent> existing = loadIntent(intent.recoveryIdentity());
        if (existing.isPresent()) {
            if (!existing.orElseThrow().equals(intent)) {
                throw new IllegalStateException("Recovery publication intent conflicts with frozen content");
            }
            return existing.orElseThrow();
        }
        Path path = intentPath(intent.recoveryIdentity());
        StrictJsonPersistence.publish(
                path,
                GSON.toJson(intent) + System.lineSeparator(),
                "legacy recovery publication intent"
        );
        LegacySplitRecoveryPublicationIntent stored = loadIntent(intent.recoveryIdentity()).orElseThrow();
        if (!stored.equals(intent)) {
            throw new IllegalStateException("Recovery publication intent failed semantic read-back verification");
        }
        return stored;
    }

    public Optional<LegacySplitRecoveryResult> loadResult(LegacySplitRecoveryIdentity recoveryIdentity) {
        Path path = resultPath(recoveryIdentity);
        if (!Files.exists(path)) return Optional.empty();
        StrictJsonPersistence.requireNoInterruptedPublication(path, "legacy recovery result");
        try {
            return Optional.of(Objects.requireNonNull(
                    GSON.fromJson(
                            StrictJsonPersistence.read(path, "legacy recovery result"),
                            LegacySplitRecoveryResult.class
                    ),
                    "legacy recovery result"
            ));
        } catch (RuntimeException exception) {
            throw StrictJsonPersistence.corrupt("legacy recovery result", exception);
        }
    }

    public synchronized LegacySplitRecoveryResult saveOrObserveResult(LegacySplitRecoveryResult result) {
        Objects.requireNonNull(result, "result");
        Optional<LegacySplitRecoveryResult> existing = loadResult(result.recoveryIdentity());
        if (existing.isPresent()) {
            LegacySplitRecoveryResult observed = existing.orElseThrow();
            if (!observed.resultIdentity().equals(result.resultIdentity())
                    || !observed.contentDigest().equals(result.contentDigest())) {
                throw new IllegalStateException("Recovery result conflicts with committed recovery identity");
            }
            return observed;
        }
        Path path = resultPath(result.recoveryIdentity());
        StrictJsonPersistence.publish(
                path,
                GSON.toJson(result) + System.lineSeparator(),
                "legacy recovery result"
        );
        LegacySplitRecoveryResult stored = loadResult(result.recoveryIdentity()).orElseThrow();
        if (!stored.resultIdentity().equals(result.resultIdentity())
                || !stored.contentDigest().equals(result.contentDigest())) {
            throw new IllegalStateException("Recovery result failed semantic read-back verification");
        }
        return stored;
    }

    public List<LegacySplitRecoveryResult> completedResults() {
        Path root = checkpointRoot.resolve(DIRECTORY_NAME);
        if (!Files.isDirectory(root)) return List.of();
        List<LegacySplitRecoveryResult> results = new ArrayList<>();
        try (Stream<Path> directories = Files.list(root)) {
            for (Path directory : directories.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                String suffix = directory.getFileName().toString();
                LegacySplitRecoveryIdentity identity = new LegacySplitRecoveryIdentity(
                        "butchercraft:legacy_split_recovery/v1/" + suffix
                );
                loadResult(identity).ifPresent(results::add);
            }
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException("Failed to enumerate legacy recovery results", exception);
        }
        return results.stream().sorted(Comparator.comparing(
                result -> result.recoveryGenerationId().committedSequence())).toList();
    }
}
