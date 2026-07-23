package com.butchercraft.world.allocation;

public final class AllocationSchema {
    public static final int CURRENT_VERSION = 1;
    public static final int MAXIMUM_DECIMAL_PRECISION = 38;
    public static final int MAXIMUM_DECIMAL_SCALE = 9;
    public static final int MAXIMUM_METADATA_ENTRIES = 64;
    public static final int MAXIMUM_METADATA_VALUE_LENGTH = 2_048;
    public static final int MAXIMUM_REQUIREMENTS_PER_SET = 1_024;
    public static final int MAXIMUM_OBSERVATION_REFERENCES = 2_048;
    public static final int MAXIMUM_ORDERING_PRECEDENCE = 1_000_000;
    public static final int MAXIMUM_RUNTIME_FAILURE_MESSAGE_LENGTH = 2_048;
    public static final int MAXIMUM_REPORT_SET_REFERENCES = 100_000;
    public static final int MAXIMUM_REPORT_COMMITMENTS = 100_000;
    public static final int MAXIMUM_REPORT_CONFLICTS = 100_000;
    public static final int MAXIMUM_REPORT_CAPACITIES = 100_000;
    public static final int MAXIMUM_REPORT_FAILURES = 100_000;
    public static final int MAXIMUM_REPORT_STAGE_COUNTS = 64;
    public static final int MAXIMUM_CYCLE_RESOURCES = 100_000;
    public static final int MAXIMUM_CYCLE_CAPACITIES = 100_000;
    public static final int MAXIMUM_CYCLE_REQUIREMENTS = 100_000;
    public static final int MAXIMUM_CYCLE_REQUESTS = 100_000;
    public static final int MAXIMUM_CYCLE_SETS = 100_000;
    public static final int MAXIMUM_CYCLE_CANDIDATE_SETS = 50_000;
    public static final int MAXIMUM_CYCLE_RUNTIMES = 100_000;
    public static final int MAXIMUM_CYCLE_COMMITMENTS = 100_000;
    public static final int MAXIMUM_TRACE_PHASES = 11;
    public static final int MAXIMUM_PROVIDERS = 20_000;
    public static final int MAXIMUM_PROVIDER_OWNER_SUBSYSTEMS = 64;
    public static final int MAXIMUM_PROVIDER_CAPABILITY_DECLARATIONS = 256;
    public static final int MAXIMUM_PROVIDER_RESOURCES = 100_000;
    public static final int MAXIMUM_PROVIDER_CAPACITIES = 100_000;
    public static final int MAXIMUM_OBSERVATION_RESOURCES = 100_000;
    public static final int MAXIMUM_OBSERVATION_CAPACITIES = 100_000;
    public static final int MAXIMUM_PROVIDER_FAILURES = 100_000;
    public static final int MAXIMUM_PROVIDER_WARNINGS = 100_000;

    private AllocationSchema() {
    }
}
