package com.butchercraft.world.materialhandling;

import com.butchercraft.workstation.endpoint.WorkstationEndpointObservation;
import com.butchercraft.workstation.endpoint.WorkstationEndpointOwnerResult;
import com.butchercraft.workstation.endpoint.WorkstationEndpointPreparation;
import com.butchercraft.workstation.endpoint.WorkstationEndpointStackPayload;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record MaterialTransferTerminalEvidence(
        String materialIdentity,
        int quantity,
        String stackContentIdentity,
        Optional<MaterialTransferEvidenceReference> sourceObservation,
        Optional<MaterialTransferEvidenceReference> sourcePreparation,
        Optional<MaterialTransferEvidenceReference> sourceResult,
        Optional<MaterialTransferEvidenceReference> destinationObservation,
        Optional<MaterialTransferEvidenceReference> destinationPreparation,
        Optional<MaterialTransferEvidenceReference> destinationResult,
        Optional<MaterialTransferEvidenceReference> returnObservation,
        Optional<MaterialTransferEvidenceReference> returnPreparation,
        Optional<MaterialTransferEvidenceReference> returnResult,
        String contentDigest
) {
    public MaterialTransferTerminalEvidence {
        materialIdentity = MaterialHandlingValidation.id(materialIdentity, "terminal material identity");
        quantity = MaterialHandlingValidation.positive(quantity, "terminal material quantity");
        stackContentIdentity = MaterialHandlingValidation.digest(
                stackContentIdentity,
                "terminal stack content identity"
        );
        sourceObservation = Objects.requireNonNull(sourceObservation, "sourceObservation");
        sourcePreparation = Objects.requireNonNull(sourcePreparation, "sourcePreparation");
        sourceResult = Objects.requireNonNull(sourceResult, "sourceResult");
        destinationObservation = Objects.requireNonNull(destinationObservation, "destinationObservation");
        destinationPreparation = Objects.requireNonNull(destinationPreparation, "destinationPreparation");
        destinationResult = Objects.requireNonNull(destinationResult, "destinationResult");
        returnObservation = Objects.requireNonNull(returnObservation, "returnObservation");
        returnPreparation = Objects.requireNonNull(returnPreparation, "returnPreparation");
        returnResult = Objects.requireNonNull(returnResult, "returnResult");
        contentDigest = MaterialHandlingValidation.digest(contentDigest, "terminal evidence content digest");
        String expected = calculateDigest(
                materialIdentity,
                quantity,
                stackContentIdentity,
                sourceObservation,
                sourcePreparation,
                sourceResult,
                destinationObservation,
                destinationPreparation,
                destinationResult,
                returnObservation,
                returnPreparation,
                returnResult
        );
        if (!expected.equals(contentDigest)) {
            throw new IllegalArgumentException("Terminal Material Transfer evidence is not canonical");
        }
    }

    public static MaterialTransferTerminalEvidence create(
            String materialIdentity,
            int quantity,
            Optional<WorkstationEndpointObservation> sourceObservation,
            Optional<WorkstationEndpointPreparation> sourcePreparation,
            Optional<WorkstationEndpointOwnerResult> sourceResult,
            Optional<WorkstationEndpointStackPayload> exactTransferStack,
            Optional<WorkstationEndpointStackPayload> inTransitCustody,
            Optional<WorkstationEndpointObservation> destinationObservation,
            Optional<WorkstationEndpointPreparation> destinationPreparation,
            Optional<WorkstationEndpointOwnerResult> destinationResult,
            Optional<WorkstationEndpointObservation> returnObservation,
            Optional<WorkstationEndpointPreparation> returnPreparation,
            Optional<WorkstationEndpointOwnerResult> returnResult
    ) {
        List<WorkstationEndpointStackPayload> payloads = java.util.stream.Stream.of(
                        sourceObservation.map(WorkstationEndpointObservation::exactEffectStack),
                        sourcePreparation.map(WorkstationEndpointPreparation::exactStack),
                        sourceResult.map(WorkstationEndpointOwnerResult::exactStack),
                        exactTransferStack,
                        inTransitCustody,
                        destinationObservation.map(WorkstationEndpointObservation::exactEffectStack),
                        destinationPreparation.map(WorkstationEndpointPreparation::exactStack),
                        destinationResult.map(WorkstationEndpointOwnerResult::exactStack),
                        returnObservation.map(WorkstationEndpointObservation::exactEffectStack),
                        returnPreparation.map(WorkstationEndpointPreparation::exactStack),
                        returnResult.map(WorkstationEndpointOwnerResult::exactStack)
                )
                .flatMap(Optional::stream)
                .toList();
        if (payloads.isEmpty()) {
            throw new IllegalArgumentException("Terminal Material Transfer lacks exact stack Content Identity");
        }
        WorkstationEndpointStackPayload exact = payloads.getFirst();
        if (exact.count() != quantity || payloads.stream().anyMatch(payload -> !payload.equals(exact))) {
            throw new IllegalArgumentException("Terminal Material Transfer stack evidence is inconsistent");
        }
        Optional<MaterialTransferEvidenceReference> sourceObservationReference = sourceObservation.map(
                MaterialTransferEvidenceReference::from
        );
        Optional<MaterialTransferEvidenceReference> sourcePreparationReference = sourcePreparation.map(
                MaterialTransferEvidenceReference::from
        );
        Optional<MaterialTransferEvidenceReference> sourceResultReference = sourceResult.map(
                MaterialTransferEvidenceReference::from
        );
        Optional<MaterialTransferEvidenceReference> destinationObservationReference = destinationObservation.map(
                MaterialTransferEvidenceReference::from
        );
        Optional<MaterialTransferEvidenceReference> destinationPreparationReference = destinationPreparation.map(
                MaterialTransferEvidenceReference::from
        );
        Optional<MaterialTransferEvidenceReference> destinationResultReference = destinationResult.map(
                MaterialTransferEvidenceReference::from
        );
        Optional<MaterialTransferEvidenceReference> returnObservationReference = returnObservation.map(
                MaterialTransferEvidenceReference::from
        );
        Optional<MaterialTransferEvidenceReference> returnPreparationReference = returnPreparation.map(
                MaterialTransferEvidenceReference::from
        );
        Optional<MaterialTransferEvidenceReference> returnResultReference = returnResult.map(
                MaterialTransferEvidenceReference::from
        );
        String digest = calculateDigest(
                materialIdentity,
                quantity,
                exact.contentDigest(),
                sourceObservationReference,
                sourcePreparationReference,
                sourceResultReference,
                destinationObservationReference,
                destinationPreparationReference,
                destinationResultReference,
                returnObservationReference,
                returnPreparationReference,
                returnResultReference
        );
        return new MaterialTransferTerminalEvidence(
                materialIdentity,
                quantity,
                exact.contentDigest(),
                sourceObservationReference,
                sourcePreparationReference,
                sourceResultReference,
                destinationObservationReference,
                destinationPreparationReference,
                destinationResultReference,
                returnObservationReference,
                returnPreparationReference,
                returnResultReference,
                digest
        );
    }

    private static String calculateDigest(
            String materialIdentity,
            int quantity,
            String stackContentIdentity,
            Optional<MaterialTransferEvidenceReference> sourceObservation,
            Optional<MaterialTransferEvidenceReference> sourcePreparation,
            Optional<MaterialTransferEvidenceReference> sourceResult,
            Optional<MaterialTransferEvidenceReference> destinationObservation,
            Optional<MaterialTransferEvidenceReference> destinationPreparation,
            Optional<MaterialTransferEvidenceReference> destinationResult,
            Optional<MaterialTransferEvidenceReference> returnObservation,
            Optional<MaterialTransferEvidenceReference> returnPreparation,
            Optional<MaterialTransferEvidenceReference> returnResult
    ) {
        MaterialHandlingDigest digest = MaterialHandlingDigest.create("butchercraft:material_transfer_terminal_evidence")
                .add(materialIdentity)
                .add(quantity)
                .add(stackContentIdentity);
        add(digest, sourceObservation);
        add(digest, sourcePreparation);
        add(digest, sourceResult);
        add(digest, destinationObservation);
        add(digest, destinationPreparation);
        add(digest, destinationResult);
        add(digest, returnObservation);
        add(digest, returnPreparation);
        add(digest, returnResult);
        return digest.finish();
    }

    private static void add(
            MaterialHandlingDigest digest,
            Optional<MaterialTransferEvidenceReference> reference
    ) {
        digest.add(reference.map(MaterialTransferEvidenceReference::evidenceIdentity).orElse(""));
        digest.add(reference.map(MaterialTransferEvidenceReference::contentDigest).orElse(""));
    }
}
