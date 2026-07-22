package com.butchercraft.world;

import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import com.butchercraft.world.persistence.WorldIdentitySavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldIdentityService {
    public static final WorldIdentityService INSTANCE = new WorldIdentityService(new WorldIdentityGenerator());

    private final WorldIdentityGenerator generator;
    private final AtomicReference<WorldIdentity> currentIdentity = new AtomicReference<>();

    public WorldIdentityService(WorldIdentityGenerator generator) {
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    public void initialize(ServerStartedEvent event) {
        getOrCreate(event.getServer());
    }

    public WorldIdentity getOrCreate(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return getOrCreate(server.overworld());
    }

    public WorldIdentity getOrCreate(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        ServerLevel overworld = level.getServer().overworld();
        WorldIdentitySavedData savedData = WorldIdentitySavedData.getOrCreate(overworld, generator);
        currentIdentity.set(savedData.identity());
        return savedData.identity();
    }

    WorldIdentity getOrCreate(long worldSeed, WorldIdentityRepository repository) {
        Objects.requireNonNull(repository, "repository");
        WorldIdentity identity = repository.load().orElseGet(() -> {
            WorldIdentity generated = generator.generate(worldSeed);
            repository.save(generated);
            return generated;
        });
        currentIdentity.set(identity);
        return identity;
    }

    public Optional<WorldIdentity> currentIdentity() {
        return Optional.ofNullable(currentIdentity.get());
    }
}
