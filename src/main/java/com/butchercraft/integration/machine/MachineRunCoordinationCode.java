package com.butchercraft.integration.machine;

public enum MachineRunCoordinationCode {
    ACCEPTED,
    EXISTING_RESULT,
    WORKSTATION_REJECTED,
    EXECUTION_REJECTED,
    STALE_RUN,
    ENDPOINT_UNAVAILABLE,
    REPLACEMENT_INSTANCE,
    CHILD_ALREADY_ACTIVE,
    RECOVERY_REQUIRED
}
