package com.butchercraft.workstation.reservation;

import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationReservationManagerTest {
    private static final WorldIdentityRootIdentity WORLD = new WorldIdentityRootIdentity(
            "butchercraft:world_identity/test", 1, "sha256:" + "1".repeat(64));

    @Test
    void operatorAndTransferBoundHandlerMayCoexist() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty(WORLD);
        WorkstationReservationRecord operator = manager.reserve(operator(1, "employee/1")).orThrow();
        WorkstationReservationRecord handler = manager.reserve(handler(1, "employee/2", "transfer/1")).orThrow();

        assertEquals(2, manager.reservationsForWorkstation(instance(1)).size());
        assertEquals(operator, manager.operatorForWorkstation(instance(1)).orElseThrow());
        assertEquals(handler, manager.handlerForWorkstation(instance(1)).orElseThrow());
    }

    @Test
    void secondOperatorAndSecondHandlerAreRejectedIndependently() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty(WORLD);
        manager.reserve(operator(1, "employee/1")).orThrow();
        manager.reserve(handler(1, "employee/2", "transfer/1")).orThrow();

        assertEquals(WorkstationReservationFailureCode.OPERATOR_ALREADY_RESERVED,
                manager.reserve(operator(1, "employee/3")).failure().orElseThrow().code());
        assertEquals(WorkstationReservationFailureCode.HANDLER_ALREADY_RESERVED,
                manager.reserve(handler(1, "employee/4", "transfer/2")).failure().orElseThrow().code());
    }

    @Test
    void employeeMayHoldOnlyOneReservation() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty(WORLD);
        manager.reserve(operator(1, "employee/1")).orThrow();

        assertEquals(WorkstationReservationFailureCode.EMPLOYEE_ALREADY_RESERVED,
                manager.reserve(handler(2, "employee/1", "transfer/1")).failure().orElseThrow().code());
    }

    @Test
    void exactDuplicateObservesExistingIdentity() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty(WORLD);
        WorkstationReservationRequest request = handler(1, "employee/1", "transfer/1");
        WorkstationReservationRecord first = manager.reserve(request).orThrow();
        WorkstationReservationResult<WorkstationReservationRecord> duplicate = manager.reserve(request);

        assertEquals(first, duplicate.orThrow());
        assertEquals(WorkstationReservationSuccessCode.EXISTING_RESERVATION_OBSERVED,
                duplicate.successCode().orElseThrow());
        assertEquals(1, manager.activeReservations().size());
    }

    @Test
    void releaseIsRoleAndIdentitySpecific() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty(WORLD);
        WorkstationReservationRecord operator = manager.reserve(operator(1, "employee/1")).orThrow();
        WorkstationReservationRecord handler = manager.reserve(handler(1, "employee/2", "transfer/1")).orThrow();

        assertFalse(manager.release(handler.reservationId(), WorkstationReservationRole.MACHINE_OPERATOR, "wrong role")
                .succeeded());
        manager.release(handler.reservationId(), WorkstationReservationRole.MATERIAL_HANDLER, "transfer complete")
                .orThrow();

        assertEquals(operator, manager.operatorForWorkstation(instance(1)).orElseThrow());
        assertTrue(manager.handlerForWorkstation(instance(1)).isEmpty());
    }

    @Test
    void operatorReleasePreservesCompatibleHandler() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty(WORLD);
        WorkstationReservationRecord operator = manager.reserve(operator(1, "employee/1")).orThrow();
        WorkstationReservationRecord handler = manager.reserve(handler(1, "employee/2", "transfer/1")).orThrow();

        manager.release(operator.reservationId(), WorkstationReservationRole.MACHINE_OPERATOR, "operator complete")
                .orThrow();

        assertTrue(manager.operatorForWorkstation(instance(1)).isEmpty());
        assertEquals(handler, manager.handlerForWorkstation(instance(1)).orElseThrow());
    }

    @Test
    void handlerInvalidationIsRoleAndIdentitySpecific() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty(WORLD);
        WorkstationReservationRecord operator = manager.reserve(operator(1, "employee/1")).orThrow();
        WorkstationReservationRecord handler = manager.reserve(handler(1, "employee/2", "transfer/1")).orThrow();

        assertFalse(manager.invalidate(
                handler.reservationId(), WorkstationReservationRole.MACHINE_OPERATOR, "wrong role").succeeded());
        manager.invalidate(handler.reservationId(), WorkstationReservationRole.MATERIAL_HANDLER, "transfer failed")
                .orThrow();

        assertEquals(operator, manager.operatorForWorkstation(instance(1)).orElseThrow());
        assertTrue(manager.handlerForWorkstation(instance(1)).isEmpty());
    }

    @Test
    void staleReleaseCannotRemoveNewerReservation() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty(WORLD);
        WorkstationReservationRecord first = manager.reserve(operator(1, "employee/1")).orThrow();
        manager.release(first.reservationId(), first.role(), "first complete").orThrow();
        WorkstationReservationRecord second = manager.reserve(operator(1, "employee/2")).orThrow();

        WorkstationReservationResult<WorkstationReservationRecord> stale =
                manager.release(first.reservationId(), WorkstationReservationRole.MATERIAL_HANDLER, "stale");

        assertFalse(stale.succeeded());
        assertEquals(WorkstationReservationFailureCode.STALE_RESERVATION, stale.failure().orElseThrow().code());
        assertEquals(second, manager.operatorForWorkstation(instance(1)).orElseThrow());
    }

    @Test
    void replacementInstanceAtSamePositionIsRejected() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty(WORLD);
        manager.reserve(operator(1, "employee/1")).orThrow();
        WorkstationReservationRequest replacement = operatorRequest(
                replacementInstance(1), 2L, 1, "employee/2");

        assertEquals(WorkstationReservationFailureCode.WORKSTATION_INSTANCE_CONFLICT,
                manager.reserve(replacement).failure().orElseThrow().code());
    }

    @Test
    void arrivalAndPositionUpdatesAdvanceOwnerRevision() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty(WORLD);
        WorkstationReservationRecord record = manager.reserve(operator(1, "employee/1")).orThrow();
        WorkstationReservationRecord arrived = manager.markArrived(record.employeeIdentity(), record.workstationIdentity())
                .orElseThrow();
        WorkstationReservationRecord moved = manager.updateOperatingPosition(
                record.employeeIdentity(), record.workstationIdentity(), 4, 2, 3).orElseThrow();

        assertEquals(WorkstationReservationState.EMPLOYEE_ARRIVED, arrived.state());
        assertTrue(moved.lastUpdateRevision() > arrived.lastUpdateRevision());
    }

    @Test
    void invalidOrUnscopedHandlerCannotAcquireAccess() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty(WORLD);

        assertEquals(WorkstationReservationFailureCode.INVALID_HANDLER_TRANSFER,
                manager.reserve(handler(
                        1,
                        "employee/1",
                        "transfer/1",
                        WorkstationReservationEndpointScope.destination(endpoint(1)),
                        "completed"
                )).failure().orElseThrow().code());
        assertThrows(IllegalArgumentException.class, () -> WorkstationReservationRequest.materialHandler(
                WORLD,
                "butchercraft:request/unscoped",
                instance(1),
                1L,
                "grinder",
                "butchercraft:employee/1",
                "butchercraft:assignment/1",
                "butchercraft:transfer/1",
                WorkstationReservationEndpointScope.none(),
                "in_transit",
                1L,
                1L,
                "minecraft:overworld",
                1, 1, 1,
                1, 1, 0,
                1
        ));
    }

    @Test
    void requestIdentityMustMatchEveryCanonicalBinding() {
        WorkstationReservationRequest canonical = operator(1, "employee/1");

        assertThrows(IllegalArgumentException.class, () -> WorkstationReservationRequest.machineOperator(
                WORLD,
                "butchercraft:workstation_reservation_request/v2/not-canonical",
                canonical.workstationIdentity(),
                canonical.workstationGeneration(),
                canonical.workstationType(),
                canonical.employeeIdentity(),
                canonical.assignmentReference(),
                canonical.createdTick(),
                canonical.dimensionIdentity(),
                canonical.workstationX(), canonical.workstationY(), canonical.workstationZ(),
                canonical.operatingX(), canonical.operatingY(), canonical.operatingZ(),
                canonical.anchorRadius()
        ));
    }

    @Test
    void endpointPurposeAndDirectionRemainExact() {
        WorkstationReservationManager sourceManager = WorkstationReservationManager.empty(WORLD);
        WorkstationReservationRecord source = sourceManager.reserve(handler(
                1, "employee/1", "transfer/1",
                WorkstationReservationEndpointScope.source(endpoint(1)), "source_bound")).orThrow();
        WorkstationReservationManager returnManager = WorkstationReservationManager.empty(WORLD);
        WorkstationReservationRecord sourceReturn = returnManager.reserve(handler(
                1, "employee/2", "transfer/2",
                WorkstationReservationEndpointScope.sourceReturn(endpoint(1)), "cancellation_requested")).orThrow();

        assertEquals(WorkstationReservationEndpointPurpose.SOURCE, source.endpointScope().purpose());
        assertEquals(WorkstationReservationEndpointDirection.WITHDRAWAL, source.endpointScope().direction());
        assertEquals(WorkstationReservationEndpointPurpose.SOURCE_RETURN, sourceReturn.endpointScope().purpose());
        assertEquals(WorkstationReservationEndpointDirection.DEPOSIT, sourceReturn.endpointScope().direction());
    }

    @Test
    void sourceReturnAdmissionSupportsCustodyBeforeMaterialHandlingCancellationPublication() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty(WORLD);

        WorkstationReservationRecord sourceReturn = manager.reserve(handler(
                1,
                "employee/source-return",
                "transfer/source-return",
                WorkstationReservationEndpointScope.sourceReturn(endpoint(1)),
                "in_transit"
        )).orThrow();

        assertEquals(WorkstationReservationRole.MATERIAL_HANDLER, sourceReturn.role());
        assertEquals(WorkstationReservationEndpointPurpose.SOURCE_RETURN, sourceReturn.endpointScope().purpose());
        assertEquals("in_transit", sourceReturn.lifecycleEvidence());
    }

    @Test
    void legacyExclusiveConflictsWithEveryModernRoleAndIsNeverRequestedNormally() {
        WorkstationReservationManager operatorManager = managerWithLegacyExclusive();
        WorkstationReservationManager handlerManager = managerWithLegacyExclusive();

        assertEquals(WorkstationReservationFailureCode.LEGACY_EXCLUSIVE_CONFLICT,
                operatorManager.reserve(operator(1, "employee/2")).failure().orElseThrow().code());
        assertEquals(WorkstationReservationFailureCode.LEGACY_EXCLUSIVE_CONFLICT,
                handlerManager.reserve(handler(1, "employee/2", "transfer/1"))
                        .failure().orElseThrow().code());
        assertThrows(IllegalArgumentException.class, () -> new WorkstationReservationRequest(
                WORLD, "butchercraft:request/legacy", instance(1), 1L, "grinder",
                "butchercraft:employee/2", WorkstationReservationRole.LEGACY_EXCLUSIVE,
                Optional.empty(), Optional.empty(), WorkstationReservationEndpointScope.none(),
                "legacy", 0L, 10L, "minecraft:overworld",
                1, 1, 1, 1, 1, 0, 1, WorkstationReservationSchema.CONFIGURATION_IDENTITY));
    }

    @Test
    void racingRequestsSerializeAtTheOwnerBoundary() throws Exception {
        WorkstationReservationManager operatorRace = WorkstationReservationManager.empty(WORLD);
        WorkstationReservationResult<WorkstationReservationRecord>[] operatorResults = race(
                () -> operatorRace.reserve(operator(1, "employee/1")),
                () -> operatorRace.reserve(operator(1, "employee/2"))
        );
        assertEquals(1, java.util.Arrays.stream(operatorResults)
                .filter(result -> result.succeeded()).count());
        assertEquals(1, operatorRace.activeReservations().size());

        WorkstationReservationManager compatibleRace = WorkstationReservationManager.empty(WORLD);
        WorkstationReservationResult<WorkstationReservationRecord>[] compatibleResults = race(
                () -> compatibleRace.reserve(operator(1, "employee/3")),
                () -> compatibleRace.reserve(handler(1, "employee/4", "transfer/2"))
        );
        assertTrue(java.util.Arrays.stream(compatibleResults).allMatch(result -> result.succeeded()));
        assertEquals(2, compatibleRace.activeReservations().size());
    }

    @Test
    void persistedCandidateRejectsDuplicateEmployeeRoleAndReplacementInstance() {
        WorkstationReservationRecord operatorOne = WorkstationReservationRecord.acquire(
                operator(1, "employee/1"), 1L, 1L);
        WorkstationReservationRecord sameEmployee = WorkstationReservationRecord.acquire(
                operator(2, "employee/1"), 2L, 2L);
        assertThrows(IllegalArgumentException.class, () -> WorkstationReservationDirectory.of(
                WORLD, 3L, 2L, java.util.List.of(operatorOne, sameEmployee)));

        WorkstationReservationRecord operatorTwo = WorkstationReservationRecord.acquire(
                operator(1, "employee/2"), 2L, 2L);
        assertThrows(IllegalArgumentException.class, () -> WorkstationReservationDirectory.of(
                WORLD, 3L, 2L, java.util.List.of(operatorOne, operatorTwo)));

        WorkstationReservationRecord replacementHandler = WorkstationReservationRecord.acquire(
                handlerRequest(replacementInstance(1), 2L, 1, "employee/3", "transfer/3",
                        WorkstationReservationEndpointScope.destination(endpoint(1)), "in_transit"),
                2L,
                2L
        );
        assertThrows(IllegalArgumentException.class, () -> WorkstationReservationDirectory.of(
                WORLD, 3L, 2L, java.util.List.of(operatorOne, replacementHandler)));
    }

    private static WorkstationReservationRequest operator(int x, String employeePath) {
        return operatorRequest(instance(x), 1L, x, employeePath);
    }

    private static WorkstationReservationRequest operatorRequest(
            String workstationIdentity,
            long generation,
            int x,
            String employeePath
    ) {
        String employee = "butchercraft:" + employeePath;
        String request = WorkstationReservationRequest.canonicalRequestIdentity(
                WORLD, workstationIdentity, employee, WorkstationReservationRole.MACHINE_OPERATOR,
                Optional.empty(), Optional.empty(), WorkstationReservationEndpointScope.none());
        return WorkstationReservationRequest.machineOperator(
                WORLD, request, workstationIdentity, generation, "grinder", employee, Optional.empty(),
                10L, "minecraft:overworld", x, 1, 1, x, 1, 0, 1);
    }

    private static WorkstationReservationRequest handler(int x, String employeePath, String transferPath) {
        return handler(
                x,
                employeePath,
                transferPath,
                WorkstationReservationEndpointScope.destination(endpoint(x)),
                "in_transit"
        );
    }

    private static WorkstationReservationRequest handler(
            int x,
            String employeePath,
            String transferPath,
            WorkstationReservationEndpointScope scope,
            String lifecycle
    ) {
        return handlerRequest(instance(x), 1L, x, employeePath, transferPath, scope, lifecycle);
    }

    private static WorkstationReservationRequest handlerRequest(
            String workstation,
            long generation,
            int x,
            String employeePath,
            String transferPath,
            WorkstationReservationEndpointScope scope,
            String lifecycle
    ) {
        String employee = "butchercraft:" + employeePath;
        String assignment = "butchercraft:assignment/" + transferPath.substring(transferPath.indexOf('/') + 1);
        String transfer = "butchercraft:" + transferPath;
        String request = WorkstationReservationRequest.canonicalRequestIdentity(
                WORLD, workstation, employee, WorkstationReservationRole.MATERIAL_HANDLER,
                Optional.of(assignment), Optional.of(transfer), scope);
        return WorkstationReservationRequest.materialHandler(
                WORLD, request, workstation, generation, "grinder", employee, assignment, transfer, scope,
                lifecycle, 2L, 11L, "minecraft:overworld", x, 1, 1, x, 1, 0, 1);
    }

    private static WorkstationReservationManager managerWithLegacyExclusive() {
        WorkstationReservationRecord legacy = WorkstationReservationRecord.migrateLegacy(
                WORLD,
                1L,
                1L,
                "butchercraft:workstation/grinder/minecraft/overworld/1/1/1",
                "grinder",
                "butchercraft:employee/legacy",
                WorkstationReservationState.EMPLOYEE_EN_ROUTE,
                5L,
                OptionalLong.empty(),
                Optional.empty(),
                "minecraft:overworld",
                1, 1, 1,
                1, 1, 0,
                1,
                Optional.empty()
        );
        return new WorkstationReservationManager(WorkstationReservationDirectory.of(
                WORLD, 2L, 1L, java.util.List.of(legacy)));
    }

    @SuppressWarnings("unchecked")
    private static WorkstationReservationResult<WorkstationReservationRecord>[] race(
            java.util.concurrent.Callable<WorkstationReservationResult<WorkstationReservationRecord>> first,
            java.util.concurrent.Callable<WorkstationReservationResult<WorkstationReservationRecord>> second
    ) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<WorkstationReservationResult<WorkstationReservationRecord>> left = executor.submit(() -> {
                ready.countDown();
                start.await();
                return first.call();
            });
            Future<WorkstationReservationResult<WorkstationReservationRecord>> right = executor.submit(() -> {
                ready.countDown();
                start.await();
                return second.call();
            });
            ready.await();
            start.countDown();
            return new WorkstationReservationResult[]{left.get(), right.get()};
        }
    }

    private static String endpoint(int x) {
        return "butchercraft:grinder|minecraft:overworld|" + x + "|1|1";
    }

    private static String instance(int x) {
        return "butchercraft:workstation_instance/v1/" + Integer.toHexString(x).repeat(64).substring(0, 64);
    }

    private static String replacementInstance(int x) {
        return "butchercraft:workstation_instance/v1/" + Integer.toHexString(x + 8).repeat(64).substring(0, 64);
    }
}
