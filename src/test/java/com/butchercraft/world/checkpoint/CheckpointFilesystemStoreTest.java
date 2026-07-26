package com.butchercraft.world.checkpoint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointFilesystemStoreTest {
    private static final WorldIdentityRootReference WORLD_ROOT = new WorldIdentityRootReference(
            "butchercraft:world/root",
            1,
            digest("world-root")
    );
    private static final WorldIdentityRootReference OTHER_WORLD_ROOT = new WorldIdentityRootReference(
            "butchercraft:world/other",
            1,
            digest("other-world-root")
    );
    private static final PlatformDeterminismManifestReference PLATFORM_MANIFEST =
            new PlatformDeterminismManifestReference(
                    "butchercraft:platform_determinism/main",
                    1,
                    digest("platform")
            );
    private static final PlatformDeterminismManifestReference OTHER_PLATFORM_MANIFEST =
            new PlatformDeterminismManifestReference(
                    "butchercraft:platform_determinism/other",
                    1,
                    digest("other-platform")
            );
    private static final List<CheckpointOwnerId> REQUIRED_OWNERS = List.of(
            CheckpointOwnerId.of("butchercraft:simulation"),
            CheckpointOwnerId.of("butchercraft:inventory"),
            CheckpointOwnerId.of("butchercraft:transactions")
    );

    @TempDir
    private Path temporaryDirectory;

    @Test
    void publishesFirstValidGenerationAndRecoversIt() {
        CheckpointFilesystemStore store = store("first");
        CheckpointPublicationReport report = store.publish(request(1L, 25L, Optional.empty()));

        assertEquals(CheckpointPublicationOutcome.PUBLISHED, report.outcome());
        assertTrue(report.successful());
        assertTrue(Files.isDirectory(store.layout().finalGenerationDirectory(report.generationManifest()
                .orElseThrow()
                .generationId())));
        assertTrue(Files.isRegularFile(store.layout().headA()));

        CheckpointFilesystemRecoveryReport recovery = store.recover(recoveryRequest());
        assertEquals(CheckpointRecoveryOutcome.LATEST_VALID_GENERATION_SELECTED, recovery.selection().outcome());
        assertEquals(report.generationManifest().map(CheckpointGenerationManifest::generationId),
                recovery.selection().selectedGenerationId());
    }

    @Test
    void publishesSecondGenerationWithPredecessorAndSelectsNewestHead() {
        CheckpointFilesystemStore store = store("second");
        CheckpointGenerationManifest first = publish(store, request(1L, 25L, Optional.empty()));
        CheckpointPublicationRequest secondRequest = request(2L, 40L, Optional.of(first));

        CheckpointGenerationManifest second = publish(store, secondRequest);
        CheckpointFilesystemRecoveryReport recovery = store.recover(recoveryRequest());

        assertTrue(Files.isRegularFile(store.layout().headA()));
        assertTrue(Files.isRegularFile(store.layout().headB()));
        assertEquals(CheckpointRecoveryOutcome.LATEST_VALID_GENERATION_SELECTED, recovery.selection().outcome());
        assertEquals(Optional.of(second.generationId()), recovery.selection().selectedGenerationId());
    }

    @Test
    void finalGenerationDirectoryIsImmutableToStoreDuplicateSafeAndConflictingWrites() {
        CheckpointFilesystemStore store = store("immutability");
        CheckpointPublicationRequest firstRequest = request(1L, 25L, Optional.empty());
        CheckpointGenerationManifest first = publish(store, firstRequest);

        CheckpointPublicationReport duplicate = store.publish(firstRequest);
        CheckpointPublicationReport conflict = store.publish(requestWithPayloadSuffix(
                1L,
                25L,
                Optional.empty(),
                "changed"
        ));

        assertEquals(CheckpointPublicationOutcome.DUPLICATE_OBSERVATION, duplicate.outcome());
        assertEquals(Optional.of(first), duplicate.generationManifest());
        assertEquals(CheckpointPublicationOutcome.CONFLICT, conflict.outcome());
        assertTrue(conflict.diagnostics().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.GENERATION_PUBLICATION_CONFLICT));
    }

    @Test
    void publicationInterruptionsBeforeCommitLeavePriorHeadRecoverable() {
        List<CheckpointPublicationPhase> preCommitPhases = List.of(
                CheckpointPublicationPhase.BEFORE_PAYLOAD_WRITE,
                CheckpointPublicationPhase.DURING_PAYLOAD_SET,
                CheckpointPublicationPhase.BEFORE_MANIFEST,
                CheckpointPublicationPhase.AFTER_MANIFEST,
                CheckpointPublicationPhase.BEFORE_FINAL_MOVE,
                CheckpointPublicationPhase.AFTER_FINAL_MOVE,
                CheckpointPublicationPhase.DURING_HEAD_WRITE
        );

        for (CheckpointPublicationPhase phase : preCommitPhases) {
            Path root = temporaryDirectory.resolve("crash_" + phase.name().toLowerCase());
            CheckpointFilesystemStore initialStore = new CheckpointFilesystemStore(root);
            CheckpointGenerationManifest first = publish(initialStore, request(1L, 25L, Optional.empty()));
            CheckpointFilesystemStore interrupted = new CheckpointFilesystemStore(root, reached -> {
                if (reached == phase) {
                    throw new IOException("simulated " + phase);
                }
            });

            CheckpointPublicationReport failed = interrupted.publish(request(2L, 40L, Optional.of(first)));
            CheckpointFilesystemRecoveryReport recovery = initialStore.recover(recoveryRequest());

            assertEquals(CheckpointPublicationOutcome.FAILED, failed.outcome(), phase.name());
            assertEquals(Optional.of(first.generationId()), recovery.selection().selectedGenerationId(), phase.name());
            assertTrue(recovery.selection().outcome() == CheckpointRecoveryOutcome.LATEST_VALID_GENERATION_SELECTED
                    || recovery.selection().outcome()
                    == CheckpointRecoveryOutcome.OLDER_VALID_GENERATION_SELECTED_AUTOMATICALLY);
        }
    }

    @Test
    void crashAfterHeadPublicationLeavesNewGenerationRecoverable() {
        Path root = temporaryDirectory.resolve("after_head");
        CheckpointFilesystemStore initialStore = new CheckpointFilesystemStore(root);
        CheckpointGenerationManifest first = publish(initialStore, request(1L, 25L, Optional.empty()));
        CheckpointFilesystemStore interrupted = new CheckpointFilesystemStore(root, reached -> {
            if (reached == CheckpointPublicationPhase.AFTER_HEAD_PUBLICATION) {
                throw new IOException("simulated after head");
            }
        });

        CheckpointPublicationReport failed = interrupted.publish(request(2L, 40L, Optional.of(first)));
        CheckpointFilesystemRecoveryReport recovery = initialStore.recover(recoveryRequest());

        assertEquals(CheckpointPublicationOutcome.FAILED, failed.outcome());
        assertEquals(Optional.of(CheckpointGenerationId.of(2L, 40L)), recovery.selection().selectedGenerationId());
    }

    @Test
    void incompleteStagingIsIgnoredAsAuthorityAndReported() throws IOException {
        CheckpointFilesystemStore store = store("staging");
        Files.createDirectories(store.layout().stagingGenerationDirectory(CheckpointGenerationId.of(1L, 25L)));

        CheckpointFilesystemRecoveryReport recovery = store.recover(recoveryRequest());

        assertEquals(CheckpointRecoveryOutcome.RECOVERY_BLOCKED, recovery.selection().outcome());
        assertTrue(recovery.artifacts().stream()
                .anyMatch(artifact -> artifact.kind() == CheckpointStorageArtifactKind.INCOMPLETE_STAGING_DIRECTORY));
        assertTrue(Files.exists(store.layout().stagingGenerationDirectory(CheckpointGenerationId.of(1L, 25L))));
    }

    @Test
    void corruptOwnerPayloadAndManifestCorruptionAreDetected() throws IOException {
        CheckpointFilesystemStore payloadStore = store("payload_corrupt");
        CheckpointGenerationManifest payloadManifest = publish(payloadStore, request(1L, 25L, Optional.empty()));
        Files.writeString(
                payloadStore.layout().ownerPayload(
                        payloadStore.layout().finalGenerationDirectory(payloadManifest.generationId()),
                        REQUIRED_OWNERS.getFirst()
                ),
                "corrupt",
                StandardCharsets.UTF_8
        );

        CheckpointFilesystemRecoveryReport payloadRecovery = payloadStore.recover(recoveryRequest());

        assertEquals(CheckpointRecoveryOutcome.RECOVERY_BLOCKED, payloadRecovery.selection().outcome());
        assertTrue(payloadRecovery.artifacts().stream()
                .anyMatch(artifact -> artifact.failure().code() == CheckpointFailureCode.PAYLOAD_DIGEST_MISMATCH));

        CheckpointFilesystemStore manifestStore = store("manifest_corrupt");
        CheckpointGenerationManifest manifest = publish(manifestStore, request(1L, 25L, Optional.empty()));
        Path manifestFile = manifestStore.layout().generationManifest(
                manifestStore.layout().finalGenerationDirectory(manifest.generationId())
        );
        String altered = Files.readString(manifestFile, StandardCharsets.UTF_8)
                .replace(manifest.manifestDigest(), digest("wrong"));
        Files.writeString(manifestFile, altered, StandardCharsets.UTF_8);

        CheckpointFilesystemRecoveryReport manifestRecovery = manifestStore.recover(recoveryRequest());

        assertEquals(CheckpointRecoveryOutcome.RECOVERY_BLOCKED, manifestRecovery.selection().outcome());
        assertTrue(manifestRecovery.artifacts().stream()
                .anyMatch(artifact -> artifact.failure().code() == CheckpointFailureCode.MANIFEST_DIGEST_MISMATCH));
    }

    @Test
    void invalidHeadDigestIsIgnoredAndOtherValidSlotCanRecover() throws IOException {
        CheckpointFilesystemStore store = store("heads");
        CheckpointGenerationManifest first = publish(store, request(1L, 25L, Optional.empty()));
        CheckpointGenerationManifest second = publish(store, request(2L, 40L, Optional.of(first)));

        corruptHeadDigest(store.layout().headB(), second);
        CheckpointFilesystemRecoveryReport recovery = store.recover(recoveryRequest());

        assertEquals(CheckpointRecoveryOutcome.OLDER_VALID_GENERATION_SELECTED_AUTOMATICALLY,
                recovery.selection().outcome());
        assertEquals(Optional.of(first.generationId()), recovery.selection().selectedGenerationId());
        assertTrue(recovery.artifacts().stream()
                .anyMatch(artifact -> artifact.kind() == CheckpointStorageArtifactKind.INVALID_HEAD_FILE));
    }

    @Test
    void newestCorruptGenerationFallsBackToOlderValidGeneration() throws IOException {
        CheckpointFilesystemStore store = store("fallback");
        CheckpointGenerationManifest first = publish(store, request(1L, 25L, Optional.empty()));
        CheckpointGenerationManifest second = publish(store, request(2L, 40L, Optional.of(first)));
        Files.writeString(
                store.layout().ownerPayload(
                        store.layout().finalGenerationDirectory(second.generationId()),
                        REQUIRED_OWNERS.getFirst()
                ),
                "corrupt",
                StandardCharsets.UTF_8
        );

        CheckpointFilesystemRecoveryReport recovery = store.recover(recoveryRequest());

        assertEquals(CheckpointRecoveryOutcome.OLDER_VALID_GENERATION_SELECTED_AUTOMATICALLY,
                recovery.selection().outcome());
        assertEquals(Optional.of(first.generationId()), recovery.selection().selectedGenerationId());
    }

    @Test
    void brokenPredecessorChainBlocksAffectedGenerationAndFallsBackWhenOlderHeadExists() {
        CheckpointFilesystemStore store = store("broken_chain");
        CheckpointGenerationManifest first = publish(store, request(1L, 25L, Optional.empty()));
        CheckpointGenerationManifest second = publish(store, requestWithPredecessorDigest(
                2L,
                40L,
                first,
                digest("wrong-predecessor")
        ));

        CheckpointFilesystemRecoveryReport recovery = store.recover(recoveryRequest());

        assertNotEquals(Optional.of(second.generationId()), recovery.selection().selectedGenerationId());
        assertEquals(Optional.of(first.generationId()), recovery.selection().selectedGenerationId());
        assertTrue(recovery.selection().diagnostics().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.PREDECESSOR_MISMATCH));
    }

    @Test
    void noValidGenerationReturnsRecoveryBlockedState() {
        CheckpointFilesystemStore store = store("empty");

        CheckpointFilesystemRecoveryReport recovery = store.recover(recoveryRequest());

        assertEquals(CheckpointRecoveryOutcome.RECOVERY_BLOCKED, recovery.selection().outcome());
        assertTrue(recovery.selection().diagnostics().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.CHECKPOINT_NO_VALID_GENERATION));
    }

    @Test
    void recoverySelectionIsIndependentOfFilesystemEnumerationOrder() {
        CheckpointFilesystemStore store = store("enumeration");
        CheckpointGenerationManifest first = publish(store, request(1L, 25L, Optional.empty()));
        publish(store, request(2L, 40L, Optional.of(first)));

        CheckpointFilesystemRecoveryReport firstRecovery = store.recover(recoveryRequest());
        CheckpointFilesystemRecoveryReport secondRecovery = store.recover(recoveryRequest());

        assertEquals(firstRecovery.selection(), secondRecovery.selection());
        assertEquals(firstRecovery.generationRecords(), secondRecovery.generationRecords());
        assertEquals(firstRecovery.headRecords(), secondRecovery.headRecords());
    }

    @Test
    void worldIdentityAndPlatformManifestMismatchAreVisible() {
        CheckpointFilesystemStore worldStore = store("world_mismatch");
        publish(worldStore, request(1L, 25L, Optional.empty()));

        CheckpointFilesystemRecoveryReport worldRecovery = worldStore.recover(new CheckpointFilesystemRecoveryRequest(
                REQUIRED_OWNERS,
                OTHER_WORLD_ROOT,
                PLATFORM_MANIFEST
        ));

        assertEquals(CheckpointRecoveryOutcome.RECOVERY_BLOCKED, worldRecovery.selection().outcome());
        assertTrue(worldRecovery.artifacts().stream()
                .anyMatch(artifact -> artifact.failure().code() == CheckpointFailureCode.WORLD_IDENTITY_MISMATCH));

        CheckpointFilesystemStore platformStore = store("platform_mismatch");
        publish(platformStore, request(1L, 25L, Optional.empty()));

        CheckpointFilesystemRecoveryReport platformRecovery = platformStore.recover(new CheckpointFilesystemRecoveryRequest(
                REQUIRED_OWNERS,
                WORLD_ROOT,
                OTHER_PLATFORM_MANIFEST
        ));

        assertEquals(CheckpointRecoveryOutcome.RECOVERY_BLOCKED, platformRecovery.selection().outcome());
        assertTrue(platformRecovery.artifacts().stream()
                .anyMatch(artifact -> artifact.failure().code()
                        == CheckpointFailureCode.PLATFORM_DETERMINISM_MANIFEST_MISMATCH));
    }

    @Test
    void unsupportedSchemaIsVisibleDuringFilesystemRecovery() throws IOException {
        CheckpointFilesystemStore store = store("unsupported_schema");
        CheckpointGenerationManifest manifest = publish(store, request(1L, 25L, Optional.empty()));
        Path manifestFile = store.layout().generationManifest(
                store.layout().finalGenerationDirectory(manifest.generationId())
        );
        String altered = Files.readString(manifestFile, StandardCharsets.UTF_8)
                .replace("\"schema_version\":1", "\"schema_version\":99");
        Files.writeString(manifestFile, altered, StandardCharsets.UTF_8);

        CheckpointFilesystemRecoveryReport recovery = store.recover(recoveryRequest());

        assertEquals(CheckpointRecoveryOutcome.RECOVERY_BLOCKED, recovery.selection().outcome());
        assertTrue(recovery.artifacts().stream()
                .anyMatch(artifact -> artifact.failure().code() == CheckpointFailureCode.UNSUPPORTED_SCHEMA));
    }

    @Test
    void rollbackSelectionLoadsOnlyValidCommittedTargetMetadata() {
        CheckpointFilesystemStore store = store("rollback");
        CheckpointGenerationManifest first = publish(store, request(1L, 25L, Optional.empty()));
        publish(store, request(2L, 40L, Optional.of(first)));

        CheckpointRollbackDecision decision = store.selectRollback(
                new CheckpointRollbackRequest(
                        "butchercraft:operator_intent/rollback",
                        first.generationId(),
                        "operator selected previous valid checkpoint",
                        Optional.empty()
                ),
                recoveryRequest()
        );

        assertEquals(CheckpointRecoveryOutcome.EXPLICIT_ROLLBACK_TARGET_ACCEPTED, decision.outcome());
        assertEquals(Optional.of(first.generationId()), decision.selectedGenerationId());
    }

    @Test
    void quarantineReportingDoesNotSilentlyDeleteArtifacts() throws IOException {
        CheckpointFilesystemStore store = store("quarantine");
        Path staging = store.layout().stagingGenerationDirectory(CheckpointGenerationId.of(1L, 25L));
        Path headTemporary = store.layout().headA().resolveSibling(store.layout().headA().getFileName() + ".tmp");
        Files.createDirectories(staging);
        Files.writeString(headTemporary, "partial", StandardCharsets.UTF_8);

        CheckpointFilesystemRecoveryReport recovery = store.recover(recoveryRequest());

        assertTrue(recovery.artifacts().stream()
                .anyMatch(artifact -> artifact.kind() == CheckpointStorageArtifactKind.INCOMPLETE_STAGING_DIRECTORY));
        assertTrue(recovery.artifacts().stream()
                .anyMatch(artifact -> artifact.kind() == CheckpointStorageArtifactKind.HEAD_TEMPORARY_FILE));
        assertTrue(Files.exists(staging));
        assertTrue(Files.exists(headTemporary));
    }

    @Test
    void canonicalSerializationIsStableAndDoesNotUsePlatformLineEndings() throws IOException {
        CheckpointFilesystemStore firstStore = store("canonical_a");
        CheckpointFilesystemStore secondStore = store("canonical_b");
        CheckpointGenerationManifest first = publish(firstStore, request(1L, 25L, Optional.empty()));
        CheckpointGenerationManifest second = publish(secondStore, request(1L, 25L, Optional.empty()));

        byte[] firstBytes = Files.readAllBytes(firstStore.layout().generationManifest(
                firstStore.layout().finalGenerationDirectory(first.generationId())
        ));
        byte[] secondBytes = Files.readAllBytes(secondStore.layout().generationManifest(
                secondStore.layout().finalGenerationDirectory(second.generationId())
        ));

        assertEquals(first.manifestDigest(), second.manifestDigest());
        assertArrayEquals(firstBytes, secondBytes);
        assertFalse(new String(firstBytes, StandardCharsets.UTF_8).contains("\r\n"));
    }

    @Test
    void unavailableStoreRootFailsExplicitly() throws IOException {
        Path fileRoot = temporaryDirectory.resolve("not_a_directory");
        Files.writeString(fileRoot, "not a directory", StandardCharsets.UTF_8);
        CheckpointFilesystemStore store = new CheckpointFilesystemStore(fileRoot);

        CheckpointPublicationReport report = store.publish(request(1L, 25L, Optional.empty()));
        CheckpointFilesystemRecoveryReport recovery = store.recover(recoveryRequest());

        assertEquals(CheckpointPublicationOutcome.FAILED, report.outcome());
        assertTrue(report.diagnostics().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.STORE_ROOT_UNAVAILABLE));
        assertEquals(CheckpointRecoveryOutcome.RECOVERY_BLOCKED, recovery.selection().outcome());
    }

    private CheckpointFilesystemStore store(String name) {
        return new CheckpointFilesystemStore(temporaryDirectory.resolve(name));
    }

    private static CheckpointGenerationManifest publish(
            CheckpointFilesystemStore store,
            CheckpointPublicationRequest request
    ) {
        CheckpointPublicationReport report = store.publish(request);
        assertEquals(CheckpointPublicationOutcome.PUBLISHED, report.outcome());
        return report.generationManifest().orElseThrow();
    }

    private static CheckpointPublicationRequest request(
            long sequence,
            long tick,
            Optional<CheckpointGenerationManifest> predecessor
    ) {
        return requestWithPayloadSuffix(sequence, tick, predecessor, "stable");
    }

    private static CheckpointPublicationRequest requestWithPayloadSuffix(
            long sequence,
            long tick,
            Optional<CheckpointGenerationManifest> predecessor,
            String suffix
    ) {
        CheckpointGenerationId generationId = CheckpointGenerationId.of(sequence, tick);
        return new CheckpointPublicationRequest(
                generationId,
                predecessor.map(CheckpointGenerationManifest::generationId),
                predecessor.map(CheckpointGenerationManifest::manifestDigest),
                tick,
                REQUIRED_OWNERS.stream()
                        .map(owner -> snapshot(generationId, tick, owner, suffix))
                        .toList(),
                REQUIRED_OWNERS,
                PLATFORM_MANIFEST,
                WORLD_ROOT
        );
    }

    private static CheckpointPublicationRequest requestWithPredecessorDigest(
            long sequence,
            long tick,
            CheckpointGenerationManifest predecessor,
            String predecessorManifestDigest
    ) {
        CheckpointGenerationId generationId = CheckpointGenerationId.of(sequence, tick);
        return new CheckpointPublicationRequest(
                generationId,
                Optional.of(predecessor.generationId()),
                Optional.of(predecessorManifestDigest),
                tick,
                REQUIRED_OWNERS.stream()
                        .map(owner -> snapshot(generationId, tick, owner, "stable"))
                        .toList(),
                REQUIRED_OWNERS,
                PLATFORM_MANIFEST,
                WORLD_ROOT
        );
    }

    private static CheckpointOwnerSnapshotPayload snapshot(
            CheckpointGenerationId generationId,
            long tick,
            CheckpointOwnerId owner,
            String suffix
    ) {
        byte[] payload = ("owner=" + owner.value()
                + "\nsequence=" + generationId.committedSequence()
                + "\ntick=" + tick
                + "\nsuffix=" + suffix).getBytes(StandardCharsets.UTF_8);
        String contentDigest = CheckpointFilesystemDigest.sha256(payload);
        String ownerSuffix = owner.value().substring(owner.value().indexOf(':') + 1);
        OwnerSnapshotDescriptor descriptor = new OwnerSnapshotDescriptor(
                owner,
                1,
                "butchercraft:snapshot/" + ownerSuffix + "_" + generationId.committedSequence(),
                contentDigest,
                CheckpointSnapshotParticipation.REQUIRED,
                "butchercraft:configuration/" + ownerSuffix,
                WORLD_ROOT,
                generationId,
                tick,
                generationId.committedSequence()
        );
        return new CheckpointOwnerSnapshotPayload(descriptor, payload, contentDigest);
    }

    private static CheckpointFilesystemRecoveryRequest recoveryRequest() {
        return new CheckpointFilesystemRecoveryRequest(REQUIRED_OWNERS, WORLD_ROOT, PLATFORM_MANIFEST);
    }

    private static void corruptHeadDigest(Path headPath, CheckpointGenerationManifest selected) throws IOException {
        String source = Files.readString(headPath, StandardCharsets.UTF_8);
        Files.writeString(
                headPath,
                source.replace(CheckpointHeadRecord.forManifest(selected).headRecordDigest(), digest("bad-head")),
                StandardCharsets.UTF_8
        );
    }

    private static String digest(String value) {
        return CheckpointCanonicalDigest.create("butchercraft:checkpoint_digest/test")
                .add(value)
                .finish();
    }
}
