package com.butchercraft.workstation.reservation;

import com.butchercraft.workstation.reservation.persistence.WorkstationReservationStorage;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationReservationStorageTest {
    private static final WorldIdentityRootIdentity WORLD = new WorldIdentityRootIdentity(
            "butchercraft:world_identity/test", 1, "sha256:" + "2".repeat(64));

    @TempDir
    Path tempDir;

    @Test
    void schemaTwoRoundTripsOperatorAndHandlerDeterministically() {
        WorkstationReservationStorage storage = storage();
        WorkstationReservationManager manager = WorkstationReservationManager.empty(WORLD);
        manager.reserve(operator()).orThrow();
        manager.reserve(handler()).orThrow();

        String first = storage.serialize(manager.directory());
        WorkstationReservationDirectory loaded = storage.deserialize(
                first, WORLD, WorkstationReservationMigrationResolver.noProof());
        String second = storage.serialize(loaded);

        assertEquals(first, second);
        assertEquals(2, loaded.records().size());
        assertTrue(first.contains("\"schema_version\": 2"));
        assertTrue(first.contains("\"role\": \"machine_operator\""));
        assertTrue(first.contains("\"role\": \"material_handler\""));
    }

    @Test
    void schemaOneWithoutProofMigratesToLegacyExclusiveIdempotently() {
        WorkstationReservationStorage storage = storage();
        String legacy = legacyJson();

        WorkstationReservationDirectory first = storage.deserialize(
                legacy, WORLD, WorkstationReservationMigrationResolver.noProof());
        WorkstationReservationDirectory second = storage.deserialize(
                legacy, WORLD, WorkstationReservationMigrationResolver.noProof());

        assertEquals(first, second);
        assertEquals(WorkstationReservationRole.LEGACY_EXCLUSIVE, first.records().getFirst().role());
        assertTrue(!first.records().getFirst().exactWorkstationInstance());
    }

    @Test
    void emptySchemaOneMigratesToCanonicalEmptySchemaTwo() {
        WorkstationReservationDirectory migrated = storage().deserialize(
                "{\"schema_version\":1,\"reservations\":[]}",
                WORLD,
                WorkstationReservationMigrationResolver.noProof()
        );

        assertEquals(WorkstationReservationSchema.CURRENT_VERSION, migrated.schemaVersion());
        assertEquals(1L, migrated.nextSequence());
        assertEquals(0L, migrated.ownerRevision());
        assertTrue(migrated.records().isEmpty());
    }

    @Test
    void schemaOneWithProofMigratesOnlyToMaterialHandler() {
        WorkstationReservationStorage storage = storage();
        String assignment = "butchercraft:assignment/1";
        String transfer = "butchercraft:transfer/1";
        WorkstationReservationEndpointScope scope = WorkstationReservationEndpointScope.source(
                "butchercraft:cutting_table|minecraft:overworld|1|1|1");
        WorkstationReservationMigrationEvidence proof = new WorkstationReservationMigrationEvidence(
                WorkstationReservationRequest.canonicalRequestIdentity(
                        WORLD,
                        instance(),
                        "butchercraft:employee/1",
                        WorkstationReservationRole.MATERIAL_HANDLER,
                        Optional.of(assignment),
                        Optional.of(transfer),
                        scope
                ),
                instance(),
                3L,
                assignment,
                transfer,
                scope,
                "source_bound",
                4L
        );

        WorkstationReservationRecord migrated = storage.deserialize(
                legacyJson(), WORLD, ignored -> Optional.of(proof)).records().getFirst();

        assertEquals(WorkstationReservationRole.MATERIAL_HANDLER, migrated.role());
        assertEquals(instance(), migrated.workstationIdentity());
        assertTrue(migrated.exactWorkstationInstance());
    }

    @Test
    void unsupportedFutureSchemaAndWrongWorldFailVisibly() {
        WorkstationReservationStorage storage = storage();
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                "{\"schema_version\":3,\"reservations\":[]}", WORLD,
                WorkstationReservationMigrationResolver.noProof()));

        WorkstationReservationManager manager = WorkstationReservationManager.empty(WORLD);
        manager.reserve(operator()).orThrow();
        WorldIdentityRootIdentity other = new WorldIdentityRootIdentity(
                "butchercraft:world_identity/other", 1, "sha256:" + "3".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                storage.serialize(manager.directory()), other,
                WorkstationReservationMigrationResolver.noProof()));
    }

    @Test
    void saveAndLoadUseAtomicDedicatedFile() {
        WorkstationReservationStorage storage = storage();
        WorkstationReservationManager manager = WorkstationReservationManager.empty(WORLD);
        manager.reserve(operator()).orThrow();
        storage.save(manager.directory());

        assertEquals(manager.directory(), storage.load(WORLD, WorkstationReservationMigrationResolver.noProof()));
    }

    @Test
    void malformedRoleMissingHandlerBindingAndWrongConfigurationFailVisibly() {
        WorkstationReservationStorage storage = storage();
        WorkstationReservationManager operatorManager = WorkstationReservationManager.empty(WORLD);
        operatorManager.reserve(operator()).orThrow();
        String operatorJson = storage.serialize(operatorManager.directory());
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                operatorJson.replace("\"role\": \"machine_operator\"", "\"role\": \"unknown_role\""),
                WORLD,
                WorkstationReservationMigrationResolver.noProof()
        ));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                operatorJson.replace(
                        WorkstationReservationSchema.CONFIGURATION_IDENTITY,
                        "butchercraft:workstation_reservation_configuration/unsupported"),
                WORLD,
                WorkstationReservationMigrationResolver.noProof()
        ));

        WorkstationReservationManager handlerManager = WorkstationReservationManager.empty(WORLD);
        handlerManager.reserve(handler()).orThrow();
        String handlerJson = storage.serialize(handlerManager.directory())
                .replace("    \"transfer_reference\": \"butchercraft:transfer/1\",\r\n", "")
                .replace("    \"transfer_reference\": \"butchercraft:transfer/1\",\n", "");
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                handlerJson,
                WORLD,
                WorkstationReservationMigrationResolver.noProof()
        ));
    }

    private WorkstationReservationStorage storage() {
        return new WorkstationReservationStorage(tempDir.resolve("nested/workstation_reservations.json"));
    }

    private static WorkstationReservationRequest operator() {
        String employee = "butchercraft:employee/1";
        String request = WorkstationReservationRequest.canonicalRequestIdentity(
                WORLD, instance(), employee, WorkstationReservationRole.MACHINE_OPERATOR,
                Optional.empty(), Optional.empty(), WorkstationReservationEndpointScope.none());
        return WorkstationReservationRequest.machineOperator(
                WORLD, request, instance(), 3L, "grinder", employee, Optional.empty(),
                10L, "minecraft:overworld", 1, 1, 1, 1, 1, 0, 1);
    }

    private static WorkstationReservationRequest handler() {
        String employee = "butchercraft:employee/2";
        String assignment = "butchercraft:assignment/1";
        String transfer = "butchercraft:transfer/1";
        WorkstationReservationEndpointScope scope = WorkstationReservationEndpointScope.destination(
                "butchercraft:grinder|minecraft:overworld|1|1|1");
        String request = WorkstationReservationRequest.canonicalRequestIdentity(
                WORLD, instance(), employee, WorkstationReservationRole.MATERIAL_HANDLER,
                Optional.of(assignment), Optional.of(transfer), scope);
        return WorkstationReservationRequest.materialHandler(
                WORLD, request, instance(), 3L, "grinder", employee, assignment, transfer, scope,
                "in_transit", 4L, 11L, "minecraft:overworld", 1, 1, 1, 1, 1, 0, 1);
    }

    private static String instance() {
        return "butchercraft:workstation_instance/v1/" + "a".repeat(64);
    }

    private static String legacyJson() {
        return """
                {
                  "schema_version": 1,
                  "reservations": [{
                    "schema_version": 1,
                    "workstation_identity": "butchercraft:workstation/grinder/minecraft/overworld/1/1/1",
                    "workstation_type": "grinder",
                    "employee_identity": "butchercraft:employee/1",
                    "state": "employee_en_route",
                    "created_tick": 10,
                    "dimension_identity": "minecraft:overworld",
                    "workstation_x": 1,
                    "workstation_y": 1,
                    "workstation_z": 1,
                    "operating_x": 1,
                    "operating_y": 1,
                    "operating_z": 0,
                    "anchor_radius": 1
                  }]
                }
                """;
    }
}
