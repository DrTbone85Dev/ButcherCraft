package com.butchercraft.world;

import com.butchercraft.ButcherCraft;
import com.butchercraft.machine.cuttingtable.execution.CuttingTableExecutionOperationHandler;
import com.butchercraft.machine.grinder.execution.GrinderExecutionOperationHandler;
import com.butchercraft.machine.pattyformer.execution.PattyFormerExecutionOperationHandler;
import com.butchercraft.world.execution.ExecutionHandlerRegistry;
import com.butchercraft.world.execution.ExecutionManager;
import com.butchercraft.world.execution.ExecutionRegistryCompatibilityObservation;
import com.butchercraft.world.execution.ExecutionRuntimeConfiguration;
import com.butchercraft.world.execution.ExecutionSchema;
import com.butchercraft.world.execution.GenericExecutionWorkHandler;
import com.butchercraft.world.execution.persistence.ExecutionStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class ExecutionService {
    public static final ExecutionService INSTANCE = new ExecutionService(
            SimulationSchedulerService.INSTANCE,
            ExecutionService::defaultHandlerRegistry,
            ExecutionRuntimeConfiguration.standard()
    );

    private final SimulationSchedulerService schedulerService;
    private final Function<MinecraftServer, ExecutionHandlerRegistry> handlerRegistryFactory;
    private final ExecutionRuntimeConfiguration configuration;
    private final AtomicReference<ActiveExecution> activeState = new AtomicReference<>();

    public ExecutionService(
            SimulationSchedulerService schedulerService,
            ExecutionHandlerRegistry handlerRegistry,
            ExecutionRuntimeConfiguration configuration
    ) {
        this(schedulerService, ignored -> handlerRegistry, configuration);
    }

    public ExecutionService(
            SimulationSchedulerService schedulerService,
            Function<MinecraftServer, ExecutionHandlerRegistry> handlerRegistryFactory,
            ExecutionRuntimeConfiguration configuration
    ) {
        this.schedulerService = Objects.requireNonNull(schedulerService, "schedulerService");
        this.handlerRegistryFactory = Objects.requireNonNull(handlerRegistryFactory, "handlerRegistryFactory");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public void prepareHandler(ServerStartedEvent event) {
        schedulerService.installHandler(new GenericExecutionWorkHandler(() -> managerFor(event.getServer())));
    }

    public void initialize(ServerStartedEvent event) {
        load(event.getServer());
    }

    public void save(ServerStoppingEvent event) {
        ActiveExecution active = activeState.get();
        if (active != null && active.server() == event.getServer()) {
            active.storage().save(active.manager());
            activeState.compareAndSet(active, null);
        }
    }

    public ExecutionManager managerFor(MinecraftServer server) {
        return load(server).manager();
    }

    public Optional<ExecutionManager> currentManager() {
        return Optional.ofNullable(activeState.get()).map(ActiveExecution::manager);
    }

    public Optional<ExecutionRegistryCompatibilityObservation> currentCompatibilityObservation() {
        return Optional.ofNullable(activeState.get())
                .flatMap(active -> active.storage().compatibilityObservation());
    }

    private ActiveExecution load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveExecution existing = activeState.get();
        if (existing != null && existing.server() == server) return existing;
        if (existing != null) existing.storage().save(existing.manager());

        ExecutionHandlerRegistry handlerRegistry = handlerRegistryFactory.apply(server);
        ExecutionStorage storage = new ExecutionStorage(executionFile(server), handlerRegistry, configuration);
        ExecutionManager manager = storage.load();
        storage.compatibilityObservation().ifPresent(observation ->
                ButcherCraft.LOGGER.info("Execution registry compatibility: {}", observation.diagnosticSummary()));
        ActiveExecution created = new ActiveExecution(server, storage, manager);
        activeState.set(created);
        return created;
    }

    private static ExecutionHandlerRegistry defaultHandlerRegistry(MinecraftServer server) {
        return new ExecutionHandlerRegistry(java.util.List.of(
                new CuttingTableExecutionOperationHandler(server),
                new GrinderExecutionOperationHandler(server),
                new PattyFormerExecutionOperationHandler(server)
        ));
    }

    public static Path executionFile(MinecraftServer server) {
        return rootDirectory(server).resolve(ExecutionSchema.FILE_NAME);
    }

    private static Path rootDirectory(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve(ExecutionSchema.DIRECTORY_NAME)
                .toAbsolutePath()
                .normalize();
    }

    private record ActiveExecution(
            MinecraftServer server,
            ExecutionStorage storage,
            ExecutionManager manager
    ) {
    }
}
