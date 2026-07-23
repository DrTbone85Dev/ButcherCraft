package com.butchercraft.world;

import com.butchercraft.ButcherCraft;
import com.butchercraft.world.simulation.SimulationClock;
import com.butchercraft.world.simulation.SimulationClockService;
import com.butchercraft.world.simulation.scheduler.PipelineStatus;
import com.butchercraft.world.simulation.scheduler.SchedulerSchema;
import com.butchercraft.world.simulation.scheduler.SimulationExecutionBudget;
import com.butchercraft.world.simulation.scheduler.SimulationPipeline;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationTickReport;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandler;
import com.butchercraft.world.simulation.scheduler.persistence.SimulationSchedulerStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class SimulationSchedulerService {
    public static final SimulationSchedulerService INSTANCE = new SimulationSchedulerService(
            SimulationClockService.INSTANCE,
            OrderContractService.INSTANCE,
            SimulationWorkHandlerRegistry.empty(),
            SimulationExecutionBudget.standard()
    );

    private final SimulationClockService clockService;
    private final OrderContractService orderContractService;
    private volatile SimulationWorkHandlerRegistry handlerRegistry;
    private final SimulationExecutionBudget executionBudget;
    private final AtomicReference<ActiveScheduler> activeState = new AtomicReference<>();

    public SimulationSchedulerService(
            SimulationClockService clockService,
            OrderContractService orderContractService,
            SimulationWorkHandlerRegistry handlerRegistry,
            SimulationExecutionBudget executionBudget
    ) {
        this.clockService = Objects.requireNonNull(clockService, "clockService");
        this.orderContractService = Objects.requireNonNull(orderContractService, "orderContractService");
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry");
        this.executionBudget = Objects.requireNonNull(executionBudget, "executionBudget");
    }

    public void initialize(ServerStartedEvent event) {
        load(event.getServer());
    }

    public void advance(ServerTickEvent.Post event) {
        ActiveScheduler active = activeState.get();
        if (active == null || active.server() != event.getServer()) {
            active = load(event.getServer());
        }
        long tick = clockService.clock(event.getServer()).simulationTick();
        SimulationTickReport report = active.pipeline().execute(tick);
        active.lastReport().set(report);
        if (report.status() == PipelineStatus.REJECTED) {
            throw new IllegalStateException("Simulation scheduler rejected authoritative tick " + tick
                    + ": " + String.join("; ", report.diagnostics()));
        }
        if (report.status() == PipelineStatus.FAILED) {
            ButcherCraft.LOGGER.error("Simulation scheduler pipeline failed at tick {}: {}",
                    tick, String.join("; ", report.diagnostics()));
        }
    }

    public void save(ServerStoppingEvent event) {
        ActiveScheduler active = activeState.get();
        if (active != null && active.server() == event.getServer()) {
            active.storage().save(active.manager());
            activeState.compareAndSet(active, null);
        }
    }

    public SimulationSchedulerManager managerFor(MinecraftServer server) {
        return load(server).manager();
    }

    public Optional<SimulationSchedulerManager> currentManager() {
        return Optional.ofNullable(activeState.get()).map(ActiveScheduler::manager);
    }

    public Optional<SimulationTickReport> lastReport() {
        ActiveScheduler active = activeState.get();
        return active == null ? Optional.empty() : Optional.ofNullable(active.lastReport().get());
    }

    public synchronized void installHandler(SimulationWorkHandler handler) {
        Objects.requireNonNull(handler, "handler");
        if (activeState.get() != null) {
            throw new IllegalStateException("Scheduler handlers must be installed before world state is loaded");
        }
        java.util.List<SimulationWorkHandler> handlers = new java.util.ArrayList<>(
                handlerRegistry.handlers().stream()
                        .filter(existing -> !existing.supportedTypeId().equals(handler.supportedTypeId()))
                        .toList()
        );
        handlers.add(handler);
        handlerRegistry = new SimulationWorkHandlerRegistry(handlers);
    }

    private ActiveScheduler load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveScheduler existing = activeState.get();
        if (existing != null && existing.server() == server) return existing;
        if (existing != null) existing.storage().save(existing.manager());

        orderContractService.orderManagerFor(server);
        SimulationClock clock = clockService.clock(server);
        SimulationSchedulerStorage storage = new SimulationSchedulerStorage(
                schedulerFile(server), handlerRegistry, clock.simulationTick()
        );
        SimulationSchedulerManager manager = storage.load();
        if (manager.lastFinalizedSimulationTick() != clock.simulationTick()) {
            throw new IllegalStateException("Scheduler and authoritative clock ticks differ: scheduler="
                    + manager.lastFinalizedSimulationTick() + ", clock=" + clock.simulationTick()
                    + ". Automatic catch-up is not supported by scheduler schema 1.");
        }
        ActiveScheduler created = new ActiveScheduler(
                server, storage, manager, new SimulationPipeline(manager, executionBudget), new AtomicReference<>()
        );
        activeState.set(created);
        return created;
    }

    public static Path schedulerFile(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve(SchedulerSchema.DIRECTORY_NAME)
                .resolve(SchedulerSchema.FILE_NAME)
                .toAbsolutePath().normalize();
    }

    private record ActiveScheduler(
            MinecraftServer server,
            SimulationSchedulerStorage storage,
            SimulationSchedulerManager manager,
            SimulationPipeline pipeline,
            AtomicReference<SimulationTickReport> lastReport
    ) { }
}
