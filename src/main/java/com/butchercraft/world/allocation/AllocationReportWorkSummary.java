package com.butchercraft.world.allocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public record AllocationReportWorkSummary(
        Map<String, Long> stageCounts,
        long consumedWorkUnits,
        long maximumWorkUnits,
        boolean truncated
) {
    public AllocationReportWorkSummary {
        if (consumedWorkUnits < 0L || maximumWorkUnits < 0L
                || consumedWorkUnits > maximumWorkUnits) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_REPORT,
                    "workSummary",
                    "Allocation report work-unit bounds are invalid"
            );
        }
        TreeMap<String, Long> canonical = new TreeMap<>();
        AllocationValidation.required(stageCounts, "stageCounts").forEach(
                (stageId, count) -> {
                    String id = AllocationValidation.id(stageId, "stageId");
                    Long value = AllocationValidation.required(count, "stageCount");
                    if (value < 0L) {
                        throw AllocationRuntimeValidation.failure(
                                AllocationRuntimeFailureCode.INVALID_REPORT,
                                id,
                                "Allocation report stage count must not be negative"
                        );
                    }
                    canonical.put(id, value);
                }
        );
        if (canonical.size() > AllocationSchema.MAXIMUM_REPORT_STAGE_COUNTS) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_REPORT,
                    "stageCounts",
                    "Allocation report exceeds the stage-count bound"
            );
        }
        stageCounts = Collections.unmodifiableMap(new LinkedHashMap<>(canonical));
    }

    public static AllocationReportWorkSummary empty() {
        return new AllocationReportWorkSummary(Map.of(), 0L, 0L, false);
    }
}
