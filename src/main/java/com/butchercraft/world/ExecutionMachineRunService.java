package com.butchercraft.world;

import com.butchercraft.world.execution.ExecutionAuthorizationEvidence;
import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.ExecutionOperationSnapshot;
import com.butchercraft.world.execution.MachineRunConfiguration;
import com.butchercraft.world.execution.MachineRunIdentity;
import com.butchercraft.world.execution.MachineRunRecord;
import com.butchercraft.world.execution.MachineRunRegistry;
import com.butchercraft.world.execution.MachineRunRegistryMutation;
import com.butchercraft.world.execution.MachineRunResultCode;
import com.butchercraft.world.execution.MachineRunSchema;
import com.butchercraft.world.execution.MachineStartAuthorizationEvidence;
import com.butchercraft.world.execution.MachineStopAuthorizationEvidence;
import com.butchercraft.world.execution.persistence.MachineRunStorage;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ExecutionMachineRunService {
    public static final ExecutionMachineRunService INSTANCE = new ExecutionMachineRunService(
            WorldIdentityService.INSTANCE,
            MachineRunConfiguration.standard(),
            ExecutionMachineRunService::runFile,
            com.butchercraft.world.simulation.SimulationClockService.INSTANCE::persistNow
    );

    private final WorldIdentityService worldIdentityService;
    private final MachineRunConfiguration configuration;
    private final Function<MinecraftServer, Path> pathFactory;
    private final Consumer<MinecraftServer> clockPersistence;
    private final AtomicReference<ActiveState> activeState = new AtomicReference<>();

    public ExecutionMachineRunService(
            WorldIdentityService worldIdentityService,
            MachineRunConfiguration configuration,
            Function<MinecraftServer, Path> pathFactory
    ) {
        this(worldIdentityService, configuration, pathFactory, server -> { });
    }

    public ExecutionMachineRunService(
            WorldIdentityService worldIdentityService,
            MachineRunConfiguration configuration,
            Function<MinecraftServer, Path> pathFactory,
            Consumer<MinecraftServer> clockPersistence
    ) {
        this.worldIdentityService = Objects.requireNonNull(worldIdentityService, "worldIdentityService");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.pathFactory = Objects.requireNonNull(pathFactory, "pathFactory");
        this.clockPersistence = Objects.requireNonNull(clockPersistence, "clockPersistence");
    }

    public void initialize(ServerStartedEvent event) {
        load(event.getServer());
    }

    public synchronized void stop(ServerStoppingEvent event) {
        ActiveState active = activeState.get();
        if (active != null && active.server() == event.getServer()) {
            if (active.persistencePresent()) active.storage().save(active.registry());
            activeState.compareAndSet(active, null);
        }
    }

    public synchronized MachineRunRegistry snapshot(MinecraftServer server) {
        return load(server).registry();
    }

    public synchronized Optional<MachineRunRecord> find(MinecraftServer server, MachineRunIdentity runIdentity) {
        return load(server).registry().find(runIdentity);
    }

    public synchronized MachineRunRegistryMutation acceptStart(
            MinecraftServer server,
            MachineStartAuthorizationEvidence evidence,
            long tick
    ) {
        return mutate(server, registry -> registry.acceptStart(evidence, configuration, tick));
    }

    public synchronized MachineRunRegistryMutation acceptStop(
            MinecraftServer server,
            MachineStopAuthorizationEvidence evidence,
            long tick
    ) {
        return mutate(server, registry -> registry.acceptStop(evidence, tick));
    }

    public synchronized MachineRunRegistryMutation prepareChild(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            long expectedRunRevision,
            ExecutionAuthorizationEvidence authorization,
            long tick
    ) {
        return mutate(server, registry -> registry.prepareChild(
                runIdentity,
                expectedRunRevision,
                authorization,
                configuration,
                tick
        ));
    }

    public synchronized MachineRunRegistryMutation admitPreparedChild(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            ExecutionOperationSnapshot operation,
            long tick
    ) {
        return mutate(server, registry -> registry.admitPreparedChild(runIdentity, operation, tick));
    }

    public synchronized MachineRunRegistryMutation cancelPreparedChild(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            ExecutionOperationId operationId,
            MachineRunResultCode failureCode,
            long tick
    ) {
        return mutate(server, registry -> registry.cancelPreparedChild(
                runIdentity,
                operationId,
                failureCode,
                tick
        ));
    }

    public synchronized MachineRunRegistryMutation observeChild(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            ExecutionOperationSnapshot operation,
            long tick
    ) {
        return mutate(server, registry -> registry.observeChild(runIdentity, operation, tick));
    }

    public synchronized MachineRunRegistryMutation suspendForRestart(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            long tick
    ) {
        return mutate(server, registry -> registry.suspendForRestart(runIdentity, tick));
    }

    public synchronized MachineRunRegistryMutation resume(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            long expectedRevision,
            long tick
    ) {
        return mutate(server, registry -> registry.resume(runIdentity, expectedRevision, tick));
    }

    public synchronized MachineRunRegistryMutation completeStop(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            long tick
    ) {
        return mutate(server, registry -> registry.completeStop(runIdentity, tick));
    }

    public synchronized MachineRunRegistryMutation recoveryRequired(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            MachineRunResultCode code,
            String detail,
            long tick
    ) {
        return mutate(server, registry -> registry.recoveryRequired(runIdentity, code, detail, tick));
    }

    public MachineRunConfiguration configuration() {
        return configuration;
    }

    public static Path runFile(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve(MachineRunSchema.DIRECTORY_NAME)
                .resolve(MachineRunSchema.FILE_NAME)
                .toAbsolutePath()
                .normalize();
    }

    private MachineRunRegistryMutation mutate(
            MinecraftServer server,
            Function<MachineRunRegistry, MachineRunRegistryMutation> mutationFactory
    ) {
        ActiveState active = load(server);
        MachineRunRegistryMutation mutation = mutationFactory.apply(active.registry());
        if (mutation.changed()) {
            clockPersistence.accept(server);
            active.storage().save(mutation.registry());
            active = new ActiveState(active.server(), active.storage(), mutation.registry(), true);
            activeState.set(active);
        }
        return mutation;
    }

    private ActiveState load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveState existing = activeState.get();
        if (existing != null && existing.server() == server) return existing;
        if (existing != null && existing.persistencePresent()) existing.storage().save(existing.registry());
        String worldIdentity = WorldIdentityRootIdentities.from(worldIdentityService.getOrCreate(server)).identity();
        MachineRunStorage storage = new MachineRunStorage(pathFactory.apply(server));
        Optional<MachineRunRegistry> persisted = storage.loadExisting();
        MachineRunRegistry registry = persisted
                .orElseGet(() -> MachineRunRegistry.empty(worldIdentity, configuration));
        if (!registry.worldIdentity().equals(worldIdentity)
                || !registry.configurationIdentity().equals(configuration.configurationIdentity())) {
            throw new IllegalStateException("Execution Machine Run persistence identity/configuration mismatch");
        }
        ActiveState created = new ActiveState(server, storage, registry, persisted.isPresent());
        activeState.set(created);
        return created;
    }

    private record ActiveState(
            MinecraftServer server,
            MachineRunStorage storage,
            MachineRunRegistry registry,
            boolean persistencePresent
    ) {
    }
}
