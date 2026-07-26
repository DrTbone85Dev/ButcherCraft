package com.butchercraft.world.execution;

import com.butchercraft.world.simulation.scheduler.SimulationWorkTypeId;

public final class ExecutionWorkTypes {
    public static final SimulationWorkTypeId GENERIC_EXECUTION_OPERATION =
            SimulationWorkTypeId.of("butchercraft:generic_execution_operation");
    public static final String OPERATION_ID_PAYLOAD_KEY = "butchercraft:execution_operation_id";

    private ExecutionWorkTypes() {
    }
}
