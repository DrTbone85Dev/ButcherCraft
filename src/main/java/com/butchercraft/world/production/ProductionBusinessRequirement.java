package com.butchercraft.world.production;

import com.butchercraft.world.business.runtime.BusinessOperationalStatus;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record ProductionBusinessRequirement(
        boolean businessRequired,
        boolean mustBeOperational,
        boolean mustBeOpen,
        boolean activeShiftRequired,
        boolean maintenanceMustBeFalse,
        int minimumActiveWorkforce,
        Set<BusinessOperationalStatus> allowedStatuses
) {
    private static final ProductionBusinessRequirement NONE = new ProductionBusinessRequirement(
            false, false, false, false, false, 0, Set.of()
    );

    public ProductionBusinessRequirement {
        if (minimumActiveWorkforce < 0) {
            throw new IllegalArgumentException("Minimum business workforce must not be negative");
        }
        EnumSet<BusinessOperationalStatus> copied = EnumSet.noneOf(BusinessOperationalStatus.class);
        Objects.requireNonNull(allowedStatuses, "allowedStatuses")
                .forEach(status -> copied.add(Objects.requireNonNull(status, "status")));
        allowedStatuses = Collections.unmodifiableSet(copied);
        if (!businessRequired && (mustBeOperational || mustBeOpen || activeShiftRequired
                || maintenanceMustBeFalse || minimumActiveWorkforce > 0 || !allowedStatuses.isEmpty())) {
            throw new IllegalArgumentException("Optional production business cannot declare runtime requirements");
        }
    }

    public static ProductionBusinessRequirement none() {
        return NONE;
    }

    public static ProductionBusinessRequirement operational() {
        return new ProductionBusinessRequirement(
                true, true, true, true, true, 1, Set.of(BusinessOperationalStatus.OPERATING)
        );
    }
}
