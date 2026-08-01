package com.butchercraft.world.workforce.department;

import java.util.Objects;
import java.util.Optional;

public record DepartmentDirectory(DepartmentRegistry registry, Optional<DepartmentAnchor> plantEntranceAnchor) {
    public DepartmentDirectory {
        registry = Objects.requireNonNull(registry, "registry");
        plantEntranceAnchor = Objects.requireNonNull(plantEntranceAnchor, "plantEntranceAnchor");
    }

    public static DepartmentDirectory empty() {
        return new DepartmentDirectory(DepartmentRegistry.empty(), Optional.empty());
    }
}
