package com.butchercraft.world.checkpoint;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveCheckpointFoundationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void requiredParticipantRegistryIsCanonicalAndComplete() {
        assertEquals(17, LiveCheckpointParticipantRegistry.requiredOwners().size());
        assertEquals(LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS,
                LiveCheckpointParticipantRegistry.requiredOwners());
        assertEquals(LiveCheckpointParticipantRegistry.requiredOwners().stream().distinct().count(),
                LiveCheckpointParticipantRegistry.requiredOwners().size());
        assertTrue(LiveCheckpointParticipantRegistry.requiredOwners().contains(
                CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER));
        assertTrue(LiveCheckpointParticipantRegistry.requiredOwners().contains(
                CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER));
        assertTrue(LiveCheckpointParticipantRegistry.requiredOwners().contains(
                LegacySplitRecoveryParticipants.CHECKPOINT_RECOVERY));
    }

    @Test
    void canonicalEmptyOwnerSnapshotRoundTripsDeterministically() {
        CheckpointOwnerFileSnapshot empty = ownerSnapshot(
                LegacySplitRecoveryParticipants.ORDERS,
                0L,
                true,
                "{\n  \"schema_version\": 1,\n  \"orders\": []\n}\n".getBytes(StandardCharsets.UTF_8)
        );

        byte[] first = CheckpointOwnerFileBundleCodec.encode(empty);
        byte[] second = CheckpointOwnerFileBundleCodec.encode(empty);
        CheckpointOwnerFileSnapshot restored = CheckpointOwnerFileBundleCodec.decode(first);

        assertArrayEquals(first, second);
        assertEquals(empty.ownerId(), restored.ownerId());
        assertTrue(restored.canonicalEmpty());
        assertArrayEquals(empty.files().getFirst().bytes(), restored.files().getFirst().bytes());
    }

    @Test
    void frozenSnapshotBytesDoNotChangeAfterSourceMutation() {
        byte[] source = "before".getBytes(StandardCharsets.UTF_8);
        CheckpointOwnerFileSnapshot snapshot = ownerSnapshot(
                LegacySplitRecoveryParticipants.INVENTORY,
                3L,
                false,
                source
        );
        byte[] frozen = CheckpointOwnerFileBundleCodec.encode(snapshot);

        source[0] = 'X';
        byte[] returned = snapshot.files().getFirst().bytes();
        returned[0] = 'Y';

        assertArrayEquals(frozen, CheckpointOwnerFileBundleCodec.encode(snapshot));
        assertEquals("before", new String(snapshot.files().getFirst().bytes(), StandardCharsets.UTF_8));
    }

    @Test
    void coordinatorCapturesEveryRequiredOwnerExactlyOnceIncludingEmptyOwners() {
        CheckpointOwnerSnapshotContext context = context(1L, 42L, Optional.empty(), Optional.empty());
        List<CheckpointOwnerSnapshotProvider> providers = LiveCheckpointParticipantRegistry.requiredOwners().stream()
                .map(owner -> new CheckpointOwnerFileSnapshotProvider(
                        owner,
                        "butchercraft:test_configuration/" + owner.value().substring(owner.value().indexOf(':') + 1),
                        () -> ownerSnapshot(owner, 42L, true, "{}\n".getBytes(StandardCharsets.UTF_8))
                ))
                .map(CheckpointOwnerSnapshotProvider.class::cast)
                .toList();
        CheckpointCoordinatedCaptureReport report = new CheckpointOwnerSnapshotCoordinator(
                LiveCheckpointParticipantRegistry.requiredOwners(), providers, List.of()
        ).capture(context);

        assertTrue(report.successful(), () -> report.failures().toString());
        assertEquals(17, report.capturedSnapshots().size());
        assertEquals(17, report.publicationRequest().orElseThrow().ownerSnapshots().size());
        report.capturedSnapshots().forEach(captured -> assertTrue(
                CheckpointOwnerFileBundleCodec.decode(captured.payload().payloadBytes()).canonicalEmpty()
        ));
    }

    @Test
    void missingOrDuplicateLiveParticipantPreventsCapture() {
        List<CheckpointOwnerSnapshotProvider> providers = new ArrayList<>();
        for (CheckpointOwnerId owner : LiveCheckpointParticipantRegistry.requiredOwners()) {
            if (!owner.equals(LegacySplitRecoveryParticipants.CONTRACTS)) {
                providers.add(provider(owner, 1L));
            }
        }
        CheckpointCoordinatedCaptureReport missing = new CheckpointOwnerSnapshotCoordinator(
                LiveCheckpointParticipantRegistry.requiredOwners(), providers, List.of()
        ).capture(context(1L, 1L, Optional.empty(), Optional.empty()));
        providers.add(provider(LegacySplitRecoveryParticipants.ORDERS, 1L));
        CheckpointCoordinatedCaptureReport duplicate = new CheckpointOwnerSnapshotCoordinator(
                LiveCheckpointParticipantRegistry.requiredOwners(), providers, List.of()
        ).capture(context(1L, 1L, Optional.empty(), Optional.empty()));

        assertFalse(missing.successful());
        assertTrue(missing.failures().stream().anyMatch(failure ->
                failure.code() == CheckpointFailureCode.OWNER_PROVIDER_MISSING));
        assertFalse(duplicate.successful());
        assertTrue(duplicate.failures().stream().anyMatch(failure ->
                failure.code() == CheckpointFailureCode.OWNER_PROVIDER_DUPLICATE));
    }

    @Test
    void publishesThirtyMonotonicCompleteGenerationsAndRetainsAllPriorState() throws IOException {
        CheckpointFilesystemStore store = new CheckpointFilesystemStore(temporaryDirectory.resolve("checkpoints"));
        Optional<CheckpointGenerationId> predecessor = Optional.empty();
        Optional<String> predecessorDigest = Optional.empty();
        long aggregateSize = 0L;
        for (long sequence = 1L; sequence <= 30L; sequence++) {
            long tick = sequence * 6_000L;
            CheckpointOwnerSnapshotContext context = context(sequence, tick, predecessor, predecessorDigest);
            List<CheckpointOwnerSnapshotProvider> providers = LiveCheckpointParticipantRegistry.requiredOwners().stream()
                    .map(owner -> provider(owner, tick))
                    .toList();
            CheckpointCoordinatedCaptureReport capture = new CheckpointOwnerSnapshotCoordinator(
                    LiveCheckpointParticipantRegistry.requiredOwners(), providers, List.of()
            ).capture(context);
            assertTrue(capture.successful(), () -> capture.failures().toString());
            CheckpointPublicationReport publication = store.publish(capture.publicationRequest().orElseThrow());
            assertTrue(publication.successful(), () -> publication.diagnostics().toString());
            CheckpointGenerationManifest manifest = publication.generationManifest().orElseThrow();
            assertEquals(sequence, manifest.generationId().committedSequence());
            assertEquals(tick, manifest.authoritativeSimulationTick());
            assertEquals(17, manifest.ownerSnapshots().size());
            predecessor = Optional.of(manifest.generationId());
            predecessorDigest = Optional.of(manifest.manifestDigest());
            aggregateSize += directorySize(store.layout().finalGenerationDirectory(manifest.generationId()));
        }

        CheckpointFilesystemRecoveryReport recovery = store.recover(new CheckpointFilesystemRecoveryRequest(
                LiveCheckpointParticipantRegistry.requiredOwners(), worldRoot(), platformManifest()
        ));
        assertEquals(30L, recovery.selection().selectedGenerationId().orElseThrow().committedSequence());
        assertEquals(30, recovery.generationRecords().size());
        assertTrue(aggregateSize > 0L);
        assertTrue(Files.isDirectory(store.layout().finalGenerationDirectory(CheckpointGenerationId.of(1L, 6_000L))));
    }

    @Test
    void checkpointPackagingDoesNotMutateOwnerSourceFilesAndUsesAtomicPublicationPrimitive() throws IOException {
        Path source = temporaryDirectory.resolve("owner.json");
        Files.writeString(source, "owner-before\n");
        byte[] before = Files.readAllBytes(source);
        CheckpointOwnerSnapshotProvider provider = new CheckpointOwnerFileSnapshotProvider(
                LegacySplitRecoveryParticipants.GOODS,
                "butchercraft:test_configuration/goods",
                () -> ownerSnapshot(LegacySplitRecoveryParticipants.GOODS, 1L, false, before)
        );

        provider.capture(context(1L, 1L, Optional.empty(), Optional.empty()));

        assertArrayEquals(before, Files.readAllBytes(source));
        String storeSource = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/checkpoint/CheckpointFilesystemStore.java"
        ));
        assertTrue(storeSource.contains("AtomicFilePublication"));
        assertFalse(storeSource.contains(".tmp\"") && storeSource.contains("Files.move"),
                "Checkpoint head publication must use the shared unique-temp publication primitive");
    }

    @Test
    void operationalPolicyMatchesRatifiedSchemaOneDefaults() {
        assertEquals(6_000L, LiveCheckpointPolicy.PERIODIC_INTERVAL_TICKS);
        assertEquals(3, LiveCheckpointPolicy.MINIMUM_RETAINED_COMMITTED_GENERATIONS);
        assertTrue(LiveCheckpointPolicy.SHUTDOWN_WAIT_MILLIS > 0L);
    }

    @Test
    void committedManifestPersistsCanonicalTriggerCausesAndLegacyManifestsRemainReadable() {
        CheckpointCoordinatedCaptureReport capture = new CheckpointOwnerSnapshotCoordinator(
                LiveCheckpointParticipantRegistry.requiredOwners(),
                LiveCheckpointParticipantRegistry.requiredOwners().stream()
                        .map(owner -> provider(owner, 6_000L))
                        .toList(),
                List.of()
        ).capture(context(1L, 6_000L, Optional.empty(), Optional.empty()));
        CheckpointPublicationRequest request = capture.publicationRequest().orElseThrow().withTriggerCauses(List.of(
                "butchercraft:checkpoint_trigger/periodic",
                "butchercraft:checkpoint_trigger/manual"
        ));
        CheckpointGenerationManifest manifest = request.toCandidate().toManifest();

        CheckpointGenerationManifest restored = CheckpointFilesystemSerializer.parseGenerationManifest(
                new String(CheckpointFilesystemSerializer.generationManifestBytes(manifest), StandardCharsets.UTF_8)
        );

        assertEquals(List.of(
                "butchercraft:checkpoint_trigger/manual",
                "butchercraft:checkpoint_trigger/periodic"
        ), restored.triggerCauses());
        assertTrue(restored.digestMatches());

        String legacyJson = new String(
                CheckpointFilesystemSerializer.generationManifestBytes(manifest),
                StandardCharsets.UTF_8
        ).replace("\"trigger_causes\":[\"butchercraft:checkpoint_trigger/manual\","
                        + "\"butchercraft:checkpoint_trigger/periodic\"],", "");
        CheckpointGenerationManifest legacy = CheckpointFilesystemSerializer.parseGenerationManifest(legacyJson);
        assertEquals(List.of(), legacy.triggerCauses());
        assertFalse(legacy.digestMatches(), "Removing non-empty trigger evidence must invalidate a live manifest");
    }

    private CheckpointOwnerSnapshotProvider provider(CheckpointOwnerId owner, long tick) {
        return new CheckpointOwnerFileSnapshotProvider(
                owner,
                "butchercraft:test_configuration/" + owner.value().substring(owner.value().indexOf(':') + 1),
                () -> ownerSnapshot(owner, tick, false,
                        (owner.value() + "@" + tick + "\n").getBytes(StandardCharsets.UTF_8))
        );
    }

    private CheckpointOwnerFileSnapshot ownerSnapshot(
            CheckpointOwnerId owner,
            long sequence,
            boolean empty,
            byte[] bytes
    ) {
        return new CheckpointOwnerFileSnapshot(
                owner,
                1,
                sequence,
                empty,
                List.of(new CheckpointOwnerFileSnapshot.FilePayload("owner.json", bytes))
        );
    }

    private CheckpointOwnerSnapshotContext context(
            long sequence,
            long tick,
            Optional<CheckpointGenerationId> predecessor,
            Optional<String> predecessorDigest
    ) {
        return new CheckpointOwnerSnapshotContext(
                CheckpointGenerationId.of(sequence, tick),
                predecessor,
                predecessorDigest,
                tick,
                platformManifest(),
                worldRoot()
        );
    }

    private WorldIdentityRootReference worldRoot() {
        return new WorldIdentityRootReference(
                "butchercraft:world_identity/test",
                1,
                CheckpointSnapshotDigest.sha256("world".getBytes(StandardCharsets.UTF_8))
        );
    }

    private PlatformDeterminismManifestReference platformManifest() {
        return new PlatformDeterminismManifestReference(
                "butchercraft:platform_determinism/test",
                1,
                CheckpointSnapshotDigest.sha256("platform".getBytes(StandardCharsets.UTF_8))
        );
    }

    private long directorySize(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            }).sum();
        }
    }
}
