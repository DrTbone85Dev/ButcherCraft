package com.butchercraft.world.production.persistence;

import com.butchercraft.world.production.ProductionPlanRegistry;
import com.butchercraft.world.production.ProductionProcessRegistry;
import com.butchercraft.world.production.ProductionRunSnapshot;

import java.util.List;
import java.util.Objects;

public record ProductionPersistenceSnapshot(
        ProductionProcessRegistry processRegistry,
        ProductionPlanRegistry planRegistry,
        List<ProductionRunSnapshot> runs
) {
    public ProductionPersistenceSnapshot {
        processRegistry = Objects.requireNonNull(processRegistry, "processRegistry");
        planRegistry = Objects.requireNonNull(planRegistry, "planRegistry");
        runs = List.copyOf(Objects.requireNonNull(runs, "runs"));
    }
}
