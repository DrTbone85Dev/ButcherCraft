package com.butchercraft.world.planning;

import com.butchercraft.world.production.ProductionOperationResult;
import com.butchercraft.world.production.ProductionPlanDefinition;
import com.butchercraft.world.production.ProductionPlanId;
import com.butchercraft.world.production.ProductionPlanMetadata;
import com.butchercraft.world.production.ProductionRunSnapshot;

import java.util.Optional;
import java.util.Set;

public final class ProductionPlanningSubmissionAdapter implements PlanningSubmissionAdapter {
    public static final PlanningSubmissionAdapterId ID =
            PlanningSubmissionAdapterId.of("butchercraft:production_planning_submission");
    private final PlanningDependencies dependencies;

    public ProductionPlanningSubmissionAdapter(PlanningDependencies dependencies) {
        this.dependencies = java.util.Objects.requireNonNull(dependencies, "dependencies");
    }

    @Override public PlanningSubmissionAdapterId id() { return ID; }
    @Override public PlanCategory supportedCategory() { return PlanCategory.PRODUCTION; }

    @Override
    public PlanningSubmissionResult submit(ApprovedPlanDefinition approved, long tick) {
        if (approved.category() != PlanCategory.PRODUCTION
                || approved.disposition() != ApprovalDisposition.EXECUTABLE) {
            return rejected(PlanningFailureCode.UNSUPPORTED_ACTION, "Approved Plan is not executable Production",
                    approved.id().value());
        }
        ProductionCandidateAction action = approved.approvedAction();
        ProductionPlanId targetId = ProductionPlanId.of(PlanningValidation.derivedId(
                "production_plan", approved.id().value(), id().value(), PlanCategory.PRODUCTION.name()
        ));
        ProductionPlanDefinition.Builder builder = ProductionPlanDefinition.builder()
                .id(targetId).processId(action.processId()).producerActorId(action.producerActorId())
                .batchCount(action.batchCount()).createdSimulationTick(tick)
                .earliestStartTick(Math.max(tick, action.earliestStartTick()))
                .priority(action.productionPriority()).requestingOrderId(action.orderId())
                .metadata(new ProductionPlanMetadata(
                        Set.of("butchercraft:planned"), Optional.of(approved.id().value()),
                        Optional.of("Created by the Economic Planning Engine")
                ));
        action.businessId().ifPresent(builder::businessId);
        action.contractId().ifPresent(builder::governingContractId);
        action.latestCompletionTick().ifPresent(builder::latestCompletionTick);
        action.bindings().forEach(builder::inventoryBinding);
        ProductionOperationResult<ProductionRunSnapshot> result =
                dependencies.productionManager().registerAndSchedulePlan(builder.build(),
                        dependencies.schedulerManager(), tick);
        if (!result.accepted()) {
            return rejected(PlanningFailureCode.SUBMISSION_REJECTED,
                    result.failures().getFirst().message(), targetId.value());
        }
        ProductionRunSnapshot run = result.value().orElseThrow();
        return PlanningSubmissionResult.submitted(targetId.value(), run.scheduledWorkId().orElseThrow());
    }

    private static PlanningSubmissionResult rejected(PlanningFailureCode code, String message, String reference) {
        return PlanningSubmissionResult.rejected(new PlanningFailure(code, message, Optional.of(reference)));
    }
}
