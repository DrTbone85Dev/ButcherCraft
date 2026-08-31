package com.butchercraft.world.checkpoint;

import com.butchercraft.development.checkpoint.LegacySplitRecoveryAdminTool;
import com.butchercraft.development.checkpoint.LegacySplitRecoveryFormatter;
import com.butchercraft.test.TestProjectPaths;
import com.butchercraft.workstation.operation.checkpoint.WorkstationLegacyRecoveryOwnerPreparer;
import com.butchercraft.world.execution.checkpoint.ExecutionLegacyRecoveryOwnerPreparer;
import com.butchercraft.world.planning.checkpoint.PlanningLegacyRecoveryOwnerPreparer;
import com.butchercraft.world.simulation.checkpoint.SimulationClockLegacyRecoveryOwnerPreparer;
import com.butchercraft.world.simulation.scheduler.SchedulerRecoveryDiscontinuity;
import com.butchercraft.world.simulation.scheduler.checkpoint.SchedulerLegacyRecoveryOwnerPreparer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySplitRecoveryPublicationServiceTest {
    private static final SplitSnapshotRecoveryAnalyzer ANALYZER = new SplitSnapshotRecoveryAnalyzer();

    @TempDir
    private Path temporaryDirectory;

    @Test
    void exactAuthorizedFixturePublishesOneCoherentGenerationWithoutConsequences() throws IOException {
        Fixture fixture = fixture("success");
        LegacySplitRecoveryAdminTool tool = new LegacySplitRecoveryAdminTool();
        long filesBeforePreview = fileCount(fixture.targetWorldRoot());

        LegacySplitRecoveryDryRunPreview preview = tool.analyze(fixture.previewRequest());

        assertTrue(preview.publicationEligible());
        assertEquals(39872L, preview.plan().authoritativeClockTick());
        assertEquals(39084L, preview.plan().schedulerLastNormallyFinalizedTick());
        assertEquals(9, preview.plan().historicalAcknowledgements().size());
        assertEquals(0L, preview.plan().ordinaryWorkProofs().stream()
                .filter(value -> value.eligibility()
                        == com.butchercraft.world.simulation.scheduler.OrdinaryWorkReconstructionProof.Eligibility
                        .ORDINARY_WORK_RECONSTRUCTABLE)
                .count());
        assertEquals(1, preview.plan().preservedAuthorizedWork().size());
        assertTrue(preview.mutationGate().wholeWorldConsequentialMutationBlocked());
        assertEquals(LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS, preview.participantOwners());
        assertEquals(filesBeforePreview, fileCount(fixture.targetWorldRoot()));
        assertTrue(LegacySplitRecoveryFormatter.previewLines(preview).stream()
                .anyMatch(line -> line.equals("Recovery dry run: no mutation performed")));

        RecoveryOperatorAuthorization authorization = tool.authorizeExact(
                preview,
                RecoveryOperatorAuthorization.Disposition.AUTHORIZE_RECOVERY_WITH_AUTHORITY_BLOCKS,
                SplitSnapshotRecoveryAnalyzerTest.operator(RecoveryOperatorAuthority.ADMINISTRATOR),
                Optional.of("2026-08-21T12:00:00Z")
        );
        LegacySplitRecoveryPublicationReport report = tool.publishExact(fixture.publicationRequest(authorization));

        assertEquals(LegacySplitRecoveryPublicationReport.Outcome.COMMITTED, report.outcome(),
                report.failures().toString());
        LegacySplitRecoveryResult result = report.recoveryResult().orElseThrow();
        assertEquals(RecoveryOperatorAuthorization.Disposition.AUTHORIZE_RECOVERY_WITH_AUTHORITY_BLOCKS,
                result.authorizedDisposition());
        assertEquals(9, result.publishedAcknowledgements().size());
        assertEquals(39085L, result.publishedDiscontinuity().inclusiveStartTick());
        assertEquals(39872L, result.publishedDiscontinuity().inclusiveEndTick());
        assertEquals(39873L, result.publishedDiscontinuity().nextAdmissionTick());
        assertEquals(1, result.preservedAuthorizedWork().size());
        assertEquals(10L, result.preservedAuthorizedWork().getFirst().childSequence());
        assertEquals(PreservedAuthorizedWork.POLICY_B,
                result.preservedAuthorizedWork().getFirst().restartPolicyIdentity());
        assertEquals(1, result.authorityBlocks().size());
        assertTrue(result.mutationGate().wholeWorldConsequentialMutationBlocked());
        assertFalse(result.mutationGate().permitsConsequentialMutation(LegacySplitRecoveryParticipants.INVENTORY));
        assertTrue(result.mutationGate().readOnlyDiagnosticsPermitted());
        assertEquals(LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS.size(), result.ownerSnapshots().size());
        assertTrue(Files.isRegularFile(fixture.storage().resultPath(result.recoveryIdentity())));

        CheckpointRecoveredGeneration generation = fixture.checkpointStore()
                .loadSelectedGeneration(fixture.recoveryRequest())
                .recoveredGeneration()
                .orElseThrow();
        assertEquals(result.recoveryGenerationId(), generation.manifest().generationId());
        Map<CheckpointOwnerId, LegacyRecoveryOwnerSnapshotDocument> ownerDocuments = ownerDocuments(generation);
        assertEquals("39872", fields(ownerDocuments.get(CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER))
                .get("authoritative_clock_tick"));
        Map<String, String> scheduler = fields(ownerDocuments.get(CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER));
        assertEquals("9", scheduler.get("acknowledgement_count"));
        assertEquals("0", scheduler.get("handler_invocation_count"));
        assertEquals("0", scheduler.get("synthetic_tick_count"));
        assertEquals("39873", scheduler.get("next_normal_admission_tick"));
        Map<String, String> execution = fields(ownerDocuments.get(LegacySplitRecoveryParticipants.EXECUTION));
        assertEquals("AUTHORIZED", execution.get("preserved_child.0.operation_state"));
        assertEquals("false", execution.get("preserved_child.0.scheduler_work_created"));
        assertEquals("SUSPENDED_RESTART_REQUIRED",
                execution.get("preserved_child.0.machine_run_lifecycle"));
        Map<String, String> workstation = fields(ownerDocuments.get(LegacySplitRecoveryParticipants.WORKSTATION));
        assertEquals("RESTART_REQUIRED", workstation.get("machine.0.operating_state"));
        assertEquals("0", workstation.get("inventory_effect_count"));
        Map<String, String> planning = fields(ownerDocuments.get(LegacySplitRecoveryParticipants.PLANNING));
        assertEquals("true", planning.get("whole_world_mutation_blocked"));
        assertEquals("0", planning.get("planning_replay_count"));

        assertEquals(fixture.liveInventoryBefore(), Files.readString(fixture.liveInventoryPath()));
        assertEquals(fixture.liveWorkstationBefore(), Files.readString(fixture.liveWorkstationPath()));
        assertEquals(fixture.liveMaterialHandlingBefore(), Files.readString(fixture.liveMaterialHandlingPath()));
        assertFalse(execution.containsKey("preserved_child.0.replacement_child_identity"));
        assertTrue(LegacySplitRecoveryFormatter.publicationLines(report).stream()
                .anyMatch(line -> line.startsWith("Recovery Result: ")));
        assertEquals(2, fixture.source().reloadCount());
    }

    @Test
    void duplicatePublicationAndRestartObserveSameResultWithoutArtifactGrowth() throws IOException {
        Fixture fixture = fixture("idempotency");
        LegacySplitRecoveryAdminTool firstTool = new LegacySplitRecoveryAdminTool();
        LegacySplitRecoveryDryRunPreview preview = firstTool.analyze(fixture.previewRequest());
        RecoveryOperatorAuthorization authorization = authorize(preview);
        LegacySplitRecoveryPublicationReport first = firstTool.publishExact(fixture.publicationRequest(authorization));
        long artifactsAfterFirst = fileCount(fixture.checkpointRoot());

        LegacySplitRecoveryPublicationReport duplicate = firstTool.publishExact(
                fixture.publicationRequest(authorization)
        );
        LegacySplitRecoveryAdminTool restartedTool = new LegacySplitRecoveryAdminTool();
        LegacySplitRecoveryPublicationReport afterRestart = restartedTool.publishExact(
                fixture.publicationRequest(authorization)
        );

        assertTrue(first.successful(), first.failures().toString());
        assertEquals(LegacySplitRecoveryPublicationReport.Outcome.EXISTING_RESULT_OBSERVED, duplicate.outcome());
        assertEquals(LegacySplitRecoveryPublicationReport.Outcome.EXISTING_RESULT_OBSERVED,
                afterRestart.outcome());
        assertEquals(first.recoveryResult().orElseThrow().resultIdentity(),
                duplicate.recoveryResult().orElseThrow().resultIdentity());
        assertEquals(first.recoveryResult().orElseThrow().resultIdentity(),
                afterRestart.recoveryResult().orElseThrow().resultIdentity());
        assertEquals(artifactsAfterFirst, fileCount(fixture.checkpointRoot()));
        assertEquals(1L, Files.list(fixture.checkpointStore().layout().generationsDirectory()).count());
        assertEquals(2L, Files.list(fixture.storage().publicationDirectory(preview.plan().recoveryIdentity()))
                .filter(Files::isRegularFile)
                .count());
        LegacySplitRecoveryStatusSnapshot status = restartedTool.status(
                fixture.source(),
                fixture.checkpointRoot()
        );
        assertEquals(LegacySplitRecoveryStatusSnapshot.State.RECOVERY_COMMITTED, status.state());
        assertFalse(status.worldFullyMutationUnblocked());
        assertEquals(1, status.remainingAuthorityBlocks().size());
    }

    @Test
    void staleChangedSourceWrongWorldAndChangedBlockSetRejectExactAuthorization() throws IOException {
        Fixture fixture = fixture("stale");
        LegacySplitRecoveryAdminTool tool = new LegacySplitRecoveryAdminTool();
        LegacySplitRecoveryDryRunPreview preview = tool.analyze(fixture.previewRequest());
        RecoveryOperatorAuthorization authorization = authorize(preview);

        fixture.source().replaceInput(changedSourceDigest(fixture.source().input()));
        LegacySplitRecoveryPublicationReport staleSource = tool.publishExact(
                fixture.publicationRequest(authorization)
        );
        assertEquals(LegacySplitRecoveryPublicationReport.Outcome.AUTHORIZATION_REJECTED, staleSource.outcome());
        assertTrue(staleSource.failures().stream().anyMatch(value ->
                value.code() == LegacySplitRecoveryPublicationFailure.Code.AUTHORIZATION_STALE));
        assertFalse(Files.exists(fixture.checkpointRoot()));

        Fixture worldFixture = fixture("wrong_world");
        LegacySplitRecoveryDryRunPreview worldPreview = tool.analyze(worldFixture.previewRequest());
        RecoveryOperatorAuthorization worldAuthorization = authorize(worldPreview);
        Fixture otherWorld = fixture("other_world");
        otherWorld.source().replaceInput(withWorld(otherWorld.source().input()));
        LegacySplitRecoveryPublicationReport wrongWorld = tool.publishExact(
                otherWorld.publicationRequest(worldAuthorization)
        );
        assertEquals(LegacySplitRecoveryPublicationReport.Outcome.AUTHORIZATION_REJECTED, wrongWorld.outcome());

        Fixture blockFixture = fixture("changed_blocks");
        LegacySplitRecoveryDryRunPreview blockPreview = tool.analyze(blockFixture.previewRequest());
        RecoveryOperatorAuthorization blockAuthorization = authorize(blockPreview);
        blockFixture.source().replaceInput(withoutPlanningBlock(blockFixture.source().input()));
        LegacySplitRecoveryPublicationReport changedBlocks = tool.publishExact(
                blockFixture.publicationRequest(blockAuthorization)
        );
        assertEquals(LegacySplitRecoveryPublicationReport.Outcome.AUTHORIZATION_REJECTED,
                changedBlocks.outcome());
    }

    @Test
    void wrongRecoveryIdentityAndAnalysisDigestRejectExactAuthorization() throws IOException {
        Fixture identityFixture = fixture("wrong_recovery_identity");
        LegacySplitRecoveryDryRunPreview identityPreview = new LegacySplitRecoveryPublicationService()
                .preview(identityFixture.previewRequest());
        LegacySplitRecoveryIdentity wrongIdentity = new LegacySplitRecoveryIdentity(
                "butchercraft:legacy_split_recovery/v1/" + "a".repeat(64)
        );
        SplitSnapshotRecoveryPlan wrongIdentityPlan = copyPlanIdentityAndDigest(
                identityPreview.plan(),
                wrongIdentity,
                identityPreview.plan().analysisDigest()
        );
        RecoveryOperatorAuthorization wrongIdentityAuthorization = RecoveryOperatorAuthorization.authorize(
                wrongIdentityPlan,
                RecoveryOperatorAuthorization.Disposition.AUTHORIZE_RECOVERY_WITH_AUTHORITY_BLOCKS,
                SplitSnapshotRecoveryAnalyzerTest.operator(RecoveryOperatorAuthority.OPERATOR),
                Optional.empty()
        );
        LegacySplitRecoveryPublicationReport wrongIdentityReport = new LegacySplitRecoveryPublicationService()
                .publish(identityFixture.publicationRequest(wrongIdentityAuthorization));
        assertEquals(LegacySplitRecoveryPublicationReport.Outcome.AUTHORIZATION_REJECTED,
                wrongIdentityReport.outcome());
        assertFalse(Files.exists(identityFixture.checkpointRoot()));

        Fixture digestFixture = fixture("wrong_analysis_digest");
        LegacySplitRecoveryDryRunPreview digestPreview = new LegacySplitRecoveryPublicationService()
                .preview(digestFixture.previewRequest());
        SplitSnapshotRecoveryPlan wrongDigestPlan = copyPlanIdentityAndDigest(
                digestPreview.plan(),
                digestPreview.plan().recoveryIdentity(),
                SplitSnapshotRecoveryAnalyzerTest.digest('9')
        );
        RecoveryOperatorAuthorization wrongDigestAuthorization = RecoveryOperatorAuthorization.authorize(
                wrongDigestPlan,
                RecoveryOperatorAuthorization.Disposition.AUTHORIZE_RECOVERY_WITH_AUTHORITY_BLOCKS,
                SplitSnapshotRecoveryAnalyzerTest.operator(RecoveryOperatorAuthority.OPERATOR),
                Optional.empty()
        );
        LegacySplitRecoveryPublicationReport wrongDigestReport = new LegacySplitRecoveryPublicationService()
                .publish(digestFixture.publicationRequest(wrongDigestAuthorization));
        assertEquals(LegacySplitRecoveryPublicationReport.Outcome.AUTHORIZATION_REJECTED,
                wrongDigestReport.outcome());
        assertFalse(Files.exists(digestFixture.checkpointRoot()));
    }

    @Test
    void everyFaultBoundaryRetriesToOneResultAndNeverReplaysHistory() throws IOException {
        for (LegacySplitRecoveryPublicationPhase phase : EnumSet.allOf(LegacySplitRecoveryPublicationPhase.class)) {
            Fixture fixture = fixture("fault_" + phase.name().toLowerCase());
            LegacySplitRecoveryDryRunPreview preview = new LegacySplitRecoveryPublicationService()
                    .preview(fixture.previewRequest());
            RecoveryOperatorAuthorization authorization = authorize(preview);
            AtomicBoolean injected = new AtomicBoolean();
            LegacySplitRecoveryPublicationService interruptedService = new LegacySplitRecoveryPublicationService(
                    reached -> {
                        if (reached == phase && injected.compareAndSet(false, true)) {
                            throw new IOException("simulated " + phase);
                        }
                    }
            );

            LegacySplitRecoveryPublicationReport interrupted = interruptedService.publish(
                    fixture.publicationRequest(authorization)
            );
            LegacySplitRecoveryPublicationReport recovered = new LegacySplitRecoveryPublicationService().publish(
                    fixture.publicationRequest(authorization)
            );

            assertFalse(interrupted.successful(), phase.name());
            assertTrue(recovered.successful(), phase.name() + " " + recovered.failures());
            LegacySplitRecoveryResult result = recovered.recoveryResult().orElseThrow();
            assertEquals(9, result.publishedAcknowledgements().size(), phase.name());
            assertEquals(1, result.preservedAuthorizedWork().size(), phase.name());
            assertEquals(1L, Files.list(fixture.checkpointStore().layout().generationsDirectory()).count(),
                    phase.name());
            assertEquals(fixture.liveInventoryBefore(), Files.readString(fixture.liveInventoryPath()), phase.name());
            assertEquals(fixture.liveWorkstationBefore(), Files.readString(fixture.liveWorkstationPath()), phase.name());
        }
    }

    @Test
    void preHeadInterruptionPreservesPreviousCheckpointAndRetryCommitsItsSuccessor() throws IOException {
        Fixture fixture = fixture("previous_generation");
        CheckpointGenerationManifest previous = publishPreviousCheckpoint(fixture);
        Path previousDirectory = fixture.checkpointStore().layout()
                .finalGenerationDirectory(previous.generationId());
        String previousDirectoryDigest = directoryDigest(previousDirectory);
        LegacySplitRecoveryDryRunPreview preview = new LegacySplitRecoveryPublicationService()
                .preview(fixture.previewRequest());
        assertEquals(2L, preview.intendedGenerationId().orElseThrow().committedSequence());
        assertEquals(previous.generationId(), preview.predecessorGenerationId().orElseThrow());
        RecoveryOperatorAuthorization authorization = authorize(preview);

        LegacySplitRecoveryPublicationService interruptedService = new LegacySplitRecoveryPublicationService(
                phase -> {
                    if (phase == LegacySplitRecoveryPublicationPhase.BEFORE_INACTIVE_HEAD_UPDATE) {
                        throw new IOException("simulated pre-head interruption");
                    }
                }
        );
        LegacySplitRecoveryPublicationReport interrupted = interruptedService.publish(
                fixture.publicationRequest(authorization)
        );

        assertFalse(interrupted.successful());
        CheckpointFilesystemRecoveryReport afterInterruption = fixture.checkpointStore()
                .inspectReadOnly(fixture.recoveryRequest());
        assertEquals(previous.generationId(), afterInterruption.selection().selectedGenerationId().orElseThrow());
        assertEquals(previous.manifestDigest(), afterInterruption.selection().selectedManifestDigest().orElseThrow());
        assertEquals(previousDirectoryDigest, directoryDigest(previousDirectory));

        LegacySplitRecoveryPublicationReport recovered = new LegacySplitRecoveryPublicationService()
                .publish(fixture.publicationRequest(authorization));
        assertTrue(recovered.successful(), recovered.failures().toString());
        LegacySplitRecoveryResult result = recovered.recoveryResult().orElseThrow();
        assertEquals(2L, result.recoveryGenerationId().committedSequence());
        CheckpointRecoveredGeneration selected = fixture.checkpointStore()
                .loadSelectedGeneration(fixture.recoveryRequest())
                .recoveredGeneration()
                .orElseThrow();
        assertEquals(result.recoveryGenerationId(), selected.manifest().generationId());
        assertEquals(previous.generationId(), selected.manifest().predecessorGenerationId().orElseThrow());
        assertEquals(previous.manifestDigest(), selected.manifest().predecessorManifestDigest().orElseThrow());
        assertEquals(previousDirectoryDigest, directoryDigest(previousDirectory));
    }

    @Test
    void missingOrDuplicateParticipantCannotCommit() throws IOException {
        Fixture missingFixture = fixture("missing_owner");
        LegacySplitRecoveryDryRunPreview preview = new LegacySplitRecoveryPublicationService()
                .preview(missingFixture.previewRequest());
        RecoveryOperatorAuthorization authorization = authorize(preview);
        List<LegacySplitRecoveryOwnerPreparer> missing = new ArrayList<>(missingFixture.preparers());
        missing.removeIf(value -> value.ownerId().equals(LegacySplitRecoveryParticipants.INVENTORY));
        LegacySplitRecoveryPublicationReport missingReport = new LegacySplitRecoveryPublicationService().publish(
                missingFixture.publicationRequest(authorization, missing)
        );
        assertFalse(missingReport.successful());
        assertTrue(missingReport.failures().stream().anyMatch(value ->
                value.code() == LegacySplitRecoveryPublicationFailure.Code.OWNER_PREPARER_MISSING));
        assertFalse(Files.exists(missingFixture.checkpointStore().layout().headA()));

        Fixture duplicateFixture = fixture("duplicate_owner");
        LegacySplitRecoveryDryRunPreview duplicatePreview = new LegacySplitRecoveryPublicationService()
                .preview(duplicateFixture.previewRequest());
        RecoveryOperatorAuthorization duplicateAuthorization = authorize(duplicatePreview);
        List<LegacySplitRecoveryOwnerPreparer> duplicate = new ArrayList<>(duplicateFixture.preparers());
        duplicate.add(new ExactUnchangedLegacyRecoveryOwnerPreparer(LegacySplitRecoveryParticipants.INVENTORY));
        LegacySplitRecoveryPublicationReport duplicateReport = new LegacySplitRecoveryPublicationService().publish(
                duplicateFixture.publicationRequest(duplicateAuthorization, duplicate)
        );
        assertFalse(duplicateReport.successful());
        assertTrue(duplicateReport.failures().stream().anyMatch(value ->
                value.code() == LegacySplitRecoveryPublicationFailure.Code.OWNER_PREPARER_DUPLICATE));
        assertFalse(Files.exists(duplicateFixture.checkpointStore().layout().headA()));
    }

    @Test
    void ordinaryPlayerCannotAuthorizeAndProtectedWorldsCannotPublish() throws IOException {
        Fixture fixture = fixture("ordinary_player");
        LegacySplitRecoveryAdminTool tool = new LegacySplitRecoveryAdminTool();
        LegacySplitRecoveryDryRunPreview preview = tool.analyze(fixture.previewRequest());
        assertThrows(SecurityException.class, () -> tool.authorizeExact(
                preview,
                RecoveryOperatorAuthorization.Disposition.AUTHORIZE_RECOVERY_WITH_AUTHORITY_BLOCKS,
                SplitSnapshotRecoveryAnalyzerTest.operator(RecoveryOperatorAuthority.ORDINARY_PLAYER),
                Optional.empty()
        ));

        Path protectedOriginal = TestProjectPaths.projectDir().resolve("run/saves/New World (24)")
                .toAbsolutePath().normalize();
        Path protectedEvidence = TestProjectPaths.projectDir()
                .resolve("run/compatibility-evidence/New World (24)-20260819-223840")
                .toAbsolutePath().normalize();
        long originalFiles = Files.exists(protectedOriginal) ? fileCount(protectedOriginal) : 0L;
        long evidenceFiles = Files.exists(protectedEvidence) ? fileCount(protectedEvidence) : 0L;
        fixture.source().replaceRoots(List.of(protectedOriginal));
        LegacySplitRecoveryPublicationRequest protectedRequest = new LegacySplitRecoveryPublicationRequest(
                fixture.source(),
                authorize(preview),
                fixture.targetWorldRoot(),
                fixture.checkpointRoot(),
                List.of(protectedOriginal, protectedEvidence),
                fixture.preparers(),
                Optional.empty()
        );
        LegacySplitRecoveryPublicationReport protectedReport = tool.publishExact(protectedRequest);

        assertEquals(LegacySplitRecoveryPublicationReport.Outcome.RECOVERY_BLOCKED, protectedReport.outcome());
        assertTrue(protectedReport.failures().stream().anyMatch(value ->
                value.code() == LegacySplitRecoveryPublicationFailure.Code.PROTECTED_PATH));
        assertEquals(originalFiles, Files.exists(protectedOriginal) ? fileCount(protectedOriginal) : 0L);
        assertEquals(evidenceFiles, Files.exists(protectedEvidence) ? fileCount(protectedEvidence) : 0L);
    }

    @Test
    void unsupportedResultSchemaFailsVisibly() throws IOException {
        Fixture fixture = fixture("unsupported_result");
        LegacySplitRecoveryIdentity recoveryIdentity = ANALYZER.analyze(fixture.source().input()).recoveryIdentity();
        Path result = fixture.storage().resultPath(recoveryIdentity);
        Files.createDirectories(result.getParent());
        Files.writeString(result, "{\"schema_version\":999}", StandardCharsets.UTF_8);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.storage().loadResult(recoveryIdentity)
        );
        assertTrue(exception.getMessage().contains("Corrupt legacy recovery result"));
    }

    @Test
    void unsupportedPublicationIntentSchemaFailsVisibly() throws IOException {
        Fixture fixture = fixture("unsupported_intent");
        LegacySplitRecoveryIdentity recoveryIdentity = ANALYZER.analyze(fixture.source().input()).recoveryIdentity();
        Path intent = fixture.storage().intentPath(recoveryIdentity);
        Files.createDirectories(intent.getParent());
        Files.writeString(intent, "{\"schema_version\":999}", StandardCharsets.UTF_8);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.storage().loadIntent(recoveryIdentity)
        );
        assertTrue(exception.getMessage().contains("Corrupt legacy recovery publication intent"));
    }

    @Test
    void boundedStressObservesOneGenerationOneResultAndStableIdentities() throws IOException {
        Fixture fixture = fixture("stress");
        LegacySplitRecoveryPublicationService service = new LegacySplitRecoveryPublicationService();
        LegacySplitRecoveryDryRunPreview preview = service.preview(fixture.previewRequest());
        RecoveryOperatorAuthorization authorization = authorize(preview);
        String resultIdentity = null;
        String generationIdentity = null;
        for (int iteration = 0; iteration < 25; iteration++) {
            LegacySplitRecoveryPublicationReport report = service.publish(fixture.publicationRequest(authorization));
            assertTrue(report.successful(), report.failures().toString());
            LegacySplitRecoveryResult result = report.recoveryResult().orElseThrow();
            if (resultIdentity == null) {
                resultIdentity = result.resultIdentity();
                generationIdentity = result.recoveryGenerationId().canonicalValue();
            }
            assertEquals(resultIdentity, result.resultIdentity());
            assertEquals(generationIdentity, result.recoveryGenerationId().canonicalValue());
            assertEquals(9, result.publishedAcknowledgements().stream().map(
                    LegacySplitRecoveryResult.PublishedAcknowledgement::identity).distinct().count());
        }
        assertEquals(1L, Files.list(fixture.checkpointStore().layout().generationsDirectory()).count());
        assertEquals(2L, Files.list(fixture.storage().publicationDirectory(preview.plan().recoveryIdentity()))
                .filter(Files::isRegularFile)
                .count());
    }

    private Fixture fixture(String name) throws IOException {
        Path target = temporaryDirectory.resolve(name).resolve("disposable_world");
        Path sourceRoot = temporaryDirectory.resolve(name).resolve("read_only_source");
        Path checkpoint = target.resolve("butchercraft/checkpoints");
        Files.createDirectories(target.resolve("butchercraft"));
        Files.createDirectories(sourceRoot);
        Path inventory = target.resolve("butchercraft/inventory.json");
        Path workstation = target.resolve("butchercraft/workstation_projection.json");
        Path material = target.resolve("butchercraft/material_handling.json");
        Files.writeString(inventory, "{\"ground_beef\":0,\"beef_patties\":9}\n");
        Files.writeString(workstation, "{\"instance_generation\":6,\"output\":9}\n");
        Files.writeString(material, "{\"transfers\":[]}\n");
        return new Fixture(
                target,
                checkpoint,
                inventory,
                workstation,
                material,
                Files.readString(inventory),
                Files.readString(workstation),
                Files.readString(material),
                new MutableAnalysisSource(SplitSnapshotRecoveryAnalyzerTest.knownFixture(), List.of(sourceRoot)),
                preparers()
        );
    }

    private static List<LegacySplitRecoveryOwnerPreparer> preparers() {
        List<LegacySplitRecoveryOwnerPreparer> values = new ArrayList<>();
        values.add(new SimulationClockLegacyRecoveryOwnerPreparer());
        values.add(new SchedulerLegacyRecoveryOwnerPreparer());
        values.add(new ExecutionLegacyRecoveryOwnerPreparer());
        values.add(new WorkstationLegacyRecoveryOwnerPreparer());
        values.add(new PlanningLegacyRecoveryOwnerPreparer());
        values.add(new CheckpointRecoveryLegacyRecoveryOwnerPreparer());
        for (CheckpointOwnerId owner : LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS) {
            if (values.stream().noneMatch(value -> value.ownerId().equals(owner))) {
                values.add(new ExactUnchangedLegacyRecoveryOwnerPreparer(owner));
            }
        }
        return List.copyOf(values);
    }

    private static RecoveryOperatorAuthorization authorize(LegacySplitRecoveryDryRunPreview preview) {
        return RecoveryOperatorAuthorization.authorize(
                preview.plan(),
                RecoveryOperatorAuthorization.Disposition.AUTHORIZE_RECOVERY_WITH_AUTHORITY_BLOCKS,
                SplitSnapshotRecoveryAnalyzerTest.operator(RecoveryOperatorAuthority.OPERATOR),
                Optional.of("2026-08-21T12:00:00Z")
        );
    }

    private static Map<CheckpointOwnerId, LegacyRecoveryOwnerSnapshotDocument> ownerDocuments(
            CheckpointRecoveredGeneration generation
    ) {
        Map<CheckpointOwnerId, LegacyRecoveryOwnerSnapshotDocument> documents = new HashMap<>();
        generation.ownerSnapshots().forEach(snapshot -> documents.put(
                snapshot.descriptor().ownerId(),
                LegacyRecoveryOwnerSnapshotDocument.deserialize(snapshot.payloadBytes())
        ));
        return documents;
    }

    private static Map<String, String> fields(LegacyRecoveryOwnerSnapshotDocument document) {
        Map<String, String> fields = new HashMap<>();
        document.fields().forEach(value -> fields.put(value.key(), value.value()));
        return fields;
    }

    private static long fileCount(Path root) throws IOException {
        if (!Files.exists(root)) return 0L;
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private static String directoryDigest(Path root) throws IOException {
        CheckpointCanonicalDigest digest = CheckpointCanonicalDigest.create(
                "butchercraft:checkpoint_digest/test_directory"
        );
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                digest.add(root.relativize(path).toString().replace('\\', '/'))
                        .add(CheckpointFilesystemDigest.sha256(Files.readAllBytes(path)));
            }
        }
        return digest.finish();
    }

    private static CheckpointGenerationManifest publishPreviousCheckpoint(Fixture fixture) {
        SplitSnapshotRecoveryPlan plan = ANALYZER.analyze(fixture.source().input());
        CheckpointGenerationId generationId = CheckpointGenerationId.of(
                1L,
                plan.schedulerLastNormallyFinalizedTick()
        );
        List<CheckpointOwnerSnapshotPayload> payloads = LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS.stream()
                .map(owner -> previousSnapshot(
                        owner,
                        generationId,
                        plan.schedulerLastNormallyFinalizedTick(),
                        plan
                ))
                .toList();
        CheckpointPublicationReport report = fixture.checkpointStore().publish(new CheckpointPublicationRequest(
                generationId,
                Optional.empty(),
                Optional.empty(),
                plan.schedulerLastNormallyFinalizedTick(),
                payloads,
                LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS,
                plan.platformDeterminismManifest(),
                plan.worldIdentityRoot()
        ));
        assertTrue(report.successful(), report.diagnostics().toString());
        return report.generationManifest().orElseThrow();
    }

    private static CheckpointOwnerSnapshotPayload previousSnapshot(
            CheckpointOwnerId owner,
            CheckpointGenerationId generationId,
            long tick,
            SplitSnapshotRecoveryPlan plan
    ) {
        String ownerPath = owner.value().substring(owner.value().indexOf(':') + 1);
        byte[] payload = ("owner=" + owner.value() + "\nsequence=1\ntick=" + tick)
                .getBytes(StandardCharsets.UTF_8);
        String contentDigest = CheckpointFilesystemDigest.sha256(payload);
        OwnerSnapshotDescriptor descriptor = new OwnerSnapshotDescriptor(
                owner,
                1,
                "butchercraft:prior_snapshot/" + ownerPath,
                contentDigest,
                CheckpointSnapshotParticipation.REQUIRED,
                plan.platformDeterminismManifest().identity(),
                plan.worldIdentityRoot(),
                generationId,
                tick,
                1L
        );
        return CheckpointOwnerSnapshotPayload.of(descriptor, payload);
    }

    private static SplitSnapshotRecoveryPlan copyPlanIdentityAndDigest(
            SplitSnapshotRecoveryPlan plan,
            LegacySplitRecoveryIdentity recoveryIdentity,
            String analysisDigest
    ) {
        SchedulerRecoveryDiscontinuity sourceDiscontinuity = plan.recoveryDiscontinuity().orElseThrow();
        SchedulerRecoveryDiscontinuity discontinuity = sourceDiscontinuity.recoveryIdentityReference()
                .equals(recoveryIdentity.value())
                ? sourceDiscontinuity
                : SchedulerRecoveryDiscontinuity.create(
                        sourceDiscontinuity.sourceSchedulerTick(),
                        sourceDiscontinuity.authoritativeClockTick(),
                        sourceDiscontinuity.sourceSchedulerSnapshotIdentity(),
                        sourceDiscontinuity.sourceSchedulerSnapshotDigest(),
                        sourceDiscontinuity.clockSnapshotIdentity(),
                        sourceDiscontinuity.clockSnapshotDigest(),
                        recoveryIdentity.value(),
                        sourceDiscontinuity.reasonIdentity(),
                        sourceDiscontinuity.historicalAcknowledgementIdentities(),
                        sourceDiscontinuity.unresolvedWorkReferences()
                );
        return new SplitSnapshotRecoveryPlan(
                plan.schemaVersion(),
                recoveryIdentity,
                analysisDigest,
                plan.worldIdentityRoot(),
                plan.platformDeterminismManifest(),
                plan.sourceSnapshots(),
                plan.authoritativeClockTick(),
                plan.schedulerLastNormallyFinalizedTick(),
                plan.historicalAcknowledgements(),
                plan.ordinaryWorkProofs(),
                Optional.of(discontinuity),
                plan.nextNormalSchedulerAdmissionTick(),
                plan.preservedAuthorizedWork(),
                plan.authorityBlocks(),
                plan.planningAuthorityBlocks(),
                plan.materialHandlingReferences(),
                plan.replacementWorkstationConflicts(),
                plan.legacyTempFindings(),
                plan.eligibility(),
                plan.issues(),
                plan.coherentCheckpointAvailable(),
                plan.operatorAuthorizationRequired(),
                false
        );
    }

    private static SplitSnapshotRecoveryAnalysisInput changedSourceDigest(
            SplitSnapshotRecoveryAnalysisInput input
    ) {
        List<RecoverySourceSnapshot> sources = new ArrayList<>(input.sourceSnapshots());
        RecoverySourceSnapshot source = sources.getFirst();
        sources.set(0, new RecoverySourceSnapshot(
                source.ownerId(),
                source.ownerSchemaVersion(),
                source.supportedSchemaVersions(),
                source.snapshotIdentity(),
                SplitSnapshotRecoveryAnalyzerTest.digest('6'),
                source.ownerRevisionOrSequence(),
                source.representedSimulationTick(),
                source.worldIdentityRoot(),
                source.configurationIdentities(),
                true,
                Optional.empty()
        ));
        return copy(input, sources, input.planningAuthorityBlocks());
    }

    private static SplitSnapshotRecoveryAnalysisInput withoutPlanningBlock(
            SplitSnapshotRecoveryAnalysisInput input
    ) {
        return copy(input, input.sourceSnapshots(), List.of());
    }

    private static SplitSnapshotRecoveryAnalysisInput withWorld(
            SplitSnapshotRecoveryAnalysisInput input
    ) {
        WorldIdentityRootReference world = new WorldIdentityRootReference(
                "butchercraft:world/other_recovery_fixture",
                1,
                SplitSnapshotRecoveryAnalyzerTest.digest('6')
        );
        List<RecoverySourceSnapshot> sources = input.sourceSnapshots().stream()
                .map(source -> new RecoverySourceSnapshot(
                        source.ownerId(),
                        source.ownerSchemaVersion(),
                        source.supportedSchemaVersions(),
                        source.snapshotIdentity(),
                        source.contentDigest(),
                        source.ownerRevisionOrSequence(),
                        source.representedSimulationTick(),
                        world,
                        source.configurationIdentities(),
                        source.ownerValidated(),
                        source.validationFailure()
                ))
                .toList();
        return new SplitSnapshotRecoveryAnalysisInput(
                input.schemaVersion(),
                world,
                input.platformDeterminismManifest(),
                sources,
                input.authoritativeClockTick(),
                input.schedulerLastNormallyFinalizedTick(),
                input.clockSnapshotIdentity(),
                input.clockSnapshotDigest(),
                input.schedulerSnapshotIdentity(),
                input.schedulerSnapshotDigest(),
                input.historicalAssessments(),
                input.ordinaryWorkProofs(),
                input.preservedAuthorizedWork(),
                input.planningAuthorityBlocks(),
                input.materialHandlingReferences(),
                input.replacementWorkstationConflicts(),
                input.legacyTempFindings(),
                input.coherentCheckpointAvailable()
        );
    }

    private static SplitSnapshotRecoveryAnalysisInput copy(
            SplitSnapshotRecoveryAnalysisInput input,
            List<RecoverySourceSnapshot> sources,
            List<com.butchercraft.world.planning.PlanningRecoveryAuthorityBlock> planningBlocks
    ) {
        return new SplitSnapshotRecoveryAnalysisInput(
                input.schemaVersion(),
                input.worldIdentityRoot(),
                input.platformDeterminismManifest(),
                sources,
                input.authoritativeClockTick(),
                input.schedulerLastNormallyFinalizedTick(),
                input.clockSnapshotIdentity(),
                input.clockSnapshotDigest(),
                input.schedulerSnapshotIdentity(),
                input.schedulerSnapshotDigest(),
                input.historicalAssessments(),
                input.ordinaryWorkProofs(),
                input.preservedAuthorizedWork(),
                planningBlocks,
                input.materialHandlingReferences(),
                input.replacementWorkstationConflicts(),
                input.legacyTempFindings(),
                input.coherentCheckpointAvailable()
        );
    }

    private record Fixture(
            Path targetWorldRoot,
            Path checkpointRoot,
            Path liveInventoryPath,
            Path liveWorkstationPath,
            Path liveMaterialHandlingPath,
            String liveInventoryBefore,
            String liveWorkstationBefore,
            String liveMaterialHandlingBefore,
            MutableAnalysisSource source,
            List<LegacySplitRecoveryOwnerPreparer> preparers
    ) {
        LegacySplitRecoveryPreviewRequest previewRequest() {
            return new LegacySplitRecoveryPreviewRequest(
                    source,
                    targetWorldRoot,
                    checkpointRoot,
                    List.of()
            );
        }

        LegacySplitRecoveryPublicationRequest publicationRequest(
                RecoveryOperatorAuthorization authorization
        ) {
            return publicationRequest(authorization, preparers);
        }

        LegacySplitRecoveryPublicationRequest publicationRequest(
                RecoveryOperatorAuthorization authorization,
                List<LegacySplitRecoveryOwnerPreparer> selectedPreparers
        ) {
            return new LegacySplitRecoveryPublicationRequest(
                    source,
                    authorization,
                    targetWorldRoot,
                    checkpointRoot,
                    List.of(),
                    selectedPreparers,
                    Optional.of("2026-08-21T12:00:00Z")
            );
        }

        LegacySplitRecoveryPublicationStorage storage() {
            return new LegacySplitRecoveryPublicationStorage(checkpointRoot);
        }

        CheckpointFilesystemStore checkpointStore() {
            return new CheckpointFilesystemStore(checkpointRoot);
        }

        CheckpointFilesystemRecoveryRequest recoveryRequest() {
            SplitSnapshotRecoveryAnalysisInput input = source.input();
            return new CheckpointFilesystemRecoveryRequest(
                    LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS,
                    input.worldIdentityRoot(),
                    input.platformDeterminismManifest()
            );
        }
    }

    private static final class MutableAnalysisSource implements LegacySplitRecoveryAnalysisSource {
        private SplitSnapshotRecoveryAnalysisInput input;
        private List<Path> roots;
        private final AtomicInteger reloads = new AtomicInteger();

        private MutableAnalysisSource(SplitSnapshotRecoveryAnalysisInput input, List<Path> roots) {
            this.input = input;
            this.roots = List.copyOf(roots);
        }

        @Override
        public SplitSnapshotRecoveryAnalysisInput reloadReadOnly() {
            reloads.incrementAndGet();
            return input;
        }

        @Override
        public List<Path> sourceRoots() {
            return roots;
        }

        SplitSnapshotRecoveryAnalysisInput input() {
            return input;
        }

        int reloadCount() {
            return reloads.get();
        }

        void replaceInput(SplitSnapshotRecoveryAnalysisInput replacement) {
            assertNotEquals(ANALYZER.analyze(input).analysisDigest(), ANALYZER.analyze(replacement).analysisDigest());
            input = replacement;
        }

        void replaceRoots(List<Path> replacement) {
            roots = List.copyOf(replacement);
        }
    }
}
