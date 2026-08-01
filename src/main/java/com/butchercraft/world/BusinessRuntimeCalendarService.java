package com.butchercraft.world;

import com.butchercraft.config.CommonConfig;
import com.butchercraft.world.business.runtime.BusinessOperatingSchedule;
import com.butchercraft.world.business.runtime.BusinessRuntimeCalendarConfiguration;
import com.butchercraft.world.business.runtime.BusinessRuntimeCalendarSchema;
import com.butchercraft.world.business.runtime.BusinessRuntimeCalendarState;
import com.butchercraft.world.business.runtime.BusinessRuntimeCalendarStorage;
import com.butchercraft.world.business.runtime.BusinessRuntimeObservationSnapshot;
import com.butchercraft.world.business.runtime.BusinessShiftDefinition;
import com.butchercraft.world.business.runtime.BusinessShiftSet;
import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.WorldTimeService;
import com.butchercraft.world.simulation.time.WorldTimeStatusSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class BusinessRuntimeCalendarService {
    public static final BusinessRuntimeCalendarService INSTANCE = new BusinessRuntimeCalendarService(
            WorldTimeService.INSTANCE
    );

    private final WorldTimeService worldTimeService;
    private final AtomicReference<ActiveBusinessCalendarRuntime> active = new AtomicReference<>();

    BusinessRuntimeCalendarService(WorldTimeService worldTimeService) {
        this.worldTimeService = Objects.requireNonNull(worldTimeService, "worldTimeService");
    }

    public void initialize(ServerStartedEvent event) {
        load(event.getServer());
    }

    public void save(ServerStoppingEvent event) {
        ActiveBusinessCalendarRuntime current = active.get();
        if (current != null && current.server() == event.getServer()) {
            current.lastState().ifPresent(current.storage()::save);
            active.compareAndSet(current, null);
        }
    }

    public Optional<BusinessRuntimeObservationSnapshot> currentSnapshot(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveBusinessCalendarRuntime current = load(server);
        Optional<WorldTimeStatusSnapshot> worldTime = worldTimeService.currentSnapshot(server);
        if (worldTime.isEmpty()) {
            return Optional.empty();
        }
        WorldTimeStatusSnapshot timeSnapshot = worldTime.orElseThrow();
        BusinessRuntimeCalendarConfiguration configuration =
                configurationFromConfig(timeSnapshot.configurationIdentity());
        BusinessRuntimeObservationSnapshot snapshot = BusinessRuntimeObservationSnapshot.observe(
                timeSnapshot.businessCalendar(),
                configuration,
                timeSnapshot.movementClassification()
        );
        current.lastState(BusinessRuntimeCalendarState.from(snapshot));
        return Optional.of(snapshot);
    }

    public Optional<BusinessRuntimeCalendarConfiguration> currentConfiguration(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return worldTimeService.currentSnapshot(server)
                .map(snapshot -> configurationFromConfig(snapshot.configurationIdentity()));
    }

    public static BusinessRuntimeCalendarConfiguration configurationFromConfig(
            com.butchercraft.world.simulation.time.WorldTimeConfigurationIdentity worldTimeIdentity
    ) {
        BusinessOperatingSchedule schedule = operatingScheduleFromConfig();
        BusinessShiftSet shiftSet = BusinessShiftSet.of(shiftsFromConfig(), schedule);
        return new BusinessRuntimeCalendarConfiguration(
                CommonConfig.BUSINESS_RUNTIME_ENABLED.get(),
                CommonConfig.BUSINESS_RUNTIME_TIMEZONE_MODE.get(),
                schedule,
                shiftSet,
                CommonConfig.BUSINESS_RUNTIME_PRODUCTION_ORDER_DEADLINES_ENABLED.get(),
                CommonConfig.BUSINESS_RUNTIME_PRODUCTION_ORDER_DEFAULT_DEADLINE_MINUTES.get(),
                worldTimeIdentity
        );
    }

    public static Path businessRuntimeCalendarFile(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve(BusinessRuntimeCalendarSchema.DIRECTORY_NAME)
                .resolve(BusinessRuntimeCalendarSchema.FILE_NAME)
                .toAbsolutePath()
                .normalize();
    }

    private ActiveBusinessCalendarRuntime load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveBusinessCalendarRuntime existing = active.get();
        if (existing != null && existing.server() == server) {
            return existing;
        }
        if (existing != null) {
            existing.lastState().ifPresent(existing.storage()::save);
        }
        BusinessRuntimeCalendarStorage storage = new BusinessRuntimeCalendarStorage(
                businessRuntimeCalendarFile(server)
        );
        ActiveBusinessCalendarRuntime created = new ActiveBusinessCalendarRuntime(
                server,
                storage,
                new AtomicReference<>(storage.load().orElse(null))
        );
        active.set(created);
        return created;
    }

    private static BusinessOperatingSchedule operatingScheduleFromConfig() {
        BusinessOperatingSchedule.Builder builder = BusinessOperatingSchedule.builder();
        applyOperatingDay(builder, BusinessDayOfWeek.MONDAY, CommonConfig.BUSINESS_RUNTIME_OPERATING_MONDAY.get());
        applyOperatingDay(builder, BusinessDayOfWeek.TUESDAY, CommonConfig.BUSINESS_RUNTIME_OPERATING_TUESDAY.get());
        applyOperatingDay(builder, BusinessDayOfWeek.WEDNESDAY, CommonConfig.BUSINESS_RUNTIME_OPERATING_WEDNESDAY.get());
        applyOperatingDay(builder, BusinessDayOfWeek.THURSDAY, CommonConfig.BUSINESS_RUNTIME_OPERATING_THURSDAY.get());
        applyOperatingDay(builder, BusinessDayOfWeek.FRIDAY, CommonConfig.BUSINESS_RUNTIME_OPERATING_FRIDAY.get());
        applyOperatingDay(builder, BusinessDayOfWeek.SATURDAY, CommonConfig.BUSINESS_RUNTIME_OPERATING_SATURDAY.get());
        applyOperatingDay(builder, BusinessDayOfWeek.SUNDAY, CommonConfig.BUSINESS_RUNTIME_OPERATING_SUNDAY.get());
        return builder.build();
    }

    private static void applyOperatingDay(
            BusinessOperatingSchedule.Builder builder,
            BusinessDayOfWeek day,
            String configValue
    ) {
        String normalized = Objects.requireNonNull(configValue, "configValue").strip().toUpperCase(Locale.ROOT);
        if (normalized.equals("CLOSED")) {
            builder.closed(day);
        } else {
            builder.open(day, normalized);
        }
    }

    private static List<BusinessShiftDefinition> shiftsFromConfig() {
        return CommonConfig.BUSINESS_RUNTIME_SHIFTS.get().stream()
                .map(BusinessRuntimeCalendarService::parseShift)
                .sorted()
                .toList();
    }

    private static BusinessShiftDefinition parseShift(String entry) {
        String[] parts = Objects.requireNonNull(entry, "entry").split("\\|", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Business shift config must use id|display name|HH:MM-HH:MM|DAYS");
        }
        String[] times = parts[2].split("-", -1);
        if (times.length != 2) {
            throw new IllegalArgumentException("Business shift time range must use HH:MM-HH:MM");
        }
        return BusinessShiftDefinition.of(parts[0], parts[1], times[0], times[1], parseDays(parts[3]));
    }

    private static Set<BusinessDayOfWeek> parseDays(String value) {
        String[] tokens = Objects.requireNonNull(value, "value").split(",", -1);
        EnumSet<BusinessDayOfWeek> days = EnumSet.noneOf(BusinessDayOfWeek.class);
        for (String token : tokens) {
            if (!token.isBlank()) {
                days.add(BusinessDayOfWeek.valueOf(token.strip().toUpperCase(Locale.ROOT)));
            }
        }
        if (days.isEmpty()) {
            throw new IllegalArgumentException("Business shift days must not be empty");
        }
        return days;
    }

    private record ActiveBusinessCalendarRuntime(
            MinecraftServer server,
            BusinessRuntimeCalendarStorage storage,
            AtomicReference<BusinessRuntimeCalendarState> state
    ) {
        private Optional<BusinessRuntimeCalendarState> lastState() {
            return Optional.ofNullable(state.get());
        }

        private void lastState(BusinessRuntimeCalendarState state) {
            this.state.set(Objects.requireNonNull(state, "state"));
        }
    }
}
