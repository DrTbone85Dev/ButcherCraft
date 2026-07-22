package com.butchercraft.world.workforce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.butchercraft.world.workforce.WorkforceTestFixtures.definition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkforceStorageTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void storageSavesAndLoadsDefinitions() {
        WorkforceRegistry registry = WorkforceRegistry.of(List.of(
                definition("beta_market", "primary"),
                definition("alpha_market", "primary")
        ));
        WorkforceStorage storage = new WorkforceStorage(temporaryDirectory.resolve("workforce_definitions.json"));

        storage.save(registry);
        WorkforceRegistry loaded = storage.load();

        assertEquals(registry.definitions(), loaded.definitions());
    }

    @Test
    void serializationPreservesDeterministicOrdering() {
        WorkforceRegistry registry = WorkforceRegistry.of(List.of(
                definition("zeta_market", "primary"),
                definition("alpha_market", "primary")
        ));
        WorkforceStorage storage = new WorkforceStorage(temporaryDirectory.resolve("workforce_definitions.json"));

        String json = storage.serialize(registry);

        assertTrue(json.indexOf("alpha_market") < json.indexOf("zeta_market"));
        assertEquals(registry.definitions(), storage.deserialize(json).definitions());
    }

    @Test
    void storageRejectsUnsupportedSchemaVersionsMalformedJsonAndInvalidEnums() {
        WorkforceStorage storage = new WorkforceStorage(temporaryDirectory.resolve("workforce_definitions.json"));

        assertThrows(IllegalArgumentException.class, () ->
                storage.deserialize("{\"schema_version\":999,\"workforce_definitions\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize("{not json"));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize("""
                {
                  "schema_version": 1,
                  "workforce_definitions": [
                    {
                      "schema_version": 1,
                      "workforce_definition_id": "alpha_market_workforce",
                      "business_id": "alpha_market",
                      "positions": [
                        {
                          "position_id": "day_manager",
                          "position_type": "manager",
                          "display_name": "Day Manager",
                          "required_skill_level": "mystery",
                          "required_certifications": ["food_safety"],
                          "assigned_shift": "day",
                          "required": true,
                          "maximum_workers": 1
                        }
                      ],
                      "shift_assignments": [
                        {
                          "shift_id": "day",
                          "position_id": "day_manager",
                          "minimum_workers": 1,
                          "maximum_workers": 1
                        }
                      ],
                      "staffing_rule": {
                        "required_positions": ["day_manager"],
                        "optional_positions": [],
                        "minimum_staffing": 1,
                        "maximum_staffing": 1
                      }
                    }
                  ]
                }
                """));
    }

    @Test
    void missingFileLoadsEmptyRegistryForMigrationReadiness() {
        WorkforceStorage storage = new WorkforceStorage(temporaryDirectory.resolve("missing.json"));

        assertEquals(0, storage.load().size());
    }
}
