package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

public record AllocationRuntimeView(
        AllocationSetId allocationSetId,
        AllocationRuntimeStatus status,
        long createdSimulationTick,
        OptionalLong waitingSimulationTick,
        OptionalLong allocatedSimulationTick,
        OptionalLong activatedSimulationTick,
        OptionalLong releasedSimulationTick,
        OptionalLong expirationSimulationTick,
        long lastUpdatedSimulationTick,
        List<AllocationCommitmentId> commitmentIds,
        Optional<AllocationRuntimeFailureCode> failureCode,
        Optional<String> failureMessage,
        AllocationMetadata metadata,
        long revision,
        int schemaVersion
) implements Comparable<AllocationRuntimeView> {
    private static final Comparator<AllocationRuntimeView> ORDER = Comparator
            .comparing(AllocationRuntimeView::allocationSetId);

    public AllocationRuntimeView {
        List<AllocationRuntimeFailure> failures = AllocationRuntimeValidation.failures();
        if (allocationSetId == null) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.NULL_VALUE,
                    "allocationSetId",
                    "AllocationSet identity is required"
            );
        }
        if (status == null) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.NULL_VALUE,
                    "status",
                    "Allocation runtime status is required"
            );
        }
        waitingSimulationTick = requireOptional(
                waitingSimulationTick,
                "waitingSimulationTick",
                failures
        );
        allocatedSimulationTick = requireOptional(
                allocatedSimulationTick,
                "allocatedSimulationTick",
                failures
        );
        activatedSimulationTick = requireOptional(
                activatedSimulationTick,
                "activatedSimulationTick",
                failures
        );
        releasedSimulationTick = requireOptional(
                releasedSimulationTick,
                "releasedSimulationTick",
                failures
        );
        expirationSimulationTick = requireOptional(
                expirationSimulationTick,
                "expirationSimulationTick",
                failures
        );
        if (metadata == null) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.NULL_VALUE,
                    "metadata",
                    "Allocation runtime metadata is required"
            );
        }
        if (failureCode == null) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.NULL_VALUE,
                    "failureCode",
                    "Allocation runtime failure code Optional is required"
            );
            failureCode = Optional.empty();
        }
        if (failureMessage == null) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.NULL_VALUE,
                    "failureMessage",
                    "Allocation runtime failure message Optional is required"
            );
            failureMessage = Optional.empty();
        } else {
            failureMessage = failureMessage.map(
                    AllocationRuntimeValidation::failureMessage
            );
        }
        commitmentIds = canonicalCommitments(commitmentIds, failures);

        if (createdSimulationTick < 0L || lastUpdatedSimulationTick < 0L) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.INVALID_SIMULATION_TICK,
                    "simulationTick",
                    "Allocation runtime ticks must not be negative"
            );
        }
        if (lastUpdatedSimulationTick < createdSimulationTick) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.INVALID_TIMESTAMP,
                    "lastUpdatedSimulationTick",
                    "Allocation runtime update tick cannot precede creation"
            );
        }
        validateOptionalTick(
                waitingSimulationTick,
                createdSimulationTick,
                lastUpdatedSimulationTick,
                "waitingSimulationTick",
                failures
        );
        validateOptionalTick(
                allocatedSimulationTick,
                createdSimulationTick,
                lastUpdatedSimulationTick,
                "allocatedSimulationTick",
                failures
        );
        validateOptionalTick(
                activatedSimulationTick,
                createdSimulationTick,
                lastUpdatedSimulationTick,
                "activatedSimulationTick",
                failures
        );
        validateOptionalTick(
                releasedSimulationTick,
                createdSimulationTick,
                lastUpdatedSimulationTick,
                "releasedSimulationTick",
                failures
        );
        if (expirationSimulationTick.isPresent()
                && expirationSimulationTick.getAsLong() < createdSimulationTick) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.INVALID_TIMESTAMP,
                    "expirationSimulationTick",
                    "Allocation runtime expiration cannot precede creation"
            );
        }
        validateChronology(
                waitingSimulationTick,
                allocatedSimulationTick,
                "waitingSimulationTick",
                "allocatedSimulationTick",
                failures
        );
        validateChronology(
                allocatedSimulationTick,
                activatedSimulationTick,
                "allocatedSimulationTick",
                "activatedSimulationTick",
                failures
        );
        validateChronology(
                allocatedSimulationTick,
                releasedSimulationTick,
                "allocatedSimulationTick",
                "releasedSimulationTick",
                failures
        );
        validateState(
                status,
                waitingSimulationTick,
                allocatedSimulationTick,
                activatedSimulationTick,
                releasedSimulationTick,
                commitmentIds,
                failureCode,
                failureMessage,
                failures
        );
        if (revision < 0L) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.INVALID_STATUS_STATE,
                    "revision",
                    "Allocation runtime revision must not be negative"
            );
        }
        if (schemaVersion != AllocationSchema.CURRENT_VERSION) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.INVALID_SCHEMA_VERSION,
                    "schemaVersion",
                    "Unsupported Allocation runtime schema version: " + schemaVersion
            );
        }
        AllocationRuntimeValidation.throwIfAny(failures);
    }

    @Override
    public int compareTo(AllocationRuntimeView other) {
        return ORDER.compare(this, AllocationValidation.required(other, "other"));
    }

    private static OptionalLong requireOptional(
            OptionalLong value,
            String field,
            List<AllocationRuntimeFailure> failures
    ) {
        if (value == null) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.NULL_VALUE,
                    field,
                    field + " is required"
            );
            return OptionalLong.empty();
        }
        return value;
    }

    private static List<AllocationCommitmentId> canonicalCommitments(
            List<AllocationCommitmentId> source,
            List<AllocationRuntimeFailure> failures
    ) {
        if (source == null) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.NULL_VALUE,
                    "commitmentIds",
                    "Allocation runtime Commitment ids are required"
            );
            return List.of();
        }
        List<AllocationCommitmentId> copy = new ArrayList<>(source.size());
        for (AllocationCommitmentId id : source) {
            if (id == null) {
                AllocationRuntimeValidation.add(
                        failures,
                        AllocationRuntimeFailureCode.NULL_VALUE,
                        "commitmentIds",
                        "Allocation runtime Commitment identity is required"
                );
            } else {
                copy.add(id);
            }
        }
        copy.sort(Comparator.naturalOrder());
        Set<AllocationCommitmentId> unique = new HashSet<>(copy);
        if (unique.size() != copy.size()) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.DUPLICATE_COMMITMENT,
                    "commitmentIds",
                    "Allocation runtime contains duplicate Commitment ids"
            );
        }
        return List.copyOf(copy);
    }

    private static void validateOptionalTick(
            OptionalLong value,
            long createdTick,
            long updatedTick,
            String field,
            List<AllocationRuntimeFailure> failures
    ) {
        if (value.isPresent()
                && (value.getAsLong() < createdTick || value.getAsLong() > updatedTick)) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.INVALID_TIMESTAMP,
                    field,
                    field + " must be between creation and last update"
            );
        }
    }

    private static void validateChronology(
            OptionalLong earlier,
            OptionalLong later,
            String earlierField,
            String laterField,
            List<AllocationRuntimeFailure> failures
    ) {
        if (earlier.isPresent() && later.isPresent()
                && later.getAsLong() < earlier.getAsLong()) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.INVALID_TIMESTAMP,
                    laterField,
                    laterField + " cannot precede " + earlierField
            );
        }
    }

    private static void validateState(
            AllocationRuntimeStatus status,
            OptionalLong waitingTick,
            OptionalLong allocatedTick,
            OptionalLong activatedTick,
            OptionalLong releasedTick,
            List<AllocationCommitmentId> commitmentIds,
            Optional<AllocationRuntimeFailureCode> failureCode,
            Optional<String> failureMessage,
            List<AllocationRuntimeFailure> failures
    ) {
        if (failureCode.isPresent() != failureMessage.isPresent()) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.INVALID_STATUS_STATE,
                    "failure",
                    "Allocation runtime failure code and message must appear together"
            );
        }
        if (status == null) {
            return;
        }
        boolean failureRequired = status == AllocationRuntimeStatus.WAITING
                || status == AllocationRuntimeStatus.FAILED
                || status == AllocationRuntimeStatus.EXPIRED;
        if (failureRequired != failureCode.isPresent()) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.INVALID_STATUS_STATE,
                    "failure",
                    "Allocation runtime failure fields do not match status " + status
            );
        }
        if (status == AllocationRuntimeStatus.WAITING && waitingTick.isEmpty()) {
            missingTick("waitingSimulationTick", status, failures);
        }
        if ((status == AllocationRuntimeStatus.ALLOCATED
                || status == AllocationRuntimeStatus.ACTIVE
                || status == AllocationRuntimeStatus.RELEASED)
                && allocatedTick.isEmpty()) {
            missingTick("allocatedSimulationTick", status, failures);
        }
        if (status == AllocationRuntimeStatus.ACTIVE && activatedTick.isEmpty()) {
            missingTick("activatedSimulationTick", status, failures);
        }
        if (status == AllocationRuntimeStatus.RELEASED && releasedTick.isEmpty()) {
            missingTick("releasedSimulationTick", status, failures);
        }
        if (activatedTick.isPresent() && allocatedTick.isEmpty()) {
            unexpectedTick(
                    "activatedSimulationTick",
                    "requires allocatedSimulationTick",
                    failures
            );
        }
        if (releasedTick.isPresent() && allocatedTick.isEmpty()) {
            unexpectedTick(
                    "releasedSimulationTick",
                    "requires allocatedSimulationTick",
                    failures
            );
        }
        switch (status) {
            case REQUESTED -> {
                requireAbsent(waitingTick, "waitingSimulationTick", status, failures);
                requireAbsent(allocatedTick, "allocatedSimulationTick", status, failures);
                requireAbsent(activatedTick, "activatedSimulationTick", status, failures);
                requireAbsent(releasedTick, "releasedSimulationTick", status, failures);
            }
            case WAITING -> {
                requireAbsent(allocatedTick, "allocatedSimulationTick", status, failures);
                requireAbsent(activatedTick, "activatedSimulationTick", status, failures);
                requireAbsent(releasedTick, "releasedSimulationTick", status, failures);
            }
            case ALLOCATED -> {
                requireAbsent(activatedTick, "activatedSimulationTick", status, failures);
                requireAbsent(releasedTick, "releasedSimulationTick", status, failures);
            }
            case ACTIVE -> requireAbsent(
                    releasedTick,
                    "releasedSimulationTick",
                    status,
                    failures
            );
            case FAILED, EXPIRED -> requireAbsent(
                    releasedTick,
                    "releasedSimulationTick",
                    status,
                    failures
            );
            case RELEASED -> {
            }
        }
        boolean commitmentsRequired = status == AllocationRuntimeStatus.ALLOCATED
                || status == AllocationRuntimeStatus.ACTIVE
                || status == AllocationRuntimeStatus.RELEASED;
        if (commitmentsRequired && commitmentIds.isEmpty()) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.INCOMPLETE_COMMITMENT_SET,
                    "commitmentIds",
                    status + " runtime requires Commitment ids"
            );
        }
        if ((status == AllocationRuntimeStatus.REQUESTED
                || status == AllocationRuntimeStatus.WAITING)
                && !commitmentIds.isEmpty()) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.INVALID_STATUS_STATE,
                    "commitmentIds",
                    status + " runtime cannot contain Commitment ids"
            );
        }
        if ((status == AllocationRuntimeStatus.FAILED
                || status == AllocationRuntimeStatus.EXPIRED)
                && (allocatedTick.isPresent() != !commitmentIds.isEmpty())) {
            AllocationRuntimeValidation.add(
                    failures,
                    AllocationRuntimeFailureCode.INVALID_STATUS_STATE,
                    "commitmentIds",
                    status + " runtime Allocation tick and Commitment ids are inconsistent"
            );
        }
    }

    private static void missingTick(
            String field,
            AllocationRuntimeStatus status,
            List<AllocationRuntimeFailure> failures
    ) {
        AllocationRuntimeValidation.add(
                failures,
                AllocationRuntimeFailureCode.INVALID_STATUS_STATE,
                field,
                status + " runtime requires " + field
        );
    }

    private static void requireAbsent(
            OptionalLong tick,
            String field,
            AllocationRuntimeStatus status,
            List<AllocationRuntimeFailure> failures
    ) {
        if (tick.isPresent()) {
            unexpectedTick(field, "is not valid for status " + status, failures);
        }
    }

    private static void unexpectedTick(
            String field,
            String reason,
            List<AllocationRuntimeFailure> failures
    ) {
        AllocationRuntimeValidation.add(
                failures,
                AllocationRuntimeFailureCode.INVALID_STATUS_STATE,
                field,
                field + " " + reason
        );
    }
}
