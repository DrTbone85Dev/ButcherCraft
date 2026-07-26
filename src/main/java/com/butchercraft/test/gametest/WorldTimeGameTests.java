package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.world.simulation.time.BusinessCalendarSnapshot;
import com.butchercraft.world.simulation.time.WorldTimeConfiguration;
import com.butchercraft.world.simulation.time.WorldTimeService;
import com.butchercraft.world.simulation.time.WorldTimeStatusSnapshot;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldTimeGameTests {
    private static final String TEMPLATE = "empty_5x4x5";

    private WorldTimeGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void scalingServiceInitializesInOverworld(GameTestHelper helper) {
        WorldTimeStatusSnapshot snapshot = snapshot(helper);

        helper.assertTrue(snapshot.scalingEnabled(), "World time scaling is enabled by default");
        helper.assertTrue(snapshot.configuredDayLengthMinutes() == WorldTimeConfiguration.DEFAULT_DAY_LENGTH_MINUTES,
                "Default configured day length is 60 minutes");
        helper.assertTrue(snapshot.sourceDimensionIdentity().equals("minecraft:overworld"),
                "Business Calendar source dimension is the Overworld");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void sixtyMinuteConfigurationAdvancesDayTimeSlowerThanGameTime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long startGameTime = level.getGameTime();
        long startDayTime = level.getDayTime();

        helper.runAtTickTime(15, () -> {
            long gameTimeDelta = level.getGameTime() - startGameTime;
            long dayTimeDelta = level.getDayTime() - startDayTime;
            helper.assertTrue(gameTimeDelta >= 15L, "gameTime continues advancing each server tick");
            helper.assertTrue(dayTimeDelta >= 3L && dayTimeDelta <= 6L,
                    "60-minute day advances dayTime near one-third speed over a bounded window");
            helper.assertTrue(dayTimeDelta < gameTimeDelta,
                    "Visible dayTime is slower than normal gameTime");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void businessCalendarDisplayMatchesCurrentScaledDayTime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorldTimeStatusSnapshot snapshot = snapshot(helper);
        BusinessCalendarSnapshot expected = BusinessCalendarSnapshot.fromDayTime(
                level.getDayTime(),
                snapshot.configurationIdentity(),
                snapshot.sourceDimensionIdentity(),
                level.getGameTime()
        );

        helper.assertTrue(snapshot.businessCalendar().businessDayIndex() == expected.businessDayIndex(),
                "Business day index derives from current dayTime");
        helper.assertTrue(snapshot.businessCalendar().dayOfWeek() == expected.dayOfWeek(),
                "Business day-of-week derives from current dayTime");
        helper.assertTrue(snapshot.businessCalendar().timeOfDay().equals(expected.timeOfDay()),
                "Business time-of-day derives from current dayTime");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void forwardTimeStyleChangeUpdatesBusinessCalendarDirectly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long targetDayTime = level.getDayTime() + (3L * BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS);
        level.setDayTime(targetDayTime);

        WorldTimeStatusSnapshot snapshot = snapshot(helper);

        helper.assertTrue(snapshot.dayTime() == targetDayTime,
                "Business Calendar snapshot observes the explicit forward dayTime change");
        helper.assertTrue(snapshot.businessCalendar().businessDayIndex()
                        == BusinessCalendarSnapshot.fromDayTime(
                                targetDayTime,
                                snapshot.configurationIdentity(),
                                snapshot.sourceDimensionIdentity(),
                                level.getGameTime()
                        ).businessDayIndex(),
                "Forward time change maps directly to the new business day");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void backwardTimeStyleChangeUpdatesBusinessCalendarDirectly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long targetDayTime = 1_000L;
        level.setDayTime(targetDayTime);

        WorldTimeStatusSnapshot snapshot = snapshot(helper);

        helper.assertTrue(snapshot.dayTime() == targetDayTime,
                "Business Calendar snapshot observes the explicit backward dayTime change");
        helper.assertTrue(snapshot.businessCalendar().timeOfDay().equals(
                        BusinessCalendarSnapshot.fromDayTime(
                                targetDayTime,
                                snapshot.configurationIdentity(),
                                snapshot.sourceDimensionIdentity(),
                                level.getGameTime()
                        ).timeOfDay()),
                "Backward time change maps directly to the visible business clock");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void scaledDayTimeDoesNotSlowServerTickObservation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long startGameTime = level.getGameTime();

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(level.getGameTime() - startGameTime >= 20L,
                    "Server gameTime advances normally while dayTime is scaled");
            helper.succeed();
        });
    }

    private static WorldTimeStatusSnapshot snapshot(GameTestHelper helper) {
        return WorldTimeService.INSTANCE.currentSnapshot(helper.getLevel().getServer()).orElseThrow();
    }
}
