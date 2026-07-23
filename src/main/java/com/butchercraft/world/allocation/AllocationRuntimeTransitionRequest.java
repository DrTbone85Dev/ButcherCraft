package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record AllocationRuntimeTransitionRequest(
        AllocationSetId allocationSetId,
        AllocationRuntimeStatus targetStatus,
        long transitionSimulationTick,
        List<AllocationCommitmentId> commitmentIds,
        Optional<AllocationRuntimeFailureCode> failureCode,
        Optional<String> failureMessage
) {
    public AllocationRuntimeTransitionRequest {
        allocationSetId = AllocationValidation.required(allocationSetId, "allocationSetId");
        targetStatus = AllocationValidation.required(targetStatus, "targetStatus");
        transitionSimulationTick = AllocationValidation.tick(
                transitionSimulationTick,
                "transitionSimulationTick"
        );
        commitmentIds = new ArrayList<>(
                AllocationValidation.required(commitmentIds, "commitmentIds")
        );
        commitmentIds.forEach(id -> AllocationValidation.required(id, "commitmentId"));
        commitmentIds.sort(Comparator.naturalOrder());
        if (commitmentIds.stream().distinct().count() != commitmentIds.size()) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.DUPLICATE_COMMITMENT,
                    allocationSetId.value(),
                    "Transition request contains duplicate Commitment ids"
            );
        }
        commitmentIds = List.copyOf(commitmentIds);
        failureCode = AllocationValidation.required(failureCode, "failureCode");
        failureMessage = AllocationValidation.required(failureMessage, "failureMessage")
                .map(AllocationRuntimeValidation::failureMessage);
        if (failureCode.isPresent() != failureMessage.isPresent()) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_STATUS_STATE,
                    allocationSetId.value(),
                    "Transition failure code and message must appear together"
            );
        }
        validateShape(targetStatus, commitmentIds, failureCode, allocationSetId);
    }

    public static AllocationRuntimeTransitionRequest waiting(
            AllocationSetId setId,
            long tick,
            String reason
    ) {
        return failureTransition(
                setId,
                AllocationRuntimeStatus.WAITING,
                tick,
                AllocationRuntimeFailureCode.CAPACITY_UNAVAILABLE,
                reason
        );
    }

    public static AllocationRuntimeTransitionRequest allocated(
            AllocationSetId setId,
            long tick,
            List<AllocationCommitmentId> commitmentIds
    ) {
        return new AllocationRuntimeTransitionRequest(
                setId,
                AllocationRuntimeStatus.ALLOCATED,
                tick,
                commitmentIds,
                Optional.empty(),
                Optional.empty()
        );
    }

    public static AllocationRuntimeTransitionRequest active(
            AllocationSetId setId,
            long tick
    ) {
        return simple(setId, AllocationRuntimeStatus.ACTIVE, tick);
    }

    public static AllocationRuntimeTransitionRequest released(
            AllocationSetId setId,
            long tick
    ) {
        return simple(setId, AllocationRuntimeStatus.RELEASED, tick);
    }

    public static AllocationRuntimeTransitionRequest failed(
            AllocationSetId setId,
            long tick,
            AllocationRuntimeFailureCode code,
            String reason
    ) {
        return failureTransition(setId, AllocationRuntimeStatus.FAILED, tick, code, reason);
    }

    public static AllocationRuntimeTransitionRequest expired(
            AllocationSetId setId,
            long tick,
            String reason
    ) {
        return failureTransition(
                setId,
                AllocationRuntimeStatus.EXPIRED,
                tick,
                AllocationRuntimeFailureCode.SET_EXPIRED,
                reason
        );
    }

    private static AllocationRuntimeTransitionRequest simple(
            AllocationSetId setId,
            AllocationRuntimeStatus status,
            long tick
    ) {
        return new AllocationRuntimeTransitionRequest(
                setId,
                status,
                tick,
                List.of(),
                Optional.empty(),
                Optional.empty()
        );
    }

    private static AllocationRuntimeTransitionRequest failureTransition(
            AllocationSetId setId,
            AllocationRuntimeStatus status,
            long tick,
            AllocationRuntimeFailureCode code,
            String reason
    ) {
        return new AllocationRuntimeTransitionRequest(
                setId,
                status,
                tick,
                List.of(),
                Optional.of(code),
                Optional.of(reason)
        );
    }

    private static void validateShape(
            AllocationRuntimeStatus status,
            List<AllocationCommitmentId> commitments,
            Optional<AllocationRuntimeFailureCode> failure,
            AllocationSetId setId
    ) {
        if (status == AllocationRuntimeStatus.REQUESTED) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_TRANSITION,
                    setId.value(),
                    "REQUESTED runtime is created through registration"
            );
        }
        if (status == AllocationRuntimeStatus.ALLOCATED) {
            if (commitments.isEmpty() || failure.isPresent()) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.INVALID_STATUS_STATE,
                        setId.value(),
                        "ALLOCATED transition requires Commitments and no failure"
                );
            }
            return;
        }
        if (!commitments.isEmpty()) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_STATUS_STATE,
                    setId.value(),
                    status + " transition cannot replace Commitment ids"
            );
        }
        boolean requiresFailure = status == AllocationRuntimeStatus.WAITING
                || status == AllocationRuntimeStatus.FAILED
                || status == AllocationRuntimeStatus.EXPIRED;
        if (requiresFailure != failure.isPresent()) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_STATUS_STATE,
                    setId.value(),
                    status + " transition failure fields are inconsistent"
            );
        }
    }
}
