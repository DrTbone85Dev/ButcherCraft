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

    private AllocationSchema() {
    }
}
