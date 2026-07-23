package com.butchercraft.world.planning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanningStressTest {
    private static final int OBSERVATIONS = 1_000_000;
    private static final int NEEDS = 500_000;
    private static final int OPPORTUNITIES = 500_000;
    private static final int CANDIDATES = 1_000_000;
    private static final int APPROVED = 100_000;

    @Test
    void constructsOneMillionImmutableObservations() {
        ObservationDefinition template = templates().observations().getFirst();
        long start = System.nanoTime();
        long count = 0;
        for (int index = 0; index < OBSERVATIONS; index++) {
            ObservationDefinition value = new ObservationDefinition(
                    ObservationId.of("test:observation_" + index), template.providerId(), template.type(),
                    template.observedSimulationTick(), template.origin(), template.references(),
                    template.payload(), template.schemaVersion()
            );
            count += value.id().value().startsWith("test:") ? 1 : 0;
        }
        report("observations", OBSERVATIONS, start);
        assertEquals(OBSERVATIONS, count);
    }

    @Test
    void constructsFiveHundredThousandImmutableNeeds() {
        NeedDefinition template = templates().needs().getFirst();
        long start = System.nanoTime();
        long count = 0;
        for (int index = 0; index < NEEDS; index++) {
            NeedDefinition value = new NeedDefinition(
                    NeedId.of("test:need_" + index), template.type(), template.detectingProviderId(),
                    template.origin(), template.sourceReferences(), template.horizon(),
                    template.basePriority(), template.createdSimulationTick(),
                    template.requiredBySimulationTick(), template.expirationSimulationTick(),
                    template.goodId(), template.requestedQuantity(), template.unit(),
                    template.aggregationKey(), template.splitPolicy(), template.metadata(),
                    template.schemaVersion()
            );
            count += value.id().value().startsWith("test:") ? 1 : 0;
        }
        report("needs", NEEDS, start);
        assertEquals(NEEDS, count);
    }

    @Test
    void constructsFiveHundredThousandImmutableOpportunities() {
        OpportunityDefinition template = templates().opportunities().getFirst();
        long start = System.nanoTime();
        long count = 0;
        for (int index = 0; index < OPPORTUNITIES; index++) {
            OpportunityDefinition value = new OpportunityDefinition(
                    OpportunityId.of("test:opportunity_" + index), template.type(),
                    template.discoveringProviderId(), template.origin(), template.actorId(),
                    template.businessId(), template.industryId(), template.supportedHorizon(),
                    template.processId(), template.outputGoodId(), template.outputUnit(),
                    template.outputPerBatch(), template.processParameters(), template.bindings(),
                    template.capacity(), template.available(), template.blockingReasons(),
                    template.earliestStartTick(),
                    template.estimatedCompletionTick(), template.existingCommitmentLoad(),
                    template.supportingReferences(), template.observedSimulationTick(),
                    template.metadata(), template.schemaVersion()
            );
            count += value.id().value().startsWith("test:") ? 1 : 0;
        }
        report("opportunities", OPPORTUNITIES, start);
        assertEquals(OPPORTUNITIES, count);
    }

    @Test
    void constructsOneMillionImmutableCandidates() {
        CandidatePlanDefinition template = templates().candidates().getFirst();
        long start = System.nanoTime();
        long count = 0;
        for (int index = 0; index < CANDIDATES; index++) {
            CandidatePlanDefinition value = new CandidatePlanDefinition(
                    CandidatePlanId.of("test:candidate_" + index), template.category(),
                    template.cycleId(), template.sourceNeedIds(), template.opportunityId(),
                    template.horizon(), template.priority(), template.generatedSimulationTick(),
                    template.action(), template.capacityClaims(), template.metrics(),
                    template.constraints(), template.feasibility(), "candidate_" + index,
                    template.metadata(), template.schemaVersion()
            );
            count += value.id().value().startsWith("test:") ? 1 : 0;
        }
        report("candidates", CANDIDATES, start);
        assertEquals(CANDIDATES, count);
    }

    @Test
    void constructsOneHundredThousandImmutableApprovedPlans() {
        ApprovedPlanDefinition template = templates().approvedPlans().getFirst();
        long start = System.nanoTime();
        long count = 0;
        for (int index = 0; index < APPROVED; index++) {
            ApprovedPlanDefinition value = new ApprovedPlanDefinition(
                    ApprovedPlanId.of("test:approved_" + index), template.cycleId(),
                    template.candidatePlanId(), template.category(), template.selectionPolicyId(),
                    template.approvedSimulationTick(), template.needAllocations(),
                    template.acceptedCapacityClaims(), template.approvedAction(),
                    template.disposition(), template.initialSubmissionState(),
                    template.metadata(), template.schemaVersion()
            );
            count += value.id().value().startsWith("test:") ? 1 : 0;
        }
        report("approved_plans", APPROVED, start);
        assertEquals(APPROVED, count);
    }

    private static PlanningCycleSnapshot templates() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(context, 3L);
        return PlanningTestFixtures.manager(context).executeCycle(20L);
    }

    private static void report(String type, int count, long start) {
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
        System.out.printf("Planning stress: %,d %s in %,d ms%n", count, type, elapsedMillis);
    }
}
