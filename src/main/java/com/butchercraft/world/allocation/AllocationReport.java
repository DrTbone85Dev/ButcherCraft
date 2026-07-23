package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public record AllocationReport(
        AllocationCycleId allocationCycleId,
        List<AllocationSetId> successfulSetIds,
        List<AllocationSetId> waitingSetIds,
        List<AllocationSetId> rejectedSetIds,
        List<AllocationSetId> failedSetIds,
        List<AllocationSetId> releasedSetIds,
        List<AllocationSetId> expiredSetIds,
        List<AllocationCommitmentId> commitmentIds,
        List<AllocationConflictRecord> conflicts,
        List<AllocationCapacityReportEntry> capacities,
        List<AllocationReportOrderingRecord> orderingContexts,
        AllocationReportWorkSummary workSummary,
        List<AllocationRuntimeFailure> failures,
        AllocationPolicyId policyId,
        long simulationTick,
        int schemaVersion
) implements Comparable<AllocationReport> {
    public AllocationReport {
        allocationCycleId = AllocationValidation.required(
                allocationCycleId,
                "allocationCycleId"
        );
        successfulSetIds = canonical(
                successfulSetIds,
                Function.identity(),
                "successfulSetIds",
                AllocationSchema.MAXIMUM_REPORT_SET_REFERENCES
        );
        waitingSetIds = canonical(
                waitingSetIds,
                Function.identity(),
                "waitingSetIds",
                AllocationSchema.MAXIMUM_REPORT_SET_REFERENCES
        );
        rejectedSetIds = canonical(
                rejectedSetIds,
                Function.identity(),
                "rejectedSetIds",
                AllocationSchema.MAXIMUM_REPORT_SET_REFERENCES
        );
        failedSetIds = canonical(
                failedSetIds,
                Function.identity(),
                "failedSetIds",
                AllocationSchema.MAXIMUM_REPORT_SET_REFERENCES
        );
        releasedSetIds = canonical(
                releasedSetIds,
                Function.identity(),
                "releasedSetIds",
                AllocationSchema.MAXIMUM_REPORT_SET_REFERENCES
        );
        expiredSetIds = canonical(
                expiredSetIds,
                Function.identity(),
                "expiredSetIds",
                AllocationSchema.MAXIMUM_REPORT_SET_REFERENCES
        );
        commitmentIds = canonical(
                commitmentIds,
                Function.identity(),
                "commitmentIds",
                AllocationSchema.MAXIMUM_REPORT_COMMITMENTS
        );
        conflicts = canonical(
                conflicts,
                Function.identity(),
                "conflicts",
                AllocationSchema.MAXIMUM_REPORT_CONFLICTS
        );
        capacities = canonical(
                capacities,
                AllocationCapacityReportEntry::capacityKey,
                "capacities",
                AllocationSchema.MAXIMUM_REPORT_CAPACITIES
        );
        orderingContexts = canonical(
                orderingContexts,
                AllocationReportOrderingRecord::requestId,
                "orderingContexts",
                AllocationSchema.MAXIMUM_REPORT_SET_REFERENCES
        );
        workSummary = AllocationValidation.required(workSummary, "workSummary");
        failures = canonical(
                failures,
                Function.identity(),
                "failures",
                AllocationSchema.MAXIMUM_REPORT_FAILURES
        );
        policyId = AllocationValidation.required(policyId, "policyId");
        simulationTick = AllocationValidation.tick(simulationTick, "simulationTick");
        schemaVersion = AllocationValidation.schema(schemaVersion);
        if (!allocationCycleId.equals(AllocationCycleId.forTick(simulationTick))) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_REPORT,
                    allocationCycleId.value(),
                    "Allocation report Cycle identity does not match its tick"
            );
        }
        validateSetCategories(
                successfulSetIds,
                waitingSetIds,
                rejectedSetIds,
                failedSetIds,
                releasedSetIds,
                expiredSetIds
        );
    }

    @Override
    public int compareTo(AllocationReport other) {
        return allocationCycleId.compareTo(
                AllocationValidation.required(other, "other").allocationCycleId
        );
    }

    @SafeVarargs
    private static void validateSetCategories(List<AllocationSetId>... categories) {
        Set<AllocationSetId> seen = new HashSet<>();
        for (List<AllocationSetId> category : categories) {
            for (AllocationSetId id : category) {
                if (!seen.add(id)) {
                    throw AllocationRuntimeValidation.failure(
                            AllocationRuntimeFailureCode.INVALID_REPORT,
                            id.value(),
                            "AllocationSet appears in multiple report outcome categories"
                    );
                }
            }
        }
    }

    private static <T, I extends Comparable<? super I>> List<T> canonical(
            Collection<T> source,
            Function<T, I> identity,
            String field,
            int maximumSize
    ) {
        List<T> values = new ArrayList<>(
                AllocationValidation.required(source, field)
        );
        values.forEach(value -> AllocationValidation.required(value, field));
        values.sort(Comparator.comparing(identity));
        if (values.size() > maximumSize) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_REPORT,
                    field,
                    "Allocation report " + field + " exceeds its schema bound"
            );
        }
        I previous = null;
        for (T value : values) {
            I id = identity.apply(value);
            if (id.equals(previous)) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.INVALID_REPORT,
                        field,
                        "Allocation report " + field + " contains a duplicate"
                );
            }
            previous = id;
        }
        return List.copyOf(values);
    }
}
