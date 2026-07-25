package com.butchercraft.world.planning;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record PlanningTriggerPublicationResult(
        Status status,
        Optional<PlanningFailureCode> failureCode,
        List<String> messages,
        OptionalLong nextEligibleTick
) {
    public PlanningTriggerPublicationResult {
        status = Objects.requireNonNull(status, "status");
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        nextEligibleTick = Objects.requireNonNull(nextEligibleTick, "nextEligibleTick");
        if (status.accepted() == failureCode.isPresent()) {
            throw new IllegalArgumentException("Planning trigger publication result is inconsistent");
        }
    }

    public static PlanningTriggerPublicationResult accepted(long nextEligibleTick) {
        return new PlanningTriggerPublicationResult(
                Status.ACCEPTED,
                Optional.empty(),
                List.of(),
                OptionalLong.of(PlanningValidation.tick(nextEligibleTick))
        );
    }

    public static PlanningTriggerPublicationResult duplicate(long nextEligibleTick) {
        return new PlanningTriggerPublicationResult(
                Status.DUPLICATE,
                Optional.empty(),
                List.of("Duplicate Planning trigger observed"),
                OptionalLong.of(PlanningValidation.tick(nextEligibleTick))
        );
    }

    public static PlanningTriggerPublicationResult failure(PlanningFailureCode code, String message) {
        return new PlanningTriggerPublicationResult(
                Status.REJECTED,
                Optional.of(Objects.requireNonNull(code, "code")),
                List.of(PlanningValidation.text(message, "Planning trigger failure")),
                OptionalLong.empty()
        );
    }

    public boolean accepted() {
        return status.accepted();
    }

    public enum Status {
        ACCEPTED(true),
        DUPLICATE(true),
        REJECTED(false);

        private final boolean accepted;

        Status(boolean accepted) {
            this.accepted = accepted;
        }

        public boolean accepted() {
            return accepted;
        }
    }
}
