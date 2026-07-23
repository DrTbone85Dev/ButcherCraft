package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AllocationRuntimeViewValidationTest {
    @Test
    void runtimeViewCanonicalizesCommitmentsAndRetainsSchemaFields() {
        AllocationCommitmentId first = AllocationCommitmentId.of("example:a");
        AllocationCommitmentId second = AllocationCommitmentId.of("example:b");
        AllocationRuntimeView view = new AllocationRuntimeView(
                AllocationSetId.of("example:set"),
                AllocationRuntimeStatus.ALLOCATED,
                10L,
                OptionalLong.of(11L),
                OptionalLong.of(12L),
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.of(100L),
                12L,
                List.of(second, first),
                Optional.empty(),
                Optional.empty(),
                AllocationMetadata.empty(),
                2L,
                AllocationSchema.CURRENT_VERSION
        );

        assertEquals(List.of(first, second), view.commitmentIds());
        assertEquals(2L, view.revision());
        assertEquals(AllocationSchema.CURRENT_VERSION, view.schemaVersion());
    }

    @Test
    void runtimeViewRejectsMalformedStatusTimestampFailureAndSchemaShapes() {
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_STATUS_STATE,
                () -> view(
                        AllocationRuntimeStatus.ACTIVE,
                        OptionalLong.empty(),
                        OptionalLong.of(12L),
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        List.of(AllocationCommitmentId.of("example:commitment")),
                        Optional.empty(),
                        Optional.empty(),
                        1
                )
        );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.NULL_VALUE,
                () -> new AllocationRuntimeView(
                        AllocationSetId.of("example:set"),
                        AllocationRuntimeStatus.REQUESTED,
                        10L,
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        OptionalLong.of(100L),
                        10L,
                        List.of(),
                        null,
                        null,
                        AllocationMetadata.empty(),
                        0L,
                        AllocationSchema.CURRENT_VERSION
                )
        );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_TIMESTAMP,
                () -> view(
                        AllocationRuntimeStatus.WAITING,
                        OptionalLong.of(9L),
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        List.of(),
                        Optional.of(AllocationRuntimeFailureCode.CAPACITY_UNAVAILABLE),
                        Optional.of("Waiting"),
                        1
                )
        );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_STATUS_STATE,
                () -> view(
                        AllocationRuntimeStatus.REQUESTED,
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        List.of(),
                        Optional.of(AllocationRuntimeFailureCode.SET_FAILED),
                        Optional.empty(),
                        1
                )
        );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_SCHEMA_VERSION,
                () -> view(
                        AllocationRuntimeStatus.REQUESTED,
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        2
                )
        );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_STATUS_STATE,
                () -> view(
                        AllocationRuntimeStatus.REQUESTED,
                        OptionalLong.empty(),
                        OptionalLong.of(12L),
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        List.of(AllocationCommitmentId.of("example:commitment")),
                        Optional.empty(),
                        Optional.empty(),
                        1
                )
        );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_STATUS_STATE,
                () -> view(
                        AllocationRuntimeStatus.FAILED,
                        OptionalLong.empty(),
                        OptionalLong.of(12L),
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        List.of(),
                        Optional.of(AllocationRuntimeFailureCode.SET_FAILED),
                        Optional.of("Failed after allocation"),
                        1
                )
        );
    }

    @Test
    void transitionRequestRejectsRequestedDuplicateAndMalformedShapes() {
        AllocationSetId setId = AllocationSetId.of("example:set");
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_TRANSITION,
                () -> new AllocationRuntimeTransitionRequest(
                        setId,
                        AllocationRuntimeStatus.REQUESTED,
                        10L,
                        List.of(),
                        Optional.empty(),
                        Optional.empty()
                )
        );
        AllocationCommitmentId commitment = AllocationCommitmentId.of("example:commitment");
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.DUPLICATE_COMMITMENT,
                () -> AllocationRuntimeTransitionRequest.allocated(
                        setId,
                        10L,
                        List.of(commitment, commitment)
                )
        );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_STATUS_STATE,
                () -> new AllocationRuntimeTransitionRequest(
                        setId,
                        AllocationRuntimeStatus.ACTIVE,
                        10L,
                        List.of(),
                        Optional.of(AllocationRuntimeFailureCode.SET_FAILED),
                        Optional.of("Unexpected failure")
                )
        );
    }

    private static AllocationRuntimeView view(
            AllocationRuntimeStatus status,
            OptionalLong waiting,
            OptionalLong allocated,
            OptionalLong activated,
            OptionalLong released,
            List<AllocationCommitmentId> commitments,
            Optional<AllocationRuntimeFailureCode> failureCode,
            Optional<String> failureMessage,
            int schema
    ) {
        return new AllocationRuntimeView(
                AllocationSetId.of("example:set"),
                status,
                10L,
                waiting,
                allocated,
                activated,
                released,
                OptionalLong.of(100L),
                20L,
                commitments,
                failureCode,
                failureMessage,
                AllocationMetadata.empty(),
                0L,
                schema
        );
    }
}
