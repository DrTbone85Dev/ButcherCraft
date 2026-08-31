package com.butchercraft.world.checkpoint;

import com.butchercraft.world.planning.PlanningRecoveryAuthorityBlock;
import com.butchercraft.world.simulation.scheduler.HistoricalCoordinationAcknowledgement;
import com.butchercraft.world.simulation.scheduler.HistoricalCoordinationAssessment;
import com.butchercraft.world.simulation.scheduler.OrdinaryWorkReconstructionProof;
import com.butchercraft.world.simulation.scheduler.SchedulerRecoveryDiscontinuity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/** Deterministic read-only analysis. This class has no publication or owner-mutation dependency. */
public final class SplitSnapshotRecoveryAnalyzer {
    private static final String DISCONTINUITY_REASON =
            "butchercraft:recovery_reason/legacy_clock_scheduler_split";

    public SplitSnapshotRecoveryPlan analyze(SplitSnapshotRecoveryAnalysisInput input) {
        Objects.requireNonNull(input, "input");
        List<RecoveryIssue> issues = new ArrayList<>();
        validateInput(input, issues);

        List<HistoricalCoordinationAcknowledgement> acknowledgements = new ArrayList<>();
        for (HistoricalCoordinationAssessment assessment : input.historicalAssessments()) {
            switch (assessment.eligibility()) {
                case ACKNOWLEDGEMENT_ELIGIBLE -> {
                    HistoricalCoordinationAcknowledgement acknowledgement =
                            assessment.acknowledgement().orElseThrow();
                    List<String> missingSources = missingAnalyzedSources(
                            acknowledgement,
                            input.sourceSnapshots()
                    );
                    if (missingSources.isEmpty()) {
                        acknowledgements.add(acknowledgement);
                    } else {
                        issues.add(issue(
                                RecoveryIssue.Code.MISSING_HISTORICAL_EVIDENCE,
                                acknowledgement.executionOperationIdentity(),
                                String.join("; ", missingSources)
                        ));
                    }
                }
                case INCOMPLETE_EVIDENCE -> issues.add(issue(
                        RecoveryIssue.Code.MISSING_HISTORICAL_EVIDENCE,
                        assessment.executionOperationIdentity(),
                        String.join("; ", assessment.reasons())
                ));
                case CONFLICTING_EVIDENCE -> issues.add(issue(
                        RecoveryIssue.Code.CONFLICTING_HISTORICAL_EVIDENCE,
                        assessment.executionOperationIdentity(),
                        String.join("; ", assessment.reasons())
                ));
            }
        }
        for (OrdinaryWorkReconstructionProof proof : input.ordinaryWorkProofs()) {
            if (proof.eligibility() == OrdinaryWorkReconstructionProof.Eligibility.NOT_PROVABLE) {
                issues.add(issue(
                        RecoveryIssue.Code.ORDINARY_WORK_NOT_PROVABLE,
                        proof.candidateIdentity(),
                        "Ordinary Scheduler Work requires inferred consequential fields"
                ));
            }
        }
        validateMaterialHandling(input.materialHandlingReferences(), issues);
        input.replacementWorkstationConflicts().forEach(conflict -> issues.add(issue(
                RecoveryIssue.Code.REPLACEMENT_WORKSTATION_CONFLICT,
                conflict.historicalInstanceIdentity(),
                "Replacement Workstation " + conflict.currentInstanceIdentity()
                        + " cannot inherit historical endpoint authority"
        )));
        validateLegacyArtifacts(input.legacyTempFindings(), issues);

        List<RecoveryAuthorityBlock> authorityBlocks = authorityBlocks(input);
        List<RecoveryIssue> sortedIssues = issues.stream().distinct().sorted().toList();
        List<HistoricalCoordinationAcknowledgement> sortedAcknowledgements = acknowledgements.stream()
                .distinct().sorted().toList();
        SplitSnapshotRecoveryPlan.Eligibility eligibility = eligibility(
                input,
                sortedIssues,
                authorityBlocks
        );
        LegacySplitRecoveryIdentity recoveryIdentity = recoveryIdentity(
                input,
                sortedAcknowledgements,
                authorityBlocks,
                sortedIssues,
                eligibility
        );

        Optional<SchedulerRecoveryDiscontinuity> discontinuity = Optional.empty();
        OptionalLong nextAdmission = OptionalLong.empty();
        if (input.authoritativeClockTick() > input.schedulerLastNormallyFinalizedTick()) {
            List<String> unresolved = unresolvedWorkReferences(input);
            SchedulerRecoveryDiscontinuity value = SchedulerRecoveryDiscontinuity.create(
                    input.schedulerLastNormallyFinalizedTick(),
                    input.authoritativeClockTick(),
                    input.schedulerSnapshotIdentity(),
                    input.schedulerSnapshotDigest(),
                    input.clockSnapshotIdentity(),
                    input.clockSnapshotDigest(),
                    recoveryIdentity.value(),
                    DISCONTINUITY_REASON,
                    sortedAcknowledgements.stream()
                            .map(HistoricalCoordinationAcknowledgement::acknowledgementIdentity)
                            .toList(),
                    unresolved
            );
            discontinuity = Optional.of(value);
            nextAdmission = OptionalLong.of(value.nextNormalAdmissionTick());
        }
        String analysisDigest = analysisDigest(
                recoveryIdentity,
                eligibility,
                discontinuity,
                authorityBlocks,
                sortedIssues
        );
        return new SplitSnapshotRecoveryPlan(
                LegacySplitRecoverySchema.CURRENT_VERSION,
                recoveryIdentity,
                analysisDigest,
                input.worldIdentityRoot(),
                input.platformDeterminismManifest(),
                input.sourceSnapshots(),
                input.authoritativeClockTick(),
                input.schedulerLastNormallyFinalizedTick(),
                sortedAcknowledgements,
                input.ordinaryWorkProofs(),
                discontinuity,
                nextAdmission,
                input.preservedAuthorizedWork(),
                authorityBlocks,
                input.planningAuthorityBlocks(),
                input.materialHandlingReferences(),
                input.replacementWorkstationConflicts(),
                input.legacyTempFindings(),
                eligibility,
                sortedIssues,
                input.coherentCheckpointAvailable(),
                eligibility != SplitSnapshotRecoveryPlan.Eligibility.NOT_SPLIT,
                false
        );
    }

