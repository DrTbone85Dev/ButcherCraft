package com.butchercraft.world.workforce.materialhandling;

import com.butchercraft.workstation.endpoint.WorkstationEndpointConfiguration;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.materialhandling.MaterialTransferId;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.materialhandling.persistence.EmployeeMaterialHandlingAssignmentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeMaterialHandlingAssignmentTest {
    private static final WorldIdentityRootIdentity WORLD = new WorldIdentityRootIdentity(
            "butchercraft:world_identity/test",
            1,
            "sha256:" + "4".repeat(64)
    );
    private static final EmployeeId EMPLOYEE = new EmployeeId("butchercraft:employee/test/1");
    private static final MaterialTransferId TRANSFER =
            new MaterialTransferId("butchercraft:material_transfer/v1/test1");

    @TempDir
    Path tempDir;

    @Test
    void assignmentIdentityIsDeterministicAndBindsTransfer() {
        EmployeeMaterialHandlingAssignment first = assignment(TRANSFER, source(1L), destination(2L));
        EmployeeMaterialHandlingAssignment second = assignment(TRANSFER, source(1L), destination(2L));
        EmployeeMaterialHandlingAssignment otherTransfer = assignment(
                new MaterialTransferId("butchercraft:material_transfer/v1/test2"),
                source(1L),
                destination(2L)
        );

        assertEquals(first.assignmentId(), second.assignmentId());
        assertEquals(first.contentDigest(), second.contentDigest());
        assertNotEquals(first.assignmentId(), otherTransfer.assignmentId());
    }

    @Test
    void endpointGenerationIsPartOfExplicitBinding() {
        EmployeeMaterialHandlingAssignment assignment = assignment(TRANSFER, source(1L), destination(2L));

        assertTrue(assignment.binds(source(1L), destination(2L)));
        assertFalse(assignment.binds(source(3L), destination(2L)));
        assertFalse(assignment.binds(source(1L), destination(4L)));
    }

    @Test
    void duplicateCreationObservesOneAssignment() {
        EmployeeMaterialHandlingAssignmentManager manager = EmployeeMaterialHandlingAssignmentManager.empty();

        EmployeeMaterialHandlingAssignmentManager.CreateResult first = manager.createOrObserve(
                WORLD, EMPLOYEE, TRANSFER, source(1L), destination(2L), 10L
        );
        EmployeeMaterialHandlingAssignmentManager.CreateResult duplicate = manager.createOrObserve(
                WORLD, EMPLOYEE, TRANSFER, source(1L), destination(2L), 10L
        );

        assertEquals(EmployeeMaterialHandlingAssignmentManager.CreateStatus.CREATED, first.status());
        assertEquals(EmployeeMaterialHandlingAssignmentManager.CreateStatus.OBSERVED, duplicate.status());
        assertEquals(first.assignment(), duplicate.assignment());
        assertEquals(1, manager.assignments().size());
    }

    @Test
    void conflictingActiveAssignmentIsRejected() {
        EmployeeMaterialHandlingAssignmentManager manager = EmployeeMaterialHandlingAssignmentManager.empty();
        manager.createOrObserve(WORLD, EMPLOYEE, TRANSFER, source(1L), destination(2L), 10L);

        EmployeeMaterialHandlingAssignmentManager.CreateResult conflict = manager.createOrObserve(
                WORLD,
                EMPLOYEE,
                new MaterialTransferId("butchercraft:material_transfer/v1/conflict"),
                source(1L),
                destination(2L),
                11L
        );

        assertEquals(EmployeeMaterialHandlingAssignmentManager.CreateStatus.CONFLICT, conflict.status());
        assertEquals(1, manager.assignments().size());
    }

    @Test
    void lifecycleKeepsWorkforceStateSeparateFromTransferState() {
        EmployeeMaterialHandlingAssignmentManager manager = EmployeeMaterialHandlingAssignmentManager.empty();
        EmployeeMaterialHandlingAssignment value = manager.createOrObserve(
                WORLD, EMPLOYEE, TRANSFER, source(1L), destination(2L), 10L
        ).assignment();

        value = manager.transition(value.assignmentId(), EmployeeMaterialHandlingAssignmentState.WALKING_TO_SOURCE,
                Optional.empty());
        value = manager.transition(value.assignmentId(), EmployeeMaterialHandlingAssignmentState.WITHDRAWAL_REQUESTED,
                Optional.empty());
        value = manager.transition(value.assignmentId(), EmployeeMaterialHandlingAssignmentState.CARRYING_TO_DESTINATION,
                Optional.empty());
        value = manager.transition(value.assignmentId(), EmployeeMaterialHandlingAssignmentState.DEPOSIT_REQUESTED,
                Optional.empty());
        value = manager.transition(value.assignmentId(), EmployeeMaterialHandlingAssignmentState.COMPLETED,
                Optional.empty());

        assertEquals(EmployeeMaterialHandlingAssignmentState.COMPLETED, value.state());
        assertFalse(value.active());
        assertEquals(6L, value.revision());
    }

    @Test
    void illegalLifecycleShortcutFailsVisibly() {
        EmployeeMaterialHandlingAssignment value = assignment(TRANSFER, source(1L), destination(2L));

        assertThrows(IllegalArgumentException.class, () -> value.transition(
                EmployeeMaterialHandlingAssignmentState.COMPLETED,
                2L,
                Optional.empty()
        ));
    }

    @Test
    void persistenceRoundTripRetainsOnlyWorkforceOwnedState() {
        EmployeeMaterialHandlingAssignmentManager manager = EmployeeMaterialHandlingAssignmentManager.empty();
        EmployeeMaterialHandlingAssignment value = manager.createOrObserve(
                WORLD, EMPLOYEE, TRANSFER, source(1L), destination(2L), 10L
        ).assignment();
        manager.transition(value.assignmentId(), EmployeeMaterialHandlingAssignmentState.WALKING_TO_SOURCE,
                Optional.empty());
        EmployeeMaterialHandlingAssignmentStorage storage = storage();

        storage.save(manager.directory());
        EmployeeMaterialHandlingAssignmentDirectory restored = storage.load();
        String serialized = storage.serialize(restored);

        assertEquals(manager.directory(), restored);
        assertFalse(serialized.contains("item_stack"));
        assertFalse(serialized.contains("encoded_stack"));
        assertFalse(serialized.contains("path_nodes"));
        assertTrue(serialized.contains("transfer_identity"));
        assertTrue(serialized.contains("instance_identity"));
    }

    @Test
    void unsupportedPersistenceSchemaFailsVisibly() {
        EmployeeMaterialHandlingAssignmentStorage storage = storage();
        String json = storage.serialize(EmployeeMaterialHandlingAssignmentDirectory.empty())
                .replaceFirst("\\\"schema_version\\\": 1", "\\\"schema_version\\\": 2");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> storage.deserialize(json));

        assertTrue(failure.getMessage().contains("Unsupported"));
    }

    @Test
    void interruptedPublicationWithoutAuthoritativeFileRequiresRecovery() throws Exception {
        EmployeeMaterialHandlingAssignmentStorage storage = storage();
        Files.writeString(storage.filePath().resolveSibling(storage.filePath().getFileName() + ".tmp"), "partial");

        assertThrows(IllegalStateException.class, storage::load);
    }

    @Test
    void directoryRejectsTwoActiveAssignmentsForOneEmployee() {
        EmployeeMaterialHandlingAssignment first = assignment(TRANSFER, source(1L), destination(2L));
        EmployeeMaterialHandlingAssignment second = assignment(
                new MaterialTransferId("butchercraft:material_transfer/v1/test2"), source(1L), destination(2L)
        );

        assertThrows(IllegalArgumentException.class, () -> new EmployeeMaterialHandlingAssignmentDirectory(
                EmployeeMaterialHandlingAssignmentSchema.CURRENT_VERSION,
                2L,
                List.of(first, second)
        ));
    }

    @Test
    void crossDimensionEndpointsAreRejected() {
        WorkstationEndpointReference otherDimension = reference(
                "butchercraft:grinder", "minecraft:the_nether", 3, 2L
        );

        assertThrows(IllegalArgumentException.class,
                () -> assignment(TRANSFER, source(1L), otherDimension));
    }

    @Test
    void failureCodesAreStableAndComplete() {
        List<String> codes = List.of(EmployeeMaterialHandlingFailureCode.values()).stream()
                .map(EmployeeMaterialHandlingFailureCode::serializedName)
                .toList();

        assertTrue(codes.containsAll(List.of(
                "employee_not_found", "employee_unavailable", "employee_off_shift", "plant_closed",
                "assignment_conflict", "source_endpoint_replaced", "source_reservation_failed",
                "source_unreachable", "withdrawal_rejected", "custody_not_proven",
                "destination_endpoint_replaced", "destination_reservation_failed", "destination_unreachable",
                "deposit_rejected", "reservation_lost", "transfer_failed", "cancellation_failed",
                "recovery_required", "unknown_outcome"
        )));
    }

    private EmployeeMaterialHandlingAssignmentStorage storage() {
        return new EmployeeMaterialHandlingAssignmentStorage(
                tempDir.resolve(EmployeeMaterialHandlingAssignmentSchema.FILE_NAME)
        );
    }

    private static EmployeeMaterialHandlingAssignment assignment(
            MaterialTransferId transfer,
            WorkstationEndpointReference source,
            WorkstationEndpointReference destination
    ) {
        return EmployeeMaterialHandlingAssignment.create(
                WORLD,
                EMPLOYEE,
                transfer,
                source,
                destination,
                1L,
                10L
        );
    }

    private static WorkstationEndpointReference source(long generation) {
        return reference("butchercraft:cutting_table", "minecraft:overworld", 1, generation);
    }

    private static WorkstationEndpointReference destination(long generation) {
        return reference("butchercraft:grinder", "minecraft:overworld", 3, generation);
    }

    private static WorkstationEndpointReference reference(
            String type,
            String dimension,
            int x,
            long generation
    ) {
        WorkstationEndpointKey key = new WorkstationEndpointKey(type, dimension, x, 64, 0);
        WorkstationInstanceId id = WorkstationInstanceId.create(
                WORLD,
                key,
                generation,
                WorkstationEndpointConfiguration.standard().instanceAllocationConfigurationIdentity()
        );
        return new WorkstationEndpointReference(id, key, generation);
    }
}
