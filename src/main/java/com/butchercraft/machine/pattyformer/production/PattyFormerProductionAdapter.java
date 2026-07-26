package com.butchercraft.machine.pattyformer.production;

import com.butchercraft.machine.pattyformer.PattyFormerBlockEntity;
import com.butchercraft.machine.pattyformer.execution.PattyFormerWorkstationReference;
import com.butchercraft.workstation.WorkstationFailure;
import com.butchercraft.workstation.WorkstationProductionRequestResult;
import com.butchercraft.workstation.WorkstationProductionSnapshot;
import com.butchercraft.workstation.WorkstationTickContext;
import com.butchercraft.world.execution.ExecutionManager;
import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.ExecutionOperationSnapshot;
import com.butchercraft.world.execution.ExecutionOwnerResultEvidence;
import com.butchercraft.world.execution.ExecutionResultEvidence;
import com.butchercraft.world.execution.ExecutionStatus;
import com.butchercraft.world.production.ProductionFailure;
import com.butchercraft.world.production.ProductionFailureCode;
import com.butchercraft.world.production.ProductionManager;
import com.butchercraft.world.production.ProductionOperationResult;
import com.butchercraft.world.production.ProductionRunId;
import com.butchercraft.world.production.ProductionRunSnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class PattyFormerProductionAdapter {
    private static final String EXECUTION_STATUS_PREFIX = "butchercraft:execution_status/";

    private PattyFormerProductionAdapter() {
    }

    public static ProductionOperationResult<ProductionRunSnapshot> requestAndObserveChainStep(
            ProductionManager productionManager,
            ExecutionManager executionManager,
            ProductionRunId runId,
            String stepIdentity,
            PattyFormerBlockEntity pattyFormer,
            WorkstationTickContext context,
            ResourceLocation expectedProcessId,
            long authoritativeTick
    ) {
        ProductionOperationResult<ProductionRunSnapshot> assigned = assignChainStep(
                productionManager,
                runId,
                stepIdentity,
                context,
                expectedProcessId,
                authoritativeTick
        );
        if (!assigned.accepted()) return assigned;

        WorkstationProductionRequestResult requested =
                Objects.requireNonNull(pattyFormer, "pattyFormer").requestProductionProcessing(context);
        if (!requested.accepted()) {
            WorkstationFailure failure = requested.failure().orElseThrow();
            return productionManager.recordWorkstationChainRejection(
                    runId,
                    stepIdentity,
                    failure.developerExplanation(),
                    authoritativeTick
            );
        }
        return observeChainStep(
                productionManager,
                executionManager,
                runId,
                stepIdentity,
                pattyFormer,
                context,
                expectedProcessId,
                authoritativeTick
        );
    }

    public static ProductionOperationResult<ProductionRunSnapshot> assignChainStep(
            ProductionManager productionManager,
            ProductionRunId runId,
            String stepIdentity,
            WorkstationTickContext context,
            ResourceLocation expectedProcessId,
            long authoritativeTick
    ) {
        Objects.requireNonNull(context, "context");
        ResourceLocation process = Objects.requireNonNull(expectedProcessId, "expectedProcessId");
        return Objects.requireNonNull(productionManager, "productionManager").assignWorkstationChainStep(
                Objects.requireNonNull(runId, "runId"),
                Objects.requireNonNull(stepIdentity, "stepIdentity"),
                workstationIdentity(context),
                process.toString(),
                authoritativeTick
        );
    }

    public static ProductionOperationResult<ProductionRunSnapshot> observeChainStep(
            ProductionManager productionManager,
            ExecutionManager executionManager,
            ProductionRunId runId,
            String stepIdentity,
            PattyFormerBlockEntity pattyFormer,
            WorkstationTickContext context,
            ResourceLocation expectedProcessId,
            long authoritativeTick
    ) {
        Objects.requireNonNull(productionManager, "productionManager");
        Objects.requireNonNull(executionManager, "executionManager");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(stepIdentity, "stepIdentity");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(expectedProcessId, "expectedProcessId");
        WorkstationProductionSnapshot snapshot =
                Objects.requireNonNull(pattyFormer, "pattyFormer").productionSnapshot();
        String workstationIdentity = workstationIdentity(context);
        String processIdentity = expectedProcessId.toString();

        if (snapshot.selectedOperationId().isPresent()
                && !snapshot.selectedOperationId().orElseThrow().equals(expectedProcessId)) {
            return productionManager.recordWorkstationChainRejection(
                    runId,
                    stepIdentity,
                    "Patty Former selected a different process than the Production assignment",
                    authoritativeTick
            );
        }
        if (snapshot.activeExecutionOperationId().isEmpty()) {
            if (snapshot.lastFailure().isPresent()) {
                return productionManager.recordWorkstationChainFailure(
                        runId,
                        stepIdentity,
                        snapshot.lastFailure().orElseThrow().developerExplanation(),
                        authoritativeTick
                );
            }
            return productionManager.findRun(runId)
                    .map(ProductionOperationResult::accepted)
                    .orElseGet(() -> ProductionOperationResult.rejected(ProductionFailure.of(
                            ProductionFailureCode.UNKNOWN_RUN,
                            "Unknown production run",
                            runId.value()
                    )));
        }

        ExecutionOperationId operationId = snapshot.activeExecutionOperationId().orElseThrow();
        ProductionOperationResult<ProductionRunSnapshot> bound = productionManager.recordWorkstationChainExecution(
                runId,
                stepIdentity,
                workstationIdentity,
                processIdentity,
                operationId.value(),
                authoritativeTick
        );
        if (!bound.accepted()) return bound;

        ExecutionOperationSnapshot execution = executionManager.find(operationId).orElse(null);
        if (execution == null) {
            return productionManager.recordWorkstationChainFailure(
                    runId,
                    stepIdentity,
                    "Production observed a Patty Former Execution operation that no longer exists",
                    authoritativeTick
            );
        }
        if (!execution.status().terminal()) return bound;
        if (execution.status() == ExecutionStatus.UNKNOWN_OUTCOME) {
            return productionManager.recordWorkstationChainUnknownOutcome(
                    runId,
                    stepIdentity,
                    terminalMessage(execution),
                    authoritativeTick
            );
        }
        if (execution.status() == ExecutionStatus.REJECTED) {
            return productionManager.recordWorkstationChainRejection(
                    runId,
                    stepIdentity,
                    terminalMessage(execution),
                    authoritativeTick
            );
        }
        if (execution.status() != ExecutionStatus.SUCCEEDED) {
            return productionManager.recordWorkstationChainFailure(
                    runId,
                    stepIdentity,
                    terminalMessage(execution),
                    authoritativeTick
            );
        }

        if (execution.ownerResultEvidence().isEmpty() || execution.resultEvidence().isEmpty()) {
            return productionManager.recordWorkstationChainFailure(
                    runId,
                    stepIdentity,
                    "Successful Patty Former Execution did not publish complete result evidence",
                    authoritativeTick
            );
        }
        ExecutionOwnerResultEvidence ownerResult = execution.ownerResultEvidence().orElseThrow();
        if (snapshot.ownerResultEvidence().isPresent()
                && !snapshot.ownerResultEvidence().orElseThrow().equals(ownerResult)) {
            return productionManager.recordWorkstationChainFailure(
                    runId,
                    stepIdentity,
                    "Patty Former owner result evidence does not match Execution result evidence",
                    authoritativeTick
            );
        }
        ExecutionResultEvidence resultEvidence = execution.resultEvidence().orElseThrow();
        return productionManager.completeWorkstationChainStepFromWorkstation(
                runId,
                stepIdentity,
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
        return PattyFormerWorkstationReference.of(context.level(), context.blockPos()).identity();
    }

    private static String terminalMessage(ExecutionOperationSnapshot execution) {
        return execution.failure()
                .map(failure -> failure.message())
                .orElse("Execution reached terminal status " + execution.status().serializedName());
    }
}
