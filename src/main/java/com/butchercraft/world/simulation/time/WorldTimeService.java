package com.butchercraft.world.simulation.time;

import com.butchercraft.ButcherCraft;
import com.butchercraft.config.CommonConfig;
import com.butchercraft.network.WorldTimeClientSnapshotPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldTimeService {
    public static final WorldTimeService INSTANCE = new WorldTimeService(new WorldTimeController());

    private static final float CONTROLLED_DAY_TIME_PER_TICK = Float.MIN_NORMAL;
    private static final int CLIENT_SYNC_INTERVAL_TICKS = 100;

    private final WorldTimeController controller;
    private final AtomicReference<ActiveWorldTime> active = new AtomicReference<>();

    WorldTimeService(WorldTimeController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    public void initialize(ServerStartedEvent event) {
        load(event.getServer());
    }

    public void advance(ServerTickEvent.Post event) {
        ActiveWorldTime activeWorldTime = load(event.getServer());
        WorldTimeConfiguration configuration = configurationFromConfig();
        ServerLevel sourceLevel = sourceLevel(event.getServer());
        if (sourceLevel == null) {
            handleUnavailableSource(activeWorldTime, event.getServer());
            return;
        }
        if (sourceLevel.dimensionType().hasFixedTime()) {
            handleFixedTimeSource(activeWorldTime, sourceLevel);
            return;
        }

        boolean controllerEnabled = prepareMinecraftDayTimeControl(activeWorldTime, sourceLevel, configuration);
        if (configuration.enabled() && activeWorldTime.externalRateConflict()) {
            WorldTimeState next = activeWorldTime.state().withObservation(
                    sourceLevel.getDayTime(),
                    sourceLevel.getDayTime(),
                    sourceLevel.getGameTime(),
                    WorldTimeMovementClassification.EXTERNAL_AUTHORITY_CONFLICT,
                    activeWorldTime.state().consecutiveUnexpectedChanges(),
                    true
            );
            activeWorldTime.state(next);
            synchronizeClients(event.getServer(), controller.snapshot(
                    next,
                    configuration,
                    sourceLevel.getGameTime(),
                    sourceLevel.getDayTime()
            ));
            return;
        }
        WorldTimeTickResult result = controller.advance(
                activeWorldTime.state(),
                controllerEnabled ? configuration : WorldTimeConfiguration.disabled(configuration.dayLengthMinutes()),
                sourceLevel.getGameTime(),
                sourceLevel.getDayTime(),
                sourceDimensionIdentity(sourceLevel)
        );
        activeWorldTime.state(result.state());
        result.dayTimeToPublish().ifPresent(sourceLevel::setDayTime);
        logFailuresOnce(activeWorldTime, result.failures());
        synchronizeClientsIfNeeded(event.getServer(), result);
    }

    public void synchronizePlayer(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<WorldTimeStatusSnapshot> snapshot = currentSnapshot(player.server);
            snapshot.ifPresent(value -> PacketDistributor.sendToPlayer(player, WorldTimeClientSnapshotPayload.from(value)));
        }
    }

    public void save(ServerStoppingEvent event) {
        ActiveWorldTime current = active.get();
        if (current != null && current.server() == event.getServer()) {
            restoreMinecraftDayTimeControl(current, sourceLevel(event.getServer()));
            current.storage().save(current.state());
            active.compareAndSet(current, null);
        }
    }

    public Optional<WorldTimeStatusSnapshot> currentSnapshot(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ServerLevel sourceLevel = sourceLevel(server);
        if (sourceLevel == null) {
            return Optional.empty();
        }
        ActiveWorldTime activeWorldTime = load(server);
        return Optional.of(controller.snapshot(
                activeWorldTime.state(),
                configurationFromConfig(),
                sourceLevel.getGameTime(),
                sourceLevel.getDayTime()
        ));
    }

    public static Path worldTimeStateFile(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve(WorldTimeSchema.DIRECTORY_NAME)
                .resolve(WorldTimeSchema.FILE_NAME)
                .toAbsolutePath()
                .normalize();
    }

    public static WorldTimeConfiguration configurationFromConfig() {
        return new WorldTimeConfiguration(
                CommonConfig.WORLD_TIME_ENABLED.get(),
                CommonConfig.WORLD_TIME_DAY_LENGTH_MINUTES.get(),
                WorldTimeDimensionPolicy.OVERWORLD_BUSINESS_SOURCE
        );
    }

    private ActiveWorldTime load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveWorldTime existing = active.get();
        if (existing != null && existing.server() == server) {
            return existing;
        }
        if (existing != null) {
            restoreMinecraftDayTimeControl(existing, sourceLevel(existing.server()));
            existing.storage().save(existing.state());
        }

        WorldTimeConfiguration configuration = configurationFromConfig();
        ServerLevel sourceLevel = sourceLevel(server);
        String sourceDimension = sourceLevel == null ? "minecraft:overworld" : sourceDimensionIdentity(sourceLevel);
        long dayTime = sourceLevel == null ? 0L : sourceLevel.getDayTime();
        long gameTime = sourceLevel == null ? 0L : sourceLevel.getGameTime();
        WorldTimeStateStorage storage = new WorldTimeStateStorage(worldTimeStateFile(server), configuration);
        WorldTimeState state = storage.load()
                .orElseGet(() -> WorldTimeController.initialState(configuration, dayTime, gameTime, sourceDimension));
        ActiveWorldTime created = new ActiveWorldTime(
                server,
                storage,
                state,
                sourceLevel == null ? -1.0F : sourceLevel.getDayTimePerTick()
        );
        active.set(created);
        return created;
    }

    private boolean prepareMinecraftDayTimeControl(
            ActiveWorldTime activeWorldTime,
            ServerLevel sourceLevel,
            WorldTimeConfiguration configuration
    ) {
        if (!configuration.enabled()) {
            restoreMinecraftDayTimeControl(activeWorldTime, sourceLevel);
            activeWorldTime.externalRateConflict(false);
            return false;
        }
        float currentRate = sourceLevel.getDayTimePerTick();
        if (currentRate != CONTROLLED_DAY_TIME_PER_TICK && currentRate != -1.0F) {
            activeWorldTime.externalRateConflict(true);
            if (!activeWorldTime.conflictWarningLogged()) {
                ButcherCraft.LOGGER.warn("ButcherCraft world time scaling observed another day-time rate controller: {}",
                        currentRate);
                activeWorldTime.conflictWarningLogged(true);
            }
            return false;
        }
        activeWorldTime.externalRateConflict(false);
        if (currentRate != CONTROLLED_DAY_TIME_PER_TICK) {
            sourceLevel.setDayTimePerTick(CONTROLLED_DAY_TIME_PER_TICK);
            activeWorldTime.controllingMinecraftDayTime(true);
        }
        return true;
    }

    private void restoreMinecraftDayTimeControl(ActiveWorldTime activeWorldTime, ServerLevel sourceLevel) {
        if (sourceLevel != null
                && activeWorldTime.controllingMinecraftDayTime()
                && sourceLevel.getDayTimePerTick() == CONTROLLED_DAY_TIME_PER_TICK) {
            sourceLevel.setDayTimePerTick(activeWorldTime.originalDayTimePerTick());
            activeWorldTime.controllingMinecraftDayTime(false);
        }
    }

    private void handleUnavailableSource(ActiveWorldTime activeWorldTime, MinecraftServer server) {
        WorldTimeConfiguration configuration = configurationFromConfig();
        WorldTimeState next = activeWorldTime.state().withObservation(
                activeWorldTime.state().lastObservedRawDayTime(),
                activeWorldTime.state().lastExpectedScaledDayTime(),
                activeWorldTime.state().lastObservationGameTime(),
                WorldTimeMovementClassification.SOURCE_DIMENSION_UNAVAILABLE,
                activeWorldTime.state().consecutiveUnexpectedChanges(),
                activeWorldTime.state().externalConflictDetected()
        );
        activeWorldTime.state(next);
        WorldTimeStatusSnapshot snapshot = controller.snapshot(
                next,
                configuration,
                next.lastObservationGameTime(),
                next.lastExpectedScaledDayTime()
        );
        synchronizeClients(server, snapshot);
    }

    private void handleFixedTimeSource(ActiveWorldTime activeWorldTime, ServerLevel sourceLevel) {
        WorldTimeConfiguration configuration = configurationFromConfig();
        WorldTimeState next = activeWorldTime.state().withObservation(
                sourceLevel.getDayTime(),
                sourceLevel.getDayTime(),
                sourceLevel.getGameTime(),
                WorldTimeMovementClassification.FIXED_TIME_DIMENSION_IGNORED,
                activeWorldTime.state().consecutiveUnexpectedChanges(),
                activeWorldTime.state().externalConflictDetected()
        );
        activeWorldTime.state(next);
        synchronizeClients(sourceLevel.getServer(), controller.snapshot(
                next,
                configuration,
                sourceLevel.getGameTime(),
                sourceLevel.getDayTime()
        ));
    }

    private void synchronizeClientsIfNeeded(MinecraftServer server, WorldTimeTickResult result) {
        if (result.shouldSynchronizeClients()
                || result.snapshot().gameTime() % CLIENT_SYNC_INTERVAL_TICKS == 0L
                || result.snapshot().movementClassification() != WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT) {
            synchronizeClients(server, result.snapshot());
        }
    }

    private void synchronizeClients(MinecraftServer server, WorldTimeStatusSnapshot snapshot) {
        WorldTimeClientSnapshotPayload payload = WorldTimeClientSnapshotPayload.from(snapshot);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private void logFailuresOnce(ActiveWorldTime activeWorldTime, List<WorldTimeFailure> failures) {
        for (WorldTimeFailure failure : failures) {
            if (failure.code() == WorldTimeFailureCode.EXTERNAL_TIME_CONTROLLER_CONFLICT) {
                if (!activeWorldTime.conflictWarningLogged()) {
                    ButcherCraft.LOGGER.warn("ButcherCraft world time scaling detected an external time controller: {}",
                            failure.message());
                    activeWorldTime.conflictWarningLogged(true);
                }
            } else if (failure.code() == WorldTimeFailureCode.BACKWARD_TIME_MOVEMENT) {
                ButcherCraft.LOGGER.info("ButcherCraft world time observed backward day-time movement: {}",
                        failure.message());
            } else if (failure.code() != WorldTimeFailureCode.DUPLICATE_TICK_APPLICATION) {
                ButcherCraft.LOGGER.warn("ButcherCraft world time failure {}: {}",
                        failure.code().serializedName(), failure.message());
            }
        }
    }

    private static ServerLevel sourceLevel(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD);
    }

    private static String sourceDimensionIdentity(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        return dimension.location().toString();
    }

    private static final class ActiveWorldTime {
        private final MinecraftServer server;
        private final WorldTimeStateStorage storage;
        private final AtomicReference<WorldTimeState> state;
        private final float originalDayTimePerTick;
        private volatile boolean controllingMinecraftDayTime;
        private volatile boolean conflictWarningLogged;
        private volatile boolean externalRateConflict;

        private ActiveWorldTime(
                MinecraftServer server,
                WorldTimeStateStorage storage,
                WorldTimeState state,
                float originalDayTimePerTick
        ) {
            this.server = Objects.requireNonNull(server, "server");
            this.storage = Objects.requireNonNull(storage, "storage");
            this.state = new AtomicReference<>(Objects.requireNonNull(state, "state"));
            this.originalDayTimePerTick = originalDayTimePerTick;
        }

        private MinecraftServer server() {
            return server;
        }

        private WorldTimeStateStorage storage() {
            return storage;
        }

        private WorldTimeState state() {
            return state.get();
        }

        private void state(WorldTimeState state) {
            this.state.set(Objects.requireNonNull(state, "state"));
        }

        private float originalDayTimePerTick() {
            return originalDayTimePerTick;
        }

        private boolean controllingMinecraftDayTime() {
            return controllingMinecraftDayTime;
        }

        private void controllingMinecraftDayTime(boolean value) {
            controllingMinecraftDayTime = value;
        }

        private boolean conflictWarningLogged() {
            return conflictWarningLogged;
        }

        private void conflictWarningLogged(boolean value) {
            conflictWarningLogged = value;
        }

        private boolean externalRateConflict() {
            return externalRateConflict;
        }

        private void externalRateConflict(boolean value) {
            externalRateConflict = value;
        }
    }
}
