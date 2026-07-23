package com.butchercraft.world.allocation;

public record AllocationReportOrderingRecord(
        AllocationRequestId requestId,
        AllocationOrderingContext orderingContext
) implements Comparable<AllocationReportOrderingRecord> {
    public AllocationReportOrderingRecord {
        requestId = AllocationValidation.required(requestId, "requestId");
        orderingContext = AllocationValidation.required(orderingContext, "orderingContext");
    }

    @Override
    public int compareTo(AllocationReportOrderingRecord other) {
        return requestId.compareTo(
                AllocationValidation.required(other, "other").requestId
        );
    }
}
