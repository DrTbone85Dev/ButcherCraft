package com.butchercraft.world.allocation;

import java.util.Comparator;
import java.util.Optional;

public record AllocationRuntimeTransitionRecord(
        AllocationSetId allocationSetId,
        Optional<AllocationRuntimeStatus> previousStatus,
        AllocationRuntimeStatus status,
        long transitionSimulationTick,
        long revision,
        Optional<AllocationRuntimeFailureCode> failureCode,
        Optional<String> failureMessage,
        int schemaVersion
) implements Comparable<AllocationRuntimeTransitionRecord> {
    private static final Comparator<AllocationRuntimeTransitionRecord> ORDER = Comparator
            .comparingLong(AllocationRuntimeTransitionRecord::transitionSimulationTick)
            .thenComparing(AllocationRuntimeTransitionRecord::allocationSetId)
            .thenComparingLong(AllocationRuntimeTransitionRecord::revision);

    public AllocationRuntimeTransitionRecord {
        allocationSetId = AllocationValidation.required(allocationSetId, "allocationSetId");
        previousStatus = AllocationValidation.required(previousStatus, "previousStatus");
        status = AllocationValidation.required(status, "status");
        transitionSimulationTick = AllocationValidation.tick(
                transitionSimulationTick,
                "transitionSimulationTick"
        );
        revision = AllocationRuntimeValidation.revision(revision);
        failureCode = AllocationValidation.required(failureCode, "failureCode");
        failureMessage = AllocationValidation.required(failureMessage, "failureMessage")
                .map(AllocationRuntimeValidation::failureMessage);
        if (failureCode.isPresent() != failureMessage.isPresent()) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_HISTORY,
                    allocationSetId.value(),
                    "History failure code and message must appear together"
            );
        }
        boolean requiresFailure = status == AllocationRuntimeStatus.WAITING
                || status == AllocationRuntimeStatus.FAILED
                || status == AllocationRuntimeStatus.EXPIRED;
        if (requiresFailure != failureCode.isPresent()) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_HISTORY,
                    allocationSetId.value(),
                    requiresFailure
                            ? "History status requires failure evidence"
                            : "History status must not contain failure evidence"
            );
        }
        if (previousStatus.isEmpty()) {
            if (status != AllocationRuntimeStatus.REQUESTED || revision != 0L) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.INVALID_HISTORY,
                        allocationSetId.value(),
                        "Initial history record must be REQUESTED at revision zero"
                );
            }
        } else if (!previousStatus.orElseThrow().allowedNextStatuses().contains(status)) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_HISTORY,
                    allocationSetId.value(),
                    "History contains an invalid status transition"
            );
        }
        schemaVersion = AllocationValidation.schema(schemaVersion);
    }

    @Override
    public int compareTo(AllocationRuntimeTransitionRecord other) {
        return ORDER.compare(this, AllocationValidation.required(other, "other"));
    }
}
