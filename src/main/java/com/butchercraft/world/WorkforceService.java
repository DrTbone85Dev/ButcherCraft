package com.butchercraft.world;

import com.butchercraft.world.business.runtime.BusinessRuntimeManager;
import com.butchercraft.world.business.runtime.BusinessRuntimeRegistry;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.workforce.WorkforceManager;
import com.butchercraft.world.workforce.WorkforceRegistry;
import com.butchercraft.world.workforce.WorkforceSchema;
import com.butchercraft.world.workforce.WorkforceStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class WorkforceService {
    public static final WorkforceService INSTANCE = new WorkforceService(
            WorldIdentityService.INSTANCE,
            BusinessRuntimeService.INSTANCE
    );

    private final WorldIdentityService worldIdentityService;
    private final BusinessRuntimeService businessRuntimeService;
    private final AtomicReference<ActiveWorkforce> activeWorkforce = new AtomicReference<>();

    public WorkforceService(WorldIdentityService worldIdentityService, BusinessRuntimeService businessRuntimeService) {
        this.worldIdentityService = Objects.requireNonNull(worldIdentityService, "worldIdentityService");
        this.businessRuntimeService = Objects.requireNonNull(businessRuntimeService, "businessRuntimeService");
    }

    public void initialize(ServerStartedEvent event) {
        managerFor(event.getServer());
    }

    public void save(ServerStoppingEvent event) {
        ActiveWorkforce active = activeWorkforce.get();
        if (active != null && active.server() == event.getServer()) {
            active.storage().save(active.manager().registry());
            activeWorkforce.compareAndSet(active, null);
        }
    }

    public WorkforceManager managerFor(MinecraftServer server) {
        return load(server).manager();
    }

    public Optional<WorkforceManager> currentManager() {
        return Optional.ofNullable(activeWorkforce.get()).map(ActiveWorkforce::manager);
    }

    private ActiveWorkforce load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveWorkforce existing = activeWorkforce.get();
        if (existing != null && existing.server() == server) {
            return existing;
        }
        if (existing != null) {
            existing.storage().save(existing.manager().registry());
        }

        WorldIdentity identity = worldIdentityService.getOrCreate(server);
        BusinessRuntimeManager businessRuntimeManager = businessRuntimeService.managerFor(server);
        BusinessRuntimeRegistry businessRuntimeRegistry = businessRuntimeManager.registry();
        Path file = workforceFile(server).toAbsolutePath().normalize();
        WorkforceStorage storage = new WorkforceStorage(file);
        WorkforceManager manager = new WorkforceManager(storage.load());
        manager.loadWithMissingDefaults(identity.businesses(), businessRuntimeRegistry);
        manager.validate(identity.businesses(), businessRuntimeRegistry);

        ActiveWorkforce created = new ActiveWorkforce(server, storage, manager);
        activeWorkforce.set(created);
        return created;
    }

    public static Path workforceFile(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(WorkforceSchema.DIRECTORY_NAME)
                .resolve(WorkforceSchema.FILE_NAME);
    }

    private record ActiveWorkforce(
            MinecraftServer server,
            WorkforceStorage storage,
            WorkforceManager manager
    ) {
    }
}
