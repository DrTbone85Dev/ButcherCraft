package com.butchercraft.world.simulation.scheduler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

record WorkBatchSubmissionResult(
        boolean accepted,
        List<ScheduledSimulationWork> work,
        Optional<WorkFailureCode> failureCode,
        List<String> messages
) {
    WorkBatchSubmissionResult {
        work = List.copyOf(Objects.requireNonNull(work, "work"));
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (accepted == failureCode.isPresent() || (!accepted && !work.isEmpty())) {
            throw new IllegalArgumentException("Batch submission result is inconsistent");
        }
    }

    static WorkBatchSubmissionResult accepted(List<ScheduledSimulationWork> work) {
        return new WorkBatchSubmissionResult(true, work, Optional.empty(), List.of());
    }

    static WorkBatchSubmissionResult rejected(WorkFailureCode code, String message) {
        return new WorkBatchSubmissionResult(false, List.of(), Optional.of(code), List.of(message));
    }
}
