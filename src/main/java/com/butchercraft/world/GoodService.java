package com.butchercraft.world;

import com.butchercraft.world.goods.BuiltInIndustryCatalog;
import com.butchercraft.world.goods.GoodManager;
import com.butchercraft.world.goods.GoodRegistry;
import com.butchercraft.world.goods.GoodSchema;
import com.butchercraft.world.goods.GoodStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class GoodService {
    public static final GoodService INSTANCE = new GoodService();

    private final AtomicReference<ActiveGoods> activeGoods = new AtomicReference<>();

    public void initialize(ServerStartedEvent event) {
        managerFor(event.getServer());
    }

    public void save(ServerStoppingEvent event) {
        ActiveGoods active = activeGoods.get();
        if (active != null && active.server() == event.getServer()) {
            active.storage().save(active.manager().registry());
            activeGoods.compareAndSet(active, null);
        }
    }

    public GoodManager managerFor(MinecraftServer server) {
        return load(server).manager();
    }

    public Optional<GoodManager> currentManager() {
        return Optional.ofNullable(activeGoods.get()).map(ActiveGoods::manager);
    }

    private ActiveGoods load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveGoods existing = activeGoods.get();
        if (existing != null && existing.server() == server) {
            return existing;
        }
        if (existing != null) {
            existing.storage().save(existing.manager().registry());
        }

        Path file = goodsFile(server).toAbsolutePath().normalize();
        GoodStorage storage = new GoodStorage(file, BuiltInIndustryCatalog.all());
        GoodRegistry registry = storage.load();
        registry.validate();
        GoodManager manager = new GoodManager(registry);

        ActiveGoods created = new ActiveGoods(server, storage, manager);
        activeGoods.set(created);
        return created;
    }

    public static Path goodsFile(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(GoodSchema.DIRECTORY_NAME)
                .resolve(GoodSchema.FILE_NAME);
    }

    private record ActiveGoods(
            MinecraftServer server,
            GoodStorage storage,
            GoodManager manager
    ) {
    }
}
