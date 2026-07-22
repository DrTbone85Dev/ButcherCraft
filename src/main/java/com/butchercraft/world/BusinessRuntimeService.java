package com.butchercraft.world;

import com.butchercraft.world.business.runtime.BusinessEventListener;
import com.butchercraft.world.business.runtime.BusinessRuntimeManager;
import com.butchercraft.world.business.runtime.BusinessRuntimeRegistry;
import com.butchercraft.world.business.runtime.BusinessRuntimeSchema;
import com.butchercraft.world.business.runtime.BusinessRuntimeStorage;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.simulation.SimulationClock;
import com.butchercraft.world.simulation.SimulationClockService;
import com.butchercraft.world.simulation.SimulationConfiguration;
import com.butchercraft.world.simulation.SimulationEventType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class BusinessRuntimeService {
    public static final BusinessRuntimeService INSTANCE = new BusinessRuntimeService(
            WorldIdentityService.INSTANCE,
            SimulationClockService.INSTANCE,
            SimulationConfiguration.standard()
    );

    private final WorldIdentityService worldIdentityService;
    private final SimulationClockService simulationClockService;
    private final SimulationConfiguration configuration;
    private final AtomicReference<ActiveBusinessRuntime> activeRuntime = new AtomicReference<>();

    public BusinessRuntimeService(
            WorldIdentityService worldIdentityService,
            SimulationClockService simulationClockService,
            SimulationConfiguration configuration
    ) {
        this.worldIdentityService = Objects.requireNonNull(worldIdentityService, "worldIdentityService");
        this.simulationClockService = Objects.requireNonNull(simulationClockService, "simulationClockService");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public void initialize(ServerStartedEvent event) {
        managerFor(event.getServer());
    }

    public void save(ServerStoppingEvent event) {
        ActiveBusinessRuntime active = activeRuntime.get();
        if (active != null && active.server() == event.getServer()) {
            active.storage().save(active.manager().registry());
            unsubscribe(active.listener());
            activeRuntime.compareAndSet(active, null);
        }
    }

    public BusinessRuntimeManager managerFor(MinecraftServer server) {
        return load(server).manager();
    }

    public Optional<BusinessRuntimeManager> currentManager() {
        return Optional.ofNullable(activeRuntime.get()).map(ActiveBusinessRuntime::manager);
    }

    private ActiveBusinessRuntime load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveBusinessRuntime existing = activeRuntime.get();
        if (existing != null && existing.server() == server) {
            return existing;
        }
        if (existing != null) {
            existing.storage().save(existing.manager().registry());
            unsubscribe(existing.listener());
        }

        WorldIdentity identity = worldIdentityService.getOrCreate(server);
        Path file = businessRuntimeFile(server).toAbsolutePath().normalize();
        BusinessRuntimeStorage storage = new BusinessRuntimeStorage(file);
        BusinessRuntimeRegistry registry = storage.load()
                .withMissingDefaults(identity.businesses(), configuration);
        registry.validateReferences(identity.businesses());
        registry.validate(configuration);

        BusinessRuntimeManager manager = new BusinessRuntimeManager(registry, configuration);
        SimulationClock clock = simulationClockService.clock(server);
        BusinessEventListener listener = new BusinessEventListener(manager, configuration);
        simulationClockService.eventBus().subscribe(SimulationEventType.DAILY_ROLLOVER, listener);
        simulationClockService.eventBus().subscribe(SimulationEventType.WEEKLY_ROLLOVER, listener);
        manager.evaluateAt(clock.calendar(), clock.time(), clock.simulationTick());

        ActiveBusinessRuntime created = new ActiveBusinessRuntime(server, storage, manager, listener);
        activeRuntime.set(created);
        return created;
    }

    private void unsubscribe(BusinessEventListener listener) {
        simulationClockService.eventBus().unsubscribe(listener);
    }

    public static Path businessRuntimeFile(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(BusinessRuntimeSchema.DIRECTORY_NAME)
                .resolve(BusinessRuntimeSchema.FILE_NAME);
    }

    private record ActiveBusinessRuntime(
            MinecraftServer server,
            BusinessRuntimeStorage storage,
            BusinessRuntimeManager manager,
            BusinessEventListener listener
    ) {
    }
}
