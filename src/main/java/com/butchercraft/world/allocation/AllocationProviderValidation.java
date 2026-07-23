package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

final class AllocationProviderValidation {
    private AllocationProviderValidation() {
    }

    static AllocationProviderValidationException failure(
            AllocationProviderFailureCode code,
            AllocationProviderFailureScope scope,
            String subject,
            String message
    ) {
        return new AllocationProviderValidationException(List.of(
                AllocationProviderFailure.global(
                        code,
                        scope,
                        subject,
                        message,
                        java.util.OptionalLong.empty()
                )
        ));
    }

    static AllocationProviderValidationException failure(
            AllocationProviderFailure failure
    ) {
        return new AllocationProviderValidationException(List.of(failure));
    }

    static List<AllocationProviderFailure> failures() {
        return new ArrayList<>();
    }

    static void throwIfAny(List<AllocationProviderFailure> failures) {
        if (!failures.isEmpty()) {
            throw new AllocationProviderValidationException(failures);
        }
    }

    static int limit(int value, int maximum, String field) {
        if (value <= 0 || value > maximum) {
            throw failure(
                    AllocationProviderFailureCode.INVALID_CONTEXT,
                    AllocationProviderFailureScope.REQUEST,
                    field,
                    field + " must be between 1 and " + maximum
            );
        }
        return value;
    }

    static <T extends Comparable<? super T>> List<T> canonical(
            Collection<T> source,
            int maximum,
            String field
    ) {
        Collection<T> input = AllocationValidation.required(source, field);
        if (input.size() > maximum) {
            throw failure(
                    AllocationProviderFailureCode.RESULT_LIMIT_EXCEEDED,
                    AllocationProviderFailureScope.REQUEST,
                    field,
                    field + " exceeds the provider schema bound of " + maximum
            );
        }
        List<T> values = new ArrayList<>(input.size());
        for (T value : input) {
            values.add(AllocationValidation.required(value, field));
        }
        values.sort(Comparator.naturalOrder());
        for (int index = 1; index < values.size(); index++) {
            if (values.get(index - 1).compareTo(values.get(index)) == 0) {
                throw failure(
                        AllocationProviderFailureCode.INVALID_CONTEXT,
                        AllocationProviderFailureScope.REQUEST,
                        field,
                        field + " contains a duplicate canonical value"
                );
            }
        }
        return List.copyOf(values);
    }

    static <T, I extends Comparable<? super I>> List<T> canonical(
            Collection<T> source,
            Function<T, I> identity,
            Comparator<T> order,
            int maximum,
            String field
    ) {
        Collection<T> input = AllocationValidation.required(source, field);
        if (input.size() > maximum) {
            throw failure(
                    AllocationProviderFailureCode.RESULT_LIMIT_EXCEEDED,
                    AllocationProviderFailureScope.PROVIDER,
                    field,
                    field + " exceeds the provider schema bound of " + maximum
            );
        }
        List<T> values = new ArrayList<>(input.size());
        for (T value : input) {
            values.add(AllocationValidation.required(value, field));
        }
        values.sort(order.thenComparing(identity));
        return List.copyOf(values);
    }
}
