package com.butchercraft.world.planning;

import com.butchercraft.world.simulation.scheduler.HandlerEffectType;
import com.butchercraft.world.simulation.scheduler.ScheduledSimulationWork;
import com.butchercraft.world.simulation.scheduler.SchedulerEffectPolicy;
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
    @Override public SchedulerEffectPolicy effectPolicy() {
        return SchedulerEffectPolicy.nonRepeatableContinuation(
                TYPE,
                "butchercraft:planning",
                "IM-010 Planning cadence remains NON_REPEATABLE until cycle-scoped Scheduler Effect Identity exists"
        );
    }

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
        PlanningCadenceExecutionResult result = managerSupplier.get()
                .executeCadenceCycle(tick, context.remainingWorkUnits());
        List<WorkPayloadEntry> summaryEntries = result.cycle()
                .map(cycle -> List.of(
                        WorkPayloadEntry.identifier("butchercraft:planning_cycle_id", cycle.id().value()),
                        WorkPayloadEntry.identifier(
                                "butchercraft:planning_frozen_input_identity",
                                cycle.cadenceEvidence().orElseThrow().frozenInputIdentity()
                        ),
                        WorkPayloadEntry.longValue(
                                "butchercraft:planning_consumed_trigger_count",
                                cycle.cadenceEvidence().orElseThrow().consumedTriggers().size()
                        )
                ))
                .orElseGet(() -> List.of(WorkPayloadEntry.identifier(
                        "butchercraft:planning_cadence_state",
                        "butchercraft:planning_cadence/deferred"
                )));
        return new SimulationWorkResult(
                SimulationWorkOutcome.DEFERRED, Optional.empty(),
                result.messages(),
                OptionalLong.of(result.nextEligibleTick()), List.of(),
                new WorkPayload(summaryEntries), result.workUnits(), tick
        );
    }
}