    private void validateInput(SplitSnapshotRecoveryAnalysisInput input, List<RecoveryIssue> issues) {
        if (input.schemaVersion() != LegacySplitRecoverySchema.CURRENT_VERSION) {
            issues.add(issue(
                    RecoveryIssue.Code.UNSUPPORTED_RECOVERY_SCHEMA,
                    LegacySplitRecoverySchema.POLICY_IDENTITY,
                    "Unsupported split-snapshot recovery schema: " + input.schemaVersion()
            ));
        }
        Map<CheckpointOwnerId, RecoverySourceSnapshot> byOwner = new HashMap<>();
        for (RecoverySourceSnapshot source : input.sourceSnapshots()) {
            RecoverySourceSnapshot existing = byOwner.putIfAbsent(source.ownerId(), source);
            if (existing != null && !existing.equals(source)) {
                issues.add(issue(
                        RecoveryIssue.Code.DUPLICATE_OWNER_SOURCE,
                        source.snapshotIdentity(),
                        "Conflicting source snapshots exist for owner " + source.ownerId().value()
                ));
            }
            if (!source.ownerSchemaSupported()) {
                issues.add(issue(
                        RecoveryIssue.Code.UNSUPPORTED_OWNER_SCHEMA,
                        source.snapshotIdentity(),
                        "Owner " + source.ownerId().value() + " does not support schema "
                                + source.ownerSchemaVersion()
                ));
            }
            if (!source.worldIdentityRoot().equals(input.worldIdentityRoot())) {
                issues.add(issue(
                        RecoveryIssue.Code.WORLD_IDENTITY_MISMATCH,
                        source.snapshotIdentity(),
                        "Source snapshot belongs to a different World Identity root"
                ));
            }
            if (!source.ownerValidated()) {
                issues.add(issue(
                        RecoveryIssue.Code.OWNER_VALIDATION_FAILED,
                        source.snapshotIdentity(),
                        source.validationFailure().orElse("Owner source validation failed")
                ));
            }
            if (source.representedSimulationTick() > input.authoritativeClockTick()) {
                issues.add(issue(
                        RecoveryIssue.Code.OWNER_SNAPSHOT_AFTER_CLOCK,
                        source.snapshotIdentity(),
                        "Owner snapshot claims state after authoritative Clock boundary"
                ));
            }
        }
        validateNamedSource(
                byOwner.get(CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER),
                input.clockSnapshotIdentity(),
                input.clockSnapshotDigest(),
                CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER,
                issues
        );
        validateNamedSource(
                byOwner.get(CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER),
                input.schedulerSnapshotIdentity(),
                input.schedulerSnapshotDigest(),
                CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER,
                issues
        );
        if (input.schedulerLastNormallyFinalizedTick() > input.authoritativeClockTick()) {
            issues.add(issue(
                    RecoveryIssue.Code.CLOCK_OLDER_THAN_SCHEDULER,
                    input.schedulerSnapshotIdentity(),
                    "Scheduler is newer than Clock; ADR-02A does not authorize inferred reconciliation"
            ));
        }
    }

