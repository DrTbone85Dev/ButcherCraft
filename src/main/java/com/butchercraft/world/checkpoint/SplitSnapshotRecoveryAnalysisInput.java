package com.butchercraft.world.checkpoint;

import com.butchercraft.world.planning.PlanningRecoveryAuthorityBlock;
import com.butchercraft.world.simulation.scheduler.HistoricalCoordinationAssessment;
import com.butchercraft.world.simulation.scheduler.OrdinaryWorkReconstructionProof;

import java.util.List;
import java.util.Objects;

public record SplitSnapshotRecoveryAnalysisInput(
        int schemaVersion,
        WorldIdentityRootReference worldIdentityRoot,
        PlatformDeterminismManifestReference platformDeterminismManifest,
        List<RecoverySourceSnapshot> sourceSnapshots,
        long authoritativeClockTick,
        long schedulerLastNormallyFinalizedTick,
        String clockSnapshotIdentity,
        String clockSnapshotDigest,
        String schedulerSnapshotIdentity,
        String schedulerSnapshotDigest,
        List<HistoricalCoordinationAssessment> historicalAssessments,
        List<OrdinaryWorkReconstructionProof> ordinaryWorkProofs,
        List<PreservedAuthorizedWork> preservedAuthorizedWork,
        List<PlanningRecoveryAuthorityBlock> planningAuthorityBlocks,
        List<MaterialHandlingRecoveryReference> materialHandlingReferences,
        List<ReplacementWorkstationConflict> replacementWorkstationConflicts,
        List<LegacyTempArtifactFinding> legacyTempFindings,
        boolean coherentCheckpointAvailable
) {
    public SplitSnapshotRecoveryAnalysisInput {
        schemaVersion = CheckpointValidation.positive(schemaVersion, "legacySplitRecoverySchemaVersion");
        worldIdentityRoot = Objects.requireNonNull(worldIdentityRoot, "worldIdentityRoot");
        platformDeterminismManifest = Objects.requireNonNull(
                platformDeterminismManifest,
                "platformDeterminismManifest"
        );
        sourceSnapshots = sorted(sourceSnapshots, "sourceSnapshots");
        authoritativeClockTick = CheckpointValidation.nonNegative(
                authoritativeClockTick,
                "authoritativeClockTick"
        );
        schedulerLastNormallyFinalizedTick = CheckpointValidation.nonNegative(
                schedulerLastNormallyFinalizedTick,
                "schedulerLastNormallyFinalizedTick"
        );
        clockSnapshotIdentity = CheckpointValidation.id(clockSnapshotIdentity, "clockSnapshotIdentity");
        clockSnapshotDigest = CheckpointValidation.digest(clockSnapshotDigest, "clockSnapshotDigest");
        schedulerSnapshotIdentity = CheckpointValidation.id(
                schedulerSnapshotIdentity,
                "schedulerSnapshotIdentity"
        );
        schedulerSnapshotDigest = CheckpointValidation.digest(
                schedulerSnapshotDigest,
                "schedulerSnapshotDigest"
        );
        historicalAssessments = sorted(historicalAssessments, "historicalAssessments");
        ordinaryWorkProofs = sorted(ordinaryWorkProofs, "ordinaryWorkProofs");
        preservedAuthorizedWork = sorted(preservedAuthorizedWork, "preservedAuthorizedWork");
        planningAuthorityBlocks = sorted(planningAuthorityBlocks, "planningAuthorityBlocks");
        materialHandlingReferences = sorted(materialHandlingReferences, "materialHandlingReferences");
        replacementWorkstationConflicts = sorted(
                replacementWorkstationConflicts,
                "replacementWorkstationConflicts"
        );
        legacyTempFindings = sorted(legacyTempFindings, "legacyTempFindings");
    }

    private static <T extends Comparable<? super T>> List<T> sorted(List<T> values, String label) {
        return Objects.requireNonNull(values, label).stream()
                .map(value -> Objects.requireNonNull(value, label + " entry"))
                .sorted()
                .toList();
    }
}
