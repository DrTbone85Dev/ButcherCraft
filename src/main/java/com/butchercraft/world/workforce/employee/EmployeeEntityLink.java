package com.butchercraft.world.workforce.employee;

import java.util.Objects;
import java.util.UUID;

public record EmployeeEntityLink(UUID entityUuid, String entityTypeId, String dimensionIdentity) {
    public EmployeeEntityLink {
        entityUuid = Objects.requireNonNull(entityUuid, "entityUuid");
        entityTypeId = EmployeeValidation.requireIdentity(entityTypeId, "entityTypeId");
        dimensionIdentity = EmployeeValidation.requireIdentity(dimensionIdentity, "dimensionIdentity");
    }
}
