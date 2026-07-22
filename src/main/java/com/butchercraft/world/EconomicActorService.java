package com.butchercraft.world;

import com.butchercraft.world.economy.actor.EconomicActorManager;
import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.economy.actor.EconomicActorSchema;
import com.butchercraft.world.economy.actor.EconomicActorStorage;
import com.butchercraft.world.goods.BuiltInIndustryCatalog;
import com.butchercraft.world.goods.GoodManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class EconomicActorService {
    public static final EconomicActorService INSTANCE = new EconomicActorService(GoodService.INSTANCE);

    private final GoodService goodService;
    private final AtomicReference<ActiveEconomicActors> activeActors = new AtomicReference<>();

    public EconomicActorService(GoodService goodService) {
        this.goodService = Objects.requireNonNull(goodService, "goodService");
    }

    public void initialize(ServerStartedEvent event) {
        managerFor(event.getServer());
    }

    public void save(ServerStoppingEvent event) {
        ActiveEconomicActors active = activeActors.get();
        if (active != null && active.server() == event.getServer()) {
            active.storage().save(active.manager().registry());
            activeActors.compareAndSet(active, null);
        }
    }

    public EconomicActorManager managerFor(MinecraftServer server) {
        return load(server).manager();
    }

    public Optional<EconomicActorManager> currentManager() {
        return Optional.ofNullable(activeActors.get()).map(ActiveEconomicActors::manager);
    }

    private ActiveEconomicActors load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveEconomicActors existing = activeActors.get();
        if (existing != null && existing.server() == server) {
            return existing;
        }
        if (existing != null) {
            existing.storage().save(existing.manager().registry());
        }

        GoodManager goodManager = goodService.managerFor(server);
        Path file = economicActorFile(server).toAbsolutePath().normalize();
        EconomicActorStorage storage = new EconomicActorStorage(
                file,
                goodManager.registry(),
                BuiltInIndustryCatalog.all()
        );
        EconomicActorRegistry registry = storage.load();
        registry.validate();
        EconomicActorManager manager = new EconomicActorManager(registry);

        ActiveEconomicActors created = new ActiveEconomicActors(server, storage, manager);
        activeActors.set(created);
        return created;
    }

    public static Path economicActorFile(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(EconomicActorSchema.DIRECTORY_NAME)
                .resolve(EconomicActorSchema.FILE_NAME);
    }

    private record ActiveEconomicActors(
            MinecraftServer server,
            EconomicActorStorage storage,
            EconomicActorManager manager
    ) {
    }
}
