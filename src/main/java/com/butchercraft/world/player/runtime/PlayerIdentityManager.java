package com.butchercraft.world.player.runtime;

import com.butchercraft.world.identity.WorldIdentity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PlayerIdentityManager {
    private final PlayerIdentityStorage storage;
    private final PlayerIdentityFactory factory;
    private PlayerIdentityRegistry registry;

    public PlayerIdentityManager(PlayerIdentityStorage storage, PlayerIdentityFactory factory) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public synchronized PlayerIdentityRecord getOrCreate(UUID minecraftUuid, WorldIdentity worldIdentity) {
        Objects.requireNonNull(minecraftUuid, "minecraftUuid");
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        ensureLoaded(worldIdentity);
        Optional<PlayerIdentityRecord> existing = registry.find(minecraftUuid);
        if (existing.isPresent()) {
            return existing.get();
        }
        PlayerIdentityRecord created = factory.createFirstTimeIdentity(minecraftUuid, worldIdentity);
        registry = registry.with(created, worldIdentity, factory.startingScenarioRegistry());
        storage.save(registry);
        return created;
    }

    public synchronized PlayerIdentityRegistry loadExisting(WorldIdentity worldIdentity) {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        registry = storage.load();
        registry.validateReferences(worldIdentity, factory.startingScenarioRegistry());
        return registry;
    }

    public synchronized PlayerIdentityRegistry registry(WorldIdentity worldIdentity) {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        ensureLoaded(worldIdentity);
        return registry;
    }

    public synchronized void save() {
        storage.save(registry == null ? PlayerIdentityRegistry.empty() : registry);
    }

    private void ensureLoaded(WorldIdentity worldIdentity) {
        if (registry == null) {
            loadExisting(worldIdentity);
        }
    }
}
