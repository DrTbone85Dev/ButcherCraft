package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AllocationHistoryTest {
    @Test
    void historyCanonicalizesInputAndSupportsReadOnlyQueries() {
        AllocationSetId setId = AllocationSetId.of("example:set");
        AllocationRuntimeTransitionRecord requested = record(
                setId,
                Optional.empty(),
                AllocationRuntimeStatus.REQUESTED,
                10L,
                0L
        );
        AllocationRuntimeTransitionRecord failed = new AllocationRuntimeTransitionRecord(
                setId,
                Optional.of(AllocationRuntimeStatus.REQUESTED),
                AllocationRuntimeStatus.FAILED,
                20L,
                1L,
                Optional.of(AllocationRuntimeFailureCode.SET_FAILED),
                Optional.of("Failed"),
                AllocationSchema.CURRENT_VERSION
        );
        AllocationHistory history = AllocationHistory.of(List.of(failed, requested));

        assertEquals(List.of(requested, failed), history.records());
        assertEquals(List.of(requested, failed), history.findBySet(setId));
        assertEquals(List.of(failed), history.findByStatus(AllocationRuntimeStatus.FAILED));
        assertEquals(Optional.of(failed), history.latest(setId));
        assertEquals(List.of(failed), history.findBetween(15L, 25L));
        assertThrows(
                UnsupportedOperationException.class,
                () -> history.records().add(requested)
        );
    }

    @Test
    void historyRejectsGapsBrokenChainsAndTerminalTransitions() {
        AllocationSetId setId = AllocationSetId.of("example:set");
        AllocationRuntimeTransitionRecord requested = record(
                setId,
                Optional.empty(),
                AllocationRuntimeStatus.REQUESTED,
                10L,
                0L
        );
        AllocationRuntimeTransitionRecord allocatedAtRevisionTwo =
                new AllocationRuntimeTransitionRecord(
                        setId,
                        Optional.of(AllocationRuntimeStatus.REQUESTED),
                        AllocationRuntimeStatus.ALLOCATED,
                        20L,
                        2L,
                        Optional.empty(),
                        Optional.empty(),
                        AllocationSchema.CURRENT_VERSION
                );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_HISTORY,
                () -> AllocationHistory.of(List.of(requested, allocatedAtRevisionTwo))
        );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_HISTORY,
                () -> new AllocationRuntimeTransitionRecord(
                        setId,
                        Optional.of(AllocationRuntimeStatus.RELEASED),
                        AllocationRuntimeStatus.FAILED,
                        30L,
                        2L,
                        Optional.of(AllocationRuntimeFailureCode.SET_FAILED),
                        Optional.of("Cannot leave terminal state"),
                        AllocationSchema.CURRENT_VERSION
                )
        );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_HISTORY,
                () -> new AllocationRuntimeTransitionRecord(
                        setId,
                        Optional.of(AllocationRuntimeStatus.REQUESTED),
                        AllocationRuntimeStatus.WAITING,
                        20L,
                        1L,
                        Optional.empty(),
                        Optional.empty(),
                        AllocationSchema.CURRENT_VERSION
                )
        );
    }

    private static AllocationRuntimeTransitionRecord record(
            AllocationSetId setId,
            Optional<AllocationRuntimeStatus> previous,
            AllocationRuntimeStatus status,
            long tick,
            long revision
    ) {
        return new AllocationRuntimeTransitionRecord(
                setId,
                previous,
                status,
                tick,
                revision,
                Optional.empty(),
                Optional.empty(),
                AllocationSchema.CURRENT_VERSION
        );
    }
}
