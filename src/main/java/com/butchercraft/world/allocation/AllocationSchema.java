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

    private AllocationSchema() {
    }
}
