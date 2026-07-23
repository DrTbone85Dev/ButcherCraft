package com.butchercraft.world.transaction;

import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.inventory.InventoryTestFixtures;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionStorageTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storageRoundTripsDeterministicHistoryStatusesAndMetadata() throws Exception {
        InventoryManager inventory = TransactionTestFixtures.manager();
        TransactionManager manager = new TransactionManager(inventory);
        EconomicTransaction transaction = new EconomicTransaction(
                TransactionId.of("test:persisted"),
                TransactionType.INVENTORY_ADD,
                java.util.Optional.empty(),
                java.util.Optional.of(InventoryTestFixtures.WAREHOUSE_ACTOR),
                java.util.Optional.empty(),
                java.util.Optional.of(InventoryTestFixtures.BEEF_INVENTORY),
                InventoryTestFixtures.BEEF,
                5L,
                com.butchercraft.world.goods.UnitOfMeasure.POUND,
                26L,
                TransactionStatus.PENDING,
                TransactionMetadata.builder()
                        .reason("Receipt")
                        .referenceId("PO-100")
                        .user("operator")
                        .externalSystem("test")
                        .comments("Accepted delivery")
                        .build(),
                TransactionSchema.CURRENT_VERSION
        );
        assertTrue(manager.submit(transaction).success());
        TransactionStorage storage = new TransactionStorage(
                temporaryDirectory.resolve("butchercraft/transactions.json"),
                inventory
        );

        storage.save(manager);
        String json = Files.readString(storage.filePath());
        TransactionManager loaded = storage.load();

        assertEquals(manager.history(), loaded.history());
        assertEquals(json, storage.serialize(loaded));
        assertTrue(json.contains("\"source_actor_id\": null"));
        assertTrue(json.contains("\"status\": \"applied\""));
        assertTrue(json.contains("\"reference_id\": \"PO-100\""));
    }

    @Test
    void missingFileLoadsEmptyHistory() {
        TransactionStorage storage = new TransactionStorage(
                temporaryDirectory.resolve("missing.json"),
                TransactionTestFixtures.manager()
        );
        assertEquals(0, storage.load().size());
    }

    @Test
    void storageRejectsMalformedUnsupportedDuplicateAndUnknownRecords() {
        InventoryManager inventory = TransactionTestFixtures.manager();
        TransactionManager manager = new TransactionManager(inventory);
        assertTrue(manager.submit(TransactionTestFixtures.beefTransaction(
                "test:stored", TransactionType.INVENTORY_ADD, 1L, 26L
        )).success());
        TransactionStorage storage = new TransactionStorage(temporaryDirectory.resolve("transactions.json"), inventory);
        String valid = storage.serialize(manager);

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize("{"));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                valid.replaceFirst("\"schema_version\": 1", "\"schema_version\": 2")
        ));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                valid.replace("\"test:beef_inventory\"", "\"test:missing_inventory\"")
        ));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                valid.replace("\"unit\": \"pound\"", "\"unit\": \"each\"")
        ));

        JsonObject invalidRecordSchema = JsonParser.parseString(valid).getAsJsonObject();
        invalidRecordSchema.getAsJsonArray("transactions").get(0).getAsJsonObject()
                .addProperty("schema_version", 2);
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(invalidRecordSchema.toString()));

        JsonObject invalidStatus = JsonParser.parseString(valid).getAsJsonObject();
        invalidStatus.getAsJsonArray("transactions").get(0).getAsJsonObject()
                .addProperty("status", "invalid");
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(invalidStatus.toString()));

        JsonObject negativeQuantity = JsonParser.parseString(valid).getAsJsonObject();
        negativeQuantity.getAsJsonArray("transactions").get(0).getAsJsonObject()
                .addProperty("quantity", -1);
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(negativeQuantity.toString()));

        JsonObject missingMetadata = JsonParser.parseString(valid).getAsJsonObject();
        missingMetadata.getAsJsonArray("transactions").get(0).getAsJsonObject().remove("metadata");
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(missingMetadata.toString()));

        JsonObject unknownGood = JsonParser.parseString(valid).getAsJsonObject();
        unknownGood.getAsJsonArray("transactions").get(0).getAsJsonObject()
                .addProperty("good_id", "test:missing_good");
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(unknownGood.toString()));

        JsonObject duplicateRoot = JsonParser.parseString(valid).getAsJsonObject();
        JsonArray transactions = duplicateRoot.getAsJsonArray("transactions");
        transactions.add(transactions.get(0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(duplicateRoot.toString()));
    }
}
