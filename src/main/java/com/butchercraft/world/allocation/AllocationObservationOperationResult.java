package com.butchercraft.world.allocation;

import java.util.List;
import java.util.Optional;

public record AllocationObservationOperationResult(
        boolean accepted,
        Optional<AllocationObservationBundle> bundle,
        List<AllocationProviderFailure> failures
) {
    public AllocationObservationOperationResult {
        bundle = AllocationValidation.required(bundle, "bundle");
        failures = AllocationProviderValidation.canonical(
                failures,
                AllocationSchema.MAXIMUM_PROVIDER_FAILURES,
                "failures"
        );
        if (accepted != bundle.isPresent() || accepted != failures.isEmpty()) {
            throw new IllegalArgumentException(
                    "Observation operation result shape is inconsistent"
            );
        }
    }

    public static AllocationObservationOperationResult accepted(
            AllocationObservationBundle bundle
    ) {
        return new AllocationObservationOperationResult(
                true,
                Optional.of(AllocationValidation.required(bundle, "bundle")),
                List.of()
        );
    }

    public static AllocationObservationOperationResult rejected(
            List<AllocationProviderFailure> failures
    ) {
        if (AllocationValidation.required(failures, "failures").isEmpty()) {
            throw new IllegalArgumentException(
                    "Rejected observation operation requires a failure"
            );
        }
        return new AllocationObservationOperationResult(
                false,
                Optional.empty(),
                failures
        );
    }
}
