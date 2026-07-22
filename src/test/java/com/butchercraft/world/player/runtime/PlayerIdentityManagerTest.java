package com.butchercraft.world.player.runtime;

import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerIdentityManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void firstJoinCreatesIdentityAndSecondJoinReusesIt() {
        WorldIdentity worldIdentity = new WorldIdentityGenerator().generate(7001L);
        Path file = tempDir.resolve("butchercraft").resolve("player_identities.json");
        PlayerIdentityManager manager = manager(file);
        UUID uuid = UUID.nameUUIDFromBytes("first-join-player".getBytes(StandardCharsets.UTF_8));

        PlayerIdentityRecord created = manager.getOrCreate(uuid, worldIdentity);
        PlayerIdentityRecord loaded = manager.getOrCreate(uuid, worldIdentity);

        assertEquals(created, loaded);
        assertEquals(1, manager.registry(worldIdentity).size());
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void identityPersistsAfterManagerReload() {
        WorldIdentity worldIdentity = new WorldIdentityGenerator().generate(7002L);
        Path file = tempDir.resolve(PlayerIdentityRuntimeSchema.FILE_NAME);
        UUID uuid = UUID.nameUUIDFromBytes("persisted-player".getBytes(StandardCharsets.UTF_8));

        PlayerIdentityRecord created = manager(file).getOrCreate(uuid, worldIdentity);
        PlayerIdentityRecord restored = manager(file).getOrCreate(uuid, worldIdentity);

        assertEquals(created, restored);
    }

    @Test
    void emptyStorageLoadsAsEmptyRegistry() {
        WorldIdentity worldIdentity = new WorldIdentityGenerator().generate(7003L);

        PlayerIdentityRegistry registry = manager(tempDir.resolve("empty.json")).loadExisting(worldIdentity);

        assertEquals(0, registry.size());
    }

    @Test
    void oneHundredSimulatedPlayerJoinsRemainUniqueAndRaceFree() throws Exception {
        WorldIdentity worldIdentity = new WorldIdentityGenerator().generate(7004L);
        PlayerIdentityManager manager = manager(tempDir.resolve("multiplayer.json"));
        List<UUID> uuids = IntStream.range(0, 100)
                .mapToObj(index -> UUID.nameUUIDFromBytes(("player-" + index).getBytes(StandardCharsets.UTF_8)))
                .toList();
        List<Callable<PlayerIdentityRecord>> tasks = uuids.stream()
                .<Callable<PlayerIdentityRecord>>map(uuid -> () -> manager.getOrCreate(uuid, worldIdentity))
                .toList();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<PlayerIdentityRecord> identities = executor.invokeAll(tasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    })
                    .toList();

            Set<UUID> uniqueUuids = identities.stream().map(PlayerIdentityRecord::minecraftUuid).collect(Collectors.toSet());
            Set<String> uniqueIdentityIds = identities.stream()
                    .map(identity -> identity.identityId().value())
                    .collect(Collectors.toSet());

            assertEquals(100, manager.registry(worldIdentity).size());
            assertEquals(100, uniqueUuids.size());
            assertEquals(100, uniqueIdentityIds.size());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private PlayerIdentityManager manager(Path path) {
        return new PlayerIdentityManager(new PlayerIdentityStorage(path), new PlayerIdentityFactory());
    }
}
