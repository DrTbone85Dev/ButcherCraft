package com.butchercraft.workstation.projection;

public final class WorkstationProjectionSchema {
    public static final int CURRENT_VERSION = 1;
    public static final String DIRECTORY_NAME = "workstations";
    public static final String PROJECTION_DIRECTORY_NAME = "projections";
    public static final String SCHEMA_DIRECTORY_NAME = "v1";
    public static final int MAXIMUM_RECORD_BYTES = 2 * 1_048_576;
    public static final String CONFIGURATION_IDENTITY =
            "butchercraft:durable_workstation_projection_configuration/v1/per_instance_sha256_shards";

    private WorkstationProjectionSchema() {
    }
}
