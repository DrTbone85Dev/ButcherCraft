package com.butchercraft.development.checkpoint;

import com.butchercraft.workstation.checkpoint.WorkstationCheckpointProjectionSnapshot;
import com.butchercraft.workstation.checkpoint.WorkstationCheckpointRestorabilityReport;
import com.butchercraft.world.checkpoint.CheckpointGenerationId;
import com.butchercraft.world.checkpoint.CheckpointPublicationOutcome;

import java.util.Objects;

public record LegacyWorkstationProjectionSuccessorReport(
        CheckpointGenerationId predecessorGenerationId,
        String predecessorManifestDigest,
        CheckpointGenerationId successorGenerationId,
        String successorManifestDigest,
        CheckpointPublicationOutcome publicationOutcome,
        int participantCount,
        int preservedOwnerPayloadCount,
        WorkstationCheckpointProjectionSnapshot workstationSnapshot,
        WorkstationCheckpointRestorabilityReport historicalWorkstationReport,
        WorkstationCheckpointRestorabilityReport successorWorkstationReport,
        long successorCheckpointBytes,
        long publicationNanos
) {
    public LegacyWorkstationProjectionSuccessorReport {
        predecessorGenerationId = Objects.requireNonNull(predecessorGenerationId, "predecessorGenerationId");
        predecessorManifestDigest = requireText(predecessorManifestDigest, "predecessorManifestDigest");
        successorGenerationId = Objects.requireNonNull(successorGenerationId, "successorGenerationId");
        successorManifestDigest = requireText(successorManifestDigest, "successorManifestDigest");
        publicationOutcome = Objects.requireNonNull(publicationOutcome, "publicationOutcome");
        workstationSnapshot = Objects.requireNonNull(workstationSnapshot, "workstationSnapshot");
        historicalWorkstationReport = Objects.requireNonNull(historicalWorkstationReport,
                "historicalWorkstationReport");
        successorWorkstationReport = Objects.requireNonNull(successorWorkstationReport,
                "successorWorkstationReport");
        if (participantCount <= 0 || preservedOwnerPayloadCount < 0
                || successorCheckpointBytes <= 0L || publicationNanos < 0L) {
            throw new IllegalArgumentException("Successor publication measurements are invalid");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
