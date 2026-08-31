package com.butchercraft.world.checkpoint;

import java.util.Objects;

/** Read-only exact custody representation; Checkpoint Recovery cannot change it. */
public record MaterialHandlingRecoveryReference(
        String transferIdentity,
        String lifecycleIdentity,
        String exactTransferStackContentDigest,
        String exactCustodyContentDigest,
        String sourceWorkstationInstanceIdentity,
        String destinationWorkstationInstanceIdentity,
        String endpointEvidenceContentDigest,
        Status status
) implements Comparable<MaterialHandlingRecoveryReference> {
    public MaterialHandlingRecoveryReference {
        transferIdentity = CheckpointValidation.id(transferIdentity, "materialTransferIdentity");
        lifecycleIdentity = CheckpointValidation.id(lifecycleIdentity, "materialTransferLifecycleIdentity");
        exactTransferStackContentDigest = CheckpointValidation.digest(
                exactTransferStackContentDigest,
                "exactTransferStackContentDigest"
        );
        exactCustodyContentDigest = CheckpointValidation.digest(
                exactCustodyContentDigest,
                "exactCustodyContentDigest"
        );
        sourceWorkstationInstanceIdentity = CheckpointValidation.id(
                sourceWorkstationInstanceIdentity,
                "materialSourceWorkstationInstanceIdentity"
        );
        destinationWorkstationInstanceIdentity = CheckpointValidation.id(
                destinationWorkstationInstanceIdentity,
                "materialDestinationWorkstationInstanceIdentity"
        );
        endpointEvidenceContentDigest = CheckpointValidation.digest(
                endpointEvidenceContentDigest,
                "materialEndpointEvidenceContentDigest"
        );
        status = Objects.requireNonNull(status, "status");
    }

    @Override
    public int compareTo(MaterialHandlingRecoveryReference other) {
        return transferIdentity.compareTo(Objects.requireNonNull(other, "other").transferIdentity);
    }

    public enum Status {
        PROOF_COMPLETE,
        RECOVERY_BLOCKED,
        UNKNOWN_OUTCOME
    }
}
