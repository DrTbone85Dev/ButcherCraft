package com.butchercraft.world.production;

public final class ProductionSchema {
    public static final int CURRENT_VERSION = 1;
    public static final String DIRECTORY_NAME = "butchercraft";
    public static final String PROCESSES_FILE_NAME = "production_processes.json";
    public static final String PLANS_FILE_NAME = "production_plans.json";
    public static final String RUNS_FILE_NAME = "production_runs.json";
    public static final int MAXIMUM_INPUT_LINES = 128;
    public static final int MAXIMUM_OUTPUT_LINES = 128;
    public static final int MAXIMUM_BINDINGS = MAXIMUM_INPUT_LINES + MAXIMUM_OUTPUT_LINES;
    public static final int MAXIMUM_TAGS = 64;
    public static final int MAXIMUM_ATTEMPTS = 1_000_000;

    private ProductionSchema() {
    }
}