    private void validateNamedSource(
            RecoverySourceSnapshot source,
            String expectedIdentity,
            String expectedDigest,
            CheckpointOwnerId owner,
            List<RecoveryIssue> issues
    ) {
        if (source == null
                || !source.snapshotIdentity().equals(expectedIdentity)
                || !source.contentDigest().equals(expectedDigest)) {
            issues.add(issue(
                    RecoveryIssue.Code.OWNER_VALIDATION_FAILED,
                    expectedIdentity,
                    "Exact " + owner.value() + " source snapshot identity/digest is not present"
            ));
        }
    }

    private void validateMaterialHandling(
            List<MaterialHandlingRecoveryReference> references,
            List<RecoveryIssue> issues
    ) {
        for (MaterialHandlingRecoveryReference reference : references) {
            switch (reference.status()) {
                case PROOF_COMPLETE -> {
                }
                case RECOVERY_BLOCKED -> issues.add(issue(
                        RecoveryIssue.Code.MATERIAL_HANDLING_RECOVERY_BLOCKED,
                        reference.transferIdentity(),
                        "Material Handling owner reports recovery blocked"
                ));
                case UNKNOWN_OUTCOME -> issues.add(issue(
                        RecoveryIssue.Code.MATERIAL_HANDLING_UNKNOWN_OUTCOME,
                        reference.transferIdentity(),
                        "Material Handling custody outcome is not provable"
                ));
            }
        }
    }

    private List<String> missingAnalyzedSources(
            HistoricalCoordinationAcknowledgement acknowledgement,
            List<RecoverySourceSnapshot> sources
    ) {
        return acknowledgement.sourceEvidence().stream()
                .filter(evidence -> sources.stream().noneMatch(source ->
                        source.ownerId().value().equals(evidence.ownerSubsystemId())
                                && source.snapshotIdentity().equals(evidence.sourceSnapshotIdentity())
                                && source.contentDigest().equals(evidence.sourceSnapshotDigest())))
                .map(evidence -> "Exact source snapshot is absent for evidence " + evidence.evidenceIdentity())
                .sorted()
                .toList();
    }

    private void validateLegacyArtifacts(List<LegacyTempArtifactFinding> findings, List<RecoveryIssue> issues) {
        for (LegacyTempArtifactFinding finding : findings) {
            String reference = legacyArtifactReference(finding);
            switch (finding.classification()) {
                case DIFFERING_REQUIRES_OWNER_ANALYSIS -> issues.add(issue(
                        RecoveryIssue.Code.LEGACY_ARTIFACT_REQUIRES_OWNER_ANALYSIS,
                        reference,
                        finding.detail()
                ));
                case MALFORMED -> issues.add(issue(
                        RecoveryIssue.Code.LEGACY_ARTIFACT_MALFORMED,
                        reference,
                        finding.detail()
                ));
                case UNSUPPORTED_SCHEMA -> issues.add(issue(
                        RecoveryIssue.Code.LEGACY_ARTIFACT_UNSUPPORTED_SCHEMA,
                        reference,
                        finding.detail()
                ));
                case IDENTITY_CONFLICT -> issues.add(issue(
                        RecoveryIssue.Code.LEGACY_ARTIFACT_IDENTITY_CONFLICT,
                        reference,
                        finding.detail()
                ));
                case IDENTICAL_STALE_DEBRIS, ABSENT, CURRENT_UNIQUE_ATTEMPT -> {
                }
            }
        }
    }

