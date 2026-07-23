package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationCycleStressTest {
    private static final int CAPACITY_COUNT = 100_000;
    private static final int COMMITMENT_COUNT = 100_000;
    private static final int CONTENTION_SET_COUNT = 5_000;

    @Test
    void oneHundredThousandObservedCapacitiesProduceStableLedgerDigests() {
        LedgerStressDigest first = capacityWorkload(false);
        LedgerStressDigest second = capacityWorkload(true);

        assertEquals(first, second);
        assertEquals(CAPACITY_COUNT, first.entryCount());
    }

    @Test
    void oneHundredThousandExistingCommitmentsSubtractExactlyAndReplay() {
        LedgerStressDigest first = commitmentWorkload(false);
        LedgerStressDigest second = commitmentWorkload(true);

        assertEquals(first, second);
        assertEquals(1, first.entryCount());
        assertEquals(COMMITMENT_COUNT, first.commitmentCount());
        assertEquals("0", first.remainingAmount());
    }

    @Test
    void highContentionMixedLedgerReplaysWithAtomicFailedBranches() {
        CycleStressDigest first = contentionWorkload(false);
        CycleStressDigest second = contentionWorkload(true);

        assertEquals(first, second);
        assertEquals(CONTENTION_SET_COUNT, first.evaluatedSetCount());
        assertEquals(1, first.successfulSetCount());
        assertEquals(CONTENTION_SET_COUNT - 1, first.waitingSetCount());
        assertEquals(2, first.commitmentCount());
        assertEquals(
                Integer.toString(CONTENTION_SET_COUNT - 1),
                first.sharedRemainingAmount()
        );
    }

    private static LedgerStressDigest capacityWorkload(boolean reverse) {
        List<ObservedResourceSnapshot> resources =
                new ArrayList<>(CAPACITY_COUNT);
        List<ObservedCapacitySnapshot> capacities =
                new ArrayList<>(CAPACITY_COUNT);
        for (int position = 0; position < CAPACITY_COUNT; position++) {
            int index = reverse ? CAPACITY_COUNT - position - 1 : position;
            var observation = AllocationCycleFixtures.observation(
                    "capacity_stress_" + index,
                    ResourceExclusivityMode.SHARED,
                    "1",
                    CapacityTypeId.of("test:capacity_stress"),
                    CapacityUnits.PRODUCTION_SLOT
            );
            resources.add(observation.resource());
            capacities.add(observation.capacity());
        }
        WorkingCapacityLedger ledger = WorkingCapacityLedger.fromObservations(
                resources,
                capacities,
                List.of()
        );
        return new LedgerStressDigest(
                ledger.entries().size(),
                0,
                Integer.toString(CAPACITY_COUNT),
                ledger.digest()
        );
    }

    private static LedgerStressDigest commitmentWorkload(boolean reverse) {
        var observation = AllocationCycleFixtures.observation(
                "commitment_stress",
                ResourceExclusivityMode.SHARED,
                Integer.toString(COMMITMENT_COUNT),
                CapacityTypeId.of("test:commitment_stress"),
                CapacityUnits.PRODUCTION_SLOT
        );
        List<AllocationCommitmentDefinition> commitments =
                new ArrayList<>(COMMITMENT_COUNT);
        for (int position = 0; position < COMMITMENT_COUNT; position++) {
            int index = reverse ? COMMITMENT_COUNT - position - 1 : position;
            AllocationSetId setId = AllocationSetId.of(
                    "stress:commitment_set_" + index
            );
            ExternalReference work = ExternalReference.of(
                    "butchercraft:execution_work",
                    "stress:commitment_work_" + index,
                    "butchercraft:production"
            );
            RequirementDefinition requirement = RequirementDefinition.create(
                    setId,
                    work,
                    ResourceCategories.PRODUCTION,
                    CapacityTypeId.of("test:commitment_stress"),
                    Optional.empty(),
                    AllocationQuantity.of(1L, CapacityUnits.PRODUCTION_SLOT),
                    100L,
                    AllocationMetadata.empty()
            );
            commitments.add(AllocationCommitmentDefinition.create(
                    AllocationCycleId.forTick(150L),
                    requirement,
                    observation.resource().resourceId(),
                    observation.capacity().capacityId(),
                    requirement.requiredQuantity(),
                    150L,
                    OptionalLong.of(1_000L),
                    List.of(
                            observation.resource().authoritativeExternalReference(),
                            observation.capacity().authoritativeExternalReference()
                    ),
                    AllocationMetadata.empty()
            ));
        }
        WorkingCapacityLedger ledger = WorkingCapacityLedger.fromObservations(
                List.of(observation.resource()),
                List.of(observation.capacity()),
                commitments
        );
        AllocationLedgerEntryView entry = ledger.entries().getFirst();
        return new LedgerStressDigest(
                1,
                entry.existingCommitmentIds().size(),
                entry.remainingQuantity().canonicalAmount(),
                ledger.digest()
        );
    }

    private static CycleStressDigest contentionWorkload(boolean reverse) {
        List<AllocationCycleFixtures.SetGraph> sets =
                new ArrayList<>(CONTENTION_SET_COUNT);
        for (int position = 0; position < CONTENTION_SET_COUNT; position++) {
            int index = reverse ? CONTENTION_SET_COUNT - position - 1 : position;
            sets.add(AllocationCycleFixtures.set(
                    "contention_" + index,
                    1,
                    AllocationCycleFixtures.demand(
                            "contention_shared",
                            "1",
                            CapacityUnits.PRODUCTION_SLOT,
                            Optional.empty()
                    ),
                    AllocationCycleFixtures.demand(
                            "contention_exclusive",
                            "1",
                            CapacityUnits.MACHINE_TIME,
                            Optional.empty()
                    )
            ));
        }
        var shared = AllocationCycleFixtures.observation(
                "contention_shared",
                ResourceExclusivityMode.SHARED,
                Integer.toString(CONTENTION_SET_COUNT),
                CapacityTypeId.of("test:contention_shared"),
                CapacityUnits.PRODUCTION_SLOT
        );
        var exclusive = AllocationCycleFixtures.observation(
                "contention_exclusive",
                ResourceExclusivityMode.EXCLUSIVE,
                "1",
                CapacityTypeId.of("test:contention_exclusive"),
                CapacityUnits.MACHINE_TIME
        );
        var scenario = AllocationCycleFixtures.scenario(
                AllocationCycleFixtures.TICK,
                sets,
                List.of(shared, exclusive)
        );
        AllocationCycleOperationResult<AllocationCycleResult> execution =
                new AllocationCycleExecutor().execute(
                        scenario.input(),
                        scenario.service()
                );
        assertTrue(execution.accepted(), () -> execution.failures().toString());
        AllocationCycleResult result = execution.value().orElseThrow();
        AllocationLedgerEntryView sharedEntry = result.finalLedger().stream()
                .filter(entry -> entry.capacityKey().capacityTypeId().equals(
                        CapacityTypeId.of("test:contention_shared")
                ))
                .findFirst().orElseThrow();
        return new CycleStressDigest(
                result.summary().evaluatedSetCount(),
                result.summary().successfulSetCount(),
                result.summary().waitingSetCount(),
                result.summary().createdCommitmentCount(),
                sharedEntry.remainingQuantity().canonicalAmount(),
                result.digests().resultDigest()
        );
    }

    private record LedgerStressDigest(
            int entryCount,
            int commitmentCount,
            String remainingAmount,
            String digest
    ) {
    }

    private record CycleStressDigest(
            int evaluatedSetCount,
            int successfulSetCount,
            int waitingSetCount,
            int commitmentCount,
            String sharedRemainingAmount,
            String resultDigest
    ) {
    }
}
