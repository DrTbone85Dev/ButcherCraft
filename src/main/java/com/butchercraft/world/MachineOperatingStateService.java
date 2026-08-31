package com.butchercraft.world;

import com.butchercraft.workstation.operation.MachineEndpointAvailability;
import com.butchercraft.workstation.operation.MachineOperatingConfiguration;
import com.butchercraft.workstation.operation.MachineOperatingMutation;
import com.butchercraft.workstation.operation.MachineOperatingPolicy;
import com.butchercraft.workstation.operation.MachineOperatingRecord;
import com.butchercraft.workstation.operation.MachineOperatingRegistry;
import com.butchercraft.workstation.operation.MachineOperatingResultCode;
import com.butchercraft.workstation.operation.MachineOperatingSchema;
import com.butchercraft.workstation.operation.MachineOperatingState;
import com.butchercraft.workstation.operation.MachineWorkstationReference;
import com.butchercraft.workstation.operation.persistence.MachineOperatingStorage;
import com.butchercraft.workstation.projection.DurableWorkstationProjectionService;
import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.MachineRunIdentity;
import com.butchercraft.world.execution.MachineStopAuthorizationEvidence;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.checkpoint.StartupMutationGateService;
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

public final class MachineOperatingStateService {
    public static final MachineOperatingStateService INSTANCE = new MachineOperatingStateService(
            WorldIdentityService.INSTANCE,
            MachineOperatingConfiguration.standard(),
            MachineOperatingStateService::stateFile,
            com.butchercraft.world.simulation.SimulationClockService.INSTANCE::persistNow
    );

    private final WorldIdentityService worldIdentityService;
    private final MachineOperatingConfiguration configuration;
    private final Function<MinecraftServer, Path> pathFactory;
    private final Consumer<MinecraftServer> clockPersistence;
    private final AtomicReference<ActiveState> activeState = new AtomicReference<>();

    public MachineOperatingStateService(
            WorldIdentityService worldIdentityService,
            MachineOperatingConfiguration configuration,
            Function<MinecraftServer, Path> pathFactory
    ) {
        this(worldIdentityService, configuration, pathFactory, server -> { });
    }

