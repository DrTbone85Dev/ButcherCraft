package com.butchercraft.world.checkpoint;

import com.butchercraft.world.planning.PlanningRecoveryAuthorityBlock;
import com.butchercraft.world.simulation.scheduler.HistoricalCoordinationAcknowledgement;
import com.butchercraft.world.simulation.scheduler.OrdinaryWorkReconstructionProof;
import com.butchercraft.world.simulation.scheduler.SchedulerRecoveryDiscontinuity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Immutable read-only plan. Its existence grants no publication authority. */
public record SplitSnapshotRecoveryPlan(
        int schemaVersion,
        LegacySplitRecoveryIdentity recoveryIdentity,
        String analysisDigest,
        WorldIdentityRootReference worldIdentityRoot,
        PlatformDeterminismManifestReference platformDeterminismManifest,
        List<RecoverySourceSnapshot> sourceSnapshots,
        long authoritativeClockTick,
        long schedulerLastNormallyFinalizedTick,
        List<HistoricalCoordinationAcknowledgement> historicalAcknowledgements,
        List<OrdinaryWorkReconstructionProof> ordinaryWorkProofs,
        Optional<SchedulerRecoveryDiscontinuity> recoveryDiscontinuity,
        OptionalLong nextNormalSchedulerAdmissionTick,
        List<PreservedAuthorizedWork> preservedAuthorizedWork,
        List<RecoveryAuthorityBlock> authorityBlocks,
        List<PlanningRecoveryAuthorityBlock> planningAuthorityBlocks,
        List<MaterialHandlingRecoveryReference> materialHandlingReferences,
        List<ReplacementWorkstationConflict> replacementWorkstationConflicts,
        List<LegacyTempArtifactFinding> legacyTempFindings,
        Eligibility eligibility,
        List<RecoveryIssue> issues,
        boolean coherentCheckpointAvailable,
        boolean operatorAuthorizationRequired,
        boolean recoveryPublished
) {
    public SplitSnapshotRecoveryPlan {
        if (schemaVersion != LegacySplitRecoverySchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported split-snapshot recovery plan schema");
        }
        recoveryIdentity = Objects.requireNonNull(recoveryIdentity, "recoveryIdentity");
        analysisDigest = CheckpointValidation.digest(analysisDigest, "recoveryAnalysisDigest");
        worldIdentityRoot = Objects.requireNonNull(worldIdentityRoot, "worldIdentityRoot");
        platformDeterminismManifest = Objects.requireNonNull(
                platformDeterminismManifest,
                "platformDeterminismManifest"
        );
        sourceSnapshots = List.copyOf(sourceSnapshots);
        authoritativeClockTick = CheckpointValidation.nonNegative(
                authoritativeClockTick,
                "authoritativeClockTick"
        );
        schedulerLastNormallyFinalizedTick = CheckpointValidation.nonNegative(
                schedulerLastNormallyFinalizedTick,
                "schedulerLastNormallyFinalizedTick"
        );
        historicalAcknowledgements = historicalAcknowledgements.stream().sorted().toList();
        ordinaryWorkProofs = ordinaryWorkProofs.stream().sorted().toList();
        recoveryDiscontinuity = Objects.requireNonNull(recoveryDiscontinuity, "recoveryDiscontinuity");
        nextNormalSchedulerAdmissionTick = Objects.requireNonNull(
                nextNormalSchedulerAdmissionTick,
                "nextNormalSchedulerAdmissionTick"
        );
        preservedAuthorizedWork = preservedAuthorizedWork.stream().sorted().toList();
        authorityBlocks = authorityBlocks.stream().sorted().toList();
        planningAuthorityBlocks = planningAuthorityBlocks.stream().sorted().toList();
        materialHandlingReferences = materialHandlingReferences.stream().sorted().toList();
        replacementWorkstationConflicts = replacementWorkstationConflicts.stream().sorted().toList();
        legacyTempFindings = legacyTempFindings.stream().sorted().toList();
        eligibility = Objects.requireNonNull(eligibility, "eligibility");
        issues = issues.stream().sorted().toList();
        if (recoveryPublished) {
            throw new IllegalArgumentException("R1 recovery plans cannot publish recovery");
        }
        if (recoveryDiscontinuity.isPresent() != nextNormalSchedulerAdmissionTick.isPresent()) {
            throw new IllegalArgumentException("Discontinuity and next admission boundary must be present together");
        }
        if (recoveryDiscontinuity.isPresent()
                && !recoveryDiscontinuity.orElseThrow().recoveryIdentityReference().equals(recoveryIdentity.value())) {
            throw new IllegalArgumentException("Discontinuity references a different Recovery Identity");
        }
    }

    public boolean publicationEligible() {
        return eligibility == Eligibility.RECOVERABLE_PROOF_COMPLETE
                || eligibility == Eligibility.RECOVERABLE_WITH_AUTHORITY_BLOCKS;
    }

    public enum Eligibility {
        NOT_SPLIT,
        RECOVERABLE_PROOF_COMPLETE,
        RECOVERABLE_WITH_AUTHORITY_BLOCKS,
        RECOVERY_BLOCKED,
        UNKNOWN_OUTCOME,
        UNSUPPORTED_SCHEMA
    }
}
