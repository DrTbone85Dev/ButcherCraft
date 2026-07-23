package com.butchercraft.world.production;

import java.util.Objects;

public record ProductionExecutionPolicy(
        ProductionRequirementLossPolicy inputLossPolicy,
        ProductionRequirementLossPolicy workforceLossPolicy,
        ProductionRequirementLossPolicy businessLossPolicy,
        ProductionRequirementLossPolicy destinationLossPolicy,
        ProductionTransactionFailurePolicy transactionFailurePolicy,
        long blockedRetryDelayTicks,
        int maximumExecutionAttempts
) {
    public ProductionExecutionPolicy {
        inputLossPolicy = Objects.requireNonNull(inputLossPolicy, "inputLossPolicy");
        workforceLossPolicy = Objects.requireNonNull(workforceLossPolicy, "workforceLossPolicy");
        businessLossPolicy = Objects.requireNonNull(businessLossPolicy, "businessLossPolicy");
        destinationLossPolicy = Objects.requireNonNull(destinationLossPolicy, "destinationLossPolicy");
        transactionFailurePolicy = Objects.requireNonNull(transactionFailurePolicy, "transactionFailurePolicy");
        if (blockedRetryDelayTicks <= 0L) {
            throw new IllegalArgumentException("Production blocked retry delay must be positive");
        }
        if (maximumExecutionAttempts <= 0 || maximumExecutionAttempts > ProductionSchema.MAXIMUM_ATTEMPTS) {
            throw new IllegalArgumentException("Production maximum execution attempts is invalid");
        }
    }

    public static ProductionExecutionPolicy standard() {
        return new ProductionExecutionPolicy(
                ProductionRequirementLossPolicy.BLOCK,
                ProductionRequirementLossPolicy.PAUSE,
                ProductionRequirementLossPolicy.BLOCK,
                ProductionRequirementLossPolicy.BLOCK,
                ProductionTransactionFailurePolicy.BLOCK,
                20L,
                100_000
        );
    }
}
