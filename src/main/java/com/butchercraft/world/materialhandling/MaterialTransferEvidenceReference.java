package com.butchercraft.world.materialhandling;

import com.butchercraft.workstation.endpoint.WorkstationEndpointObservation;
import com.butchercraft.workstation.endpoint.WorkstationEndpointOwnerResult;
import com.butchercraft.workstation.endpoint.WorkstationEndpointPreparation;

import java.util.Objects;

public record MaterialTransferEvidenceReference(
        String evidenceIdentity,
        String contentDigest
) {
    public MaterialTransferEvidenceReference {
        evidenceIdentity = MaterialHandlingValidation.id(evidenceIdentity, "evidence identity");
        contentDigest = MaterialHandlingValidation.digest(contentDigest, "evidence content digest");
    }

    public static MaterialTransferEvidenceReference from(WorkstationEndpointObservation observation) {
        Objects.requireNonNull(observation, "observation");
        return new MaterialTransferEvidenceReference(observation.evidenceIdentity(), observation.contentDigest());
    }

    public static MaterialTransferEvidenceReference from(WorkstationEndpointPreparation preparation) {
        Objects.requireNonNull(preparation, "preparation");
        return new MaterialTransferEvidenceReference(preparation.evidenceIdentity(), preparation.contentDigest());
    }

    public static MaterialTransferEvidenceReference from(WorkstationEndpointOwnerResult result) {
        Objects.requireNonNull(result, "result");
        return new MaterialTransferEvidenceReference(result.evidenceIdentity(), result.contentDigest());
    }
}
