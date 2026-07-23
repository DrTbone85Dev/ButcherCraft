package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.List;

final class AllocationRuntimeValidation {
    private AllocationRuntimeValidation() {
    }

    static AllocationRuntimeValidationException failure(
            AllocationRuntimeFailureCode code,
            String subject,
            String message
    ) {
        return new AllocationRuntimeValidationException(
                List.of(new AllocationRuntimeFailure(code, subject, message))
        );
    }

    static List<AllocationRuntimeFailure> failures() {
        return new ArrayList<>();
    }

    static void add(
            List<AllocationRuntimeFailure> failures,
            AllocationRuntimeFailureCode code,
            String subject,
            String message
    ) {
        failures.add(new AllocationRuntimeFailure(code, subject, message));
    }

    static void throwIfAny(List<AllocationRuntimeFailure> failures) {
        if (!failures.isEmpty()) {
            throw new AllocationRuntimeValidationException(failures);
        }
    }

    static long revision(long revision) {
        if (revision < 0L) {
            throw failure(
                    AllocationRuntimeFailureCode.INVALID_STATUS_STATE,
                    "revision",
                    "Allocation runtime revision must not be negative"
            );
        }
        return revision;
    }

    static long incrementRevision(long revision) {
        try {
            return Math.incrementExact(revision);
        } catch (ArithmeticException exception) {
            throw failure(
                    AllocationRuntimeFailureCode.ARITHMETIC_OVERFLOW,
                    "revision",
                    "Allocation runtime revision overflowed"
            );
        }
    }

    static String failureMessage(String value) {
        return AllocationValidation.text(
                value,
                "failureMessage",
                AllocationSchema.MAXIMUM_RUNTIME_FAILURE_MESSAGE_LENGTH
        );
    }
}
