package com.butchercraft.world.production;

import com.butchercraft.world.simulation.scheduler.WorkPriority;

public enum ProductionPriority {
    LOW(WorkPriority.LOW),
    NORMAL(WorkPriority.NORMAL),
    HIGH(WorkPriority.HIGH),
    URGENT(WorkPriority.URGENT),
    CRITICAL(WorkPriority.CRITICAL);

    private final WorkPriority schedulerPriority;

    ProductionPriority(WorkPriority schedulerPriority) {
        this.schedulerPriority = schedulerPriority;
    }

    public WorkPriority schedulerPriority() {
        return schedulerPriority;
    }
}
