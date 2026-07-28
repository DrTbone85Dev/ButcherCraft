package com.butchercraft.world.workforce.employee;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmployeeStorageTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void missingFileLoadsEmptyDirectory() {
        EmployeeStorage storage = new EmployeeStorage(temporaryDirectory.resolve("employee_records.json"));

        EmployeeDirectory directory = storage.load();

        assertEquals(0L, directory.nextSequence());
        assertEquals(0, directory.registry().size());
    }

    @Test
    void storageSavesAndLoadsEmployeeRecords() {
        EmployeeManager manager = EmployeeTestFixtures.manager();
        EmployeeTestFixtures.employee(manager);
        EmployeeStorage storage = new EmployeeStorage(temporaryDirectory.resolve("employee_records.json"));

        storage.save(manager.directory());
        EmployeeDirectory loaded = storage.load();

        assertEquals(manager.directory().nextSequence(), loaded.nextSequence());
        assertEquals(manager.directory().registry().records(), loaded.registry().records());
    }

    @Test
    void serializationPreservesDeterministicOrdering() {
        EmployeeManager manager = EmployeeTestFixtures.manager();
        manager.createEmployee(
                com.butchercraft.world.identity.WorldIdentityRootIdentities.from(EmployeeTestFixtures.WORLD_IDENTITY),
                EmployeeTestFixtures.BUSINESS,
                java.util.Optional.of("Beta Worker"),
                java.util.Optional.of(EmployeeTestFixtures.dayShift()),
                java.util.Optional.empty(),
                EmployeeTestFixtures.calendar(0L, 7, 0),
                "butchercraft:employee_creation/test",
                EmployeeTestFixtures.BUSINESS_RUNTIME.identity().value()
        );
        manager.createEmployee(
                com.butchercraft.world.identity.WorldIdentityRootIdentities.from(EmployeeTestFixtures.WORLD_IDENTITY),
                EmployeeTestFixtures.BUSINESS,
                java.util.Optional.of("Alpha Worker"),
                java.util.Optional.of(EmployeeTestFixtures.dayShift()),
                java.util.Optional.empty(),
                EmployeeTestFixtures.calendar(0L, 7, 0),
                "butchercraft:employee_creation/test",
                EmployeeTestFixtures.BUSINESS_RUNTIME.identity().value()
        );
        EmployeeStorage storage = new EmployeeStorage(temporaryDirectory.resolve("employee_records.json"));

        String json = storage.serialize(manager.directory());

        EmployeeDirectory loaded = storage.deserialize(json);

        assertEquals(manager.directory().registry().records(), loaded.registry().records());
        assertEquals(json, storage.serialize(loaded));
    }

    @Test
    void storageRejectsUnsupportedSchemaAndMalformedJson() {
        EmployeeStorage storage = new EmployeeStorage(temporaryDirectory.resolve("employee_records.json"));

        assertThrows(IllegalArgumentException.class, () ->
                storage.deserialize("{\"schema_version\":999,\"next_sequence\":0,\"employee_records\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize("{not json"));
    }

    @Test
    void directoryRejectsSequenceThatWouldReuseEmployeeIdentity() {
        EmployeeManager manager = EmployeeTestFixtures.manager();
        EmployeeRecord record = EmployeeTestFixtures.employee(manager);

        assertThrows(IllegalArgumentException.class, () ->
                new EmployeeDirectory(record.sequence(), manager.registry()));
    }
}