    private List<RecoveryAuthorityBlock> authorityBlocks(SplitSnapshotRecoveryAnalysisInput input) {
        List<RecoveryAuthorityBlock> blocks = new ArrayList<>();
        for (PlanningRecoveryAuthorityBlock planning : input.planningAuthorityBlocks()) {
            RecoveryAuthorityBlock.Scope scope = switch (planning.dependencyScope()) {
                case SPECIFIC_DEPENDENT_WORK -> RecoveryAuthorityBlock.Scope.SPECIFIC_DEPENDENT_WORK;
                case BOUNDED_AUTHORITY_SCOPE -> RecoveryAuthorityBlock.Scope.BOUNDED_AUTHORITY_SCOPE;
                case WHOLE_WORLD_MUTATION -> RecoveryAuthorityBlock.Scope.WHOLE_WORLD_MUTATION;
            };
            blocks.add(RecoveryAuthorityBlock.create(
                    CheckpointOwnerId.of("butchercraft:planning"),
                    planning.blockIdentity(),
                    planning.contentDigest(),
                    planning.reasonIdentity(),
                    scope,
                    planning.dependentAuthorityIdentities().isEmpty()
                            ? List.of(planning.planningWorkIdentity())
                            : planning.dependentAuthorityIdentities(),
                    planning.dependencyClosureProven()
            ));
        }
        for (MaterialHandlingRecoveryReference material : input.materialHandlingReferences()) {
            if (material.status() == MaterialHandlingRecoveryReference.Status.PROOF_COMPLETE) continue;
            String reason = material.status() == MaterialHandlingRecoveryReference.Status.UNKNOWN_OUTCOME
                    ? "butchercraft:recovery_reason/material_handling_unknown_outcome"
                    : "butchercraft:recovery_reason/material_handling_recovery_required";
            blocks.add(RecoveryAuthorityBlock.create(
                    CheckpointOwnerId.of("butchercraft:material_handling"),
                    material.transferIdentity(),
                    material.endpointEvidenceContentDigest(),
                    reason,
                    RecoveryAuthorityBlock.Scope.BOUNDED_AUTHORITY_SCOPE,
                    List.of(material.transferIdentity()),
                    true
            ));
        }
        for (LegacyTempArtifactFinding finding : input.legacyTempFindings()) {
            if (finding.classification()
                    != LegacyTempArtifactFinding.Classification.DIFFERING_REQUIRES_OWNER_ANALYSIS
                    && finding.classification() != LegacyTempArtifactFinding.Classification.MALFORMED) {
                continue;
            }
            String reference = legacyArtifactReference(finding);
            String reason = finding.classification() == LegacyTempArtifactFinding.Classification.MALFORMED
                    ? "butchercraft:recovery_reason/legacy_owner_artifact_malformed"
                    : "butchercraft:recovery_reason/legacy_owner_artifact_differs";
            blocks.add(RecoveryAuthorityBlock.create(
                    finding.ownerId(),
                    reference,
                    finding.artifactContentDigest()
                            .or(() -> finding.finalContentDigest())
                            .orElse(CheckpointValidation.zeroDigest()),
                    reason,
                    RecoveryAuthorityBlock.Scope.WHOLE_WORLD_MUTATION,
                    List.of(reference),
                    false
            ));
        }
        return blocks.stream().distinct().sorted().toList();
    }

