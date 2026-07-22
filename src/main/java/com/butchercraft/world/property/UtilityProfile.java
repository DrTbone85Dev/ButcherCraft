package com.butchercraft.world.property;

import java.util.Objects;

public record UtilityProfile(
        ElectricalService electricalService,
        boolean waterService,
        boolean sewerService,
        boolean naturalGas,
        boolean loadingDock,
        boolean railAccess,
        boolean highwayAccess,
        RefrigerationCapacity refrigerationCapacity
) {
    public UtilityProfile {
        electricalService = Objects.requireNonNull(electricalService, "electricalService");
        refrigerationCapacity = Objects.requireNonNull(refrigerationCapacity, "refrigerationCapacity");
    }
}
