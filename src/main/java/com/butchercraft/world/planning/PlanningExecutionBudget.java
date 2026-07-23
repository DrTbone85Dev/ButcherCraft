package com.butchercraft.world.planning;

public record PlanningExecutionBudget(
        int maximumObservations,
        int maximumNeeds,
        int maximumConstraints,
        int maximumOpportunities,
        int maximumOpportunitiesPerNeed,
        int maximumCandidates,
        int maximumCandidatesPerNeed,
        int maximumEvaluations,
        int maximumApprovedPlans,
        int maximumApprovedPlansPerNeed,
        int maximumSubmissions,
        int maximumAggregationGroupSize,
        int maximumRecursiveDepth,
        long maximumProviderWorkUnits,
        long maximumTotalWorkUnits,
        int maximumPayloadSize
) {
    public PlanningExecutionBudget {
        if (maximumObservations <= 0 || maximumNeeds <= 0 || maximumConstraints <= 0
                || maximumOpportunities <= 0 || maximumOpportunitiesPerNeed <= 0
                || maximumCandidates <= 0 || maximumCandidatesPerNeed <= 0 || maximumEvaluations <= 0
                || maximumApprovedPlans <= 0 || maximumApprovedPlansPerNeed <= 0 || maximumSubmissions <= 0
                || maximumAggregationGroupSize <= 0 || maximumRecursiveDepth <= 0
                || maximumProviderWorkUnits <= 0 || maximumTotalWorkUnits <= 0 || maximumPayloadSize <= 0) {
            throw new IllegalArgumentException("Every Planning budget dimension must be positive");
        }
    }

    public static PlanningExecutionBudget standard() {
        return new PlanningExecutionBudget(
                100_000, 25_000, 100_000, 100_000, 64, 250_000, 128, 250_000,
                5_000, 16, 5_000, 1_000, 8, 1_000_000L, 5_000_000L, 65_536
        );
    }
}