    private SplitSnapshotRecoveryPlan.Eligibility eligibility(
            SplitSnapshotRecoveryAnalysisInput input,
            List<RecoveryIssue> issues,
            List<RecoveryAuthorityBlock> authorityBlocks
    ) {
        Set<RecoveryIssue.Code> codes = new HashSet<>();
        issues.forEach(issue -> codes.add(issue.code()));
        if (containsAny(codes,
                RecoveryIssue.Code.UNSUPPORTED_RECOVERY_SCHEMA,
                RecoveryIssue.Code.UNSUPPORTED_OWNER_SCHEMA,
                RecoveryIssue.Code.LEGACY_ARTIFACT_UNSUPPORTED_SCHEMA)) {
            return SplitSnapshotRecoveryPlan.Eligibility.UNSUPPORTED_SCHEMA;
        }
        if (containsAny(codes,
                RecoveryIssue.Code.WORLD_IDENTITY_MISMATCH,
                RecoveryIssue.Code.OWNER_VALIDATION_FAILED,
                RecoveryIssue.Code.DUPLICATE_OWNER_SOURCE,
                RecoveryIssue.Code.OWNER_SNAPSHOT_AFTER_CLOCK,
                RecoveryIssue.Code.CLOCK_OLDER_THAN_SCHEDULER,
                RecoveryIssue.Code.CONFLICTING_HISTORICAL_EVIDENCE,
                RecoveryIssue.Code.REPLACEMENT_WORKSTATION_CONFLICT,
                RecoveryIssue.Code.LEGACY_ARTIFACT_IDENTITY_CONFLICT,
                RecoveryIssue.Code.MATERIAL_HANDLING_RECOVERY_BLOCKED)) {
            return SplitSnapshotRecoveryPlan.Eligibility.RECOVERY_BLOCKED;
        }
        if (containsAny(codes,
                RecoveryIssue.Code.MISSING_HISTORICAL_EVIDENCE,
                RecoveryIssue.Code.ORDINARY_WORK_NOT_PROVABLE,
                RecoveryIssue.Code.MATERIAL_HANDLING_UNKNOWN_OUTCOME)) {
            return SplitSnapshotRecoveryPlan.Eligibility.UNKNOWN_OUTCOME;
        }
        if (!authorityBlocks.isEmpty() || containsAny(codes,
                RecoveryIssue.Code.LEGACY_ARTIFACT_REQUIRES_OWNER_ANALYSIS,
                RecoveryIssue.Code.LEGACY_ARTIFACT_MALFORMED)) {
            return SplitSnapshotRecoveryPlan.Eligibility.RECOVERABLE_WITH_AUTHORITY_BLOCKS;
        }
        boolean noRecoveryContent = input.authoritativeClockTick() == input.schedulerLastNormallyFinalizedTick()
                && input.historicalAssessments().isEmpty()
                && input.preservedAuthorizedWork().isEmpty()
                && input.planningAuthorityBlocks().isEmpty()
                && input.materialHandlingReferences().isEmpty();
        return noRecoveryContent
                ? SplitSnapshotRecoveryPlan.Eligibility.NOT_SPLIT
                : SplitSnapshotRecoveryPlan.Eligibility.RECOVERABLE_PROOF_COMPLETE;
    }

    private boolean containsAny(Set<RecoveryIssue.Code> actual, RecoveryIssue.Code... candidates) {
        for (RecoveryIssue.Code candidate : candidates) {
            if (actual.contains(candidate)) return true;
        }
        return false;
    }

