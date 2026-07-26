package com.butchercraft.machine.grinder.production;

import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.grinder.execution.GrinderWorkstationReference;
import com.butchercraft.workstation.WorkstationFailure;
import com.butchercraft.workstation.WorkstationProductionRequestResult;
import com.butchercraft.workstation.WorkstationProductionSnapshot;
import com.butchercraft.workstation.WorkstationTickContext;
import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.ExecutionOperationSnapshot;
import com.butchercraft.world.execution.ExecutionOwnerResultEvidence;
import com.butchercraft.world.execution.ExecutionManager;
import com.butchercraft.world.execution.ExecutionResultEvidence;
import com.butchercraft.world.execution.ExecutionStatus;
import com.butchercraft.world.production.ProductionManager;
import com.butchercraft.world.production.ProductionOperationResult;
import com.butchercraft.world.production.ProductionRunId;
import com.butchercraft.world.production.ProductionRunSnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class GrinderProductionAdapter {
    private static final String EXECUTION_STATUS_PREFIX = "butchercraft:execution_status/";

    private GrinderProductionAdapter() {
    }

    public static ProductionOperationResult<ProductionRunSnapshot> requestAndObserve(
            ProductionManager productionManager,
            ExecutionManager executionManager,
            ProductionRunId runId,
            GrinderBlockEntity grinder,
            WorkstationTickContext context,
            ResourceLocation expectedProcessId,
            long authoritativeTick
    ) {
        ProductionOperationResult<ProductionRunSnapshot> assigned = assign(
                productionManager,
                runId,
                context,
                expectedProcessId,
                authoritativeTick
        );
        if (!assigned.accepted()) return assigned;

        WorkstationProductionRequestResult requested =
                Objects.requireNonNull(grinder, "grinder").requestProductionProcessing(context);
        if (!requested.accepted()) {
            WorkstationFailure failure = requested.failure().orElseThrow();
            return productionManager.recordWorkstationRejection(
                    runId,
                    failure.developerExplanation(),
                    authoritativeTick
            );
        }
        return observe(
                productionManager,
                executionManager,
                runId,
                grinder,
                context,
                expectedProcessId,
                authoritativeTick
        );
    }

    public static ProductionOperationResult<ProductionRunSnapshot> assign(
            ProductionManager productionManager,
            ProductionRunId runId,
            WorkstationTickContext context,
            ResourceLocation expectedProcessId,
            long authoritativeTick
    ) {
        Objects.requireNonNull(context, "context");
        ResourceLocation process = Objects.requireNonNull(expectedProcessId, "expectedProcessId");
        return Objects.requireNonNull(productionManager, "productionManager").assignWorkstation(
                Objects.requireNonNull(runId, "runId"),
                workstationIdentity(context),
                process.toString(),
                authoritativeTick
        );
    }

    public static ProductionOperationResult<ProductionRunSnapshot> observe(
            ProductionManager productionManager,
            ExecutionManager executionManager,
            ProductionRunId runId,
            GrinderBlockEntity grinder,
            WorkstationTickContext context,
            ResourceLocation expectedProcessId,
            long authoritativeTick
    ) {
        Objects.requireNonNull(productionManager, "productionManager");
        Objects.requireNonNull(executionManager, "executionManager");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(expectedProcessId, "expectedProcessId");
        WorkstationProductionSnapshot snapshot = Objects.requireNonNull(grinder, "grinder").productionSnapshot();
        String workstationIdentity = workstationIdentity(context);
        String processIdentity = expectedProcessId.toString();

        if (snapshot.selectedOperationId().isPresent()
                && !snapshot.selectedOperationId().orElseThrow().equals(expectedProcessId)) {
            return productionManager.recordWorkstationRejection(
                    runId,
                    "Grinder selected a different process than the Production assignment",
                    authoritativeTick
            );
        }
        if (snapshot.activeExecutionOperationId().isEmpty()) {
            if (snapshot.lastFailure().isPresent()) {
                return productionManager.recordWorkstationFailure(
                        runId,
                        snapshot.lastFailure().orElseThrow().developerExplanation(),
                        authoritativeTick
                );
            }
            return productionManager.findRun(runId)
                    .map(ProductionOperationResult::accepted)
                    .orElseGet(() -> ProductionOperationResult.rejected(
                            com.butchercraft.world.production.ProductionFailure.of(
                                    com.butchercraft.world.production.ProductionFailureCode.UNKNOWN_RUN,
                                    "Unknown production run",
                                    runId.value()
                            )
                    ));
        }

        ExecutionOperationId operationId = snapshot.activeExecutionOperationId().orElseThrow();
        ProductionOperationResult<ProductionRunSnapshot> bound = productionManager.recordWorkstationExecution(
                runId,
                workstationIdentity,
                processIdentity,
                operationId.value(),
                authoritativeTick
        );
        if (!bound.accepted()) return bound;

        ExecutionOperationSnapshot execution = executionManager.find(operationId).orElse(null);
        if (execution == null) {
            return productionManager.recordWorkstationFailure(
                    runId,
                    "Production observed a Grinder Execution operation that no longer exists",
                    authoritativeTick
            );
        }
        if (!execution.status().terminal()) return bound;
        if (execution.status() == ExecutionStatus.UNKNOWN_OUTCOME) {
            return productionManager.recordWorkstationUnknownOutcome(
                    runId,
                    terminalMessage(execution),
                    authoritativeTick
            );
        }
        if (execution.status() == ExecutionStatus.REJECTED) {
            return productionManager.recordWorkstationRejection(
                    runId,
                    terminalMessage(execution),
                    authoritativeTick
            );
        }
        if (execution.status() != ExecutionStatus.SUCCEEDED) {
            return productionManager.recordWorkstationFailure(
                    runId,
                    terminalMessage(execution),
                    authoritativeTick
            );
        }

        if (execution.ownerResultEvidence().isEmpty() || execution.resultEvidence().isEmpty()) {
            return productionManager.recordWorkstationFailure(
                    runId,
                    "Successful Grinder Execution did not publish complete result evidence",
                    authoritativeTick
            );
        }
        ExecutionOwnerResultEvidence ownerResult = execution.ownerResultEvidence().orElseThrow();
        if (snapshot.ownerResultEvidence().isPresent()
                && !snapshot.ownerResultEvidence().orElseThrow().equals(ownerResult)) {
            return productionManager.recordWorkstationFailure(
                    runId,
                    "Grinder owner result evidence does not match Execution result evidence",
                    authoritativeTick
            );
        }
        ExecutionResultEvidence resultEvidence = execution.resultEvidence().orElseThrow();
        return productionManager.completeFromWorkstation(
                runId,
                workstationIdentity,
                processIdentity,
                operationId.value(),
                EXECUTION_STATUS_PREFIX + execution.status().serializedName(),
                ownerResult.ownerResultIdentity(),
                ownerResult.contentDigest(),
                resultEvidence.evidenceIdentity(),
                resultEvidence.resultContentDigest(),
                authoritativeTick
        );
    }

    private static String workstationIdentity(WorkstationTickContext context) {
        return GrinderWorkstationReference.of(context.level(), context.blockPos()).identity();
    }

    private static String terminalMessage(ExecutionOperationSnapshot execution) {
        return execution.failure()
                .map(failure -> failure.message())
                .orElse("Execution reached terminal status " + execution.status().serializedName());
    }
}
