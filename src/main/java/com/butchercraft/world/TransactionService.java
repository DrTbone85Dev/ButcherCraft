package com.butchercraft.world;

import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.transaction.TransactionManager;
import com.butchercraft.world.transaction.TransactionSchema;
import com.butchercraft.world.transaction.TransactionStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class TransactionService {
    public static final TransactionService INSTANCE = new TransactionService(InventoryService.INSTANCE);

    private final InventoryService inventoryService;
    private final AtomicReference<ActiveTransactions> activeTransactions = new AtomicReference<>();

    public TransactionService(InventoryService inventoryService) {
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
    }

    public void initialize(ServerStartedEvent event) {
        managerFor(event.getServer());
    }

    public void save(ServerStoppingEvent event) {
        ActiveTransactions active = activeTransactions.get();
        if (active != null && active.server() == event.getServer()) {
            active.storage().save(active.manager());
            activeTransactions.compareAndSet(active, null);
        }
    }

    public TransactionManager managerFor(MinecraftServer server) {
        return load(server).manager();
    }

    public Optional<TransactionManager> currentManager() {
        return Optional.ofNullable(activeTransactions.get()).map(ActiveTransactions::manager);
    }

    private ActiveTransactions load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveTransactions existing = activeTransactions.get();
        if (existing != null && existing.server() == server) {
            return existing;
        }
        if (existing != null) {
            existing.storage().save(existing.manager());
        }

        InventoryManager inventoryManager = inventoryService.managerFor(server);
        Path file = transactionFile(server).toAbsolutePath().normalize();
        TransactionStorage storage = new TransactionStorage(file, inventoryManager);
        TransactionManager manager = storage.load();
        ActiveTransactions created = new ActiveTransactions(server, storage, manager);
        activeTransactions.set(created);
        return created;
    }

    public static Path transactionFile(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(TransactionSchema.DIRECTORY_NAME)
                .resolve(TransactionSchema.FILE_NAME);
    }

    private record ActiveTransactions(
            MinecraftServer server,
            TransactionStorage storage,
            TransactionManager manager
    ) {
    }
}
