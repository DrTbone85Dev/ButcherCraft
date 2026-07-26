package com.butchercraft.world.execution;

import com.butchercraft.world.simulation.scheduler.HandlerEffectType;
import com.butchercraft.world.simulation.scheduler.ScheduledSimulationWork;
import com.butchercraft.world.simulation.scheduler.SchedulerEffectPolicy;
import com.butchercraft.world.simulation.scheduler.SimulationExecutionContext;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandler;
import com.butchercraft.world.simulation.scheduler.SimulationWorkResult;
import com.butchercraft.world.simulation.scheduler.SimulationWorkTypeId;
import com.butchercraft.world.simulation.scheduler.WorkFailureCode;
import com.butchercraft.world.simulation.scheduler.WorkPayloadEntry;
import com.butchercraft.world.simulation.scheduler.WorkPayloadValueType;
import com.butchercraft.world.simulation.scheduler.WorkValidationResult;

import java.util.Objects;
import java.util.function.Supplier;

public final class GenericExecutionWorkHandler implements SimulationWorkHandler {
    private final Supplier<ExecutionManager> managerSupplier;

    public GenericExecutionWorkHandler(Supplier<ExecutionManager> managerSupplier) {
        this.managerSupplier = Objects.requireNonNull(managerSupplier, "managerSupplier");
    }

    @Override
    public SimulationWorkTypeId supportedTypeId() {
        return ExecutionWorkTypes.GENERIC_EXECUTION_OPERATION;
    }

    @Override
    public HandlerEffectType effectType() {
        return HandlerEffectType.IDEMPOTENT;
    }

    @Override
    public SchedulerEffectPolicy effectPolicy() {
        return SchedulerEffectPolicy.idempotent(
                supportedTypeId(),
                "butchercraft:execution",
                "IM-011 generic Execution Scheduler effect policy"
        );
    }

    @Override
    public WorkValidationResult validate(ScheduledSimulationWork work) {
        if (!work.typeId().equals(supportedTypeId())) {
            return WorkValidationResult.rejected(
                    WorkFailureCode.UNKNOWN_WORK_TYPE,
                    "Generic Execution handler received a different Work type"
            );
        }
        if (work.payload().entries().size() != 1) {
            return WorkValidationResult.rejected(
                    WorkFailureCode.INVALID_PAYLOAD,
                    "Generic Execution Work payload requires exactly one operation id"
            );
        }
        WorkPayloadEntry operation = work.payload().find(ExecutionWorkTypes.OPERATION_ID_PAYLOAD_KEY).orElse(null);
        if (operation == null || operation.type() != WorkPayloadValueType.IDENTIFIER) {
            return WorkValidationResult.rejected(
                    WorkFailureCode.INVALID_PAYLOAD,
                    "Generic Execution Work payload has no canonical operation id"
            );
        }
        try {
            ExecutionOperationId.of(operation.canonicalValue());
        } catch (IllegalArgumentException exception) {
            return WorkValidationResult.rejected(
                    WorkFailureCode.INVALID_PAYLOAD,
                    "Generic Execution Work payload operation id is invalid"
            );
        }
        return WorkValidationResult.acceptedResult();
    }

    @Override
    public SimulationWorkResult execute(SimulationExecutionContext context) {
        WorkPayloadEntry operation = context.work().payload().find(ExecutionWorkTypes.OPERATION_ID_PAYLOAD_KEY)
                .orElseThrow();
        return managerSupplier.get().executeScheduledOperation(
                ExecutionOperationId.of(operation.canonicalValue()),
                context
        );
    }
}
