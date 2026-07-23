package com.butchercraft.world;

import com.butchercraft.world.economy.actor.EconomicActorManager;
import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.inventory.InventorySchema;
import com.butchercraft.world.inventory.InventoryStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class InventoryService {
    public static final InventoryService INSTANCE = new InventoryService(EconomicActorService.INSTANCE);

    private final EconomicActorService economicActorService;
    private final AtomicReference<ActiveInventory> activeInventory = new AtomicReference<>();

    public InventoryService(EconomicActorService economicActorService) {
        this.economicActorService = Objects.requireNonNull(economicActorService, "economicActorService");
    }

    public void initialize(ServerStartedEvent event) {
        managerFor(event.getServer());
    }

    public void save(ServerStoppingEvent event) {
        ActiveInventory active = activeInventory.get();
        if (active != null && active.server() == event.getServer()) {
            active.storage().save(active.manager());
            activeInventory.compareAndSet(active, null);
        }
    }

    public InventoryManager managerFor(MinecraftServer server) {
        return load(server).manager();
    }

    public Optional<InventoryManager> currentManager() {
        return Optional.ofNullable(activeInventory.get()).map(ActiveInventory::manager);
    }

    private ActiveInventory load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveInventory existing = activeInventory.get();
        if (existing != null && existing.server() == server) {
            return existing;
        }
        if (existing != null) {
            existing.storage().save(existing.manager());
        }

        EconomicActorManager actorManager = economicActorService.managerFor(server);
        EconomicActorRegistry actorRegistry = actorManager.registry();
        Path file = inventoryFile(server).toAbsolutePath().normalize();
        InventoryStorage storage = new InventoryStorage(file, actorRegistry.goodRegistry(), actorRegistry);
        InventoryManager manager = storage.load();
        manager.validate();

        ActiveInventory created = new ActiveInventory(server, storage, manager);
        activeInventory.set(created);
        return created;
    }

    public static Path inventoryFile(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(InventorySchema.DIRECTORY_NAME)
                .resolve(InventorySchema.FILE_NAME);
    }

    private record ActiveInventory(
            MinecraftServer server,
            InventoryStorage storage,
            InventoryManager manager
    ) {
    }
}
