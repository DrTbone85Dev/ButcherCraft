package com.butchercraft.world.planning;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningDomainTest {
    @Test
    void identifiersAreStableComparableAndValidated() {
        assertEquals(PlanningCycleId.of("butchercraft:planning_cycle/tick_1"),
                PlanningCycleId.forTick(1L));
        assertTrue(NeedId.of("test:a").compareTo(NeedId.of("test:b")) < 0);
        assertThrows(IllegalArgumentException.class, () -> NeedId.of("not namespaced"));
        assertThrows(IllegalArgumentException.class, () -> PlanningCycleId.forTick(-1L));
    }

    @Test
    void planningArtifactsDefensivelyCopyCollections() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(context, 3L);
        PlanningCycleSnapshot cycle = PlanningTestFixtures.manager(context).executeCycle(20L);

        assertThrows(UnsupportedOperationException.class, () -> cycle.needs().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> cycle.needs().getFirst().metadata().put("test:new", "value"));
        assertThrows(UnsupportedOperationException.class,
                () -> cycle.opportunities().getFirst().bindings().clear());
    }

    @Test
    void invalidBudgetsAndIncompleteArtifactsFailAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new PlanningExecutionBudget(
                0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1L, 1L, 1
        ));
        assertThrows(IllegalArgumentException.class, () -> new OpportunityCapacity(
                1, 1, 1, 1, 1, 0
        ));
        assertThrows(IllegalArgumentException.class, () -> new CandidatePlanDefinition(
                CandidatePlanId.of("test:candidate"), PlanCategory.PRODUCTION,
                PlanningCycleId.forTick(1L), List.of(), OpportunityId.of("test:opportunity"),
                PlanningHorizon.IMMEDIATE, PlanningPriority.NORMAL, 1L, null,
                List.of(), null, List.of(), CandidateFeasibility.VALID,
                "deduplication", Map.of(), PlanningValidation.SCHEMA_VERSION
        ));
    }

    @Test
    void cyclicConstraintProvenanceIsRejected() {
        ConstraintId firstId = ConstraintId.of("test:first");
        ConstraintId secondId = ConstraintId.of("test:second");
        ConstraintDefinition first = constraint(firstId, List.of(secondId));
        ConstraintDefinition second = constraint(secondId, List.of(firstId));
        PlanningCycleId cycleId = PlanningCycleId.forTick(1L);
        PlanningCycleReport report = new PlanningCycleReport(
                cycleId, 1L, PlanningSelectionPolicy.DEFAULT_ID, PlanningCycleStatus.COMPLETED,
                0, 0, 2, 0, 0, 0, 0, 0, false, PlanningExecutionBudget.standard(),
                2L, 2L, List.of()
        );
        PlanningCycleSnapshot cycle = new PlanningCycleSnapshot(
                cycleId, 1L, PlanningSelectionPolicy.DEFAULT_ID, PlanningCycleStatus.COMPLETED,
                List.of(), List.of(), List.of(first, second), List.of(), List.of(), List.of(),
                List.of(), List.of(), report, 1L, PlanningValidation.SCHEMA_VERSION
        );

        assertThrows(IllegalArgumentException.class, () -> PlanningManager.validateGraph(cycle));
    }

    @Test
    void constraintGraphDepthIsBounded() {
        List<ConstraintDefinition> constraints = new java.util.ArrayList<>();
        ConstraintId parent = null;
        for (int index = 0; index <= PlanningExecutionBudget.standard().maximumRecursiveDepth(); index++) {
            ConstraintId id = ConstraintId.of("test:depth_" + index);
            constraints.add(constraint(id, parent == null ? List.of() : List.of(parent)));
            parent = id;
        }

        assertThrows(IllegalArgumentException.class,
                () -> PlanningManager.validateGraph(emptyCycle(constraints, List.of())));
    }

    @Test
    void candidateConstraintProvenanceMustResolve() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(context, 3L);
        PlanningCycleSnapshot source = PlanningTestFixtures.manager(context).executeCycle(20L);
        CandidatePlanDefinition candidate = source.candidates().getFirst();
        CandidatePlanDefinition invalid = new CandidatePlanDefinition(
                candidate.id(), candidate.category(), candidate.cycleId(), candidate.sourceNeedIds(),
                candidate.opportunityId(), candidate.horizon(), candidate.priority(),
                candidate.generatedSimulationTick(), candidate.action(), candidate.capacityClaims(),
                candidate.metrics(), List.of(ConstraintId.of("test:missing_constraint")),
                candidate.feasibility(), candidate.deduplicationKey(), candidate.metadata(),
                candidate.schemaVersion()
        );
        PlanningCycleSnapshot cycle = new PlanningCycleSnapshot(
                source.id(), source.simulationTick(), source.policyId(), source.status(),
                source.observations(), source.needs(), source.constraints(), source.opportunities(),
                List.of(invalid), source.approvedPlans(), source.needRuntimes(),
                source.submissionRuntimes(), source.report(), source.revision(), source.schemaVersion()
        );

        assertThrows(IllegalArgumentException.class, () -> PlanningManager.validateGraph(cycle));
    }

    private static PlanningCycleSnapshot emptyCycle(
            List<ConstraintDefinition> constraints,
            List<CandidatePlanDefinition> candidates
    ) {
        PlanningCycleId cycleId = PlanningCycleId.forTick(1L);
        PlanningCycleReport report = new PlanningCycleReport(
                cycleId, 1L, PlanningSelectionPolicy.DEFAULT_ID, PlanningCycleStatus.COMPLETED,
                0, 0, constraints.size(), 0, candidates.size(), 0, 0, 0, false,
                PlanningExecutionBudget.standard(), constraints.size(),
                constraints.size() + candidates.size(), List.of()
        );
        return new PlanningCycleSnapshot(
                cycleId, 1L, PlanningSelectionPolicy.DEFAULT_ID, PlanningCycleStatus.COMPLETED,
                List.of(), List.of(), constraints, List.of(), candidates, List.of(),
                List.of(), List.of(), report, 1L, PlanningValidation.SCHEMA_VERSION
        );
    }

    private static ConstraintDefinition constraint(ConstraintId id, List<ConstraintId> parents) {
        return new ConstraintDefinition(
                id, ConstraintType.CYCLE_BUDGET_EXHAUSTED,
                PlanningProviderId.of("test:provider"),
                new PlanningOrigin("test:planning", Optional.empty()),
                PlanningSeverity.BLOCKING, List.of(), parents, 1L, OptionalLong.empty(),
                ConstraintScope.CYCLE, Map.of(), PlanningValidation.SCHEMA_VERSION
        );
    }
}
