package com.butchercraft.world.economy.actor;

import com.butchercraft.world.goods.BuiltInIndustryCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.butchercraft.world.economy.actor.EconomicActorTestFixtures.goods;
import static com.butchercraft.world.economy.actor.EconomicActorTestFixtures.registry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomicActorStorageTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void storageRoundTripsDefinitionsCapabilitiesAndRelationships() {
        EconomicActorRegistry registry = registry();
        EconomicActorStorage storage = storage("economic_actors.json");

        storage.save(registry);
        EconomicActorRegistry loaded = storage.load();

        assertEquals(registry.definitions(), loaded.definitions());
        assertEquals(registry.relationshipCount(), loaded.relationshipCount());
        assertTrue(Files.exists(temporaryDirectory.resolve("economic_actors.json")));
        assertFalse(Files.exists(temporaryDirectory.resolve("economic_actors.json.tmp")));
        EconomicActorDefinition bakery = loaded.find(ActorId.of("test:bakery")).orElseThrow();
        EconomicActorDefinition warehouse = loaded.find(ActorId.of("test:warehouse")).orElseThrow();
        assertEquals(3, bakery.capabilities().size());
        assertEquals(ActorId.of("test:farm"), bakery.relationships().stream()
                .flatMap(relationship -> relationship.dependsOnActorId().stream())
                .findFirst()
                .orElseThrow());
        assertEquals(2, warehouse.relationships().getFirst().supportedIndustryIds().size());
    }

    @Test
    void serializationIsDeterministicAndExcludesRuntimeState() {
        EconomicActorStorage storage = storage("economic_actors.json");
        String json = storage.serialize(registry());

        assertTrue(json.indexOf("test:bakery") < json.indexOf("test:farm"));
        assertTrue(json.indexOf("test:farm") < json.indexOf("test:warehouse"));
        assertTrue(json.contains("\"schema_version\": 1"));
        assertTrue(json.contains("\"actors\""));
        assertTrue(json.contains("\"actor_type\""));
        assertTrue(json.contains("\"capabilities\""));
        assertTrue(json.contains("\"relationships\""));
        assertTrue(json.contains("\"supported_industry_ids\""));
        assertTrue(json.contains("\"depends_on_actor_id\""));
        assertFalse(json.contains("runtime_status"));
        assertFalse(json.contains("last_simulation_tick"));
        assertEquals(json, storage.serialize(storage.deserialize(json)));
    }

    @Test
    void missingFileLoadsEmptyValidatedRegistry() {
        EconomicActorRegistry loaded = storage("missing.json").load();

        assertEquals(0, loaded.size());
        assertEquals(goods().definitions(), loaded.goodRegistry().definitions());
        assertEquals(BuiltInIndustryCatalog.all(), loaded.knownIndustries());
    }

    @Test
    void storageRejectsMalformedPersistenceAndUnsupportedSchemas() {
        EconomicActorStorage storage = storage("economic_actors.json");

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize("{not json"));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(root(99, "")));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(root(
                1,
                actorObject(99, "test:farm", "butchercraft:agriculture", "produce", "")
        )));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(root(
                1,
                actorObject(1, "test:farm", "butchercraft:agriculture", "produce", "")
                        .replace("\"display_name\": \"Test Actor\",", "")
        )));
    }

    @Test
    void storageRejectsDuplicateActorsRelationshipsAndCapabilities() {
        EconomicActorStorage storage = storage("economic_actors.json");
        String farm = actorObject(1, "test:farm", "butchercraft:agriculture", "produce", "");
        String relationship = relationshipObject(1, "test:grain", "output", null);

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(root(1, farm + "," + farm)));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(root(
                1,
                actorObject(1, "test:farm", "butchercraft:agriculture", "produce,produce", "")
        )));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(root(
                1,
                actorObject(
                        1,
                        "test:farm",
                        "butchercraft:agriculture",
                        "produce",
                        relationship + "," + relationship
                )
        )));
    }

    @Test
    void storageRejectsUnknownTypesCapabilitiesIndustriesGoodsAndDependencies() {
        EconomicActorStorage storage = storage("economic_actors.json");
        String valid = root(1, actorObject(1, "test:farm", "butchercraft:agriculture", "produce", ""));

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                valid.replace("\"actor_type\": \"producer\"", "\"actor_type\": \"unknown\"")
        ));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                valid.replace("\"produce\"", "\"unknown\"")
        ));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                valid.replace("butchercraft:agriculture", "test:unknown")
        ));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(root(
                1,
                actorObject(
                        1,
                        "test:farm",
                        "butchercraft:agriculture",
                        "produce",
                        relationshipObject(1, "test:missing", "output", null)
                )
        )));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(root(
                1,
                actorObject(
                        1,
                        "test:farm",
                        "butchercraft:agriculture",
                        "produce",
                        relationshipObject(1, "test:grain", "output", "test:missing")
                )
        )));
    }

    @Test
    void storageRejectsCircularDependencyChains() {
        EconomicActorStorage storage = storage("economic_actors.json");
        String first = actorObject(
                1,
                "test:first",
                "butchercraft:restaurants",
                "consume",
                relationshipObject(1, "test:grain", "consumed", "test:second")
        );
        String second = actorObject(
                1,
                "test:second",
                "butchercraft:restaurants",
                "consume",
                relationshipObject(1, "test:grain", "consumed", "test:first")
        );

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(root(1, first + "," + second)));
    }

    private EconomicActorStorage storage(String fileName) {
        return new EconomicActorStorage(
                temporaryDirectory.resolve(fileName),
                goods(),
                BuiltInIndustryCatalog.all()
        );
    }

    private static String root(int schemaVersion, String actors) {
        return """
                {
                  "schema_version": %d,
                  "actors": [%s]
                }
                """.formatted(schemaVersion, actors);
    }

    private static String actorObject(
            int schemaVersion,
            String id,
            String industryId,
            String capabilities,
            String relationships
    ) {
        String capabilityJson = java.util.Arrays.stream(capabilities.split(","))
                .filter(capability -> !capability.isEmpty())
                .map(capability -> "\"" + capability + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return """
                {
                  "schema_version": %d,
                  "id": "%s",
                  "display_name": "Test Actor",
                  "actor_type": "producer",
                  "industry_id": "%s",
                  "capabilities": [%s],
                  "relationships": [%s]
                }
                """.formatted(schemaVersion, id, industryId, capabilityJson, relationships);
    }

    private static String relationshipObject(
            int schemaVersion,
            String goodId,
            String goodRole,
            String dependencyActorId
    ) {
        String dependency = dependencyActorId == null
                ? ""
                : ",\n  \"depends_on_actor_id\": \"" + dependencyActorId + "\"";
        return """
                {
                  "schema_version": %d,
                  "good_id": "%s",
                  "good_role": "%s",
                  "supported_industry_ids": []%s
                }
                """.formatted(schemaVersion, goodId, goodRole, dependency);
    }
}
