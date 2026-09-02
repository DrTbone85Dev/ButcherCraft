package com.butchercraft.world.checkpoint;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Child-process fixture for proving restoration convergence after an abrupt JVM halt. */
public final class OwnerNativeRestorationHardCrashProcess {
    static final int HARD_CRASH_EXIT_CODE = 73;

    private OwnerNativeRestorationHardCrashProcess() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected <world-root> <crash|resume>");
        }
        Fixture fixture = fixture(Path.of(arguments[0]));
        switch (arguments[1]) {
            case "crash" -> crashAfterEightOwners(fixture);
            case "resume" -> resume(fixture);
            default -> throw new IllegalArgumentException("Unsupported child-process mode: " + arguments[1]);
        }
    }

    static Fixture fixture(Path worldRoot) {
        Path normalizedWorldRoot = worldRoot.toAbsolutePath().normalize();
        Path ownerRoot = normalizedWorldRoot.resolve("butchercraft");
        Path checkpointRoot = ownerRoot.resolve("checkpoints");
        WorldIdentityRootReference world = new WorldIdentityRootReference(
                "butchercraft:world/hard_crash_test", 1, digest("world"));
        PlatformDeterminismManifestReference platform = new PlatformDeterminismManifestReference(
                "butchercraft:platform/hard_crash_test", 1, digest("platform"));
        CheckpointGenerationId generationId = CheckpointGenerationId.of(1L, 50L);
        List<CheckpointOwnerSnapshotPayload> snapshots = new ArrayList<>();
        int sequence = 1;
        for (CheckpointOwnerId owner : LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS) {
            byte[] payload = ("snapshot:" + owner.value()).getBytes(StandardCharsets.UTF_8);
            String contentDigest = CheckpointSnapshotDigest.sha256(payload);
            OwnerSnapshotDescriptor descriptor = new OwnerSnapshotDescriptor(
                    owner,
                    1,
                    owner.value() + "/snapshot/" + sequence,
                    contentDigest,
                    CheckpointSnapshotParticipation.REQUIRED,
                    owner.value() + "/configuration/1",
                    world,
                    generationId,
                    50L,
                    sequence++
            );
            snapshots.add(new CheckpointOwnerSnapshotPayload(descriptor, payload, contentDigest));
        }
        CheckpointGenerationManifest manifest = new CheckpointGenerationCandidate(
                generationId,
                Optional.empty(),
                Optional.empty(),
                50L,
                snapshots.stream().map(CheckpointOwnerSnapshotPayload::descriptor).toList(),
                platform,
                world,
                CheckpointPublicationState.COMPLETE_CANDIDATE
        ).toManifest();
        CheckpointRecoveredGeneration generation = new CheckpointRecoveredGeneration(
                new CheckpointGenerationRecord(manifest, CheckpointPublicationState.COMMITTED), snapshots);
        return new Fixture(
                ownerRoot,
                new RestorationStorage(checkpointRoot),
                new OwnerNativeRestorationContext(
                        normalizedWorldRoot,
                        ownerRoot,
                        world,
                        manifest,
                        RestorationSource.CHECKPOINT,
                        Optional.empty()
                ),
                generation,
                CheckpointHeadRecord.forManifest(manifest)
        );
    }

    private static void crashAfterEightOwners(Fixture fixture) {
        AtomicInteger completedOwners = new AtomicInteger();
        fixture.coordinator().prepareAndRestore(
                fixture.context(),
                fixture.generation(),
                fixture.head(),
                (phase, ownerId, logicalFile) -> {
                    if (phase == RestorationPhase.OWNER_COMPLETED
                            && completedOwners.incrementAndGet() == 8) {
                        Runtime.getRuntime().halt(HARD_CRASH_EXIT_CODE);
                    }
                }
        );
        throw new IllegalStateException("Hard-crash boundary was not reached");
    }

    private static void resume(Fixture fixture) {
        List<RestorationIntent> intents = fixture.storage().incompleteIntents();
        if (intents.size() != 1) {
            throw new IllegalStateException("Fresh process expected one incomplete Restoration Intent");
        }
        RestorationResult result = fixture.coordinator().resume(
                fixture.context(), intents.getFirst(), RestorationProbe.NONE);
        if (result.participants().size() != LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS.size()) {
            throw new IllegalStateException("Fresh process did not restore the exact owner set");
        }
        System.out.println("RESTORATION_RESUMED " + result.restorationIdentity().value());
    }

    static byte[] nativeBytes(CheckpointOwnerId owner) {
        return ("native:" + owner.value()).getBytes(StandardCharsets.UTF_8);
    }

    static String relativePath(CheckpointOwnerId owner) {
        return "owners/" + owner.value().replace(':', '_').replace('/', '_') + ".json";
    }

    private static String digest(String value) {
        return CheckpointSnapshotDigest.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    record Fixture(
            Path ownerRoot,
            RestorationStorage storage,
            OwnerNativeRestorationContext context,
            CheckpointRecoveredGeneration generation,
            CheckpointHeadRecord head
    ) {
        OwnerNativeRestorationCoordinator coordinator() {
            List<OwnerNativeRestorationAdapter> adapters =
                    LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS.stream()
                            .map(FakeAdapter::new)
                            .map(OwnerNativeRestorationAdapter.class::cast)
                            .toList();
            return new OwnerNativeRestorationCoordinator(storage, adapters);
        }
    }

    private record FakeAdapter(CheckpointOwnerId ownerId) implements OwnerNativeRestorationAdapter {
        @Override
        public OwnerNativeRestorationPlan prepare(
                OwnerNativeRestorationContext context,
                CheckpointOwnerSnapshotPayload snapshot
        ) {
            if (!snapshot.descriptor().ownerId().equals(ownerId)) {
                throw new IllegalArgumentException("Wrong owner snapshot");
            }
            List<OwnerNativeRestorationPlan.NativeFile> files = new ArrayList<>();
            files.add(OwnerNativeRestorationPlan.NativeFile.of(
                    ownerId.value(), relativePath(ownerId), nativeBytes(ownerId)));
            if (ownerId.equals(LegacySplitRecoveryParticipants.WORKSTATION)) {
                files.add(OwnerNativeRestorationPlan.NativeFile.of(
                        "workstation_reservations.json",
                        "workstation_reservations.json",
                        "{}".getBytes(StandardCharsets.UTF_8)));
            }
            return OwnerNativeRestorationPlan.create(
                    snapshot.descriptor(), 1, files, Optional.empty());
        }

        @Override
        public void verify(OwnerNativeRestorationContext context, OwnerNativeRestorationPlan plan) {
            if (!ownerId.equals(plan.ownerId())) {
                throw new IllegalStateException("Prepared plan belongs to another owner");
            }
        }
    }
}
