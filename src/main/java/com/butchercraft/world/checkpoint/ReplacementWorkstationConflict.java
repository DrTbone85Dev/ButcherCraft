package com.butchercraft.world.checkpoint;

import java.util.Objects;

public record ReplacementWorkstationConflict(
        String endpointKeyIdentity,
        String historicalInstanceIdentity,
        long historicalGeneration,
        String currentInstanceIdentity,
        long currentGeneration,
        String evidenceContentDigest
) implements Comparable<ReplacementWorkstationConflict> {
    public ReplacementWorkstationConflict {
        endpointKeyIdentity = CheckpointValidation.id(endpointKeyIdentity, "workstationEndpointKeyIdentity");
        historicalInstanceIdentity = CheckpointValidation.id(
                historicalInstanceIdentity,
                "historicalWorkstationInstanceIdentity"
        );
        historicalGeneration = CheckpointValidation.positive(historicalGeneration, "historicalGeneration");
        currentInstanceIdentity = CheckpointValidation.id(
                currentInstanceIdentity,
                "currentWorkstationInstanceIdentity"
        );
        currentGeneration = CheckpointValidation.positive(currentGeneration, "currentGeneration");
        evidenceContentDigest = CheckpointValidation.digest(
                evidenceContentDigest,
                "replacementConflictEvidenceDigest"
        );
        if (historicalInstanceIdentity.equals(currentInstanceIdentity)) {
            throw new IllegalArgumentException("Replacement conflict requires distinct Workstation instances");
        }
    }

    @Override
    public int compareTo(ReplacementWorkstationConflict other) {
        return endpointKeyIdentity.compareTo(Objects.requireNonNull(other, "other").endpointKeyIdentity);
    }
}
