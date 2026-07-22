package com.butchercraft.world.simulation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class SimulationClockService {
    public static final SimulationClockService INSTANCE =
            new SimulationClockService(SimulationConfiguration.standard(), new SimulationEventBus());

    private final SimulationConfiguration configuration;
    private final SimulationEventBus eventBus;
    private final AtomicReference<ActiveSimulation> activeSimulation = new AtomicReference<>();

    public SimulationClockService(SimulationConfiguration configuration, SimulationEventBus eventBus) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    public void initialize(ServerStartedEvent event) {
        load(event.getServer());
    }

    public void advance(ServerTickEvent.Post event) {
        ActiveSimulation active = activeSimulation.get();
        if (active == null || active.server() != event.getServer()) {
            active = load(event.getServer());
        }
        active.clock().advance(1L);
    }

    public void save(ServerStoppingEvent event) {
        ActiveSimulation active = activeSimulation.get();
        if (active != null && active.server() == event.getServer()) {
            active.storage().save(active.clock().state());
            activeSimulation.compareAndSet(active, null);
        }
    }

    public SimulationClock clock(MinecraftServer server) {
        return load(server).clock();
    }

    public Optional<SimulationClock> currentClock() {
        return Optional.ofNullable(activeSimulation.get()).map(ActiveSimulation::clock);
    }

    public SimulationEventBus eventBus() {
        return eventBus;
    }

    private ActiveSimulation load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveSimulation existing = activeSimulation.get();
        if (existing != null && existing.server() == server) {
            return existing;
        }
        Path file = simulationStateFile(server).toAbsolutePath().normalize();
        SimulationStateStorage storage = new SimulationStateStorage(file, configuration);
        SimulationState state = storage.load().orElseGet(() -> SimulationState.initial(configuration));
        SimulationClock clock = new SimulationClock(configuration, state, eventBus);
        ActiveSimulation created = new ActiveSimulation(server, storage, clock);
        activeSimulation.set(created);
        return created;
    }

    public static Path simulationStateFile(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(SimulationSchema.DIRECTORY_NAME)
                .resolve(SimulationSchema.FILE_NAME);
    }

    private record ActiveSimulation(
            MinecraftServer server,
            SimulationStateStorage storage,
            SimulationClock clock
    ) {
    }
}
