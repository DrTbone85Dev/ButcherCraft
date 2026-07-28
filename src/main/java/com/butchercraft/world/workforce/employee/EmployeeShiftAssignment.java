package com.butchercraft.world.workforce.employee;

import com.butchercraft.world.business.runtime.BusinessRuntimeConfigurationIdentity;
import com.butchercraft.world.business.runtime.BusinessShiftDefinition;
import com.butchercraft.world.business.runtime.BusinessShiftSet;

import java.util.Objects;

public record EmployeeShiftAssignment(
        String shiftId,
        String shiftIdentity,
        String shiftDisplayName,
        String shiftSetIdentity,
        String configurationIdentity
) {
    public EmployeeShiftAssignment {
        shiftId = EmployeeValidation.requireShiftId(shiftId, "shiftId");
        shiftIdentity = EmployeeValidation.requireIdentity(shiftIdentity, "shiftIdentity");
        shiftDisplayName = EmployeeValidation.requireText(shiftDisplayName, "shiftDisplayName");
        shiftSetIdentity = EmployeeValidation.requireIdentity(shiftSetIdentity, "shiftSetIdentity");
        configurationIdentity = EmployeeValidation.requireIdentity(configurationIdentity, "configurationIdentity");
    }

    public static EmployeeShiftAssignment from(
            BusinessShiftDefinition shift,
            BusinessShiftSet shiftSet,
            BusinessRuntimeConfigurationIdentity configurationIdentity
    ) {
        Objects.requireNonNull(shift, "shift");
        Objects.requireNonNull(shiftSet, "shiftSet");
        Objects.requireNonNull(configurationIdentity, "configurationIdentity");
        return new EmployeeShiftAssignment(
                shift.id(),
                shift.identity().value(),
                shift.displayName(),
                shiftSet.identity().value(),
                configurationIdentity.value()
        );
    }

    public boolean matches(BusinessShiftDefinition shift) {
        Objects.requireNonNull(shift, "shift");
        return shift.id().equals(shiftId) && shift.identity().value().equals(shiftIdentity);
    }
}
