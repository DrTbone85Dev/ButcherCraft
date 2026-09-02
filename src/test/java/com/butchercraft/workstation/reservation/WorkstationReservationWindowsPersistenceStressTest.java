package com.butchercraft.workstation.reservation;

import com.butchercraft.workstation.reservation.persistence.WorkstationReservationStorage;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationReservationWindowsPersistenceStressTest {
    private static final int ITERATIONS = 100;
    private static final WorldIdentityRootIdentity WORLD = new WorldIdentityRootIdentity(
            "butchercraft:world_identity/windows_stress", 1, "sha256:" + "6".repeat(64));

    @TempDir
    Path temporary;

    @Test
    void repeatedCompatibleAcquireReleaseSaveAndReloadLeavesExactStateAndNoAttemptDebris() throws Exception {
        WorkstationReservationStorage storage = new WorkstationReservationStorage(
                temporary.resolve("nested").resolve(WorkstationReservationSchema.FILE_NAME));
        int failures = 0;
        int staleReleaseRejections = 0;
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            WorkstationReservationManager manager = WorkstationReservationManager.empty(WORLD);
            WorkstationReservationRecord operator = manager.reserve(operator(iteration)).orThrow();
            WorkstationReservationRecord handler = manager.reserve(handler(iteration)).orThrow();
            storage.save(manager.directory());
            WorkstationReservationManager reloaded = new WorkstationReservationManager(storage.load(
                    WORLD, WorkstationReservationMigrationResolver.noProof()));
            reloaded.release(handler.reservationId(), WorkstationReservationRole.MATERIAL_HANDLER,
                    "stress handler release").orThrow();
            reloaded.release(operator.reservationId(), WorkstationReservationRole.MACHINE_OPERATOR,
                    "stress operator release").orThrow();
            if (reloaded.release(handler.reservationId(), WorkstationReservationRole.MACHINE_OPERATOR,
                    "stale wrong-role release").succeeded()) {
                failures++;
            } else {
                staleReleaseRejections++;
            }
            storage.save(reloaded.directory());
            WorkstationReservationDirectory finalDirectory = storage.load(
                    WORLD, WorkstationReservationMigrationResolver.noProof());
            if (!finalDirectory.records().stream().noneMatch(WorkstationReservationRecord::active)) {
                failures++;
            }
        }

        long debris;
        try (var files = Files.walk(temporary)) {
            debris = files.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals(WorkstationReservationSchema.FILE_NAME))
                    .count();
        }
        WorkstationReservationDirectory finalDirectory = storage.load(
                WORLD, WorkstationReservationMigrationResolver.noProof());
        assertEquals(0, failures);
        assertEquals(ITERATIONS, staleReleaseRejections);
        assertEquals(2, finalDirectory.records().size());
        assertTrue(finalDirectory.records().stream().noneMatch(WorkstationReservationRecord::active));
        assertEquals(0L, debris);
        assertTrue(Files.isRegularFile(storage.filePath()));
        assertFalse(storage.serialize(finalDirectory).isBlank());
        System.out.printf(
                "IM032A_WINDOWS_STRESS iterations=%d failures=%d final_records=%d active=0 "
                        + "stale_release_rejections=%d temp_artifacts=%d%n",
                ITERATIONS, failures, finalDirectory.records().size(), staleReleaseRejections, debris);
    }

    private static WorkstationReservationRequest operator(int iteration) {
        String instance = instance(iteration);
        String employee = "butchercraft:employee/operator/" + iteration;
        String request = WorkstationReservationRequest.canonicalRequestIdentity(
                WORLD, instance, employee, WorkstationReservationRole.MACHINE_OPERATOR,
                Optional.empty(), Optional.empty(), WorkstationReservationEndpointScope.none());
        return WorkstationReservationRequest.machineOperator(
                WORLD, request, instance, 1L, "grinder", employee, Optional.empty(),
                iteration, "minecraft:overworld", iteration, 1, 0, iteration, 1, -1, 1);
    }

    private static WorkstationReservationRequest handler(int iteration) {
        String instance = instance(iteration);
        String employee = "butchercraft:employee/handler/" + iteration;
        String assignment = "butchercraft:assignment/" + iteration;
        String transfer = "butchercraft:transfer/" + iteration;
        WorkstationReservationEndpointScope scope = WorkstationReservationEndpointScope.destination(
                "butchercraft:grinder|minecraft:overworld|" + iteration + "|1|0");
        String request = WorkstationReservationRequest.canonicalRequestIdentity(
                WORLD, instance, employee, WorkstationReservationRole.MATERIAL_HANDLER,
                Optional.of(assignment), Optional.of(transfer), scope);
        return WorkstationReservationRequest.materialHandler(
                WORLD, request, instance, 1L, "grinder", employee,
                assignment, transfer, scope, "in_transit", iteration,
                iteration, "minecraft:overworld", iteration, 1, 0, iteration, 1, -1, 1);
    }

    private static String instance(int index) {
        String digest = WorkstationReservationCanonicalDigest.create("stress_instance").add(index).finish();
        return "butchercraft:workstation_instance/v1/" + WorkstationReservationCanonicalDigest.suffix(digest);
    }
}