    private LegacySplitRecoveryIdentity recoveryIdentity(
            SplitSnapshotRecoveryAnalysisInput input,
            List<HistoricalCoordinationAcknowledgement> acknowledgements,
            List<RecoveryAuthorityBlock> authorityBlocks,
            List<RecoveryIssue> issues,
            SplitSnapshotRecoveryPlan.Eligibility eligibility
    ) {
        CheckpointCanonicalDigest digest = CheckpointCanonicalDigest.create(
                "butchercraft:legacy_split_recovery_identity"
        ).add(LegacySplitRecoverySchema.CURRENT_VERSION)
                .add(LegacySplitRecoverySchema.POLICY_IDENTITY)
                .add(input.worldIdentityRoot().identity())
                .add(input.worldIdentityRoot().schemaVersion())
                .add(input.worldIdentityRoot().rootDigest())
                .add(input.platformDeterminismManifest().identity())
                .add(input.platformDeterminismManifest().schemaVersion())
                .add(input.platformDeterminismManifest().manifestDigest())
                .add(input.authoritativeClockTick())
                .add(input.schedulerLastNormallyFinalizedTick())
                .add(input.clockSnapshotIdentity())
                .add(input.clockSnapshotDigest())
                .add(input.schedulerSnapshotIdentity())
                .add(input.schedulerSnapshotDigest())
                .add(input.coherentCheckpointAvailable())
                .add(eligibility.name())
                .add(input.sourceSnapshots().size());
        input.sourceSnapshots().forEach(source -> add(digest, source));
        digest.add(acknowledgements.size());
        acknowledgements.forEach(value -> digest.add(value.acknowledgementIdentity()).add(value.contentDigest()));
        digest.add(input.ordinaryWorkProofs().size());
        input.ordinaryWorkProofs().forEach(value -> add(digest, value));
        digest.add(input.preservedAuthorizedWork().size());
        input.preservedAuthorizedWork().forEach(value -> add(digest, value));
        digest.add(input.planningAuthorityBlocks().size());
        input.planningAuthorityBlocks().forEach(value -> digest.add(value.blockIdentity()).add(value.contentDigest()));
        digest.add(authorityBlocks.size());
        authorityBlocks.forEach(value -> digest.add(value.blockIdentity()).add(value.ownerBlockContentDigest()));
        digest.add(input.materialHandlingReferences().size());
        input.materialHandlingReferences().forEach(value -> add(digest, value));
        digest.add(input.replacementWorkstationConflicts().size());
        input.replacementWorkstationConflicts().forEach(value -> add(digest, value));
        digest.add(input.legacyTempFindings().size());
        input.legacyTempFindings().forEach(value -> add(digest, value));
        digest.add(issues.size());
        issues.forEach(value -> digest.add(value.code().name())
                .add(value.referenceIdentity()).add(value.detail()));
        return LegacySplitRecoveryIdentity.fromDigest(digest.finish());
    }

    private void add(CheckpointCanonicalDigest digest, RecoverySourceSnapshot source) {
        digest.add(source.ownerId().value())
                .add(source.ownerSchemaVersion())
                .add(source.snapshotIdentity())
                .add(source.contentDigest())
                .add(source.ownerRevisionOrSequence())
                .add(source.representedSimulationTick())
                .add(source.worldIdentityRoot().identity())
                .add(source.worldIdentityRoot().schemaVersion())
                .add(source.worldIdentityRoot().rootDigest())
                .add(source.ownerValidated())
                .add(source.validationFailure().orElse(""))
                .add(source.configurationIdentities().size());
        source.configurationIdentities().forEach(digest::add);
    }

    private void add(CheckpointCanonicalDigest digest, OrdinaryWorkReconstructionProof proof) {
        digest.add(proof.candidateIdentity())
                .add(proof.eligibility().name())
                .add(proof.workIdentity().map(value -> value.value()).orElse(""))
                .add(proof.authoritativeSubmissionSequence().orElse(-1L))
                .add(proof.payloadDigest().orElse(""))
                .add(proof.handlerContractIdentity().orElse(""))
                .add(proof.stageIdentity().orElse(""))
                .add(proof.retryPolicyIdentity().orElse(""))
                .add(proof.invocationIdentity().map(value -> value.value()).orElse(""))
                .add(proof.effectIdentity().map(value -> value.value()).orElse(""))
                .add(proof.ownerResultIdentity().orElse(""))
                .add(proof.terminalOutcome().map(Enum::name).orElse(""))
                .add(proof.startedSimulationTick().orElse(-1L))
                .add(proof.completedSimulationTick().orElse(-1L))
                .add(proof.acknowledgementProofComplete())
                .add(proof.conflictingEvidence());
    }

    private void add(CheckpointCanonicalDigest digest, PreservedAuthorizedWork work) {
        digest.add(work.executionOperationIdentity())
                .add(work.machineRunIdentity())
                .add(work.machineRunGeneration())
                .add(work.childIdentity())
                .add(work.childSequence())
                .add(work.workstationInstanceIdentity())
                .add(work.workstationInstanceGeneration())
                .add(work.authorizationContentDigest())
                .add(work.restartPolicyIdentity())
                .add(work.sourceEvidenceIdentities().size());
        work.sourceEvidenceIdentities().forEach(digest::add);
    }

