package com.butchercraft.workstation.operation;

import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.operation.persistence.MachineOperatingStorage;
import com.butchercraft.world.execution.MachineRunConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineOperatingPersistenceTest {
    private static final String WORLD_IDENTITY = "butchercraft:world_identity/test";

    @TempDir
    Path temporaryDirectory;

    @Test
    void strictRoundTripPreservesPreparedStartEvidence() {
        MachineOperatingConfiguration configuration = MachineOperatingConfiguration.standard();
        MachineOperatingRegistry registry = MachineOperatingRegistry.empty(WORLD_IDENTITY, configuration);
        MachineOperatingMutation mutation = registry.prepareStart(
                workstation(),
                MachineOperatingPolicy.poweredContinuousExplicitStop(),
                0L,
                "butchercraft:test",
                "butchercraft:machine_start_request/one",
                MachineRunConfiguration.standard().configurationIdentity(),
                10L,
                configuration
        );
        assertTrue(mutation.accepted());
        MachineOperatingStorage storage = new MachineOperatingStorage(
                temporaryDirectory.resolve(MachineOperatingSchema.FILE_NAME)
        );

        storage.save(mutation.registry());

        assertEquals(mutation.registry(), storage.loadExisting().orElseThrow());
        assertTrue(storage.loadExisting().orElseThrow().records().getFirst()
                .startAuthorizationIdentity().isPresent());
    }

    @Test
    void unsupportedSchemaFailsVisibly() throws Exception {
        MachineOperatingStorage storage = new MachineOperatingStorage(
                temporaryDirectory.resolve(MachineOperatingSchema.FILE_NAME)
        );
        storage.save(MachineOperatingRegistry.empty(
                WORLD_IDENTITY,
                MachineOperatingConfiguration.standard()
        ));
        String invalid = Files.readString(storage.filePath()).replaceFirst(
                "\\\"schema_version\\\": 1",
                "\\\"schema_version\\\": 99"
        );
        Files.writeString(storage.filePath(), invalid);

        assertThrows(IllegalArgumentException.class, storage::loadExisting);
    }

    private static MachineWorkstationReference workstation() {
        return new MachineWorkstationReference(
                new WorkstationInstanceId("butchercraft:workstation_instance/v1/" + "a".repeat(64)),
                new WorkstationEndpointKey(
                        "butchercraft:grinder",
                        "minecraft:overworld",
                        1,
                        64,
                        2
                ),
                1L,
                "butchercraft:workstation_instance_allocation/schema_1"
        );
    }
}
