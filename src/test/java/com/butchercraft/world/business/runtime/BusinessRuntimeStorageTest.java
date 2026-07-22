package com.butchercraft.world.business.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.allDays;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.shift;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.state;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessRuntimeStorageTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void storageSavesAndLoadsRuntimeRegistry() {
        BusinessRuntimeRegistry registry = BusinessRuntimeRegistry.of(List.of(
                state("beta_market", allDays(8, 17), List.of(shift("day", 8, 17, 2)), 4),
                state("alpha_market", allDays(6, 18), List.of(shift("day", 6, 18, 3)), 6)
        ));
        BusinessRuntimeStorage storage = new BusinessRuntimeStorage(temporaryDirectory.resolve("business_runtime.json"));

        storage.save(registry);
        BusinessRuntimeRegistry loaded = storage.load();

        assertEquals(registry.states(), loaded.states());
    }

    @Test
    void serializationPreservesDeterministicOrdering() {
        BusinessRuntimeRegistry registry = BusinessRuntimeRegistry.of(List.of(
                state("zeta_market", allDays(8, 17), List.of(shift("day", 8, 17, 2)), 4),
                state("alpha_market", allDays(8, 17), List.of(shift("day", 8, 17, 2)), 4)
        ));
        BusinessRuntimeStorage storage = new BusinessRuntimeStorage(temporaryDirectory.resolve("business_runtime.json"));

        String json = storage.serialize(registry);

        assertTrue(json.indexOf("alpha_market") < json.indexOf("zeta_market"));
        assertEquals(registry.states(), storage.deserialize(json).states());
    }

    @Test
    void storageRejectsUnsupportedSchemaVersionsAndCorruptJson() {
        BusinessRuntimeStorage storage = new BusinessRuntimeStorage(temporaryDirectory.resolve("business_runtime.json"));

        assertThrows(IllegalArgumentException.class, () ->
                storage.deserialize("{\"schema_version\":999,\"business_runtime_states\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize("{not json"));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize("""
                {
                  "schema_version": 1,
                  "business_runtime_states": [
                    {
                      "schema_version": 999,
                      "business_id": "alpha_market",
                      "operational_status": "closed",
                      "open": false,
                      "active_shift_id": null,
                      "workforce_capacity": 4,
                      "active_workforce": 0,
                      "maintenance": false,
                      "last_state_change_simulation_tick": 0,
                      "business_hours": {
                        "opening_hour": 8,
                        "opening_minute": 0,
                        "closing_hour": 17,
                        "closing_minute": 0,
                        "operating_weekdays": [1, 2, 3, 4, 5]
                      },
                      "shifts": []
                    }
                  ]
                }
                """));
    }

    @Test
    void missingFileLoadsEmptyRegistryForMigrationReadiness() {
        BusinessRuntimeStorage storage = new BusinessRuntimeStorage(temporaryDirectory.resolve("missing.json"));

        assertEquals(0, storage.load().size());
    }
}