    private void add(CheckpointCanonicalDigest digest, MaterialHandlingRecoveryReference material) {
        digest.add(material.transferIdentity())
                .add(material.lifecycleIdentity())
                .add(material.exactTransferStackContentDigest())
                .add(material.exactCustodyContentDigest())
                .add(material.sourceWorkstationInstanceIdentity())
                .add(material.destinationWorkstationInstanceIdentity())
                .add(material.endpointEvidenceContentDigest())
                .add(material.status().name());
    }

    private void add(CheckpointCanonicalDigest digest, ReplacementWorkstationConflict conflict) {
        digest.add(conflict.endpointKeyIdentity())
                .add(conflict.historicalInstanceIdentity())
                .add(conflict.historicalGeneration())
                .add(conflict.currentInstanceIdentity())
                .add(conflict.currentGeneration())
                .add(conflict.evidenceContentDigest());
    }

    private void add(CheckpointCanonicalDigest digest, LegacyTempArtifactFinding finding) {
        digest.add(finding.ownerId().value())
                .add(finding.targetFileName())
                .add(finding.relation().name())
                .add(finding.classification().name())
                .add(finding.finalValidation().name())
                .add(finding.artifactValidation().name())
                .add(finding.finalContentDigest().orElse(""))
                .add(finding.artifactContentDigest().orElse(""));
    }

    private String analysisDigest(
            LegacySplitRecoveryIdentity recoveryIdentity,
            SplitSnapshotRecoveryPlan.Eligibility eligibility,
            Optional<SchedulerRecoveryDiscontinuity> discontinuity,
            List<RecoveryAuthorityBlock> blocks,
            List<RecoveryIssue> issues
    ) {
        CheckpointCanonicalDigest digest = CheckpointCanonicalDigest.create(
                "butchercraft:legacy_split_recovery_analysis"
        ).add(LegacySplitRecoverySchema.CURRENT_VERSION)
                .add(recoveryIdentity.value())
                .add(eligibility.name())
                .add(discontinuity.isPresent())
                .add(discontinuity.map(SchedulerRecoveryDiscontinuity::contentDigest).orElse(""))
                .add(blocks.size());
        blocks.forEach(value -> digest.add(value.blockIdentity()));
        digest.add(issues.size());
        issues.forEach(value -> digest.add(value.code().name())
                .add(value.referenceIdentity()).add(value.detail()));
        return digest.finish();
    }

    private List<String> unresolvedWorkReferences(SplitSnapshotRecoveryAnalysisInput input) {
        List<String> references = new ArrayList<>();
        input.historicalAssessments().stream()
                .filter(value -> value.eligibility()
                        != HistoricalCoordinationAssessment.Eligibility.ACKNOWLEDGEMENT_ELIGIBLE)
                .map(HistoricalCoordinationAssessment::executionOperationIdentity)
                .forEach(references::add);
        input.ordinaryWorkProofs().stream()
                .filter(value -> value.eligibility() == OrdinaryWorkReconstructionProof.Eligibility.NOT_PROVABLE)
                .map(OrdinaryWorkReconstructionProof::candidateIdentity)
                .forEach(references::add);
        input.planningAuthorityBlocks().stream()
                .map(PlanningRecoveryAuthorityBlock::planningWorkIdentity)
                .forEach(references::add);
        return references.stream().distinct().sorted().toList();
    }

    private RecoveryIssue issue(RecoveryIssue.Code code, String reference, String detail) {
        return new RecoveryIssue(code, reference, detail);
    }

    private String legacyArtifactReference(LegacyTempArtifactFinding finding) {
        String digest = CheckpointCanonicalDigest.create("butchercraft:legacy_temp_artifact")
                .add(finding.ownerId().value())
                .add(finding.targetFileName())
                .add(finding.relation().name())
                .add(finding.finalContentDigest().orElse(""))
                .add(finding.artifactContentDigest().orElse(""))
                .finish();
        return "butchercraft:legacy_temp_artifact/"
                + digest.substring("sha256:".length());
    }
}
