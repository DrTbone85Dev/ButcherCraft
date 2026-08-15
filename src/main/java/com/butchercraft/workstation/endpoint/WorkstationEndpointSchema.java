package com.butchercraft.workstation.endpoint;

public final class WorkstationEndpointSchema {
    public static final int INSTANCE_SCHEMA_VERSION = 1;
    public static final int LEGACY_ENDPOINT_PROTOCOL_VERSION = 1;
    public static final int STACK_AWARE_ENDPOINT_PROTOCOL_VERSION = 2;
    public static final int STACK_AWARE_JOURNAL_SCHEMA_VERSION = 2;

    /**
     * Schema-1 runtime alias retained so legacy identities and records remain byte-for-byte stable.
     */
    public static final int CURRENT_VERSION = 1;
    public static final String DIRECTORY_NAME = "butchercraft";
    public static final String INSTANCE_FILE_NAME = "workstation_instances.json";
    public static final String JOURNAL_FILE_NAME = "workstation_endpoint_journal.json";

    private WorkstationEndpointSchema() {
    }
}
