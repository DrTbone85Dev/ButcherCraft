package com.butchercraft.world;

import com.butchercraft.world.planning.EconomicPlanningWorkHandler;
import com.butchercraft.world.planning.PlanningDependencies;
import com.butchercraft.world.planning.PlanningExecutionBudget;
import com.butchercraft.world.planning.PlanningManager;
import com.butchercraft.world.planning.PlanningSelectionPolicy;
import com.butchercraft.world.planning.PlanningStorage;
import com.butchercraft.world.simulation.scheduler.BuiltInSimulationStages;
import com.butchercraft.world.simulation.scheduler.RetryPolicy;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.butchercraft.world.simulation.scheduler.SimulationWorkRequest;
import com.butchercraft.world.simulation.scheduler.WorkOrigin;
import com.butchercraft.world.simulation.scheduler.WorkPayload;
import com.butchercraft.world.simulation.scheduler.WorkPayloadEntry;
import com.butchercraft.world.simulation.scheduler.WorkPriority;
import com.butchercraft.world.simulation.scheduler.WorkReference;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class EconomicPlanningService {
    public static final EconomicPlanningService INSTANCE = new EconomicPlanningService(
            GoodService.INSTANCE, EconomicActorService.INSTANCE, BusinessRuntimeService.INSTANCE,
            WorkforceService.INSTANCE, InventoryService.INSTANCE, TransactionService.INSTANCE,
            OrderContractService.INSTANCE, ProductionService.INSTANCE, SimulationSchedulerService.INSTANCE
    );
    private static final SimulationWorkId CONTINUATION_WORK_ID =
            SimulationWorkId.of("butchercraft:economic_planning_cycle/continuation");

    private final GoodService goodService;
    private final EconomicActorService actorService;
    private final BusinessRuntimeService businessService;
    private final WorkforceService workforceService;
    private final InventoryService inventoryService;
    private final TransactionService transactionService;
    private final OrderContractService orderService;
    private final ProductionService productionService;
    private final SimulationSchedulerService schedulerService;
    private final AtomicReference<ActivePlanning> active = new AtomicReference<>();

    public EconomicPlanningService(
            GoodService goodService,
            EconomicActorService actorService,
            BusinessRuntimeService businessService,
            WorkforceService workforceService,
            InventoryService inventoryService,
            TransactionService transactionService,
            OrderContractService orderService,
            ProductionService productionService,
            SimulationSchedulerService schedulerService
    ) {
        this.goodService = Objects.requireNonNull(goodService);
        this.actorService = Objects.requireNonNull(actorService);
        this.businessService = Objects.requireNonNull(businessService);
        this.workforceService = Objects.requireNonNull(workforceService);
        this.inventoryService = Objects.requireNonNull(inventoryService);
        this.transactionService = Objects.requireNonNull(transactionService);
        this.orderService = Objects.requireNonNull(orderService);
        this.productionService = Objects.requireNonNull(productionService);
        this.schedulerService = Objects.requireNonNull(schedulerService);
    }

    public void prepareHandler(ServerStartedEvent event) {
        schedulerService.installHandler(new EconomicPlanningWorkHandler(() -> managerFor(event.getServer())));
    }

    public void initialize(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        PlanningDependencies dependencies = dependencies(server);
        PlanningStorage storage = new PlanningStorage(rootDirectory(server), dependencies,
                PlanningSelectionPolicy.standard(), PlanningExecutionBudget.standard());
        PlanningManager manager = storage.load();
        ActivePlanning created = new ActivePlanning(server, storage, manager);
        active.set(created);
        ensureContinuationWork(dependencies);
    }

    public void save(ServerStoppingEvent event) {
        ActivePlanning current = active.get();
        if (current != null && current.server() == event.getServer()) {
            current.storage().save(current.manager());
            active.compareAndSet(current, null);
        }
    }

    public PlanningManager managerFor(MinecraftServer server) {
        ActivePlanning current = active.get();
        if (current == null || current.server() != server) {
            throw new IllegalStateException("Economic Planning is not initialized for this server");
        }
        return current.manager();
    }

    public Optional<PlanningManager> currentManager() {
        return Optional.ofNullable(active.get()).map(ActivePlanning::manager);
    }

    private PlanningDependencies dependencies(MinecraftServer server) {
        return new PlanningDependencies(
                goodService.managerFor(server), actorService.managerFor(server),
                businessService.managerFor(server), workforceService.managerFor(server),
                inventoryService.managerFor(server), transactionService.managerFor(server),
                orderService.orderManagerFor(server), orderService.contractManagerFor(server),
                productionService.managerFor(server), schedulerService.managerFor(server)
        );
    }

    private void ensureContinuationWork(PlanningDependencies dependencies) {
        if (dependencies.schedulerManager().registry().find(CONTINUATION_WORK_ID).isPresent()) return;
        long authoritativeTick = dependencies.schedulerManager().lastFinalizedSimulationTick();
        SimulationWorkRequest request = SimulationWorkRequest.builder()
                .id(CONTINUATION_WORK_ID)
                .typeId(EconomicPlanningWorkHandler.TYPE)
                .stageId(BuiltInSimulationStages.PLANNING)
                .scheduledTick(Math.addExact(authoritativeTick, 1L))
                .priority(WorkPriority.NORMAL)
                .origin(new WorkOrigin(
                        "butchercraft:economic_planning", Optional.of("butchercraft:framework"),
                        Optional.empty(), authoritativeTick, "butchercraft:planning_service",
                        Optional.empty(), Optional.empty()
                ))
                .payload(new WorkPayload(List.of(WorkPayloadEntry.identifier(
                        EconomicPlanningWorkHandler.POLICY_PAYLOAD_KEY,
                        PlanningSelectionPolicy.DEFAULT_ID.value()
                ))))
                .retryPolicy(RetryPolicy.never())
                .maximumAttempts(Integer.MAX_VALUE)
                .references(List.of(new WorkReference(
                        "butchercraft:planning_policy", PlanningSelectionPolicy.DEFAULT_ID.value())))
                .build();
        var result = dependencies.schedulerManager().submit(request, authoritativeTick);
        if (!result.accepted()) {
            throw new IllegalStateException("Scheduler rejected Economic Planning continuation Work: "
                    + String.join("; ", result.messages()));
        }
    }

    public static Path rootDirectory(MinecraftServer server) {
        return Objects.requireNonNull(server).getWorldPath(LevelResource.ROOT)
                .resolve("butchercraft").toAbsolutePath().normalize();
    }

    private record ActivePlanning(MinecraftServer server, PlanningStorage storage, PlanningManager manager) {
    }
}
