package com.butchercraft.workstation.projection;

public final class WorkstationProjectionSchema {
    public static final int LEGACY_VERSION = 1;
    public static final int CURRENT_VERSION = 2;
    public static final String DIRECTORY_NAME = "workstations";
    public static final String PROJECTION_DIRECTORY_NAME = "projections";
    // This versions the stable sharded storage layout, not the payload schema restored by R4.
    public static final String SCHEMA_DIRECTORY_NAME = "v1";
    public static final String LEGACY_SCHEMA_DIRECTORY_NAME = "v1";
    public static final int MAXIMUM_RECORD_BYTES = 2 * 1_048_576;
    public static final String CONFIGURATION_IDENTITY =
            "butchercraft:durable_workstation_projection_configuration/v2/condition_receipt_closure";
    public static final String LEGACY_CONFIGURATION_IDENTITY =
            "butchercraft:durable_workstation_projection_configuration/v1/per_instance_sha256_shards";

    private WorkstationProjectionSchema() {
    }
}
