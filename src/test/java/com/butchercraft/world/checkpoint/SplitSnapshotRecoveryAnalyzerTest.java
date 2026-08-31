package com.butchercraft.world.checkpoint;

import com.butchercraft.test.TestProjectPaths;
import com.butchercraft.world.planning.PlanningRecoveryAuthorityBlock;
import com.butchercraft.world.simulation.scheduler.HistoricalCoordinationAcknowledgement;
import com.butchercraft.world.simulation.scheduler.HistoricalCoordinationAssessment;
import com.butchercraft.world.simulation.scheduler.HistoricalCoordinationProof;
import com.butchercraft.world.simulation.scheduler.HistoricalCoordinationSourceEvidence;
import com.butchercraft.world.simulation.scheduler.OrdinaryWorkReconstructionProof;
import com.butchercraft.world.simulation.scheduler.SchedulerEffectIdentity;
import com.butchercraft.world.simulation.scheduler.SchedulerInvocationIdentity;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitSnapshotRecoveryAnalyzerTest {
    private static final SplitSnapshotRecoveryAnalyzer ANALYZER = new SplitSnapshotRecoveryAnalyzer();
    private static final WorldIdentityRootReference WORLD = new WorldIdentityRootReference(
            "butchercraft:world/recovery_fixture",
            1,
            digest('a')
    );
    private static final PlatformDeterminismManifestReference PLATFORM =
            new PlatformDeterminismManifestReference(
                    "butchercraft:platform_determinism/recovery_fixture",
                    1,
                    digest('b')
            );

    @Test
    void knownHistoricalFixtureProducesExactReadOnlyPlan() {
        SplitSnapshotRecoveryPlan plan = ANALYZER.analyze(knownFixture());

        assertEquals(39872L, plan.authoritativeClockTick());
        assertEquals(39084L, plan.schedulerLastNormallyFinalizedTick());
        assertEquals(39085L, plan.recoveryDiscontinuity().orElseThrow().inclusiveMissingStartTick());
        assertEquals(39872L, plan.recoveryDiscontinuity().orElseThrow().inclusiveMissingEndTick());
        assertEquals(788L, plan.recoveryDiscontinuity().orElseThrow().missingTickCount());
        assertEquals(39873L, plan.nextNormalSchedulerAdmissionTick().orElseThrow());
        assertEquals(9, plan.historicalAcknowledgements().size());
        assertEquals(1, plan.preservedAuthorizedWork().size());
        assertEquals(1, plan.planningAuthorityBlocks().size());
        assertEquals(SplitSnapshotRecoveryPlan.Eligibility.RECOVERABLE_WITH_AUTHORITY_BLOCKS,
                plan.eligibility());
        assertTrue(plan.operatorAuthorizationRequired());
        assertFalse(plan.recoveryPublished());
        assertFalse(plan.preservedAuthorizedWork().getFirst().terminal());
        assertFalse(plan.preservedAuthorizedWork().getFirst().schedulerInvocationStarted());
        assertFalse(plan.preservedAuthorizedWork().getFirst().automaticallyScheduled());
        assertEquals(PreservedAuthorizedWork.POLICY_B,
                plan.preservedAuthorizedWork().getFirst().restartPolicyIdentity());
    }

    @Test
    void recoveryIdentityAndAnalysisDigestAreDeterministicAcrossInputOrder() {
        SplitSnapshotRecoveryAnalysisInput first = knownFixture();
        SplitSnapshotRecoveryAnalysisInput reversed = reorder(first);

        SplitSnapshotRecoveryPlan firstPlan = ANALYZER.analyze(first);
        SplitSnapshotRecoveryPlan secondPlan = ANALYZER.analyze(first);
        SplitSnapshotRecoveryPlan reversedPlan = ANALYZER.analyze(reversed);

        assertEquals(firstPlan, secondPlan);
        assertEquals(firstPlan.recoveryIdentity(), reversedPlan.recoveryIdentity());
        assertEquals(firstPlan.analysisDigest(), reversedPlan.analysisDigest());
        assertEquals(firstPlan.historicalAcknowledgements(), reversedPlan.historicalAcknowledgements());
        assertEquals(firstPlan.authorityBlocks(), reversedPlan.authorityBlocks());
    }

    @Test
    void recoveryIdentityChangesWhenExactSourceEvidenceChanges() {
        SplitSnapshotRecoveryAnalysisInput input = knownFixture();
        List<RecoverySourceSnapshot> changedSources = new ArrayList<>(input.sourceSnapshots());
        RecoverySourceSnapshot source = changedSources.getFirst();
        changedSources.set(0, new RecoverySourceSnapshot(
                source.ownerId(), source.ownerSchemaVersion(), source.supportedSchemaVersions(),
                source.snapshotIdentity(), digest('f'), source.ownerRevisionOrSequence(),
                source.representedSimulationTick(), source.worldIdentityRoot(), source.configurationIdentities(),
                true, Optional.empty()
        ));

        assertNotEquals(
                ANALYZER.analyze(input).recoveryIdentity(),
                ANALYZER.analyze(copy(input, changedSources, input.historicalAssessments(),
                        input.ordinaryWorkProofs(), input.materialHandlingReferences(),
                        input.replacementWorkstationConflicts())).recoveryIdentity()
        );
    }

    @Test
    void succeededAndFailedTerminalProofsProduceOnlyTheirExactOutcome() {
        HistoricalCoordinationAssessment succeeded = HistoricalCoordinationAssessment.evaluate(
                terminalProof(1, HistoricalCoordinationProof.TerminalOutcome.SUCCEEDED, true, false)
        );
        HistoricalCoordinationAssessment failed = HistoricalCoordinationAssessment.evaluate(
                terminalProof(2, HistoricalCoordinationProof.TerminalOutcome.FAILED, false, false)
        );

        assertEquals(HistoricalCoordinationAssessment.Eligibility.ACKNOWLEDGEMENT_ELIGIBLE,
                succeeded.eligibility());
        assertEquals(HistoricalCoordinationProof.TerminalOutcome.SUCCEEDED,
                succeeded.acknowledgement().orElseThrow().terminalOutcome());
        assertEquals(HistoricalCoordinationAssessment.Eligibility.ACKNOWLEDGEMENT_ELIGIBLE,
                failed.eligibility());
        assertEquals(HistoricalCoordinationProof.TerminalOutcome.FAILED,
                failed.acknowledgement().orElseThrow().terminalOutcome());
        assertTrue(failed.acknowledgement().orElseThrow().ownerResultIdentity().isEmpty());
    }

    @Test
    void missingCriticalIdentityCannotReceiveAcknowledgementAndYieldsUnknownOutcome() {
        HistoricalCoordinationProof complete = terminalProof(
                1,
                HistoricalCoordinationProof.TerminalOutcome.SUCCEEDED,
                true,
                false
        );
        HistoricalCoordinationProof missingOwnerResult = new HistoricalCoordinationProof(
                complete.executionOperationIdentity(), complete.schedulerWorkIdentity(),
                complete.schedulerInvocationIdentity(), complete.schedulerEffectIdentity(),
                complete.authorizationIdentity(), complete.authorizationContentDigest(),
                complete.domainEffectIdentity(), complete.handlerContractIdentity(), complete.ownerSubsystemId(),
                Optional.empty(), Optional.empty(), complete.executionResultIdentity(),
                complete.executionResultContentDigest(), complete.workstationInstanceIdentity(),
                complete.workstationInstanceGeneration(), complete.machineRunIdentity(), complete.childSequence(),
                complete.terminalOutcome(), complete.schedulerInvocationStarted(), complete.startedSimulationTick(),
                complete.completedSimulationTick(), complete.sourceEvidence(), false
        );
        HistoricalCoordinationAssessment assessment = HistoricalCoordinationAssessment.evaluate(missingOwnerResult);
        SplitSnapshotRecoveryAnalysisInput base = knownFixture();
        SplitSnapshotRecoveryPlan plan = ANALYZER.analyze(copy(
                base, base.sourceSnapshots(), List.of(assessment), List.of(), List.of(), List.of()
        ));

        assertEquals(HistoricalCoordinationAssessment.Eligibility.INCOMPLETE_EVIDENCE,
                assessment.eligibility());
        assertTrue(assessment.acknowledgement().isEmpty());
        assertEquals(SplitSnapshotRecoveryPlan.Eligibility.UNKNOWN_OUTCOME, plan.eligibility());
        assertTrue(plan.historicalAcknowledgements().isEmpty());
    }

    @Test
    void acknowledgementEvidenceMustBelongToAnExactAnalyzedOwnerSnapshot() {
        HistoricalCoordinationProof complete = terminalProof(
                1,
                HistoricalCoordinationProof.TerminalOutcome.SUCCEEDED,
                true,
                false
        );
        List<HistoricalCoordinationSourceEvidence> mismatchedSources = new ArrayList<>(complete.sourceEvidence());
        HistoricalCoordinationSourceEvidence execution = mismatchedSources.getFirst();
        mismatchedSources.set(0, new HistoricalCoordinationSourceEvidence(
                execution.ownerSubsystemId(),
                execution.sourceSnapshotIdentity(),
                digest('f'),
                execution.evidenceIdentity(),
                execution.evidenceContentDigest()
        ));
        HistoricalCoordinationAssessment assessment = HistoricalCoordinationAssessment.evaluate(
                new HistoricalCoordinationProof(
                        complete.executionOperationIdentity(), complete.schedulerWorkIdentity(),
                        complete.schedulerInvocationIdentity(), complete.schedulerEffectIdentity(),
                        complete.authorizationIdentity(), complete.authorizationContentDigest(),
                        complete.domainEffectIdentity(), complete.handlerContractIdentity(),
                        complete.ownerSubsystemId(), complete.ownerResultIdentity(),
                        complete.ownerResultContentDigest(), complete.executionResultIdentity(),
                        complete.executionResultContentDigest(), complete.workstationInstanceIdentity(),
                        complete.workstationInstanceGeneration(), complete.machineRunIdentity(),
                        complete.childSequence(), complete.terminalOutcome(),
                        complete.schedulerInvocationStarted(), complete.startedSimulationTick(),
                        complete.completedSimulationTick(), mismatchedSources, false
                )
        );
        SplitSnapshotRecoveryAnalysisInput base = knownFixture();
        SplitSnapshotRecoveryPlan plan = ANALYZER.analyze(copy(
                base, base.sourceSnapshots(), List.of(assessment), List.of(), List.of(), List.of()
        ));

        assertEquals(HistoricalCoordinationAssessment.Eligibility.ACKNOWLEDGEMENT_ELIGIBLE,
                assessment.eligibility());
        assertTrue(plan.historicalAcknowledgements().isEmpty());
        assertEquals(SplitSnapshotRecoveryPlan.Eligibility.UNKNOWN_OUTCOME, plan.eligibility());
        assertTrue(plan.issues().stream().anyMatch(issue ->
                issue.code() == RecoveryIssue.Code.MISSING_HISTORICAL_EVIDENCE));
    }

    @Test
    void conflictingTerminalEvidenceBlocksRecovery() {
        HistoricalCoordinationAssessment conflict = HistoricalCoordinationAssessment.evaluate(
                terminalProof(1, HistoricalCoordinationProof.TerminalOutcome.SUCCEEDED, true, true)
        );
        SplitSnapshotRecoveryAnalysisInput base = knownFixture();
        SplitSnapshotRecoveryPlan plan = ANALYZER.analyze(copy(
                base, base.sourceSnapshots(), List.of(conflict), List.of(), List.of(), List.of()
        ));

        assertEquals(HistoricalCoordinationAssessment.Eligibility.CONFLICTING_EVIDENCE,
                conflict.eligibility());
        assertEquals(SplitSnapshotRecoveryPlan.Eligibility.RECOVERY_BLOCKED, plan.eligibility());
    }

    @Test
    void ordinaryWorkRequiresEveryConsequentialField() {
        OrdinaryWorkReconstructionProof complete = ordinaryProof(true, true, false);
        OrdinaryWorkReconstructionProof acknowledgementOnly = ordinaryProof(false, true, false);
        OrdinaryWorkReconstructionProof notProvable = ordinaryProof(false, false, false);

        assertEquals(OrdinaryWorkReconstructionProof.Eligibility.ORDINARY_WORK_RECONSTRUCTABLE,
                complete.eligibility());
        assertEquals(OrdinaryWorkReconstructionProof.Eligibility.ACKNOWLEDGEMENT_ONLY,
                acknowledgementOnly.eligibility());
        assertEquals(OrdinaryWorkReconstructionProof.Eligibility.NOT_PROVABLE,
                notProvable.eligibility());
    }

    @Test
    void schedulerWorkWithoutExecutionOutcomeRemainsUnknown() {
        SplitSnapshotRecoveryAnalysisInput base = knownFixture();
        SplitSnapshotRecoveryPlan plan = ANALYZER.analyze(copy(
                base, base.sourceSnapshots(), List.of(), List.of(ordinaryProof(false, false, false)),
                List.of(), List.of()
        ));

        assertEquals(SplitSnapshotRecoveryPlan.Eligibility.UNKNOWN_OUTCOME, plan.eligibility());
        assertTrue(plan.issues().stream().anyMatch(issue ->
                issue.code() == RecoveryIssue.Code.ORDINARY_WORK_NOT_PROVABLE));
    }

    @Test
    void planningDependencyClosureFallsBackToWholeWorldAndMayRemainScopedWhenProven() {
        PlanningRecoveryAuthorityBlock unproven = planningBlock(false);
        PlanningRecoveryAuthorityBlock proven = PlanningRecoveryAuthorityBlock.unresolvedNonRepeatable(
                "butchercraft:planning_work/scoped",
                39601L,
                39601L,
                planningEvidence(),
                PlanningRecoveryAuthorityBlock.DependencyScope.BOUNDED_AUTHORITY_SCOPE,
                List.of("butchercraft:production"),
                true
        );

        assertTrue(unproven.wholeWorldMutationBlocked());
        assertEquals(PlanningRecoveryAuthorityBlock.DependencyScope.BOUNDED_AUTHORITY_SCOPE,
                proven.dependencyScope());
        assertTrue(proven.dependencyClosureProven());
    }

    @Test
    void operatorAuthorizationBindsExactAnalysisAndIgnoresAuditTimestampForIdentity() {
        SplitSnapshotRecoveryPlan plan = ANALYZER.analyze(knownFixture());
        RecoveryOperatorAuthorization first = RecoveryOperatorAuthorization.authorize(
                plan,
                RecoveryOperatorAuthorization.Disposition.AUTHORIZE_RECOVERY_WITH_AUTHORITY_BLOCKS,
                operator(RecoveryOperatorAuthority.OPERATOR),
                Optional.of("2026-08-20T12:00:00Z")
        );
        RecoveryOperatorAuthorization second = RecoveryOperatorAuthorization.authorize(
                plan,
                RecoveryOperatorAuthorization.Disposition.AUTHORIZE_RECOVERY_WITH_AUTHORITY_BLOCKS,
                operator(RecoveryOperatorAuthority.OPERATOR),
                Optional.of("2027-01-01T00:00:00Z")
        );
        SplitSnapshotRecoveryAnalysisInput changed = knownFixture();
        List<RecoverySourceSnapshot> sources = new ArrayList<>(changed.sourceSnapshots());
        RecoverySourceSnapshot source = sources.getLast();
        sources.set(sources.size() - 1, new RecoverySourceSnapshot(
                source.ownerId(), source.ownerSchemaVersion(), source.supportedSchemaVersions(),
                source.snapshotIdentity(), digest('e'), source.ownerRevisionOrSequence(),
                source.representedSimulationTick(), source.worldIdentityRoot(), source.configurationIdentities(),
                true, Optional.empty()
        ));
        SplitSnapshotRecoveryPlan changedPlan = ANALYZER.analyze(copy(
                changed, sources, changed.historicalAssessments(), changed.ordinaryWorkProofs(),
                changed.materialHandlingReferences(), changed.replacementWorkstationConflicts()
        ));

        assertEquals(first.authorizationIdentity(), second.authorizationIdentity());
        assertEquals(first.contentDigest(), second.contentDigest());
        assertTrue(first.targets(plan));
        assertFalse(first.targets(changedPlan));
        assertThrows(IllegalArgumentException.class, () -> RecoveryOperatorAuthorization.authorize(
                plan,
                RecoveryOperatorAuthorization.Disposition.AUTHORIZE_PROOF_COMPLETE_PUBLICATION,
                operator(RecoveryOperatorAuthority.OPERATOR),
                Optional.empty()
        ));
        assertThrows(SecurityException.class, () -> RecoveryOperatorAuthorization.authorize(
                plan,
                RecoveryOperatorAuthorization.Disposition.AUTHORIZE_RECOVERY_WITH_AUTHORITY_BLOCKS,
                operator(RecoveryOperatorAuthority.ORDINARY_PLAYER),
                Optional.empty()
        ));
    }

    @Test
    void unsupportedSchemaWorldMismatchAndReplacementInstanceAreTypedBlocks() {
        SplitSnapshotRecoveryAnalysisInput base = knownFixture();
        SplitSnapshotRecoveryAnalysisInput unsupportedAnalysis = new SplitSnapshotRecoveryAnalysisInput(
                LegacySplitRecoverySchema.CURRENT_VERSION + 1,
                base.worldIdentityRoot(), base.platformDeterminismManifest(), base.sourceSnapshots(),
                base.authoritativeClockTick(), base.schedulerLastNormallyFinalizedTick(),
                base.clockSnapshotIdentity(), base.clockSnapshotDigest(), base.schedulerSnapshotIdentity(),
                base.schedulerSnapshotDigest(), base.historicalAssessments(), base.ordinaryWorkProofs(),
                base.preservedAuthorizedWork(), base.planningAuthorityBlocks(),
                base.materialHandlingReferences(), base.replacementWorkstationConflicts(),
                base.legacyTempFindings(), base.coherentCheckpointAvailable()
        );
        assertEquals(SplitSnapshotRecoveryPlan.Eligibility.UNSUPPORTED_SCHEMA,
                ANALYZER.analyze(unsupportedAnalysis).eligibility());

        RecoverySourceSnapshot source = base.sourceSnapshots().getFirst();
        RecoverySourceSnapshot unsupported = new RecoverySourceSnapshot(
                source.ownerId(), 99, List.of(1), source.snapshotIdentity(), source.contentDigest(),
                source.ownerRevisionOrSequence(), source.representedSimulationTick(), source.worldIdentityRoot(),
                source.configurationIdentities(), true, Optional.empty()
        );
        List<RecoverySourceSnapshot> unsupportedSources = new ArrayList<>(base.sourceSnapshots());
        unsupportedSources.set(0, unsupported);
        assertEquals(SplitSnapshotRecoveryPlan.Eligibility.UNSUPPORTED_SCHEMA,
                ANALYZER.analyze(copy(base, unsupportedSources, base.historicalAssessments(),
                        base.ordinaryWorkProofs(), List.of(), List.of())).eligibility());

        RecoverySourceSnapshot mismatched = new RecoverySourceSnapshot(
                source.ownerId(), source.ownerSchemaVersion(), source.supportedSchemaVersions(),
                source.snapshotIdentity(), source.contentDigest(), source.ownerRevisionOrSequence(),
                source.representedSimulationTick(), new WorldIdentityRootReference(
                "butchercraft:world/other", 1, digest('d')),
                source.configurationIdentities(), true, Optional.empty()
        );
        List<RecoverySourceSnapshot> mismatchSources = new ArrayList<>(base.sourceSnapshots());
        mismatchSources.set(0, mismatched);
        assertEquals(SplitSnapshotRecoveryPlan.Eligibility.RECOVERY_BLOCKED,
                ANALYZER.analyze(copy(base, mismatchSources, base.historicalAssessments(),
                        base.ordinaryWorkProofs(), List.of(), List.of())).eligibility());

        ReplacementWorkstationConflict replacement = new ReplacementWorkstationConflict(
                "butchercraft:workstation_endpoint/patty_former",
                workstationIdentity(1), 1L, workstationIdentity(2), 2L, digest('f')
        );
        assertEquals(SplitSnapshotRecoveryPlan.Eligibility.RECOVERY_BLOCKED,
                ANALYZER.analyze(copy(base, base.sourceSnapshots(), base.historicalAssessments(),
                        base.ordinaryWorkProofs(), List.of(), List.of(replacement))).eligibility());
    }

    @Test
    void materialHandlingCustodyIsExactAndClassifiedWithoutRepair() {
        SplitSnapshotRecoveryAnalysisInput base = knownFixture();
        MaterialHandlingRecoveryReference proof = material(MaterialHandlingRecoveryReference.Status.PROOF_COMPLETE);
        MaterialHandlingRecoveryReference blocked = material(MaterialHandlingRecoveryReference.Status.RECOVERY_BLOCKED);
        MaterialHandlingRecoveryReference unknown = material(MaterialHandlingRecoveryReference.Status.UNKNOWN_OUTCOME);

        SplitSnapshotRecoveryPlan proofPlan = ANALYZER.analyze(copy(
                base, base.sourceSnapshots(), base.historicalAssessments(), base.ordinaryWorkProofs(),
                List.of(proof), List.of()
        ));
        SplitSnapshotRecoveryPlan blockedPlan = ANALYZER.analyze(copy(
                base, base.sourceSnapshots(), base.historicalAssessments(), base.ordinaryWorkProofs(),
                List.of(blocked), List.of()
        ));
        SplitSnapshotRecoveryPlan unknownPlan = ANALYZER.analyze(copy(
                base, base.sourceSnapshots(), base.historicalAssessments(), base.ordinaryWorkProofs(),
                List.of(unknown), List.of()
        ));

        assertEquals(digest('7'), proofPlan.materialHandlingReferences().getFirst().exactCustodyContentDigest());
        assertEquals(SplitSnapshotRecoveryPlan.Eligibility.RECOVERY_BLOCKED,
                blockedPlan.eligibility());
        assertEquals(SplitSnapshotRecoveryPlan.Eligibility.UNKNOWN_OUTCOME, unknownPlan.eligibility());
    }

    @Test
    void clockSchedulerDirectionIsExplicitAndNoSyntheticTicksExist() {
        SplitSnapshotRecoveryAnalysisInput base = knownFixture();
        SplitSnapshotRecoveryPlan exact = ANALYZER.analyze(base);
        SplitSnapshotRecoveryAnalysisInput schedulerNewer = new SplitSnapshotRecoveryAnalysisInput(
                base.schemaVersion(), base.worldIdentityRoot(), base.platformDeterminismManifest(),
                base.sourceSnapshots(), 39083L, 39084L, base.clockSnapshotIdentity(), base.clockSnapshotDigest(),
                base.schedulerSnapshotIdentity(), base.schedulerSnapshotDigest(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), false
        );

        assertEquals(39873L, exact.nextNormalSchedulerAdmissionTick().orElseThrow());
        assertEquals(SplitSnapshotRecoveryPlan.Eligibility.RECOVERY_BLOCKED,
                ANALYZER.analyze(schedulerNewer).eligibility());
        assertTrue(java.util.Arrays.stream(exact.recoveryDiscontinuity().orElseThrow()
                        .getClass().getDeclaredMethods())
                .noneMatch(method -> method.getName().matches("execute|iterator|ticks|streamTicks")));
    }

    @Test
    void equalClockAndSchedulerWithNoRecoveryContentIsNotSplit() {
        SplitSnapshotRecoveryAnalysisInput base = knownFixture();
        List<RecoverySourceSnapshot> coherentSources = base.sourceSnapshots().stream()
                .map(source -> new RecoverySourceSnapshot(
                        source.ownerId(), source.ownerSchemaVersion(), source.supportedSchemaVersions(),
                        source.snapshotIdentity(), source.contentDigest(), source.ownerRevisionOrSequence(),
                        Math.min(source.representedSimulationTick(), 39084L), source.worldIdentityRoot(),
                        source.configurationIdentities(), true, Optional.empty()
                ))
                .toList();
        SplitSnapshotRecoveryAnalysisInput coherent = new SplitSnapshotRecoveryAnalysisInput(
                base.schemaVersion(), base.worldIdentityRoot(), base.platformDeterminismManifest(),
                coherentSources, 39084L, 39084L, base.clockSnapshotIdentity(), base.clockSnapshotDigest(),
                base.schedulerSnapshotIdentity(), base.schedulerSnapshotDigest(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), true
        );

        SplitSnapshotRecoveryPlan plan = ANALYZER.analyze(coherent);
        assertEquals(SplitSnapshotRecoveryPlan.Eligibility.NOT_SPLIT, plan.eligibility());
        assertTrue(plan.recoveryDiscontinuity().isEmpty());
        assertFalse(plan.operatorAuthorizationRequired());
    }

    @Test
    void reportExplainsAcknowledgedUnresolvedAndNonexecutedContent() {
        List<String> lines = SplitSnapshotRecoveryReport.lines(ANALYZER.analyze(knownFixture()));
        String text = String.join("\n", lines);

        assertTrue(text.contains("Historical acknowledgements: 9"));
        assertTrue(text.contains("Acknowledgement: butchercraft:historical_coordination_acknowledgement"));
        assertTrue(text.contains("Authorized-unscheduled preserved: 1"));
        assertTrue(text.contains("scheduled=false terminal=false"));
        assertTrue(text.contains("Blocked authority: butchercraft:planning"));
        assertTrue(text.contains("classification=IDENTICAL_STALE_DEBRIS authoritative=false"));
        assertTrue(text.contains("Historical execution: none"));
        assertTrue(text.contains("Recovery publication: not performed"));
    }

    @Test
    void historicalAcknowledgementHasNoExecutableSchedulerCapability() throws IOException {
        assertFalse(com.butchercraft.world.simulation.scheduler.SimulationWorkHandler.class
                .isAssignableFrom(HistoricalCoordinationAcknowledgement.class));
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/simulation/scheduler/HistoricalCoordinationAcknowledgement.java"
        ));
        assertFalse(source.contains("SimulationWorkRequest"));
        assertFalse(source.contains("SimulationExecutionContext"));
        assertFalse(source.contains("SimulationWorkOutcome"));
    }

    @Test
    void noRuntimeRecoveryPublicationApiWasIntroduced() throws IOException {
        String analyzer = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/checkpoint/SplitSnapshotRecoveryAnalyzer.java"
        ));
        assertFalse(analyzer.contains(".save("));
        assertFalse(analyzer.contains(".publish("));
        assertFalse(analyzer.contains("SimulationSchedulerManager"));
        assertFalse(analyzer.contains("ExecutionManager"));
        assertFalse(analyzer.contains("PlanningManager"));
    }

    @Test
    void legacyTempsAreClassifiedWithoutTimestampSelectionOrDeletion(@TempDir Path directory)
            throws IOException {
        Path target = directory.resolve("simulation_state.json");
        Path fixed = directory.resolve("simulation_state.json.tmp");
        Path unique = directory.resolve("simulation_state.json.tmp-12345");
        Files.writeString(target, "same");
        Files.writeString(fixed, "same");
        Files.writeString(unique, "different");
        LegacyTempArtifactClassifier classifier = new LegacyTempArtifactClassifier();

        LegacyTempArtifactFinding identical = classifier.classify(
                CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER,
                target, fixed, LegacyTempArtifactFinding.OwnerValidation.OWNER_VALID,
                LegacyTempArtifactFinding.OwnerValidation.OWNER_VALID
        );
        LegacyTempArtifactFinding uniqueAttempt = classifier.classify(
                CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER,
                target, unique, LegacyTempArtifactFinding.OwnerValidation.OWNER_VALID,
                LegacyTempArtifactFinding.OwnerValidation.NOT_ANALYZED
        );
        Files.writeString(fixed, "different-fixed");
        LegacyTempArtifactFinding differing = classifier.classify(
                CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER,
                target, fixed, LegacyTempArtifactFinding.OwnerValidation.OWNER_VALID,
                LegacyTempArtifactFinding.OwnerValidation.OWNER_VALID
        );
        LegacyTempArtifactFinding malformed = classifier.classify(
                CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER,
                target, fixed, LegacyTempArtifactFinding.OwnerValidation.OWNER_VALID,
                LegacyTempArtifactFinding.OwnerValidation.MALFORMED
        );
        LegacyTempArtifactFinding absent = classifier.classify(
                CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER,
                target, directory.resolve("simulation_state.json.tmp-missing"),
                LegacyTempArtifactFinding.OwnerValidation.OWNER_VALID,
                LegacyTempArtifactFinding.OwnerValidation.NOT_ANALYZED
        );

        assertEquals(LegacyTempArtifactFinding.Classification.IDENTICAL_STALE_DEBRIS,
                identical.classification());
        assertEquals(LegacyTempArtifactFinding.Classification.CURRENT_UNIQUE_ATTEMPT,
                uniqueAttempt.classification());
        assertEquals(LegacyTempArtifactFinding.Classification.DIFFERING_REQUIRES_OWNER_ANALYSIS,
                differing.classification());
        assertEquals(CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER, differing.ownerId());
        assertEquals(LegacyTempArtifactFinding.Classification.MALFORMED, malformed.classification());
        assertEquals(LegacyTempArtifactFinding.Classification.ABSENT, absent.classification());
        SplitSnapshotRecoveryPlan differingPlan = ANALYZER.analyze(withLegacyFinding(
                knownFixture(),
                differing
        ));
        assertEquals(SplitSnapshotRecoveryPlan.Eligibility.RECOVERABLE_WITH_AUTHORITY_BLOCKS,
                differingPlan.eligibility());
        assertTrue(differingPlan.authorityBlocks().stream().anyMatch(block ->
                block.ownerId().equals(CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER)
                        && block.scope() == RecoveryAuthorityBlock.Scope.WHOLE_WORLD_MUTATION));
        assertTrue(Files.exists(fixed));
        assertTrue(Files.exists(unique));
    }

    @Test
    void readOnlySourceAnalysisLeavesEveryFixtureByteUnchanged(@TempDir Path directory) throws IOException {
        Path clock = directory.resolve("simulation_state.json");
        Path scheduler = directory.resolve("simulation_scheduler.json");
        Path temp = directory.resolve("simulation_state.json.tmp");
        Files.writeString(clock, "{\"schema_version\":1,\"simulation_tick\":39872}");
        Files.writeString(scheduler, "{\"schema_version\":2,\"last_finalized_simulation_tick\":39084}");
        Files.copy(clock, temp);
        String beforeClock = CheckpointSnapshotDigest.sha256(Files.readAllBytes(clock));
        String beforeScheduler = CheckpointSnapshotDigest.sha256(Files.readAllBytes(scheduler));
        String beforeTemp = CheckpointSnapshotDigest.sha256(Files.readAllBytes(temp));
        ReadOnlyRecoverySourceReader reader = new ReadOnlyRecoverySourceReader();
        RecoverySourceSnapshot clockSource = reader.read(
                clock, CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER, 1, List.of(1),
                "butchercraft:snapshot/clock_fixture", 1L, 39872L, WORLD,
                List.of("butchercraft:configuration/clock"), true, Optional.empty());
        RecoverySourceSnapshot schedulerSource = reader.read(
                scheduler, CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER, 2, List.of(1, 2),
                "butchercraft:snapshot/scheduler_fixture", 1L, 39084L, WORLD,
                List.of("butchercraft:configuration/scheduler"), true, Optional.empty());
        LegacyTempArtifactFinding finding = new LegacyTempArtifactClassifier().classify(
                CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER,
                clock, temp, LegacyTempArtifactFinding.OwnerValidation.OWNER_VALID,
                LegacyTempArtifactFinding.OwnerValidation.OWNER_VALID
        );
        ANALYZER.analyze(new SplitSnapshotRecoveryAnalysisInput(
                LegacySplitRecoverySchema.CURRENT_VERSION,
                WORLD,
                PLATFORM,
                List.of(clockSource, schedulerSource),
                39872L,
                39084L,
                clockSource.snapshotIdentity(),
                clockSource.contentDigest(),
                schedulerSource.snapshotIdentity(),
                schedulerSource.contentDigest(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(finding),
                false
        ));

        assertEquals(beforeClock, CheckpointSnapshotDigest.sha256(Files.readAllBytes(clock)));
        assertEquals(beforeScheduler, CheckpointSnapshotDigest.sha256(Files.readAllBytes(scheduler)));
        assertEquals(beforeTemp, CheckpointSnapshotDigest.sha256(Files.readAllBytes(temp)));
    }

    @Test
    void invalidPreservedChildCannotClaimSchedulingOrAlternativeRestartPolicy() {
        assertThrows(IllegalArgumentException.class, () -> new PreservedAuthorizedWork(
                operationIdentity(10), machineRunIdentity(), 1L, childIdentity(10), 10L,
                workstationIdentity(1), 1L, digest('a'), List.of("butchercraft:evidence/child_10"),
                "butchercraft:machine_restart_policy/a"
        ));
    }

    static SplitSnapshotRecoveryAnalysisInput knownFixture() {
        List<HistoricalCoordinationAssessment> assessments = new ArrayList<>();
        List<OrdinaryWorkReconstructionProof> ordinary = new ArrayList<>();
        for (int index = 1; index <= 9; index++) {
            assessments.add(HistoricalCoordinationAssessment.evaluate(terminalProof(
                    index,
                    HistoricalCoordinationProof.TerminalOutcome.SUCCEEDED,
                    true,
                    false
            )));
            ordinary.add(ordinaryProof(false, true, false, index));
        }
        PreservedAuthorizedWork childTen = new PreservedAuthorizedWork(
                operationIdentity(10), machineRunIdentity(), 1L, childIdentity(10), 10L,
                workstationIdentity(1), 1L, digest('a'), List.of("butchercraft:evidence/child_10"),
                PreservedAuthorizedWork.POLICY_B
        );
        LegacyTempArtifactFinding temp = new LegacyTempArtifactFinding(
                CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER,
                "fixture/simulation_state.json",
                Optional.of("fixture/simulation_state.json.tmp"),
                LegacyTempArtifactFinding.Relation.LEGACY_FIXED_TEMP,
                LegacyTempArtifactFinding.Classification.IDENTICAL_STALE_DEBRIS,
                LegacyTempArtifactFinding.OwnerValidation.OWNER_VALID,
                LegacyTempArtifactFinding.OwnerValidation.OWNER_VALID,
                Optional.of(digest('a')),
                Optional.of(digest('a')),
                "Byte-identical stale non-authoritative debris"
        );
        return new SplitSnapshotRecoveryAnalysisInput(
                LegacySplitRecoverySchema.CURRENT_VERSION,
                WORLD,
                PLATFORM,
                sources(WORLD),
                39872L,
                39084L,
                "butchercraft:snapshot/clock",
                digest('1'),
                "butchercraft:snapshot/scheduler",
                digest('2'),
                assessments,
                ordinary,
                List.of(childTen),
                List.of(planningBlock(false)),
                List.of(),
                List.of(),
                List.of(temp),
                false
        );
    }

    private static HistoricalCoordinationProof terminalProof(
            int sequence,
            HistoricalCoordinationProof.TerminalOutcome outcome,
            boolean ownerResult,
            boolean conflicting
    ) {
        Optional<String> owner = ownerResult ? Optional.of("butchercraft:workstation") : Optional.empty();
        Optional<String> result = ownerResult
                ? Optional.of("butchercraft:workstation_result/patty_" + sequence)
                : Optional.empty();
        Optional<String> resultDigest = ownerResult ? Optional.of(digest('5')) : Optional.empty();
        Optional<String> workstation = ownerResult ? Optional.of(workstationIdentity(1)) : Optional.empty();
        OptionalLong generation = ownerResult ? OptionalLong.of(1L) : OptionalLong.empty();
        return new HistoricalCoordinationProof(
                operationIdentity(sequence),
                Optional.of(SimulationWorkId.of(operationIdentity(sequence) + "/work")),
                Optional.of(invocation(sequence)),
                Optional.of(effect(sequence)),
                Optional.of("butchercraft:execution_authorization/patty_" + sequence),
                Optional.of(digest('3')),
                Optional.of("butchercraft:domain_effect/patty_" + sequence),
                Optional.of("butchercraft:handler_contract/patty_former"),
                owner,
                result,
                resultDigest,
                Optional.of("butchercraft:execution_result/patty_" + sequence),
                Optional.of(digest('4')),
                workstation,
                generation,
                Optional.of(machineRunIdentity()),
                OptionalLong.of(sequence),
                Optional.of(outcome),
                true,
                OptionalLong.of(39084L + sequence),
                OptionalLong.of(39094L + sequence),
                ownerResult ? List.of(
                        new HistoricalCoordinationSourceEvidence(
                                "butchercraft:execution",
                                "butchercraft:snapshot/execution",
                                digest('3'),
                                "butchercraft:evidence/execution_" + sequence,
                                digest('4')
                        ),
                        new HistoricalCoordinationSourceEvidence(
                                "butchercraft:workstation",
                                "butchercraft:snapshot/workstation",
                                digest('4'),
                                "butchercraft:evidence/workstation_" + sequence,
                                digest('5')
                        )
                ) : List.of(new HistoricalCoordinationSourceEvidence(
                        "butchercraft:execution",
                        "butchercraft:snapshot/execution",
                        digest('3'),
                        "butchercraft:evidence/execution_" + sequence,
                        digest('4')
                )),
                conflicting
        );
    }

    private static OrdinaryWorkReconstructionProof ordinaryProof(
            boolean complete,
            boolean acknowledgementComplete,
            boolean conflicting
    ) {
        return ordinaryProof(complete, acknowledgementComplete, conflicting, 1);
    }

    private static OrdinaryWorkReconstructionProof ordinaryProof(
            boolean complete,
            boolean acknowledgementComplete,
            boolean conflicting,
            int sequence
    ) {
        return new OrdinaryWorkReconstructionProof(
                "butchercraft:ordinary_work_candidate/" + sequence,
                Optional.of(SimulationWorkId.of(operationIdentity(sequence) + "/work")),
                complete ? OptionalLong.of(sequence) : OptionalLong.empty(),
                complete ? Optional.of(digest('6')) : Optional.empty(),
                complete ? Optional.of("butchercraft:handler_contract/patty_former") : Optional.empty(),
                complete ? Optional.of("butchercraft:scheduler_stage/processing") : Optional.empty(),
                complete ? Optional.of("butchercraft:retry_policy/none") : Optional.empty(),
                complete ? Optional.of(invocation(sequence)) : Optional.empty(),
                complete ? Optional.of(effect(sequence)) : Optional.empty(),
                complete ? Optional.of("butchercraft:workstation_result/patty_" + sequence) : Optional.empty(),
                complete ? Optional.of(HistoricalCoordinationProof.TerminalOutcome.SUCCEEDED) : Optional.empty(),
                complete ? OptionalLong.of(39084L + sequence) : OptionalLong.empty(),
                complete ? OptionalLong.of(39094L + sequence) : OptionalLong.empty(),
                acknowledgementComplete,
                conflicting
        );
    }

    private static PlanningRecoveryAuthorityBlock planningBlock(boolean closureProven) {
        return PlanningRecoveryAuthorityBlock.unresolvedNonRepeatable(
                "butchercraft:planning_work/eligible_39601",
                39601L,
                39601L,
                planningEvidence(),
                PlanningRecoveryAuthorityBlock.DependencyScope.BOUNDED_AUTHORITY_SCOPE,
                List.of("butchercraft:planning", "butchercraft:production"),
                closureProven
        );
    }

    private static List<PlanningRecoveryAuthorityBlock.EvidenceReference> planningEvidence() {
        return List.of(new PlanningRecoveryAuthorityBlock.EvidenceReference(
                "butchercraft:planning",
                "butchercraft:evidence/planning_39601",
                digest('8')
        ));
    }

    private static MaterialHandlingRecoveryReference material(MaterialHandlingRecoveryReference.Status status) {
        return new MaterialHandlingRecoveryReference(
                "butchercraft:material_transfer/recovery_fixture",
                "butchercraft:material_transfer_lifecycle/in_transit",
                digest('7'),
                digest('7'),
                workstationIdentity(3),
                workstationIdentity(1),
                digest('8'),
                status
        );
    }

    private static List<RecoverySourceSnapshot> sources(WorldIdentityRootReference world) {
        return List.of(
                source(CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER, 1, List.of(1),
                        "butchercraft:snapshot/clock", digest('1'), 39872L, world),
                source(CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER, 2, List.of(1, 2),
                        "butchercraft:snapshot/scheduler", digest('2'), 39084L, world),
                source(CheckpointOwnerId.of("butchercraft:execution"), 1, List.of(1),
                        "butchercraft:snapshot/execution", digest('3'), 39872L, world),
                source(CheckpointOwnerId.of("butchercraft:workstation"), 1, List.of(1),
                        "butchercraft:snapshot/workstation", digest('4'), 39872L, world),
                source(CheckpointOwnerId.of("butchercraft:planning"), 1, List.of(1),
                        "butchercraft:snapshot/planning", digest('8'), 39601L, world),
                source(LegacySplitRecoveryParticipants.MATERIAL_HANDLING, 2, List.of(1, 2),
                        "butchercraft:snapshot/material_handling", digest('7'), 39872L, world),
                source(LegacySplitRecoveryParticipants.PRODUCTION, 1, List.of(1),
                        "butchercraft:snapshot/production", digest('9'), 39872L, world),
                source(LegacySplitRecoveryParticipants.TRANSACTIONS, 1, List.of(1),
                        "butchercraft:snapshot/transactions", digest('a'), 39872L, world),
                source(LegacySplitRecoveryParticipants.INVENTORY, 1, List.of(1),
                        "butchercraft:snapshot/inventory", digest('b'), 39872L, world),
                source(LegacySplitRecoveryParticipants.BUSINESS_RUNTIME, 1, List.of(1),
                        "butchercraft:snapshot/business_runtime", digest('c'), 39872L, world),
                source(LegacySplitRecoveryParticipants.WORKFORCE, 1, List.of(1),
                        "butchercraft:snapshot/workforce", digest('d'), 39872L, world),
                source(LegacySplitRecoveryParticipants.GOODS, 1, List.of(1),
                        "butchercraft:snapshot/goods", digest('e'), 39872L, world),
                source(LegacySplitRecoveryParticipants.ECONOMIC_ACTORS, 1, List.of(1),
                        "butchercraft:snapshot/economic_actors", digest('f'), 39872L, world),
                source(LegacySplitRecoveryParticipants.ORDERS, 1, List.of(1),
                        "butchercraft:snapshot/orders", digest('0'), 39872L, world),
                source(LegacySplitRecoveryParticipants.CONTRACTS, 1, List.of(1),
                        "butchercraft:snapshot/contracts", digest('1'), 39872L, world),
                source(LegacySplitRecoveryParticipants.PLAYER_IDENTITY, 1, List.of(1),
                        "butchercraft:snapshot/player_identity", digest('2'), 39872L, world)
        );
    }

    private static RecoverySourceSnapshot source(
            CheckpointOwnerId owner,
            int schema,
            List<Integer> supported,
            String identity,
            String contentDigest,
            long tick,
            WorldIdentityRootReference world
    ) {
        return new RecoverySourceSnapshot(
                owner, schema, supported, identity, contentDigest, 1L, tick, world,
                List.of("butchercraft:configuration/recovery_fixture"), true, Optional.empty()
        );
    }

    private static SplitSnapshotRecoveryAnalysisInput reorder(SplitSnapshotRecoveryAnalysisInput source) {
        List<RecoverySourceSnapshot> snapshots = reversed(source.sourceSnapshots());
        List<HistoricalCoordinationAssessment> assessments = reversed(source.historicalAssessments());
        List<OrdinaryWorkReconstructionProof> ordinary = reversed(source.ordinaryWorkProofs());
        return copy(source, snapshots, assessments, ordinary,
                reversed(source.materialHandlingReferences()), reversed(source.replacementWorkstationConflicts()));
    }

    private static SplitSnapshotRecoveryAnalysisInput withLegacyFinding(
            SplitSnapshotRecoveryAnalysisInput source,
            LegacyTempArtifactFinding finding
    ) {
        return new SplitSnapshotRecoveryAnalysisInput(
                source.schemaVersion(), source.worldIdentityRoot(), source.platformDeterminismManifest(),
                source.sourceSnapshots(), source.authoritativeClockTick(),
                source.schedulerLastNormallyFinalizedTick(), source.clockSnapshotIdentity(),
                source.clockSnapshotDigest(), source.schedulerSnapshotIdentity(),
                source.schedulerSnapshotDigest(), source.historicalAssessments(),
                source.ordinaryWorkProofs(), source.preservedAuthorizedWork(), List.of(),
                source.materialHandlingReferences(), source.replacementWorkstationConflicts(),
                List.of(finding), source.coherentCheckpointAvailable()
        );
    }

    private static SplitSnapshotRecoveryAnalysisInput copy(
            SplitSnapshotRecoveryAnalysisInput source,
            List<RecoverySourceSnapshot> snapshots,
            List<HistoricalCoordinationAssessment> assessments,
            List<OrdinaryWorkReconstructionProof> ordinary,
            List<MaterialHandlingRecoveryReference> material,
            List<ReplacementWorkstationConflict> replacements
    ) {
        return new SplitSnapshotRecoveryAnalysisInput(
                source.schemaVersion(), source.worldIdentityRoot(), source.platformDeterminismManifest(), snapshots,
                source.authoritativeClockTick(), source.schedulerLastNormallyFinalizedTick(),
                source.clockSnapshotIdentity(), source.clockSnapshotDigest(), source.schedulerSnapshotIdentity(),
                source.schedulerSnapshotDigest(), assessments, ordinary, source.preservedAuthorizedWork(),
                source.planningAuthorityBlocks(), material, replacements, source.legacyTempFindings(),
                source.coherentCheckpointAvailable()
        );
    }

    private static <T> List<T> reversed(List<T> values) {
        List<T> copy = new ArrayList<>(values);
        Collections.reverse(copy);
        return copy;
    }

    private static SchedulerInvocationIdentity invocation(int sequence) {
        return SchedulerInvocationIdentity.of(
                "butchercraft:scheduler_invocation/v2/" + hex(sequence)
        );
    }

    private static SchedulerEffectIdentity effect(int sequence) {
        return SchedulerEffectIdentity.of(
                "butchercraft:scheduler_effect_identity/v2/" + hex(sequence + 16)
        );
    }

    private static String operationIdentity(int sequence) {
        return "butchercraft:execution_operation/v1/" + hex(sequence + 32);
    }

    private static String machineRunIdentity() {
        return "butchercraft:machine_run/v1/" + hex(48);
    }

    private static String childIdentity(int sequence) {
        return "butchercraft:machine_run_child/v1/" + hex(sequence + 64);
    }

    private static String workstationIdentity(int generation) {
        return "butchercraft:workstation_instance/v1/" + hex(generation + 80);
    }

    private static String hex(int value) {
        return "%064x".formatted(value);
    }

    static String digest(char value) {
        return "sha256:" + String.valueOf(value).repeat(64);
    }

    static RecoveryOperatorEvidence operator(RecoveryOperatorAuthority authority) {
        return new RecoveryOperatorEvidence(
                "butchercraft:operator/test",
                authority,
                "butchercraft:operator_evidence/test",
                digest('c')
        );
    }
}
