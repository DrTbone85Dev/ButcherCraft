package com.butchercraft.world.player.runtime;

import com.butchercraft.world.WorldIdentityService;
import com.butchercraft.world.identity.WorldIdentity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerJoinInitializer {
    public static final PlayerJoinInitializer INSTANCE =
            new PlayerJoinInitializer(WorldIdentityService.INSTANCE, new PlayerIdentityFactory());

    private final WorldIdentityService worldIdentityService;
    private final PlayerIdentityFactory factory;
    private final ConcurrentMap<Path, PlayerIdentityManager> managersByFile = new ConcurrentHashMap<>();

    public PlayerJoinInitializer(WorldIdentityService worldIdentityService, PlayerIdentityFactory factory) {
        this.worldIdentityService = Objects.requireNonNull(worldIdentityService, "worldIdentityService");
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public void initialize(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        WorldIdentity worldIdentity = worldIdentityService.getOrCreate(server);
        managerFor(server).getOrCreate(player.getUUID(), worldIdentity);
    }

    public PlayerIdentityManager managerFor(MinecraftServer server) {
        Path file = playerIdentityFile(server).toAbsolutePath().normalize();
        return managersByFile.computeIfAbsent(file, path ->
                new PlayerIdentityManager(new PlayerIdentityStorage(path), factory));
    }

    public static Path playerIdentityFile(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(PlayerIdentityRuntimeSchema.DIRECTORY_NAME)
                .resolve(PlayerIdentityRuntimeSchema.FILE_NAME);
    }
}
