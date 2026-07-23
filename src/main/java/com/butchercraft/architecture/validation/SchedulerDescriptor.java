package com.butchercraft.architecture.validation;

import java.util.List;
import java.util.Objects;

public record SchedulerDescriptor(
        String id,
        int expectedOrderStep,
        List<SchedulerStageDescriptor> stages
) {
    public SchedulerDescriptor {
        id = Objects.requireNonNull(id, "id").strip();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (expectedOrderStep <= 0) {
            throw new IllegalArgumentException("expectedOrderStep must be positive");
        }
        stages = Objects.requireNonNull(stages, "stages").stream()
                .map(stage -> Objects.requireNonNull(stage, "stage"))
                .toList();
    }
}
