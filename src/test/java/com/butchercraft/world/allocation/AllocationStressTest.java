package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AllocationStressTest {
    private static final int IDENTITY_COUNT = 100_000;
    private static final int QUANTITY_COUNT = 100_000;
    private static final int REQUIREMENT_COUNT = 100_000;
    private static final int REQUEST_AND_SET_COUNT = 50_000;

    @Test
    void boundedDomainStressWorkloadIsDeterministicAndOrderIndependent() {
        StressDigest first = runWorkload();
        StressDigest second = runWorkload();

        assertEquals(first, second);
        assertEquals(IDENTITY_COUNT, first.identityCount());
        assertEquals(QUANTITY_COUNT, first.quantityCount());
        assertEquals(REQUIREMENT_COUNT, first.requirementCount());
        assertEquals(REQUEST_AND_SET_COUNT, first.requestCount());
        assertEquals(REQUEST_AND_SET_COUNT, first.setCount());
    }

    private static StressDigest runWorkload() {
        List<ResourceId> identities = new ArrayList<>(IDENTITY_COUNT);
        long quantityDigest = 1L;
        for (int index = 0; index < IDENTITY_COUNT; index++) {
            identities.add(ResourceId.of("stress:resource_" + index));
        }
        for (int index = 0; index < QUANTITY_COUNT; index++) {
            AllocationQuantity quantity = AllocationQuantity.of(
                    index,
                    CapacityUnits.PRODUCTION_SLOT
            );
            quantityDigest = mix(quantityDigest, quantity.hashCode());
        }

        List<RequirementId> requirementIds = new ArrayList<>(REQUIREMENT_COUNT);
        List<AllocationRequestDefinition> requests = new ArrayList<>(REQUEST_AND_SET_COUNT);
        List<AllocationSetDefinition> sets = new ArrayList<>(REQUEST_AND_SET_COUNT);
        for (int index = 0; index < REQUIREMENT_COUNT; index++) {
            String suffix = "stress_" + index;
            AllocationOrderingContext ordering = AllocationTestFixtures.ordering(
                    suffix,
                    index % 4,
                    index % 5,
                    index % 2 == 0
                            ? OptionalLong.of(1_000_000L + index)
                            : OptionalLong.empty(),
                    index,
                    index + 1L,
                    index
            );
            RequirementDefinition requirement = AllocationTestFixtures.requirement(
                    suffix,
                    ordering,
                    ResourceCategories.PRODUCTION,
                    CapacityTypeId.of("butchercraft:production_slot"),
                    Optional.empty(),
                    AllocationQuantity.of(index + 1L, CapacityUnits.PRODUCTION_SLOT)
            );
            requirementIds.add(requirement.id());
            if (index < REQUEST_AND_SET_COUNT) {
                AllocationRequestDefinition request = AllocationRequestDefinition.create(
                        requirement.allocationSetId(),
                        AllocationTestFixtures.work(suffix),
                        List.of(requirement),
                        ordering,
                        AllocationMetadata.empty()
                );
                requests.add(request);
                sets.add(AllocationSetDefinition.create(
                        requirement.allocationSetId(),
                        AllocationTestFixtures.work(suffix),
                        request,
                        List.of(requirement),
                        ordering.planningCycleReference(),
                        ordering.requestCreationSimulationTick(),
                        OptionalLong.empty(),
                        AllocationMetadata.empty(),
                        AllocationSchema.CURRENT_VERSION
                ));
            }
        }

        identities = new ArrayList<>(identities.reversed());
        identities.sort(Comparator.naturalOrder());
        requirementIds = new ArrayList<>(requirementIds.reversed());
        requirementIds.sort(Comparator.naturalOrder());
        requests = new ArrayList<>(requests.reversed());
        requests.sort(AllocationRequestDefinition.canonicalComparator(2_000_000L));
        sets = new ArrayList<>(sets.reversed());
        sets.sort(Comparator.naturalOrder());

        long identityDigest = digest(identities);
        long requirementDigest = digest(requirementIds);
        long requestDigest = digest(requests.stream()
                .map(AllocationRequestDefinition::id)
                .toList());
        long setDigest = digest(sets.stream().map(AllocationSetDefinition::id).toList());
        return new StressDigest(
                identities.size(),
                QUANTITY_COUNT,
                requirementIds.size(),
                requests.size(),
                sets.size(),
                identityDigest,
                quantityDigest,
                requirementDigest,
                requestDigest,
                setDigest
        );
    }

    private static long digest(List<?> values) {
        long digest = 1L;
        for (Object value : values) {
            digest = mix(digest, value.hashCode());
        }
        return digest;
    }

    private static long mix(long digest, int value) {
        return 31L * digest + value;
    }

    private record StressDigest(
            int identityCount,
            int quantityCount,
            int requirementCount,
            int requestCount,
            int setCount,
            long identityDigest,
            long quantityDigest,
            long requirementDigest,
            long requestDigest,
            long setDigest
    ) {
    }
}
