package com.butchercraft.world.economy.order;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

public record ContractTerms(
        boolean partialFulfillmentAllowed,
        boolean overFulfillmentAllowed,
        boolean cancellationAllowed,
        OptionalLong cancellationNoticeSimulationTicks,
        boolean autoRenewal,
        OptionalInt maximumOpenOrders,
        Optional<String> lateFulfillmentPolicyId,
        SubstitutionPolicy substitutionPolicy
) {
    public ContractTerms {
        cancellationNoticeSimulationTicks = Objects.requireNonNull(
                cancellationNoticeSimulationTicks,
                "cancellationNoticeSimulationTicks"
        );
        maximumOpenOrders = Objects.requireNonNull(maximumOpenOrders, "maximumOpenOrders");
        lateFulfillmentPolicyId = Objects.requireNonNull(lateFulfillmentPolicyId, "lateFulfillmentPolicyId")
                .map(value -> DomainValidation.requireId(value, "Late fulfillment policy id"));
        substitutionPolicy = Objects.requireNonNull(substitutionPolicy, "substitutionPolicy");
        cancellationNoticeSimulationTicks.ifPresent(value -> {
            if (value < 0L) {
                throw new IllegalArgumentException("Cancellation notice ticks must not be negative");
            }
            if (!cancellationAllowed) {
                throw new IllegalArgumentException("Cancellation notice requires cancellation to be allowed");
            }
        });
        maximumOpenOrders.ifPresent(value -> {
            if (value <= 0) {
                throw new IllegalArgumentException("Maximum open orders must be positive");
            }
        });
    }

    public static ContractTerms standard() {
        return new ContractTerms(
                true,
                false,
                true,
                OptionalLong.empty(),
                false,
                OptionalInt.empty(),
                Optional.empty(),
                SubstitutionPolicy.EXACT_ONLY
        );
    }
}
