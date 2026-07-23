package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AllocationRuntimeStressTest {
    private static final int RUNTIME_COUNT = 100_000;
    private static final int HISTORY_COUNT = RUNTIME_COUNT * 2;

    @Test
    void runtimeRegistryAndHistoryRemainDeterministicAtScale() {
        StressDigest ascending = runWorkload(false);
        StressDigest descending = runWorkload(true);

        assertEquals(ascending, descending);
        assertEquals(RUNTIME_COUNT, ascending.runtimeCount());
        assertEquals(RUNTIME_COUNT, ascending.failedRuntimeCount());
        assertEquals(HISTORY_COUNT, ascending.historyCount());
        assertEquals(RUNTIME_COUNT, ascending.failedHistoryCount());
    }

    private static StressDigest runWorkload(boolean descending) {
        List<AllocationRuntimeView> views = new ArrayList<>(RUNTIME_COUNT);
        List<AllocationRuntimeTransitionRecord> records =
                new ArrayList<>(HISTORY_COUNT);
        for (int position = 0; position < RUNTIME_COUNT; position++) {
            int index = descending ? RUNTIME_COUNT - position - 1 : position;
            AllocationSetId setId = AllocationSetId.of("stress:set_" + index);
            long createdTick = index * 2L;
            long failedTick = createdTick + 1L;
            views.add(new AllocationRuntimeView(
                    setId,
                    AllocationRuntimeStatus.FAILED,
                    createdTick,
                    OptionalLong.empty(),
                    OptionalLong.empty(),
                    OptionalLong.empty(),
                    OptionalLong.empty(),
                    OptionalLong.empty(),
                    failedTick,
                    List.of(),
                    Optional.of(AllocationRuntimeFailureCode.SET_FAILED),
                    Optional.of("Stress fixture failure"),
                    AllocationMetadata.empty(),
                    1L,
                    AllocationSchema.CURRENT_VERSION
            ));
            records.add(new AllocationRuntimeTransitionRecord(
                    setId,
                    Optional.empty(),
                    AllocationRuntimeStatus.REQUESTED,
                    createdTick,
                    0L,
                    Optional.empty(),
                    Optional.empty(),
                    AllocationSchema.CURRENT_VERSION
            ));
            records.add(new AllocationRuntimeTransitionRecord(
                    setId,
                    Optional.of(AllocationRuntimeStatus.REQUESTED),
                    AllocationRuntimeStatus.FAILED,
                    failedTick,
                    1L,
                    Optional.of(AllocationRuntimeFailureCode.SET_FAILED),
                    Optional.of("Stress fixture failure"),
                    AllocationSchema.CURRENT_VERSION
            ));
        }

        AllocationRuntimeRegistry registry = AllocationRuntimeRegistry.of(views);
        AllocationHistory history = AllocationHistory.of(records);
        return new StressDigest(
                registry.size(),
                registry.findByStatus(AllocationRuntimeStatus.FAILED).size(),
                history.size(),
                history.findByStatus(AllocationRuntimeStatus.FAILED).size(),
                runtimeDigest(registry),
                historyDigest(history)
        );
    }

    private static long runtimeDigest(AllocationRuntimeRegistry registry) {
        long digest = 1L;
        for (AllocationRuntimeView view : registry.views()) {
            digest = 31L * digest + view.hashCode();
        }
        return digest;
    }

    private static long historyDigest(AllocationHistory history) {
        long digest = 1L;
        for (AllocationRuntimeTransitionRecord record : history.records()) {
            digest = 31L * digest + record.hashCode();
        }
        return digest;
    }

    private record StressDigest(
            int runtimeCount,
            int failedRuntimeCount,
            int historyCount,
            int failedHistoryCount,
            long runtimeDigest,
            long historyDigest
    ) {
    }
}
