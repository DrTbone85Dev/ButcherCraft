package com.butchercraft.workstation.reservation;

import java.util.Objects;
import java.util.Optional;

public record WorkstationReservationResult<T>(
        Optional<T> value,
        Optional<WorkstationReservationFailure> failure
) {
    public WorkstationReservationResult {
        value = Objects.requireNonNull(value, "value");
        failure = Objects.requireNonNull(failure, "failure");
        if (value.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("Workstation reservation result must contain one value or one failure");
        }
    }

    public static <T> WorkstationReservationResult<T> succeeded(T value) {
        return new WorkstationReservationResult<>(Optional.of(Objects.requireNonNull(value, "value")), Optional.empty());
    }

    public static <T> WorkstationReservationResult<T> failed(
            WorkstationReservationFailureCode code,
            String detail
    ) {
        return new WorkstationReservationResult<>(
                Optional.empty(),
                Optional.of(new WorkstationReservationFailure(code, detail))
        );
    }

    public boolean succeeded() {
        return value.isPresent();
    }

    public T orThrow() {
        return value.orElseThrow(() -> new IllegalStateException(failure.orElseThrow().detail()));
    }
}
