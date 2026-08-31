package com.butchercraft.world.checkpoint;

import com.butchercraft.persistence.StrictJsonPersistence;
import com.google.gson.Gson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class RestorationStorage {
    public static final String DIRECTORY_NAME = "restorations";
    public static final String INTENT_FILE = "restoration_intent.json";
    public static final String RESULT_FILE = "restoration_result.json";
    public static final String PREPARED_OWNERS_DIRECTORY = "prepared_owners";
    private static final Gson GSON = StrictJsonPersistence.gson();

    private final Path root;

    public RestorationStorage(Path checkpointRoot) {
        root = Objects.requireNonNull(checkpointRoot, "checkpointRoot")
                .toAbsolutePath().normalize().resolve(DIRECTORY_NAME);
    }

    public Path directory(RestorationIdentity identity) {
        return root.resolve(suffix(identity));
    }

    public Optional<RestorationIntent> loadIntent(RestorationIdentity identity) {
        return load(directory(identity).resolve(INTENT_FILE), RestorationIntent.class, "restoration intent");
    }

    public Optional<OwnerNativeRestorationPlan> loadPreparedPlan(
            RestorationIdentity identity,
            CheckpointOwnerId ownerId
    ) {
        return load(preparedPlanPath(identity, ownerId), OwnerNativeRestorationPlan.class,
                "prepared owner restoration plan");
    }

    public synchronized OwnerNativeRestorationPlan saveOrObservePreparedPlan(
            RestorationIdentity identity,
            OwnerNativeRestorationPlan plan
    ) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(plan, "plan");
        Optional<OwnerNativeRestorationPlan> existing = loadPreparedPlan(identity, plan.ownerId());
        if (existing.isPresent()) {
            if (!existing.orElseThrow().equals(plan)) {
                throw new IllegalStateException("Prepared owner restoration plan conflicts with frozen content");
            }
            return existing.orElseThrow();
        }
        return publishAndRead(
                preparedPlanPath(identity, plan.ownerId()),
                plan,
                OwnerNativeRestorationPlan.class,
                "prepared owner restoration plan"
        );
    }

    public List<OwnerNativeRestorationPlan> loadCompletePreparedPlans(RestorationIntent intent) {
        Objects.requireNonNull(intent, "intent");
        List<OwnerNativeRestorationPlan> plans = intent.expectedOwners().stream()
                .map(expected -> loadPreparedPlan(intent.restorationIdentity(), expected.ownerId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Restoration intent is missing prepared owner bytes: " + expected.ownerId().value()
                        )))
                .sorted()
                .toList();
        for (int index = 0; index < plans.size(); index++) {
            OwnerNativeRestorationPlan plan = plans.get(index);
            RestorationIntent.ExpectedOwner expected = intent.expectedOwners().get(index);
            if (!plan.ownerId().equals(expected.ownerId())
                    || !plan.snapshotIdentity().equals(expected.snapshotIdentity())
                    || !plan.snapshotContentDigest().equals(expected.snapshotContentDigest())
                    || plan.ownerSchemaVersion() != expected.ownerSchemaVersion()
                    || !plan.logicalContentDigest().equals(expected.logicalContentDigest())) {
                throw new IllegalStateException("Prepared owner bytes do not match restoration intent: "
                        + expected.ownerId().value());
            }
        }
        return plans;
    }

    public synchronized RestorationIntent saveOrObserveIntent(RestorationIntent intent) {
        Objects.requireNonNull(intent, "intent");
        Optional<RestorationIntent> existing = loadIntent(intent.restorationIdentity());
        if (existing.isPresent()) {
            if (!existing.orElseThrow().equals(intent)) {
                throw new IllegalStateException("Restoration intent conflicts with immutable prepared content");
            }
            return existing.orElseThrow();
        }
        return publishAndRead(
                directory(intent.restorationIdentity()).resolve(INTENT_FILE),
                intent,
                RestorationIntent.class,
                "restoration intent"
        );
    }

    public Optional<RestorationParticipantResult> loadParticipant(
            RestorationIdentity identity,
            CheckpointOwnerId ownerId
    ) {
        return load(participantPath(identity, ownerId), RestorationParticipantResult.class,
                "restoration participant result");
    }

    public synchronized RestorationParticipantResult saveOrObserveParticipant(
            RestorationParticipantResult result
    ) {
        Objects.requireNonNull(result, "result");
        Optional<RestorationParticipantResult> existing = loadParticipant(
                result.restorationIdentity(), result.ownerId());
        if (existing.isPresent()) {
            if (!existing.orElseThrow().equals(result)) {
                throw new IllegalStateException("Restoration participant result conflicts with prior publication");
            }
            return existing.orElseThrow();
        }
        return publishAndRead(
                participantPath(result.restorationIdentity(), result.ownerId()),
                result,
                RestorationParticipantResult.class,
                "restoration participant result"
        );
    }

    public Optional<RestorationResult> loadResult(RestorationIdentity identity) {
        return load(directory(identity).resolve(RESULT_FILE), RestorationResult.class, "restoration result");
    }

    public synchronized RestorationResult saveOrObserveResult(RestorationResult result) {
        Objects.requireNonNull(result, "result");
        Optional<RestorationResult> existing = loadResult(result.restorationIdentity());
        if (existing.isPresent()) {
            if (!existing.orElseThrow().equals(result)) {
                throw new IllegalStateException("Restoration result conflicts with prior completion evidence");
            }
            return existing.orElseThrow();
        }
        return publishAndRead(
                directory(result.restorationIdentity()).resolve(RESULT_FILE),
                result,
                RestorationResult.class,
                "restoration result"
        );
    }

    public List<RestorationResult> completedResults() {
        if (!Files.isDirectory(root)) return List.of();
        List<RestorationResult> results = new ArrayList<>();
        try (Stream<Path> directories = Files.list(root)) {
            for (Path directory : directories.filter(Files::isDirectory).sorted().toList()) {
                Path path = directory.resolve(RESULT_FILE);
                if (Files.exists(path)) {
                    load(path, RestorationResult.class, "restoration result").ifPresent(results::add);
                }
            }
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException("Failed to enumerate restoration results", exception);
        }
        return results.stream().sorted(java.util.Comparator.comparing(RestorationResult::restoredSimulationTick)
                .thenComparing(result -> result.restorationIdentity().value())).toList();
    }

    public List<RestorationIntent> incompleteIntents() {
        if (!Files.isDirectory(root)) return List.of();
        List<RestorationIntent> intents = new ArrayList<>();
        try (Stream<Path> directories = Files.list(root)) {
            for (Path directory : directories.filter(Files::isDirectory).sorted().toList()) {
                Path intentPath = directory.resolve(INTENT_FILE);
                if (!Files.isRegularFile(intentPath) || Files.isRegularFile(directory.resolve(RESULT_FILE))) continue;
                load(intentPath, RestorationIntent.class, "restoration intent").ifPresent(intents::add);
            }
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException("Failed to enumerate incomplete restorations", exception);
        }
        return intents.stream().sorted(java.util.Comparator.comparing(
                intent -> intent.restorationIdentity().value())).toList();
    }

    private Path participantPath(RestorationIdentity identity, CheckpointOwnerId ownerId) {
        return directory(identity).resolve("participants").resolve(safe(ownerId.value()) + ".json");
    }

    private Path preparedPlanPath(RestorationIdentity identity, CheckpointOwnerId ownerId) {
        return directory(identity).resolve(PREPARED_OWNERS_DIRECTORY).resolve(safe(ownerId.value()) + ".json");
    }

    private <T> Optional<T> load(Path path, Class<T> type, String label) {
        if (!Files.exists(path)) return Optional.empty();
        StrictJsonPersistence.requireNoInterruptedPublication(path, label);
        try {
            return Optional.of(Objects.requireNonNull(
                    GSON.fromJson(StrictJsonPersistence.read(path, label), type), label
            ));
        } catch (RuntimeException exception) {
            throw StrictJsonPersistence.corrupt(label, exception);
        }
    }

    private <T> T publishAndRead(Path path, T value, Class<T> type, String label) {
        StrictJsonPersistence.publish(path, GSON.toJson(value) + System.lineSeparator(), label);
        T observed = load(path, type, label).orElseThrow();
        if (!observed.equals(value)) {
            throw new IllegalStateException(label + " failed semantic read-back verification");
        }
        return observed;
    }

    private static String suffix(RestorationIdentity identity) {
        String value = Objects.requireNonNull(identity, "identity").value();
        return value.substring(value.lastIndexOf('/') + 1);
    }

    private static String safe(String value) {
        return value.replace(":", "__colon__").replace("/", "__slash__");
    }
}
