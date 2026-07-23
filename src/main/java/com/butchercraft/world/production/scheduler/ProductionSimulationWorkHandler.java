package com.butchercraft.world.production.scheduler;

import com.butchercraft.world.production.ProductionManager;
import com.butchercraft.world.production.ProductionRunId;
import com.butchercraft.world.simulation.scheduler.HandlerEffectType;
import com.butchercraft.world.simulation.scheduler.ScheduledSimulationWork;
import com.butchercraft.world.simulation.scheduler.SimulationExecutionContext;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandler;
import com.butchercraft.world.simulation.scheduler.SimulationWorkResult;
import com.butchercraft.world.simulation.scheduler.SimulationWorkTypeId;
import com.butchercraft.world.simulation.scheduler.WorkFailureCode;
import com.butchercraft.world.simulation.scheduler.WorkPayloadEntry;
import com.butchercraft.world.simulation.scheduler.WorkPayloadValueType;
import com.butchercraft.world.simulation.scheduler.WorkValidationResult;

import java.util.Objects;

public final class ProductionSimulationWorkHandler implements SimulationWorkHandler {
    private final ProductionManager manager;

    public ProductionSimulationWorkHandler(ProductionManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @Override
    public SimulationWorkTypeId supportedTypeId() {
        return ProductionWorkTypes.PRODUCTION_RUN;
    }

    @Override
    public HandlerEffectType effectType() {
        return HandlerEffectType.TRANSACTION_BACKED;
    }

    @Override
    public WorkValidationResult validate(ScheduledSimulationWork work) {
        if (!work.typeId().equals(supportedTypeId())) {
            return WorkValidationResult.rejected(WorkFailureCode.UNKNOWN_WORK_TYPE,
                    "Production handler received a different Work type");
        }
        if (work.payload().entries().size() != 1) {
            return WorkValidationResult.rejected(WorkFailureCode.INVALID_PAYLOAD,
                    "Production Work payload requires exactly one run id");
        }
        WorkPayloadEntry entry = work.payload().find(ProductionWorkTypes.RUN_ID_PAYLOAD_KEY).orElse(null);
        if (entry == null || entry.type() != WorkPayloadValueType.IDENTIFIER) {
            return WorkValidationResult.rejected(WorkFailureCode.INVALID_PAYLOAD,
                    "Production Work payload has no canonical run id");
        }
        ProductionRunId runId;
        try {
            runId = ProductionRunId.of(entry.canonicalValue());
        } catch (IllegalArgumentException exception) {
            return WorkValidationResult.rejected(WorkFailureCode.INVALID_PAYLOAD,
                    "Production Work payload run id is invalid");
        }
        if (manager.findRun(runId).isEmpty()) {
            return WorkValidationResult.rejected(WorkFailureCode.UNKNOWN_WORK,
                    "Production Work references an unknown run");
        }
        return WorkValidationResult.acceptedResult();
    }

    @Override
    public SimulationWorkResult execute(SimulationExecutionContext context) {
        WorkPayloadEntry entry = context.work().payload().find(ProductionWorkTypes.RUN_ID_PAYLOAD_KEY)
                .orElseThrow();
        return manager.executeScheduledRun(
                ProductionRunId.of(entry.canonicalValue()),
                context
        );
    }
}
