package com.butchercraft.architecture.validation;

import java.util.List;
import java.util.Objects;

public record SchedulerStageDescriptor(
        String id,
        int executionOrder,
        List<String> dependencyIds
) {
    public SchedulerStageDescriptor {
        id = requireText(id, "id");
        dependencyIds = Objects.requireNonNull(dependencyIds, "dependencyIds").stream()
                .map(dependency -> requireText(dependency, "dependency"))
                .toList();
    }

    private static String requireText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }
}
