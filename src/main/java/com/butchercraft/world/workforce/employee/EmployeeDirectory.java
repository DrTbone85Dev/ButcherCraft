package com.butchercraft.world.workforce.employee;

import java.util.Objects;

public record EmployeeDirectory(long nextSequence, EmployeeRegistry registry) {
    public EmployeeDirectory {
        if (nextSequence < 0L) {
            throw new IllegalArgumentException("Employee next sequence must not be negative: " + nextSequence);
        }
        registry = Objects.requireNonNull(registry, "registry");
        long highestSequence = registry.records().stream()
                .mapToLong(EmployeeRecord::sequence)
                .max()
                .orElse(-1L);
        if (nextSequence <= highestSequence) {
            throw new IllegalArgumentException(
                    "Employee next sequence must be greater than existing record sequences: " + nextSequence
            );
        }
    }

    public static EmployeeDirectory empty() {
        return new EmployeeDirectory(0L, EmployeeRegistry.empty());
    }
}
