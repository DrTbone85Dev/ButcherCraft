package com.butchercraft.world.planning;

import com.butchercraft.world.simulation.scheduler.HandlerEffectType;
import com.butchercraft.world.simulation.scheduler.ScheduledSimulationWork;
import com.butchercraft.world.simulation.scheduler.SimulationExecutionContext;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandler;
import com.butchercraft.world.simulation.scheduler.SimulationWorkOutcome;
import com.butchercraft.world.simulation.scheduler.SimulationWorkResult;
import com.butchercraft.world.simulation.scheduler.SimulationWorkTypeId;
import com.butchercraft.world.simulation.scheduler.WorkFailureCode;
import com.butchercraft.world.simulation.scheduler.WorkPayload;
import com.butchercraft.world.simulation.scheduler.WorkPayloadEntry;
import com.butchercraft.world.simulation.scheduler.WorkPayloadValueType;
import com.butchercraft.world.simulation.scheduler.WorkValidationResult;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

public final class EconomicPlanningWorkHandler implements SimulationWorkHandler {
    public static final SimulationWorkTypeId TYPE =
            SimulationWorkTypeId.of("butchercraft:economic_planning_cycle");
    public static final String POLICY_PAYLOAD_KEY = "butchercraft:planning_policy_id";
    private final Supplier<PlanningManager> managerSupplier;

    public EconomicPlanningWorkHandler(Supplier<PlanningManager> managerSupplier) {
        this.managerSupplier = Objects.requireNonNull(managerSupplier, "managerSupplier");
    }

    @Override public SimulationWorkTypeId supportedTypeId() { return TYPE; }
    @Override public HandlerEffectType effectType() { return HandlerEffectType.NON_REPEATABLE; }

    @Override
    public WorkValidationResult validate(ScheduledSimulationWork work) {
        if (!work.typeId().equals(TYPE) || work.payload().entries().size() != 1) {
            return WorkValidationResult.rejected(
                    WorkFailureCode.INVALID_PAYLOAD, "Economic Planning Work payload is invalid");
        }
        WorkPayloadEntry policy = work.payload().find(POLICY_PAYLOAD_KEY).orElse(null);
        if (policy == null || policy.type() != WorkPayloadValueType.IDENTIFIER) {
            return WorkValidationResult.rejected(
                    WorkFailureCode.INVALID_PAYLOAD, "Economic Planning Work requires one policy id");
        }
        try {
            PlanningPolicyId id = PlanningPolicyId.of(policy.canonicalValue());
            if (!id.equals(PlanningSelectionPolicy.DEFAULT_ID)) {
                return WorkValidationResult.rejected(
                        WorkFailureCode.INVALID_PAYLOAD, "Economic Planning policy is unsupported");
            }
        } catch (IllegalArgumentException exception) {
            return WorkValidationResult.rejected(
                    WorkFailureCode.INVALID_PAYLOAD, "Economic Planning policy id is malformed");
        }
        return WorkValidationResult.acceptedResult();
    }

    @Override
    public SimulationWorkResult execute(SimulationExecutionContext context) {
        long tick = context.authoritativeSimulationTick();
        try {
            PlanningCycleSnapshot cycle = managerSupplier.get().executeCycle(tick);
            long artifactCount = (long) cycle.observations().size() + cycle.needs().size()
                    + cycle.constraints().size() + cycle.opportunities().size()
                    + cycle.candidates().size() + cycle.approvedPlans().size();
            int units = Math.toIntExact(Math.min(Integer.MAX_VALUE,
                    Math.min(context.remainingWorkUnits(), Math.max(1L, artifactCount))));
            return new SimulationWorkResult(
                    SimulationWorkOutcome.DEFERRED, Optional.empty(),
                    List.of("Economic Planning Cycle " + cycle.status().name().toLowerCase(
                            java.util.Locale.ROOT)),
                    OptionalLong.of(Math.addExact(tick, 1L)), List.of(),
                    new WorkPayload(List.of(
                            WorkPayloadEntry.identifier("butchercraft:planning_cycle_id", cycle.id().value())
                    )), units, tick
            );
        } catch (RuntimeException exception) {
            return SimulationWorkResult.failed(tick, WorkFailureCode.HANDLER_EXCEPTION,
                    exception.getMessage() == null ? "Economic Planning Cycle failed" : exception.getMessage(), 1);
        }
    }
}
