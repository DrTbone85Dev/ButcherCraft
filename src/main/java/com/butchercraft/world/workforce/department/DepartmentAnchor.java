package com.butchercraft.world.workforce.department;

import java.util.Objects;

public record DepartmentAnchor(String dimensionIdentity, int x, int y, int z, int radius) {
    public DepartmentAnchor {
        dimensionIdentity = DepartmentValidation.requireIdentity(dimensionIdentity, "dimensionIdentity");
        if (radius < 1 || radius > 64) {
            throw new IllegalArgumentException("Department anchor radius must be 1-64: " + radius);
        }
    }

    public boolean sameDimension(String candidateDimension) {
        return dimensionIdentity.equals(Objects.requireNonNull(candidateDimension, "candidateDimension"));
    }
}
