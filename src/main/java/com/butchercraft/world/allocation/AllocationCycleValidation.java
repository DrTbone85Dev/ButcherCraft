package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Function;

final class AllocationCycleValidation {
    private AllocationCycleValidation() {
    }

    static AllocationCycleValidationException failure(
            AllocationCycleFailureCode code,
            AllocationCycleFailureScope scope,
            String subject,
            String message
    ) {
        return new AllocationCycleValidationException(
                List.of(new AllocationCycleFailure(code, scope, subject, message))
        );
    }

    static <T, I extends Comparable<? super I>> List<T> canonical(
            Collection<T> source,
            Function<T, I> identity,
            Comparator<T> order,
            int maximum,
            AllocationCycleFailureCode duplicateCode,
            String label
    ) {
        Collection<T> input = AllocationValidation.required(source, label);
        if (input.size() > maximum) {
            throw failure(
                    AllocationCycleFailureCode.STRUCTURAL_BOUND_EXCEEDED,
                    AllocationCycleFailureScope.CYCLE,
                    label,
                    label + " exceeds the Allocation schema bound of " + maximum
            );
        }
        List<T> values = new ArrayList<>(input.size());
        for (T value : input) {
            values.add(AllocationValidation.required(value, label));
        }
        values.sort(order);
        TreeSet<I> identities = new TreeSet<>();
        for (T value : values) {
            I id = identity.apply(value);
            if (!identities.add(id)) {
                throw failure(
                        duplicateCode,
                        AllocationCycleFailureScope.CYCLE,
                        id.toString(),
                        "Duplicate " + label + " identity"
                );
            }
        }
        return List.copyOf(values);
    }

    static AllocationCycleValidationException structural(RuntimeException exception) {
        if (exception instanceof AllocationCycleValidationException cycle) {
            return cycle;
        }
        String message = exception.getMessage() == null
                ? "Allocation structural validation failed"
                : exception.getMessage();
        return failure(
                AllocationCycleFailureCode.STRUCTURAL_VALIDATION_FAILED,
                AllocationCycleFailureScope.CYCLE,
                "allocation_cycle",
                message
        );
    }

    static long add(long first, long second, String subject) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException exception) {
            throw failure(
                    AllocationCycleFailureCode.ARITHMETIC_OVERFLOW,
                    AllocationCycleFailureScope.CYCLE,
                    subject,
                    "Allocation cycle operation count overflowed"
            );
        }
    }
}
