package com.butchercraft.world;

import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.economy.order.ContractManager;
import com.butchercraft.world.economy.order.OrderContractSchema;
import com.butchercraft.world.economy.order.OrderManager;
import com.butchercraft.world.economy.order.persistence.ContractStorage;
import com.butchercraft.world.economy.order.persistence.OrderStorage;
import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.transaction.TransactionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class OrderContractService {
    public static final OrderContractService INSTANCE = new OrderContractService(
            EconomicActorService.INSTANCE, InventoryService.INSTANCE, TransactionService.INSTANCE
    );

    private final EconomicActorService economicActorService;
    private final InventoryService inventoryService;
    private final TransactionService transactionService;
    private final AtomicReference<ActiveOrderContracts> activeState = new AtomicReference<>();

    public OrderContractService(
            EconomicActorService economicActorService,
            InventoryService inventoryService,
            TransactionService transactionService
    ) {
        this.economicActorService = Objects.requireNonNull(economicActorService, "economicActorService");
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
        this.transactionService = Objects.requireNonNull(transactionService, "transactionService");
    }

    public void initialize(ServerStartedEvent event) {
        load(event.getServer());
    }

    public void save(ServerStoppingEvent event) {
        ActiveOrderContracts active = activeState.get();
        if (active != null && active.server() == event.getServer()) {
            save(active);
            activeState.compareAndSet(active, null);
        }
    }

    public OrderManager orderManagerFor(MinecraftServer server) {
        return load(server).orderManager();
    }

    public ContractManager contractManagerFor(MinecraftServer server) {
        return load(server).contractManager();
    }

    public Optional<OrderManager> currentOrderManager() {
        return Optional.ofNullable(activeState.get()).map(ActiveOrderContracts::orderManager);
    }

    public Optional<ContractManager> currentContractManager() {
        return Optional.ofNullable(activeState.get()).map(ActiveOrderContracts::contractManager);
    }

    private ActiveOrderContracts load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveOrderContracts existing = activeState.get();
        if (existing != null && existing.server() == server) return existing;
        if (existing != null) save(existing);

        EconomicActorRegistry actorRegistry = economicActorService.managerFor(server).registry();
        InventoryManager inventoryManager = inventoryService.managerFor(server);
        TransactionManager transactionManager = transactionService.managerFor(server);
        ContractStorage contractStorage = new ContractStorage(contractFile(server), actorRegistry);
        ContractManager contractManager = contractStorage.load();
        OrderStorage orderStorage = new OrderStorage(
                orderFile(server), actorRegistry, inventoryManager.registry(), transactionManager, contractManager
        );
        OrderManager orderManager = orderStorage.load();

        ActiveOrderContracts created = new ActiveOrderContracts(
                server, orderStorage, contractStorage, orderManager, contractManager
        );
        activeState.set(created);
        return created;
    }

    private static void save(ActiveOrderContracts active) {
        active.orderStorage().save(active.orderManager());
        active.contractStorage().save(active.contractManager());
    }

    public static Path orderFile(MinecraftServer server) {
        return rootDirectory(server).resolve(OrderContractSchema.ORDERS_FILE_NAME);
    }

    public static Path contractFile(MinecraftServer server) {
        return rootDirectory(server).resolve(OrderContractSchema.CONTRACTS_FILE_NAME);
    }

    private static Path rootDirectory(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve(OrderContractSchema.DIRECTORY_NAME).toAbsolutePath().normalize();
    }

    private record ActiveOrderContracts(
            MinecraftServer server,
            OrderStorage orderStorage,
            ContractStorage contractStorage,
            OrderManager orderManager,
            ContractManager contractManager
    ) { }
}
