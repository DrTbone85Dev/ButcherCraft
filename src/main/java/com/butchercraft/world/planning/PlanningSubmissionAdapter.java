package com.butchercraft.world.planning;

public interface PlanningSubmissionAdapter {
    PlanningSubmissionAdapterId id();
    PlanCategory supportedCategory();
    PlanningSubmissionResult submit(ApprovedPlanDefinition approvedPlan, long simulationTick);
}

record PlanningSubmissionResult(
        PlanningSubmissionStatus status,
        java.util.Optional<String> targetPlanReference,
        java.util.Optional<com.butchercraft.world.simulation.scheduler.SimulationWorkId> workReference,
        java.util.Optional<PlanningFailure> failure
) {
    PlanningSubmissionResult {
        java.util.Objects.requireNonNull(status); java.util.Objects.requireNonNull(targetPlanReference);
        java.util.Objects.requireNonNull(workReference); java.util.Objects.requireNonNull(failure);
    }
    static PlanningSubmissionResult submitted(String target,
            com.butchercraft.world.simulation.scheduler.SimulationWorkId work) {
        return new PlanningSubmissionResult(PlanningSubmissionStatus.SUBMITTED,
                java.util.Optional.of(target), java.util.Optional.of(work), java.util.Optional.empty());
    }
    static PlanningSubmissionResult rejected(PlanningFailure failure) {
        return new PlanningSubmissionResult(PlanningSubmissionStatus.REJECTED,
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.of(failure));
    }
}
