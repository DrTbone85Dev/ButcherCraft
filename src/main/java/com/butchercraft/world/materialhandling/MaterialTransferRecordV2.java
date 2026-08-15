package com.butchercraft.world.materialhandling;

import com.butchercraft.workstation.endpoint.WorkstationEndpointObservationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointOwnerResultV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointPreparationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointStackStateV2;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Minimum schema-2 Material Handling record; custody lifecycle ownership is unchanged. */
public record MaterialTransferRecordV2(
        int schemaVersion,
        long sequence,
        MaterialTransferIdV2 transferId,
        WorldIdentityRootIdentity worldIdentity,
        WorkstationEndpointReference source,
        WorkstationEndpointReference destination,
        String materialIdentity,
        int quantity,
        String assignmentTypeIdentity,
        Optional<String> employeeReference,
        MaterialTransferLifecycle lifecycle,
        WorkstationEndpointStackStateV2 exactTransferStack,
        Optional<WorkstationEndpointStackStateV2> exactInTransitCustody,
        List<WorkstationEndpointObservationV2> endpointObservations,
        List<WorkstationEndpointPreparationV2> endpointPreparations,
        List<WorkstationEndpointOwnerResultV2> endpointOwnerResults,
        String configurationIdentity,
        long creationRevision,
        long lastUpdateRevision,
        Optional<String> failureDetail
) implements Comparable<MaterialTransferRecordV2>, MaterialTransferView {
    public MaterialTransferRecordV2 {
        if (schemaVersion != MaterialHandlingSchema.STACK_AWARE_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema-2 Material Handling record");
        }
        MaterialHandlingValidation.positive(sequence, "transfer sequence");
        transferId = Objects.requireNonNull(transferId, "transferId");
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        source = Objects.requireNonNull(source, "source");
        destination = Objects.requireNonNull(destination, "destination");
        materialIdentity = MaterialHandlingValidation.id(materialIdentity, "material identity");
        MaterialHandlingValidation.positive(quantity, "transfer quantity");
        assignmentTypeIdentity = MaterialHandlingValidation.id(assignmentTypeIdentity, "assignment type identity");
        employeeReference = Objects.requireNonNull(employeeReference, "employeeReference")
                .map(value -> MaterialHandlingValidation.id(value, "employee reference"));
        lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        exactTransferStack = Objects.requireNonNull(exactTransferStack, "exactTransferStack");
        if (exactTransferStack.isEmpty() || exactTransferStack.count() != quantity) {
            throw new IllegalArgumentException("Exact transfer payload must bind the complete transfer quantity");
        }
        exactInTransitCustody = Objects.requireNonNull(exactInTransitCustody, "exactInTransitCustody");
        if (exactInTransitCustody.isPresent() && !exactInTransitCustody.orElseThrow().equals(exactTransferStack)) {
            throw new IllegalArgumentException("In-transit custody must equal the exact transfer payload");
        }
        endpointObservations = List.copyOf(Objects.requireNonNull(endpointObservations, "endpointObservations"));
        endpointPreparations = List.copyOf(Objects.requireNonNull(endpointPreparations, "endpointPreparations"));
        endpointOwnerResults = List.copyOf(Objects.requireNonNull(endpointOwnerResults, "endpointOwnerResults"));
        for (WorkstationEndpointObservationV2 observation : endpointObservations) {
            requirePayload(observation.transferStack(), exactTransferStack);
        }
        for (WorkstationEndpointPreparationV2 preparation : endpointPreparations) {
            requirePayload(preparation.observation().transferStack(), exactTransferStack);
        }
        for (WorkstationEndpointOwnerResultV2 result : endpointOwnerResults) {
            requirePayload(result.transferStack(), exactTransferStack);
            if (!endpointPreparations.contains(result.preparation())) {
                throw new IllegalArgumentException("Schema-2 owner result must bind a retained preparation");
            }
        }
        for (WorkstationEndpointPreparationV2 preparation : endpointPreparations) {
            if (!endpointObservations.contains(preparation.observation())) {
                throw new IllegalArgumentException("Schema-2 preparation must bind a retained observation");
            }
        }
        if (endpointPreparations.stream().map(WorkstationEndpointPreparationV2::effectId).distinct().count()
                != endpointPreparations.size()
                || endpointOwnerResults.stream().map(WorkstationEndpointOwnerResultV2::effectId).distinct().count()
                != endpointOwnerResults.size()) {
            throw new IllegalArgumentException("Schema-2 endpoint effects must be unique within one transfer");
        }
        boolean custodyRequired = lifecycle == MaterialTransferLifecycle.SOURCE_WITHDRAW_COMMITTED
                || lifecycle == MaterialTransferLifecycle.IN_TRANSIT
                || lifecycle == MaterialTransferLifecycle.DESTINATION_BOUND
                || lifecycle == MaterialTransferLifecycle.DESTINATION_DEPOSIT_PREPARED
                || lifecycle == MaterialTransferLifecycle.CANCELLATION_REQUESTED
                || lifecycle == MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED;
        if (custodyRequired != exactInTransitCustody.isPresent()
                && lifecycle != MaterialTransferLifecycle.RECOVERY_REQUIRED
                && lifecycle != MaterialTransferLifecycle.UNKNOWN_OUTCOME) {
            throw new IllegalArgumentException("Schema-2 lifecycle and in-transit custody disagree");
        }
        configurationIdentity = MaterialHandlingValidation.id(configurationIdentity, "configuration identity");
        creationRevision = MaterialHandlingValidation.positive(creationRevision, "creation revision");
        lastUpdateRevision = MaterialHandlingValidation.positive(lastUpdateRevision, "last-update revision");
        if (lastUpdateRevision < creationRevision) throw new IllegalArgumentException("Transfer revisions regressed");
        failureDetail = Objects.requireNonNull(failureDetail, "failureDetail")
                .map(value -> MaterialHandlingValidation.text(value, "failure detail"));
        MaterialTransferIdV2 expectedId = MaterialTransferIdV2.create(
                worldIdentity, sequence, source, destination, materialIdentity, quantity, assignmentTypeIdentity,
                employeeReference, configurationIdentity
        );
        if (!expectedId.equals(transferId)) throw new IllegalArgumentException("Transfer Identity is not canonical");
    }

    public static MaterialTransferRecordV2 create(
            long sequence,
            WorldIdentityRootIdentity worldIdentity,
            WorkstationEndpointReference source,
            WorkstationEndpointReference destination,
            String materialIdentity,
            int quantity,
            String assignmentTypeIdentity,
            Optional<String> employeeReference,
            MaterialTransferLifecycle lifecycle,
            WorkstationEndpointStackStateV2 exactTransferStack,
            Optional<WorkstationEndpointStackStateV2> custody,
            List<WorkstationEndpointObservationV2> observations,
            List<WorkstationEndpointPreparationV2> preparations,
            List<WorkstationEndpointOwnerResultV2> results,
            String configurationIdentity,
            long creationRevision,
            long lastUpdateRevision,
            Optional<String> failureDetail
    ) {
        MaterialTransferIdV2 id = MaterialTransferIdV2.create(
                worldIdentity, sequence, source, destination, materialIdentity, quantity, assignmentTypeIdentity,
                employeeReference, configurationIdentity
        );
        return new MaterialTransferRecordV2(
                MaterialHandlingSchema.STACK_AWARE_SCHEMA_VERSION, sequence, id, worldIdentity, source, destination,
                materialIdentity, quantity, assignmentTypeIdentity, employeeReference, lifecycle, exactTransferStack,
                custody, observations, preparations, results, configurationIdentity, creationRevision,
                lastUpdateRevision, failureDetail
        );
    }

    public MaterialTransferRecordV2 transition(
            MaterialTransferLifecycle target,
            long ownerRevision,
            Optional<WorkstationEndpointStackStateV2> custody,
            List<WorkstationEndpointObservationV2> observations,
            List<WorkstationEndpointPreparationV2> preparations,
            List<WorkstationEndpointOwnerResultV2> results,
            Optional<String> detail
    ) {
        Objects.requireNonNull(target, "target");
        if (!lifecycle.canTransitionTo(target)) {
            throw new IllegalArgumentException("Invalid schema-2 Material Transfer transition: "
                    + lifecycle + " -> " + target);
        }
        return new MaterialTransferRecordV2(
                schemaVersion, sequence, transferId, worldIdentity, source, destination, materialIdentity, quantity,
                assignmentTypeIdentity, employeeReference, target, exactTransferStack, custody, observations,
                preparations, results, configurationIdentity, creationRevision, ownerRevision, detail
        );
    }

    private static void requirePayload(
            WorkstationEndpointStackStateV2 evidencePayload,
            WorkstationEndpointStackStateV2 transferPayload
    ) {
        if (!evidencePayload.equals(transferPayload)) {
            throw new IllegalArgumentException("Embedded endpoint evidence must bind exact Material Handling payload");
        }
    }

    @Override
    public String transferIdentity() {
        return transferId.value();
    }

    @Override
    public Optional<MaterialCustodyLocation> custodyLocation() {
        if (lifecycle == MaterialTransferLifecycle.UNKNOWN_OUTCOME) return Optional.empty();
        if (lifecycle == MaterialTransferLifecycle.RECOVERY_REQUIRED) {
            return exactInTransitCustody.isPresent()
                    ? Optional.of(MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME)
                    : Optional.of(MaterialCustodyLocation.SOURCE_WORKSTATION);
        }
        return switch (lifecycle) {
            case REQUESTED, SOURCE_BOUND, SOURCE_WITHDRAW_PREPARED, FAILED, CANCELLED,
                    CANCELLATION_RETURN_COMMITTED -> Optional.of(MaterialCustodyLocation.SOURCE_WORKSTATION);
            case SOURCE_WITHDRAW_COMMITTED, IN_TRANSIT, DESTINATION_BOUND,
                    DESTINATION_DEPOSIT_PREPARED, CANCELLATION_REQUESTED,
                    CANCELLATION_RETURN_PREPARED -> Optional.of(MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME);
            case DESTINATION_DEPOSIT_COMMITTED, COMPLETED ->
                    Optional.of(MaterialCustodyLocation.DESTINATION_WORKSTATION);
            case UNKNOWN_OUTCOME, RECOVERY_REQUIRED -> throw new IllegalStateException("Handled above");
        };
    }

    @Override
    public Optional<String> terminalDetail() {
        return failureDetail;
    }

    @Override
    public boolean hasProvenMaterialHandlingCustody() {
        return exactInTransitCustody.isPresent()
                && custodyLocation().filter(MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME::equals).isPresent();
    }

    @Override
    public int compareTo(MaterialTransferRecordV2 other) {
        return Long.compare(sequence, other.sequence);
    }
}
