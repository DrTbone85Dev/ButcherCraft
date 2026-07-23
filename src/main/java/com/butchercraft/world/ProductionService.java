package com.butchercraft.world;

import com.butchercraft.world.production.ProductionDependencies;
import com.butchercraft.world.production.ProductionManager;
import com.butchercraft.world.production.ProductionSchema;
import com.butchercraft.world.production.persistence.ProductionStorage;
import com.butchercraft.world.production.scheduler.ProductionSimulationWorkHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class ProductionService {
    public static final ProductionService INSTANCE = new ProductionService(
            GoodService.INSTANCE,
            EconomicActorService.INSTANCE,
            BusinessRuntimeService.INSTANCE,
            WorkforceService.INSTANCE,
            InventoryService.INSTANCE,
            TransactionService.INSTANCE,
            OrderContractService.INSTANCE,
            SimulationSchedulerService.INSTANCE
    );

    private final GoodService goodService;
    private final EconomicActorService actorService;
    private final BusinessRuntimeService businessRuntimeService;
    private final WorkforceService workforceService;
    private final InventoryService inventoryService;
    private final TransactionService transactionService;
    private final OrderContractService orderContractService;
    private final SimulationSchedulerService schedulerService;
    private final AtomicReference<ActiveProduction> activeState = new AtomicReference<>();

    public ProductionService(
            GoodService goodService,
            EconomicActorService actorService,
            BusinessRuntimeService businessRuntimeService,
            WorkforceService workforceService,
            InventoryService inventoryService,
            TransactionService transactionService,
            OrderContractService orderContractService,
            SimulationSchedulerService schedulerService
    ) {
        this.goodService = Objects.requireNonNull(goodService, "goodService");
        this.actorService = Objects.requireNonNull(actorService, "actorService");
        this.businessRuntimeService = Objects.requireNonNull(businessRuntimeService, "businessRuntimeService");
        this.workforceService = Objects.requireNonNull(workforceService, "workforceService");
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
        this.transactionService = Objects.requireNonNull(transactionService, "transactionService");
        this.orderContractService = Objects.requireNonNull(orderContractService, "orderContractService");
        this.schedulerService = Objects.requireNonNull(schedulerService, "schedulerService");
    }

    public void initialize(ServerStartedEvent event) {
        load(event.getServer());
    }

    public void bindScheduler(ServerStartedEvent event) {
        ActiveProduction active = load(event.getServer());
        active.manager().validateSchedulerReferences(schedulerService.managerFor(event.getServer()));
    }

    public void save(ServerStoppingEvent event) {
        ActiveProduction active = activeState.get();
        if (active != null && active.server() == event.getServer()) {
            active.storage().save(active.manager());
            activeState.compareAndSet(active, null);
        }
    }

    public ProductionManager managerFor(MinecraftServer server) {
        return load(server).manager();
    }

    public Optional<ProductionManager> currentManager() {
        return Optional.ofNullable(activeState.get()).map(ActiveProduction::manager);
    }

    private ActiveProduction load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveProduction existing = activeState.get();
        if (existing != null && existing.server() == server) return existing;
        if (existing != null) existing.storage().save(existing.manager());

        ProductionDependencies dependencies = new ProductionDependencies(
                goodService.managerFor(server),
                actorService.managerFor(server),
                businessRuntimeService.managerFor(server),
                workforceService.managerFor(server),
                inventoryService.managerFor(server),
                transactionService.managerFor(server),
                orderContractService.orderManagerFor(server),
                orderContractService.contractManagerFor(server)
        );
        ProductionStorage storage = new ProductionStorage(
                processFile(server), planFile(server), runFile(server), dependencies
        );
        ProductionManager manager = storage.load();
        schedulerService.installHandler(new ProductionSimulationWorkHandler(manager));
        ActiveProduction created = new ActiveProduction(server, storage, manager);
        activeState.set(created);
        return created;
    }

    public static Path processFile(MinecraftServer server) {
        return rootDirectory(server).resolve(ProductionSchema.PROCESSES_FILE_NAME);
    }

    public static Path planFile(MinecraftServer server) {
        return rootDirectory(server).resolve(ProductionSchema.PLANS_FILE_NAME);
    }

    public static Path runFile(MinecraftServer server) {
        return rootDirectory(server).resolve(ProductionSchema.RUNS_FILE_NAME);
    }

    private static Path rootDirectory(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve(ProductionSchema.DIRECTORY_NAME).toAbsolutePath().normalize();
    }

    private record ActiveProduction(
            MinecraftServer server,
            ProductionStorage storage,
            ProductionManager manager
    ) {
    }
}
