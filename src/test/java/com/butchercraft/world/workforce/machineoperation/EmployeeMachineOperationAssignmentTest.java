package com.butchercraft.world.workforce.machineoperation;

import com.butchercraft.workstation.endpoint.WorkstationEndpointConfiguration;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.workstation.reservation.WorkstationReservationId;
import com.butchercraft.world.checkpoint.CheckpointOwnerFileBundleCodec;
import com.butchercraft.world.checkpoint.CheckpointOwnerFileSnapshot;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.machineoperation.persistence.EmployeeMachineOperationAssignmentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeMachineOperationAssignmentTest {
    private static final WorldIdentityRootIdentity WORLD = new WorldIdentityRootIdentity(
            "butchercraft:world_identity/test", 1, "sha256:" + "6".repeat(64));
    private static final EmployeeId EMPLOYEE = new EmployeeId("butchercraft:employee/test/1");
    private static final String POLICY = "butchercraft:machine_operating_policy/powered_continuous_explicit_stop/v1";
    private static final String OPERATION = "butchercraft:grind_beef";
    private static final String INPUT = "butchercraft:beef_trim";

    @TempDir
    Path tempDir;

    @Test
    void identityIsDeterministicAndBindsExactWorkstationGeneration() {
        EmployeeMachineOperationAssignment first = assignment(1L, grinder(3L), 10);
        EmployeeMachineOperationAssignment duplicate = assignment(1L, grinder(3L), 10);
        EmployeeMachineOperationAssignment replacement = assignment(1L, grinder(4L), 10);

        assertEquals(first.assignmentId(), duplicate.assignmentId());
        assertEquals(first.contentDigest(), duplicate.contentDigest());
        assertNotEquals(first.assignmentId(), replacement.assignmentId());
    }

    @Test
    void managerCreatesObservesAndRejectsConflictingActiveIntent() {
        EmployeeMachineOperationAssignmentManager manager = EmployeeMachineOperationAssignmentManager.empty();

        var created = manager.createOrObserve(
                WORLD, EMPLOYEE, grinder(3L), "grinder", POLICY, OPERATION, INPUT, 1, 10, 20L);
        var observed = manager.createOrObserve(
                WORLD, EMPLOYEE, grinder(3L), "grinder", POLICY, OPERATION, INPUT, 1, 10, 21L);
        var conflict = manager.createOrObserve(
                WORLD, EMPLOYEE, grinder(3L), "grinder", POLICY, OPERATION, INPUT, 1, 11, 22L);

        assertEquals(EmployeeMachineOperationAssignmentManager.CreateStatus.CREATED, created.status());
        assertEquals(EmployeeMachineOperationAssignmentManager.CreateStatus.OBSERVED, observed.status());
        assertEquals(EmployeeMachineOperationAssignmentManager.CreateStatus.CONFLICT, conflict.status());
        assertEquals(created.assignment(), observed.assignment());
        assertEquals(1, manager.assignments().size());
    }

    @Test
    void finiteLifecycleRequiresExactReservationAndRunReferences() {
        EmployeeMachineOperationAssignmentManager manager = EmployeeMachineOperationAssignmentManager.empty();
        EmployeeMachineOperationAssignment value = manager.createOrObserve(
                WORLD, EMPLOYEE, grinder(3L), "grinder", POLICY, OPERATION, INPUT, 1, 2, 20L).assignment();
        EmployeeMachineOperationAssignmentId assignmentId = value.assignmentId();
        WorkstationReservationId reservation = new WorkstationReservationId(
                "butchercraft:workstation_reservation/v2/operator");

        assertThrows(IllegalArgumentException.class, () -> manager.publish(
                assignmentId, EmployeeMachineOperationAssignmentState.NAVIGATING, 0,
                Optional.empty(), Optional.empty(), Optional.empty(), 0L, 0L, Optional.empty(), 21L));

        value = manager.publish(value.assignmentId(), EmployeeMachineOperationAssignmentState.NAVIGATING, 0,
                Optional.empty(), Optional.of(reservation), Optional.empty(), 0L, 0L, Optional.empty(), 21L);
        value = manager.publish(value.assignmentId(), EmployeeMachineOperationAssignmentState.READY_TO_START, 0,
                Optional.empty(), Optional.of(reservation), Optional.empty(), 0L, 0L, Optional.empty(), 22L);

        EmployeeMachineOperationAssignment ready = value;
        assertThrows(IllegalArgumentException.class, () -> manager.publish(
                ready.assignmentId(), EmployeeMachineOperationAssignmentState.START_REQUESTED, 0,
                Optional.empty(), Optional.of(reservation), Optional.empty(), 0L, 0L, Optional.empty(), 23L));

        value = manager.publish(value.assignmentId(), EmployeeMachineOperationAssignmentState.START_REQUESTED, 0,
                Optional.of("butchercraft:machine_run/v1/run"), Optional.of(reservation), Optional.empty(),
                1L, 1L, Optional.empty(), 23L);
        value = manager.publish(value.assignmentId(), EmployeeMachineOperationAssignmentState.RUNNING, 1,
                value.runIdentity(), Optional.of(reservation), Optional.empty(), 2L, 1L, Optional.empty(), 24L);

        assertEquals(1, value.completedQuantity());
        assertEquals(1, value.remainingQuantity());
        assertEquals(EmployeeMachineOperationAssignmentState.RUNNING, value.state());

        value = manager.publish(value.assignmentId(), EmployeeMachineOperationAssignmentState.WAITING_FOR_SAFE_STOP,
                2, value.runIdentity(), Optional.of(reservation), Optional.empty(),
                3L, 2L, Optional.empty(), 25L);
        assertEquals(EmployeeMachineOperationAssignmentState.WAITING_FOR_SAFE_STOP, value.state());
    }

    @Test
    void completedQuantityAndObservationFreshnessCannotRegressOrExceedTarget() {
        EmployeeMachineOperationAssignmentManager manager = runningManager(2, 1);
        EmployeeMachineOperationAssignment value = manager.assignments().getFirst();

        assertThrows(IllegalArgumentException.class, () -> manager.publish(
                value.assignmentId(), EmployeeMachineOperationAssignmentState.RUNNING, 0,
                value.runIdentity(), value.reservationId(), Optional.empty(), 3L, 2L, Optional.empty(), 30L));
        assertThrows(IllegalArgumentException.class, () -> manager.publish(
                value.assignmentId(), EmployeeMachineOperationAssignmentState.RUNNING, 3,
                value.runIdentity(), value.reservationId(), Optional.empty(), 3L, 2L, Optional.empty(), 30L));
        assertThrows(IllegalArgumentException.class, () -> manager.publish(
                value.assignmentId(), EmployeeMachineOperationAssignmentState.RUNNING, 1,
                value.runIdentity(), value.reservationId(), Optional.empty(), 1L, 0L, Optional.empty(), 30L));
    }

    @Test
    void cancellationBeforeStartIsTerminalAndCreatesNoRunReference() {
        EmployeeMachineOperationAssignmentManager manager = EmployeeMachineOperationAssignmentManager.empty();
        EmployeeMachineOperationAssignment value = manager.createOrObserve(
                WORLD, EMPLOYEE, grinder(3L), "grinder", POLICY, OPERATION, INPUT, 1, 10, 20L).assignment();

        value = manager.publish(value.assignmentId(), EmployeeMachineOperationAssignmentState.CANCELLATION_REQUESTED,
                0, Optional.empty(), Optional.empty(), Optional.empty(), 0L, 0L, Optional.empty(), 21L);
        value = manager.publish(value.assignmentId(), EmployeeMachineOperationAssignmentState.CANCELLED,
                0, Optional.empty(), Optional.empty(), Optional.empty(), 0L, 0L, Optional.empty(), 22L);

        assertFalse(value.active());
        assertTrue(value.runIdentity().isEmpty());
        EmployeeMachineOperationAssignment terminal = value;
        assertThrows(IllegalArgumentException.class, () -> manager.publish(
                terminal.assignmentId(), EmployeeMachineOperationAssignmentState.NAVIGATING, 0,
                Optional.empty(), Optional.empty(), Optional.empty(), 0L, 0L, Optional.empty(), 23L));
    }

    @Test
    void consequenceFreeReplacementCancellationRequiresAbsenceOfConsequentialEvidence() {
        EmployeeMachineOperationAssignment candidate = recoveryRequired(
                assignment(1L, grinder(3L), 10),
                EmployeeMachineOperationFailureCode.WORKSTATION_REPLACED,
                Optional.empty(), 0, 0L, 0L);

        assertTrue(candidate.isConsequenceFreeReplacementCancellationCandidate());
        assertFalse(recoveryRequired(
                assignment(2L, grinder(3L), 10),
                EmployeeMachineOperationFailureCode.RECOVERY_REQUIRED,
                Optional.empty(), 0, 0L, 0L).isConsequenceFreeReplacementCancellationCandidate());
        assertFalse(recoveryRequired(
                assignment(3L, grinder(3L), 10),
                EmployeeMachineOperationFailureCode.WORKSTATION_REPLACED,
                Optional.of("butchercraft:machine_run/v1/unresolved_start"), 0, 1L, 0L)
                .isConsequenceFreeReplacementCancellationCandidate());
        assertFalse(recoveryRequired(
                assignment(4L, grinder(3L), 10),
                EmployeeMachineOperationFailureCode.WORKSTATION_REPLACED,
                Optional.empty(), 0, 1L, 0L).isConsequenceFreeReplacementCancellationCandidate());
        assertFalse(recoveryRequired(
                assignment(5L, grinder(3L), 10),
                EmployeeMachineOperationFailureCode.WORKSTATION_REPLACED,
                Optional.empty(), 0, 0L, 1L).isConsequenceFreeReplacementCancellationCandidate());
        assertFalse(recoveryRequired(
                assignment(6L, grinder(3L), 10),
                EmployeeMachineOperationFailureCode.WORKSTATION_REPLACED,
                Optional.empty(), 1, 0L, 0L).isConsequenceFreeReplacementCancellationCandidate());

        EmployeeMachineOperationAssignment pendingSupply = recoveryRequired(
                assignment(7L, grinder(3L), 10),
                EmployeeMachineOperationFailureCode.WORKSTATION_REPLACED,
                Optional.empty(), 0, 0L, 0L);
        pendingSupply = pendingSupply.evolve(
                EmployeeMachineOperationAssignmentState.RECOVERY_REQUIRED,
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.of("butchercraft:material_transfer/v1/unresolved_supply"),
                0L,
                0L,
                pendingSupply.failure(),
                pendingSupply.revision() + 1L,
                pendingSupply.lastUpdatedTick() + 1L);
        assertFalse(pendingSupply.isConsequenceFreeReplacementCancellationCandidate());
    }

    @Test
    void terminalizedReplacementPersistsThroughSaveAndCheckpointAndAllowsNewAssignment() {
        EmployeeMachineOperationAssignmentManager manager = EmployeeMachineOperationAssignmentManager.empty();
        EmployeeMachineOperationAssignment created = manager.createOrObserve(
                WORLD, EMPLOYEE, grinder(3L), "grinder", POLICY, OPERATION, INPUT, 1, 10, 20L).assignment();
        EmployeeMachineOperationFailure replacement = new EmployeeMachineOperationFailure(
                EmployeeMachineOperationFailureCode.WORKSTATION_REPLACED,
                "exact Workstation Instance was replaced");
        EmployeeMachineOperationAssignment recovery = manager.publish(
                created.assignmentId(), EmployeeMachineOperationAssignmentState.RECOVERY_REQUIRED, 0,
                Optional.empty(), Optional.empty(), Optional.empty(), 0L, 0L,
                Optional.of(replacement), 21L);
        EmployeeMachineOperationAssignment cancelled = manager.publish(
                recovery.assignmentId(), EmployeeMachineOperationAssignmentState.CANCELLED, 0,
                Optional.empty(), Optional.empty(), Optional.empty(), 0L, 0L,
                Optional.of(replacement), 22L);

        assertEquals(cancelled, manager.publish(
                cancelled.assignmentId(), EmployeeMachineOperationAssignmentState.CANCELLED, 0,
                Optional.empty(), Optional.empty(), Optional.empty(), 0L, 0L,
                Optional.of(replacement), 23L));
        assertFalse(cancelled.active());
        assertEquals(Optional.of(replacement), cancelled.failure());

        EmployeeMachineOperationAssignmentStorage storage = storage();
        storage.save(manager.directory());
        EmployeeMachineOperationAssignmentDirectory restored = storage.load();
        assertEquals(EmployeeMachineOperationAssignmentState.CANCELLED,
                restored.assignments().getFirst().state());
        assertEquals(Optional.of(replacement), restored.assignments().getFirst().failure());

        byte[] nativeFile = storage.serialize(restored).getBytes(StandardCharsets.UTF_8);
        CheckpointOwnerFileSnapshot checkpoint = new CheckpointOwnerFileSnapshot(
                LegacySplitRecoveryParticipants.WORKFORCE, 3, restored.ownerRevision(), false,
                List.of(new CheckpointOwnerFileSnapshot.FilePayload(
                        EmployeeMachineOperationAssignmentSchema.FILE_NAME, nativeFile)));
        CheckpointOwnerFileSnapshot decoded = CheckpointOwnerFileBundleCodec.decode(
                CheckpointOwnerFileBundleCodec.encode(checkpoint));
        EmployeeMachineOperationAssignmentDirectory checkpointRestored = storage.deserialize(
                new String(decoded.files().getFirst().bytes(), StandardCharsets.UTF_8));
        assertEquals(EmployeeMachineOperationAssignmentState.CANCELLED,
                checkpointRestored.assignments().getFirst().state());
        assertEquals(Optional.of(replacement), checkpointRestored.assignments().getFirst().failure());

        EmployeeMachineOperationAssignmentManager restoredManager =
                new EmployeeMachineOperationAssignmentManager(checkpointRestored);
        var next = restoredManager.createOrObserve(
                WORLD, EMPLOYEE, grinder(4L), "grinder", POLICY, OPERATION, INPUT, 1, 5, 30L);
        assertEquals(EmployeeMachineOperationAssignmentManager.CreateStatus.CREATED, next.status());
        assertNotEquals(cancelled.assignmentId(), next.assignment().assignmentId());
    }

    @Test
    void persistenceRoundTripRetainsReferencesWithoutDuplicatingOwnerState() {
        EmployeeMachineOperationAssignmentManager manager = runningManager(10, 4);
        EmployeeMachineOperationAssignment value = manager.assignments().getFirst();
        manager.publish(value.assignmentId(), value.state(), value.completedQuantity(), value.runIdentity(),
                value.reservationId(), Optional.of("butchercraft:material_transfer/v1/inbound"),
                value.observedRunRevision() + 1L, value.observedChildSequence(), Optional.empty(), 30L);
        EmployeeMachineOperationAssignmentStorage storage = storage();

        storage.save(manager.directory());
        EmployeeMachineOperationAssignmentDirectory restored = storage.load();
        String serialized = storage.serialize(restored);

        assertEquals(manager.directory(), restored);
        assertTrue(serialized.contains("pending_supply_transfer_identity"));
        assertTrue(serialized.contains("run_identity"));
        assertTrue(serialized.contains("reservation_identity"));
        assertFalse(serialized.contains("item_stack"));
        assertFalse(serialized.contains("terminal_children"));
        assertFalse(serialized.contains("machine_inventory"));
    }

    @Test
    void unsupportedSchemaAndInterruptedPublicationFailVisibly() throws Exception {
        EmployeeMachineOperationAssignmentStorage storage = storage();
        String json = storage.serialize(EmployeeMachineOperationAssignmentDirectory.empty())
                .replaceFirst("\\\"schema_version\\\": 1", "\\\"schema_version\\\": 2");

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(json));
        Files.writeString(storage.filePath().resolveSibling(storage.filePath().getFileName() + ".tmp"), "partial");
        assertThrows(IllegalStateException.class, storage::load);
    }

    @Test
    void directoryRejectsTwoActiveAssignmentsForOneEmployee() {
        EmployeeMachineOperationAssignment first = assignment(1L, grinder(3L), 10);
        EmployeeMachineOperationAssignment second = assignment(2L, pattyFormer(4L), 10);

        assertThrows(IllegalArgumentException.class, () -> new EmployeeMachineOperationAssignmentDirectory(
                EmployeeMachineOperationAssignmentSchema.CURRENT_VERSION, 2L, 3L, List.of(first, second)));
    }

    @Test
    void typedFailuresAreStableAndRecoveryRequiresEvidence() {
        List<String> codes = List.of(EmployeeMachineOperationFailureCode.values()).stream()
                .map(EmployeeMachineOperationFailureCode::serializedName)
                .toList();

        assertTrue(codes.containsAll(List.of(
                "employee_unavailable", "navigation_failed", "reservation_conflict", "reservation_missing",
                "workstation_unavailable", "workstation_replaced", "unsupported_machine", "input_unavailable",
                "run_conflict", "run_missing", "run_result_conflict", "player_interrupted", "recovery_required")));
        EmployeeMachineOperationAssignment value = assignment(1L, grinder(3L), 10);
        assertThrows(IllegalArgumentException.class, () -> value.evolve(
                EmployeeMachineOperationAssignmentState.RECOVERY_REQUIRED, 0, Optional.empty(), Optional.empty(),
                Optional.empty(), 0L, 0L, Optional.empty(), 2L, 21L));
    }

    @Test
    void oneHundredIndependentAssignmentsRemainBoundedAndDeterministic() {
        EmployeeMachineOperationAssignmentManager manager = EmployeeMachineOperationAssignmentManager.empty();

        assertTimeout(Duration.ofSeconds(5), () -> {
            for (int index = 0; index < 100; index++) {
                EmployeeId employee = new EmployeeId("butchercraft:employee/scale/" + index);
                WorkstationEndpointReference workstation = reference(
                        index % 2 == 0 ? "butchercraft:grinder" : "butchercraft:patty_former",
                        100 + index,
                        1_000L + index);
                String machine = index % 2 == 0 ? "grinder" : "patty_former";
                String operation = index % 2 == 0 ? OPERATION : "butchercraft:form_beef_patties";
                String input = index % 2 == 0 ? INPUT : "butchercraft:ground_beef";

                var result = manager.createOrObserve(
                        WORLD, employee, workstation, machine, POLICY, operation, input, 1, 64, index);

                assertEquals(EmployeeMachineOperationAssignmentManager.CreateStatus.CREATED, result.status());
                assertEquals(result.assignment(), manager.activeFor(employee).orElseThrow());
                assertEquals(result.assignment(), manager.activeForWorkstation(
                        workstation.instanceId().value()).orElseThrow());
            }
        });
        assertEquals(100, manager.assignments().size());
    }

    private EmployeeMachineOperationAssignmentStorage storage() {
        return new EmployeeMachineOperationAssignmentStorage(
                tempDir.resolve(EmployeeMachineOperationAssignmentSchema.FILE_NAME));
    }

    private static EmployeeMachineOperationAssignmentManager runningManager(int target, int completed) {
        EmployeeMachineOperationAssignmentManager manager = EmployeeMachineOperationAssignmentManager.empty();
        EmployeeMachineOperationAssignment value = manager.createOrObserve(
                WORLD, EMPLOYEE, grinder(3L), "grinder", POLICY, OPERATION, INPUT, 1, target, 20L).assignment();
        WorkstationReservationId reservation = new WorkstationReservationId(
                "butchercraft:workstation_reservation/v2/operator");
        value = manager.publish(value.assignmentId(), EmployeeMachineOperationAssignmentState.NAVIGATING, 0,
                Optional.empty(), Optional.of(reservation), Optional.empty(), 0L, 0L, Optional.empty(), 21L);
        value = manager.publish(value.assignmentId(), EmployeeMachineOperationAssignmentState.READY_TO_START, 0,
                Optional.empty(), Optional.of(reservation), Optional.empty(), 0L, 0L, Optional.empty(), 22L);
        value = manager.publish(value.assignmentId(), EmployeeMachineOperationAssignmentState.START_REQUESTED, 0,
                Optional.of("butchercraft:machine_run/v1/run"), Optional.of(reservation), Optional.empty(),
                1L, 1L, Optional.empty(), 23L);
        manager.publish(value.assignmentId(), EmployeeMachineOperationAssignmentState.RUNNING, completed,
                value.runIdentity(), Optional.of(reservation), Optional.empty(), 2L, 1L, Optional.empty(), 24L);
        return manager;
    }

    private static EmployeeMachineOperationAssignment assignment(
            long sequence,
            WorkstationEndpointReference workstation,
            int target
    ) {
        return EmployeeMachineOperationAssignment.create(
                WORLD, sequence, EMPLOYEE, workstation, workstation.endpointKey().workstationTypeIdentity()
                .substring("butchercraft:".length()), POLICY,
                workstation.endpointKey().workstationTypeIdentity().contains("patty")
                        ? "butchercraft:form_beef_patties" : OPERATION,
                workstation.endpointKey().workstationTypeIdentity().contains("patty")
                        ? "butchercraft:ground_beef" : INPUT,
                1, target, sequence, 20L);
    }

    private static EmployeeMachineOperationAssignment recoveryRequired(
            EmployeeMachineOperationAssignment assignment,
            EmployeeMachineOperationFailureCode failureCode,
            Optional<String> runIdentity,
            int completed,
            long observedRunRevision,
            long observedChildSequence
    ) {
        return assignment.evolve(
                EmployeeMachineOperationAssignmentState.RECOVERY_REQUIRED,
                completed,
                runIdentity,
                Optional.empty(),
                Optional.empty(),
                observedRunRevision,
                observedChildSequence,
                Optional.of(new EmployeeMachineOperationFailure(failureCode, "recovery evidence")),
                assignment.revision() + 1L,
                assignment.lastUpdatedTick() + 1L);
    }

    private static WorkstationEndpointReference grinder(long generation) {
        return reference("butchercraft:grinder", 3, generation);
    }

    private static WorkstationEndpointReference pattyFormer(long generation) {
        return reference("butchercraft:patty_former", 5, generation);
    }

    private static WorkstationEndpointReference reference(String type, int x, long generation) {
        WorkstationEndpointKey key = new WorkstationEndpointKey(type, "minecraft:overworld", x, 64, 0);
        WorkstationInstanceId id = WorkstationInstanceId.create(
                WORLD, key, generation,
                WorkstationEndpointConfiguration.standard().instanceAllocationConfigurationIdentity());
        return new WorkstationEndpointReference(id, key, generation);
    }
}
