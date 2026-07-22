package com.butchercraft.world.business.runtime;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.CONFIGURATION;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.allDays;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.shift;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.state;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.tickAt;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessRuntimeStressTest {
    @Test
    void tenThousandBusinessesRemainDeterministicAcrossOneYear() {
        BusinessRuntimeRegistry registry = BusinessRuntimeRegistry.of(IntStream.range(0, 10_000)
                .mapToObj(index -> state(
                        "business_" + index,
                        allDays(0, 24),
                        List.of(shift("all_day", 0, 24, 1)),
                        1
                ))
                .toList());
        BusinessRuntimeManager first = new BusinessRuntimeManager(registry, CONFIGURATION);
        BusinessRuntimeManager second = new BusinessRuntimeManager(registry, CONFIGURATION);

        for (int day = 0; day < 365; day++) {
            long tick = tickAt(day, 0, 0);
            List<BusinessRuntimeState> firstChanges = first.evaluateAt(tick);
            List<BusinessRuntimeState> secondChanges = second.evaluateAt(tick);

            assertEquals(firstChanges, secondChanges);
            if (day == 0) {
                assertEquals(10_000, firstChanges.size());
                Set<String> changedIds = new HashSet<>(firstChanges.stream()
                        .map(state -> state.businessId().value())
                        .toList());
                assertEquals(10_000, changedIds.size());
            } else {
                assertEquals(0, firstChanges.size());
            }
            first.validateRuntimeState();
            second.validateRuntimeState();
        }

        assertEquals(first.registry().states(), second.registry().states());
        assertEquals(10_000, first.registry().findByStatus(BusinessOperationalStatus.OPERATING).size());
    }
}