    public MachineOperatingStateService(
            WorldIdentityService worldIdentityService,
            MachineOperatingConfiguration configuration,
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

    public synchronized MachineOperatingRegistry snapshot(MinecraftServer server) {
        return load(server).registry();
    }

    public synchronized Optional<MachineOperatingRecord> find(
            MinecraftServer server,
            String workstationInstanceIdentity
    ) {
        return load(server).registry().find(workstationInstanceIdentity);
    }

    public synchronized MachineOperatingMutation prepareStart(
            MinecraftServer server,
            MachineWorkstationReference workstation,
            MachineOperatingPolicy policy,
            long expectedOperatingRevision,
            String sourceOwner,
            String sourceRequestIdentity,
            String machineRunConfigurationIdentity,
            long tick
    ) {
        return mutate(server, registry -> registry.prepareStart(
                workstation,
                policy,
                expectedOperatingRevision,
                sourceOwner,
                sourceRequestIdentity,
                machineRunConfigurationIdentity,
                tick,
                configuration
        ));
    }

    public synchronized MachineOperatingMutation activateStart(
            MinecraftServer server,
            String workstationInstanceIdentity,
            MachineRunIdentity runIdentity,
            String startAuthorizationIdentity,
            long tick
    ) {
        return mutate(server, registry -> registry.activateStart(
                workstationInstanceIdentity,
                runIdentity,
                startAuthorizationIdentity,
                tick
        ));
    }

    public synchronized MachineOperatingMutation abandonUncommittedStart(
            MinecraftServer server,
            String workstationInstanceIdentity,
            String startAuthorizationIdentity,
            long tick
    ) {
        return mutate(server, registry -> registry.abandonUncommittedStart(
                workstationInstanceIdentity,
                startAuthorizationIdentity,
                tick
        ));
    }

    public synchronized MachineOperatingMutation authorizeStop(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            long expectedRunRevision,
            long expectedOperatingRevision,
            String sourceOwner,
            String sourceRequestIdentity,
            String machineRunConfigurationIdentity,
            long tick
    ) {
        return load(server).registry().authorizeStop(
                runIdentity,
                expectedRunRevision,
                expectedOperatingRevision,
                sourceOwner,
                sourceRequestIdentity,
                machineRunConfigurationIdentity,
                tick
        );
    }

    public synchronized MachineOperatingMutation publishStop(
            MinecraftServer server,
            MachineStopAuthorizationEvidence evidence,
            long tick
    ) {
        return mutate(server, registry -> registry.publishStop(evidence, tick));
    }

    public synchronized MachineOperatingMutation completeStop(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            long tick
    ) {
        return mutate(server, registry -> registry.completeStop(runIdentity, tick));
    }

    public synchronized MachineOperatingMutation suspendForRestart(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            long tick
    ) {
        return mutate(server, registry -> registry.suspendForRestart(runIdentity, tick));
    }

    public synchronized MachineOperatingMutation resume(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            long expectedRevision,
            long tick
    ) {
        return mutate(server, registry -> registry.resume(runIdentity, expectedRevision, tick));
    }

    public synchronized MachineOperatingMutation publishOperationalState(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            MachineOperatingState next,
            Optional<String> eligibilityIdentity,
            Optional<String> blockageReason,
            long tick
    ) {
        return mutate(server, registry -> registry.publishOperationalState(
                runIdentity,
                next,
                eligibilityIdentity,
                blockageReason,
                tick
        ));
    }

    public synchronized MachineOperatingMutation bindChild(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            ExecutionOperationId operationId,
            long tick
    ) {
        return mutate(server, registry -> registry.bindChild(runIdentity, operationId, tick));
    }

    public synchronized MachineOperatingMutation clearChild(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            ExecutionOperationId operationId,
            long tick
    ) {
        return mutate(server, registry -> registry.clearChild(runIdentity, operationId, tick));
    }

    public synchronized MachineOperatingMutation availability(
            MinecraftServer server,
            String workstationInstanceIdentity,
            MachineEndpointAvailability availability,
            long tick
    ) {
        return mutate(server, registry -> registry.availability(workstationInstanceIdentity, availability, tick));
    }

    public synchronized MachineOperatingMutation recoveryRequired(
            MinecraftServer server,
            String workstationInstanceIdentity,
            MachineOperatingResultCode code,
            String detail,
            MachineEndpointAvailability availability,
            long tick
    ) {
        return mutate(server, registry -> registry.recoveryRequired(
                workstationInstanceIdentity,
                code,
                detail,
                availability,
                tick
        ));
    }

    public MachineOperatingConfiguration configuration() {
        return configuration;
    }

    public static Path stateFile(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve(MachineOperatingSchema.DIRECTORY_NAME)
                .resolve(MachineOperatingSchema.FILE_NAME)
                .toAbsolutePath()
                .normalize();
    }

    private MachineOperatingMutation mutate(
            MinecraftServer server,
            Function<MachineOperatingRegistry, MachineOperatingMutation> mutationFactory
    ) {
        StartupMutationGateService.INSTANCE.require(server, LegacySplitRecoveryParticipants.WORKSTATION);
        ActiveState active = load(server);
        MachineOperatingMutation mutation = mutationFactory.apply(active.registry());
        if (mutation.changed()) {
            clockPersistence.accept(server);
            active.storage().save(mutation.registry());
            active = new ActiveState(active.server(), active.storage(), mutation.registry(), true);
            activeState.set(active);
            mutation.record().ifPresent(record -> DurableWorkstationProjectionService.INSTANCE
                    .refreshOperatingStateReference(server, record.workstation().instanceId().value()));
        }
        return mutation;
    }

    private ActiveState load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveState existing = activeState.get();
        if (existing != null && existing.server() == server) return existing;
        if (existing != null && existing.persistencePresent()) existing.storage().save(existing.registry());
        String worldIdentity = WorldIdentityRootIdentities.from(worldIdentityService.getOrCreate(server)).identity();
        MachineOperatingStorage storage = new MachineOperatingStorage(pathFactory.apply(server));
        Optional<MachineOperatingRegistry> persisted = storage.loadExisting();
        MachineOperatingRegistry registry = persisted
                .orElseGet(() -> MachineOperatingRegistry.empty(worldIdentity, configuration));
        if (!registry.worldIdentity().equals(worldIdentity)
                || !registry.configurationIdentity().equals(configuration.configurationIdentity())) {
            throw new IllegalStateException("Machine operating-state persistence identity/configuration mismatch");
        }
        ActiveState created = new ActiveState(server, storage, registry, persisted.isPresent());
        activeState.set(created);
        return created;
    }

    private record ActiveState(
            MinecraftServer server,
            MachineOperatingStorage storage,
            MachineOperatingRegistry registry,
            boolean persistencePresent
    ) {
    }
}
