package com.butchercraft.world.checkpoint;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointRecoveryFoundationTest {
    private static final CheckpointRecoveryEvaluator EVALUATOR = new CheckpointRecoveryEvaluator();
    private static final String DIGEST_A = digest('a');
    private static final String DIGEST_B = digest('b');
    private static final String DIGEST_C = digest('c');
    private static final WorldIdentityRootReference WORLD_ROOT =
            new WorldIdentityRootReference("butchercraft:world/root", 6, DIGEST_A);
    private static final WorldIdentityRootReference OTHER_WORLD_ROOT =
            new WorldIdentityRootReference("butchercraft:world/other", 6, DIGEST_B);
    private static final PlatformDeterminismManifestReference PLATFORM_MANIFEST =
            new PlatformDeterminismManifestReference("butchercraft:platform_determinism/main", 1, DIGEST_B);
    private static final PlatformDeterminismManifestReference OTHER_PLATFORM_MANIFEST =
            new PlatformDeterminismManifestReference("butchercraft:platform_determinism/other", 1, DIGEST_C);
    private static final List<CheckpointOwnerId> REQUIRED_OWNERS = List.of(
            CheckpointOwnerId.of("butchercraft:scheduler"),
            CheckpointOwnerId.of("butchercraft:simulation"),
            CheckpointOwnerId.of("butchercraft:transactions")
    );

    @Test
    void generationIdentityIsCanonicalVersionedComparableAndStable() {
        CheckpointGenerationId first = CheckpointGenerationId.of(1L, 25L);
        CheckpointGenerationId second = CheckpointGenerationId.of(2L, 30L);

        assertEquals(CheckpointSchema.CURRENT_VERSION, first.schemaVersion());
        assertEquals("butchercraft:checkpoint/00000000000000000001/25", first.canonicalValue());
        assertTrue(first.compareTo(second) < 0);
        assertEquals(first, CheckpointGenerationId.of(1L, 25L));
    }

    @Test
    void unsupportedGenerationIdentitySchemaFailsExplicitly() {
        CheckpointIntegrityResult result = EVALUATOR.validateGenerationId(
                new CheckpointGenerationId(99, 1L, 25L)
        );

        assertFalse(result.successfulResult());
        assertEquals(CheckpointFailureCode.UNSUPPORTED_SCHEMA, result.failures().getFirst().code());
    }

    @Test
    void predecessorMetadataIsExcludedFromCanonicalGenerationIdentity() {
        CheckpointGenerationManifest first = manifest(2L, 40L, Optional.empty());
        CheckpointGenerationManifest withDifferentPredecessor = new CheckpointGenerationManifest(
                CheckpointSchema.CURRENT_VERSION,
                first.generationId(),
                Optional.of(CheckpointGenerationId.of(1L, 10L)),
                Optional.of(DIGEST_C),
                first.authoritativeSimulationTick(),
                first.ownerSnapshots(),
                first.platformDeterminismManifest(),
                first.worldIdentityRoot(),
                CheckpointValidation.zeroDigest()
        ).withCalculatedDigest();

        assertEquals(first.generationId(), withDifferentPredecessor.generationId());
        assertNotEquals(first.manifestDigest(), withDifferentPredecessor.manifestDigest());
    }

    @Test
    void ownerSnapshotsAreOrderedDeterministicallyInManifests() {
        CheckpointGenerationId generationId = CheckpointGenerationId.of(1L, 25L);
        List<OwnerSnapshotDescriptor> reversed = new ArrayList<>(snapshots(generationId, 25L, REQUIRED_OWNERS));
        Collections.reverse(reversed);

        CheckpointGenerationManifest manifest = candidate(generationId, Optional.empty(), Optional.empty(), reversed)
                .toManifest();

        assertEquals(REQUIRED_OWNERS, manifest.ownerSnapshots().stream()
                .map(OwnerSnapshotDescriptor::ownerId)
                .toList());
    }

    @Test
    void duplicateOwnerSnapshotsFailExplicitly() {
        CheckpointGenerationId generationId = CheckpointGenerationId.of(1L, 25L);
        OwnerSnapshotDescriptor first = snapshot(generationId, REQUIRED_OWNERS.getFirst(), 25L, DIGEST_A);
        OwnerSnapshotDescriptor second = snapshot(generationId, REQUIRED_OWNERS.getFirst(), 25L, DIGEST_B);
        CheckpointGenerationManifest manifest = manifestWithSnapshots(
                1L,
                25L,
                Optional.empty(),
                List.of(first, second)
        );
        CheckpointGenerationManifest reversed = manifestWithSnapshots(
                1L,
                25L,
                Optional.empty(),
                List.of(second, first)
        );

        CheckpointIntegrityResult result = EVALUATOR.validateManifest(
                manifest,
                REQUIRED_OWNERS,
                WORLD_ROOT,
                PLATFORM_MANIFEST
        );

        assertTrue(result.failures().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.DUPLICATE_OWNER_SNAPSHOT));
        assertEquals(manifest.manifestDigest(), reversed.manifestDigest());
    }

    @Test
    void ownerIdentityConflictsFailExplicitly() {
        CheckpointGenerationId generationId = CheckpointGenerationId.of(1L, 25L);
        OwnerSnapshotDescriptor first = snapshot(generationId, REQUIRED_OWNERS.getFirst(), 25L, DIGEST_A);
        OwnerSnapshotDescriptor second = new OwnerSnapshotDescriptor(
                REQUIRED_OWNERS.get(1),
                1,
                first.snapshotIdentity(),
                DIGEST_B,
                CheckpointSnapshotParticipation.REQUIRED,
                "butchercraft:configuration/test",
                WORLD_ROOT,
                generationId,
                25L,
                1L
        );
        CheckpointGenerationManifest manifest = manifestWithSnapshots(
                1L,
                25L,
                Optional.empty(),
                List.of(first, second, snapshot(generationId, REQUIRED_OWNERS.get(2), 25L, DIGEST_C))
        );

        CheckpointIntegrityResult result = EVALUATOR.validateManifest(
                manifest,
                REQUIRED_OWNERS,
                WORLD_ROOT,
                PLATFORM_MANIFEST
        );

        assertTrue(result.failures().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.OWNER_IDENTITY_CONFLICT));
    }

    @Test
    void missingRequiredOwnerFailsExplicitly() {
        CheckpointGenerationId generationId = CheckpointGenerationId.of(1L, 25L);
        CheckpointGenerationManifest manifest = manifestWithSnapshots(
                1L,
                25L,
                Optional.empty(),
                snapshots(generationId, 25L, REQUIRED_OWNERS.subList(0, 2))
        );

        CheckpointIntegrityResult result = EVALUATOR.validateManifest(
                manifest,
                REQUIRED_OWNERS,
                WORLD_ROOT,
                PLATFORM_MANIFEST
        );

        assertTrue(result.failures().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.MISSING_REQUIRED_OWNER));
    }

    @Test
    void manifestDigestIsStableAndChangesWhenContentChanges() {
        CheckpointGenerationManifest first = manifest(1L, 25L, Optional.empty());
        CheckpointGenerationManifest identical = manifest(1L, 25L, Optional.empty());
        CheckpointGenerationId generationId = CheckpointGenerationId.of(1L, 25L);
        CheckpointGenerationManifest changed = manifestWithSnapshots(
                1L,
                25L,
                Optional.empty(),
                List.of(
                        snapshot(generationId, REQUIRED_OWNERS.getFirst(), 25L, DIGEST_C),
                        snapshot(generationId, REQUIRED_OWNERS.get(1), 25L, DIGEST_A),
                        snapshot(generationId, REQUIRED_OWNERS.get(2), 25L, DIGEST_B)
                )
        );

        assertEquals(first.manifestDigest(), identical.manifestDigest());
        assertNotEquals(first.manifestDigest(), changed.manifestDigest());
    }

    @Test
    void predecessorMismatchAndBrokenChainsFailExplicitly() {
        CheckpointGenerationManifest predecessor = manifest(1L, 25L, Optional.empty());
        CheckpointGenerationManifest mismatched = new CheckpointGenerationManifest(
                CheckpointSchema.CURRENT_VERSION,
                CheckpointGenerationId.of(2L, 40L),
                Optional.of(predecessor.generationId()),
                Optional.of(DIGEST_C),
                40L,
                snapshots(CheckpointGenerationId.of(2L, 40L), 40L, REQUIRED_OWNERS),
                PLATFORM_MANIFEST,
                WORLD_ROOT,
                CheckpointValidation.zeroDigest()
        ).withCalculatedDigest();
        Map<CheckpointGenerationId, CheckpointGenerationRecord> map = map(
                record(predecessor, CheckpointPublicationState.SUPERSEDED),
                record(mismatched, CheckpointPublicationState.COMMITTED)
        );

        CheckpointIntegrityResult result = EVALUATOR.validateGenerationChain(
                record(mismatched, CheckpointPublicationState.COMMITTED),
                map,
                REQUIRED_OWNERS,
                WORLD_ROOT,
                PLATFORM_MANIFEST
        );

        assertTrue(result.failures().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.PREDECESSOR_MISMATCH));

        CheckpointIntegrityResult broken = EVALUATOR.validateGenerationChain(
                record(mismatched, CheckpointPublicationState.COMMITTED),
                Map.of(mismatched.generationId(), record(mismatched, CheckpointPublicationState.COMMITTED)),
                REQUIRED_OWNERS,
                WORLD_ROOT,
                PLATFORM_MANIFEST
        );
        assertTrue(broken.failures().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.BROKEN_PREDECESSOR_CHAIN));
    }

    @Test
    void invalidSequenceAndTickProgressionFailExplicitly() {
        CheckpointGenerationManifest predecessor = manifest(1L, 100L, Optional.empty());
        CheckpointGenerationManifest current = new CheckpointGenerationManifest(
                CheckpointSchema.CURRENT_VERSION,
                CheckpointGenerationId.of(3L, 90L),
                Optional.of(predecessor.generationId()),
                Optional.of(predecessor.manifestDigest()),
                90L,
                snapshots(CheckpointGenerationId.of(3L, 90L), 90L, REQUIRED_OWNERS),
                PLATFORM_MANIFEST,
                WORLD_ROOT,
                CheckpointValidation.zeroDigest()
        ).withCalculatedDigest();

        CheckpointIntegrityResult result = EVALUATOR.validateGenerationChain(
                record(current, CheckpointPublicationState.COMMITTED),
                map(record(predecessor, CheckpointPublicationState.SUPERSEDED),
                        record(current, CheckpointPublicationState.COMMITTED)),
                REQUIRED_OWNERS,
                WORLD_ROOT,
                PLATFORM_MANIFEST
        );

        assertTrue(result.failures().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.INVALID_SEQUENCE_PROGRESSION));
        assertTrue(result.failures().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.INVALID_SIMULATION_TICK_PROGRESSION));
    }

    @Test
    void worldIdentityAndPlatformManifestMismatchesFailExplicitly() {
        CheckpointGenerationManifest manifest = manifest(1L, 25L, Optional.empty());

        CheckpointIntegrityResult worldMismatch = EVALUATOR.validateManifest(
                manifest,
                REQUIRED_OWNERS,
                OTHER_WORLD_ROOT,
                PLATFORM_MANIFEST
        );
        CheckpointIntegrityResult platformMismatch = EVALUATOR.validateManifest(
                manifest,
                REQUIRED_OWNERS,
                WORLD_ROOT,
                OTHER_PLATFORM_MANIFEST
        );

        assertTrue(worldMismatch.failures().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.WORLD_IDENTITY_MISMATCH));
        assertTrue(platformMismatch.failures().stream()
                .anyMatch(failure -> failure.code()
                        == CheckpointFailureCode.PLATFORM_DETERMINISM_MANIFEST_MISMATCH));
    }

    @Test
    void headValidationRejectsMissingInvalidOrUncommittedGeneration() {
        CheckpointGenerationManifest manifest = manifest(1L, 25L, Optional.empty());
        CheckpointHeadRecord head = CheckpointHeadRecord.forManifest(manifest);

        CheckpointIntegrityResult missing = EVALUATOR.validateHead(head, Map.of());
        CheckpointIntegrityResult uncommitted = EVALUATOR.validateHead(
                head,
                map(record(manifest, CheckpointPublicationState.COMPLETE_CANDIDATE))
        );

        assertTrue(missing.failures().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.HEAD_REFERENCES_INVALID_GENERATION));
        assertTrue(uncommitted.failures().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.HEAD_REFERENCES_UNCOMMITTED_GENERATION));
    }

    @Test
    void headDigestMismatchFailsExplicitly() {
        CheckpointGenerationManifest manifest = manifest(1L, 25L, Optional.empty());
        CheckpointHeadRecord head = new CheckpointHeadRecord(
                CheckpointSchema.CURRENT_VERSION,
                1L,
                manifest.generationId(),
                manifest.manifestDigest(),
                Optional.empty(),
                Optional.empty(),
                DIGEST_C
        );

        CheckpointIntegrityResult result = EVALUATOR.validateHead(
                head,
                map(record(manifest, CheckpointPublicationState.COMMITTED))
        );

        assertTrue(result.failures().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.HEAD_DIGEST_MISMATCH));
    }

    @Test
    void selectorChoosesNewestValidCommittedGeneration() {
        CheckpointGenerationManifest first = manifest(1L, 25L, Optional.empty());
        CheckpointGenerationManifest second = manifest(2L, 40L, Optional.of(first));

        CheckpointRecoverySelection selection = EVALUATOR.selectLatestValidCommitted(request(
                List.of(record(first, CheckpointPublicationState.SUPERSEDED),
                        record(second, CheckpointPublicationState.COMMITTED)),
                List.of(CheckpointHeadRecord.forManifest(first), CheckpointHeadRecord.forManifest(second))
        ));

        assertEquals(CheckpointRecoveryOutcome.LATEST_VALID_GENERATION_SELECTED, selection.outcome());
        assertEquals(Optional.of(second.generationId()), selection.selectedGenerationId());
    }

    @Test
    void selectorFallsBackToOlderValidCommittedGenerationWithDiagnostics() {
        CheckpointGenerationManifest first = manifest(1L, 25L, Optional.empty());
        CheckpointGenerationManifest second = manifest(2L, 40L, Optional.of(first));
        CheckpointGenerationManifest corruptSecond = new CheckpointGenerationManifest(
                second.schemaVersion(),
                second.generationId(),
                second.predecessorGenerationId(),
                second.predecessorManifestDigest(),
                second.authoritativeSimulationTick(),
                second.ownerSnapshots(),
                second.platformDeterminismManifest(),
                second.worldIdentityRoot(),
                DIGEST_C
        );

        CheckpointRecoverySelection selection = EVALUATOR.selectLatestValidCommitted(request(
                List.of(record(first, CheckpointPublicationState.SUPERSEDED),
                        record(corruptSecond, CheckpointPublicationState.COMMITTED)),
                List.of(CheckpointHeadRecord.forManifest(first), CheckpointHeadRecord.forManifest(corruptSecond))
        ));

        assertEquals(CheckpointRecoveryOutcome.OLDER_VALID_GENERATION_SELECTED_AUTOMATICALLY, selection.outcome());
        assertEquals(Optional.of(first.generationId()), selection.selectedGenerationId());
        assertTrue(selection.diagnostics().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.MANIFEST_DIGEST_MISMATCH));
    }

    @Test
    void recoveryIsBlockedWhenNoValidCommittedGenerationExists() {
        CheckpointGenerationManifest manifest = manifest(1L, 25L, Optional.empty());

        CheckpointRecoverySelection selection = EVALUATOR.selectLatestValidCommitted(request(
                List.of(record(manifest, CheckpointPublicationState.COMPLETE_CANDIDATE)),
                List.of(CheckpointHeadRecord.forManifest(manifest))
        ));

        assertEquals(CheckpointRecoveryOutcome.RECOVERY_BLOCKED, selection.outcome());
        assertTrue(selection.diagnostics().stream()
                .anyMatch(failure -> failure.code()
                        == CheckpointFailureCode.HEAD_REFERENCES_UNCOMMITTED_GENERATION));
    }

    @Test
    void conflictingGenerationRecordsBlockRecoveryExplicitly() {
        CheckpointGenerationId generationId = CheckpointGenerationId.of(1L, 25L);
        CheckpointGenerationManifest first = manifest(1L, 25L, Optional.empty());
        CheckpointGenerationManifest conflicting = manifestWithSnapshots(
                1L,
                25L,
                Optional.empty(),
                List.of(
                        snapshot(generationId, REQUIRED_OWNERS.getFirst(), 25L, DIGEST_B),
                        snapshot(generationId, REQUIRED_OWNERS.get(1), 25L, DIGEST_B),
                        snapshot(generationId, REQUIRED_OWNERS.get(2), 25L, DIGEST_C)
                )
        );

        CheckpointRecoverySelection selection = EVALUATOR.selectLatestValidCommitted(request(
                List.of(
                        record(first, CheckpointPublicationState.COMMITTED),
                        record(conflicting, CheckpointPublicationState.COMMITTED)
                ),
                List.of(CheckpointHeadRecord.forManifest(first))
        ));

        assertEquals(CheckpointRecoveryOutcome.RECOVERY_BLOCKED, selection.outcome());
        assertTrue(selection.diagnostics().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.DUPLICATE_GENERATION_RECORD));
    }

    @Test
    void identicalInputsAndInputCollectionOrderDoNotAffectSelection() {
        CheckpointGenerationManifest first = manifest(1L, 25L, Optional.empty());
        CheckpointGenerationManifest second = manifest(2L, 40L, Optional.of(first));
        List<CheckpointGenerationRecord> generations = List.of(
                record(second, CheckpointPublicationState.COMMITTED),
                record(first, CheckpointPublicationState.SUPERSEDED)
        );
        List<CheckpointHeadRecord> heads = List.of(
                CheckpointHeadRecord.forManifest(second),
                CheckpointHeadRecord.forManifest(first)
        );

        CheckpointRecoverySelection ordered = EVALUATOR.selectLatestValidCommitted(request(generations, heads));
        List<CheckpointGenerationRecord> reversedGenerations = new ArrayList<>(generations);
        List<CheckpointHeadRecord> reversedHeads = new ArrayList<>(heads);
        Collections.reverse(reversedGenerations);
        Collections.reverse(reversedHeads);
        CheckpointRecoverySelection reversed = EVALUATOR.selectLatestValidCommitted(
                request(reversedGenerations, reversedHeads)
        );

        assertEquals(ordered, EVALUATOR.selectLatestValidCommitted(request(generations, heads)));
        assertEquals(ordered, reversed);
    }

    @Test
    void explicitRollbackToOlderValidCommittedGenerationIsAcceptedWithoutRewritingHistory() {
        CheckpointGenerationManifest first = manifest(1L, 25L, Optional.empty());
        CheckpointGenerationManifest second = manifest(2L, 40L, Optional.of(first));

        CheckpointRollbackDecision decision = EVALUATOR.selectRollback(
                new CheckpointRollbackRequest(
                        "butchercraft:operator_intent/test",
                        first.generationId(),
                        "operator requested known-good baseline",
                        Optional.of("butchercraft:audit/rollback_1")
                ),
                request(List.of(record(first, CheckpointPublicationState.SUPERSEDED),
                                record(second, CheckpointPublicationState.COMMITTED)),
                        List.of(CheckpointHeadRecord.forManifest(first), CheckpointHeadRecord.forManifest(second)))
        );

        assertEquals(CheckpointRecoveryOutcome.EXPLICIT_ROLLBACK_TARGET_ACCEPTED, decision.outcome());
        assertEquals(Optional.of(first.generationId()), decision.selectedGenerationId());
        assertTrue(decision.newerHistoryPreserved());
        assertTrue(decision.laterRuntimeMustPublishRecoveryHistory());
    }

    @Test
    void rollbackRejectsMissingIntentInvalidOrUncommittedTargets() {
        CheckpointGenerationManifest manifest = manifest(1L, 25L, Optional.empty());

        CheckpointRollbackDecision missingIntent = EVALUATOR.selectRollback(
                new CheckpointRollbackRequest("", manifest.generationId(), "", Optional.empty()),
                request(List.of(record(manifest, CheckpointPublicationState.COMMITTED)),
                        List.of(CheckpointHeadRecord.forManifest(manifest)))
        );
        CheckpointRollbackDecision uncommitted = EVALUATOR.selectRollback(
                new CheckpointRollbackRequest(
                        "butchercraft:operator_intent/test",
                        manifest.generationId(),
                        "test",
                        Optional.empty()
                ),
                request(List.of(record(manifest, CheckpointPublicationState.COMPLETE_CANDIDATE)),
                        List.of(CheckpointHeadRecord.forManifest(manifest)))
        );

        assertEquals(CheckpointRecoveryOutcome.INVALID_ROLLBACK_REQUEST, missingIntent.outcome());
        assertTrue(missingIntent.failures().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.ROLLBACK_OPERATOR_INTENT_MISSING));
        assertEquals(CheckpointRecoveryOutcome.INVALID_ROLLBACK_REQUEST, uncommitted.outcome());
        assertTrue(uncommitted.failures().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.ROLLBACK_TARGET_UNCOMMITTED));
    }

    @Test
    void unsupportedManifestSchemaFailsExplicitly() {
        CheckpointGenerationManifest manifest = new CheckpointGenerationManifest(
                99,
                new CheckpointGenerationId(99, 1L, 25L),
                Optional.empty(),
                Optional.empty(),
                25L,
                snapshots(new CheckpointGenerationId(99, 1L, 25L), 25L, REQUIRED_OWNERS),
                PLATFORM_MANIFEST,
                WORLD_ROOT,
                CheckpointValidation.zeroDigest()
        ).withCalculatedDigest();

        CheckpointIntegrityResult result = EVALUATOR.validateManifest(
                manifest,
                REQUIRED_OWNERS,
                WORLD_ROOT,
                PLATFORM_MANIFEST
        );

        assertTrue(result.failures().stream()
                .anyMatch(failure -> failure.code() == CheckpointFailureCode.UNSUPPORTED_SCHEMA));
    }

    private static CheckpointRecoverySelectionRequest request(
            List<CheckpointGenerationRecord> generations,
            List<CheckpointHeadRecord> heads
    ) {
        return new CheckpointRecoverySelectionRequest(
                generations,
                heads,
                REQUIRED_OWNERS,
                WORLD_ROOT,
                PLATFORM_MANIFEST
        );
    }

    private static CheckpointGenerationManifest manifest(
            long sequence,
            long tick,
            Optional<CheckpointGenerationManifest> predecessor
    ) {
        CheckpointGenerationId generationId = CheckpointGenerationId.of(sequence, tick);
        return candidate(
                generationId,
                predecessor.map(CheckpointGenerationManifest::generationId),
                predecessor.map(CheckpointGenerationManifest::manifestDigest),
                snapshots(generationId, tick, REQUIRED_OWNERS)
        ).toManifest();
    }

    private static CheckpointGenerationManifest manifestWithSnapshots(
            long sequence,
            long tick,
            Optional<CheckpointGenerationManifest> predecessor,
            List<OwnerSnapshotDescriptor> snapshots
    ) {
        CheckpointGenerationId generationId = CheckpointGenerationId.of(sequence, tick);
        return candidate(
                generationId,
                predecessor.map(CheckpointGenerationManifest::generationId),
                predecessor.map(CheckpointGenerationManifest::manifestDigest),
                snapshots
        ).toManifest();
    }

    private static CheckpointGenerationCandidate candidate(
            CheckpointGenerationId generationId,
            Optional<CheckpointGenerationId> predecessorId,
            Optional<String> predecessorManifestDigest,
            List<OwnerSnapshotDescriptor> snapshots
    ) {
        return new CheckpointGenerationCandidate(
                generationId,
                predecessorId,
                predecessorManifestDigest,
                generationId.authoritativeSimulationTick(),
                snapshots,
                PLATFORM_MANIFEST,
                WORLD_ROOT,
                CheckpointPublicationState.COMPLETE_CANDIDATE
        );
    }

    private static List<OwnerSnapshotDescriptor> snapshots(
            CheckpointGenerationId generationId,
            long tick,
            List<CheckpointOwnerId> owners
    ) {
        List<String> digests = List.of(DIGEST_A, DIGEST_B, DIGEST_C);
        List<OwnerSnapshotDescriptor> snapshots = new ArrayList<>();
        for (int index = 0; index < owners.size(); index++) {
            snapshots.add(snapshot(generationId, owners.get(index), tick, digests.get(index % digests.size())));
        }
        return snapshots;
    }

    private static OwnerSnapshotDescriptor snapshot(
            CheckpointGenerationId generationId,
            CheckpointOwnerId owner,
            long tick,
            String digest
    ) {
        String ownerSuffix = owner.value().substring(owner.value().indexOf(':') + 1);
        return new OwnerSnapshotDescriptor(
                owner,
                1,
                "butchercraft:snapshot/" + ownerSuffix + "_" + generationId.committedSequence(),
                digest,
                CheckpointSnapshotParticipation.REQUIRED,
                "butchercraft:configuration/" + ownerSuffix,
                WORLD_ROOT,
                generationId,
                tick,
                generationId.committedSequence()
        );
    }

    private static CheckpointGenerationRecord record(
            CheckpointGenerationManifest manifest,
            CheckpointPublicationState state
    ) {
        return new CheckpointGenerationRecord(manifest, state);
    }

    private static Map<CheckpointGenerationId, CheckpointGenerationRecord> map(
            CheckpointGenerationRecord... records
    ) {
        Map<CheckpointGenerationId, CheckpointGenerationRecord> result = new TreeMap<>();
        for (CheckpointGenerationRecord record : records) {
            result.put(record.generationId(), record);
        }
        return result;
    }

    private static String digest(char value) {
        return "sha256:" + String.valueOf(value).repeat(64);
    }
}
