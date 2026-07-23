package com.butchercraft.world.simulation.scheduler;

public record SimulationExecutionBudget(
        int maximumWorkItemsPerTick,
        int maximumWorkItemsPerStage,
        long maximumHandlerWorkUnits,
        int maximumGeneratedWorkItems,
        int maximumSameTickSubmissions,
        int maximumRetryTransitions,
        int maximumGenerationDepth
) {
    public SimulationExecutionBudget {
        requirePositive(maximumWorkItemsPerTick, "maximumWorkItemsPerTick");
        requirePositive(maximumWorkItemsPerStage, "maximumWorkItemsPerStage");
        if (maximumHandlerWorkUnits <= 0L) throw new IllegalArgumentException("Maximum work units must be positive");
        requirePositive(maximumGeneratedWorkItems, "maximumGeneratedWorkItems");
        requirePositive(maximumSameTickSubmissions, "maximumSameTickSubmissions");
        requirePositive(maximumRetryTransitions, "maximumRetryTransitions");
        requirePositive(maximumGenerationDepth, "maximumGenerationDepth");
    }
    public static SimulationExecutionBudget standard() {
        return new SimulationExecutionBudget(1_000, 250, 10_000L, 250, 100, 100, 8);
    }
    private static void requirePositive(int value, String label) {
        if (value <= 0) throw new IllegalArgumentException(label + " must be positive");
    }
}
