package com.butchercraft.world.execution;

import com.butchercraft.world.execution.persistence.MachineRunStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineRunPersistenceTest {
    private static final String WORLD_IDENTITY = "butchercraft:world_identity/test";
    private static final String INSTANCE_ID = "butchercraft:workstation_instance/v1/" + "b".repeat(64);

    @TempDir
    Path temporaryDirectory;

    @Test
    void strictRoundTripPreservesRunAndMonotonicGeneration() {
        MachineRunConfiguration configuration = MachineRunConfiguration.standard();
        MachineRunRegistry registry = MachineRunRegistry.empty(WORLD_IDENTITY, configuration);
        MachineStartAuthorizationEvidence start = MachineStartAuthorizationEvidence.issued(
                WORLD_IDENTITY,
                INSTANCE_ID,
                0L,
                "butchercraft:test",
                "butchercraft:machine_start_request/one",
                "butchercraft:machine_operating_policy/test",
                configuration.configurationIdentity(),
                10L
        );
        MachineRunRegistryMutation mutation = registry.acceptStart(start, configuration, 10L);
        assertTrue(mutation.accepted());
        MachineRunStorage storage = new MachineRunStorage(temporaryDirectory.resolve(MachineRunSchema.FILE_NAME));

        storage.save(mutation.registry());

        MachineRunRegistry loaded = storage.loadExisting().orElseThrow();
        assertEquals(mutation.registry(), loaded);
        assertEquals(2L, loaded.generationAllocators().getFirst().nextGeneration());
        MachineRunRegistryMutation duplicate = loaded.acceptStart(start, configuration, 11L);
        assertEquals(MachineRunResultCode.EXISTING_RUN, duplicate.code());
        assertEquals(loaded, duplicate.registry());
    }

    @Test
    void interruptedPublicationAndUnsupportedSchemaFailVisibly() throws Exception {
        Path file = temporaryDirectory.resolve(MachineRunSchema.FILE_NAME);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, "partial");
        MachineRunStorage interruptedStorage = new MachineRunStorage(file);
        assertThrows(IllegalStateException.class, interruptedStorage::loadExisting);

        Files.delete(temporary);
        MachineRunStorage storage = new MachineRunStorage(file);
        storage.save(MachineRunRegistry.empty(WORLD_IDENTITY, MachineRunConfiguration.standard()));
        String invalid = Files.readString(file).replaceFirst(
                "\\\"schema_version\\\": 1",
                "\\\"schema_version\\\": 99"
        );
        Files.writeString(file, invalid);

        assertThrows(IllegalArgumentException.class, storage::loadExisting);
    }
}
