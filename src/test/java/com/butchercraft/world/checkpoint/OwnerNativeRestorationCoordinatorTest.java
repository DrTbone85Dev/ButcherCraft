package com.butchercraft.world.checkpoint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerNativeRestorationCoordinatorTest {
    @TempDir
    Path temporary;

    @Test
    void restoresExactSeventeenOwnersAndRepeatedRequestObservesResult() throws Exception {
        Fixture fixture = fixture();
        AtomicInteger preparations = new AtomicInteger();
        OwnerNativeRestorationCoordinator coordinator = fixture.coordinator(preparations);

        RestorationResult first = coordinator.prepareAndRestore(
                fixture.context(), fixture.generation(), fixture.head(), RestorationProbe.NONE);
        RestorationResult second = coordinator.prepareAndRestore(
                fixture.context(), fixture.generation(), fixture.head(), RestorationProbe.NONE);

        assertEquals(first, second);
        assertEquals(17, first.participants().size());
        assertEquals(17, preparations.get());
        assertFalse(first.mutationGate().wholeWorldConsequentialMutationBlocked());
        for (CheckpointOwnerId owner : LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS) {
            assertArrayEquals(nativeBytes(owner), Files.readAllBytes(fixture.ownerRoot().resolve(path(owner))));
        }
    }

    @Test
    void crashAfterIntentResumesBeforeAnyOwnerPublication() {
        Fixture fixture = fixture();
        OwnerNativeRestorationCoordinator coordinator = fixture.coordinator(new AtomicInteger());

        assertThrows(SimulatedHardCrash.class, () -> coordinator.prepareAndRestore(
                fixture.context(), fixture.generation(), fixture.head(),
                crashAt(RestorationPhase.INTENT_PUBLISHED, 1)));

        RestorationStorage storage = fixture.storage();
        assertEquals(1, storage.incompleteIntents().size());
        assertTrue(LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS.stream()
                .noneMatch(owner -> Files.exists(fixture.ownerRoot().resolve(path(owner)))));

        RestorationIntent intent = storage.incompleteIntents().getFirst();
        RestorationResult result = fixture.coordinator(new AtomicInteger())
                .resume(fixture.context(), intent, RestorationProbe.NONE);
        assertEquals(17, result.participants().size());
        assertTrue(storage.incompleteIntents().isEmpty());
    }

    @Test
    void crashAfterHalfTheOwnersResumesWithoutMixedGeneration() {
        Fixture fixture = fixture();
        OwnerNativeRestorationCoordinator coordinator = fixture.coordinator(new AtomicInteger());

        assertThrows(SimulatedHardCrash.class, () -> coordinator.prepareAndRestore(
                fixture.context(), fixture.generation(), fixture.head(),
                crashAt(RestorationPhase.OWNER_COMPLETED, 8)));

        RestorationIntent intent = fixture.storage().incompleteIntents().getFirst();
        assertEquals(8, intent.expectedOwners().stream()
                .filter(owner -> fixture.storage().loadParticipant(
                        intent.restorationIdentity(), owner.ownerId()).isPresent()).count());
        RestorationResult result = fixture.coordinator(new AtomicInteger())
                .resume(fixture.context(), intent, RestorationProbe.NONE);
        assertEquals(17, result.participants().size());
        result.participants().forEach(participant -> assertTrue(
                participant.files().stream().allMatch(file -> Files.isRegularFile(
                        fixture.ownerRoot().resolve(file.targetRelativePath())))));
    }

    @Test
    void crashAfterAllOwnersBeforeCrossOwnerVerificationResumesSafely() {
        Fixture fixture = fixture();
        assertThrows(SimulatedHardCrash.class, () -> fixture.coordinator(new AtomicInteger())
                .prepareAndRestore(fixture.context(), fixture.generation(), fixture.head(),
                        crashAt(RestorationPhase.NATIVE_SET_PUBLISHED, 1)));

        RestorationIntent intent = fixture.storage().incompleteIntents().getFirst();
        assertEquals(17, intent.expectedOwners().stream()
                .filter(owner -> fixture.storage().loadParticipant(
                        intent.restorationIdentity(), owner.ownerId()).isPresent()).count());
        RestorationResult result = fixture.coordinator(new AtomicInteger())
                .resume(fixture.context(), intent, RestorationProbe.NONE);

        assertEquals(17, result.participants().size());
        assertTrue(fixture.storage().incompleteIntents().isEmpty());
    }

    @Test
    void crashAfterCrossOwnerVerificationBeforeResultResumesAndReverifies() {
        Fixture fixture = fixture();
        AtomicInteger verifications = new AtomicInteger();
        OwnerNativeRestorationCoordinator coordinator = fixture.coordinator(
                new AtomicInteger(), (context, plans) -> verifications.incrementAndGet());
        assertThrows(SimulatedHardCrash.class, () -> coordinator.prepareAndRestore(
                fixture.context(), fixture.generation(), fixture.head(),
                crashAt(RestorationPhase.RESTORATION_VERIFIED, 1)));

        RestorationIntent intent = fixture.storage().incompleteIntents().getFirst();
        RestorationResult result = fixture.coordinator(
                new AtomicInteger(), (context, plans) -> verifications.incrementAndGet())
                .resume(fixture.context(), intent, RestorationProbe.NONE);

        assertEquals(2, verifications.get());
        assertEquals(17, result.participants().size());
    }

    @Test
    void failedCrossOwnerVerificationCannotPublishCompletionResult() {
        Fixture fixture = fixture();
        StartupRecoveryException failure = assertThrows(StartupRecoveryException.class, () ->
                fixture.coordinator(new AtomicInteger(), (context, plans) -> {
                    throw new IllegalStateException("split owner set");
                }).prepareAndRestore(
                        fixture.context(), fixture.generation(), fixture.head(), RestorationProbe.NONE));

        assertEquals(StartupRecoveryFailureCode.RESTORATION_INCOMPLETE, failure.failureCode());
        RestorationIntent intent = fixture.storage().incompleteIntents().getFirst();
        assertTrue(fixture.storage().loadResult(intent.restorationIdentity()).isEmpty());
    }

    @Test
    void crashAfterNativeFileBeforeParticipantObservesExactBytesOnRetry() {
        Fixture fixture = fixture();
        OwnerNativeRestorationCoordinator coordinator = fixture.coordinator(new AtomicInteger());

        assertThrows(SimulatedHardCrash.class, () -> coordinator.prepareAndRestore(
                fixture.context(), fixture.generation(), fixture.head(),
                crashAt(RestorationPhase.NATIVE_FILE_PUBLISHED, 1)));

        RestorationIntent intent = fixture.storage().incompleteIntents().getFirst();
        RestorationResult result = fixture.coordinator(new AtomicInteger())
                .resume(fixture.context(), intent, RestorationProbe.NONE);
        RestorationParticipantResult first = result.participants().getFirst();
        assertEquals(RestorationParticipantResult.Publication.OBSERVED_EXACT,
                first.files().getFirst().publication());
    }

    @Test
    void unexpectedNativeStateAfterIntentIsAVisibleConflict() throws Exception {
        Fixture fixture = fixture();
        OwnerNativeRestorationCoordinator coordinator = fixture.coordinator(new AtomicInteger());
        assertThrows(SimulatedHardCrash.class, () -> coordinator.prepareAndRestore(
                fixture.context(), fixture.generation(), fixture.head(),
                crashAt(RestorationPhase.INTENT_PUBLISHED, 1)));
        RestorationIntent intent = fixture.storage().incompleteIntents().getFirst();
        CheckpointOwnerId firstOwner = intent.expectedOwners().getFirst().ownerId();
        Path target = fixture.ownerRoot().resolve(path(firstOwner));
        Files.createDirectories(target.getParent());
        Files.writeString(target, "unexpected", StandardCharsets.UTF_8);

        StartupRecoveryException failure = assertThrows(StartupRecoveryException.class, () ->
                fixture.coordinator(new AtomicInteger()).resume(
                        fixture.context(), intent, RestorationProbe.NONE));
        assertEquals(StartupRecoveryFailureCode.RESTORATION_CONFLICT, failure.failureCode());
        assertEquals("unexpected", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    void changedPreexistingStateAfterIntentIsNotOverwritten() throws Exception {
        Fixture fixture = fixture();
        CheckpointOwnerId firstOwner = LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS.getFirst();
        Path target = fixture.ownerRoot().resolve(path(firstOwner));
        Files.createDirectories(target.getParent());
        Files.writeString(target, "pre-state", StandardCharsets.UTF_8);
        assertThrows(SimulatedHardCrash.class, () -> fixture.coordinator(new AtomicInteger())
                .prepareAndRestore(fixture.context(), fixture.generation(), fixture.head(),
                        crashAt(RestorationPhase.INTENT_PUBLISHED, 1)));
        Files.writeString(target, "outside-change", StandardCharsets.UTF_8);

        RestorationIntent intent = fixture.storage().incompleteIntents().getFirst();
        StartupRecoveryException failure = assertThrows(StartupRecoveryException.class, () ->
                fixture.coordinator(new AtomicInteger()).resume(
                        fixture.context(), intent, RestorationProbe.NONE));
        assertEquals(StartupRecoveryFailureCode.RESTORATION_CONFLICT, failure.failureCode());
        assertEquals("outside-change", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    void crashAfterResultConvergesByObservingImmutableCompletion() {
        Fixture fixture = fixture();
        assertThrows(SimulatedHardCrash.class, () -> fixture.coordinator(new AtomicInteger())
                .prepareAndRestore(fixture.context(), fixture.generation(), fixture.head(),
                        crashAt(RestorationPhase.RESULT_PUBLISHED, 1)));

        assertTrue(fixture.storage().incompleteIntents().isEmpty());
        RestorationResult observed = fixture.coordinator(new AtomicInteger()).prepareAndRestore(
                fixture.context(), fixture.generation(), fixture.head(), RestorationProbe.NONE);
        assertEquals(17, observed.participants().size());
    }

    @Test
    void restorationIdentityAndIntentAreDeterministic() {
        Fixture first = fixture();
        Fixture second = fixture();
        RestorationIdentity firstIdentity = RestorationIdentity.derive(
                first.context().worldIdentityRoot(), first.generation().manifest(), first.head(),
                RestorationSource.CHECKPOINT, Optional.empty());
        RestorationIdentity secondIdentity = RestorationIdentity.derive(
                second.context().worldIdentityRoot(), second.generation().manifest(), second.head(),
                RestorationSource.CHECKPOINT, Optional.empty());
        assertEquals(firstIdentity, secondIdentity);
    }

    @Test
    void missingRequiredParticipantBlocksBeforeIntent() {
        Fixture fixture = fixture();
        CheckpointRecoveredGeneration incomplete = new CheckpointRecoveredGeneration(
                fixture.generation().generationRecord(),
                fixture.generation().ownerSnapshots().subList(1, fixture.generation().ownerSnapshots().size()));
        StartupRecoveryException failure = assertThrows(StartupRecoveryException.class, () ->
                fixture.coordinator(new AtomicInteger()).prepareAndRestore(
                        fixture.context(), incomplete, fixture.head(), RestorationProbe.NONE));
        assertEquals(StartupRecoveryFailureCode.CHECKPOINT_INVALID, failure.failureCode());
        assertTrue(fixture.storage().incompleteIntents().isEmpty());
    }

    private Fixture fixture() {
        Path worldRoot = temporary.resolve("world");
        Path ownerRoot = worldRoot.resolve("butchercraft");
        Path checkpointRoot = ownerRoot.resolve("checkpoints");
        WorldIdentityRootReference world = new WorldIdentityRootReference(
                "butchercraft:world/test", 1, digest("world"));
        PlatformDeterminismManifestReference platform = new PlatformDeterminismManifestReference(
                "butchercraft:platform/test", 1, digest("platform"));
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
                        worldRoot, ownerRoot, world, manifest, RestorationSource.CHECKPOINT, Optional.empty()),
                generation,
                CheckpointHeadRecord.forManifest(manifest)
        );
    }

    private static RestorationProbe crashAt(RestorationPhase expected, int occurrence) {
        AtomicInteger count = new AtomicInteger();
        return (phase, owner, file) -> {
            if (phase == expected && count.incrementAndGet() == occurrence) throw new SimulatedHardCrash();
        };
    }

    private static List<OwnerNativeRestorationAdapter> adapters(AtomicInteger preparations) {
        return LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS.stream()
                .map(owner -> new FakeAdapter(owner, preparations))
                .map(OwnerNativeRestorationAdapter.class::cast)
                .toList();
    }

    private static byte[] nativeBytes(CheckpointOwnerId owner) {
        return ("native:" + owner.value()).getBytes(StandardCharsets.UTF_8);
    }

    private static String path(CheckpointOwnerId owner) {
        return "owners/" + owner.value().replace(':', '_').replace('/', '_') + ".json";
    }

    private static String digest(String value) {
        return CheckpointSnapshotDigest.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private record Fixture(
            Path ownerRoot,
            RestorationStorage storage,
            OwnerNativeRestorationContext context,
            CheckpointRecoveredGeneration generation,
            CheckpointHeadRecord head
    ) {
        OwnerNativeRestorationCoordinator coordinator(AtomicInteger preparations) {
            return new OwnerNativeRestorationCoordinator(storage, adapters(preparations));
        }

        OwnerNativeRestorationCoordinator coordinator(
                AtomicInteger preparations,
                RestorationCompletionVerifier verifier
        ) {
            return new OwnerNativeRestorationCoordinator(storage, adapters(preparations), verifier);
        }
    }

    private record FakeAdapter(
            CheckpointOwnerId ownerId,
            AtomicInteger preparations
    ) implements OwnerNativeRestorationAdapter {
        @Override
        public OwnerNativeRestorationPlan prepare(
                OwnerNativeRestorationContext context,
                CheckpointOwnerSnapshotPayload snapshot
        ) {
            preparations.incrementAndGet();
            if (!snapshot.descriptor().ownerId().equals(ownerId)) {
                throw new IllegalArgumentException("Wrong owner snapshot");
            }
            return OwnerNativeRestorationPlan.create(
                    snapshot.descriptor(),
                    List.of(OwnerNativeRestorationPlan.NativeFile.of(
                            ownerId.value(), path(ownerId), nativeBytes(ownerId))),
                    Optional.empty()
            );
        }

        @Override
        public void verify(OwnerNativeRestorationContext context, OwnerNativeRestorationPlan plan) {
            assertEquals(ownerId, plan.ownerId());
            assertArrayEquals(nativeBytes(ownerId), plan.nativeFiles().getFirst().bytes());
        }
    }

    private static final class SimulatedHardCrash extends Error {
    }
}
