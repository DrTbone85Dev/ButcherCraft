package com.butchercraft.world.production.scheduler;

import com.butchercraft.world.simulation.scheduler.SimulationWorkTypeId;

public final class ProductionWorkTypes {
    public static final SimulationWorkTypeId PRODUCTION_RUN =
            SimulationWorkTypeId.of("butchercraft:production_run");
    public static final String RUN_ID_PAYLOAD_KEY = "butchercraft:production_run_id";

    private ProductionWorkTypes() {
    }
}
