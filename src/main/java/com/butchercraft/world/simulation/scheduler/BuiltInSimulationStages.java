package com.butchercraft.world.simulation.scheduler;

import java.util.List;

public final class BuiltInSimulationStages {
    public static final SimulationStageId TICK_PREPARATION = SimulationStageId.of("butchercraft:tick_preparation");
    public static final SimulationStageId OBLIGATION_EVALUATION = SimulationStageId.of("butchercraft:obligation_evaluation");
    public static final SimulationStageId PLANNING = SimulationStageId.of("butchercraft:planning");
    public static final SimulationStageId EXECUTION = SimulationStageId.of("butchercraft:execution");
    public static final SimulationStageId OBSERVATION = SimulationStageId.of("butchercraft:observation");
    public static final SimulationStageId TICK_FINALIZATION = SimulationStageId.of("butchercraft:tick_finalization");

    private BuiltInSimulationStages() { }

    public static List<SimulationStageDefinition> definitions() {
        return List.of(
                stage(TICK_PREPARATION, "Tick Preparation", 100, false),
                stage(OBLIGATION_EVALUATION, "Obligation Evaluation", 200, true),
                stage(PLANNING, "Planning", 300, true),
                stage(EXECUTION, "Execution", 400, true),
                stage(OBSERVATION, "Observation", 500, true),
                stage(TICK_FINALIZATION, "Tick Finalization", 600, false)
        );
    }

    private static SimulationStageDefinition stage(
            SimulationStageId id, String name, int order, boolean sameTick
    ) {
        return new SimulationStageDefinition(
                id, name, order, StageFailurePolicy.CONTINUE_STAGE, sameTick, SchedulerSchema.CURRENT_VERSION
        );
    }
}
