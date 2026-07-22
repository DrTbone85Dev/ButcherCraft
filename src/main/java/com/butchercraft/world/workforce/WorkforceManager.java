package com.butchercraft.world.workforce;

import com.butchercraft.world.business.Business;
import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.business.runtime.BusinessRuntimeRegistry;
import com.butchercraft.world.business.runtime.BusinessRuntimeState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class WorkforceManager {
    private WorkforceRegistry registry;

    public WorkforceManager(WorkforceRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public synchronized WorkforceDefinition createDefinition(Business business, BusinessRuntimeState runtimeState) {
        WorkforceDefinition definition = BuiltInWorkforceDefinitions.defaultDefinition(business, runtimeState);
        registry = registry.with(definition);
        return definition;
    }

    public synchronized WorkforceRegistry loadWithMissingDefaults(
            Collection<Business> businesses,
            BusinessRuntimeRegistry runtimeRegistry
    ) {
        registry = registry.withMissingDefaults(businesses, runtimeRegistry);
        return registry;
    }

    public synchronized List<WorkforceDefinition> definitionsFor(BusinessId businessId) {
        return registry.findByBusinessId(businessId);
    }

    public synchronized List<WorkforcePosition> requiredPositionsForCurrentShift(BusinessRuntimeState runtimeState) {
        Objects.requireNonNull(runtimeState, "runtimeState");
        return runtimeState.activeShiftId()
                .map(shiftId -> requiredPositionsForShift(runtimeState.businessId(), shiftId))
                .orElse(List.of());
    }

    public synchronized List<WorkforcePosition> requiredPositionsForShift(BusinessId businessId, String shiftId) {
        Objects.requireNonNull(businessId, "businessId");
        String normalizedShiftId = WorkforcePosition.requireShiftId(shiftId);
        List<WorkforcePosition> positions = new ArrayList<>();
        for (WorkforceDefinition definition : registry.findByBusinessId(businessId)) {
            positions.addAll(definition.requiredPositionsForShift(normalizedShiftId));
        }
        return List.copyOf(positions);
    }

    public synchronized WorkforceRegistry registry() {
        return registry;
    }

    public synchronized void validate(Collection<Business> businesses, BusinessRuntimeRegistry runtimeRegistry) {
        registry.validateReferences(businesses, runtimeRegistry);
    }
}
