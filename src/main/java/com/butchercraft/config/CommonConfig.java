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

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CommonConfig() {
    }
}
