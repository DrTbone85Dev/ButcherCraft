package com.butchercraft.world.allocation;

public record AllocationObservationSummary(
        int providerCount,
        int successfulProviderCount,
        int failedProviderCount,
        int resourceCount,
        int capacityCount,
        int warningCount,
        int failureCount,
        long deterministicOperationCount
) {
    public AllocationObservationSummary {
        nonNegative(providerCount, "providerCount");
        nonNegative(successfulProviderCount, "successfulProviderCount");
        nonNegative(failedProviderCount, "failedProviderCount");
        nonNegative(resourceCount, "resourceCount");
        nonNegative(capacityCount, "capacityCount");
        nonNegative(warningCount, "warningCount");
        nonNegative(failureCount, "failureCount");
        if (deterministicOperationCount < 0L) {
            throw new IllegalArgumentException(
                    "deterministicOperationCount must not be negative"
            );
        }
        if (providerCount != successfulProviderCount + failedProviderCount) {
            throw new IllegalArgumentException(
                    "Provider summary success and failure counts must balance"
            );
        }
    }

    private static void nonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }
}
