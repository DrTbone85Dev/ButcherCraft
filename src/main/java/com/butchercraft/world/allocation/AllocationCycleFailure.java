package com.butchercraft.world.allocation;

import java.util.Comparator;

public record AllocationCycleFailure(
        AllocationCycleFailureCode code,
        AllocationCycleFailureScope scope,
        String subject,
        String message
) implements Comparable<AllocationCycleFailure> {
    private static final Comparator<AllocationCycleFailure> ORDER = Comparator
            .comparing(AllocationCycleFailure::scope)
            .thenComparing(AllocationCycleFailure::code)
            .thenComparing(AllocationCycleFailure::subject)
            .thenComparing(AllocationCycleFailure::message);

    public AllocationCycleFailure {
        code = AllocationValidation.required(code, "code");
        scope = AllocationValidation.required(scope, "scope");
        subject = AllocationValidation.text(
                subject,
                "subject",
                AllocationSchema.MAXIMUM_METADATA_VALUE_LENGTH
        );
        message = AllocationValidation.text(
                message,
                "message",
                AllocationSchema.MAXIMUM_RUNTIME_FAILURE_MESSAGE_LENGTH
        );
    }

    @Override
    public int compareTo(AllocationCycleFailure other) {
        return ORDER.compare(this, AllocationValidation.required(other, "other"));
    }
}
