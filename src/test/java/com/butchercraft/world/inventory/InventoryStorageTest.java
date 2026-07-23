package com.butchercraft.world.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.butchercraft.world.inventory.InventoryTestFixtures.GRAIN_INVENTORY;
import static com.butchercraft.world.inventory.InventoryTestFixtures.actors;
import static com.butchercraft.world.inventory.InventoryTestFixtures.manager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryStorageTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void storageRoundTripsDefinitionsRuntimeQuantitiesAndTypedMetadata() {
        InventoryManager source = manager();
        InventoryManager manager = new InventoryManager(
                source.registry(),
                source.runtimes().stream()
                        .map(runtime -> runtime.inventoryId().equals(GRAIN_INVENTORY)
                                ? new InventoryRuntime(
                                        runtime.inventoryId(),
                                        InventoryStatus.LOCKED,
                                        runtime.entries(),
                                        30L,
                                        runtime.schemaVersion()
                                )
                                : runtime)
                        .toList()
        );
        InventoryStorage storage = storage("inventory.json");

        storage.save(manager);
        InventoryManager loaded = storage.load();

        assertEquals(manager.registry().containers(), loaded.registry().containers());
        assertEquals(manager.registry().storageNodes(), loaded.registry().storageNodes());
        assertEquals(10L, loaded.quantityIn(GRAIN_INVENTORY, InventoryTestFixtures.GRAIN));
        InventoryRuntime runtime = loaded.requireRuntime(GRAIN_INVENTORY);
        assertEquals(InventoryStatus.LOCKED, runtime.status());
        assertEquals(30L, runtime.lastSimulationTick());
        InventoryEntryMetadata metadata = runtime.entries().getFirst().metadata();
        assertEquals("LOT-001", metadata.lotNumber().orElseThrow());
        assertEquals(500L, metadata.expirationSimulationTick().orElseThrow());
        assertEquals(9_500, metadata.qualityBasisPoints().orElseThrow());
        assertEquals(InventoryTestFixtures.FARM_ACTOR, metadata.originActorId().orElseThrow());
        assertTrue(Files.exists(temporaryDirectory.resolve("inventory.json")));
        assertFalse(Files.exists(temporaryDirectory.resolve("inventory.json.tmp")));
    }

    @Test
    void serializationIsDeterministicAndUsesStableFields() {
        InventoryStorage storage = storage("inventory.json");
        String json = storage.serialize(manager());

        assertTrue(json.contains("\"schema_version\": 1"));
        assertTrue(json.contains("\"storage_nodes\""));
        assertTrue(json.contains("\"inventory_containers\""));
        assertTrue(json.contains("\"inventory_runtimes\""));
        assertTrue(json.contains("\"maximum_distinct_goods\""));
        assertTrue(json.contains("\"last_simulation_tick\""));
        assertTrue(json.contains("\"origin_actor_id\""));
        assertTrue(json.indexOf("test:cooler") < json.indexOf("test:distribution_center"));
        assertTrue(json.indexOf("test:beef_inventory") < json.indexOf("test:grain_inventory"));
        assertEquals(json, storage.serialize(storage.deserialize(json)));
    }

    @Test
    void missingFileLoadsEmptyRegistryAndRuntimeSet() {
        InventoryManager loaded = storage("missing.json").load();

        assertEquals(0, loaded.registry().size());
        assertEquals(0, loaded.registry().storageNodeCount());
        assertEquals(0L, loaded.entryCount());
    }

    @Test
    void storageRejectsMalformedJsonUnsupportedSchemasAndMissingFields() {
        InventoryStorage storage = storage("inventory.json");
        JsonObject unsupportedRoot = validRoot();
        unsupportedRoot.addProperty("schema_version", 99);
        JsonObject unsupportedContainer = validRoot();
        containers(unsupportedContainer).get(0).getAsJsonObject().addProperty("schema_version", 99);
        JsonObject missingName = validRoot();
        containers(missingName).get(0).getAsJsonObject().remove("display_name");

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize("{not json"));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(unsupportedRoot.toString()));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(unsupportedContainer.toString()));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(missingName.toString()));
    }

    @Test
    void storageRejectsDuplicateContainersNodesAndRuntimes() {
        InventoryStorage storage = storage("inventory.json");
        JsonObject duplicateContainer = validRoot();
        containers(duplicateContainer).add(containers(duplicateContainer).get(0).deepCopy());
        JsonObject duplicateNode = validRoot();
        nodes(duplicateNode).add(nodes(duplicateNode).get(0).deepCopy());
        JsonObject duplicateRuntime = validRoot();
        runtimes(duplicateRuntime).add(runtimes(duplicateRuntime).get(0).deepCopy());
        JsonObject missingRuntime = validRoot();
        runtimes(missingRuntime).remove(0);

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(duplicateContainer.toString()));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(duplicateNode.toString()));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(duplicateRuntime.toString()));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(missingRuntime.toString()));
    }

    @Test
    void storageRejectsUnknownActorsNodesGoodsUnitsAndInvalidQuantities() {
        InventoryStorage storage = storage("inventory.json");
        JsonObject unknownActor = validRoot();
        containers(unknownActor).get(0).getAsJsonObject().addProperty("owner_actor_id", "test:missing");
        JsonObject unknownNode = validRoot();
        containers(unknownNode).get(0).getAsJsonObject().addProperty("storage_node_id", "test:missing");
        JsonObject unknownGood = validRoot();
        firstEntry(unknownGood).addProperty("good_id", "test:missing");
        JsonObject unknownUnit = validRoot();
        firstEntry(unknownUnit).addProperty("unit", "barrel");
        JsonObject negativeQuantity = validRoot();
        firstEntry(negativeQuantity).addProperty("quantity", -1);

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(unknownActor.toString()));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(unknownNode.toString()));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(unknownGood.toString()));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(unknownUnit.toString()));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(negativeQuantity.toString()));
    }

    @Test
    void storageRejectsCapacityViolationsAndCircularHierarchies() {
        InventoryStorage storage = storage("inventory.json");
        JsonObject capacityViolation = validRoot();
        JsonObject grainRuntime = runtime(capacityViolation, "test:grain_inventory");
        grainRuntime.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("quantity", 51);

        JsonObject circular = validRoot();
        node(circular, "test:warehouse").addProperty("parent_node_id", "test:cooler");
        node(circular, "test:cooler").addProperty("parent_node_id", "test:warehouse");

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(capacityViolation.toString()));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(circular.toString()));
    }

    private InventoryStorage storage(String fileName) {
        var actors = actors();
        return new InventoryStorage(temporaryDirectory.resolve(fileName), actors.goodRegistry(), actors);
    }

    private JsonObject validRoot() {
        return JsonParser.parseString(storage("inventory.json").serialize(manager())).getAsJsonObject();
    }

    private static JsonArray containers(JsonObject root) {
        return root.getAsJsonArray("inventory_containers");
    }

    private static JsonArray nodes(JsonObject root) {
        return root.getAsJsonArray("storage_nodes");
    }

    private static JsonArray runtimes(JsonObject root) {
        return root.getAsJsonArray("inventory_runtimes");
    }

    private static JsonObject firstEntry(JsonObject root) {
        return runtimes(root).get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
    }

    private static JsonObject runtime(JsonObject root, String inventoryId) {
        for (var element : runtimes(root)) {
            JsonObject runtime = element.getAsJsonObject();
            if (runtime.get("inventory_id").getAsString().equals(inventoryId)) {
                return runtime;
            }
        }
        throw new IllegalArgumentException("Missing runtime fixture: " + inventoryId);
    }

    private static JsonObject node(JsonObject root, String nodeId) {
        for (var element : nodes(root)) {
            JsonObject node = element.getAsJsonObject();
            if (node.get("id").getAsString().equals(nodeId)) {
                return node;
            }
        }
        throw new IllegalArgumentException("Missing node fixture: " + nodeId);
    }
}
