package com.butchercraft.workstation.reservation;

import java.util.Objects;
import java.util.Optional;

public record WorkstationReservationResult<T>(
        Optional<T> value,
        Optional<WorkstationReservationFailure> failure,
        Optional<WorkstationReservationSuccessCode> successCode
) {
    public WorkstationReservationResult {
        value = Objects.requireNonNull(value, "value");
        failure = Objects.requireNonNull(failure, "failure");
        successCode = Objects.requireNonNull(successCode, "successCode");
        if (value.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("Workstation reservation result must contain one value or one failure");
        }
        if (value.isPresent() != successCode.isPresent()) {
            throw new IllegalArgumentException("Successful reservation result must contain a success code");
        }
    }

    public static <T> WorkstationReservationResult<T> succeeded(T value) {
        return succeeded(value, WorkstationReservationSuccessCode.ACQUIRED);
    }

    public static <T> WorkstationReservationResult<T> succeeded(
            T value,
            WorkstationReservationSuccessCode successCode
    ) {
        return new WorkstationReservationResult<>(
                Optional.of(Objects.requireNonNull(value, "value")),
                Optional.empty(),
                Optional.of(Objects.requireNonNull(successCode, "successCode"))
        );
    }

    public static <T> WorkstationReservationResult<T> failed(
            WorkstationReservationFailureCode code,
            String detail
    ) {
        return new WorkstationReservationResult<>(
                Optional.empty(),
                Optional.of(new WorkstationReservationFailure(code, detail)),
                Optional.empty()
        );
    }

    public boolean succeeded() {
        return value.isPresent();
    }

    public T orThrow() {
        return value.orElseThrow(() -> new IllegalStateException(failure.orElseThrow().detail()));
    }
}
