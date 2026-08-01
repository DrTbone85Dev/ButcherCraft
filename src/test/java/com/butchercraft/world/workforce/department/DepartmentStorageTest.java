package com.butchercraft.world.workforce.department;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepartmentStorageTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void missingFileLoadsBuiltInDepartmentDirectory() {
        DepartmentStorage storage = new DepartmentStorage(temporaryDirectory.resolve("departments.json"));

        DepartmentDirectory directory = storage.load(DepartmentTestFixtures.WORLD_ROOT);

        assertEquals(5, directory.registry().records().size());
        assertTrue(directory.registry().find(DepartmentSchema.PROCESSING).orElseThrow().anchor().isPresent());
    }

    @Test
    void storageSavesAndLoadsDepartmentDirectory() {
        DepartmentManager manager = new DepartmentManager(DepartmentTestFixtures.defaults());
        manager.assignAnchor(DepartmentSchema.PACKAGING,
                new DepartmentAnchor("minecraft:overworld", 4, 65, 5, 3));
        DepartmentStorage storage = new DepartmentStorage(temporaryDirectory.resolve("departments.json"));

        storage.save(manager.directory());
        DepartmentDirectory loaded = storage.load(DepartmentTestFixtures.WORLD_ROOT);

        assertEquals(manager.directory().registry().records(), loaded.registry().records());
        assertEquals(manager.directory().plantEntranceAnchor(), loaded.plantEntranceAnchor());
    }

    @Test
    void serializationPreservesDeterministicOrdering() {
        DepartmentStorage storage = new DepartmentStorage(temporaryDirectory.resolve("departments.json"));
        DepartmentDirectory directory = new DepartmentDirectory(DepartmentRegistry.of(java.util.List.of(
                DepartmentTestFixtures.record(DepartmentSchema.SHIPPING),
                DepartmentTestFixtures.record(DepartmentSchema.PROCESSING),
                DepartmentTestFixtures.record(DepartmentSchema.PACKAGING)
        )), java.util.Optional.empty());

        String json = storage.serialize(directory);
        DepartmentDirectory loaded = storage.deserialize(json);

        assertEquals(directory.registry().records(), loaded.registry().records());
        assertEquals(json, storage.serialize(loaded));
    }

    @Test
    void storageRejectsUnsupportedSchemaAndMalformedJson() {
        DepartmentStorage storage = new DepartmentStorage(temporaryDirectory.resolve("departments.json"));

        assertThrows(IllegalArgumentException.class, () ->
                storage.deserialize("{\"schema_version\":999,\"departments\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize("{not json"));
    }
}
