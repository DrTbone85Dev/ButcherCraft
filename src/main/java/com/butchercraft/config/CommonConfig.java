package com.butchercraft.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

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
    public static final ModConfigSpec.BooleanValue BUSINESS_RUNTIME_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> BUSINESS_RUNTIME_TIMEZONE_MODE;
    public static final ModConfigSpec.ConfigValue<String> BUSINESS_RUNTIME_OPERATING_MONDAY;
    public static final ModConfigSpec.ConfigValue<String> BUSINESS_RUNTIME_OPERATING_TUESDAY;
    public static final ModConfigSpec.ConfigValue<String> BUSINESS_RUNTIME_OPERATING_WEDNESDAY;
    public static final ModConfigSpec.ConfigValue<String> BUSINESS_RUNTIME_OPERATING_THURSDAY;
    public static final ModConfigSpec.ConfigValue<String> BUSINESS_RUNTIME_OPERATING_FRIDAY;
    public static final ModConfigSpec.ConfigValue<String> BUSINESS_RUNTIME_OPERATING_SATURDAY;
    public static final ModConfigSpec.ConfigValue<String> BUSINESS_RUNTIME_OPERATING_SUNDAY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BUSINESS_RUNTIME_SHIFTS;
    public static final ModConfigSpec.BooleanValue BUSINESS_RUNTIME_PRODUCTION_ORDER_DEADLINES_ENABLED;
    public static final ModConfigSpec.IntValue BUSINESS_RUNTIME_PRODUCTION_ORDER_DEFAULT_DEADLINE_MINUTES;

    static {
        BUILDER.push("world_time");
        WORLD_TIME_ENABLED = BUILDER
                .comment("Enable ButcherCraft's server-authoritative scaled Minecraft day-time progression.")
                .define("enabled", true);
        WORLD_TIME_DAY_LENGTH_MINUTES = BUILDER
                .comment("Real-time minutes per full 24000-unit Minecraft day. Vanilla-equivalent is 20; default is 60.")
                .defineInRange("day_length_minutes", 60, 20, 1440);
        BUILDER.pop();

        BUILDER.push("business_runtime");
        BUSINESS_RUNTIME_ENABLED = BUILDER
                .comment("Enable Business Calendar-derived plant operating hours, shift observation, and deadline display.")
                .define("enabled", true);
        BUSINESS_RUNTIME_TIMEZONE_MODE = BUILDER
                .comment("Schema-1 business runtime derives directly from the Business Calendar.")
                .define("timezone_mode", "BUSINESS_CALENDAR");
        BUILDER.push("operating_hours");
        BUSINESS_RUNTIME_OPERATING_MONDAY = operatingDay("monday", "06:00-18:00");
        BUSINESS_RUNTIME_OPERATING_TUESDAY = operatingDay("tuesday", "06:00-18:00");
        BUSINESS_RUNTIME_OPERATING_WEDNESDAY = operatingDay("wednesday", "06:00-18:00");
        BUSINESS_RUNTIME_OPERATING_THURSDAY = operatingDay("thursday", "06:00-18:00");
        BUSINESS_RUNTIME_OPERATING_FRIDAY = operatingDay("friday", "06:00-18:00");
        BUSINESS_RUNTIME_OPERATING_SATURDAY = operatingDay("saturday", "CLOSED");
        BUSINESS_RUNTIME_OPERATING_SUNDAY = operatingDay("sunday", "CLOSED");
        BUILDER.pop();
        BUSINESS_RUNTIME_SHIFTS = BUILDER
                .comment("Named shifts as id|display name|HH:MM-HH:MM|MONDAY,TUESDAY. "
                        + "Schema 1 rejects overlaps and shifts outside operating hours.")
                .defineListAllowEmpty("shifts", List.of(
                        "day_shift|Day Shift|06:00-14:30|MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY",
                        "evening_shift|Evening Shift|14:30-18:00|MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY"
                ), value -> value instanceof String);
        BUILDER.push("production_order_deadlines");
        BUSINESS_RUNTIME_PRODUCTION_ORDER_DEADLINES_ENABLED = BUILDER
                .comment("Assign the fixed Production Order a server-authoritative target deadline on creation.")
                .define("enabled", true);
        BUSINESS_RUNTIME_PRODUCTION_ORDER_DEFAULT_DEADLINE_MINUTES = BUILDER
                .comment("Business Calendar minutes after creation for the fixed Production Order target deadline.")
                .defineInRange("default_deadline_minutes", 240, 0, 100_800);
        BUILDER.pop();
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CommonConfig() {
    }

    private static ModConfigSpec.ConfigValue<String> operatingDay(String name, String defaultValue) {
        return BUILDER
                .comment("Operating window for " + name
                        + ". Use CLOSED or HH:MM-HH:MM. Opening is inclusive; closing is exclusive.")
                .define(name, defaultValue);
    }
}
