package com.butchercraft.world.allocation;

import java.util.Comparator;

public record AllocationRuntimeFailure(
        AllocationRuntimeFailureCode code,
        String subject,
        String message
) implements Comparable<AllocationRuntimeFailure> {
    private static final Comparator<AllocationRuntimeFailure> ORDER = Comparator
            .comparing(AllocationRuntimeFailure::code)
            .thenComparing(AllocationRuntimeFailure::subject)
            .thenComparing(AllocationRuntimeFailure::message);

    public AllocationRuntimeFailure {
        code = AllocationValidation.required(code, "code");
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
    public int compareTo(AllocationRuntimeFailure other) {
        return ORDER.compare(this, AllocationValidation.required(other, "other"));
    }
}
