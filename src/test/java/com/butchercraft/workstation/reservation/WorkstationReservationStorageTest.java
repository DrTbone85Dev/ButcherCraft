package com.butchercraft.workstation.reservation;

import com.butchercraft.workstation.reservation.persistence.WorkstationReservationStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationReservationStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void storageRoundTripsActiveReservationsDeterministically() {
        WorkstationReservationStorage storage =
                new WorkstationReservationStorage(tempDir.resolve("workstation_reservations.json"));
        WorkstationReservationRecord grinder = WorkstationReservationRecord.enRoute(
                request("grinder", 1, "employee/1", 10L));
        WorkstationReservationRecord pattyFormer = WorkstationReservationRecord.enRoute(
                request("patty_former", 3, "employee/2", 11L));
        WorkstationReservationDirectory directory = WorkstationReservationDirectory.of(List.of(pattyFormer, grinder));

        String first = storage.serialize(directory);
        WorkstationReservationDirectory loaded = storage.deserialize(first);
        String second = storage.serialize(loaded);

        assertEquals(List.of(grinder, pattyFormer), loaded.records());
        assertEquals(first, second);
        assertTrue(first.contains("\"schema_version\": 1"));
        assertTrue(first.contains("\"state\": \"employee_en_route\""));
    }

    @Test
    void saveAndLoadUseDedicatedFile() {
        WorkstationReservationStorage storage =
                new WorkstationReservationStorage(tempDir.resolve("nested").resolve("workstation_reservations.json"));
        WorkstationReservationRecord grinder = WorkstationReservationRecord.enRoute(
                request("grinder", 1, "employee/1", 10L));

        storage.save(WorkstationReservationDirectory.of(List.of(grinder)));
        WorkstationReservationDirectory loaded = storage.load();

        assertEquals(List.of(grinder), loaded.records());
    }

    private static WorkstationReservationRequest request(
            String type,
            int x,
            String employeePath,
            long createdTick
    ) {
        return new WorkstationReservationRequest(
                "butchercraft:workstation/" + type + "/minecraft/overworld/" + x + "/1/1",
                type,
                "butchercraft:" + employeePath,
                createdTick,
                "minecraft:overworld",
                x,
                1,
                1,
                x,
                1,
                0,
                1
        );
    }
}
