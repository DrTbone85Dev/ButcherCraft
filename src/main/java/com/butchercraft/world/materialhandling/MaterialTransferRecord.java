package com.butchercraft.world.materialhandling;

import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind;
import com.butchercraft.workstation.endpoint.WorkstationEndpointObservation;
import com.butchercraft.workstation.endpoint.WorkstationEndpointOwnerResult;
import com.butchercraft.workstation.endpoint.WorkstationEndpointPreparation;
import com.butchercraft.workstation.endpoint.WorkstationEndpointStackPayload;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record MaterialTransferRecord(
        int schemaVersion,
        MaterialTransferId transferId,
        long sequence,
        String requestContentDigest,
        WorldIdentityRootIdentity worldIdentity,
        WorkstationEndpointReference source,
        WorkstationEndpointReference destination,
        String materialIdentity,
        int quantity,
        String assignmentTypeIdentity,
        Optional<String> employeeReference,
        String sourceInvocationIdentity,
        String destinationInvocationIdentity,
        String returnInvocationIdentity,
        String configurationIdentity,
        MaterialTransferLifecycle lifecycle,
        Optional<MaterialCustodyLocation> custodyLocation,
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
        Optional<WorkstationEndpointOwnerResult> returnResult,
        Optional<MaterialTransferTerminalEvidence> terminalEvidence,
        Optional<String> terminalDetail,
        long creationRevision,
        long lastUpdateRevision,
        String stateEvidenceIdentity,
        String stateContentDigest
) implements Comparable<MaterialTransferRecord> {
    private static final String EVIDENCE_PREFIX = "butchercraft:material_transfer_state/v1/";

    public MaterialTransferRecord {
        if (schemaVersion != MaterialHandlingSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Material Transfer schema version: " + schemaVersion);
        }
        transferId = Objects.requireNonNull(transferId, "transferId");
        sequence = MaterialHandlingValidation.positive(sequence, "material transfer sequence");
        requestContentDigest = MaterialHandlingValidation.digest(requestContentDigest, "request content digest");
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        source = Objects.requireNonNull(source, "source");
        destination = Objects.requireNonNull(destination, "destination");
        materialIdentity = MaterialHandlingValidation.id(materialIdentity, "material identity");
        quantity = MaterialHandlingValidation.positive(quantity, "material quantity");
        assignmentTypeIdentity = MaterialHandlingValidation.id(assignmentTypeIdentity, "assignment type identity");
        employeeReference = Objects.requireNonNull(employeeReference, "employeeReference")
                .map(value -> MaterialHandlingValidation.id(value, "employee reference"));
        sourceInvocationIdentity = MaterialHandlingValidation.id(sourceInvocationIdentity, "source invocation identity");
        destinationInvocationIdentity = MaterialHandlingValidation.id(
                destinationInvocationIdentity,
                "destination invocation identity"
        );
        returnInvocationIdentity = MaterialHandlingValidation.id(
                returnInvocationIdentity,
                "source-return invocation identity"
        );
        configurationIdentity = MaterialHandlingValidation.id(configurationIdentity, "configuration identity");
        lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        custodyLocation = Objects.requireNonNull(custodyLocation, "custodyLocation");
        sourceObservation = Objects.requireNonNull(sourceObservation, "sourceObservation");
        sourcePreparation = Objects.requireNonNull(sourcePreparation, "sourcePreparation");
        sourceResult = Objects.requireNonNull(sourceResult, "sourceResult");
        exactTransferStack = Objects.requireNonNull(exactTransferStack, "exactTransferStack");
        inTransitCustody = Objects.requireNonNull(inTransitCustody, "inTransitCustody");
        destinationObservation = Objects.requireNonNull(destinationObservation, "destinationObservation");
        destinationPreparation = Objects.requireNonNull(destinationPreparation, "destinationPreparation");
        destinationResult = Objects.requireNonNull(destinationResult, "destinationResult");
        returnObservation = Objects.requireNonNull(returnObservation, "returnObservation");
        returnPreparation = Objects.requireNonNull(returnPreparation, "returnPreparation");
        returnResult = Objects.requireNonNull(returnResult, "returnResult");
        terminalEvidence = Objects.requireNonNull(terminalEvidence, "terminalEvidence");
        terminalDetail = Objects.requireNonNull(terminalDetail, "terminalDetail")
                .map(value -> MaterialHandlingValidation.text(value, "terminal detail"));
        creationRevision = MaterialHandlingValidation.positive(creationRevision, "creation revision");
        lastUpdateRevision = MaterialHandlingValidation.positive(lastUpdateRevision, "last update revision");
        if (lastUpdateRevision < creationRevision) throw new IllegalArgumentException("Transfer revision regressed");
        stateEvidenceIdentity = MaterialHandlingValidation.id(stateEvidenceIdentity, "state evidence identity");
        stateContentDigest = MaterialHandlingValidation.digest(stateContentDigest, "state content digest");

        String expectedRequestDigest = calculateRequestDigest(
                worldIdentity,
                source,
                destination,
                materialIdentity,
                quantity,
                assignmentTypeIdentity,
                employeeReference,
                configurationIdentity
        );
        MaterialTransferId expectedId = MaterialTransferId.create(
                worldIdentity,
                sequence,
                expectedRequestDigest,
                configurationIdentity
        );
        if (!expectedId.equals(transferId)) throw new IllegalArgumentException("Material Transfer identity is not canonical");
        if (!requestContentDigest.equals(expectedRequestDigest)) {
            throw new IllegalArgumentException("Material Transfer request digest is not canonical");
        }
        if (!source.endpointKey().dimensionIdentity().equals(destination.endpointKey().dimensionIdentity())) {
            throw new IllegalArgumentException("Schema 1 Material Transfer endpoints must share one dimension");
        }
        validateEvidenceBindings(
                source,
                destination,
                sourceInvocationIdentity,
                destinationInvocationIdentity,
                returnInvocationIdentity,
                sourceObservation,
                sourcePreparation,
                sourceResult,
                exactTransferStack,
                inTransitCustody,
                destinationObservation,
                destinationPreparation,
                destinationResult,
                returnObservation,
                returnPreparation,
                returnResult,
                terminalEvidence,
                materialIdentity,
                quantity
        );
        validateLifecycleEvidence(
                lifecycle,
                custodyLocation,
                sourceObservation,
                sourcePreparation,
                sourceResult,
                exactTransferStack,
                inTransitCustody,
                destinationObservation,
                destinationPreparation,
                destinationResult,
                returnObservation,
                returnPreparation,
                returnResult,
                terminalEvidence,
                terminalDetail
        );
        String expectedDigest = calculateStateDigest(
                transferId,
                requestContentDigest,
                lifecycle,
                custodyLocation,
                sourceObservation,
                sourcePreparation,
                sourceResult,
                exactTransferStack,
                inTransitCustody,
                destinationObservation,
                destinationPreparation,
                destinationResult,
                returnObservation,
                returnPreparation,
                returnResult,
                terminalEvidence,
                terminalDetail,
                lastUpdateRevision
        );
        if (!expectedDigest.equals(stateContentDigest)
                || !(EVIDENCE_PREFIX + MaterialHandlingDigest.suffix(expectedDigest)).equals(stateEvidenceIdentity)) {
            throw new IllegalArgumentException("Material Transfer state evidence is not canonical");
        }
    }

    public static MaterialTransferRecord requested(
            WorldIdentityRootIdentity worldIdentity,
            long sequence,
            WorkstationEndpointReference source,
            WorkstationEndpointReference destination,
            String materialIdentity,
            int quantity,
            String assignmentTypeIdentity,
            Optional<String> employeeReference,
            String configurationIdentity,
            long ownerRevision
    ) {
        String requestDigest = calculateRequestDigest(
                worldIdentity,
                source,
                destination,
                materialIdentity,
                quantity,
                assignmentTypeIdentity,
                employeeReference,
                configurationIdentity
        );
        MaterialTransferId id = MaterialTransferId.create(
                worldIdentity,
                sequence,
                requestDigest,
                configurationIdentity
        );
        String suffix = MaterialHandlingDigest.suffix(MaterialHandlingDigest.create("butchercraft:material_invocation")
                .add(id.value()).add(requestDigest).finish());
        return create(
                id,
                sequence,
                requestDigest,
                worldIdentity,
                source,
                destination,
                materialIdentity,
                quantity,
                assignmentTypeIdentity,
                employeeReference,
                "butchercraft:material_handling_invocation/v1/" + suffix + "/source",
                "butchercraft:material_handling_invocation/v1/" + suffix + "/destination",
                "butchercraft:material_handling_invocation/v1/" + suffix + "/return",
                configurationIdentity,
                MaterialTransferLifecycle.REQUESTED,
                Optional.of(MaterialCustodyLocation.SOURCE_WORKSTATION),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ownerRevision,
                ownerRevision
        );
    }

    public MaterialTransferRecord transition(
            MaterialTransferLifecycle target,
            long ownerRevision,
            Optional<MaterialCustodyLocation> nextCustodyLocation,
            Optional<WorkstationEndpointObservation> nextSourceObservation,
            Optional<WorkstationEndpointPreparation> nextSourcePreparation,
            Optional<WorkstationEndpointOwnerResult> nextSourceResult,
            Optional<WorkstationEndpointStackPayload> nextExactStack,
            Optional<WorkstationEndpointStackPayload> nextCustody,
            Optional<WorkstationEndpointObservation> nextDestinationObservation,
            Optional<WorkstationEndpointPreparation> nextDestinationPreparation,
            Optional<WorkstationEndpointOwnerResult> nextDestinationResult,
            Optional<String> nextTerminalDetail
    ) {
        return transition(
                target,
                ownerRevision,
                nextCustodyLocation,
                nextSourceObservation,
                nextSourcePreparation,
                nextSourceResult,
                nextExactStack,
                nextCustody,
                nextDestinationObservation,
                nextDestinationPreparation,
                nextDestinationResult,
                returnObservation,
                returnPreparation,
                returnResult,
                nextTerminalDetail
        );
    }

    public MaterialTransferRecord transition(
            MaterialTransferLifecycle target,
            long ownerRevision,
            Optional<MaterialCustodyLocation> nextCustodyLocation,
            Optional<WorkstationEndpointObservation> nextSourceObservation,
            Optional<WorkstationEndpointPreparation> nextSourcePreparation,
            Optional<WorkstationEndpointOwnerResult> nextSourceResult,
            Optional<WorkstationEndpointStackPayload> nextExactStack,
            Optional<WorkstationEndpointStackPayload> nextCustody,
            Optional<WorkstationEndpointObservation> nextDestinationObservation,
            Optional<WorkstationEndpointPreparation> nextDestinationPreparation,
            Optional<WorkstationEndpointOwnerResult> nextDestinationResult,
            Optional<WorkstationEndpointObservation> nextReturnObservation,
            Optional<WorkstationEndpointPreparation> nextReturnPreparation,
            Optional<WorkstationEndpointOwnerResult> nextReturnResult,
            Optional<String> nextTerminalDetail
    ) {
        if (!lifecycle.canTransitionTo(target)) {
            throw new IllegalStateException("Illegal Material Transfer transition: " + lifecycle + " -> " + target);
        }
        Optional<MaterialTransferTerminalEvidence> nextTerminalEvidence = Optional.empty();
        if (target == MaterialTransferLifecycle.COMPLETED || target == MaterialTransferLifecycle.CANCELLED) {
            nextTerminalEvidence = terminalEvidence.isPresent()
                    ? terminalEvidence
                    : Optional.of(MaterialTransferTerminalEvidence.create(
                            materialIdentity,
                            quantity,
                            nextSourceObservation,
                            nextSourcePreparation,
                            nextSourceResult,
                            nextExactStack,
                            nextCustody,
                            nextDestinationObservation,
                            nextDestinationPreparation,
                            nextDestinationResult,
                            nextReturnObservation,
                            nextReturnPreparation,
                            nextReturnResult
                    ));
            nextSourceObservation = Optional.empty();
            nextSourcePreparation = Optional.empty();
            nextSourceResult = Optional.empty();
            nextExactStack = Optional.empty();
            nextCustody = Optional.empty();
            nextDestinationObservation = Optional.empty();
            nextDestinationPreparation = Optional.empty();
            nextDestinationResult = Optional.empty();
            nextReturnObservation = Optional.empty();
            nextReturnPreparation = Optional.empty();
            nextReturnResult = Optional.empty();
        }
        return create(
                transferId,
                sequence,
                requestContentDigest,
                worldIdentity,
                source,
                destination,
                materialIdentity,
                quantity,
                assignmentTypeIdentity,
                employeeReference,
                sourceInvocationIdentity,
                destinationInvocationIdentity,
                returnInvocationIdentity,
                configurationIdentity,
                target,
                nextCustodyLocation,
                nextSourceObservation,
                nextSourcePreparation,
                nextSourceResult,
                nextExactStack,
                nextCustody,
                nextDestinationObservation,
                nextDestinationPreparation,
                nextDestinationResult,
                nextReturnObservation,
                nextReturnPreparation,
                nextReturnResult,
                nextTerminalEvidence,
                nextTerminalDetail,
                creationRevision,
                ownerRevision
        );
    }

    private static MaterialTransferRecord create(
            MaterialTransferId transferId,
            long sequence,
            String requestContentDigest,
            WorldIdentityRootIdentity worldIdentity,
            WorkstationEndpointReference source,
            WorkstationEndpointReference destination,
            String materialIdentity,
            int quantity,
            String assignmentTypeIdentity,
            Optional<String> employeeReference,
            String sourceInvocationIdentity,
            String destinationInvocationIdentity,
            String returnInvocationIdentity,
            String configurationIdentity,
            MaterialTransferLifecycle lifecycle,
            Optional<MaterialCustodyLocation> custodyLocation,
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
            Optional<WorkstationEndpointOwnerResult> returnResult,
            Optional<MaterialTransferTerminalEvidence> terminalEvidence,
            Optional<String> terminalDetail,
            long creationRevision,
            long lastUpdateRevision
    ) {
        String digest = calculateStateDigest(
                transferId,
                requestContentDigest,
                lifecycle,
                custodyLocation,
                sourceObservation,
                sourcePreparation,
                sourceResult,
                exactTransferStack,
                inTransitCustody,
                destinationObservation,
                destinationPreparation,
                destinationResult,
                returnObservation,
                returnPreparation,
                returnResult,
                terminalEvidence,
                terminalDetail,
                lastUpdateRevision
        );
        return new MaterialTransferRecord(
                MaterialHandlingSchema.CURRENT_VERSION,
                transferId,
                sequence,
                requestContentDigest,
                worldIdentity,
                source,
                destination,
                materialIdentity,
                quantity,
                assignmentTypeIdentity,
                employeeReference,
                sourceInvocationIdentity,
                destinationInvocationIdentity,
                returnInvocationIdentity,
                configurationIdentity,
                lifecycle,
                custodyLocation,
                sourceObservation,
                sourcePreparation,
                sourceResult,
                exactTransferStack,
                inTransitCustody,
                destinationObservation,
                destinationPreparation,
                destinationResult,
                returnObservation,
                returnPreparation,
                returnResult,
                terminalEvidence,
                terminalDetail,
                creationRevision,
                lastUpdateRevision,
                EVIDENCE_PREFIX + MaterialHandlingDigest.suffix(digest),
                digest
        );
    }

    private static void validateEvidenceBindings(
            WorkstationEndpointReference source,
            WorkstationEndpointReference destination,
            String sourceInvocationIdentity,
            String destinationInvocationIdentity,
            String returnInvocationIdentity,
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
            Optional<WorkstationEndpointOwnerResult> returnResult,
            Optional<MaterialTransferTerminalEvidence> terminalEvidence,
            String materialIdentity,
            int quantity
    ) {
        sourceObservation.ifPresent(observation -> validateObservation(
                observation,
                source,
                WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL,
                "source"
        ));
        destinationObservation.ifPresent(observation -> validateObservation(
                observation,
                destination,
                WorkstationEndpointEffectKind.DESTINATION_DEPOSIT,
                "destination"
        ));
        returnObservation.ifPresent(observation -> validateObservation(
                observation,
                source,
                WorkstationEndpointEffectKind.SOURCE_RETURN,
                "source return"
        ));
        sourcePreparation.ifPresent(preparation -> validatePreparation(
                preparation,
                sourceObservation.orElseThrow(() -> new IllegalArgumentException("Source preparation lacks observation")),
                sourceInvocationIdentity,
                "source"
        ));
        destinationPreparation.ifPresent(preparation -> validatePreparation(
                preparation,
                destinationObservation.orElseThrow(
                        () -> new IllegalArgumentException("Destination preparation lacks observation")
                ),
                destinationInvocationIdentity,
                "destination"
        ));
        returnPreparation.ifPresent(preparation -> validatePreparation(
                preparation,
                returnObservation.orElseThrow(
                        () -> new IllegalArgumentException("Source-return preparation lacks observation")
                ),
                returnInvocationIdentity,
                "source return"
        ));
        sourceResult.ifPresent(result -> validateResult(
                result,
                sourcePreparation.orElseThrow(() -> new IllegalArgumentException("Source result lacks preparation")),
                "source"
        ));
        destinationResult.ifPresent(result -> validateResult(
                result,
                destinationPreparation.orElseThrow(
                        () -> new IllegalArgumentException("Destination result lacks preparation")
                ),
                "destination"
        ));
        returnResult.ifPresent(result -> validateResult(
                result,
                returnPreparation.orElseThrow(
                        () -> new IllegalArgumentException("Source-return result lacks preparation")
                ),
                "source return"
        ));
        if (exactTransferStack.isPresent() && sourceObservation.isPresent()
                && !exactTransferStack.get().equals(sourceObservation.get().exactEffectStack())) {
            throw new IllegalArgumentException("Exact transfer stack differs from source observation");
        }
        if (inTransitCustody.isPresent() && !inTransitCustody.equals(exactTransferStack)) {
            throw new IllegalArgumentException("In-transit custody must equal the exact withdrawn ItemStack");
        }
        List<WorkstationEndpointStackPayload> evidencePayloads = java.util.stream.Stream.of(
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
        Optional<WorkstationEndpointStackPayload> canonicalPayload = exactTransferStack.isPresent()
                ? exactTransferStack
                : evidencePayloads.stream().findFirst();
        evidencePayloads.forEach(payload -> {
                    if (payload.count() != quantity
                            || canonicalPayload.filter(payload::equals).isEmpty()) {
                        throw new IllegalArgumentException("Material Transfer exact stack evidence is inconsistent");
                    }
                });
        terminalEvidence.ifPresent(evidence -> {
            if (!evidence.materialIdentity().equals(materialIdentity) || evidence.quantity() != quantity) {
                throw new IllegalArgumentException("Terminal evidence does not bind the canonical material request");
            }
        });
    }

    private static void validateLifecycleEvidence(
            MaterialTransferLifecycle lifecycle,
            Optional<MaterialCustodyLocation> custodyLocation,
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
            Optional<WorkstationEndpointOwnerResult> returnResult,
            Optional<MaterialTransferTerminalEvidence> terminalEvidence,
            Optional<String> terminalDetail
    ) {
        Optional<MaterialCustodyLocation> expectedLocation = switch (lifecycle) {
            case REQUESTED, SOURCE_BOUND, SOURCE_WITHDRAW_PREPARED, CANCELLATION_RETURN_COMMITTED,
                    CANCELLED, FAILED ->
                    Optional.of(MaterialCustodyLocation.SOURCE_WORKSTATION);
            case SOURCE_WITHDRAW_COMMITTED, IN_TRANSIT, DESTINATION_BOUND, DESTINATION_DEPOSIT_PREPARED,
                    CANCELLATION_REQUESTED, CANCELLATION_RETURN_PREPARED ->
                    Optional.of(MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME);
            case DESTINATION_DEPOSIT_COMMITTED, COMPLETED ->
                    Optional.of(MaterialCustodyLocation.DESTINATION_WORKSTATION);
            case RECOVERY_REQUIRED -> custodyLocation;
            case UNKNOWN_OUTCOME -> Optional.empty();
        };
        if (!custodyLocation.equals(expectedLocation) || custodyLocation.isEmpty()
                && lifecycle != MaterialTransferLifecycle.UNKNOWN_OUTCOME) {
            throw new IllegalArgumentException("Material Transfer custody location does not match lifecycle evidence");
        }
        if (lifecycle == MaterialTransferLifecycle.COMPLETED || lifecycle == MaterialTransferLifecycle.CANCELLED) {
            MaterialTransferTerminalEvidence evidence = terminalEvidence.orElseThrow(
                    () -> new IllegalArgumentException("Terminal Material Transfer lacks collapsed evidence")
            );
            if (sourceObservation.isPresent() || sourcePreparation.isPresent() || sourceResult.isPresent()
                    || exactTransferStack.isPresent() || inTransitCustody.isPresent()
                    || destinationObservation.isPresent() || destinationPreparation.isPresent()
                    || destinationResult.isPresent() || returnObservation.isPresent()
                    || returnPreparation.isPresent() || returnResult.isPresent()) {
                throw new IllegalArgumentException("Terminal Material Transfer retains a redundant full stack payload");
            }
            if (evidence.sourceObservation().isEmpty()) {
                throw new IllegalArgumentException("Terminal Material Transfer lacks source Content Identity evidence");
            }
            if (lifecycle == MaterialTransferLifecycle.COMPLETED
                    && (evidence.sourceResult().isEmpty() || evidence.destinationResult().isEmpty())) {
                throw new IllegalArgumentException("Completed Material Transfer lacks endpoint owner-result references");
            }
            if (lifecycle == MaterialTransferLifecycle.CANCELLED && evidence.sourceResult().isPresent()
                    && evidence.returnResult().isEmpty()) {
                throw new IllegalArgumentException("Post-withdrawal cancellation lacks source-return evidence");
            }
            if (lifecycle == MaterialTransferLifecycle.CANCELLED && terminalDetail.isEmpty()) {
                throw new IllegalArgumentException("Cancelled Material Transfer requires a reason");
            }
            return;
        }
        if (terminalEvidence.isPresent()) {
            throw new IllegalArgumentException("Nonterminal Material Transfer contains terminal evidence");
        }
        boolean normalSourceBound = switch (lifecycle) {
            case SOURCE_BOUND, SOURCE_WITHDRAW_PREPARED, SOURCE_WITHDRAW_COMMITTED, IN_TRANSIT,
                    DESTINATION_BOUND, DESTINATION_DEPOSIT_PREPARED, DESTINATION_DEPOSIT_COMMITTED,
                    CANCELLATION_RETURN_PREPARED, CANCELLATION_RETURN_COMMITTED -> true;
            default -> false;
        };
        if (normalSourceBound && sourceObservation.isEmpty()) {
            throw new IllegalArgumentException("Bound Material Transfer lacks source observation");
        }
        if ((sourcePreparation.isPresent() || sourceResult.isPresent() || inTransitCustody.isPresent()
                || returnObservation.isPresent() || returnPreparation.isPresent() || returnResult.isPresent())
                && exactTransferStack.isEmpty()) {
            throw new IllegalArgumentException("Prepared Material Transfer lacks exact stack evidence");
        }
        if ((sourceResult.isPresent() || inTransitCustody.isPresent() || returnObservation.isPresent())
                && sourcePreparation.isEmpty()) {
            throw new IllegalArgumentException("Committed withdrawal lacks Workstation evidence");
        }
        boolean requiresTransitCustody = switch (lifecycle) {
            case IN_TRANSIT, DESTINATION_BOUND, DESTINATION_DEPOSIT_PREPARED,
                    CANCELLATION_RETURN_PREPARED -> true;
            case CANCELLATION_REQUESTED -> custodyLocation.filter(
                    MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME::equals
            ).isPresent();
            default -> false;
        };
        if (requiresTransitCustody && inTransitCustody.isEmpty()) {
            throw new IllegalArgumentException("In-transit Material Transfer lacks authoritative custody");
        }
        boolean requiresDestination = lifecycle == MaterialTransferLifecycle.DESTINATION_BOUND
                || lifecycle == MaterialTransferLifecycle.DESTINATION_DEPOSIT_PREPARED
                || lifecycle == MaterialTransferLifecycle.DESTINATION_DEPOSIT_COMMITTED
                || lifecycle == MaterialTransferLifecycle.COMPLETED;
        if (requiresDestination && destinationObservation.isEmpty()) {
            throw new IllegalArgumentException("Bound destination lacks Workstation observation");
        }
        if ((lifecycle == MaterialTransferLifecycle.DESTINATION_DEPOSIT_COMMITTED
                || lifecycle == MaterialTransferLifecycle.COMPLETED)
                && (destinationPreparation.isEmpty() || destinationResult.isEmpty() || inTransitCustody.isPresent())) {
            throw new IllegalArgumentException("Committed deposit has inconsistent custody or Workstation evidence");
        }
        if (lifecycle == MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED
                && returnObservation.isEmpty()) {
            throw new IllegalArgumentException("Prepared cancellation return lacks source-return observation");
        }
        if (lifecycle == MaterialTransferLifecycle.CANCELLATION_RETURN_COMMITTED
                && (returnPreparation.isEmpty() || returnResult.isEmpty() || inTransitCustody.isPresent())) {
            throw new IllegalArgumentException("Committed cancellation return has inconsistent custody evidence");
        }
        if (lifecycle == MaterialTransferLifecycle.FAILED && sourceResult.isPresent()
                && (returnResult.isEmpty() || inTransitCustody.isPresent())) {
            throw new IllegalArgumentException("Failed post-withdrawal transfer lacks a proven source return");
        }
        if (lifecycle == MaterialTransferLifecycle.RECOVERY_REQUIRED) {
            MaterialCustodyLocation location = custodyLocation.orElseThrow();
            if (location == MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME
                    && (sourceResult.isEmpty() || inTransitCustody.isEmpty()
                    || destinationResult.isPresent() || returnResult.isPresent())) {
                throw new IllegalArgumentException("Recovery Required does not prove Material Handling custody");
            }
            if (location == MaterialCustodyLocation.SOURCE_WORKSTATION && sourceResult.isPresent()
                    && returnResult.isEmpty()) {
                throw new IllegalArgumentException("Recovery Required does not prove source custody");
            }
            if (location == MaterialCustodyLocation.DESTINATION_WORKSTATION && destinationResult.isEmpty()) {
                throw new IllegalArgumentException("Recovery Required does not prove destination custody");
            }
        }
        if (lifecycle.terminal() && lifecycle != MaterialTransferLifecycle.COMPLETED && terminalDetail.isEmpty()) {
            throw new IllegalArgumentException("Exceptional terminal Material Transfer requires a detail");
        }
    }

    private static void validateObservation(
            WorkstationEndpointObservation observation,
            WorkstationEndpointReference endpoint,
            WorkstationEndpointEffectKind kind,
            String label
    ) {
        if (!observation.instanceId().equals(endpoint.instanceId()) || observation.effectKind() != kind) {
            throw new IllegalArgumentException("Material Transfer " + label + " observation binding mismatch");
        }
    }

    private static void validatePreparation(
            WorkstationEndpointPreparation preparation,
            WorkstationEndpointObservation observation,
            String invocationIdentity,
            String label
    ) {
        if (!preparation.instanceId().equals(observation.instanceId())
                || !preparation.invocationIdentity().equals(invocationIdentity)
                || preparation.effectKind() != observation.effectKind()
                || preparation.slotIndex() != observation.slotIndex()
                || !preparation.exactStack().equals(observation.exactEffectStack())
                || preparation.expectedInventoryRevision() != observation.inventoryRevision()
                || preparation.expectedEndpointEffectRevision() != observation.endpointEffectRevision()
                || !preparation.preFreshnessIdentity().equals(observation.freshnessIdentity())
                || !preparation.endpointConfigurationIdentity().equals(
                        observation.endpointConfigurationIdentity()
                )) {
            throw new IllegalArgumentException("Material Transfer " + label + " preparation binding mismatch");
        }
    }

    private static void validateResult(
            WorkstationEndpointOwnerResult result,
            WorkstationEndpointPreparation preparation,
            String label
    ) {
        if (!result.effectId().equals(preparation.effectId())
                || result.journalSequence() != preparation.journalSequence()
                || !result.exactStack().equals(preparation.exactStack())
                || !result.preFreshnessIdentity().equals(preparation.preFreshnessIdentity())
                || !result.postFreshnessIdentity().equals(preparation.postFreshnessIdentity())
                || !result.endpointConfigurationIdentity().equals(preparation.endpointConfigurationIdentity())
                || result.resultCode() != com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode.APPLIED) {
            throw new IllegalArgumentException("Material Transfer " + label + " owner-result binding mismatch");
        }
    }

    private static String calculateRequestDigest(
            WorldIdentityRootIdentity worldIdentity,
            WorkstationEndpointReference source,
            WorkstationEndpointReference destination,
            String materialIdentity,
            int quantity,
            String assignmentTypeIdentity,
            Optional<String> employeeReference,
            String configurationIdentity
    ) {
        MaterialHandlingDigest digest = MaterialHandlingDigest.create("butchercraft:material_transfer_request")
                .add(MaterialHandlingSchema.CURRENT_VERSION)
                .add(worldIdentity.identity())
                .add(worldIdentity.rootDigest())
                .add(source.instanceId().value())
                .add(source.endpointKey().canonicalValue())
                .add(source.generation())
                .add(destination.instanceId().value())
                .add(destination.endpointKey().canonicalValue())
                .add(destination.generation())
                .add(materialIdentity)
                .add(quantity)
                .add(assignmentTypeIdentity);
        employeeReference.ifPresent(digest::add);
        return digest.add(configurationIdentity).finish();
    }

    private static String calculateStateDigest(
            MaterialTransferId transferId,
            String requestContentDigest,
            MaterialTransferLifecycle lifecycle,
            Optional<MaterialCustodyLocation> custodyLocation,
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
            Optional<WorkstationEndpointOwnerResult> returnResult,
            Optional<MaterialTransferTerminalEvidence> terminalEvidence,
            Optional<String> terminalDetail,
            long lastUpdateRevision
    ) {
        return MaterialHandlingDigest.create("butchercraft:material_transfer_state")
                .add(transferId.value())
                .add(requestContentDigest)
                .add(lifecycle.name())
                .add(custodyLocation.map(Enum::name).orElse(""))
                .add(sourceObservation.map(WorkstationEndpointObservation::evidenceIdentity).orElse(""))
                .add(sourcePreparation.map(WorkstationEndpointPreparation::evidenceIdentity).orElse(""))
                .add(sourceResult.map(WorkstationEndpointOwnerResult::evidenceIdentity).orElse(""))
                .add(exactTransferStack.map(WorkstationEndpointStackPayload::contentDigest).orElse(""))
                .add(inTransitCustody.map(WorkstationEndpointStackPayload::contentDigest).orElse(""))
                .add(destinationObservation.map(WorkstationEndpointObservation::evidenceIdentity).orElse(""))
                .add(destinationPreparation.map(WorkstationEndpointPreparation::evidenceIdentity).orElse(""))
                .add(destinationResult.map(WorkstationEndpointOwnerResult::evidenceIdentity).orElse(""))
                .add(returnObservation.map(WorkstationEndpointObservation::evidenceIdentity).orElse(""))
                .add(returnPreparation.map(WorkstationEndpointPreparation::evidenceIdentity).orElse(""))
                .add(returnResult.map(WorkstationEndpointOwnerResult::evidenceIdentity).orElse(""))
                .add(terminalEvidence.map(MaterialTransferTerminalEvidence::contentDigest).orElse(""))
                .add(terminalDetail.orElse(""))
                .add(lastUpdateRevision)
                .finish();
    }

    public boolean hasProvenMaterialHandlingCustody() {
        return custodyLocation.filter(MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME::equals).isPresent()
                && sourceResult.isPresent()
                && inTransitCustody.isPresent()
                && destinationResult.isEmpty()
                && returnResult.isEmpty();
    }

    @Override
    public int compareTo(MaterialTransferRecord other) {
        return transferId.compareTo(other.transferId);
    }
}
