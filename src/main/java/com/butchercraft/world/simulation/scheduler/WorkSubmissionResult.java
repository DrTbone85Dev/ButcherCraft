package com.butchercraft.world.simulation.scheduler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record WorkSubmissionResult(
        boolean accepted,
        Optional<ScheduledSimulationWork> work,
        Optional<WorkFailureCode> failureCode,
        List<String> messages
) {
    public WorkSubmissionResult {
        work = Objects.requireNonNull(work, "work");
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (accepted != work.isPresent() || accepted == failureCode.isPresent()) {
            throw new IllegalArgumentException("Submission result is inconsistent");
        }
    }
    public static WorkSubmissionResult accepted(ScheduledSimulationWork work) {
        return new WorkSubmissionResult(true, Optional.of(work), Optional.empty(), List.of());
    }
    public static WorkSubmissionResult rejected(WorkFailureCode code, String message) {
        return new WorkSubmissionResult(false, Optional.empty(), Optional.of(code), List.of(message));
    }
}
