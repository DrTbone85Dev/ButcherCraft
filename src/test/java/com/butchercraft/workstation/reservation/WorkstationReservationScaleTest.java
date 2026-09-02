package com.butchercraft.workstation.reservation;

import com.butchercraft.workstation.reservation.persistence.WorkstationReservationStorage;
import com.butchercraft.world.checkpoint.CheckpointOwnerFileBundleCodec;
import com.butchercraft.world.checkpoint.CheckpointOwnerFileSnapshot;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationReservationScaleTest {
    private static final int CATEGORY_SIZE = 125;
    private static final int WORKSTATION_COUNT = CATEGORY_SIZE * 4;
    private static final int EXPECTED_RESERVATIONS = CATEGORY_SIZE * 5;
    private static final WorldIdentityRootIdentity WORLD = new WorldIdentityRootIdentity(
            "butchercraft:world_identity/reservation_scale", 1, "sha256:" + "5".repeat(64));

    @TempDir
    Path temporary;

    @Test
    void fiveHundredWorkstationsRemainDeterministicAcrossLookupSerializationReloadAndCheckpointCapture() {
        List<WorkstationReservationRecord> legacy = new ArrayList<>();
        for (int index = 0; index < CATEGORY_SIZE; index++) {
            int coordinate = index;
            legacy.add(WorkstationReservationRecord.migrateLegacy(
                    WORLD,
                    index + 1L,
                    index + 1L,
                    "butchercraft:legacy_workstation/" + coordinate,
                    "grinder",
                    employee("legacy", coordinate),
                    WorkstationReservationState.EMPLOYEE_EN_ROUTE,
                    coordinate,
                    OptionalLong.empty(),
                    Optional.empty(),
                    "minecraft:overworld",
                    coordinate, 1, 0,
                    coordinate, 1, -1,
                    1,
                    Optional.empty()
            ));
        }
        WorkstationReservationManager manager = new WorkstationReservationManager(
                WorkstationReservationDirectory.of(
                        WORLD, CATEGORY_SIZE + 1L, CATEGORY_SIZE, legacy));

        for (int index = CATEGORY_SIZE; index < CATEGORY_SIZE * 2; index++) {
            manager.reserve(operator(index, employee("operator", index))).orThrow();
        }
        for (int index = CATEGORY_SIZE * 2; index < CATEGORY_SIZE * 3; index++) {
            manager.reserve(handler(index, employee("handler", index), "transfer/" + index)).orThrow();
        }
        for (int index = CATEGORY_SIZE * 3; index < WORKSTATION_COUNT; index++) {
            manager.reserve(operator(index, employee("operator", index))).orThrow();
            manager.reserve(handler(index, employee("handler", index), "transfer/" + index)).orThrow();
        }

        long lookupStarted = System.nanoTime();
        for (int index = 0; index < WORKSTATION_COUNT; index++) {
            if (index < CATEGORY_SIZE) {
                assertTrue(manager.findByEmployee(employee("legacy", index)).isPresent());
            } else if (index < CATEGORY_SIZE * 2) {
                assertTrue(manager.operatorForWorkstation(instance(index)).isPresent());
            } else if (index < CATEGORY_SIZE * 3) {
                assertTrue(manager.handlerForWorkstation(instance(index)).isPresent());
            } else {
                assertEquals(2, manager.reservationsForWorkstation(instance(index)).size());
            }
        }
        long lookupNanos = System.nanoTime() - lookupStarted;

        long compatibilityStarted = System.nanoTime();
        for (int index = CATEGORY_SIZE; index < CATEGORY_SIZE * 2; index++) {
            assertEquals(WorkstationReservationCompatibilityDecision.REJECTED,
                    manager.compatibility(operator(index, employee("contender", index))).decision());
        }
        long compatibilityNanos = System.nanoTime() - compatibilityStarted;

        WorkstationReservationStorage storage = new WorkstationReservationStorage(
                temporary.resolve(WorkstationReservationSchema.FILE_NAME));
        long serializationStarted = System.nanoTime();
        String json = storage.serialize(manager.directory());
        long serializationNanos = System.nanoTime() - serializationStarted;
        long reloadStarted = System.nanoTime();
        WorkstationReservationDirectory loaded = storage.deserialize(
                json, WORLD, WorkstationReservationMigrationResolver.noProof());
        long reloadNanos = System.nanoTime() - reloadStarted;

        byte[] reservationBytes = json.getBytes(StandardCharsets.UTF_8);
        long checkpointStarted = System.nanoTime();
        byte[] checkpoint = CheckpointOwnerFileBundleCodec.encode(new CheckpointOwnerFileSnapshot(
                LegacySplitRecoveryParticipants.WORKFORCE,
                2,
                loaded.ownerRevision(),
                false,
                List.of(new CheckpointOwnerFileSnapshot.FilePayload(
                        WorkstationReservationSchema.FILE_NAME, reservationBytes))
        ));
        CheckpointOwnerFileSnapshot decoded = CheckpointOwnerFileBundleCodec.decode(checkpoint);
        long checkpointNanos = System.nanoTime() - checkpointStarted;

        assertEquals(WORKSTATION_COUNT, distinctWorkstations(loaded.records()));
        assertEquals(EXPECTED_RESERVATIONS, loaded.records().size());
        assertEquals(CATEGORY_SIZE, roleCount(loaded.records(), WorkstationReservationRole.LEGACY_EXCLUSIVE));
        assertEquals(CATEGORY_SIZE * 2, roleCount(loaded.records(), WorkstationReservationRole.MACHINE_OPERATOR));
        assertEquals(CATEGORY_SIZE * 2, roleCount(loaded.records(), WorkstationReservationRole.MATERIAL_HANDLER));
        assertEquals(reservationBytes.length, decoded.files().getFirst().bytes().length);
        System.out.printf(
                "IM032A_SCALE workstations=%d reservations=%d employees=%d operator_only=%d handler_only=%d "
                        + "coexisting=%d legacy=%d schema2_bytes=%d serialize_ms=%.3f reload_ms=%.3f "
                        + "lookup_ms=%.3f compatibility_ms=%.3f checkpoint_bytes=%d checkpoint_ms=%.3f%n",
                WORKSTATION_COUNT,
                EXPECTED_RESERVATIONS,
                EXPECTED_RESERVATIONS,
                CATEGORY_SIZE,
                CATEGORY_SIZE,
                CATEGORY_SIZE,
                CATEGORY_SIZE,
                reservationBytes.length,
                milliseconds(serializationNanos),
                milliseconds(reloadNanos),
                milliseconds(lookupNanos),
                milliseconds(compatibilityNanos),
                checkpoint.length,
                milliseconds(checkpointNanos)
        );
    }

    private static long distinctWorkstations(List<WorkstationReservationRecord> records) {
        return records.stream().map(WorkstationReservationRecord::locationIdentity).distinct().count();
    }

    private static long roleCount(List<WorkstationReservationRecord> records, WorkstationReservationRole role) {
        return records.stream().filter(record -> record.role() == role).count();
    }

    private static WorkstationReservationRequest operator(int coordinate, String employee) {
        String request = WorkstationReservationRequest.canonicalRequestIdentity(
                WORLD, instance(coordinate), employee, WorkstationReservationRole.MACHINE_OPERATOR,
                Optional.empty(), Optional.empty(), WorkstationReservationEndpointScope.none());
        return WorkstationReservationRequest.machineOperator(
                WORLD, request, instance(coordinate), 1L, "grinder", employee, Optional.empty(),
                coordinate, "minecraft:overworld", coordinate, 1, 0, coordinate, 1, -1, 1);
    }

    private static WorkstationReservationRequest handler(
            int coordinate,
            String employee,
            String transferPath
    ) {
        String transfer = "butchercraft:" + transferPath;
        String assignment = "butchercraft:assignment/" + coordinate;
        WorkstationReservationEndpointScope scope = WorkstationReservationEndpointScope.destination(
                endpoint(coordinate));
        String request = WorkstationReservationRequest.canonicalRequestIdentity(
                WORLD, instance(coordinate), employee, WorkstationReservationRole.MATERIAL_HANDLER,
                Optional.of(assignment), Optional.of(transfer), scope);
        return WorkstationReservationRequest.materialHandler(
                WORLD, request, instance(coordinate), 1L, "grinder", employee,
                assignment, transfer, scope, "in_transit", coordinate,
                coordinate, "minecraft:overworld", coordinate, 1, 0, coordinate, 1, -1, 1);
    }

    private static String employee(String category, int index) {
        return "butchercraft:employee/" + category + "/" + index;
    }

    private static String instance(int index) {
        String digest = WorkstationReservationCanonicalDigest.create("scale_instance").add(index).finish();
        return "butchercraft:workstation_instance/v1/" + WorkstationReservationCanonicalDigest.suffix(digest);
    }

    private static String endpoint(int coordinate) {
        return "butchercraft:grinder|minecraft:overworld|" + coordinate + "|1|0";
    }

    private static double milliseconds(long nanos) {
        return nanos / 1_000_000.0D;
    }
}
