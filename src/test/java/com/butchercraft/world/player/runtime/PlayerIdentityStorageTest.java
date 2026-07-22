package com.butchercraft.world.player.runtime;

import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerIdentityStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void saveLoadRoundTripPreservesMultiplePlayers() {
        WorldIdentity worldIdentity = new WorldIdentityGenerator().generate(8001L);
        PlayerIdentityRegistry registry = registry(worldIdentity, "storage-a", "storage-b");
        PlayerIdentityStorage storage = new PlayerIdentityStorage(tempDir.resolve("players.json"));

        storage.save(registry);
        PlayerIdentityRegistry restored = storage.load();

        assertEquals(registry.identities(), restored.identities());
    }

    @Test
    void serializationIsDeterministic() {
        WorldIdentity worldIdentity = new WorldIdentityGenerator().generate(8002L);
        PlayerIdentityRegistry registry = registry(worldIdentity, "deterministic-b", "deterministic-a");
        PlayerIdentityStorage storage = new PlayerIdentityStorage(tempDir.resolve("deterministic.json"));

        String first = storage.serialize(registry);
        String second = storage.serialize(PlayerIdentityRegistry.of(registry.identities().reversed()));

        assertEquals(first, second);
        assertTrue(first.contains("\"schema_version\": 1"));
        assertTrue(first.contains("\"player_identities\""));
    }

    @Test
    void emptyRegistryRoundTrips() {
        PlayerIdentityStorage storage = new PlayerIdentityStorage(tempDir.resolve("empty.json"));

        PlayerIdentityRegistry restored = storage.deserialize(storage.serialize(PlayerIdentityRegistry.empty()));

        assertEquals(0, restored.size());
    }

    @Test
    void corruptPersistenceIsRejected() throws Exception {
        Path file = tempDir.resolve("corrupt.json");
        Files.writeString(file, "{not json", StandardCharsets.UTF_8);

        PlayerIdentityStorage storage = new PlayerIdentityStorage(file);

        assertThrows(IllegalArgumentException.class, storage::load);
    }

    @Test
    void unknownRootSchemaIsRejectedForFutureMigrationSafety() {
        PlayerIdentityStorage storage = new PlayerIdentityStorage(tempDir.resolve("future.json"));
        String futureSchema = """
                {
                  "schema_version": 2,
                  "player_identities": []
                }
                """;

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(futureSchema));
    }

    @Test
    void unknownRecordSchemaIsRejectedForFutureMigrationSafety() {
        WorldIdentity worldIdentity = new WorldIdentityGenerator().generate(8003L);
        PlayerIdentityStorage storage = new PlayerIdentityStorage(tempDir.resolve("future-record.json"));
        String json = storage.serialize(registry(worldIdentity, "future-record"));
        String marker = "\"schema_version\": 1";
        int rootSchema = json.indexOf(marker);
        int recordSchema = json.indexOf(marker, rootSchema + marker.length());
        String futureRecord = json.substring(0, recordSchema) + "\"schema_version\": 2"
                + json.substring(recordSchema + marker.length());

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(futureRecord));
    }

    private PlayerIdentityRegistry registry(WorldIdentity worldIdentity, String... playerNames) {
        PlayerIdentityFactory factory = new PlayerIdentityFactory();
        List<PlayerIdentityRecord> identities = List.of(playerNames).stream()
                .map(name -> factory.createFirstTimeIdentity(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), worldIdentity))
                .toList();
        return PlayerIdentityRegistry.of(identities);
    }
}
