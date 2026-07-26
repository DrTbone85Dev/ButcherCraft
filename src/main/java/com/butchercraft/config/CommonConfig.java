package com.butchercraft.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CommonConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_FOUNDATION_INITIALIZATION = BUILDER
            .comment("Log when ButcherCraft common initialization completes.")
            .define("logFoundationInitialization", true);

    public static final ModConfigSpec.BooleanValue ENABLE_DEVELOPMENT_DIAGNOSTIC = BUILDER
            .comment("Enable the safe /butchercraft diagnostic command in development and test environments.")
            .define("enableDevelopmentDiagnostic", true);

    public static final ModConfigSpec.BooleanValue WORLD_TIME_ENABLED;
    public static final ModConfigSpec.IntValue WORLD_TIME_DAY_LENGTH_MINUTES;

    static {
        BUILDER.push("world_time");
        WORLD_TIME_ENABLED = BUILDER
                .comment("Enable ButcherCraft's server-authoritative scaled Minecraft day-time progression.")
                .define("enabled", true);
        WORLD_TIME_DAY_LENGTH_MINUTES = BUILDER
                .comment("Real-time minutes per full 24000-unit Minecraft day. Vanilla-equivalent is 20; default is 60.")
                .defineInRange("day_length_minutes", 60, 20, 1440);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CommonConfig() {
    }
}
